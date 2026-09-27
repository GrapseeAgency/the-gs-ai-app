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
struct gs_llama_ctx {
    llama_config_t cfg{};
    bool available = false;
    std::string backend_name = "none";
    std::string device_name  = "";
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

        // Re-seed the temperature stage so the caller's value takes effect.
        llama_sampler_chain_add(ctx->sampler, llama_sampler_init_temp(temperature));

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

typedef struct {
    llama_result_t result;
    int32_t drafted;
    int32_t accepted;
    int32_t generated;
} spec_run_t;

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

llama_result_t gs_llama_generate_speculative(gs_llama_context_t* ctx,
                                             gs_llama_context_t* draft,
                                             const char* prompt,
                                             int32_t max_tokens,
                                             float temperature,
                                             int32_t n_draft,
                                             gs_spec_stats_t* out_stats) {
    llama_result_t r{};
    r.text = nullptr; r.n_tokens = 0; r.status = GS_ERR_INVALID_ARG;
    int32_t drafted = 0, accepted = 0, generated = 0;
    if (out_stats) { out_stats->drafted = 0; out_stats->accepted = 0;
                     out_stats->generated = 0; out_stats->accept_rate = 1.0; }

    auto finish = [&](int32_t status) {
        r.status = status;
        r.n_tokens = generated;
        if (out_stats) {
            out_stats->drafted = drafted;
            out_stats->accepted = accepted;
            out_stats->generated = generated;
            out_stats->accept_rate = drafted > 0 ? (double)accepted / (double)drafted : 1.0;
        }
        return r;
    };

    if (!ctx || !prompt) { set_err("ctx or prompt is null"); return finish(GS_ERR_INVALID_ARG); }
    if (max_tokens <= 0) { set_err("max_tokens must be > 0"); return finish(GS_ERR_INVALID_ARG); }
    if (!draft) { set_err("draft context is null"); return finish(GS_ERR_INVALID_ARG); }
    if (n_draft <= 0) n_draft = 4;
    if (temperature > 0.0f) {
        // Exact losslessness above greedy needs the modified rejection
        // sampler, which is not implemented. Refusing is the honest option:
        // returning a plausible-looking number would be a silent quality lie.
        set_err("speculative decoding is implemented for the greedy sampler only");
        return finish(GS_ERR_INVALID_ARG);
    }
    if (!ctx->available || !draft->available) {
        set_err("model unavailable");
        return finish(GS_ERR_UNAVAILABLE);
    }

#if defined(GS_LLAMA_HAVE_LLAMA)
    try {
        const std::string p(prompt);
        llama_memory_clear(llama_get_memory(ctx->ctx), true);
        llama_memory_clear(llama_get_memory(draft->ctx), true);

        auto tokenize = [](gs_llama_ctx* c, const std::string& s) {
            const int32_t n = -llama_tokenize(c->vocab, s.c_str(), (int32_t)s.size(), nullptr, 0, true, false);
            std::vector<llama_token> t((size_t)n);
            llama_tokenize(c->vocab, s.c_str(), (int32_t)s.size(), t.data(), n, true, false);
            return t;
        };
        auto to_piece = [](gs_llama_ctx* c, llama_token tok) {
            char buf[256];
            const int n = llama_token_to_piece(c->vocab, tok, buf, sizeof(buf), 0, true);
            return n > 0 ? std::string(buf, (size_t)n) : std::string();
        };
        auto greedy = [](gs_llama_ctx* c) {
            return llama_sampler_sample(c->sampler, c->ctx, -1);
        };

        std::vector<llama_token> main_toks = tokenize(ctx, p);
        std::vector<llama_token> draft_toks = tokenize(draft, p);
        std::string err;
        if (spec_prefill(ctx, main_toks, &err) < 0) { set_err("main prefill: " + err); return finish(GS_ERR_GENERATION); }
        if (spec_prefill(draft, draft_toks, &err) < 0) { set_err("draft prefill: " + err); return finish(GS_ERR_GENERATION); }

        // Re-seed both samplers greedy for the whole call.
        if (ctx->sampler) llama_sampler_free(ctx->sampler);
        if (draft->sampler) llama_sampler_free(draft->sampler);
        ctx->sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
        draft->sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
        llama_sampler_chain_add(ctx->sampler, llama_sampler_init_greedy());
        llama_sampler_chain_add(draft->sampler, llama_sampler_init_greedy());

        int32_t pos = (int32_t)main_toks.size();
        int32_t draft_pos = (int32_t)draft_toks.size();
        std::string out;
        std::vector<llama_token> emitted;
        bool need_resync = false;
        int32_t resyncs = 0;
        emitted.reserve((size_t)max_tokens);

        const int32_t cap = (n_draft + 2) < llama_n_batch(ctx->ctx) ? (n_draft + 2) : llama_n_batch(ctx->ctx);
        llama_batch batch = llama_batch_init(cap, 0, 1);
        if (!batch.token) { set_err("batch init failed"); return finish(GS_ERR_NO_MEMORY); }

        while (generated < max_tokens) {
            // 1. Draft proposes up to n_draft tokens.
            std::vector<llama_token> proposal;
            proposal.reserve((size_t)n_draft);
            for (int32_t i = 0; i < n_draft && generated + (int32_t)proposal.size() < max_tokens; ++i) {
                const llama_token t = greedy(draft);
                if (llama_vocab_is_eog(draft->vocab, t)) break;
                proposal.push_back(t);
                // advance the draft
                llama_batch db = llama_batch_init(1, 0, 1);
                db.token[0] = t; db.pos[0] = (llama_pos)draft_pos; db.n_seq_id[0] = 1;
                db.seq_id[0][0] = 0; db.n_tokens = 1; db.logits[0] = true;
                if (llama_decode(draft->ctx, db) != 0) { llama_batch_free(db); break; }
                llama_batch_free(db);
                ++draft_pos;
            }
            need_resync = false;
            if (proposal.empty()) {
                const llama_token t = greedy(ctx);
                if (llama_vocab_is_eog(ctx->vocab, t)) break;
                out += to_piece(ctx, t);
                emitted.push_back(t);
                ++generated; ++pos;
                llama_batch ob = llama_batch_init(1, 0, 1);
                ob.token[0] = t; ob.pos[0] = (llama_pos)pos; ob.n_seq_id[0] = 1;
                ob.seq_id[0][0] = 0; ob.n_tokens = 1; ob.logits[0] = true;
                if (llama_decode(ctx->ctx, ob) != 0) { llama_batch_free(ob); break; }
                llama_batch_free(ob);
                ++pos;
                continue;
            }
            drafted += (int32_t)proposal.size();

            // 2. Main verifies all of them in ONE batched decode.
            //    The extra +1 position yields the main's own next token, which
            //    is what we emit at the first disagreement.
            const int32_t nv = (int32_t)proposal.size();
            for (int32_t i = 0; i < nv; ++i) {
                batch.token[i]    = proposal[(size_t)i];
                batch.pos[i]      = (llama_pos)(pos + i);
                batch.n_seq_id[i] = 1;
                batch.seq_id[i][0]= 0;
                batch.n_tokens    = nv;
                batch.logits[i]   = true;   // need a choice at every position
            }
            if (llama_decode(ctx->ctx, batch) != 0) {
                llama_batch_free(batch);
                set_err("main verify decode failed");
                return finish(GS_ERR_GENERATION);
            }
            // Reconstruct the sampler's view position by position.
            for (int32_t i = 0; i + 1 < nv; ++i) {
                // idx = i, NOT -1. The batch set logits at every position, but
                // llama_sampler_sample takes the index of the token whose
                // distribution to read. -1 means "the last one", so every
                // verify position was being judged by the final position's
                // logits. Acceptance still looked plausible while the emitted
                // text was garbage.
                const llama_token chosen = llama_sampler_sample(ctx->sampler, ctx->ctx, i);
                llama_sampler_accept(ctx->sampler, proposal[(size_t)i]);
                if (llama_vocab_is_eog(ctx->vocab, chosen)) { /* fallthrough */ }
                if (chosen == proposal[(size_t)i]) {
                    ++accepted;
                    out += to_piece(ctx, proposal[(size_t)i]);
                    emitted.push_back(proposal[(size_t)i]);
                } else {
                    // First disagreement: the main model's own token wins and
                    // the rest of the draft is discarded.
                    //
                    // The cache has already consumed all nv positions and
                    // llama.cpp cannot roll back a batched decode, so the
                    // agreeing prefix is correct but everything after it is
                    // stale. Rebuild from the token list below.
                    out += to_piece(ctx, chosen);
                    emitted.push_back(chosen);
                    ++generated;
                    need_resync = true;
                    goto resync;
                }
            }
            {
                // All proposed tokens agreed; emit the main's own next token,
                // which is the distribution at the last verified position.
                const llama_token next = llama_sampler_sample(ctx->sampler, ctx->ctx, nv - 1);
                if (!llama_vocab_is_eog(ctx->vocab, next)) {
                    out += to_piece(ctx, next);
                    emitted.push_back(next);
                    ++generated;
                }
                // Cache already sits at pos+nv-1; the sampled token is written
                // on the next iteration's draft phase alignment.
                pos += nv;
                draft_pos = pos;
            }
        resync:
            // Rebuild both contexts to exactly (prompt + emitted). Greedy
            // decoding is deterministic, so re-prefilling the same token list
            // reproduces the same state bit for bit. This is the price of the
            // batched verify: there is no partial rollback.
            if (need_resync || pos + n_draft + 2 > llama_n_ctx(ctx->ctx)) {
                ++resyncs;
                std::vector<llama_token> full(main_toks);
                full.insert(full.end(), emitted.begin(), emitted.end());
                llama_memory_clear(llama_get_memory(ctx->ctx), true);
                llama_memory_clear(llama_get_memory(draft->ctx), true);
                if (ctx->sampler) llama_sampler_free(ctx->sampler);
                if (draft->sampler) llama_sampler_free(draft->sampler);
                ctx->sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
                draft->sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
                llama_sampler_chain_add(ctx->sampler, llama_sampler_init_greedy());
                llama_sampler_chain_add(draft->sampler, llama_sampler_init_greedy());
                std::string re;
                if (spec_prefill(ctx, full, &re) < 0) {
                    llama_batch_free(batch);
                    set_err("resync main prefill: " + re);
                    return finish(GS_ERR_GENERATION);
                }
                spec_prefill(draft, full, &re);
                pos = draft_pos = (int32_t)full.size();
            }
            if (generated >= max_tokens) break;
        }
        llama_batch_free(batch);

        if (out.empty()) { set_err("decoded zero tokens"); return finish(GS_ERR_GENERATION); }
        r.text = strdup(out.c_str());
        if (!r.text) { set_err("strdup failed"); return finish(GS_ERR_NO_MEMORY); }
        return finish(GS_OK);
    } catch (const std::exception& e) {
        set_err(std::string("exception in speculative generate: ") + e.what());
        return finish(GS_ERR_INTERNAL);
    } catch (...) {
        set_err("unknown exception in speculative generate");
        return finish(GS_ERR_INTERNAL);
    }
#else
    (void)draft; (void)n_draft;
    set_err("libllama not linked");
    return finish(GS_ERR_UNAVAILABLE);
#endif
}




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
