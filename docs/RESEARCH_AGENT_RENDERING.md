# RESEARCH — Agent-driven rendering & formatting in chat

> Kickoff research for the "agent renders and formats whatever it wants" tier.
> Two deep passes, 2026-11-05. Sources cited inline. Feeds the design work that
> follows; nothing here is implemented yet.

## 1. The industry has standardized into LAYERS (not one protocol)

| Layer | What it standardizes | Names to know |
|---|---|---|
| Transport / events | How agent activity + state stream to a frontend | **AG-UI** (CopilotKit; 17 typed event families over SSE, adopted by Google/LangChain/AWS/MS), plain SSE/streamed fetch |
| UI description | How the UI itself is described in the payload | **A2UI** (Google, v0.9→v1.0: declarative JSONL, component **catalogs**, functions registry), **OpenUI Lang** (Thesys), Markdoc tags (mdocUI) |
| Rendering surface | Where generated UI renders inside a chat host | **MCP Apps**, **OpenAI Apps SDK / ChatKit**, mcp-ui |

Key sources:
- awesome-generative-ui (github.com/narrowin/awesome-generative-ui): "Late 2025–
  mid 2026: standardization into layers… production systems mostly settled on
  **constrained, allow-listed components**."
- A2UI v0.9 announcement (developers.googleblog.com, 2026-04-17): Agent SDK,
  version negotiation, dynamic catalogs, **"resilient streaming: incrementally
  parse and heal LLM output, allowing the client to render UI components as
  they are being generated — no waiting for the full JSON block."**
- A2UI v1.0 spec (github.com/a2ui-project/a2ui): envelope schema, catalog
  JSON-Schema rules, `allowedParents/allowedChildren` composition constraints,
  renderer-side validation with **component isolation** (one invalid component
  renders an isolated error; the rest of the surface still displays).
- Android is first-class now: Jetpack Compose **A2UI renderer (alpha 02)** —
  parses streamed JSONL, validates against catalog, renders `ATUISurface`,
  progressive placeholders while streaming, and an **agent self-correction
  loop** when a component fails validation (daily.dev, 2026-10-30).
- Vercel AI SDK 6: `ToolLoopAgent` + tool-result→component mapping is the
  recommended path; RSC `streamUI` is paused/experimental (fast.io guide,
  reactify-solutions AG-UI deep dive).

## 2. Streaming structured UI — the production patterns that matter

From production post-mortems and guides (chiraghasija.cc 2026-07, nisai.dev,
dev.to/loopandretry 2026-09, llmbestpractices.com, joinloop.co.in 2026-10):

1. **Tolerant partial-JSON repair for DISPLAY; strict validation only on
   stream close.** Auto-close open strings/brackets with a small stack, parse
   the repaired buffer, keep the last parseable version; render it optimistically.
2. **Never commit scalars until closed.** A partial `"total": 14` that becomes
   `142.50` looks like a bug — render partial strings, but hold numbers/booleans
   until the closing token proves them complete.
3. **NDJSON/JSONL** (one object per line) is the friendliest stream format for
   lists of independent items — A2UI adopted exactly this.
4. **Key in-flight buffers by block INDEX** (a single response can interleave
   multiple tool calls/blocks; flattening by name merges them into garbage).
5. **Render budget:** batch deltas to one paint per animation frame; memoize
   completed markdown blocks; only the last, still-growing block re-renders.
6. **Field-order-aware schemas:** the fields the UI shows first should be
   EMITTED first (summary before long arrays) — schema design is UX design.
7. **Optimistic display, conservative execution:** the display path may guess;
   the execution path only ever sees the fully-accumulated, strictly-parsed
   payload after the stream's stop event.
8. **Don't validate partial objects against the full schema** — required fields
   legitimately haven't arrived yet; you'll drown in false errors.

## 3. Formatting JUDGMENT — when to render what

The "agent formats whatever it wants" problem is a decision problem before it
is a rendering problem. Collated guidance (prompt-architects.com format matrix,
aiseo structure study, neuralbuddies markdown research):

| The job | The right surface |
|---|---|
| Comparing options with shared dimensions | table (or compare widget) |
| Sequence of actions / order matters | numbered steps |
| A decision | **verdict first**, then reasoning |
| Data another system parses | JSON (never prose) |
| Nuance still being explored | prose |
| Actions to take today | checklist |
| Trends/distributions/relationships over numbers | chart — pick by relationship |
| Things that DON'T share dimensions | do NOT force into a table — per-item prose or explicit N/A rules |

Meta-rules with evidence behind them: put format instructions EARLY and
concrete (models default to prose); strict formats can manufacture false
precision — every strict format needs an escape hatch ("N/A if not applicable");
worked examples beat descriptions ("match this shape exactly").

## 4. Where GS already IS (stronger than it may realize)

GS independently built the A2UI philosophy before A2UI was named:
- `src/lib/widgets/catalog.ts` = a **catalog** (35 kinds × sub-types, LIMITS),
- `validate.ts` = **validate + repair + clamp** (A2UI's validate-before-render,
  including repair-JSON and per-widget fallbacks),
- `prompt.ts` = a computed, drift-proof **system-prompt contract** (A2UI's
  `generate_system_prompt`),
- streamed fenced JSON in the chat stream = the transport (JSONL-adjacent),
- 190+ renderers on web, full native parity on Android (audited: 206 structural
  checks, 168/168 functional render proof, Kotlin type-set mirror).

The gaps vs the 2026 state of the art:
1. **Progressive widget streaming** — we render a widget only when its fence
   closes; the SotA renders title/fields progressively with skeletons and
   completes them as tokens land (A2UI "resilient streaming").
2. **Judgment prompt** — nothing in the prompt teaches WHEN to use chart vs
   table vs steps vs prose (section 3's decision matrix is absent).
3. **JSONL/line-oriented option** — one-fence-one-widget caps composition;
   A2UI-style multi-message streams would let one answer compose several
   surfaces progressively.
4. **Component isolation on web** — a broken widget falls back per-widget
   (good), but the A2UI pattern of rendering siblings with an isolated error
   chip for the broken one is stricter.
5. **iOS** — the new `ios/` app has NO widget renderer yet; this tier is the
   natural moment to give it the catalogue (SwiftUI declarative rendering of
   the same validated payloads).
6. **Self-correction loop** — A2UI's renderer sends validation errors BACK to
   the agent for an in-place fix; our repair is deterministic-only.

## 5. Design principles this tier should follow (evidence-backed)

- Catalog-constrained beats free-form codegen for production (security: no
  eval'd HTML; reliability: validated shapes; consistency: one design system).
  Free-form ```html remains the escape hatch, as today.
- The stream is a sequence of partial states — the UI must render EVERY one
  without ever looking broken (fix scalars-late, animate-frame batching,
  skeleton-until-closed).
- Judgment is teachable: an explicit decision matrix in the prompt (chart when
  relationship-over-values, table when scanning shared dimensions, steps when
  order matters, verdict-first for decisions) measurably shapes output.
- Anti-monotony stays a RULE, not a hope: art direction derives per widget
  (already shipped: PALETTES/SURFACES/directWidgetArt) and must survive the
  new pipeline untouched.

## 6. Next step

User is supplying the finished UI designs ("the perfect UI design we made") so
the renderer and the judgment prompt can target the REAL design system rather
than invented ones. Scope questions (surfaces, free-form vs catalog, staging)
recorded in the session; to be resolved before implementation.
