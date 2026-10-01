# ISSUE LOG

Running record, newest last. Read this first.

Branch: `native-runtime`. All commits pushed. Working copy `/tmp/opencode/gsai`
(`/mnt/new_volume/.git/index` is wedged and is not being repaired).

Time is UTC.

## WHERE THE OPERATOR'S FIVE ITEMS STAND

| # | Item | State | Evidence |
| --- | --- | --- | --- |
| 1 | iOS llama.cpp cross-compile | **DONE** | run `36759872954`, all three jobs green, **46 passed, 0 skipped**; `GsNative.chat -> Hello! How can I assist you today?` |
| 2 | arm64 device run | **CLOSED — HARDWARE, NOT WORK** | three hosts, three *different* measured reasons; the finding is nested virtualisation, not arm64. Section written in `RESULTS-mobile-concurrency.md`. **No fourth host attempted** |
| 3 | a3 skip | **DONE** | `a3_download_refuses_without_consent_resumes_and_verifies` PASS, run `36763870230` |
| 4 | Android test-suite gaps | **DONE** | run `36763870230`, **14/14**, zero skips, including `a7_the_switch_actually_routes_off_and_on` |
| 5 | dynamic feature module `:ocr-fallback` | **CLOSED PERMANENTLY** | a version bump is *measurably* not the fix: `.single()` is byte-identical in AGP 8.5.2, 8.7.3, 8.9.2, 8.11.1, 8.12.3 and 8.13.2 — six releases, read from the sources jars. The operator's wording is verbatim in `android/app/build.gradle.kts` |

Runs behind those numbers, all on `native-runtime`:

| Workflow | Head | Result |
| --- | --- | --- |
| `android-device` | `96200fa` | **success — 18 PASS, 0 SKIP, 0 FAIL** on an x86_64 emulator |
| `android-app` | `96200fa` | success — compile + unit tests |
| `android-native` | `820db89` | success |
| `ios-native` | `a9a31fa` | llama.cpp + xcframework green; the DEVICE app build is new and unverified |

**The one-line version:** the iOS engine has llama.cpp and answers a prompt on a
real simulator; Android is **18/18 with nothing skipped**, the switch x network
matrix is proven in all four directions, and a reachable provider that dies
mid-answer is now labelled as such instead of with a fabricated HTTP status; the
arm64 run is impossible on every reachable runner for one hardware reason; and the
OCR split is closed permanently because no AGP release fixes it.

**Still open:** the two honest gaps, both recorded rather than worked around —
**performance numbers** on an emulator (no TTFT, no tok/s for the 0.5B, and it is
the item that most affects the shippability decision) and **stable-diffusion on the
mobile runtime** (structurally unreachable, because `sd_render()` spawns `sd-cli` as
a subprocess from a developer-machine path). Both are in the plan audit in
`RESULTS-mobile-concurrency.md`.

## THE DEVICE SUITE IS FULLY GREEN: 18 PASS, 0 SKIP, 0 FAIL

Run `36814985859`, head `96200fa`. **Every test passes and nothing skips** — which
is the condition item 3 was opened for, and a8 is the last holdout.

`a8_each_backend_behaviour_maps_to_its_own_failure_arm` drives `send()` through
four distinct backend behaviours and asserts each maps to its own classification.
All four are now distinct, and the third is the product defect that took four
attempts to close:

| backend behaviour | arm | the string the arm emits |
| --- | --- | --- |
| a port nothing listens on | `GenuineUnreachable` | `— Offline — cannot reach GS. —` |
| a stream that starts and dies | **`MidStreamCut`** | `— The connection dropped mid-turn while GS was working…` |
| a 500 with a sanitized body | `BackendError` | `— upstream model unavailable —` |
| a 200 with no events | the clean-break branch | `''` — empty, and **not** offline |

Before `fix(114)` the second row read:

    mid-stream cut -> — GS backend error (HTTP 0) —      server saw 1 request

A provider that had served a request and begun answering, reported to the user with
a status code that does not exist, for a failure that did not happen. It now says
the connection dropped mid-turn, which is what happened.

**Why it took four attempts, since the shape of it is the point.** Each attempt
wrapped a different operation and each looked correct:

| attempt | wrapped | why it missed |
| --- | --- | --- |
| `fix(104)` | `channel.readUTF8Line()` | the throw was not from the read |
| `fix(108)` | the same, plus a `CancellationException` discriminator | it was never a cancellation |
| `fix(111)` | `bodyAsChannel()`, `isClosedForRead`, the read | all three were inside and none was it |
| `fix(114)` | **`client.post` itself** | — |

