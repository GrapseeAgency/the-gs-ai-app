# UI architecture — the Nexly contract

This is the load-bearing contract for both native apps (Android Compose in
`android/app/src/main/java/com/grapsee/gsai/ui`, iOS SwiftUI in
`ios/App/Sources`). It exists so a surface cannot drift from the reference by
accident: every screen is built from the same four layers, and each layer has
one rule that has already cost a bug when broken.

```
Reference sheet  ──▶  Screen (one per route)
                        └─▶ Kit components (Ref*/Aero*)
                              └─▶ Glass role  (gsGlass)
                                    └─▶ Design tokens (GsTheme / Aero)
```

## 1. Tokens — one source of truth

- Android: `ui/theme/Tokens.kt` (`GsColors`, `GsRadius`, `GsSpacing`,
  `GsType`) and `theme/Type.kt`. iOS: `Theme/DesignSystem.swift` (`Aero`).
- A screen **never** writes a raw hex color, a raw corner radius, or a raw
  spacing number. It asks the token layer.
- **Contrast is a token, not a taste.** A foreground token must never resolve
  to the background token's value; text and icons use `textPrimary` /
  `textSecondary` / `textTertiary`, never `background`/`surface`.

## 2. Glass — roles, never alphas

`gsGlass(role, shape)` (Android `components/GlassMaterial.kt`) and
`View.gsGlass(_:corner:)` / `gsCapsuleGlass(_:)` (iOS
`Theme/GlassMaterial.swift`) are the only way a surface becomes glass.

| Role | Used for | Optical character |
|---|---|---|
| `Card` | content panels, list groups | soft, mid fill, low rim |
| `Elevated` | the Home hero, focus panels | higher lift, stronger rim |
| `FloatingControl` | pills, dock, composer, icon buttons | thinnest glass, brightest rim |
| `Modal` | bottom sheets / dialogs | heaviest veil, top-rounded |
| `Sunken` | input wells, destructive rows | recessed, no lift |

Four cues are painted in every role: a translucent fill, an in-scattered
airlight veil, a directional **top-leading** rim (the key light in every
reference frame), and an interior luminance fall, over a warm non-grey shadow.
A screen that adds its own border on top of a glass role is double-drawing the
rim — remove the local border.

Blur tiers: `Modifier.blur` lowers to a real `RenderEffect` on API 31+; API 26
uses the baked airlight veil. Both sit behind the same `gsGlass` call, so a
screen never branches on API level.

## 3. Icons — opaque strokes, or nothing renders

`ui/icons/GsIcons.kt` is generated from `scripts/gs-icon-stems.txt` (540
Lucide stems) by `scripts/gen_gs_icons.py`. **Every stroke must be opaque**
(`SolidColor(Color.Black)`). The composable `Icon(tint = …)` recolours with a
`SrcIn` filter (`result = tint × source alpha`); a transparent source
(`Color.Unspecified`, alpha 0) renders nothing and the tint cannot resurrect
it. This was the whole-family "invisible icons" defect — never reintroduce it.
Regenerate from the stems file; never hand-edit paths.

Icon containers are glass tiles (`RefIconTile` on Android, the rounded glass
tile in the drawer/settings rows on iOS): a 34–40dp rounded square behind the
glyph, not a bare icon on the canvas.

## 4. Interaction — a control owns its pointer

- **Never wrap an interactive block in a `SelectionContainer`.** In
  `chat/content/RichBlocks.kt`, `BlocksContent` mounts selection around
  **prose only** (`Block.isProseBlock()`: paragraph, heading, bullet/ordered
  list, blockquote, table, math). Widgets, charts, code cards, mermaid,
  collapsibles, citation chips and tool results render **outside** it and own
  their taps. Wrapping the whole column made a chart tap select the message.
- Every chart honours the `ChartProbe` contract (`content/ChartInteractivity.kt`):
  a tap resolves to a `ChartDatum` and reports it in the `ChartInspectBar`
  under the plot — no overlay, so the datum is never hidden by the readout.
- Every tappable surface carries a `Role.Button`, an `onClickLabel`, and a real
  destination. A row that goes nowhere is a bug.

## 5. Honesty — no fabricated data

A screen renders only what the app actually has. There is no invented usage
percentage, no fake model name, no "Billing history" that does not exist, and
no notification feed when nothing is wired. An absent state says so in plain
language and offers the one real action that resolves it.

## 6. Screen inventory (reference sheets → routes)

| Sheet screen | Android route/screen | iOS route/view |
|---|---|---|
| Home | `home/HomeDashboardScreen` | `Features/Home/HomeDashboardView` |
| Chat + reasoning card | `chat/ChatScreen` | `Features/Chats/ChatDetailView` |
| Drawer | `navigation/GsDrawer` | `Components/AeroDrawer` |
| Your chats | `chat/ChatsScreen` | `Features/Chats/ChatsListView` |
| Search conversations | `chat/ChatSearchScreen` | `Features/Chats/ChatSearchView` |
| Settings hub | `settings/SettingsHubScreen` | `Features/Settings/SettingsView` |
| Profile / Account | `profile/ProfileScreen`, `settings/*` | `Features/Profile/ProfileView` |
| Welcome / onboarding | `auth/ReferenceWelcomeScreen`, `auth/OnboardingScreen` | `Features/Auth/ReferenceWelcomeView` |
| Auth | `auth/AuthScreen` | `Features/Auth/*` |
| Voice | `voice/VoiceScreen` | `Features/Voice/VoiceView` |
| Notifications | `notifications/NotificationsScreen` | `Features/Notifications/NotificationsView` |
| Billing / plan | `billing/BillingScreen` | `Features/Billing/BillingView` |
| Orb | `orbs/OrbPhase.kt` + `orbs/ThinkingOrb.kt` | `Orbs/OrbPhase.swift` + `Orbs/ThinkingOrbView.swift` |
| Rendered artifacts | `chat/content/Widget*` (200+ widgets) | `Features/Chats/Content/*` |

## 7. Definition of done for a surface

1. Built from the kit + tokens; no raw hex/radius/spacing.
2. Glass by role; no local border over a rim.
3. Icons via `GsIcons` (opaque) / SF Symbols in glass tiles.
4. Interactive blocks outside any `SelectionContainer`; taps resolve to data.
5. Honest empty/error states; every row routes to a real screen.
6. Static gates + the platform compile (Android `android-app`, iOS
   `ios-native` device+simulator) are green.
