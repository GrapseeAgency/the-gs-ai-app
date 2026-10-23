#!/usr/bin/env python3
"""Every gate script must be run by a workflow, not merely mentioned by one.

WHY
---
`check_home_type_parity.py` read both platforms, held twelve Home type roles to a
contract, and had comments in three languages plus a doc paragraph saying it "fails
CI". No workflow invoked it. It could not fail, so the contract it describes was
enforced by nothing but the honesty of whoever last edited a role — which is the same
defect as a pixel gate that passes on a blank frame, one layer out: the check existed,
was correct, and was never executed.

The rule this enforces: a file named `check_*.py`/`check_*.sh`/`test_*.py` inside
`.github/scripts/` or `scripts/` is a gate, and a gate must be invoked from a
workflow's run block. Mentions inside YAML comments do not count — they are stripped
before scanning, because a comment is how the missing invocation was hidden.

Limits, stated rather than implied: this proves an invocation line exists, not that it
runs on the right triggers (that is `check_workflow_trigger_covers_scripts.py`) nor
that its step can fail (`scripts/check_tests_can_fail.py`). It also cannot see scripts
invoked indirectly through a runner that takes the name as an argument.
"""
import re
import sys
import tempfile
from pathlib import Path

DIRS = (".github/scripts", "scripts")
WORKFLOWS = ".github/workflows"
GATE_GLOBS = ("check_*.py", "check_*.sh", "test_*.py")


def gate_scripts(root: Path):
    found = {}
    for d in DIRS:
        for pattern in GATE_GLOBS:
            for p in sorted((root / d).glob(pattern)):
                found[p.name] = p
    return found


def invoked_names(root: Path):
    """Basenames that appear in a workflow line that is not a YAML comment."""
    names = set()
    for wf in sorted((root / WORKFLOWS).glob("*.yml")):
        for line in wf.read_text().splitlines():
            body = line.strip()
            if not body or body.startswith("#"):
                continue
            names.update(re.findall(r"[\w./-]*?(check_[\w-]+?\.(?:py|sh)|test_[\w-]+?\.py)", body))
    return names


def audit(root: Path, scripts=None) -> list:
    scripts = scripts if scripts is not None else gate_scripts(root)
    invoked = invoked_names(root)
    missing = []
    for name, path in scripts.items():
        if name not in invoked:
            missing.append(str(path.relative_to(root)))
    return missing


def self_test() -> int:
    """A synthetic tree where one gate is uninvoked: the checker must name it."""
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        (root / ".github/scripts").mkdir(parents=True)
        (root / ".github/workflows").mkdir(parents=True)
        (root / "scripts").mkdir()
        (root / ".github/scripts/check_wired.py").write_text("print('ok')\n")
        (root / ".github/scripts/check_orphan.py").write_text("print('never run')\n")
        (root / "scripts/test_suite.py").write_text("print('never run either')\n")
        (root / ".github/workflows/ci.yml").write_text(
            "jobs:\n  a:\n    steps:\n"
            "      # python3 .github/scripts/check_orphan.py   <- a comment is not an invocation\n"
            "      - run: python3 .github/scripts/check_wired.py\n")
        missing = audit(root)
        want = {".github/scripts/check_orphan.py", "scripts/test_suite.py"}
        if set(missing) != want:
            print(f"self-test FAILED: expected {sorted(want)}, got {sorted(missing)}")
            return 1
        print("self-test ok: an orphan gate and a comment-only mention are both caught, "
              "and the wired one is not")
    return 0


def main() -> int:
    if "--self-test" in sys.argv:
        return self_test()
    root = Path(".").resolve()
    missing = audit(root)
    total = len(gate_scripts(root))
    if missing:
        print(f"{len(missing)} of {total} gate script(s) are not invoked by any workflow:")
        for m in missing:
            print(f"  {m}")
        print("\nA gate that nothing runs cannot fail. Wire it into a workflow's run "
              "block, or delete it and stop claiming it holds.")
        return 1
    print(f"ok: all {total} gate script(s) are invoked from a workflow run block")
    return 0


if __name__ == "__main__":
    sys.exit(main())
