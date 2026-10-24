#!/usr/bin/env python3
"""The drawer's type roles and geometry must be the same numbers on both platforms.

WHY THIS READS CONSTANTS AND NOT LITERALS
-----------------------------------------
Home's parity lint pairs call sites by the literal string each `Text` renders,
because Home's sizes are literals at the call site. The drawer deliberately is
not: every size lives in `DrawerMetrics` on both platforms so the measured sheet
can disagree with the code in exactly one file. A literal-based scanner sees
`fontSize = DrawerMetrics.wordmark` and reports the style's base size (17), not
the shipped one (22) — a gate that confidently measures the wrong number, which
is the failure this whole family of checks exists to prevent.

So this lint does the two things that are actually true of the code:

  1. every metric NAME present on one platform exists on the other with the same
     value (a drawer that is 22 pt on Android and 17 pt on iOS fails here), and
  2. every literal the drawer renders is rendered on BOTH platforms (a label that
     only one side has fails here).

Names are the contract's keys, so drift is reported against a name a reader can
find in one grep rather than against a line number.

Usage
  check_drawer_type_parity.py --derive   # print both tables
  check_drawer_type_parity.py            # assert them
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
KT = ROOT / "android/app/src/main/java/com/grapsee/gsai/ui/navigation/drawer/DrawerMetrics.kt"
SWIFT = ROOT / "ios/App/Sources/Components/Drawer/DrawerMetrics.swift"
CONTRACT = ROOT / "shared-contracts/drawer-type-roles.json"

KT_SOURCES = [
    ROOT / "android/app/src/main/java/com/grapsee/gsai/ui/navigation/GsDrawer.kt",
    ROOT / "android/app/src/main/java/com/grapsee/gsai/ui/navigation/drawer/DrawerRows.kt",
    ROOT / "android/app/src/main/java/com/grapsee/gsai/ui/navigation/drawer/DrawerData.kt",
]
SWIFT_SOURCES = [
    ROOT / "ios/App/Sources/Components/AeroDrawer.swift",
    ROOT / "ios/App/Sources/Components/Drawer/DrawerMetrics.swift",
]

# Kotlin: `val wordmark = 22.sp` / `const val panelRatio: Float = 0.786f`
KT_NUM = re.compile(r"\b(?:const\s+)?val\s+(\w+)\s*(?::\s*\w+\s*)?=\s*([0-9]+(?:\.[0-9]+)?)")
# Swift: `static let wordmark: CGFloat = 22`
SW_NUM = re.compile(r"\bstatic\s+let\s+(\w+)\s*(?::\s*[\w.]+)?\s*=\s*([0-9]+(?:\.[0-9]+)?)")


def metrics(path, pattern):
    text = path.read_text()
    out = {}
    for name, value in pattern.findall(text):
        try:
            out[name] = float(value)
        except ValueError:
            continue
    return out


def labels(paths):
    text = "\n".join(p.read_text() for p in paths)
    return {m for m in re.findall(r'"([A-Z][^"\\]{1,40})"', text)}


def main() -> int:
    derive = "--derive" in sys.argv
    kt = metrics(KT, KT_NUM)
    sw = metrics(SWIFT, SW_NUM)
    shared = sorted(set(kt) & set(sw))

    if derive:
        print(f'{"metric":22s} {"android":>9s} {"ios":>9s}')
        for name in sorted(set(kt) | set(sw)):
            a = f'{kt[name]:g}' if name in kt else '—'
            i = f'{sw[name]:g}' if name in sw else '—'
            print(f'{name:22s} {a:>9s} {i:>9s}')
        print()
        print(f'android-only: {sorted(set(kt) - set(sw))}')
        print(f'ios-only:     {sorted(set(sw) - set(kt))}')
        return 0

    try:
        contract = json.load(open(CONTRACT))
    except OSError:
        print(f'::error::{CONTRACT.relative_to(ROOT)} is missing. Regenerate it with '
              f'--derive and commit it; a contract that can be deleted is not a contract.')
        return 2

    problems = []
    checked = 0
    for name in contract.get("metrics", []):
        if name not in kt:
            problems.append(f"{name}: missing on Android")
            continue
        if name not in sw:
            problems.append(f"{name}: missing on iOS")
            continue
        checked += 1
        if abs(kt[name] - sw[name]) > 0.01:
            problems.append(f"{name}: Android {kt[name]:g} vs iOS {sw[name]:g}")

    kt_labels = labels(KT_SOURCES)
    sw_labels = labels(SWIFT_SOURCES)
    for literal in contract.get("literals", []):
        if literal not in kt_labels:
            problems.append(f'"{literal}": no Android Text() renders it any more')
        if literal not in sw_labels:
            problems.append(f'"{literal}": no iOS Text() renders it any more')

    # A metric that exists on only one platform is drift even if nothing lists it.
    for name in sorted(set(kt) ^ set(sw)):
        side = "iOS" if name in sw else "Android"
        problems.append(f"{name}: only {side} has this metric")

    if problems:
        print(f'::error::the drawer contract is violated ({len(problems)} problem(s), '
              f'{checked} metric(s) compared):')
        for p in problems:
            print(f'  - {p}')
        return 1
    print(f'ok: {checked} drawer metric(s) identical on both platforms, '
          f'{len(contract.get("literals", []))} literal(s) rendered on both')
    return 0


if __name__ == "__main__":
    sys.exit(main())
