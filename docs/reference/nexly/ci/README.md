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
screen. A flash assistant turn contains one prose glass card and one separate
plan glass card with its own orb; a thinking-mode turn additionally mounts the
Thinking panel, which is why the adaptive-thinking image exists separately.

## Sheet match

The visual pass corrected the things that made the first native version read as
a generic chat screen rather than the sheet:

- the user turn is capped at **205pt on compact phones** (about half the
  device width) instead of filling the row; the avatar keeps its own lane and
  the time/tick sits under the bubble;
- assistant cards are capped at **286pt (prose) / 290pt (plan)** on phones,
  about 70% of the 393pt width, leaving the pearl field visible at the right;
- the plan title is a single line at the sheet's lighter weight;
- glyph chips use a **pale tone wash with the saturated colour in the glyph**
  (not saturated chips with white ink), at a **24pt** chip / 12pt glyph so they
  read as annotations rather than giant app icons;
- plan rows are **interactive checkboxes**: each row shows a trailing check
  ring and tapping it fills the ring, strikes the row and fires haptic
  feedback (the row is exposed as a checkbox to assistive tech, not a dead
  list). The state is persisted per conversation and rides the next turn as
  non-visible context, so the model can adapt instead of forgetting it;
- the Android composer now uses the same compact proportions as iOS: a 38pt
  sparkle lane inside a 46pt capsule, 14pt field and 36pt peach send control,
  instead of the oversized 48pt leading control;
- text uses the sheet's **soft neutral grey ink**, not the app's near-black
  brown: the user/preamble stay around `#4A4750`, plan titles around
  `#555860`, rows around `#5E6068`, and metadata around `#8C8E96`;
- cards use a warm milky veil and hairline rim (`regularMaterial` plus a
  warm tint on iOS, a tinted translucent draw on Android) with almost no
  shadow, so the iridescent bubbles read through them;
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

## Flash mode

Flash is the product default for these turns. It does **not** request or display
a Thinking/Reasoning sequence: the flash full-screen plan render has no stage
card, and the fresh coding/life renders have none either. The Thinking panel is
still available as a *component* for a real thinking-mode turn (the
`*thinking*` image), but it is not painted onto flash answers.

The routing decision is separate from the answer and is a short, non-thinking
classification call (`planSearch`). It now runs for every turn, not only
WEB/deep routes, and can return:

- **answer** — no search, no clarification;
- **clarify** — one concise question with 2–5 quick-choice options (the native
  clarify chips send the chosen label as the next user turn);
- **search** — run the backend retrieval first, then answer in the same turn.

This is what lets a flash turn ask when the request is genuinely ambiguous or
fetch current information before writing. It is still a backend planner, not a
model-callable browser tool; the model never silently pretends it searched.

## Fresh coding/life questions

`scripts/live-random-questions.ts` was run once through the repository's
OpenRouter secret (flash: reasoning disabled). Four questions were captured:

1. Python list comprehension returning `None` with `.append()`.
2. React `useEffect` fetching twice in development.
3. Hitting snooze and feeling behind all morning.
4. Asking a friend to stop interrupting.

The native harness renders the first and third on **both** Android and iOS at
393pt wide by 1400pt tall, so the whole answer is visible rather than cut at a
device fold:

- `nexly-chat-random-coding-393x1400.png` / `nexly-chat-ios-random-coding-393x1400.png`
- `nexly-chat-random-life-393x1400.png` / `nexly-chat-ios-random-life-393x1400.png`

The life answer contains a real model-authored plan card; the coding answer
contains real model-authored fenced code. Raw/final captures are committed as
`docs/reference/nexly/random-*.md`. The fourth answer ends by asking the user a
follow-up question — the model does ask when a conversation needs a next step.

## Plan progress → model context

The check rings are no longer a local-only decoration. `NexlyPlanProgressStore`
(Android SharedPreferences / iOS UserDefaults) persists completion and the
helpful/not-helpful rating per conversation, fingerprinted to the plan so stale
checks cannot restore onto a different timetable.

On the next message the client sends a compact, non-visible `turnContext`:

```
Plan: Here's your balanced schedule for tomorrow:
- Completed (2/9): 6:30 – 7:00 AM — Wake up + light stretch; ...
- Pending (7): ...
- User feedback: the plan was marked helpful.
```

`prepareTurn` sanitises and caps it (2,000 chars), appends it to the system
prompt under `USER-STATED PLAN PROGRESS`, and never stores it as a user message.
The model therefore sees real progress and an explicit preference signal; it is
told not to repeat the block or claim unmarked completions.

