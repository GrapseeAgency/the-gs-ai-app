#!/usr/bin/env bash
# ============================================================================
# smooth-check.sh -- full-app smoothness checker (adb only)
#
# Measures 48 interactions of com.grapsee.gsai and prints PASS/FAIL per
# interaction plus an overall verdict. The device's native refresh rate is
# the budget (120Hz -> 8.33ms, 60Hz -> 16.67ms). Read from the device,
# never assumed.
#
# PASS per interaction requires ALL of:
#   janky_percent < 1.0
#   p95_ms        < budget (1000 / native refresh rate)
#   worst_frame   < 33.0 ms
#
# Exit code 0 = all PASS, 1 = any FAIL, 2 = environment error.
#
# Raw data:   docs/perf/runs/<timestamp>/   (framestats, gfxinfo summaries,
#             perfetto traces when the device has perfetto, command log)
# Summary:    docs/perf/runs/<timestamp>/summary.md
#
# STATE: by default the checker wipes app data ONCE at the start
#        (pm clear) and seeds everything it needs itself: onboarding,
#        empty chat, 50+ message history, conversations, pushed image,
#        airplane-mode error state. Do not run it with precious app state.
#        SMOOTH_SKIP_RESET=1 skips the wipe for iteration runs (onboarding
#        and cold-start interactions then measure the current state as
#        best effort).
#
# TUNING: element lookup uses uiautomator dump (text / content-desc) with
#         coordinate fallbacks. If a fallback misses on your phone, export
#         COMPOSER_XY="x y" and/or SEND_XY="x y" before running.
#
# Runtime: roughly 15-25 minutes on a real phone (50-message seeding + 48
#          measurements + 6s perfetto window per interaction).
# ============================================================================
set -u
export LC_ALL=C
cd "$(dirname "$0")/.." || exit 2

PKG="${PKG:-com.grapsee.gsai}"
ADB="${ADB:-adb}"
TS="$(date -u +%Y%m%d-%H%M%S)"
OUT="${OUT:-docs/perf/runs/$TS}"
RESULTS_TSV="$OUT/results.tsv"
COMPOSER_XY="${COMPOSER_XY:-}"
SEND_XY="${SEND_XY:-}"

mkdir -p "$OUT" || exit 2
: > "$RESULTS_TSV"
: > "$OUT/checker.log"
: > "$OUT/commands.log"

say(){ printf '%s\n' "$*" | tee -a "$OUT/checker.log" >/dev/null; printf '%s\n' "$*"; }
shc(){ printf '$ adb shell %s\n' "$*" >>"$OUT/commands.log"; "$ADB" shell "$@" 2>/dev/null | tr -d '\r'; }
tap(){ shc "input tap $1 $2" >/dev/null; }
swipe(){ shc "input swipe $1 $2 $3 $4 ${5:-250}" >/dev/null; }
key(){ shc "input keyevent $1" >/dev/null; }
typetext(){ shc "input text $1" >/dev/null; }

cleanup(){
  shc "cmd connectivity airplane-mode disable" >/dev/null
  shc "rm /sdcard/smuidump.xml" >/dev/null
}
trap cleanup EXIT

# ---------------------------------------------------------------- device ---
command -v "$ADB" >/dev/null 2>&1 || { echo "FATAL: adb not found on host"; exit 2; }
"$ADB" start-server >/dev/null 2>&1
"$ADB" wait-for-device || { echo "FATAL: no adb device connected"; exit 2; }
shc "pm path $PKG" | grep -q "package:" || { echo "FATAL: $PKG not installed on device"; exit 2; }

