# ISSUE LOG

Running record, newest last. Read this first.

Branch: `native-runtime`. All commits pushed. Working copy `/tmp/opencode/gsai`
(`/mnt/new_volume/.git/index` is wedged and is not being repaired).

Time is UTC.

## THE WHOLE SUITE IS GREEN — 12/12

Run `36707513732`, x86_64 emulator, Android API 30. **Fourteen** tests, no
failures, no skips, job green. (Re-confirmed after the ABI was made a parameter
and after two new tests were added, so this is not a run from before either
change.)

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
| `a7_the_switch_actually_routes_off_and_on` | PASS | `OFF -> — GS backend error (HTTP 0) —` and `ON -> Paris.` — the two branches DIFFER, so the flag routes |
| `a8_the_canned_responder_still_answers` | PASS | `arm = GenuineUnreachable (streamLocalReply REACHED)` — the canned responder ran. **Three earlier versions proved nothing**; see below |
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
    FrontendWiringTest: converse  -> local-0893dde7-ebaa-4e87-89ef-f87bc56cbc5c
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


## A REAL PRODUCT BUG: the switch did not gate the engine

`ChatRepository.streamLocalReply` is the path taken when the **provider fails**.
It read:

    val reply = nativeReply(prompt) ?: localReply(prompt)

unconditionally. `nativeReply()` calls `GsNative.chat`. So with "Prefer on-device
AI" **OFF** — the default, and the setting a user turns off precisely to stop
prompts reaching an on-device model — an unavailable network was enough to send
the prompt there anyway.

That is the opposite of the property the switch exists for, as commit 1(b) says
in the same file:

> With "Prefer on-device AI" off — which is the default — this is a single false
> and everything below is byte-for-byte the routing that shipped.

Now gated on `SettingsStore.preferLocal`. The canned responder is **not**
removed: a phone with no model, a build with no engine, or a generation that
throws must still answer.

**Twelve green tests never caught this.** a1 and a6 both run with the engine
available and neither reaches `streamLocalReply`; a6 exercises `streamLocalFirst`,
the local-**first** branch, which was correctly gated. The provider-**failed**
branch had no test at all.

Proven by a7, whose two branches differ on a real device:

    LocalFirstToggleTest: OFF -> — GS backend error (HTTP 0) —
    LocalFirstToggleTest: ON  -> Paris.

The `assertNotEquals(off, on)` is what makes it a routing test: two independent
assertions pass whether the flag is wired up, inverted, or ignored.

### a8 was a false pass, and the fix for it is in the file

My first a8 pointed `ApiClient` at `127.0.0.1:1`, which produces
`SendFailure.BackendError`, and that arm of `send()` **returns at that line**
without ever calling `streamLocalReply`:

    is SendFailure.BackendError -> {
        val serverText = … ?: "GS backend error (HTTP ${failure.status})"
        …
        return activeId
    }

So it never called `localReply()` once, and `assertTrue(reply.isNotBlank())` was
satisfied by the error marker itself. Run `36707513732` printed the identical
string for both a8 and a7's OFF branch — that is what exposed it.

a8 now serves a real chunked SSE response from a raw `ServerSocket` and hangs up
mid-stream, which is the case `localReply()` exists for, and asserts the
responder's text is present and the error marker is not. No new dependency:
`build.gradle.kts` has only the ktor **client** artifacts, and adding a server to
the app for one test is the wrong trade.

## ONE COMMIT, THREE CHANGES: `eb6995a`

`git add -A` put three unrelated fixes under a message that describes one of them,
so this is the map of what is actually in it. Recorded because the commit message
is not the whole record and a reader diffing `eb6995a` against its subject line
would otherwise find two changes they were not told about.

