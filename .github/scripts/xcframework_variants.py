#!/usr/bin/env python3
"""Summarise the slices in an xcframework's Info.plist.

Called as:
    VARIANTS=$(plutil -extract AvailableLibraries json -o - "$XF/Info.plist" \
                | python3 xcframework_variants.py)

Prints one word per library, space separated, on stdout: `device simulator` or
just `device`. Exits non-zero if there is no simulator slice, because a
framework without one builds fine and then fails at runtime with a dyld error
about a missing architecture -- which reads like a broken build rather than a
missing slice.

WHY SupportedPlatformVariant AND NOT THE SLICE NAME

The first version of the CI check grepped the plist for `ios-simulator`, and the
runner's own plist says (run 36660102564):

    "LibraryIdentifier"        => "ios-arm64-simulator"
    "SupportedPlatform"        => "ios"
    "SupportedPlatformVariant" => "simulator"

The string "ios-simulator" does not appear anywhere in it. A simulator slice is
named after its ARCHITECTURE, so a 32-bit one would be `ios-armv7-simulator` and
an Intel-simulator build `ios-x86_64-simulator`. Grepping for a fixed substring
is therefore correct for exactly one ABI -- the one that happens to have been
built -- and wrong for every other, which is a check that reports success for
the wrong reason.

SupportedPlatformVariant is present for a simulator slice and absent for a
device slice, on every ABI, with nothing to guess.

WHY A SCRIPT AND NOT `python3 -c`

It runs inside a YAML `run: |` block. A python body there has to be indented
with the block, and a body line at column 0 -- `import json, sys`,
`print(...)` -- ENDS the block scalar and leaves the rest of the file to parse
as YAML mapping keys:

    yaml.scanner.ScannerError: while scanning a simple key
      in ".github/workflows/ios-native.yml", line 378, column 1
    could not find expected ':'

That is not hypothetical: it happened here, twice, before this file existed.
"""
import json
import sys


def main() -> int:
    try:
        libs = json.load(sys.stdin)
    except ValueError as exc:
        sys.exit("cannot read AvailableLibraries from the plist: %s" % exc)
    if not isinstance(libs, list) or not libs:
        sys.exit("AvailableLibraries is empty; this is not an xcframework plist")

    variants = []
    for lib in libs:
        # Absent means a device slice. That is the plist's convention, not a
        # guess: -create-xcframework writes the key only for a simulator slice.
        variants.append(str(lib.get("SupportedPlatformVariant") or "device"))

    print(" ".join(sorted(variants)))

    for lib in libs:
        ident = lib.get("LibraryIdentifier", "?")
        archs = ",".join(lib.get("SupportedArchitectures", [])) or "?"
        print("  slice %-22s arch=%-8s variant=%s"
              % (ident, archs, lib.get("SupportedPlatformVariant") or "device"),
              file=sys.stderr)

    if "simulator" not in variants:
        sys.exit("no simulator slice in this xcframework (variants: %s); "
                 "a simulator test run needs one" % " ".join(variants))
    if "device" not in variants:
        sys.exit("no device slice in this xcframework (variants: %s)"
                 % " ".join(variants))
    return 0


if __name__ == "__main__":
    sys.exit(main())