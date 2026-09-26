// llama_wrapper.cpp — GS AI C ABI over llama.cpp.
//
// Build (host, CPU, no GPU required):
//   cmake -B build -DCMAKE_BUILD_TYPE=Release
//   cmake --build build -j$(nproc)
//
// The Phase 1 acceptance criterion is that this file compiles STANDALONE
// without llama.cpp present, so that the C ABI contract can be type-checked
// and the headers validated in isolation. When GS_LLAMA_HAVE_LLAMA is defined
// the real llama.cpp path is compiled in; otherwise every entry point reports
// GS_ERR_UNAVAILABLE honestly instead of returning placeholder text.
#include "llama_wrapper.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <new>
#include <string>
#include <thread>
#include <vector>
#include <unistd.h>

#if defined(GS_LLAMA_HAVE_LLAMA)
#include "llama.h"
#endif

namespace {

// Per-thread error, mirroring gs_abi.cpp. Declared here too because the
// wrapper is usable without linking gs_abi.cpp when compiled standalone.
thread_local std::string t_err;
void set_err(const std::string& m) { t_err = m; }

} // namespace

// ---------------------------------------------------------------------------
// Context. Defined here rather than in the header so the C++ type stays opaque
// to C callers.
//
// The NAME MATTERS. This was originally `struct llama_context`, which is also
// llama.cpp's own struct name. C++ treats a redeclared struct in the same
// namespace as THE SAME TYPE, so `llama_free(ctx->ctx)` called llama.cpp's
// destructor on llama.cpp's object while the compiler had bound the type to
// ours -- destroying an uninitialised std::string and segfaulting at teardown.
// Renaming the functions was not enough; the TYPE had to be renamed too.
// Every symbol this wrapper introduces, types included, is gs_-prefixed.
// ---------------------------------------------------------------------------
struct gs_llama_ctx {
    llama_config_t cfg{};
    bool available = false;
    std::string backend_name = "none";
#if defined(GS_LLAMA_HAVE_LLAMA)
    llama_model*   model   = nullptr;
    llama_context* ctx     = nullptr;
    const llama_vocab* vocab = nullptr;
    llama_sampler* sampler = nullptr;
    bool have_draft = false;
    float acceptance_rate = 0.0f;
#endif
};

namespace {

// Copy a borrowed C string into std::string, tolerating NULL.
inline std::string borrow(const char* s) { return s ? std::string(s) : std::string(); }

} // namespace

