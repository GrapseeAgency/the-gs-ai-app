// Batched decode: many independent sequences in ONE llama_decode call.
//
// Why this exists
// ---------------
// LocalProvider held a Mutex around a single context, so the machine served
// exactly one request at a time and throughput was capped at 4.76 req/s while
// 7.7 of 8 GiB of VRAM sat idle. That was named "the first wall" in
// native/docs/million-user-shape.md.
//
// The obvious fix -- a pool of N contexts -- does not work, and it is worth
// being explicit about why. N contexts still submit to ONE GPU queue, so Vulkan
// serialises the submissions and aggregate throughput barely moves. What
// actually raises throughput is putting tokens from several sequences into a
// single decode, which is what n_seq_max is for.
//
// The API is deliberately batch-shaped rather than async: callers hand over N
// prompts and get N strings back. The scheduler that coalesces concurrent
// requests into these batches lives in Rust (local_provider.rs); this file is
// the primitive it drives.
//
// Losslessness: with a greedy sampler, each sequence's output is identical to
// what it would have been decoded alone. The batch only changes how many tokens
// travel together, never which tokens are chosen.
//
// Two bugs this file is careful about, both found by measurement
// -------------------------------------------------------------
// 1. llama_sampler_sample(smpl, ctx, idx) reads the logits of batch entry `idx`
//    of the MOST RECENT llama_decode. Passing -1 means "the last entry", which
//    in a batch of N is only correct for the sequence that happened to be last.
//    Sample N sequences with -1 and N-1 of them are chosen from the wrong
//    distribution -- silently, with plausible-looking output. So this tracks,
//    per sequence, the batch index its logits landed at.
//
// 2. llama_tokenize's two-pass convention: a NEGATIVE return is the negated
//    number of tokens required, which is the measure pass. An early version
//    tested `k <= 0` and therefore turned every prompt into zero tokens. The
//    batch returned GS_OK, 50 empty strings, and a throughput reading of
//    2118 req/s -- a number that measured the absence of work. The losslessness
//    check in run_batch.rs is what caught it, which is why that check runs
//    before the throughput number is believed.

#include "llama_wrapper.h"

#include <algorithm>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

