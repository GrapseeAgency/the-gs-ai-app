#!/usr/bin/env python3
"""
No artificial delay( in ChatRepository.kt.

WHY

v0.71.2 shipped this, in streamLocalReply():

    reply.split(" ").forEachIndexed { index, word ->
        onDelta(if (index == 0) word else " $word")
        delay(26)
    }

The engine does not stream. GsNative.chat returns the whole completion as one
String -- Rust chat_impl -> gs_mobile_chat -> gs_llama_chat runs to completion
before returning -- so that loop replayed an ALREADY-FINISHED answer one word at
a time. It cost ~4.9s of dead air per 256-token reply (~190 words x 26ms), on
every reply, and it made a finished answer look like work still in progress.

The v0.71.2 report was "the native path is lagging and incredibly slow". This
was a real cause and none of it was the model.

THE RULE

ChatRepository.kt may not contain delay( at all.

Justification, not superstition: this file owns the chat send path. Its local
branch has no stream to pace -- the engine returns a completed string -- so any
per-chunk delay there is pure latency dressed up as streaming. The network
branch streams for real, and its pacing lives in ChatStreamController, which
coalesces paints at ~30Hz and is NOT this file. A delay that is legitimately
about retry backoff belongs at the transport layer (ApiClient), where the other
per-file lints already police it.

So this is not "26ms is too slow". It is "there is nothing here to be slow about".
The number is not the point; the presence of a delay is.

WHAT IT IS NOT

A text check, not a compiler, and deliberately not a general "no delay() in the
app" rule -- retry backoff is legitimate and appears elsewhere. Scoped to the one
file where a delay can only be simulated streaming.
"""
import re
import sys

TARGET = "android/app/src/main/java/com/grapsee/gsai/data/repository/ChatRepository.kt"

# `delay(` not preceded by a `.` -- that would be something else entirely.
DELAY = re.compile(r'(?<![.\w])delay\s*\(')


def main(argv):
    path = argv[1] if len(argv) > 1 else TARGET
    try:
        with open(path, encoding="utf-8", errors="replace") as fh:
            lines = fh.read().split("\n")
    except OSError as exc:
        print("::error::cannot read %s: %s" % (path, exc))
        return 2

    findings = []
    for lineno, line in enumerate(lines, 1):
        stripped = line.strip()
        # Comments are exempt: this file's own history explains the removed
        # typewriter and quotes `delay(26)` to do so. A lint that matches its
        # own documentation cries wolf on the fix.
        if stripped.startswith("//") or stripped.startswith("*") or stripped.startswith("/*"):
            continue
        if DELAY.search(line):
            findings.append((lineno, stripped))

    if findings:
        for lineno, text in findings:
            print("%s:%d: error: delay( in ChatRepository.kt is artificial "
                  "streaming. The local engine returns a completed String, so a "
                  "per-chunk delay here adds latency and fakes progress." %
                  (path, lineno))
            print("    %s" % text[:110])
        print("\n::error::%d artificial delay(s). Emit the finished text once, "
              "or move real backoff to ApiClient." % len(findings))
        return 1

    print("ok: no artificial delay( in %s" % path)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))