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

typedef struct gs_llama_ctx gs_llama_context_t;
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
    int32_t     n_seq_max;    /* concurrent sequences. 0 or 1 = no batching. */

    /* THE MEASURED LEVERS. All three were HARDCODED or UNSET before, which is why
     * they could not be measured: cp.n_batch and cp.n_ubatch were the literals
     * 512 and 512, and cp.flash_attn was never assigned at all.
     *
     * 0 OR NEGATIVE MEANS "the value this build already used", not "zero":
     *
     *   n_batch    0 -> 512, the literal that was there
     *   n_ubatch   0 -> 512, the literal that was there
     *   flash_attn 0 -> off, which is what llama_context_default_params() gives
     *
     * so a caller that passes none of them gets byte-identical behaviour to a
     * caller that does not exist. That matters more than it sounds: a config
     * default of 0 for n_batch would mean "process nothing", which is a different
     * bug rather than a different default.
     *
     * n_batch and n_ubatch are SEPARATE on purpose. n_batch is how many tokens
     * are handed to llama_decode at once, which is the prompt-processing
     * throughput knob and therefore the TTFT knob for a long prompt. n_ubatch is
     * the micro-batch, which bounds the peak compute of a single graph. llama.cpp
     * requires n_ubatch <= n_batch.
     */
    int32_t     n_batch;
    int32_t     n_ubatch;

    /* Flash attention. This is an ENUM in llama_context_params, not a bool, and
     * the mapping is the upstream one rather than a truthiness test:
     *
     *     LLAMA_FLASH_ATTN_TYPE_AUTO     = -1
     *     LLAMA_FLASH_ATTN_TYPE_DISABLED =  0
     *     LLAMA_FLASH_ATTN_TYPE_ENABLED  =  1
     *
     * AUTO is what llama_context_default_params() carries, so a negative value
     * means "leave it on the library default" and preserves the behaviour this
     * build had before the field was ever assigned. */
    int32_t     flash_attn;

    /* THREADS FOR PROMPT PROCESSING, which is a DIFFERENT FIELD from n_threads.
     * n_threads is generation; n_threads_batch is batch processing, and batch
     * processing is what the time-to-first-token is made of.
     *
     * THE SENTINEL IS ASYMMETRIC WITH THE OTHER THREE, DELIBERATELY:
     *
     *     >= 0  set it to exactly this
     *     <  0   leave llama_context_default_params()'s value alone
     *
     * A 0 here is not "off", it is "the library default", so there is no 0-means-
     * off reading to confuse it with -- and forcing 0 would ask llama.cpp for
     * zero batch threads. The other three fields use 0-means-default because
     * zero is not a plausible value for a size; 0 IS plausible-looking for a
     * thread count, which is exactly why it is not the sentinel here. */
    int32_t     n_threads_batch;
} llama_config_t;

} /* extern "C" -- reopened below */

#include <string>
#if defined(GS_LLAMA_HAVE_LLAMA)
#include <llama.h>
#endif

/* The context layout.
 *
 * Defined here, rather than kept opaque, so a second translation unit
 * (batch.cpp) can reach the fields without a duplicate declaration that could
 * drift. It is NOT the surface a host app uses: that is gs_abi.h plus, for
 * mobile, gs_mobile.h.
 */
struct gs_llama_ctx {
    llama_config_t cfg{};
    bool available = false;
    std::string backend_name = "none";
    std::string device_name  = "";
#if defined(GS_LLAMA_HAVE_LLAMA)
    llama_model*       model   = nullptr;
    llama_context*     ctx     = nullptr;
    const llama_vocab* vocab   = nullptr;
    llama_sampler*     sampler = nullptr;
#endif
};

