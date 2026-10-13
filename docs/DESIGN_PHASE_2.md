# Design Phase 2 — structural frontend redesign

Operator directive: the app must stop looking like a system design tool and
become a clean, beautiful, understandable, impactful consumer app. Structural,
not cosmetic. The visual bar is Dola (screenshots in upload/); the spirit is
taken, the execution must be humbler and better. RULE ZERO is enforced by
rejection: no emoji in chrome, no em-dashes in copy, no decorative gradients,
monochrome plus one accent (the existing ink/white accent stays).

## Icon system (the operator's first complaint)

- Family: **Lucide** (ISC license), pinned `lucide-static@0.469.0`.
  Geometry vendored as native Compose vectors by `scripts/gen_gs_icons.py`
  into `ui/icons/GsIcons.kt` — one 24x24 stroke drawing, width 2, round
  caps/joins. One optical weight app-wide. iOS keeps **SF Symbols** (the
  platform-coherent outline family; also what Dola uses on iOS).
- Container rule: rounded square, radius `GsRadius.md`, one size per tier
  (44dp list rows / 40dp compact). No mixing.
- Color rule: monochrome icons in neutral containers everywhere; the primary
  action per screen uses the ink/white accent. Every screen, consistently.
- No decorative icons. No robot/sparkle glyphs (RULE ZERO).

## Motion system (operator values, `ui/theme/Ease.kt`)

| Use | Value |
|---|---|
| Entrance | 200ms ease-out |
| Exit | 150ms ease-in |
| State change | 180ms ease-in-out |
| Sheet / drawer | 250ms cubic-bezier(0.2, 0, 0, 1) |
| Message send | slide up 8dp + fade, 150ms (`Modifier.messageAppear`) |
| Streaming chunk | 120ms fade |
| Reduced motion | every curve flattens (1ms) via the existing settings gate |

No bounce. No spring for entrances/exits/state changes.

## Resource → screen mapping (each used where it earns its keep)

| Resource | Screen | Why |
|---|---|---|
| uiverse #4 dull-ladybug-18 (action button, layered press + icon slide) | Composer send button | primary CTA deserves a physical layered press |
| uiverse #7 dangerous-dolphin-47 (input container, soft ring + glow) | Composer bar | focus ring in monochrome, no gradient |
| uiverse #13 cowardly-goat-90 (expanding input, attach + voice) | Composer attach expansion | animated width reveal of attach actions |
| uiverse #14 hot-dodo-99 (orb pulse into panel) | Streaming thinking indicator | the AI orb is already the app's life-sign |
| uiverse #5 stupid-vampirebat-24 (layered 3D button, outline sweep) | Drawer "New chat" | the one rare, high-emphasis action |
| uiverse #6 clever-insect-61 (pill button, animated arrow) | Drawer/empty-state "continue" CTAs | navigation affordance with motion cue |
| uiverse #2 tiny-moose-42 (soft checkbox, radial glow, stroke check) | Search filter multi-select | quiet multi-select with a satisfying check |
| uiverse #1 evil-shrimp-6 (glossy 3D checkbox, inset shadow) | Settings one premium toggle row | premium feel exactly once |
| uiverse #3 fast-vampirebat-53 (staggered multi-element loader) | Model loading / first-load state | staggered calm loader |
| uiverse #8 great-wombat-13 (3D folder card, open state) | Archived chats folder card | the folder metaphor made physical |
| uiverse #9 big-chipmunk-8 (tool showcase card) | Onboarding "what GS can do" slide | feature card, not marketing |
| uiverse #10 tricky-robin-67 (flip card) | Settings "About" informational card | expandable detail without a new screen |
| uiverse #12 sour-dragon-56 (animated tooltip) | Onboarding hint tooltip | first-run guidance |
| uiverse #11 young-walrus-64 (organic blob loader) | Research/long-operation wait | distinct from model loading |
| MD3 carousel spec | Mode picker sheet + onboarding pages | horizontal selection, MD3 motion/touch targets |
| 21st ai-prompt-box | Composer rebuild | layout, expand behavior, keyboard handling |
| 21st chat-message | Renderer comparison | adopt patterns where better than ours |
| 21st chat-reasoning | Collapsible reasoning panel in streaming message | thinking trace, collapsed by default |
| assistant-ui alternatives matrix | Chat UI sanity check | adopt only patterns every production app shares |

## Screen worklist (before/after per screen, one commit per screen)

1. Splash / first launch
2. Auth gate
3. Onboarding
4. Empty chat state
5. Active chat, streaming reply
6. Rich reply (table + code + citation)
7. Image attachment flow
8. Composer (idle / typing / attach)
9. Mode / capability picker sheet
10. Drawer / navigation
11. Search history
12. Settings
13. Settings sub-screens
14. Error / failure states
15. Model loading / download progress
16. Reduction — screens that should not exist get deleted

## Reduction audit (screen 16)

- Home surface: reduced to the chat-first shell (the app IS chat).
- Drawer: already a chat history panel; More sheet keeps the catalog one step
  away. Audit continues per-screen; deletions recorded in commits.

## Outcome (design phase 2, shipped on main)

Shipped units, one commit each:
1. Foundation: GsIcons (Lucide 0.469.0, 98 curated stroke vectors), GsEase
   motion spec, this document. 9902e1a
