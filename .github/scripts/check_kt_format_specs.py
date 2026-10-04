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

SPEC = re.compile(r"%[-+ #0-9.]*[a-zA-Z]")
CALL = re.compile(r'"((?:[^"\\]|\\.)*)"\s*\.format\(([^)]*)\)', re.S)


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


def main(argv):
    if len(argv) < 2:
        sys.exit(__doc__)
    problems = 0
    checked = 0
    for path in argv[1:]:
        src = strip_comments(open(path).read())
        for match in CALL.finditer(src):
            literal, args = match.group(1), match.group(2).strip()
            specs = [s for s in SPEC.findall(literal) if s != "%%"]
            if not specs:
                continue
            checked += 1
            nargs = 0 if args == "" else len([a for a in args.split(",") if a.strip()])
            if len(specs) != nargs:
                problems += 1
                line = src[: match.start()].count("\n") + 1
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
