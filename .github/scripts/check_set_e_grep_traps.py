#!/usr/bin/env python3
"""Find `grep` calls that `set -e` will treat as fatal when they find nothing.

The failure this catches
------------------------
`grep` exits 1 when it matches nothing. That is not an error, it is an answer.
Under `set -e` -- optionally with `set -o pipefail`, which promotes the 1 through a
pipeline -- an unguarded `grep` that legitimately finds nothing kills the script.

In `.github/scripts/run-instrumented.sh` that is exactly what happened, and it cost
three ~70-minute device runs. The script sets `set -e` at line 393 and then, ~80
lines later, added a gate that counts test annotations::

    set -e                        # line 393, pre-existing
    ...
    IGNORED=$(grep -rhcE '^[[:space:]]*@Ignore\\b' android/app/src/androidTest/.../*.kt \\
      2>/dev/null | awk '{s+=$1} END {print s+0}')

This repository has **zero** `@Ignore` annotations, so `grep -c` correctly exits 1,
`pipefail` promotes it, and `set -e` kills the script on the assignment -- one line
before the gate's first `echo`. Every device run reported::

    connectedDebugAndroidTest exit: 1
    === dumping logcat (test exit was 2) ===

with nothing from the gate in between. Reproduced without a runner::

    bash -c 'set -euo pipefail; IGNORED=$(grep -rhcE "^[[:space:]]*@Ignore\\b" ... | awk ...); echo AFTER'
    before
    exit=1                       # "AFTER" never printed

Note the direction. This repository has already been bitten three times by the
opposite trap -- `grep -q` closing a pipe early, SIGPIPE-ing the producer, and a
`pipefail` pipeline firing on a *successful* match. Both are the same underlying
mistake: **under `set -e`, a command's exit status decides whether the script
lives, and grep's exit status encodes "did I find anything", not "did I work".**

What is flagged, and what is deliberately not
---------------------------------------------
Flagged: after a `set -e`, a statement that runs `grep` **outside** a condition and
**without** a `||` / `&&` discharge -- an assignment from a command substitution, or
a bare pipeline.

Not flagged, because each is correct:

- ``if grep -q x; then`` / ``while grep -q x; do`` -- a condition; its status IS the
  question being asked, so `set -e` correctly does not fire.
- ``grep q file || echo none`` -- explicitly discharged.
- ``grep q file && act`` -- discharged.
- ``{ grep ... || true; } | awk`` -- the brace group forces exit 0 and adds no
  stdout. This is the CORRECT form and appears in the fix; flagging it would
  punish the right answer.
- Anything before the first ``set -e``.

The fix it recommends is the brace-group form, not ``|| echo 0``: that appends a
second line to the substitution's stdout, so the variable becomes ``"0\\n0"`` and the
arithmetic dies with ``arithmetic syntax error in expression (error token is "0")``.
``bash -n`` accepts the broken form; only executing it fails.

Usage: check_set_e_grep_traps.py <file.sh|file.yml> [...]
Exit: 0 clean, 1 with a report per finding, 2 on usage error.
"""

import os
import re
import sys

import yaml

SET_E = re.compile(r"^\s*set\s+-[a-zA-Z]*e")
# `VAR=$( ... grep ... )` -- the assignment form. The variable is the POINT of the
# statement, so a grep that finds nothing yields an empty variable and the
# assignment fails under `set -e`. Guarding it changes no semantics whatsoever.
ASSIGNED = re.compile(r"^\s*[A-Za-z_][A-Za-z0-9_]*\s*\+?=")
GREP_USE = re.compile(r"(?<![\w.])grep\b")


def strip_quoted_and_comments(line):
    """Blank comments and quoted spans so prose cannot trip the matcher."""
    out, i, quote = [], 0, None
    while i < len(line):
        ch = line[i]
        if quote:
            if ch == "\\":
                i += 2
                continue
            if ch == quote:
                quote = None
            i += 1
            continue
        if ch in "\"'":
            quote = ch
            i += 1
            continue
        if ch == "#":
            break
        out.append(ch)
        i += 1
    return "".join(out)


