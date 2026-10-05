# OUTAGE: message send path returns 502 upstream_error (open since at least 2026-10-04)

**Status:** OPEN — needs server-side action. No APK can fix this.
**Reported by:** operator, real arm64 phone, twice ("even the flash model wasn't working at all")
**Verified by:** direct curl against the deployed host, repeatedly, across all model names

## Symptom

Sending any message returns 502 in ~1.4s with no reply:

```
POST /api/v1/conversations/{id}/messages
HTTP/2 502
{"code":"upstream_error","message":"GS AI is temporarily unavailable. Please try again shortly."}
```

Everything else works:

| endpoint | result |
|---|---|
| `POST /api/v1/conversations` | **201** (~1.0s) |
| `GET /api/v1/conversations` | **200** (~0.4s) |
| `GET /api/health` | **200** `{"status":"ok"}` |
| `POST /api/v1/conversations/{id}/messages` | **502** (~1.4s) |

## It is not model selection

Every model name tested against the live host returns 502:

```
model=flash         → 502
model=fast          → 502
model=default       → 502
model=gemini-flash  → 502
model=<none>        → 502
```

The app sends only `content`, `stream`, `attachments`, `mode` — it sends **no model
field at all**. Model routing is entirely server-side, so a client cannot select a
different model to dodge this. Every model is equally dead because the failure is
upstream of routing.

## Where the 502 comes from

`src/lib/turn-executor.ts`, in the catch around turn execution:

```ts
} catch (e) {
  const raw = String(e instanceof Error ? e.message : e)
  console.error(`TURN-ERROR conv=${id}: ${raw.slice(0, 300)}`)
  const message = useVision ? visionErrorMessage(raw) : userFacingTurnError(raw)
  ...
  result.errorResponse = { status: 502, code: 'upstream_error', message }
```

`userFacingTurnError` (line 1228) discards the real error entirely:

```ts
const userFacingTurnError = (raw: string): string => {
  console.error(`TURN-ERROR conv=${id}: ${raw.slice(0, 300)}`)
  return 'GS AI is temporarily unavailable. Please try again shortly.'
}
```

The raw provider error goes to the server log and **nothing else**. The client sees
a generic string with no error class, no status, no provider name. That is correct
for not leaking internals to users, and it is also why this is hard to diagnose from
the outside — you have to read server logs to learn anything.

## Why "flash" specifically felt broken

The user's phrasing was "even the flash model wasn't working at all", which reads
like a model-specific failure. It is not. Two things combined to produce that
impression:

1. Every model is down, so any model chosen fails identically.
2. The failure arrives as a **success-shaped HTTP response** — 502 arrives in
   1.4s, well inside any timeout — so the app treats it as a completed turn with no
   text, then renders a fabricated "connection dropped" notice. The 20-second wait
   the user reported was the *client-side* typewriter replaying an already-failed
   turn (fixed in v0.71.3), not the server thinking.

So the visible symptom was a slow, plausible-looking failure for a fast, total
server outage.

## Most likely cause

The provider credentials are gone. `src/lib/keypool.ts` documents a four-layer
durable key pool precisely because this has happened repeatedly:

```
FORENSIC AUDIT [2] — FOUR-LAYER DURABLE KEY POOL.
  The hosting platform rewrites .env, deletes .secrets/, and rolls back git
  history on every container reboot; API keys were destroyed at least four
  times mid-session.
```

Layers tried in order by `loadKeyPool()`:

| layer | source |
|---|---|
| (a) | `process.env.OPENROUTER_API_KEYS` |
| (b) | `.secrets/openrouter.keys` (gitignored) |
| (c) | SQLite vault `db/vault.db` (gitignored) |
| (d) | `/home/z/.gs-vault/openrouter.keys` (outside the project tree) |

If all four are empty, `loadKeyPool()` logs:

```
KEYPOOL layer=none count=0 — every key layer is empty (platform wipe); re-provision keys
```

and every generation fails. The keys are filtered to `sk-or-` prefixed values by
`parseKeys()`, so an expired-but-present key also yields an unusable pool.

