/* ocr_wrapper.h — GS AI C ABI for OCR. */
#ifndef GS_OCR_WRAPPER_H
#define GS_OCR_WRAPPER_H

#include "gs_abi.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    const char* det_model;   /* borrowed; detection model path */
    const char* rec_model;   /* borrowed; recognition model path */
    int32_t     use_gpu;
} ocr_config_t;

typedef struct ocr_context ocr_context_t;

int32_t ocr_validate_config(const ocr_config_t* config);

/* NULL on failure; gs_last_error() carries the reason. */
ocr_context_t* ocr_create(const ocr_config_t* config);

/* Returns an OWNED malloc'd string of extracted text, or NULL on failure.
 * Release with ocr_free_string. */
char* ocr_recognize(ocr_context_t* ctx, const char* image_path);

void ocr_free_string(char* s);

int32_t ocr_available(ocr_context_t* ctx);
const char* ocr_backend_name(ocr_context_t* ctx);
void ocr_free(ocr_context_t* ctx);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* GS_OCR_WRAPPER_H */
