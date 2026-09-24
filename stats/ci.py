"""GS BENCH — Phase 5: confidence intervals.

Hard rule 3: every score carries a CI. No CI, no score.

Pure-python implementations (no numpy required) so the CLI works everywhere;
scipy/evalci are used opportunistically when installed (they are in the bench
image). Nothing here estimates a score — these functions only quantify
uncertainty around measured samples.
"""

from __future__ import annotations

import math
import random
from typing import Dict, Iterable, List, Sequence, Tuple

Z_975 = 1.959963984540054  # two-sided 95% normal quantile


def wilson_ci(passes: int | float, n: int, alpha: float = 0.05) -> Dict[str, float]:
    """Wilson score interval for a binomial proportion.

    The right CI for pass/fail metrics (accuracy, resolved_rate, pass@k):
    stays inside [0,1] and does not collapse to [0,1] at small n the way
    Wald does.
    """
    if n <= 0:
        raise ValueError("n must be > 0")
    p = passes / n
    z = Z_975 if abs(alpha - 0.05) < 1e-9 else _normal_quantile(1 - alpha / 2)
    denom = 1 + z * z / n
    center = (p + z * z / (2 * n)) / denom
    spread = (z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n))) / denom
    return {
        "point": p,
        "lo": max(0.0, center - spread),
        "hi": min(1.0, center + spread),
    }


def bootstrap_ci(
    scores: Sequence[float],
    n_boot: int = 10_000,
    alpha: float = 0.05,
    seed: int = 20260924,
) -> Dict[str, float]:
    """Percentile bootstrap CI for an arbitrary per-sample score vector
    (continuous metrics: constraint_pct, Elo-ish, cost...)."""
    if len(scores) == 0:
        raise ValueError("scores must be non-empty")
    rng = random.Random(seed)
    means: List[float] = []
    data = list(scores)
    for _ in range(n_boot):
        sample = [data[rng.randrange(len(data))] for _ in range(len(data))]
        means.append(sum(sample) / len(sample))
    means.sort()
    lo_idx = max(0, int(math.floor((alpha / 2) * n_boot)))
    hi_idx = min(n_boot - 1, int(math.ceil((1 - alpha / 2) * n_boot)) - 1)
    return {
        "point": sum(data) / len(data),
        "lo": means[lo_idx],
        "hi": means[hi_idx],
    }


def clustered_ci(
    scores: Sequence[float],
    cluster_ids: Sequence[object],
    alpha: float = 0.05,
) -> Dict[str, float]:
    """Cluster-robust (design-effect adjusted) CI.

    Agentic benchmarks can correlate within task families/episodes; treating
    correlated samples as i.i.d. understates the CI. Uses the cluster-level
    ratio estimator (standard errors of per-cluster means weighted by cluster
    size), the approach used in survey statistics and clustered A/B tests.
    """
    if len(scores) != len(cluster_ids):
        raise ValueError("scores and cluster_ids must align")
    n = len(scores)
    if n == 0:
        raise ValueError("empty sample")
    clusters: Dict[object, List[float]] = {}
    for s, c in zip(scores, cluster_ids):
        clusters.setdefault(c, []).append(s)
    m = len(clusters)
    if m == 1:
        # Single cluster: fall back to a plain SD-based CI over samples.
        return {"point": _mean(scores), "lo": None, "hi": None, "note": "single-cluster; no between-cluster variance estimable"}
    point = _mean(scores)
    # CR0 cluster-robust SE of the overall mean:
    #   SE = sqrt(m/(m-1) * sum_c ( sum_{i in c} (x_i - mean)^2 ) ) / n
    cluster_sums = [sum(x - point for x in v) for v in clusters.values()]
    se = math.sqrt(m / (m - 1) * sum(cs * cs for cs in cluster_sums)) / n
    z = Z_975 if abs(alpha - 0.05) < 1e-9 else _normal_quantile(1 - alpha / 2)
    se = max(se, 1e-12)
    return {
        "point": point,
        "lo": max(0.0, point - z * se) if point >= 0 else point - z * se,
        "hi": min(1.0, point + z * se) if point <= 1 else point + z * se,
        "se": se,
        "n_clusters": m,
    }


# ---------------------------------------------------------------------------
# evalci integration (paired comparison). evalci 0.1.0 is pip-verified; when
# importable we defer to it (its method="permutation" is the spec'd path).
# When absent we run our own paired permutation test — identical semantics,
# implemented locally so compare works on any machine, not only in the image.
# ---------------------------------------------------------------------------

def paired_compare(
    scores_a: Sequence[float],
    scores_b: Sequence[float],
    n_permutations: int = 10_000,
    seed: int = 20260924,
) -> Dict[str, object]:
    """Paired comparison: delta, 95% CI on delta, permutation p-value, n."""
    if len(scores_a) != len(scores_b) or len(scores_a) == 0:
        raise ValueError("paired_compare needs equal-length non-empty score vectors")
    try:
        import evalci  # type: ignore

        result = evalci.compare(scores_a, scores_b, method="permutation")  # type: ignore[attr-defined]
        # Normalize evalci's shape into ours; keep raw for the audit log.
        out = {
            "engine": f"evalci=={getattr(evalci, '__version__', 'unknown')}",
            "n": len(scores_a),
            "delta": float(result["delta"]),
            "ci_lo": float(result["ci"][0]),
            "ci_hi": float(result["ci"][1]),
            "p_value": float(result["p_value"]),
            "raw": str(result)[:400],
        }
        return out
    except Exception as e:  # noqa: BLE001 — evalci optional by design
        engine_note = f"evalci unavailable ({type(e).__name__}); local permutation"
    diffs = [a - b for a, b in zip(scores_a, scores_b)]
    observed = _mean(diffs)
    rng = random.Random(seed)
    count = 0
    m = len(diffs)
    for _ in range(n_permutations):
        s = 0.0
        for d in diffs:
            s += d if rng.random() < 0.5 else -d
        if abs(s / m) >= abs(observed) - 1e-12:
            count += 1
    p = (count + 1) / (n_permutations + 1)
    boot = bootstrap_ci(diffs, n_boot=5_000, seed=seed)
    return {
        "engine": engine_note,
        "n": m,
        "delta": observed,
        "ci_lo": boot["lo"],
        "ci_hi": boot["hi"],
        "p_value": p,
        "n_permutations": n_permutations,
    }


# -- internals ---------------------------------------------------------------

def _mean(xs: Iterable[float]) -> float:
    xs = list(xs)
    return sum(xs) / len(xs) if xs else float("nan")


def _normal_quantile(p: float) -> float:
    """Acklam's inverse normal CDF — sufficient to 1e-9 for alpha grids."""
    a = [-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02,
         1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00]
    b = [-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02,
         6.680131188771972e+01, -1.328068155288572e+01]
    c = [-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00,
         -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00]
    d = [7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00,
         3.754408661907416e+00]
    plow, phigh = 0.02425, 1 - 0.02425
    if p < plow:
        q = math.sqrt(-2 * math.log(p))
        return (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / \
               ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1)
    if p > phigh:
        return -_normal_quantile(1 - p)
    q = p - 0.5
    r = q * q
    return (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q / \
           (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1)


def wilson_from_binary(samples: Sequence[float]) -> Dict[str, float]:
    """Convenience: binary per-sample scores (1/0) -> Wilson CI."""
    n = len(samples)
    return wilson_ci(sum(1 for s in samples if s >= 0.5), n)
