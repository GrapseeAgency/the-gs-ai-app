#!/usr/bin/env python3
"""iOS static verification: brace/paren balance + banned-API sweep (GS AI App)."""
import re
import sys

FILES = [
    "/home/z/my-project/ios/App/Sources/Features/Voice/VoiceDictation.swift",
    "/home/z/my-project/ios/App/Sources/Features/Voice/SpeechPlayer.swift",
    "/home/z/my-project/ios/App/Sources/Features/Home/HomeView.swift",
    "/home/z/my-project/ios/App/Sources/Navigation/AppRouter.swift",
    "/home/z/my-project/ios/App/Sources/Features/Chats/ChatDetailView.swift",
    "/home/z/my-project/ios/App/Sources/Networking/SQLiteChatStore.swift",
    "/home/z/my-project/ios/App/Sources/Networking/ConversationStore.swift",
    "/home/z/my-project/ios/App/Sources/Features/Chats/ChatSearchView.swift",
]

BANNED = [
    r"\.fontDesign", r"SwiftData", r"@Observable", r"ContentUnavailableView",
    r"\.symbolEffect", r"\.onChange\(of:[^)]*\)\s*\{\s*\w+\s*,\s*\w+",
    r"AVAudioApplication", r"\.onChange\(of:\s*.+\s*\)\s*\{\s*oldValue",
    r"#Predicate<", r"\.searchable\(.+\bpresentationBackground", r"\.sensoryFeedback",
]

ok = True
for path in FILES:
    src = open(path).read()
    # strip string literals + line comments for honest balance counting
    stripped = re.sub(r'"(?:[^"\\]|\\.)*"', '""', src)
    stripped = re.sub(r"//[^\n]*", "", stripped)
    braces = stripped.count("{") - stripped.count("}")
    parens = stripped.count("(") - stripped.count(")")
    status = []
    if braces != 0:
        ok = False
        status.append(f"BRACE IMBALANCE {braces:+d}")
    if parens != 0:
        ok = False
        status.append(f"PAREN IMBALANCE {parens:+d}")
    for pattern in BANNED:
        for m in re.finditer(pattern, src):
            ok = False
            line = src[: m.start()].count("\n") + 1
            status.append(f"BANNED API '{m.group(0)[:40]}' @ line {line}")
    name = path.split("/")[-1]
    print(f"{name}: {'CLEAN' if not status else '; '.join(status)}")

print("\nRESULT:", "PASS" if ok else "FAIL")
sys.exit(0 if ok else 1)
