# ISSUE LOG

Running record, newest last. Read this first.

Branch: `native-runtime`. All commits pushed. Working copy `/tmp/opencode/gsai`
(`/mnt/new_volume/.git/index` is wedged and is not being repaired).

Time is UTC.

## THE WHOLE SUITE IS GREEN — 11/11

Run `36643948566`, x86_64 emulator, Android API 30. `Starting 11 tests`, no
failures, no skips, and the job is green.

| test | result | evidence |
|------|--------|----------|
| `a0_selfCheck_reports_a_real_backend` | PASS | `selfCheck after init = context=present backend=available(llama.cpp)`; `isAvailable = true` |
| `a1_chat_returns_real_text` | PASS | `chat -> Hello! How can I assist you today?` (34 chars) |
| `a1b_chat_answers_the_question_it_was_asked` | PASS | asked `What is the capital of France? Answer with one word.` → `Paris.` |
| `a2_ocr_reads_the_fixture` | PASS | `ocr -> INVOICE INV-4471 DUE 2026-03-01` |
| `a3_download_refuses_without_consent_resumes_and_verifies` | PASS | `Refused(reason=on-device AI has not been enabled)` then `Allowed(model=...qwen2.5-0.5b...)` |
| `a4_resume_continues_from_the_partial_file` | PASS | `seeded partial = 4096 bytes`, resumed, SHA-256 matched the catalogue |
| `a5_prefer_local_is_off_by_default` | PASS | `stored preferLocal = false` |
| `failureIsAnExceptionNotAnEmptyString` | PASS | `chat("hello")` returns text, `chat("")` raises |
| `libraryLoadsAndExportsBuildInfo` | PASS | — |
| `ocrReturnsTextOnAFixtureImage` | PASS | `INVOICE INV-4471 DUE 2026-03-01` |
| `shutdownIsIdempotent` | PASS | — |

**arm64-v8a `libgs_ffi.so` publishes**: ELF64 `machine=AArch64`, 4,529,528 B
(run `36561597743`). Same chat template, same `<|im_end|>` strip.

## STILL OPEN

| item | state | why |
|------|-------|-----|
| arm64 **device** run | NOT ATTEMPTED | an `arm64-v8a` system image on an `x86_64` runner has no KVM. Every result above is x86_64, which is **not the shipping ABI**. No arm64 code path has ever executed. |
| `armeabi-v7a` / `x86` `.so` | FAILING | run `36561597743`. 32-bit ABIs, undiagnosed. |
| iOS build + simulator tests | NOT STARTED | never attempted |
| dynamic feature module | BLOCKED (2/4) | `:ocr-fallback` variant matching; `com.android.dynamic-feature` fixed the first error, the base-variant `applicationId` lookup has not |
| frontend wiring (chat screen → `GsNative`) | NOT VERIFIED | the engine works and the tests call it directly; no test taps the real UI |
| native OCR / Tesseract | NOT LINKED | `gs_mobile_ocr` is a stub: "no OCR engine is compiled into this mobile build". OCR ships through ML Kit in the app module, which is the decided primary. Tesseract is cross-compiled and verified but not in the `.so`. |

## FIXED, in the order they were found

1. `GS_LLAMA_PREBUILT` was never set in CI — the shipped `.so` was **portable-only**, `isAvailable = false`.
2. `libc++_shared.so` was missing, so the `.so` would not `dlopen`.
3. llama.cpp was pinned to the wrong commit; the wrapper did not compile against it.
4. `LLAMA_BUILD_APP` defaults ON and built a CLI whose deps were disabled.
5. `ggml.h` was at `ggml-include/`, not `ggml/include/`.
6. `lib` prefix doubled: `rustc-link-lib=static=libggml` → `liblibggml.a`.
7. Archive names sorted alphabetically, the reverse of static link order.
8. arm64 was built for the **host** — a hand-written toolchain file with no `--target`.
9. **`"llamacpp-x86_64"` hardcoded** in the artifact selector while the paths beside it were parameterised, so every ABI downloaded x86-64 archives.
10. Chat was never **templated** — the raw prompt went to a raw-completion generator, so an Instruct model completed a document instead of answering.
11. `GsNativeException` **did not exist**; every native failure was a silent null reaching a caller declared `: String`.
12. `MODEL_1_5B` shipped with the **0.5B's SHA-256**, so every HIGH-tier device would fetch 1.05 GB and fail verification.
13. The OCR fixture's 5x7 font could not render an unambiguous `V`; four attempts, ending with the JDK's AWT.

## WHAT WAS WRONG AND REVERTED

- A "bolding" pass on the fixture was shipped as a fix and **was a no-op**: 25,664 ink pixels at `bold=1`, `2` and `3` alike. The verification printed unchanged pixels and it was read as evidence they had changed. Reverted in `ae4a909`.
- It was claimed that `assert_arch_member.sh` was defective. It was right — the archives it checked were a *different artifact* from the one the linker read, and the earlier inspection used the wrong artifact ID.
- A `CMAKE_SYSTEM_PROCESSOR` gate was shipped. It is derived from `-DANDROID_ABI`, so it reports whatever was asked for and cannot fail. Replaced by `.github/scripts/assert_archive_arch.py`, tested in both directions against real artifacts.
- It was asserted that Pillow would be on the runner. It is not: `Pillow path failed (No module named 'PIL'); using the fallback font`.
