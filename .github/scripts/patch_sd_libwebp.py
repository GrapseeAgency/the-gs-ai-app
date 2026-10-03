#!/usr/bin/env python3
"""Patch sd.cpp's vendored libwebp for NDKs that no longer ship cpufeatures.

The problem this solves
-----------------------
sd.cpp at the pinned SHA vendors libwebp, and libwebp's CMakeLists has an
``if(ANDROID)`` block that builds a static ``cpufeatures-webp`` target out of::

    ${ANDROID_NDK}/sources/android/cpufeatures/cpu-features.c

Google removed that directory from the NDK years ago. The path resolves to
nothing and CMake's GENERATE step dies with two errors::

    CMake Error at thirdparty/libwebp/CMakeLists.txt:204 (add_library):
      Cannot find source file: .../sources/android/cpufeatures/cpu-features.c
    CMake Error at thirdparty/libwebp/CMakeLists.txt:204 (add_library):
      No SOURCES given to target: cpufeatures-webp

Why a condition and not a deletion
----------------------------------
An earlier attempt deleted the vendored directory and commented the reference
out, which broke sd.cpp's top-level CMakeLists instead -- ``CMake Error at
CMakeLists.txt:60 (endif)``. So nothing is removed. One condition is added::

    if(ANDROID)
      include_directories(${ANDROID_NDK}/sources/android/cpufeatures)

becomes

    if(ANDROID AND EXISTS "${ANDROID_NDK}/sources/android/cpufeatures/cpu-features.c")
      include_directories(${ANDROID_NDK}/sources/android/cpufeatures)

which is what the ``else`` branch two lines below already does. With the file
absent the block is skipped, HAVE_CPU_FEATURES_H falls to the 0 the non-Android
path has always set, and libwebp compiles its portable feature-detection
branch. On an ancient NDK that still ships the directory, behaviour is preserved
exactly. The condition cannot pass vacuously, because it names the precise file
whose absence is the failure.

Verified on host, not reasoned about
------------------------------------
    reproduce   cmake with ANDROID=ON, ANDROID_NDK=<empty dir>
                -> the two CMake Errors above, same file, same line 204
    patched     same command, same empty dir
                -> "-- Build files have been written to: ..."
    built       cmake --build -> 227/227, exit 0
    behaviour-neutral, not merely quieter:
        generated webp/config.h:   /* #undef HAVE_CPU_FEATURES_H */
        cpu.c.o undefined Android* refs:  0
        libwebp.a undefined cpu-features refs:  0

Exit status
-----------
0  patched, or already patched (both are success and both print which)
1  the file is missing, or the block has moved, or the tree is not sd.cpp
   -- in every one of those cases the build is left UNTOUCHED and the message
   names what was actually found, because a silent no-op here would produce the
   identical CMake error with a green patch step immediately above it.
"""

import os
import sys

REL = os.path.join("thirdparty", "libwebp", "CMakeLists.txt")

OLD = (
    "if(ANDROID)\n"
    "  include_directories(${ANDROID_NDK}/sources/android/cpufeatures)"
)
NEW = (
    "if(ANDROID AND EXISTS "
    '"${ANDROID_NDK}/sources/android/cpufeatures/cpu-features.c")\n'
    "  include_directories(${ANDROID_NDK}/sources/android/cpufeatures)"
)


def fail(msg, lines=()):
    print("::error::" + msg)
    for ln in lines:
        print("::error::  " + ln)
    sys.exit(1)


def main(argv):
    if len(argv) != 2:
        sys.exit("usage: patch_sd_libwebp.py <sd.cpp source root>")

    root = argv[1].rstrip("/")
    path = os.path.join(root, REL)

    if not os.path.isfile(path):
        found = []
        for dirpath, dirnames, filenames in os.walk(root):
            if "libwebp" in dirpath and "CMakeLists.txt" in filenames:
                found.append(os.path.join(dirpath, "CMakeLists.txt"))
            if len(found) > 8:
                break
        fail(
            "%s does not exist, so there is nothing to patch. The pinned sd.cpp "
            "sha may have moved it. Candidate CMakeLists.txt files:" % REL,
            found or ["(none found under %s)" % root],
        )

    src = open(path).read()

    if OLD in src:
        open(path, "w").write(src.replace(OLD, NEW, 1))
        print("  patched the ANDROID cpufeatures guard in %s" % REL)
    elif NEW in src:
        # Idempotent. A second run must not be a failure, and must not be silent
        # either -- "already patched" is a different fact from "patched".
        print("  already patched (the guarded form is present): no change made")
    else:
        near = []
        for i, line in enumerate(src.splitlines(), 1):
            if "ANDROID" in line or "cpufeatures" in line:
                near.append("%d: %s" % (i, line.strip()[:100]))
        fail(
            "neither the unguarded nor the guarded form of the cpufeatures "
            "ANDROID block is present in %s, so the patch was NOT applied and "
            "the build tree is unchanged. Every ANDROID/cpufeatures line that "
            "does exist:" % REL,
            near[:25],
        )

    print("  the guard now reads:")
    for line in open(path).read().splitlines():
        if line.startswith("if(ANDROID"):
            print("    %s" % line.strip()[:110])
    print("  upstream still ships libwebp unchanged; only this build tree is patched")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))