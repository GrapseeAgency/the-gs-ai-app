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
| 36079361754 | baseline (fixed harness) | 2026-09-25 00:50 | 204 | b997585b | FAILED: ifbench ImportError (fixed 84faf9e); tau2 data-dir (fixed); gpqa/aime CANCELLED at 6h cap — z-ai 429 lockdown 02:00-06:50Z, 0 recoverable samples |

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
| Baseline       | gpqa_diamond | 🚫 BLOCKED — z-ai 429 lockdown + 6h runner cap (tests/baseline-scaffold-v1.json) | — | — | — | pending quota |
| Baseline       | aime         | 🚫 BLOCKED — same (0 recoverable samples from partial inspect logs) | — | — | — | pending quota |
| Baseline       | ifbench      | 🚫 BLOCKED this run (ImportError; FIXED 84faf9e — needs re-dispatch) | — | — | — | pending quota |
| Baseline       | tau2_telecom | 🚫 BLOCKED this run (data dir; FIXED post-dispatch) → then true blocker = user-sim 402 (needs funded OpenRouter key) | — | — | — | pending funding |
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

## BLOCKER (2026-09-25T07:00Z) — exactly what is broken

The z-ai account (primary provider for the gs-ai tier) has returned
account-level 429 on EVERY call since ~02:00Z (7+ hours). Evidence:
`dev.log` `[ZAI-BREAKER] 429 observed` on every 10-minute probe, plus two
Actions runs burning their 6-hour caps in breaker-cooldown cycles (run 9:
~245 calls in bursts then lockdown; run 36079361754: 245→245 calls).

Per the task's own rule ("If the workflow cannot run for a reason you cannot
fix, say exactly what is broken and stop. Do not fall back to local"):
benchmark execution is STOPPED until provider quota returns. The sandbox
breaker discipline was never bypassed.

## Resume sequence (when quota returns — verify with ONE shim probe first)

1. Probe: `curl -s -X POST http://localhost:3000/api/v1/openai/chat/completions -H 'Content-Type: application/json' -d '{"model":"gs-ai","messages":[{"role":"user","content":"reply OK"}],"max_tokens":8}'` — a 200 means quota is back.
2. Dispatch baseline (fixed harness completes all four rows):
   `curl -X POST -H "Authorization: token $PAT" -H "Accept: application/vnd.github+json" https://api.github.com/repos/GrapseeAgency/the-gs-ai-app/actions/workflows/benchmark.yml/dispatches -d '{"ref":"main","inputs":{"model":"gs-ai","suite":"knowledge","scaffold":"baseline"}}'`
3. Download artifacts → `python3 scripts/assemble-baseline.py --run-dir <artifacts> --output tests/baseline-scaffold-v1.json`
4. Then one arm per dispatch, in order: `scaffold=aci` → `verification` → `context` → `router`; after each: assemble + `stats/regression.py` delta vs baseline + verdict in this table.
5. tau2 stays BLOCKED unless an OpenRouter key with user-sim funding is provisioned (raw 402) — never silently substituted.