ACT="$(shc "cmd package resolve-activity --brief $PKG" | grep -v '^$' | tail -1)"
case "$ACT" in
  */*) : ;;
  *) ACT="$PKG/.MainActivity" ;;
esac

SZ="$(shc "wm size" | grep -oE '[0-9]+x[0-9]+' | tail -1)"
W="${SZ%x*}"; H="${SZ#*x}"
[ -n "$W" ] && [ -n "$H" ] || { W=1080; H=2400; }

refresh_rate(){
  local r
  r="$(shc "dumpsys display" | grep -oE 'renderFrameRate [0-9.]+' | head -1 | awk '{print $2}')"
  if [ -z "$r" ]; then
    r="$(shc "dumpsys display" | grep -oE 'mRefreshRate=[0-9.]+' | head -1 | cut -d= -f2)"
  fi
  if [ -z "$r" ]; then
    r="$(shc "settings get system peak_refresh_rate")"
  fi
  if [ -z "$r" ] || [ "$r" = "null" ] || [ "$r" = "0" ]; then r=60; fi
  echo "${r%%.*}"
}
HZ="$(refresh_rate)"
case "$HZ" in ''|*[!0-9]*) HZ=60 ;; esac
[ "$HZ" -lt 30 ] && HZ=60
BUDGET="$(awk "BEGIN{printf \"%.2f\", 1000/$HZ}")"

PF="$(shc 'command -v perfetto' | head -1)"
PF_ON="0"; [ -n "$PF" ] && PF_ON="1"

MODEL="$(shc 'getprop ro.product.model')"
AREL="$(shc 'getprop ro.build.version.release')"
ASDK="$(shc 'getprop ro.build.version.sdk')"
{
  echo "model=$MODEL android=$AREL sdk=$ASDK screen=${W}x${H} refresh=${HZ}Hz budget=${BUDGET}ms perfetto=$PF_ON"
  echo "animator_scale: $(shc 'settings get global animator_duration_scale')"
  echo "activity=$ACT"
} > "$OUT/device.txt"

# ------------------------------------------------------------- ui lookup ---
ui_dump(){
  shc "uiautomator dump /sdcard/smuidump.xml" >/dev/null
  "$ADB" pull /sdcard/smuidump.xml "$OUT/uidump-last.xml" >/dev/null 2>&1
}
ui_center(){ # $1 attr (text|content-desc)  $2 needle -> "cx cy" or rc=1
  [ -f "$OUT/uidump-last.xml" ] || return 1
  local m b
  m="$(grep -o "$1=\"[^\"]*$2[^\"]*\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" "$OUT/uidump-last.xml" 2>/dev/null | head -1)"
  [ -z "$m" ] && return 1
  b="$(printf '%s' "$m" | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"')"
  [ -z "$b" ] && return 1
  printf '%s' "$b" | sed -E 's/.*\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\].*/\1 \2 \3 \4/' | awk '{print int(($1+$3)/2), int(($2+$4)/2)}'
}
find_any(){ # needles... -> "cx cy" or rc=1  (single uiautomator dump)
  ui_dump || return 1
  local n c
  for n in "$@"; do
    c="$(ui_center text "$n")" && { echo "$c"; return 0; }
    c="$(ui_center content-desc "$n")" && { echo "$c"; return 0; }
  done
  return 1
}
ui_find(){ # $1 needle -> "cx cy" or rc=1
  find_any "$1"
}
tap_ui(){ # $1 needle  $2 fallback "x y"
  local c
  c="$(find_any "$1" 2>/dev/null)" || c="$2"
  tap $c
}

# ----------------------------------------------------------------- gfx -----
gfx_reset(){ shc "dumpsys gfxinfo $PKG reset" >/dev/null; }

PF_WINDOW=6
PF_T0=0
pf_start(){ # $1 id
  [ "$PF_ON" = "1" ] || return 0
  PF_T0="$(date +%s)"
  shc "perfetto -o /data/misc/perfetto-traces/sm-$1.pftrace -t ${PF_WINDOW}s gfx wm am sched view freq idle -b 64mb >/dev/null 2>&1 &"
}
pf_stop(){ # $1 id
  [ "$PF_ON" = "1" ] || return 0
  local now el rest
  now="$(date +%s)"; el=$((now - PF_T0)); rest=$((PF_WINDOW - el))
  [ "$rest" -gt 0 ] && sleep "$rest"
  sleep 0.5
  "$ADB" pull "/data/misc/perfetto-traces/sm-$1.pftrace" "$OUT/$1.pftrace" >/dev/null 2>&1 || true
}

