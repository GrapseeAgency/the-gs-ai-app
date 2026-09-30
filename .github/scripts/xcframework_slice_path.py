#!/usr/bin/env python3
"""Print the on-disk slice directory of an xcframework's device or simulator slice.

    $ python3 xcframework_slice_path.py "$XF" simulator
    ios-arm64_x86_64-simulator

WHY IT EXISTS. The consumer job used to check:

    for SLICE in ios-arm64 ios-arm64-simulator; do
      test -f "$XF/$SLICE/libgs_ffi.a" || fail

which was correct for exactly one build. Run 36668750491:

    ::error::ios/Frameworks/GsFfi.xcframework/ios-arm64-simulator/libgs_ffi.a
      is missing

because `xcodebuild -create-xcframework` names a multi-architecture slice after
ALL of its architectures, joined by underscores:

    ios-arm64_x86_64-simulator     arm64 + x86_64 simulator
    ios-arm64-simulator            arm64 simulator only
    ios-arm64                      device

So the directory name changes whenever the architecture set does -- which is
exactly when a hardcoded name stops being true. There is no spelling that is
right in advance. The identifier is IN the plist, so read it.

READS THE PLIST WITH plistlib, NOT plutil. `plutil -extract ... json` is the
obvious tool and it is what the neighbouring scripts use, but plutil exists only
on macOS. The first version of this called it, and on Linux the script died with

    FileNotFoundError: [Errno 2] No such file or directory: 'plutil'

rather than a message about the plist it was trying to read. plistlib is in the
standard library on every platform, so this is testable off the runner -- which
is the only reason the three cases below were checked at all.

A DEVICE SLICE HAS NO SupportedPlatformVariant. That absence means "device", not
"unknown", and treating it as unknown is how a device slice stops matching.
"""
import os
import plistlib
import sys


def fail(msg: str):
    sys.exit(msg)


def load_libraries(plist_path: str):
    try:
        with open(plist_path, "rb") as fh:
            plist = plistlib.load(fh)
    except (OSError, plistlib.InvalidFileException) as exc:
        fail("cannot read %s: %s" % (plist_path, exc))
    libs = plist.get("AvailableLibraries")
    if not isinstance(libs, list) or not libs:
        fail("%s has no AvailableLibraries; this is not an xcframework plist"
             % plist_path)
    return libs


def main() -> int:
    if len(sys.argv) != 3:
        fail("usage: xcframework_slice_path.py <xcframework-dir> "
             "<device|simulator>")
    root, want = sys.argv[1], sys.argv[2]
    if want not in ("device", "simulator"):
        fail("want must be 'device' or 'simulator', not %r" % want)

    plist_path = os.path.join(root, "Info.plist")
    if not os.path.isfile(plist_path):
        fail("no Info.plist in %s" % root)

    libs = load_libraries(plist_path)
    matches = []
    for lib in libs:
        variant = str(lib.get("SupportedPlatformVariant") or "device")
        if variant != want:
            continue
        ident = lib.get("LibraryIdentifier")
        if not ident:
            fail("a %s slice in %s has no LibraryIdentifier"
                 % (want, plist_path))
        matches.append((ident, lib.get("LibraryPath") or ""))

    if not matches:
        present = sorted({str(l.get("SupportedPlatformVariant") or "device")
                          for l in libs})
        fail("no %s slice in %s (variants present: %s)"
             % (want, plist_path, ", ".join(present)))

    if len(matches) > 1:
        fail("more than one %s slice in %s: %s"
             % (want, plist_path, ", ".join(m[0] for m in matches)))

    ident, libpath = matches[0]
    binary = os.path.join(root, ident, libpath)
    if not os.path.isfile(binary):
        fail("%s is described in %s but is not on disk" % (binary, plist_path))

    print(ident)
    return 0


if __name__ == "__main__":
    sys.exit(main())