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

---

# PASS 4 — deep spec read: A2UI internals, Artifacts, official Compose renderer, SDUI veterans (2026-11-05)

## A2UI v1.0 spec body (a2ui_protocol.md, read into §schemas)

- **Transport contract (4 requirements):** ordered delivery (stateful updates corrupt out of order), explicit message framing (JSONL newlines / WS frames / SSE events), metadata channel (capabilities exchange + data-model sync), optional bidirectional return for `action`.
- **Dynamic types are the core abstraction:** every bindable property is `Dynamic*` = literal | `{"@path": …}` (JSON-Pointer data binding) | `{"@call": …}` (function call). The renderer resolves them against the surface's data model — UI structure is SEPARATE from data.
- **ChildList templates:** a container's children can be a static id array OR a template `{componentId, path}` that instantiates one component per data-model list item. (Server-driven list rendering without repeating nodes.)
- **AccessibilityAttributes are protocol-level:** label, description, live (off/polite/assertive), hidden — attached to ANY component via ComponentCommon. Accessibility is not a per-platform afterthought in the spec.
- **Catalog swappability:** the envelope `$ref`s `catalog.json#/$defs/anyComponent`; mapping that placeholder to the Basic Catalog OR your own file is the entire customization story. Same envelope, your design system.

## Claude Artifacts — the OTHER philosophy (codegen in a sandbox)

- Side panel; each artifact = single-file HTML/React running in a sandboxed iframe on a SEPARATE ORIGIN (claudeusercontent.com), CSP-locked network, frozen library set (React, Tailwind, Recharts, shadcn, Lucide, D3, Three.js, lodash, mathjs, Papaparse), host↔artifact via window.postMessage; artifacts API lets the artifact call Claude itself; 20 MB persistent storage on published artifacts; MCP connections since Oct 2025.
- Mobile: smaller iframe inside the chat surface + fullscreen on demand.
- GS is deliberately in the OTHER camp (declarative → native), which on mobile is the right camp: native perf, native accessibility, the glass design system, no webview flash. But Artifacts teach: auto-trigger heuristics (≈15+ lines, self-contained, reusable), iterate-in-place (natural-language edit re-renders), and the split-origin security story.

## Official Jetpack Compose A2UI renderer (developer.android.com, Sept 2026) — CONCRETE API

- `A2uiMessageParser()` + `A2uiMessageProcessor(catalogs=[…])` in a ViewModel; `processor.activeSurfaces: StateFlow`; render with `A2uiSurface(surfaceModel)` keyed by `surface.id`.
- Component state machine: `Loading / Error / Success` per component; a **readiness evaluator** holds components until their dynamic data bindings resolve — progressive rendering is a first-class renderer feature.
- `materialA2uiBasicCatalogV1()` ships Material 3; a custom catalog fills 5 slots with no Material default: **Image, Video, AudioPlayer, UrlOpener, MessageFormatter**. Custom components register alongside.
- **`catalog.toJsonSchemaString()` → embedded in the system prompt.** The catalog IS the prompt (same as our widgets/prompt.ts discipline). joreilly.dev's working example: fenced ```a2ui JSONL array, flat component list, exactly one `id: "root"`, literal preformatted strings ("don't use ${} expressions"), button action = `{event: {name, context}}`.
- Two-way: `A2uiActionInterceptor` + `outboundEvents` flow (button taps, client errors) back to the agent — the self-correction loop is officially wired.
- IMPLICATION for GS: we COULD adopt Google's renderer as the Compose backbone (custom catalog = our glass components + widget composites) instead of a parallel parser/renderer. Decision point for the design phase — build-on vs build-parallel. Build-on buys maintenance + protocol credibility; parallel buys line-DSL token efficiency + full control.

## SDUI veterans (Airbnb Ghost Platform, Lyft Canvas, DoorDash, Shopify, Netflix — and Spotify's corpse)