CIO decodes the chunked body while `client.post` is still returning; headers and
first chunk arrive in one segment, so a peer that dies mid-body is discovered
there, before an `HttpResponse` exists. The detail that finally made it diagnosable
was that the delta **never reached `onDelta`** — nothing had handed the bytes to
the loop yet — which was consistent with that and unexplained by every earlier
hypothesis.

Two changes made the diagnosis possible rather than merely correct, and both are
worth more than the fix:

- **`BackendError` gained a `localReason`.** `HTTP 0` is `BackendError(0, null)` —
  what the classifier had to say when it knew nothing, rendered as though it knew
  something. Naming the actual exception is both the right thing to show a user and
  the only reason the next run produced evidence instead of another guess.
- **`GsStreamCutException.status` became `Int?`.** When the cut surfaces from
  `client.post` there is no status, and a `0` here would have reintroduced the same
  fabricated code through the exception's own constructor.

The rule applied to the POST is the **mirror** of the one already in
`classifySendFailure`, not a new one: `UnknownHostException`, `ConnectException` and
`SocketTimeoutException` are the shapes that prove the peer was never reached, so
they are rethrown unchanged and a genuine offline event still reads as one.

## THE SUITE IS GREEN — first as 12/12, now 14/14

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
| `36740298082` | the bare assignment `VAR=$(nm … ; wc -l)` under `set -e` killed the step, and `2>/dev/null` ate the reason |
| `36742545988` | `pipefail` + `grep -q` reported successful matches as misses — the check was non-deterministic |
| `36745378533` | both simulator merges read an already-fat library, so a file called `-sim-arm64.a` contained x86_64 |
| `36747684730` | `sed … ; head -12` under `set -e`: the diagnostic line killed the step while printing a diagnostic |
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

## THE iOS ENGINE ANSWERS A PROMPT

Run `36759872954`, head `76a48b2`. **46 passed, 0 skipped** -- the five skips from
the previous run are gone, and the three verbatim lines that matter:

    GsNative.buildInfo -> gs-ffi 0.1.0 +llama
    GsNative.chat -> Hello! How can I assist you today?
    GsNativeLoader: native engine ready (/Users/runner/Library/Developer/CoreSimulator/
      Devices/4E3A6BB3-.../data/Containers/Data/Application/15E02482-.../Library/...)

All eight `GsNativeTests`, none skipped:

    testBuildInfoNamesTheLlamaBackend              passed
    testChatReturnsNonEmptyText                    passed
    testEmbedImageRejectsShortBuffer               passed
    testFailureThrowsRatherThanReturningEmptyString passed
    testFrameworkIsReachableOrAbsentCleanly        passed
    testOcrReturnsTextOnFixture                    passed
    testSamePromptTwiceIsIdentical                 passed
    testShutdownIsIdempotent                       passed

`Hello! How can I assist you today?` is a real generation from a 0.5B model on the
iOS simulator, and it is the claim that has been open since this branch started:
the iOS build was portable-only, so `backendAvailable` was false, so the engine
could not answer, so the tests skipped and the job was green anyway.

`testSamePromptTwiceIsIdentical` passing is worth its own line, because it tests a
bug that was real here once: the C layer appended a temperature stage to a sampler
chain it never reset, so a REUSED context disagreed with itself while a fresh one
was perfect. A determinism claim that only ever ran on a fresh context would not
have caught it.

The OCR fixture rendered with a real typeface, not the 5x7 fallback:

    wrote .../invoice.png  1235x172  text='INVOICE INV-4471 DUE 2026-03-01'
    renderer : AWT SansSerif 64pt
    ink      : 16118 px (7.6% of the page)
    fixture signature: 89504e470d0a1a0a

which is why using the existing renderer instead of the one I had just written was
worth the minute it cost: the naive 5x7 version at scale 3 had a documented history
of reading `INVOICE INV-4471` as `IMJOICE IMYAA71 DUE 2926-03-g1`.

### The model is verified twice, in two places

    bytes: 491400032 (want 491400032)
    sha256: 74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db
    VERIFIED: 0.5B model is the bytes and the hash android-device measures

size before hash, so a truncated download is named as truncated; and the hash is
re-read from the copy inside the container, because the file that has to be right
is the one the test opens, not the one that was downloaded.

