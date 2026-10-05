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
# PIN THE GRADLE, DO NOT BORROW THE RUNNER'S. The runner image ships Gradle
# 9.8.0, and every AGP 8.x dies on it at plugin apply --
#     Plugin 'com.android.internal.application' relies on
#     'org.gradle.api.problems.internal.InternalProblems', a Gradle internal
#     API that was removed in Gradle 9.6.0.
# Raw, android-app run 37282692340 and android-device run 37282851304
# ("Welcome to Gradle 9.8.0!" straight into that failure). The repo carries no
# gradle-wrapper.jar BY DESIGN (no committed binaries), so the wrapper is
# GENERATED here from the runner's own Gradle and every invocation below goes
# through ./gradlew, which downloads the distribution the properties file pins
# (gradle-8.13-bin.zip). The generated jar lives only on the runner.
# GENERATED IN AN EMPTY DIRECTORY, NOT IN THIS ONE (raw: android-app run
# 37283167572 -- `gradle wrapper` inside the project configures the build,
# applies AGP, and dies on the runner's Gradle 9.8 before generating anything).
# An EMPTY settings.gradle: Gradle 9 refuses the wrapper task in a
# directory with no build (raw: run 37284603107, "does not contain a
# Gradle build"), and an empty one has no plugins to apply, so the
# runner's Gradle 9.8 cannot trip over AGP here.
mkdir -p /tmp/gs-wrapper-gen
touch /tmp/gs-wrapper-gen/settings.gradle
cd /tmp/gs-wrapper-gen
if ! gradle wrapper --gradle-version 8.13 --distribution-type bin --no-daemon; then
  echo "FAIL: could not generate the Gradle 8.13 wrapper"; exit 1
fi
cd "$OLDPWD"
cp /tmp/gs-wrapper-gen/gradlew /tmp/gs-wrapper-gen/gradlew.bat .
chmod +x gradlew
# The jar is the only missing piece; the properties file is COMMITTED and is
# the source of truth for the pinned version.
cp /tmp/gs-wrapper-gen/gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.jar
./gradlew --version | sed -n '1,9p'
if ! ./gradlew --no-daemon --console=plain :app:assembleDebug :app:assembleDebugAndroidTest; then
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
echo
echo
echo "=== 2e. the lever-4 quantisations ==="
# FOUR files, ~2.1 GB, onto an emulator. The loop is here so the four push the
# same way: the same run-as route, the same size verification, and a name that
# the device test can compute rather than guess.
#
# EACH IS SIZE-CHECKED ON THE DEVICE. A truncated adb push reports success, and
# llama_model_load_from_file on a truncated GGUF reports a corrupt model -- so
# without this the failure appears as a MODEL fault when it is a TRANSFER fault.
# The expected size is the one in ModelCatalog.kt, not a number typed here.
push_gguf() {
  local FILE="$1" WANT="$2"
  if [ ! -f "$GITHUB_WORKSPACE/$FILE" ]; then
    echo "  SKIP    $FILE (not on the runner)"
    return 0
  fi
  adb push "$GITHUB_WORKSPACE/$FILE" /data/local/tmp/$FILE >/dev/null
  adb shell chmod 644 /data/local/tmp/$FILE || true
  adb shell run-as com.grapsee.gsai cp /data/local/tmp/$FILE "files/$FILE" 2>&1 | head -1
  local DSZ
  DSZ=$(adb shell run-as com.grapsee.gsai stat -c%s "files/$FILE" 2>/dev/null | tr -d '\r' || echo 0)
  if [ "${DSZ:-0}" = "$WANT" ]; then
    echo "  OK      $FILE  $DSZ bytes"
  else
    # Deleted rather than left, so the test SKIPS it instead of loading a
    # partial file and reporting a corrupt model.
    echo "  WRONG   $FILE on-device ${DSZ:-0} != $WANT; removing so it is not measured"
    adb shell run-as com.grapsee.gsai rm -f "files/$FILE" || true
  fi
  adb shell rm -f /data/local/tmp/$FILE || true
}

