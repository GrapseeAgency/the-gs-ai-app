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
| 36155160244 | baseline (subset ifbench,tau2_telecom) | 2026-09-25 15:35 | 204 | f90ee73 | ifbench cancelled at 6h cap at 230/300 samples — PRE throughput/protocol work, log mean 0.2783 (64/230), provenance only; tau2 honest BLOCKED (user-sim 402); subset skip path verified step-by-step |
| 37428551164 | baseline (subset ifbench) | 2026-10-06 07:15 | 204 | b64a7f7 | cancelled at 6h cap at 113/200 samples (protocol 300->200 per b64a7f7) — CURRENT protocol; HARVESTED: 54/113 = 0.4779 Wilson95 [0.388, 0.5692] recorded as the baseline ifbench row; gpqa/aime/tau2 honest SKIPPED_BY_SUBSET |
| 37815718627 | ACI lever (subset gpqa_diamond,aime,ifbench) | 2026-10-08 17:19 | 204 | 441505f | cancelled at 6h cap 2026-10-08 23:22; HARVESTED gpqa: 111 samples 66 correct = 0.5946 [0.5016, 0.6813], all 111 glm-4-plus, 0 errors; aime + ifbench: ZERO samples in logs (status=started, no sample completed before cap; gpqa completions stopped 19:25Z, dead window 19:25-23:21Z across all three jobs); tau2 honest SKIPPED_BY_SUBSET; verdict NEUTRAL -> REVERT (see ACI row) |
| 37935024936 | verification lever (subset gpqa_diamond,aime,ifbench) | 2026-10-09 13:10 | 204 | 1578951 | cancelled at 6h cap 2026-10-09 19:12; HARVESTED: gpqa 1 sample (1 correct), aime 2 samples (2 correct), ifbench 2 samples (2 correct) — 5 samples total; NO CI possible at n=1-2, verdict NONE (a lever without a CI is not a result). Multi-call scaffold (generator + verifier per sample) under provider starvation (z-ai breaker cycling + free-chain daily rate limits + dual-run load) collapsed throughput to ~5 samples/6h. The gs-free fallback DID work on the scaffold path (samples completed via openai/gs-ai@verification, 0 errors) — mechanism differs from the ACI zero-sample failure: calls succeeded, each just paid heavy throttle backoff. One re-dispatch allowed per directive; scheduled after the router lever lands (quiet pool). |
| 37936151195 | context lever (subset gpqa_diamond,aime,ifbench; ifbench n_samples=100) | 2026-10-09 13:20 | 204 | 2d79e50 | cancelled at 6h cap 2026-10-09 19:22; HARVESTED: gpqa 177 samples 82 correct, aime 12 samples 10 correct, ifbench 269 samples 188 correct, 0 errors across all legs. DEVIATION RECORDED: eval metadata shows max_samples=100 reached inspect, yet 269 ifbench samples executed — the custom task's dataset construction bypasses inspect's max-samples slicer (suspected; the full command line was lost with the killed CLI process, no rawlog); matched-sample discipline unaffected (id intersections). tau2 honest SKIPPED_BY_SUBSET. |

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
| Baseline       | gpqa_diamond | ✅ SCORED_PARTIAL 0.6854 [0.583, 0.7725] (61/89 of 198, run 37764839853 harvest, cancelled at 6h cap; providers: glm-4-plus 51 + gs-free fallback 38) | — | — | — | baseline row locked |
| Baseline       | aime         | ✅ SCORED_PARTIAL 1.0 [0.7575, 1.0] (12/12 of 30, run 37764839853 harvest, cancelled at 6h cap; small n: 12-sample CI is the honest range; providers: gs-free fallback 11 + glm-4-plus 1) | — | — | — | baseline row locked |
| Baseline       | ifbench      | ✅ SCORED_PARTIAL 0.4779 [0.388, 0.5692] (54/113, run 37428551164 harvest, current protocol; n_target 200, cancelled at 6h cap) | — | — | — | baseline row locked; matched-sample basis for levers |
| Baseline       | tau2_telecom | 🚫 BLOCKED — 3 attempts spent: (1) user-sim model rejection [fixed 2b0f8a1], (2) 113/114 z-ai breaker infra errors + 360-min cap infeasibility [re-scoped telecom_small + shim fallback f956ed2], (3) upstream tau2 1.0.1 telecom_small loader TypeError (4.1s, zero samples). Full raw error in tests/baseline-scaffold-v1.json | — | — | — | upstream tau2 fix or a working task-split mechanism |
| ACI            | gpqa_diamond | ✅ SCORED_PARTIAL 0.5946 [0.5016, 0.6813] (66/111, run 37815718627 harvest, cancelled at 6h cap; providers: glm-4-plus 111, no fallback; per-sample latency p50 6388ms p95 7400ms) | matched-sample delta: +0.0308 (paired Wald 95% [-0.0818, +0.1433], 65 common sample ids vs baseline run 37764839853; discordant 8 aci-only-right vs 6 baseline-only-right, McNemar exact p=0.79; both arms all-CI-spanning) | ~0 (same model tier, no fallback) | faster per-sample (p50 6.4s vs baseline 19.8s, confounded: baseline mix included the slower gs-free chain on 38/89) | NEUTRAL -> REVERT per verdict rule (CI includes zero); aime + ifbench legs: ZERO samples at the 6h cap (logs status=started, no completed sample; raw artifact results-aime-gs-ai-aci 6654 bytes, results-ifbench-gs-ai-aci 7037 bytes) — those two rows carry no verdict |
| Verification   | gpqa_diamond | NO-DATA: 1/1 sample at the 6h cap (run 37935024936 harvest; providers openai/gs-ai@verification, 0 errors) | none — n=1 admits no CI | n/a | n/a | NO CI = NOT A RESULT (rule 3); one re-dispatch allowed, queued after router |
| Verification   | aime         | NO-DATA: 2/2 samples at the 6h cap (same run) | none — n=2 admits no CI | n/a | n/a | same as gpqa row |
| Verification   | ifbench      | NO-DATA: 2/2 samples at the 6h cap (same run) | none — n=2 admits no CI | n/a | n/a | same as gpqa row |
| Context        | gpqa_diamond | ✅ SCORED 82/177 = 0.4633 (run 37936151195 harvest; providers openai/gs-ai@context 177, 0 errors) | matched-sample delta +0.0260 (paired Wald 95% [-0.0933, +0.1452], n=77 common ids vs baseline run 37764839853; discordant 12 vs 10, McNemar p=0.83) | ~0 | ~0 | NEUTRAL -> REVERT per verdict rule (CI includes zero) |
| Context        | aime         | ✅ SCORED 10/12 = 0.8333 [0.5520, 0.9530] (same run) | matched-sample delta -0.0909 (paired Wald 95% [-0.2608, +0.0790], n=11 common ids vs baseline run 37764839853; discordant 0 vs 1, McNemar p=1.0) | ~0 | ~0 | NEUTRAL -> REVERT per verdict rule (CI includes zero) |
| Context        | ifbench      | ✅ SCORED 188/269 = 0.6990 (same run) | matched-sample delta +0.3056 (paired Wald 95% [+0.2014, +0.4097], n=108 common ids vs baseline run 37428551164; discordant 38 vs 5, McNemar p<0.0001) | ~0 | ~0 | Mechanical rule says KEEP (CI excludes zero, up) — BUT the delta is NOT ATTRIBUTABLE to the lever: (a) the context profile is a documented no-op on single-turn tasks (eval transcripts carry 2 messages; the episodic summary fires only above 5), so it cannot move ifbench; (b) within-run pass rate decays monotonically across the window (quintiles 0.868 / 0.849 / 0.717 / 0.717 / 0.368), proving ifbench strict-constraint scores track serving conditions; (c) the baseline row was harvested 2026-10-06 in the z-ai-breaker era with NO fallback chain, the context run rode the gs-free fallback era. The +0.3056 measures the serving-era change, not the lever. |
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

