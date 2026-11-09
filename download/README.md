# Native app downloads

## Latest release: v0.86.27 — native GenUI fixes (build 150)

[Open the release page](https://github.com/GrapseeAgency/the-gs-ai-app/releases/tag/v0.86.27). Native Kotlin/Jetpack Compose Android and Swift/SwiftUI iOS clients, not a website packaged as an app. Sandboxed executable HTML/JavaScript/React content uses an embedded web runtime where appropriate.

| Platform | Actual download | Installation |
| --- | --- | --- |
| Android | [Signed Release APK](https://github.com/GrapseeAgency/the-gs-ai-app/releases/download/v0.86.27/GS-AI-App-v0.86.27.apk) | Install over the existing shipping app; no uninstall needed. Allow installation from your browser/file manager when prompted. |
| iPhone / iPad | [Unsigned arm64 device IPA](https://github.com/GrapseeAgency/the-gs-ai-app/releases/download/v0.86.27/GS-AI-iOS-v0.86.27-unsigned.ipa) | **Re-sign with your own Apple account/provisioning using a compatible sideloading tool before installation.** This is a real device build, not a renamed simulator app. It is not TestFlight/App Store signed; tapping this IPA alone will not install it. |
| Xcode iOS Simulator | [Release App.app ZIP](https://github.com/GrapseeAgency/the-gs-ai-app/releases/download/v0.86.27/GS-AI-iOS-v0.86.27-simulator.zip) | Extract App.app, boot a compatible simulator, run `xcrun simctl install booted /path/to/App.app`, then `xcrun simctl launch booted com.grapsee.gsai`. Not installable on a physical iPhone. |

### Integrity and exact build identity

Source: [`12b3f43e4330`](https://github.com/GrapseeAgency/the-gs-ai-app/commit/12b3f43e4330bec4d801817b7e83351d5e3560b1). [Successful packaging and publication](https://github.com/GrapseeAgency/the-gs-ai-app/actions/runs/42620724192). [Exact-source native interactions and six-section review — all ten jobs passed](https://github.com/GrapseeAgency/the-gs-ai-app/actions/runs/42620731065).

| File | Bytes | SHA-256 (GitHub-computed release digest) |
| --- | ---: | --- |
| `GS-AI-App-v0.86.27.apk` | 73283158 | `23c549b2df5a72c4e4092d3ed1939a893974ff67f7ce155ee8c2e7c5fc443fdb` |
| `GS-AI-iOS-v0.86.27-unsigned.ipa` | 12033454 | `de35639a45430f8785e113bb7482f1438b42bbd8bff3920999e954b8993d9276` |
| `GS-AI-iOS-v0.86.27-simulator.zip` | 19964086 | `1b1119812a3be64543099d1e659a6c2276d19cc31bdb7b1a2fac617ca9967e89` |

The release includes `SHA256SUMS.txt`, `build-provenance.json`, Android certificate/package/optimization checks, and iOS Mach-O platform/architecture checks. Android's broad unit invocation discovered 532 tests: 429 executed with zero failures/errors; 103 conditional capture tests were skipped there. The separate mandatory native interaction and complete six-section capture/review workflow passed on this exact source. Both iOS device and simulator Release binaries compiled and passed their platform checks; this does not claim a new physical-device runtime session or physical haptic/wake proof.

The APK uses the existing shipping certificate, is not debuggable, packages the baseline profile and all four native engine ABIs, and carries the public service origin rather than emulator loopback. iOS ships an actual arm64 `iPhoneOS` app and a distinct `iPhoneSimulator` app. Model weights are not bundled; existing model/download behavior is unchanged.

The Android live updater reads `update-manifest.json`, now pointing to this release asset with its verified digest and size. **The committed legacy `download/GS-AI-App.apk` and screenshots below are historical, not the current build.** Use the release links above.

### Fixes to check

- Real task add/edit/delete/toggle and response-isolated React controls/persistence.
- Source-derived charts, ranges, table controls and calculators; missing input stays empty instead of becoming a fabricated zero/minimum, and currency is not invented.
- Custom source-defined discount/formula calculators stay interactive instead of being rejected by a closed category list or relabelled finance.
- Custom glass icon normalization, source-byte-safe code handling, truthful unsupported-runtime behavior, and iOS durable history/relaunch/clear/delete/retention repairs.

Exact whole-phone/reference pixel identity and physical haptic/background-wake delivery are not certified. Please test the downloaded builds on your devices.

---

# Historical release documentation (not current downloads)

## Historical release: v0.86.25 — the Model Centre, and the glass redesign finished (versionCode 148)

    file    GS-AI-App.apk
    size    72366000 bytes
    sha256  e4d1120c9e3de01da79994146890d1cf4526de3c39c1bba5f86d3ccfbb4625d8

![rich reply](qa-0.86.25-rich-reply.png)

What landed in this build (584 commits since v0.86.2 — the GenUI passes, the
0.86.3–0.86.25 craft series, and the Model Centre):

1. **The Model Centre.** The pre-chat "Explore AI Models" card opens a real
   browser now: three plain tiers — Fast / Everyday / Best for difficult
   questions — where a single tap writes the same preference key the chat send
   path reads. Everything technical (context windows, speed dots, capability
   chips, reasoning modes) stays collapsed inside per-model "Advanced
   details", and the spec-sheet comparison view is demoted to the Advanced
   section, off the consumer path. Twin-parity on iOS, from the same commit.
2. **Three GenUI passes: model freedom, 36-kind widget parity.** Key Insights
   cards, checklist +Add Task, the Quick Calculator with multi-line steps and
   source elevation, the rich-prose PROSE_PROMPT contract (flowing prose is
   the default, structure arrives when it genuinely helps), and Section 01-02
   screenshot parity verified across Android, iOS and Web.
3. **The 0.86.3–0.86.25 craft series finished the reference redesign.** The
   pre-chat lotus-orb hero and redesigned model picker, the Dreamy Pastel
   reference shells, Archived/Shared Conversations and Conversation Details,
   Data Export & Account Deletion, the identity editor, a real Language page,
   Travel Agent reachable, dead screens removed, overlays that are menus that
   hang off their buttons (not pages), settings rows that finally open pages,
   the library page behind every save, the dock fade — and the last stock
   dialog on either platform is gone.
4. **Action pills send real turns.** The contextual pills under an answer
   ("Ask follow-up", "Show me a detailed report", ...) now dispatch their
   prompt through the chat's gated send path on both platforms — the twin of
   the clarify chips, not a dead button.
5. **Six shipped-red fixes this round.** The PROSE_PROMPT template literal
   that broke parsing of every /api route, the VoiceScreen luminance import,
   the braces scanner's backtick blind spot, the imports linter's symbol
   index, the widget contour nine-point minimum the normalizer bypassed, and
   the eleven v2 widget kinds the contract-lock fixture never met. All
   verified green in CI before this APK shipped.

Fresh native engine: `libgs_ffi.so` rebuilt for all four ABIs (android-native
run 42246640175). `versionCode 148` installs over 125 without uninstalling.
Release build verified on CI: not-debuggable, signed with the shipping cert
(b1ffd75d…), baseline profile packaged (`android-release` run 42257461385).
Design screenshots captured from the same revision by `android-screens`
(run 42247691640; the pair above, plus the drawer in
`qa-0.86.25-drawer.png`).

## Previous shipped release: v0.86.2 — chat sheets are solid surfaces (versionCode 125)

    file    GS-AI-App.apk
    size    66270856 bytes
    sha256  07811e6d38981f64fedd52760d825618d9974554693281062dbecdf9265098b9

![options sheet](qa-0.86.2-options.png)

What landed in this build:

- **The chat options sheet no longer lets the transcript through.** It was
  80%-opaque glass, so a table and a code block behind it turned the rows into
  a smear. All three chat sheets now use the opaque `sheetSurface`; the glass
  language stays on the cards behind them.
- **The screenshot suite survives emulator ANRs.** A "Pixel Launcher isn't
  responding" dialog had stolen input for an entire run and produced a cascade
  of false MISSING markers; the suite now dismisses system ANRs before taps,
  before the consent poll and before every capture.
- Carried from v0.86.1: chats multi-select, previews as prose (including table
  separator rows), the dock fade, onboarding reached and unobstructed, the
  reference AI tiles, Model Details, and the chat options sheet with Chat
  Details.

Verification: `android-app` 39676536466 green, `android-screens` 39676540912
green (the screenshot above), `android-release` 39678530955 green (this APK).

## Previous shipped release: v0.82.0 — the glass release (versionCode 119)

    file    GS-AI-App.apk
    size    66188932 bytes
    sha256  3a7ecc9a5567bed93ed575cb4a7e289ce72b9c294f1b086a1a1ec98286738550

One material, five roles, one luminous orb — the reference design system is
now spread across every real surface on both platforms.

| Home — Elevated hero + Card suggestions | Modal — the on-device AI consent sheet |
|---|---|
| ![home glass](qa-0.82-home.png) | ![modal glass](qa-0.82-onboarding-modal.png) |

**What changed**

1. **The glass role contract, screen by screen.** `gsGlass(role, shape)` is now
   the only way a surface asks to be glass — a screen picks a *role*
   (Card / Elevated / FloatingControl / Modal / Sunken), never an alpha. Each
   role paints four optical cues in painterly order: a translucent fill, an
   in-scattered airlight veil, a directional top-leading rim (the key light in
   every reference frame), and an interior luminance fall, over a warm,
   non-grey shadow. Applied to the composer, top bar, suggestion row, the kit
   rows, the drawer, search, notifications, Settings/Billing/Profile, the Home
   hero and the chat sheets.
2. **The 12-state luminous orb.** A presentation layer (`OrbPhase`) maps the
   twelve product states — idle, listening, recording, thinking, reasoning,
   generating, streaming, success, error, paused, offline, reduced-motion —
   onto the nine golden dot-field geometries and lights them with an additive
   glow. Geometry is untouched, so the golden test stays green; the glow is
   driven by the real microphone level. `docs/ORBS.md` rewritten to match.
3. **Blur tiers.** A real `RenderEffect` frost on API 31+ behind the same
   `gsGlass` call, and a baked airlight-veil frost for API 26 — no bitmap
   readback, so the low tier never costs a frame.
4. **iOS parity.** The same five roles rebuilt in SwiftUI (`Material`
   translucency + veil + directional rim + luminance fall), the 12-phase orb,
   and RMS computed inside `AVAudioEngine.installTap` — the exact twin of
   Android's `onRmsChanged`. It compiles on real Xcode for both the device
   (aarch64-apple-ios) and the simulator.

`versionCode 119` installs over 118 without uninstalling. Release build
verified on CI: not-debuggable, single R8 dex, baseline profile packaged,
signed with the shipping cert, public origin baked in
(`android-release` run 39579101136). Design screenshots captured from the same
revision by `android-screens` (run 39566257125).

## Previous shipped release: v0.77.3

    file    GS-AI-App.apk
    size    64356239 bytes
    sha256  2d5ac003bc1339dbf9e059e33d952b288d93041aa69a3eb6a500ea3922303cbe

### v0.77.3 — quiet scrolling, and hard pictures get their pixels back

Scrolling up no longer opens the keyboard: `imeNestedScroll()` on the
transcript treated an upward drag as "bring the IME back" while the composer
held focus. Removed; the hide-on-drag stays.

Images whose longest edge is >= 2600 px now also send ONE native-resolution
crop of their densest region (local gradient map) after the full frame, so
small printed text — labels, documents, signs — survives instead of arriving
as mush at 2048 px. Best effort: any failure degrades to the full frame only.

`versionCode 92` installs over 91 without uninstalling.

## Previous release: v0.77.2

    file    GS-AI-App.apk
    size    64356239 bytes
    sha256  10cb33e35e6d2808ed7b9d6e3aa7db719997b643588b8d1caea09b050a34b7c2

### v0.77.2 — the orb is an orb

v0.77.1 put the activity orb in the composer's send slot but rendered it with
the 20dp preset scaled up — at 34dp that preset is fifteen fat blobs in a
circle, which reads as a broken spinner. Both presets were rasterised at the
shipped size and compared: INLINE scaled up falls apart, STANDARD (44-segment
ribbon, 11-ring globe, 41-node web) scaled down keeps its structure. The send
slot now draws STANDARD at 34dp.

The `Thinking…` line above the composer is gone too. With the orb in the slot
it was a third status for one fact, and the word collided with the
`Thinking ▾` mode chip inside the composer. The orb carries the phase; tap it
to stop the turn.

`ThinkingOrb` gained `renderScale` to draw a preset's authored geometry at any
physical size, centring the authored space instead of cropping it.

`versionCode 91` installs over 90 without uninstalling.

## Older release: v0.77.1

    file    GS-AI-App.apk (superseded)
    size    64356235 bytes
    sha256  8904fd1ddf6f7d67564c3d37c48fbbfcbe93441607afd257f24a3b4e58e9b9f3

### v0.77.1 — one composer, one status

Two things on screen were announcing "a turn is running" in shapes that meant
something else.

**The empty reply looked like an input field.** Before the first token an
assistant turn has no text, so all it painted was the `GS` monogram, the `GS`
label and a blinking streaming caret underneath — a circle, a name and a
cursor, which on a phone is indistinguishable from a text field parked at the
top of the transcript. That row now waits for its first token.

**The stop button is now the orb.** The send slot showed a white disc with a
stop square while a separate orb animated above the composer: two symbols for
one fact, one of them generic. The slot now renders the activity orb for the
turn's real phase — breathing ring before the first token, ribbon while
composing, globe while searching — and the orb is the stop target. Tap the
thing that is happening to stop it. 36dp touch target, INLINE geometry, so the
dot counts and speeds are the designed ones.

The static three-dot indicator is gone with it. The word stays (`Thinking…`,
`Searching…`) because that is the honest part.

`versionCode 90` installs over 89 without uninstalling.

## Older release: v0.77.0

    file    GS-AI-App.apk (superseded)
    size    64356235 bytes
    sha256  9afc99c16f0e1f97fc10e7417a5ffe29598b5900d6cb465b70e60578f3af8385

### v0.77.0 — the first APK that is actually a RELEASE build

Every APK before this one was a debug build. `android-release.yml` ran
`:app:assembleDebug` and uploaded `app-debug.apk` under the name GS-AI-App.apk,
so what you installed carried `android:debuggable="true"`. On Android that
means ART never AOT-compiles anything (interpreted + JIT, every process start,
forever), Compose keeps its debug runtime checks, CheckJNI is on, and R8 never
runs. That is why none of the previous smoothness work was felt: the code was
fine, the build was throwing it away.

Verify it yourself — `aapt2 dump badging GS-AI-App.apk | grep debuggable`
prints nothing on this APK, and it did on every one before it.

| | v0.76.0 (debug) | v0.77.0 (release) |
|---|---|---|
| dex files | 18 | **1** |
| classes.dex | 39 MB | **5.3 MB** |
| APK | 80 MB | **62 MB** |
| debuggable | true | **false** |
| `assets/dexopt/baseline.prof` | absent | **present** |

Also in this build:

1. **Local inference no longer runs on the main thread.** `GsNative.chat()` is a
   blocking JNI call that runs llama.cpp to completion. It was reached on
   `Dispatchers.Main.immediate`, so an on-device turn froze the UI for the whole
   generation. Now on a dedicated thread at `THREAD_PRIORITY_BACKGROUND`, so the
   inference pool yields to RenderThread whenever a frame is due.
2. **The message fade-in no longer replays on every scroll.** `messageAppear`
   was `Modifier.composed {}` per row, and its `remember` died with the row, so
   scrolling back through history re-animated whatever entered the viewport.
3. **Transcript images are cached.** They had no cache at all and were
   re-downloaded and re-decoded every time a row re-entered the viewport, on the
   fling path, behind an 8-second timeout.
4. **Lifecycle-aware state collection** at all six collection sites, and the
   thinking orb no longer builds draw calls for dots it has already faded out.

If anything crashes, send the stack trace: obfuscation is deliberately off, so
class and method names are readable.

## Older release: v0.71.3

    file    GS-AI-App.apk (superseded)
    size    84104873 bytes
    sha256  07ecf81df4fdd351b8686cbc82a9196d63d4aab1c9cce7d9e488cab6eed279ac

**The server this app talks to is currently returning 502 for every message.**
`POST /api/v1/conversations/{id}/messages` answers
`{"code":"upstream_error","message":"GS AI is temporarily unavailable."}` — verified
across every model name (`flash`, `fast`, `default`, `gemini-flash`, and no model
field at all). Conversation create and read both work; only the send path is down.
No APK can fix this. `GET /api/health` still reports `200 {"status":"ok"}` because
it never touches the upstream, so the outage looks healthy to monitoring.

### v0.71.3 — performance

Three causes of the reported slowness on the on-device path. Two fixed in Kotlin,
no native rebuild needed.

1. **~4.9s of dead air per reply, self-inflicted.** `streamLocalReply` replayed an
   already-finished answer one word at a time with `delay(26)`. The engine does not
   stream — `GsNative.chat` returns one completed `String` — so the answer already
   existed and the loop existed only to imitate the network path. At the 256-token
   budget that is ~190 words x 26ms. Now emits once.
2. **Four threads on an eight-core phone.** `init` hardcoded 2048/4, idling half the
   CPU for the whole generation. Now `initTuned` with `availableProcessors()` for
   both `n_threads` and `n_threads_batch`. `initTuned` was already in the shipped
   ABI, verified against the symbol table of the arm64 `libgs_ffi.so` in this APK.
3. **An empty streaming bubble rendered literally nothing.** The kinetic caret rides
   the last line of a live paragraph, so with zero blocks there was no caret and no
   dots — for the entire local generation. Now shows the caret alone.

### v0.71.2 — a failed turn was shown and stored as the assistant's reply

The app answered a failed send with "The connection dropped mid-turn while GS was
working. Reopen this chat in a moment to load the finished reply." Three lies in one
sentence: `accumulated` was empty so GS was not working; recovery had *already*
returned null so there was no finished reply to load; and the text was persisted as
the assistant's message. Now honest, with a JVM test that would have caught it.

### Contents

Native chat ships in this build: `libgs_ffi.so` for all four ABIs (armeabi-v7a,
arm64-v8a, x86, x86_64), alongside ML Kit OCR. `libgs_sd.so` is deliberately not
staged — diffusion is disabled on mobile. No Tesseract or Leptonica.

**arm64 device execution is now partially verified.** The phone found the v0.71.0
and v0.71.1 defects above, which is real arm64 evidence — but no arm64 *performance*
benchmark has run, so the thread change is the standard mobile configuration applied
without a device measurement behind it. Real-device TTFT remains estimated, not
measured.