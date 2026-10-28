# Pending references — supplied by the owner, not yet in the app

Three screens were supplied as reference art. **None of them exists on either
platform**, so they are listed here rather than in a status doc that could read
as "done".

| reference | expected screen | state |
| --- | --- | --- |
| `privacy-data-controls.png` | "Privacy & Data Controls" — hero, Data & Permissions, Conversation & Activity, Export & Deletion groups | **built (Android)** — see deltas below |
| `chat-history-search.png` | "Chat History & Search" — search field + filter chips, totals card, Recent Chats, Quick Actions 2×2, new-conversation footer | **built (Android)** — see deltas below |
| `model-selection.png` | "Model Selection & Reasoning Effort" — AI Model radio list, Reasoning Effort 4-up, Model Preferences toggles | **built (Android)** — four-across tiles + real switches |

## Evidence

Before this pass a literal search for each screen's own copy returned zero files
across `android/app/src/main` and `ios/App/Sources` — that is what "absent" meant.
After it, on Android:

```
Privacy & Data Controls                 1 file  (PrivacySettingsScreen)
Chat History & Search                   1 file  (ChatSearchScreen)
Model Selection & Reasoning Effort      1 file  (AiIntelligenceScreen)
```

iOS still returns 0 for all three — Android only, see the resume order.

## Near misses — do not mistake these for the reference

- `AiIntelligenceScreen` (`android/…/settings/ReferenceSettingsScreens.kt:627`,
  title "AI Intelligence") is a *different* screen from
  `model-selection.png`: different title, different section set, no Reasoning
  Effort 4-up, no Model Preferences toggles. It is also the screen that had no
  navigation entry until the settings hub gained one, so it has never been
  verified against anything.
- `PrivacySettingsScreen` is not "Privacy & Data Controls": the reference's four
  groups (Data & Permissions, Conversation & Activity, Export & Deletion, plus
  its two hero cards) are not its structure.
- The drawer's recent-chats list is not "Chat History & Search": the reference
  is a full page with search, chips, totals and a Quick Actions grid.

## Accepted deltas from the mocks (all three screens)

Every one of these is a place where the mock draws something the build cannot
back. They are corrections, not omissions:

- **Privacy — App Permissions** names the two grants the manifest actually has
  (microphone, notifications). The mock says "camera, microphone, location";
  there is no camera or location grant in this build.
- **Privacy — footer** says "private to this app, never sold". The mock says
  "encrypted"; Room here is not encrypted, and saying so would be false.
- **Privacy — Personalization & Ads, Third-Party Integrations, Search History,
  Data Retention** are static rows with no chevron. There is no ad profile, no
  integration, no stored search log and no retention setting behind them.
- **Privacy — Delete Your Account** keeps the mock's title but its subtitle says
  what it erases (everything this app stores on this device). There is no server
  account to delete.
- **Chat History — the four topic chips** (Chats / Research / Ideas / Planning)
  are KEYWORD-derived from the conversation title. The schema has no facet
  column, so the buckets are approximate by construction.
- **Chat History — "More" is not drawn.** The mock puts a sixth chip beside the
  five topics; there is no sixth topic to open, and a chip that filters nothing
  is a dead control. The row still scrolls when the five do not fit.
- **Chat History — the mock's filter button is not drawn.** It would have to
  back two filters (this week, has files) that no control currently exposes;
  the topic chips are the only filters the page has.
- **Chat History — "Saved items"** replaces the mock's "Saved Conversations":
  the app has saved *items*, and that is where the row goes.
- **A vertical ratio is not the mock's.** The mock canvas is 0.441 aspect and
  fits all three model groups in one frame; a 393×852 device is far shorter, so
  Model Preferences sits below the fold and the page scrolls. Only the mock's
  horizontal ratios and its type scale were taken.
- **Privacy — the hero art is the shipped Pearl illustration.** The mock bleeds
  a pearlescent photograph into the hero's right edge; the same role is filled
  by `RefArt.Pearl` at 55 %, clipped by the card. No unshipped photograph is
  invented for it.
- **Model Selection — Auto-select model** is the mock's OFF switch, disabled,
  because the backend always chooses the model.
- **Model Selection — the model radios** persist a preference, but the backend
  ignores a client-chosen model (`src/app/api/v1/conversations/[id]/route.ts:20`).

## Acceptance

These are not "captured" until they are *gated*, the same way the Settings trio
is: rendered in CI, anchored on a measured rim, and compared against the mock's
own numbers with a self-test proving the gate can fail. A screenshot that merely
exists is not evidence of parity.

## Where each screen stands (2026-10-28, after `3f4c2266`)

All three are **built on Android**. The first device capture (run 40650433868)
found four defects; `3f4c2266` fixed three of them, and the pass that followed
wired the model radio to `SettingsStore`, replaced the repeated row filler with
the real last message, and made the topic chips actually filter.

**`model-selection.png` — built, JVM-verified, awaiting the next capture.**

- AI Model: the mock's four rows, both sentences of copy and the Recommended
  chip; each model keeps its mock tone (Pro peach, Advanced lilac, Code mint,
  Creative rose); the chosen model is a check in a soft well, as drawn.
- Reasoning Effort: four across with the mock's order, copy, timings, radios
  and tone-coded wells; the selected tile carries the mock's accent outline.
  The older three-tile Fast/Thinking/Auto control is deleted.
- Model Preferences: real switches. Auto-select is OFF and disabled (the
  backend chooses the model); Remember my choices writes
  `SettingsStore.rememberChoices`; Reset to default writes pro / on / standard.
- The model radio writes `SettingsStore.preferredModel` (it used to be a local
  `remember`, so the tick reset every visit). `ModelSelectionInteractionTest`
  presses all four effort tiles, both switches and Reset on the JVM.

**`privacy-data-controls.png` — built; chevrons removed from static rows.**

The capture showed Personalization & Ads, Third-Party Integrations, Search
History and Data Retention still drawing a chevron; `RefNavRow`'s default
trailing IS a chevron, and `onClick = null` only removed the click. They now
pass `trailing = {}`. Chat History opens the search page for real. The hero
carries the mock's right-edge pearlescent bleed, drawn from the shipped
`RefArt.Pearl`.

**`chat-history-search.png` — built; the capture's three defects fixed.**

- The six-chip row clipped "More" at the right edge; the row scrolls now, and
  "More" itself is gone — there is no sixth topic behind it.
- The topic chips now filter both the recent list and the search hits
  (keyword-derived; the schema has no topic column). Before this, tapping one
  only repainted the chip.
- Recent rows show the real last message from
  `conversationDao.observeActiveWithPreview()`, not the same
  "Tap to reopen this conversation" line on every row.
- The totals caption wraps instead of eliding to "Your chat history with Ne…".

**iOS has no counterpart for any of the three.**

## Resume order

1. Capture the three screens again (`android-screens`, label `final`) and read
   `26`, `31`, `33`; the JVM test covers behaviour, not pixels.
2. Port all three to iOS.
3. Only then gate each screen; a screen that is still being laid out has nothing
   stable to anchor a rim on.

## Instrument notes worth keeping

The Settings gate is green on both platforms (`3dc6e9ff`) *because* several
instruments were repaired, each found by a failing run: iOS was being graded with
Android's rim column, Android's anchors were stale by 53pt after a row-pitch
change, two material windows were sampling the page instead of glass, the
screenshot suite discarded its own verdict twice (`|| true`, then a zero exit from
`am instrument`), and a probe reported `OK` while being provably unable to fail.
Do not trust a green from this rig without reading which probe produced it.

