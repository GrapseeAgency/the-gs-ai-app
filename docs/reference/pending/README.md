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
  what it erases (everything this app stores on this device), and confirming it
  also signs the session out. There is no server account to delete.
- **Chat History — the topic chips are KEYWORD-derived** from the conversation
  title through the shipped `drawerTopic()` classifier (the same one the drawer
  uses). The schema has no facet column, so the buckets are approximate by
  construction; `Chats` is the classifier's general-chat bucket, which is what
  makes it different from `All`.
- **Chat History — "Saved items"** replaces the mock's "Saved Conversations":
  the app has saved *items*, and that is where the row goes.
- **Chat History — the totals are ACTIVE conversations.** An archived chat is
  counted on the archived facet, not in the mock's headline total.
- **Model Selection — Auto-select model** is the mock's OFF switch, disabled,
  because the backend always chooses the model.
- **Model Selection — the model radios** persist a preference, but the backend
  ignores a client-chosen model (`src/app/api/v1/conversations/[id]/route.ts:20`).
- **Model Selection — the page continues below the mock's closing card.** Model
  source, View model details, Rendering and the on-device footnote are kept so
  `SETTINGS_MODEL` is not orphaned. The mock's own three groups (AI Model,
  Reasoning Effort, Model Preferences) fit one 393×852 view; the extras scroll.

## Acceptance

These are not "captured" until they are *gated*, the same way the Settings trio
is: rendered in CI, anchored on a measured rim, and compared against the mock's
own numbers with a self-test proving the gate can fail. A screenshot that merely
exists is not evidence of parity.

## Where each screen stands (2026-10-28, second pass)

All three are **built on Android**. `ReferenceRowInteractionTest` is the
always-on proof (9 JVM tests: hub → privacy → chat routes, model persistence,
reset confirmation, remember switch, four effort round-trips, and Model
Preferences visible without scrolling). The device captures are the pixel
evidence; the first capture (40650433868) and the final one (40659388170) are
named where they changed a decision.

**`model-selection.png` — built.**

- AI Model: the mock's four rows, both sentences of copy and the Recommended
  chip; each model keeps its mock tone; the chosen model is a check in a soft
  well, as drawn.
- Reasoning Effort: four across in one card with hairline column dividers, the
  mock's order, copy, timings, radios (hollow rings, accent ring + dot when
  selected) and the accent outline on the chosen tile. Each description is the
  mock's two lines; the radio announces `selected` semantics. The older
  three-tile Fast/Thinking/Auto control is deleted.
- Model Preferences: real switches. Auto-select is OFF and disabled; Remember
  my choices writes `SettingsStore.rememberChoices`; Reset to default opens the
  repo's confirm dialog, then writes pro / on / standard.
- The model radio writes `SettingsStore.preferredModel` (it used to be a local
  `remember`, so the tick reset every visit), and the mock's closing card
  ("Smarter choices. Better conversations.") is present.

**`privacy-data-controls.png` — built.**

The capture showed Personalization & Ads, Third-Party Integrations, Search
History and Data Retention still drawing a chevron; `RefNavRow`'s default
trailing IS a chevron, and `onClick = null` only removed the click. They now
pass `trailing = {}`. Chat History opens the search page for real. The hero
uses the same shipped lotus photograph and `LOTUS_BLEED_ALPHA` recipe as the
Settings hub, and the footer card carries the mock's title above the honest
body copy.

**`chat-history-search.png` — built.**

- Six chips, as drawn. `More` opens a sheet with the two real facets the store
  can answer: Archived conversations (an archived preview flow) and Has files
  (an EXISTS over message attachments). The round filter button beside the
  field opens the same sheet.
- The topic chips filter both the recent list and the search hits through
  `drawerTopic()`; classification runs once per title on `Dispatchers.Default`,
  is cached process-wide, and filters inside `derivedStateOf`.
- Totals come from `ConversationDao.observeTotals()` — `COUNT(*)` + `MAX` over
  active conversations — not from the preview list's size.
- Recent Chats: ≤7 rows from the preview flow, real last-message preview
  (markdown flattened), pinned → Star/Mint, has files → Paperclip/Blue, else
  MessageCircle/Lilac, a real ⋮ opening `ConversationActionsSheet`, and a
  "View all" link to `CHATS`.
- Quick Actions: Search Chats focuses the field, Manage History → `CHAT_ARCHIVE`,
  Saved items → `SETTINGS_MEMORY`, Export Chat History writes the JSON export
  through `DataExport`. The footer CTA starts a new conversation.

**iOS has no counterpart for any of the three.**

## Resume order

1. Port the three screens to iOS.
2. Gate each screen the way the Settings trio is gated (measured rim +
   self-test), once their layout is frozen.

## Instrument notes worth keeping

The Settings gate is green on both platforms (`3dc6e9ff`) *because* several
instruments were repaired, each found by a failing run: iOS was being graded with
Android's rim column, Android's anchors were stale by 53pt after a row-pitch
change, two material windows were sampling the page instead of glass, the
screenshot suite discarded its own verdict twice (`|| true`, then a zero exit from
`am instrument`), and a probe reported `OK` while being provably unable to fail.
Do not trust a green from this rig without reading which probe produced it.