def examine(script_text, label, report):
    """Walk a shell script, reporting unguarded greps that follow a `set -e`.

    Each finding is (label, lineno, severity, text). Severity is ERROR for an
    ASSIGNMENT fed by a grep -- benign to guard, and the shape that killed three
    device runs -- and NOTE for a bare pipeline, where the grep may be the
    assertion itself and silencing it would disable a real check.
    """
    armed = False
    lines = script_text.split("\n")

    # JOIN BACKSLASH CONTINUATIONS INTO ONE LOGICAL STATEMENT BEFORE ANALYSING.
    #
    # The first version walked physical lines, so
    #
    #     GOT=$(grep -oE '...' "$SYMFILE" \
    #             | sed 's/.*//' | sort -u || true)
    #
    # was judged on its FIRST line, which has no `||`, and was reported -- even
    # though the statement is discharged one line later. Two false positives in
    # check_jni_matches_kt.sh, and both were of the shape this lint exists to
    # catch, which is the most damaging kind of noise: a lint that cries wolf on
    # the correct form trains people to ignore it.
    #
    # bash already told us how to find a statement boundary -- a trailing
    # backslash -- so use exactly that.
    logical = []           # (first_lineno, joined_code)
    buf, start = "", 0
    for lineno, raw in enumerate(lines, 1):
        code = strip_quoted_and_comments(raw)
        if not buf:
            if not code.strip():
                continue
            start = lineno
            buf = code
        else:
            buf += " " + code.strip()
        if buf.rstrip().endswith("\\"):
            buf = buf.rstrip()[:-1]
            continue
        logical.append((start, buf))
        buf = ""
    if buf:
        logical.append((start, buf))

    for lineno, code in logical:
        if SET_E.match(code):
            armed = True
            continue
        if not armed or not GREP_USE.search(code):
            continue

        # A grep is SAFE when its status IS the question being asked.
        #
        # The first version treated any `(` before `grep` as a condition, which made
        # `VAR=$(grep ...)` look safe. It is the opposite: `$(` is a COMMAND
        # SUBSTITUTION, and under `set -e` a substitution whose command fails fails
        # the assignment. That mistake made the lint silent on the very file it was
        # written for, which is the worst possible failure mode for a lint.
        #
        # So the safe forms are enumerated explicitly rather than inferred from a
        # punctuation class:
        #   if / while / until / elif  [ ( ] [!] grep ...   -- a condition
        #   ( grep ... )                                   -- a subshell condition
        #   grep ... && act   /  grep ... || act           -- discharged
        # Everything else, INCLUDING `VAR=$(grep ...)`, is a finding.
        # Locate the grep so "before it" can be tested positionally.
        g = GREP_USE.search(code)
        if g is None:
            continue
        before = code[: g.start()]

        # A CONDITION is safe, and the keyword may be separated from the grep by a
        # whole pipeline:
        #     if ! find . -name '*.a' | grep -q .; then
        # The first version used `keyword[^|;&]*grep`, which could not cross the
        # pipe, so that extremely common shape was reported. Position is the right
        # test: if the keyword appears BEFORE the grep in the same logical line, the
        # grep's status is the question being asked.
        if re.search(r"\b(if|while|until|elif|then|do)\b", before):
            continue
        if re.search(r"!", before) and not re.search(r"[!=]", before):
            continue

        # `grep` inside `<( ... )` -- process substitution -- has its exit status
        # DISCARDED by the shell; only the substitution's stdout matters. So
        #     mapfile -t MEMBERS < <(ar t "$a" | grep -v '^__')
        # cannot die under `set -e` no matter what grep returns.
        # The closing delimiter of `<( ... )` is `)`, NOT `>`. The first version
        # required a `>` somewhere in the line, which no process substitution
        # contains, so the exemption never fired and this shape was reported:
        #     mapfile -t MEMBERS < <(ar t "$abs" | grep -v '^__')
        if re.search(r"<\(", before) and ")" in code[g.start():]:
            continue

        # A command-substitution or subshell in a CONDITION is still a condition.
        if re.search(r"^\s*\(\s*", code):
            continue
        if re.search(r"grep\b[^\n]*\|\|", code):
            continue
        if re.search(r"grep\b[^\n]*&&", code):
            continue
        if re.search(r"\|\|\s*true\s*;?\s*\}", code):
            continue          # the brace-group form, which is correct

        severity = "ERROR" if ASSIGNED.search(code) else "NOTE"
        report.append((label, lineno, severity, code.strip()[:96]))