- **Ship the fallback renderer FIRST** — before any real component. Unknown component/version without a fallback = crash on old clients (Shopify: "the server ships the fallback").
- **Version negotiation lives on the SERVER:** client sends its max schema version; server downgrades/omits. Never bury compat logic client-side.
- **Semantic component names win** (Lyft: RideOptionCard, PromoBanner — not Row/Column): more types, but they map to product concepts the model and the metrics understand. **Pure-generic primitives KILLED Spotify's HubFramework** — over-abstraction is a documented death cause. → GSUI's mix (generic primitives + semantic composites = our widget kinds) matches the survivors exactly.
- **Sections are screen-independent** (Airbnb): the same PriceBreakdownSection renders on checkout and trip details. Our widget kinds + GSUI cards should behave the same — one definition, any layout slot.
- **Observability before velocity** (Netflix): every unknown-component fallback, every validation failure, every clamp = a metric. Build the counters when the renderer lands, not after.
- **Payload size is a feature:** Lyft chose protobuf for 40–60% smaller payloads; Airbnb paginates UI (deferred responses, above-the-fold first). Our line-DSL + caps serve the same goal inside the chat stream.
- **QA is THE cost** (Lyft, Just Eat): server-driven variations need multi-device test matrices → our three-way fixture corpus + render-proof audits are the right muscle memory; keep them mandatory.
- Adoption pattern: migrate ONE component behind a flag, ramp 1→10→100%, watch crash-free rate + render latency; add versioning before screen #2, observability before screen #3.

## Design deltas this pass forces (recorded, not yet decided — awaiting user designs)

1. Build-on-Compose-A2UI vs parallel GSUI renderer — now a real, researched fork.
2. Accessibility fields belong in the DSL grammar (v1), mirroring A2UI ComponentCommon.
3. The prompt embeds the machine-derived schema (catalog IS the prompt) — keep, deepen.
4. Fallback/isolation renderer is milestone-zero, not an afterthought.
5. Telemetry counters (drops, clamps, unknown components) ship WITH the renderer.
6. Semantic+generic hybrid vocabulary confirmed as the survivor pattern.

---

# PASS 5 — grammar science, transport standard, token architecture, accessibility (2026-11-05)

## Design tokens (W3C DTCG) — how glass scales to two platforms

- Three tiers: **primitive** (`color.blue.500`, `space.4`) → **semantic** (`color.surface.primary = {color.blue.500}`) → **component** (`button.primary.background`). Reference chains (`{…}`) make one edit propagate everywhere; composite types (shadow, gradient, typography, border) are first-class; OKLCH for perceptual uniformity.
- Anti-pattern: raw pixels in semantic tokens — breaks the whole point.
- GSUI rule this forces: **payloads carry SEMANTIC tokens only** (`accent: teal`, `surface: hero`, `hue: 3`) — never raw hex. Each platform's renderer maps semantic → its own glass primitives. The model literally cannot break the design system because it never touches raw values.

## DSL vs JSON for LLMs — the honest math

- Grammar School benchmark: DSL tokens stay CONSTANT as data grows; JSON grows linearly; crossover ≈100 items; grammar overhead ≈2k tokens one-time.
- TOON benchmark (arXiv 2603.03306): the **"prompt tax"** — a novel format's instructional prompt can EXCEED its output savings for small payloads (Qwen3-235B: TOON total 4715 tokens vs plain JSON 2772!); plain JSON still wins one-shot accuracy; constrained decoding can CRATER strong models (Hermes-405B: 92.5% → 35% under grammar constraints); repair loops double token cost when the system prompt is huge; per-case few-shot examples matching the schema reduce structural mistakes.
- YAML line research: fewer punctuation tokens → fewer structure-breaking mistakes.
- GSUI consequences: (1) the GSUI prompt section must be LEAN — the grammar + component table is a per-request tax; (2) judgment layer: GSUI blocks only pay off when the UI is substantial (tiny stat → plain widget fence, not a gsui block); (3) few-shot examples in the prompt must match the schema exactly; (4) do NOT use grammar-constrained decoding — the DSL is designed to be safe in plain generation with a tolerant parser.

## AG-UI — the transport standard (if GS ever exposes GSUI externally)