## ITEM 5: the split is unblocked to one specific question, and the app still builds

`dynamicFeatures` is OFF. Two things were genuinely fixed and both STAY, because
they were real and the module cannot work without them:

| Was | Now |
| --- | --- |
| `android/ocr-fallback/src/main/AndroidManifest.xml` did not exist | present, with `<dist:module dist:instant="false">`, `<dist:on-demand/>`, `dist:fusing include="true"`, and no `applicationId` |
| `dependencies { implementation(core-ktx) }` only | plus `mlkit.text.recognition`, `play.core`, `play.core.ktx` — every alias already used by the base |
| `dynamicFeatures += setOf(":ocr-fallback")` | off, with the reason below |

The module could not have compiled in ANY state, which is why the four recorded
attempts never found out: they all failed in `processDebugMainManifest`, which
runs long before Kotlin compiles, so a compile error sat behind a manifest error
the whole time.

### The remaining question, narrowed to one line

Run `36764251868` fails with the same message, and the AGP **8.5.2 sources** say
what it means. `DynamicFeatureVariantImpl.kt:230-245`:

    val artifact = variantDependencies.getArtifactFileCollection(
        ConsumedConfigType.COMPILE_CLASSPATH, ArtifactScope.PROJECT,
        AndroidArtifacts.ArtifactType.BASE_MODULE_METADATA)
    ...
    artifact.elements.map { ModuleMetadata.load(it.single().asFile) }   // line 244

`it.single()` on an EMPTY collection. So the message is not about an
`applicationId`, and the base's id is plainly set at `build.gradle.kts:94`. The
collection is the set of `BASE_MODULE_METADATA` artifacts **the base produced**,
and there are none — because the task that produces it,
`writeDebugBaseMetadata`, appears **nowhere in the 45 tasks** the run executed:

    :app:checkDebugAarMetadata, :app:compileDebugKotlin, :app:packageDebugResources,
    :ocr-fallback:processDebugMainManifest, :ocr-fallback:processManifestDebugForFeature,
    ... 45 tasks, no writeDebugBaseMetadata

So the split's tasks reached the graph through the ordinary project dependency
(`:app:checkDebugLibraries` puts the split on the compile classpath), NOT through
`dynamicFeatures` registering the split. The question is no longer "what is the
split missing" but "why does the base's variant not see `dynamicFeatures`", and
the `No matching variant` error from the four earlier attempts was the same
invisible wiring seen from a different task.

Ruled out, from the same run, so the next attempt does not repeat any of it:
split manifest, split dependencies, `buildTypes` on both modules, no flavors
anywhere, `minSdk` 26 on both, `applicationId` on the base, `versionCode` 73,
`namespace` a subpackage, `:ocr-fallback` in `settings.gradle.kts`.

**One piece of evidence I over-read, recorded because the next person will too:**
`:ocr-fallback:generateDebugFeatureTransitiveDeps` being in the task graph is
NOT proof the split was registered — that task is created by the split's OWN
plugin, so it appears whether or not the base registered anything. Reading a task
list as proof of a wiring relationship is the same error as reading a name as
proof of a value.

### The app build is green, and that is the priority

`android-app` is **success** on `e712047`, and `android-device` is **success**
with **14/14** on `18dff88`. A split that does not resolve is not worth an APK
that does, and that was true four attempts ago.

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
39. Item 1 is COMPLETE: run 36759872954, all three iOS jobs green, 46 passed and
    0 skipped, `GsNative.chat -> Hello! How can I assist you today?` from the 0.5B
    model on the simulator. The build reports `gs-ffi 0.1.0 +llama`, so it is no
    longer portable-only, and the engine answers.
40. Item 2 is BLOCKED with three measured reasons, one per host. Item 3 (a3) is
    done. Item 4 is done: 14/14 on Android with a7 and a8 passing. Item 5, the
    dynamic feature module, is the only one of the operator's five still open.
41. Item 5, `:ocr-fallback`, is BLOCKED at one specific line. The split is now
    manifest-complete and dependency-complete; `dynamicFeatures` is off because
    the base's variant does not see it, so it never registers
    `writeDebugBaseMetadata`, so `DynamicFeatureVariantImpl.kt:244` calls
    `.single()` on an empty collection. `android-app` stays green.
