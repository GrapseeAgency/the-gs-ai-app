# GS Search Event Protocol — PHASE 8.2 (v2, FROZEN)

Canonical contract for the search/research event stream. Superset of v1: every
v1 event remains byte-compatible; new events are additive and old clients
ignore unknown event names. One line per event on the SSE wire:
`data: {"event":"<name>","data":"<payload>"}` where payload is a STRING — for
search/source/clarify/research events the payload is a JSON-encoded object
(double-encoded), exactly like `done`.

## Base events (unchanged from v1)
- `status` — payload string: `searching` | `working` | `composing` | `search_failed`
- `delta` — payload string: answer text chunk
- `done` — payload string: JSON of the persisted Message (includes `sources[]`, optional `clarifyOptions`)
- `error` — payload string: error text

## `search` event (v1 subtypes + v2 additions)
`{"type":"started","intent":"sector_news","depth":"quick","label":"AI news"}`
`{"type":"query","round":1,"query":"AI news today","engines":["bing-news-rss"]}`
`{"type":"engines","round":1,"engines":[{"id":"google-news-rss","status":"ok","count":8},{"id":"z-ai","status":"failed","error":"rate limited"}]}`   ← v2: per-engine truth (§16/§28 search.engine)
`{"type":"results","round":1,"query":"AI news today","found":12,"engines":["bing-news-rss","google-news-rss"]}`
`{"type":"round","round":1,"sourcesVerified":4,"sourcesFailed":1,"verified":true,"syndicatedGroups":1,"elapsedMs":8421}`   ← v2 fields
`{"type":"failed","reason":"rate_limited"}`
`{"type":"completed","queries":2,"sources":5,"retrieved":4,"usedCitations":[1,2,4]}`   (legacy; see `research` completed)

## `source` event (v1 subtypes + v2 additions)
One event per real backend action on one source. `ordinal` = citation number.
`{"type":"discovered","source":{...}}`            — same shape as v1, plus `sourceType` + `authority` (v2)
`{"type":"opening","ordinal":1}`
`{"type":"reading","ordinal":1}`
`{"type":"read","ordinal":1,"chars":5231,"publishedDate":"...","status":"retrieved"}`   ← v2 canonical name of v1 `completed` (BOTH are emitted; clients must treat them idempotently)
`{"type":"completed","ordinal":1,"chars":5231,"publishedDate":"...","status":"retrieved"}`   (v1 alias, still emitted)
`{"type":"evidence","ordinal":1,"chars":5231,"windowChars":1800}`   ← v2 (§28 evidence.extracted — extraction really happened)
`{"type":"failed","ordinal":1,"reason":"...","status":"failed"|"snippet_only"}`
`{"type":"skipped","ordinal":1,"reason":"budget","status":"skipped"}`

status values: `discovered` | `retrieved` | `snippet_only` | `failed` | `skipped` | `used`
sourceType values (v2): `news` | `general` | `reference` | `academic` | `book` | `primary`
authority values (v2): `primary` | `academic` | `reputable` | `reference` | `discovery`

## `research` event (NEW in v2 — research-level truth, §28)
`{"type":"started","intent":"sector_news","depth":"deep","model":"gs-free","roundsPlanned":3}`
`{"type":"round_completed","round":1,"found":18,"discovered":12,"read":3,"failed":2,"syndicatedGroups":1,"elapsedMs":9430}`
`{"type":"synthesis_started","model":"gs-free"}`                     (§28 synthesis.started)
`{"type":"completed","queries":3,"sources":7,"retrieved":5,"usedCitations":[1,2,5],"totalMs":23140,"timings":{"firstSearchMs":812,"firstSourceMs":1903,"firstTokenMs":22100}}`
`{"type":"failed","reason":"no_results","message":"..."}`
`{"type":"cancelled","by":"user"}`

## `clarify` event (unchanged)
`{"question":"What kind of news would you like today?","options":[...]}`

## Spec-name mapping (§28 → wire)
research.started→research:started · search.query→search:query · search.engine→search:engines ·
search.results→search:results · source.discovered/opening/reading→source:discovered/opening/reading ·
source.read→source:read (+ legacy completed) · source.snippet_only→source:failed(status snippet_only) ·
source.failed→source:failed · evidence.extracted→source:evidence · research.round.completed→research:round_completed ·
synthesis.started→research:synthesis_started (+ status composing) · answer.delta→delta ·
citation.bound→research:completed.usedCitations (+ search:completed) · research.completed→research:completed ·
research.failed→research:failed

## Honest-state rules (binding for every client)
- Render ONLY from received events. No timers, no fake steps, no fake reading.
- Orb: SEARCHING only during real engine/search activity; WORKING only while
  source retrieval/evidence processing is in flight; COMPOSING on
  `research:synthesis_started`; IDLE after done.
- Favicons: `https://icons.duckduckgo.com/ip3/<domain>.ico` derived CLIENT-side
  from the domain; monogram fallback; never trust model text for URL/domain.
- Citations `[N]` resolve only against persisted sources with ordinal N.
  snippet-only sources display "headline only". Never claim a page was read
  when its status is not `retrieved`.

## Research details (§20 audit trail)
Clients compose the expandable "Research details" view from real events:
queries run, engines with per-engine outcome, sources found/read/failed,
syndicated groups, citations used. After reload, derive from persisted
`sources[]` (+ trace summary); live detail beyond that is memory-only.

## MessageJson additions (backward compatible)
`sources[]` now also includes: `sourceType` (string), `authority` (string),
`syndicatedOf` (int|null — ordinal of the representative source it duplicates).
`Message.researchTimings` (optional JSON string) carries the timing block.