17 typed events over SSE: RUN_STARTED/FINISHED/ERROR, TEXT_MESSAGE_START/CONTENT/END, TOOL_CALL_START/ARGS/END/RESULT, STATE_SNAPSHOT/STATE_DELTA (RFC 6902 JSON Patch), THINKING, CUSTOM, RAW, ACTIVITY_*. Our internal chat SSE + fenced blocks maps 1:1 onto this vocabulary; adopting the names in our internal docs keeps a future external binding cheap.

## Accessibility — dynamic UI is systematically invisible to screen readers

- ACM study: dynamic visual changes (animations, state feedback) often produce NO accessibility event and NO label → blind users get nothing. Root causes: missing labels on dynamic components, missing live-region announcements.
- Codename One pattern: a portable **semantics tree** parallel to the visual tree (roles, values, ranges, actions, grouping, traversal order, live regions) mapped per platform.
- Flutter: `Semantics(liveRegion:)` auto-announces updates; semantics-tree build cost is a real perf line item.
- GSUI consequences: every primitive gets an automatic semantic contract (role + label defaults per component, live-region on progress/stat updates, traversal order = document order by construction). Accessibility is IN the grammar (optional a11y overrides), not a renderer afterthought.

# PASS 6 — platform performance contracts, layout science, security model (2026-11-05)

## Compose: stability is correctness, not optimization

- `List<T>` is UNSTABLE to the Compose compiler → non-skippable composables → recomposition storms. Production case: 7.2× recompositions per scroll frame, 450 ms frames on flagships, fixed by `@Immutable` + `kotlinx.collections.immutable` `ImmutableList` (recompositions 7.2→1.0, janky frames −60%).
- Compiler metrics (`compose_reports`) catch non-skippable composables at build time; teams run the diff in CI.
- `derivedStateOf` narrows scope; state reads pushed to leaves; `Modifier.Node` over `composed{}` (3–10× faster); R8 full mode.
- GSUI consequences: parsed tree = `@Immutable data class` with `ImmutableList` children; per-line streaming updates keyed by node id (stable keys or state loss); recomposition counter ships in debug from day one.

## SwiftUI: identity + diffing boundaries (Airbnb engineering)

- Diffing: Equatable conformance controls what's compared; `@Equatable` + `@SkipEquatable` (closures excluded); decomposed subviews = diffing boundaries (Airbnb: scroll hitches −15% on Search).
- Identity: ForEach ids from DATA (never indices, never `UUID()`); unstable id = destroy + rebuild (state loss).
- GSUI consequences: `GsuiNode: Equatable`; each primitive view is a small diffable struct taking scalars, not the whole subtree; node id is the identity.

## Layout science — why GSUI must be coordinate-free (validated by research)

- LaySPA (arXiv 2509.16891): LLMs have "spatial cognition deficiency" — even GPT-5 fails alignment, overlap avoidance, hierarchy on canvas layouts; RL with geometric-validity rewards helps but specialized models still win.
- SG-Layout (arXiv 2608.1106): accuracy degrades as object count and relation density grow; graph-structured conditioning helps.
- LayoutLLM-T2I: LLMs are good "architects" for STRUCTURE (coarse boxes) — fine geometry belongs to the renderer.
- GSUI design validated: the model emits STRUCTURE (stack/grid/flow/grouping); the renderer owns ALL geometry. No x/y/width/height in the grammar — ever. Absolute positioning is the one door we keep closed.

## Security — the threat model for model-authored UI

- Indirect prompt injection is in the wild (Unit42): hidden zero-size/opacity-0 text, attribute embedding, runtime assembly; top intent = corrupting output.
- EchoLeak (AAAI): zero-click exfiltration via generated markdown + CSP-allowlist abuse.
- Defense stack that survived review: prompt partitioning, provenance gating, **output policy gates (format allowlists!)**, URL allowlists, strict CSP, signed media proxies, HITL for external content, **action allowlisting with dangerous flags**.
- GSUI inherits a strong position (native text, no webview, no eval) but must still ship: action allowlist v1 = prompt|copy ONLY; URL/scheme validation on image/link/video (http(s) + app-known hosts; block data:/javascript:); string clipping (already in caps); the widget composite routes through the validated pipeline = output policy gate; telemetry counters on drops/clamps = detection.

