#!/usr/bin/env bash
# assert_arch_member <archive> <label> [expected]
#
# `expected` defaults to arm64/aarch64. It exists because this script was reused
# for a two-ABI matrix and rejected a CORRECT x86_64 build:
#     FAIL: libcommon.a: archive member is not arm64/aarch64
# which is the same class of mistake as matching only "arm64" and rejecting ELF.
#
#
# Verifies that a Mach-O object inside a static archive is arm64.
#
# This exists as a file rather than inline YAML for two reasons, both of which
# cost a CI run:
#
#   * A `<<'SH'` heredoc cannot terminate inside a YAML block scalar: the
#     terminator is indented with the rest of the block, so the shell never sees
#     it at column 0. The modulemap in ios-native.yml is built with printf for
#     exactly this reason.
#   * Inline, the two slice steps drifted: the simulator step kept a pre-fix
#     version of the check while the device step had been corrected, so a passing
#     device check hid a broken simulator one.
#
# Two subtleties it handles, each of which failed on a real run:
#
#   * $1 is often relative to the caller's working-directory, and the extraction
#     happens in a temp dir, so it is made absolute first.
#     -> ar: target/aarch64-apple-ios/release/libgs_ffi.a: No such file or directory
#   * `ar t` lists __.SYMDEF first on macOS. That is an archive pseudo-member,
#     not an object, and extracting it produces nothing.
#     -> FAIL: device slice is not arm64: (empty)
#
# And one shell subtlety: `sed -n '1p'`, never `head -1`. Under
# `set -euo pipefail`, head closes the pipe, its upstream grep dies on SIGPIPE,
# and pipefail turns that into a failing step.
#     -> grep: stdout: Broken pipe
#
# `file -b` on the .a itself is NOT a valid architecture check either: an ar
# archive reports only "current ar archive". That is what lipo is for, and the
# caller checks the archive with lipo before calling this.
#
# BOTH SPELLINGS, because this is used for two formats that name the same
# architecture differently:
#   Apple Mach-O  : "arm64"            (lipo says arm64, file says arm64)
#   Android ELF   : "ARM aarch64"      (there is no "arm64" in an ELF header)
#
# The first version of this script matched only "arm64" and was written for iOS.
# Reused unchanged for Android it rejected a CORRECT build:
#     libggml-base.a first object member: ggml.c.o
#     ELF 64-bit LSB relocatable, ARM aarch64, version 1 (SYSV), not stripped
#     FAIL: libggml-base.a: archive member is not arm64
# A check that fails a passing build trains people to ignore it.
set -euo pipefail

if [ "$#" -lt 2 ] || [ "$#" -gt 3 ]; then
  echo "usage: $0 <archive> <label> [expected-arch]" >&2
  exit 2
fi

rel="$1"
label="$2"
EXPECT="${3:-arm64}"

if [ ! -f "$rel" ]; then
  echo "FAIL: $label: no such archive: $rel" >&2
  exit 1
fi

abs="$(cd "$(dirname "$rel")" && pwd)/$(basename "$rel")"

member="$(ar t "$abs" | grep -v '^__' | sed -n '1p')"
if [ -z "$member" ]; then
  echo "FAIL: $label: archive has no object members" >&2
  ar t "$abs" >&2
  exit 1
fi

echo "$label first object member: $member"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
( cd "$work" && ar x "$abs" "$member" && file -b "$member" ) > "$work/out.txt"
cat "$work/out.txt"

# `file` spells the same ISA differently per format and per vendor:
#   Apple Mach-O : arm64        Android/Linux ELF : aarch64
#   x86_64 ELF   : x86-64      x86_64 Mach-O      : x86_64
case "$EXPECT" in
  arm64)   PAT='arm64|aarch64|ARM aarch64' ;;
  x86_64)  PAT='x86-64|x86_64' ;;
  x86)     PAT='80386|i386' ;;
  # 32-bit ARM, and the fourth ABI in the android matrix. `file` prints the ISA
  # UPPERCASE and alone:
  #     ELF 32-bit LSB relocatable, ARM, EABI5, version 1 (SYSV), not stripped
  # so the `*)` fallback below, which uses $EXPECT verbatim, would grep for
  # lowercase "armeabi-v7a" and reject a perfectly correct build with
  #     FAIL: libllama.a: archive member is not armeabi-v7a
  # -- the same failure this script already caused once, for x86_64. Verified
  # against the real file(1) strings for all four ABIs before being written.
  armeabi-v7a) PAT='ARM|arm|eabi' ;;
  *)       PAT="$EXPECT" ;;
esac
if ! grep -qE "$PAT" "$work/out.txt"; then
  echo "FAIL: $label: archive member is not $EXPECT" >&2
  echo "  file said: $(cat "$work/out.txt")" >&2
  exit 1
fi
