/* simd.c — GS AI CPU kernels with validated scalar fallbacks.
 *
 * Rule 11: -Wall -Wextra -Werror clean.
 *
 * Strategy: the SIMD paths use GCC/Clang function-level `target` attributes so
 * the intrinsics compile without global -mavx2/-msse2 flags, while dispatch
 * stays a runtime check. Compiling the whole file with -mavx2 would make the
 * binary crash on a CPU without AVX2 even though the dispatch is correct;
 * compiling without it makes the intrinsics fail to inline. Per-function
 * target attributes are the only way to get both.
 *
 * Every kernel keeps a scalar path, and tests/test_kernels.c compares the two
 * across tail-straddling sizes so no SIMD path ships unvalidated.
 */
#include "simd.h"

#include <math.h>
#include <string.h>

#if defined(__x86_64__) || defined(_M_X64) || defined(__i386__)
#  define GS_X86 1
#  include <immintrin.h>
#  if defined(__GNUC__) || defined(__clang__)
#    define GS_TARGET(x) __attribute__((target(x)))
#  else
#    define GS_TARGET(x)
#  endif
#elif defined(__aarch64__) || defined(__ARM_NEON)
#  define GS_NEON 1
#  include <arm_neon.h>
#  define GS_TARGET(x)
#endif

/* ------------------------------------------------------------------ */
/* backend detection                                                   */
/* ------------------------------------------------------------------ */
const char* gs_simd_backend(void) {
#if defined(GS_X86) && (defined(__GNUC__) || defined(__clang__))
    __builtin_cpu_init();
    if (__builtin_cpu_supports("avx512f")) return "avx512f";
    if (__builtin_cpu_supports("avx2"))   return "avx2";
    if (__builtin_cpu_supports("sse2"))   return "sse2";
    return "scalar";
#elif defined(GS_NEON)
    return "neon";
#else
    return "scalar";
#endif
}

#if defined(GS_X86)
static int have_avx2(void) {
    __builtin_cpu_init();
    return __builtin_cpu_supports("avx2");
}
static int have_sse2(void) {
    __builtin_cpu_init();
    return __builtin_cpu_supports("sse2");
}

static GS_TARGET("avx2") float dot_avx2(const float* a, const float* b, size_t n, size_t* i) {
    __m256 acc = _mm256_setzero_ps();
    size_t k = *i;
    for (; k + 8 <= n; k += 8) {
        acc = _mm256_add_ps(acc, _mm256_mul_ps(_mm256_loadu_ps(a + k),
                                               _mm256_loadu_ps(b + k)));
    }
    float tmp[8];
    _mm256_storeu_ps(tmp, acc);
    float s = 0.0f;
    for (int j = 0; j < 8; ++j) s += tmp[j];
    *i = k;
    return s;
}

static GS_TARGET("sse2") float dot_sse2(const float* a, const float* b, size_t n, size_t* i) {
    __m128 acc = _mm_setzero_ps();
    size_t k = *i;
    for (; k + 4 <= n; k += 4) {
        acc = _mm_add_ps(acc, _mm_mul_ps(_mm_loadu_ps(a + k), _mm_loadu_ps(b + k)));
    }
    float tmp[4];
    _mm_storeu_ps(tmp, acc);
    float s = 0.0f;
    for (int j = 0; j < 4; ++j) s += tmp[j];
    *i = k;
    return s;
}

static GS_TARGET("avx2") void add_avx2(const float* a, const float* b, float* o, size_t n, size_t* i) {
    size_t k = *i;
    for (; k + 8 <= n; k += 8) {
        _mm256_storeu_ps(o + k, _mm256_add_ps(_mm256_loadu_ps(a + k),
                                              _mm256_loadu_ps(b + k)));
    }
    *i = k;
}

static GS_TARGET("sse2") void add_sse2(const float* a, const float* b, float* o, size_t n, size_t* i) {
    size_t k = *i;
    for (; k + 4 <= n; k += 4) {
        _mm_storeu_ps(o + k, _mm_add_ps(_mm_loadu_ps(a + k), _mm_loadu_ps(b + k)));
    }
    *i = k;
}

static GS_TARGET("avx2") void scale_avx2(const float* a, float s, float* o, size_t n, size_t* i) {
    const __m256 vs = _mm256_set1_ps(s);
    size_t k = *i;
    for (; k + 8 <= n; k += 8) {
        _mm256_storeu_ps(o + k, _mm256_mul_ps(_mm256_loadu_ps(a + k), vs));
    }
    *i = k;
}
#endif /* GS_X86 */

/* ------------------------------------------------------------------ */
/* kernels                                                             */
/* ------------------------------------------------------------------ */

void gs_dot_f32(const float* a, const float* b, size_t n, float* out) {
    if (!a || !b || !out) return;
    size_t i = 0;
    float acc = 0.0f;
#if defined(GS_X86)
    if (have_avx2())        acc += dot_avx2(a, b, n, &i);
    else if (have_sse2())   acc += dot_sse2(a, b, n, &i);
#endif
    for (; i < n; ++i) acc += a[i] * b[i];
    *out = acc;
}

void gs_add_f32(const float* a, const float* b, float* out, size_t n) {
    if (!a || !b || !out) return;
    size_t i = 0;
#if defined(GS_X86)
    if (have_avx2())      add_avx2(a, b, out, n, &i);
    else if (have_sse2()) add_sse2(a, b, out, n, &i);
#endif
    for (; i < n; ++i) out[i] = a[i] + b[i];
}

void gs_scale_f32(const float* a, float s, float* out, size_t n) {
    if (!a || !out) return;
    size_t i = 0;
#if defined(GS_X86)
    if (have_avx2()) scale_avx2(a, s, out, n, &i);
#endif
    for (; i < n; ++i) out[i] = a[i] * s;
}

void gs_l2norm_f32(float* v, size_t n) {
    if (!v) return;
    float sum = 0.0f;
    gs_dot_f32(v, v, n, &sum);
    const float norm = sqrtf(sum);
    /* A zero vector is left as zero rather than producing NaN. A NaN
     * embedding silently poisons every downstream cosine score. */
    if (norm > 0.0f) gs_scale_f32(v, 1.0f / norm, v, n);
}

float gs_cosine_f32(const float* a, const float* b, size_t n) {
    if (!a || !b) return 0.0f;
    float acc = 0.0f;
    gs_dot_f32(a, b, n, &acc);
    return acc;
}

int32_t gs_dot_i8(const int8_t* a, const int8_t* b, size_t n) {
    if (!a || !b) return 0;
    int32_t acc = 0;
    for (size_t i = 0; i < n; ++i) acc += (int32_t)a[i] * (int32_t)b[i];
    return acc;
}