def main(argv):
    if len(argv) < 2:
        sys.exit(__doc__)
    problems = 0
    notes = 0
    scanned = 0
    for path in argv[1:]:
        try:
            if path.endswith((".yml", ".yaml")):
                doc = yaml.safe_load(open(path))
                blocks = []
                if isinstance(doc, dict):
                    for job in (doc.get("jobs") or {}).values():
                        for step in job.get("steps") or []:
                            if step.get("run"):
                                blocks.append((step.get("name") or "?", step["run"]))
                for name, body in blocks:
                    scanned += 1
                    report = []
                    examine(body, "%s  step=%s" % (os.path.basename(path), name), report)
                    for label, lineno, severity, text in report:
                        notes += (severity == "NOTE")
                        if severity == "ERROR":
                            problems += 1
                            print("::error::%s:%d  %s" % (label, lineno, text))
                            print("::error::  an ASSIGNMENT fed by `grep`, which exits 1 "
                                  "when it matches nothing. Under `set -e` the variable "
                                  "is the point of the statement, so a correct "
                                  "\"none found\" is fatal.")
                            print("::error::  Fix: `{ grep ... || true; } | ...` -- not "
                                  "`... || echo 0`, which appends a second value to the "
                                  "substitution and breaks any arithmetic on it.")
                        else:
                            print("::warning::%s:%d  %s" % (label, lineno, text))
                            print("::warning::  a bare pipeline ending in `grep` after "
                                  "`set -e`. If this grep IS the assertion, a "
                                  "match-less run correctly fails the step -- but with "
                                  "NO diagnostic naming what was expected.")
                            print("::warning::  Deliberately NOT silenced: wrapping this "
                                  "in `|| true` would disable a real check. Converting it "
                                  "to `if ! grep ...; then echo \"FAIL: <what>\"; exit 1;"
                                  " fi` keeps the verdict and adds the message.")
            else:
                text = open(path, errors="replace").read()
                scanned += 1
                report = []
                examine(text, os.path.basename(path), report)
                for label, lineno, severity, line in report:
                    notes += (severity == "NOTE")
                    if severity == "ERROR":
                        problems += 1
                        print("::error::%s:%d  %s" % (label, lineno, line))
                        print("::error::  an ASSIGNMENT fed by `grep`, which exits 1 "
                              "when it matches nothing. Under `set -e` that makes the "
                              "assignment fatal.")
                        print("::error::  Fix: `{ grep ... || true; } | ...` -- not "
                              "`... || echo 0`, which appends a second value.")
                    else:
                        print("::warning::%s:%d  %s" % (label, lineno, line))
                        print("::warning::  a bare pipeline ending in `grep` after "
                              "`set -e`. If this grep IS the assertion, a match-less "
                              "run correctly fails the step, but with no diagnostic.")
                        print("::warning::  Deliberately NOT silenced.")
        except (OSError, yaml.YAMLError) as exc:
            print("::error::%s: %s" % (path, exc))
            problems += 1
    tail = (", %d NOTE(s) -- bare pipelines where grep may be the assertion" % notes) if notes else ""
    if problems:
        print("FAIL: %d set -e grep trap(s) in %d script(s)/workflow(s)%s"
              % (problems, scanned, tail))
        return 1
    print("ok: no unguarded `grep` feeding an assignment after `set -e`, in %d "
          "script(s)/workflow(s)%s" % (scanned, tail))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