## Cumulative research index (6 passes)

1. Layers + SotA landscape · 2. Streaming mechanics + judgment matrix · 3. A2UI/OpenUI Lang/SwiftUI SDUI · 4. A2UI spec internals + Artifacts + official Compose renderer + SDUI veterans · 5. Design tokens + DSL math + AG-UI + a11y · 6. Compose/SwiftUI performance contracts + layout science + security.

---

# PASS 7 — the glass we render INTO + the reveal + the evidence (2026-11-05)

## Material 3 Expressive (Android target API surface)

- **MotionScheme spring physics replaces easing**: 6 tokens — `fastEffectsSpec` (~150ms, stiff, minimal overshoot: toggles/checkboxes), `defaultEffectsSpec` (~300ms: button presses), `slowEffectsSpec` (~500ms, gentle bounce: emphasis), and the spatial trio for position/size (navigation, sheets, hero transitions). Springs = (dampingRatio, stiffness); never `tween()` except color/opacity.
- `MaterialExpressiveTheme` (NOT MaterialTheme), `expressiveLightColorScheme()/expressiveDarkColorScheme()`, everything behind `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`. Dynamic color needs API 31+ — expressive scheme is the fallback.
- Discipline from Google's own mistake list: **emphasized typography reserved for hero/CTA moments** (overuse defeats it); paired color roles only (container + onContainer); stable keys on lazy items; `graphicsLayer` not `scale()`/`offset()`; **reduced-motion → snap() fallback** (LocalReduceMotion).
- GSUI renderer contract: every block reveal/transition goes through MotionScheme tokens, never ad-hoc durations; emphasis budget enforced in the judgment layer (≤1 emphasized moment per block).

## iOS Liquid Glass (iOS 26+ API surface)

- `glassEffect(.regular, in: shape)` — regular variant default, capsule default shape; `.clear` ONLY over media with a dimming layer (~35% dark); `.identity` opt-out. Apply LAST in modifier chain. `GlassEffectContainer(spacing:)` for blending/morphing of nearby glass — **glass cannot sample other glass**, ungrouped glass stacks incorrectly. `glassEffectID` + `@Namespace` for morph transitions; `.buttonStyle(.glass/.glassProminent)`; `.interactive()` for touch feedback; `.tint()` semantic-only; `ConcentricRectangle` for nested corners; `.scrollEdgeEffectStyle` keeps controls legible over scrolling content.
- UIKit parity exists (UIGlassEffect, UIGlassContainerEffect, UIButton.Configuration.glass()).
- HIG: **glass lives on the chrome/control layer, NOT the content layer** — legibility is the #1 criticism of iOS 26. Test under Reduce Transparency + Increase Contrast.
- GSUI consequence: block bodies are content (solid/tonal surfaces); glass is reserved for block chrome, action chips, and floating controls. This matches our existing GsGlassControls direction and keeps 4.5:1 contrast enforceable.

## Streaming reveal — the patterns for structured output

- Do NOT token-stream structured output — partial JSON/DSL looks broken. Pattern: **structured skeleton** (empty shell of the expected block, shimmering) at fence-open, **swap content on first VALID parse**, not per token. A2UI 0.9 calls this "resilient streaming" (incremental parse-and-heal); Vercel speculative RSC renders the tool skeleton the moment the tool NAME parses, then optimistically hydrates as args stream.
- Skeleton must match expected shape (<100ms appearance, subtle shimmer); phase-based status lines for tool gaps; stop button preserves partial; buffered re-parse of accumulated source each chunk (same trick our fence parser already uses); a11y: mark skeleton aria-busy/hidden, announce on swap (→ liveRegion from pass 5).
- GSUI: our line grammar gives a natural unit — skeleton at fence-open (`gsui` + first component id), progressive per-line commit as each line parses, block-complete polish pass. Never re-flow already-committed lines (layout stability).

## The evidence that this whole category is right (and where it wins/loses)