score_and_record(){ # $1 id  $2 name
  local id="$1" name="$2"
  "$ADB" shell dumpsys gfxinfo "$PKG" framestats > "$OUT/$id-framestats.raw" 2>/dev/null
  "$ADB" shell dumpsys gfxinfo "$PKG" > "$OUT/$id-summary.raw" 2>/dev/null

  # per-frame durations in ms: FrameCompleted(col14) - IntendedVsync(col2)
  awk 'BEGIN{FS=","}
    $1 ~ /^-?[0-9]+$/ && NF>=14 && $2 ~ /^[0-9]+$/ && $14 ~ /^[0-9]+$/ {
      d=($14-$2)/1000000; if (d>0 && d<1000) printf "%.2f\n", d
    }' "$OUT/$id-framestats.raw" > "$OUT/$id.durs"

  local n p50="" p90="" p95="" p99="" worst="" jder=0 stats
  n="$(wc -l < "$OUT/$id.durs" | tr -d ' ')"
  if [ "${n:-0}" -ge 5 ]; then
    sort -n "$OUT/$id.durs" > "$OUT/$id.sorted"
    stats="$(awk -v m="$n" 'BEGIN{
        a=int((m-1)*0.5)+1; b=int((m-1)*0.9)+1; c=int((m-1)*0.95)+1; e=int((m-1)*0.99)+1}
      NR==a{f50=$1} NR==b{f90=$1} NR==c{f95=$1} NR==e{f99=$1} {last=$1}
      END{printf "%.2f %.2f %.2f %.2f %.2f\n", f50, f90, f95, f99, last}' "$OUT/$id.sorted")"
    read -r p50 p90 p95 p99 worst <<<"$stats"
    jder="$(awk -v b="$BUDGET" '$1>b{c++} END{print c+0}' "$OUT/$id.sorted")"
  fi

  # full-window totals from the gfxinfo summary (device jank definition)
  local stot sjank spct frames janky jankp
  stot="$(grep -oE 'Total frames rendered: [0-9]+' "$OUT/$id-summary.raw" | grep -oE '[0-9]+' | tail -1)"
  sjank="$(grep -oE 'Janky frames: [0-9]+' "$OUT/$id-summary.raw" | grep -oE '[0-9]+' | tail -1)"
  spct="$(grep -oE 'Janky frames: [0-9]+ \([0-9.]+%\)' "$OUT/$id-summary.raw" | sed -E 's/.*\(([0-9.]+)%\).*/\1/' | tail -1)"
  if [ -n "$stot" ] && [ "$stot" -gt 0 ]; then
    frames="$stot"; janky="${sjank:-0}"
    jankp="${spct:-$(awk -v j="$janky" -v t="$frames" 'BEGIN{printf "%.2f", j*100/t}')}"
  else
    frames="${n:-0}"; janky="$jder"
    jankp="$(awk -v j="$jder" -v t="${n:-0}" 'BEGIN{if(t>0) printf "%.2f", j*100/t; else print "0.00"}')"
  fi

  # percentile fallback to the summary block when framestats is unusable
  if [ -z "$p95" ]; then
    p50="$(grep -oE '50th percentile: [0-9.]+' "$OUT/$id-summary.raw" | awk '{print $3}' | sed 's/ms$//' | tail -1)"
    p90="$(grep -oE '90th percentile: [0-9.]+' "$OUT/$id-summary.raw" | awk '{print $3}' | sed 's/ms$//' | tail -1)"
    p95="$(grep -oE '95th percentile: [0-9.]+' "$OUT/$id-summary.raw" | awk '{print $3}' | sed 's/ms$//' | tail -1)"
    p99="$(grep -oE '99th percentile: [0-9.]+' "$OUT/$id-summary.raw" | awk '{print $3}' | sed 's/ms$//' | tail -1)"
  fi

  local verdict
  verdict="$(awk -v j="$jankp" -v p="$p95" -v w="$worst" -v b="$BUDGET" 'BEGIN{
    if (j=="" || p=="" || w=="") { print "FAIL"; exit }
    if (j+0 < 1.0 && p+0 < b+0 && w+0 < 33.0) print "PASS"; else print "FAIL"
  }')"

  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$id" "$name" "$frames" "$janky" "$jankp" "${p50:--}" "${p90:--}" "${p95:--}" "${p99:--}" "${worst:--}" "$verdict" >> "$RESULTS_TSV"
  say "[$id] $name -> $verdict  (frames=$frames jank=${jankp}% p50=${p50:--} p95=${p95:--} p99=${p99:--} worst=${worst:--})"
}

