#!/usr/bin/env python3
"""Find bool-returning functions whose result is consumed as an ABI error code.

The failure this catches
------------------------
``write_png`` returns ``bool``: ``true`` means success. The diffusion path in
``gs_sd_wrapper.cpp`` used to read it as an int error code::

    const int rc = write_png(img.data, img.width, img.height, img.channel, out);
    ...
    if (rc != GS_OK) return rc;

``true`` is 1, ``GS_OK`` is 0, so ``1 != 0`` is true and the function returned
**the boolean's success value as an error code**. On a real device that meant
diffusion ran, the encoder wrote a correct 512x512 PNG, and the run reported::

    GsNative.sdGenerate: gs_sd_generate(512x512, 1 steps) -> 1: no reason recorded

Note that ``1`` is not a code in the ABI at all -- every code is 0 or negative.
That is the tell: a return value outside the ABI's domain is a convention bug, not
a generation failure.

Why it survived so long
-----------------------
It is invisible to review and to the compiler -- both forms compile, and only one
is correct at runtime. The 18-symbol export gate cannot see it. And it survived
because the whole ``#if defined(GS_SD_HAVE_SDCPP)`` block had never been handed
to a compiler until this session.

The three rules, and the false positive each one avoids
------------------------------------------------------
  1. assigned to an int-typed variable        -> the bug
  2. compared against an ABI code             -> `if (write_png(...) != GS_OK)`
  3. returned directly, unmapped              -> `return write_png(...)`

Rule 3 must NOT fire on a ternary that maps bool to a code, because this is the
CORRECT usage and it exists in the same file, twelve lines away::

    return write_png(pixels, w, h, channels, path) ? GS_OK : GS_ERR_IO;

An earlier draft of this rule flagged that line, which is the difference between a
lint that reports a defect and one that gets switched off.

Usage: check_error_code_conventions.py <file.cpp|file.h> [...]
Exit: 0 clean, 1 with a report per finding, 2 on usage error.
"""

import os
import re
import sys

BOOL_DECL = re.compile(r"^\s*(?:static\s+|inline\s+|extern\s+)*bool\s+(\w+)\s*\(")
CODES = r"GS_OK|GS_ERR_[A-Z_]+"


def bool_returners(src):
    return {m.group(1) for line in src.split("\n") if (m := BOOL_DECL.match(line))}


def findings(src, names):
    out = []
    for lineno, line in enumerate(src.split("\n"), 1):
        stripped = line.strip()
        if stripped.startswith("//") or stripped.startswith("*") or stripped.startswith("/*"):
            continue
        for name in sorted(names):
            if re.search(r"\b(?:const\s+)?(?:int|int32_t|auto)\s+\w+\s*=\s*%s\s*\(" % name, line):
                out.append((lineno, name, "assigned to an int-typed variable", stripped))
            if re.search(r"%s\s*\([^;]*\)\s*(?:!=|==)\s*(?:%s)" % (name, CODES), line):
                out.append((lineno, name, "compared against an ABI code", stripped))
            m = re.search(r"return\s+%s\s*\((.*)$" % name, stripped)
            if m and not re.search(r"\?.*(?:%s)" % CODES, m.group(1)):
                out.append((lineno, name, "returned directly, unmapped", stripped))
    return out


def main(argv):
    if len(argv) < 2:
        sys.exit(__doc__)
    problems = 0
    scanned = 0
    for path in argv[1:]:
        try:
            src = open(path, errors="replace").read()
        except OSError as exc:
            print("::error::%s: %s" % (path, exc))
            problems += 1
            continue
        names = bool_returners(src)
        if not names:
            continue
        scanned += 1
        for lineno, name, why, text in findings(src, names):
            problems += 1
            print("::error::%s:%d  %s() is declared `bool` and its result is %s"
                  % (os.path.basename(path), lineno, name, why))
            print("::error::  %s" % text)
            print("::error::  true is 1 and GS_OK is 0, so this inverts success "
                  "into failure and returns a value that is not in the ABI. Map it: "
                  "write_png(...) ? GS_OK : GS_ERR_IO")
    if problems:
        print("FAIL: %d finding(s) in %d file(s) with bool-returning functions"
              % (problems, scanned))
        return 1
    print("ok: %d file(s) with bool-returning functions, no ABI-convention misuse"
          % scanned)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))