- ACL 2026 Findings (arXiv 2508.19227), 428 human raters, 100 UIX prompts: **GenUI 84% / tie 4% / chat 12%** vs Claude 3.7 chat UI; aesthetics 89%, satisfaction 87%, usability +151%; biggest domain win: data analysis & visualization **93.8%**; weakest: advanced AI/ML text-heavy (50%) — GSUI judgment layer must keep text-dominant answers as prose. 380 real user queries: 50.8% win / 41.1% loss. **86.5% rated the SAME content as more credible/professional when structured** — layout itself manufactures trust. Ablation: static rubric −17%, no-iteration −14% → rubric quality > iteration count.
- MLLM-as-UI-Judge (arXiv 2510.08783): multimodal LLMs predict human UI ratings within ±1 Likert point 72–77% (Claude best); reliable for coarse gates, not fine calls; models overestimate Ease-of-Use, underestimate Aesthetics/Comfort. → Our M4 self-judgment can use rubric-scored passes but human fixture review (M5) stays the arbiter.

# PASS 8 — the operational last mile (2026-11-05)

## RTL / i18n / bidirectional text

- Rule: **logical start/end everywhere, never left/right** — layout mirrors for Arabic/Hebrew; Compose + SwiftUI do most of it automatically IF no directional hardcoding. Directional icons (back/forward) mirror; content glyphs (clocks, checkmarks, logos) never do.
- Bidi isolation: LTR identifiers/URLs/numbers inside RTL text need isolation (Unicode FSI/PDI, platform-native spans) or punctuation "jumps" sides. Keep code/GSUI blocks directionally stable (LTR) while prose follows locale direction.
- Test with pseudolocales: Android `ar-XB`, Xcode RTL pseudolanguage. Plan for 30–50% text expansion; charts/media don't auto-flip (axes stay LTR unless semantics demand otherwise).
- GSUI: grammar already stores text as quoted strings → platform text stacks handle bidi; renderer-level rule: GSUI blocks lay out leading/trailing, never left/right; numbers/dates formatted by platform locale, never baked into strings by the model.

## QA matrix reality (M5 planning)

- 3-tier model: emulators in CI (every commit) → cloud device farm for release-candidate matrix → 2–3 local reference devices (current Pixel, current + older iPhone, one mid-range Android) for exploratory. Matrix built from analytics, reviewed quarterly, drop configs <2% share. Fixed 8-device matrix covering 90% of users is a proven pattern.
- Visual regression: semantic/ perceptual diffing tools (Applitools/Percy class) beat pixel diffing across OEM skins; cost envelope $500–5k/mo for cloud farms.
- For GSUI M5: fixture-driven three-way audit (parser TS ↔ Compose ↔ SwiftUI over the same corpus) is the cheap deterministic core; device matrix only for the chrome/glass layers.

## MCP Apps (SEP-1865) — the external-exposure path, studied, not adopted

- Final Jan 26 2026: first official MCP extension (`io.modelcontextprotocol/ui`), co-authored Anthropic + OpenAI. Server publishes a `ui://` resource (HTML, `text/html;profile=mcp-app`), tool links via `_meta.ui.resourceUri`; host renders in a MANDATORY sandboxed iframe; CSP built from server-declared domains, default = deny all network; JSON-RPC-over-postMessage bridge; `structuredContent` kept out of model context; tool ≠ UI; no persistence beyond session.
- Takeaway: it's HTML-in-an-iframe because it must be cross-platform web — exactly the design we are NOT doing (native, no eval). But two of its contract ideas are ours to steal: (1) **tool result ≠ UI payload** — the model-facing text stays useful when the UI can't render (our honesty-labelling fallback); (2) **structured data channel kept out of the model's context** to save tokens. If GS ever exposes MCP tools externally, a GSUI block can be packaged as a ui:// resource that renders our DSL — the DSL is the durable artifact, the iframe would just be one more renderer.

## Offline & caching — chat's natural advantage

- SDUI literature: cache last-known-good UI config (SQLite keyed by screen), stale-while-revalidate, separate cacheable structure from volatile data; unknown-component policy = skip silently (non-critical) / placeholder / update-required.
- GSUI's form is its own offline story: **the DSL source is the cache**. Message records already persist the raw fenced block; re-parse + re-render is deterministic offline with zero server. No component-URL hydration, no remote templates — the entire failure mode class of webview SDUI offline doesn't exist here. Renderer version drift is the only real risk → fixture corpus + version field in the fence header covers it.

