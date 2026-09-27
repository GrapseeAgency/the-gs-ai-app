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
// Writes through the shared gs_abi channel. This used to be a private
// thread_local that nothing read, so every llama error was reported to Rust as
// an empty string despite a correct message being right here.
void set_err(const std::string& m) { gs_set_error(m.c_str()); }

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
    // n_gpu_layers < 0 is llama.cpp's "offload every layer" sentinel and must be
    // accepted: it is the only value that means GPU-only. Rejecting it forced
    // every caller to 0, i.e. CPU-only, while the operator believed the run was
    // on the GPU.
    if (config->n_gpu_layers == INT32_MIN) { set_err("n_gpu_layers is out of range"); return GS_ERR_INVALID_ARG; }
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
        mp.n_gpu_layers = config->n_gpu_layers;  // 0 == CPU only, <0 == every layer
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
        // Sequences the context keeps apart. Batched decode needs >1; 1 is
        // exactly the pre-batching behaviour.
        cp.n_seq_max = config->n_seq_max > 0 ? (uint32_t)config->n_seq_max : 1u;
        cp.n_threads = config->n_threads > 0 ? config->n_threads
                                             : (int)std::thread::hardware_concurrency();
        cp.n_batch   = 512;
        cp.n_ubatch  = 512;
        // KV cache element type. F16 is the default; a caller can trade fidelity
        // for footprint by asking for 4-bit. ggml validates the pairing.
        cp.type_k = (ggml_type)(config->cache_type_k ? config->cache_type_k : GGML_TYPE_F16);
        cp.type_v = (ggml_type)(config->cache_type_v ? config->cache_type_v : GGML_TYPE_F16);

        c->ctx = llama_init_from_model(c->model, cp);
        if (!c->ctx) {
            set_err("llama_init_from_model failed");
            llama_model_free(c->model);
            llama_backend_free();
            delete c;
            return nullptr;
        }

        c->vocab = llama_model_get_vocab(c->model);

        // The chain is built lazily per call by build_sampler_chain() and torn
        // down at the end of the call. It used to be built once here and then
        // have a temperature stage APPENDED on every generate, which meant:
        //   call 1: [top_k, top_p, temp, dist]
        //   call 2: [top_k, top_p, temp, dist, temp]
        //   call N: [... dist, temp, temp, ... ]   one leaked stage per call
        // The dist stage's RNG therefore advanced with request history, so the
        // same prompt on a fresh context and on a reused one gave different
        // answers. Measured: two independent contexts agreed 10/10, while a
        // single reused context agreed 0/10 with itself across two runs.
        // temperature=0 also did not mean greedy; it meant "greedy, then N
        // stacked temp stages whose combined distortion grew with N".
        c->sampler = nullptr;

        c->available   = true;
        // Name the device that was ACTUALLY selected, not the one that was
        // requested. The old label derived from n_gpu_layers, so llama.cpp's
        // "offload every layer" sentinel (-1) was reported as CPU while every
        // layer sat on the GPU — a label that would have gone straight into a
        // benchmark table claiming the wrong hardware.
        c->backend_name = "llama.cpp+cpu";
#if defined(GS_LLAMA_HAVE_LLAMA)
        // Report the accelerator that actually exists on this machine, by
        // enumerating backends rather than by inspecting what was requested.
        //
        // The label used to be derived from n_gpu_layers, so llama.cpp's
        // "offload every layer" sentinel (-1) printed "cpu" while every layer
        // was on the GPU. A benchmark table that names the wrong hardware is
        // worse than one that names none, and llama_model_desc does not carry
        // the device in current llama.h.
        {
            const size_t n = ggml_backend_dev_count();
            for (size_t i = 0; i < n; ++i) {
                ggml_backend_dev_t dev = ggml_backend_dev_get(i);
                if (!dev) continue;
                const enum ggml_backend_dev_type t = ggml_backend_dev_type(dev);
                if (t != GGML_BACKEND_DEVICE_TYPE_GPU && t != GGML_BACKEND_DEVICE_TYPE_IGPU) continue;
                const char *name = ggml_backend_dev_name(dev);
                std::string label = "llama.cpp+gpu";
                if (name) {
                    const std::string nm(name);
                    if (nm.find("Vulkan") != std::string::npos)      label = "llama.cpp+vulkan";
                    else if (nm.find("CUDA") != std::string::npos)   label = "llama.cpp+cuda";
                    else if (nm.find("ROCm") != std::string::npos)    label = "llama.cpp+rocm";
                    else if (nm.find("Metal") != std::string::npos)   label = "llama.cpp+metal";
                    else if (nm.find("SYCL") != std::string::npos)    label = "llama.cpp+sycl";
                }
                c->backend_name = label;
                c->device_name  = name ? name : "gpu";
                break;
            }
        }
