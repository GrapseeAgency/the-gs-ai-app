#!/usr/bin/env bash
set -euo pipefail
cd android
gradle :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --stacktrace
adb wait-for-device
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell settings put global animator_duration_scale 1
mkdir -p ../native-avatar-evidence
# Keep system diagnostics even if Android crashes or ADB disconnects.
adb logcat -c
adb logcat -v threadtime > ../native-avatar-evidence/logcat.txt 2>&1 &
LOG_PID=$!
trap 'kill "$LOG_PID" 2>/dev/null || true' EXIT
set +e
timeout 150 adb shell am instrument -w -r \
  -e class com.grapsee.gsai.BotAvatarNativeDeviceTest \
  com.grapsee.gsai.test/androidx.test.runner.AndroidJUnitRunner \
  | tee ../native-avatar-evidence/instrumentation.txt
instrument_status=${PIPESTATUS[0]}
set -e
if ! grep -Fq 'OK (1 test)' ../native-avatar-evidence/instrumentation.txt; then instrument_status=1; fi
cd ..
if ! adb get-state | grep -q '^device$'; then
  echo 'Android emulator disconnected; no visual pass can be claimed.'
  exit 1
fi
# Preserve actual hardware window evidence even if a later assertion fails.
for name in original-avatar-waiting.png original-avatar-working-1.png original-avatar-working-2.png original-avatar-settled.png $(printf 'avatar-motion-%02d.png ' {0..11}); do
  out="native-avatar-evidence/$name"
  if ! adb exec-out run-as com.grapsee.gsai cat "files/native-avatar-evidence/$name" > "$out"; then
    rm -f "$out"
    continue
  fi
  if ! python3 - "$out" <<'PY'
from pathlib import Path
import sys, struct
p = Path(sys.argv[1])
b = p.read_bytes()
assert len(b) > 24 and b[:8] == b"\x89PNG\r\n\x1a\n", f"invalid native PNG {p}"
w, h = struct.unpack(">II", b[16:24])
if p.name.startswith("avatar-motion-"):
    assert w >= 32 and h == w
else:
    assert w >= 300 and h >= 600
print("Hardware-window screenshot:", p, w, h, len(b))
PY
  then
    rm -f "$out"
  fi
done
for required in original-avatar-waiting.png original-avatar-working-1.png original-avatar-working-2.png original-avatar-settled.png; do
  test -s "native-avatar-evidence/$required" || instrument_status=1
done
exit "$instrument_status"
