#!/usr/bin/env python3
"""The three places the gs_ffi_mobile_* ABI is written down must agree.

There are two of them, and they are NOT the same list on purpose:

  .github/scripts/gs_mobile_abi.txt   the full ABI, read by both checks in
                                      android-native.yml
  .github/workflows/ios-native.yml     a ten-symbol SUBSET inline, because the
                                      iOS device slice is not required to carry
                                      the sd_* diffusion entry points

This lint exists because "they are supposed to agree" is not a property anything
enforces. ios-native.yml pins its symbols in a shell `for` loop, which nothing
parses, so the day someone renames a symbol in the .txt the iOS gate keeps
checking for the old name forever and reports a MISSING that is a stale list
rather than a broken build -- the exact failure this repository has already paid
for once, when a hand-written check disagreed with the repository's own counter.

Four properties, each of which can fail on its own:

  1. NON-EMPTY. An extraction that returns nothing has not proved the list is a
     subset of anything, it has proved the extractor matched nothing. A subset
     check over an empty set is vacuously true, and a vacuous pass is the thing
     this lint exists to prevent -- so emptiness is a failure, not a pass. This
     is not hypothetical: the first version of this script sliced the workflow
     from `MISSING=""` to the next `do`, and the file contains TWO of each, so
     it silently extracted the wrong block and reported an empty set.

  2. SUBSET. Every symbol iOS pins must exist in the declared ABI.

  3. NO UNDECLARED SYMBOL. A symbol pinned by iOS but absent from the file is
     worse than missing: the iOS gate would demand an export that nothing
     declares, and the Android floor would count fewer symbols than iOS expects.

  4. NON-TRIVIAL. The subset must actually be a subset -- if iOS pinned all 18
     the sd_* exemption would be a fiction, and the comment claiming the iOS
     device slice need not carry diffusion would be false.

Exits 0 when all four hold, 1 otherwise, naming which one failed.
"""

import re
import sys
import os

ABI_TXT = os.path.join(".github", "scripts", "gs_mobile_abi.txt")
IOS_YML = os.path.join(".github", "workflows", "ios-native.yml")
PREFIX = "gs_ffi_mobile_"


def declared_abi(path):
    """The full ABI, one suffix per line, comments and blanks removed."""
    if not os.path.isfile(path):
        raise SystemExit("FAIL 1/4: the declared ABI %s does not exist" % path)
    out = set()
    with open(path) as fh:
        for line in fh:
            t = line.strip()
            if not t or t.startswith("#"):
                continue
            if t.startswith(PREFIX):
                raise SystemExit(
                    "FAIL 1/4: %s contains the full name %r. This file holds\n"
                    "         SUFFIXES; the prefix is added when the list is read, so a\n"
                    "         full name here would never match anything." % (path, t)
                )
            if not re.fullmatch(r"[A-Za-z_][A-Za-z_0-9]*", t):
                raise SystemExit("FAIL 1/4: %s has a line that is not an identifier: %r" % (path, t))
            out.add(t)
    return out


def ios_pinned(path):
    """The suffixes ios-native.yml's `for S in ...` loop demands.

    Anchored on the loop itself. Slicing between two markers is what produced the
    empty-set bug described in this file's docstring, so the anchor must be the
    thing being read and the terminator must be the one that ENDS IT.
    """
    if not os.path.isfile(path):
        raise SystemExit("FAIL 1/4: %s does not exist" % path)
    s = open(path).read()
    m = re.search(r"for S in (" + PREFIX + r"[^;]*?)\n\s*do\n", s, re.S)
    if not m:
        raise SystemExit(
            "FAIL 1/4: no `for S in gs_ffi_mobile_... do` loop in %s.\n"
            "         This lint cannot check a list it cannot find, and it will not\n"
            "         assume the list is fine because it is not looking at it." % path
        )
    return set(re.findall(PREFIX + r"([A-Za-z_0-9]+)", m.group(1)))


def main():
    abi = declared_abi(ABI_TXT)
    ios = ios_pinned(IOS_YML)

    # (1) non-empty, checked on BOTH sides. An empty set is a subset of anything,
    # so this is what keeps the other three checks from passing for free.
    if not abi:
        raise SystemExit("FAIL 1/4: the declared ABI is EMPTY")
    if not ios:
        raise SystemExit(
            "FAIL 1/4: the iOS list extracted as EMPTY. An empty list is a subset of\n"
            "         any list, so 2/4, 3/4 and 4/4 would all pass for free. The\n"
            "         extractor is wrong until proven otherwise -- not the workflow."
        )

    undeclared = ios - abi
    if undeclared:
        raise SystemExit(
            "FAIL 3/4: ios-native.yml demands symbols the declared ABI does not have:\n"
            "         %s\n"
            "         The iOS gate would fail for an export nothing declares. Either add\n"
            "         them to %s or drop them from the iOS list." % (" ".join(sorted(undeclared)), ABI_TXT)
        )

    if ios == abi:
        raise SystemExit(
            "FAIL 4/4: the iOS list is the WHOLE ABI (%d symbols), so the documented\n"
            "         exemption -- that the iOS device slice need not carry the sd_*\n"
            "         diffusion entry points -- is not true any more. If the iOS slice\n"
            "         really does carry them, delete the exemption from the comment;\n"
            "         if it does not, restore the subset. One of those two is a lie\n"
            "         right now." % len(abi)
        )

    sd = sorted(x for x in abi - ios if x.startswith("sd_"))
    print("ABI list agreement OK")
    print("  declared ABI   : %d symbols (%s)" % (len(abi), ABI_TXT))
    print("  iOS subset     : %d symbols (inline in %s)" % (len(ios), IOS_YML))
    print("  iOS omits %2d sd_*   : %s" % (len(sd), " ".join(sd)))
    other = sorted(x for x in abi - ios if not x.startswith("sd_"))
    print("  iOS omits %2d other  : %s" % (len(other), " ".join(other) or "(none)"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
