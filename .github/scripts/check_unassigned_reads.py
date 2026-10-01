#!/usr/bin/env python3
"""check_unassigned_reads.py -- find shell variables that are READ but never assigned.

WHY THIS EXISTS

Run 36822191969 of the `app + XCTests (iOS simulator)` job reported:

    ::error::the reader returned nothing for the device slice.

while the repository's own symbol counter, same reader and same flags, printed 10.
The real cause was that a line assigning `NAMES` had been deleted by an earlier
edit, so `$NAMES` was empty and every count downstream was counting nothing.

`bash -n` cannot see that. The block still parsed, still linted clean, and still
read correctly. A deleted assignment is not a syntax error; it is an empty string
that every downstream check then measures and finds nothing in -- which is the
shape of a passing check that proves nothing.

It is the third distinct class this session that only a purpose-built check can
see, alongside the continuation-comment and early-exiting-consumer lints, and the
fourth bug it would have caught had it existed:

    printf ... | grep -q ... || MISSING="..."      (check_pipefail_traps.py)
    N=$(... | awk '{print $2; exit}')              (pipefail + set -e on an assignment)
    [ "$NM_LINES" -lt 1 ]                          (a numeric test that fails OPEN)

WHAT IT DOES

For every `run: |` block in every workflow, collect the assignments
(`VAR=`, `for VAR in`, `read VAR`, `while ... VAR`, `printf -v VAR`) and the reads
(`$VAR`, `${VAR}`), then report any read whose name is never assigned in that
block and is not in the known-environment list.

WHY A TEXT SCAN AND NOT A PARSER

No `yaml` module, same as the other lints here: the runner must not be able to fail
this check for a missing dependency. The `run: |` blocks are located the same way
the other two lints locate them, so all three agree about what a block is.

The scan is deliberately conservative about what counts as an assignment, and
conservative in the direction that produces FEWER findings. A false finding costs a
run; a missed one costs the thing this exists to protect.

EXIT STATUS

0 when nothing is found, 1 when something is, 2 when the lint itself is misused.
"""

import os
import re
import sys

WF_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
    ".github", "workflows",
)

# Names that exist without being assigned in the block.
KNOWN = {
    # Provided by the runner, and by `env:` at workflow or job level.
    "GITHUB_WORKSPACE", "GITHUB_ENV", "GITHUB_OUTPUT", "GITHUB_STEP_SUMMARY",
    "GITHUB_SHA", "GITHUB_REF", "GITHUB_HEAD_REF", "GITHUB_BASE_REF",
    "GITHUB_REPOSITORY", "GITHUB_RUN_ID", "GITHUB_RUN_NUMBER", "GITHUB_JOB",
    "GITHUB_ACTOR", "GITHUB_API_URL", "GITHUB_SERVER_URL", "GITHUB_EVENT_NAME",
    "GITHUB_EVENT_PATH", "GITHUB_WORKFLOW", "GITHUB_ACTION", "GITHUB_ACTIONS",
    "RUNNER_OS", "RUNNER_ARCH", "RUNNER_TEMP", "RUNNER_TOOL_CACHE",
    "RUNNER_DEBUG", "CI", "GITHUB_PATH",
    # Present in every shell.
    "HOME", "PATH", "PWD", "OLDPWD", "USER", "SHELL", "TMPDIR", "LANG", "LC_ALL",
    # Set by GitHub on the hosted images, not by anything in these files.
    "ANDROID_HOME", "ANDROID_NDK_ROOT", "ANDROID_SDK_ROOT", "JAVA_HOME",
}

# Provided by the runner images rather than by a workflow, documented as such
# rather than left to look like an oversight.
RUNNER_IMAGE_VARS = {
    # `ANDROID_NDK_HOME` is exported by GitHub's ubuntu images, alongside
    # ANDROID_HOME. It is what android-deps.yml and android-native.yml read for
    # the toolchain file, and it is NOT declared in any of these workflows --
    # which is correct, because declaring it would be a guess about the image.
    "ANDROID_NDK_HOME",
}