## Cumulative research index (8 passes — saturation)

1. Layers + SotA · 2. Streaming mechanics + judgment · 3. A2UI/OpenUI/SwiftUI SDUI · 4. A2UI internals + Artifacts + official renderer + SDUI veterans · 5. Tokens + DSL math + AG-UI + a11y · 6. Perf contracts + layout science + security · 7. M3 Expressive + Liquid Glass + reveal patterns + evidence base · 8. RTL/i18n + QA matrix + MCP Apps + offline.

---

# PASS 9 — Bangla script, list mechanics, and the science of sameness (2026-11-05)

## Bangla (বাংলা) rendering — the market's script is a hard case, plan for it

- Bangla is a complex script: **hundreds of conjunct ligatures** fuse consonants into single glyphs → character count barely predicts rendered width; boxes measured on Latin cap height **clip tall conjuncts**; the hasanta (্) is zero-width and invisible but changes surrounding letter shapes — dropping it silently corrupts words. Broken rendering ("letters drawn as separate pieces") = the chosen font lacks fused conjunct forms; Noto Sans Bengali carries them and is the reference face.
- Modern Android/iOS system shapers (HarfBuzz/ICU lineage) handle Indic reordering natively — the old "Android can't render Bangla" era is gone; the remaining failure modes are FONT CHOICE, line-height/clipping (conjunct ascenders + matra line), and width estimation.
- bn localization runs 10–20% longer than English → fixed-height text containers are a bug class; GSUI caps already allow multi-line, but renderer must size blocks from measured text, never estimated character counts.
- GSUI renderer rules: bundle/declare a conjunct-complete Bengali face; extra line-height headroom on Bangla text styles; test fixtures MUST include Bangla strings (labels, stat values, timeline entries); numerals — platform-locale digits (০১২৩ vs 0123) formatted by the renderer, never pre-formatted by the model.

## Chat-list mechanics for heavy blocks (the scroll path IS the product)

- Compose: `Modifier.animateItem()` (1.7+) animates insert/remove/placement — requires **stable keys** (`items(key = { it.id })`), customizable to M3 springs (fadeInSpec/fadeOutSpec/placementSpec). Chat pattern: LazyColumn + rememberLazyListState + animateScrollToItem on new message.
- SwiftUI — measured, not folklore (STRV lab, memory-intensive rows): **List (UITableView-backed) vs LazyVStack**: scroll time 5.53s vs 52.3s; hangs 4.6 vs 78; memory on scroll-back: List reverts (118MB) while LazyVStack keeps everything (152MB). At scale, LazyVStack = 1.2GB / sub-20fps at 100k rows; List holds 60fps at 45MB. LazyVStack does NOT recycle; List does.
- Root causes of drop-frames (both platforms identical in kind): identity instability (unstable ForEach ids → full re-render on any change) and fat view bodies (row observing the whole model). Fix pattern: stable ids + rows take primitives.
- GSUI decisions locked by this: iOS chat list = List-backed (or UICollectionView-composed) scrolling, not LazyVStack-in-ScrollView, for unbounded histories; GSUI block views take scalars/small view models, never the whole message object; block = one diffing boundary.

## Anti-monotony is now an engineering requirement, not a taste (three independent studies)

- CHI-track study (ACM 10.1145/3772318.3790758): AI-assisted UIs measured **more visually similar to each other** (LPIPS layout similarity + color convergence) than human-designed ones — generative assistance demonstrably narrows design space.
- **Design Theater** benchmark (arXiv 2607.22928): generative-UI tools fail to implement **>25% of their own stated design reasoning** (34% for functional requirements); recognize only ~54% of implicit UX principles; same prompt → narrow layout/visual convergence across ALL tools (color varies most — meaning COLOR is where models naturally vary, LAYOUT is where they collapse).
- **Design Creativity Bench** (arXiv 2610.11539): every frontier model produces designs more similar to other models' outputs than to human references; same model, two different domains → still similar (originality ≠ creative range; human distinctiveness on unrelated tasks 0.811 vs models ≤0.63).
- Consequence for GSUI M4: prompting "be creative" is proven insufficient. The judgment layer needs a **structural rotation policy**: track the archetypes used in recent blocks (per conversation), bias the next block toward an unused composition; a small archetype inventory (hero-stat / split / timeline-flow / grid-cards / flow-diagram / dense-table...) is the control surface. This is the mechanism behind the user's anti-monotony rule.

