# GS AI — SCAFFOLD LEVER REPORT (living document)

Experiment: **SAME model, DIFFERENT scaffold.** Generator pinned to the gs-ai
tier (served id recorded per run — marketing name never substituted). One
lever per dispatch (rule 1). Every score carries a CI (rule 3). Every claim
must trace to a raw artifact or live log line.

Model note (rule 7): the shim reports the SERVING id returned by the upstream
provider. Historically observed `glm-4-plus` for the `gs-ai` tier while the
serving catalogue maps it to glm-4.6 — the discrepancy is recorded, not
hidden; the per-run result files carry whatever the provider returned at run
time.

## Dispatch log (rule: every dispatch recorded)

| Run | Arm | Dispatched (UTC) | HTTP | Head sha | Outcome |
|-----|-----|------------------|------|----------|---------|
| 36043125749 | baseline (run 9, pre-fix harness) | 2026-09-24 18:42 | 204 | e1bc2c4 | FAILED: gpqa/aime cancelled at 6h runner cap under z-ai throttle (~245 real calls); ifbench BLOCKED (task missing); tau2 BLOCKED (websockets dep, fixed) |
| 36079361754 | baseline (fixed harness) | 2026-09-25 00:50 | 204 | b997585b | in flight |

## Execution architecture

sandbox (writes code, dispatches via REST)
  → GitHub Actions benchmark.yml (matrix: benchmark × [gs-ai]; `scaffold`
    dispatch input selects ONE lever)
  → eval jobs drive inspect-ai / tau2 against the sandbox shim
    `/api/v1/openai/*` (`gs-ai@<profile>` selects the scaffold variant)
  → aggregate job commits the scorecard to `bench-scores` and uploads
    result artifacts (raw logs included, uploaded `if: always()`).

No benchmark executes in the sandbox. The production circuit breaker is
never bypassed; throttle windows are waited out.

## Lever definitions (verbatim scaffold objects in src/lib/bench/scaffold.ts)

| Lever | Profile | What changes |
|---|---|---|
| Baseline | `gs-ai` | raw synthesis, byte-identical to the Phase 1 shim path |
| ACI (tool design) | `gs-ai@aci` | poka-yoke tools (typed/enumerated args validated BEFORE execution, structured errors), per-tool output summarization, 8k offload + agent-aware hints |
| Verification gates | `gs-ai@verification` | separate-model verifier (glm-4.5-flash ≠ generator, temperature 0), max 2 regenerations, honest "unverified" label |
| Context management | `gs-ai@context` | episodic memory: LLM summary when >5 messages, last 2 verbatim (single-turn = honest no-op) |
| Planner/executor | `gs-ai@router` | planner glm-4.6 thinking-high (≤5 steps) → executor gs-ai |

## RESULTS — THE TABLE

Status marks: ✅ scored · ⏳ run in flight · 🚫 BLOCKED (raw reason in artifact)

| Lever          | Benchmark    | Score [CI]          | Δ vs baseline       | Cost Δ | Latency Δ | Verdict |
|----------------|--------------|---------------------|---------------------|--------|-----------|---------|
| Baseline       | gpqa_diamond | ⏳ run 36079361754  | —                   | —      | —         | —       |
| Baseline       | aime         | ⏳ run 36079361754  | —                   | —      | —         | —       |
| Baseline       | ifbench      | ⏳ run 36079361754 (run-9 attempt: task missing, fixed 80405b3) | — | — | — | — |
| Baseline       | tau2_telecom | ⏳ run 36079361754 (expected BLOCKED: user-sim unfunded) | — | — | — | — |
| ACI            | all          | pending dispatch    | —                   | —      | —         | —       |
| Verification   | all          | pending dispatch    | —                   | —      | —         | —       |
| Context        | all          | pending dispatch    | —                   | —      | —         | —       |
| Planner/Exec   | all          | pending dispatch    | —                   | —      | —         | —       |
| C++ engine     | n/a (app)    | see native/ENGINE-OVERHEAD.json | n/a     | n/a    | core overhead measured; TTFT/tok-s/battery/offline NOT MEASURED | built, device metrics pending |

## Provenance chain

- Scaffold layer commit: 5d598db
- IFBench harness fix (official dataset+verifier custom task): 80405b3
- Eval pacing fix (suite defaults merge; conn 4→2): 62c5e55
- C++ engine: a8ea7d2 (native/, ASan-clean, 14/14 smoke checks)
- Workflow: benchmark.yml (`scaffold` choice input; competitor columns dropped —
  402s are unfunded keys, not results)

## Already-live lever evidence (pre-dispatch endpoint validation, 1 call each)

- ACI: model emitted a trailing-junk tool call (`}}}`) and wrong arithmetic
  (9810); scaffold recovery + calculator returned the correct 5210
  (BENCH-SCAFFOLD profile=aci ... tool_calls={"calculator":1}).
- Verification: verdict flow PASS with 1 verifier call (gs_scaffold telemetry).
- Router/context smokes were blocked by the open z-ai breaker and deferred to
  the lever dispatches (their telemetry JSONL will be the evidence).

## Power discipline

GPQA-198 at a 0.90 baseline resolves only MDE ≈ 10 points (q = 0.455) —
UNDERPOWERED for +5pt claims. The scorecard's power section refuses winner
declarations on underpowered n (stats/power.py). IFBench at n=300 is powered
for ~±5-6pt at typical baselines; AIME at n=30 resolves only very large
effects (~±18pt) — flagged.
