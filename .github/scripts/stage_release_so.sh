#!/usr/bin/env bash
# Stage libgs_ffi.so into android/app/src/main/jniLibs for every ABI, from an
# android-native run's artifacts.
#
# WHY THIS EXISTS
# ---------------
# android-release.yml used to run `gradle assembleDebug` and nothing else. Verified
# absent three ways before this script existed:
#
#   git ls-files | grep '\.so$'                        -> 0 files
#   android/app/src/main/jniLibs/                      -> does not exist
#   build.gradle.kts refs to .so / jniLibs / abiFilters -> none
#
# So every APK this pipeline produced contained NO NATIVE LIBRARY AT ALL.
# GsNativeLoader.initWith failed on every shipped build, isAvailable() was false,
# ChatRepository's local branch was inert, and the prefer-local toggle did nothing.
# The feature was absent, not broken.
#
# android-device.yml has staged the same library from the same artifact all along,
# for the device suite. A release needs it too.
#
# ONE library, not two. libgs_sd.so is deliberately NOT staged: `enable_sd_diffusion`
# now defaults to false so android-native no longer produces it, and shipping it
# would add 41.9 MB plus a diffusion path whose 882 MB checkpoint is not in the APK.
# Tesseract is absent by construction -- it lives in the :ocr-fallback module, which
# :app does not depend on, and ML Kit is a direct :app dependency instead.
#
# Usage: stage_release_so.sh <android-native-run-id> [repo]
set -euo pipefail

# THE DEFAULT IS HERE, NOT ONLY IN THE WORKFLOW.
#
# Run 37207411446 failed in 8 seconds with:
#     Run bash .github/scripts/stage_release_so.sh ""
#     stage_release_so.sh: line 31: usage: stage_release_so.sh <android-native-run-id>
#
# because `inputs.so_run_id` is EMPTY on a tag push. GitHub only populates
# `workflow_dispatch` inputs for a manual dispatch -- on `push: tags: ['v*']` the
# `inputs` context is empty and the input's `default:` is never applied. So the
# declared default existed only for the dispatch path, and the release path, which is
# the one that matters, got an empty string.
#
# Same shape as the `enable_sd_diffusion` trap in this repository, where `== true`
# against an empty input on push is false and silently disabled the flag. Both are
# the same mistake: a default written in one place and relied on from another.
DEFAULT_SO_RUN=37161154981
SO_RUN="${1:-$DEFAULT_SO_RUN}"
if [ -z "$SO_RUN" ]; then
  echo "::error::no android-native run id given and the built-in default is empty" >&2
  exit 1
fi
REPO="${2:-${GITHUB_REPOSITORY:?GITHUB_REPOSITORY must be set or passed as arg 2}}"
API="${GITHUB_API_URL:-https://api.github.com}"
GH_TOKEN="${GH_TOKEN:?GH_TOKEN must be set}"

ABIS="armeabi-v7a arm64-v8a x86 x86_64"
STAGED=0

echo "staging libgs_ffi.so from android-native run $SO_RUN"

for ABI in $ABIS; do
  DIR="android/app/src/main/jniLibs/$ABI"
  mkdir -p "$DIR"
  ZIP="/tmp/so-$ABI.zip"

  ID=$(curl -fsSL -H "Authorization: Bearer $GH_TOKEN" \
        "$API/repos/$REPO/actions/runs/$SO_RUN/artifacts?per_page=100" \
      | python3 -c 'import json,sys
try:
    arts = json.load(sys.stdin).get("artifacts", [])
except Exception:
    arts = []
want = "libgs_ffi-" + sys.argv[1]
for a in arts:
    if a.get("name") == want:
        print(a["id"]); break' "$ABI")

  if [ -z "$ID" ]; then
    echo "::error::no libgs_ffi-$ABI artifact in android-native run $SO_RUN" >&2
    echo "::error::Shipping without it means an APK whose native chat is absent on" >&2
    echo "::error::that ABI while the prefer-local toggle is still offered." >&2
    exit 1
  fi

  curl -fsSL -H "Authorization: Bearer $GH_TOKEN" \
    "$API/repos/$REPO/actions/artifacts/$ID/zip" -o "$ZIP"
  rm -rf "/tmp/so-$ABI" && mkdir -p "/tmp/so-$ABI"
  unzip -o -q "$ZIP" -d "/tmp/so-$ABI"

  SO=$(find "/tmp/so-$ABI" -name 'libgs_ffi.so' -type f | sed -n '1p')
  if [ -z "$SO" ] || [ ! -s "$SO" ]; then
    echo "::error::libgs_ffi-$ABI.zip did not contain a non-empty libgs_ffi.so" >&2
    find "/tmp/so-$ABI" -type f | sed 's/^/::error::  /' >&2 | sed -n '1,10p'
    exit 1
  fi

  cp "$SO" "$DIR/libgs_ffi.so"
  printf '  %-14s %10s bytes  %s\n' "$ABI" \
    "$(stat -c%s "$DIR/libgs_ffi.so")" \
    "$(file -b "$DIR/libgs_ffi.so" | cut -c1-50)"
  STAGED=$((STAGED + 1))
done

test "$STAGED" -eq 4 || {
  echo "::error::staged $STAGED of 4 ABIs. Every ABI needs its own .so, or the APK" >&2
  echo "::error::is silently missing the library on those devices." >&2
  exit 1
}

echo "staged $STAGED ABI(s) of libgs_ffi.so"
echo "libgs_sd.so deliberately NOT staged -- diffusion is off on mobile"
