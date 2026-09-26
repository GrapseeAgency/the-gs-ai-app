/* test_kernels.c — validates every SIMD kernel against a scalar reference.
 *
 * A vectorised path that is never compared to its scalar twin is a source of
 * silent numerical drift. These tests exist to make that impossible to ship.
 *
 * Rule 11: -Wall -Wextra -Werror clean.
 */
#include "simd.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int failures = 0;

static void check(int cond, const char* what) {
    printf("%s %s\n", cond ? "PASS" : "FAIL", what);
    if (!cond) ++failures;
}

/* Independent scalar reference, written differently on purpose. */
static double ref_dot(const float* a, const float* b, size_t n) {
    double s = 0.0;
    for (size_t i = 0; i < n; ++i) s += (double)a[i] * (double)b[i];
    return s;
}

int main(void) {
    printf("SIMD backend: %s\n", gs_simd_backend());

    /* Exercise sizes that straddle the vector widths: 4-wide, 8-wide, and
     * awkward tails at 5, 13, 37. A kernel that only passes on n=1024 is
     * broken for every real embedding size. */
    const size_t sizes[] = {1, 3, 4, 5, 7, 8, 9, 13, 16, 37, 64, 257, 1024};
    const size_t nsizes = sizeof(sizes) / sizeof(sizes[0]);
    char msg[128];

    for (size_t si = 0; si < nsizes; ++si) {
        const size_t n = sizes[si];
        float* a = (float*)malloc(n * sizeof(float));
        float* b = (float*)malloc(n * sizeof(float));
        float* got = (float*)malloc(n * sizeof(float));
        float* ref = (float*)malloc(n * sizeof(float));
        if (!a || !b || !got || !ref) { printf("FAIL malloc\n"); return 1; }

        /* Cast to int BEFORE subtracting: i is size_t, so (i % 17) - 8
         * underflows to ~1.8e19 whenever i%17 < 8 and every dot product
         * below overflows to inf. Signed arithmetic is what was meant. */
        for (size_t i = 0; i < n; ++i) {
            a[i] = (float)((int)(i % 17) - 8) * 0.37f;
            b[i] = (float)((int)(i % 11) - 5) * 0.91f;
        }

        /* dot: SIMD vs independent double-precision reference */
        float d = 0.0f;
        gs_dot_f32(a, b, n, &d);
        const double r = ref_dot(a, b, n);
        const double tol = 1e-4 * (fabs(r) > 1.0 ? fabs(r) : 1.0);
        snprintf(msg, sizeof(msg), "dot n=%zu simd=%.6f ref=%.6f", n, (double)d, r);
        check(fabs((double)d - r) <= tol, msg);

        /* add: every element */
        gs_add_f32(a, b, got, n);
        int ok = 1;
        for (size_t i = 0; i < n; ++i) ref[i] = a[i] + b[i];
        for (size_t i = 0; i < n; ++i) if (got[i] != ref[i]) { ok = 0; break; }
        snprintf(msg, sizeof(msg), "add n=%zu exact", n);
        check(ok, msg);

        /* scale */
        gs_scale_f32(a, 2.5f, got, n);
        ok = 1;
        for (size_t i = 0; i < n; ++i) {
            const float e = a[i] * 2.5f;
            if (fabsf(got[i] - e) > 1e-6f) { ok = 0; break; }
        }
        snprintf(msg, sizeof(msg), "scale n=%zu", n);
        check(ok, msg);

        /* l2norm then cosine: after normalising two identical vectors the
         * cosine must be 1. This is the property the CLIP ranking path relies
         * on, so it is asserted directly rather than via a proxy. */
        float* u = (float*)malloc(n * sizeof(float));
        float* v = (float*)malloc(n * sizeof(float));
        memcpy(u, a, n * sizeof(float));
        memcpy(v, a, n * sizeof(float));
        gs_l2norm_f32(u, n);
        gs_l2norm_f32(v, n);
        const float cos_self = gs_cosine_f32(u, v, n);
        snprintf(msg, sizeof(msg), "cosine(u,u) n=%zu = %.6f (expect 1)", n, (double)cos_self);
        check(fabsf(cos_self - 1.0f) < 1e-4f, msg);

        /* a zero vector must stay zero, never NaN */
        float* z = (float*)calloc(n, sizeof(float));
        gs_l2norm_f32(z, n);
        int nan_seen = 0;
        for (size_t i = 0; i < n; ++i) if (isnan(z[i])) { nan_seen = 1; break; }
        snprintf(msg, sizeof(msg), "l2norm(zero) n=%zu produces no NaN", n);
        check(!nan_seen, msg);

        free(a); free(b); free(got); free(ref); free(u); free(v); free(z);
    }

    /* int8 dot: exact integer arithmetic, no float tolerance allowed */
    {
        const int8_t x[8] = {1, -2, 3, -4, 5, -6, 7, -8};
        const int8_t y[8] = {8, -7, 6, -5, 4, -3, 2, -1};
        int32_t expect = 0;
        for (int i = 0; i < 8; ++i) expect += (int32_t)x[i] * (int32_t)y[i];
        snprintf(msg, sizeof(msg), "dot_i8 = %d (expect %d)", gs_dot_i8(x, y, 8), expect);
        check(gs_dot_i8(x, y, 8) == expect, msg);
    }

    /* NULL safety: every kernel must be a no-op, not a crash */
    {
        gs_dot_f32(NULL, NULL, 0, NULL);
        gs_add_f32(NULL, NULL, NULL, 0);
        gs_scale_f32(NULL, 0.0f, NULL, 0);
        gs_l2norm_f32(NULL, 0);
        check(1, "NULL inputs are no-ops");
    }

    printf(failures ? "KERNELS FAIL (%d)\n" : "KERNELS PASS\n", failures);
    return failures ? 1 : 0;
}
