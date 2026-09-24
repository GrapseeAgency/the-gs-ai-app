#!/usr/bin/env python3
"""GS BENCH — Phase 11: cost tracking with hard budget enforcement.

Reference motivation: 77x cost gap between cheapest and most expensive models
on identical agent jobs — per-TOKEN price is not per-TASK cost. The tracker:
  - records cost per TASK (with per-token breakdown),
  - records verbosity (output tokens per task),
  - HARD-STOPS the benchmark at budget and marks the run.

Token counts are provider-reported where available (shim usage fields are
estimates and are labeled as such); rates come from MODEL_RATES below.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, List, Tuple


class BudgetExceededError(RuntimeError):
    pass


@dataclass
class Rate:
    input_per_1m: float
    output_per_1m: float
    source: str = "configured"


# USD per 1M tokens. GS AI tiers run on self-hosted/z-ai free allocation —
# the marginal API cost is 0.00; competitor rates are the configured list
# prices at provisioning time and MUST be re-verified before cost reports.
MODEL_RATES: Dict[str, Rate] = {
    "gs-ai": Rate(0.0, 0.0, source="internal allocation"),
    "gs-ai-flash": Rate(0.0, 0.0, source="internal allocation"),
    "gs-ai-thinking": Rate(0.0, 0.0, source="internal allocation"),
    "openai/gpt-5.5": Rate(1.25, 10.0, source="list price 2026-09"),
    "anthropic/claude-opus-4.8": Rate(5.0, 25.0, source="list price 2026-09"),
    "google/gemini-3.1-pro": Rate(1.25, 10.0, source="list price 2026-09"),
}


@dataclass
class CostTracker:
    budget_usd: float
    model: str = "unknown"
    benchmark: str = "unknown"
    spent: float = 0.0
    tasks: List[Dict[str, float]] = field(default_factory=list)

    def record(self, model: str, input_tokens: int, output_tokens: int) -> Tuple[float, float]:
        """Record one model call. Returns (call_cost, cumulative_spent).
        Raises BudgetExceededError when the budget is crossed — the adapter
        must let this abort the run and mark it BUDGET-ABORTED."""
        if model not in MODEL_RATES:
            raise KeyError(f"no rate configured for model '{model}' — refusing to guess (no fabrication)")
        rate = MODEL_RATES[model]
        cost = (input_tokens / 1e6) * rate.input + (output_tokens / 1e6) * rate.output
        self.spent += cost
        self.tasks.append(
            {
                "model": model,
                "input_tokens": input_tokens,
                "output_tokens": output_tokens,
                "cost_usd": round(cost, 6),
                "verbosity_out_tokens": output_tokens,
            }
        )
        if self.spent > self.budget:
            raise BudgetExceededError(f"Spent ${self.spent:.4f} > budget ${self.budget:.4f}")
        return cost, self.spent

    def record_task(self, calls: List[Tuple[int, int]], model: str | None = None) -> Tuple[float, float]:
        """Record one benchmark TASK (possibly many calls)."""
        model = model or self.model
        total_in = sum(c[0] for c in calls)
        total_out = sum(c[1] for c in calls)
        cost = 0.0
        for i, o in calls:
            c, _ = self.record(model, i, o)
            cost += c
        return cost, self.spent

    def summary(self) -> Dict[str, float | str | int]:
        out_tokens = sum(t["output_tokens"] for t in self.tasks)
        n = max(len(self.tasks), 1)
        return {
            "model": self.model,
            "benchmark": self.benchmark,
            "budget_usd": self.budget_usd,
            "spent_usd": round(self.spent, 6),
            "tasks": len(self.tasks),
            "cost_per_task_usd": round(self.spent / n, 6),
            "avg_verbosity_out_tokens": round(out_tokens / n, 1),
        }


def rate_audit_note() -> str:
    return "; ".join(f"{k}: {v.source}" for k, v in MODEL_RATES.items())
