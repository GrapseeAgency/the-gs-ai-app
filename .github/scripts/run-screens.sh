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
# The suite writes into the INSTRUMENTATION package's own external dir
# (com.grapsee.gsai.test) — the app's dir is not writable by the test on
# API 30 scoped storage. List before pulling so a miss is visible evidence.
mkdir -p device-artifacts/screens
adb shell ls -la "/sdcard/Android/data/com.grapsee.gsai.test/files/screens" || true
adb pull "/sdcard/Android/data/com.grapsee.gsai.test/files/screens/." device-artifacts/screens/ \
  || echo "::warning::screenshot pull failed"
adb logcat -d -v threadtime > device-artifacts/logcat.txt || true
ls -la device-artifacts/screens || true