# Shell keywords and specials that are not variable names.
NOT_VARS = {
    "?", "!", "#", "$", "*", "@", "-", "0", "_",
}

ASSIGN_RES = [
    # NAME= and NAME+= at the start of a word.
    re.compile(r'(?:^|[;\s(])(\w+)=(?![=])'),
    re.compile(r'(?:^|[;\s(])export\s+(\w+)=(?![=])'),
    # for V in ...;  /  for V
    re.compile(r'\bfor\s+(\w+)\s+in\b'),
    # read V  /  read -r V
    re.compile(r'\bread\s+(?:-\w+\s+)*(\w+)'),
    # while read V
    re.compile(r'\bwhile\s+read\s+(?:-\w+\s+)*(\w+)'),
    # printf -v V
    re.compile(r'\bprintf\s+-v\s+(\w+)'),
    # local V / declare V, without an immediate value.
    re.compile(r'\b(?:local|declare|typeset)\s+(?:-\w+\s+)*(\w+)(?!\s*=)'),
]

# $VAR and ${VAR}
READ_RES = [
    re.compile(r'\$\{([A-Za-z_]\w*)\}'),
    re.compile(r'\$([A-Za-z_]\w*)'),
]


def strip_noise(line):
    """Remove comments and single/double-quoted string bodies.

    A `$` inside a string literal is usually an awk program, and `K="v"NAME` must
    not read as an assignment of NAME. This is a text scan, so it cannot parse
    quoting perfectly; it errs toward removing more, which produces fewer
    findings.
    """
    out = []
    i = 0
    n = len(line)
    quote = None
    while i < n:
        c = line[i]
        if quote == "'":
            if c == "'":
                quote = None
            i += 1
            continue
        if quote == '"':
            if c == '\\' and i + 1 < n:
                i += 2
                continue
            if c == '"':
                quote = None
            # A $VAR inside double quotes IS a real read, so keep it.
            out.append(c)
            i += 1
            continue
        if c == '#' and (not out or out[-1] in ' \t'):
            break
        if c in ("'", '"'):
            quote = c
            i += 1
            continue
        if c == '\\' and i + 1 < n:
            out.append(line[i + 1])
            i += 2
            continue
        out.append(c)
        i += 1
    return ''.join(out)


def run_blocks(text):
    """Yield (block_number, first_line_index, [lines]) for each `run: |` block."""
    lines = text.split('\n')
    i = 0
    block_no = 0
    while i < len(lines):
        m = re.match(r'^(\s*)run:\s*\|\s*$', lines[i])
        if not m:
            i += 1
            continue
        block_no += 1
        indent = len(m.group(1))
        # The block's own indentation is the run: key's column plus at least one.
        start = i
        i += 1
        body = []
        while i < len(lines):
            ln = lines[i]
            if ln.strip() == '':
                body.append('')
                i += 1
                continue
            cur = len(ln) - len(ln.lstrip(' '))
            if cur <= indent:
                break
            body.append(ln[indent + 2:] if len(ln) > indent + 2 else ln.strip())
            i += 1
        yield block_no, start, body


def step_names(text):
    """Map a line index to the nearest preceding `- name:` for reporting."""
    names = []
    for ln in text.split('\n'):
        m = re.match(r'^\s*-\s*name:\s*(.+?)\s*$', ln)
        names.append(m.group(1).strip('"\'') if m else None)
    return names


