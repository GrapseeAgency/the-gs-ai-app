#!/usr/bin/env python3
"""Assemble the Phase A baseline scaffold scorecard from gs-bench run artifacts.

Inputs: a directory containing *.result.json files downloaded from the
GitHub Actions artifacts of ONE workflow run (one scaffold arm), plus the
sandbox shim dev.log slice for latency percentiles (optional).

Outputs: tests/<output>.json with, per benchmark:
  score + Wilson CI, n_samples, cost_usd, latency p50/p95 (from shim log when
  provided), failed/errored/skipped counts, provenance (git commit, dataset
  revision, harness version, docker digest), and the power analysis.

Honesty rules enforced here:
  - BLOCKED entries are copied verbatim with their raw_reason.
  - No number is invented: latency percentiles require the shim log slice,
    else the field says "not-collected".
  - UNDERPOWERED benchmarks are flagged (stats/power) and the file refuses
    winner declarations on them.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO))

from stats.ci import wilson_ci, bootstrap_ci  # noqa: E402
from stats.power import resolution  # noqa: E402


def load_results(run_dir: Path) -> list[dict]:
    results = []
    for f in sorted(run_dir.rglob("*.result.json")):
        try:
            results.append(json.loads(f.read_text()))
        except Exception as e:  # noqa: BLE001
            print(f"WARN: unreadable {f}: {e}", file=sys.stderr)
    return results


def latency_percentiles(shim_log: Path, since: str, model: str) -> dict:
    """p50/p95 latency (ms) of BENCH-SHIM calls for one model since a timestamp.
    Source: sandbox dev.log BENCH-SHIM lines (server-side evidence)."""
    if not shim_log or not shim_log.exists():
        return {"source": "not-collected", "p50_ms": None, "p95_ms": None}
    latencies: list[int] = []
    pat = re.compile(
        r"BENCH-SHIM kind=chat_completions requested=(\S+) profile=(\S+) served=(\S+) latencyMs=(\d+)"
    )
    for line in shim_log.read_text(errors="ignore").splitlines():
        m = pat.search(line)
        if not m:
            continue
        requested, profile, served, ms = m.groups()
        if profile != "baseline" or "glm" not in served:
            continue
        latencies.append(int(ms))
    if not latencies:
        return {"source": "not-collected", "p50_ms": None, "p95_ms": None}
    latencies.sort()

    def pct(p: float) -> int:
        i = min(len(latencies) - 1, int(round(p / 100.0 * (len(latencies) - 1))))
        return latencies[i]

    return {
        "source": f"shim dev.log ({len(latencies)} calls, profile=baseline)",
        "p50_ms": pct(50),
        "p95_ms": pct(95),
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--run-dir", required=True, help="dir with downloaded *.result.json")
    ap.add_argument("--output", required=True, help="output json path")
    ap.add_argument("--shim-log", default=str(REPO / "dev.log"))
    ap.add_argument("--arm", default="baseline-scaffold-v1")
    ap.add_argument("--notes", default="")
    args = ap.parse_args()

    results = load_results(Path(args.run_dir))
    if not results:
        print("no result files found", file=sys.stderr)
        return 2

    scored = [r for r in results if r.get("status") == "done" and r.get("score") is not None]
    blocked = [r for r in results if r.get("status") == "BLOCKED"]
    other = [r for r in results if r not in scored and r not in blocked]

    lat = latency_percentiles(Path(args.shim_log), "", "gs-ai")

    benchmarks = []
    for r in results:
        entry = {
            "benchmark": r.get("benchmark"),
            "model": r.get("model"),
            "scaffold": r.get("scaffold", "baseline"),
            "status": r.get("status"),
            "n_samples": r.get("n_samples"),
            "metric": r.get("metric"),
            "git_commit": r.get("git_commit"),
            "dataset_revision": r.get("dataset_revision"),
            "harness": r.get("harness"),
            "harness_version": r.get("harness_version"),
            "docker_image_digest": r.get("docker_image_digest"),
            "raw_log_url": r.get("raw_log_url"),
            "run_id": r.get("run_id"),
        }
        if r.get("status") == "BLOCKED":
            entry["raw_reason"] = r.get("raw_reason")
        else:
            entry.update(
                {
                    "score": r.get("score"),
                    "score_ci_lo": r.get("score_ci_lo"),
                    "score_ci_hi": r.get("score_ci_hi"),
                    "failed_count": r.get("failed_count"),
                    "errored_count": r.get("errored_count"),
                    "skipped_count": r.get("skipped_count"),
                    "cost_usd": (r.get("cost") or {}).get("spent_usd") if isinstance(r.get("cost"), dict) else r.get("cost"),
                    "read_rate": None,       # inspect adapter does not emit these
                    "citation_rate": None,   # recorded honestly as not-collected
                }
            )
        benchmarks.append(entry)

    # Power analysis per scored benchmark (rule: refuse winners on underpowered n)
    power = []
    for r in scored:
        base = float(r["score"])
        n = int(r.get("n_samples") or 0)
        mde = 0.05
        try:
            res = resolution(base, mde, n)
        except ValueError:
            res = {"verdict": "not-computable (score at boundary)"}
        power.append(
            {
                "benchmark": r.get("benchmark"),
                "baseline_measured": base,
                "n": n,
                "mde_targeted": mde,
                **res,
            }
        )

    out = {
        "artifact": args.arm,
        "generated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "rule_reminder": [
            "same model throughout (served id recorded per run)",
            "every score carries a CI (hard rule 3)",
            "underpowered benchmarks cannot crown winners (stats/power)",
            "BLOCKED entries carry the raw reason (hard rule 5)",
        ],
        "latency_scaffold_calls": lat,
        "benchmarks": benchmarks,
        "power_analysis": power,
        "blocked_count": len(blocked),
        "scored_count": len(scored),
        "notes": args.notes,
    }
    Path(args.output).write_text(json.dumps(out, indent=2))
    print(f"wrote {args.output} (scored={len(scored)} blocked={len(blocked)})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
