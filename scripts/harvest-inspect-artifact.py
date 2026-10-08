#!/usr/bin/env python3
"""Harvest an inspect_ai benchmark artifact into a baseline-scaffold row.

Usage: python3 scripts/harvest-inspect-artifact.py <artifact_dir> <benchmark> <scaffold>

Reads the inspect eval log JSON inside <artifact_dir>, computes:
  - n scored samples, passes, score, Wilson 95% CI
  - latency p50/p95 from per-sample total_time (ms)
  - provider distribution from assistant message "model" fields
  - failed/errored/skipped counts
Prints one JSON row to stdout. The caller merges it into
tests/baseline-scaffold-v1.json and the lever report.
"""
import glob
import json
import math
import os
import sys
from datetime import datetime, timezone


def wilson(passes: int, n: int, z: float = 1.96) -> tuple[float, float]:
    if n == 0:
        return (0.0, 0.0)
    p = passes / n
    denom = 1 + z * z / n
    centre = (p + z * z / (2 * n)) / denom
    half = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / denom
    return (max(0.0, centre - half), min(1.0, centre + half))


def percentile(values: list[float], q: float) -> float:
    if not values:
        return -1
    vs = sorted(values)
    idx = min(len(vs) - 1, max(0, math.ceil(q / 100 * len(vs)) - 1))
    return vs[idx]


def find_eval_log(artifact_dir: str) -> str:
    # inspect eval logs are large JSON files with a "samples" key; result.json
    # and latest.json are small CLI wrappers. Prefer inspect-log named files.
    candidates = sorted(
        glob.glob(os.path.join(artifact_dir, "*.json")),
        key=os.path.getsize,
        reverse=True,
    )
    for c in candidates:
        if c.endswith(".result.json") or c.endswith("latest.json"):
            continue
        try:
            with open(c) as fh:
                head = fh.read(4096)
            if '"samples"' in head:
                return c
        except OSError:
            continue
    raise SystemExit(f"no inspect eval log found in {artifact_dir}: {[os.path.basename(c) for c in candidates]}")


def main() -> None:
    artifact_dir, benchmark, scaffold = sys.argv[1], sys.argv[2], sys.argv[3]
    log_path = find_eval_log(artifact_dir)
    with open(log_path) as fh:
        d = json.load(fh)

    samples = d.get("samples") or []
    passes = 0
    scored = 0
    failed = errored = skipped = 0
    times: list[float] = []
    providers: dict[str, int] = {}

    for s in samples:
        status = s.get("status") or "complete"
        scores = s.get("scores") or {}
        value = None
        for _scorer, sc in scores.items():
            v = sc.get("value")
            if isinstance(v, (int, float)):
                value = float(v)
            elif isinstance(v, str):
                try:
                    value = float(v)
                except ValueError:
                    value = None
        if status == "skipped" or s.get("error") == "sample_skipped":
            skipped += 1
        elif s.get("error"):
            errored += 1
        if value is None:
            if status not in ("skipped",) and not s.get("error"):
                errored += 1
            continue
        scored += 1
        if value > 0:
            passes += 1
        tt = s.get("total_time")
        if isinstance(tt, (int, float)):
            times.append(float(tt))
        for m in s.get("messages") or []:
            if m.get("role") == "assistant" and m.get("model"):
                providers[m["model"]] = providers.get(m["model"], 0) + 1
                break

    n = scored
    lo, hi = wilson(passes, n)
    score = passes / n if n else None

    # target n from the CLI result.json when present
    target = None
    res_files = glob.glob(os.path.join(artifact_dir, "*.result.json"))
    result_meta: dict = {}
    if res_files:
        try:
            result_meta = json.load(open(res_files[0]))
            target = result_meta.get("n_samples")
        except Exception:
            pass

    row = {
        "benchmark": benchmark,
        "scaffold": scaffold,
        "status": "SCORED_PARTIAL" if (target and n < target) else ("SCORED" if n else "BLOCKED"),
        "score": round(score, 4) if score is not None else None,
        "score_ci_lo": round(lo, 4),
        "score_ci_hi": round(hi, 4),
        "n_samples": n,
        "n_target": target,
        "passes": passes,
        "failed_count": failed,
        "errored_count": errored,
        "skipped_count": skipped,
        "latency_ms_p50": round(percentile(times, 50), 1) if times else None,
        "latency_ms_p95": round(percentile(times, 95), 1) if times else None,
        "provider_distribution": providers,
        git_commit": (
            ((d.get("eval") or {}).get("revision") or {}).get("commit")
            if isinstance((d.get("eval") or {}).get("revision"), dict)
            else (d.get("eval") or {}).get("revision")
        )
        or result_meta.get("git_commit"),
        "run_id": result_meta.get("run_id"),
        "harness": "inspect_ai",
        "harness_version": ((d.get("eval") or {}).get("packages") or {}).get("inspect-ai"),
        "eval_log": os.path.basename(log_path),
        "harvested_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "error": result_meta.get("status") if result_meta.get("status") not in (None, "done") else None,
    }
    print(json.dumps(row, indent=1))


if __name__ == "__main__":
    main()