extern "C" {

void gs_llama_free_text(char* s) { if (s) free(s); }

/* Run `n` independent prompts through the context concurrently.
 *
 * n must be <= the context's n_seq_max. Each prompt gets its own seq_id, so
 * the KV cache keeps them apart.
 *
 * out_texts[i] is malloc'd on return and owned by the caller (free it).
 * out_lens[i] receives the byte length. A slot that produced nothing is set to
 * -1 rather than to an empty string, so "no answer" stays distinguishable from
 * "the answer was empty".
 */
int32_t gs_llama_batch_generate(gs_llama_context_t* ctx,
                                const char* const* prompts,
                                int32_t n,
                                int32_t max_tokens,
                                float temperature,
                                char** out_texts,
                                int32_t* out_lens) {
    if (!ctx || !prompts || !out_texts || !out_lens) return GS_ERR_INVALID_ARG;
    if (n <= 0 || max_tokens <= 0) return GS_ERR_INVALID_ARG;
    if (!ctx->available) return GS_ERR_UNAVAILABLE;

    for (int32_t i = 0; i < n; ++i) {
        out_texts[i] = nullptr;
        out_lens[i] = -1;
    }

#if defined(GS_LLAMA_HAVE_LLAMA)
    if ((uint32_t)n > llama_n_seq_max(ctx->ctx)) {
        gs_set_error("batch size exceeds the context's n_seq_max");
        return GS_ERR_INVALID_ARG;
    }
    const int32_t n_batch =
        llama_n_batch(ctx->ctx) > 0 ? llama_n_batch(ctx->ctx) : 512;
    if (n_batch < n) {
        // Every decode carries one token per live sequence, so the batch must
        // fit them all. Silently truncating would drop sequences.
        gs_set_error("n_batch is smaller than the requested batch");
        return GS_ERR_INVALID_ARG;
    }

    try {
        llama_memory_clear(llama_get_memory(ctx->ctx), /*data=*/true);

        std::vector<std::vector<llama_token>> toks((size_t)n);
        std::vector<std::string>             outs((size_t)n);
        std::vector<int32_t>                 gen((size_t)n, 0);
        std::vector<bool>                    alive((size_t)n, true);
        std::vector<int32_t>                 pos((size_t)n, 0);
        // Where each sequence's logits live in the output buffer. This is the
        // whole ballgame; see the file header.
        std::vector<int32_t>                 logits_idx((size_t)n, -1);

        auto tokenize = [&](const char* s) -> std::vector<llama_token> {
            const std::string str(s ? s : "");
            // NEGATIVE means "this is how many tokens you need" -- that is the
            // measure pass. Testing `k <= 0` silently turned every prompt into
            // zero tokens, and the batch returned GS_OK with 50 empty strings.
            const int32_t k = -llama_tokenize(ctx->vocab, str.c_str(),
                                              (int32_t)str.size(), nullptr, 0,
                                              /*add_special=*/true, /*parse_special=*/false);
            if (k <= 0) return {};
            std::vector<llama_token> t((size_t)k);
            if (llama_tokenize(ctx->vocab, str.c_str(), (int32_t)str.size(),
                               t.data(), k, true, false) != k) {
                return {};
            }
            return t;
        };

        llama_batch b = llama_batch_init(n_batch, 0, (int32_t)n);
        if (!b.token) { gs_set_error("batch init failed"); return GS_ERR_NO_MEMORY; }

        for (int32_t s = 0; s < n; ++s) {
            toks[(size_t)s] = tokenize(prompts[s]);
            if (toks[(size_t)s].empty()) {
                alive[(size_t)s] = false;
                out_lens[(size_t)s] = 0;
                out_texts[(size_t)s] = strdup("");
            }
        }

        auto decode = [&](int32_t count) -> bool {
            b.n_tokens = count;
            return llama_decode(ctx->ctx, b) == 0;
        };
        // --- PREFILL, pass 1: every prompt EXCEPT its last token ----------
        // Two constraints shape this.
        //
        // (a) Logits are deliberately not requested here. A chunked prefill ends
        //     with only the LAST sequence's prompt tokens in the output buffer,
        //     so asking for logits in this pass would serve one sequence out of
        //     N. Pass 2 puts every sequence's final token in one decode, which
        //     is the only way to get N live distributions at once.
        //
        // (b) The last token is HELD BACK rather than submitted here and
        //     repeated in pass 2. Re-decoding an occupied position is rejected.
        //     Raw error from llama.cpp:
        //         the tokens for sequence 0 in the input batch have a starting
        //         position of Y = 10
        //         it is required that the sequence positions remain
        //         consecutive: Y = X + 1
        //     X was already 10, so the repeat was illegal. Holding the token
        //     back makes pass 2 the legitimate Y = X + 1.
        for (int32_t s = 0; s < n; ++s) {
            if (!alive[(size_t)s]) continue;
            const auto& t = toks[(size_t)s];
            const size_t body = t.size() - 1;  // all but the last token
            size_t fed = 0;
            while (fed < body) {
                const int32_t take = (int32_t)std::min((size_t)n_batch, body - fed);
                for (int32_t j = 0; j < take; ++j) {
                    b.token[j]     = t[fed + (size_t)j];
                    b.pos[j]       = (llama_pos)(fed + (size_t)j);
                    b.n_seq_id[j]  = 1;
                    b.seq_id[j][0] = (llama_seq_id)s;
                    b.logits[j]    = false;
                }
                if (!decode(take)) {
                    llama_batch_free(b);
                    gs_set_error("prefill decode failed");
                    return GS_ERR_GENERATION;
                }
                fed += (size_t)take;
            }
            pos[(size_t)s] = (int32_t)t.size();
        }

        // --- PREFILL, pass 2: one final token per sequence, logits on -----
        // Re-evaluating the last prompt token is idempotent for a deterministic
        // model and costs one token of compute per sequence. It is the only way
        // to get all N distributions into the output buffer at once, because a
        // chunked prefill leaves only the LAST sequence's logits behind.
        int32_t m = 0;
        for (int32_t s = 0; s < n; ++s) {
            if (!alive[(size_t)s]) continue;
            const auto& t = toks[(size_t)s];
            b.token[m]     = t.back();
            b.pos[m]       = (llama_pos)(t.size() - 1);
            b.n_seq_id[m]  = 1;
            b.seq_id[m][0] = (llama_seq_id)s;
            b.logits[m]    = true;
            logits_idx[(size_t)s] = m;
            ++m;
        }
        if (m > 0 && !decode(m)) {
            llama_batch_free(b);
            gs_set_error("logits prefill pass failed");
            return GS_ERR_GENERATION;
        }

        // --- DECODE: one token per live sequence, all in one batch -------
        // One sampler per sequence: greedy carries no state today, but a
        // temperature or top-p implementation does, and a shared chain would
        // then let one sequence's history decide another's token.
        std::vector<llama_sampler*> samplers((size_t)n, nullptr);
        for (int32_t s = 0; s < n; ++s) {
            if (!alive[(size_t)s]) continue;
            samplers[(size_t)s] = llama_sampler_init_greedy();
            if (!samplers[(size_t)s]) {
                llama_batch_free(b);
                gs_set_error("sampler init failed");
                return GS_ERR_NO_MEMORY;
            }
        }
        (void)temperature;  // greedy only; see the note above

        int32_t live = 0;
        for (int32_t s = 0; s < n; ++s) if (alive[(size_t)s]) ++live;

        while (live > 0) {
            // Sample every live sequence from ITS OWN logits entry, then feed
            // all of the sampled tokens back in one decode.
            live = 0;
            int32_t k = 0;
            for (int32_t s = 0; s < n; ++s) {
                if (!alive[(size_t)s]) continue;
                const llama_token t = llama_sampler_sample(samplers[(size_t)s],
                                                          ctx->ctx,
                                                          logits_idx[(size_t)s]);
                if (llama_vocab_is_eog(ctx->vocab, t)) { alive[(size_t)s] = false; continue; }
                char piece[256];
                const int len = llama_token_to_piece(ctx->vocab, t, piece,
                                                     sizeof(piece), 0,
                                                     /*special=*/true);
                if (len > 0) outs[(size_t)s].append(piece, (size_t)len);
                ++gen[(size_t)s];
                if (gen[(size_t)s] >= max_tokens) { alive[(size_t)s] = false; continue; }

                b.token[k]     = t;
                b.pos[k]       = (llama_pos)pos[(size_t)s];
                b.n_seq_id[k]  = 1;
                b.seq_id[k][0] = (llama_seq_id)s;
                b.logits[k]    = true;
                logits_idx[(size_t)s] = k;   // where THIS decode's logits land
                ++pos[(size_t)s];
                ++k;
                ++live;
            }
            if (k == 0) break;
            if (!decode(k)) {
                llama_batch_free(b);
                for (auto* sp : samplers) if (sp) llama_sampler_free(sp);
                gs_set_error("batched decode failed");
                return GS_ERR_GENERATION;
            }
        }
        llama_batch_free(b);
        for (auto* sp : samplers) if (sp) llama_sampler_free(sp);

        for (int32_t s = 0; s < n; ++s) {
            if (out_texts[(size_t)s]) continue;  // pre-existing "" for empty prompts
            out_texts[s] = strdup(outs[(size_t)s].c_str());
            out_lens[s]  = (int32_t)outs[(size_t)s].size();
        }
        return GS_OK;
    } catch (const std::exception& e) {
        gs_set_error((std::string("batched generate: ") + e.what()).c_str());
        return GS_ERR_INTERNAL;
    }
#else
    (void)max_tokens; (void)temperature; (void)prompts;
    return GS_ERR_UNAVAILABLE;
#endif
}

}  // extern "C"
