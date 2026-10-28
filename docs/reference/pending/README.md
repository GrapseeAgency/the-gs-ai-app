# Pending references — supplied by the owner, not yet in the app

Three screens were supplied as reference art. **None of them exists on either
platform**, so they are listed here rather than in a status doc that could read
as "done".

| reference | expected screen | state |
| --- | --- | --- |
| `privacy-data-controls.png` | "Privacy & Data Controls" — hero, Data & Permissions, Conversation & Activity, Export & Deletion groups | **absent** |
| `chat-history-search.png` | "Chat History & Search" — search field + filter chips, totals card, Recent Chats, Quick Actions 2×2, new-conversation footer | **absent** |
| `model-selection.png` | "Model Selection & Reasoning Effort" — AI Model radio list, Reasoning Effort 4-up, Model Preferences toggles | **absent** |

## Evidence

A literal search for each screen's own copy returns zero files across
`android/app/src/main` and `ios/App/Sources`:

```
Privacy & Data Controls                 files: 0
Chat History & Search                   files: 0
Model Selection & Reasoning Effort      files: 0
Reasoning Effort                        files: 0
Quick Actions                           files: 0
Export Chat History                     files: 0
```

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