# q4_k_m is FIRST so the control exists even if a later push fails.
push_gguf qwen2.5-0.5b-instruct-q4_k_m.gguf 491400032
push_gguf qwen2.5-0.5b-instruct-q4_0.gguf   428730208
push_gguf qwen2.5-0.5b-instruct-q5_k_m.gguf 522186592
push_gguf qwen2.5-0.5b-instruct-q8_0.gguf   675710816

# WHAT THE APK ACTUALLY CONTAINS, BEFORE ANYTHING IS INSTALLED.
#
# Run 36971941114 failed every native test with
#     dlopen failed: library "libc++_shared.so" not found
# while the log showed the companion being COPIED into jniLibs:
#     -rw-r--r-- 1 runner runner  6447640 Oct  2 06:07 libc++_shared.so
#     -rw-r--r-- 1 runner runner 42356592 Oct  2 06:07 libgs_ffi.so
# and Gradle reporting it as packaged. So the file was written, merged, and
# still not found, and "not found" from a linker is ambiguous between
#   * the APK does not contain it
#   * the APK contains it and the linker could not use it (compressed, wrong ABI)
#   * the library that needs it is not the one being inspected
#
# `unzip -l` settles the first two in one line and costs nothing. The APK's own
# listing is the only primary evidence that exists for this question.
echo
echo "=== the APK's native entries, and what libgs_ffi.so needs ==="
APK=$(find "$GITHUB_WORKSPACE/android/app/build/outputs/apk" -name '*.apk' 2>/dev/null | head -1 || true)
SO_JNI="$GITHUB_WORKSPACE/android/app/src/main/jniLibs/$ABI/libgs_ffi.so"
if [ -z "$APK" ]; then
  echo "  no APK found under build/outputs/apk -- the build did not produce one"
  find "$GITHUB_WORKSPACE/android/app/build/outputs" -maxdepth 3 2>/dev/null | sed -n '1,12p' | sed 's/^/    /'
elif [ ! -f "$SO_JNI" ]; then
  echo "  no jniLibs .so to read DT_NEEDED from: $SO_JNI"
else
  echo "  apk: $APK"
  echo "  every lib/<abi>/ entry it contains:"
  unzip -l "$APK" 2>/dev/null | grep -E 'lib/[^/]+/.*\.so$' | awk '{printf "    %10s  %s
", $1, $4}' | sort -k2
  NEEDED=$(readelf -d "$SO_JNI" 2>/dev/null | sed -n 's/.*(NEEDED).*\[\(.*\)\]/\1/p')
  echo "  libgs_ffi.so DT_NEEDED: $NEEDED"
  for N in $NEEDED; do
    case "$N" in
      libc.so|libm.so|libdl.so|liblog.so|libandroid.so|libz.so|libjnigraphics.so|libEGL.so|libGLESv2.so|libGLESv3.so|libOpenSLES.so|libaaudio.so)
        continue ;;
    esac
    if unzip -l "$APK" 2>/dev/null | grep -q "lib/$ABI/$N$"; then
      echo "    OK       $N is in the APK at lib/$ABI/"
    else
      echo "    ABSENT   $N is NOT in the APK at lib/$ABI/ -- THIS IS WHY dlopen failed"
    fi
  done
  echo "  is the APK's copy of each .so STORED (uncompressed)? A DEFLATED .so"
  echo "  cannot be loaded straight out of the APK when extractNativeLibs is"
  echo "  false, and the linker says only 'not found':"
  # `unzip -Z`, NOT `unzip -lv`. The -v listing is
  #     Length  Date  Time  Name
  # i.e. FOUR fields, so an awk that compared $1 to $7 compared a length to
  # nothing and printed DEFLATED for a file created with ZIP_STORED. The
  # difference is invisible unless you read a file you know the answer for,
  # which is why both a stored and a deflated fixture are built here.
  # `unzip -Z` is perm ver os size flags METHOD date time name, so the method
  # is $6 and the name is $NF.
  unzip -Z "$APK" 2>/dev/null | grep -E 'lib/[^/]+/(libgs_ffi|libc\+\+_shared)\.so$' \
    | awk '{ printf "    %-48s %s\n", $NF, ($6=="stor" ? "STORED" : "DEFLATED (" $6 ")") }'
