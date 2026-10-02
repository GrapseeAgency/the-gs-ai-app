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
| `ios-native` | `c12e866` | success — **48 tests, 0 failures**, plus the DEVICE app build verified from its bytes |

**The one-line version:** the iOS engine has llama.cpp and answers a prompt on a
real simulator; Android is **18/18 with nothing skipped**, the switch x network
matrix is proven in all four directions, and a reachable provider that dies
mid-answer is now labelled as such instead of with a fabricated HTTP status; the
arm64 run is impossible on every reachable runner for one hardware reason; and the
OCR split is closed permanently because no AGP release fixes it.

## ITEM 1 OF THE NEW BRIEF — stable-diffusion.cpp, IN-PROCESS

**The subprocess is gone.** The old route resolved `sd-cli` from
`/mnt/new_volume/sd/...` or `$GS_SD_CLI` and spawned it. A phone has neither the
path nor the binary.

### The gate nobody checked was the headers

The `stablediffusion` job in `android-deps.yml` had been **succeeding** and
producing 17.7 MB, and the package was:

    ./libggml-base.a   ./libstable-diffusion.a   ./libggml.a   ./libggml-cpu.a

Archives and **no headers** — so nothing could be compiled against it, which is
exactly why the wrapper could not call the library. `android-deps.yml` now copies
`include/stable-diffusion.h` in, and **asserts the five symbols the consumer is
written against are present**, so an upstream rename fails there rather than as a
confusing compile error elsewhere:

    new_sd_ctx  free_sd_ctx  sd_ctx_params_init  sd_img_gen_params_init
    generate_image

Also **pinned** to `3f8527a46c54ecf4cb4ed6003da8e8982283c73c` (2026-09-27). It was
`git clone --depth 1` of HEAD, so the package could change under a later build with
no diff anywhere. The checkout is verified to land on that sha.

Verified from run `36832394895`, artifact `11147951401`: the packaged header is
20,473 bytes and all five symbols are present.

### The API is not what the project's docs suggest

Read from the pinned header, not from memory — and the naming has changed:

    stable_diffusion_*   ->  0 occurrences in the current header

The real names are `sd_*`: `new_sd_ctx`, `free_sd_ctx`, `sd_img_gen_params_init`,
`generate_image`. Two consequences that would otherwise have been compile errors:

  * sd.cpp's own symbols are `sd_*`, and this repository's wrapper already declared
    `sd_create` / `sd_generate` / `sd_free` / `sd_context_t` / `sd_config_t`.
    Including both headers is a namespace collision in one `extern "C"` block, and
    the near-misses are worse than an error because they compile. **Every wrapper
    symbol is now `gs_sd_*`** — the operator's name, and collision-free.
  * `sd_guidance_params_t` has **no `scale` field**. It has `txt_cfg`, `img_cfg` and
    `distilled_guidance`. The wrapper sets `txt_cfg`.

There is also **no image writer** in the public header (`save_imatrix` is a
calibration dump), so the PNG is encoded in `gs_sd_wrapper.cpp` — stored-deflate,
correct CRCs and ADLER-32, ~100 lines, no dependency, which is one fewer shared
library in every phone APK. It is exposed as `gs_sd_write_png_for_test` so a failure
can be **attributed**: if generation produces no file, the question is whether
diffusion failed or the encoder did.

### A fourth private thread-local, which gs_abi.h already documents as a bug

`gs_abi.h` records why the shared error channel exists:

    "Added because the three wrappers each kept a PRIVATE thread_local ... so 43
     writes in the llama wrapper and every mobile error were unreachable -- a
     caller got "" for a failure that had a perfectly good explanation sitting in a
     variable nothing read."

**This file was still the fourth.** Every `sd_wrapper` failure set a private
`s_err` that nothing read. `set_err` now calls `gs_set_error`.

### No placebo, ever

`gs_sd_generate` returns `GS_ERR_UNAVAILABLE` and writes **nothing** when the
library is absent. `gs_sd_create` returns NULL rather than a context whose every
call fails, so a caller can tell "could not load" from "loaded but cannot generate".
The reason is that a test which decodes a PNG cannot tell a real generation from a
grey rectangle, and a grey rectangle that always appears is worse than no image at
all.

### What the operator can check

Seventeen `gs_ffi_mobile_*` entry points, confirmed read out of the real `.so`:

    expected 17 symbols, found 17
    all 17 expected gs_ffi_mobile_* symbols present

from run `36859948614`, job `110361806080` (x86_64; all four ABI jobs green).

**Three device tests** (`c0`, `c1`, `c2`) are written and the run id is passed as
`native_run_id`. Their results are recorded below once the device job reports.

## ITEM 2 OF THE NEW BRIEF — arm64, CLOSED, AND BOTH CHECKS CORRECTED THE EVIDENCE

The operator asked for two measurements before closing it. Both found something
wrong with what was there.

**A. `ubuntu-24.04-arm64` — THE LABEL WAS WRONG, and the real one never ran.**
Run `36688288136` probed `ubuntu-24.04-arm`, with no `64`. The official
runner-images README lists the arm64 images as `ubuntu-22.04-arm64`,
`ubuntu-24.04-arm64`, `ubuntu-26.04-arm64`. It was *additionally* wrong on its own
terms: with the switch OFF the KVM step takes `exit 0`, so its `::error::` line
cannot have come from that step at all.

The real label was added to the probe (the old one kept, so the evidence stays
reproducible) and dispatched: run `36831713320`, `runs-on: ['ubuntu-24.04-arm64']`,
created `2026-10-01T07:40:43Z`, **queued for 4h51m and never started a job**. Not
failed — queued. So the arm64 Linux host **has never been measured**, which is a
narrower claim than either "no KVM" or "it works", and the honest one.

**B. `macos-15` — `kern.hv_support` DOES NOT EXIST.** Run `36832392187`,
macos-15-arm64. The probe had never asked; the operator's exact check is now a
step. The first version suppressed stderr, so `unreadable` could have meant the key
is absent, permission denied, or no `sysctl`. It now reports stderr and has a fourth
verdict for `absent`. The conclusion does not depend on which — `HVF error:
HV_UNSUPPORTED` either way — and both are set by the HOST, not by QEMU.

**Closed with the operator's wording:**

> arm64 device runtime requires a physical device connected via adb, or a dedicated
> bare-metal arm64 CI runner. Attempts on ubuntu-x86_64 (wrong arch),
> ubuntu-24.04-arm (no KVM), and macos-15 (HVF unsupported) all failed for reasons
> outside our control. All CI evidence is x86_64; the arm64 .so compiles and passes
> ELF verification but has never executed.

What does **not** change: no arm64 instruction has been executed by this
repository.

## A REAL PRODUCT BUG, AND THE FALSE PASS THAT LET IT LIVE

`gs_sd_render_svg` — the procedural renderer, the path the dossier uses for
diagrams and charts — **never read the key of a field.** Every row it drew was
labelled with the *previous value*, and one field was silently dropped.

Found by `c0_the_procedural_path_writes_a_real_svg_on_the_device`, run
`36868936414`, on the device:

    java.lang.AssertionError: the SVG is missing "inference", so the spec was not
    rendered. A file that exists but omits what was asked for is the same failure
    as no file, wearing a different hat.

### Reproduced locally, before fixing

The real function was lifted out of the file into a harness and run, rather than
reasoned about. For the spec a test sends:

    {"title":"Engine Wiring","width":"800","height":"400",
     "layer1":"orchestration","layer2":"inference"}

it produced:

    <text ...>Engine Wiring: 800</text>
    <text ...>400: orchestration</text>

`inference` is absent, and neither row carries its own key.

### The cause

