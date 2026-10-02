#!/usr/bin/env python3
"""undefined_syms.py <libgs_ffi.so> [--allow NAME ...]

Prints every symbol the shared object imports but does not define, minus an
allow-list of the C runtime and the dynamic loader. Exits 1 if anything in a
DENIED namespace is left over.

## Why this exists, and what it would have cost

Run 36975792039. `android-native` was **green on all four ABIs**, the 18-symbol
ABI gate was green, the DT_NEEDED completeness check was green, and the APK built,
installed and launched. Then 24 of 29 device tests failed with:

    java.lang.UnsatisfiedLinkError: dlopen failed: cannot locate symbol
    "ggml_sage_attn" referenced by "/data/app/.../libgs_ffi.so"

**A missing symbol is invisible to every check this repository had.** The symbol
gate counts *exports*; the DT_NEEDED check asks which whole *libraries* are
needed. Neither asks what the `.so* fails to supply for itself. The failure is a
`dlopen`, on a device, 40 minutes in.

## The specific cause, so the scope is not arbitrary

`android-native` links **two different versions of ggml** into one shared object:
llama.cpp's (from `llamacpp-<abi>`) and stable-diffusion.cpp's (from
`stablediffusion-<abi>`). Measured from the two artifacts of android-deps
36966641772, `libggml-base.a` in each:

    llama.cpp  574 ggml_* symbols
    sd.cpp     591 ggml_* symbols
    DEFINED BY BOTH, with two implementations: 574
    only in sd.cpp:     17   (incl. ggml_sage_attn)
    only in llama.cpp:   0

sd.cpp's ggml is a **strict superset**, so linking llama.cpp's copy first
satisfies every shared name, the linker never extracts sd.cpp's archive for them,
and the 17 newer symbols are left undefined.

**So `ggml_`/`llama_`/`sd_` are DENIED, not allowed.** A future ggml that adds a
symbol must add it here, and this check goes red naming it. That is the right
direction for this mistake: a new undefined symbol should cost a CI run, not a
device run.

## The bug this script had in its first version, because it is the whole point

The first `defined()` matched the `Ndx` column with a bare `\\S+`, which happily
consumed `UND`. So **every undefined symbol counted as defined**, and the check
reported:

    250 imported, 0 unresolved

on the very `.so` that could not load. A checker that reports clean on the exact
artefact it was written for is worse than no checker, and the only reason this one
is trustworthy is that it was run against that artefact and disagreed.

The fix is a NEGATIVE LOOKAHEAD on the Ndx column, plus handling for the version
suffixes `readelf -W` prints:

    1: 0000000000000000   0 FUNC  GLOBAL DEFAULT UND strlen@LIBC (2)

`@LIBC` and the trailing ` (2)` are both part of the line and the first version of
the regex dropped 150 of 400 undefined symbols by assuming the name was the last
whitespace-delimited field.
"""

import re
import subprocess
import sys

ALWAYS = frozenset((
    "libc.so", "libm.so", "libdl.so", "liblog.so", "libandroid.so",
    "libz.so", "libjnigraphics.so", "libEGL.so", "libGLESv2.so", "libGLESv3.so",
    "libOpenSLES.so", "libaaudio.so",
))
ALLOWED_PREFIXES = (
    "__cxa_", "_ZN", "_ZT", "_ZTV", "_ZTI", "_ZTS", "_ZThn", "_ZTv",
    "Java_", "lib",
)
DENIED_PREFIXES = ("ggml_", "llama_", "sd_", "gguf_")

# Num: Value Size Type Bind Vis Ndx Name[ @Version] [ (N)]
#
# Ndx is the ONLY thing that says defined vs undefined. The Value column is 0 for
# every symbol in a relocatable object and says nothing; filtering on it found
# 2 symbols where there are thousands, which is the first version's second bug.
SYM = re.compile(
    r"^\s*\d+:\s+[0-9a-f]+\s+\S+\s+(?P<type>\S+)\s+\S+\s+\S+\s+"
    r"(?P<ndx>\S+)\s+(?P<name>\S+?)"
    r"(?:@(?P<ver>\S+?))?(?:\s+\((?P<veridx>\d+)\))?\s*$"
)


