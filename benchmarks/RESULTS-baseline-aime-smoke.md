# Baseline smoke result — AIME 2024, `gs-ai`

Smoke run validating the end-to-end path only. **Not a scorecard** and not a
lever comparison: one benchmark, one arm, `baseline` scaffold.

## Result

| field | value |
| --- | --- |
| model | `gs-ai` |
| scaffold | `baseline` |
| benchmark | `aime` (inspect_evals/aime2024) |
| n_samples | 30 (30 completed, 0 errored, 0 failed, 0 skipped) |
| score | 0.0333 (exact match) |
| 95% CI | [0.0059, 0.1667] (Wilson, `score_stderr` 0.0328) |
| harness | inspect_ai 0.3.268 |
| git commit | `d3f2a2a04eb8` |
| dataset revision | `unresolved-at-run-start` (as recorded by the runner) |
| duration | 84s wall (`started_at` → `completed_at`) |

Raw artifacts: `~/gs-bench-results/smoke/` (result JSON + inspect log +
raw harness log, kept together per hard rule 5).

## What this run proves

The full path works against real providers: the shim served every request with
zero 503s, the harness completed all 30 samples, and the CLI produced a score
with a CI rather than BLOCKED.

Provider mix during the run: Groq (`qwen/qwen3.8-27b`) and OpenRouter
(`meta-llama/llama-3.3-70b-instruct`) via the GS AI router. Neither a provider
key nor a model secret is recorded in any artifact.

## Honest caveats

- **n=30 is far too small to characterise the system.** The CI spans roughly
  0.6%–16.7%, so the true accuracy could sit almost anywhere in that range.
  This run establishes plumbing, not capability.
- `swe-mini` and `tau2-telecom` do not exist as suites in this repo. The
  catalog defines only `knowledge` (gpqa_diamond, aime, ifbench,
  tau2_telecom) and `blocked`. The requested invocation
  `--suite knowledge,swe-mini,tau2-telecom` therefore cannot run verbatim, and
  `--concurrency` is not a flag this CLI accepts.
- No lever has been measured. ACI, verification, context, and router deltas are
  undetermined until those arms are run against the same suite and samples.