run_interaction(){ # $1 id  $2 name
  local id="$1" name="$2"
  say "-- $id $name"
  FOUND_XY=""
  do_prep "$id"
  pf_start "$id"
  gfx_reset
  do_action "$id"
  sleep 0.3
  pf_stop "$id"
  score_and_record "$id" "$name"
}

# --------------------------------------------------------------- seeding ---
SEED50_DONE=0
CONV2_DONE=0
FOUND_XY=""
[ -n "$COMPOSER_XY" ] || COMPOSER_XY=""
[ -n "$SEND_XY" ] || SEND_XY=""

grants(){
  local g
  for g in android.permission.READ_MEDIA_IMAGES android.permission.READ_MEDIA_VIDEO \
           android.permission.READ_EXTERNAL_STORAGE android.permission.CAMERA \
           android.permission.RECORD_AUDIO android.permission.POST_NOTIFICATIONS; do
    shc "pm grant $PKG $g" >/dev/null
  done
}

open_app(){ shc "am start -n $ACT" >/dev/null; sleep 1.2; }
open_drawer(){ open_app; swipe 8 $((H/2)) $((W*75/100)) $((H/2)) 300; sleep 1.0; }
go_chat(){ key 4; sleep 0.6; open_app; }

composer_tap(){
  if [ -n "$COMPOSER_XY" ]; then tap $COMPOSER_XY; return; fi
  tap_ui "Message" "$((W/2)) $((H-170))"
}
send_tap(){
  if [ -n "$SEND_XY" ]; then tap $SEND_XY; return; fi
  tap_ui "Send" "$((W-90)) $((H-170))"
}
attach_tap(){
  local c
  c="$(find_any "Attach" "attach" "Attachment" "Add" 2>/dev/null)" || c="60 $((H-170))"
  tap $c
}
# note: attach_tap kept for manual debugging; measured interactions use
# prep-resolved FOUND_XY so finders never run inside the measured window
send_msg(){ # $1 text (no spaces, no percent signs)
  composer_tap; sleep 0.8
  typetext "$1"
  send_tap; sleep 0.5
}
seed_msgs(){ # $1 count  (resolve composer+send once, keyboard stays open)
  local i c sc
  if [ -n "$COMPOSER_XY" ]; then c="$COMPOSER_XY"; else c="$(find_any 'Message' 2>/dev/null)" || c="$((W/2)) $((H-170))"; fi
  if [ -n "$SEND_XY" ]; then sc="$SEND_XY"; else sc=""; fi
  if [ -z "$sc" ]; then
    tap $c; sleep 0.9
    sc="$(find_any 'Send' 2>/dev/null)" || sc="$((W-90)) $((H-380))"
  fi
  for i in $(seq 1 "$1"); do
    tap $c; sleep 0.4
    typetext "seed_msg_$i"
    tap $sc; sleep 0.4
  done
  key 4
}
seed_image(){
  shc "screencap -p /sdcard/Pictures/smooth-check.png" >/dev/null
  shc "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/smooth-check.png" >/dev/null
}
open_mode_picker(){
  local c
  c="$(find_any "GS AI" "GPT" "Mode" "Model" 2>/dev/null)" || c="$((W/2)) $((H-240))"
  tap $c; sleep 1.2
}
open_settings(){
  open_drawer
  tap_ui "Settings" "$((W*35/100)) $((H*85/100))"
  sleep 1.2
}
ensure_conv2(){
  [ "$CONV2_DONE" = "1" ] && return 0
  open_drawer
  tap_ui "New chat" "$((W*20/100)) $((H*12/100))"; sleep 1.0
  send_msg "conv2_seed_marker"
  CONV2_DONE=1
}

