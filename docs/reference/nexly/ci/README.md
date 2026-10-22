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
pixel sizes). The screenshots intentionally cover the **model response** (the
assistant turn); the chat header, user bubble and composer are shell, verified
separately in `ChatDetailView.swift` / `ChatScreen.kt`.

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

- Android: https://github.com/GrapseeAgency/the-gs-ai-app/actions/runs/39829906823
- iOS: https://github.com/GrapseeAgency/the-gs-ai-app/actions/runs/39829910647

Android passes the full unit suite (267 tests, including the new
`NexlyPlanTest`); the screenshot tests are gated behind `-PgsShots=true` and are
excluded from the ordinary suite, exactly like `HomeScreenshotTest`.

The screenshots here are from commit `ea4e52bc` on
`nexly/chat-reference-verify`.
