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
