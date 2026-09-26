---
name: llama-cpp-c-api
description: |-
  Drive llama.cpp / libllama through its public C API (llama.h) from C, C++, Rust, or any FFI host.
  TRIGGER — read BEFORE writing any code that calls libllama — whenever the task involves: llama.cpp,
  libllama, llama.h, GGUF loading, local/on-device LLM inference, llama_backend_init, llama_model_load_from_file,
  llama_context, llama_decode, llama_sampler_chain, KV cache, batch logits, ggml; or a native runtime with a
  "llama.cpp backend" / dlopen of libllama; or a binding layer (cxx, JNI, Swift) that must expose llama symbols.
  Covers init → model load → context → tokenize → decode → sample → text, plus batching, embeddings,
  state save/restore, KV-cache ops and error handling. Use when FFI-debugging a llama backend or when
  dlopen of libllama returns unresolved symbols.
---

# llama.cpp C API (`llama.h`)

## Purpose

`llama.cpp` is a C/C++ inference library. Its **only** public surface is the C header
`llama.h` — everything else (`ggml`, `gguf`, `common.h`) is internal or example-only.

This skill gives you the correct call *sequence* and the correct *ownership* rules so a
host binding does not segfault. The two things that actually break llama bindings:

1. **Wrong call order** — you cannot decode before a context exists.
2. **Wrong ownership** — freeing a `llama_context` does not free its `llama_model`.

Both are covered below. For the *linking / dlopen / symbol-resolution* side of putting
libllama behind a plugin boundary, see `ffi-debugging`.

## Quick Reference — the canonical sequence

| Step | Call | Notes |
|---|---|---|
| 0 | `llama_backend_init()` | Once per process. `llama_backend_free()` at exit. |
| 1 | `llama_numa_init(GGML_NUMA_STRATEGY_DISABLED)` | Optional. Tune only if measured. |
| 2 | `llama_model_default_params()` → `llama_model_load_from_file(path, p)` | Returns `nullptr` on failure. |
| 3 | `llama_context_default_params()` → `llama_init_from_model(model, p)` | Context is **not** owned by model. |
| 4 | `llama_model_get_vocab(model)` | `const llama_vocab *` |
| 5 | `llama_tokenize(vocab, text, len, buf, cap, add_special, parse_special)` | Returns token count; may exceed `cap`. |
| 6 | `llama_batch_get_one(tokens, n)` | Modern one-shot batch helper. |
| 7 | `llama_decode(ctx, batch)` | `!= 0` means failure. **Reuse the batch.** |
| 8 | sampler chain → `llama_sampler_sample(smpl, ctx, -1)` | Build chain once, reuse. |
| 9 | `llama_vocab_is_eog(vocab, tok)` | Loop-exit condition. |
| 10 | `llama_token_to_piece(vocab, tok, buf, cap, 0, true)` | Detokenize one token. |
| teardown | free batch → free context → free model → `llama_backend_free()` | **Reverse order.** |

### Ownership rules (get these wrong and you get use-after-free)

```
llama_backend_free()      owns nothing
  └─ llama_model          owns weights; freed by llama_model_free
       └─ llama_context   owns KV cache; freed by llama_context_free
            └─ llama_batch owns token storage; freed by llama_batch_free
                 └─ llama_sampler owns chain state; freed by llama_sampler_free
```

**Context is a child of model, not a peer.** Two contexts may share one model
(parallel sequences); a context never outlives its model. Free the context first.

### API names that changed across versions

`llama.h` is **not** ABI-stable and the names have churned. Check your vendored header
before trusting any of these:

| Older (pre-2024) | Newer | Status |
|---|---|---|
| `llama_load_model_from_file` | `llama_model_load_from_file` | renamed |
| `llama_new_context_with_model` | `llama_init_from_model` | renamed |
| `llama_kv_cache` (type) | `llama_memory_t` | renamed |
| `llama_sampler_sample(smpl, logits, n_vocab)` | `llama_sampler_sample(smpl, ctx, idx)` | **signature changed — easiest trap** |
| `common_tokenize` / `common_batch_add` | `llama_tokenize` / `llama_batch_get_one` | `common.*` is example-only |

`common.h` helpers (`common_tokenize`, `common_batch_add`, `common_token_to_piece`)
are **not** part of the public API. They are not exported by `libllama.so` on every
build. Never call them from a plugin or a binding.

## Working Example — single-shot completion in C

