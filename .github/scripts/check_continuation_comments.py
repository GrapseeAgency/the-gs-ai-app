#!/usr/bin/env python3
"""Fail a `run:` block that puts a `#` comment inside a backslash continuation.

WHY THIS EXISTS, AND WHY IT IS A SCRIPT RATHER THAN A THING I REMEMBER TO CHECK.

Run 36728962757:

    .../_temp/a65912d8-....sh: line 96: -DGGML_BLAS=OFF: command not found
    ##[error]Process failed with exit code 127.

The cmake call ended with

    -DGGML_LLAMAFILE=OFF \\
    # GGML_BLAS=OFF, and this is not cosmetic. Run 36725021214:
    ...
    -DGGML_BLAS=OFF \\

and that is two commands, not one. bash reads the continued line, sees the `#`,
and treats the entire physical line as a comment -- the trailing backslash goes
with it. So the cmake invocation stopped at -DGGML_LLAMAFILE=OFF and the next
non-comment line became a command. The flag never reached cmake, and the failure
looked like a GGML_BLAS problem when it was a shell problem.

`bash -n` does NOT catch this. It parses `-DGGML_LLAMAFILE=OFF` followed by a
comment as a complete, valid command list, so the file passes every existing
check. That is the whole reason the mistake survived a commit and a CI run.

A `#` is only a comment when nothing is being continued. So the rule is
mechanical: a line whose first non-blank character is `#`, where the previous
non-blank line ends in an odd number of backslashes, is a bug.

The same class of bug also appears as a `#` comment inside `$( ... )`, which
bash parses the same way, so subshell interiors are checked too.

Usage:  python3 .github/scripts/check_continuation_comments.py [workflow.yaml ...]
        with no arguments it checks every file under .github/workflows.
Exit 0 when clean, 1 when not, and every finding is printed with its line.
"""
import sys
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
WF_DIR = ROOT / ".github" / "workflows"


def ends_with_continuation(line: str) -> bool:
    """True when `line` leaves a backslash continuation open.

    A backslash only continues if it is the LAST character, and only when it is
    not itself escaped. `\\\\` is a literal backslash, so a line ending in two
    backslashes is a complete command.
    """
    stripped = line.rstrip("\n")
    n = 0
    for ch in reversed(stripped):
        if ch == "\\":
            n += 1
        else:
            break
    return n % 2 == 1


def scan(text: str, where: str) -> list:
    """Return [(line_no, previous_line_no, text)] for every offending comment."""
    lines = text.split("\n")
    bad = []
    # Tracks whether we are inside `$( ... )` as well as inside a continuation.
    depth = 0
    for i, line in enumerate(lines):
        prev = lines[i - 1] if i > 0 else ""
        if depth > 0 and line.lstrip().startswith("#"):
            bad.append((i + 1, i, where, "inside $( ... )", line))
        elif depth == 0 and line.lstrip().startswith("#") and ends_with_continuation(prev):
            bad.append((i + 1, i, where, "after a \\ continuation", line))
        # Recompute subshell depth for the NEXT line, ignoring comment text and
        # anything inside quotes, which is enough for these files.
        if not line.lstrip().startswith("#"):
            opens = line.count("$(")
            closes = line.count(")")
            depth = max(0, depth + opens - closes)
    return bad


def main(argv):
    if argv:
        files = [pathlib.Path(a) for a in argv]
    else:
        files = sorted(WF_DIR.glob("*.y*ml")) + sorted((ROOT / ".github" / "scripts").glob("*.sh"))
    if not files:
        print("no files to check")
        return 0
    findings = []
    for f in files:
        if not f.exists():
            print("missing: %s" % f)
            return 1
        findings.extend(scan(f.read_text(encoding="utf-8"), f.name))
    for line_no, prev_no, where, why, text in findings:
        print("%s:%d: comment %s" % (where, line_no, why))
        print("    %d: %s" % (prev_no, text[:100]))
        print("    %d: %s" % (line_no, text[:100]))
    if findings:
        print("FAIL: %d comment(s) inside a continuation. bash reads the line as a "
              "comment and drops the backslash, so the command ends early."
              % len(findings))
        return 1
    print("OK: %d file(s), no comment inside a continuation" % len(files))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
