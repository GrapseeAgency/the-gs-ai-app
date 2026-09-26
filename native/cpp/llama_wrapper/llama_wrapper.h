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

typedef struct {
    const char* model_path;   /* borrowed */
    int32_t     n_ctx;
    int32_t     n_threads;
    int32_t     n_gpu_layers; /* 0 = CPU only. This is the guaranteed path. */
    int32_t     use_mmap;     /* nonzero -> mmap weights */
} llama_config_t;

typedef struct {
    char*   text;             /* OWNED by caller -> gs_llama_free_result_text */
    int32_t n_tokens;
    int32_t status;           /* gs_status_t */
} llama_result_t;

typedef struct llama_context gs_llama_context_t;

/* Last-error accessor is shared: gs_last_error() from gs_abi.h. */

/* Validates config without loading a model. Returns GS_OK or a gs_status_t. */
int32_t gs_llama_validate_config(const llama_config_t* config);

/* Creates a context. Returns NULL on failure; call gs_last_error() for the
 * reason. Deliberately returns NULL rather than a partially-initialised
 * handle so callers cannot use a half-built context. */
gs_llama_context_t* gs_llama_create(const llama_config_t* config);

/* Generates up to max_tokens. On GS_OK, result->text is caller-owned.
 * On any error, result->text is NULL and result->status carries the code. */
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

/* Destroys the context. NULL-safe. Context must outlive all borrowed strings. */
void gs_llama_free(gs_llama_context_t* ctx);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* GS_LLAMA_WRAPPER_H */