# ----------------------------------------------------- prep (unmeasured) ---
do_prep(){
  case "$1" in
  01)
    if [ "${SMOOTH_SKIP_RESET:-0}" != "1" ]; then
      say "   pm clear $PKG in 3s (checker seeds its own state; app data is wiped)"
      sleep 3
      shc "pm clear $PKG" >/dev/null
    fi
    shc "am force-stop $PKG" >/dev/null; sleep 1.2
    grants
    ;;
  40) open_app ;;
  41) : ;;
  42)
    FOUND_XY="$(find_any "Get started" "Get Started" "Start" "Done" "Continue" "Skip" 2>/dev/null)"
    [ -z "$FOUND_XY" ] && FOUND_XY="$((W/2)) $((H*80/100))"
    ;;
  02) key 4; sleep 0.8; sleep 1.0 ;;
  03) key 3; sleep 1.4 ;;
  04) open_app ;;
  05) : ;;
  06) : ;;
  07) : ;;
  08)
    if [ "$SEED50_DONE" != "1" ]; then
      say "   seeding 50 messages (unmeasured, takes about 2 minutes)"
      seed_msgs 50
      SEED50_DONE=1
    fi
    ;;
  09)
    local i
    for i in 1 2 3 4 5 6 7 8; do
      swipe "$((W/2))" "$((H*30/100))" "$((W/2))" "$((H*80/100))" 100
    done
    sleep 0.6
    ;;
  10) : ;;
  11) : ;;
  12) : ;;
  13) : ;;
  14) key 4; sleep 0.6 ;;
  15) : ;;
  16)
    FOUND_XY="$(find_any "Attach" "attach" "Attachment" "Add" 2>/dev/null)" || FOUND_XY=""
    [ -z "$FOUND_XY" ] && FOUND_XY="60 $((H-170))"
    ;;
  17) : ;;
  18) : ;;
  19)
    seed_image; key 4; sleep 0.6
    FOUND_XY="$(find_any "Attach" "attach" "Attachment" "Add" 2>/dev/null)" || FOUND_XY=""
    [ -z "$FOUND_XY" ] && FOUND_XY="60 $((H-170))"
    ;;
  20) : ;;
  21)
    FOUND_XY="$(find_any "Send" "Done" "Add" 2>/dev/null)" || FOUND_XY=""
    [ -z "$FOUND_XY" ] && FOUND_XY="$((W-90)) 160"
    ;;
  22) : ;;
  23) go_chat ;;
  24) open_drawer ;;
  25) open_drawer ;;
  26) open_drawer ;;
  27) ensure_conv2; open_drawer ;;
  28) go_chat ;;
  29) open_mode_picker ;;
  30) open_mode_picker ;;
  31) open_mode_picker ;;
  32) open_drawer ;;
  33)
    open_drawer
    tap_ui "Search" "$((W/2)) $((H*12/100))"
    sleep 1.2
    ;;
  34)
    open_drawer
    tap_ui "Search" "$((W/2)) $((H*12/100))"
    sleep 1.0
    typetext "seed"; sleep 0.8
    ;;
  35) open_settings ;;
  36) open_settings ;;
  37) open_settings ;;
  38) open_settings ;;
  39) open_settings ;;
  43) go_chat; shc "cmd connectivity airplane-mode enable" >/dev/null; sleep 2.0 ;;
  44)
    shc "cmd connectivity airplane-mode disable" >/dev/null; sleep 2.5
    FOUND_XY="$(find_any "Retry" "Try again" 2>/dev/null)" || FOUND_XY=""
    [ -z "$FOUND_XY" ] && FOUND_XY="$((W/2)) $((H*60/100))"
    ;;
  45)
    go_chat; open_settings
    FOUND_XY="$(find_any "Model" "Download" 2>/dev/null)" || FOUND_XY=""
    [ -z "$FOUND_XY" ] && FOUND_XY="$((W/2)) $((H*50/100))"
    ;;
  46) : ;;
  47) key 4; sleep 0.5; key 4; sleep 0.5; open_app ;;
  48) shc "am force-stop $PKG" >/dev/null; sleep 1.5 ;;
  esac
}

