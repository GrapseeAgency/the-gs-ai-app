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

One family across the whole product — **Poppins** (Regular / Medium / SemiBold /
Bold, bundled for both platforms and registered in `UIAppFonts` on iOS) — in
three voices. There is no serif tier: an earlier revision set display and
headline in a serif, and it read as editorial on a product that is a companion.

| Role | Weight | Size / leading | Tracking |
|---|---|---|---|
| displayLarge | Bold | 34 / 42 | −0.6 |
| displayMedium · headlineLarge | SemiBold | 30 / 38 · 26 / 33 | −0.5 · −0.3 |
| headlineMedium · headlineSmall | SemiBold | 22 / 29 · 19 / 26 | −0.2 · −0.1 |
| titleLarge · titleMedium · titleSmall | SemiBold | 17 / 23 · 15 / 21 · 13 / 18 | 0 |
| bodyLarge · bodyMedium · bodySmall | Regular | 15 / 23 · 14 / 21 · 12 / 17 | +0.1 |
| labelLarge · labelMedium | SemiBold · Medium | 14 / 19 · 11.5 / 16 | +0.1 · +0.2 |

Android's `GsTypography` (ui/theme/Type.kt) is the source of these numbers and
iOS's `Aero` mirrors them. `check_home_type_parity.py` fails CI when a Home role
disagrees between the platforms, and `TextStyle.untracked()` (Type.kt) zeroes the
positive tracking on Home because iOS renders no letter spacing at all — the
same string was otherwise one word wider on Android, which is enough to change a
line break.

**The mock is not set in Poppins, and nobody should claim otherwise.** The
reference sheet's greeting is a narrower grotesque with a double-storey `g`: at
matched cap height "Good morning," spans 159 pt in the mock, 171 pt in SF Pro
and 193 pt in Poppins. Poppins stays because it is the product's declared family,
it is the only one bundled for both platforms, and it is what mandate b10 (one
surface, identical on both) can actually satisfy — but the letterforms do not
match the mock, and the sizes above are taken from the mock's *metrics*, not from
its typeface.

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