fi

echo "in-app gguf listing:"
adb shell run-as com.grapsee.gsai ls -la files/ 2>&1 | grep -E 'gguf|safetensors' | sed 's/^/    /' || true

echo "=== 2d. the SDXS-512 diffusion checkpoint ==="
# 882 MB, and the filename must be EXACTLY DiffusionCatalog.destination()'s
# basename, because the device test looks it up by that name in the app's private
# dir -- the same convention the GGUF uses, and the same reason: a test that
# searches for a file whose name it computes is not searching for anything.
#
# The FILENAME IS NOT THE ONLY THING THAT HAS TO MATCH. A .safetensors handed to
# llama_model_load_from_file produces a corrupt-model error, so the diffusion test
# passes its own path to sdCreate and never lets the two look interchangeable.
CHECKPOINT_NAME="sdxs-512-dreamshaper.safetensors"
if [ "${GS_SD_ABSENT:-false}" = "true" ] || [ ! -f "$GITHUB_WORKSPACE/$CHECKPOINT_NAME" ]; then
  echo "no verified checkpoint on the runner (GS_SD_ABSENT=${GS_SD_ABSENT:-unset})"
  echo "the diffusion test will SKIP, loudly, rather than pass vacuously"
else
  echo "pushing $CHECKPOINT_NAME (882 MB on an emulator; this is slow)"
  adb push "$GITHUB_WORKSPACE/$CHECKPOINT_NAME" /data/local/tmp/$CHECKPOINT_NAME
  adb shell chmod 644 /data/local/tmp/$CHECKPOINT_NAME || true
  # Same run-as route as the GGUF, for the same reason: /sdcard is EACCES on
  # API 30 and the adb shell user cannot create the app's external dir.
  adb shell run-as com.grapsee.gsai mkdir -p files 2>&1 | head -2
  if adb shell run-as com.grapsee.gsai cp /data/local/tmp/$CHECKPOINT_NAME "files/$CHECKPOINT_NAME" 2>&1 | head -2; then
    echo "checkpoint in the app private dir:"
    adb shell run-as com.grapsee.gsai ls -la "files/$CHECKPOINT_NAME" 2>&1 | sed 's/^/    /'
  else
    echo "::warning::run-as copy of the checkpoint failed; the diffusion test will SKIP"
  fi
  # THE DEVICE COPY MUST BE THE SAME SIZE as the verified one. A truncated adb
  # push that reports success would otherwise surface as a diffusion failure ten
  # minutes later, pointing at stable-diffusion.cpp instead of at adb.
  DSZ=$(adb shell run-as com.grapsee.gsai stat -c%s "files/$CHECKPOINT_NAME" 2>/dev/null | tr -d '\r' || echo 0)
  case "${DSZ:-0}" in
    882587118) echo "checkpoint size verified on the device: $DSZ" ;;
    *) echo "::warning::on-device checkpoint size is '${DSZ:-0}', not 882587118."
       echo "::warning::The diffusion test will SKIP rather than load a partial file." ;;
  esac
  # Free the copy the app cannot read, so the device does not carry it twice.
  adb shell rm -f /data/local/tmp/$CHECKPOINT_NAME || true
