#!/usr/bin/env python3
"""Find Kotlin/Java format-string calls whose specifier count != argument count.

The failure this catches
------------------------
``"%.1f sd=%.1f".format(stats.mean)`` compiles, reviews cleanly, and throws
``java.util.MissingFormatArgumentException: Format specifier '%.1f'`` at RUNTIME.
It did exactly that in ``DeviceVerificationTest.kt`` and took out a test run that
had already generated a real 512x512 PNG::

    SD0 backend: sd.cpp:dlopen
    SD0 generated ... -> 787072 bytes in 186932ms
    java.util.MissingFormatArgumentException: Format specifier '%.1f'
    (android-device 37161479688)

What made it expensive rather than merely wrong is WHERE it sat: between the
assertions that proved the PNG decoded and the three assertions that judge whether
the image is blank. A diagnostic that can throw, placed immediately above the
checks it precedes, turns a green run red AND deletes the evidence needed to judge
the result.

Comments are stripped before scanning, because a lint that matches its own
documentation is a lint that cries wolf on the fix.

Usage: check_kt_format_specs.py <file.kt> [<file.kt> ...]
Exit: 0 clean, 1 with a report per mismatch, 2 on usage error.
"""

import os
import re
import sys

# A conversion specifier: flags, width, precision, then a conversion character.
#
# `%%` IS MATCHED AS A UNIT, because space is a legitimate flag character and
# without this the literal "%% of frozen blocks kept their instances" is scanned as
#
#     %5.1f      a real specifier
#     % o        ...the SECOND percent, then the space as a flag, then "o"
#
# `-- a specifier invented out of the English words "percent of". It reported two
# false positives in ParserStreamingBenchmark.kt, both of which were correct code.
# Alternation means the scan consumes `%%` before it can start a specifier at the
# second percent, and `%%` entries are then discarded.
SPEC = re.compile(r"%(?:%|[-+ #0-9.]*[a-zA-Z])")

# PAIR THE LITERAL WITH ITS OWN .format().
#
# The first version used
#     "((?:[^"\\]|\\.)*)"\s*\.format\(([^)]*)\)   with re.S
# and reported FIVE false positives in ParserStreamingBenchmark.kt, turning
# android-app.yml red on a commit that was correct. Two reasons, both structural:
#
#   1. `[^)]*` for the arguments stops at the first ')', and these calls put their
#      arguments on the FOLLOWING lines, so a 6-argument call counted as 0 or 1.
#   2. `re.S` let the literal body span lines, so a literal from one call paired
#      with the `.format(` of another. It reported "%-58s %10d %10s ..." (line 389)
#      at line 362, and paired "%5.1f%% of frozen blocks" with a "% o" from a
#      different line -- inventing a specifier out of the words "% of".
#
# So: the gap between the closing quote and `.format(` must be WHITESPACE ONLY,
# which is what makes the pairing unambiguous, and the arguments are extracted by
# counting parens rather than by stopping at the first one.
LITERAL = re.compile(r'"((?:[^"\\]|\\.)*)"[ \t\r\n]*\.format\(')


def split_args(text, start):
    """Return (args_text, end_index) for the argument list beginning at `start`.

    Counts parentheses so a nested call, or arguments on following lines, are
    handled. A string literal inside the arguments may contain parens, so quotes
    are tracked too.
    """
    depth = 0
    i = start
    quote = None
    while i < len(text):
        ch = text[i]
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
        elif ch in "([{":
            depth += 1
        elif ch in ")]}":
            if depth == 0:
                return text[start:i], i
            depth -= 1
        i += 1
    return text[start:], len(text)


def find_calls(src):
    """(lineno, literal, args_text) for every `"...".format(...)` call."""
    out = []
    for m in LITERAL.finditer(src):
        args, _end = split_args(src, m.end())
        out.append((src[: m.start()].count("\n") + 1, m.group(1), args))
    return out


def strip_comments(src):
    """Remove // and /* */ comments while leaving string literals intact."""
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    out = []
    for line in src.split("\n"):
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
            if ch == '"':
                quote = '"'
                res.append(ch)
                i += 1
                continue
            if ch == "/" and i + 1 < len(line) and line[i + 1] == "/":
                break
            res.append(ch)
            i += 1
        out.append("".join(res))
    return "\n".join(out)


def split_top_level(args):
    """Split on commas that are not inside (), [], {} or a string."""
    parts, buf, depth, quote = [], [], 0, None
    for ch in args:
        if quote:
            buf.append(ch)
            if ch == "\\":
                continue
            if ch == quote:
                quote = None
            continue
        if ch in "\"'":
            quote = ch
            buf.append(ch)
            continue
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append("".join(buf))
            buf = []
            continue
        buf.append(ch)
    parts.append("".join(buf))
    return parts


def main(argv):
    if len(argv) < 2:
        sys.exit(__doc__)
    problems = 0
    checked = 0
    for path in argv[1:]:
        src = strip_comments(open(path).read())
        for line, literal, raw_args in find_calls(src):
            args = raw_args.strip()
            specs = [s for s in SPEC.findall(literal) if s != "%%"]
            if not specs:
                continue
            checked += 1
            nargs = 0 if args == "" else len([a for a in split_top_level(args) if a.strip()])
            if len(specs) != nargs:
                problems += 1
                print("::error::%s:%d  %d specifier(s) %s vs %d argument(s)"
                      % (os.path.basename(path), line, len(specs), specs, nargs))
                print("::error::  throws MissingFormatArgumentException at RUNTIME, "
                      "not at compile time. Use one .format() call for the whole "
                      "message.")
    if problems:
        print("FAIL: %d mismatch(es) in %d format call(s)" % (problems, checked))
        return 1
    print("ok: %d format call(s), specifier counts match argument counts" % checked)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