def declared_keys(text):
    """Every SCREAMING_CASE key anywhere in the file.

    `env:` blocks, `workflow_dispatch.inputs`, job-level `env:` and matrix keys are
    all spelled the same way in YAML, and a text scan cannot tell them apart from
    each other -- which is fine, because none of them is a shell assignment and all
    of them legitimately provide the variable.

    Deliberately over-broad. A variable named like a YAML key somewhere in the file
    will be treated as provided, which can only produce FEWER findings, and a false
    finding costs a 25-minute run while a missed one costs the thing this exists to
    protect.
    """
    keys = set()
    for m in re.finditer(r'^\s*([A-Z_][A-Z0-9_]*):', text, re.M):
        keys.add(m.group(1))
    # AND THE CROSS-STEP MECHANISM, which is a real one and the reason a plain
    # per-block scan is not enough on its own.
    #
    # `echo "FOO=bar" >> "$GITHUB_ENV"` in one step makes $FOO available in every
    # LATER step of the same job. ios-native.yml sets IOS_DESTINATION exactly that
    # way and reads it three steps later. A per-block scan sees an assignment in
    # one block and a read in another and reports a variable that is in fact
    # provided, so the write is recognised wherever it appears in the file.
    for m in re.finditer(r'([A-Z_][A-Z0-9_]*)=[^\n]*>>\s*"?\$GITHUB_ENV', text):
        keys.add(m.group(1))
    for m in re.finditer(r'echo\s+"?([A-Z_][A-Z0-9_]*)=', text):
        keys.add(m.group(1))
    return keys | RUNNER_IMAGE_VARS


def analyse(path):
    with open(path, encoding='utf-8') as fh:
        text = fh.read()
    names = step_names(text)
    provided = KNOWN | declared_keys(text)
    findings = []
    for block_no, start, body in run_blocks(text):
        assigned = set(provided)
        reads = []
        for offset, raw in enumerate(body):
            # A block line number, relative to the run block, 1-based.
            rel = offset + 1
            code = strip_noise(raw)
            for rx in ASSIGN_RES:
                for m in rx.finditer(code):
                    assigned.add(m.group(1))
            for rx in READ_RES:
                for m in rx.finditer(code):
                    nm = m.group(1)
                    if nm in NOT_VARS:
                        continue
                    reads.append((nm, rel))
        step = None
        for k in range(start, -1, -1):
            if names[k]:
                step = names[k]
                break
        seen = set()
        for nm, rel in reads:
            if nm in assigned:
                continue
            key = (nm, rel)
            if key in seen:
                continue
            seen.add(key)
            findings.append((os.path.basename(path), step, block_no, rel, nm))
    return findings


def main(argv):
    if argv and argv[0] in ('-h', '--help'):
        sys.stdout.write(__doc__)
        return 0
    if not os.path.isdir(WF_DIR):
        sys.stdout.write("no workflow directory at %s\n" % WF_DIR)
        return 2
    files = sorted(f for f in os.listdir(WF_DIR)
                   if f.endswith(('.yml', '.yaml')))
    if not files:
        sys.stdout.write("no workflow files found under %s\n" % WF_DIR)
        return 2

    all_findings = []
    for f in files:
        try:
            all_findings.extend(analyse(os.path.join(WF_DIR, f)))
        except (OSError, UnicodeDecodeError) as exc:
            sys.stdout.write("could not read %s: %s\n" % (f, exc))
            return 2

    if all_findings:
        for path, step, block_no, rel, nm in all_findings:
            sys.stdout.write(
                "FAIL  %s  step=%r  run-block %d line %d  reads $%s\n"
                % (path, step, block_no, rel, nm))
        sys.stdout.write("""
%d variable(s) are read but never assigned in their run block.

    NAMES=$("$LLVM_NM" --defined-only --extern-only "$ARCHIVE")

`bash -n` cannot see this. The block still parses, still lints clean, and every
downstream check then measures an EMPTY STRING and finds nothing in it -- which is
the shape of a passing check that proves nothing. It cost run 36822191969 of the
`app + XCTests (iOS simulator)` job: the reader was reported as returning nothing
while the repository's own counter, same reader and same flags, printed 10.

Two of the three instances in this repository were the same mistake twice: a line
edited out of the middle of a block that used to be its only assignment.

Fix by assigning the variable. If the read is deliberate -- an unset variable is
the thing being tested -- write `${VAR-}` or `${VAR:-}` so the intent is visible
to the next reader, and to this check.
""" % len(all_findings))
        return 1

    sys.stdout.write(
        "OK: %d workflow file(s), every variable read in a run block is assigned "
        "there\n" % len(files))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
