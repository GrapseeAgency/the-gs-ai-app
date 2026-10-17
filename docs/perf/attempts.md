# perf attempts log

Each reverted or non-moving fix attempt is logged here, one entry per attempt:

- iteration
- interaction id
- hypothesis + fix
- raw number before/after
- revert reason (if reverted)

## ITERATION 0
- checker built: scripts/smooth-check.sh (48 interactions, adb-only, gfxinfo framestats + device-budget gate + optional perfetto).
- awaiting operator baseline run.
## RUN 4 (emulator baseline attempt, run 38777152919) -- validity analysis
- 0 of 48 rows usable as-is: 33 rows rendered ZERO frames. Rows with data: 01 (4f, p50 3050ms), 02 (3f, 1800ms), 03, 05 (7f, 42.86% janky), 06 (6f, 50%), 08, 23 (2f), 28 (3f), 43 (7f), 47 (9f), 48 (5f).
- Root causes found in the artifact, not the app:
  1. IME stayed open after sends -> every swipe in 07/08/09 started INSIDE the keyboard; the app rendered nothing. Fixed: send_msg/seed_msgs close the IME; swipe bands raised above IME reach.
  2. 04 pulse-swipe on an EMPTY chat invalidates nothing (no items to scroll) -> 0 frames is the honest result of a no-op gesture, not jank. Fixed: measured action = IME resize over the empty state (a real chat-layout pass).
  3. Android gfxinfo prints a 4950ms PERCENTILE SENTINEL when 0 frames rendered. The checker propagated it as data. Fixed: 0-frame rows now report invalid (frames=0, percentiles "-") and FAIL.
  4. No window-focus evidence existed to diagnose 0-frame rows. Fixed: per-interaction mCurrentFocus + uidump snapshots in the run dir.
- Ordering signal from the rows that DID have data (emulator, software GPU):
  01-cold-start p50 3050ms >> 02-warm 1800 > 23-drawer 1800 > 28-mode-picker p95 1300 > 47-model-load 1050 > 48-splash 1000 > 05-send 150 > 06-streaming 77 > 43-error 121.
  Startup (01/02/48) dominates; then drawer-open, mode-picker-open, model-load; send/streaming are single-digit-jank on the emulator already.
- App fixes already in this run (9f? no -- fixes landed before run 4): streaming scope isolation (AssistantMessage no longer recomposes per token; commit "perf(06-streaming-reply...)") and onboarding Ready-sweep scope (commit "perf(40-42-onboarding)"). Their effect needs a run with valid streaming/scroll rows to show; run 5 measures that.

## RUN 9 (valid full baseline) + SHIP
- Session preflight via run-as fixed the last systematic validity failure: chat reached, 50-message seeding worked, 30+ rows carry real frame data (06 streaming 99 frames, 08 scroll 114, 12 image 102, 47 model 68, 43 error 58).
- App fixes in this build: streaming scope isolation (AssistantMessage no longer recomposes per token) + onboarding Ready-sweep scope. Validated compiling in every run since 94b7521.
- Ordering signal (janky% / p50, run 9): attach-open 100%/117ms, image-picker-open 100%/200ms (system picker UI, not app code), long-scroll 87.7%/85ms, empty-chat IME resize 87.0%/125ms, streaming 85.9%/89ms, model-load 85.3%/85ms, code-block 84.1%/77ms, fling 84.6%/p95 350ms, typing 81.1%/85ms.
- The emulator floor: swiftshader renders a well-formed Compose frame in 85-125ms vs the 60Hz 16.67ms budget, so every content-bearing interaction sits at 75-100% janky regardless of app health (the 6ms mode-picker row still shows jank rows from single hitches). The 1%-jank gate is physically unreachable on software rendering; the ORDERING is the deliverable, as the mandate states.
- Remaining 0-frame rows (02/03/07/13/21/22/24/26/27/29/34/36-38/40/42/44/46/48): launch-window rows (01/02/48) collect after gfx_reset timing windows that the thrashing emulator occasionally empties; state-flow rows (21/22/26/27/34/36-38/44) need per-screen needle tuning on the next operator-backed iteration. All raw evidence retained per run in docs/perf/emulator-runs/.
- Checker harness iterations this round: PATH fix (38769712321), boot hardening + Vulkan-off (38771590818), ANDROID_AVD_HOME pin (38775646716), IME discipline + no-data sentinel fix (38780804440), crash-evidence capture + CI rebase (38784123399), full auth walker + foreground guard (38784673957), session preflight via run-as (38789594585->valid), memory relief 4GB + trace 16mb.

## ITERATION 3 (perf-smooth-3) — runs 10/11

Fixes shipped (one commit each):
- 8a732e5 perf(08/09/10/11/12): BlockMemo LRU — finished-message block parse memoized process-wide (viewport re-entry no longer re-parses markdown on main thread).
- ce5a61d perf(11): highlightCode memo by code+lang+palette (no regex tokenizer re-run on code block re-entry).
- ad73644 perf(15): WorkspaceEmptyState draft deferred read (keystrokes no longer invalidate screen scope on empty chat).
- 6cb3369 perf(06): STREAM_PAINT_MS 33->50ms (flush tax halved).
- 7fd4f50 perf(01/02/47): GsNative context create moved OFF main thread — root cause: ModelStore.init ran GsNativeLoader.initWith (native GGUF context create) synchronously in Application.onCreate, stalling first frame of every start.

Run 10 (6cb3369, fixes 1-4): UNIFORM +20-30% shift across ALL rows including untouched ones (19: 100->94 jank% w/ p95 550 same, 43: 74.1->85.7, 30: 70->77.8, 45: 75->80) while direct-target rows 08/09 recorded 0 frames (measurement window miss, no data). A uniform shift across unrelated rows = session-wide emulator slowdown (uiautomator churn visible in logcat-fatal), NOT per-fix regression. No reverts on this evidence; run 11 (7fd4f50, all 5 fixes) re-measures. Verdict for fixes 1-4 deferred to run 11.

## RUN 11 VERDICT (all 5 fixes, 7fd4f50)
Controls flat (19/30/39/45 within noise of run 9). Targeted tails improved: 11 p95 350->150, 12 200->150, 47 300->150, 06 300->200, 05 250->200. Jank% band unchanged (swiftshader floor). ALL 5 FIXES KEPT; no reverts. Run 10's uniform shift confirmed as session noise.
