#!/usr/bin/env bash
set -euo pipefail
# Run instrumentation directly so Android remains installed while evidence
# is copied. Gradle connectedDebugAndroidTest uninstalls the test target during
# its cleanup, making run-as report "unknown package" even after 1/1 tests PASS.
cd android
gradle :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --stacktrace
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
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
for name in original-avatar-debug-idle.png original-avatar-idle.png original-avatar-working-1.png original-avatar-working-2.png; do
  if ! adb exec-out run-as com.grapsee.gsai cat "files/native-avatar-evidence/$name" > "native-avatar-evidence/$name"; then
    echo "Screenshot missing (expected only if test failed early): $name"
    rm -f "native-avatar-evidence/$name"
    continue
  fi
  test "$(wc -c < "native-avatar-evidence/$name")" -gt 20000 || continue
  python3 - "$name" <<'PY'
from pathlib import Path
import sys, struct
p = Path("native-avatar-evidence") / sys.argv[1]
b = p.read_bytes()
assert b[:8] == b"\x89PNG\r\n\x1a\n", f"invalid native PNG {p}"
w, h = struct.unpack(">II", b[16:24])
assert w >= 300 and h >= 600, f"unexpected emulator frame size {w}x{h}"
print("Verified native screenshot:", p, w, h, len(b))
PY
done

exit "$instrument_status"