#endif
    }
#else
    c->available    = false;
    c->backend_name = "llama.cpp:not-compiled";
    set_err("libllama not linked: rebuild with GS_LLAMA_HAVE_LLAMA");
#endif

    return c;
}

llama_result_t gs_llama_generate_opts(gs_llama_context_t* ctx,
                              const char* prompt,
                              int32_t max_tokens,
                              float temperature,
                              int32_t clear_cache,
                              int32_t start_pos) {
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

        // Clear the KV cache before prefill. Without this the cache still
        // holds the previous call's positions while this call's batch starts
        // writing at position 0 again, llama_decode fails, and the symptom is
        // "the first sample works and every later sample errors with -5".
        // Each call is an independent completion: a benchmark samples the same
        // model 100 times and must not accumulate the previous question.
        if (clear_cache) {
            // Skipped only when a snapshot was just restored. Clearing here
            // would discard the restored prefix and silently re-prefill it,
            // which makes prefix reuse a no-op that still looks like it ran.
            llama_memory_clear(llama_get_memory(ctx->ctx), /*data=*/true);
        }

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

        // Prefill must be CHUNKED to the context's n_batch.
        //
        // Feeding the whole prompt as one batch trips
        //   GGML_ASSERT(n_tokens_all <= cparams.n_batch) failed
        // inside llama-context.cpp, which is a GGML_ASSERT: it aborts the
        // process outright, so the caller never sees an error. A benchmark
        // whose prompt grows with its scaffold preamble therefore dies on the
        // long questions and silently produces no data for that arm.
        const int32_t n_batch = llama_n_batch(ctx->ctx);
        const int32_t chunk   = (n_batch > 0 ? n_batch : 512);
        const int32_t n_prompt = (int32_t)toks.size();
        // Where this batch begins. After a restore the cache is already
        // occupied, so the batch has to continue from there.
        int32_t base_pos = start_pos;
        if (base_pos < 0) {
#if defined(GS_LLAMA_HAVE_LLAMA)
            base_pos = (int32_t)llama_memory_seq_pos_max(llama_get_memory(ctx->ctx), 0) + 1;
#else
            base_pos = 0;
#endif
        }
        if ((int64_t)base_pos + n_prompt + 1 > (int64_t)llama_n_ctx(ctx->ctx)) {
            r.status = GS_ERR_INVALID_ARG;
            set_err("prompt of " + std::to_string(n_prompt) + " tokens at position "
                    + std::to_string(base_pos) + " does not fit a context of "
                    + std::to_string(llama_n_ctx(ctx->ctx)) + "; raise the context or shorten the prompt");
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
        const int32_t cap = (n_prompt < chunk ? n_prompt : chunk) + 1;
        llama_batch batch = llama_batch_init(cap, 0, 1);
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
        for (int32_t base = 0; base < n_prompt; base += chunk) {
            const int32_t n = (n_prompt - base < chunk ? n_prompt - base : chunk);
            for (int32_t i = 0; i < n; ++i) {
                batch.token[i]    = toks[(size_t)base + (size_t)i];
                batch.pos[i]      = (llama_pos)(base_pos + base + i);
                batch.n_seq_id[i] = 1;
                batch.seq_id[i][0]= 0;    // single sequence 0
                batch.n_tokens    = n;
                // Logits are needed only after the final prompt token.
                batch.logits[i]   = (base + n == n_prompt) && (i + 1 == n);
            }
            if (llama_decode(ctx->ctx, batch) != 0) {
                llama_batch_free(batch);
                r.status = GS_ERR_GENERATION;
                set_err("llama_decode failed on prompt at offset " + std::to_string(base));
                return r;
            }
        }

        // Build a fresh chain for this call and tear it down at the end.
        // temperature <= 0 means GREEDY, which is what every caller passing 0.0
        // assumed and what losslessness checks require. A non-zero temperature
        // gets the truncated chain plus a fixed-seed dist stage, so a run is
        // reproducible from a clean context without depending on how many
        // requests preceded it.
        if (ctx->sampler) { llama_sampler_free(ctx->sampler); ctx->sampler = nullptr; }
        {
            llama_sampler_chain_params sp = llama_sampler_chain_default_params();
            sp.no_perf = true;
            ctx->sampler = llama_sampler_chain_init(sp);
            if (!ctx->sampler) {
                r.status = GS_ERR_NO_MEMORY;
                set_err("sampler chain init failed");
                return r;
            }
            if (temperature <= 0.0f) {
                llama_sampler_chain_add(ctx->sampler, llama_sampler_init_greedy());
            } else {
                llama_sampler_chain_add(ctx->sampler, llama_sampler_init_top_k(40));
                llama_sampler_chain_add(ctx->sampler, llama_sampler_init_top_p(0.95f, 1));
                llama_sampler_chain_add(ctx->sampler, llama_sampler_init_temp(temperature));
                // Fixed seed: reproducible within a clean context, and now also
                // independent of how many requests came before this one.
                llama_sampler_chain_add(ctx->sampler, llama_sampler_init_dist(1234));
            }
        }

        // RAII, so every exit path below frees the chain. Freeing it by hand at
        // each return is exactly how the leak-one-sampler-per-call bug survived
        // this long.
        struct SamplerGuard {
            llama_sampler*& slot;
            ~SamplerGuard() {
                if (slot) { llama_sampler_free(slot); slot = nullptr; }
            }
        } sampler_guard{ctx->sampler};

        std::string out;
        std::vector<llama_token> emitted;
        emitted.reserve((size_t)max_tokens);
        int n_cur = base_pos + (int)toks.size();
        int32_t n_generated = 0;   // counted here, not derived from the text
        for (int32_t i = 0; i < max_tokens; ++i) {
            const llama_token next = llama_sampler_sample(ctx->sampler, ctx->ctx, -1);
            if (llama_vocab_is_eog(ctx->vocab, next)) break;
            ++n_generated;

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
        // Tokens actually sampled. This was out.size(), i.e. a BYTE count,
        // which made a 64-token generation report 359 and turned tokens/sec
        // into a number about bytes per second.
        r.n_tokens = n_generated;
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

llama_result_t gs_llama_generate(gs_llama_context_t* ctx,
                                  const char* prompt,
                                  int32_t max_tokens,
                                  float temperature) {
    return gs_llama_generate_opts(ctx, prompt, max_tokens, temperature, /*clear_cache=*/1, /*start_pos=*/0);
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

const char* gs_llama_backend_name(gs_llama_context_t* ctx) {
    if (!ctx) return "none";
    return ctx->backend_name.c_str();  // borrowed static-ish string
}

int32_t gs_llama_n_layer(gs_llama_context_t* ctx) {
    if (!ctx) return 0;
#if defined(GS_LLAMA_HAVE_LLAMA)
    if (!ctx->model) return 0;
    return (int32_t)llama_model_n_layer(ctx->model);
#else
    return 0;
#endif
}

int32_t gs_llama_gpu_offload_supported(gs_llama_context_t* ctx) {
    (void)ctx;
#if defined(GS_LLAMA_HAVE_LLAMA)
    return llama_supports_gpu_offload() ? 1 : 0;
#else
    return 0;
#endif
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

// ---------------------------------------------------------------------------
// Speculative decoding
// ---------------------------------------------------------------------------
//
// Losslessness argument: with the greedy sampler we emit only tokens the main
// model itself chose. If the draft agrees with the main model for the first k
// positions, those k tokens are exactly what greedy decoding would have
// produced. At the first disagreement we emit the main model's own choice and
// discard the rest of the draft, so the main model is never asked to continue
// from a token it did not select.
//
// The win is arithmetic, not magical: k draft tokens cost k sequential decodes
// of a small model, and verifying them costs one batched decode of the large
// model. When acceptance is high, that is fewer large-model steps.

#if defined(GS_LLAMA_HAVE_LLAMA)
// Prefill `toks` starting at `start`, chunked. Returns positions written.
static int spec_prefill_at(gs_llama_ctx* ctx, const std::vector<llama_token>& toks,
                           int32_t start, std::string* err) {
    const int32_t n = (int32_t)toks.size();
    if (n <= 0) return 0;
    const int32_t chunk = llama_n_batch(ctx->ctx) > 0 ? llama_n_batch(ctx->ctx) : 512;
    llama_batch batch = llama_batch_init(n < chunk ? n : chunk, 0, 1);
    if (!batch.token) { if (err) *err = "batch init failed"; return -1; }
    for (int32_t base = 0; base < n; base += chunk) {
        const int32_t m = (n - base < chunk) ? (n - base) : chunk;
        for (int32_t i = 0; i < m; ++i) {
            batch.token[i]    = toks[(size_t)(base + i)];
            batch.pos[i]      = (llama_pos)(start + base + i);
            batch.n_seq_id[i] = 1;
            batch.seq_id[i][0]= 0;
            batch.n_tokens    = m;
            batch.logits[i]   = (base + m == n) && (i + 1 == m);
        }
        if (llama_decode(ctx->ctx, batch) != 0) {
            if (err) *err = "decode failed at offset " + std::to_string(base);
            llama_batch_free(batch);
            return -1;
        }
    }
    llama_batch_free(batch);
    return n;
}

// Prefill `toks` into ctx, chunked. Returns the number of positions written.
static int spec_prefill(gs_llama_ctx* ctx, const std::vector<llama_token>& toks, std::string* err) {
    const int32_t n = (int32_t)toks.size();
    if (n <= 0) return 0;
    const int32_t chunk = llama_n_batch(ctx->ctx) > 0 ? llama_n_batch(ctx->ctx) : 512;
    llama_batch batch = llama_batch_init(n < chunk ? n : chunk, 0, 1);
    if (!batch.token) { if (err) *err = "batch init failed"; return -1; }
    for (int32_t base = 0; base < n; base += chunk) {
        const int32_t m = (n - base < chunk) ? (n - base) : chunk;
        for (int32_t i = 0; i < m; ++i) {
            batch.token[i]    = toks[(size_t)(base + i)];
            batch.pos[i]      = (llama_pos)(base + i);
            batch.n_seq_id[i] = 1;
            batch.seq_id[i][0]= 0;
            batch.n_tokens    = m;
            batch.logits[i]   = (base + m == n) && (i + 1 == m);
        }
        if (llama_decode(ctx->ctx, batch) != 0) {
            if (err) *err = "decode failed at offset " + std::to_string(base);
            llama_batch_free(batch);
            return -1;
        }
    }
    llama_batch_free(batch);
    return n;
}
#endif


// ---------------------------------------------------------------------------
// KV state serialisation and prefix reuse
// ---------------------------------------------------------------------------

#if defined(GS_LLAMA_HAVE_LLAMA)
// Prefill `toks` starting at `start`, chunked to n_batch.
static int gs_prefill_at(gs_llama_ctx* ctx, const std::vector<llama_token>& toks,
                         int32_t start, std::string* err) {
    const int32_t n = (int32_t)toks.size();
    if (n <= 0) return 0;
    const int32_t chunk = llama_n_batch(ctx->ctx) > 0 ? llama_n_batch(ctx->ctx) : 512;
    llama_batch batch = llama_batch_init(n < chunk ? n : chunk, 0, 1);
    if (!batch.token) { if (err) *err = "batch init failed"; return -1; }
    for (int32_t base = 0; base < n; base += chunk) {
        const int32_t m = (n - base < chunk) ? (n - base) : chunk;
        for (int32_t i = 0; i < m; ++i) {
            batch.token[i]    = toks[(size_t)(base + i)];
            batch.pos[i]      = (llama_pos)(start + base + i);
            batch.n_seq_id[i] = 1;
            batch.seq_id[i][0]= 0;
            batch.n_tokens    = m;
            batch.logits[i]   = (base + m == n) && (i + 1 == m);
        }
        if (llama_decode(ctx->ctx, batch) != 0) {
            if (err) *err = "decode failed at offset " + std::to_string(base);
            llama_batch_free(batch);
            return -1;
        }
    }
    llama_batch_free(batch);
    return n;
}
#endif

extern "C" {

int32_t gs_llama_n_ctx(gs_llama_context_t* ctx) {
    if (!ctx) return 0;
#if defined(GS_LLAMA_HAVE_LLAMA)
    return ctx->ctx ? (int32_t)llama_n_ctx(ctx->ctx) : 0;
#else
    return 0;
#endif
}

int32_t gs_llama_prefill(gs_llama_context_t* ctx, const char* prompt, int32_t clear_cache) {
    if (!ctx || !prompt) return GS_ERR_INVALID_ARG;
    if (!ctx->available) return GS_ERR_UNAVAILABLE;
#if defined(GS_LLAMA_HAVE_LLAMA)
    try {
        if (clear_cache) llama_memory_clear(llama_get_memory(ctx->ctx), true);
        const std::string p(prompt);
        const int32_t n = -llama_tokenize(ctx->vocab, p.c_str(), (int32_t)p.size(), nullptr, 0, true, false);
        if (n <= 0) return GS_ERR_INVALID_ARG;
        std::vector<llama_token> toks((size_t)n);
        if (llama_tokenize(ctx->vocab, p.c_str(), (int32_t)p.size(), toks.data(), n, true, false) != n)
            return GS_ERR_GENERATION;
        const int32_t base = (int32_t)llama_memory_seq_pos_max(llama_get_memory(ctx->ctx), 0) + 1;
        if ((int64_t)base + n + 1 > (int64_t)llama_n_ctx(ctx->ctx)) {
            set_err("prefill of " + std::to_string(n) + " tokens at position " + std::to_string(base)
                    + " does not fit a context of " + std::to_string(llama_n_ctx(ctx->ctx)));
            return GS_ERR_INVALID_ARG;
        }
        std::string err;
        if (gs_prefill_at(ctx, toks, base, &err) < 0) { set_err("prefill: " + err); return GS_ERR_GENERATION; }
        return GS_OK;
    } catch (const std::exception& e) {
        set_err(std::string("prefill exception: ") + e.what());
        return GS_ERR_INTERNAL;
    }
#else
    (void)prompt; (void)clear_cache;
    return GS_ERR_UNAVAILABLE;
#endif
}

int64_t gs_llama_state_size(gs_llama_context_t* ctx) {
    if (!ctx) return 0;
#if defined(GS_LLAMA_HAVE_LLAMA)
    return ctx->ctx ? (int64_t)llama_state_get_size(ctx->ctx) : 0;
#else
    return 0;
#endif
}

int64_t gs_llama_state_save(gs_llama_context_t* ctx, uint8_t* dest, int64_t cap) {
    if (!ctx || !dest || cap <= 0) return 0;
#if defined(GS_LLAMA_HAVE_LLAMA)
    if (!ctx->ctx) return 0;
    const size_t n = llama_state_get_data(ctx->ctx, dest, (size_t)cap);
    return n > 0 ? (int64_t)n : 0;
#else
    (void)dest; (void)cap;
    return 0;
#endif
}

int64_t gs_llama_state_restore(gs_llama_context_t* ctx, const uint8_t* src, int64_t len) {
    if (!ctx || !src || len <= 0) return 0;
#if defined(GS_LLAMA_HAVE_LLAMA)
    if (!ctx->ctx) return 0;
    const size_t n = llama_state_set_data(ctx->ctx, src, (size_t)len);
    return n > 0 ? (int64_t)n : 0;
#else
    (void)src; (void)len;
    return 0;
#endif
}

void gs_llama_state_free(uint8_t* buf) { free(buf); }

}  // extern "C"