def read(path):
    out = subprocess.run(
        ["readelf", "--dyn-syms", "-W", path], capture_output=True, text=True
    )
    if out.returncode != 0:
        raise SystemExit("readelf could not read %s: %s" % (path, out.stderr.strip()))
    if not out.stdout.strip():
        raise SystemExit("readelf produced no symbol table for %s" % path)
    defined, undefined = set(), []
    unparsed = 0
    for line in out.stdout.split("\n"):
        if ":" not in line:
            continue
        m = SYM.match(line)
        if not m:
            # A row with no name is not a symbol. readelf emits
            #     0: 0000000000000000  0 NOTYPE LOCAL DEFAULT UND
            # with nothing after UND, and counting that as "unparsed" would be a
            # permanent, meaningless warning.
            if re.match(r"^\s*\d+:", line) and re.search(r"UND\s*\S", line):
                unparsed += 1
            continue
        name = m.group("name")
        if not name:
            continue
        if m.group("ndx") == "UND":
            if m.group("type") not in ("FILE", "SECTION", "TLS"):
                undefined.append(name)
        elif not name.startswith("."):
            defined.add(name)
    return defined, undefined, unparsed


def main(argv):
    extra, args, verbose, i = set(), [], False, 1
    while i < len(argv):
        if argv[i] == "--allow":
            extra.add(argv[i + 1])
            i += 2
        elif argv[i] == "--verbose":
            verbose = True
            i += 1
        else:
            args.append(argv[i])
            i += 1
    if len(args) != 1:
        print("usage: undefined_syms.py <libgs_ffi.so> [--allow NAME]", file=sys.stderr)
        return 2
    path = args[0]
    try:
        have, imported, unparsed = read(path)
    except SystemExit as e:
        print("::error::%s" % e)
        return 1

    unresolved = []
    for n in imported:
        if n in have or n in extra or n in ALWAYS or n.startswith(ALLOWED_PREFIXES):
            continue
        unresolved.append(n)

    denied = [n for n in unresolved if n.startswith(DENIED_PREFIXES)]
    other = [n for n in unresolved if not n.startswith(DENIED_PREFIXES)]

    for n in sorted(denied):
        print("::error::%s imports %s and does not define it." % (path, n))
        print("::error::  A ggml/llama/sd symbol must come from an archive linked INTO")
        print("::error::  this .so. This one does not, so dlopen fails on a device with")
        print("::error::  'cannot locate symbol \"%s\"' -- and nothing else in CI notices." % n)
    # 163 lines of `memcpy`, `pthread_mutex_lock`, `_Znwm` would bury the four
    # that matter. They are EXPECTED: they are resolved at load time from the
    # DT_NEEDED libraries, so "imported and not defined here" is their normal
    # state. A count and a sample, not a wall.
    if verbose:
        for n in sorted(other):
            print("::warning::%s imports %s and does not define it" % (path, n))
    if other and not verbose:
        sample = ", ".join(sorted(other)[:6])
        print("::warning::%d further imported-but-undefined symbol(s), expected: they"
              % len(other))
        print("::warning::  come from DT_NEEDED at load time. e.g. %s" % sample)

    print("  %s" % path)
    print("  %d imported, %d defined, %d unresolved (%d denied, %d other), %d line(s) unparsed"
          % (len(imported), len(have), len(unresolved), len(denied), len(other), unparsed))
    if unparsed:
        print("::warning::%d symbol line(s) did not match the parser. A check that silently"
              % unparsed)
        print("::warning::skips lines is a check that silently skips symbols.")
    if denied:
        print("FAIL: %d symbol(s) in a denied namespace are undefined." % len(denied))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
