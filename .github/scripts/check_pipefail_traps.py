#!/usr/bin/env python3
"""Flag pipelines whose consumer exits early, inside a `set -e` run block.

This exists because of four separate 25-minute CI runs, all the same bug in four
different clothes. GitHub executes a `run:` block as `bash -e {0}`, so a step's
own `set -uo pipefail` ADDS to an `-e` that is already on, and bash SILENTLY
suspends `set -e` for a command in an `if` CONDITION. Between those two facts,
where a pipeline appears decides whether it is harmless, whether it lies, or
whether it ends the step with no message at all.

Rule A -- a bare pipeline, or one in an assignment, ending in an early-exiting
consumer. `head`, `grep -q`, `grep -mN` and `tail -n +N` all exit as soon as they
have what they need, which closes the pipe. The producer then takes SIGPIPE and
exits 141, and under `-e` the step dies there. `-e` is NOT suspended here.

    NMERR=$(nm -gU "$OUT" || true)                 # fine
    sed 's/^/    /' "$NMERR" | head -12            # Rule A  -> "sed: stdout: Broken pipe"
                                                    #            then "exit code 1", no message
Rule B -- the same consumer inside an `if`/`while` CONDITION, where `-e` is
suspended so the step survives, but the STATUS is still wrong: `pipefail` reports
the producer's 141 rather than `grep`'s 0, so a successful match is read as a
failure.

    if ! nm -gU "$OUT" | grep -qE " T _?llama_model_load$"; then MISSING="$MISSING x"; fi
                                                 # Rule B -> reported MISSING at random
Observed, ten runs on one archive with all four symbols present:

    run  1: MISSING=' gs_llama_backend_name gs_llama_create'
    run  6: MISSING=''

`grep -q` is a `grep -m1` that also silences output, and it is the single most
common way to write this. `head` is the same thing by hand.

THE FIX IN BOTH CASES IS TO NOT LET A CONSUMER CLOSE THE PIPE:
  * bound the INPUT rather than the output -- `sed -n '1,12p' f` reads the whole
    file and prints twelve lines, so nothing is ever cut off;
  * or run the tool once, capture it, and test the capture in bash
    (`ALLSYM=$(nm ... || true)` then `case "$NAME" in "$SYM"|_"$SYM")`);
  * or, where the early exit is genuinely wanted, keep it in an `if` CONDITION
    and do not rely on `$?` -- which is what this file's rule B tells you.

A line that ends in `|| true`, or that is itself an `if`/`while`/`until`
condition, is reported as a NOTE rather than an error: `|| true` removes Rule A's
failure mode, and an `if` condition is where Rule B lives.

Usage:  python3 .github/scripts/check_pipefail_traps.py [file ...]
        no arguments checks every workflow under .github/workflows.
Exit 0 when clean, 1 on any Rule A finding, 2 if only Rule B notes remain.
"""
import re as _re
import re
import sys
import pathlib

# NO PyYAML, DELIBERATELY. Run 36750432883:
#
#     ModuleNotFoundError: No module named 'yaml'
#     ##[error]a bare pipeline ends in an early-exiting consumer (exit 1)
#     ##[error]or the lint itself failed, which is equally a stop.
#
# The first version imported yaml, which is installed on the machine this was
# written on and is NOT installed on the GitHub macOS runner. The gate did the
# right thing with a wrong answer -- it failed loudly rather than reporting
# "clean", because its own exit code distinguishes the two -- but it cost a
# twenty-five minute run to learn that a lint cannot depend on a package the
# runner might not have.
#
# So the run blocks are extracted from the raw text instead. That is possible
# because every `run:` in these workflows is a block scalar (`run: |`), whose
# body is simply every following line indented further than the key. An
# over-approximation is acceptable for a lint: reading a block it should have
# skipped produces at worst a spurious note, never a missed bare pipeline.

ROOT = pathlib.Path(__file__).resolve().parents[2]
WF_DIR = ROOT / ".github" / "workflows"

