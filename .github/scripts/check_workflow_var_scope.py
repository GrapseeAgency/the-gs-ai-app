#!/usr/bin/env python3
"""Find shell variables a workflow step READS but never ASSIGNS.

The failure this catches
------------------------
Each `run:` block is its own shell. A variable assigned in one step does not
exist in the next, and under `set -u` reading it there aborts the step. This
happened in android-native.yml, where the libgs_sd.so copy reached for
``$SD_ROOT``::

    line 197: SD_ROOT: unbound variable
    ##[error]Process completed with exit code 1.

``SD_ROOT`` was local to the "Fetch stable-diffusion.cpp" step. The value that
step actually publishes to later steps is ``GS_SD_PREBUILT``, via
``$GITHUB_ENV``. The whole Option B build gate sat behind an unbound variable.

Scope rules this implements, each of which is a false positive if missed
------------------------------------------------------------------------
assigned in the block   ``VAR=``, ``export VAR=``, ``for VAR in``, C-style
                        ``for ((VAR=``,
                        ``while read -r A B C`` -- read assigns EVERY name, not
                        just the first, and getting that wrong produces two
                        phantom findings in ios-native.yml
environment             workflow-level, job-level and step-level ``env:`` keys,
                        plus anything the block reads via ``${{ env.X }}``
provided by the runner  ``GITHUB_*``, ``ANDROID_*``, ``RUNNER_*`` and friends
guarded                ``${VAR:-default}`` is a SAFE read -- the point of the
                        form is that the variable may be unset. Only bare reads
                        are findings, because only bare reads abort under ``-u``
comments                masked first, so a variable named only inside a comment
                        is not a finding. Same lesson as check_kt_format_specs.py

Usage: check_workflow_var_scope.py <workflow.yml> [...]
Exit: 0 clean, 1 with a report per finding, 2 on usage error.
"""

import os
import re
import sys

import yaml

RUNNER_PROVIDED = re.compile(
    r"^(GITHUB_|ANDROID_|RUNNER_|CI$|HOME$|PWD$|PATH$|JAVA_HOME$|NDK_|GRADLE_|"
    r"CARGO_|RUST|GHOST|CODEX|SHELL$|TERM$|USER$|LANG$|LD_LIBRARY_PATH$|TMPDIR$|_"
    r"|ImageOS$|ImageVersion$|RUNNER_NAME$|RUNNER_OS$)",
)

# Two alternatives, because one is not enough:
#   ${NAME ...}   braced: the tail tells us whether a default was supplied
#   $NAME         bare:   an unset value here aborts under `set -u`
# A single `\$\{?([A-Z_][A-Z0-9_]*)\}?` pattern gets `${ImageOS:-}` WRONG -- it
# captures `I` out of the camelCase name and reports a variable that does not
# exist. Reading the braced form as one token is what avoids inventing findings.
READ = re.compile(r"\$(?:\{([A-Za-z_][A-Za-z0-9_]*)([^}]*)\}|([A-Za-z_][A-Za-z0-9_]*))")

# `mapfile -t KT` and `readarray -t KTT` assign an array without ever writing
# `KT=`, which is how android-app.yml builds its source lists.
ARRAY_ASSIGN = re.compile(r"\b(?:mapfile|readarray)\s+(?:-[a-zA-Z]+\s+)*([A-Za-z_][A-Za-z0-9_]*)")

# `echo "NAME=value" >> "$GITHUB_ENV"` is the SUPPORTED way to publish a value to
# later steps in the same job. Both GS_SD_PREBUILT (android-native) and
# IOS_DESTINATION (ios.yml) work this way, and treating those reads as findings
# would flag the correct pattern twice.
GITHUB_ENV_WRITE = re.compile(r'echo\s+"?([A-Za-z_][A-Za-z0-9_]*)=[^\n]*>>\s*"?\$GITHUB_ENV')

SHELL_BUILTINS = {
    "RANDOM", "SECONDS", "LINENO", "FUNCNAME", "PWD", "OLDPWD", "IFS",
    "PIPESTATUS", "PIPELINE", "BASH_COMMAND", "EPOCHSECONDS", "SRANDOM",
}


