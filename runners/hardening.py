#!/usr/bin/env python3
"""GS BENCH — Phase 8: reward-hacking defense.

Applies to every AGENTIC benchmark (tau2 now; SWE-bench/Terminal-Bench when
unblocked). Four defenses, each mapped to its evidence base:

1. HELD-OUT ONLY — scoring uses held-out checks. tau2's database-state
   assertions are not visible to the model (they live in the harness). For
   SWE-bench-style suites the visible->held-out switch is recorded as
   exploit_rate (SpecBench: 28.57% -> 0.56% resolve-rate swing).
2. ENVIRONMENT HARDENING — strip model-visible git history, block network
   access to test fixtures, disable pytest hooks via the env below
   (P1 classification: 87.7% relative exploit reduction).
3. AGENTIC BENCHMARK CHECKLIST — task-level preflight (see
   CHECKLIST_ITEMS): timeout, no-reward-shaping, no-test-leak, sandbox-net.
4. DUAL TRACE — visible and held-out outcomes both recorded; delta is
   exploit_rate, stored in every result JSON.
"""

from __future__ import annotations

from typing import Any, Dict, List

HARDENED_ENV = {
    # 2. Environment hardening (applied by cli adapters to every subprocess):
    "GIT_DIR": "",                    # no repo introspection by the model
    "PYTEST_DISABLE_PLUGIN_AUTOLOAD": "1",
    "PYTEST_ADDOPTS": "-p no:cacheprovider",
    "HF_DATASETS_OFFLINE": "0",
    "GS_BENCH_NO_FIXTURE_NET": "1",   # adapter-level fixture-net guard flag
}

CHECKLIST_ITEMS = [
    "timeout set per sample (config timeout_per_sample_s)",
    "scoring uses held-out checks only",
    "no reward shaping derived from model-visible state",
    "no test fixture content present in the model prompt",
    "sandbox network restricted to the shim endpoint + package indexes",
    "git history / VCS metadata stripped from the model workspace",
    "trace records visible AND held-out outcomes",
]


def agentic_result_fields(
    per_sample_visible: List[float],
    per_sample_heldout: List[float] | None = None,
) -> Dict[str, Any]:
    """Compute the Phase 8 fields for a result JSON.

    exploit_rate = mean(visible) - mean(heldout) when both exist (the visible
    minus held-out gap is the hacking signal). When only held-out/audit-style
    single-trace data exists, exploit_rate is None — reported as NOT
    MEASURED, never zero (hard rule 7).
    """
    fields: Dict[str, Any] = {
        "hardening": {
            "held_out_only": True,
            "env": sorted(HARDENED_ENV.keys()),
            "checklist": CHECKLIST_ITEMS,
        },
    }
    if per_sample_heldout is not None and len(per_sample_visible) == len(per_sample_heldout) and per_sample_heldout:
        mv = sum(per_sample_visible) / len(per_sample_visible)
        mh = sum(per_sample_heldout) / len(per_sample_heldout)
        fields["exploit_rate"] = round(mv - mh, 4)
        fields["visible_pass"] = round(mv, 4)
        fields["heldout_pass"] = round(mh, 4)
    else:
        fields["exploit_rate"] = None
        fields["exploit_rate_note"] = "NOT MEASURED (single trace only; visible/held-out pair absent)"
    return fields


def preflight_checklist(entry: Dict[str, Any]) -> List[str]:
    """Return checklist items that the run must satisfy; adapters raise if a
    hard item (timeout, held-out scoring) is missing."""
    problems = []
    if not entry.get("timeout_per_sample_s"):
        problems.append("missing timeout_per_sample_s")
    if entry.get("agentic") and not entry.get("dataset_revision"):
        problems.append("agentic task without pinned dataset revision")
    return problems