extern "C" {

typedef struct {
    char*   text;             /* OWNED by caller -> gs_llama_free_result_text */
    int32_t n_tokens;
    int32_t status;           /* gs_status_t */
} llama_result_t;



/* Last-error accessor is shared: gs_last_error() from gs_abi.h. */

/* Validates config without loading a model. Returns GS_OK or a gs_status_t. */
int32_t gs_llama_validate_config(const llama_config_t* config);

/* Creates a context. Returns NULL on failure; call gs_last_error() for the
 * reason. Deliberately returns NULL rather than a partially-initialised
 * handle so callers cannot use a half-built context. */
gs_llama_context_t* gs_llama_create(const llama_config_t* config);

/* Batched decode: N independent prompts, ONE llama_decode per step.
 *
 * Each prompt takes its own seq_id so the KV cache keeps them apart. With a
 * greedy sampler every sequence's output is identical to decoding it alone.
 *
 * out_texts[i] is malloc'd and owned by the caller (free it). out_lens[i] is the
 * byte length, or -1 when that slot produced nothing, so "no answer" stays
 * distinguishable from "the answer was empty".
 *
 * n must not exceed the context's n_seq_max.
 */
/* Releases a string produced by gs_llama_batch_generate. The deallocator is
 * paired with the wrapper's own allocator rather than assumed to be the host's
 * free(), which is a real hazard across a C ABI. */
void gs_llama_free_text(char* s);

int32_t gs_llama_batch_generate(gs_llama_context_t* ctx,
                                const char* const* prompts,
                                int32_t n,
                                int32_t max_tokens,
                                float temperature,
                                char** out_texts,
                                int32_t* out_lens);

/* Generates up to max_tokens. On GS_OK, result->text is caller-owned.
 * On any error, result->text is NULL and result->status carries the code. */
/* Batched decode: N independent prompts, ONE llama_decode per step.
 *
 * Each prompt takes its own seq_id so the KV cache keeps them apart. With a
 * greedy sampler every sequence's output is identical to decoding it alone.
 *
 * out_texts[i] is malloc'd and owned by the caller (free it). out_lens[i] is the
 * byte length, or -1 when that slot produced nothing, so "no answer" stays
 * distinguishable from "the answer was empty".
 *
 * n must not exceed the context's n_seq_max.
 */
/* Releases a string produced by gs_llama_batch_generate. The deallocator is
 * paired with the wrapper's own allocator rather than assumed to be the host's
 * free(), which is a real hazard across a C ABI. */
void gs_llama_free_text(char* s);

int32_t gs_llama_batch_generate(gs_llama_context_t* ctx,
                                const char* const* prompts,
                                int32_t n,
                                int32_t max_tokens,
                                float temperature,
                                char** out_texts,
                                int32_t* out_lens);


llama_result_t gs_llama_generate(gs_llama_context_t* ctx,
                              const char* prompt,
                              int32_t max_tokens,
                              float temperature);

/* Chat, templated. `system` may be NULL or empty.
 *
 * This exists because the raw prompt was being handed straight to an INSTRUCT
 * model, which does not continue a conversation -- it continues a document. The
 * observed result on the emulator, from a passing test:
 *
 *     chat("hello") -> ", i have a question about the following code:"
 *
 * A prompt fragment. The model was completing the bare word "hello" in whatever
 * style the base weights remembered, because nothing ever told it a user was
 * speaking or that an assistant turn was expected.
 *
 * The template is read from the MODEL ITSELF (llama_model_chat_template) rather
 * than hardcoded to ChatML, so a different GGUF carries its own template and is
 * templated correctly without a code change here. add_ass=true, so the prompt
 * ends with the tokens that open an assistant turn and generation begins where
 * the answer does.
 */
llama_result_t gs_llama_chat(gs_llama_context_t* ctx,
                             const char* system,
                             const char* user,
                             int32_t max_tokens,
                             float temperature);

/* Releases result->text. Safe to call with NULL. */
void gs_llama_free_result_text(llama_result_t* result);

/* Token count for text, or negative on error. */
int32_t gs_llama_token_count(gs_llama_context_t* ctx, const char* text);

/* 1 if the backing backend initialised and generate can work. */
int32_t gs_llama_available(gs_llama_context_t* ctx);

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

/* KV state serialisation, for prefix reuse. save allocates a buffer the caller
 * owns and frees with gs_llama_state_free; restore reads one. Returns the byte
 * count written, or 0 on failure. */
int64_t gs_llama_state_save(gs_llama_context_t* ctx, uint8_t* dest, int64_t cap);
int64_t gs_llama_state_restore(gs_llama_context_t* ctx, const uint8_t* src, int64_t len);
int64_t gs_llama_state_size(gs_llama_context_t* ctx);
void    gs_llama_state_free(uint8_t* buf);

/* Destroys the context. NULL-safe. Context must outlive all borrowed strings. */
void gs_llama_free(gs_llama_context_t* ctx);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* GS_LLAMA_WRAPPER_H */
