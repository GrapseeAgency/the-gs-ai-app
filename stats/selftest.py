#!/usr/bin/env python3
"""GS BENCH — Phase 5 self-test: compute ONE real CI + one power analysis.

Runs with the repo's local python (numpy/scipy present but NOT required).
Real inputs, real arithmetic — no fabricated numbers:
  - GPQA-Diamond sample size 198; Wilson CI computed for a hypothetical
    180/198 pass count is DEMONSTRATION arithmetic on stated inputs.
  - Power: how many GPQA samples to resolve a +5pt improvement over 0.90.
"""
import json
import sys

sys.path.insert(0, ".")

from stats.ci import wilson_ci, bootstrap_ci, clustered_ci, paired_compare
from stats.power import required_n, resolution, mde_for_n
from stats.regression import welch_ttest

out = {}

# 1) Wilson CI on stated binary outcome 180/198 (demonstration arithmetic).
out["wilson_180_of_198"] = wilson_ci(180, 198)

# 2) Bootstrap CI on a stated 30-sample AIME score vector (1/0 pattern).
aime = [1, 1, 0, 1, 1, 1, 0, 1, 0, 1, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 0, 1, 1, 1, 1, 1, 0, 1, 1, 1]
out["bootstrap_aime_24_of_30"] = bootstrap_ci(aime, n_boot=10000)

# 3) Clustered CI: 198 samples across 6 subtopics.
scores = [1] * 180 + [0] * 18
clusters = (["bio"] * 33 + ["phys"] * 33 + ["chem"] * 33 + ["cs"] * 33 + ["math"] * 33 + ["eng"] * 33)
out["clustered_180_of_198_6clusters"] = clustered_ci(scores, clusters)

# 4) Paired permutation compare (local engine since evalci not installed here).
a = [1, 1, 0, 1, 0, 1, 1, 0, 1, 1, 0, 1, 1, 1, 0, 1, 1, 0, 1, 1]
b = [1, 0, 0, 1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 1, 0, 0, 1, 1]
out["paired_compare_demo"] = paired_compare(a, b, n_permutations=5000)

# 5) Power analysis: resolve +5pt over a 0.90 baseline at GPQA scale.
out["required_n_baseline90_mde5"] = required_n(0.90, 0.05)
out["resolution_gpqa198"] = resolution(0.90, 0.05, 198)
out["mde_for_n198"] = mde_for_n(0.90, 198)

# 6) Welch regression gate demo (stated vectors).
out["welch_demo"] = welch_ttest([0.8, 0.9, 0.85, 0.88, 0.92, 0.86], [0.82, 0.91, 0.87, 0.86, 0.93, 0.88])

print(json.dumps(out, indent=2, default=str))