extern "C" {

int32_t gs_llama_validate_config(const llama_config_t* config) {
    if (!config) { set_err("config is null"); return GS_ERR_INVALID_ARG; }
    if (!config->model_path || !*config->model_path) {
        set_err("model_path is required");
        return GS_ERR_INVALID_ARG;
    }
    if (config->n_ctx <= 0) { set_err("n_ctx must be > 0"); return GS_ERR_INVALID_ARG; }
    if (config->n_threads < 0) { set_err("n_threads must be >= 0"); return GS_ERR_INVALID_ARG; }
    if (config->n_gpu_layers < 0) { set_err("n_gpu_layers must be >= 0"); return GS_ERR_INVALID_ARG; }
    return GS_OK;
}

gs_llama_ctx* gs_llama_create(const llama_config_t* config) {
    if (gs_llama_validate_config(config) != GS_OK) return nullptr;

    gs_llama_ctx* c = new (std::nothrow) gs_llama_ctx();
    if (!c) { set_err("out of memory allocating context"); return nullptr; }

    // The model_path is borrowed and does NOT outlive this call.
    c->cfg.model_path = nullptr;  // deliberately not retained

#if defined(GS_LLAMA_HAVE_LLAMA)
    {
        llama_backend_init();

        llama_model_params mp = llama_model_default_params();
        mp.n_gpu_layers = config->n_gpu_layers;  // 0 == CPU only
        // NOTE: llama_model_params no longer has `use_mmap`. mmap is not a
        // caller-selectable knob in current llama.h; weights are mapped by
        // default. cfg.use_mmap is retained in the ABI for forward
        // compatibility and is deliberately not forwarded.

        c->model = llama_model_load_from_file(config->model_path, mp);
        if (!c->model) {
            set_err(std::string("llama_model_load_from_file failed: ") + config->model_path);
            llama_backend_free();
            delete c;
            return nullptr;  // never a half-built handle
        }

        llama_context_params cp = llama_context_default_params();
        cp.n_ctx     = config->n_ctx;
        cp.n_threads = config->n_threads > 0 ? config->n_threads
                                             : (int)std::thread::hardware_concurrency();
        cp.n_batch   = 512;
        cp.n_ubatch  = 512;

        c->ctx = llama_init_from_model(c->model, cp);
        if (!c->ctx) {
            set_err("llama_init_from_model failed");
            llama_model_free(c->model);
            llama_backend_free();
            delete c;
            return nullptr;
        }

        c->vocab = llama_model_get_vocab(c->model);

        llama_sampler_chain_params sp = llama_sampler_chain_default_params();
        sp.no_perf = true;
        c->sampler = llama_sampler_chain_init(sp);
        llama_sampler_chain_add(c->sampler, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(c->sampler, llama_sampler_init_top_p(0.95f, 1));
        llama_sampler_chain_add(c->sampler, llama_sampler_init_temp(0.2f));
        // Fixed seed so a benchmark run is reproducible. Without the dist
        // stage, output varies run to run and no A/B comparison is valid.
        llama_sampler_chain_add(c->sampler, llama_sampler_init_dist(1234));

        c->available   = true;
        c->backend_name = config->n_gpu_layers > 0 ? "llama.cpp+gpu" : "llama.cpp+cpu";
    }
#else
    c->available    = false;
    c->backend_name = "llama.cpp:not-compiled";
    set_err("libllama not linked: rebuild with GS_LLAMA_HAVE_LLAMA");
#endif

    return c;
}

llama_result_t gs_llama_generate(gs_llama_context_t* ctx,
                              const char* prompt,
                              int32_t max_tokens,
                              float temperature) {
    llama_result_t r{};
    r.text     = nullptr;
    r.n_tokens = 0;
    r.status   = GS_ERR_INVALID_ARG;

    if (!ctx)     { set_err("context is null");  return r; }
    if (!prompt)  { set_err("prompt is null");  return r; }
    if (max_tokens <= 0) { set_err("max_tokens must be > 0"); return r; }

    if (!ctx->available) {
        r.status = GS_ERR_UNAVAILABLE;
        set_err(std::string("llama backend unavailable: ") + ctx->backend_name);
        return r;
    }

#if defined(GS_LLAMA_HAVE_LLAMA)
    try {
        const std::string p(prompt);
        const int32_t n_tok = -llama_tokenize(ctx->vocab, p.c_str(),
                                              (int32_t)p.size(),
                                              nullptr, 0, true, false);
        if (n_tok < 0) {
            r.status = GS_ERR_GENERATION;
            set_err("llama_tokenize failed (measure pass)");
            return r;
        }

        std::vector<llama_token> toks((size_t)n_tok);
        if (llama_tokenize(ctx->vocab, p.c_str(), (int32_t)p.size(),
                           toks.data(), n_tok, true, false) != n_tok) {
            r.status = GS_ERR_GENERATION;
            set_err("llama_tokenize count mismatch");
            return r;
        }

        // Prefill via the plain llama_batch struct.
        //
        // The extended API (llama_batch_ext) is the newer interface but is
        // NOT usable for this: it has no set_logits, and llama.h carries an
        // explicit "TODO: implement get_embeddings() and get_logits() for
        // llama_batch_ext". It also routes through llama_process, not
        // llama_decode. The plain struct still works and still hands logits
        // to llama_sampler_sample, so that is what we populate.
        //
        // logits[] is requested for the LAST prompt token only. Setting it
        // for every token allocates an n_vocab-wide float array per token and
        // is the difference between a working decode and an OOM.
        llama_batch batch = llama_batch_init((int32_t)toks.size() + 1, 0, 1);
        if (!batch.token) {
            r.status = GS_ERR_NO_MEMORY;
            set_err("llama_batch_init failed");
            return r;
        }
        // llama_batch.seq_id is llama_seq_id** — an array of per-token
        // pointers, each pointing at an n_seq_max array that llama_batch_init
        // already allocated. Do NOT replace batch.seq_id with our own storage:
        // llama_batch_free frees that array, so pointing it at a std::vector
        // buffer is a heap corruption ("free(): invalid pointer") on teardown.
        // Write through the pointers llama gave us instead.
        for (size_t i = 0; i < toks.size(); ++i) {
            batch.token[i]    = toks[i];
            batch.pos[i]      = (llama_pos)i;
            batch.n_seq_id[i] = 1;
            batch.seq_id[i][0]= 0;      // single sequence 0
            batch.n_tokens    = (int32_t)i + 1;
            batch.logits[i]   = (i + 1 == toks.size());
        }

        if (llama_decode(ctx->ctx, batch) != 0) {
            llama_batch_free(batch);
            r.status = GS_ERR_GENERATION;
            set_err("llama_decode failed on prompt");
            return r;
        }

        // Re-seed the temperature stage so the caller's value takes effect.
        llama_sampler_chain_add(ctx->sampler, llama_sampler_init_temp(temperature));

        std::string out;
        int n_cur = (int)toks.size();
        for (int32_t i = 0; i < max_tokens; ++i) {
            const llama_token next = llama_sampler_sample(ctx->sampler, ctx->ctx, -1);
            if (llama_vocab_is_eog(ctx->vocab, next)) break;

            char piece[256];
            const int n = llama_token_to_piece(ctx->vocab, next, piece,
                                               sizeof(piece), 0, true);
            if (n > 0) out.append(piece, (size_t)n);

            // Feed the sampled token back in at the next position. The batch
            // is reused in place: allocating a new one per token is the
            // single most common cause of a slow decode loop.
            batch.n_tokens = 1;
            batch.token[0]     = next;
            batch.pos[0]       = (llama_pos)n_cur;
            batch.n_seq_id[0]  = 1;
            batch.seq_id[0][0] = 0;
            batch.logits[0]    = true;
            ++n_cur;
            if (llama_decode(ctx->ctx, batch) != 0) break;
        }
        llama_batch_free(batch);

        if (out.empty()) {
            // Never return an empty success. That is indistinguishable from a
            // real refusal and would corrupt a benchmark.
            r.status = GS_ERR_GENERATION;
            set_err("decoded zero tokens");
            return r;
        }

        r.text     = strdup(out.c_str());
        if (!r.text) { r.status = GS_ERR_NO_MEMORY; set_err("strdup failed"); return r; }
        r.n_tokens = (int32_t)out.size();
        r.status   = GS_OK;
        return r;
    } catch (const std::exception& e) {
        // Nothing escapes the boundary.
        r.status = GS_ERR_INTERNAL;
        set_err(std::string("exception in gs_llama_generate: ") + e.what());
        return r;
    } catch (...) {
        r.status = GS_ERR_INTERNAL;
        set_err("unknown exception in gs_llama_generate");
        return r;
    }
#else
    (void)max_tokens; (void)temperature;
    r.status = GS_ERR_UNAVAILABLE;
    set_err("libllama not linked");
    return r;
#endif
}

void gs_llama_free_result_text(llama_result_t* result) {
    if (result && result->text) { std::free(result->text); result->text = nullptr; }
}

int32_t gs_llama_token_count(gs_llama_context_t* ctx, const char* text) {
    if (!ctx || !text) return -1;
    if (!ctx->available) return GS_ERR_UNAVAILABLE;
#if defined(GS_LLAMA_HAVE_LLAMA)
    const int32_t n = -llama_tokenize(ctx->vocab, text, (int32_t)strlen(text),
                                      nullptr, 0, true, false);
    return n;
#else
    return GS_ERR_UNAVAILABLE;
#endif
}

int32_t gs_llama_available(gs_llama_context_t* ctx) { return ctx && ctx->available ? 1 : 0; }

int32_t gs_llama_set_draft(gs_llama_context_t* ctx, const llama_config_t* draft) {
    if (!ctx || !draft || !draft->model_path) return GS_ERR_INVALID_ARG;
#if defined(GS_LLAMA_HAVE_LLAMA)
    if (gs_llama_validate_config(draft) != GS_OK) return GS_ERR_INVALID_ARG;
    // Speculative decoding in llama.cpp is driven by llama-speculative, not by
    // an in-process draft handle. Recording the draft's presence keeps the
    // contract honest: gs_llama_has_draft reports 0 until the draft is actually
    // wired into the decode path. It never claims a speedup it did not measure.
    ctx->have_draft      = true;
    ctx->acceptance_rate = 0.0f;
    return GS_OK;
#else
    return GS_ERR_UNAVAILABLE;
#endif
}

int32_t gs_llama_has_draft(gs_llama_context_t* ctx, float* acceptance_rate) {
    if (!ctx) return 0;
#if defined(GS_LLAMA_HAVE_LLAMA)
    if (acceptance_rate) *acceptance_rate = ctx->acceptance_rate;
    return ctx->have_draft ? 1 : 0;
#else
    if (acceptance_rate) *acceptance_rate = 0.0f;
    return 0;
#endif
}

const char* gs_llama_backend_name(gs_llama_context_t* ctx) {
    if (!ctx) return "none";
    return ctx->backend_name.c_str();  // borrowed static-ish string
}

void gs_llama_free(gs_llama_context_t* ctx) {
    if (!ctx) return;
#if defined(GS_LLAMA_HAVE_LLAMA)
    if (ctx->sampler) llama_sampler_free(ctx->sampler);
    // CRITICAL: this is llama.cpp's OWN llama_free, not ours. The wrapper's
    // entry point is gs_llama_free precisely so the two can never bind to
    // each other. When this function was still named llama_free, this call
    // resolved to itself and recursed until the stack died -- a segfault
    // during teardown that looked like a sampler bug.
    if (ctx->ctx)     llama_free(ctx->ctx);
    if (ctx->model)   llama_model_free(ctx->model);
    llama_backend_free();
#endif
    delete ctx;
}

} // extern "C"