# A consumer that exits as soon as it has enough, closing the pipe.
EARLY = re.compile(
    r"\|\s*(?:"
    r"head(?:\s|$)"
    r"|grep\b[^|]*?(?:\s-q\b|\s-qE\b|\s-qi\b|\s-m[0-9])"
    r"|tail\s+-n\s*\+"
    r")"
)
# A pipeline that is exempt because something downstream absorbs the failure.
GUARDED = re.compile(r"\|\|\s*(?:true|:|\{)")
# Contexts where bash suspends `set -e`.
CONDITION = re.compile(r"^\s*(?:if|elif|while|until)\b")
ASSIGN = re.compile(r"^\s*(?:[A-Za-z_][A-Za-z0-9_]*=|\(\s*$)")
SUBST = re.compile(r"\$\(")


def run_blocks(text):
    """Yield (line_no, label, body) for every `run: |` block in the raw text.

    A block scalar's body is every following line indented further than its key,
    up to the next non-blank line at that indentation or shallower. That is YAML's
    rule and it needs no parser.

    The LABEL is the step's own `- name:`, found by walking back up from the
    `run:` key to the nearest `- name:` at the step's indentation. Without it the
    findings read `run-block at line 412`, which is locatable by grep but tells
    the reader nothing about WHICH step is about to die -- and the whole value of
    this lint is that it names the line and the step in one breath.
    """
    lines = text.split("\n")
    for i, line in enumerate(lines):
        m = _re.match(r"^(\s*)(?:-\s+)?run:\s*[|>][-+]?\s*$", line)
        if not m:
            continue
        key_indent = len(m.group(1))
        body = []
        for j in range(i + 1, len(lines)):
            nxt = lines[j]
            if not nxt.strip():
                body.append("")
                continue
            indent = len(nxt) - len(nxt.lstrip())
            if indent <= key_indent:
                break
            body.append(nxt)
        # Walk back for the step's name, stopping at the previous list item.
        label = "run-block at line %d" % (i + 1)
        for k in range(i - 1, -1, -1):
            prev = lines[k]
            if not prev.strip():
                continue
            pm = _re.match(r"^(\s*)-(?:\s+)name:\s*(.+?)\s*$", prev)
            # The list item's DASH is two columns LEFT of its own keys, so
            #     - name: ...
            #       run: |
            # puts the dash at key_indent - 2. The first version compared the
            # dash indent against `key_indent` itself, so it never matched and
            # every finding was labelled "run-block at line N" -- locatable by
            # grep, but silent about WHICH STEP is about to die.
            dash = len(pm.group(1)) if pm else None
            if pm and dash <= key_indent:
                label = pm.group(2).strip("'\"")
                break
            dm = _re.match(r"^(\s*)-(\s|$)", prev)
            if dm:
                d = len(dm.group(1))
                if d < key_indent - 2:
                    break   # a step boundary, and this one had no name
        yield i + 1, label, "\n".join(body)


def runs_of(data):
    """Backwards-compatible shim. Kept so a caller passing a parsed document
    still works, but nothing in this file uses it and the gate never needs a
    YAML parser."""
    for jn, job in ((data or {}).get("jobs") or {}).items():
        for st in (job.get("steps") or []):
            if "run" in st:
                yield jn, st.get("name", "?"), st["run"]


# A command substitution NESTED inside a larger command: a failure inside it does
# not fail the enclosing command under -e, so the risk is an empty value rather
# than a dead step. Worth a note, not a failure.
NESTED = _re.compile(r"[=(,]\s*\$\(")


def _logical_lines(text):
    """Yield (first_lineno, joined_text) with backslash continuations merged.

    Needed because the guard is often on the NEXT line:

        lipo -info "$X" | grep -q 'arm64' \\
          || { echo "FAIL: no arm64"; exit 1; }

    Scanned line-by-line that reads as an UNGUARDED pipeline and is reported as
    fatal, when the continuation is right there. Two of the first ten findings
    were exactly that, so a lint that cries wolf on guarded code gets ignored,
    and an ignored lint is worse than none.
    """
    buf, start = "", None
    for n, line in enumerate(text.split("\n"), 1):
        if start is None:
            start = n
        if line.rstrip().endswith("\\"):
            buf += line.rstrip()[:-1] + " "
            continue
        yield start, buf + line
        buf, start = "", None
    if buf:
        yield start, buf