def mask_comments(run):
    """Blank out everything from an unquoted '#' to end of line."""
    out = []
    for line in run.split("\n"):
        res, i, quote = [], 0, None
        while i < len(line):
            ch = line[i]
            if quote:
                res.append(ch)
                if ch == "\\":
                    res.append(line[i + 1])
                    i += 2
                    continue
                if ch == quote:
                    quote = None
                i += 1
                continue
            if ch in "\"'":
                quote = ch
                res.append(ch)
                i += 1
                continue
            if ch == "#":
                break
            res.append(ch)
            i += 1
        out.append("".join(res))
    return "\n".join(out)


def env_keys(mapping):
    keys = set()
    if isinstance(mapping, dict):
        for key, value in mapping.items():
            if isinstance(key, str):
                keys.add(key)
            keys |= set(re.findall(r"env\.([A-Z0-9_]+)", str(value)))
    return keys


def assigned_names(run):
    names = set()
    names |= set(re.findall(r"\b([A-Za-z_][A-Za-z0-9_]*)\s*=(?!=)", run))
    names |= set(re.findall(r"for\s+([A-Za-z_][A-Za-z0-9_]*)\s+in\b", run))
    names |= set(re.findall(r"for\s+\(\(\s*([A-Za-z_][A-Za-z0-9_]*)", run))
    names |= set(ARRAY_ASSIGN.findall(run))
    # `while read -r A B C` assigns A, B AND C. Capturing only the first name is
    # what produced the phantom ios-native findings.
    for group in re.findall(r"\bread\s+(?:-[a-zA-Z]+\s+)+([A-Za-z_][A-Za-z0-9_\s]*)", run):
        names |= set(group.split())
    names |= set(re.findall(r"\b([A-Za-z_][A-Za-z0-9_]*)=", run))
    return names


def reads_of(run):
    """(name, is_guarded) for every variable reference in the block."""
    out = []
    for braced, tail, bare in READ.findall(run):
        if braced:
            # A default (`${V:-x}` or `${V-x}`) is the guarded form: the author is
            # saying the variable may legitimately be unset.
            out.append((braced, bool(re.match(r"\s*:?[-=]", tail))))
        elif bare:
            out.append((bare, False))
    return out


def check(path):
    doc = yaml.safe_load(open(path))
    workflow_env = env_keys(doc.get("env") or {})
    findings = []
    for job_name, job in (doc.get("jobs") or {}).items():
        job_env = env_keys(job.get("env") or {}) | workflow_env
        published = set()  # accumulated from $GITHUB_ENV by EARLIER steps
        for step in job.get("steps") or []:
            raw = step.get("run") or ""
            if not raw:
                continue
            run = mask_comments(raw)
            known = (
                assigned_names(run)
                | env_keys(step.get("env") or {})
                | job_env
                | published
                | SHELL_BUILTINS
            )
            missing = sorted(
                {
                    name
                    for name, is_guarded in reads_of(run)
                    if not is_guarded and not RUNNER_PROVIDED.match(name)
                }
                - known
            )
            # Values this step publishes are visible to LATER steps in the job.
            published |= set(GITHUB_ENV_WRITE.findall(run))
            if missing:
                findings.append((job_name, step.get("name"), missing))
    return findings


def main(argv):
    if len(argv) < 2:
        sys.exit(__doc__)
    problems = 0
    for path in argv[1:]:
        try:
            findings = check(path)
        except (OSError, yaml.YAMLError) as exc:
            print("::error::%s: %s" % (path, exc))
            problems += 1
            continue
        for job_name, step_name, missing in findings:
            problems += len(missing)
            print("::error::%s  job=%s  step=%s"
                  % (os.path.basename(path), job_name, step_name))
            print("::error::  read but never assigned here: %s" % ", ".join(missing))
            print("::error::  each run: block is its own shell. Under `set -u` this "
                  "aborts the step. If the value comes from another step, it must "
                  "be published via $GITHUB_ENV; if it may legitimately be unset, "
                  "read it as ${VAR:-default}.")
    if problems:
        print("FAIL: %d unscoped variable read(s)" % problems)
        return 1
    print("ok: every run block's variables are assigned in it, in env:, or guarded")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))