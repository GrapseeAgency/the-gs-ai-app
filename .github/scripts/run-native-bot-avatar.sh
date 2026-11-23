#!/usr/bin/env bash
set -euo pipefail
# android-emulator-runner executes each YAML script line in a fresh shell.
# Keep cd, gradle, screenshot retrieval in THIS single bash process.
cd android
gradle :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.grapsee.gsai.BotAvatarWebViewDeviceTest \
  --no-daemon --stacktrace
cd ..
mkdir -p native-avatar-evidence
for name in original-avatar-idle.png original-avatar-working-1.png original-avatar-working-2.png; do
  adb exec-out run-as com.grapsee.gsai cat "files/native-avatar-evidence/$name" > "native-avatar-evidence/$name"
  test "$(wc -c < "native-avatar-evidence/$name")" -gt 20000
done
