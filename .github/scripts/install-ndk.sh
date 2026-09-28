#!/usr/bin/env bash
# install-ndk.sh — install the Android SDK command-line tools and the NDK.
#
# WHY THIS IS A SCRIPT AND WHY IT RUNS IN EVERY JOB
#
# It ran once, in a `setup` job, and its output was passed to the build jobs as
# a path string. Every build job then failed with
#
#     MISSING NDK: /home/runner/android-sdk/ndk/25.2.9519653
#     ##[error]Process completed with exit code 1.
#
# because each GitHub Actions job gets a FRESH VM. A job output is a string, not
# a filesystem. The `setup` job also cost a full runner to produce a path.
#
# The ONNX Runtime job passed through this whole time, which is what made the
# mistake look like a Tesseract/llama.cpp problem: that job needs no NDK, so it
# never noticed the SDK was missing. One job passing is not evidence the setup
# worked.
#
# So: every job that needs the NDK installs it, through this one script, and the
# script fails loudly rather than leaving a half-installed SDK behind.

set -euo pipefail

NDK_VERSION="${NDK_VERSION:-25.2.9519653}"
CMDLINE_VERSION="${CMDLINE_VERSION:-11076708}"
SDK="${ANDROID_HOME:-$HOME/android-sdk}"

echo "=== installing Android SDK into $SDK ==="
echo "    ndk      : $NDK_VERSION"
echo "    cmdline  : $CMDLINE_VERSION"

mkdir -p "$SDK/cmdline-tools"
cd /tmp
wget -q "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_VERSION}_latest.zip"
unzip -q -o "commandlinetools-linux-${CMDLINE_VERSION}_latest.zip"
# The zip unpacks cmdline-tools/, and the SDK requires the *latest/* layout or
# every sdkmanager invocation fails.
rm -rf "$SDK/cmdline-tools/latest"
mv cmdline-tools "$SDK/cmdline-tools/latest"

export PATH="$SDK/cmdline-tools/latest/bin:$PATH"
# Assert before relying on it. The image ships ANDROID_HOME with no
# command-line tools, which is what produced
#     sdkmanager: command not found
# in the first run of android-native.yml.
command -v sdkmanager >/dev/null || { echo "sdkmanager is still not on PATH"; exit 1; }
sdkmanager --version

yes | sdkmanager --licenses >/dev/null 2>&1 || true
sdkmanager --install "ndk;$NDK_VERSION" "platform-tools" >/dev/null

export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export ANDROID_NDK_HOME="$SDK/ndk/$NDK_VERSION"
# ANDROID_NDK_ROOT as well: cargo-ndk 4 resolves a disagreement between the two
# in favour of whichever it sees, and the image ships a different NDK, so a
# mismatch means the job builds against an NDK it did not install.
export ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"
export PATH="$SDK/cmdline-tools/latest/bin:$SDK/platform-tools:$PATH"

if [ -n "${GITHUB_ENV:-}" ]; then
  {
    echo "ANDROID_HOME=$ANDROID_HOME"
    echo "ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"
    echo "ANDROID_NDK_HOME=$ANDROID_NDK_HOME"
    echo "ANDROID_NDK_ROOT=$ANDROID_NDK_ROOT"
  } >> "$GITHUB_ENV"
  echo "$PATH" >> "$GITHUB_PATH"
fi

# The single most useful assertion in this file. Every consumer of the NDK
# checks this exact path, and the failure it prevents is the one that just
# happened four times.
test -d "$ANDROID_NDK_HOME" || {
  echo "FAIL: NDK $NDK_VERSION was not installed at $ANDROID_NDK_HOME"
  ls -la "$SDK/ndk" 2>/dev/null || echo "  (no ndk directory at all)"
  exit 1
}
test -x "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android24-clang" || {
  echo "FAIL: the arm64 clang wrapper is missing under $ANDROID_NDK_HOME"
  ls "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin" 2>/dev/null | sed -n '1,10p'
  exit 1
}

echo "=== NDK ready ==="
echo "  size    : $(du -sh "$ANDROID_NDK_HOME" | cut -f1)"
echo "  clang   : $ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android24-clang --version | head -1"
echo "  ndk root: $ANDROID_NDK_HOME"