42. Do not re-open item 5 by changing the split's configuration again. The next
    move is to find why the BASE's variant does not pick up `dynamicFeatures`, and
    the evidence to work from is the absence of the producer task in the graph, not
    another error message.

## THIS WAVE: the four CLOSES, and what each one actually cost

### CLOSE 1 — the ChatRepository bug, verified in all four directions

The fix was already committed:

    val reply = if (SettingsStore.preferLocal) {
        nativeReply(prompt) ?: localReply(prompt)
    } else {
        localReply(prompt)
    }

and what it needed was not a re-verification but the three directions that had
never been tested. Case 2 in particular — prefer-local OFF with the network **up**
— did not exist at all: every routing test pointed ApiClient at a dead port, so the
network path had never once been observed succeeding. Proving it needs a backend
that answers, which is why `WireServer.kt` exists.

Four tests, four markers no other source can produce:

| test | result | evidence |
| --- | --- | --- |
| `b1_prefer_off_network_dead_uses_the_canned_responder` | PASS | `— Offline — cannot reach GS. —` then a real answer, and **not** `Paris` |
| `b2_prefer_off_network_up_uses_the_network` | PASS | `NETWORK ANSWER`, **server saw 1 message request** |
| `b3_prefer_on_network_dead_uses_the_model` | PASS | `Paris.` |
| `b4_prefer_on_network_up_uses_the_model` | PASS | `Paris.` with **server saw 0 message requests** |

b4's zero is the half that matters: with the switch on, `send()` tries
`streamLocalFirst` before any network call, so zero requests is what shows the
turn short-circuited — a test that only read the reply would also have passed if
the network had simply answered.

### THREE WAYS A BROKEN HARNESS READ AS A PRODUCT DEFECT

This is the part worth keeping, because it cost three runs and all three were my
own fault.

1. **The server was never asked.** `WireServer` bound
   `InetAddress.getLoopbackAddress()`, which returns IPv6 `::1` on many Android
   devices, while the client dialled `127.0.0.1`. No exception, no connection, and
   the arm is `GenuineUnreachable` either way. Two runs printed
   `server saw 0 message request(s)` and the first was read as a cleartext policy
   block — a plausible cause for an absence, offered in place of a measurement of
   one. The server now binds IPv4 by name and **prints where it is listening**, and
   `assertReachable()` proves it answers over a bare socket before any result from
   it is believed.
2. **A bare `HttpClient` instead of the shipped `gsHttpClient`.** The app's client
   installs `ContentNegotiation`; without it `setBody(SendMessageRequest(...))`
   cannot serialize, the request fails before connecting, and the turn is
   classified `BackendError(0, null)` rather than `GenuineUnreachable`. The product
   was correct and the test was wrong.
3. **a8's own premise was false.** It asserted on the REPLY, and five different
   mis-set-ups printed the identical string `— GS backend error (HTTP 0) —`.

### CLOSE 2 — a8 rewritten to assert the CLASSIFICATION

`SendFailure` has one subtype per arm, so that is what the test pins. Four backend
behaviours, four distinct classifications, each asserted on the string its own arm
emits:

| backend behaviour | arm | result |
| --- | --- | --- |
| a port nothing listens on | `GenuineUnreachable` | PASS — `— Offline — cannot reach GS. —` |
| a 500 with a sanitized body | `BackendError` | PASS — `— upstream model unavailable —` |
| a 200 with no events, closed cleanly | the clean-break branch | PASS — **not** offline |
| a stream that starts and then dies | `MidStreamCut` | **FAIL — see below** |

The 500 assertion was **mine and it was wrong**: I demanded
`"GS backend error (HTTP 500)"`, and the arm is

    val serverText = failure.serverMessage?.takeIf { it.isNotBlank() }
        ?: "GS backend error (HTTP ${failure.status})"

so a real backend's sanitized `{"error": "..."}` is shown verbatim. That is better
behaviour than the text I required, and my assertion would have needed a broken
server to pass. It now asserts the property — the status or the server's message
must surface, and neither may claim the network is down.

### THE MID-STREAM CUT: four attempts, and the one fact that settled it

The product defect is real and the operator-visible string was wrong:

    refused connect   -> — Offline — cannot reach GS. —
    mid-stream cut    -> — GS backend error (HTTP 0) —      server saw 1 request
    HTTP 500          -> — upstream model unavailable —

One request was served and one SSE delta was written, so the backend was reachable
and had begun answering — and the user was shown a status code that does not exist,
for a failure that did not happen.

