#!/usr/bin/env python3
"""Find shell comments that sit between backslash-continued lines.

The failure this catches
------------------------
In ``sh``/``bash`` a trailing ``\\`` joins the next PHYSICAL line into the current
command. If that continued line starts with ``#``, it is a comment -- and the
comment runs to the end of that line. So the lines of the command BELOW it are
never joined, and whatever follows the comment block becomes a command of its
own. This repository shipped that bug into android-deps.yml and it cost a CI
run::

    cmake -S src-sd -B build \\
      -DCMAKE_TOOLCHAIN_FILE="$PWD/android.toolchain.cmake" \\
      ...
      -DGGML_OPENMP=OFF \\
      # OPTION B: SHARED, so sd.cpp and its ggml 0.25.3 leave as ONE
      # self-contained .so that libgs_ffi.so never links against.
      -DSD_BUILD_SHARED_LIBS=ON \\
      -DSD_WEBP=OFF ...

became, in the runner::

    line 35: -DSD_BUILD_SHARED_LIBS=ON: command not found
    ##[error]Process completed with exit code 127.

and, worse, the four flags after that line were dropped from the cmake call with
no error at all. So it is not only a crash: a comment in the wrong place
DELATES the code under it. That is why this is a lint and not a convention.

It is also silent under ``bash -n``, which parses each line as a valid comment
and a valid command. This script is the only thing standing between that and the
next run.

Usage::

    check_shell_continuations.py <workflow.yml> [<workflow.yml> ...]

Exit status: 0 clean, 1 with one report per trap found, 2 on a usage/YAML error.
Each run: block is also parsed with ``bash -n`` so a shell syntax error is caught
here rather than at run time.
"""

import os
import subprocess
import sys
import tempfile

import yaml


def run_blocks(workflow):
    """Yield (job_name, step_name, run_text) for every step with a run: block."""
    with open(workflow) as fh:
        doc = yaml.safe_load(fh)
    if not isinstance(doc, dict) or "jobs" not in doc:
        raise ValueError("%s has no 'jobs' mapping; is it a workflow?" % workflow)
    for job_name, job in (doc.get("jobs") or {}).items():
        for step in (job.get("steps") or []):
            body = step.get("run")
            if isinstance(body, str) and body.strip():
                yield job_name, (step.get("name") or "(unnamed)"), body


def traps_in(run_text):
    """Return (index, line, next_line) for every comment-after-continuation."""
    lines = run_text.split("\n")
    found = []
    for i in range(len(lines) - 1):
        if lines[i].rstrip().endswith("\\") and lines[i + 1].lstrip().startswith("#"):
            found.append((i, lines[i], lines[i + 1]))
    return found


def shell_syntax_ok(run_text):
    """bash -n on the block under `set -euo pipefail`, as the runner sees it."""
    with tempfile.NamedTemporaryFile("w", suffix=".sh", delete=False) as tmp:
        tmp.write("set -euo pipefail\n" + run_text)
        path = tmp.name
    try:
        proc = subprocess.run(["bash", "-n", path], capture_output=True, text=True)
        return proc.returncode, proc.stderr.strip()
    finally:
        os.unlink(path)


def main(argv):
    if len(argv) < 2:
        sys.exit(__doc__)
    problems = 0
    for workflow in argv[1:]:
        try:
            blocks = list(run_blocks(workflow))
        except (OSError, ValueError, yaml.YAMLError) as exc:
            print("::error::%s: %s" % (workflow, exc))
            problems += 1
            continue

        for job_name, step_name, run_text in blocks:
            for idx, line, comment in traps_in(run_text):
                problems += 1
                print(
                    "::error::%s  job=%s  step=%s  line %d"
                    % (workflow, job_name, step_name, idx + 1)
                )
                print("::error::  %s" % line.strip())
                print(
                    "::error::  %s"
                    % comment.strip()
                )
                print(
                    "::error::  a comment here ends the continued command, so "
                    "whatever follows the comment block runs as its own command. "
                    "Flags under it are dropped SILENTLY. Move the comment "
                    "ABOVE the command."
                )

            rc, err = shell_syntax_ok(run_text)
            if rc != 0:
                problems += 1
                print(
                    "::error::%s  job=%s  step=%s  bash -n: %s"
                    % (workflow, job_name, step_name, err.split("\n")[0][:120])
                )

    if problems:
        print("FAIL: %d problem(s)" % problems)
        return 1
    print("ok: no continuation-comment traps; every run block parses")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))