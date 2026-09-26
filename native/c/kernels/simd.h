// simd.h — GS AI CPU kernels. C, no dependencies, portable.
//
// Rule 11: must compile with -Wall -Wextra -Werror.
// Every kernel has a scalar reference path so results are identical on
// machines with and without SIMD. A SIMD path that is never validated
// against its scalar twin is a source of silent numerical drift.
#ifndef GS_SIMD_H
#define GS_SIMD_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Name of the active SIMD path: "scalar", "sse2", "avx2", "avx512f",
 * or "neon". Reported in build artifacts so a benchmark can state which
 * kernel produced its number. */
const char* gs_simd_backend(void);

void gs_dot_f32(const float* a, const float* b, size_t n, float* out);
void gs_add_f32(const float* a, const float* b, float* out, size_t n);
void gs_scale_f32(const float* a, float s, float* out, size_t n);
void gs_l2norm_f32(float* v, size_t n);

/* Cosine similarity of two already-L2-normalised vectors: a plain dot.
 * Callers normalise once, then rank with this. */
float gs_cosine_f32(const float* a, const float* b, size_t n);

/* Quantised dot for int8 embeddings — the INT8 CLIP path in the dossier.
 * Accumulates into int32 so there is no per-element widening cost. */
int32_t gs_dot_i8(const int8_t* a, const int8_t* b, size_t n);

#ifdef __cplusplus
}
#endif

#endif /* GS_SIMD_H */