| Change | What it does |
| --- | --- |
| `fix(79)` | moves the `GGML_BLAS` comment out of the backslash-continued cmake command and adds `.github/scripts/check_continuation_comments.py`, which runs first in the `llama-ios` job |
| a8 Kotlin | `runCatching` moved inside `runBlocking`, and `io.ktor.http.contentType` imported — the two errors run `36728958062` reported |
| `test(80)` | `testBuildInfoNamesTheLlamaBackend`, the first assertion that the iOS build contains llama.cpp at all |

The rule going forward: commit one concern at a time, with explicit paths, never
`git add -A` on a branch where three unrelated things are in flight.

## THE PRODUCT WAS NEVER BROKEN: `classifySendFailure` is correct

a8 spent three runs printing the same string, `— GS backend error (HTTP 0) —`,
and three times it looked like the same product defect. It was not. Run
`36730290869`'s measurement settles it:

    CannedResponderTest: connect failure = java.net.ConnectException
    CannedResponderTest:   is ConnectException=true

and `classifySendFailure` is:

    val unreachable = e is UnknownHostException || e is ConnectException || ...
    return if (unreachable) GenuineUnreachable else BackendError(0, null)

A `ConnectException` reaches `GenuineUnreachable`, whose arm emits
`— Offline — cannot reach GS. —`. The string the run printed is emitted by the
`else`. So `unreachable` was **false** in the repository's path while the same
predicate was **true** in the test's.

The difference was the test's own client. a8 built `HttpClient(CIO)` with no
plugins; the app's real one (`di/ServiceLocator.kt:40`) installs
`ContentNegotiation { json(GsApiJson) }`, and `sendMessageStream` sends
`setBody(SendMessageRequest(...))`. With nothing to serialize that
`@Serializable` class with, the request fails **before it connects**, and the
exception is not a `ConnectException`.

Three harnesses, one identical output:

| # | Harness | Why it never reached `localReply` |
| --- | --- | --- |
| 1 | `127.0.0.1:1` | `BACKEND_ERROR` returns before `streamLocalReply` |
| 2 | `ServerSocket` hanging up mid-stream | `MidStreamCut` never calls `streamLocalReply`; one call site, at line 486 |
| 3 | bare `HttpClient(CIO)` | the request never reaches the network |

A marker that appears for three unrelated reasons is a marker telling you about
the test. Both routing tests now use `gsHttpClient(...)`, the app's own factory.

## a8 PASSES, AND IT TOOK THREE WRONG MECHANISMS TO GET THERE

Run `36732807926`. Fourteen tests, no failures, no skips. The line that matters:

    CannedResponderTest: arm = GenuineUnreachable (streamLocalReply REACHED)

`streamLocalReply` has exactly one call site in the whole file — line 486, in the
`GenuineUnreachable` arm — and this run reached it. `localReply` ran. The canned
responder is proven, on a device, in the branch it exists for.

a7 changed shape too, and the change is the proof that the harness was the
problem:

