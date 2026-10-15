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
