# Native runtime skills — provenance and live verification

Seven skill documents, each written from upstream primary sources and then
**executed against a live toolchain**. Anything below marked VERIFIED was actually
run on this machine; anything BLOCKED was attempted or prerequisite-checked and
failed. Nothing here is estimated.

Machine: Arch Linux, Intel i5-6500 (4 cores, no GPU), 15 GB RAM, g++ 16.2.1,
cmake 4.4.3, ninja 1.13.2, binutils present, Python 3.14.7 / uv 0.12.19.

| Skill | Lines | Status |
|---|---|---|
| `llama-cpp-c-api` | 277 | VERIFIED — clone, configure, build, real decode |
| `cxx-bridge-patterns` | 278 | VERIFIED — live `#[cxx::bridge]` build + run |
| `cargo-workspace-sys` | 282 | VERIFIED — `cxx_build` / cmake-rs path exercised |
| `android-ndk-cross` | 299 | PARTIAL — rustup prerequisite cleared; NDK/adb absent |
| `ffi-debugging` | 296 | VERIFIED — commands run on real artefacts |
| `model-downloads` | 253 | VERIFIED — full 491 MB download, sha256 recorded |
| `stable-diffusion-cpp-build` | 265 | VERIFIED to configure — ggml submodule resolved |

## VERIFIED — llama.cpp end to end

```
git clone --depth 1 https://github.com/ggml-org/llama.cpp /tmp/llama.cpp
cmake -B build -DGGML_NATIVE=ON -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=ON
cmake --build build -j4
```

- clone exit `0`, configure exit `0`, build exit `0` (build id `b1-2145525`)
- artefacts: `build/bin/llama-cli`, `build/bin/libllama.so -> libllama.so.0 -> libllama.so.0.5.0`
- real decode, exit `0`, completion `Hello.`
- **Prompt 40.0 t/s · Generation 6.2 t/s** at `-n 16 -t 4`

### Measurement honesty (rule 5)

That rate is a **desktop x86-64 CPU sandbox** figure with 4 threads. It is **not**
device TTFT, **not** phone tok/s, and **not** a battery result. It is recorded here
only to prove the decode path works end to end. Device-class numbers remain
`NOT MEASURED` until a real on-device run produces an artifact.

## VERIFIED — model artifact

| Field | Value |
|---|---|
| File | `qwen2.5-0.5b-instruct-q4_k_m.gguf` |
| Bytes | `491400032` (matches the `Content-Length` recorded when the skill was written) |
| Magic | `GGUF` |
| sha256 | `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db` |
| Loaded ftype | `Q4_K - Medium` (matches requested quant) |

## VERIFIED — CXX bridge

A real project with a shared struct by value, `Vec<String>` across the boundary and a
`UniquePtr<Engine>` factory compiled and ran clean on `rustc 1.98.1` (meets the
cxx 1.88 floor). Shim symbols present: `cxxbridge1$202$Engine$run`,
`cxxbridge1$202$Engine$backends`, `cxxbridge1$202$new_engine`.

## BLOCKED — android-ndk-cross (raw reasons)

Blocked on prerequisites, **not** on a documentation error. The skill's step 1 is
now satisfied; the sysroot is not.

```
rustup 1.29.1 installed -> which cargo = /home/arafat/.cargo/bin/cargo   PASS
rustup target list | grep aarch64-linux-android  -> available           PASS
ANDROID_NDK_HOME = <unset>                                              BLOCKED
adb              = not found                                            BLOCKED
```

No NDK, no `adb`, and per instruction neither was to be installed. Every other
stage of that skill remains unexecuted. The skill documents the prerequisites
rather than claiming a result.

## VERIFIED — native runtime phases 1-6, 9, 10

Committed on `native-runtime`, all unpushed (no token yet).

| Phase | Deliverable | Evidence |
|---|---|---|
| 1 | workspace + C ABI + kernels | headers compile standalone; kernels 68/68; cargo check + clippy 0 |
| 2 | libllama linked, CPU decode | 20/20 ABI checks, `" Hello."`, 383.7 ms / 16 tok |
| 3 | provider pool | 59 core tests: rotation, parking, dead keys, breaker, failover |
| 4 | agent loop | all six intents classify with no LLM call; Time served locally |
| 5 | memory | identifiers survive compaction; 20 turns fit a 4K window |
| 6 | verification | numeric/contradiction/citation guards; same-model refused |
| 9 | plugins + skills | panic isolation + quarantine; 1% listing cap enforced |
| 10 | server | live curl, all five endpoints |

`cargo test --workspace` 74 passed, 0 failed. `cargo clippy` 0 warnings.

### Live server evidence

