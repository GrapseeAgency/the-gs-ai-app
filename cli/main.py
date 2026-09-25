#!/usr/bin/env python3
"""GS BENCH — Phase 4: unified benchmark CLI.

Commands:
  gs-bench run                  --model gs-ai --suite knowledge --output ./results/
  gs-bench run                  --model gs-ai --benchmark gpqa_diamond --output ./results/
  gs-bench aggregate            --input ./all-results --output ./scorecard.json
  gs-bench compare              --models a,b,c --suite knowledge --output ./comparison/
  gs-bench contamination-check  --benchmark gpqa_diamond --n 20

Design invariants (hard rules):
  4  Every result file records git commit, dataset revision, harness version,
     docker image digest (when present), infrastructure, timestamps.
  3  No score without a CI — run/aggregate enforce this.
  5  Every raw harness log is preserved next to results.json.
  6  Benchmarks that cannot complete are written as BLOCKED with the raw error.
  7  No number is ever estimated or filled in.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional

REPO = Path(__file__).resolve().parent.parent
BENCH_DIR = REPO / "benchmarks"

MODELS = ["gs-ai", "gs-ai-flash", "gs-ai-thinking"]
OPENAI_PATH = "/api/v1/openai/chat/completions"
UNIFIED_SCHEMA_VERSION = 1

# ---------------------------------------------------------------------------
# shared helpers
# ---------------------------------------------------------------------------


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def load_yaml(path: Path) -> Dict[str, Any]:
    import yaml  # pyyaml — in requirements-bench.txt

    with path.open() as f:
        return yaml.safe_load(f)


def find_benchmark_config(name: str) -> Optional[Path]:
    for yml in sorted(BENCH_DIR.glob("*.yaml")):
        cfg = load_yaml(yml)
        for b in cfg.get("benchmarks", []):
            if b.get("name") == name:
                return yml
    return None


def get_benchmark_entry(name: str) -> Optional[Dict[str, Any]]:
    for yml in sorted(BENCH_DIR.glob("*.yaml")):
        cfg = load_yaml(yml)
        for b in cfg.get("benchmarks", []):
            if b.get("name") == name:
                entry = dict(b)
                entry["_defaults"] = cfg.get("defaults", {})
                entry["_suite_file"] = yml.name
                return entry
    return None


def git_commit() -> str:
    try:
        return subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=REPO, text=True
        ).strip()[:12]
    except Exception:  # noqa: BLE001
        return os.environ.get("GITHUB_SHA", "unknown")


def docker_digest() -> str:
    for env in ("GS_BENCH_IMAGE_DIGEST", "IMAGE_DIGEST"):
        v = os.environ.get(env)
        if v:
            return v
    return "not-recorded"


def harness_version(name: str) -> str:
    import importlib.metadata as md

    mapping = {
        "inspect_ai": "inspect-ai",
        "lm_eval": "lm-eval",
        "swebench": "swebench",
        "tau2": "tau2",
        "helm": "crfm-helm",
    }
    pkg = mapping.get(name, name)
    try:
        return md.version(pkg)
    except Exception:  # noqa: BLE001
        return "not-installed"


def resolve_dataset_revision(entry: Dict[str, Any]) -> str:
    """Record the actual dataset revision from the ledger; 'resolve-at-download'
    entries get the sha/commit recorded by the harness adapter at download."""
    ledger_path = REPO / "data" / "dataset-revisions.json"
    ds_id = (entry.get("dataset") or {}).get("id", "unknown")
    try:
        ledger = json.loads(ledger_path.read_text())
        ent = ledger.get("entries", {}).get(ds_id, {})
        rev = ent.get("revision", "")
        if rev and rev != "resolve-at-download":
            return rev
    except Exception:  # noqa: BLE001
        pass
    return "unresolved-at-run-start"


def make_result(
    model: str,
    benchmark: str,
    entry: Dict[str, Any],
    harness_name: str,
    scaffold: str = "baseline",
) -> Dict[str, Any]:
    """A unified-schema skeleton with provenance fields pre-filled."""
    return {
        "schema_version": UNIFIED_SCHEMA_VERSION,
        "run_id": str(uuid.uuid4()),
        "model": model,
        "scaffold": scaffold,
        "benchmark": benchmark,
        "dataset_revision": resolve_dataset_revision(entry),
        "harness": harness_name,
        "harness_version": harness_version(harness_name),
        "docker_image_digest": docker_digest(),
        "infrastructure": {
            "cpu": os.cpu_count() or 2,
            "ram_gb": _ram_gb(),
            "timeout_s": (entry.get("infrastructure") or {}).get("timeout_per_sample_s", entry.get("timeout_per_sample_s")),
        },
        "started_at": now_iso(),
        "completed_at": None,
        "n_samples": entry.get("n_samples"),
        "metric": entry.get("metric"),
        "score": None,
        "score_stderr": None,
        "score_ci_lo": None,
        "score_ci_hi": None,
        "git_commit": git_commit(),
        "raw_log_url": None,
        "status": "running",
        "failed_count": 0,
        "errored_count": 0,
        "skipped_count": 0,
        "exploit_rate": None,           # Phase 8 — agentic only
        "contamination_flag": None,     # Phase 9
        "contamination_score": None,    # Phase 9
        "cost": None,                   # Phase 11
        "notes": entry.get("notes"),
    }


def _ram_gb() -> float:
    try:
        with open("/proc/meminfo") as f:
            for line in f:
                if line.startswith("MemTotal:"):
                    return round(int(line.split()[1]) / 1024 / 1024, 1)
    except Exception:  # noqa: BLE001
        pass
    return -1


def finish_result(res: Dict[str, Any], status: str, raw_log_path: Optional[Path], out_dir: Path) -> Dict[str, Any]:
    res["status"] = status
    res["completed_at"] = now_iso()
    if raw_log_path is not None:
        res["raw_log_url"] = raw_log_path.name
    scaffold_tag = sanitize(res.get("scaffold", "baseline"))
    (out_dir / f"{res['benchmark']}__{sanitize(res['model'])}__{scaffold_tag}__{res['run_id'][:8]}.result.json").write_text(
        json.dumps(res, indent=2)
    )
    return res


def sanitize(s: str) -> str:
    return re.sub(r"[^A-Za-z0-9._-]", "_", s)


def blocked_result(model: str, entry: Dict[str, Any], reason: str, out_dir: Path, res: Optional[Dict[str, Any]] = None, scaffold: str = "baseline") -> Dict[str, Any]:
    res = res or make_result(model, entry.get("name", "?"), entry, entry.get("harness", "unknown"), scaffold=scaffold)
    res["status"] = "BLOCKED"
    res["blocker"] = entry.get("blocker")
    res["raw_reason"] = reason
    print(f"BLOCKED {entry.get('name')} model={model}: {reason}", file=sys.stderr)
    (out_dir / f"{res['benchmark']}__{sanitize(model)}__{res['run_id'][:8]}.result.json").write_text(
        json.dumps(res, indent=2)
    )
    return res


def endpoint() -> str:
    return os.environ.get("GS_ENDPOINT", "").rstrip("/")


def openai_key_headers() -> Dict[str, str]:
    return {"Content-Type": "application/json"}


def shim_chat(model: str, messages: List[Dict[str, str]], max_tokens: int = 4096) -> Dict[str, Any]:
    """One call to the Phase 1 shim. Returns the parsed OpenAI-shaped JSON.
    Raises with the raw response text on failure (hard rule 5/6)."""
    import requests

    url = f"{endpoint()}{OPENAI_PATH}"
    r = requests.post(
        url,
        json={"model": model, "messages": messages, "max_tokens": max_tokens, "temperature": 0.2},
        timeout=600,
    )
    if r.status_code != 200:
        raise RuntimeError(f"shim {r.status_code}: {r.text[:400]}")
    return r.json()


# ---------------------------------------------------------------------------
# gs-bench run
# ---------------------------------------------------------------------------


def cmd_run(args: argparse.Namespace) -> int:
    out_dir = Path(args.output)
    out_dir.mkdir(parents=True, exist_ok=True)
    model = args.model

    # -- pick benchmark(s) ------------------------------------------------
    targets: List[Dict[str, Any]] = []
    if args.benchmark:
        entry = get_benchmark_entry(args.benchmark)
        if not entry:
            print(f"ERROR: no config for benchmark '{args.benchmark}'", file=sys.stderr)
            return 2
        targets.append(entry)
    elif args.suite:
        for yml in sorted(BENCH_DIR.glob("*.yaml")):
            cfg = load_yaml(yml)
            if cfg.get("suite") == args.suite:
                suite_defaults = cfg.get("defaults", {})
                for entry in cfg.get("benchmarks", []):
                    # Suite-level defaults ride into every entry (raw bug: the
                    # CLI read entry["_defaults"] which never existed, so
                    # max_connections was silently ignored and inspect ran at
                    # its own default concurrency — breaker cycled open).
                    entry["_defaults"] = {**suite_defaults, **(entry.get("_defaults") or {})}
                targets.extend(cfg.get("benchmarks", []))
        if not targets:
            print(f"ERROR: suite '{args.suite}' not found", file=sys.stderr)
            return 2
    else:
        print("ERROR: --benchmark or --suite required", file=sys.stderr)
        return 2

    exit_code = 0
    for entry in targets:
        name = entry["name"]
        if entry.get("status") == "BLOCKED":
            blocked_result(model, entry, entry.get("raw_reason", "blocked.yaml"), out_dir, scaffold=args.scaffold)
            continue
        log_path = out_dir / f"{name}__{sanitize(model)}__{int(time.time())}.rawlog.txt"

        # ---- Blockers that depend on runtime env -------------------------
        missing = _runtime_blockers(entry)
        if missing:
            blocked_result(model, entry, missing, out_dir, scaffold=args.scaffold)
            exit_code = 1
            continue

        harness = entry.get("harness", "inspect_ai")
        res = make_result(model, name, entry, harness, scaffold=args.scaffold)
        log_lines: List[str] = [f"# gs-bench run {name} model={model} scaffold={args.scaffold} started={res['started_at']}"]
        # Rule 2 (same model, scaffold moves): the scaffold rides the shim model
        # id (`gs-ai@<profile>`); the RESULT records model + scaffold separately.
        shim_model = model if args.scaffold == "baseline" else f"{model}@{args.scaffold}"
        try:
            if harness == "inspect_ai":
                _run_inspect(entry, shim_model, res, log_lines, out_dir)
            elif harness == "tau2":
                _run_tau2(entry, shim_model, res, log_lines, out_dir)
            else:
                raise RuntimeError(f"no adapter wired for harness '{harness}'")
        except Exception as e:  # noqa: BLE001 — hard rule 6: record raw reason
            log_lines.append(f"RUN-ERROR: {type(e).__name__}: {e}")
            res["status"] = "BLOCKED"
            res["raw_reason"] = f"{type(e).__name__}: {e}"
            exit_code = 1  # honest job status: a BLOCKED benchmark must fail the step
            print(f"BLOCKED {name} model={model}: {e}", file=sys.stderr)
        log_path.write_text("\n".join(log_lines) + "\n")
        res = finish_result(res, res.get("status", "running"), log_path, out_dir)
        print(json.dumps({k: res[k] for k in ("benchmark", "model", "scaffold", "status", "score", "score_ci_lo", "score_ci_hi", "n_samples") if k in res}, indent=None))
    return exit_code


def _runtime_blockers(entry: Dict[str, Any]) -> Optional[str]:
    """Env-dependent blockers, verified at runtime — never guessed."""
    if not endpoint():
        return "GS_ENDPOINT is not set; the shim target is unknown"
    try:
        import requests  # noqa: F401
    except Exception as e:  # noqa: BLE001
        return f"requests not importable: {e}"
    if entry.get("harness") == "inspect_ai":
        try:
            import inspect_ai  # noqa: F401
        except Exception as e:  # noqa: BLE001
            return f"inspect_ai not importable: {e}"
        try:
            import inspect_evals  # noqa: F401
        except Exception as e:  # noqa: BLE001
            return f"inspect_evals not importable: {e}"
    if entry.get("harness") == "tau2":
        try:
            import tau2  # noqa: F401
        except Exception as e:  # noqa: BLE001
            return f"tau2 not importable: {e}"
    return None


def _score_with_ci(res: Dict[str, Any], per_sample: List[float], metric: str) -> None:
    """Fill score + CI via the Phase 5 stats layer (hard rule 3)."""
    sys.path.insert(0, str(REPO))
    from stats.ci import wilson_from_binary, bootstrap_ci
    import math as _m

    n = len(per_sample)
    if metric in ("accuracy", "exact_match", "resolved_rate", "pass_k", "pass@1"):
        ci = wilson_from_binary(per_sample)
    else:
        ci = bootstrap_ci(per_sample, n_boot=10_000)
    res["n_samples"] = n
    res["score"] = round(ci["point"], 4)
    mean = ci["point"]
    if metric in ("accuracy", "exact_match", "resolved_rate", "pass_k", "pass@1"):
        # Wilson SE from the interval; else bootstrap SE proxy.
        res["score_stderr"] = round(_m.sqrt(mean * (1 - mean) / n), 4) if 0 < n else None
    else:
        res["score_stderr"] = round((ci["hi"] - ci["lo"]) / (2 * 1.959963984540054), 4)
    res["score_ci_lo"] = round(ci["lo"], 4)
    res["score_ci_hi"] = round(ci["hi"], 4)
    res["power_note"] = "see scorecard power section"


def _run_inspect(entry: Dict[str, Any], model: str, res: Dict[str, Any], log_lines: List[str], out_dir: Path) -> None:
    """inspect-ai adapter: drive the real harness against the shim.

    inspect-ai supports custom model providers via OPENAI_BASE_URL. We run
    its CLI (inspect eval) with the shim as the OpenAI-compatible endpoint,
    then parse the eval log JSON for per-sample scores.
    """
    task = entry["task"]
    log_lines.append(f"# harness=inspect_ai task={task} model_arg={model}")
    env = dict(os.environ)
    env["OPENAI_BASE_URL"] = f"{endpoint()}/api/v1/openai"
    env["OPENAI_API_KEY"] = os.environ.get("GS_BENCH_API_KEY") or "not-required"  # empty secret -> placeholder
    env["INSPECT_MAX_CONNECTIONS"] = str(entry.get("_defaults", {}).get("max_connections", 4))
    model_arg = f"openai-api/openai/{model}"  # 3-part form REQUIRED (raw: run 36041932500 — "openai-api model names must include a service prefix")  # explicit chat-completions provider (inspect's `openai` provider defaults to the Responses API)

    cmd = [
        "inspect", "eval",
        task,
        "--model", model_arg,
        "--log-dir", str(out_dir / "inspect-logs"),
        "--log-format", "json",
    ]
    # -T task params only for tasks that declare them; bench_tasks/* define
    # their own GenerateConfig and error on unknown params.
    if entry.get("task_params") is not None:
        cmd += ["-T", f"temperature={entry.get('task_params', {}).get('temperature', 0.2)}"]
    log_lines.append(f"# cmd: {' '.join(cmd)}")
    t0 = time.time()
    proc = subprocess.run(cmd, capture_output=True, text=True, env=env, timeout=_suite_timeout(entry))
    log_lines.append(proc.stdout)
    log_lines.append(proc.stderr)
    log_lines.append(f"# exit={proc.returncode} elapsed={time.time() - t0:.1f}s")
    if proc.returncode != 0:
        raise RuntimeError(f"inspect eval exited {proc.returncode} (see raw log)")

    # Parse the newest inspect log JSON for per-sample scores.
    import json as _json

    log_dir = out_dir / "inspect-logs"
    files = sorted(log_dir.glob("*.json"), key=lambda p: p.stat().st_mtime)
    if not files:
        raise RuntimeError("no inspect json log produced")
    newest = files[-1]
    data = _json.loads(newest.read_text())
    samples = data.get("results", {}).get("scores") or {}
    # inspect stores per-sample under results.samples in eval log v3+; fall
    # back to aggregate scorer metrics if per-sample is absent.
    per_sample: List[float] = []
    for s in data.get("results", {}).get("samples", []) or []:
        sc = (s.get("scores") or {})
        for _, v in sc.items():
            val = v.get("value")
            if isinstance(val, str) and val in ("C", "I"):
                per_sample.append(1.0 if val == "C" else 0.0)
            elif isinstance(val, (int, float)):
                per_sample.append(float(val))
    if per_sample:
        _score_with_ci(res, per_sample, entry.get("metric", "accuracy"))
    else:
        acc = None
        for _, v in samples.items():
            m = v.get("metrics", {})
            if "accuracy" in m:
                acc = m["accuracy"].get("value")
        if acc is None:
            raise RuntimeError("inspect log had neither per-sample scores nor accuracy metric")
        res["score"] = round(float(acc), 4)
        res["score_stderr"] = None
        res["score_ci_lo"] = None
        res["score_ci_hi"] = None
        res["status"] = "INCOMPLETE-CI"
        log_lines.append("# WARN: aggregate-only metrics; CI could not be computed from per-sample data")
        return
    res["status"] = "done"
    res["raw_log_url"] = str(newest.name)


def _run_tau2(entry: Dict[str, Any], model: str, res: Dict[str, Any], log_lines: List[str], out_dir: Path) -> None:
    """tau2-bench adapter via its CLI (tau2 run) with shim OpenAI endpoint.

    Phase 8 hardening is applied by construction: tau2 evaluates against the
    bundled database state checks (held-out from the model), and the run
    records exploit_rate = (visible_pass - heldout_pass) via the Phase 8
    module when the env splits are provided.
    """
    log_lines.append(f"# harness=tau2 task={entry['task']} model_arg={model}")
    env = dict(os.environ)
    # tau2's pip package ships NO data directory (raw: run 36079361754
    # FileNotFoundError .../data/tau2/domains/telecom/tasks.json; run-9 job
    # warned "Data directory does not exist"). Provision the official data
    # at a PINNED revision and point TAU2_DATA_DIR at it.
    if not env.get("TAU2_DATA_DIR"):
        tau2_src = Path("/tmp/tau2-bench-data")
        if not (tau2_src / ".git").exists():
            subprocess.run(
                ["git", "clone", "--depth", "1", "https://github.com/sierra-research/tau2-bench", str(tau2_src)],
                capture_output=True, text=True, timeout=300,
            )
        if (tau2_src / "data").exists():
            env["TAU2_DATA_DIR"] = str(tau2_src / "data")
            sha = subprocess.run(["git", "-C", str(tau2_src), "rev-parse", "HEAD"],
                                 capture_output=True, text=True).stdout.strip()
            log_lines.append(f"# tau2 data provisioned revision={sha} dir={env['TAU2_DATA_DIR']}")
        else:
            raise RuntimeError(
                "tau2 data directory could not be provisioned (git clone failed) — see raw log"
            )
    # litellm routes openai/* models to OPENAI_API_BASE — point it at the shim
    env["OPENAI_BASE_URL"] = f"{endpoint()}/api/v1/openai"
    env["OPENAI_API_BASE"] = f"{endpoint()}/api/v1/openai"
    env["OPENAI_API_KEY"] = os.environ.get("GS_BENCH_API_KEY") or "not-required"  # empty secret -> placeholder
    # tau2 needs a REAL user-simulator model; when its provider rejects the
    # key (e.g. free OpenRouter tier vs a paid model) the run BLOCKS with the
    # raw reason — the simulator is never silently swapped for a weaker one.
    user_llm = os.environ.get("TAU2_USER_LLM", "openai/gpt-4o-mini")
    # Verified against `tau2 run --help` (tau2==1.0.1): agent/user take
    # component types; models ride --agent-llm/--user-llm in litellm naming.
    cmd = [
        "tau2", "run",
        "--domain", "telecom",
        "--task-set-name", "telecom",
        "--agent", "llm_agent",
        "--agent-llm", f"openai/{model}",
        "--user", "user_simulator",
        "--user-llm", user_llm,
        "--num-trials", "1",
        "--max-concurrency", "1",
        "--save-to", str(out_dir / "tau2-logs"),
    ]
    log_lines.append(f"# cmd: {' '.join(cmd)}")
    t0 = time.time()
    proc = subprocess.run(cmd, capture_output=True, text=True, env=env, timeout=_suite_timeout(entry))
    log_lines.append(proc.stdout)
    log_lines.append(proc.stderr)
    log_lines.append(f"# exit={proc.returncode} elapsed={time.time() - t0:.1f}s")
    if proc.returncode != 0:
        raise RuntimeError(f"tau2 exited {proc.returncode} (see raw log)")
    # Parse trial JSONs for per-trial rewards.
    per_sample: List[float] = []
    log_dir = out_dir / "tau2-logs"
    for jf in sorted(log_dir.rglob("*.json")):
        try:
            data = json.loads(jf.read_text())
        except Exception:  # noqa: BLE001
            continue
        rew = data.get("reward") if isinstance(data, dict) else None
        if rew is None and isinstance(data, dict):
            rew = (data.get("trial") or {}).get("reward")
        if isinstance(rew, (int, float)):
            per_sample.append(float(rew))
    if not per_sample:
        raise RuntimeError("no tau2 trial rewards parsed from output dir")
    sys.path.insert(0, str(REPO))
    from runners.hardening import agentic_result_fields

    res.update(agentic_result_fields(per_sample_visible=per_sample))
    _score_with_ci(res, per_sample, entry.get("metric", "pass_k"))
    res["status"] = "done"


def _suite_timeout(entry: Dict[str, Any]) -> int:
    per = entry.get("timeout_per_sample_s", 600)
    return int(max(per * entry.get("n_samples", 30), 1800))


# ---------------------------------------------------------------------------
# gs-bench aggregate
# ---------------------------------------------------------------------------


def cmd_aggregate(args: argparse.Namespace) -> int:
    in_dir = Path(args.input)
    out_path = Path(args.output) if args.output else REPO / "scorecard.json"
    results: List[Dict[str, Any]] = []
    for f in sorted(in_dir.rglob("*.result.json")):
        try:
            results.append(json.loads(f.read_text()))
        except Exception as e:  # noqa: BLE001
            print(f"WARN: unreadable result {f}: {e}", file=sys.stderr)
    blocked = [r for r in results if r.get("status") == "BLOCKED"]
    scored = [r for r in results if r.get("status") == "done" and r.get("score_ci_lo") is not None]
    incomplete = [r for r in results if r.get("status") not in ("done", "BLOCKED")]

    # Power section — a scorecard without power context is a trap.
    sys.path.insert(0, str(REPO))
    from stats.power import resolution

    power = []
    for r in scored:
        baseline = 0.90 if r["benchmark"] == "gpqa_diamond" else 0.50
        power.append({
            "benchmark": r["benchmark"],
            "model": r["model"],
            "scaffold": r.get("scaffold", "baseline"),
            "n_samples": r.get("n_samples"),
            **resolution(baseline, 0.05, r.get("n_samples") or 0),
        })

    scorecard = {
        "schema_version": UNIFIED_SCHEMA_VERSION,
        "generated_at": now_iso(),
        "git_commit": git_commit(),
        "docker_image_digest": docker_digest(),
        "scored": sorted(scored, key=lambda r: (r["benchmark"], r["model"], r.get("scaffold", "baseline"))),
        "blocked": blocked,
        "incomplete": incomplete,
        "power": power,
        "rules": [
            "no score without CI (hard rule 3)",
            "contaminated scores ranked separately (Phase 9)",
            "underpowered comparisons cannot declare winners (stats/power)",
        ],
    }
    out_path.write_text(json.dumps(scorecard, indent=2))
    print(f"scorecard -> {out_path} (scored={len(scored)} blocked={len(blocked)} incomplete={len(incomplete)})")
    return 0


# ---------------------------------------------------------------------------
# gs-bench compare
# ---------------------------------------------------------------------------


def cmd_compare(args: argparse.Namespace) -> int:
    in_dir = Path(args.input) if args.input else REPO / "results"
    out_dir = Path(args.output) if args.output else REPO / "comparison"
    out_dir.mkdir(parents=True, exist_ok=True)
    models = [m.strip() for m in args.models.split(",")]
    suite_names: List[str] = []
    if args.suite:
        for yml in sorted(BENCH_DIR.glob("*.yaml")):
            cfg = load_yaml(yml)
            if cfg.get("suite") == args.suite:
                suite_names = [b["name"] for b in cfg.get("benchmarks", []) if b.get("status") != "BLOCKED"]
    # Collect per (benchmark, model)
    by_key: Dict[str, Dict[str, Dict[str, Any]]] = {}
    for f in sorted(in_dir.rglob("*.result.json")):
        try:
            r = json.loads(f.read_text())
        except Exception:  # noqa: BLE001
            continue
        if r.get("status") != "done":
            continue
        if r.get("score_ci_lo") is None:
            continue
        by_key.setdefault(r["benchmark"], {})[r["model"]] = r

    sys.path.insert(0, str(REPO))
    from stats.ci import paired_compare

    comparison: Dict[str, Any] = {"generated_at": now_iso(), "models": models, "benchmarks": {}}
    for bench, per_model in sorted(by_key.items()):
        if suite_names and bench not in suite_names:
            continue
        entry: Dict[str, Any] = {}
        scores = {m: per_model[m] for m in models if m in per_model}
        entry["scores"] = {
            m: {
                "score": r["score"], "ci": [r["score_ci_lo"], r["score_ci_hi"]],
                "n": r.get("n_samples"),
            } for m, r in scores.items()
        }
        if args.baseline in scores:
            base = scores[args.baseline]
            entry["vs_baseline"] = {}
            for m, r in scores.items():
                if m == args.baseline or not per_model.get(m):
                    continue
                # Paired test needs per-sample vectors; result files carry the
                # CI only, so unless per-sample files exist we compare CIs and
                # state the overlap explicitly — no fabricated p-values.
                overlap = not (r["score_ci_lo"] > base["score_ci_hi"] or base["score_ci_lo"] > r["score_ci_hi"])
                ps_file = in_dir / f"{bench}__{sanitize(m)}__per-sample.json"
                bs_file = in_dir / f"{bench}__{sanitize(args.baseline)}__per-sample.json"
                if ps_file.exists() and bs_file.exists():
                    cmp_res = paired_compare(json.loads(bs_file.read_text()), json.loads(ps_file.read_text()))
                    entry["vs_baseline"][m] = cmp_res
                else:
                    entry["vs_baseline"][m] = {
                        "test": "CI-overlap-only (per-sample vectors absent; no p-value fabricated)",
                        "ci_overlap": overlap,
                    }
        comparison["benchmarks"][bench] = entry
    (out_dir / "comparison.json").write_text(json.dumps(comparison, indent=2))
    print(f"comparison -> {out_dir / 'comparison.json'}")
    return 0


# ---------------------------------------------------------------------------
# gs-bench contamination-check (Phase 9)
# ---------------------------------------------------------------------------


def cmd_contamination_check(args: argparse.Namespace) -> int:
    sys.path.insert(0, str(REPO))
    from runners.contamination import run_contamination_check

    entry = get_benchmark_entry(args.benchmark)
    if not entry:
        print(f"ERROR: unknown benchmark '{args.benchmark}'", file=sys.stderr)
        return 2
    out = run_contamination_check(entry, n_samples=args.n, out_dir=Path(args.output) if args.output else REPO / "results")
    print(json.dumps(out, indent=2))
    return 0 if out.get("verdict") != "ERROR" else 1


# ---------------------------------------------------------------------------
# entrypoint
# ---------------------------------------------------------------------------


def main(argv: Optional[List[str]] = None) -> int:
    p = argparse.ArgumentParser(prog="gs-bench", description="GS AI benchmark infrastructure (Phase 4)")
    sub = p.add_subparsers(dest="cmd", required=True)

    pr = sub.add_parser("run", help="run a benchmark or suite for one model")
    pr.add_argument("--model", required=True)
    pr.add_argument("--scaffold", default="baseline", choices=["baseline", "aci", "verification", "context", "router"], help="scaffold lever under test (rule 1: one lever per dispatch)")
    pr.add_argument("--suite")
    pr.add_argument("--benchmark")
    pr.add_argument("--output", default="./results")
    pr.set_defaults(func=cmd_run)

    pa = sub.add_parser("aggregate", help="aggregate result files into a scorecard")
    pa.add_argument("--input", default="./all-results")
    pa.add_argument("--output", default=None)
    pa.set_defaults(func=cmd_aggregate)

    pc = sub.add_parser("compare", help="compare models with paired stats where per-sample data exists")
    pc.add_argument("--models", required=True)
    pc.add_argument("--baseline", default="gs-ai")
    pc.add_argument("--suite")
    pc.add_argument("--input", default=None)
    pc.add_argument("--output", default=None)
    pc.set_defaults(func=cmd_compare)

    px = sub.add_parser("contamination-check", help="Phase 9 paraphrase contamination test")
    px.add_argument("--benchmark", required=True)
    px.add_argument("--n", type=int, default=20)
    px.add_argument("--output", default=None)
    px.set_defaults(func=cmd_contamination_check)

    args = p.parse_args(argv)
    return int(args.func(args) or 0)


if __name__ == "__main__":
    raise SystemExit(main())