# ------------------------------------------------- action (measured) -------
do_action(){
  local i c
  case "$1" in
  01)
    shc "am start -W -n $ACT" > "$OUT/01-amstart.raw"
    sleep 1.0
    ;;
  40)
    sleep 0.8
    swipe "$((W/2))" "$((H*55/100))" "$((W/2))" "$((H*55/100-120))" 200
    sleep 0.5
    swipe "$((W/2))" "$((H*55/100-120))" "$((W/2))" "$((H*55/100))" 200
    sleep 0.5
    ;;
  41)
    swipe "$((W*80/100))" "$((H/2))" "$((W*20/100))" "$((H/2))" 250
    sleep 1.2
    ;;
  42)
    tap $FOUND_XY
    sleep 2.0
    ;;
  02)
    shc "am start -W -n $ACT" > "$OUT/02-amstart.raw"
    sleep 1.2
    ;;
  03)
    shc "am start -W -n $ACT" > "$OUT/03-amstart.raw"
    sleep 1.5
    ;;
  04)
    swipe "$((W/2))" "$((H*60/100))" "$((W/2))" "$((H*50/100))" 200
    sleep 0.6
    swipe "$((W/2))" "$((H*50/100))" "$((W/2))" "$((H*60/100))" 200
    sleep 0.5
    ;;
  05) send_msg "hello_gs_first_message"; sleep 2.5 ;;
  06) send_msg "tell_me_a_very_long_story_about_a_robot"; sleep 5.0 ;;
  07)
    for i in $(seq 1 10); do
      swipe "$((W/2))" "$((H*65/100))" "$((W/2))" "$((H*35/100))" 180
      sleep 0.15
    done
    for i in $(seq 1 10); do
      swipe "$((W/2))" "$((H*35/100))" "$((W/2))" "$((H*65/100))" 180
      sleep 0.15
    done
    ;;
  08)
    for i in $(seq 1 6); do
      swipe "$((W/2))" "$((H*70/100))" "$((W/2))" "$((H*20/100))" 400
      sleep 0.3
    done
    for i in $(seq 1 6); do
      swipe "$((W/2))" "$((H*20/100))" "$((W/2))" "$((H*70/100))" 400
      sleep 0.3
    done
    ;;
  09)
    for i in 1 2 3; do
      swipe "$((W/2))" "$((H*85/100))" "$((W/2))" "$((H*5/100))" 80
      sleep 0.6
    done
    ;;
  10) send_msg "show_a_markdown_table_with_5_rows_of_data"; sleep 8.0 ;;
  11) send_msg "show_a_kotlin_code_block_with_20_lines"; sleep 8.0 ;;
  12) send_msg "generate_an_image_of_a_mountain_at_sunset"; sleep 10.0 ;;
  13) tap "$((W/2))" "$((H*45/100))"; sleep 1.5 ;;
  14) composer_tap; sleep 1.3 ;;
  15) typetext "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJ"; sleep 1.0 ;;
  16) tap $FOUND_XY; sleep 1.6 ;;
  17) key 4; sleep 1.0 ;;
  18) typetext "press_test"; send_tap; sleep 1.0 ;;
  19) tap $FOUND_XY; sleep 1.8 ;;
  20) tap "$((W*30/100))" "$((H*30/100))"; sleep 2.0 ;;
  21) tap $FOUND_XY; sleep 3.0 ;;
  22) sleep 2.5 ;;
  23) swipe 8 "$((H/2))" "$((W*75/100))" "$((H/2))" 300; sleep 1.2 ;;
  24) swipe "$((W*30/100))" "$((H*70/100))" "$((W*30/100))" "$((H*30/100))" 300; sleep 1.0 ;;
  25) key 4; sleep 0.9 ;;
  26) tap "$((W*35/100))" "$((H*25/100))"; sleep 1.6 ;;
  27) tap "$((W*35/100))" "$((H*35/100))"; sleep 1.6 ;;
  28) open_mode_picker; sleep 0.3 ;;
  29) swipe "$((W/2))" "$((H*65/100))" "$((W/2))" "$((H*35/100))" 300; sleep 1.0 ;;
  30) tap "$((W/2))" "$((H*50/100))"; sleep 1.4 ;;
  31) key 4; sleep 0.9 ;;
  32) tap_ui "Search" "$((W/2)) $((H*12/100))"; sleep 1.4 ;;
  33) typetext "seed"; sleep 1.4 ;;
  34) typetext "_msg"; sleep 1.6 ;;
  35) tap_ui "Settings" "$((W*35/100)) $((H*85/100))"; sleep 1.5 ;;
  36) swipe "$((W/2))" "$((H*70/100))" "$((W/2))" "$((H*30/100))" 300; sleep 1.0 ;;
  37) tap "$((W*85/100))" "$((H*40/100))"; sleep 1.2 ;;
  38) tap "$((W/2))" "$((H*35/100))"; sleep 1.4 ;;
  39) key 4; sleep 0.9 ;;
  43) send_msg "offline_test"; sleep 3.0 ;;
  44) tap $FOUND_XY; sleep 2.5 ;;
  45) tap $FOUND_XY; sleep 4.0 ;;
  46) sleep 3.5 ;;
  47) send_msg "model_ready_test"; sleep 3.0 ;;
  48)
    shc "am start -W -n $ACT" > "$OUT/48-amstart.raw"
    sleep 1.5
    ;;
  esac
}