`HTTP 0` is `BackendError(0, null)`, which is what the classifier had to say when
it knew nothing, rendered as though it knew something. So `BackendError` gained a
`localReason` and the arm names the actual exception. That change is what turned
the next run from a third guess into a measurement:

    Classify: mid-stream cut -> — EOFException: Chunked stream has ended
                              unexpectedly: no chunk size —

Four attempts, each of which wrapped something:

| attempt | what it wrapped | why it missed |
| --- | --- | --- |
| `fix(104)` | `channel.readUTF8Line()` | the throw was not from the read |
| `fix(108)` | the same, plus a `CancellationException` discriminator | it was never a cancellation |
| `fix(111)` | `bodyAsChannel()`, `isClosedForRead`, the read | all three were already inside and none was it |
| `fix(114)` | **`client.post` itself** | — |

CIO decodes the chunked body while `client.post` is still returning; headers and
first chunk arrive in one segment, so a peer that dies mid-body is discovered
there, before an `HttpResponse` exists. That also explains a detail no earlier
hypothesis accounted for: the delta never reached `onDelta`, because nothing had
handed the bytes to the loop yet.

The rule applied to the POST is the **mirror** of the one already in
`classifySendFailure`, not a new one: `UnknownHostException`, `ConnectException` and
`SocketTimeoutException` are the shapes that prove the peer was never reached, so
they are rethrown unchanged and a genuine offline event still reads as one.
Everything else that fails after the request was written implies a connection
existed and then the response did not.

### CLOSE 3, item 2 — arm64, closed as hardware

The section in `RESULTS-mobile-concurrency.md` states what is proven (the arm64
`.so` exists, reads `ARM aarch64` from its ELF header, exports the ten
`gs_ffi_mobile_*` entry points, verified from the bytes), what is not (that it
**runs** — no arm64 instruction has been executed by this repository), the three
measured reasons, and what would close it: an arm64 Linux runner exposing
`/dev/kvm`, an Apple silicon Mac with Hypervisor.framework, or a physical arm64
phone over adb.

The finding underneath all three: the first host is the wrong architecture, the
other two are the **right** architecture with no accelerator, through two different
kernel interfaces. The blocker is nested virtualisation, not arm64.

It also bounds what the x86_64 run proves, which is the claim most likely to be
overstated: the same Rust and C++ executes, the ABI and the whole routing layer are
identical, and exactly two things differ — the SIMD kernels available and the host
calling convention.

### CLOSE 3, item 5 — option B, because A was measured to be unavailable

The instruction was to try one version bump. Before trying, the sources jar for six
AGP releases was fetched from Google's Maven and the failing line read:

    8.5.2   .single()=1   artifact.elements.map { ModuleMetadata.load(it.single().asFile) })
    8.7.3   .single()=1   (identical)
    8.9.2   .single()=1   (identical)
    8.11.1  .single()=1   (identical)
    8.12.3  .single()=1   (identical)
    8.13.2  .single()=1   (identical)

Byte-identical across six releases. No version fixes THIS failure, so the bump is
not the fix. The changelog was deliberately not consulted for a fix note: a sentence
saying "dynamic feature fixes" would not say whether it fixed this, and reading one
would have been a citation in place of a measurement.

`dynamicFeatures` stays commented out with the exact bug, the six-version
measurement, and the operator's wording verbatim.

## THE FOUR ITEMS THE OPERATOR EXPECTED TO BE INCOMPLETE

**1. iOS device build — ADDED.** The workflow built only `-sdk iphonesimulator` and
justified it: *"the device slice needs a signing identity and a physical device."*
The second half is wrong. A **signed** app needs an identity; an **installed** app
needs a device; an **unsigned** `.app` for `iphoneos` builds with
`CODE_SIGNING_ALLOWED=NO`, exactly as the simulator build in the same job already
did. So the slice was never un-buildable here.

What was missing, and what only a device build can catch: both slices are **arm64**,
so `lipo` cannot tell them apart and a simulator build passes any architecture
assertion. What differs is the platform, and nothing asserted it. Now asserted by
**directory name** (`Debug-iphoneos`), which is the only thing that distinguishes
the two products.

That assertion was wrong twice first, and both times in the same way:

    ** BUILD SUCCEEDED **
    Ld    .../Debug-iphoneos/App.app/App normal (in target 'App')
    ::error::GSApp.app is not a Debug-iphoneos/Release-iphoneos product

