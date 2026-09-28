#!/usr/bin/env bash
# assert_arm64_member <archive> <label>
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
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <archive> <label>" >&2
  exit 2
fi

rel="$1"
label="$2"

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

if ! grep -q 'arm64' "$work/out.txt"; then
  echo "FAIL: $label: archive member is not arm64" >&2
  exit 1
fi
