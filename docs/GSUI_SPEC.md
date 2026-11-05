# GSUI v1 — limitless generative UI for Android + iOS

> The spec for the tier where the agent renders **whatever it wants** in the
> chat — beyond the 200-widget catalogue — natively on Compose and SwiftUI.
> Research basis: docs/RESEARCH_AGENT_RENDERING.md (A2UI v1.0 spec, OpenUI Lang
> token benchmarks, SwiftUI SDUI patterns, streaming-UX post-mortems).
> Status: DESIGN — implementation milestones at the bottom.

## 1. The three decisions (evidence-backed)

**D1. The wire format is a line-oriented DSL with forward references.**
OpenUI Lang's benchmark: 4,800 tokens vs 9,948 (+107%) for the same UI as
JSON (openui.com/docs/openui-lang/benchmarks). Its `id = Component(args)`
line format streams naturally: every completed line is a renderable
component, forward references render as skeletons until the referenced line
arrives (top-down reveal). Line-by-line parsing also degrades gracefully —
a truncated stream loses only the last line, not the whole payload.

**D2. The vocabulary is primitives + composites, arranged freely.**
"No layout is fixed in the components; the library is primitives the model
arranges itself" (thesysdev/open-intelligent-ui). GSUI gives the agent ~30
layout/text/media/control/data primitives PLUS every existing widget kind as
a named composite node. The catalogue becomes a subset, not a ceiling —
that is what "limitless beyond 200+" means in a safe architecture: the agent
composes anything from allow-listed parts, never writes code.

**D3. Both platforms render the SAME validated tree with their OWN design
system.** A2UI v1.0 removes hardcoded branding precisely so renderers defer
to native themes. GSUI payloads carry structure and semantics only; Android
renders them in Compose with WidgetCardShell/GsGlassControls, iOS renders
them in SwiftUI with GsGlassControls.swift — the mirrored-glass design
system the apps already share (identical geometry, e.g. 48×28 switch track).

## 2. The DSL (GSUI Lang v1)

Rides the existing chat stream as a fenced block; the parser is line-first:

    ```gsui
    card#resume {accent: teal, surface: hero}
      heading "Aria Rahman" {size: title}
      caption "Product engineer — Dhaka"
      row#stats {gap: 12}
        stat {label: "Shipped", value: 42}
        stat {label: "On-time", value: "97%"}
      tabs#detail
        tab "Work"
          bullets "- led X\n- built Y"
        tab "Contact"
          button "Email Aria" {action: prompt, prompt: "Draft an email to Aria"}
          button "Call" {action: prompt, prompt: "…"}
    ```

Grammar (v1, deliberately tiny):
- `component#id {props}` — one component per line; indentation = nesting
  (2 spaces per level, max depth 8).
- `#id` assigns an identity; a component can REFERENCE a not-yet-seen `#id`
  as a child placeholder → renderer shows a skeleton until the line lands.
- `{...}` props: `key: value` pairs; strings quoted; numbers/bools bare.
- Long values (table rows, chart series, list items) may use a single-line
  JSON array value — the ONE place JSON is allowed, parsed per-line.
- Comments (`//`) and blank lines are ignored — the model can annotate.

Why lines beat A2UI-style message envelopes for US: our existing transport
is one chat stream with fences; A2UI's createSurface/updateComponents/
updateDataModel/deleteSurface machinery buys dynamic multi-surface apps, not
single-answer composition. We adopt their ISOLATION and ACTION semantics
without their envelope. A2UI remains the migration path if GS ever needs
multi-surface, function-call, bidirectional sessions.

## 3. Primitive vocabulary (v1 — 30 primitives + all composites)

| Group | Primitives |
|---|---|
| Layout | `stack` (v/h/z), `scroll`, `card`, `grid`, `tabs`, `sheet`, `divider`, `spacer`, `flow` (wrapping row) |
| Text | `heading`, `body`, `caption`, `mono`, `bullets`, `quote`, `badge` |
| Media | `image`, `video`, `link`, `map` |
| Controls | `button`, `chips`, `toggle`, `slider`, `input`, `stepper`, `rating`, `progress` |
| Data | `stat`, `spark`, `table`, `timeline`, `kv` (key-value list), `diff` |
| Composites | every widget kind in catalog.ts (35 kinds × sub-types) as `widget:chart{...}`, `widget:sim{...}`, … — the full 200+ surface is addressable inside any layout slot |

Caps (mirror LIMITS): depth ≤ 8, nodes ≤ 120 per block, ≤ 3 gsui blocks per
answer, ≤ 64 KB per block, strings clipped at 400 chars, children arrays ≤ 60.
Every cap is clamp-and-render (the forgiveness contract), never reject.

