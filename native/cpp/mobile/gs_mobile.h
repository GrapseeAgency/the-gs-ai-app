/* gs_mobile.h — portable C ABI for mobile (Android / iOS).
 *
 * Why this exists and why it is separate from llama_wrapper.h
 * -----------------------------------------------------------
 * The desktop wrappers bind ONNX Runtime, llama.cpp and Tesseract. None of those
 * are available as drop-in prebuilts for arm64 Android or iOS, and linking a
 * 300 MB llama.cpp build into a phone binary is not the product. So the mobile
 * surface is deliberately SMALL and PORTABLE: it compiles with nothing but a
 * C++17 compiler and libm, and every backend-dependent call reports
 * GS_ERR_UNAVAILABLE with a reason instead of pretending.
 *
 * That is the same rule the desktop wrappers already follow, and it is the
 * honest one: a capability that is not compiled in must be discoverable as such
 * at run time, never inferred from an empty result.
 *
 * FFI rules are those of gs_abi.h and are enforced, not documented:
 *   - no C++ exception crosses this boundary
 *   - strings IN are borrowed, strings OUT are owned by the caller
 *   - handles come from gs_mobile_create and die only in gs_mobile_free
 */
#ifndef GS_MOBILE_H
#define GS_MOBILE_H

#include <stdint.h>
#include <stddef.h>

#include "gs_abi.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct gs_mobile_ctx gs_mobile_ctx_t;

/* ---- lifecycle --------------------------------------------------------- */

/* Create a context.
 *
 * `model_path` may be NULL or empty, in which case no model is loaded and
 * chat() reports GS_ERR_UNAVAILABLE until one is. That is deliberate: the
 * common mobile case is "app launched, model not yet downloaded", and failing at
 * create time would make a download-in-progress indistinguishable from a crash.
 */
gs_mobile_ctx_t* gs_mobile_create(const char* model_path, int32_t n_ctx, int32_t n_threads);

/* Destroy a context. NULL-safe. */
void gs_mobile_free(gs_mobile_ctx_t* ctx);

/* 1 when a generation backend is compiled in, 0 otherwise. On a default mobile
 * build this is 0 and chat() says so with a reason. */
int32_t gs_mobile_backend_available(gs_mobile_ctx_t* ctx);

/* Name of the compiled-in backend: "llama.cpp", "none". */
const char* gs_mobile_backend_name(gs_mobile_ctx_t* ctx);

/* ---- inference --------------------------------------------------------- */

/* Generate a completion. Returns an owned UTF-8 string the caller frees with
 * gs_free_string, or NULL with gs_last_error() carrying the reason. */
char* gs_mobile_chat(gs_mobile_ctx_t* ctx, const char* prompt, int32_t max_tokens, float temperature);

/* ---- vision ------------------------------------------------------------ */

/* OCR. Returns owned text (possibly empty when the image has no text) or NULL
 * on failure. An empty string and a failure are deliberately distinguishable. */
char* gs_mobile_ocr(gs_mobile_ctx_t* ctx, const char* image_path);

/* Embed an image into `out`, which must hold gs_mobile_embed_dim floats.
 *
 * `rgb` is raw 8-bit RGB, three bytes per pixel, row-major, w*h*3 bytes. Not a
 * path: decoding PNG/JPEG would be a dependency the mobile build does not carry,
 * and the host already holds the decoded pixels. Returns the number of floats
 * written, or negative gs_status_t. */
int32_t gs_mobile_embed_image(gs_mobile_ctx_t* ctx, const uint8_t* rgb, int32_t w, int32_t h,
                              float* out, int32_t cap);

/* Embed a text query into `out`, which must hold gs_mobile_embed_dim floats.
 * Returns floats written, or negative gs_status_t. */
int32_t gs_mobile_embed_text(gs_mobile_ctx_t* ctx, const char* text, float* out, int32_t cap);

/* Embedding width, or negative gs_status_t when no embedder is compiled in. */
int32_t gs_mobile_embed_dim(gs_mobile_ctx_t* ctx);

/* ---- device gate ------------------------------------------------------- */

/* Device class for local-first routing decisions in the host app. Mirrors
 * gs_device_class() in gs_abi.h and is re-exported so a mobile caller needs
 * only this header. */
gs_device_class_t gs_mobile_device_class(void);

/* 1 when local inference is recommended for a model of `max_params` bytes. */
int32_t gs_mobile_should_use_local(uint64_t max_params);

/* Build identification, so a shipped binary can be matched to a commit. */
const char* gs_mobile_build_info(void);

/* ---------------------------------------------------------------------------
 * Image generation, IN-PROCESS. Declared here so the definitions in
 * gs_mobile.cpp are not unprototyped, and so Rust's extern block has a header to
 * be written against rather than a set of names copied out of a .cpp.
 *
 * The diffusion handle and the procedural renderer are separate on purpose.
 * gs_mobile_render_svg needs no weights, no GPU and no diffusion, so it works in
 * EVERY build; the sd_* entry points need the library AND a checkpoint, and report
 * which of the two is missing rather than collapsing them.
 * ------------------------------------------------------------------------- */
typedef struct gs_mobile_img gs_mobile_img_t;

gs_mobile_img_t* gs_mobile_sd_create(const char* model_path);
int32_t gs_mobile_sd_set_options(gs_mobile_img_t* img, int32_t threads, float cfg_scale);
int32_t gs_mobile_sd_available(gs_mobile_img_t* img);
const char* gs_mobile_sd_backend_name(gs_mobile_img_t* img);
int32_t gs_mobile_sd_generate(gs_mobile_img_t* img,
                              const char* prompt,
                              const char* negative_prompt,
                              int32_t width, int32_t height, int32_t steps,
                              const char* output_path);
void gs_mobile_sd_free(gs_mobile_img_t* img);

/* Always available. No model, no GPU, no diffusion. */
int32_t gs_mobile_render_svg(const char* spec_json, const char* output_path);

#ifdef __cplusplus
} /* extern "C" */
#endif
#endif /* GS_MOBILE_H */