| | OFF branch | ON branch |
| --- | --- | --- |
| before (`36732807926`'s predecessor) | `— GS backend error (HTTP 0) —` | `Paris.` |
| after | `— Offline — cannot reach GS. —` | `Paris.` |

`ON` is unchanged because it comes from `streamLocalFirst`, which never touches
the network. `OFF` is now the string the product actually emits.

The three wrong harnesses, all of which printed the identical marker and all of
which looked like the same product defect:

1. **`127.0.0.1:1`.** Connection refused is a `SendFailure.BackendError`, and
   that arm returns at the marker line without ever calling `streamLocalReply`.
2. **A `ServerSocket` hanging up mid-stream.** This is the case the responder
   exists for, and it is the wrong one: `MidStreamCut` does not call
   `streamLocalReply` either. It appends "The connection dropped mid-turn".
3. **A bare `HttpClient(CIO)`.** The app's real client installs
   `ContentNegotiation { json(GsApiJson) }`, and `sendMessageStream` sends
   `setBody(SendMessageRequest(...))`. With nothing to serialize that class with,
   the request failed **before it connected**, so the exception was not a
   `ConnectException` and the turn was classified `BackendError(0, null)`.

What exonerated the product was measuring the failure instead of interpreting it:

    CannedResponderTest: connect failure = java.net.ConnectException
    CannedResponderTest:   is ConnectException=true

against code that says `e is ConnectException` ⇒ `GenuineUnreachable`. Two
predicates, same expression, opposite results — so the repository was not
throwing the exception the test was throwing.

## BLOCKED, WITH EVIDENCE: the arm64 device run (item 2)

Three distinct hosts, three distinct measured reasons. Each is a raw line from a
run, and no two of them are the same failure — so this is not one blocker
retried three times, it is the question answered on every runner that can be
reached.

| Run | Host | Result |
| --- | --- | --- |
| `36676280935` | `ubuntu-latest`, x86_64 | `FATAL \| Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64 host.` |
| `36688288136` | `ubuntu-24.04-arm` | real arm64 (`uname -m = aarch64`) but **no `/dev/kvm`** |
| `36735804030` | `macos-15`, arm64 | real arm64, emulator launched, QEMU acquired, then: `HVF error: HV_UNSUPPORTED` / `qemu-system-aarch64-headless: failed to initialize HVF: Invalid argument` |

`ubuntu-24.04-arm` and `macos-15` are both genuinely arm64, and both are refused
by the *hosted runner's* lack of nested virtualisation — `/dev/kvm` on Linux,
Hypervisor.framework on macOS. The architecture was never the obstacle on either.
There is no arm64 runner available to this repository that exposes an
accelerator, so the arm64 emulator cannot boot anywhere reachable.

### What the macOS probe had to fix before it could answer

Three of those runs were cancelled or crashed before reaching a verdict, and each
obstruction was a real defect in the probe rather than news about the runner:

- **`36688948635` / `36688885249` — a false pass.** The boot step began
  `if ! command -v sdkmanager; then ::warning::…; exit 0`, so a green job had
  tested nothing. It now installs the SDK and exits 1 if it cannot.
- **`36728988366` — cancelled at 15m26s**, after a 10-minute system-image
  download, so the 20-minute boot loop could not fit. The SDK is now cached and
  the bound is 12 minutes, and every poll prints.
- **`36731431711` — the step killed itself.** GitHub runs `run:` blocks as
  `bash -e {0}`, so `adb` in a bare assignment terminated the shell. Verified
  against both shapes under `bash -e`.
- **`36733985703` — the wrong emulator binary.** It resolved to
  `$ANDROID_HOME/tools/emulator`, the SDK Tools layout removed in 2017, so the
  emulator derived its Qt path as `../emulator/lib64/qt/lib` relative to the
  checkout's parent and died before QEMU ran. Resolved explicitly now, and the
  step inventories every candidate and every `qemu/*` directory.

The inventory earned its place immediately. It showed the correct binary selected
(`qt? …/emulator/lib64/qt/lib -> PRESENT`) and the 2017 leftover rejected
(`qt? …/tools/lib64/qt/lib -> missing`), and it proved my first guess at the Qt
path was wrong — I had derived `dirname(binary)/../lib64/qt/lib` from a single
relative log line, and the real layout is `dirname(binary)/lib64/qt/lib`. So all
four plausible locations are now probed and all of them printed. Checking the
disk instead of deriving a path from one line is what turned "it crashed" into
"Hypervisor.framework is unavailable".

## ITEM 1 IS DONE: the iOS build has llama.cpp in it

Run `36753918296`, head `fefa133`, **all three jobs green**:

| Job | Result |
| --- | --- |
| `llama.cpp for iOS (device + both simulator archs)` | success |
| `xcframework` | success |
| `app + XCTests (iOS simulator)` | success |

    passed=41 failed=0 skipped=5
    VERIFIED: 41 passed, 5 skipped on the iOS simulator

and the line that is the whole point of the item, from a test that EXECUTED:

    Test Case '-[AppTests.GsNativeTests testBuildInfoNamesTheLlamaBackend]' started.
    GsNative.buildInfo -> gs-ffi 0.1.0 +llama
    Test Case '-[AppTests.GsNativeTests testBuildInfoNamesTheLlamaBackend]' passed (0.142 seconds).

`+llama` rather than `portable`. That string is a compile-time constant
(`gs_mobile.cpp:190`), so this is the build reporting what it contains, not a
claim about what it was asked to contain.

### Why it took eleven runs, and what each one was

| Run | What it was |
| --- | --- |
| `36725021214` | `libggml-metal.a` needs `-framework Metal -framework Foundation`; both links, two files |
| `36728962757` | `-DGGML_BLAS=OFF` never reached cmake: a `#` comment sat under a `\` continuation and bash discarded the backslash |
| `36730369437` | `__chkstk_darwin` is only exported from iOS 12; rustc's floor for `aarch64-apple-ios` is 10 and nothing set it |
| `36733985703` | `lipo -create` cannot merge two same-architecture archives — that is an archive job (`libtool -static`) |
| `36735879525` | the symbol check named `llama_model_load` etc., which are C++ and therefore mangled at the pinned commit |
| `36740298082` | `VAR=$(nm … \| wc -l)` under `set -e` killed the step, and `2>/dev/null` ate the reason |
| `36742545988` | `pipefail` + `grep -q` reported successful matches as misses — the check was non-deterministic |
| `36745378533` | both simulator merges read an already-fat library, so a file called `-sim-arm64.a` contained x86_64 |
| `36747684730` | `sed … \| head -12` under `set -e`: the diagnostic line killed the step while printing a diagnostic |
| `36750432883` | the new gate itself imported `yaml`, which the macOS runner does not have |
| `36751766601` | I had lowered the APP's iOS floor to 15.0 to fix the LIBRARY's, and `PhotosPickerItem` stopped compiling |

Two of those eleven were defects in the machinery written to detect defects in
the machinery. Both are now lints that run before the cross-compile:
`check_continuation_comments.py`, `check_pipefail_traps.py`, and
`check_ios_deployment_target.py`.

### What the 5 remaining skips need, and why they are honest

    GsNativeTests testBuildInfoNamesTheLlamaBackend              passed
    GsNativeTests testFrameworkIsReachableOrAbsentCleanly        passed
    GsNativeTests testShutdownIsIdempotent                       passed
    GsNativeTests testChatReturnsNonEmptyText                    skipped   no GGUF
    GsNativeTests testSamePromptTwiceIsIdentical                 skipped   no GGUF
    GsNativeTests testFailureThrowsRatherThanReturningEmptyString skipped  isAvailable
    GsNativeTests testEmbedImageRejectsShortBuffer               skipped   isAvailable
    GsNativeTests testOcrReturnsTextOnFixture                    skipped   no fixture

Three of them skip on a model, and two skip on `isAvailable`, which is
`ensureLoaded() && GsNative.backendAvailable()` — and `backendAvailable` is
guarded by `guard let context`, so it is false on a correct build with no model
provisioned. So all five are downstream of ONE missing thing: a 0.5B GGUF in the
simulator's Application Support. No weights are in git, and there is no step that
provisions one, which is the next thing to do.

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
21. The device workflow's artifact lookups compared against the LITERAL string
    "llamacpp-$ABI" / "libgs_ffi-$ABI" -- they were inside single quotes, so the
    shell never expanded `$ABI`. The lookup could never match, for any ABI. The
    arm64 run reported an artifact missing while printing it as available, in the
    same step.
22. `ABI` was used in three steps that did not declare it. Each `run:` block is a
    fresh shell, so those three would have expanded to empty and produced
    `native/prebuilts/android-/` and `jniLibs//libgs_ffi.so`.
23. A backslash continuation that runs into a `#` comment line ends the command
    there, so comments left inside `$( ... )` made the pipe a separate command.
    Third trap of this family in this repo, after the YAML column-0 trap and the
    flattened continuation.
24. iOS: `libggml-metal.a` references Metal and Objective-C symbols that the
    cargo link does not resolve, because `cc` is the linker driver and
    `ios/project.yml`'s `OTHER_LDFLAGS` only reaches xcodebuild. Both links need
    `-framework Metal -framework Foundation`, from different files.
25. a8 asserted on the backend-error marker and never called the canned
    responder; the arm that emits that marker returns before `streamLocalReply`.
26. iOS `llama-ios` job lost 7 minutes to `bash -n`-invisible syntax: a `#`
    comment directly under a `\` continuation, which bash reads as a comment and
    thereby discards the backslash. `-DGGML_BLAS=OFF` was never passed to cmake.
    Now linted, and the lint is verified against the failure.
27. a8's measurement block did not compile: `runCatching`'s block is `() -> R`,
    not `suspend () -> R`, and `contentType` is an `io.ktor.http` extension. A
    file that only compiles on a 30-minute emulator workflow reports its compile
    errors an hour after they are written.
28. Nothing asserted that the iOS build contains llama.cpp. `backendAvailable`
    needs a context, `isAvailable` reports the framework, `initialize` needs a
    GGUF, so a portable iOS build was indistinguishable from a llama one and
    every engine-backed test skipped on both.
29. The iOS cargo link asked for `__chkstk_darwin`, which libSystem only exports
    from iOS 12.0 onward, while rustc's built-in floor for `aarch64-apple-ios` is
    10.0 and nothing here set it. `IPHONEOS_DEPLOYMENT_TARGET=15.0` in three
    places now, and asserted with `rustc --print deployment-target`.
30. The first version of that assertion FAILED OPEN: rustc prints
    `IPHONEOS_DEPLOYMENT_TARGET=10.0`, and stripping only `iOS ` left
    `IPHONEOS_DEPLOYMENT_TARGET=10`, so `[ ... -lt 12 ]` errored to exit 2 and
    the check accepted a 10.0 build. A check that fails open is worse than none,
    because it is reported as a pass.
31. a7 and a8 built `HttpClient(CIO)` with no plugins, so
    `setBody(SendMessageRequest(...))` had no `ContentNegotiation` to serialize
    it and the request failed before connecting. The product's
    `classifySendFailure` was correct throughout.
32. The arm64 probe ran `$ANDROID_HOME/tools/emulator` — the Android SDK Tools
    layout removed in 2017 — so the emulator derived its Qt library path as
    `../emulator/lib64/qt/lib` relative to the checkout's parent and died before
    QEMU ran. The architecture was never tested in that run.
33. GitHub executes `run:` blocks as `bash -e {0}`, so a step's own
    `set -uo pipefail` ADDS to an `-e` that is already on. `adb` inside an `if`
    condition is safe; `adb` in a bare assignment terminates the shell, which is
    what killed the poll loop on its first iteration.
34. Item 2 is BLOCKED with evidence: `macos-15` gives `HV_UNSUPPORTED` from
    Hypervisor.framework, `ubuntu-24.04-arm` has no `/dev/kvm`, and `x86_64`
    rejects the architecture outright. No reachable arm64 runner exposes an
    accelerator.
35. `lipo -create` cannot merge two same-architecture static archives -- "have the
    same architectures (arm64) and can't be in the same fat output file". The iOS
    slice merge is an archive job (`libtool -static`); lipo is correct only for
    the two-architecture simulator combine.
36. The five remaining iOS skips are all downstream of one missing thing: no step
    provisions a 0.5B GGUF into the simulator's Application Support, and
    `GsNativeLoader.isAvailable` is false without one even though the build now
    reports `+llama`.
37. I lowered the app's iOS deployment floor from xcodebuild's default (16.0+) to
    15.0 while fixing the library's, and `PhotosPickerItem` stopped compiling.
    The two floors are independent; a library built for 15.0 links into a 16.0 app.
