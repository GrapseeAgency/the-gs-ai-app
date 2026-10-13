#!/usr/bin/env bash
# run-screens.sh — executed INSIDE the android-screens emulator runner via
# `bash <script>` (the runner's own script: field goes through /usr/bin/sh,
# which is dash and rejects `set -o pipefail`; the bash prefix is the same
# workaround android-device's run-instrumented.sh already documents).
set -euo pipefail

adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c
adb shell am instrument -w \
  -e class com.grapsee.gsai.GsScreenshotSuite \
  com.grapsee.gsai.test/androidx.test.runner.AndroidJUnitRunner \
  | tee instrumentation-out.txt || true
mkdir -p device-artifacts/screens
adb pull "/sdcard/Android/data/com.grapsee.gsai/files/screens" device-artifacts/ 2>/dev/null \
  || echo "::warning::screenshot dir not found on device"
adb logcat -d -v threadtime > device-artifacts/logcat.txt || true
ls -la device-artifacts/screens || true
