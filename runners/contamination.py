#!/usr/bin/env python3
"""GS BENCH — Phase 9: contamination check (paraphrase test).

For a public benchmark, before its score is admissible for ranking:
  1. Paraphrase N samples with an LLM (a DIFFERENT model family than the one
     under test — paraphrasing with the model under test would confound).
  2. Run the policy model on original vs paraphrased.
  3. If the score difference exceeds 5 percentage points → CONTAMINATED
     (MMLU showed 18pt drops; GSM8K/HumanEval ~8pt).
  4. contamination_score = |acc_original - acc_paraphrased|; recorded in the
     result JSON. Contaminated scores rank separately.

Hard rule 7: this module reports what it measured or raises — it never
returns a synthetic flag.
"""

from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path
from typing import Any, Dict, List, Tuple

REPO = Path(__file__).resolve().parent.parent
if str(REPO) not in sys.path:
    sys.path.insert(0, str(REPO))

from stats.ci import wilson_from_binary  # noqa: E402

PARAPHRASER_MODEL = os.environ.get("GS_BENCH_PARAPHRASER", "gs-ai-flash")
# NOTE: gs-ai-flash is the same family as gs-ai. To keep the paraphrase
# independent of the model under test, use an OpenRouter passthrough when
# evaluating gs-ai*; the caller may override GS_BENCH_PARAPHRASER.
PARAPHRASE_INDEPENDENCE_NOTE = (
    "paraphrase model should differ from the model under test; "
    "override with GS_BENCH_PARAPHRASER (default here is same-family gs-ai-flash, "
    "which is recorded — not hidden — in the output)"
)

PROMPT = (
    "Paraphrase the following exam question. Keep the meaning, the correct "
    "answer, the answer options (if any) and the difficulty exactly the same. "
    "Change wording and sentence order. Output ONLY the paraphrased question.\n\n"
    "{question}"
)


def _shim_chat(model: str, messages: List[Dict[str, str]], timeout: int = 600) -> str:
    import requests

    url = f"{os.environ.get('GS_ENDPOINT', '').rstrip('/')}/api/v1/openai/chat/completions"
    r = requests.post(
        url,
        json={"model": model, "messages": messages, "temperature": 0.2},
        timeout=timeout,
    )
    if r.status_code != 200:
        raise RuntimeError(f"shim {r.status_code}: {r.text[:300]}")
    return r.json()["choices"][0]["message"]["content"]


def _extract_answer_letter(reply: str) -> str:
    m = re.search(r"ANSWER:\s*([A-E])", reply, re.IGNORECASE) or re.search(
        r"\b([A-E])\b", reply[-40:]
    )
    return m.group(1).upper() if m else "?"


def _load_samples(entry: Dict[str, Any], n: int) -> List[Tuple[str, str]]:
    """Load n real dataset samples via HF datasets (pinned revision).
    Raises — never substitutes synthetic questions."""
    from datasets import load_dataset  # in requirements-bench.txt

    ds_id = entry["dataset"]["id"]
    subset = entry["dataset"].get("subset")
    ds = load_dataset(ds_id, subset, split=entry["dataset"].get("split", "test"))
    out = []
    for row in ds.select(range(min(n, len(ds)))):
        q = row.get("Question") or row.get("question") or row.get("prompt") or ""
        choices = row.get("Choices") or row.get("choices") or []
        letter = row.get("Correct Answer") or row.get("answer") or row.get("label")
        if isinstance(letter, int):
            letter = "ABCDE"[letter] if 0 <= letter < 5 else str(letter)
        if not q or letter is None:
            continue
        opt_txt = "\n".join(f"{chr(65+i)}) {c}" for i, c in enumerate(choices)) if choices else ""
        out.append((f"{q}\n{opt_txt}", str(letter).upper()))
    if not out:
        raise RuntimeError(f"no usable samples parsed from {ds_id}")
    return out


def run_contamination_check(entry: Dict[str, Any], n_samples: int = 20, out_dir: Path | None = None) -> Dict[str, Any]:
    benchmark = entry["name"]
    result: Dict[str, Any] = {
        "benchmark": benchmark,
        "n_samples_requested": n_samples,
        "paraphraser_model": os.environ.get("GS_BENCH_PARAPHRASER", PARAPHRASER_MODEL),
        "note": PARAPHRASE_INDEPENDENCE_NOTE,
        "started": True,
    }
    try:
        samples = _load_samples(entry, n_samples)
    except Exception as e:  # noqa: BLE001
        result.update({"verdict": "ERROR", "raw_reason": f"dataset load failed: {e}"})
        return result

    original_correct: List[float] = []
    paraphrased_correct: List[float] = []
    mcq = benchmark in ("gpqa_diamond", "mmlu_pro")
    errors = 0
    for q, gold in samples:
        try:
            if mcq:
                base_prompt = (
                    "Answer the following multiple choice question. The last line of your "
                    "response MUST be exactly 'ANSWER: <letter>'.\n\n" + q
                )
                resp_o = _shim_chat("gs-ai", [{"role": "user", "content": base_prompt}])
                para_q = _shim_chat(
                    result["paraphraser_model"],
                    [{"role": "user", "content": PROMPT.format(question=q)}],
                )
                resp_p = _shim_chat("gs-ai", [{"role": "user", "content": base_prompt.replace(q, para_q)}])
                original_correct.append(1.0 if _extract_answer_letter(resp_o) == gold else 0.0)
                paraphrased_correct.append(1.0 if _extract_answer_letter(resp_p) == gold else 0.0)
            else:
                para_q = _shim_chat(
                    result["paraphraser_model"],
                    [{"role": "user", "content": PROMPT.format(question=q)}],
                )
                resp_o = _shim_chat("gs-ai", [{"role": "user", "content": q}])
                resp_p = _shim_chat("gs-ai", [{"role": "user", "content": para_q}])
                # open-ended: same-answer test is not automatable honestly here
                # → record responses in the artifact, flag indeterminate.
                original_correct.append(-1.0)
                paraphrased_correct.append(-1.0)
                result.setdefault("open_ended_pairs", []).append({"orig_resp": resp_o[:200], "para_resp": resp_p[:200]})
        except Exception as e:  # noqa: BLE001
            errors += 1
            result.setdefault("errors", []).append(str(e)[:200])
    result["errors_count"] = errors

    if mcq and any(v >= 0 for v in original_correct):
        orig_ci = wilson_from_binary([v for v in original_correct if v >= 0])
        para_ci = wilson_from_binary([v for v in paraphrased_correct if v >= 0])
        score_diff = abs(orig_ci["point"] - para_ci["point"])
        result.update(
            {
                "n_scored": int(sum(1 for v in original_correct if v >= 0)),
                "accuracy_original": round(orig_ci["point"], 4),
                "accuracy_original_ci": [round(orig_ci["lo"], 4), round(orig_ci["hi"], 4)],
                "accuracy_paraphrased": round(para_ci["point"], 4),
                "accuracy_paraphrased_ci": [round(para_ci["lo"], 4), round(para_ci["hi"], 4)],
                "contamination_score": round(score_diff, 4),
                "threshold": 0.05,
                "verdict": "CONTAMINATED" if score_diff > 0.05 else "NOT-FLAGGED",
            }
        )
    else:
        result.update(
            {
                "verdict": "INDETERMINATE",
                "raw_reason": "open-ended benchmark: paraphrase scoring not automatable in this module; raw pairs recorded",
            }
        )
    if out_dir is not None:
        out_dir.mkdir(parents=True, exist_ok=True)
        (out_dir / f"contamination__{benchmark}.json").write_text(json.dumps(result, indent=2))
    return result
