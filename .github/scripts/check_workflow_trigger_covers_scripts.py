#!/usr/bin/env python3
"""Find workflows that invoke a script its own push/PR trigger cannot reach.

The failure this catches
------------------------
A lint wired into a workflow whose ``paths:`` filter excludes the paths that lint
inspects. It looks enforced and is not: the workflow only runs in response to a
change that has ALREADY broken something the lint checks, so the lint can never
prevent the break.

This is not hypothetical. Five lints were added to ``android-app.yml``'s compile
job -- two of which read ``.github/workflows/*.yml``, and all of which live in
``.github/scripts/`` -- while that workflow's trigger was::

    paths:
      - 'android/**'

So editing a workflow, a lint, or a C++ file did not run the lints that check
exactly those files. The wiring step reported success and enforced nothing.

The same shape as the three CI gates found in the audit, arrived at from the other
direction: a check that cannot run cannot fail. Wiring the lint in was necessary
and not sufficient; the trigger is what makes it real.

What is flagged
---------------
A ``run:`` block that invokes a script under ``.github/scripts/`` while the
workflow's own ``paths:`` filter does not include ``.github/scripts``. That
narrow rule is deliberate:

- it is precise -- you are asserting the workflow RUNS that script, so a change to
  the script must reach it;
- it is about the workflow's OWN trigger, not about correctness, so it needs no
  opinion on what the script checks;
- ``workflow_dispatch`` is unaffected and is not treated as a trigger, because a
  manual run is not a gate.

Workflows with no ``paths:`` filter are not flagged: they run on every push, so
every script they invoke is reachable.

Usage: check_workflow_trigger_covers_scripts.py <workflow.yml> [...]
Exit: 0 clean, 1 with a report, 2 on usage error.
"""

import os
import re
import sys

import yaml

SCRIPT_REF = re.compile(r"(?:^|[\s\"'])\.github/scripts/([A-Za-z0-9_.\-]+)")


def trigger_paths(doc):
    """The union of push and pull_request `paths:` entries. None => unfiltered."""
    on = doc.get("on") or doc.get(True) or {}
    if not isinstance(on, dict):
        return None
    collected = []
    for event in ("push", "pull_request"):
        cfg = on.get(event)
        if not isinstance(cfg, dict):
            continue
        paths = cfg.get("paths")
        if paths is None:
            return None  # unfiltered: runs on every push
        collected.extend(str(p) for p in paths)
    # push and pull_request usually carry the SAME list; printing it twice is noise
    # in a diagnostic, and a diagnostic that pads its output is one people skim.
    return sorted(set(collected))


def main(argv):
    if len(argv) < 2:
        sys.exit(__doc__)
    problems = 0
    checked = 0
    for path in argv[1:]:
        try:
            doc = yaml.safe_load(open(path))
        except (OSError, yaml.YAMLError) as exc:
            print("::error::%s: %s" % (path, exc))
            problems += 1
            continue
        if not isinstance(doc, dict) or "jobs" not in doc:
            continue
        paths = trigger_paths(doc)
        if paths is None:
            continue
        checked += 1
        covered_scripts = any(p.startswith(".github/scripts") for p in paths)
        for job_name, job in (doc.get("jobs") or {}).items():
            for step in job.get("steps") or []:
                run = step.get("run") or ""
                for script in sorted(set(SCRIPT_REF.findall(run))):
                    if covered_scripts:
                        continue
                    problems += 1
                    print("::error::%s  job=%s  step=%s"
                          % (os.path.basename(path), job_name, step.get("name")))
                    print("::error::  runs .github/scripts/%s, but this workflow's"
                          % script)
                    print("::error::  push/pull_request `paths:` is %s -- which does not"
                          % ", ".join(paths))
                    print("::error::  include .github/scripts. A change to that script")
                    print("::error::  does not run this step, so the step cannot fail on")
                    print("::error::  a change to the script it exists to run.")
    if problems:
        print("FAIL: %d unreachable script invocation(s) in %d filtered workflow(s)"
              % (problems, checked))
        return 1
    print("ok: every workflow that runs a .github/scripts/ script is triggered by it "
          "(%d filtered workflow(s) checked)" % checked)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))