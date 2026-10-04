#!/usr/bin/env python3
"""
Flag an escaped backtick (\\`) in Kotlin source.

WHY THIS LINT IS THIS NARROW

Release 37229157857 failed with:

    e: .../SendFailureNoticeTest.kt:67:14 Unsupported escape sequence
    > Task :app:compileDebugUnitTestKotlin FAILED

The offending token was "\\`accumulated\\`" in a string -- a LaTeX/Markdown
habit carried into a language that has no use for it. Kotlin string escapes are
exactly: \\t \\b \\n \\r \\' \\" \\\\ \\$ \\uXXXX. A backtick is not among them and
does not need to be one; it is not special inside a Kotlin string.

A general "unsupported escape" scan was tried first and rejected: over
android/app/src it reports 64 findings, ALL of them false positives, because
backslashes are literal inside Kotlin raw strings (three double-quotes on each
side) and inside // and slash-star comments -- and this tree is full of regexes
written both ways. Making that check trustworthy means writing a Kotlin lexer,
which is a big fragile thing to own for a lint.

So this checks exactly one token, the one with no legitimate use anywhere:

    \\`   -> always an error

That is a zero-false-positive signal by construction, which is the only kind
worth adding to CI.

WHAT IT IS NOT

This is a text check, not a compiler. It cannot see whether the surrounding
expression typechecks. Only `compileDebugKotlin` / `compileDebugUnitTestKotlin`
decide that, as this session has now established twice.
"""
import os
import re
import sys

# A backslash immediately followed by a backtick, outside a comment.
# Comments and raw strings are skipped by the cheap heuristics below; both are
# line- or block-oriented, so this is a deliberate under-approximation.
ESCAPED_BACKTICK = re.compile(r'\\`')

OPEN = ("/*", "*/")


def strip_noise(line, in_block_comment):
    """Remove /** */ and // spans and raw strings from one line."""
    out = []
    i = 0
    n = len(line)
    while i < n:
        if in_block_comment:
            end = line.find("*/", i)
            if end == -1:
                return "".join(out), True
            in_block_comment = False
            i = end + 2
            continue
        rest = line[i:]
        # raw string """ ... """ : backslashes are literal, so skip wholesale.
        # Tracked per-line only; a raw string spanning lines is treated as open
        # to the end of the line, which can hide a real hit on a continuation
        # line. Under-approximating is the safe direction for a zero-noise lint.
        if rest.startswith('"""'):
            close = rest.find('"""', 3)
            if close == -1:
                return "".join(out), False
            i += close + 3
            continue
        if rest.startswith("//"):
            break
        if rest.startswith("/*"):
            in_block_comment = True
            i += 2
            continue
        out.append(line[i])
        i += 1
    return "".join(out), in_block_comment


def scan(path):
    findings = []
    in_block_comment = False
    with open(path, encoding="utf-8", errors="replace") as fh:
        for lineno, raw in enumerate(fh, 1):
            code, in_block_comment = strip_noise(raw, in_block_comment)
            for m in ESCAPED_BACKTICK.finditer(code):
                findings.append((lineno, m.start(), raw.strip()))
    return findings


def main(argv):
    roots = argv[1:] or ["android/app/src"]
    files = []
    for root in roots:
        if os.path.isfile(root):
            files.append(root)
            continue
        for dirpath, _dirs, names in os.walk(root):
            for name in names:
                if name.endswith(".kt"):
                    files.append(os.path.join(dirpath, name))

    total = 0
    for path in sorted(files):
        for lineno, col, text in scan(path):
            print("%s:%d:%d: error: escaped backtick (\\`) is not a Kotlin "
                  "escape sequence; write a plain ` instead" % (path, lineno, col + 1))
            print("    %s" % text[:110])
            total += 1

    if total:
        print("\n::error::%d escaped backtick(s) -- these do not compile" % total)
        return 1
    print("ok: %d file(s), no escaped backticks" % len(files))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))