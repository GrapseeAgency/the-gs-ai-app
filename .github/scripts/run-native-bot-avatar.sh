#!/usr/bin/env bash
set -euo pipefail
cd android
gradle :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --stacktrace
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# Motion is enabled only on the CI test emulator. The production app still
# honors each user's reduced-motion setting.
adb shell settings put global animator_duration_scale 1
mkdir -p ../native-avatar-evidence
set +e
adb shell am instrument -w -r \
  -e class com.grapsee.gsai.BotAvatarWebViewDeviceTest \
  com.grapsee.gsai.test/androidx.test.runner.AndroidJUnitRunner \
  | tee ../native-avatar-evidence/instrumentation.txt
instrument_status=${PIPESTATUS[0]}
set -e
if ! grep -Fq 'OK (1 test)' ../native-avatar-evidence/instrumentation.txt; then instrument_status=1; fi
cd ..
# Preserve all evidence, even if cadence or visibility failed mid-test.
for name in original-avatar-idle.png original-avatar-working-1.png original-avatar-working-2.png original-avatar-settled.png $(printf 'avatar-motion-%02d.png ' {0..11}); do
  if ! adb exec-out run-as com.grapsee.gsai cat "files/native-avatar-evidence/$name" > "native-avatar-evidence/$name"; then
    echo "Required screenshot missing: $name"
    rm -f "native-avatar-evidence/$name"
    instrument_status=1
    continue
  fi
  if ! python3 - "$name" <<'PY'
from pathlib import Path
import sys, struct
p = Path("native-avatar-evidence") / sys.argv[1]
b = p.read_bytes()
assert b[:8] == b"\x89PNG\r\n\x1a\n", f"invalid native PNG {p}"
w, h = struct.unpack(">II", b[16:24])
if p.name.startswith("avatar-motion-"):
    assert w >= 32 and h == w, f"unexpected avatar crop {w}x{h}"
else:
    assert w >= 300 and h >= 600, f"unexpected emulator frame size {w}x{h}"
print("Verified native screenshot:", p, w, h, len(b))
PY
  then
    instrument_status=1
  fi
done
exit "$instrument_status"
