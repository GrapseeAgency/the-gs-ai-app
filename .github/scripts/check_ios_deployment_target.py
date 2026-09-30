#!/usr/bin/env python3
"""Assert the iOS app's deployment target is at least what its OWN SOURCE needs.

Run 36751766601, first run to reach the app build, and it failed there:

    ios/App/Sources/Features/Chats/AttachmentSheetView.swift:34:38: error:
      'PhotosPickerItem' is only available in iOS 16.0 or newer
    ios/App/Sources/Features/Chats/AttachmentSheetView.swift:276:45: error:
      'PhotosPickerItem' is only available in iOS 16.0 or newer
    ** BUILD FAILED **
    ##[error]Process completed with exit code 65.

AND THIS IS A REGRESSION I INTRODUCED, so it is worth being precise about.

Commit 2ccb3a7 set `IPHONEOS_DEPLOYMENT_TARGET: "15.0"` in `ios/project.yml` to
fix an undefined `__chkstk_darwin` in the CARGO link. Before that line existed,
`ios/project.yml` said nothing and xcodebuild picked its own default, which was
16.0 or higher. So a change aimed at the native library silently lowered the
APP's floor by a version, and the app's own Swift code stopped compiling.

THE TWO NUMBERS ARE FOR TWO DIFFERENT THINGS, and conflating them is the bug:

  * the STATIC LIBRARY needs iOS >= 12, because `__chkstk_darwin` is only
    exported from libSystem from 12.0 onward, and 15.0 because that is what the
    prebuilt archives were configured with. That is the job-level
    `IPHONEOS_DEPLOYMENT_TARGET` in ios-native.yml.
  * the APP needs iOS >= 16, because `PhotosPickerItem` is PhotosUI, introduced
    in iOS 16. That is `ios/project.yml`.

A library built for 15.0 links perfectly well into an app whose floor is 16.0.
The floors are independent, and the app's is the HIGHER of the two.

WHY A CHECK RATHER THAN A COMMENT. The comment above the value in project.yml was
already there and already said the number was for the library. It did not stop me
lowering the app below what its own source needs, because a number cannot be wrong
about itself -- only a comparison can. So this reads the floors the source
actually requires, reads the target the project declares, and fails if the target
is below the highest requirement.

The table is deliberately one entry wide. It is not a general iOS-availability
oracle and does not pretend to be; it records the one API whose floor has actually
been hit by a real build, with the run that hit it. Adding an entry means someone
has seen a build fail on that symbol, which is the only evidence that belongs in
a table like this.

Usage:  python3 .github/scripts/check_ios_deployment_target.py
Exit 0 when the declared target satisfies every requirement found in the source.
Exit 1 otherwise, printing the symbol, the file, and the version needed.
"""
import re
import sys
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
PROJECT = ROOT / "ios" / "project.yml"
SOURCE_DIR = ROOT / "ios" / "App" / "Sources"

# symbol -> (minimum iOS, the run that proved it)
#   'PhotosPickerItem' is PhotosUI, iOS 16.0. Run 36751766601.
API_FLOOR = {
    "PhotosPickerItem": (16, "36751766601"),
    "PhotosPicker": (16, "36751766601"),
}

TARGET_RE = re.compile(r'^\s*IPHONEOS_DEPLOYMENT_TARGET:\s*"?([0-9]+(?:\.[0-9]+)?)"?\s*$')


def declared_target():
    """The APP's floor from ios/project.yml, or None if it declares none."""
    if not PROJECT.exists():
        return None, "ios/project.yml is missing"
    for line in PROJECT.read_text(encoding="utf-8").split("\n"):
        m = TARGET_RE.match(line)
        if m:
            return float(m.group(1)), line.strip()
    return None, "ios/project.yml declares no IPHONEOS_DEPLOYMENT_TARGET"


def used_symbols():
    """(symbol, path, line_no) for every API in the table that the source uses."""
    if not SOURCE_DIR.exists():
        return []
    hits = []
    for path in sorted(SOURCE_DIR.rglob("*.swift")):
        text = path.read_text(encoding="utf-8", errors="replace")
        for n, line in enumerate(text.split("\n"), 1):
            if line.strip().startswith("//"):
                continue
            for sym in API_FLOOR:
                if re.search(r"\b%s\b" % re.escape(sym), line):
                    hits.append((sym, path, n))
    return hits


def main():
    target, evidence = declared_target()
    hits = used_symbols()
    needed = max((API_FLOOR[s][0] for s, _, _ in hits), default=0)

    print("  declared app deployment target : %s"
          % ("%g" % target if target is not None else "<none>"))
    print("  highest floor in the source    : %s" % (needed if needed else "<none>"))

    if target is None:
        print("::error::%s" % evidence)
        print("::error::xcodebuild would then pick its own default, which is how the")
        print("::error::floor drifted in the first place. Declare it.")
        return 1

    if target < needed:
        print("::error::ios/project.yml declares IPHONEOS_DEPLOYMENT_TARGET %g but"
              % target)
        print("::error::the app's own source needs %d.0:" % needed)
        for sym, path, n in hits:
            print("::error::  %s:%d  %s  (iOS %d, run %s)"
                  % (path.relative_to(ROOT), n, sym, API_FLOOR[sym][0], API_FLOOR[sym][1]))
        print("::error::")
        print("::error::NOTE the LIBRARY floor is a different number and stays 15.0.")
        print("::error::The job-level IPHONEOS_DEPLOYMENT_TARGET in ios-native.yml is the")
        print("::error::one for the cargo link -- it exists for __chkstk_darwin (iOS 12)")
        print("::error::and for the prebuilt archives. A library built for 15.0 links")
        print("::error::into an app whose floor is higher, so the two are independent.")
        return 1

    print("VERIFIED: the app's floor is %g, which satisfies the %s the source requires"
          % (target, needed if needed else "(nothing recorded)"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
