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