**This is a hypothesis, not a confirmed diagnosis.** The four-layer design with
self-healing exists because this is what has happened before, but nothing in the
public repo can confirm the current state of the host's key layers. Confirm it from
server logs by grepping for `KEYPOOL` and `TURN-ERROR`.

## The CI pre-flight is fooled by the same health endpoint

`eval-gate.yml` line 89 gates the whole live eval on:

```yaml
- name: Pre-flight — origin must be alive
  run: |
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 "$ORIGIN_URL/api/health" || echo 000)
    if [ "$code" != "200" ]; then
      echo "::notice::origin $ORIGIN_URL not reachable — live eval is NEUTRAL this run"
      echo "reachable=false" >> "$GITHUB_OUTPUT"
    else
      echo "reachable=true" >> "$GITHUB_OUTPUT"
    fi
```

Because `/api/health` returns a hardcoded `200`, this pre-flight reports the origin
as reachable during a total send-path outage — which is the state right now. The eval
then proceeds, and every eval case fails against a 502 for reasons that have nothing
to do with whatever it was measuring. **Those failures are noise, not signal, and
should not be read as model regressions.**

Fixing `/api/health` as described below repairs this gate for free. Failing that,
the pre-flight should send one cheap real message rather than GET a static document.

## Second candidate: provider account or quota

Even with valid keys, an expired account, exhausted credit, or a provider-side
outage produces the same 502 — the catch in `turn-executor.ts` maps *any* error
during turn execution to `upstream_error`, including auth and rate-limit failures
from the provider. Distinguish these in the logs:

| log line | meaning |
|---|---|
| `KEYPOOL layer=none count=0` | all key layers wiped → re-provision |
| `KEYPOOL layer=env count=0` but another layer non-zero | self-heal should have run; check write permissions |
| `TURN-ERROR ... 401` | keys present but rejected → expired or revoked |
| `TURN-ERROR ... 402` | out of credit |
| `TURN-ERROR ... 429` | rate limited or quota exhausted |
| `TURN-ERROR ... 5xx` from provider | provider-side outage, not our problem |

## What to do

1. **Read the server logs.** `grep -E 'KEYPOOL|TURN-ERROR' <logfile>`. The table
   above maps the line to the cause. This is step 1 because `userFacingTurnError`
   destroys the diagnostic detail for every client.
2. **If `layer=none`:** re-provision with `scripts/provision-keys.ts`. The pool
   self-heals the other three layers on the next request, so one paste is enough.
3. **If keys are present and 401/402:** replace the keys; check the provider account
   has credit.
4. **Verify with the same curl used above.** A fixed host answers 200 on
   `POST /api/v1/conversations/{id}/messages` and streams tokens.

## Bug worth fixing regardless: the health endpoint lies

`src/app/api/health/route.ts` is the entire handler:

```ts
export async function GET() {
  return NextResponse.json({
    status: 'ok',
    backgroundRuns: backgroundRunsEnabled(),
  })
}
```

It returns a hardcoded `'ok'` and never contacts the provider. That is why this
outage is invisible to anything watching `/api/health` — the one endpoint a
monitor would poll has been reporting healthy the entire time the product is
non-functional.

A liveness probe that cannot observe the thing being load-bearing is decoration.
Options, cheapest first:

- Add a cheap authenticated provider probe (a 1-token completion) to `/api/health`
  and return `degraded` on failure. Costs a fraction of a cent per poll.
- Report `degraded` with a cached upstream result refreshed every N minutes, so
  the probe stays free.
- At minimum, surface key-pool state: `keypool: {layer, count}`. That alone would
  have turned this outage from "mysterious 502" into "KEYPOOL layer=none" at a
  glance, and it leaks nothing — the layer name and a count are not secrets.

Keep a separate unauthenticated `/api/health/live` for pure process liveness if
load balancers need something that cannot fail. Mixing the two is what caused the
confusion.

## Client-side, already shipped

v0.71.2 stopped the app from presenting a failed turn as the assistant's reply, and
v0.71.3 removed the artificial 26ms-per-word replay that made the failure *look*
like slow generation. The app now says the request failed and offers regenerate.

That is the honest behaviour, but it is presentation. It does not make the server
work, and it should not be mistaken for a fix to this outage.