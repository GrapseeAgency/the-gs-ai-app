/* gs_sd_wrapper.h — the GS AI C ABI over stable-diffusion.cpp.
 *
 * EVERY NAME HERE IS gs_sd_*, AND THAT IS NOT A STYLE CHOICE.
 *
 * stable-diffusion.cpp's own public API is sd_*:
 *
 *     SD_API sd_ctx_t* new_sd_ctx(const sd_ctx_params_t*);
 *     SD_API void      free_sd_ctx(sd_ctx_t*);
 *     SD_API bool      generate_image(sd_ctx_t*, const sd_img_gen_params_t*,
 *                                     sd_image_t**, int*);
 *
 * so this file previously declaring sd_create / sd_generate / sd_free /
 * sd_context_t / sd_config_t meant that #include "stable-diffusion.h" and
 * #include this header put two unrelated `extern "C"` namespaces in one
 * translation unit. Any overlap is a duplicate-symbol link error, and the
 * near-misses are worse than an error because they compile.
 *
 * The API below is read from the header of the PINNED commit, not from memory:
 *   3f8527a46c54ecf4cb4ed6003da8e8982283c73c (2026-09-27)
 * and that header contains ZERO occurrences of "stable_diffusion_", so the
 * naming an earlier reading of that project's docs suggests does not exist.
 * android-deps.yml asserts the five symbols the implementation is written
 * against are present in the packaged header, so an upstream rename fails there
 * rather than as a confusing compile error here.
 */
#ifndef GS_SD_WRAPPER_H
#define GS_SD_WRAPPER_H

#include "gs_abi.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct gs_sd_ctx gs_sd_ctx_t;

/*
 * Load a model. Returns NULL on failure, and gs_last_error() carries a reason
 * that names the cause -- never a generic "failed".
 *
 * NULL rather than a context whose every call fails, so a caller can tell
 * "could not load" from "loaded but cannot generate".
 */
gs_sd_ctx_t *gs_sd_create(const char *model_path);

/*
 * Options the operator's signature does not carry, set between create and
 * generate. Both have working defaults, so this call is optional.
 *
 *   threads   <= 0 keeps the default (sd_get_num_physical_cores()).
 *   cfg_scale <  0 keeps the default.
 *
 * n_threads matters more than it looks: it is the decode parallelism of the
 * UNet, and on a phone it is usually the difference between "one image in five
 * minutes" and "one image in twenty".
 */
int32_t gs_sd_set_options(gs_sd_ctx_t *ctx, int32_t threads, float cfg_scale);

/* 1 when the backend can generate, 0 when it cannot. */
int32_t gs_sd_available(const gs_sd_ctx_t *ctx);

/* "sd.cpp" or "sd.cpp:not-compiled". Never NULL. */
const char *gs_sd_backend_name(const gs_sd_ctx_t *ctx);

/*
 * Generate one image and WRITE IT AS A PNG to output_path.
 *
 * The PNG is encoded here, from the raw pixels stable-diffusion.cpp returns.
 * That library exposes no image writer in its public header, so there are two
 * honest options: link a PNG encoder, or write one. This writes one -- a stored-
 * deflate PNG with correct CRCs, ~100 lines and no dependency, which matters
 * because a phone build carries one fewer third-party library for it.
 *
 * Returns GS_OK, or a status with a reason in gs_last_error(). It NEVER writes a
 * placeholder image on failure: a test that decodes a PNG cannot tell a real
 * generation from a grey rectangle, and a grey rectangle that always appears is
 * the worst possible outcome for a quality measurement.
 */
int32_t gs_sd_generate(gs_sd_ctx_t *ctx,
                       const char *prompt,
                       const char *negative_prompt,
                       int32_t width,
                       int32_t height,
                       int32_t steps,
                       const char *output_path);

void gs_sd_free(gs_sd_ctx_t *ctx);

/*
 * The PNG encoder, exposed for a test.
 *
 * Exists so that a failure can be attributed: if gs_sd_generate produces no file,
 * the question is whether diffusion failed or the encoder did, and the only way to
 * tell them apart is to drive the encoder directly with pixels that are known
 * good. Returns GS_OK or GS_ERR_IO.
 */
int32_t gs_sd_write_png_for_test(const uint8_t *pixels, uint32_t w, uint32_t h,
                                 uint32_t channels, const char *path);

/* Procedural path: no model, no diffusion. Renders an SVG diagram from a
 * structured spec. Always available, needs no weights, and is the only image
 * path that works without the sd.cpp library linked. */
int32_t gs_sd_render_svg(const char *spec_json, const char *output_path);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* GS_SD_WRAPPER_H */