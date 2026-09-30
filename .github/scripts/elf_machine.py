#!/usr/bin/env python3
"""Print the ELF `Machine` string for a file, or nothing.

    $ python3 elf_machine.py android/app/src/main/jniLibs/x86_64/libgs_ffi.so
    x86-64

Reads bytes 18-19 of the ELF header, which is e_machine, little-endian, and
maps it to the name `file(1)` prints so the value can be compared against a
word in a message.

A SCRIPT RATHER THAN `python3 -c`, and not for style. A python body inside a
YAML `run: |` block has to be indented with the block, and a body line at column
0 ends the block scalar -- the rest of the file then parses as YAML mapping keys:

    yaml.scanner.ScannerError: while scanning a simple key
      in ".github/workflows/android-device.yml", line 279, column 1
    could not find expected ':'

That has been hit three separate times in this repository, each time after
rewriting the inline version by hand. This file is the standing answer.

Why the mapping matters. Comparing two NAMES that came from two different tables
is how android-native.yml ended up checking `MACH[0x03] == "i386"` against
`WANT["x86"] == "i686"` and failing every correct x86 build:

    libggml-base.a: 9 objects, 9 wrong-architecture

One table, keyed by ABI, on one side of the comparison only.
"""
import struct
import sys

# e_machine -> the name `file -b` prints.
MACHINE = {
    0x03: "Intel i386",
    0x28: "ARM",
    0x3E: "x86-64",
    0xB7: "AArch64",
}

# ABI -> the e_machine a library for that ABI must carry.
E_MACHINE = {
    "x86_64": 0x3E,
    "arm64-v8a": 0xB7,
    "armeabi-v7a": 0x28,
    "x86": 0x03,
}


def main() -> int:
    if len(sys.argv) not in (2, 3):
        sys.exit("usage: elf_machine.py <file> [abi]\n"
                 "  with no ABI, prints the machine name\n"
                 "  with an ABI, also fails if the file is not that ABI")
    try:
        with open(sys.argv[1], "rb") as fh:
            head = fh.read(20)
    except OSError as exc:
        sys.exit("cannot read %s: %s" % (sys.argv[1], exc))

    if len(head) < 20 or head[:4] != b"\x7fELF":
        sys.exit("%s is not an ELF file (magic %r)" % (sys.argv[1], head[:4]))

    machine = struct.unpack_from("<H", head, 18)[0]
    print(MACHINE.get(machine, "e_machine=0x%02x" % machine))

    # Also usable as the whole gate, so a caller never has to spell the ABI
    # table a second time in shell.
    if len(sys.argv) == 3:
        want = E_MACHINE.get(sys.argv[2])
        if want is None:
            sys.exit("unknown ABI %r; expected one of %s"
                     % (sys.argv[2], ", ".join(sorted(E_MACHINE))))
        if machine != want:
            sys.exit("%s is %s, not %s (%s)"
                     % (sys.argv[1], MACHINE.get(machine, hex(machine)),
                        MACHINE[want], sys.argv[2]))
    return 0


if __name__ == "__main__":
    sys.exit(main())