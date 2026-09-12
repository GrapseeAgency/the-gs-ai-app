# GS AI App — Physical-Device Validation Checklist

**Purpose:** the implementation is natively engineered and statically gated; only real hardware can prove the resulting behaviour actually *feels* native. Every item below names the exact mechanism under test, the code path that implements it, the native standard it must meet, and what a failure looks like. After this round, every failure gets investigated at the named code path — root cause fixed, symptom never masked.

**Build under test**

| | |
|---|---|
| Artifact | `device-validation/GS-AI-App-device-validation.apk` |
| Version | versionCode **61** / versionName **0.60.0** (unchanged — no release, no bump) |
| Built from | commit `e4a14cd` lineage — v0.60.0 (released) + Task 87/88 mechanism reconciliation |
| SHA-256 | `f84798dbf4d9c9b488b958f0d2a3edcec899075ba861e9a7f1d25919c5a10388` |
| Size | 12,954,817 bytes |
| Install | `adb install -r device-validation/GS-AI-App-device-validation.apk` (same keystore as every release since v0.52 — installs straight over v0.58–v0.60.0, keeps all local data) |
| LiveUpdate | This build will **not** be offered an update by the in-app pill (manifest still serves the released 61 — same version, no downgrade ping). CDN files were not touched. |

**Recommended device matrix (minimum):**
1. Pixel-class stock Android (Android 14+ — predictive back + haptics-genic).
2. One OEM-heavy device (Samsung/小米/Xiaomi — IME, edge-gesture and back-portrait variance lives here).
3. One small phone (≤ 6.1") and one large/tablet or foldable-unfolded (adaptive behaviour).
4. One iPhone (iOS 17+) for the iOS checklist items — VoiceOver, Dynamic Type, Taptic.

---

## DV-01 · Haptics — Android

**Mechanism:** every haptic routes through `GsHaptics.enabled()` gating + `View.performHapticFeedback` with platform constants — never vibration patterns.
**Code paths:** `ui/theme/GsHaptics.kt:37`; `ui/home/HomeScreen.kt:772` (permission denial tick), `:933` (orb hold-threshold `CLOCK_TICK`); `ui/voice/VoiceScreen.kt:394–413` (`VIRTUAL_KEY` on committed send/copy, `LongPress` reserved for reveal); ChatScreen send dispatch (`VIRTUAL_KEY`).

| # | Step | Expected | Failure signature |
|---|---|---|---|
| 1.1 | Hold the hero orb until dictation arms | One quiet tick exactly at threshold (~350 ms), then mic opens | Tick fires on touch-down, fires late, double-fires, or no tick |
| 1.2 | Release orb early (tap) | **No** haptic on the tap-to-voice-mode navigation | Tick on plain tap |
| 1.3 | Send a message | Short `VIRTUAL_KEY` tick on commit only | Buzz on keystroke, or none on send |
| 1.4 | Deny mic permission (revoke in Settings, re-hold) | Tick + snackbar appears | Silent denial |
| 1.5 | Settings → Haptics **off** → repeat 1.1/1.3 | Zero vibration anywhere | Haptics still fire (gate broken) |
| 1.6 | Cold start → immediately hold orb | Tick still lands | First haptic of the session late/dead (Taptic-style warm-up issue is iOS-side; Android must be instant) |

## DV-02 · Haptics — iOS (Taptic)

**Mechanism:** `GSHaptics` generators pre-armed via `prepare()`; vocabulary: tap = committed opens, select = filters, success = mutations/copy, warning = destructives, press = hold threshold.
**Code paths:** `Components/GSHaptics.swift:24` (prepare), `Features/Home/HomeView.swift:463–470` (orb a11y action + press), ~25 call sites (send/copy/pin/archive/delete/filters/voice threshold), `prepare()` on screen appear.

| # | Step | Expected | Failure signature |
|---|---|---|---|
| 2.1 | Cold launch → Home → first send | Crisp `.light` impact with zero lag | First haptic mushy or late (prepare chain missing on that surface) |
| 2.2 | Copy any message / save to library | `success` (notification) tick | Impact instead of success pattern |
| 2.3 | Delete (library/assistant/project) | `warning` pattern **after** the confirm dialog action, never before | Warning fires when opening the dialog |
| 2.4 | Home orb hold past threshold | `press` impact at threshold, then live partials | Impact on touch-down |
| 2.5 | Settings → Haptics off → repeat | Fully silent | Gate not consulted |

## DV-03 · Predictive back (Android 14+)

**Mechanism:** manifest `android:enableOnBackInvokedCallback="true"`, zero custom `BackHandler` outside the two sanctioned escape hatches; nav transitions are seek-able by the gesture.
**Code paths:** `AndroidManifest.xml`; `ui/navigation/GsNavHost.kt:142–163` (pop transitions); `ui/chat/ChatScreen.kt` (BackHandler: search folds first, then edit mode); `ui/settings/SettingsScreen.kt` (BackHandler folds expanded sections only).

| # | Step | Expected | Failure signature |
|---|---|---|---|
| 3.1 | Chat detail → slow partial back-swipe, hold, release slowly | App screen shrinks with the finger; releasing at <threshold springs back **into place** | Screen jumps/cross-fades — transitions not seek-able |
| 3.2 | Back-swipe completes on a pushed screen | Predictive-to-home or in-app pop animation plays without a frame of blank | Flicker/black flash |
| 3.3 | With chat search open, press system back | Search bar folds first; second back leaves the screen | Back exits the screen with search open |
| 3.4 | With a Settings section expanded, system back | Section folds first | Back leaves Settings |
| 3.5 | Keyboard open, system back | Keyboard dismisses first (IME is back-target #1) | Back navigates while keyboard stays |

## DV-04 · IME / OEM keyboards

**Mechanism:** `adjustResize` + `enableEdgeToEdge` + single `imePadding` ownership; transcript `imeNestedScroll()`; iOS interactive dismissal + search-field conventions; auth focus chain.
**Code paths:** `MainActivity` (manifest `windowSoftInputMode="adjustResize"`); chat transcript LazyColumn (`imeNestedScroll`); `Components/AeroComponents.swift` AeroInputBar (composer `axis:.vertical` + enter-to-send dedup; `searchField` mode); `Features/Auth/AuthFlowView.swift` (focus chain, textContentType).

| # | Step | Expected | Failure signature |
|---|---|---|---|
| 4.1 | Gboard: focus composer, type multi-line, hit ↵ (Enter-to-send ON) | Sends on ↵ exactly once — no double-send from the newline path | Two messages per ↵ (the 150 ms dedup window failed) |
| 4.2 | Enter-to-send OFF, hit ↵ | Newline inserted, nothing sends | Message sends |
| 4.3 | Samsung/SwiftKey/floating keyboard: focus composer | Composer rises with the keyboard, transcript bottom line stays visible | Composer pinned under keyboard / gap |
| 4.4 | With keyboard open, drag the transcript downward | Keyboard tracks the finger down (interactive) — iOS; on Android the transcript scrolls under a resizing IME without a blank band | Keyboard vanishes instantly (iOS `.immediately` regression); black band (Android inset race) |
| 4.5 | Search fields (Search/ChatSearch/Explore/Research): type, hit search ↵ | Commits and **retires** the keyboard; no autocap/autocorrect | Keyboard stays; capitalised queries |
| 4.6 | Auth sign-in: email → ↵ → password → ↵ → sign in | Focus walks the chain in order | Focus dies after first field |
| 4.7 | Split keyboard (iPad) / one-handed mode (OEM) | Composer and send button remain reachable, nothing clipped | Controls under the keyboard seam |

## DV-05 · Streaming under load

**Mechanism:** app-scoped stream ownership + isolated live-bubble recomposition (Android, 33 Hz flush); off-main `StreamAccumulator` + `Equatable MessageBubble` (iOS). Mid-stream, only the live bubble repaints.
**Code paths:** `data/chat/ChatStreamController.kt` (Android ownership); `ui/chat/ChatScreen.kt` (live bubble slot, follow-scroll single snapshotFlow + isAtBottom gate); `Networking/StreamAccumulator.swift`, `Features/Chats/ChatDetailView.swift` (live-edge sentinel + userIsReading latch).

| # | Step | Expected | Failure signature |
|---|---|---|---|
| 5.1 | Ask for a 1200+ word answer; while it streams, scroll up fast | Zero hitch; scroll stays yours; no snap-back | Fights the finger / frame drops |
| 5.2 | Stay at bottom while streaming | Text follows seamlessly | Stair-step stutter (flush cadence visible) |
| 5.3 | Scroll away mid-stream, wait 10 s, jump-to-latest | Re-engages follow from the true live edge | Lands mid-history, follow off |
| 5.4 | Stream → press stop → immediately re-send | Old stream torn down, no ghost tokens from turn 1 | Interleaved/partial text |
| 5.5 | Background the app mid-stream, return 30 s later | Turn completed and persisted; transcript intact | Dropped stream / duplicated message |
| 5.6 | Low-end device (or 60 fps→throttled): long code block streaming | Blocks render plain while growing, highlight lands once on completion | Per-token re-highlight flicker |

## DV-06 · Audio

**Mechanism:** Android TTS requests audio focus and abandons it; iOS sets a `.playback` session category properly; recognizer and TTS live and die with VoiceScreen.
**Code paths:** Android TTS focus handling (Task 85 wiring, `data/` tts + VoiceScreen); iOS `Features/Voice/SpeechPlayer.swift` + `AVAudioSession` setup; `ui/voice/VoiceScreen.kt` lifecycle (recognizer/destroy pairing).

| # | Step | Expected | Failure signature |
|---|---|---|---|
| 6.1 | Start TTS read-back → receive a phone call | TTS ducks/pauses, resumes or stays paused correctly after | Two audios overlap |
| 6.2 | TTS playing → open Music/YouTube | GS pauses (focus abandoned) | Both play |
| 6.3 | Voice dictation with Bluetooth headphones connected | Input from headset mic; partials appear | Silence; phone-mic used |
| 6.4 | Hold-to-dictate → lock screen mid-hold | Mic torn down immediately (ON_STOP) | Recognizer keeps running behind lock |
| 6.5 | Silent switch ON (iPhone) → TTS read-back | TTS still audible (playback category) or deliberately silent per design — record what happens | Inconsistent |

## DV-07 · Flaky-network recovery

**Mechanism:** LiveUpdater — 10-min throttle, single-flight, HTTP Range resume (206/416), four integrity gates before installer; offline banner animates in; chat send fails honest.
**Code paths:** `data/liveupdate/LiveUpdater.kt`; `components/GsComponents.kt:448` (GsOfflineBanner `AnimatedVisibility`); ChatScreen send error path.

| # | Step | Expected | Failure signature |
|---|---|---|---|
| 7.1 | Airplane-mode ON while reading a chat | Offline banner slides in (fade+expand) | Banner pops/abrupts, or absent |
| 7.2 | Send a message offline | Honest failure state with retry — no fake success | Spinner forever / optimistic message lost silently |
| 7.3 | Airplane-mode OFF | Banner animates out; connectivity resumes without restart | Banner sticks |
| 7.4 | Kill network **mid-download** of a LiveUpdate (large APK, throttle 200 kB/s in developer settings) → restore | Download resumes from byte offset (Range), integrity gates still pass, installer opens | Restart-at-zero, or corrupt install attempt (magic/gates failed) |
| 7.5 | Two LiveUpdate checks racing (fast Home re-entries) | Single-flight: one download only | Duplicate downloads |

## DV-08 · Gesture physics

**Mechanism:** pointer-stream `kineticPress` (never consumes, releases when scroll wins, graphicsLayer-scoped); drawer drag commit/fling thresholds; edge-swipe single-purpose; orb hold choreography.
**Code paths:** `ui/theme/Motion.kt:93–141` (both kineticPress overloads); `Components/AeroDrawer.swift:144–150` (close drag −110/−240 thresholds); `Navigation/AppRouter.swift:142–165` (reveal span, Home-root gate `router.path.isEmpty`); `ui/home/HomeScreen.kt:922–946` (orb press-hold-release state machine).

| # | Step | Expected | Failure signature |
|---|---|---|---|
| 8.1 | Start a vertical scroll on top of any card/chip | Press deformation releases the moment the scroll consumes the gesture — no stuck-shrink | Card stays scaled after scrolling away |
| 8.2 | Long-press an assistant card (hold without release) | Action sheet opens; card visually responds while held; **no** open-sheet-then-navigate double action | Both long-press and tap fire |
| 8.3 | Drawer: drag halfway, release dead | Springs back. Drag past ~50% or fling left | Commits on a dead short drag / refuses a good fling |
| 8.4 | On a pushed iOS screen, swipe from left edge | Interactive pop scrubs the previous screen — drawer does NOT also open | Drawer mounts behind the popping screen |
| 8.5 | On Home (iOS), swipe from left edge | Drawer rides the finger | Nothing (over-gated) |
| 8.6 | Orb: hold 400 ms, release mid-dictation | Partial transcript hands off or dissolves quietly — never a stuck "Listening…" | Label freezes |
| 8.7 | Tap rapidly 5× on any list row | Navigation fires once per deliberate tap, no double-push | Two identical screens stacked |

## DV-09 · TalkBack / VoiceOver

**Mechanism:** real roles and actions, not labels-on-nothing. Selected chips announce selection; long-presses have labels; the orb is a button with a named action; screen-reader-hints mode adds explicit activate actions.
**Code paths:** `GsComponents.kt` GsCard (`onLongClickLabel = "More options"`), GsChip (`selected` + `Role.Button` + activate hint), GsSectionHeader (heading semantics); `HomeScreen.kt:912–921` (orb role+action); `AeroComponents.swift` AeroChip (`isSelected`), HomeView orb (named dictation action); Settings screenReaderHints/highContrast toggles.

| # | Step | Expected | Failure signature |
|---|---|---|---|
| 9.1 | TalkBack: explore Home orb | "…button, double-tap to Open voice mode" | Orb is silent/unlabelled |
| 9.2 | VoiceOver: filter chips on Search | Announces value + "selected" when active | State never announced |
| 9.3 | TalkBack: long-press-equivalent on assistant card | "More options" action available in the local-menu | Action missing ( TalkBack users cannot reach the sheet) |
| 9.4 | Turn ON screen-reader hints in Settings → swipe a row | Explicit "activate" hint replaces ambiguous announcements | Hints toggle does nothing |
| 9.5 | High contrast ON → scan Home/Chat/Settings | Strokes/borders strengthen; text falls to max-contrast colours | Some text stays low-contrast (variant-on-variant) |
| 9.6 | Focus order on auth sign-in | Name → email → password → submit, reading order matches visual order | Focus jumps randomly |

## DV-10 · Cold start

**Mechanism:** baseline profile installed + ProfileInstaller; per-key SettingsStore hydration (a poisoned preference can't kill launch); guarded Application init; DayNight launch theme.
**Code paths:** `android/app/src/main_baseline_profile*` + ProfileInstaller (Task 70); `data/SettingsStore.kt` hydration; `data/CrashCatcher` (on-device stack trace, shareable in Settings); iOS `GSApp` bootstrap.

| # | Step | Expected | Failure signature |
|---|---|---|---|
| 10.1 | Force-stop → cold launch (stock launcher) | First frame < ~1 s on a Pixel-class device; no white/black flash before the obsidian canvas | Flash of wrong-coloured launch screen (theme mismatch) |
| 10.2 | Cold start with airplane mode ON | App opens fully usable offline; honest empty/error states | Hang on splash waiting for network |
| 10.3 | Corrupt a settings key (or install over an older data set) → cold start | Launch survives, that key resets to default | Crash loop |
| 10.4 | After any crash: Settings → crash report entry | Stack trace available and shareable | Silent loss |
| 10.5 | Repeat 10.1 five times | Consistent; no janky variance | One-off multi-second hangs (disk/profile issue) |

## DV-11 · Dynamic Type / font scaling

**Mechanism:** iOS — every font `relativeTo:` + app font-scale slider mapped to real `DynamicTypeSize` caps; Android — system fontScale up to 2.0 + in-app slider staged in memory and committed once on release.
**Code paths:** `GSApp.swift:58–67` (scale→DynamicTypeSize map), `Theme/DesignSystem.swift` `Aero.responsive/relativeTo`; `ui/settings/SettingsScreen.kt` (stageFontScale commit-on-finish), layout tests at `sp` scale app-wide.

| # | Step | Expected | Failure signature |
|---|---|---|---|
| 11.1 | iOS: set font scale 1.3 + system Dynamic Type XXL → open every tab | Text scales; no clipped labels, no overlapping rows; chips wrap or ellipsize honestly | Truncated control labels |
| 11.2 | Android: system font size → Largest (2.0) → Home | Hero, composer and disclaimer reflow; pinned composer never covers the transcript | Composer overlaps content |
| 11.3 | In-app font slider (both platforms): drag through all steps | Preview moves live; single commit on release (Android logcat: no disk write per frame); stepping back to 1.0 restores exactly | Write storm per drag tick; visual drift after round-trip |
| 11.4 | Accessibility bold text (iOS) / bold fonts (Android) | Everything bolds; no baseline clipping in chips | Clipped descenders |
| 11.5 | 1.3× chat transcript with long code blocks | Code blocks scroll horizontally, never wrap into garbage | Broken monospace layout |

## DV-12 · Edge-to-edge & system bars

**Mechanism:** `enableEdgeToEdge` + transparent system bars; inset ownership — status/navigation padding at the root, `imePadding` at exactly one owner per screen; iOS safe areas + restored system navigation titles.
**Code paths:** `MainActivity` (edge-to-edge); `HomeScreen.kt` root Box (status/nav padding); `AuthScreen.kt` (statusBars+navigationBars+ime); `ExploreView.swift:162–163` etc. (navigationTitle restore).

| # | Step | Expected | Failure signature |
|---|---|---|---|
| 12.1 | Device with camera cutout → Home, Chat, Voice, Settings | Content never intrudes into the cutout; aurora background may extend behind bars (by design) | Text under the cutout |
| 12.2 | Gesture-nav vs 3-button-nav (switch in system settings) | Composer/disclaimer clear the nav area in both; no double padding | Big dead zone with 3-button nav |
| 12.3 | Keyboard open on chat → status bar stays visible at top | Screen doesn't shift wholesale | Whole app jumps up (double imePadding) |
| 12.4 | Landscape (phone): Chat + Voice | Safe areas respected left/right; transcript usable | Content behind the notch/cutout |
| 12.5 | iOS: push any detail screen | System inline title appears in the nav bar (Explore, Library…) | Blank nav bar / frozen in-content title only |

## DV-13 · Large-screen behaviour (tablet / foldable / unfolded)

**Mechanism:** adaptive helpers (`Aero.responsive`, iPad column from Task 85); two-pane layouts are a **frozen design decision** (documented below) — the test is that the single-pane layout degrades gracefully, not that panes appear.
**Code paths:** iOS `Aero.responsive` + iPad column; Android layout weights/max-widths; `ExploreView`/`CreateView` grid adaptive columns.

| # | Step | Expected | Failure signature |
|---|---|---|---|
| 13.1 | Tablet landscape: Home | Hero doesn't stretch absurdly wide; composer has a sane max measure | One line stretching full 12" width |
| 13.2 | Foldable: fold/unfold mid-chat | Conversation survives the configuration change; stream (if live) continues | State loss / duplicate transcript |
| 13.3 | Tablet: transcript list | Comfortable measure (columns/width caps), not 30-word lines | Full-bleed text lines |
| 13.4 | Split-screen (Android) 50%: chat + composer | Everything usable at reduced height | Composer clipped, scroll dead |
| 13.5 | iPad Stage Manager / slide-over | Layout reflows, keyboard focus intact | Stale frame after resize |

---

## Explicitly NOT defects (documented decisions — do not re-flag)

1. **Deep links** — no producer/backend exists yet; blocked externally.
2. **Push notifications** — no backend service; blocked externally.
3. **M3 1.3.0 drawer predictive-back scrub** — framework-version blocked (material3 1.4+ required).
4. **iOS in-content headers vs system `navigationTitle` on remaining ~15 screens** — design-frozen (partially restored where done: Explore, Library…).
5. **Chip hit targets < 44 pt** — expansion is a visual design change; frozen.
6. **Two-pane tablet layouts** — design-frozen (13.1–13.5 test graceful single-pane, not panes).
7. **Shimmer placeholders vs `.redacted`** — visual identity decision, frozen.
8. **Hermes/animate anything decorative** — aurora/skeleton loops are AI-life signs and deliberately never gated by reduce-motion.

## Failure report template (fill per failing item)

```
DV-XX.Y  <area.step>
Device / OS / build:        (e.g. Pixel 8, Android 15, validation build f84798db)
Platform:                   Android / iOS
What was done (exact):
What happened (recording attached):
Expected per checklist:
Happens every time:         Y/N
Console/logcat excerpt:     (if any)
```

Every accepted failure triggers a code-path-level investigation against the path named in the item, a root-cause fix on this same unreleased tree, and a re-run of the affected steps — no symptom masks, no version bumps, no release until the device results support it.

---

# PHASE 5 — INPUT & MULTIMODAL FOUNDATION (device test matrix)

Backend prerequisite: run the repo's Next.js API (`bun run dev` on :3000) — the
apps point at it (`10.0.2.2:3000` emulator / `localhost:3000` simulator) and
uploads land in `uploads/attachments/`.

| # | Case | Android | iOS |
|---|---|---|---|
| 1 | Text-only send | unchanged path, byte-identical request body | unchanged path |
| 2 | Single image (Gallery) | chip → spinner → thumb → send → chip in bubble; file in `filesDir/attachments/` | chip → spinner → thumb → send → chip in bubble; file in Application Support |
| 3 | Multiple images (Gallery) | multi-select; >remaining slots → honest snackbar; extras not added | same |
| 4 | Small PDF (Files) | monochrome PDF icon chip + name + size | same |
| 5 | Large PDF (>10 MB) | chip fails "Too large" BEFORE upload; Retry stays failed | same |
| 6 | Unsupported file (e.g. .apk/.mp3) | picker filters / honest "Unsupported type" | same |
| 7 | Cancelled picker | no chip, no state change | same |
| 8 | Camera capture | TakePicture via FileProvider; cancel → silent; no camera app → honest snackbar | UIImagePickerController; denied → honest state |
| 9 | Denied photo permission | NOT REQUIRED — system Photo Picker needs no permission | NOT REQUIRED — PHPicker needs no permission |
| 10 | Upload failure (airplane mid-upload) | chip → "Network error" + Retry; retry re-runs upload only (no re-stage) | same |
| 11 | Processing failure | N/A — no processing state exists (honest absence) | N/A |
| 12 | Retry after failure | only failed step re-runs | same |
| 13 | Remove attachment | staged copy deleted when never uploaded; uploaded record untouched | same |
| 14 | Rotate / background / foreground | drafts + chips survive (process-scoped store); ON_STOP snapshot | drafts + chips survive; scenePhase background snapshot |
| 15 | Send after attachment | blocked until every chip ready; attachments-only send works (empty text) | same |
| 16 | Draft persistence with attachments | ready chips restore with draft text; missing staged file dropped honestly | same |
| 17 | History reload | chips render from Room JSON (staged thumb or monochrome icon) | chips render from SQLite JSON |
| 18 | Voice handoff | mic → VoiceScreen → "Send to chat" seeds composer (no auto-send) | mic → VoiceView → "Send to chat" seeds composer (no auto-send) |
| 19 | Attach sheet honesty | Gallery/Camera/Files/Voice real; Code snippet + Prompt template muted "Not available yet" | same |
| 20 | Orb discipline | no orb during upload (chip states carry it); streaming/voice mappings unchanged | same |

Monochrome check: all chrome (chips, sheet, icons, spinners) theme-resolved;
only real user thumbnails may show their own colors.

## PHASE 6 — IMAGE UNDERSTANDING (vision is real)

| # | Scenario | Android | iOS |
|---|----------|---------|-----|
| 21 | Attach photo + "What is this?" → send | real analysis streams into transcript; orb = Working… pre-token, Composing… on tokens | same |
| 22 | Image only (no text) → send | server asks "Describe this image." — real description back | same |
| 23 | Screenshot text ("Read the text in this image") | model reads actual on-screen strings | same |
| 24 | Follow-up WITHOUT re-upload ("what colour was it?") | model still sees the image (server re-includes last 2 image turns) | same |
| 25 | Multiple images (2-6) | order-aware answers; ≤6 images per request | same |
| 26 | Corrupt image (valid magic bytes, broken body) | honest turn error: "appears to be corrupted and could not be opened" + retry path | same |
| 27 | GIF attachment | transcoded server-side (provider rejects GIF natively) — real answer | same |
| 28 | HEIC (iOS camera format) | N/A on Android picker (JPEG) | normalized server-side to JPEG |
| 29 | Oversized (>10 MB) upload | 413 before send (unchanged) | same |
| 30 | MIME-spoofed upload (JPEG bytes named .png) | 415 magic-byte reject (unchanged); decoder re-checks server-side | same |
| 31 | Provider outage mid-vision | honest "Image understanding is unavailable right now. Please try again." | same |
| 32 | Large image + scrolling during stream | transcript stays responsive (30 Hz coalescing, thumbnail chips) | same |

Monochrome check: vision adds no chrome — orb states reuse the Phase 4 catalogue.
