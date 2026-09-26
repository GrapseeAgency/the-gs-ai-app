/* sd_wrapper.h — GS AI C ABI over stable-diffusion.cpp. */
#ifndef GS_SD_WRAPPER_H
#define GS_SD_WRAPPER_H

#include "gs_abi.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    const char* model_path;   /* borrowed */
    int32_t     width;
    int32_t     height;
    int32_t     steps;
    float       cfg_scale;
    int32_t     use_gpu;      /* nonzero requests Vulkan; 0 = CPU, the
                               * guaranteed path. Falls back to CPU and
                               * records the reason in gs_last_error(). */
} sd_config_t;

typedef struct sd_context sd_context_t;

int32_t sd_validate_config(const sd_config_t* config);

/* NULL on failure; gs_last_error() carries the reason. */
sd_context_t* sd_create(const sd_config_t* config);

/* Writes an image to output_path (borrowed). Returns GS_OK or a status.
 * Never writes a placeholder image on failure. */
int32_t sd_generate(sd_context_t* ctx,
                    const char* prompt,
                    const char* negative_prompt,
                    const char* output_path);

/* Procedural path: no model, no diffusion. Renders an SVG diagram from a
 * structured spec. Always available, needs no weights. */
int32_t sd_render_svg(const char* spec_json, const char* output_path);

int32_t sd_available(sd_context_t* ctx);
const char* sd_backend_name(sd_context_t* ctx);
void sd_free(sd_context_t* ctx);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* GS_SD_WRAPPER_H */