## STATUS (2026-10-08) — quota restored via OpenRouter key pool

Historic z-ai 429 lockdown (2026-09-25 blocker) is RESOLVED: the shim now
serves through the OpenRouter keypool. 19 keys delivered, 8 revoked by the
provider (401: pool slots 7-14), 11 alive provisioned into all vault layers.
ITEM 1 closed live: POST /api/v1/conversations/{id}/messages returned HTTP
200 in 6.98s with the exact marker echoed. Runner secret OPENROUTER_API_KEY
rotated (HTTP 204) so the tau2 user-sim is funded.

Throughput reality (root cause from runs 36155160244 + 37428551164): ifbench
runs at 19-39 samples/hour, so the 200-sample protocol fits the cap only at the top of that range; partial-harvest stays the operating model.
Operating model: partial-harvest at the cap (per-sample scores are complete
for every sample emitted; task order deterministic → matched-sample deltas).
No winner is declared on underpowered deltas (power discipline below).

## Execution sequence (current)

1. Baseline ifbench: DONE via harvest (see dispatch log + tests/baseline-scaffold-v1.json).
2. Baseline-completion DONE (runs 37764839853 + 37769190819 + 37814071288): gpqa_diamond + aime rows harvested (SCORED_PARTIAL); tau2_telecom BLOCKED after 3 attempts (see RESULTS).
3. ACI lever: DONE (run 37815718627 harvest). Verdict NEUTRAL -> REVERT on the only leg with data (gpqa matched-sample delta +0.0308, paired Wald 95% [-0.0818, +0.1433], includes zero). aime/ifbench legs produced zero samples at the 6h cap; recorded honestly, no verdict on them.
4. Verification lever: run 37935024936 landed NO-DATA (5 samples total across 3 legs; no CI possible). One re-dispatch allowed per the directive, scheduled AFTER the router lever lands (quiet pool).
5. Context lever: DONE (run 37936151195 harvest). gpqa NEUTRAL (+0.0260, p=0.83), aime NEUTRAL (-0.0909, p=1.0), ifbench mechanically KEEP (+0.3056, p<0.0001) with the delta attributed to the serving-era change (no-op lever on single-turn + within-run decay 0.868->0.368 + baseline harvested in the pre-fallback breaker era), not to the lever.
6. Remaining arm: `scaffold=router`, one dispatch; after it lands: harvest artifacts -> delta+CI vs baseline -> verdict in this table; then the single verification re-dispatch.
7. Harvest, never re-burn: cancelled runs with scored partial logs are data, not failures. Zero-sample logs at the cap are honest no-data rows, not failures.
8. ROOT CAUSE of the run 37815718627 aime/ifbench zero-sample legs (found in code review after the harvest, not a re-run claim): the shim's gs-free OpenRouter fallback existed ONLY on the baseline profile path (src/app/api/v1/openai/chat/completions/route.ts zai branch); the scaffold lever path (runScaffoldCompletion -> callModel in src/lib/bench/scaffold.ts) had NO fallback, so when the z-ai breaker opened mid-run every @aci call hard-failed. Multi-call scaffold samples (aime math chains, ifbench tool loop) died wholesale; single-call gpqa squeezed through until the breaker stuck open at ~19:25Z. Fix: gs-free fallback wired into callModel with loud BENCH-SCAFFOLD log lines and per-call served-id recording (no silent substitution). Verification/context/router dispatches run on the fixed path.
