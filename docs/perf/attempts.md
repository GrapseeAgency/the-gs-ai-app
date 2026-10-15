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
