#define _POSIX_C_SOURCE 200809L
/* test_llama_abi.c — exercises the C ABI end to end against a real model.
 *
 * This is the Phase 2 gate. It deliberately goes through the C ABI rather than
 * calling llama.h directly, because the ABI contract (ownership, error codes,
 * the vtable-by-value rule) is what the rest of the system depends on.
 */
#include "llama_wrapper.h"
#include "gs_abi.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

static int failures = 0;
static void check(int cond, const char* what) {
    printf("%s %s\n", cond ? "PASS" : "FAIL", what);
    if (!cond) ++failures;
}

int main(int argc, char** argv) {
    if (argc < 2) {
        fprintf(stderr, "usage: %s <model.gguf>\n", argv[0]);
        return 2;
    }
    const char* model = argv[1];

    printf("device class: %d (%s)\n", (int)gs_device_class(), gs_device_reason());
    printf("simd-independent ABI smoke, model=%s\n", model);

    /* --- 1. config validation must reject bad input BEFORE any load ----- */
    {
        llama_config_t bad = {0};
        check(gs_llama_validate_config(NULL) == GS_ERR_INVALID_ARG, "null config rejected");
        check(gs_llama_validate_config(&bad) == GS_ERR_INVALID_ARG, "missing model_path rejected");

        llama_config_t noctx = {0};
        noctx.model_path = model;
        noctx.n_ctx = 0;
        check(gs_llama_validate_config(&noctx) == GS_ERR_INVALID_ARG, "n_ctx=0 rejected");

        llama_config_t ok = {0};
        ok.model_path = model;
        ok.n_ctx = 512;
        ok.n_threads = 4;
        ok.n_gpu_layers = 0;   /* CPU only: the guaranteed path */
        ok.use_mmap = 1;
        check(gs_llama_validate_config(&ok) == GS_OK, "valid config accepted");
    }

    /* --- 2. a real model must actually load ----------------------------- */
    llama_config_t cfg = {0};
    cfg.model_path = model;
    cfg.n_ctx = 512;
    cfg.n_threads = 4;
    cfg.n_gpu_layers = 0;
    cfg.use_mmap = 1;

    gs_llama_context_t* ctx = gs_llama_create(&cfg);
    if (!ctx) {
        printf("BLOCKED: gs_llama_create failed: %s\n", gs_last_error());
        return 3;
    }
    check(1, "gs_llama_create returned a handle");
    printf("  backend: %s\n", gs_llama_backend_name(ctx));
    check(gs_llama_available(ctx) == 1, "backend reports available");

    /* --- 3. token counting ------------------------------------------------ */
    {
        const int32_t n = gs_llama_token_count(ctx, "Hello, world!");
        printf("  token_count(\"Hello, world!\") = %d\n", n);
        check(n > 0, "token_count positive");
    }

    /* --- 4. THE REAL DECODE ---------------------------------------------- */
    {
        struct timespec t0, t1;
        clock_gettime(CLOCK_MONOTONIC, &t0);

        llama_result_t r = gs_llama_generate(ctx, "Say hello in one word.", 16, 0.2f);

        clock_gettime(CLOCK_MONOTONIC, &t1);
        const double ms = (t1.tv_sec - t0.tv_sec) * 1000.0 +
                          (t1.tv_nsec - t0.tv_nsec) / 1.0e6;

        if (r.status != GS_OK) {
            printf("BLOCKED: gs_llama_generate status=%d err=%s\n", r.status, gs_last_error());
            gs_llama_free(ctx);
            return 4;
        }
        check(r.text != NULL && strlen(r.text) > 0, "generate returned text");
        printf("  output: \"%s\"\n", r.text);
        printf("  wall time: %.1f ms for 16 tokens\n", ms);

        /* An empty success would be indistinguishable from a real refusal. */
        check(strlen(r.text) > 0, "output is not empty");

        gs_llama_free_result_text(&r);
        check(r.text == NULL, "free_result_text nulls the pointer");
    }

    /* --- 5. error paths must not crash or leak a fake success ------------ */
    {
        llama_result_t r = gs_llama_generate(NULL, "x", 16, 0.2f);
        check(r.status == GS_ERR_INVALID_ARG, "null ctx rejected");

        r = gs_llama_generate(ctx, NULL, 16, 0.2f);
        check(r.status == GS_ERR_INVALID_ARG, "null prompt rejected");

        r = gs_llama_generate(ctx, "x", 0, 0.2f);
        check(r.status == GS_ERR_INVALID_ARG, "max_tokens=0 rejected");
    }

    /* --- 6. speculative decoding reports honestly ------------------------- */
    {
        float acc = -1.0f;
        const int has = gs_llama_has_draft(ctx, &acc);
        printf("  draft: has=%d acceptance=%.3f\n", has, acc);
        /* Not claiming a speedup it did not measure is the contract. */
        check(has == 0 || acc >= 0.0f, "acceptance rate is never negative");
    }

    /* --- 7. NULL-safe teardown -------------------------------------------- */
    gs_llama_free(NULL);
    gs_llama_free_result_text(NULL);
    check(1, "NULL teardown is safe");

    gs_llama_free(ctx);
    check(1, "context freed");

    printf(failures ? "LLAMA ABI FAIL (%d)\n" : "LLAMA ABI PASS\n", failures);
    return failures ? 1 : 0;
}