## Cross-device progress

Checklist progress is no longer only local. The server stores it per
conversation (`PlanProgress` in Prisma; SQLite locally), exposed at

```
GET/PUT /api/v1/conversations/{id}/plan-progress
```

Both clients hydrate from that endpoint and PUT on each toggle/rating. The
local store remains the offline fallback. `prepareTurn` also reads the stored
row when the client sends no `turnContext`, so a second device gets the same
progress into the model.

The server-side path was exercised in run `39966154390`: the PUT/GET succeeded
and the turn log printed

```
PLAN-PROGRESS conv=… Plan: demo morning | Completed (2/2): Drink water; Open curtains | Pending (0): none | User feedback: the plan was marked helpful.
```

That run’s model call then hit the free-key quota and returned a 502, so the
final model sentence was not captured on the server-persisted variant; the
earlier client-context run (`39961095648`) did capture the correct sentence
(“You completed "Drink water" and "Open curtains"”). The context construction
is deterministic and logged; the upstream exhaustion is external.

## Activity stages

The third card row is no longer a fixed “Effort” claim. It is driven by the
turn:

- **Search** with the real source count when the backend actually ran research;
- **Answer** (“Composing your plan…” while running, “Plan ready” when settled)
  otherwise;
- **Reasoning** shows a real sentence only when the provider streamed a trace;
  otherwise it says “No reasoning trace exposed”.

The card is shown only when a real thinking/reasoning event or search
sources exist. A flash plan turn has no stage card; it no longer invents a
reasoning trace or an effort level the model never received. The
free model profile (`supportsReasoningEffort: false`) sends no effort value;
the old static “High effort” fixture has been removed from the card and from
the claims.

Web search in this path is still a **backend capability gate** (explicit/fresh
queries trigger research), not a tool the model calls on its own. When it runs,
the sources are counted and surfaced in the Search row.

## End-to-end capability probe

The branch was stood up locally in CI against SQLite and the real production
route (`scripts/live-backend-capabilities.ts`), using the repository's
OpenRouter key in flash mode. Run `39960319355` (commit `a00ffee4`) produced:

- **Clarify:** “Can you fix my code? It is not working.” → the planner asked
  *“Which programming language and framework are you using, and can you share
  the code or error message?”* with five quick choices (JavaScript, Python,
  Java, C#, Other). Sending “JavaScript (Node.js)” produced the answer turn.
- **Search:** “What is the latest stable version of the Bun runtime right
  now?” → the planner ran retrieval and returned **8 sources**. Those sources
  did not contain the Bun release data, and the model explicitly refused to
  invent the version, pointing to `github.com/oven-sh/bun/releases` instead.
  That is the honest-failure behavior we want, not a hallucinated version.
- **Normal coding answer:** the Python `.append()` question answered without
  an unnecessary search or clarify.
- The turn log shows `mode=flash effective=flash`, the planner running, and
  `searchExecuted=true` for the fresh question.

A second run (`39961095648`, commit `d58ddd1e`) proved the checklist context
end-to-end: a request carrying `turnContext` with “Completed: Drink water;
Open curtains” was answered with **“You completed "Drink water" and "Open
curtains" in your demo morning plan.”** That run’s clarify/search leg did not
repeat because the free upstream failed those planner calls; the two runs
together cover every capability, and the script is kept as a probe rather than
a green gate so an upstream flake is reported, not hidden.

Full payloads: [`backend-capabilities.json`](../backend-capabilities.json)
(clarify + search) and
[`backend-capabilities-progress.json`](../backend-capabilities-progress.json)
(progress context).

## Verified runs

Both workflows completed green on commit `ed9656a9`:

- Android: https://github.com/GrapseeAgency/the-gs-ai-app/actions/runs/39938777811
  (run `39968123204`, 281 unit tests)
- iOS: https://github.com/GrapseeAgency/the-gs-ai-app/actions/runs/39938781413
  (run `39968127637`)

`reference-vs-android-393x852.png` and `reference-vs-ios-393x852.png` place the
sheet and the final native render at the same scale for a direct comparison.
The native render is flash, so the sheet's Thinking/Reasoning panel is absent
by design; the `*thinking*` image is the separate thinking-mode component.

Android runs the full unit suite (281 tests, including `NexlyPlanTest` and the
new progress-store tests); the
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
