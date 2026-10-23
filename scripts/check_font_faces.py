#!/usr/bin/env python3
"""The bundled faces must be the faces we think they are.

This exists because a real defect got through every other check: the vendor workflow
fetched the first variable .ttf in `ofl/intertight`, which was `InterTight-Italic[wght].ttf`,
instanced it into four weights and rewrote the name table so the files called themselves
Regular/Medium/SemiBold/Bold. iOS then found `InterTight-Regular` and drew it — the silent
fallback was gone, registration tests were green, the type contract was green, and every
glyph on the Home page was **italic**. It was visible in a capture; nothing asserted it.

So: italicAngle must be 0, the italic bit in OS/2.fsSelection must be clear, the subfamily
names must be the four we ship, and the platforms must ship identical bytes. A face that
fails is a face the app is drawing wrong, and no amount of agreement elsewhere fixes it.

Usage: check_font_faces.py [--root .] [--expect regular,medium,semibold,bold]
"""
import argparse
import hashlib
import struct
import sys
from pathlib import Path

WEIGHT_NAMES = {400: "Regular", 500: "Medium", 600: "SemiBold", 700: "Bold"}
IOS_FILES = {"regular": "InterTight-Regular.ttf", "medium": "InterTight-Medium.ttf",
             "semibold": "InterTight-SemiBold.ttf", "bold": "InterTight-Bold.ttf"}


def tables(b):
    n = struct.unpack(">H", b[4:6])[0]
    out = {}
    for i in range(n):
        off = 12 + 16 * i
        tag = b[off:off + 4].decode("latin-1")
        o, length = struct.unpack(">II", b[off + 8:off + 16])
        out[tag] = (o, length)
    return out


def name_record(b, t, nid):
    no = t["name"][0]
    count, string_off = struct.unpack(">HH", b[no + 2:no + 6])
    best = None
    for i in range(count):
        rec = no + 6 + 12 * i
        pid, eid, lid, record_id, length, off = struct.unpack(">HHHHHH", b[rec:rec + 12])
        if record_id != nid or pid != 3:
            continue
        raw = b[no + string_off + off: no + string_off + off + length]
        value = raw.decode("utf-16-be", "replace")
        if lid == 0x409:
            return value
        best = best or value
    return best


def read_face(path):
    b = path.read_bytes()
    t = tables(b)
    if "post" not in t or "OS/2" not in t or "name" not in t:
        return None
    post = t["post"][0]
    italic_angle = struct.unpack(">i", b[post + 4:post + 8])[0] / 65536.0
    os2 = t["OS/2"][0]
    fs_selection = struct.unpack(">H", b[os2 + 62:os2 + 64])[0]
    weight = struct.unpack(">H", b[os2 + 4:os2 + 6])[0]
    return {
        "italic_angle": italic_angle,
        "italic_bit": bool(fs_selection & 1),
        "fs_selection": fs_selection,
        "weight": weight,
        "subfamily": name_record(b, t, 2),
        "full_name": name_record(b, t, 4),
        "sha": hashlib.sha256(b).hexdigest(),
        "size": len(b),
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=".")
    ap.add_argument("--expect", default="regular,medium,semibold,bold")
    args = ap.parse_args()

    root = Path(args.root)
    android_dir = root / "android/app/src/main/res/font"
    ios_dir = root / "ios/App/Resources/Fonts"
    problems, checked = [], 0

    for weight_file in args.expect.split(","):
        a = android_dir / f"inter_tight_{weight_file}.ttf"
        i = ios_dir / IOS_FILES.get(weight_file, f"InterTight-{weight_file}.ttf")
        if not a.is_file():
            problems.append(f"missing Android face {a.relative_to(root)}")
            continue
        if not i.is_file():
            problems.append(f"missing iOS face {i.relative_to(root)}")
            continue

        fa, fi = read_face(a), read_face(i)
        if not fa or not fi:
            problems.append(f"{weight_file}: a table needed to judge the face is absent")
            continue
        checked += 1

        for side, face in (("android", fa), ("ios", fi)):
            if abs(face["italic_angle"]) > 0.01:
                problems.append(
                    f"{weight_file} ({side}): italicAngle {face['italic_angle']:.2f} — the "
                    f"glyphs are slanted; this file is the italic cut wearing an upright name")
            if face["italic_bit"]:
                problems.append(
                    f"{weight_file} ({side}): OS/2 italic bit is set in an upright face")
            if face["subfamily"] != WEIGHT_NAMES.get(face["weight"]):
                problems.append(
                    f"{weight_file} ({side}): subfamily {face['subfamily']!r} does not match "
                    f"usWeightClass {face['weight']} ({WEIGHT_NAMES.get(face['weight'])})")
        if fa["sha"] != fi["sha"]:
            problems.append(
                f"{weight_file}: the two platforms ship different bytes "
                f"({fa['size']} vs {fi['size']}) — one of them was edited by hand")
        print(f"  {weight_file:9s} {WEIGHT_NAMES.get(fa['weight'], '?'):9s} "
              f"angle {fa['italic_angle']:.2f}  {fa['size'] // 1024:4d} KB  "
              f"{fa['sha'][:12]}  both platforms")

    # No stray face may sit in the bundle either: a leftover file is a face the app
    # registers and might resolve by name.
    for directory, pattern in ((android_dir, "*.ttf"), (ios_dir, "*.ttf")):
        if not directory.is_dir():
            continue
        for path in sorted(directory.glob(pattern)):
            if "inter_tight" not in path.name.lower() and "intertight" not in path.name.lower():
                problems.append(f"unrelated face still bundled: {path.relative_to(root)}")

    if problems:
        print(f"\n::error::{len(problems)} font-contract failure(s):")
        for p in problems:
            print(f"  - {p}")
        return 1
    print(f"\nOK: {checked} face(s) upright, named for their weight, identical on both platforms")
    return 0


if __name__ == "__main__":
    sys.exit(main())
