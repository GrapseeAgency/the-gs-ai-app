#!/bin/bash
# FORENSIC AUDIT [31] — BLACK-BOX ACCEPTANCE MATRIX RUNNER.
# Every case goes through the REAL public origin, SSE streaming, the same
# wire shape the Android app uses. Captures per case: final answer + server
# capability/execution logs (GS-ROUTER / GS-CAP / SEARCH / READ-RATE).

ORIGIN="https://preview-chat-c945696f-6447-4dfa-b510-971d8b9eb5bf.space-z.ai"
CONV_AE=""   # fresh convs for A/B/C/D/E/G
CONV_F=""    # F → H → I → J chain

new_conv() {
  curl -s -X POST "$ORIGIN/api/v1/conversations" -H 'Content-Type: application/json' \
    -H 'x-gs-app-version: 0.68.2' -d '{"title":"audit-matrix"}' | python3 -c "import json,sys; print(json.load(sys.stdin).get('id',''))"
}

# send <convId> <label> <message>: SSE turn, prints final text.
send() {
  local conv="$1" label="$2" msg="$3"
  echo ""
  echo "════════ CASE $label ════════"
  echo "REQUEST: $msg"
  curl -s -N -X POST "$ORIGIN/api/v1/conversations/$conv/messages" \
    -H 'Content-Type: application/json' -H 'x-gs-app-version: 0.68.2' \
    -d "$(python3 -c "import json,sys; print(json.dumps({'content': sys.argv[1], 'stream': True, 'timezone': 'Asia/Dhaka'}))" "$msg")" \
    --max-time 180 | python3 -c "
import sys, json
final = None
err = None
deltas = []
for line in sys.stdin:
    line = line.strip()
    if not line.startswith('data:'): continue
    try:
        ev = json.loads(line[5:].strip())
    except Exception:
        continue
    e, d = ev.get('event'), ev.get('data')
    if e == 'delta' and isinstance(d, str): deltas.append(d)
    elif e == 'done':
        try: final = json.loads(d)
        except Exception: final = d
    elif e == 'error': err = d
text = (final or {}).get('content') if isinstance(final, dict) else None
if text is None: text = ''.join(deltas)
print('RESPONSE:', (text or ('ERROR: ' + str(err)))[:600].replace(chr(10),' '))
"
}

echo "=== creating conversations ==="
for i in 1 2 3 4 5 6 7; do CONV=$(new_conv); echo "conv$i=$CONV"; done
