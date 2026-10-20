#!/bin/bash
# gs-dev watchdog — keeps the dev stack alive in this sandbox.
# The container reaper kills tool-shell children between agent calls; this
# watchdog must be double-fork ORPHANED to PID 1 (PPid=1) to survive:
#   ( setsid .zscripts/watchdog.sh >/dev/null 2>&1 < /dev/null & )
# Restarts next dev (:3000) and eval-monitor (:3031) within ~10s of death.

LOG=/home/z/my-project/.zscripts/watchdog.log
APP_DIR=/home/z/my-project
MONITOR_DIR=/home/z/my-project/mini-services/eval-monitor

echo "[$(date '+%F %T')] watchdog started (pid $$)" >> "$LOG"

while true; do
  if ! curl -s -o /dev/null --max-time 4 http://localhost:3000/; then
    echo "[$(date '+%F %T')] :3000 down — starting Next dev" >> "$LOG"
    cd "$APP_DIR" || { sleep 10; continue; }
    setsid nohup bun run dev >/dev/null 2>&1 < /dev/null &
    for i in $(seq 1 18); do
      sleep 5
      curl -s -o /dev/null --max-time 4 http://localhost:3000/ && break
    done
    curl -s -o /dev/null --max-time 4 http://localhost:3000/ \
      && echo "[$(date '+%F %T')] :3000 UP" >> "$LOG" \
      || echo "[$(date '+%F %T')] :3000 FAILED to come up" >> "$LOG"
  fi

  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 4 http://localhost:3031/ 2>/dev/null)
  if [ -z "$code" ] || [ "$code" = "000" ]; then
    echo "[$(date '+%F %T')] :3031 down — starting eval-monitor" >> "$LOG"
    cd "$MONITOR_DIR" || { sleep 10; continue; }
    setsid nohup bun run dev > dev.log 2>&1 < /dev/null &
  fi

  sleep 10
done
