/* gs_ocr_tess.h — OCR C ABI backed by Tesseract.
 *
 * Separate from ocr_wrapper.h, which is the Android/Paddle-Lite ABI and is
 * still built by its own CMake toolchain. This one is the desktop path.
 *
 * Ownership:
 *   - const char* inputs are borrowed.
 *   - the char* returned by gs_ocr_recognize is owned by the caller and must be
 *     released with gs_ocr_free_string.
 *   - the context comes from gs_ocr_create and goes to gs_ocr_free.
 *
 * No C++ exception crosses this boundary.
 */
#ifndef GS_OCR_TESS_H
#define GS_OCR_TESS_H

#ifdef __cplusplus
extern "C" {
#endif

#define GS_OCR_OK             0
#define GS_OCR_ERR_ARG       -1
#define GS_OCR_ERR_INIT      -2
#define GS_OCR_ERR_READ      -3
#define GS_OCR_ERR_RECOGNIZE -4
#define GS_OCR_ERR_UNAVAILABLE -6

typedef struct gs_ocr_ctx gs_ocr_ctx_t;

/* Last error for the calling thread. Never NULL; "" when nothing failed. */
const char *gs_ocr_last_error(void);

/* NULL on failure; gs_ocr_last_error() carries the reason. */
gs_ocr_ctx_t *gs_ocr_create(void);

/* Recognise text in an image file. Returns a malloc'd UTF-8 string owned by the
 * caller, or NULL on failure. An image with no text yields an empty string, not
 * NULL, so "no text" stays distinguishable from "OCR failed". */
char *gs_ocr_recognize(gs_ocr_ctx_t *ctx, const char *image_path);

void gs_ocr_free_string(char *s);
void gs_ocr_free(gs_ocr_ctx_t *ctx);

#ifdef __cplusplus
} /* extern "C" */
#endif
#endif /* GS_OCR_TESS_H */