fi

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
# THE APP'S PRIVATE DIR, REACHED WITH run-as. NOT getExternalFilesDir().
#
# The generator works -- run 36566952196 printed
#   wrote /tmp/invoice.png  1158x90  text='INVOICE INV-4471 DUE 2026-03-01'
# -- and the push still did not happen, because the adb shell user cannot create
# the app's external directory. Raw, from the same run's earlier history:
#   mkdir: '/sdcard/Android/data/com.grapsee.gsai': Permission denied
#
# That directory is created by the SYSTEM when the app first touches external
# storage, and by then the test is already running. The app's PRIVATE dir needs no
# permission at all, and a debug APK is debuggable, so run-as can write into it.
# This is the same route the 491 MB model already takes successfully:
#   model = /data/user/0/com.grapsee.gsai/files/qwen2.5-0.5b-instruct-q4_k_m.gguf
FIXTURE_NAME=invoice.png
MARKER="INVOICE INV-4471 DUE 2026-03-01"
if python3 "$GITHUB_WORKSPACE/.github/scripts/make_invoice_png.py" /tmp/invoice.png "$MARKER"; then
  echo "  marker: $MARKER"
  file /tmp/invoice.png || true
  adb push /tmp/invoice.png /data/local/tmp/$FIXTURE_NAME >/dev/null 2>&1
  adb shell chmod 644 /data/local/tmp/$FIXTURE_NAME || true
  adb shell run-as com.grapsee.gsai mkdir -p files 2>&1 | head -2 || true
  if adb shell run-as com.grapsee.gsai cp /data/local/tmp/$FIXTURE_NAME "files/$FIXTURE_NAME" 2>&1 | head -2; then
    echo "  placed in the app's private dir via run-as"
    echo "  in-app listing: $(adb shell run-as com.grapsee.gsai ls -l "files/$FIXTURE_NAME" 2>&1 | tr -d '\r')"
  else
    echo "  WARNING: run-as copy failed; the OCR test will name the cause"
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
./gradlew --no-daemon --console=plain :app:connectedDebugAndroidTest 2>&1 | tee connected.log
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

# THE EXPECTED COUNT IS COUNTED FROM THE SOURCES, NEVER WRITTEN DOWN.
#
# This used to be `if [ "$RAN" = "0" ]` -- a floor, and a floor is the weakest
# possible gate. It cannot tell "every test ran" from "one test ran", so if
# discovery silently found only one class, or a runner argument narrowed the run,
# or an APK shipped with a stale test DEX, the check passed and the run was green
# with 35 of 36 tests never executed.
#
# A green run that tested one thing is worse than a red one, so the number is
# DERIVED the same way the 18-symbol ABI floor is derived from the declared ABI
# rather than written down beside it. Adding a test and not seeing this fail would
# mean the new test never runs -- which is precisely the defect this detects, and
# precisely what happens if the expected count is a literal.
#
# `^\s*@Test`, not a bare `@Test`: a docstring that says "@Test" must not be
# counted as a test, or adding a sentence to a comment would break the build. Both
# counts happen to be 36 today, which is exactly why the naive form would have
# looked fine.
# ROOT CAUSE, MEASURED. This gate never executed once across three device runs, and
# the reason is not subtle once it is stated:
#
#     set -e                       <-- line 393, PRE-EXISTING
#     ...
#     IGNORED=$(grep -rhcE '^[[:space:]]*@Ignore\b' .../*.kt | awk '{s+=$1} ...')
#
# This repository has ZERO @Ignore annotations. `grep -c` exits 1 when it matches
# nothing -- correctly, that is what grep does -- and `set -o pipefail` promotes
# that 1 to the pipeline's status, and `set -e` then treats the ASSIGNMENT as fatal.
# So the script died on the line before its first echo and reported nothing:
#
#     connectedDebugAndroidTest exit: 1
#     === dumping logcat (test exit was 2) ===
#
# Reproduced with the script's own options, no runner involved:
#
#     bash -c 'set -euo pipefail; IGNORED=$(grep -rhcE "^[[:space:]]*@Ignore\b" ... | awk ...); echo AFTER'
#     before
#     exit=1            <- "AFTER" never printed
#
# This is the SAME shape as the `grep -q` SIGPIPE landmine this file already
# documents three times, inverted: there a grep that SUCCEEDED killed the step, here
# a grep that correctly found NOTHING kills it. "No matches" is a result, not an
# error, and under `set -e` a result is indistinguishable from a failure.
#
# The fix is `{ grep ... || true; } | awk ...`, NOT `grep ... | awk ... || echo 0`.
# The second form is wrong in a way `bash -n` cannot see and only running it catches:
# `|| echo 0` APPENDS to the substitution's stdout, so awk's "0" is followed by
# echo's "0", the variable becomes "0\n0", and the arithmetic dies with
#     bash: line 1: 0
#     0: arithmetic syntax error in expression (error token is "0")
# Wrapping the grep in a brace group neutralises its exit code while contributing
# nothing to stdout, so awk's value arrives exactly once. `bash -n` passed on the
# broken form; executing it did not. The marker below is FIRST, before any
# computation, so the next run says whether the block was entered at all rather than
# both "entered and failed" and "never entered" presenting as silence.
#
# Note this bug was NOT in my new logic's arithmetic -- a theory I tested and
# disproved, and recorded as disproven rather than shipped. `$(( ))` on an empty
# operand is fine in bash: `D=""; echo $((D - 0))` prints 0 and exits 0. The fault is
# `set -e` meeting a zero-match grep, one line earlier.
echo "=== test-count gate reached: runner started $RAN ==="

