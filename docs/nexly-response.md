# Nexly response slice

> **Native status (2026-10-22): the reference is now implemented in the
> Android and iOS chat shells, not just this web proof.** The plan block,
> Thinking card, glass composition and the shared icon grammar are wired into
> the native transcript. Read `docs/NEXLY_GOAL.md` for the durable brief and
> `docs/reference/nexly/chat-plan-reference.png` for the target image. The
> sections below describe the original web proof and remain accurate as the
> contract reference.

A web/React proof of the reference assistant response: glass turns, a
Thinking/Reasoning/Effort card that survives the first token, and a validated
timetable with a glass glyph on every row.

Open `http://localhost:3000/nexly` (or the dev port you started) to see it.

## What is real here

- **5,184 glass glyphs.** The build script reads Tabler Icons outline nodes
  (MIT) and emits one SVG sprite plus a name manifest. The renderer uses one
  `<use>` per glyph, not 5,000 React components.
- **A model-facing icon grammar.** A reply can use `:sunrise:` inline or
  `"icon": "sunrise"` in a plan row. Names are kebab-case Tabler ids, with
  friendly aliases such as `study` -> `book-2`, `workout` -> `run`,
  `lunch` -> `tools-kitchen-2`.
- **Emoji enforcement at the renderer.** The prompt bans emoji; the renderer
  also maps common emoji to glass glyphs and drops any remaining pictograph.
  This is a safety net, not a suggestion.
- **A validated plan block.** `parseNexlyPlan()` checks the payload, caps rows,
  validates every icon, normalises the closing line and falls back to a
  sparkle for an unknown name. The card never guesses at markdown alignment.
- **Thinking after the preamble.** `NexlyThinkingCard` is a sibling of the
  preamble inside the assistant card. It is not gated on `content.isEmpty`, so
  a host can keep it mounted while the answer and plan are already visible.
- **A wire prompt.** `NEXLY_PLAN_PROMPT` in `src/lib/nexly/plan.ts` is the
  instruction to give the model. It is ready to be inserted into a system
  prompt; it is not inserted into the live app in this pass.

The `/nexly` screen renders a static demo payload, not a live model call. The
header, user bubble and composer around it are demo context. The reusable
pieces are the assistant turn, thinking card, plan card, rich text and glass
icon.

The demo inlines the full 1.9 MB sprite so the library is instant. In
production, serve the sprite once as a cached asset or subset it to the glyphs
used by the current reply. A host must render the sprite once (see
`src/app/nexly/page.tsx`) before `<GlassIcon>` can resolve `<use>` references.

## Wire contract

````text
Got it! I'll create a balanced plan for your tomorrow.

Let me think for a moment...

```plan
{
  "title": "Here's your personalized plan for tomorrow:",
  "rows": [
    { "time": "8:00 – 8:30 AM", "icon": "sunrise", "text": "Morning routine + light exercise" },
    { "time": "9:00 – 11:00 AM", "icon": "book-2", "text": "Classes" }
  ],
  "closing": "You got this, Arafat!",
  "closingIcon": "flame",
  "timestamp": "9:45 PM"
}
```
````

Inline: `:sparkles:`, `:moon:`, `:flame:`. Unknown icon names fall back to
`sparkles`; unknown shortcode text stays literal.

## Files

- `src/app/nexly/page.tsx` and `layout.tsx`: demo route, server-injects the
  sprite so every glyph resolves on first paint.
- `src/app/nexly/nexly.css`: scoped glass, phone, backdrop and library styles.
- `src/components/nexly/GlassIcon.tsx`: the single glyph primitive.
- `src/components/nexly/NexlyThinkingCard.tsx`: three-stage card.
- `src/components/nexly/NexlyPlanCard.tsx`: the timetable.
- `src/components/nexly/NexlyAssistantTurn.tsx`: orb + glass card composition.
- `src/components/nexly/NexlyRichText.tsx`: inline `:icon:` and emoji handling.
- `src/lib/nexly/icons.ts`: vocabulary, aliases, tones, emoji map.
- `src/lib/nexly/plan.ts`: schema, parser, prompt.
- `src/lib/nexly/demo.ts`: the reference response.
- `public/icons/gs-sprite.svg`: 5,184 symbols.
- `public/icons/TABLER-LICENSE.txt`: upstream MIT license.

## Commands

```bash
bun run icons:nexly   # regenerate the sprite and name manifest
bun run test:nexly    # validate vocab, parse the demo wire, check emoji handling
```

## Native rollout

All four steps are now implemented:

1. `plan` is in the shared catalogue (`src/lib/widgets/catalog.ts`), validator
   (`validate.ts`), prompt (`NEXLY_PLAN_PROMPT` inside `WIDGET_PROMPT`) and web
   stream registry (`registry.ts`, `stream.ts`).
2. The Thinking card is driven by the real turn on both platforms: the
   backend `mode` event (effective mode + effort), the reasoning stream and the
   answer phase. Plan turns keep the reference copy; other turns use adaptive
   copy, and a flash turn with no reasoning does not show the card. It is no
   longer gated on `content.isEmpty`.
3. Android and iOS both parse validated `plan` payloads into first-class blocks
   (`Block.PlanBlock` / `Block.plan`) and render a glass timetable with circular
   glyph tiles, time over activity, a closing line and a timestamp.
4. The native clients bundle the same Tabler outline font and codepoint map;
   the web proof uses the matching SVG sprite.
5. The full reference screen is rendered in CI from production pieces on both
   platforms (shared header, real user message bubble, assistant blocks and
   shared composer). The 411×914 renders show all nine plan rows, closing,
   timestamp and composer without scrolling.

Verification is no longer static-only: the Android and iOS CI gates compile the
apps, run the native test suites and upload the plan, adaptive-thinking and
full-screen renders. See `docs/reference/nexly/ci/`.

## Icon provenance

The glass tile, palette, aliases, emoji normaliser, plan grammar and renderer
are custom. The outline glyph geometry is Tabler Icons, MIT licensed. The full
license text is in `public/icons/TABLER-LICENSE.txt`. If the brief requires
bespoke hand-drawn artwork for all 2,000+ glyphs, that is a separate
illustration commission; this implementation gives the model a large, coherent,
licensed vocabulary now.
