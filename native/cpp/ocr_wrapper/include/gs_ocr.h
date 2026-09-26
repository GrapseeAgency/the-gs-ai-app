/* gs_ocr.h — C ABI for OCR. Every entry is extern "C", every entry catches.
 *
 * Ownership (dossier rule 4):
 *   - const char* and const unsigned char* inputs are BORROWED.
 *   - char* returned by gs_ocr_recognize is malloc'd and owned by the caller.
 *   - Handles come from gs_ocr_create, go to gs_ocr_free. Nothing else.
 *
 * No C++ exception may cross this boundary.
 */
#ifndef GS_OCR_H
#define GS_OCR_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    const char* det_model_dir;  /* borrowed; dir holding inference.pdiparams */
    const char* rec_model_dir;  /* borrowed */
    const char* label_path;     /* borrowed; may be NULL for the default dict */
    int32_t     use_gpu;
    int32_t     cpu_threads;
} gs_ocr_config_t;

typedef struct gs_ocr_ctx gs_ocr_ctx_t;

/* NULL on failure. gs_ocr_last_error() carries the reason. */
gs_ocr_ctx_t* gs_ocr_create(const gs_ocr_config_t* config);

/* Recognise text in a raw image buffer.
 * Returns an OWNED malloc'd UTF-8 string, or NULL on failure.
 * Release with gs_ocr_free_string. An empty string is a VALID result meaning
 * "no text found" — it is never used to signal an error. */
char* gs_ocr_recognize(gs_ocr_ctx_t* ctx,
                       const unsigned char* img,
                       int32_t w, int32_t h, int32_t channels);

void gs_ocr_free_string(char* s);
void gs_ocr_free(gs_ocr_ctx_t* ctx);

/* 1 when Paddle-Lite is linked AND initialised, so recognition can run.
 * 0 is the honest state when the runtime is absent. */
int gs_ocr_available(gs_ocr_ctx_t* ctx);
const char* gs_ocr_backend_name(gs_ocr_ctx_t* ctx);
const char* gs_ocr_last_error(void);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* GS_OCR_H */