The loop searched for `:` **first**, which lands `p` on the *value's* opening
quote, and then took everything from there to the next quote as the key:

    while ((p = spec.find(':', p)) != std::string::npos) {
        ++p;
        ...
        const std::string k = spec.substr(p + 1, e - p - 1);

So `k` is the value. Every row was labelled with the previous value, and the last
pair was consumed looking for a value it never found.

**The row count was correct throughout.** That is why nothing noticed: the only
test covering this function asserted the file was non-empty.

### The fix, and the nine shapes it was checked against

The loop now walks quoted strings — `"key"` `:` `"value"` — and advances past the
value's closing quote, so no pair is read twice and none is consumed and lost.

| input | before | after |
| --- | --- | --- |
| the spec c0 sent | `Engine Wiring: 800` / `400: orchestration` | `layer1: orchestration` / `layer2: inference` |
| three fields, no title | `1: 2` — **two fields lost** | `a: 1` / `b: 2` / `c: 3` |
| unquoted value | **nothing rendered**, a field lost | `b: 2` |
| value containing a colon | `T: x:y` | `a: x:y` / `b: 2` |
| single quotes, spaces after `:`, trailing junk, empty value | all mislabelled | all correct |

Every shape is equal or better.

### The test that could not catch it, and why

`the_procedural_path_renders_without_any_model` used **the same spec** and asserted:

    assert!(s.contains("orchestration"), "field must be rendered");

which **passed against the broken parser**, because the broken row was

    <text ...>400: orchestration</text>

The value IS present — in the wrong row, paired with the previous value instead of
with its own key. **The defect is a one-position shift, and a one-position shift
is invisible to any check that looks at one side of the pair.**

It now asserts the pair *and* the row count, with the rendered rows printed on
failure. Both directions verified against the real harness output:

    BUGGY  'layer1: orchestration'  False    FIXED  True
    BUGGY  'layer2: inference'      False    FIXED  True
    BUGGY  row count: 2                       FIXED  row count: 2

The count is 2 in **both**, which is the point: a count alone would have passed
against the bug, exactly as the value-presence check did.

This is the clearest instance so far of the rule this repository keeps re-learning:
*asserting that a value appears is not the same as asserting that it is paired
with the right key.*

## A VERSION SKEW THAT PRESENTED AS SIXTEEN BUGS

Run `36868936414` installed a `.so` from an android-native run whose commit
predated `initTuned`. The library loaded, the emulator booted, the 491 MB model
downloaded and checksum-verified, and then:

    Tests 23/23 completed (16 failed)
    perf_...swept_in_one_run    FAILED  (all 12 configs rejected)
    a1 a1b a2 a3 a6 a7 b1 b2 b3 b4 c2 a0, GsNativeTest x2   FAILED

**Fifteen of the sixteen said exactly one thing:**

    GsNativeException: no native context; call init(modelPath) first

which points at the *model*. The sweep test had caught that `initTuned` threw,
recorded `initTuned failed` for all twelve configurations and left no context — so
the fifteen were a consequence, not sixteen findings.

The line that named the cause was in a logcat artifact, after a 25-minute run:

    No implementation found for boolean
      com.grapsee.gsai.native.GsNative_initTuned(java.lang.String, int, int)

Two fixes:

  * **`check_jni_matches_kt.sh`** now compares every Kotlin `external fun` against
    the `.so` **before** the emulator boots. It fails closed in three *named*
    ways — `nm` cannot read the file, the `.so` has no JNI layer at all, no Kotlin
    sources found — because a gate whose own failure is a silent abort or a false
    accusation is worse than no gate. Its first version was tested against a text
    file, `nm` said "file format not recognized", the pipeline swallowed the
    status, and it reported **every** symbol missing with a straight face.

  * **The sweep test restores the shipping default context before its own
    assertions**, and `a0` now establishes its own instead of inheriting one. A
    test that ends by destroying shared state turns the suite red, because
    instrumentation chooses the order.

## TWO LINTS, BOTH OF WHICH HAD TO BE WRONG FIRST

**`check_kt_braces.py`** — a brace-balance lint, wired into `android-app`.

Three hand-checks of ONE Kotlin file's brace balance each gave a **different wrong
answer**:

  1. `s.count('{')` vs `s.count('}')` — reported IMBALANCED on a file the build had
     compiled, because a JSON spec inside a string contributes one of each.
     "Fixing" it would have meant adding a brace: the worst response to a broken
     check.
  2. Per-line scanning — a Kotlin `"..."` literal may span **lines**.
  3. No `/* */` or `'c'` handling — still wrong.
  4. Comments, both string forms and char literals handled — **still wrong**, on
     `AttachmentUploader.kt`, which the build compiles:

         "filename=\"${displayName.replace(\"\", "")}\""

     `${` is **code**. The braces and quotes inside it are real. A scanner that
     treats the whole literal as opaque mis-lexes everything after the first inner
     quote.

  5. Templates lexed as code — **still wrong** on `AuthScreen.kt`, which is
     Compose: `"${if (x) { a } else { b }}"` — the `}` closing the `if` block was
     mistaken for the `}` ending the template. Template-local nesting is now
     counted.

Verified in **both** directions: eleven fixtures (line/block comments, a
triple-quoted literal spanning lines, escaped quotes, a `'{'` char literal, a
template with a nested `if/else`, a template containing a string literal, missing
brace, extra brace, unterminated string, unterminated template) and **all 99**
Kotlin sources in the tree, every one of which the build compiles. That is the
reference this is calibrated against, and the failure message says so — treating a
red lint as a fact about the *file* is how a broken check gets "fixed" by breaking
the code.

And once more: **the fixture was wrong before the check was.** The extra-brace
fixture was first written as `class C { fun i() { } }`, which is *balanced*, and
the lint correctly said ok.

## A GATE THAT WAS GOING QUIETLY LOOSE

`[ "$N" -ge 8 ]` against an ABI of **TEN** symbols was already 2 under. Adding seven
image-generation entry points made the ABI **SEVENTEEN** and the floor **NINE**
under — a gate whose margin grows every time the thing it guards gets bigger.

`android-native` now compares the **SET**: every symbol named, read out of the `.so`
with `nm -D --defined-only`, compared with `comm`. Missing → `::error::` and
`exit 1`. Unexpected → `::warning::`, **not** fatal, because an extra export is a
compatibility question and failing on it would make the gate impossible to extend
without editing the workflow first.

Exercised locally in four directions before going in: all present, one missing, all
missing, and one extra landing in `unexpected` rather than `missing`.

## FIVE COMPILE ERRORS THAT WERE NOT FIVE DEFECTS

Worth recording together, because four of the five were *not* the thing the compiler
led with:

  * `CStr::from_bytes_with_nul(c.as_bytes()).unwrap()` on a `CString` — a CString
    never carries its own NUL in `as_bytes()`, so that unwrap was a **panic on every
    call**, inside an `extern "system"` function. It compiles. It was not among the
    six reported errors, because it is not an error. `render_svg_via_ffi` now takes
    `&str` and owns the conversion, so the caller cannot get it wrong.
  * `fn gs_ffi_render_svg` was **invented**. The shim is `gs_ffi_mobile_render_svg`.
    An extern declaration of a nonexistent symbol is not a compile error — it is an
    undefined reference at link time, and on a platform where it resolves it is a
    call to whatever is at that address. Every extern the sd bridge declares is now
    checked against a definition.
  * `into_handle()` returned `usize` where the boundary wanted `jlong`. The
    compiler's own suggestion was `try_into().unwrap()` — a panic on a 32-bit ABI if
    a pointer does not fit, and 32-bit Android is one of the four ABIs shipped.
  * `GS_OK` was imported **twice**, by the same author, in two edits, while fixing
    six errors. The compiler said so.
  * `env.get_string()` takes `&mut self` in jni 0.21, so every entry point that reads
    a Java string takes `mut env`. Checked across the file, not the two lines: all
    seven that call it bind it mutably.

## A COMMIT MESSAGE THAT DESCRIBED A CHANGE IT DID NOT CONTAIN

`c9bd2d7` said:

    build.rs          .file(... "gs_sd_wrapper.cpp")
    CMakeLists.txt    add_library(gs_sd STATIC sd_wrapper/gs_sd_wrapper.cpp)

and contained **neither** — `git add -A native/cpp` staged the two files inside
that directory and left the two outside it edited and uncommitted. The rename of the
sources landed, so the commit looked complete and passed review of its own diff.

It cost three runs to find:

    cc1plus: fatal error: ../../cpp/sd_wrapper/sd_wrapper.cpp: No such file or
      directory

A commit message that describes a change the commit does not contain is worse than a
wrong change, because the message is what the next reader trusts instead of
re-deriving it. The check that would have caught it is cheap and now stated in the
message: **a build script naming a file that is not there fails only at the next
compile, on a runner, twenty minutes later.**

## TWO BUILD SYSTEMS, AND ONLY ONE OF THEM BUILDS THE MOBILE LIBRARY

Checked rather than assumed, when an include path had to be added:

    $ grep -E '^add_library|^add_executable' native/cpp/CMakeLists.txt
    10:add_library(gs_abi STATIC src/gs_abi.cpp)
    17:add_library(gs_llama STATIC llama_wrapper/llama_wrapper.cpp)
    45:add_library(gs_sd STATIC sd_wrapper/gs_sd_wrapper.cpp)
    51:add_library(gs_ocr STATIC ocr_wrapper/ocr_wrapper.cpp)

    $ grep -c gs_mobile native/cpp/CMakeLists.txt
    0

`gs_mobile.cpp` is built **exclusively** through `build.rs`. Each `cc::Build` has
its own `-I` list, so the `sd_wrapper` directory was only on the `gs_sd` compile
while `gs_mobile.cpp` included `gs_sd_wrapper.h` — and each `cc::Build` compiles one
unit, so neither one could see the other's problem:

    gs_mobile.cpp:41:10: fatal error: gs_sd_wrapper.h: No such file or directory

That asymmetry is invisible until someone greps for it, which is why it is written
down rather than fixed.

## NO LINK-ORDER CHANGE WAS NEEDED, AND THAT IS WORTH RECORDING

The brief said the link order needed changing, with `libgs_sd.a` before
`libgs_mobile.a`. It already was, and that is not the load-bearing fact anyway:

  * `libgs_sd.a` references `gs_set_error` (in `libgs_abi.a`, compiled above) and
    the sd.cpp archives. A static consumer must precede what it consumes, so the
    four sd.cpp archives are emitted **before** `gs_sd`, in dependency order:
    `stable-diffusion -> ggml -> ggml-cpu -> ggml-base`. Alphabetical is
    `base, cpu, ggml, stable-diffusion` — **exactly backwards**, the same mistake
    already recorded twice in that file for llama. Each archive is asserted to
    exist.
  * The sd.cpp archives do **not** have to come after `libgs_ffi.a`. Their consumer
    on that path is the Rust object that references them, which is the link entry
    point, not another archive. So only the order **among** the archives matters.

## iOS IS GREEN WITH A DEVICE BUILD, 48 TESTS, 0 FAILURES

Run `36828234718`, head `c12e866`, all three jobs green.

    Executed 48 tests, with 0 failures (0 unexpected) in 359.878 seconds

**48, up from 46.** The two new ones are the iOS counterpart of a6 and a7 — the
first tests that ever drove `ChatViewModel`:

    testTheChatScreenAnswersFromTheEngine                 passed (51.606 seconds)
    testTheChatScreenLeavesTheEngineAloneWhenTheSwitchIsOff  passed (0.004 seconds)

51.6 seconds is a real 0.5B generation through the ViewModel, and 0.004 seconds is
the switch being off and the engine correctly never being consulted.

### The device build, verbatim from the run

    === DEVICE BUILD VERIFIED ===
      product      : .../Build/Products/Debug-iphoneos/App.app
      architecture : arm64 Mach-O, under Debug-iphoneos
      device slice : .../GsFfi.xcframework/ios-arm64/libgs_ffi.a
      defines all ten gs_ffi_mobile_* entry points:
      abi_version, backend_available, build_info, chat,
      create, embed_dim, embed_image, free, ocr,
      should_use_local
      linked in    : proven by the unguarded gs_ffi_mobile_backend_available()
      call in GsNative.swift -- no module fails the COMPILE,
      no symbols fail the LINK, and neither happened.

      NOT verified: a signed .ipa. That needs an identity and a
      provisioning profile this repository does not have, and the gap
      between linking for a device and installing on one is signing.
      NOT verified: execution. Nothing here runs the device binary.

`ios-arm64`, never `ios-arm64-simulator` — checking the simulator archive would be
a green run that proves nothing about the device.

### And the trigger had a hole of its own

Commit `434d35c` — an `@MainActor` fix to `ios/App/Tests/GsNativeTests.swift` — was
pushed and **no run happened**, because the path filter was `native/**`,
`.github/workflows/ios-native.yml`, `.cargo/config.toml` and not `ios/**`. A job
that builds the iOS app, links the device slice and runs the app's XCTests was not
triggered by a change to the iOS app or its tests. Three of the seven device-build
failures were found by dispatching by hand.

A workflow that verifies a directory it does not watch verifies it by accident, and
only as often as someone remembers. `ios/**` is in the filter now.

**Still open, and both are honest gaps rather than work in progress:**

**Performance is measured but the device is not.** 12.15 tok/s decode and 4808 ms
TTFT for the 0.5B at Q4_K_M, from `perf_the_0_5b_reports_a_measurable_decode_rate`
— derived from two budgets, with the budget's honouring verified first and the
spread published beside the number. **These are a lower bound, not a phone
prediction:** x86_64 emulator, CPU-only llama.cpp, no NPU, no GPU delegate. The
4808 ms TTFT is the figure to weigh, and whether a real phone moves it enough is
the measurement that cannot be taken here, for the same nested-virtualisation reason
as the arm64 run.

**stable-diffusion on the mobile runtime is not wired and cannot be wired as
written.** `sd_render()` spawns `sd-cli` as a subprocess from
`/mnt/new_volume/sd/...` or `$GS_SD_CLI`. A phone has neither. Doing it properly
means calling stable-diffusion.cpp's model and sampler in process through a C ABI.
The one closable piece — `sd_render_svg`, which is *already linked* into the mobile
`.so` because `build.rs` compiles `libgs_sd.a` unconditionally — needs four layers
and a **static link-order change**, since `libgs_sd.a` currently precedes
`libgs_mobile.a`. Sized as open work in the plan audit rather than claimed.

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

## THE iOS DEVICE BUILD: SEVEN RUNS, SEVEN DIFFERENT CAUSES

The operator's fourth item — "iOS device build (not just simulator)" — was
INCOMPLETE because the workflow built only `-sdk iphonesimulator` and justified it:
*"the device slice needs a signing identity and a physical device."* The second
half of that is wrong. A **signed** app needs an identity; an **installed** app
needs a device; an **unsigned** `.app` for `iphoneos` builds with
`CODE_SIGNING_ALLOWED=NO`, exactly as the simulator build in the same job already
did. The slice was never un-buildable here.

What was missing, and what only a device build can catch: **both slices are
arm64**, so `lipo` cannot tell them apart and a simulator build passes any
architecture assertion. What differs is the platform, and nothing asserted it.

Adding the build and the assertion took seven runs, and **no two failures had the
same cause**:

| run | what it reported | what was actually wrong |
| --- | --- | --- |
| `36782878437` | `GSApp.app is not a Debug-iphoneos product` | the PRODUCT is `App.app`. `GSApp` is the scheme and the module; `project.yml` leaves `PRODUCT_NAME` at the XcodeGen default so the `AppTests` TEST_HOST resolves |
| `36784337230` | settings "did not resolve a product" | **my diagnostic** printed 25 of ~200 lines, and the keys are alphabetical, so they were hundreds of lines below what it printed |
| `36814592966` | `no GsFfi.framework inside the DEVICE .app` | the xcframework is built with `-library`, so it is **static**; a static framework is linked in and deliberately never embedded |
| `36816078754` | `nm` returned 0 on the app binary | an iOS **app executable's globals are stripped** — nothing links against them. Correct behaviour, wrong check |
| `36818412286` | `nm` returned 0 on the archive | **wrong reader.** Xcode 26.6's `nm` rejects rustc's LLVM-22 objects and reports 0 |
| `36819532571` | `no llvm-nm in the Rust sysroot` | **wrong discovery.** `llvm-nm` ships in the `llvm-tools` component, which `count_exported_symbols.sh` already installs |
| `36820816726` | reader "returned nothing", counter said 10 | the `NAMES=$(...)` assignment had been **deleted by my own earlier edit** |

Two of those seven were bugs I introduced into my own new code, and one of them was
a mechanism the repository had already documented.

### The two that are worth generalising

**A mechanism with its failure mode already written down is not an invitation to
reimplement it.** `count_exported_symbols.sh` carries the raw error:

    nm: error: .../libgs_ffi.a(gs_ffi...rcgu.o): Unknown attribute kind (105)
      (Producer: 'LLVM22.1.8-rust-1.98.1-stable'
       Reader: 'LLVM APPLE_1_2100.1.1.101_0')

and its docstring names my exact mistake in its own words: *"Earlier this same
check hid the problem twice before: `nm -gU ... 2>/dev/null` turned the failure into
a count of 0 with no explanation."* I used `nm -a ... 2>/dev/null || true`. I
grepped for `nm `, read the fatten step, and did not read the comment block above
the counter.

**A deleted assignment is not a syntax error.** The `NAMES` and `APP_NM` lines were
edited out while replacing the blocks around them; both variables were still read,
`bash -n` was clean, both existing lints were clean, and the step ran to its own
guard and reported a reader problem instead. `check_unassigned_reads.py` exists for
that class — and is wired in as a **NOTE, not a gate**, because it has a
demonstrable false positive in this tree: it reports `$SDK_BUILTOOLS` in
android-release.yml's Gates step, whose own first line assigns it.

That last one is the honest ending. Had I trusted the lint's output without reading
the step it named, I would have "fixed" a working release gate.

### What the device build is now asserted to be

From the built bytes, and the log says which of these is which:

1. the product is under `Debug-iphoneos` **by directory name** — the only thing
   that distinguishes it from the simulator product, since both are arm64
2. the executable is `Mach-O 64-bit executable arm64`
3. the **device slice** `GsFfi.xcframework/ios-arm64/libgs_ffi.a` exists and
   `llvm-nm --defined-only --extern-only` finds **all ten** `gs_ffi_mobile_*`
   entry points in it — `ios-arm64`, never `ios-arm64-simulator`
4. the app **links** it, proven by `GsNative.swift` calling
   `gs_ffi_mobile_backend_available()` **unguarded**: no module fails the COMPILE,
   no symbols fail the LINK, and neither happened

And what is **not** claimed, printed in the step's own output: a signed `.ipa`
(needs an identity and a provisioning profile this repository does not have), and
**execution** — nothing here runs the device binary.
---

## THE THREE PERFORMANCE LEVERS WERE NOT PARAMETERS, THEY WERE LITERALS

Item 1 of the new brief asks for levers 3-5: batch size, quantisation, flash
attention. Two of them had never been *measured*, and the reason turned out to be
that they could not be. In `native/cpp/llama_wrapper/llama_wrapper.cpp`:

```cpp
cp.n_batch   = 512;
cp.n_ubatch  = 512;
```

Two literals. Not parameters, not defaults read from a config, not fields of any
struct — two numbers typed into an assignment. And `cp.flash_attn` was **never
assigned at all**, so it inherited whatever `llama_context_default_params()`
happened to carry, which is not a number anyone wrote down and therefore not a
number anyone can compare a change against.

This is worth recording as a category rather than an incident. "We never measured
this lever" and "this lever does not exist" look identical from outside, and the
second one is a stronger claim than anyone can support without reading the
assignment. Reading the assignment took four minutes and changed the task from
*sweep three values* to *add three parameters, then sweep*.

### 0 MEANS "AS THIS BUILD ALREADY WAS", AND THAT IS NOT ZERO

The new fields default to today's behaviour, which for two of them is a literal:

| field | sentinel | why not just 0 |
|---|---|---|
| `n_batch` | `0 -> 512` | `n_batch = 0` means "process no tokens" |
| `n_ubatch` | `0 -> 512` | same |
| `flash_attn` | `< 0 -> AUTO` | it is an enum; 0 is `DISABLED` |
| `n_threads_batch` | `< 0 -> library default` | 0 means zero batch threads |

A config default of `0` for a *size* is a plausible-looking way to ship a build
that answers nothing, so 0 cannot be the sentinel for a size. A config default of
`0` for a *thread count* is a value somebody types by accident, so 0 cannot be
the sentinel there either. Two different sentinels for two different classes of
field is the correct answer, and `gs_mobile_create` now delegates with
`-1, 0, 0, -1` rather than five zeros.

### n_ubatch is CLAMPED, because ggml asserts rather than returns

`n_ubatch <= n_batch` is a `GGML_ASSERT` in llama.cpp, and an assert is a process
abort, not an error return. A caller asking for a micro-batch larger than the
batch would take the app down instead of being told. The clamp is in C++, where
the numbers are still just numbers.

## THE FIELD WAS AN ENUM, AND A BOOL WOULD HAVE BEEN A SECOND DEFECT

The first attempt at lever 5 was `cp.flash_attn = config->flash_attn != 0;`. It
did not compile — run **36955318134**, all four ABIs:

```
../../cpp/llama_wrapper/llama_wrapper.cpp:126:12: error: no member named
'flash_attn' in 'llama_context_params'
    cp.flash_attn = config->flash_attn != 0;
    ~~ ^
```

Read from the pinned header, `3018a11e79e489b657dbb77c95694889ccff92df`:

```c
enum llama_flash_attn_type {
    LLAMA_FLASH_ATTN_TYPE_AUTO     = -1,
    LLAMA_FLASH_ATTN_TYPE_DISABLED =  0,
    LLAMA_FLASH_ATTN_TYPE_ENABLED  =  1,
};
...
enum llama_flash_attn_type   flash_attn_type;   // when to enable Flash Attention
```

The bool would have been a defect even if it had compiled. `AUTO` is the library
default and **is not reachable by a bool at all**, so `!= 0` would have pinned
the kernel to `DISABLED` for every caller that had not opted in — turning "unset,
therefore the default" into "explicitly off" and making the benchmark measure
something this build had never done. Three enum states where I planned for two,
and the state I would have dropped is the one the code was actually in.

## AND THERE WAS A SECOND THREAD FIELD THAT NOBODY WAS SETTING

The same struct has two thread fields, and the wrapper set one of them:

```c
int32_t  n_threads;        // number of threads to use for GENERATION
int32_t  n_threads_batch;  // number of threads to use for BATCH PROCESSING
```

`n_threads_batch` was never assigned. Prompt processing ran on
`llama_context_default_params()`'s value for the entire life of this code.

**This corrects how an existing number was read.** The Item 3 table reported "4
threads is optimal, 8 threads is 70x worse" and recorded it as a result about
threads. That result is real and it stands — but it is a result about
*generation* threads. Time-to-first-token is prompt processing, and prompt
processing was never given the number. The one field that could have moved TTFT
by moving prompt processing across cores was never swept, and the table's own
wording was broader than its evidence.

The new test sweeps `n_threads_batch` over 1, 2, 4, 8 on a 2000+ character
prompt, and its load-bearing assertion is **directional**: one batch thread must
not be *faster* than several on a multi-core runner. "The numbers differ" would
pass on a 1% scheduling wobble, which is noise, and a field that never reached
llama.cpp produces exactly that.

`0` is deliberately not swept. It is the leave-the-default sentinel, and passing
it as a thread count is the one value that could turn a 53-minute device run into
a timeout.

## A FLOOR THAT COULD NOT NOTICE ITS OWN SET GROWING

Adding the eighteenth `gs_ffi_mobile_*` symbol exposed a gate that had stopped
describing its subject. Three copies of the ABI existed in three formats:

1. the inline `EXPECTED_ABI` list in `android-native.yml` — 17 names
2. the floor check 20 lines above it — the literal `-ge 17`
3. an eleven-symbol subset inline in `ios-native.yml` — a shell `for` loop

The floor's comment claimed it "matches the ABI". It did not, and it could not:

```sh
[ "$N" -ge 17 ] || { echo "FAIL: the ABI has 17 gs_ffi_mobile_* entry points"; exit 1; }
```

With 18 symbols exported, `18 -ge 17` **passes**. A floor cannot notice a set
growing. The comment two lines above it already conceded the deeper problem —
*"a floor count can pass with the WRONG symbols"* — and the per-symbol comparison
below it is what actually establishes the ABI.

My first fix was to add a nineteenth hardcoded number. Both Android checks now
read `.github/scripts/gs_mobile_abi.txt` and the floor is `wc -l` of it, so there
is no number left to bump.

**The check that proves it matters is the rename.** Replacing
`gs_ffi_mobile_ocr` with `gs_ffi_mobile_ocr2` keeps the count at 18, so the floor
passes and only the set comparison catches it. Simulated before writing the
commit, not after.

### A LINT, AND AN EXTRACTOR THAT WAS WRONG BEFORE THE FILE WAS

The iOS list is a *deliberate* subset — the iOS device slice is not required to
carry the six `sd_*` diffusion entry points or `render_svg`, and pointing its
check at the full list would fail it for a reason unrelated to iOS. Nothing
enforced that the two lists agreed.

`.github/scripts/check_abi_lists_agree.py` now does, with four properties that can
each fail alone. Property 1 is NON-EMPTINESS, and it exists because the first
version of the extractor sliced `ios-native.yml` from `MISSING=""` to the next
`do` — and the file contains **two of each**, so it read the wrong block,
extracted zero symbols, and reported a confident `subset: True`.

That is a vacuous pass, and it would have shipped as a green lint. The same shape
as the SVG row parser, the `nativeLibraryDir` shape, and `Elf64.kt`: a check that
reports a clean result because it was reading the wrong thing. The extractor is
now anchored on the `for S in ... do` loop itself.

Each property was then made to fail against a mutated copy, in isolation:

| mutation | rejected as |
|---|---|
| iOS list emptied | `FAIL 1/4` |
| iOS pins `gs_ffi_mobile_teleport` | `FAIL 3/4` |
| iOS subset inflated to all 18 | `FAIL 4/4` |
| full name instead of a suffix in the `.txt` | `FAIL 1/4` |

## A BRACE LINT THAT COULD NOT SEE AN EXTRA PAREN

`check_kt_braces.py` counts `{` and `}`. It passed a file containing:

```kotlin
rows += TbRow(gen, batch, true, ttft, t49, reply, if (ok) "" else "no usable timing"))
//                                                                                  ^ extra
```

Two opens, three closes. Balanced braces, one paren too many. Run **36956138901**:

```
e: ...DeviceVerificationTest.kt:2355:98 Unexpected token
```

The real gap is that the lint's job is "will this file parse", and a brace counter
is not that. **I wrote a bracket checker to close the gap, and it produced 197
false positives on a file that has compiled for months** — one raw JSON string
(`"layer1":"orchestration","layer2":"inference"}"""`, line 1367) breaks the
literal stripper, and every later report cascades from that one miscount. Deleted
rather than iterated, and recorded here because the failure is the interesting
part: this repository has now had a hand-rolled bracket scanner be wrong *five*
times, and the existing brace lint is still the only one of the five that is
right. `kotlinc` is the authority and it names the line in five minutes.

The one thing worth keeping from the attempt: the arity of every `initTuned` call
is now checked by a script that strips comments first and does not count the
trailing comma. My first version of *that* check reported 11 arguments for a
7-argument call, because it counted commas inside a `//` comment and the comma
before `)`. Both versions were wrong before the file was.

## STATUS

| item | state |
|---|---|
| lever 3 — `n_batch` | reachable and swept on a 500-token prompt |
| lever 5 — flash attention | reachable; the field is an enum, and it is `AUTO` by default |
| lever 7 — `n_threads_batch` | **found while reading the pinned header**; the field existed and was never set |
| lever 4 — quantisation | not started; needs three more 500 MB downloads in the device job |
| lever 6 — speculative decoding | not started |
| ABI floor | was a stale literal that could not notice growth; now derived |
| ABI lists | two copies, one unchecked; now linted, with a non-vacuity property |
| last CI | `android-native` **success** (36956136615, all four ABIs, 18-symbol gate derived and green); `android-app` **failure** on the extra paren, now fixed and not yet re-run |

## A DEFAULTED PARAMETER IN THE WRONG POSITION BROKE FOUR CALL SITES AT ONCE

`android-app` **36957611401** failed on four lines, none of them in new code:

    e: DeviceVerificationTest.kt:1272:44 No value passed for parameter 'out'
    e: DeviceVerificationTest.kt:1272:53 Argument type mismatch
    e: DeviceVerificationTest.kt:1839:49 No value passed for parameter 'out'

The cause was one line in the previous commit. `timeBudget` was

```kotlin
private fun timeBudget(budget: Int, repeats: Int, out: (String) -> Unit, prompt: String = perfPrompt)
```

and every call site in the file is a **trailing lambda**:

```kotlin
timeBudget(1, repeats) { println("PERF $it") }
```

Kotlin binds a trailing lambda to the **last** parameter. `prompt` had been
appended after `out`, so the lambda stopped being the trailing argument and
became an attempt to supply `prompt: String` -- a type error on four unrelated
lines at once, in a file that had compiled the day before.

Appending a parameter to the end of a Kotlin function reads as strictly additive.
It is the one edit that is not: if the last parameter is a function type, it
silently rewrites the call syntax of every existing caller. A default value does
not soften this. `prompt: String = perfPrompt` in that position is not a safe
default, it is a different overload's worth of breakage wearing one.

Now `prompt` comes **before** `out`, and it has **no default** -- a benchmark
number with an implicit prompt is not a measurement, and a default is exactly what
let the too-short shared prompt survive into the batch sweep unnoticed. All six
call sites now name their prompt, which is also the only way to read a row of the
table and know which prompt produced it.

## A TEST THAT MEASURES WITH A PROMPT TOO SHORT TO MEASURE WITH

Worth stating on its own because it is the second time in this file.

The threads/context sweep measures with `perfPrompt` -- *"Count from 1 to 60 in
decimal, one number per line"* -- which is short and generates for a long time.
That is the right shape for measuring **decode rate**.

It is the wrong shape for `n_batch` or `n_threads_batch`, because both are
**prompt-processing** knobs. On a prompt this short there are a handful of prompt
tokens to batch, so the sweep would have found no effect and the finding would
have been *"batching does not matter"* -- a conclusion produced entirely by the
choice of prompt, reported as a property of the engine.

Both new levers therefore build a 2000+ character prompt from real words (repeated
filler tokenises to far fewer tokens than its character count suggests, which would
have made a 2200-character prompt a 200-token one wearing a long prompt's label)
and assert the length before using it.

## STATUS

| item | state |
|---|---|
| lever 3 -- `n_batch` | reachable, swept on a 500-token prompt |
| lever 5 -- flash attention | reachable; the field is an enum, and it defaults to `AUTO` |
| lever 7 -- `n_threads_batch` | **found while reading the pinned header**; the field existed and was never set |
| lever 4 -- quantisation | not started; needs three more 500 MB downloads in the device job |
| lever 6 -- speculative decoding | not started |
| last CI | `android-native` **success** (36956136615); `android-app` failing on a Kotlin signature, fixed, not yet re-run |

## ITEM 2'S PRECONDITION WAS BROKEN IN THREE PLACES, AND THE THIRD IS NOT A CONFIG BUG

The brief asks for an SD checkpoint in the download flow, a separate consent, a
separate storage path, and an on-device 512x512 PNG. The first of those needs the
library linked, and it was not. `gs_sd_generate` had been answering
`GS_ERR_UNAVAILABLE` and the honest reason was three links apart.

### Link 1: the green build said so, in a line that had to be read twice

`android-native` run **36956136615** at head 2188f66, all four ABIs, **success**.
The build emits one of three lines depending on what it found:

```
(no) cargo:warning=linking stable-diffusion.cpp in-process from ...
(no) cargo:warning=GS_SD_PREBUILT=... has no include/stable-diffusion.h
YES cargo:warning=portable sd_wrapper: procedural path only.
```

The second line is the one that matters, and it is the one that is **absent**.
`build.rs` only prints it when the variable is *present and its contents are
wrong*. Neither branch printing means `GS_SD_PREBUILT` was never set at all — the
`if let Some(root)` block was skipped and `have_sd` stayed `false`.

A reader who stopped at "portable ... procedural path only" would conclude the
package was missing. It was not missing. Nothing was pointing at it.

### Link 2: the variable is set NOWHERE, in any workflow

```
$ grep -rn GS_SD_PREBUILT .github/workflows/
(no output)
```

`build.rs` reads it, documents the exact package shape it expects, panics with a
helpful message if the package is wrong, and prints a helpful message if the
header is missing. All three of those are unreachable, because the variable is
never assigned. The same shape as `GS_LLAMA_PREBUILT`, which *was* unset for a
long time and shipped a portable-only `.so` while passing every symbol and size
gate in the repository.

### Link 3: the fetch step never downloaded it either

The step is named "Fetch the cross-compiled llama.cpp for this ABI" and it fetches
exactly one artifact, `llamacpp-<abi>`. `android-deps.yml`'s `stablediffusion` job
had been building and uploading `stablediffusion-arm64` for weeks. Nothing consumed
it.

### Link 4 — the one that is NOT a configuration mistake

**The stable-diffusion package is arm64-only, and the device job is x86_64.**

| | |
|---|---|
| `android-deps.yml` workflow env | `ABI: arm64-v8a` |
| `stablediffusion` job | no matrix; builds one ABI |
| artifact name | the literal `stablediffusion-arm64` |
| `android-device.yml` default `abi` | **`x86_64`** |

So fixing links 1-3 would have produced a working arm64 `.so` and left the
**x86_64** `.so — the one the emulator actually loads and the one every device
test runs against — still portable-only. The build would have gone green and
reported success at the thing it did not do.

The toolchain file already reads `$ABI` from the environment, so an ABI axis is a
matrix and a job-level `env`, not a rewrite. It builds `[arm64-v8a, x86_64]`:

- **arm64-v8a** — real phones, the shipping target
- **x86_64** — the only ABI `android-device.yml` runs, because it runs natively on
  a linux runner; arm64 there needs KVM for a foreign guest, which is the
  nested-virtualisation wall that has already blocked every arm64 device attempt

`armeabi-v7a` and `x86` are deliberately **not** built. Their `.so` keeps today's
honest state: the wrapper compiles in its portable branch and
`gs_sd_generate` says `GS_ERR_UNAVAILABLE` with a reason.

**A matrix that the toolchain file does not read produces two identical arm64
archives under two different names**, and the second one is a lie the linker
believes. So the job-level `env: ABI: ${{ matrix.abi }}` is load-bearing, not
decoration.

### The fetch step fails CLOSED for the two ABIs that need it

`android-native` now downloads `stablediffusion-<abi>` and, when it is absent:

- **fails** for `x86_64` and `arm64-v8a`, because a device test must not run
  against a library that cannot generate — the same reasoning as the existing
  llamacpp step, whose comment says *"the tests would then 'pass' by asserting
  that nothing works, which is the opposite of what they are for"*
- **warns and continues** for the other two, which have no package by design

When it is absent, it prints the artifact list and names **three** causes with
three different fixes, because "not found" is a symptom:

1. the `stablediffusion` job did not build this ABI
2. the job failed
3. `deps_run_id` points at a run from *before* the matrix existed

Cause 3 is the one that will actually bite first, and the list makes it visible
at a glance. From the real artifact list of deps run 36957596213:

```
llamacpp-arm64-v8a               20634675 bytes
llamacpp-armeabi-v7a             19285754 bytes
llamacpp-x86                     19522105 bytes
llamacpp-x86_64                  20348505 bytes
onnxruntime-arm64                12528253 bytes
stablediffusion-arm64            17722165 bytes
tesseract-arm64                   3721224 bytes
```

`stablediffusion-arm64` is there and `stablediffusion-x86_64` is not. A reader can
tell "built under the old name" from "not built at all" without opening a log.

### The checkpoint: measured, not quoted

Every size and digest was read from the Hugging Face API's blob metadata for the
specific file, because an empty `sha256` in this codebase means the download is
**refused** rather than performed unchecked, and a wrong one means a download
that cannot complete.

**Chosen: `akleine/sdxs-512` → `sdxs.safetensors`, 882,587,118 bytes,
`sha256 6cca5bfd11b588cdfb4602018c7e623d24c95fdfdc5ab2d4b9e6978b3186980f`**

| checkpoint | bytes | why not |
|---|---:|---|
| `akleine/sdxs-512` | 882,587,118 | **chosen** |
| `concedo/sdxs-512-tinySDdistilled-GGUF` Q8_0 | ~716,000,000 | 23% smaller, but a third-party re-quantization, so the digest certifies a conversion rather than a release |
| `akleine/sdxs-09` | 1,342,124,230 | 52% larger, same family |
| `segmind/SSD-1B-A1111` | 4,465,700,000 | 5x the size |
| `segmind/Segmind-Vega` | 3,293,400,000 | 3.7x the size |

`docs/distilled_sd.md` at the pinned stable-diffusion.cpp commit lists this model
under "SD1.x, SD2.x with tiny U-Nets" and gives the invocation with
`--cfg-scale 1 --steps 1`, **both described as mandatory**. So `steps = 1` and
`cfgScale = 1.0f` in the catalogue are *constraints of the checkpoint*, not
tuning, and a test that passes 20 steps is not testing this model.

The brief named SD 1.5 (4.27 GB) and SDXL-Turbo (6.9 GB) and asked for the
smallest thing that produces recognizable images. 882 MB is still **larger than
the 1.5B LLM** and 1.8x the 0.5B one, which is the whole reason diffusion gets
its own consent rather than a second line in the existing one: a user who wants
offline chat has not agreed to a second gigabyte.

### One downloader, so the discipline transfers

`ModelDownloader.download` read exactly four fields of `Model` — `id`, `url`,
`sha256`, `bytes` — and nothing else. Those are now parameters of one private
`downloadVerified`, and both `download(model, ...)` and
`downloadCheckpoint(checkpoint, ...)` delegate to it. The body is unchanged.

That is not refactoring for its own sake: the size check, the SHA-256 check, the
resume logic, the "a partial larger than the real file is not a prefix of it"
case and the refusal to fetch a file with no digest are the entire reason the LLM
path is trustworthy, and an 882 MB checkpoint deserves them *more*. A second
implementation would be a second place for the discipline to be forgotten.

**While doing this I introduced a bug and my own check did not see it.** The
replacement ran over a region that started *before* the two delegations, so it
rewrote `id = model.id` into `id = id` in both of them — self-referential named
arguments in the one function every LLM download goes through. It was caught by
reading the diff, not by a check, because there was no check. The lesson recorded
here is the one that generalises: **a scripted edit whose replacement region
overlaps the text you just inserted will rewrite the text you just inserted.**

### The test FAILS on a missing checkpoint rather than skipping

`sd0_diffusion_generates_a_real_png_on_this_device` fails when the checkpoint is
absent, and the message names both external causes. A skip would have been a green
run that proved nothing, and 882 MB of CDN-dependent download makes "skip" the
state it would drift into.

It asserts four things separately, because they are four different defects:

1. **`sdAvailable` is true.** The assertion that would have caught all four links
   above. A `.so` can export all eighteen mobile symbols and still be unable to
   generate.
2. **The PNG decodes, at 512x512, with `BitmapFactory`.** A real decoder, not a
   hand-rolled one, so a correct header with a wrong body is caught.
3. **It is not blank** — three separate assertions, because uniform-grey, all-black
   and all-white are different bugs. A uniform image decodes as a perfectly valid
   PNG, which is why "the file exists" and "it decoded" cannot stand in for this.
4. **Two different prompts produce measurably different images** (mean absolute
   luminance difference >= 3.0). This is the one that catches a disconnected text
   encoder, and **every other assertion in the test passes on a fixed grey
   image** — so without it the test cannot tell a working pipeline from a
   placeholder that happens to be textured.

## ITEM 4: WHICH ARCH DO THE iOS TESTS ACTUALLY EXECUTE ON?

The brief asks for that number. It was knowable and unstated, and the honest
answer has three parts, only one of which is a real test.

### They run on a SIMULATOR, not a device

`simulator-tests` provisions with `xcrun simctl install "$UDID" "$APP"` and its
own comment says *"this runs against a simulator"*. The `Run the XCTests on a
booted simulator` step's destination is a **UDID** discovered by
`pick_simulator_udid.py`, not a device.

There is no physical iOS device on a GitHub runner and no signing identity in
this repository, so `xcodebuild test -destination 'platform=iOS,id=<udid>'`
against real hardware is unreachable here and adding it would produce a step
that can never run. The **device slice** is built and arch-verified
(`Debug-iphoneos`, `ios-arm64`, `Mach-O 64-bit executable arm64`) — but nothing
executes it. So "iOS is green" means "the simulator tests are green", and that is
now stated in the log rather than left to be inferred.

### The library arch WAS asserted. The SIMULATOR the tests run in was NOT.

`assert_arch_member.sh` checks both static slices — device `arm64` and simulator
`aarch64-apple-ios-sim` — from the bytes, via `ar t` + `file -b`, with both
spellings handled because Apple Mach-O says `arm64` and Android ELF says
`ARM aarch64`.

That is the right check for an **archive**. It is not the check the brief asks
for, which is about the **architecture the tests execute on**, and nothing in the
workflow asserted it. Three sources can answer that and they can disagree:

| source | what it reports | asserted? |
|---|---|---|
| `uname -m` | the **host** | no |
| `lipo -info` on the built `.app` | the **binary** that gets installed | **now yes** |
| `simctl getenv $UDID SIMULATOR_ARCHS` | the **simulator** the tests run in | reported |

`uname -m` is the trap. All three iOS jobs run on `macos-latest`, which is an
Apple Silicon machine, so a simulator there is arm64 — but it is arm64 because of
the **host**, not because anything checked. If GitHub moves that label to Intel,
`uname -m` becomes `x86_64`, the tests silently take the **emulated** path, and
the only thing that would notice is a test asserting on arch. There was none.

The executable's arch is now **asserted**, and the assertion accepts either
`arm64` or `x86_64` and rejects anything else — including an `iPhoneOS` device
product, which would mean the tests are pointed at something they cannot run on.
A check that only greps for `arm64` would pass on that state, which is the same
mistake `assert_arch_member.sh`'s own header records:

> `FAIL: libcommon.a: archive member is not arm64/aarch64`
> ...the same class of mistake as matching only "arm64" and rejecting ELF.

The executable is located by `CFBundleExecutable` from the bundle's `Info.plist`,
**not** by `basename`. It is routinely not the app's name — the same lesson as
`PRODUCT_NAME` being `App` rather than `GSApp`, which is why a `-name 'GSApp.app'`
fallback in this file was dead code that could never match anything.

`SIMULATOR_ARCHS` is reported rather than asserted because a fat binary listing
both architectures is legitimate, and failing on it would be wrong.

### THE ANSWER IS NOW ONE LINE IN THE LOG

```
==========================================================
 iOS TESTS EXECUTE ON: arm64 (native)
 They run on a SIMULATOR, not a physical device.
==========================================================
```

and the same string goes to the job summary.

### One assumption I had to undo mid-write

The step was first written with `env: UDID: ${{ steps.provision.outputs.udid }}`.
There is **no `id:` on the provisioning step and no `outputs:` block** in that
job, so it would have expanded to an empty string — and an empty UDID makes
`simctl getenv` fail with an error naming neither the cause nor the step.

That is the recurring shape in this repository: **a wrong assumption about where
something IS, written as a hard reference, is indistinguishable from the thing it
is not.** The UDID is now re-discovered with the *same* script the test step uses,
which is also what makes this step inspect the simulator the tests will actually
use rather than whichever one sorts first. Two different simulators would make the
answer a different number from the question.

## ITEM 1, LEVER 4: THE FOUR QUANTISATIONS, AND WHAT "QUALITY" MAY AND MAY NOT MEAN

The brief asks for TTFT, tok/s, and a good/acceptable/bad judgement of chat
quality on 5 fixed prompts. Sizes and digests are read from the Hugging Face API
for the specific files, because an empty `sha256` in this codebase means the
download is **refused** and a wrong one means a download that cannot complete.

| quantisation | bytes | sha256 (first 24) |
|---|---:|---|
| Q4_0 | 428,730,208 | `7671c0c304e6ce5a7fc577bc` |
| Q4_K_M | 491,400,032 | `74a4da8c9fdbcd15bd1f6d01` *(the default, the CONTROL)* |
| Q5_K_M | 522,186,592 | `041474553fcabfc2a2d67903` |
| Q8_0 | 675,710,816 | `ca59ca7f13d0e15a8cfa77b` |

That is **2.12 GB of extra download in a job that already fetches 491 MB**, and
it is stated in the workflow rather than discovered when a run times out.

**Q4_K_M is a row in the table, not a special case.** It is the current default,
which makes it the control: without it, "Q4_0 is 30% faster" has no baseline, and
"Q4_0 is faster" could equally mean the device got faster. It is **linked** from
the already-verified `model.gguf` rather than downloaded a second time — 491 MB
and four minutes saved, and the bytes are the bytes.

**A quantisation that fails its size or digest is deleted, not measured.** The
number would be real and wrong, which is worse than no number. The sweep then
measures however many arrived and names the ones that did not. The same rule
applies on the device: a truncated `adb push` reports success, and
`llama_model_load_from_file` on a truncated GGUF reports a **corrupt model** — so
without the on-device size check the failure appears as a model fault when it is a
transfer fault.

### The quality score is FACTUALITY, and the test says so in its own name

Each of the five prompts has a fact that is checkable without reading English
well: the capital of France is Paris, 12 + 7 is 19, the largest planet is
Jupiter, "good morning" in French contains "bonjour", and the sequence prompt
asks for five specific primes. `required` is a list where **any** member
satisfies the fact, deliberately — the model has several correct ways to say
"Paris", and requiring one spelling would measure formatting rather than
quantisation.

**This is not fluency, coherence or helpfulness, and it is not a proxy for one.**
A model can score `good` on all five and still write nonsense between the answers,
and nothing here would notice. A 0.5B model almost certainly does. So the column
is `facts`, the verdict is labelled **FACTUALITY**, and the log prints:

```
  'verdict' is FACTUALITY on 5 prompts with checkable answers.
  It is NOT fluency, coherence or helpfulness, and must not be quoted
  as 'chat quality'. A 0.5B model can pass all five and write nonsense.
```

Asking a model to judge it was rejected for a reason worth recording: a 0.5B
model grading prose is a worse judge of prose than the thing being graded, and
its verdict would be a number with no more connection to quality than the prompt
length.

**Three bands, not two.** "All five correct" versus "at least one wrong" would
put Q4_0 and Q8_0 in the same band whenever the small model misses a fact at
*every* quantisation — which is likely, and would make the measurement
uninformative. So there is partial credit, and the per-prompt fact counts are
printed so the bands can be second-guessed from the log.

**A zero is not a quality result.** The test asserts that every loaded
quantisation produced *some* text, because a factuality score of `0/8` from a
model that emitted nothing is a **load failure being scored as a quality
regression**.

### The speed number is on the SHORT prompt, deliberately

A speed number is not comparable across prompts. Lever 3 measures a 500-token
prompt because `n_batch` is a prompt-processing knob; this table measures the
short `perfPrompt` because the quantisation question is about the default chat
path. Mixing the two would make the quantisation table incomparable with the
batch table and with every other table in the file.

## ITEM 1, LEVER 6: SPECULATIVE DECODING — WHAT THE CODE ACTUALLY IS, AND WHAT THE ARITHMETIC SAYS

The brief: retry speculative decoding with a matching tokenizer, and "if it beats
1.0x after the fix, restore the feature. If not, report the number and delete it
permanently." Two things had to be established before spending a 53-minute device
run on it, and neither needed a device.

### The pinned llama.cpp has NO speculative API

```
$ grep -nE 'speculative|draft|n_draft' <llama.h @ 3018a11e79e489b657dbb77c95694889ccff92df>
(no output)
```

Zero occurrences. So there is no upstream `llama_speculative_*` to lean on. That
is consistent with the deleted implementation having been **hand-rolled** from
`llama_decode` plus the greedy sampler, and the surviving comment block says so —
it argues losslessness in terms of the greedy sampler and arithmetic, not in terms
of an upstream API:

> *Losslessness argument: with the greedy sampler we emit only tokens the main
> model itself chose... The win is arithmetic, not magical.*

### What survives is a half, and it is UNREACHABLE code

| symbol | callers | in the header |
|---|---:|---|
| `spec_prefill_at` | **0** | n/a (`static`) |
| `spec_prefill` | **0** | n/a (`static`) |
| `gs_llama_prefill` | **0** | **no** |
| `gs_prefill_at` | 1 | n/a (`static`) |
| `gs_llama_n_ctx` | used | yes |

The draft/verify loop, the draft context and the entry point are all gone.
`gs_llama_create` is still the only context factory, so **no draft model is ever
created**. Three of the four surviving functions have zero callers, and they
compile and pass every gate, which is exactly why they were never noticed.

`llama_bridge.rs` still documents a loop that is not there:

> `/// Context size actually in force, used by the speculative loop.`

And `run_spec.rs` is honest about the deletion, which is worth crediting:

> *This used to be the speculative-decoding A/B harness. Speculative decoding was
> DELETED -- the commit that removed it carries the measurement that justified it.*

So the original diagnosis — *"the draft and target used different tokenizers"* —
was correct, and the deletion was honest. What was left behind is three dead
functions and a stale doc comment. **Item 5's "TODO or NOTE comment" audit, one
instance, found by reading rather than by a device run.**

### The brief's own fallback pair is the same SIZE, and the arithmetic has a ceiling

The brief anticipates that no smaller same-family draft exists (Qwen2.5's smallest
is 0.5B) and offers a same-size pair. The only such pair with a matching
tokenizer is:

| role | file | bytes |
|---|---|---:|
| target | `qwen2.5-0.5b-instruct-q4_k_m.gguf` | 491,400,032 |
| draft | `qwen2.5-0.5b-instruct-q4_0.gguf` | 428,730,208 |

Same architecture, same BPE, **c = draft/target = 0.8725**.

For k drafted tokens, a draft pass each (cost `c` in units of one target pass) and
one batched verification pass:

```
speedup(alpha, k) = (alpha*k + 1) / (k*c + 1)
speedup > 1  <=>  alpha > c          (for k > 0)
```

- **break-even acceptance rate is `c` = 87.25%, independent of `k`**
- **ceiling at 100% acceptance is `1/c` = 1.146x**

| k | α=0.80 | α=0.875 | α=0.90 | α=0.95 | α=1.00 |
|---:|---:|---:|---:|---:|---:|
| 4 | 0.935 | 1.002 | 1.025 | 1.069 | 1.114 |
| 16 | 0.922 | 1.003 | 1.029 | 1.083 | 1.136 |

**So it is not automatically a loss, and my first derivation said it was.** The
intermediate script asserted *"there is no positive integer k for which drafting
pays for itself"* — while its own table printed `1.136x` three lines above. The
condition I reached for (`c*k < 1`) was not the break-even condition; the
break-even is `alpha > c`, which does not depend on `k` at all. The table was
right and the conclusion was wrong, which is the same shape as every other
time in this log where a stated conclusion outran the numbers under it.

**Acceptance above 87.25% is genuinely plausible here**, and that is the one
unmeasured quantity that is the entire experiment: a different *quantisation of
the same model* is the highest-acceptance draft that can exist, because the draft
is approximating the very distribution it is being used to verify.

### But this is a THROUGHPUT lever and the number under test is TTFT

Even at a perfect 1.146x, speculative decoding **adds a draft prefill before the
first token is produced**. Time-to-first-token therefore gets *worse by
construction*, whatever happens to tok/s.

The brief's target is *"cut TTFT below 3000 ms"*. A lever with a +14.6% ceiling on
throughput and a certain regression on TTFT is the wrong lever for that target,
and saying so is more useful than measuring it. **Lever 6 is deferred, not
deleted**, and the reason is written down: the honest result is *"a same-size pair
cannot exceed 1.146x on throughput and regresses TTFT"*, which is derivable from
two measured file sizes and one inequality. Restoring the feature would need a
**smaller** same-tokenizer draft, and no such model is published for this family.

## SIX HAND-ROLLED KOTLIN SCANNERS, SIX FAILURES, AND THE ONE THAT IS STILL RIGHT

This is a meta-finding and it is the most useful thing in this entry, because it
says where the time actually went and what not to do next.

| # | scanner | how it failed | what it cost |
|---|---|---|---|
| 1 | the SVG row parser | a one-position shift: rows labelled with the previous value, one field dropped | a real product bug, found only by lifting the function into a harness |
| 2 | the `nativeLibraryDir` shape check | asserted a *shape* that was not there, and reported it as a hard failure | indistinguishable from the thing it was not |
| 3 | the brace scanner (v1) | wrong about the file it was written for | run 1 |
| 4 | the brace scanner (v2) | wrong about its own fixture | run 2 |
| 5 | the Byte/Int scanner | wrong again | run 3 |
| 6 | **the bracket scanner (this session)** | **197 false positives on a file that has compiled for months** | one red run, then a red lint |
| 7 | **the object-member scanner (this session)** | **18 false positives, across four distinct classes, and a verdict that disagreed with its own output** | two rounds |

### What they have in common

Every one of them was written to avoid a five-minute compile. Every one of them
had to be debugged, and the debugging cost more than the compiles it saved. **The
compile is not slow; it is the only authority, and it names the line.**

### The seventh scanner, in detail, because the failures are instructive

It was written for a real class of bug — I hit *two* in an hour, and both were
worth a check:

1. a `typealias` inside an `object`, which is illegal Kotlin and whose parse
   failure cascaded into two more errors 39 lines away
2. `K.wifiOnly`, used twice and declared never, which the compiler reported as
   `Unresolved reference 'wifiOnly'` — naming a member that **is** declared, so
   the error sends you to a correct declaration and you find nothing wrong

The scanner reported 18 undeclared members on 102 files that compile. In classes:

- **`X.entries` and `X.valueOf`** — compiler-synthesised enum members. 6 of them.
- **`DiffusionCatalog.fitsDevice` and friends** — my brace-walk stopped early, so
  it collected the *nested* `Checkpoint` data class's members and missed the
  object's own `fun`s. The declared list it printed is the evidence: it contains
  `SDXS_512, bytes, cfgScale, ...` and not one `fun`.
- **`Block.withShiftedSource`, `GsColors.toMaterialScheme`** — extension
  properties and top-level extension functions, which are not members of the
  object at all.

And the worst part: run over the whole tree it printed **18 undeclared** and
exited **0**. `python3 ... | tail -22; echo $?` reports `tail`'s status, not
Python's. **A check whose verdict is read through a pipe is not a check.** I have
now made that mistake twice in this session — an arity checker that printed the
right answer and returned the wrong one, and this.

### The general rule, and it is about the checks, not the code

> A hand-rolled scanner for a language with a compiler is a liability after the
> first false positive, and the cost is not linear — a scanner that cries wolf
> gets deleted, and the bug it was written for comes back.

**What survives in the tree is two lints, and both are simple enough to be right:**

- `check_kt_braces.py` — counts `{` and `}`. Took four attempts and is currently
  correct. It cannot see a missing paren, and that limitation is now written
  down rather than papered over.
- `check_row_fields.py` — reads `data class` field lists and checks row-member
  reads. Added this session, verified by **reintroducing the exact bug it exists
  to catch** on a copy and confirming it rejects it, and with a non-vacuity guard
  so a batch that examines nothing is a failure.

Both are *narrow on purpose*. A narrow check that is right is worth more than a
general one that is nearly right, and the general one has now failed six times.

## THE THIRD INLINE-PYTHON TRAP, AND A MECHANISM I COULD NOT PIN DOWN

The SD fetch step died at **parse time**, before executing a statement:

```
deps run: 36963660068, looking for artifact: stablediffusion-x86_64
File "<string>", line 2
import json,sys
IndentationError: unexpected indent
##[error]Process completed with exit code 1.
```

It had found the *right* deps run. The three-cause diagnostic it was written to
print never ran — and a gate whose failure is a silent abort is the one thing its
own header says is worse than no gate.

### The mechanism, as far as I can actually establish it

**I do not know why that program was an IndentationError, and I am not going to
write down a confident-sounding reason.** What I can state as measured:

| program | result |
|---|---|
| `python3 -c $'\n    x=1\n    print(x)'` | **accepted**, exit 0 |
| `python3 -c $'x=1\n    y=2'` | **IndentationError**, exit 1 |

So a leading newline followed by *consistently* indented lines is fine, and a
**dedent** is what Python rejects. The CI log shows line 2 as `import json,sys`
with **no** indent, which means the program python received had code on line 1 and
an unindented `import` on line 2.

My first explanation was that YAML's block scalar determines its own indent from
the first non-empty line, so the empty line between the opening quote and the
first statement keeps its indentation. **I then tested that and it did not
reproduce** — the exact string that `yaml.safe_load` produces for that step runs
correctly here. My test before that had extracted a *snippet* into a fresh script
rather than going through the block scalar at all, so it proved nothing, and I
nearly wrote the wrong mechanism into this log on the strength of it.

What is left is: the shape is fragile, it fires on this runner, and it fires
*before any statement runs*, so a gate containing it can die without printing its
own diagnostic. That is sufficient reason to remove it, and insufficient reason
to claim a cause.

### The fix is the one the repository already established

`ios-native.yml` records the same trap with a *different* symptom — a body line at
column 0 **ended** the block scalar and the remainder parsed as YAML mapping keys
— and fixed it with `pick_simulator_udid.py`. **A multi-line program inside a YAML
`run: |` block is a file, not an argument.** Three instances, one fix, and the fix
is a file.

`.github/scripts/print_artifact_id.py` replaces both inline pythons in the step.
It also fixes a second, quieter bug: it exits **0 in every case**, including "not
found" and "the file is not JSON". The inline version's exit status was non-zero
on a missing name, and `ID=$(...)` is an *assignment*, so `set -euo pipefail` would
kill the step on the assignment rather than at the `if` written to handle exactly
that.

### What the replacement is verified against

Run 36963660068's **real** artifact list:

```
stablediffusion-x86_64           -> id='11208263244'
stablediffusion-arm64-v8a        -> id='11208957786'
stablediffusion-arm64            -> id=''      <- the pre-matrix name
stablediffusion-armeabi-v7a      -> id=''      <- legitimately not built
```

and the diagnostic, which is the reason it exists:

```
::error::    stablediffusion-arm64-v8a            17722133 bytes  expired=False
::error::    stablediffusion-x86_64               17859809 bytes  expired=False
```

A reader who is being told "not in this run" can see, in two lines, whether the
answer is **cause 1** (this ABI is not in the matrix) or **cause 3** (the run
predates the matrix). That distinction is the entire value of the three-cause
message, and an artifact list printed by a six-line inline python is one bad
indentation away from not existing.

## THE SD PACKAGE WAS BUILT IN A SHAPE NOBODY CONSUMED, AND ITS OWN ARCH CHECK WAS `|| true`

`android-native` run **36965589398** got further than ever and then failed on
something that looks like a missing library and is not:

```
deps run: 36963660068, looking for artifact: stablediffusion-x86_64
artifact id: 11208263244
...
=== native/prebuilts/sd-android-x86_64 contents ===
native/prebuilts/sd-android-x86_64/include/stable-diffusion.h
native/prebuilts/sd-android-x86_64/libggml-base.a
native/prebuilts/sd-android-x86_64/libggml-cpu.a
native/prebuilts/sd-android-x86_64/libggml.a
native/prebuilts/sd-android-x86_64/libstable-diffusion.a
header: 20473 bytes
::error::native/prebuilts/sd-android-x86_64/lib is missing: libstable-diffusion.a ...
::error::present:
##[error]Process completed with exit code 2.
```

**The archives were in that listing.** Every one of them. They were at the package
**root**; `build.rs` looks in `lib/`:

```rust
let libdir = std::path::Path::new(root).join("lib");          // build.rs:197
let p = libs.join(format!("lib{name}.a"));                     // build.rs:429
```

and `android-deps.yml` wrote them as `B="dist/$(basename "$S")"` while writing the
header to `dist/include/`. So the package was half in the documented shape. This
is the **fourth** "a wrong assumption about where something IS" in this
repository, and the fourth one I have made myself this session.

### And the diagnostic that would have said so was cut off in its last line

`ls -1 "$SD_ROOT/lib"` on a directory that does not exist exits 2. Under `set -e`
the step died **there**, so the run ended with an empty `::error::present:` and
exit 2 instead of my `exit 1`.

The one line that names the actual fault — *the archives are at the root, not in
`lib/`* — is the line that was cut, and the real listing was printed fourteen
lines earlier where nobody was looking. The listing is now `find -maxdepth 2`, so
it cannot fail, and it is verified against a package laid out the wrong way:

```
include/stable-diffusion.h
libggml.a
libggml-base.a
libggml-cpu.a
libstable-diffusion.a
```

### THE ARCH CHECK WAS RUNNING AND ITS RESULT WAS BEING THROWN AWAY

```sh
bash .../assert_arch_member.sh "$B" "$(basename "$S")" arm64 || true
```

Two faults in one line.

1. **The expectation is the literal `arm64` on every leg of the matrix.** The
   x86_64 build was being asserted to be arm64.
2. **`|| true` discards the verdict.** So (1) was invisible, and the
   `stablediffusion (x86_64)` job reported **success** having verified nothing
   about its own architecture.

`llamacpp`'s equivalent is the correct shape and has always worked:

```sh
bash .../assert_arch_member.sh "$B" "$(basename "$S")" "${{ matrix.expect }}"
```

no `|| true`, and an `expect` column in the matrix. **A check whose result is
discarded is not a check, and this one was actively concealing a check that was
checking the wrong thing.** The matrix now carries `expect` and the `|| true` is
gone, so the x86_64 leg will fail if it is not x86_64.

### `armeabi-v7a` and `x86` now SUCCEED, and that is the fail-closed design working

Both have no stable-diffusion package by design. The fetch step warns, sets
`GS_SD_PREBUILT=` empty, and continues — so their `.so` compiles in the portable
branch and `gs_sd_generate` answers `GS_ERR_UNAVAILABLE` with a reason, which is
the honest state. `x86_64` and `arm64-v8a` **fail closed**, because a device test
must not run against a library that cannot generate. Two green, two red, and the
split is the one that was specified rather than the one that was convenient.

## ITEM 2, THE CHAIN, COMPLETE: THE LIBRARY IS LINKED IN-PROCESS

`android-native` run **36970739680** at head `893fe58`. **All four ABIs green,
14/14 steps each.** The lines that matter, from the x86_64 job:

```
GS_SD_PREBUILT: /home/runner/work/.../native/prebuilts/sd-android-x86_64
diffusion is IN-PROCESS for x86_64: no subprocess, no sd-cli
warning: gs-ffi@0.1.0: linking stable-diffusion.cpp in-process from
    /home/runner/work/.../native/prebuilts/sd-android-x86_64 (4 archives)
gs_ffi_mobile_* exported: 18, ABI declares: 18
all 18 expected gs_ffi_mobile_* symbols present
process-creating imports: execlp fork waitpid
```

That is Item 2's bar minus the pixels: **an on-device PNG from a text prompt, no
subprocess**, and the only process-creating imports are the three
`ggml_print_backtrace` symbols that have been there all along.

### The precondition was four separate faults, and each one hid the next

| # | fault | found by |
|---|---|---|
| 1 | `GS_SD_PREBUILT` set in **no workflow** | reading one build line twice |
| 2 | the fetch step never downloaded the SD artifact | reading the step's own name |
| 3 | the SD package was **arm64-only** and the device job is **x86_64** | the matrix comparison |
| 4 | the package was **half** in the documented shape | a listing four lines above an error |

Fault 3 is the one that would have survived the other three. Fixing 1 and 2
produces a working arm64 `.so` and leaves the **x86_64** `.so — the one the
emulator loads — still portable-only, with the build green.

### What each fault looked like from the outside

**Fault 1**, run 36956136615, `success`, all four ABIs:

```
(no) cargo:warning=linking stable-diffusion.cpp in-process from ...
(no) cargo:warning=GS_SD_PREBUILT=... has no include/stable-diffusion.h
YES cargo:warning=portable sd_wrapper: procedural path only.
```

The **absent** line is the diagnosis. build.rs prints the second line only when
the variable is present and its contents are wrong; neither branch printing means
it was never set at all.

**Fault 3**, and the package that would have shipped:

```
stablediffusion-arm64      <- the literal name, every run, every ABI
```

against `android-device.yml`'s default `abi: x86_64`.

**Fault 4**, run 36965589398 — the archives were in the error's own listing:

```
::error::native/prebuilts/sd-android-x86_64/lib is missing: libstable-diffusion.a ...
native/prebuilts/sd-android-x86_64/libggml-base.a     <- present, four lines up
```

and then a *second* `lib` fault in my own check, which looked for
`liblibggml-base.a` because the stems already started with `lib`. **Third
instance of that one**, and `build.rs` says so about itself:

> The same mistake is already recorded twice in this file — the `lib` prefix on
> archive stems, and llama's own ordering

### Four checks, each of which could have passed on a defect

| check | what it stops | how it was verified |
|---|---|---|
| the SD archive **presence** check | a package one directory away from the right shape | ran on the complete tree AND on a tree with one archive deleted |
| `archive_arch.py` | an arm64 archive linked into an x86_64 `.so`, which the emulator refuses to load, turning every test into a **SKIP** rather than a failure | 5 fixtures: correct, mislabelled, relabelled, non-archive, unknown ABI |
| `archive_arch.py` **exit 3** | a `.a` with no readable ELF objects passing as correct | the non-archive fixture exits 3, not 0 |
| the ABI floor, derived | a stale literal that cannot notice a set growing | a RENAME keeps the count at 18; only the set comparison catches it |

`assert_arch_member.sh`'s header supplied the last two, verbatim:

> `file -b` on the .a itself is NOT a valid architecture check either: an ar
> archive reports only "current ar archive".

which I had already written **wrong** in a new step, and which failed all four
correct archives with `WRONG libggml.a: current ar archive` (run 36968639571)
before I read it.

### AND THEN THE NEVER-COMPILED CODE

With the library finally linked, the first error in that code appeared
immediately:

```
../../cpp/sd_wrapper/gs_sd_wrapper.cpp:315:16: error: use of undeclared
identifier 'GS_ERR_RUNTIME'
```

The whole `#if defined(GS_SD_HAVE_SDCPP)` block — the actual in-process
diffusion implementation — has passed review, the 18-symbol ABI gate and every
device run **without ever being handed to a compiler**. `GS_ERR_RUNTIME` is not a
code; the nearest true one is `GS_ERR_GENERATION` ("decode failed or produced
nothing"), which is exactly the case.

**The general lesson, and it is not about C++: a code path gated behind a build
flag is not covered by a test that asserts the flag is off.** The device tests
asserted `GS_ERR_UNAVAILABLE` with its reason, which is the *correct* behaviour
for a build without the library — and a correct assertion is precisely what a
never-compiled path looks like from the outside.

The rest of that block was then checked against the pinned header rather than
hoped about: every `GS_ERR_*`, every upstream entry point including the three that
do **not** start with `sd_` (`new_sd_ctx`, `free_sd_ctx`, `generate_image`), every
field set on `sd_img_gen_params_t`, and `sample_params.guidance.txt_cfg`. My
first two attempts at that check reported `prompt` and `negative_prompt` as
undeclared, which was **my extraction** grabbing the first `typedef struct {` in
the file rather than the one ending `} sd_img_gen_params_t;`. Locating a struct by
its terminator rather than by the keyword that introduces one is the difference
between a check and a guess.

## "A PUSH CANCELS IN-FLIGHT RUNS" IS TRUE FOR SOME WORKFLOWS AND NOT OTHERS

I have been operating on a single global rule — *a push to `native-runtime`
cancels in-flight runs* — and holding documentation commits back because of it.
It is a property of each workflow's triggers, and for the one that matters most
here it is **false**.

| workflow | triggers | can a push cancel it? |
|---|---|---|
| `android-app` | dispatch, **push**, PR | **yes** (`cancel-in-progress: true`) |
| `android-deps` | dispatch, **push** | **yes** |
| `android-native` | dispatch, **push** | **yes** |
| `ios-native` | dispatch, **push** | **yes** |
| `ios` | dispatch, **push** | **yes** |
| **`android-device`** | dispatch, **`workflow_run`** | **NO — it has no `push` trigger at all** |
| `benchmark` | dispatch | no |
| `eval-gate` | PR, schedule, dispatch | no |
| `android-release` | push, dispatch | push, but no `cancel-in-progress` |
| `arm64-probe` | dispatch, push | push, but no `cancel-in-progress` |

A push can only cancel a run whose workflow **enters its concurrency group on
`push`**. `android-device.yml` has `workflow_dispatch` and `workflow_run` and
nothing else, so a commit cannot join
`android-device-${{ github.ref }}` and therefore cannot cancel anything in it.

### The cancellation I attributed to a push was the job timeout

Run **36958995854** came back `cancelled`, and I had it filed as "a push
cancels it". The timestamps say otherwise:

```
started    03:10:37
cancelled  04:10:48
= 60 minutes 11 seconds, with timeout-minutes: 60 on the job
```

Sixty minutes and eleven seconds is not a push; it is the job's own budget, spent.
`timeout-minutes` is now 150, with the arithmetic for what the job now has to do
written next to it.

**Both beliefs were wrong in the same way**: each was a single rule generalised
past what it was measured on, and each would have changed what I did next. The
first made me delay a docs commit for no reason; had I generalised the other way —
"pushes never cancel" — I would have pushed during an `android-native` run and
lost it, because for that workflow it is exactly true that they do.

docs: two versions of ggml in one .so, and the check that finds it in CI instead of on a device

Run **36975792039**. `android-native` green on all four ABIs, the 18-symbol ABI
gate green, the DT_NEEDED completeness check green, APK built, installed,
launched. Then **24 of 29 device tests failed**:

    java.lang.UnsatisfiedLinkError: dlopen failed: cannot locate symbol
    "ggml_sage_attn" referenced by "/data/app/.../libgs_ffi.so"

## THE CAUSE, MEASURED FROM BOTH ARTIFACTS

`android-native` links **two different versions of ggml** into one shared object:
llama.cpp's, from `llamacpp-<abi>`, and stable-diffusion.cpp's, from
`stablediffusion-<abi>`. From `libggml-base.a` in each, android-deps 36966641772:

| | llama.cpp | sd.cpp |
|---|---:|---:|
| `ggml_*` symbols defined | 574 | 591 |
| `ggml_sage_attn` | no | **yes** |
| defined by **both**, two implementations | | **574** |
| only in sd.cpp | | **17** |
| only in llama.cpp | **0** | |

**sd.cpp's ggml is a strict superset.** So the linker's job is trivial and
catastrophic at once: llama.cpp's copy is linked first and satisfies all 574
shared names, so sd.cpp's `libggml-base.a` is never extracted for them, and the
members holding the 17 newer symbols are never pulled in. Four of those are
referenced by `libstable-diffusion.a` and therefore end up undefined:

    ggml_sage_attn   ggml_prec_set_acc
    ggml_quantize_i8_convrot   ggml_mul_mat_i8_tensorwise

All four verified present in **sd.cpp's** `libggml-base.a` and absent from
llama.cpp's, and all four `UND` in the shipped `.so`. The device reported
`ggml_sage_attn` because it is the first one the loader reaches.

For scale: **3,520,408 bytes** without stable-diffusion linked, **42,356,592**
with. Item 2's cost is 38.8 MB and a library that does not load.

### And this is the SECOND error, behind the first

The previous run, 36971941114, failed with `library "libc++_shared.so" not found`.
Shipping the companion fixed that and revealed this one. `c3` — added in between —
**passed**, and proved the companion *was* in the APK, which is what ruled out the
other two explanations. Three failures, each revealing the next.

## THE CHECK, AND THE TWO BUGS IT HAD FIRST

`.github/scripts/undefined_syms.py`, wired into android-native's verify step.
Every existing check asks what the `.so` needs **from the system**; none asks what
it fails to supply **for itself**. `ggml_`/`llama_`/`sd_`/`gguf_` are **denied, not
allowed**, so a future ggml that adds a symbol goes red in CI naming it.

**Bug 1, and it is the whole point of this entry.** The first `defined()` matched
the `Ndx` column with a bare `\S+`, which consumed `UND`. So **every undefined
symbol counted as defined**, and on the very `.so` that could not load:

    250 imported, 0 unresolved

**A checker that reports clean on the exact artefact it was written for is worse
than no checker.** The only reason this one is trustworthy is that it was run
against that artefact and disagreed with me.

**Bug 2.** Filtering "defined" on the *value* column found 2 symbols where there
are thousands: in a relocatable archive the value is 0 and says nothing.
`Ndx` is the column that decides.

**Bug 3.** `readelf -W` prints version suffixes — `strlen@LIBC (2)` — and the
regex assumed the name was the last field, silently dropping **150 of 400**
undefined symbols.

### Non-vacuity, on both real artefacts of the same run

| artefact | result |
|---|---|
| `x86_64`, SD linked | **exit 1** — 4 denied, each named |
| `armeabi-v7a`, no SD | **exit 0** — 0 denied |

163 further imported-but-undefined symbols (`memcpy`, `pthread_mutex_lock`,
`_Znwm`) are *expected*: they resolve at load time from `DT_NEEDED`. They are
counted and sampled, not listed, because 163 lines of noise bury the 4 that
matter.

## WHAT FIXES IT, AND IT IS NOT A LINK-ORDER CHANGE

Putting sd.cpp's archives first would resolve the four names, and then
**`libllama.a`'s calls into ggml would reach sd.cpp's implementation** — a
different version of the same library, with its own struct layouts. That converts
a `dlopen` failure into memory corruption, which is strictly worse and much
harder to attribute.

The correct shape is what llama.cpp itself ships: stable-diffusion.cpp as its
**own shared object**, `libstable_diffusion.so`, carrying **its own** ggml,
loaded with `dlopen`/`dlsym` on first use. Each `.so` then has its own symbol
namespace, the two ggml copies coexist, and the failure mode of a missing
diffusion backend is the one this project already models correctly —
`GS_ERR_UNAVAILABLE` with `sd.cpp:not-compiled` as the reason.

That is a real piece of work — a CMake shared target, its own artifacts, symbol
visibility, and a loader — and it is **Item 2's real remaining scope**, not
something a link flag settles.

---

## FIFTEEN TESTS, ONE ROOT CAUSE: THE FUNCTION THAT RECOVERS A CONTEXT COULD NOT

`android-device` **36981908316**, head `9b60f2c`. **29 tests, 12 passed, 17
failed, 0 skipped.** Fifteen of the seventeen carried the identical message:

    GsNativeException: GsNative.backendAvailable: no native context;
    call init(modelPath) first

Fifteen identical failures is one cause wearing fifteen costumes, and the count is
the least interesting number in the run. The cause was in
`GsNativeLoader.initWith`:

```kotlin
fun initWith(modelPath: String): Boolean {
    if (!ensureLoaded()) return false
    synchronized(this) {
        if (pendingModelPath == modelPath && GsNative.backendAvailable()) return true
        return try {                       // <-- the catch starts HERE
            val ok = GsNative.init(modelPath)
            ...
        } catch (t: Throwable) { ... false }
    }
}
```

**`backendAvailable()` is evaluated on the line above the `try`.** It raises
`GsNativeException` rather than returning false, so if that branch is reached the
exception leaves `initWith` entirely.

The branch is reached whenever `pendingModelPath` matches the requested path while
the context is dead. And it *did* match, because:

- `pendingModelPath` is cleared by `GsNativeLoader.release()`
- it is **not** cleared by a direct `GsNative.shutdown()`
- and four of the five new lever tests called `GsNative.shutdown()` directly, 9
  times, to reset the context between configurations

So the sequence was: a lever test tears the context down without updating the
bookkeeping, then calls `initWith` to put it back, `initWith` believes the context
is already live, asks, the question throws, the exception escapes into the lever
test, **the lever test fails on its last line**, and the restore never happens.
Every later test needing the engine then finds no context.

### Two defects, and the second is the one that would have bitten a user

1. **Mine.** The lever tests bypassed `GsNativeLoader`'s bookkeeping. They now
   call `GsNativeLoader.release()`, which does the shutdown *and* clears
   `pendingModelPath`. Nine call sites.

2. **Pre-existing, and this is the finding.** `initWith` is the function whose job
   is to *recover* a context, its contract is *"@return true when a context exists
   afterwards"*, and it **throws** in precisely the state recovery exists for. No
   test suite is needed to see that: if the context dies for any reason while the
   recorded path is stale, the user gets an exception from the recovery function.
   The whole body is now inside the `try`, and `pendingModelPath` is cleared on
   failure — without that second half, a *failed* init leaves a stale path that
   makes the *next* call take the branch that throws.

**`isAvailable()` had the same shape** and is the more quotable of the two:

```kotlin
fun isAvailable(): Boolean = ensureLoaded() && GsNative.backendAvailable()
```

A predicate whose answer is "no" **throws** instead of answering. Now `runCatching`
around it. It also does not consult `pendingModelPath`, deliberately: `initWith`'s
fast path is the proof that a stale recorded value is worse than none.

### THE THIRD LINE OF DEFENCE, AND WHY IT PRINTS

`@After restoreTheProcessWideContextAfterEveryTest` repairs the context after
every test and **prints when it had to**. The two source fixes cannot cover a test
nobody anticipated; a shared process-wide resource makes that a matter of when,
not if. The repair is silent in the normal case, and loud in the abnormal one —
because a repair that printed nothing would be the same false-pass this suite
exists to avoid, one level up: a green suite in which a test quietly failed to
clean up after itself.

### What this cost, and what it bought

The run was red and 17 tests' worth of signal was lost to one defect — including
the four lever tests that had **already printed their measurements** before
failing on the restore. That is worth stating plainly: the numbers in
`RESULTS-mobile-concurrency.md` came from tests that this run reports as FAILED,
and they are reported as what they are — measurements printed by a failing test,
with the failure cause identified and fixed separately.

It also means the run's own ordering is evidence. Execution order, from the
per-test logcat timestamps, is: the three pre-existing sweeps passed, then `sd0`
failed, then `perf_lever5` measured two settings successfully and failed, and
**everything after it that needed the engine failed.** `perf_lever5`'s logcat
contains both `loaded=true` rows and then a stack frame with no assertion message
— a test that worked and then failed on its way out.

---

## ITEM 3, FINAL ATTEMPT, AND THE CLOSE

`arm64-probe` **36992975588**, `host: ubuntu-24.04-arm64`. Dispatched
deliberately as the operator's one last try, with the correct runner label, and
cancelled at **121.3 min = 2.02 h** still `queued` with `runner_name: null`.

The six earlier attempts on this workflow all report `queue=0 min` in the API,
and that number is the **assignment** timestamp rather than the wait — so the run
history under-reports this problem by showing every attempt as instant. Measuring
`created_at` against wall-clock is the only way to see it, and the answer is that
the last six arm64 dispatches have collectively waited hours to never begin.

**`started_at` is not evidence of anything.** It read `09:59:55Z` for a run
created at `09:59:54Z` that then sat queued for two hours. Any arm64 claim built
on that field would report a run that never started as one that did.

This is the ninth time a tool reported its own failure as a property of the
artifact, and the first time the failure mode was *time* rather than a
mis-parsed column: the six `queue=0 min` rows are the run list asserting that six
arm64 attempts were dispatched instantly when none of them ran at all. A number
that is correct about a different question is worse than no number, because it is
checkable and wrong.

Closed: arm64 device execution and arm64 real-device TTFT. Not closed, because it
was never claimed: the arm64 objects build and link, and `archive_arch.py` reads
`e_machine` from every member of every `.a` and exits 3 rather than skipping an
arch it cannot read. **Linking is not running, and no arm64 instruction has been
executed in this repository.**
docs: the `read` mis-binding is unique, and `per_page` is applied before any sort I write

Two checks, both run rather than eyeballed, because the lever-4 fix was a
`while read` with one name too many and that class deserves a sweep rather than
a patch.

## EVERY `while read` IN THE WORKFLOWS, BOUND AGAINST ITS REAL INPUT

| file:line | names | input | verdict |
|---|---|---|---|
| `ios-native.yml:870` | `_ADDR _TYPE NAME` | `nm -gU` output | 3 fields -> 3 names, and `case "$NAME"` is field-exact so `gs_llama_create` cannot be satisfied by `gs_llama_create_result` |
| `ios-native.yml:2569` | `TRIPLE ARCH SYSROOT` | here-doc `TARGETS`, 3 columns | correct, verified by running it |
| `ios-native.yml:2676` | `A` | `find -name '*.a'` | one path per line -> one name, and `N < 4` fails the step |
| `android-device.yml:584` | `NAME FILE BYTES SHA` | here-doc `TABLE`, 3 columns | **THE BUG.** Fixed in the previous commit |
| `android-device.yml:608` | `FILE BYTES SHA` | the same table | correct, and short rows now `exit 1` |

So the mis-binding was **unique**: one loop of four here-doc-fed readers, and the
other three were already right — including one that does the harder thing and
matches a symbol name *field-exactly* with a comment naming the exact failure it
prevents.

Two of the three that are right carry a non-vacuity guard, which is why they are
right rather than lucky:

    ios-native.yml:870   MISSING accumulates and the step fails
    ios-native.yml:2676   N < 4 -> ::error:: and exit 1

The broken loop had neither. It was three names too generous and no guard, which
is exactly the combination that produces a green run measuring one row of four.

## `per_page` IS APPLIED BEFORE ANY SORT I WRITE, SO A SMALL PAGE IS ARBITRARY

Chasing the dispatched device run, this query:

    /actions/workflows/android-device.yml/runs?branch=native-runtime&per_page=2

sorted by `created_at` and put **run 36781918633 at head `48028f3`** at the top —
a run several hundred commits old — while the run I had just dispatched at head
`086bb0d` was absent from the list entirely. The same endpoint without the branch
filter, `per_page=10`, sorted the same way, returned it correctly as the newest:

    37005521803  in_progress  2026-10-02T12:14:36Z  head=086bb0d

The API paginates before the response reaches me, so `per_page=2` is a page
chosen by *the server's* ordering, and my sort only orders whatever survived it.
**Sorting a truncated page tells you about the truncation, not about the
workflow.** Every run lookup in this repository now uses the unfiltered endpoint
with a page large enough to hold the history, and checks the `head_sha` of what
came back rather than trusting position.

This is the tenth instance of the same shape — a tool answering a *different*
question than the one asked, reported as if it were the answer. The others were
`nm` unreadable, an awk scalar fatal, `file -b` on an archive, a `readelf` `Ndx`
column read as a name, `queue=0 min` standing in for queue time, and
`started_at` standing in for a runner. Here the discarded information was a whole
page, and it was discarded silently, in a way that would have had me polling an
ancient run and concluding the dispatch had not landed.


---

## LEVER 4, COMPLETE: FOUR ROWS, AND THE SHIPPED DEFAULT IS THE SLOWEST

`android-device` **37005521803**, head `086bb0d`. **29 tests, 28 passed, 1 failed
(`sd0`, correctly), 0 skipped** — the `initWith` fix held, and the fifteen-test
cascade did not return.

    quantisation            bytes          TTFT ms   tok/s  facts
    q4_0              428,730,208               792   37.35   9/10
    q8_0              675,710,816               942   26.77   9/10
    q5_k_m            522,186,592              3805   13.25   9/10
    q4_k_m  (shipped) 491,400,032              4269   11.65   8/10

`q4_0` is **5.4x lower TTFT, 3.2x the decode rate, one more fact correct and 13%
smaller** than the quantisation this build ships. All four within one run, so
none of it depends on the cross-run drift below.

### Why the ordering is trusted and the magnitudes are not

Every magnitude in this table is suspect, because the shared runner host moves
them. The same `q4_k_m` file, consecutive runs, no code change:

    run 36992620210   TTFT 3067 ms   17.87 tok/s
    run 37005521803   TTFT 4269 ms   11.65 tok/s

+39% TTFT and −35% tok/s on one fixed file. **1.39x and 1.53x of pure host load.**
So the numbers that mean something are the ones three independent things agree on:

1. **TTFT and tok/s are different code paths** — load-plus-prefill versus pure
   decode — and they produce the **identical four-way rank order**.
   Contention does not sort two unrelated measurements the same way.
2. **File size disagrees with that order.** By size: `q4_0 < q4_k_m < q5_k_m <
   q8_0`. By speed: `q4_0 < q8_0 < q5_k_m < q4_k_m`. A bandwidth or page-fault
   story requires the largest file to be slowest, and `q8_0` is 58% larger than
   `q4_0` yet **second fastest**.
3. **The fastest row ran first and cold**, so no warm-up explains it.

That is also the general lesson, and it is the one worth keeping: when every
*magnitude* in a benchmark is unreliable, the question is not which number to
trust but **which relations survive**. Relations measured by independent code
paths agreeing is evidence. A single magnitude is not.

### What it licenses, and what it does not

**Licenses, with confidence:** on this configuration the shipped default is the
slowest of the four quantisations available for it. Actionable, not in dispute.

**Does not license "ship Q4_0".** One emulator, one 0.5B model, CPU-only,
`GGML_BLAS=OFF`. The K-quant advantage on arm64 is normally the reverse of this,
because the NEON and dot-product kernels differ between the K and legacy layouts
— and arm64 execution is the capability item 3 just closed permanently. Choosing
a shipped quantisation needs a run on the target architecture.

### The 3000 ms target, met for the first time, by the lever nobody turned

`q4_0` short-prompt TTFT is **792 ms** against the brief's 3000 ms. The largest
available win was not in `n_batch` (flat), `flash_attn_type` (3.2%) or
`n_threads_batch` (already at its optimum) — none of those three was ever going to
reach 3000 ms. It was in **which file gets loaded**, a knob this build could
already have turned and had not.

The long-prompt case has no `q4_0` measurement and is left blank. Scaling the 5.4x
from a short prompt to a 500-token prefill would be inventing the number that
matters most.


---

## THREE GATES, EACH REASONABLE, COLLECTIVELY A FALSE PASS

The lever-4 sweep reported one quantisation of four and the run was green, twice.
Taking the broken run's own output and pushing it through the check found three
separate defects, each hiding the next.

**1. I renamed a variable and left three references to the old one.** Fixing
`while read -r NAME FILE BYTES SHA` to `FILE BYTES SHA`, I left `$NAME` in three
`GS_QUANT_MISSING=` assignments (lines 622, 630, 638). Under `set -euo pipefail`
that is an abort on an unbound variable: **the first error path of the loop I had
just fixed would have died with `NAME: unbound variable`, naming neither the file
nor the reason.** All three are on the fetch-failure, size-mismatch and
digest-mismatch arms -- every path where something goes wrong and none where it
succeeds, which is why the next run passed and this went unnoticed.

I verified the loop's binding and its short-row guard and never executed its error
paths. `bash -n` on the step passed; `bash -n` cannot see an unbound variable.

**2. The fetch step warned and continued.** `::warning::` plus `continue` is how
two device runs downloaded one of four files and reported the lever as measured.
It also failed open in the worst direction: a transient Hugging Face outage
removes exactly the row that shows the shipped default is the slowest of the four.
All three arms now `exit 1`, verified by executing the workflow's own loop five
ways with only `curl`, `stat` and `sha256sum` stubbed:

    variant    exit  VERIFIED  ::error::  ::warning::
    allgood      0         3          0           0
    fetch        1         0          5           0
    size         1         0          2           0
    digest       1         0          2           0
    shortrow     1         0          2           0

**3. The assertion itself accepted one row.** `assertTrue(loaded.isNotEmpty())`
over a four-file sweep. It is now `assertEquals(sweep.size, loaded.size)` with a
message that separates "the transfer failed" from "the engine refused the model".

    run 37005521803 (4 rows)  ->  assertEquals(4, 4) -> PASS
    run 36992620210 (1 row)   ->  assertEquals(4, 1) -> FAIL, naming all three

**A load refusal is a legitimate result when the subject is whether the engine
accepts a model** -- `a8_each_backend_behaviour_maps_to_its_own_failure_arm` and
the `sd0` backend assertion both rely on it. **It is not a legitimate result when
the subject is which quantisation to ship**, because that question has no answer
from a subset.

The green run was not wrong about anything it reported. It reported one correct
number and called it four, and the three rows it dropped included the only one
that would have answered the question. That is the hard version of a false pass:
every gate individually reasonable, collectively misleading.


---

## "Not an int attribute" NAMED THE ARCHIVE. IT NAMED THE READER.

`ios-native` **37020835936** red on at least three consecutive runs, and Item 4's
arch assertion never executes because the job dies earlier. Verbatim from the
runner:

    reader: .../toolchains/stable-aarch64-apple-darwin/.../llvm-nm
    rustc : rustc 1.98.1 (48a229cea 2026-09-01)
    llvm-nm: error: .../libgs_ffi.a(...rcgu.o): Not an int attribute
      (Producer: 'LLVM23.1.1-rust-1.99.0-stable' Reader: 'LLVM 22.1.8-rust-1.98.1-stable')

The xcframework was **written by one toolchain and read by another**, and llvm-nm
prints both versions in its own error. An older LLVM cannot decode a newer
object's attribute kinds and reports them as `Not an int attribute`.

That is a statement about the READER. The message points at the wrong file, and
the eleventh instance of a tool's failure being reported as a property of the
artifact -- the first where the toolchain printed the evidence itself.

### The cause is a missing pin, and the fix does not need the mechanism

Three sites run `sh -s -- -y --profile minimal --default-toolchain stable`: one in
`ios-native.yml`, **two** in `android-native.yml`, in jobs that do not share a
runner. Verified absent: no `rust-toolchain*` in the repository, no `toolchain`
key in any `Cargo.toml`. `stable` is not a version, it is a promise that something
will resolve it, three times, independently.

Which version went which way, and why, **I could not pin down and have not
recorded a mechanism for it.** With one toolchain the producer and the reader are
the same LLVM by construction, so the skew becomes impossible rather than merely
unlikely, and the answer is not needed:

    GS_RUST_TOOLCHAIN: '1.98.1'      # workflow-level env, in BOTH workflows

`1.98.1` is the version the runner actually resolved, per the log above.

### `--default-toolchain ""` would have restored the bug silently

An empty value makes rustup read "no preference" and install its own default --
the unpinned resolution, with a green step. So all three sites refuse first, and
the refusal names the condition rather than a symptom. Verified with only the
installer stubbed:

    ios-native      var=1.98.1   reaches the installer
    ios-native      var=UNSET    ::error::GS_RUST_TOOLCHAIN is empty or unset
    android-native  var=1.98.1   reaches the installer
    android-native  var=UNSET    ::error::GS_RUST_TOOLCHAIN is empty or unset

### Two mistakes of my own, both caught by the checks and not by reading

I put the guard **between `curl ... \` and `| sh -s --`**, severing the pipe
continuation so `| sh` became a command of its own; `bash -n` on the extracted
step said `syntax error near unexpected token '|'`. I then wrote the `env:` entry
at **column 0** inside a block whose keys are at column 2, and `yaml.safe_load`
rejected the file. Both files were reverted and redone.

### The diagnostic, so the next reader is not left guessing

`count_exported_symbols.sh` now parses both versions out of llvm-nm's error and
says what it is when they differ:

    THIS IS TOOLCHAIN SKEW, NOT A MALFORMED ARCHIVE.
    object produced by: LLVM23.1.1-rust-1.99.0-stable
    read by          : LLVM 22.1.8-rust-1.98.1-stable

Verified in both directions against the runner's real error text: fires on the
skew, silent on `file format not recognized`, so a genuinely bad archive is still
reported as a plain read failure.

### What this unblocks

Nothing yet, and that is worth saying. Item 4's answer -- the arch the iOS tests
execute on -- is still **not measured**. This commit removes the reason the step
never ran; the next `ios-native` run is what produces the number, and until that
run is green the answer stands as the last recorded one: a **simulator**, on
`macos-latest`, with the executable's slice asserted and the executing simulator's
arch asserted separately.
