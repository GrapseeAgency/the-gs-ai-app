#!/usr/bin/env bash
# run-screens.sh — executed INSIDE the android-screens emulator runner via
# `bash <script>` (the runner's own script: field goes through /usr/bin/sh,
# which is dash and rejects `set -o pipefail`; the bash prefix is the same
# workaround android-device's run-instrumented.sh already documents).
set -euo pipefail

adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c
# Collect the verdict AND keep the frames. `| tee ... || true` used to swallow
# the instrumentation failure, so a run whose own suite asserted "the audit could
# not reach 3 screen(s)" still reported SUCCESS and the artifact shipped with
# MISSING-* frames in it. Capture PIPESTATUS, do every copy tolerantly, and only
# then exit with the suite's status.
set +e
adb shell am instrument -w \
  -e class com.grapsee.gsai.GsScreenshotSuite \
  com.grapsee.gsai.test/androidx.test.runner.AndroidJUnitRunner \
  | tee instrumentation-out.txt
INSTRUMENT_STATUS=${PIPESTATUS[0]}
set -e
# Screenshots live in the INSTRUMENTATION package's INTERNAL files dir
# (scoped storage: the test package's external dirs proved unreliable and the
# app's Android/data is not adb-readable). run-as works because the test APK
# is debuggable; strip the CRLF adb adds to ls output.
mkdir -p device-artifacts/screens
adb shell run-as com.grapsee.gsai mkdir -p files/screens || true
adb shell run-as com.grapsee.gsai ls files/screens || true
SHOTS=$(adb shell run-as com.grapsee.gsai ls files/screens | tr -d '\r' || true)
for f in $SHOTS; do
  [ -n "$f" ] || continue
  adb exec-out run-as com.grapsee.gsai cat "files/screens/$f" \
    > "device-artifacts/screens/$f" 2>/dev/null || true
done
adb logcat -d -v threadtime > device-artifacts/logcat.txt || true
ls -la device-artifacts/screens || true

echo "instrumentation status: $INSTRUMENT_STATUS"
exit "$INSTRUMENT_STATUS"
