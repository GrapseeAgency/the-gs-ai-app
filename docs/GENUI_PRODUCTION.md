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
