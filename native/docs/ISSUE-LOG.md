# ISSUE LOG

Running record, newest last. Read this first.

Branch: `native-runtime`. All commits pushed. Working copy `/tmp/opencode/gsai`
(`/mnt/new_volume/.git/index` is wedged and is not being repaired).

Time is UTC.

## THE WHOLE SUITE IS GREEN — 12/12

Run `36658400508`, x86_64 emulator, Android API 30. Twelve tests, no failures,
no skips, job green.

| test | result | evidence |
|------|--------|----------|
| `a0_selfCheck_reports_a_real_backend` | PASS | `selfCheck after init = context=present backend=available(llama.cpp)`; `isAvailable = true` |
| `a1_chat_returns_real_text` | PASS | `chat -> Hello! How can I assist you today?` (34 chars) |
| `a1b_chat_answers_the_question_it_was_asked` | PASS | asked `What is the capital of France? Answer with one word.` → `Paris.` |
| `a2_ocr_reads_the_fixture` | PASS | `ocr -> INVOICE INV-4471 DUE 2026-03-01` |
| `a3_download_refuses_without_consent_resumes_and_verifies` | PASS | `Refused(reason=on-device AI has not been enabled)` then `Allowed(model=...qwen2.5-0.5b...)` |
| `a4_resume_continues_from_the_partial_file` | PASS | `seeded partial = 4096 bytes`, resumed, SHA-256 matched the catalogue |
| `a5_prefer_local_is_off_by_default` | PASS | `stored preferLocal = false` — and it now ASSERTS this; it used to be `assertTrue("settings are readable", true)` |
| `a6_the_chat_screen_path_answers_from_the_engine` | PASS | see below |
| `failureIsAnExceptionNotAnEmptyString` | PASS | `chat("hello")` returns text, `chat("")` raises |
| `libraryLoadsAndExportsBuildInfo` | PASS | — |
| `ocrReturnsTextOnAFixtureImage` | PASS | `INVOICE INV-4471 DUE 2026-03-01` |
| `shutdownIsIdempotent` | PASS | — |

## THE CHAT SCREEN REACHES THE ENGINE — `a6`

This is the claim the rest of the file exists to support, and until run
`36658400508` nothing tested it: a1/a1b call `GsNative.chat` directly, and they
would pass unchanged in an app whose chat screen had been left unwired.

`a6` drives `ChatRepository.send` — the function the screen calls — with
`preferLocal` on, a model installed, and an `ApiClient` pointed at
`http://127.0.0.1:1`, so the provider path cannot answer and cannot hang. The
device's own words:

    FrontendWiringTest: asked     -> capital of France
    FrontendWiringTest: replied   -> Paris.
    FrontendWiringTest: converse  -> local-f160688f-1b0d-4f25-8464-c1d1dae92190
    FrontendWiringTest: persisted -> [user, assistant]

`Paris.` can only come from the on-device model: the built-in canned responder
has no France branch and falls through to one of three generic templates, none
of which contain the word. The canned responder is untouched and still there.

**arm64-v8a `libgs_ffi.so` publishes**: ELF64 `machine=AArch64`, 4,529,528 B
(run `36561597743`). Same chat template, same `<|im_end|>` strip.

## ALL FOUR ABIs PUBLISH — verified from the bytes

Run `36651304587`, four green jobs. Not taken from the job status: every
artifact was downloaded and its ELF header read.

| ABI | bytes | `file -b` |
|-----|-------|-----------|
| `arm64-v8a` | 4,562,904 | `ELF 64-bit LSB shared object, ARM aarch64, ... for Android 21, built by NDK r25c` |
| `armeabi-v7a` | 3,504,900 | `ELF 32-bit LSB shared object, ARM, EABI5 version 1, ... NDK r25c` |
| `x86` | 5,746,332 | `ELF 32-bit LSB shared object, Intel i386, ... NDK r25c` |
| `x86_64` | 4,985,656 | `ELF 64-bit LSB shared object, x86-64, ... NDK r25c` |

All four export the same ten `gs_ffi_mobile_*` symbols and carry llama.cpp
(`strings` finds `llama.cpp` 13–14 times per binary), and each ships a
`libc++_shared.so` whose machine matches its own ABI — the last point is the
same class of bug that just cost two 32-bit runs.

**Three separate faults, none of them the linker** that `36561597743` was
blamed on:

1. The 32-bit archives **were never built** — the llamacpp matrix had two
   entries, not four. `FAIL: the llamacpp-armeabi-v7a artifact is not in run …`
2. The sysroot **directory** for 32-bit ARM is `arm-linux-androideabi`. The
   workflow asked for `armv7a-linux-androideabi`, which is the Clang *target*
   triple. The runner printed its own sysroot listing to prove it:
   `aarch64-linux-android  arm-linux-androideabi  i686-linux-android  x86_64-linux-android`