# PASS 10 — charts on device, draw-phase discipline, haptic language, diversity knobs (2026-11-05)

## Chart rendering budgets (both platforms)

- Swift Charts: smooth to 5–10k simple marks on modern iPhones, then lag. Playbook: **LTTB downsampling to ~500 visible points** (visually faithful), RectangleMark over BarMark (skips layout pass), `chartScrollableAxes` + `chartXVisibleDomain` for windowing. Swift Charts for ~95% of chart types — it is accessible BY DEFAULT (VoiceOver reads values); custom Canvas/Path only for radar/sankey/force-graph/per-stroke animation — and then YOU own a11y, gestures, animation.
- SwiftUI Canvas: single rendering context — the production pattern for candlestick/real-time charts (hundreds of points, no per-point view hierarchy).
- Compose: official perf skill — `drawWithCache` caches Path/Brush/Shader per size+state (chart grid = cached static layer, data = redrawn); animated values read inside `graphicsLayer{}`/`offset{}` lambdas (Draw-phase, no recomposition); **DrawScope runs 60–120×/s → zero allocations inside** (no `Path()` in draw blocks); `Path.rewind()` not `reset()` in hot loops.
- These confirm and extend our existing 200-pt/16-series caps: the caps are right, and the LTTB option exists if we ever need density.

## Haptics — a small token language, same discipline as color

- Vocabulary: notification(success/error/warning) for outcomes, **selection tick** for value changes, light impact for presses — weight ∝ stakes. One-sentence rule: "if you can't say what a haptic confirms, it must not fire." Never the only signal; respect system settings/silent switch; `prepare()` generators ahead of the tap; reuse generator instances; nothing during scroll loops.
- Treat as design-system tokens (success/error/warning/selection/long-press) with platform implementations (Core Haptics/AHAP + UIFeedbackGenerator; VibratorManager API 31+), synced with visual feedback in the same frame.
- GSUI: action chips get selection/impact haptics; block-complete could get a single subtle success tick (budgeted like emphasis — restraint is the design).

## Diversity knobs vs structural sameness (honest split)

- Sampling knobs that help lexical variety on long output: temperature 0.7–1.3 for creative ranges (0.5–0.7 for reasoning-tuned models to avoid repetition loops), top_p 0.9–1.0, frequency_penalty 0.2–0.5 / presence_penalty 0.3–0.6 (the classic "Revenue increased in Q1/Q2/Q3" case was fixed by frequency_penalty 0.3 at temp 0.4 — accuracy AND variety coexist).
- But per Design Creativity Bench, **structural/layout sameness survives high-temperature settings** — sampling knobs cannot be the anti-monotony mechanism. Mechanism = judgment-layer rotation + explicit "previous archetypes" context in the GSUI prompt + archetype examples in few-shots that demonstrate DIFFERENT compositions.
- Prompt-side aid (research-backed): asking for varied formats/perspectives in the prompt adds diversity without raising temperature — our grammar examples should showcase compositional variety, not one house style.

## Cumulative research index (10 passes)

1. Layers + SotA · 2. Streaming + judgment · 3. A2UI/OpenUI/SwiftUI SDUI · 4. A2UI internals + Artifacts + official renderer + veterans · 5. Tokens + DSL math + AG-UI + a11y · 6. Perf contracts + layout science + security · 7. M3E + Liquid Glass + reveal + evidence · 8. RTL + QA + MCP Apps + offline · 9. Bangla + list mechanics + sameness science · 10. Chart budgets + draw discipline + haptics + diversity knobs.
