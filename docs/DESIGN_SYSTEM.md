# AERUO KINETIC — Design System

> "Kinetic aurora over obsidian." Disciplined editorial surfaces; energy lives in **motion** and **one aurora accent family**. This file is the single source of truth — Android (`ui/theme/*`) and iOS (`Theme/DesignSystem.swift`) implement these tokens exactly.

## 1. Palette

### Light (paper editorial)
| Token | Hex |
|---|---|
| background | `#F7F7F5` |
| surface | `#FFFFFF` |
| text | `#14161A` |
| textMuted | `#6B7280` |
| outline | `#E5E5E1` |
| container | `#F0F0ED` |
| containerHigh | `#E2E2DD` |

### Dark (obsidian — the hero mode)
| Token | Hex |
|---|---|
| background | `#0A0D12` |
| surface | `#11151C` |
| raised | `#181E28` |
| text | `#EDEFF2` |
| textMuted | `#8B93A1` |
| outline | `#232B37` |
| container | `#141923` |
| containerHigh | `#232B3A` |

### Aurora accent (the only accent)
| Token | Hex |
|---|---|
| accent | `#2DD4A8` |
| accentDeep | `#0FA37E` |

### Kinetic aurora gradient — **restricted use**
`#2DD4A8 → #4CC3FF → #9D7BFF`
Allowed ONLY when the AI is literally alive: streaming caret, generation progress, voice waveform, listening state, primary CTA glow on press. Never decorative, never on static chrome.

## 2. Typography

| Style | Font | Size/Weight |
|---|---|---|
| displayTitle | **Serif** | 34 semibold |
| display | Serif | 28 semibold |
| headline | Serif | 22 semibold |
| title | Sans | 17 semibold |
| body | Sans | 15 regular |
| caption | Sans | 13 regular |
| label | Sans | 12 medium |

Serif = editorial/brand voice (greetings, screen titles, empty-state headlines). Sans = everything functional.

## 3. Motion (the "Kinetic" in Aeruo)

| Token | Value |
|---|---|
| spring.standard | response 0.35 / damping 0.8 (Compose: stiffness 380, ratio 0.8) |
| spring.snappy | response 0.28 / damping 0.75 |
| spring.gentle | response 0.5 / damping 0.9 |
| pressScale | 0.97 on every tappable surface |
| staggerStep | 30ms between list item entrances |
| auroraShift | 2.2s linear loop for gradient drift |
| caretPulse | 800ms |

**Rules:** everything interactive gets spring press feedback. Lists enter staggered. Sheets rise with spring + gesture drag. Streaming text breathes (aurora caret). Haptics: light impact on quick actions, success on completion, medium on destructive confirm.

## 4. Geometry

| Token | Value |
|---|---|
| radius.card | 16 |
| radius.chip | full capsule |
| radius.sheet | 24 |
| radius.input | 26 (pill input bar) |
| elevation | soft: shadow r12 y4 @6% (iOS) / 1–3dp (Android) |

## 5. Component API (frozen on both platforms)

`SectionHeader · Card · Chip · ListRow · InputBar · QuickActionTile · EmptyState · LoadingState(aurora) · ErrorState(retry) · OfflineBanner · SkeletonBlock · AuroraIndicator · ScreenScaffold(title/back/actions)`

## 6. Composition rules

1. Background never pure black in light mode, never flat grey in dark — always the obsidian step ladder (bg → surface → raised → container).
2. One accent per screen view; aurora gradient max once per view-state.
3. AI responses: generous line-height (1.5), body 15, code blocks on `raised` with 12-radius, tool states always visible with aurora indicator.
4. States are first-class: every screen ships empty/loading/error/offline from day one.