id_name(){
  case "$1" in
  01) echo "cold-start-first-frame" ;;
  02) echo "warm-start-first-frame" ;;
  03) echo "resume-from-background" ;;
  04) echo "empty-chat-render" ;;
  05) echo "first-message-send" ;;
  06) echo "streaming-reply-5s" ;;
  07) echo "short-chat-scroll-20-swipe" ;;
  08) echo "long-chat-scroll-50-message" ;;
  09) echo "long-chat-fling-top-to-bottom" ;;
  10) echo "rich-reply-render-table" ;;
  11) echo "rich-reply-render-code" ;;
  12) echo "rich-reply-render-image" ;;
  13) echo "citation-tap-response" ;;
  14) echo "composer-open" ;;
  15) echo "composer-type-50-chars" ;;
  16) echo "composer-expand-attach" ;;
  17) echo "composer-collapse" ;;
  18) echo "composer-send-button-press" ;;
  19) echo "image-picker-open" ;;
  20) echo "image-preview-render" ;;
  21) echo "image-upload-progress" ;;
  22) echo "image-upload-complete" ;;
  23) echo "drawer-open" ;;
  24) echo "drawer-scroll" ;;
  25) echo "drawer-close" ;;
  26) echo "drawer-tap-conversation" ;;
  27) echo "conversation-switch" ;;
  28) echo "mode-picker-open" ;;
  29) echo "mode-picker-scroll" ;;
  30) echo "mode-picker-select" ;;
  31) echo "mode-picker-close" ;;
  32) echo "search-open" ;;
  33) echo "search-type" ;;
  34) echo "search-results-render" ;;
  35) echo "settings-open" ;;
  36) echo "settings-scroll" ;;
  37) echo "settings-toggle-response" ;;
  38) echo "settings-submenu-open" ;;
  39) echo "settings-back" ;;
  40) echo "onboarding-first-slide" ;;
  41) echo "onboarding-advance" ;;
  42) echo "onboarding-complete" ;;
  43) echo "network-error-state" ;;
  44) echo "retry-after-error" ;;
  45) echo "model-download-progress" ;;
  46) echo "model-download-complete" ;;
  47) echo "model-load-first-use" ;;
  48) echo "splash-to-first-frame" ;;
  *) echo "unknown" ;;
  esac
}

