#!/usr/bin/env python3
"""Find a quantised model FILENAME written as a literal in a test source.

The failure this catches
------------------------
A test that hardcodes which quantisation it expects, while the pipeline that
provisions the model chooses the quant somewhere else. The two drift, the test
stops finding a model that is present, and it SKIPS.

That last part is why this is worth a lint rather than a code review note. A skip
is the one result a reader cannot distinguish from success:

    ios/App/Tests/GsNativeTests.swift
    ios/App/Tests/GsChatUiTests.swift
        dir.appendingPathComponent("qwen2.5-0.5b-instruct-q4_k_m.gguf")

    .github/workflows/ios-native.yml, "Provision the model and the OCR fixture"
        GGUF="$WORK/qwen2.5-0.5b-instruct-q4_0.gguf"
        cp "$GGUF" "$SUPPORT/qwen2.5-0.5b-instruct-q4_0.gguf"

The model was provisioned into Application Support and `deviceModel()` returned
`nil`, so every test gated on it skipped reporting **"no model provisioned"** --
which reads as a precondition that was not met, and is indistinguishable from one.
A wrong assertion contradicts the evidence; a stale literal here removes it.

This is the same defect already found and fixed in three Android test files, where
the filename now comes from `ModelCatalog.MODEL_0_5B.id`. iOS has no catalogue, so
the fix there is to glob the family (`qwen2.5-0.5b-instruct-`) and take whatever was
provisioned, deterministically. This lint stops the literal coming back.

Comments are exempt, and deliberately so
----------------------------------------
The fix documents the old literal in a doc comment, because knowing what it WAS is
how a reader understands why the code globs now. Masking comments is the same
lesson as `check_kt_format_specs.py`: a lint that matches its own documentation
cries wolf on the fix, and a lint people disable protects nothing.

What is flagged
---------------
A quantised GGUF filename appearing in **code** -- a string literal on a
non-comment line. A partial family prefix (`qwen2.5-0.5b-instruct-`) is fine and is
what the fix uses, so only a name carrying an explicit quant (`q4_0`, `q4_k_m`,
`Q4_K_M`, ...) is a finding.

Usage: check_model_filename_literals.py <file.swift|file.kt> [...]
Exit: 0 clean, 1 with a report, 2 on usage error.
"""

import os
import re
import sys

# a quant token: q4_0 / q4_k_m / Q4_K_M / q5_k_m / q8_0 ...
QUANT = re.compile(r"[-_.]q(?:2|3|4|5|6|8)_(?:0|k_m|kb|1)_?", re.IGNORECASE)
GGUF_NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]*?\.gguf")


def mask_comments(line):
    """Blank a // line comment and a /* */ block tail. Returns (code, in_block_state)."""
    out, i, quote = [], 0, None
    while i < len(line):
        ch = line[i]
        if quote:
            out.append(ch)
            if ch == "\\":
                if i + 1 < len(line):
                    out.append(line[i + 1])
                i += 2
                continue
            if ch == quote:
                quote = None
            i += 1
            continue
        if ch in "\"'":
            quote = ch
            out.append(ch)
            i += 1
            continue
        if ch == "/" and i + 1 < len(line) and line[i + 1] == "/":
            break
        if ch == "/" and i + 1 < len(line) and line[i + 1] == "*":
            break
        out.append(ch)
        i += 1
    return "".join(out)


def main(argv):
    if len(argv) < 2:
        sys.exit(__doc__)
    problems = 0
    checked = 0
    for path in argv[1:]:
        try:
            lines = open(path, errors="replace").read().split("\n")
        except OSError as exc:
            print("::error::%s: %s" % (path, exc))
            problems += 1
            continue
        checked += 1
        in_block = False
        for lineno, raw in enumerate(lines, 1):
            code = raw
            if in_block:
                if "*/" in raw:
                    in_block = False
                continue
            code = mask_comments(raw)
            if "/*" in code and "*/" not in code.split("/*", 1)[1]:
                in_block = True
            for name in GGUF_NAME.findall(code):
                if not QUANT.search(name):
                    continue          # a family prefix is the fixed form
                problems += 1
                print("::error::%s:%d  %s" % (os.path.basename(path), lineno, name))
                print("::error::  a quantised GGUF filename written as a LITERAL. The "
                      "pipeline that provisions the model chooses the quant "
                      "separately, and when the two disagree this test SKIPS rather "
                      "than fails -- reporting \"no model provisioned\" for a model "
                      "that is present.")
                print("::error::  Fix: read the name from the catalogue "
                      "(ModelCatalog on Android) or glob the family "
                      "(`qwen2.5-0.5b-instruct-*`) and take what was provisioned, "
                      "sorted for determinism.")
    if problems:
        print("FAIL: %d hardcoded quantised filename(s) in %d file(s)" % (problems, checked))
        return 1
    print("ok: %d file(s), no quantised GGUF filename written as a literal" % checked)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
