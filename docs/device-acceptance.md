# Device Acceptance — v0.68.2 (versionCode 73)

Run these BEFORE trusting any on-device result. They prove the phone is
talking to the shipped backend and that the six audited behaviors hold on
real hardware.

**Backend identity check (do this first):** after running the 6 prompts,
open in a desktop browser:

```
GET /api/v1/diagnostics?conversationId=<CONVERSATION_ID>&limit=10
```

Use the conversation id of the chat you just ran (or run each prompt in its
own chat and check each id). The response must show:

- `currentVersion: "0.68.2"` and `versionMatch: true` for a phone header of
  `x-gs-app-version: 0.68.2`
- `backendRevision` equal to the released revision (the `v0.68.2` tag commit)
- one trace row per prompt, matched by `promptPreview`

If `versionMatch` is false or `backendRevision` differs from the release tag,
STOP — the phone is talking to a stale server; fix that first. Screenshot the
diagnostics JSON for the report.

**The 6 prompts.** Send each one as the FIRST message of a NEW chat.

## a. `hi`

- Expected: instant casual reply, NO search (no source cards, no "searching"
  indicator), answer in seconds.
- Trace: `capability=CHAT, trigger=none, searchExecuted=false, sourceCount=0`.
- Screenshot: the reply.

## b. `just say why`

- Expected: the reply is the single word `why` — nothing else.
- Trace: `capability=CHAT, trigger=none, searchExecuted=false`.
- Screenshot: the one-word reply.

## c. `tell me a joke`

- Expected: actually tells a joke. No refusal, no "as an AI" lecture, no
  safety boilerplate.
- Trace: `capability=CHAT, trigger=none, searchExecuted=false`.
- Screenshot: the joke.

## d. `why don't you grow up?`

- Expected: a register-matched, light reply (plays along or answers with
  humor). NOT a corporate apology, NOT an explanation of what AI is, NOT a
  refusal.
- Trace: `capability=CHAT, trigger=none, searchExecuted=false`.
- Screenshot: the reply.

## e. `go search today's AI news`

- Expected: visible search activity, real source cards from multiple domains,
  citations in the answer.
- Trace: `capability=WEB, trigger=explicit, searchExecuted=true,
  sourceCount>0, sourcesRead>=1`, non-empty `domains`, non-empty `cited`.
- Screenshot: the answer WITH its source cards/citations.

## f. `what time is it?`

- Expected: the REAL current local time (compare with the phone clock), no
  search, no hedging about not knowing the time.
- Trace: `capability=TIME, trigger=none, searchExecuted=false` (time evidence
  is built server-side, no web search).
- Screenshot: the reply next to the phone's clock.

## Pass rule

All six pass on the phone AND the diagnostics block matches the released
version/revision → the device is accepted for testing this build. Any
failure: capture the screenshot AND the diagnostics JSON row for that prompt
— that pair is the bug report.
