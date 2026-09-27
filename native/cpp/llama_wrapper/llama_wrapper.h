/* llama_wrapper.h — GS AI C ABI over llama.cpp.
 *
 * Ownership model (dossier rule 4):
 *   - `model_path` and every `const char*` INPUT are borrowed for the duration
 *     of the call only.
 *   - llama_result_t.text is malloc'd and owned by the CALLER.
 *   - Handles come from gs_llama_create and go to gs_llama_free. Nothing else.
 *
 * No C++ exception may cross this boundary; gs_llama_generate catches internally
 * and reports GS_ERR_INTERNAL with the message retrievable via gs_last_error().
 *
 * Compiles standalone:
 *   g++ -c -std=c++17 -I cpp/include -I <llama.cpp>/include llama_wrapper.cpp
 */
#ifndef GS_LLAMA_WRAPPER_H
#define GS_LLAMA_WRAPPER_H

#include "gs_abi.h"

#ifdef __cplusplus
extern "C" {
#endif

/* vtable-by-VALUE contract: the FIRST member of the struct is the vtable
 * itself, not a pointer to it. Recover with
 *   reinterpret_cast<gs_llm_service_vtable_t*>(handle)
 * A pointer-vs-value mismatch here segfaults at a small offset (0x11 was
 * observed). This is pinned by tests/integration/test_abi_contract.c. */
typedef struct gs_llm_service_vtable_s {
    int32_t  (*generate)(void* self, const char* prompt, int32_t max_tokens,
                         float temperature, char** out_text);
    int32_t  (*token_count)(void* self, const char* text);
    void     (*destroy)(void* self);
    /* 1 when the backend actually initialised. 0 means every call will report
     * GS_ERR_UNAVAILABLE — never a placeholder success. */
    int32_t  (*available)(void* self);
} gs_llm_service_vtable_t;

/* KV cache element types, mirroring ggml_type. Named rather than passed as a
 * raw int so an ABI caller cannot set a type the build does not support. */
typedef enum {
    GS_KV_F16  = 1,   /* exact, the default */
    GS_KV_Q8_0 = 8,
    GS_KV_Q5_1 = 7,
    GS_KV_Q5_0 = 6,
    GS_KV_Q4_1 = 3,
    GS_KV_Q4_0 = 2,   /* the aggressive 4-bit option */
    GS_KV_IQ4_NL = 20
} gs_kv_type_t;

typedef struct {
    const char* model_path;   /* borrowed */
    int32_t     n_ctx;
    int32_t     n_threads;
    int32_t     n_gpu_layers; /* <0 = every layer. 0 = CPU only. */
    int32_t     use_mmap;     /* nonzero -> mmap weights */
    int32_t     cache_type_k; /* gs_kv_type_t. F16 unless asked otherwise. */
    int32_t     cache_type_v; /* gs_kv_type_t. F16 unless asked otherwise. */
} llama_config_t;

typedef struct {
    char*   text;             /* OWNED by caller -> gs_llama_free_result_text */
    int32_t n_tokens;
    int32_t status;           /* gs_status_t */
} llama_result_t;

typedef struct gs_llama_ctx gs_llama_context_t;

/* Last-error accessor is shared: gs_last_error() from gs_abi.h. */

/* Validates config without loading a model. Returns GS_OK or a gs_status_t. */
int32_t gs_llama_validate_config(const llama_config_t* config);

/* Creates a context. Returns NULL on failure; call gs_last_error() for the
 * reason. Deliberately returns NULL rather than a partially-initialised
 * handle so callers cannot use a half-built context. */
gs_llama_context_t* gs_llama_create(const llama_config_t* config);

/* Generates up to max_tokens. On GS_OK, result->text is caller-owned.
 * On any error, result->text is NULL and result->status carries the code. */
/* Speculative decoding. This build of llama.cpp exposes no speculative API,
 * so draft-and-verify is implemented here.
 *
 * A draft context is any second gs_llama_context_t built from a SMALLER model.
 * For each step the draft proposes n_draft tokens autoregressively; the main
 * model then decodes all of them in ONE batched llama_decode and we keep the
 * longest prefix on which its own greedy choice agrees, discarding the rest.
 *
 * Under the greedy sampler that makes the output byte-identical to
 * non-speculative decoding, which is the property that makes this lossless
 * rather than an approximation. Sampling above 0 is NOT covered: exact
 * losslessness there needs the modified rejection sampler, and this call
 * refuses to pretend otherwise.
 */
typedef struct {
    int32_t drafted;     /* tokens the draft proposed */
    int32_t accepted;    /* of those, confirmed by the main model */
    int32_t generated;   /* tokens emitted in total */
    double  accept_rate; /* accepted/drafted, or 1.0 when nothing was drafted */
    /* How many times the KV cache had to be rebuilt from the token list. A
     * batched verify cannot be partially rolled back, so every disagreement
     * costs one re-prefill. This is the dominant cost and must be reported. */
    int32_t resyncs;
} gs_spec_stats_t;

llama_result_t gs_llama_generate_speculative(gs_llama_context_t* ctx,
                                             gs_llama_context_t* draft,
                                             const char* prompt,
                                             int32_t max_tokens,
                                             float temperature,
                                             int32_t n_draft,
                                             gs_spec_stats_t* out_stats);

llama_result_t gs_llama_generate(gs_llama_context_t* ctx,
                              const char* prompt,
                              int32_t max_tokens,
                              float temperature);

/* Releases result->text. Safe to call with NULL. */
void gs_llama_free_result_text(llama_result_t* result);

/* Token count for text, or negative on error. */
int32_t gs_llama_token_count(gs_llama_context_t* ctx, const char* text);

/* 1 if the backing backend initialised and generate can work. */
int32_t gs_llama_available(gs_llama_context_t* ctx);

/* Speculative decoding support. llama-create must be called with a draft model
 * path for this to report 1. */
int32_t gs_llama_set_draft(gs_llama_context_t* ctx, const llama_config_t* draft);
int32_t gs_llama_has_draft(gs_llama_context_t* ctx, float* acceptance_rate);

/* Self-reported backend name, borrowed static string. Never NULL. */
const char* gs_llama_backend_name(gs_llama_context_t* ctx);

/* 1 when this build has a working accelerator offload path (Vulkan, CUDA,
 * SYCL, ...), 0 for a CPU-only build. A caller that requires GPU execution must
 * check this at load and refuse to run rather than silently degrading to the
 * host: that is how a "GPU" run becomes a CPU run that still reports success.
 *
 * This build of llama.cpp exposes no per-model offloaded-layer count through a
 * stable C API, so the authoritative check that layers really landed on the
 * device is llama.cpp's own load report ("offloaded N/M layers to GPU"), which
 * the caller reads from the log. This function answers the prior question:
 * is offload even possible here. */
int32_t gs_llama_gpu_offload_supported(gs_llama_context_t* ctx);

/* Layer count, so a demo can report cache footprint per layer. The KV cache
 * bytes themselves are NOT read from here: llama.cpp exposes no stable byte
 * count for them, so the demos measure real process RSS (VmHWM) instead of
 * reporting a number derived from the requested type. */
int32_t gs_llama_n_layer(gs_llama_context_t* ctx);

/* Context size actually in force, as opposed to the one requested. */
int32_t gs_llama_n_ctx(gs_llama_context_t* ctx);

/* Destroys the context. NULL-safe. Context must outlive all borrowed strings. */
void gs_llama_free(gs_llama_context_t* ctx);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* GS_LLAMA_WRAPPER_H */