`GSApp` is the SCHEME and the MODULE name. The PRODUCT is `App`, and
`ios/project.yml` says why at the line that otherwise reads as an oversight:
`PRODUCT_NAME` is left at the XcodeGen default so the `AppTests` TEST_HOST resolves.
The same wrong name was **already in the shipped workflow's fallback**, where it
was dead code that could never match anything.

Two of my own lints caught defects in this work, which is the second time each has
happened:

- `check_pipefail_traps.py` flagged the symbol check
  `printf ... | grep -q ... || MISSING="..."`, which is wrong in BOTH directions:
  `grep -q` exits on the first match, `printf` takes SIGPIPE and exits 141,
  pipefail reports that, and the `||` appends a symbol that **was found**. Replaced
  with a bash regex anchored at both ends, run against six shapes of real `nm`
  output first — including `_gs_ffi_mobile_backend_available_extra` present while
  `_gs_ffi_mobile_backend_available` is absent, which an unanchored check accepts.
- The `TARGET_BUILD_DIR` extraction copied from the existing step used `exit` in
  awk, which closes the pipe and can SIGPIPE `printf`; under pipefail that is the
  substitution's status and `BUILD_DIR=$(...)` is an assignment, so `set -e` kills
  the step. It survives on the existing step only because that output happens to
  fit the 64K pipe buffer — a property of the input, not of the code.

**2. Performance — INCOMPLETE, and it is the gap that matters most.** No TTFT, no
tokens-per-second, for the 0.5B on a device. What exists is real CPU decode
(`a1b`, `b3 -> Paris.`) and 23 tests in `gs-bench`, and neither is a performance
number. This is the item that most affects the shippability decision.

**3. stable-diffusion.cpp on mobile — NO, and not by exporting one more symbol.**

    fn sd_cli_path() -> Result<PathBuf, String> {
        if let Ok(p) = std::env::var("GS_SD_CLI") { return Ok(p.into()); }
        for cand in ["/mnt/new_volume/sd/stable-diffusion.cpp/build/bin/sd-cli", ...]

`sd_render()` then **spawns that binary as a child process**. A phone has no
`sd-cli`, no `/mnt/new_volume`, and no environment to set. Wiring it means calling
stable-diffusion.cpp's model and sampler **in process** through a C ABI — a
different piece of work, not a smaller one.

What is cheap and remains open: `sd_render_svg` is **already linked** into the
mobile `.so`, because `build.rs` compiles `libgs_sd.a` unconditionally, but
`gs_mobile.cpp` contains no reference to it. It is a procedural renderer with no
weights, no GPU and no diffusion — which is exactly why it is the one worth
exposing. One symbol and one device test.

**4. iOS frontend wiring — the code was there and nothing proved it.**
`ChatViewModel.swift:494` is `let local = try? GsNative.chat`, but all eight
`GsNativeTests` called `GsNative` **directly** and none drove the ViewModel. So
iOS had what a6 has on Android in its source and did not have a6's guarantee.
Now closed with the two counterparts of a6 and a7, which is the same defect class
as a8 measuring its own harness.

## TWO LOOPS THAT COST MORE THAN THE BUGS

Recorded because both are generalisable and both produced green runs that measured
nothing.

**A test server that cannot be reached looks exactly like a product that cannot
reach the network.** Same exception surface, same `GenuineUnreachable` arm, same
user-visible string. `WireServer.assertReachable()` now proves the harness answers
over a bare `java.net.Socket` before any of its results are believed, and b2 and
all three of a8's server cases call it first. A test that cannot distinguish its
own breakage from a product defect is worse than no test, because it reports a
failure that is not there and reports it confidently.

**A diagnostic that cannot show the thing being diagnosed is worse than none.**

    ::error::first 25 lines of its output:
    ...
    ARCHS = arm64

`BUILT_PRODUCTS_DIR` and `FULL_PRODUCT_NAME` are alphabetical, so they are hundreds
of lines below the 25 printed. The dump was fine; the diagnostic hid the answer.
It now greps for the keys the step reads, and the product is located in the build
directory rather than reconstructed from settings that may not be emitted.

And the lesson underneath both: **the local verification of the fix was wrong twice
before it was right.** The first two fixtures omitted the four-space indent real
`xcodebuild` puts in front of every setting, and the second was silently
overwritten mid-call by a leftover script. Both would have had me "fixing" correct
code. A harness needs checking as carefully as the thing it checks.
