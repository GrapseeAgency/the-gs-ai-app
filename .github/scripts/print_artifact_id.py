#!/usr/bin/env python3
"""print_artifact_id.py <artifacts.json> <artifact-name>

Prints the numeric id of the named artifact, or an empty line if there is none.

## Why this is a file and not `python3 -c`

THIRD instance of the same trap in this repository, and the two previous ones are
documented in `ios-native.yml`:

    e: android-native.yml -- the SD fetch step
       File "<string>", line 2
       import json,sys
       IndentationError: unexpected indent
       ##[error]Process completed with exit code 1.

The step wanted this:

    SD_ID=$(python3 -c '
        import json,sys
        d=json.load(open(sys.argv[1]))
        print(next((str(a["id"]) for a in d.get("artifacts",[])
                    if a["name"]==sys.argv[2]), ""))' "$LIST" "$WANT")

Inside a YAML `run: |` block the block's own indentation is stripped, and what
reaches the shell is a quoted string whose first line is empty and whose second is
indented. `python3 -c` sees a program that starts with a newline and then an
indented line, and that is an IndentationError before it executes a statement.

The earlier instance, in `ios-native.yml`, put a body line at column 0, which
ENDED the block scalar and made the remainder parse as YAML mapping keys:

    yaml.scanner.ScannerError: while scanning a simple key

Both are the same lesson and both are cheap to avoid: **a multi-line program
inside a YAML block scalar is a file, not an argument.** `pick_simulator_udid.py`
is the precedent in this repository, and this is the same shape of problem.

## The empty-line contract

Prints NOTHING followed by a newline when the name is absent, so the caller can
use it as:

    ID=$(python3 print_artifact_id.py "$LIST" "$WANT")
    if [ -z "$ID" ]; then ... fi

Exits 0 in every case, including "not found" and "the file is not JSON" -- because
the caller has to be able to print its own three-cause diagnostic, and a non-zero
exit under `set -euo pipefail` would abort the step before it printed anything.
That is a real trap here: the first version DID exit non-zero on a missing name,
and `ID=$(...)` is an assignment, so `set -e` killed the step on the assignment
rather than at the `if` that was written to handle it.
"""

import json
import sys


def list_artifacts(path, prefix):
    """Print the artifact list, for the three-cause diagnostic.

    Sorted by name, because the diagnostic's whole job is to let a reader tell
    "built under another name" from "not built at all", and an unsorted list makes
    that a hunt. `prefix` is the annotation to put on each line, so the caller
    can emit `::error::` annotations from a plain script.
    """
    try:
        with open(path) as fh:
            data = json.load(fh)
    except Exception as e:  # noqa: BLE001
        print("%scould not read %s as the artifacts list: %s" % (prefix, path, e),
              file=sys.stderr)
        return
    artifacts = data.get("artifacts", []) if isinstance(data, dict) else []
    if not artifacts:
        print("%s(no artifacts at all in this run)" % prefix)
        return
    for a in sorted(artifacts, key=lambda x: x.get("name", "")):
        print("%s    %-34s %10s bytes  expired=%s"
              % (prefix, a.get("name", ""), a.get("size_in_bytes", "?"), a.get("expired")))


def main(argv):
    if len(argv) == 3 and argv[1] == "--list":
        list_artifacts(argv[2], "::error::")
        return 0
    if len(argv) != 3:
        print("usage: print_artifact_id.py [--list] <artifacts.json> [name]", file=sys.stderr)
        return 0
    path, want = argv[1], argv[2]
    try:
        with open(path) as fh:
            data = json.load(fh)
    except Exception as e:  # noqa: BLE001 - deliberately broad, see the docstring
        # Say what happened on stderr and print an empty id, so the caller's own
        # diagnostic runs and the reader learns the fetch was malformed rather
        # than that the artifact is absent.
        print("could not read %s as the artifacts list: %s" % (path, e), file=sys.stderr)
        print("")
        return 0

    artifacts = data.get("artifacts", []) if isinstance(data, dict) else []
    for a in artifacts:
        if a.get("name") == want:
            print(str(a.get("id", "")))
            return 0
    print("")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
