# Production generative UI — how this is supposed to work

Status: implemented in `src/lib/widgets/` (v0.81.6 backend). This document is
the contract, the reasoning, and the honest list of what is and is not
guaranteed.

## 1. What ChatGPT and Claude actually do

They do **not** ask the model to "please emit JSON in a code fence" and hope.
The pipeline is:

1. **Declare tools with a JSON Schema.** The model is given callable tools, not
   prose instructions. OpenAI: `tools[].function.parameters` + `strict: true`
   ([structured outputs](https://platform.openai.com/docs/guides/structured-outputs)).
   Anthropic: `tools[].input_schema` + `strict: true`
   ([tool use](https://docs.anthropic.com/en/docs/build-with-claude/tool-use/overview),
   [strict tool use](https://docs.anthropic.com/en/docs/build-with-claude/tool-use/strict-tool-use)).
2. **Constrain decoding.** Strict mode compiles the schema into the sampling
   grammar, so the output *cannot* violate the schema. This is the part a prompt
   can never provide.
3. **Validate again on the server.** Even with strict mode, the server checks
   the payload against its own copy of the schema before it reaches the UI.
4. **Render by component identity, not by parsing prose.** Vercel's generative
   UI maps a tool call to a React component
   ([AI SDK](https://sdk.vercel.ai/docs/ai-sdk-ui/generative-user-interfaces)).
   Google's A2UI does the same with a declarative, platform-neutral JSON
   protocol ([a2ui.sh](https://a2ui.sh/)); MCP-UI does it with sandboxed frames.
5. **Fall back.** If the model refuses or the call fails, the assistant still
   answers in prose. The UI is an enhancement, never a single point of failure.

The lesson: **the guarantee lives in the pipeline, not in the model.** A prompt
can be ignored; a grammar cannot.

## 2. The GS constraint

GS's provider (`z-ai-web-dev-sdk`) exposes **no native tool calling** — this is
documented in `src/lib/websearch.ts` and is why widget rendering has been
prompt-driven. That design has a known failure mode: a weak or text-only model
answers "show me a chart" with *"I can't display visualizations"*, and the app
shows that refusal verbatim.

So GS cannot copy step 1–2 today. It compensates with steps 3–5, and keeps the
tool definitions ready for the day the provider changes.

## 3. The pipeline as built

```
user turn
   │
   ├─ model streams prose (+ maybe widget fences)
   │
   ├─ compileWidgets()                    src/lib/widgets/compile.ts
   │     ├─ path MODEL     valid fences → normalise, cap, keep
   │     ├─ path REPAIRED  malformed JSON → repairJson() → validate
   │     └─ path FALLBACK  no widget + visual request + numbers in text
   │                        → deterministic extraction → chart fence
   │
   ├─ persist + emit `done` (every client now receives renderable content)
   │
   └─ client renders natively (Compose / SwiftUI / web)
```

Files:

| File | Responsibility |
|---|---|
| `catalog.ts` | kinds, subtypes, limits — one source of truth |
| `validate.ts` | fence extraction, JSON repair, per-kind validation + normalisation |
| `compile.ts` | three-path compiler, deterministic extraction fallback |
| `tools.ts` | OpenAI / Anthropic tool definitions for a tool-calling provider |

### What `repairJson` fixes

Models fail in boring, repeatable ways. These are repaired rather than dropped:

- trailing commas before `}` / `]`
- smart quotes (`“ ” ‘ ’`)
- `//` and `/* */` comments
- a prose prefix before the first `{`
- one missing closing brace (truncated stream)

Anything else is **dropped**: an unrepairable fence is removed from the answer
instead of shipping a broken block.

### What validation guarantees

- the kind is in the catalogue;
- the subtype is a known enum value (unknown chart type → `bar`, marked repaired);
- numbers are numbers (numeric strings coerced), non-finite values rejected;
- pie/donut accept one point per slice; every other chart needs two;
- caps are enforced (3 widgets, 8 series, 200 points, 100 rows, 8 columns, 64 KB);
- https is required for images.

### What the fallback will not do

It will **not** invent data. If the user asks for a chart and the answer states
no numbers, the compiler returns `path: none`. (The Android client additionally
shows a clearly labelled *sample* chart in that case; the server does not,
because a server-invented number could be mistaken for a fact.)

## 4. Platform notes (why the wire format is fences, not components)

The wire format is a fenced block whose body is JSON. That is deliberate:

- **Android (Compose)**: the block list keys siblings by `sourceStart`
  ([LazyColumn keys](https://developer.android.com/develop/ui/compose/lists#item-keys)).
  A synthesised block must therefore carry a unique key — v0.81.4 shipped a
  collision here and it was a real crash. Fixed with a sentinel offset.
- **iOS (SwiftUI)**: the same rule applies to `ForEach` identity
  ([Identifiable](https://developer.apple.com/documentation/swiftui/identifiable)).
  Synthesised models need stable, unique IDs, not array indices.
- **Web**: the JSON is rendered by a React component map, exactly like the AI
  SDK's generative UI.

Keeping one JSON contract means a renderer can be rewritten without touching
the model, and the model can be swapped without touching the renderers.

## 5. What is guaranteed, and what is not

**Guaranteed by this pipeline**

- A widget that reaches a client is schema-valid and renderable.
- Malformed model output is repaired or removed; it never renders as a broken card.
- When the answer contains the numbers and the user asked for a visual, a widget
  is produced even if the model refused.
- Every client gets the same content, because the work happens before persistence.

**Not guaranteed**

- That the model *chooses* to render a widget. That is model judgement, and no
  prompt can force it.
- That the model produces correct data. Validation checks shape, not truth.
- That the deployed backend runs this code. **It currently does not** — the live
  server still answers "I can't display charts", and its prompt omits the emoji
  rule, which proves it is an older build.

## 6. Migration path to a tool-calling provider

1. Send `toOpenAITools()` or `toAnthropicTools()` with the request.
2. On `tool_use`, call `toolCallToFence(name, input)` and stream that fence in
   place of the tool call.
3. Keep `compileWidgets()` as the validator. Never trust the provider's schema
   check alone; a server-side check is what protects the UI.
4. Delete nothing. The prompt path stays as the fallback for models without
   tool support.

That is the whole difference between "we asked the model nicely" and
"the model cannot ship something we cannot render".

## 7. Fence routing: one canonical language set (v0.81.7)

Models do not only emit ```chart. They emit the **sub-type as the fence
language** — ```funnel, ```gantt, ```heatmap, ```stackedarea — with the type
implied by the fence. That is a valid, common shape, and both clients used to
treat it as an unknown language:

1. the fence fell through to `Block.CodeBlock`, so the transcript showed raw JSON;
2. the recovery pass then saw "no widget" and drew a *sample* chart
   ("Item 1…4") **underneath a payload that was already correct**.

The fix is a single mapping, not a per-site patch:

| Layer | Symbol | Behaviour |
| --- | --- | --- |
| Android | `ContentBlocks.widgetKindOf()` | alias → `chart`, kind → itself, else `null` |
| Android | `injectChartType()` | adds `"type"` when the payload omits it; an explicit type always wins |
| Android | `WidgetRecovery.widgetFence` | built **from** `WIDGET_FENCE_LANGUAGES`, so it cannot drift |
| Backend | `catalog.widgetKindOf()` / `injectChartType()` | same mapping; the persisted answer is rewritten to canonical ```chart |
| Backend | `validate.extractWidgetFences()` | normalises alias fences before validation |

`stackedarea` → `stackedArea` is the only case where the alias is not already
the canonical spelling.

## 8. Visual identity is derived, not requested (v0.81.7)

The prompt cannot make 150 widget kinds look different — the renderer has to.
Three derivations, all stable and all in the client:

- **Palette.** `WidgetPalette` holds 12 fixed data hues instead of cycling five
  theme roles (`accent/success/warning/link/toolExecution`). Colour is data;
  a series keeps its identity across light/dark and across widgets.
- **Card tone.** `widgetTone(tag)` picks one of four surfaces — `Plain` (dense
  content), `Tinted` (charts), `Hero` (KPI/gauge), `Outline` (lists). Every
  existing view inherits it from its tag, with no per-call-site bookkeeping.
- **Card hue.** `widgetHue(tag)` gives each family its own hue, and charts
  further key off their **sub-type** (`CHART_HUE_SLOTS`), so a bar chart, a
  funnel and a heatmap in one answer read as three different objects.

One real rendering bug fell out of this work: pie and donut keyed slice colours
off `series.indices`. The common payload is **one** series holding one point per
slice, so `colors.size == 1` and every slice was painted the same colour — a
five-slice pie that looked like a single-colour disc. Both now key off
`slices.indices`.

Verified by `WidgetPaletteTest` (12 distinct hues, stable cycle, 12 distinct
family hues, four distinct tones) and `ChartFenceRoutingTest` (9 routing cases).

## 9. What the live server actually sends (v0.81.8)

Probing the deployed backend with three prompts produced three payloads, and
**two of the three failed on the client**. This is the part that no amount of
prompt engineering would have caught, because the model was behaving sensibly —
our decoder was not.

| Probe | Server output | Before |
| --- | --- | --- |
| "bar chart of quarterly revenue" | ```` ```chart ```` + `data: [[0,120],[1,180]]` | rendered |
| "funnel chart of checkout" | ```` ```funnel ```` + `data: [12000,5400,2100,1600]` | code block + fake sample chart |
| "Bar chart: Revenue Q1 120…" | **no fence at all**, `data: [["Q1",120],…]` | raw JSON as body text |

Three fixes, all at the boundary rather than in the renderers:

1. **`PointListSerializer`** accepts `[x,y]`, flat `[v,…]`, `["label", v]` and
   `{"x":…,"y":…}`, normalising everything to `[x, y]` at decode time. Before,
   only the first shape decoded; the other three made `parseWidget` return null.
2. **`promoteBarePayload()`** turns a paragraph or ```json block that is exactly
   one valid widget payload into a real widget block. Conservative by
   construction: one JSON object, inferred kind, must pass the same validator a
   fenced payload passes, and an unterminated payload stays text while streaming.
3. **`inferWidgetKind()`** reads the payload's own discriminator (`series`,
   `columns`+`rows`, `cards`, `entries`, …) and confirms it with the validator,
   so prose containing braces can never be promoted.

The backend got the same two behaviours (`normalisePoints`, `extractBareWidget`)
because it owns the text before persistence — a client-only fix would leave the
stored transcript full of JSON.

Verified: `PayloadShapeTest` (9 cases) on Android, compiler assertions 16–18 on
the backend.

## 10. Composition, art direction, and a rulebook (v0.81.9)

The thing that separates ChatGPT and Claude from "a template engine" is not a
secret renderer. It is four ordinary pieces, all of them now present here:

| Piece | ChatGPT / Claude | GS |
| --- | --- | --- |
| Component library | supported chart/table/app types | the 150 native widget kinds |
| Composition | model arranges blocks per answer | model emits any mix of fences, up to 3 per answer |
| Design system | tokens: type, space, colour, dark mode | `src/lib/design/rulebook.ts` |
| Renderer | host draws structured output | `WidgetBlocks.kt` / `WebView` for ```html |

**The rulebook is the missing piece that made output look generic.** Claude
loads design guidelines *before* it draws (its tool description literally says
"call visualize_read_me once before your first show_widget"). GS had no
equivalent, so the model had no vocabulary beyond "give me a card": it produced
the same grey block every time. `DESIGN_RULEBOOK` now defines the 12 data hues,
the ink scale for light and dark, the type and spacing scales, the composition
rules (lead visual, at most three blocks, never two of a kind adjacent, never
two adjacent blocks sharing an accent) and the bans (emoji, ASCII charts, >5
series, shadow+border, fixed widths).

**Art direction is a payload decision, not a renderer default.** Every widget
accepts two optional keys:

```json
{ "type": "bar", "accent": "#E8590C", "variant": "hero", ... }
```

`accent` is any `#RRGGBB`; `variant` is `plain | tinted | hero | outline`. On
Android they travel through `LocalWidgetStyle` (a `staticCompositionLocalOf`),
so all ~40 widget views honour them without a parameter threaded through 18
data classes. `seriesColor(0)` also follows the accent, otherwise the card
would be tangerine and its bars azure. The backend carries both keys through
validation instead of rebuilding the payload without them.

**Bespoke work has an escape hatch.** When no kind fits — a game, a simulation,
an animated explainer — the model emits a fenced `html` block and writes it
itself; it runs in the existing sandboxed WebView. The prompt says this
explicitly, because "nearly fits" is how answers end up looking generic.

Verified by `WidgetStyleTest` (7 cases: hex parsing, alpha, invalid input,
unknown variant, styled payload still parses).

## 11. The theme reaches inside model-authored documents (v0.81.10)

A ```html block used to receive exactly two colours — text and accent — and a
transparent background. Everything else (surfaces, hairlines, the twelve data
hues, the type scale) the model had to invent, so every bespoke view came out
with its own greys and looked pasted onto the app. That is the same failure as
"every widget is the same grey card", just in the other direction.

The harness now injects the live theme as CSS custom properties before the
document loads:

```
--gs-text  --gs-text-2  --gs-text-3
--gs-surface  --gs-raised  --gs-border
--gs-accent  --gs-hue-1 … --gs-hue-12
--gs-radius  --gs-space  --gs-font
```

plus `color-scheme: light|dark`, so form controls, scrollbars and default text
already match the app. The contract in the prompt says to read them and never
hardcode `#fff`/`#000`; the rulebook is the same one the native widgets use, so
a ```html card and a native card are the same design system.

**`svg` is now a first-class fence.** Claude's visualizer accepts SVG or HTML;
models reach for SVG for diagrams, icons and small illustrations, and we showed
it as source code. An ```svg block is wrapped into the same sandboxed document
(and the height bridge already measured `<svg>` bounds).

## 12. Three bugs found by testing the prompt against the catalogue

Writing the drift guard (below) surfaced failures that no prompt change could
have fixed, because they were in our own validation:

1. **`repairJson` deleted every URL.** It stripped `//` to remove comments with
   a regex, which also removed the `//` in `https://…`. Every media, carousel,
   link, citation and image payload therefore failed as "invalid JSON" and was
   dropped. Comment stripping is now a string-aware scanner — there is no regex
   that can tell a comment from a URL.
2. **`media` lost `document` and `pdf`.** The clients render both; the catalogue
   did not list them, so the validator rewrote the type to `image`.
3. **Top-level data was rejected.** `{"type":"bar","labels":[…],"values":[…]}` and
   `{"type":"image","url":"…"}` are unambiguous, common, and were refused. Both
   now normalise into the documented shape.

The client got the same treatment: `widgetJson` is lenient (trailing commas,
comments, unquoted keys). The backend repairs payloads before persisting, but
the client also renders older transcripts and mid-stream payloads, so it cannot
depend on that.

## 13. The drift guard

`npm run test:widgets` now asserts that **every** kind, chart type, media type,
calc type, app type and form field in the catalogue is named in the prompt, that
every kind has a sample payload which validates, and that the art-direction and
theme-token contracts are present. A capability the prompt never names is a
capability the model will never use, however good the renderer is.

## 14. Sub-types as fence languages, and charts that read as charts (v0.81.11)

The ```funnel bug had siblings. Models name the *shape* in the fence for every
family, not just charts:

| Fence | Kind | Injected type |
| --- | --- | --- |
| ```kanban, ```booking, ```calendar | `app` | the fence itself |
| ```loan, ```mortgage, ```tax | `calc` | the fence itself |
| ```gallery, ```pdf, ```search | `media` | the fence itself |

All of them used to fall through to `Block.CodeBlock`. The mapping lives in one
place per client (`widgetKindOf` + `injectWidgetType`) and the backend
normalises before persistence, so the stored answer only carries canonical
fences. Two deliberate exceptions: ```code stays source code (a JS object
literal starts with `{`), and `search` resolves to `media` because a ```search
fence holds results, not a filter panel.

The same work exposed a tone bug: `media`/`calc`/`app` views pass their
**sub-type** as the card tag, so `widgetTone` never matched the family and every
calculator, board and PDF preview silently fell back to the plain surface. The
tone table now resolves the family first (`widgetFamily`), keeping the per-type
hue and the intended surface.

Charts also got their axes: `niceStep` snaps the ceiling to a 1/2/5×10^n step so
the four gridlines read 0/50/100/150 instead of 0/61/122/183, the bar chart
labels each bar and the axis, and the line chart gained the same gutter and
x-labels. A chart without a scale is a picture; with one it is a chart.

## 15. The clock travels on the wire (v0.81.12)

`prepareTurn` now anchors every relative date to the device clock:

```
body.timezone = TimeZone.getDefault().id   // "Asia/Dhaka", on every turn
→ CONTEXT: Today is 2026-10-20 in Asia/Dhaka (local time 01:39).
   Resolve every relative date … from this clock, never from memory.
```

The backend reads `body.timezone`, falling back to the `x-client-timezone`
header; null means UTC. The Android default parameter is the device zone, so
the translation scratch conversation carries it too, and `explicitNulls=false`
keeps the field off the wire entirely when it is null — old servers never see
a new key. The wire contract is locked by `SendMessageRequestTest`.

Two smaller things landed here as well. The table widget right-aligns a column
when most of its non-blank cells match one quantity shape (sign/currency +
digits + optional unit) — one `n/a` no longer breaks the column, and numbers
down a column can finally be compared. And the form widget's submit flow is
confirmed end to end: fill → Submit → a summary the user reads → "Send as
message" → `dispatch(text, echoUser = true)` — the widget does not fire a
prompt the user never saw.

## 16. The live PDF payload, and the probe that caught it (v0.81.13)

`scripts/live-widget-probe.ts` asks the live server for ten widgets by fence
name and routes the raw reply through the current rules. Ten out of ten came
back fenced — but the ```pdf reply was **rejected**: the server answers a
document request with

```json
{"title":"Lease agreement","pages":[{"page":1,"text":"LEASE AGREEMENT …"}]}
```

and every layer answered `media needs items, code or text`. A page body has no
item URLs, so the widget shipped as **nothing**. The clients were equally
empty: `DocList` filtered on `it.url.isNotBlank()` and printed "No document"
into the void.

The fix treats a page body as the document itself:

- `validate.ts` — `pages:[{page,text}]` validates; a typeless payload that
  carries pages becomes `pdf`, not `image`; `inferWidgetKind` offers `media`
  for a bare `pages` key.
- Android — `MediaWidget.pages: List<DocPage>`; a `doc` with pages parses;
  `DocList` renders pages first (page number, body, `+N more pages`) and URL
  documents after; a blank type plus pages means `pdf`.

Verified end to end: the saved live bytes compile to one `media` widget with
zero errors, assertions 25 in the backend suite lock it, and the Android side
has the same payload pinned in `SubtypeFenceTest`.

## 17. Twenty-five live prompts, zero dropped (v0.81.14)

The probe grew from routing-only to a two-gate check (fence routed AND the
compiler keeps the widget) over 25 prompts across every family, and it caught
three more live shapes:

| Live shape | Was | Now |
| --- | --- | --- |
| `{"title":…,"pages":3}` | `List<DocPage>` decode threw — whole widget lost | `DocPages` union deserialiser: array → bodies, number → count |
| ````search` with `results` (not `items`) | `media needs items` | `results` is read as the item list |
| ````gantt` with `entries` (a schedule) | `chart needs at least one series` | retried as a `timeline` widget, fence rewritten |
| ````mindmap` with mermaid syntax | rendered as source code | routes to the mermaid diagram renderer |

The `pages` count case is the instructive one: Kotlin data classes cannot
distinguish "absent" from "default", so absence must be read off the raw JSON
object — and a number in a `List<T>` field throws during decode, which the
`catch` in `parseWidget` turns into a silent null. Both traps are now pinned by
tests on both sides, and the probe reports **25 routed, 0 not**.

## 18. The natural-language canary (v0.81.15)

`scripts/live-natural-probe.ts` asks twelve requests the way users actually ask
them — "Gantt chart for a 6-week launch plan", never naming a fence. Against the
currently deployed server, five of twelve rendered. That is the **deployment
gap talking, not a client bug**: the old build carries no `WIDGET_PROMPT` and no
rulebook, so the model answers in prose. The probe is a canary for the deploy —
the same row that reads `fences=[none]` today turns into a widget the day the
server ships.

One real client bug did surface. Asked for a Gantt chart, the live model wrote
mermaid's gantt *syntax* (`title … dateFormat … section …`) inside the ```gantt
fence. The compiler validated it as a chart payload, answered "invalid JSON",
and **stripped the fence** — destroying a diagram the client could have drawn.
Both sides now apply one rule: a widget fence that is not a JSON object is not
a widget. `extractWidgetFences` skips non-`{` bodies, and `fenceToBlock` routes
a non-JSON ```gantt (and any ```mindmap) to the mermaid renderer. The natural
probe is run the same way as the fence probe: `npx tsc … && node …`.