# THE PATH IS ANCHORED TO $GITHUB_WORKSPACE, and that is the second bug in this gate.
#
# Run 37200141082 executed the gate for the first time and reported:
#
#     === test-count gate reached: runner started 36 ===
#     tests declared in the sources    : 0 (@Ignore: 0) -> expected 0
#     NOTE: the runner started 36 test(s) for 0 @Test methods.
#
# DECLARED was 0 because the glob matched NOTHING. This script does `cd android` at
# line 110 and never comes back, so by line ~470 the working directory is
# $GITHUB_WORKSPACE/android and the workspace-relative path
# `android/app/src/androidTest/...` does not exist from there.
#
# That is the same mistake three times over in this session: a path written in one
# coordinate system and used in another -- the ios-native block-scalar indentation,
# the lint reporting body-relative line numbers for file-relative edits, and now this.
# The pattern is worth naming because it is the single most productive way to break
# something that works on a laptop.
#
# And it failed SILENTLY in the worst direction: 36 > 0 took the over-run NOTE branch,
# so a gate that had counted NOTHING printed a reassuring note. Hence the DECLARED==0
# check below -- a gate that cannot count must not pass.
GTEST="$GITHUB_WORKSPACE/android/app/src/androidTest/java/com/grapsee/gsai"
DECLARED=$( { grep -rhcE '^[[:space:]]*@Test\b' "$GTEST"/*.kt || true; } 2>/dev/null | awk '{s+=$1} END {print s+0}' )
DECLARED=${DECLARED:-0}
IGNORED=$( { grep -rhcE '^[[:space:]]*@Ignore\b' "$GTEST"/*.kt || true; } 2>/dev/null | awk '{s+=$1} END {print s+0}' )
IGNORED=${IGNORED:-0}
EXPECTED=$((DECLARED - IGNORED))
echo "tests declared in the sources    : $DECLARED (@Ignore: $IGNORED) -> expected $EXPECTED"
echo "  counted in: $GTEST"
if [ "$DECLARED" -eq 0 ]; then
  echo "FAIL: 0 @Test methods were counted in $GTEST"
  echo "      The count could NOT be computed, so this gate can make no judgement --"
  echo "      and run 37200141082 showed what happens when it proceeds anyway: 36 > 0"
  echo "      took the over-run branch and it printed a reassuring NOTE having counted"
  echo "      nothing. A check that cannot count must not pass."
  ls -1d "$GITHUB_WORKSPACE"/android/app/src/*/java/com/grapsee/gsai 2>/dev/null \
    | sed 's/^/      source sets present: /' || echo "      (none found)"
  echo "      cwd was: $PWD"
  TEST_RC=1
fi
# UNDER-RUN FAILS, OVER-RUN WARNS. The defect this exists to catch is tests
# SILENTLY NOT RUNNING, which is the under-run direction. Over-running is not a
# defect: one @Test method that drives 50 prompts internally, or a future
# @ParameterizedTest, legitimately produces more executions than annotations, and a
# gate that fails on that trains people to disable it. So the two directions are
# deliberately not treated the same.
if [ "$RAN" -lt "$EXPECTED" ] 2>/dev/null; then
  echo "FAIL: the runner started $RAN test(s) but $EXPECTED are declared in"
  echo "      android/app/src/androidTest. A partial run is not a pass."
    echo "      $((EXPECTED - RAN)) test(s) did NOT run. Usual causes:"
    echo "        - a test class failed to compile into the androidTest DEX"
    echo "        - a runner argument narrowed the run (package=/class=)"
    echo "        - the installed androidTest APK is stale"
    echo "      Which declared tests are absent from the results, by name:"
    # The method that FOLLOWS an @Test annotation -- not every `fun` in the file.
    # A bare `fun` grep also catches helpers named chunk/describe/frame/handle/
    # quote/run/score/start/stop/turn, and listing those as "did not run" would be
    # a lie in a diagnostic whose whole job is to be true.
    awk '
      /^[[:space:]]*@Test[[:space:]]*$/ { want = 1; next }
      want && /^[[:space:]]*fun[[:space:]]+[A-Za-z0-9_]+/ {
        line = $0
        sub(/^[[:space:]]*fun[[:space:]]+/, "", line)
        sub(/[( ].*$/, "", line)
        print line
        want = 0
      }
    ' android/app/src/androidTest/java/com/grapsee/gsai/*.kt | sort -u > /tmp/gs-declared-tests.txt
    : > /tmp/gs-ran-tests.txt
    for f in app/build/outputs/androidTest-results/connected/*.xml; do
      [ -e "$f" ] || continue
      python3 - "$f" 2>/dev/null >> /tmp/gs-ran-tests.txt <<'PYEOF' || true
import sys, xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    sys.exit(0)
for tc in root.iter("testcase"):
    print(tc.get("name", ""))
PYEOF
    done
    comm -23 /tmp/gs-declared-tests.txt <(sort -u /tmp/gs-ran-tests.txt) \
      | sed 's/^/        DID NOT RUN: /' | sed -n '1,40p'
  TEST_RC=1
elif [ "$EXPECTED" -gt 0 ] && [ "$RAN" -gt "$EXPECTED" ] 2>/dev/null; then
  echo "NOTE: the runner started $RAN test(s) for $EXPECTED @Test methods."
  echo "      Expected when a test method expands into several executions"
  echo "      (the 50-prompt comparison does exactly this). Not a failure, but"
  echo "      if a new test is missing, this is where it would show up."
fi

echo
echo "=== 5. what ran, what skipped, what failed ==="
# A skipped test is not a pass and is not a failure. The distinction is the whole
# point of reporting rather than exiting 0.
for f in app/build/outputs/androidTest-results/connected/*.xml; do
  [ -e "$f" ] || continue
  echo "--- $(basename "$f") ---"
  # The `||` here binds to python3, NOT to grep -- so grep's exit status was the
  # statement's status, and `grep -oE` finding no `<testsuite` (exit 1) killed the
  # script under the `set -e` from line 393. Found by check_set_e_grep_traps.py.
  #
  # It never fired only because this loop had not run: the XML directory is absent
  # when the tests do not produce results, and then the loop body is skipped. A gate
  # that only works because its input has been missing is not a gate.
  #
  # `sed -n '1,5p'` rather than `head -5`: head closes the pipe early, SIGPIPEs the
  # producer, and under pipefail that is a 141 -- the trap this file documents three
  # times already.
  python3 - "$f" <<'PY' 2>/dev/null || { grep -oE '<testsuite[^>]*' "$f" || true; } | sed -n '1,5p'
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