def scan(text, where):
    """Return (errors, notes) as lists of (lineno, source_line, why)."""
    errors, notes = [], []
    for n, logical in _logical_lines(text):
        stripped = logical.strip()
        if not stripped or stripped.lstrip().startswith("#"):
            continue
        if not EARLY.search(stripped):
            continue
        if CONDITION.match(logical):
            notes.append((n, logical, "inside an if/while condition: -e is "
                            "suspended, but pipefail reports the producer's 141, "
                            "so a successful match can read as a FAILURE"))
        elif GUARDED.search(stripped):
            notes.append((n, logical, "guarded with || true or || { ... }: the step "
                            "will not die, but the value can still be empty"))
        elif NESTED.search(stripped) and not ASSIGN.match(stripped):
            notes.append((n, logical, "nested in a larger command: a failure inside "
                            "a command substitution does not fail the enclosing "
                            "command, so the VALUE may come out empty rather than "
                            "the step dying"))
        else:
            errors.append((n, logical, "bare pipeline, unguarded: -e applies"))
    return errors, notes


def main(argv):
    files = ([pathlib.Path(a) for a in argv] if argv
             else sorted(WF_DIR.glob("*.y*ml")))
    if not files:
        print("no workflow files found under %s" % WF_DIR)
        return 0
    all_errors, all_notes = [], []
    for f in files:
        if not f.exists():
            print("missing: %s" % f)
            return 1
        text = f.read_text(encoding="utf-8")
        blocks = list(run_blocks(text))
        if not blocks:
            print("NOTE  %s: no `run: |` block found. The extractor is a text scan;"
                  " if this file uses another form the lint is not seeing it." % f.name)
        for file_line, name, body in blocks:
            errors, notes = scan(body, "%s/%s" % (f.name, name))
            # Report the line WITHIN the block, and the block's file line, so the
            # reader can find it: add the offset.
            for n, line, _why in errors:
                all_errors.append((f.name, "-", name, n, line))
            for n, line, why in notes:
                all_notes.append((f.name, "-", name, n, line, why))

    for f, jn, name, n, line, why in all_notes:
        # The step NAME and a line number RELATIVE TO THE STEP'S run BLOCK. A bare
        # "file:NN" that is really an offset into a run block sends the reader to
        # the wrong line, and a gate that cannot be located is a gate people
        # work around. `grep -n` on the step name is the locator.
        print("NOTE  %s  job=%s step=%r  run-block line %d"
              % (f, jn, name, n))
        print("      %s" % line.strip()[:110])
        print("      %s" % why)
    if all_notes:
        print("\n%d note(s). A note will not fail the build; read them anyway." % len(all_notes))

    if all_errors:
        for f, jn, name, n, line in all_errors:
            print("FAIL  %s  job=%s step=%r  run-block line %d"
                  % (f, jn, name, n))
            print("      %s" % line.strip()[:110])
        print("""
%d bare pipeline(s) end in a consumer that exits early.

    sed 's/^/  /' "$F" | head -12

GitHub runs this as `bash -e {0}`. `head` closes the pipe, the producer takes
SIGPIPE and exits 141, `-e` kills the step there, and the only evidence is

    sed: stdout: Broken pipe
    ##[error]Process completed with exit code 1.

which names neither the line nor the cause. Fix by bounding the INPUT instead of
the output -- `sed -n '1,12p' "$F"` reads the whole file and prints twelve lines,
so nothing is cut off -- or by running the tool once and testing the capture in
bash. See the module docstring for the four runs that motivated this.""" % len(all_errors))
        return 1
    if all_notes:
        return 2
    print("OK: %d workflow file(s), no bare pipeline ends in an early-exiting consumer"
          % len(files))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
