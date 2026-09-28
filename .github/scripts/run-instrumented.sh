#!/usr/bin/env bash
# run-instrumented.sh — executed INSIDE the emulator runner, on the runner host
# with ADB available.
#
# Three things happen here, in order, and each one leaves evidence behind:
#
#   1. build the APK and the test APK
#   2. install them
#   3. run the instrumented tests, then dump logcat and screenshots whatever
#      the test outcome
#
# The logcat dump runs in an `always` trap rather than after a successful test.
# A test failure with no logcat is the single most expensive thing to debug
# remotely, and the emulator is destroyed the moment the step ends, so anything
# not collected in the step is gone.
#
# The build uses the prebuilt llama.cpp when it is present, so the tests can
# exercise real generation instead of the GS_ERR_UNAVAILABLE path. Without it
# they still RUN and still assert -- they just assert the portable behaviour,
# which is a real thing to assert. The distinction is printed either way.

set -uo pipefail

# Never lose the emulator's log because a test failed.
dump_evidence() {
  local rc=$?
  echo "=== dumping logcat (test exit was $rc) ==="
  adb logcat -d -v threadtime > emulator-logcat.txt 2>/dev/null || \
    echo "(logcat unavailable)"
  wc -l emulator-logcat.txt 2>/dev/null || true
  echo "=== last 120 lines of logcat ==="
  tail -120 emulator-logcat.txt 2>/dev/null || true

  # Screenshots. A screenshot of a failed assertion is often the fastest way to
  # see that a dialog never appeared, or that a spinner never stopped.
  mkdir -p device-artifacts
  adb exec-out screencap -p > device-artifacts/00-boot.png 2>/dev/null || true

  echo "=== installed packages (proves install happened) ==="
  adb shell pm list packages 2>/dev/null | grep -i grapsee || echo "(none)"

  echo "=== gs_ffi native library on the device ==="
  adb shell 'ls -la /data/app/*/com.grapsee.gsai*/lib/* 2>/dev/null' || true
  return $rc
}
trap dump_evidence EXIT

cd "$GITHUB_WORKSPACE"

echo "=== 0. prebuilt llama.cpp, if the deps workflow produced one ==="
PREBUILT=""
for D in native/prebuilts/android-arm64 native/prebuilts/android-x86_64; do
  if [ -f "$D/include/llama.h" ]; then PREBUILT="$D"; break; fi
done
if [ -n "$PREBUILT" ]; then
  echo "using prebuilt llama.cpp from $PREBUILT"
  ls -la "$PREBUILT/lib" 2>/dev/null || true
  export GS_LLAMA_PREBUILT="$GITHUB_WORKSPACE/$PREBUILT"
else
  echo "NO prebuilt llama.cpp found; the build is PORTABLE-ONLY and every"
  echo "generation call will report GS_ERR_UNAVAILABLE. The tests will run and"
  echo "will assert that. That is a real assertion, but it is not the same as"
  echo "exercising generation, and the output says which happened."
  find native/prebuilts -maxdepth 3 2>/dev/null | sed -n '1,20p' || true
fi

# Push the model onto the device. It goes to the app's EXTERNAL files dir rather
# than internal storage because adb cannot write into /data/data without root,
# and the test looks in the external dir first.
echo
echo "=== 0a. THE EMULATOR MUST BE BOOTED AND THE RIGHT ABI ==="
# A test that runs on a half-booted emulator produces fake passes, and a test
# that runs on the wrong ABI skips every native path and looks green. Both are
# asserted here, before anything else, and a failure stops the run rather than
# proceeding.
BOOT=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
echo "  sys.boot_completed = '${BOOT:-<empty>}'"
if [ "$BOOT" != "1" ]; then
  echo "FAIL: the emulator has not finished booting (sys.boot_completed=${BOOT:-empty})"
  echo "      Running tests now would produce fake passes."
  adb devices -l || true
  exit 1
fi
ABI=$(adb shell getprop ro.product.cpu.abi 2>/dev/null | tr -d '\r' || true)
MACH=$(adb shell uname -m 2>/dev/null | tr -d '\r' || true)
SDKV=$(adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || true)
echo "  ro.product.cpu.abi = ${ABI:-<empty>}"
echo "  uname -m            = ${MACH:-<empty>}"
echo "  sdk                 = ${SDKV:-<empty>}"
if [ "$MACH" != "x86_64" ]; then
  echo "FAIL: expected an x86_64 emulator, got '${MACH:-empty}'."
  echo "      An arm64 .so would be rejected by the linker and every native"
  echo "      test would SKIP rather than FAIL."
  exit 1
