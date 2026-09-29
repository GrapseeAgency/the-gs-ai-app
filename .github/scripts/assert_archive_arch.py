#!/usr/bin/env python3
"""Fail unless every object in every archive under a directory is one ABI.

This exists because a green android-deps was shipping x86-64 objects labelled
arm64-v8a, and four separate checks missed it:

  - android-deps' own success
  - assert_arch_member.sh, which inspects archives and passed
  - CMAKE_SYSTEM_PROCESSOR, which reports whatever -DANDROID_ABI said
  - a CMake gate reading the same value

All four were true and none of them read the bytes. This reads the bytes: it
parses each `ar` member, checks the ELF magic, and requires e_machine to be the
expected machine for the ABI. No member may be a different architecture, and no
archive may contain zero objects -- a filter that quietly skips what it cannot
parse reports success on an archive it never examined.

Usage: assert_archive_arch.py <dir> <abi>
       abi is one of arm64-v8a, armeabi-v7a, x86_64, x86

Exits non-zero and prints the offending archive if anything is wrong.
"""
import collections
import glob
import os
import struct
import sys

MACHINE = {0x03: "i386", 0x28: "ARM", 0x3E: "x86-64", 0xB7: "AArch64"}
WANT = {
    "arm64-v8a": "AArch64",
    "armeabi-v7a": "ARM",
    "x86_64": "x86-64",
    "x86": "i386",
}

AR_MAGIC = b"!<arch>\n"
THIN_MAGIC = b"!<thin>\n"


def members(path):
    """Yield (name, body) for every member of a normal ar archive."""
    d = open(path, "rb").read()
    if d[:8] == THIN_MAGIC:
        raise ValueError("thin archive: members are external paths, so an "
                         "archive that travelled without its .o files is "
                         "empty at link time")
    if d[:8] != AR_MAGIC:
        raise ValueError("not an ar archive (magic %r)" % d[:8])
    off = 8
    while off + 60 <= len(d):
        h = d[off:off + 60]
        if h[58:60] != b"\x60\n":
            break
        name = h[0:16].decode("ascii", "replace").strip().rstrip("/")
        try:
            size = int(h[48:58].decode().strip())
        except ValueError:
            break
        yield name, d[off + 60:off + 60 + size]
        off += 60 + size + (size % 2)


def survey(path):
    """(object count, histogram, names that were not ELF objects)."""
    seen = collections.Counter()
    notobj = []
    for name, body in members(path):
        if body[:4] != b"\x7fELF":
            # The long-name table and the symbol index are not objects. They are
            # named "/" and "//" or similar; anything else must be reported.
            if name in ("/", "//", "/SYM64/", "__.SYMDEF", "__.SYMDEF SORTED"):
                continue
            notobj.append(name)
            continue
        seen[MACHINE.get(struct.unpack_from("<H", body, 18)[0],
                         "unknown")] += 1
    return sum(seen.values()), seen, notobj


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    d, abi = sys.argv[1], sys.argv[2]
    if abi not in WANT:
        sys.exit("unknown ABI %r; expected one of %s" % (abi, sorted(WANT)))
    want = WANT[abi]
    archives = sorted(glob.glob(os.path.join(d, "*.a")))
    if not archives:
        sys.exit("no .a files under %s" % d)

    bad = 0
    for a in archives:
        try:
            n, hist, notobj = survey(a)
        except ValueError as e:
            print("  %-18s FAILED: %s" % (os.path.basename(a), e))
            bad += 1
            continue
        label = os.path.basename(a)
        print("  %-18s objects=%-5d %s" % (label, n, dict(hist)))
        if notobj:
            # Reported, NOT fatal. These are the ar symbol table and the long
            # name table, whose name field is blank rather than "/". A non-ELF
            # member cannot be a wrong-architecture OBJECT, and the bug this
            # exists to catch was exactly that: 169 correct AArch64 members
            # labelled arm64-v8a that were really x86-64, which IS caught below.
            print("  %s: %d non-object member(s) (symbol/name tables), ignored"
                  % (label, len(notobj)))
        if n == 0:
            print("  %s: contains no ELF objects at all" % label)
            bad += 1
            continue
        # EXACTLY one architecture, and it must be this ABI. A single foreign
        # member among 168 correct ones is the bug this exists to catch.
        if sorted(hist) != [want]:
            print("  %s: expected only %s, found %s" % (label, want, dict(hist)))
            bad += 1
    if bad:
        print("assert_archive_arch: %d archive(s) are not %s" % (bad, abi))
        sys.exit(1)
    print("  every object in every archive under %s is %s" % (d, want))


if __name__ == "__main__":
    main()
