# Nexly chat response — CI pixel evidence

These PNGs are real native renders from the verification branch
`nexly/chat-reference-verify`, not mockups and not web screenshots.

- **Android:** `NexlyChatScreenshotTest` (Roborazzi + Robolectric native
  graphics) mounts the real `BlocksContent` composition with
  `glassAssistant = true`, `ReferenceThinkingCard`, `NexlyPlanCard`, the real
  orb and `nexlyPearlBackdrop`. It is the same renderer path the chat screen
  uses.
- **iOS:** `NexlyChatRenderTests` hosts the real `NexlyAssistantBlocksView`
  (Thinking card + Plan card + glass cards + orb) over `NexlyChatBackdrop` in a
  keyed `UIWindow` and captures the composited layer tree with
  `drawHierarchy(afterScreenUpdates:)`, so `UIVisualEffectView` glass is
  included.

The wire is the exact nine-row plan from
[`chat-plan-reference.png`](../chat-plan-reference.png), including the
reference tones, closing line and 9:45 PM timestamp.

Targets: 393×852, 411×914, 360×780 at 3× (Android Roborazzi outputs the same
pixel sizes).

## Full reference screen

`nexly-chat-full-411x914.png` (and the iOS twin) is the WHOLE screen, built
from production pieces:

- the shared `NexlyChatHeader` / `NexlyBrandHeader`;
- the real `UserMessage` / `MessageBubble` user turn, including the peach
  bubble, avatar, 9:41 PM timestamp and delivery ticks;
- the real assistant `BlocksContent` / `NexlyAssistantBlocksView` with the
  Thinking card and all nine plan rows;
- the shared `ComposerRow` / `NexlyComposerBar` capsule with the peach
  paper-plane.

At 411×914 the complete composition — header, user turn, preamble, Thinking
card, all nine timetable rows, closing line, timestamp and composer — fits
without scrolling. The 393×852 images are the narrow-width variant; the last
row is below the fold there, which is the honest narrow-device behaviour.

## Adaptive thinking / effort

The `*thinking*` images are a **non-plan question** (“Quantum entanglement…”).
They prove the card is not fixed reference copy:

- “Understanding your request…”
- “Working through the best approach…” with the active Reasoning halo
- “High effort · composing the answer…”

The live card derives its phase from the real turn: the backend's `mode`
event (`effectiveMode`, `effort`), the reasoning stream, and whether the
answer has started. Plan turns keep the exact reference copy. A flash turn
with no reasoning does not show a fake thinking card.

Produced by:

- `.github/workflows/android-app.yml` → step “Nexly chat response renders”
- `.github/workflows/ios.yml` → step “Collect the Nexly chat response render”

Artifacts on the run: `nexly-chat-render-android`, `nexly-chat-render-ios`.

## Verified runs

Both render/test gates completed green:

- Android: https://github.com/GrapseeAgency/the-gs-ai-app/actions/runs/39836410886
- iOS: https://github.com/GrapseeAgency/the-gs-ai-app/actions/runs/39836414880

Android passes the full unit suite (267 tests, including the new
`NexlyPlanTest`); the screenshot tests are gated behind `-PgsShots=true` and are
excluded from the ordinary suite, exactly like `HomeScreenshotTest`.

The screenshots here are from commit `f8932fe0` on
`nexly/chat-reference-verify`. The iOS full-chat image in this folder predates
the final composer bottom inset (`1d0632fc`); that run changes only the
floating margin, not the composition.
