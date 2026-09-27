/* CLIP embedding ABI.
 *
 * C-only surface so the Rust side never touches the ONNX Runtime C++ types.
 * No C++ exception is allowed to cross this boundary: every entry point is
 * noexcept and reports failure through the return code.
 */
#ifndef GS_CLIP_H
#define GS_CLIP_H

#ifdef __cplusplus
extern "C" {
#endif

typedef struct gs_clip_ctx gs_clip_ctx_t;

/* Status codes, aligned with gs_status in the shared ABI. */
#define GS_CLIP_OK             0
#define GS_CLIP_ERR_ARG       -1
#define GS_CLIP_ERR_LOAD      -2
#define GS_CLIP_ERR_ONNX      -3
#define GS_CLIP_ERR_SHAPE     -4
#define GS_CLIP_ERR_TOKENIZER -5
#define GS_CLIP_ERR_UNAVAILABLE -6

/* Last error for the calling thread, owned by the wrapper. */
const char *gs_clip_last_error(void);

/* Create a context from a vision ONNX model and a text ONNX model.
 * The CLIP BPE vocabulary is read from sidecar files that sit next to the text
 * model: clip_vocab.tsv and clip_merges.tsv. Returns NULL on failure. */
gs_clip_ctx_t *gs_clip_create(const char *vision_path, const char *text_path);

/* Embed a 256x256 RGB image (row-major, 3 bytes per pixel).
 * The caller owns the preprocessing; this takes the final tensor. */
int gs_clip_embed_image(gs_clip_ctx_t *ctx, const unsigned char *rgb, int w, int h,
                        float *out_embedding);

/* Embed a query string. Tokenisation happens inside the wrapper. */
int gs_clip_embed_text(gs_clip_ctx_t *ctx, const char *text, float *out_embedding);

/* Embed pre-tokenised ids (CLIP context length 77). Exposed so a caller that
 * already owns a tokenizer does not pay for a second one. */
int gs_clip_embed_tokens(gs_clip_ctx_t *ctx, const long long *ids, int n, float *out_embedding);

int gs_clip_embed_dim(gs_clip_ctx_t *ctx);
void gs_clip_free(gs_clip_ctx_t *ctx);

#ifdef __cplusplus
} /* extern "C" */
#endif
#endif /* GS_CLIP_H */
