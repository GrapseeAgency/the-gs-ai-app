#!/usr/bin/env python3
"""Print the id of the first artifact in a run whose name matches a suffix, or nothing.

    $ curl -fsS ".../runs/$RUN/artifacts" | python3 pick_artifact_id.py llamacpp arm64-v8a
    11074424923

WHY A SCRIPT, AND WHY IT IS A SCRIPT THIS TIME SPECIFICALLY

This replaced two `python3 -c` one-liners, both of which were broken, and the
second reason is new:

  1. Both were single-quoted, so `$ABI` was never expanded by the shell and
     python compared the artifact name against the literal string
     "llamacpp-$ABI". The arm64 device run then failed with

         FAIL: the llamacpp-arm64-v8a artifact is not in run 36662206872.
         Available:
           llamacpp-x86 19522105 bytes
           llamacpp-armeabi-v7a 19285754 bytes
           llamacpp-arm64-v8a 20634675 bytes
           llamacpp-x86_64 20348505 bytes

     -- with the artifact it claimed was missing printed, in that same step, as
     available. The "Available" list is what made it diagnosable in one read.

  2. Making that a multi-line single-quoted string inside `$( ... | ... )` broke
     the shell outright, and `bash -n` on every `run:` block caught it before
     dispatch:

         bash: line 46: syntax error near unexpected token `|'
         while looking for matching `)'

     A one-liner cannot hold the python at all comfortably; a multi-line one does
     not fit inside `$( ) |`. A file has neither constraint, which is the third
     time in this repository that a script file has been the answer.

  3. And a python body inside a YAML `run: |` block must be indented with the
     block; a line at column 0 ends the block scalar and the rest of the file
     parses as YAML mapping keys.

Prints nothing, and exits 0, when there is no match, so the caller decides
whether "absent" is fatal. The caller does decide that -- an emulator of one ABI
cannot load another ABI's .so, so a missing .so is a build failure and a missing
llama.cpp means the app cannot generate at all.
"""
import json
import sys


def main() -> int:
    if len(sys.argv) != 3:
        sys.exit("usage: pick_artifact_id.py <prefix> <abi>   (reads the "
                 "artifacts JSON on stdin)")

    prefix, abi = sys.argv[1], sys.argv[2]
    want = "%s-%s" % (prefix, abi)

    try:
        data = json.load(sys.stdin)
    except ValueError as exc:
        sys.exit("cannot read the artifacts JSON: %s" % exc)

    artifacts = data.get("artifacts")
    if not isinstance(artifacts, list):
        sys.exit("no artifacts list in that JSON; is it a run's artifacts URL?")

    for a in artifacts:
        if a.get("name") == want:
            print(a.get("id", ""))
            return 0

    # No match. Say so on stderr and print nothing, so the caller's own message
    # is the one that names the run -- but do not go quiet, because the failure
    # this replaces was a claim of absence that was false.
    print("pick_artifact_id: no artifact named %r. Available: %s"
          % (want, ", ".join(sorted(a.get("name", "?") for a in artifacts))),
          file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())