```c
#include "llama.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(const char *model_path, const char *prompt, int n_predict) {
    llama_backend_init();

    // 1. model
    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;              // 0 = CPU only. >0 offloads to GPU.
    llama_model * model = llama_model_load_from_file(model_path, mparams);
    if (!model) { fprintf(stderr, "BLOCKED: model load failed: %s\n", model_path); return 1; }

    // 2. context
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx      = 2048;
    cparams.n_batch    = 512;
    cparams.n_ubatch   = 512;
    cparams.n_threads  = 4;
    llama_context * ctx = llama_init_from_model(model, cparams);
    if (!ctx) { fprintf(stderr, "BLOCKED: context init failed\n"); llama_model_free(model); return 1; }

    const llama_vocab * vocab = llama_model_get_vocab(model);

    // 3. tokenize (two-call: measure, then fill)
    int n_tok = -llama_tokenize(vocab, prompt, strlen(prompt), NULL, 0, true, false);
    if (n_tok < 0) { fprintf(stderr, "BLOCKED: tokenize failed\n"); return 1; }
    llama_token * toks = malloc(sizeof(llama_token) * n_tok);
    if (llama_tokenize(vocab, prompt, strlen(prompt), toks, n_tok, true, false) != n_tok) {
        fprintf(stderr, "BLOCKED: tokenize count mismatch\n"); free(toks); return 1;
    }

    // 4. decode the prompt, asking for logits on the LAST token only
    llama_batch batch = llama_batch_init(n_tok + 1, 0, 1);
    memcpy(batch.token, toks, sizeof(llama_token) * n_tok);
    for (int i = 0; i < n_tok; ++i) batch.pos[i] = i;
    for (int i = 0; i < n_tok; ++i)  batch.n_tokens = i + 1, batch.logits[i] = (i == n_tok - 1);

    if (llama_decode(ctx, batch) != 0) { fprintf(stderr, "BLOCKED: prompt decode failed\n"); return 1; }

    // 5. sampler chain, built once
    llama_sampler_chain_params sp = llama_sampler_chain_default_params();
    sp.no_perf = true;
    llama_sampler * smpl = llama_sampler_chain_init(sp);
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(1234));  // seed = reproducible

    // 6. generate
    int n_cur = n_tok;
    for (int i = 0; i < n_predict; ++i) {
        llama_token next = llama_sampler_sample(smpl, ctx, -1);   // -1 = last logits
        if (llama_vocab_is_eog(vocab, next)) break;

        char piece[256];
        int  n = llama_token_to_piece(vocab, next, piece, sizeof(piece), 0, true);
        if (n > 0) { fwrite(piece, 1, n, stdout); fflush(stdout); }

        // feed the sampled token back in at the next position
        batch.n_tokens = 0;
        llama_batch_add(batch, next, n_cur, 0, true);
        ++n_cur;
        if (llama_decode(ctx, batch) != 0) { fprintf(stderr, "BLOCKED: decode failed at %d\n", i); break; }
    }

    // 7. teardown, reverse order
    llama_sampler_free(smpl);
    llama_batch_free(batch);
    free(toks);
    llama_context_free(ctx);
    llama_model_free(model);
    llama_backend_free();
    return 0;
}
```

### `logits[]` — the field people get wrong

`llama_batch.logits[i]` is **not** "is this token valid". It is *"do I need the full
logit distribution for position i"*. Setting it on every token computes and retains an
`n_vocab`-wide float array per token — that is the difference between a working
decode and an OOM. Set it on the **last prompt token only** during prefill, and on
the single generated token during the loop.

### Two-phase tokenize

`llama_tokenize` with `n_tokens_max = 0` returns the **required** count. Never
allocate optimistically and hope. It also returns a **negative** value on tokenize
failure — a `0` return is not an error, a negative one is.

## Common Pitfalls

1. **Treating `llama_context` as a peer of `llama_model`.** It is a child. Freeing
   the model first leaves the context pointing at freed weights.
2. **Calling `common_*` helpers from a binding.** `common.h` is not shipped in the
   public ABI of every build. Use `llama_tokenize` / `llama_batch_get_one`.
3. **Assuming `llama_sampler_sample` takes logits.** The third argument is a context
   and a logit index in current `llama.h`. Passing a logits pointer where an index
   is expected compiles (pointer-sized) and then produces garbage tokens.
4. **`llama_batch_init(n, embd, n_seq_max)` with `embd=1`** on a completion path —
   you get an embedding batch and the decode fails or returns junk.
5. **Setting `logits[i] = true` for all `i`.** OOM, or a silent 10x slowdown.
6. **Forgetting `llama_backend_init()`.** Symptom is a segfault inside `ggml` on the
   first `llama_model_default_params()`, with no llama-level error.
