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
echo "=== 0b. model.gguf is present in the workspace ==="
# It is NOT pushed yet. It cannot be: the app does not exist at this point, so its
# external files directory does not exist either. See step 2b, after the install.
if [ -f "$GITHUB_WORKSPACE/model.gguf" ]; then
  ls -l "$GITHUB_WORKSPACE/model.gguf"
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
echo "=== 2b. put the model where the app is allowed to read it ==="
# NOT /sdcard. Raw, run 36420986162:
#     java.io.FileNotFoundException: /sdcard/qwen2.5-0.5b-instruct-q4_k_m.gguf:
#     open failed: EACCES (Permission denied)
# On API 30 an arbitrary file at the root of /sdcard is outside every media
# collection, so READ_EXTERNAL_STORAGE would not grant it even if it were
# declared -- and the app deliberately declares no storage permission at all.
#
# The app's OWN external files dir is readable with no permission whatsoever, and
# it only exists once the app is installed, which is why this is step 2b and not
# step 0b. It also needs no permission on a real device, so this path is what a
# user's own model install will use too -- the test exercises the real location
# rather than a CI-only one.
# THREE PLACES TRIED, IN ORDER, because each failed differently on API 30:
#
#   1. the app's PRIVATE dir, reached with run-as (a debug APK is debuggable)
#   2. /sdcard/Android/data/<pkg>/files  -- the adb shell user CANNOT create it:
#          mkdir: '/sdcard/Android/data/com.grapsee.gsai': Permission denied
#      (run 36430187499)
#   3. /sdcard -- the app gets EACCES reading it (run 36420986162), because an
#      arbitrary file at the root of /sdcard is outside every media collection
#      and this app declares no storage permission at all
#
# Only (1) is expected to work. (2) and (3) remain as diagnostics: if (1) fails,
# the log shows exactly which barrier applies instead of a test failing later
# with a permission error that names nothing.
DEV_MODEL=""
MODEL_NAME=qwen2.5-0.5b-instruct-q4_k_m.gguf
if [ -f "$GITHUB_WORKSPACE/model.gguf" ]; then
  echo "pushing model.gguf (491 MB on an emulator; be patient)"
  adb push "$GITHUB_WORKSPACE/model.gguf" /data/local/tmp/$MODEL_NAME >/dev/null
  adb shell chmod 644 /data/local/tmp/$MODEL_NAME || true
  if adb shell run-as com.grapsee.gsai mkdir -p files 2>&1 | head -2 &&
     adb shell run-as com.grapsee.gsai cp /data/local/tmp/$MODEL_NAME files/$MODEL_NAME 2>&1 | head -2; then
    DEV_MODEL="/data/data/com.grapsee.gsai/files/$MODEL_NAME"
    echo "pushed into the app's private dir via run-as"
  else
    echo "run-as route failed; recording the alternative barriers for diagnosis:"
    echo "  2. $(adb shell mkdir -p /sdcard/Android/data/com.grapsee.gsai/files 2>&1 | head -1)"
    echo "  3. $(adb shell run-as com.grapsee.gsai ls -la /sdcard/$MODEL_NAME 2>&1 | head -1)"
    DEV_MODEL=""
  fi
  echo "in-app listing: $(adb shell run-as com.grapsee.gsai ls -la files/ 2>&1 | tail -2 || true)"
else
  echo "no model.gguf; model-backed tests will SKIP rather than fail"
fi

