# Phase 11 - Measurement on the local model

Benchmark `mmlu_dev`: 100 MMLU dev items sampled round-robin across all 57 subjects,
staged at `native/data/benchmarks/mmlu_dev/dev100.jsonl` (dataset not committed; loader only).
Source `cais/mmlu` `all/dev` parquet, deterministic SHA-256 ordering.

Model `qwen2.5-0.5b-instruct-q4_k_m.gguf` via llama.cpp on Vulkan, `offloaded 25/25 layers
to GPU` (AMD Radeon RX 580 2048SP). No provider was called.

Identical across all five arms: same sample set (`sample_set_sha=6cb6fd9d55a6b221`),
`max_tokens=256`, `temperature=0.0`, `ctx=2048`, same binary, run back to back.
Integrity gates: `n_errored=0` and `n_skipped=0` for every arm.

## Main table

| Lever | Score | CI95 | n | parse_rate | n_parsed | n_correct | errored | skipped | p50 | p95 |
|---|---|---|---|---|---|---|---|---|---|---|
| baseline | 0.2800 | [0.2014, 0.3749] | 100 | 0.94 | 94 | 28 | 0 | 0 | 210 ms | 1867 ms |
| aci | 0.1600 | [0.1010, 0.2442] | 100 | 0.54 | 54 | 16 | 0 | 0 | 1776 ms | 1884 ms |
| verification | 0.2800 | [0.2014, 0.3749] | 100 | 0.99 | 99 | 28 | 0 | 0 | 1794 ms | 2059 ms |
| context | 0.1000 | [0.0552, 0.1744] | 100 | 0.27 | 27 | 10 | 0 | 0 | 1784 ms | 1877 ms |
| router | 0.2800 | [0.2014, 0.3749] | 100 | 0.94 | 94 | 28 | 0 | 0 | 212 ms | 1786 ms |

`provider_distribution = {"local": 100}` and
`model_distribution = {"local:qwen2.5-0.5b-instruct-q4_k_m.gguf [llama.cpp+vulkan]": 100}`
for every arm.

## Score delta vs baseline

| Lever | Delta | CIs overlap | Verdict |
|---|---|---|---|
| aci | -0.1200 | yes | no significant change |
| verification | +0.0000 | yes | no significant change |
| context | -0.1800 | no | significant |
| router | +0.0000 | yes | no significant change |

## Why the spread is not a quality effect

Every interval overlaps baseline's, so no lever separates. The mechanism is visible in
the two columns that move together, `parse_rate` and p50 latency:

| Lever | parse_rate | p50 | accuracy on PARSED only | chi2 vs baseline | p |
|---|---|---|---|---|---|
| baseline | 0.94 | 210 ms | 0.2979 | - | - |
| aci | 0.54 | 1776 ms | 0.2963 | 0.000 | 0.984 |
| verification | 0.99 | 1794 ms | 0.2828 | 0.053 | 0.818 |
| context | 0.27 | 1784 ms | 0.3704 | 0.512 | 0.474 |
| router | 0.94 | 212 ms | 0.2979 | 0.000 | 1.000 |

The reasoning scaffolds ask the model to reason before answering, so it writes roughly 8x
more tokens (p50 210 ms -> ~1780 ms). At the fixed 256-token budget it frequently never
reaches its own `ANSWER: <letter>` line. The answer is never stated, the parser correctly
refuses, and the sample scores wrong. `context` lost 73 of 100 samples this way, `aci` 46.

Conditioned only on samples where an answer actually exists, all five arms are
indistinguishable: 0.2828 to 0.3704, every p > 0.47.

`router` is the harness's own control: by construction the same prompt as baseline, routed
through the pool identically. It reproduced baseline exactly (28/100, chi2=0.000, p=1.000).
The pipeline is deterministic and the control behaves as designed, so the remaining deltas
are properties of the scaffolds, not of the measurement.

## Caveat on absolute level

Baseline 0.28 against a 0.25 random-choice floor. The Wilson interval [0.2014, 0.3749]
contains 0.25. This model is not measurably better than guessing here, so the benchmark is
uninformative at this model size for detecting reasoning-quality differences and no lever
delta should be read from the Score column.

## What would make it informative

- A larger model: the RX 580 has 8 GB, enough for a 1.5B-3B quantised model. 3B scores
  roughly 0.45-0.50 on MMLU, well clear of the floor.
- A token budget the reasoning scaffolds can finish inside. `max_tokens=768` applied to all
  five arms would let `aci` and `context` state an answer while keeping arms comparable.

