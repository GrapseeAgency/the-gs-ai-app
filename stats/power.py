"""GS BENCH — Phase 5: statistical power / resolution diagnostics.

A benchmark is only a measurement if it can resolve the effect you claim.
q = N / N* (resolution ratio): if q < 1 the run is UNDERPOWERED and the
reporter must refuse to declare a winner (hard rule 3 in spirit).
"""

from __future__ import annotations

import math
from typing import Dict

from .ci import _normal_quantile, Z_975


def required_n(baseline: float, mde: float, alpha: float = 0.05, power: float = 0.8) -> int:
    """Sample size needed to detect an absolute improvement of `mde` over
    `baseline` (two-sided two-proportion test, equal allocation).

    baseline, mde: proportions in [0,1]; mde = p1 - p0 > 0.
    """
    if not (0.0 < baseline < 1.0):
        raise ValueError("baseline must be a proportion in (0,1)")
    if mde <= 0:
        raise ValueError("mde must be > 0")
    p0, p1 = baseline, min(1.0, baseline + mde)
    pbar = (p0 + p1) / 2
    z_a = Z_975 if abs(alpha - 0.05) < 1e-9 else _normal_quantile(1 - alpha / 2)
    z_b = _normal_quantile(power)
    n = ((z_a * math.sqrt(2 * pbar * (1 - pbar)) + z_b * math.sqrt(p0 * (1 - p0) + p1 * (1 - p1))) ** 2)
    n /= mde * mde
    return int(math.ceil(n))


def resolution(baseline: float, mde: float, n_actual: int, alpha: float = 0.05, power: float = 0.8) -> Dict[str, object]:
    """q = N / N*. q >= 1 → adequately powered for the claimed MDE."""
    n_required = required_n(baseline, mde, alpha, power)
    q = n_actual / n_required
    return {
        "baseline": baseline,
        "mde": mde,
        "n_actual": n_actual,
        "n_required": n_required,
        "q": round(q, 4),
        "underpowered": q < 1.0,
        "verdict": "POWERED" if q >= 1.0 else "UNDERPOWERED — refuse to report a winner",
    }


def mde_for_n(baseline: float, n: int, alpha: float = 0.05, power: float = 0.8) -> float:
    """Minimum detectable effect for a given sample size (inverse of required_n)."""
    if not (0.0 < baseline < 1.0):
        raise ValueError("baseline must be a proportion in (0,1)")
    z_a = Z_975 if abs(alpha - 0.05) < 1e-9 else _normal_quantile(1 - alpha / 2)
    z_b = _normal_quantile(power)
    # Solve required_n(p0, mde) = n for mde via bisection on the closed form.
    lo, hi = 1e-4, 1.0 - baseline - 1e-6
    if required_n(baseline, hi, alpha, power) <= n:
        return hi
    for _ in range(80):
        mid = (lo + hi) / 2
        if required_n(baseline, mid, alpha, power) > n:
            lo = mid
        else:
            hi = mid
    return round(hi, 4)