fi
echo "  emulator is booted and x86_64 -- proceeding"

echo
echo "=== 0b. put the model on the device ==="
DEV_MODEL=""
if [ -f "$GITHUB_WORKSPACE/model.gguf" ]; then
  echo "pushing model.gguf (this is slow on an emulator; be patient)"
  adb push "$GITHUB_WORKSPACE/model.gguf" /sdcard/qwen2.5-0.5b-instruct-q4_k_m.gguf || true
  DEV_MODEL=/sdcard/qwen2.5-0.5b-instruct-q4_k_m.gguf
  echo "on device: $(adb shell ls -la /sdcard/qwen2.5-0.5b-instruct-q4_k_m.gguf 2>&1)"
else
  echo "no model.gguf; model-backed tests will SKIP rather than fail"
fi

echo
echo "=== 1. build the APK and the instrumented-test APK ==="
cd android
if gradle --no-daemon --console=plain :app:assembleDebug :app:assembleDebugAndroidTest; then
  echo "build OK"
else
  echo "BUILD FAILED -- the tests cannot run, and that is reported as a failure"
  exit 1
fi
ls -la app/build/outputs/apk/debug/ 2>/dev/null || true

echo
echo "=== 2. install ==="
adb install -r -t app/build/outputs/apk/debug/app-debug.apk || exit 1
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk || exit 1
adb shell pm list packages | grep -i grapsee || true

echo
echo "=== 3. the device's own view of the ABI ==="
adb shell getprop ro.product.cpu.abi
adb shell getprop ro.build.version.sdk
# The point of the x86_64 matrix entry in android-deps.yml: this must be x86_64
# for the prebuilt to be the one that loads.
echo "note: the app's native libs must be x86_64 to run here; an arm64 .so"
echo "      would be rejected by the linker and every test would SKIP rather"
echo "      than fail, which is why that check exists."

echo
echo "=== 4. instrumented tests ==="
set +e
gradle --no-daemon --console=plain \
  :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.gs_test_model="${GS_TEST_MODEL:-$DEV_MODEL}" 2>&1 | tee connected.log
TEST_RC=${PIPESTATUS[0]}
set -e
echo "connectedDebugAndroidTest exit: $TEST_RC"

echo
echo "=== 5. what ran, what skipped, what failed ==="
# A skipped test is not a pass and is not a failure. The distinction is the whole
# point of reporting rather than exiting 0.
for f in app/build/outputs/androidTest-results/connected/*.xml; do
  [ -e "$f" ] || continue
  echo "--- $(basename "$f") ---"
  python3 - "$f" <<'PY' 2>/dev/null || grep -oE '<testsuite[^>]*' "$f" | head -5
import sys, xml.etree.ElementTree as ET
try:
    t = ET.parse(sys.argv[1])
except Exception as e:
    print("  (could not parse:", e, ")")
    sys.exit(0)
r = t.getroot()
for s in r.iter("testsuite"):
    print("  %-52s tests=%s failures=%s errors=%s skipped=%s" % (
        s.get("name","?"), s.get("tests"), s.get("failures"),
        s.get("errors"), s.get("skipped")))
    for tc in s.iter("testcase"):
        st = "PASS"
        for tag, lab in (("failure","FAIL"), ("error","ERROR"), ("skipped","SKIP")):
            if tc.find(tag) is not None:
                st = lab
        if st != "PASS":
            print("      %-6s %s" % (st, tc.get("name")))
            for tag in ("failure","error","skipped"):
                el = tc.find(tag)
                if el is not None and el.get("message"):
                    print("             %s" % el.get("message")[:300])
PY
done
[ -e app/build/outputs/androidTest-results/connected ] || \
  echo "  (no XML results directory: the tests did not run)"

echo
echo "=== 6. grep logcat for the native bridge ==="
grep -E 'GsNativeLoader|GsNativeTest|ModelStore|ModelDownloader|ChatRepository|UnsatisfiedLinkError|GS_ERR|gs_mobile' emulator-logcat.txt 2>/dev/null | tail -60 || \
  echo "(no matching lines)"

echo
echo "=== 7. final screenshot ==="
adb exec-out screencap -p > device-artifacts/02-after-tests.png 2>/dev/null || true
ls -la device-artifacts/ 2>/dev/null || true

exit $TEST_RC