7. **Rebuilding the sampler chain every token.** It is not cheap; build once per
   context and reuse.
8. **`n_gpu_layers > 0` on a CPU-only build.** Silently ignored at load, then you
   benchmark a number that was never produced by a GPU.
9. **Assuming `llama.h` symbols are exported.** When `libllama.so` is built with
   `-fvisibility=hidden`, most symbols are internal. `nm -D --defined-only` is the
   only ground truth — see `ffi-debugging`.
10. **Reporting TTFT/tok-s from a mock or CPU-only path as device performance.**
    Those are core-overhead numbers, not backend numbers. Mark them
    `NOT_MEASURED` until a real backend is linked.

## Debugging

**Validate the build with a real decode before writing any code against it.**
A build that configures is not a build that decodes. Fastest end-to-end check:

```bash
git clone --depth 1 https://github.com/ggml-org/llama.cpp
cd llama.cpp
cmake -B build -DGGML_NATIVE=ON -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=ON
cmake --build build -j"$(nproc)"          # BUILD_EXIT must be 0
./build/bin/llama-cli -m /path/model.gguf -p "Say hello in one word." -n 16 -t 4 --single-turn
```

Artefacts that must exist afterwards: `build/bin/llama-cli` and
`build/bin/libllama.so.0.<x>.<y>.<z>` (the versioned real file behind the
`libllama.so` → `libllama.so.0` symlink chain). A `llama-cli` of ~16 KB is
normal — it is a thin front end over `libllama.so`.

> **Flag names drift — check `--help`, do not trust memory.** Verified on
> `b1-2145525`: the single-turn flag is **`-st` / `--single-turn`**. The older
> `--no-conversation` and `-no-cnv` are **gone** and fail with
> `error: invalid argument: --no-conversation`. Run `./build/bin/llama-cli --help`
> and grep before you copy a command from anywhere, including from these skills.
>
> A successful run prints a banner with `build`, `model`, `ftype` and `modalities`,
> then the completion, then the rate line:
>
> ```
> [ Prompt: 40.0 t/s | Generation: 6.2 t/s ]
> ```
>
> **Label what that number is.** On a desktop CPU with `-t 4` it is a *sandbox
> CPU* figure. It is **not** a device TTFT, not a phone tok/s, and not a battery
> result. Report it as sandbox-CPU or mark it `NOT MEASURED` for device purposes.
> Also verify the model actually loaded — `ftype` must match the quant you
> downloaded (e.g. `Q4_K - Medium`), and a run that silently fell back is worse
> than a run that failed.

**Confirm the symbols are actually exported** before blaming your own binding:

```bash
nm -D --defined-only /path/libllama.so | grep -E 'llama_(backend_init|model_load_from_file|decode)'
```

**Run under a sanitiser** — use the `debug` CMake build, not `release`:

```bash
cmake -B build-debug -DCMAKE_BUILD_TYPE=Debug -DLLAMA_SANITIZE_ADDRESS=ON
cmake --build build-debug -j4
```

**Isolate in stages.** A three-call program (init → load → free) that segfaults tells
you the problem is linking or model-file, not your sampling code. Bisect upward:

1. `llama_backend_init()` + `llama_backend_free()` — no model
2. + `llama_model_load_from_file` — no context
3. + `llama_init_from_model` — no decode
4. + one `llama_decode` on a static batch
5. + sampler loop

**Capture the real error string.** llama.cpp writes diagnostics to stderr/log, not
to return codes. Redirect and keep it — a `nullptr` return plus an empty log means you
are looking at the wrong log level.

**Reproducibility:** set `llama_sampler_init_dist(seed)` with a fixed seed. If output
varies run-to-run, the sampler chain is missing the dist stage — you will never
regression-test an unseeded sampler.

## Source Links

- Developer guide (primary; **note: dated — several `lama_*` typos and pre-rename
  API names, use as conceptual map not as copy-paste source):
  https://raw.githubusercontent.com/QuasarByte/llama-cpp-jna/refs/heads/main/llama-cpp-developer-guide.md
- LobeHub llamacpp C API reference by category:
  https://lobehub.com/skills/datathings-marketplace-llamacpp
- DeepWiki — libllama public API surface:
  https://deepwiki.com/ggml-org/llama.cpp
- Upstream repo (authoritative `llama.h` — always read this before trusting a snippet):
  https://github.com/ggml-org/llama.cpp
- `examples/simple` and `examples/embedding` in the above repo are the reference
  implementations for the call order above.
