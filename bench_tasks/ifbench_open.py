"""GS BENCH — custom inspect tasks for benchmarks inspect_evals does not ship.

Why this exists (raw evidence):
  - run 36043125749 (run 9) ifbench job: `inspect eval inspect_evals/ifbench`
    → "No inspect tasks were found at the specified paths." IFBench is NOT in
    inspect-evals 0.21.0 (PyPI latest, wheel-verified: no ifbench module) nor
    on its main branch (checked via API).
  - `allenai/IFBench` on HF is gated for anonymous access (HF API: "Invalid
    username or password" without auth) but `allenai/IFBench_test` is PUBLIC
    (gated: False) and carries the official test prompts.

This task therefore loads the OFFICIAL dataset split (allenai/IFBench_test)
and applies the OFFICIAL strict verifier (allenai/IFBench@1c40f0c, Apache-2.0,
`pip install git+...@sha`), ported line-for-line from evaluation_lib.py
test_instruction_following_strict.

Honest correction recorded: knowledge.yaml previously said n_samples=58 —
that is the number of NEW CONSTRAINT TYPES in the IFBench paper, not the
prompt count. The public official test data holds 300 prompts (resolved at
download; written to data/dataset-revisions.json). n_samples now records
reality, not the assumption.
"""

from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List

from inspect_ai.dataset import Sample
from inspect_ai.model import GenerateConfig
from inspect_ai.scorer import Scorer, scorer, accuracy, Score, Target
from inspect_ai.solver import Solver, Task, generate

DATASET_ID = "allenai/IFBench_test"
SPLIT = "train"
REVISIONS_LEDGER = Path(__file__).resolve().parent.parent / "data" / "dataset-revisions.json"


def _resolve_hf_revision() -> str:
    """Record the dataset revision actually used (hard rule 4). Best effort:
    on failure the value says so instead of guessing."""
    try:
        from huggingface_hub import HfApi

        info = HfApi().dataset_info(DATASET_ID)
        return getattr(info, "sha", None) or "unresolved-at-run-start"
    except Exception as e:  # noqa: BLE001 — provenance must never fake a value
        return f"unresolved ({type(e).__name__}: {e})"


def _ledger_write(revision: str, n_rows: int) -> None:
    try:
        ledger: Dict[str, Any] = {}
        if REVISIONS_LEDGER.exists():
            ledger = json.loads(REVISIONS_LEDGER.read_text())
        ledger[f"ifbench::{DATASET_ID}"] = {
            "revision": revision,
            "split": SPLIT,
            "n_rows_resolved": n_rows,
            "resolved_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        }
        REVISIONS_LEDGER.parent.mkdir(parents=True, exist_ok=True)
        REVISIONS_LEDGER.write_text(json.dumps(ledger, indent=2))
    except Exception:
        pass  # ledger is provenance support, never the score path


def _load_rows() -> tuple[List[Sample], str]:
    from datasets import load_dataset

    revision = _resolve_hf_revision()
    ds = load_dataset(DATASET_ID, split=SPLIT)
    rows: List[Sample] = []
    for i, row in enumerate(ds):
        rows.append(
            Sample(
                id=str(row.get("key", i)),
                input=row["prompt"],
                metadata={
                    "instruction_id_list": list(row["instruction_id_list"]),
                    "kwargs": [dict(k) for k in row["kwargs"]],
                },
            )
        )
    _ledger_write(revision, len(rows))
    return rows, revision


@scorer(metrics=[accuracy()])
def ifbench_strict_scorer() -> Scorer:
    """Strict IFBench scoring: a prompt passes only if EVERY constraint holds.

    Ported from allenai/IFBench evaluation_lib.py::test_instruction_following_strict
    (commit 1c40f0c10d9b5c5c2f10a175a28007ebb64f7f4d):
        kwargs = {k: v for k, v in kwargs.items() if v is not None}
        instruction.build_description(**kwargs)
        args = instruction.get_instruction_args()
        if args and "prompt" in args:
            instruction.build_description(prompt=prompt)
        followed = bool(response.strip()) and instruction.check_following(response)
    """

    async def score(state, target: Target) -> Score:
        from ifbench import instructions_registry

        response = state.output.completion
        meta = state.metadata
        followed: List[bool] = []
        reasons: List[str] = []
        for index, instruction_id in enumerate(meta["instruction_id_list"]):
            try:
                instruction_cls = instructions_registry.INSTRUCTION_DICT[instruction_id]
                instruction = instruction_cls(instruction_id)
                kw = {
                    key: value
                    for key, value in (meta["kwargs"][index] or {}).items()
                    if value is not None
                }
                instruction.build_description(**kw)
                args = instruction.get_instruction_args()
                if args and "prompt" in args:
                    instruction.build_description(prompt=state.user_prompt.text)
                if response and response.strip() and instruction.check_following(response):
                    followed.append(True)
                else:
                    followed.append(False)
                    reasons.append(instruction_id)
            except Exception as e:  # noqa: BLE001 — a verifier crash is NOT a pass
                followed.append(False)
                reasons.append(f"{instruction_id} (verifier-error: {type(e).__name__}: {e})")
        all_followed = bool(followed) and all(followed)
        return Score(
            value=1.0 if all_followed else 0.0,
            answer=response,
            explanation=(
                f"constraints_followed={sum(followed)}/{len(followed)}; "
                f"failed={reasons[:5]}"
            ),
        )

    return score


@task
def ifbench_open() -> Task:
    rows, _revision = _load_rows()
    return Task(
        dataset=rows,
        solver=generate(),
        scorer=ifbench_strict_scorer(),
        config=GenerateConfig(temperature=0.2),
    )