# ------------------------------------------------------------------ main ---
say "smooth-check: $PKG on $MODEL (android $AREL, ${W}x${H} @ ${HZ}Hz, budget ${BUDGET}ms, perfetto=$PF_ON)"
say "raw data: $OUT"

ORDER="01 40 41 42 02 03 04 05 06 07 08 09 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 43 44 45 46 47 48"

for id in $ORDER; do
  run_interaction "$id" "$(id_name "$id")"
done

# ---------------------------------------------------------------- report ---
{
  printf '%-38s %6s %6s %7s %6s %6s %6s  %s\n' "INTERACTION" "FRAMES" "JANKY" "JANK%" "P50" "P95" "P99" "VERDICT"
  sort -t"$(printf '\t')" -k1,1 "$RESULTS_TSV" | \
  while IFS="$(printf '\t')" read -r id name frames janky jankp p50 p90 p95 p99 worst verdict; do
    printf '%-38s %6s %6s %7s %6s %6s %6s  %s\n' \
      "$id-$name" "$frames" "$janky" "${jankp}%" "$p50" "$p95" "$p99" "$verdict"
  done
} > "$OUT/table.txt"

say ""
while IFS= read -r l; do say "$l"; done < "$OUT/table.txt"

TOTAL="$(wc -l < "$RESULTS_TSV" | tr -d ' ')"
PASSN="$(awk -F"$(printf '\t')" '$11=="PASS"{c++} END{print c+0}' "$RESULTS_TSV")"
FAILN=$((TOTAL - PASSN))
say ""
say "TOTAL: $PASSN of $TOTAL PASS, $FAILN FAIL"

VERDICT_LINE="TOTAL: $PASSN of $TOTAL PASS, $FAILN FAIL"
{
  echo "# smooth-check run $TS"
  echo
  echo "- date: $(date -u +%FT%TZ)"
  echo "- package: $PKG  activity: $ACT"
  echo "- device: $MODEL (android $AREL, sdk $ASDK), screen ${W}x${H}, refresh ${HZ}Hz, budget ${BUDGET}ms"
  echo "- perfetto captured: $PF_ON (1 = traces sm-<id>.pftrace in this folder, open at ui.perfetto.dev)"
  echo
  echo '```'
  cat "$OUT/table.txt"
  echo '```'
  echo
  echo "$VERDICT_LINE"
  echo
  echo "Stat sources: FRAMES/JANKY/JANK% from dumpsys gfxinfo summary since reset"
  echo "(device jank definition, full window). P50/P90/P95/P99/WORST from"
  echo "gfxinfo framestats per-frame durations, FrameCompleted-IntendedVsync,"
  echo "last ~120 frames (adb limitation). Worst frame source: read the"
  echo "matching sm-<id>.pftrace at ui.perfetto.dev frame timeline."
  echo "Raw command log: commands.log. Raw frame data: <id>-framestats.raw,"
  echo "<id>-summary.raw, <id>.durs, <id>.sorted. Launch timings: <id>-amstart.raw."
} > "$OUT/summary.md"

say "raw data + summary.md written to $OUT"

if [ "$FAILN" -eq 0 ]; then
  exit 0
else
  exit 1
fi
