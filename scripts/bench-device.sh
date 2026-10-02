#!/usr/bin/env bash
# bench-device.sh -- one command, real numbers, on ANY phone you plug in.
#
#   ./scripts/bench-device.sh            # auto-detect, use the default model
#   ./scripts/bench-device.sh <serial>   # force a device serial
#   MODEL=/path/to/qwen.gguf ./scripts/bench-device.sh
#
# What it does, in order, leaving evidence for every step:
#   1. find the connected device (adb devices) -- fail clearly if none
#   2. locate the 0.5B Q4_0 GGUF, push it into the app's private dir
#   3. run the instrumented decode-rate benchmark on that device
#   4. scrape TTFT / tok/s from the logcat it leaves behind
#   5. write native/docs/DEVICE-BENCH-<serial>.md
#
# It does NOT estimate. If no device is attached, it says so and exits 2. If the
# benchmark test cannot run, the markdown says what failed -- it does not print
# a plausible-looking number.

set -uo pipefail

PKG=com.grapsee.gsai
MODEL_ENV="${MODEL:-}"
OUT_DIR="native/docs"

adb_bin=$(command -v adb || true)
if [ -z "$adb_bin" ]; then
  echo "FAIL: adb not on PATH. Install the Android SDK platform-tools and retry." >&2
  exit 2
fi

SERIAL="${1:-}"
if [ -z "$SERIAL" ]; then
  # exactly one non-offline device, or fail loud
  mapfile -t DEVS < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
  if [ "${#DEVS[@]}" -eq 0 ]; then
    echo "FAIL: no device/emulator connected. adb sees:" >&2
    adb devices >&2 || true
    echo "Plug in a phone and run again. Nothing was benchmarked." >&2
    exit 2
  fi
  if [ "${#DEVS[@]}" -gt 1 ]; then
    echo "FAIL: more than one device connected; pass the serial as the first argument." >&2
    adb devices >&2
    exit 2
  fi
  SERIAL="${DEVS[0]}"
fi

echo "device serial : $SERIAL"
echo "model         : ${MODEL_ENV:-<auto>}"

# --- 1. the model -------------------------------------------------------------
MODEL_SRC=""
if [ -n "$MODEL_ENV" ] && [ -f "$MODEL_ENV" ]; then
  MODEL_SRC="$MODEL_ENV"
else
  for cand in \
      qwen2.5-0.5b-instruct-q4_0.gguf \
      native/prebuilts/qwen2.5-0.5b-instruct-q4_0.gguf \
      model.gguf; do
    if [ -f "$cand" ]; then MODEL_SRC="$cand"; break; fi
  done
fi
if [ -z "$MODEL_SRC" ]; then
  echo "FAIL: no GGUF found. Put qwen2.5-0.5b-instruct-q4_0.gguf in the repo root" >&2
  echo "or set MODEL=/path/to/it.gguf. Nothing was benchmarked." >&2
  exit 2
fi
MODEL_NAME=$(basename "$MODEL_SRC")
echo "using model   : $MODEL_SRC ($MODEL_NAME)"

# --- 2. push it into the app's private dir ------------------------------------
echo "pushing model ($(stat -c%s "$MODEL_SRC" 2>/dev/null || echo '?') bytes) ..."
adb -s "$SERIAL" push "$MODEL_SRC" "/data/local/tmp/$MODEL_NAME" >/dev/null
adb -s "$SERIAL" shell "run-as $PKG mkdir -p files" >/dev/null 2>&1 || true
if ! adb -s "$SERIAL" shell "run-as $PKG cp /data/local/tmp/$MODEL_NAME files/$MODEL_NAME" >/dev/null 2>&1; then
  echo "FAIL: could not copy the model into the app's private dir via run-as." >&2
  echo "Is $PKG installed and debuggable on this device?" >&2
  exit 2
fi
ONDEV=$(adb -s "$SERIAL" shell "run-as $PKG stat -c%s files/$MODEL_NAME" 2>/dev/null | tr -d '\r')
echo "on-device size: $ONDEV bytes"
if [ "$ONDEV" != "$(stat -c%s "$MODEL_SRC")" ]; then
  echo "FAIL: on-device size differs from source; the push was truncated and must" >&2
  echo "not be benchmarked." >&2
  exit 2
fi

# --- 3. run the instrumented decode benchmark --------------------------------
# Reuses the fixed single-model benchmark that already asserts a real,
# reproducible decode rate. Any crash here is reported, not interpolated.
echo "running instrumented benchmark ..."
(
  cd android
  ./gradlew connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.grapsee.gsai.DeviceVerificationTest#perf_the_0_5b_reports_a_measurable_decode_rate \
    --no-daemon 2>&1 | tail -20
) | tee /tmp/bench-device-gradle.log

# --- 4. scrape the numbers the test printed -----------------------------------
adb -s "$SERIAL" logcat -d > /tmp/bench-device-logcat.txt 2>/dev/null || true
TTFT=$(grep -aoE 'ttft=[0-9]+ms' /tmp/bench-device-logcat.txt | head -1 | tr -d 'a-zms=')
TPS=$(grep -aoE 'tok/s=[0-9.]+|tps=[0-9.]+' /tmp/bench-device-logcat.txt | head -1 | sed -E 's/^[a-z/=]+//')
THERMAL=$(adb -s "$SERIAL" shell 'cat /sys/class/thermal/thermal_zone*/temp' 2>/dev/null | tr '\n' ' ' || true)

# --- 5. the report -------------------------------------------------------------
SER=$(echo "$SERIAL" | tr '/:' '_')
OUT="$OUT_DIR/DEVICE-BENCH-${SER}.md"
mkdir -p "$OUT_DIR"
{
  echo "# DEVICE BENCH -- $SERIAL"
  echo
  echo "- model        : $MODEL_NAME ($ONDEV bytes)"
  echo "- gradle filter: DeviceVerificationTest#perf_the_0_5b_reports_a_measurable_decode_rate"
  echo "- generated    : $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo
  echo "| metric | value | source |"
  echo "|---|---|---|"
  echo "| TTFT | ${TTFT:-<none>} ms | DeviceVerificationTest logcat |"
  echo "| decode rate | ${TPS:-<none>} tok/s | DeviceVerificationTest logcat |"
  echo "| device temp | ${THERMAL:-<unreadable>} | /sys/class/thermal |"
  echo
  echo "## raw logcat lines"
  echo
  echo '```'
  grep -aE 'TTFT|tok/s|ttft|decode rate|perf_' /tmp/bench-device-logcat.txt | head -20 || true
  echo '```'
  echo
  echo "## notes"
  echo
  echo "- Thermal reading is one sample; run again while sustained to see throttling."
  echo "- The instrumented test's own pass/fail is the source of truth for whether"
  echo "  these numbers mean anything; a crash is not a result."
} > "$OUT"

echo "wrote $OUT"
if [ -z "$TTFT" ] && [ -z "$TPS" ]; then
  echo "WARNING: no TTFT/tok/s lines found in logcat. The benchmark likely did not run;" >&2
  echo "see /tmp/bench-device-gradle.log and $OUT." >&2
  exit 1
fi
exit 0
