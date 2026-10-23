# Nexly chat response — native CI evidence

These PNGs are real native renders from the verification branch
`nexly/chat-reference-verify`, not mockups and not web screenshots. The data in
them is a **live model response**, not a hand-authored demo fixture.

## The live model input/output

Both content sets were captured with `scripts/live-nexly-model.ts`, which sends
the production `SYSTEM_PROMPT + WIDGET_PROMPT` to OpenRouter
(`nvidia/nemotron-3-ultra-550b-a55b:free`) with a real key, then runs the
server-side `ensureWidgets` / `parseNexlyReply` path. No key is written to the
repository.

| Render | Real question | Captured model output |
| --- | --- | --- |
| Plan + whole screen | “I work a 9–5 shift tomorrow and need to fit in a gym session, meal prep, and two hours of study for an exam. Can you plan my day?” | [`live-plan-real2-final.md`](../live-plan-real2-final.md) (raw: [`live-plan-real2-raw.md`](../live-plan-real2-raw.md)) |
| Prose + adaptive status card | “I keep procrastinating on my thesis. Why does that happen and what is one realistic change I can make this week?” | [`live-prose-real1-final.md`](../live-prose-real1-final.md) (raw: [`live-prose-real1-raw.md`](../live-prose-real1-raw.md)) |

The plan answer contains **9 timed rows**, includes the requested work, gym,
meal prep and exam study, uses only valid Tabler icon names (no emoji), and the
compiler path is `model` with zero validation errors. Its title, closing and
timestamp are the model's own, which is why they differ from the sample sheet.
The question is deliberately **not** the reference's sample question, so the
render also proves the card adapts to fresh content.

## What is mounted

- **Android:** `NexlyChatScreenshotTest` (Roborazzi + Robolectric native
  graphics) mounts the real Compose tree: `NexlyChatHeader`, `UserMessage`,
  `BlocksContent` with `glassAssistant = true`, `ReferenceThinkingCard`,
  `NexlyPlanCard`, `NexlyAssistantOrb`, `ComposerRow` and
  `nexlyPearlBackdrop`.
- **iOS:** `NexlyChatRenderTests` hosts the real
  `NexlyBrandHeader` / `MessageBubble` / `NexlyAssistantBlocksView` /
  `NexlyComposerBar` / `NexlyChatBackdrop` in a keyed `UIWindow` and captures
  the composited layer tree with `drawHierarchy(afterScreenUpdates:)`, so
  `UIVisualEffectView` glass is included.

The full-screen harness uses the same internal production views as the chat
screen. The assistant turn contains one prose glass card (preamble + Thinking
card) and one separate plan glass card with its own orb, exactly as the
reference does.

## Sheet match

The visual pass corrected the things that made the first native version read as
a generic chat screen rather than the sheet:

- the user turn is capped at **190pt on compact phones** (about half the
  device width) instead of filling the row; the avatar keeps its own lane and
  the time/tick sits under the bubble;
- assistant cards are capped at **286pt (prose) / 290pt (plan)** on phones,
  about 70% of the 393pt width, leaving the pearl field visible at the right;
- the plan title is a single line at the sheet's lighter weight;
- glyph chips use a **pale tone wash with the saturated colour in the glyph**
  (not saturated chips with white ink);
- text uses the sheet's **soft neutral grey ink**, not the app's near-black
  brown: the user/preamble stay around `#4A4750`, plan titles around
  `#555860`, rows around `#5E6068`, and metadata around `#8C8E96`;
- cards use a thinner white veil and hairline rim (`thinMaterial` on iOS,
  a translucent white draw on Android) with almost no shadow, so the
  iridescent bubbles read through them;
- the backdrop has defined pearl spheres with white rims, refraction bands and
  specular highlights, plus the lotus in the lower-left.

## Size matrix

Every row is rendered from the same production components; there is no
width-specific mock.

| Target | Plan screen | Prose screen |
| --- | --- | --- |
| 320×568 | ✓ | — |
| 360×640 | ✓ | — |
| 375×667 | ✓ | — |
| 393×852 | ✓ | ✓ |
| 411×914 | ✓ | ✓ |
| 430×932 | ✓ | — |
| 768×1024 (tablet) | ✓ | — |
| 1024×768 (landscape) | ✓ | — |

- The conversation column is capped at **640pt** and centered, so tablet and
  landscape renders do not stretch the phone composition.
- The brand header, user bubble, assistant cards, plan rows and composer all
  reflow to the available width; nothing is clipped horizontally at 320pt.
- The composer stays on screen at every target, including landscape. On short
  devices the transcript scrolls; that is a chat surface, not a clipped layout.
- At 393×852 and 411×914 the complete plan (title, nine rows, closing and
  timestamp) is visible above the composer.

## Adaptive Thinking / Reasoning / Effort

`nexly-chat-*thinking-393x852.png` is a deterministic component state render of
the real prose answer with the Reasoning row active. It proves the card is not
fixed reference copy:

- “Understanding your request…”
- “Working through the best approach…” with the active Reasoning halo
- “High effort · composing the answer…”

In production the card is driven by the backend `mode` event
(`effectiveMode`, `requested`, `effort`), the reasoning stream and whether the
answer has started. Plan turns keep the reference copy; every other turn uses
task-neutral copy and the real effort. A flash turn with no reasoning does not
show the card. The free model used for the capture returns a final answer
without a chain-of-thought trace, so the live screenshots show the settled
`done` state; the adaptive state render is labeled as a component state, not
as a fabricated model transcript.

## Verified runs

Both workflows completed green on commit `33c61a17`:

- Android: https://github.com/GrapseeAgency/the-gs-ai-app/actions/runs/39865438005
  (run `39865438005`)
- iOS: https://github.com/GrapseeAgency/the-gs-ai-app/actions/runs/39865441786
  (run `39865441786`)

Android runs the full unit suite (267 tests, including `NexlyPlanTest`); the
screenshot tests are gated behind `-PgsShots=true` and are excluded from the
ordinary suite, exactly like `HomeScreenshotTest`. The workflows also assert
that every expected PNG exists before uploading.

Artifacts on the runs: `nexly-chat-render-android` and
`nexly-chat-render-ios` (retained for 30 days). The stable copies of the full
matrix live in `/home/arafat/nexly-evidence/`.

## Honest limits

- The mock's photographic pearl/flower backdrop is approximated with native
  gradients, glass orbs and the app's lotus asset. The reference image is not
  shipped as an asset.
- The system status bar is OS chrome, not part of the app's view hierarchy, so
  it is absent from these app-only captures. A device shows it normally.
- The live model's wording differs from the sheet by design. The structure,
  density, tones, icon treatment, glass composition and rows match.
