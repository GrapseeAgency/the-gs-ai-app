#!/usr/bin/env python3
"""archive_arch.py <archive.a> <abi>  -- verify an ELF static archive's architecture.

Prints one line: `<n> objects, <bad> wrong-architecture (want <name>)`.
Exits 0 when every object matches, 3 when the archive contains no ELF objects at
all, and 1 on a mismatch. Those are three DIFFERENT answers and the caller is
written to treat 3 as a failure, which is the point.

## Why the objects are read rather than the archive

`file -b libggml.a` reports, in full:

    current ar archive

An ar archive has no architecture of its own; its MEMBERS do. So every check
here walks the ar member table and reads the ELF header of each member. This is
recorded in `assert_arch_member.sh`'s own header:

> `file -b` on the .a itself is NOT a valid architecture check either: an ar
> archive reports only "current ar archive". That is what lipo is for, and the
> caller checks the archive with lipo before calling this.

I wrote exactly that broken check in a new fetch step, it failed all four
archives with `WRONG libggml.a: current ar archive`, and the archives were fine.
android-native run 36968639571.

## Why numbers, not names

`e_machine` is compared as a NUMBER against the ABI's number. An earlier version
had two dicts -- one mapping `e_machine` to a name, one mapping the ABI to a name
-- and their x86 entries disagreed:

    MACH = {0x03: "i386",  0x3e: "x86-64",  0x28: "ARM", 0xb7: "AArch64"}
    WANT = {..., "x86": "i686"}

"i386" != "i686", so EVERY object in a correct x86 build counted as wrong:

    libggml-base.a: 9 objects, 9 wrong-architecture
    ::error::libggml-base.a in the x86 package is not x86

and the artifact was fine. Raw, run 36648181332. **A check whose two halves can
drift is the same defect as the gate it replaced, only inverted: that one could
not fail, this one could only fail.** One table, one comparison, no name on
either side of it.

## ar member walking, briefly

`ar` is 8 bytes of `!<arch>\n`, then members each with a 60-byte header whose
bytes 48..58 are the size in decimal and whose bytes 58..60 are `` `\n ``. A
member is padded to an even offset. The first member on macOS is the
`__.SYMDEF` pseudo-member, which is not an object and yields no ELF magic, which
is why the walk skips anything that is not `\x7fELF` rather than assuming the
first member is code.

## Exit 3 is the important one

`0 objects -- not an ar archive, refusing to pass`

A static archive with no readable objects is indistinguishable, to every other
check in this pipeline, from a correct one. Refusing is the only safe answer, and
it is why the caller treats a non-zero exit as fatal.
"""

import struct
import sys

E_MACHINE = {
    "arm64-v8a": 0xB7,
    "armeabi-v7a": 0x28,
    "x86_64": 0x3E,
    "x86": 0x03,
}
NAMES = {0x03: "i386", 0x3E: "x86-64", 0x28: "ARM", 0xB7: "AArch64"}


def scan(path):
    """Yield the ELF payload of every ELF member of the ar archive at `path`."""
    with open(path, "rb") as fh:
        d = fh.read()
    off = 8  # skip "!<arch>\n"
    while off + 60 <= len(d):
        h = d[off:off + 60]
        if h[58:60] != b"`\n":
            break
        try:
            sz = int(h[48:58].decode().strip())
        except ValueError:
            break
        body = d[off + 60:off + 60 + sz]
        if body[:4] == b"\x7fELF":
            yield body
        off += 60 + sz + (sz % 2)  # members are padded to an even offset


def main(argv):
    if len(argv) != 3:
        print("usage: archive_arch.py <archive.a> <abi>", file=sys.stderr)
        print("  abi is one of: %s" % ", ".join(sorted(E_MACHINE)), file=sys.stderr)
        return 3
    path, abi = argv[1], argv[2]
    if abi not in E_MACHINE:
        print("unknown abi %r; expected one of %s" % (abi, ", ".join(sorted(E_MACHINE))),
              file=sys.stderr)
        return 3
    want = E_MACHINE[abi]
    total = bad = 0
    for body in scan(path):
        total += 1
        # e_machine is a 2-byte field at offset 18 in a 32/64-bit ELF header.
        if struct.unpack_from("<H", body, 18)[0] != want:
            bad += 1
    if total == 0:
        print("0 objects -- not an ar archive, refusing to pass")
        return 3
    print("%d objects, %d wrong-architecture (want %s)" % (total, bad, NAMES.get(want)))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
