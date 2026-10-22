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

Produced by:

- `.github/workflows/android-app.yml` → step “Nexly chat response renders”
- `.github/workflows/ios.yml` → step “Collect the Nexly chat response render”

Artifacts on the run: `nexly-chat-render-android`, `nexly-chat-render-ios`.

## Verified runs

Both workflows completed green:

- Android: https://github.com/GrapseeAgency/the-gs-ai-app/actions/runs/39821402724
- iOS: https://github.com/GrapseeAgency/the-gs-ai-app/actions/runs/39821406646

Android passes the full unit suite (267 tests, including the new
`NexlyPlanTest`); the screenshot test is gated behind `-PgsShots=true` and is
excluded from the ordinary suite, exactly like `HomeScreenshotTest`.

The screenshots here are from commit `99bb7082` on
`nexly/chat-reference-verify`.
