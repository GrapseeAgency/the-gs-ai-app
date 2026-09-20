# GS Search Event Protocol — PHASE 8.1 (v1, FROZEN)

Canonical contract for the search/research event stream. Additive to the
existing SSE wire: every event is one line `data: {"event":"<name>","data":"<payload>"}`
where `payload` is a STRING — for all `search*`/`source`/`clarify` events the
payload is a JSON-encoded object (double-encoded), exactly like the existing
`done` event. Old clients that ignore unknown event names stay compatible.

## Existing events (unchanged)
- `status` — payload string: `searching` | `composing` | `search_failed` (+ NEW: `working`)
- `delta` — payload string: answer text chunk
- `done` — payload string: JSON of the persisted Message (now includes `sources[]` + optional `clarifyOptions`)
- `error` — payload string: error text

## New events

### `search`
`{"type":"started","intent":"sector_news","depth":"quick","label":"cybersecurity news"}`
`{"type":"query","round":1,"query":"cybersecurity news today","engines":["bing-news-rss","google-news-rss"]}`
`{"type":"results","round":1,"query":"cybersecurity news today","found":12,"engines":["bing-news-rss","google-news-rss","duckduckgo-lite"]}`
`{"type":"round","round":1,"sourcesVerified":4,"sourcesFailed":1,"verified":true}`
`{"type":"failed","reason":"rate_limited"}`   (search-level failure)
`{"type":"completed","queries":2,"sources":5,"retrieved":4,"usedCitations":[1,2,4]}`

### `source`
One event per real backend action on one source. `source.ordinal` is the
citation number. NEVER emitted for a source that was not actually processed.
`{"type":"discovered","source":{"ordinal":1,"title":"...","url":"https://...","domain":"reuters.com","snippet":"...","publishedDate":"2026-02-19","status":"discovered","query":"..."}}`
`{"type":"opening","ordinal":1}`
`{"type":"reading","ordinal":1}`                     (fetch started, real)
`{"type":"completed","ordinal":1,"chars":5231,"publishedDate":"2026-02-19","status":"retrieved"}`
`{"type":"failed","ordinal":1,"reason":"timeout","status":"failed"}`
`{"type":"skipped","ordinal":1,"reason":"budget","status":"skipped"}`

status values: `discovered` | `retrieved` (full page read) | `snippet_only` (not fetched — headline only, honest) | `failed` | `skipped` | `used` (cited in final answer; also flagged in persisted sources)

### `clarify`
Emitted when the request is ambiguous (e.g. "what is today's news?" with no
topic/region). The assistant message this turn IS the clarification question.
`{"question":"What kind of news would you like today?","options":[{"id":"world","label":"World"},{"id":"technology","label":"Technology"},{"id":"business","label":"Business"},{"id":"science","label":"Science"},{"id":"sports","label":"Sports"},{"id":"entertainment","label":"Entertainment"},{"id":"health","label":"Health"},{"id":"bangladesh","label":"Bangladesh"}]}`
Tapping an option sends a normal user message ("Technology news"). The
persisted Message carries optional `clarifyOptions` (same JSON) so reloads can
re-render the chips.

## Honest-state rules (binding for every client)
- Render trace steps ONLY from received events. No timers, no fake steps.
- `status:"searching"` only after a `search started` event; `working` only
  while source retrieval is in flight; `composing` on synthesis start.
- Source cards: favicon URL = `https://icons.duckduckgo.com/ip3/<domain>.ico`
  (derive client-side from domain; never trust model text for URL/domain).
- Citation markers `[N]` in answer text are tappable and must open the URL of
  persisted source with ordinal N. Strip markers with no matching source.
- After `done`, the trace collapses to a summary line; source cards stay.

## MessageJson additions (backward compatible)
`sources[]` now includes: `status` (string), `used` (bool), `rank?` (int).
`Message` may include `clarifyOptions?: string` (JSON of the clarify payload).