3. The x86 arch gate compared `MACH[0x03]=="i386"` against `WANT["x86"]=="i686"`.
   **A check that could only fail**: 9 objects, 9 wrong, on a correct build.


## iOS — first ever build of the app

Nothing here had run before commit `17ad0b5`. `ios-native` built a green
`GsFfi.xcframework` and stopped; `ios/project.yml` never referenced it, so
`canImport(GsFfi)` was false in every build and `GsNativeLoader.probe()` returned
`.failed(.libraryMissing(...))` — a supported configuration and a green result.

| run | what happened |
|-----|---------------|
| `36659050115` | `xcode-select: error: invalid developer directory Xcode_16.2.app` — I hardcoded a toolchain the image does not have |
| `36660102564` | slice check grepped for `ios-simulator`; the real name is `ios-arm64-simulator`. Both slices were present |
| `36662206901` | **first compile.** 5 errors, all pre-existing, never compiled before |
| `36663379374` | compiles clean; **first link**: 21 undefined symbols, all libc++ |
| `d3f8033` | added `-lc++ -ObjC++` to both targets. not yet run |

The four Swift bugs the first compile found, none of them new code:

- `gs_llama_free_text` is an **internal C++ symbol** in `llama_wrapper.h`, not
  part of the mobile ABI. The xcframework publishes only `gs_abi.h` and
  `gs_mobile.h`, neither of which declares it. The ABI's own `gs_free_string` is
  the correct call (`gs_abi.h:53`: "Every `gs_free_*` in other headers forwards
  here so callers only need one free convention").
- `GsNative.loadedModelPath`, three sites, all the wrong receiver. The member is
  on `GsNativeLoader`; inside `GsNativeLoader` the call must be `GsNative.loadedModel`.
- `private enum Result` was not `Equatable`, so `guard resolve() == .ready`
  could not compile. `Reason` already was, so the conformance is derivable.

Check added after: every `gs_*` function the Swift bridge calls is declared in one
of the two headers the xcframework publishes. That is what would have caught
`gs_llama_free_text` before the build did.


## iOS — the app builds, links, and its tests RUN

Run `36671474326`, both jobs green. Before this session nothing in `ios/` had
ever been compiled: `canImport(GsFfi)` was false, `probe()` returned
`libraryMissing`, and that is a supported configuration, so it read green.

The chain to get here, each run failing at the next thing:

| run | what it showed |
|-----|----------------|
| `36659050115` | `xcode-select: invalid developer directory Xcode_16.2.app` — hardcoded, not on the image |
| `36660102564` | slice check grepped `ios-simulator`; real name `ios-arm64-simulator`. Both slices WERE there |
| `36662206901` | **first compile** — 4 pre-existing Swift bugs |
| `36663379374` | **first link** — 21 undefined symbols, all libc++ |
| `36664200219` | no x86_64 simulator slice; runner was Intel |
| `36665244922` | `x86_64-apple-ios-sim` is not a Rust target |
| `36666106903` | `assert_arch_member.sh` got a LABEL where the ARCH goes, so arch defaulted to arm64 |
| `36667019000` | `ar` cannot read a fat archive |
| `36667890600` | `lipo -thin` on a thin library is an error |
| `36668750491` | xcframework **green + uploaded**; consumer job failed on a hardcoded slice name |
| `36669723190` | app **compiles and links**; test bundle missing `@testable import GSApp` |
| `36670596253` | 2 methods `throw XCTSkip` without `throws` |
| `36671474326` | **all green** |

All 7 GsNative tests, first execution ever:

```
passed  testFrameworkIsReachableOrAbsentCleanly          0.011s
passed  testShutdownIsIdempotent                         0.002s
skipped testChatReturnsNonEmptyText                      0.016s
skipped testEmbedImageRejectsShortBuffer                 0.211s
skipped testFailureThrowsRatherThanReturningEmptyString  0.004s
skipped testOcrReturnsTextOnFixture                      0.002s
skipped testSamePromptTwiceIsIdentical                   0.001s
```

Whole `AppTests` bundle: **40 passed, 0 failed, 5 skipped**.

The 5 skips had a cause, in the log:

    GsNativeLoader: native engine unavailable: no GsFfi framework:
      framework imported but no build info

`buildInfo` read `gs_last_error()` — the FAILURE channel — so it returned `""`
whenever no error was pending, which on a healthy engine is always. The framework
was imported and linked; the engine was told it was missing. `gs_mobile_build_info()`
is the function for this and it is in `gs_mobile.h:95`, one of the two headers the
xcframework publishes. Fixed in `8fa8792`.

**That is why the job was green with 5 skips and not caught: the two tests that
could run without an engine both passed.** A green job reported an engine that
reported itself missing.


## arm64 device run — attempted, and it is a hard limit

Run `36676280935`, the one attempt the brief authorised. Everything before the
emulator worked, and each step is its own evidence:

    emulator ABI: arm64-v8a
    total: 102M                                     <- arm64 llama.cpp fetched
    libgs_ffi-arm64-v8a artifact id: 11074441047
    ELF machine: AArch64  (ABI arm64-v8a)           <- the shipping .so, from bytes
    sha256: 74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db
    [checksum matched the catalogue]

Then the emulator, in its own words:

    Android emulator version 37.1.11.0 (build_id 15917651)
    FATAL | Avd's CPU Architecture 'arm64' is not supported by the QEMU2
           emulator on x86_64 host. System image must match the host architecture.
    [command].../emulator -port 5554 -avd test ...
    adb: device 'emulator-5554' not found
    error: could not connect to TCP port 5554: Connection refused

This is not a misconfiguration and no flag fixes it. `/dev/kvm` EXISTS on the
runner and is usable:

    crw-rw-rw- 1 root kvm 10, 232 Sep 30 06:03 /dev/kvm
    disable Linux hardware acceleration: false

and that is exactly the point. KVM accelerates a guest of the HOST's
architecture; it is not an architecture translator. arm64 on an x86_64 host needs
full software emulation, and Google's QEMU2 emulator refuses rather than crawling.

**So: no arm64 code path has ever executed, and cannot on this runner.** To run
it, an arm64 host is required -- an arm64 GitHub runner, or any Apple Silicon Mac
with the Android emulator. The `.so` is verified correct from its ELF bytes and
the C/Rust is identical across ABIs, but that is an argument, not a measurement,
and it is recorded as one.

What the attempt did establish: the ABI parameterisation works end to end. The
workflow selected `system-images;android-30;google_apis;arm64-v8a`, fetched the
arm64 llama.cpp and the arm64 `libgs_ffi.so`, and asserted `ELF machine: AArch64`
from the bytes before the APK was ever built.

## STILL OPEN

| item | state | why |
|------|-------|-----|
| arm64 **device** run | **ATTEMPTED — impossible on this runner** | Run `36676280935`. The whole arm64 pipeline works; the emulator refuses to start: `FATAL: Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64 host.` Needs an arm64 host. See below. |
| ~~`armeabi-v7a` / `x86`~~ | **CLOSED** | all four ABIs publish: run `36651304587`, verified from the ELF bytes below. |
| iOS app + XCTests | GREEN | Runs `36671474326` / `36673059524`: the app compiles and links against the xcframework and all 7 tests execute (40 passed, 0 failed, 5 skipped). **The iOS engine is portable-only** — `GS_LLAMA_PREBUILT` is set for Android only, so `gs_mobile_backend_available` is `return 0` by design and the 5 backend tests skip honestly. iOS cannot answer a prompt yet. |
| dynamic feature module | BLOCKED (2/4) | `:ocr-fallback` variant matching; `com.android.dynamic-feature` fixed the first error, the base-variant `applicationId` lookup has not |
| ~~frontend wiring (chat screen → `GsNative`)~~ | **CLOSED** | `a6` drives `ChatRepository.send` on the emulator: `replied -> Paris.`, `persisted -> [user, assistant]`. Run `36658400508`. |
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
14. The 32-bit ABIs were blocked by three faults in sequence — a two-entry
    matrix, `armv7a-linux-androideabi` used as a sysroot *directory*, and an x86
    gate comparing `"i386"` to `"i686"` that could only report failure.
15. `a5_prefer_local_is_off_by_default` asserted `assertTrue("settings are
    readable", true)` — a test that could not fail, in a suite reported 11/11.
16. `a6`'s own cleanup called `ModelStore.removeInstalled()`, which is
    `File(p).delete()`. It would have destroyed the 491 MB GGUF and taken a1,
    a1b and a2 down with it.
17. iOS: `buildInfo` read `gs_last_error()` — the FAILURE channel — so it was
    empty on a healthy engine and `probe()` reported a correctly linked
    framework as missing. `gs_mobile_build_info()` is the right call.
18. iOS: `GsNativeTests.swift` never had `@testable import GSApp`, so it could
    not see the very types it tests. Every other test file had it.
19. iOS: `assert_arch_member.sh` was handed a LABEL where the ARCHITECTURE goes,
    so `EXPECT` silently defaulted to arm64 and a correct x86_64 build was
    rejected. The two pre-existing call sites passed only because arm64 is the
    default -- nothing was being checked.
20. iOS: `GS_LLAMA_PREBUILT` is set for Android and never for iOS, so the iOS
    engine is portable-only and `noBackend` is correct. Recorded as a gap, not
    hidden behind a green job.
