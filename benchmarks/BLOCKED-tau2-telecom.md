# BLOCKED — `tau2_telecom` (telecom, n=50 declared)

**Status: BLOCKED. Excluded from the delta table. No score reported.**

## Raw reason

Two separate defects were found and fixed; a third is a capacity limit that
cannot be fixed in code.

### 1. FIXED — key pool read the wrong env convention (commit `962ce6c`)

```
GS Free is offline: the OpenRouter key pool is empty (env, key file and db
vault are all empty after the platform wipes). Re-provision keys to restore
free-tier models.
```

`src/lib/keypool.ts` read only `OPENROUTER_API_KEYS`. This deployment provisions
`OPENROUTER_API_KEY`, `_SECONDARY`, `_FOURTH`, `_1` … `_64` (64 numbered keys,
`OPENROUTER_API_KEYS` absent). The pool came up empty and every vendor
passthrough returned 502 despite valid keys being present.

### 2. FIXED — the user simulator 404'd on its own model (commit `962ce6c`)

```
litellm.NotFoundError: NotFoundError: OpenAIException - Unknown model
'gpt-4o-mini'. Serving: gs-ai, gs-ai-flash, gs-ai-thinking, any vendor/model id
for OpenRouter passthrough, and gs-ai@<...> scaffold variants.
```

litellm strips its own `openai/` prefix and posts a bare `gpt-4o-mini`, which
the shim rejected. Observed as 83/83 simulations terminating
`infrastructure_error` with zero messages and null rewards.

### 3. NOT FIXABLE HERE — the task set is far larger than the declared n

The suite declares `n_samples: 50`, but `_run_tau2` passes
`--task-set-name telecom` with no limit, and that task set contains **114**
tasks (measured: `/tmp/tau2-bench-data/data/tau2/domains/telecom/tasks.json`).
Each task is a multi-turn agent/user conversation.

With the two defects fixed, a smoke run made **106 LLM calls in ~25 minutes
without completing the task set** (per-task cost is many calls, ~10–14 s each
at `--max-concurrency 1`). Extrapolated, one arm exceeds the CLI's own budget:

```
_suite_timeout = max(timeout_per_sample_s * n_samples, 1800) = 600 * 50 = 30000s ≈ 8.3h
```

That is per arm, and the comparison needs 5 arms.

## Secondary defect found (not on the critical path)

`telecom_small` is unusable in the installed tau2:

```
TypeError: get_tasks_small() got an unexpected keyword argument 'task_split_name'
```

An upstream loader/signature mismatch, internal to tau2. Not triggered by the
benchmark, which uses `telecom`.

## What would unblock it

Any one of:

- a task-set cap wired through `_run_tau2` (e.g. honour the declared
  `n_samples: 50` and pass only 50 tasks), plus a concurrency above 1;
- a funded, higher-rate provider tier — the current free tiers meter output
  tokens per minute and are the direct cause of the per-call cost;
- `telecom_small` fixed upstream, giving a 20-task set that fits the window.

Until then `tau2_telecom` is reported BLOCKED. It is **not** imputed, and no
substitute benchmark stands in for it.
