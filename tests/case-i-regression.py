#!/usr/bin/env python3
"""FORENSIC AUDIT [8] — CASE I HARD REGRESSION: 20 live search turns; the
final answer must NEVER deny the search the backend actually ran."""
import json, re, sys, urllib.request

ORIGIN = "https://preview-chat-c945696f-6447-4dfa-b510-971d8b9eb5bf.space-z.ai"
DENIAL_RES = [
    r"I\s+(?:cannot|can't|don't|do\s+not|won't)\b[^.\n]{0,32}\b(?:perform|execute|run|do|conduct|make)\b[^.\n]{0,24}\b(?:new\s+|web\s+|live\s+|real[- ]time\s+)?search",
    r"I\s+(?:don't|do\s+not|cannot|can't)\s+have\s+(?:the\s+)?(?:ability|capability|capacity|tools?|feature)\b[^.\n]{0,40}\b(?:search|browse|internet|web|online)",
    r"I\s+(?:cannot|can't|don't|do\s+not|unable\s+to)\b[^.\n]{0,32}\b(?:access|reach|connect\s+to|browse|use|search)\b[^.\n]{0,32}\b(?:the\s+)?(?:internet|web|online|live\s+internet|real[- ]time)",
    r"I\s+(?:don't|do\s+not)\s+have\b[^.\n]{0,32}\b(?:internet|web|online|live|real[- ]time)\b",
    r"I\s+(?:cannot|can't)\s+browse\b",
    r"I\s+am\s+(?:not\s+able|unable)\s+to\s+(?:search|browse|access)",
]

def new_conv():
    req = urllib.request.Request(f"{ORIGIN}/api/v1/conversations",
        data=json.dumps({"title": "case-i-regression"}).encode(),
        headers={"Content-Type": "application/json", "x-gs-app-version": "0.68.0"}, method="POST")
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)["id"]

def send(conv, msg):
    req = urllib.request.Request(f"{ORIGIN}/api/v1/conversations/{conv}/messages",
        data=json.dumps({"content": msg, "stream": True, "timezone": "Asia/Dhaka"}).encode(),
        headers={"Content-Type": "application/json", "x-gs-app-version": "0.68.0"}, method="POST")
    deltas, err = [], None
    with urllib.request.urlopen(req, timeout=180) as r:
        for raw in r:
            line = raw.decode().strip()
            if not line.startswith("data:"):
                continue
            try:
                ev = json.loads(line[5:].strip())
            except Exception:
                continue
            if ev.get("event") == "delta" and isinstance(ev.get("data"), str):
                deltas.append(ev["data"])
            elif ev.get("event") == "error":
                err = ev.get("data")
    return "".join(deltas), err

QUERIES = [
    "search the web for the latest Android 16 features",
    "go to the internet and find today's technology headlines",
    "search for who won the last cricket world cup",
    "search the internet for the population of Dhaka in 2026",
    "search for the latest space launches this month",
    "search the web for OpenAI news today",
    "search for the current temperature in Tokyo",
    "go online and search for Premier League scores",
    "search the web for the newest Python release",
    "search for today's business news",
    "search the web for quantum computing breakthroughs 2026",
    "search for the latest flagship phone releases",
    "search the internet for NASA Artemis updates",
    "search for today's science headlines",
    "search the web for the most recent Nobel prize winners",
    "search for current football transfer news",
    "go to the web and search renewable energy news today",
    "search for the latest Bangladesh news today",
    "search the web for Samsung Galaxy S26 rumors",
    "search for today's health news headlines",
]

violations = 0
for i, q in enumerate(QUERIES, 1):
    conv = new_conv()
    try:
        text, err = send(conv, q)
    except Exception as e:
        print(f"{i:02d} TURN-ERROR {e}")
        continue
    body = text if text else f"(error: {err})"
    hit = next((p for p in DENIAL_RES if re.search(p, body, re.I)), None)
    if hit:
        violations += 1
        print(f"{i:02d} VIOLATION  q='{q[:44]}' matched={hit}  answer='{body[:120]}'")
    else:
        print(f"{i:02d} clean      q='{q[:44]}' answer='{body[:80].replace(chr(10),' ')}'")

print(f"\nREGRESSION RESULT: {violations} contradiction(s) across {len(QUERIES)} search turns")
sys.exit(1 if violations else 0)
