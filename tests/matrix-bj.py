#!/usr/bin/env python3
"""FORENSIC AUDIT [31] — black-box matrix cases B-L through the real public origin."""
import json, subprocess, sys, urllib.request

ORIGIN = "https://preview-chat-c945696f-6447-4dfa-b510-971d8b9eb5bf.space-z.ai"

def new_conv():
    req = urllib.request.Request(f"{ORIGIN}/api/v1/conversations",
        data=json.dumps({"title": "audit-matrix"}).encode(),
        headers={"Content-Type": "application/json", "x-gs-app-version": "0.68.2"}, method="POST")
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)["id"]

def send(conv, msg):
    req = urllib.request.Request(f"{ORIGIN}/api/v1/conversations/{conv}/messages",
        data=json.dumps({"content": msg, "stream": True, "timezone": "Asia/Dhaka"}).encode(),
        headers={"Content-Type": "application/json", "x-gs-app-version": "0.68.2"}, method="POST")
    final, deltas, err = None, [], None
    with urllib.request.urlopen(req, timeout=180) as r:
        for raw in r:
            line = raw.decode().strip()
            if not line.startswith("data:"):
                continue
            try:
                ev = json.loads(line[5:].strip())
            except Exception:
                continue
            e, d = ev.get("event"), ev.get("data")
            if e == "delta" and isinstance(d, str):
                deltas.append(d)
            elif e == "done":
                try: final = json.loads(d)
                except Exception: final = d
            elif e == "error":
                err = d
    text = (final or {}).get("content") if isinstance(final, dict) else None
    if text is None:
        text = "".join(deltas)
    return text or (f"ERROR: {err}"), (final or {})

def case(label, conv, msg):
    print(f"\n════════ CASE {label} ════════")
    print(f"REQUEST: {msg}")
    text, final = send(conv, msg)
    srcs = final.get("sources") or []
    print(f"RESPONSE: {text[:500].replace(chr(10),' ')}")
    if srcs:
        print(f"SOURCES: {len(srcs)} -> " + ", ".join(sorted({s.get('domain','?') for s in srcs})[:6]))
    return text

convB = new_conv(); convC = new_conv(); convD = new_conv(); convE = new_conv()
convF = new_conv(); convG = new_conv(); convK = new_conv(); convL = new_conv()
print(f"convs B={convB} C={convC} D={convD} E={convE} F={convF} G={convG} K={convK} L={convL}")

case("B (time)", convB, "what time is it?")
case("E (2+2)", convE, "what is 2+2?")
case("G (subjective)", convG, "what's the most beautiful diagram chart?")
case("C (explicit search)", convC, "go to the internet and search today's AI news")
case("D (freshness)", convD, "who is the current president of Chile?")
case("F (historical)", convF, "who was the first Muslim in Islamic theology?")
case("H (joke, after F)", convF, "tell me a joke")
case("I (search again primary)", convF, "search again using primary sources")
case("J (stop, after I)", convF, "stop searching and summarise what you found")
case("L (wikipedia)", convL, "what does Wikipedia say about the London Underground map?")
