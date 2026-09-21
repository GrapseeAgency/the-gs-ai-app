#!/usr/bin/env python3
"""PHASE 8.3 forensic baseline — cases A-I through the SAME public origin.
Records the ACTUAL capability decision per turn. No code changes."""
import json, time, urllib.request, sys

BASE = 'https://preview-chat-c945696f-6447-4dfa-b510-971d8b9eb5bf.space-z.ai'
LOG = '/home/z/my-project/dev.log'

def log_size():
    try:
        import os
        return os.path.getsize(LOG)
    except Exception:
        return 0

def router_delta(offset):
    """New GS-ROUTER/planner lines appended to dev.log since offset."""
    try:
        with open(LOG, 'rb') as f:
            f.seek(offset)
            new = f.read().decode('utf-8', 'replace')
        out = []
        for line in new.splitlines():
            if 'GS-ROUTER' in line or 'PLANNER' in line.upper():
                out.append(line[:150])
        return out
    except Exception:
        return []

def api(path, body, method='POST'):
    req = urllib.request.Request(
        BASE + path, data=json.dumps(body).encode(),
        headers={'Content-Type': 'application/json'}, method=method)
    with urllib.request.urlopen(req, timeout=160) as r:
        return json.loads(r.read())

def turn(cid, text, label):
    off = log_size()
    t0 = time.time()
    try:
        d = api(f'/api/v1/conversations/{cid}/messages', {'content': text, 'stream': False})
        dt = round(time.time() - t0, 1)
        srcs = d.get('sources') or []
        statuses = {}
        for s in srcs:
            st = s.get('status') or '?'
            statuses[st] = statuses.get(st, 0) + 1
        rec = {
            'case': label, 'request': text, 'latency_s': dt,
            'sources': len(srcs), 'sourceStatus': statuses,
            'answerHead': (d.get('content') or '')[:170].replace('\n', ' '),
            'router': router_delta(off),
        }
    except Exception as e:
        rec = {'case': label, 'request': text, 'ERROR': repr(e)[:200]}
    print(json.dumps(rec, ensure_ascii=False))
    sys.stdout.flush()
    return rec

def conv(title):
    return api('/api/v1/conversations', {'title': title})['id']

which = sys.argv[1] if len(sys.argv) > 1 else 'ab'

if which == 'ab':
    # ---- Conv A: A(hi matey) -> B(what time is it) -> E(hello)
    cA = conv('8.3-FORENSIC-A')
    print(json.dumps({'convA': cA}))
    turn(cA, 'hi matey', 'A')
    turn(cA, 'okay so what time is it?', 'B')
    turn(cA, 'hello', 'E')
    # ---- Conv B: C(explicit search AI news) -> D(president of Chile)
    cB = conv('8.3-FORENSIC-B')
    print(json.dumps({'convB': cB}))
    turn(cB, 'go to the internet and find today\'s AI news', 'C')
    turn(cB, 'who is the current president of Chile?', 'D')
elif which == 'c':
    # ---- Conv C: G(search first Muslim) -> F(diagram) -> H(stop) -> I(again)
    cid = sys.argv[2]
    turn(cid, 'search the internet for who was the first Muslim', 'G')
    turn(cid, 'what\'s the most beautiful diagram chart?', 'F')
    turn(cid, 'stop searching and tell me what you already found', 'H')
    turn(cid, 'search again using primary sources', 'I')
elif which == 'd':
    # ---- Conv D: §7 contamination regression (4 turns)
    cid = sys.argv[2]
    turn(cid, 'search the internet for first Muslim in history', '7-T1')
    turn(cid, 'what is the most beautiful diagram chart?', '7-T2')
    turn(cid, 'tell me a joke', '7-T3')
    turn(cid, 'what does Wikipedia say about the London Underground map?', '7-T4')
    turn(cid, 'tell me a joke', '7-T5')