2. Drawer (10): search pill + New chat accent top, recents weighted center,
   account footer pinned bottom, Lucide, spacing-led groups. fb82117
3. Composer (8): one calm pill, borderless field, plus/mic, ink-circle send
   with up-arrow + layered press, neutral mode chip, ink stop control. 716e430
4. Attach + mode sheet (9): rounded-square icon containers, spacing groups,
   MD3-carousel mode row with peek + scale-in check. 66a4d96
5. Empty chat + message motion (4, 5): filled quiet suggestion chips with
   staggered entrance, every transcript row slides up 8dp + fades 150ms. 409c509
6. A11y/CI semantics for the new send/stop controls. e098347
7. Chat chrome (5, 6, 14): all Material glyphs to Lucide, assistant sparkle
   becomes the GS monogram. c19b054
8. Shared surfaces (6, 7, 11): chats list, chat search, rich blocks,
   attachment chips, conversation actions, shared input bar. 4170a11
9. Settings (12): group icons into the container spec, Lucide, stale accent
   line tells the truth. 8d8674e
10. Auth + onboarding (2, 3): Lucide sweep, capability cards get the check
    scale-in. 84f2b50
11. Splash + model download (1, 15): splash verified clean (no change), the
    indeterminate download wait becomes the staggered three-dot loader. 4c56657
12. Full-family sweep: 23 remaining surfaces to Lucide. 2c3f521
13. Reduction (16): the More sheet deleted; demo surfaces
    (Explore/Create/Projects/Library/Assistants) out of navigation; Voice
    promoted to a drawer row. 1f49f9b
14. Suite: drawer capture (20b) + Settings path via the account footer. ad59c72

## Evaluated and deliberately dropped (with reasons)

- uiverse #5 rotating outline sweep (New chat): decorative motion; in
  monochrome the kineticPress carries the press honestly.
- uiverse #1 glossy 3D checkbox: the native Switch keeps the platform
  contract; glossy blur is decoration here.
- uiverse #10 flip card: About is three rows of text; a flip card is a trick.
- uiverse #6 animated-arrow pill: no continue-CTA surface exists where the
  motion earns its keep; adding one would be inventing UI.
- uiverse #11 blob loader: one loader language per app; a second organic
  loader would compete with the orbs and the staggered dots.
- uiverse #12 tooltip: needs a trigger layer that does not exist; adding one
  crosses into new-feature territory the directive forbids.
- 21st chat-reasoning panel: the thinking mode already labels itself honestly
  in the orb row; a full trace panel is a larger build and stays a follow-up.

## Fix round (the build the phase broke, and screen 16 finished)

The design-phase commits never compiled: the icon generator's Kotlin template
called an API that does not exist (`ImageVector.Builder` takes no
`defaultStroke*` params; stroke width/cap/join are per-path on `addPath`;
`PathParser().parsePathDataString` is `parsePathString`). The AFTER evidence
run (38464946814 at ad59c72) caught it; no emulator had ever rendered these
commits. Fix round 04afe08:

- Generator template corrected against the verified compose 1.7.3
  `ui-graphics` sources (addPath: pathData/fill/stroke/strokeLineWidth/
  strokeLineCap/strokeLineJoin; PathParser.parsePathString().toNodes());
  GsIcons.kt regenerated (98 icons, byte-identical geometry, new contract).
- `Ease.kt`: `composed` imported from `androidx.compose.ui` (not runtime) —
  this alone resolved the messageAppear/staggerDelay cascade.
- Missing imports restored: GsComponents `background`; consent dialog
  `Row/fillMaxWidth/Arrangement/GsStaggeredLoader`; onboarding + settings
  `RoundedCornerShape`; ChatScreen `clickable` + top-level `staggerDelay`;
  GsDrawer `contentDescription` semantics.
- Wrong-icon names: SharedChatsScreen Trash2Outline -> Trash2, SearchScreen
  SearchOff -> SearchX.
- 141 dead `material.icons` imports purged across 21 files; zero `Icons.*`
  expressions and zero material-icon imports remain in ui/ — the one-family
  rule is now literal.
- Screen 16 completed: the six unreachable demo dirs deleted outright
  (ui/create x5 incl. the four studio workspaces, ui/explore, ui/library,
  ui/projects x2, ui/assistants x3, ui/home) with their NavHost
  registrations, route constants (EXPLORE/CREATE/LIBRARY/PROJECTS/ASSISTANTS,
  assistant + project + studio routes) and SECTION_ROUTES entries.
- Search index honestly reduced to the real Room corpus (conversations +
  messages, FTS with LIKE fallback) after the fake library/assistant/project
  catalogues died with their screens.
- DeviceVerificationTest offline/backend-error assertions + doc comments now
  assert the RULE ZERO paren-wrap production copy ("(Offline, cannot reach
  GS.)"), matching the SendFailureNoticeTest treatment from the v0.72.0
  release round.
- AssistantsStore/ProjectStore/SampleData stay (persisted user data + schema
  stability); they are UI-orphaned and can go in a data-layer cleanup round.

## Follow-ups (carried)

- iOS mirror (unchanged, next round).
- The 21st chat-reasoning panel (unchanged).
- Data-layer orphan cleanup (AssistantsStore/ProjectStore/SampleData now
  UI-unreferenced; persisted-data migration needed before deletion).
