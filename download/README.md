Here are all the generated files.

## Latest release: v0.71.3

    file    GS-AI-App.apk
    size    84104873 bytes
    sha256  07ecf81df4fdd351b8686cbc82a9196d63d4aab1c9cce7d9e488cab6eed279ac

**The server this app talks to is currently returning 502 for every message.**
`POST /api/v1/conversations/{id}/messages` answers
`{"code":"upstream_error","message":"GS AI is temporarily unavailable."}` — verified
across every model name (`flash`, `fast`, `default`, `gemini-flash`, and no model
field at all). Conversation create and read both work; only the send path is down.
No APK can fix this. `GET /api/health` still reports `200 {"status":"ok"}` because
it never touches the upstream, so the outage looks healthy to monitoring.

### v0.71.3 — performance

Three causes of the reported slowness on the on-device path. Two fixed in Kotlin,
no native rebuild needed.

1. **~4.9s of dead air per reply, self-inflicted.** `streamLocalReply` replayed an
   already-finished answer one word at a time with `delay(26)`. The engine does not
   stream — `GsNative.chat` returns one completed `String` — so the answer already
   existed and the loop existed only to imitate the network path. At the 256-token
   budget that is ~190 words x 26ms. Now emits once.
2. **Four threads on an eight-core phone.** `init` hardcoded 2048/4, idling half the
   CPU for the whole generation. Now `initTuned` with `availableProcessors()` for
   both `n_threads` and `n_threads_batch`. `initTuned` was already in the shipped
   ABI, verified against the symbol table of the arm64 `libgs_ffi.so` in this APK.
3. **An empty streaming bubble rendered literally nothing.** The kinetic caret rides
   the last line of a live paragraph, so with zero blocks there was no caret and no
   dots — for the entire local generation. Now shows the caret alone.

### v0.71.2 — a failed turn was shown and stored as the assistant's reply

The app answered a failed send with "The connection dropped mid-turn while GS was
working. Reopen this chat in a moment to load the finished reply." Three lies in one
sentence: `accumulated` was empty so GS was not working; recovery had *already*
returned null so there was no finished reply to load; and the text was persisted as
the assistant's message. Now honest, with a JVM test that would have caught it.

### Contents

Native chat ships in this build: `libgs_ffi.so` for all four ABIs (armeabi-v7a,
arm64-v8a, x86, x86_64), alongside ML Kit OCR. `libgs_sd.so` is deliberately not
staged — diffusion is disabled on mobile. No Tesseract or Leptonica.

**arm64 device execution is now partially verified.** The phone found the v0.71.0
and v0.71.1 defects above, which is real arm64 evidence — but no arm64 *performance*
benchmark has run, so the thread change is the standard mobile configuration applied
without a device measurement behind it. Real-device TTFT remains estimated, not
measured.