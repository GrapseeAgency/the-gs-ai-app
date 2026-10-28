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
- **Chat History — "Saved items"** replaces the mock's "Saved Conversations":
  the app has saved *items*, and that is where the row goes.
- **Model Selection — Auto-select model** is the mock's OFF switch, disabled,
  because the backend always chooses the model.
- **Model Selection — the model radios** persist a preference, but the backend
  ignores a client-chosen model (`src/app/api/v1/conversations/[id]/route.ts:20`).

## Acceptance

These are not "captured" until they are *gated*, the same way the Settings trio
is: rendered in CI, anchored on a measured rim, and compared against the mock's
own numbers with a self-test proving the gate can fail. A screenshot that merely
exists is not evidence of parity.

## Where each screen stands (2026-10-28, `7d691cc6`)

**`model-selection.png` — built on Android, verified by capture, not yet parity.**

Verified against run 40644772932's `31-ai-intelligence.png`:

- all four model rows carry the mock's full copy, both sentences, no ellipsis;
- "Recommended" sits beside the title on its chip;
- every row shows a visible ring (selected keeps its inner dot);
- Reasoning Effort runs in the mock's order: Minimal, Light, Standard, High.

Still different, and the reason this is not parity:

1. Reasoning Effort is a **row list with chevrons**. The mock draws **four across
   as tiles with radios**. The chevron is also wrong semantically — it promises
   navigation where the control selects.
2. Model Preferences is **rows**; the mock draws **switches**.
3. The rows are taller than the mock's tiles, so Model Preferences falls below the
   fold where the mock shows it in one view.
4. iOS has no counterpart at all.

**`privacy-data-controls.png`, `chat-history-search.png` — not started.** No
source exists on either platform; nothing to iterate on until the first build.

## Resume order

1. Reasoning Effort → four-across tiles with radios (kills deltas 1 and helps 3).
2. Model Preferences → switches.
3. `chat-history-search.png` — full page: search field + filter chips, totals
   card, Recent Chats, Quick Actions 2×2, new-conversation footer.
4. `privacy-data-controls.png` — two hero cards + four groups.
5. Port `model-selection` to iOS.
6. Only then gate each screen; a screen that is still being laid out has nothing
   stable to anchor a rim on.

## Instrument notes worth keeping

The Settings gate is green on both platforms (`3dc6e9ff`) *because* several
instruments were repaired, each found by a failing run: iOS was being graded with
Android's rim column, Android's anchors were stale by 53pt after a row-pitch
change, two material windows were sampling the page instead of glass, the
screenshot suite discarded its own verdict twice (`|| true`, then a zero exit from
`am instrument`), and a probe reported `OK` while being provably unable to fail.
Do not trust a green from this rig without reading which probe produced it.

