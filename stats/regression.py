"""GS BENCH — Phase 5: regression gate.

Before a new model/config rollout is declared non-regressive:
  welch_ttest(baseline_scores, new_scores) -> {t, p, delta}
BLOCK the rollout if p < 0.05 AND delta < -0.05 (statistically significant
AND practically more than 5 points worse).
"""

from __future__ import annotations

import math
from typing import Dict

from .ci import _normal_quantile

BLOCK_THRESHOLD = {"alpha": 0.05, "max_delta": -0.05}


def _t_sf(t: float, df: float) -> float:
    """Survival function of Student t. scipy when available; else a
    normal approximation (documented degradation, scipy is always present in
    the bench image)."""
    try:
        from scipy import stats as _s  # type: ignore

        return float(_s.t.sf(t, df))
    except Exception:  # noqa: BLE001
        # df>30 → t≈normal; conservative fallback.
        return float(_normal_quantile_max(t))


def _normal_quantile_max(t: float) -> float:
    # P(T >= t) approximated by P(Z >= t) for large df (explicit fallback).
    a = 0.2316419
    b1, b2, b3, b4, b5 = 0.319381530, -0.356563782, 1.781477937, -1.821255978, 1.330274429
    sign = 1.0 if t >= 0 else -1.0
    x = abs(t) / math.sqrt(2)
    # Abramowitz-Stegun 7.1.26 based two-sided tail / 2
    L = 1.0 / (1.0 + a * x)
    poly = L * (b1 + L * (b2 + L * (b3 + L * (b4 + L * b5))))
    cdf_half = 1.0 - poly * math.exp(-x * x) / math.sqrt(2 * math.pi)
    tail = 1.0 - cdf_half  # P(Z > x) where x = |t|/sqrt(2)? — use standard erfc instead
    # Prefer math.erfc: exact and dependency-free.
    return 0.5 * math.erfc(t / math.sqrt(2)) if sign >= 0 else 1.0 - 0.5 * math.erfc(t / math.sqrt(2))


def welch_ttest(baseline_scores, new_scores) -> Dict[str, object]:
    n1, n2 = len(baseline_scores), len(new_scores)
    if n1 < 2 or n2 < 2:
        raise ValueError("welch_ttest needs >=2 samples per arm")
    m1 = sum(baseline_scores) / n1
    m2 = sum(new_scores) / n2
    v1 = sum((x - m1) ** 2 for x in baseline_scores) / (n1 - 1)
    v2 = sum((x - m2) ** 2 for x in new_scores) / (n2 - 1)
    se = math.sqrt(v1 / n1 + v2 / n2)
    t = (m2 - m1) / se if se > 0 else 0.0
    df = (v1 / n1 + v2 / n2) ** 2 / ((v1 / n1) ** 2 / (n1 - 1) + (v2 / n2) ** 2 / (n2 - 1))
    # two-sided p
    p = 2.0 * _t_sf(abs(t), df)
    delta = m2 - m1
    block = (p < BLOCK_THRESHOLD["alpha"]) and (delta < BLOCK_THRESHOLD["max_delta"])
    return {
        "t": round(t, 4),
        "df": round(df, 2),
        "p": round(p, 6),
        "delta": round(delta, 4),
        "baseline_mean": round(m1, 4),
        "new_mean": round(m2, 4),
        "block_rollout": bool(block),
        "gate": BLOCK_THRESHOLD,
    }