## 4. Streaming mechanics (the part that makes it feel alive)

1. The client parses the fence LINE-BY-LINE as tokens arrive; each completed
   line mounts immediately (Compose/SwiftUI state update per line, native
   batching handles frame budget — the animation-frame discipline from the
   research applies automatically).
2. Forward references render a skeleton shell with the right approximate
   height; the referenced line replaces the skeleton in place.
3. Scalars (numbers, bools, enum-ish strings) inside the LAST line are
   uncommitted: the line is re-rendered only when it is complete (the
   "14 → 142.50" rule from the streaming post-mortems).
4. On stream end, the whole tree validates strictly once; any line that
   fails renders as an ISOLATED error chip — siblings stay (DynamicUI's
   skip-unknown pattern + A2UI component isolation).
5. The server may append a one-line self-correction (`// fix: …` re-issue of
   a failed line) — the A2UI self-correction loop, line-granularity.

## 5. Actions & safety

- `button`/`chips` carry `{action: prompt, prompt: "…"}` (sends a chat
  message — same as today's actions widget) or `{action: copy, text: "…"}`.
  No other action classes in v1: no navigation, no arbitrary intents, no
  URL open beyond existing link rules.
- GSUI is declarative ONLY. No codegen, no raw HTML inside gsui fences (the
  sandboxed ```html escape hatch continues to exist as its own thing).
- Unknown component names and unknown props are dropped with a debug
  annotation, never fatal. Depth/nodes/bytes caps clamp.

## 6. Renderers

**Android (Compose)** — `GsuiParser.kt` (line → tree, mirrors the TS
validator), `GsuiNode` sealed model, `GsuiView` dispatcher walking primitives;
reuses WidgetCardShell, WidgetPalette/seriesColor, GsGlassControls, and the
existing widget views for composite nodes. Progressive parse feeds a
`mutableStateListOf<GsuiLine>` — Compose recomposition handles the rest.

**iOS (SwiftUI)** — `GsuiParser.swift` (same grammar, same tests), `GsuiNode`
enum, `GsuiView: View` with a ComponentRegistry (DynamicUI/swiftui-json-render
pattern); reuses GsGlassControls.swift, AeroComponents, theme/accent
environment. Unknown-component isolation and accessibility labels are v1
requirements, not afterthoughts (DynamicUI proves the JSON can carry them).

**Shared truth:** the grammar + primitive props schema live in
`src/lib/gsui/` (TypeScript): the validator, the caps, and the prompt text.
Kotlin and Swift ports are generated-by-hand but TESTED against the same
fixture corpus (`src/lib/gsui/fixtures/*.gsui` must parse identically in all
three — the audit pattern from CATALOGUE_AUDIT.md, now three-way).

## 7. The judgment layer (what the prompt teaches)

Alongside the vocabulary, the system prompt gains the decision matrix
(RESEARCH_AGENT_RENDERING.md §3): chart when the question is about
relationships/trends in numbers; table when scanning shared dimensions;
steps when order matters; verdict-first for decisions; checklist for
actions; GSUI composition when the answer is an artifact (profile, plan,
comparison card, mini-app); plain prose when it's thinking, not showing.
Plus anti-monotony: art direction derives per block from the existing
rulebook palettes — never the same surface twice in a row.

## 8. Milestones

- **M1 — contract**: `src/lib/gsui/` (parser, validator, caps, fixtures,
  prompt section) + fence routing `gsui`/`ui` + tests. No renderer yet.
- **M2 — Compose renderer**: parser port + primitives + progressive
  streaming in the Android transcript; composite nodes delegate to the
  existing widget views.
- **M3 — SwiftUI renderer**: parser port + registry + glass primitives in
  ChatDetailView; the iOS app gets generative UI for the first time.
- **M4 — judgment + art direction**: prompt matrix live; palette derivation
  per GSUI block; self-correction line format.
- **M5 — three-way audit**: fixture corpus parses identically in TS/Kotlin/
  Swift; every primitive renders real content on both platforms (the
  render-proof pattern); docs updated (WIDGET_COVERAGE, GENUI).

## 9. What GSUI is NOT (v1)

- Not a multi-surface app runtime (A2UI's surfaces/data-model binding/
  renderer function calls — revisit only if a real use case appears).
- Not a markdown replacement — prose stays markdown; GSUI blocks are the
  artifact slots inside the transcript.
- Not unconstrained: the constraint IS the feature (allow-listed primitives,
  clamped caps, prompt-only actions). "Limitless" comes from composition
  space, not from executable freedom.