```
GET  /health              -> {"status":"ok", ...}
POST /v1/chat/completions ("what time is it?")
    -> served_locally:true, provider:"local-clock", 13 tokens total
POST /v1/completions      -> HTTP 501 (logprobs honestly unimplemented)
POST /v1/chat/completions with an EMPTY provider pool
    -> HTTP 503 "no provider available: every provider is dead, parked,
               or has no live key"
POST detached:true        -> {"stream_id":"chatcmpl-3"} returned immediately
```

The 503 is the load-bearing result: an empty pool produces a refusal with an
actionable reason, never an invented answer.

## Four FFI defects found by a gate rather than by reading

1. **Function symbol collision.** The wrapper's `llama_free` and libllama's
   `llama_free` share a name. The inner teardown call bound to the wrapper's
   own function: infinite recursion, stack exhaustion, SIGSEGV at teardown
   with all 20 functional checks already passing.
2. **Heap corruption.** `llama_batch.seq_id` is `llama_seq_id**`. Reassigning
   it to `std::vector` storage made `llama_batch_free` free a non-heap
   pointer ("free(): invalid pointer" + core dump).
3. **Type collision.** The wrapper declared `struct llama_context`, also
   llama.cpp's name. C++ treats a redeclared struct in one namespace as the
   SAME type, so `llama_free()` instantiated the wrapper's destructor on the
   library's object and segfaulted inside `~basic_string`. Renaming the
   functions was not enough; the type had to become `gs_llama_ctx`.
4. **Missing build define.** `build.rs` never defined `GS_LLAMA_HAVE_LLAMA`,
   so the wrapper compiled in its "libllama not linked" mode while Rust linked
   `libllama.so`. `LlamaModel::load` returned a handle that could never
   generate. `load` now verifies the backend initialised rather than trusting
   a non-null handle.

Also: `llama_backend_init`/`free` are process-global and not reentrant, so
every libllama call now goes through a `LLAMA_LOCK`. Load-bearing for the
multi-threaded server, not just the test harness.

## BLOCKED — phases 7, 8 (diffusion), 11

| Phase | Raw reason |
|---|---|
| 7 OCR | PaddleOCR needs a Paddle-Lite NDK cross-compile. `ANDROID_NDK_HOME` unset, no NDK installed (per instruction). |
| 8 diffusion | Needs a ~2.3 GB SD weight download. The procedural SVG path is live and needs no weights. |
| 11 measurement | Provider quota: z-ai account-level 429 and OpenRouter 402 on the tau2 user-sim, recorded in `worklog.md`. No scorecard was produced, because producing one would mean fabricating it. |

## Answers to the two questions, as far as the evidence goes

**Can local-only replace provider calls for simple chat?** Not established, and
not claimed. One 0.5B Q4_K_M model produced a correct one-word answer on CPU.
That demonstrates the *path*, not the capability. The 0.5B is deliberately a
plumbing fixture. Answering it properly needs the 60%-of-traffic question set
run local-only versus pooled with Wilson CIs, which is Phase 11 and is blocked.

**Highest-leverage change?** Not yet measurable; the lever table requires
Phase 11. What the evidence does say: 16 tokens took 383.7 ms on 4 CPU cores,
so on this hardware **latency, not capability, is the binding constraint** for
local chat. That points at speculative decoding and right-sized models as the
first levers to measure, rather than the context-compaction levers the dossier
lists.

## Findings that forced skill corrections

1. **`huggingface-cli` is gone.** `huggingface-hub 2.0.0` dropped the `cli` extra
   (`warning: ... does not have an extra named 'cli'`); only `hf` is installed, and
   `--local-dir-use-symlinks` was removed with it. `model-downloads` rewritten to the
   `hf download` surface.
2. **`llama-cli --no-conversation` no longer exists** — fails with
   `error: invalid argument: --no-conversation`. The current flag is
   `-st` / `--single-turn`. Recorded in `llama-cpp-c-api` with an explicit
   "check `--help`, trust nothing from memory" warning.
3. **cxx requires cxx-mapped types in C++.** A first build using `std::string` /
   `std::vector` fails; the implementation must use `rust::Str` and
   `rust::Vec<rust::String>`. Shared structs must not be redefined in your own
   header, hand-written `.cc` must include the generated header, and C++
   constructors are not auto-mapped. Six new pitfalls added.
4. **stable-diffusion.cpp needs its `ggml` submodule.** `git clone --depth 1`
   leaves `ggml/` empty and cmake fails with `ggml-impl.h is required` — a message
   that reads like a cmake variable bug. Fix recorded as pitfall 1.
5. **System Rust is not rustup.** On this box `/usr/bin/cargo` (Arch system Rust)
   meant `rustup target add` had no effect on the toolchain actually in use.
   `which cargo` is now a mandatory first check in `android-ndk-cross`.

## Provenance note

`ffi-debugging`'s two supplied primary sources were unresolvable placeholders (a bare
`raw.githubusercontent.com/` host and a bare `kdab.com` host). That skill was written
from the standard binutils/glibc/gABI surface and its Source Links section states this
explicitly rather than presenting invented URLs as sources.