echo
echo "=== 2c. the OCR fixture ==="
# WITHOUT THIS THE OCR TEST CANNOT RUN, AND IT SKIPS RATHER THAN FAILS. Raw, run
# 36545847058:
#     NOTE: no ImageMagick on this runner. The OCR test needs it to draw the
#     java.lang.AssertionError: fixture image missing at
#       /storage/emulated/0/Android/data/com.grapsee.gsai/files/Pictures/invoice.png
# a2 SKIPPED, so that run said nothing whatsoever about OCR.
#
# GENERATED, NOT COMMITTED. A committed PNG is a binary in the repository, and
# the marker would then be a constant in two places free to drift apart. Drawing
# it here means the string the test asserts on and the string in the image are
# the same edit.
#
# PURE PYTHON, NOT IMAGEMAGICK. `convert` is not installed on the runner, and
# adding it would mean sudo in a workflow that is supposed to build without it.
# make_invoice_png.py rasterises a 5x7 bitmap font and writes the PNG with zlib
# and struct from the standard library, so this works on any runner that can run
# the tests at all.
FIXTURE_DIR=/storage/emulated/0/Android/data/com.grapsee.gsai/files/Pictures
FIXTURE_NAME=invoice.png
MARKER="INVOICE INV-4471 DUE 2026-03-01"
if python3 "$GITHUB_WORKSPACE/.github/scripts/make_invoice_png.py" /tmp/invoice.png "$MARKER"; then
  echo "  marker: $MARKER"
  file /tmp/invoice.png || true
  adb shell mkdir -p "$FIXTURE_DIR" 2>&1 | head -2 || true
  if adb push /tmp/invoice.png "$FIXTURE_DIR/$FIXTURE_NAME" >/dev/null 2>&1; then
    echo "  pushed to $FIXTURE_DIR/$FIXTURE_NAME"
    echo "  on device: $(adb shell ls -l "$FIXTURE_DIR/$FIXTURE_NAME" 2>&1 | tr -d '\r')"
  else
    echo "  WARNING: push failed; the OCR test will name the cause"
  fi
else
  # Do not pretend. A missing fixture must read as a missing fixture.
  echo "  ERROR: could not generate the fixture, so a2 will SKIP and say why."
fi

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
# NO -Pandroid.testInstrumentationRunnerArguments... here. Gradle rejects it
# against the configuration cache and the argument silently never arrives:
#     Passing custom test runner argument
#     android.testInstrumentationRunnerArguments.gs_test_model from gradle
#     properties or command line is not compatible with configuration caching.
# DeviceVerificationTest.findModel() searches /sdcard first, which is exactly
# where the push above puts it, so the argument bought nothing and cost a
# silently-missing input.
#
# ONE LINE, NO BACKSLASH CONTINUATION. This was written as a continuation with
# the comments placed after the `\`, which is a two-line trap: `\`+newline joins
# the next line onto this one, the `#` then comments out everything to the end of
# the JOINED line, gradle silently never ran, and the task name became a command:
#     .github/scripts/run-instrumented.sh: line 149: :app:connectedDebugAndroidTest: command not found
#     connectedDebugAndroidTest exit: 127
# Raw, run 36414439107. It cost a booted emulator and a 5-minute APK build.
gradle --no-daemon --console=plain :app:connectedDebugAndroidTest 2>&1 | tee connected.log
TEST_RC=${PIPESTATUS[0]}
set -e
echo "connectedDebugAndroidTest exit: $TEST_RC"

# 127 is "command not found", which is what a swallowed gradle invocation looks
# like from here. Naming it means the next reader does not have to guess.
if [ "$TEST_RC" = "127" ]; then
  echo "FAIL: gradle was never invoked (exit 127). A backslash continuation"
  echo "      followed by a comment line comments out the rest of the joined"
  echo "      line. Check the gradle invocation above."
  TEST_RC=1
fi
if [ ! -s connected.log ]; then
  echo "FAIL: connected.log is empty, so no test output exists at all."
  TEST_RC=1
fi

# A run in which ZERO tests execute is a FAILURE, whatever exit code Gradle
# returned. This exact false pass is why the guard exists: run 36411916910 was
# green in all nine steps, reported "Starting 0 tests on emulator-5554", and
# exit 0. Only `testInstrumentationRunner` was missing.
RAN=$(grep -oE 'Starting [0-9]+ tests?' connected.log | grep -oE '[0-9]+' | tail -1 || true)
RAN=${RAN:-0}
echo "tests the runner actually started: $RAN"
if [ "$RAN" = "0" ]; then
  echo "FAIL: the runner started ZERO tests. This is not a pass; it is an empty run."
  echo "      Check testInstrumentationRunner in :app defaultConfig -- without it"
  echo "      the androidTest APK has no runner and discovery returns nothing."
  TEST_RC=1
fi

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
