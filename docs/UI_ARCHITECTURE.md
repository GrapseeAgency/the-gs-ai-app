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

## 4b. Artwork — one illustrator, two platforms

The illustration set is drawn, not imported. `ui/components/ReferenceArt.kt`
(Android) and `Components/GsArtwork.swift` (iOS) implement the same fourteen
scenes from primitives, so both platforms share:

- **one light source** — upper left; every highlight and specular sits there;
- **one material** — peach glass with a white edge and a warm, never grey,
  shadow;
- **one motif** — the five-petal bloom, which reappears inside the lens, the
  water, the library card and the notification bell.

| Scene | Used by | Android | iOS |
|---|---|---|---|
| `Bloom` | Welcome, onboarding, empty chat, brand lockup | `RefArt.Bloom` | `.bloom` |
| `Conversation` | Empty chats list, empty thread | `RefArt.Conversation` | `.conversation` |
| `Search` | No search results | `RefArt.Search` | `.search` |
| `Offline` | Network-empty states | `RefArt.Offline` | `.offline` |
| `Error` | Failed turns | `RefArt.Error` | `.error` |
| `Library` | Saved responses | `RefArt.Library` | `.library` |
| `Voice` | Voice empty state | `RefArt.Voice` | `.voice` |
| `Success` | Plan/billing, confirmations | `RefArt.Success` | `.success` |
| `Privacy` | Privacy sections | `RefArt.Privacy` | `.privacy` |
| `Archive` | Empty archive | `RefArt.Archive` | `.archive` |
| `Notifications` | Notifications empty | `RefArt.Notifications` | `.notifications` |
| *authored render* | Home — avatar and video-card art | `R.drawable.gs_companion_orb` | `GSCompanionOrb` |
| `Pearl` | available, unused since Home adopted the authored render | `RefArt.Pearl` | — |
| `Lotus` | Home — Today's reflection card: the flower alone | `RefArt.Lotus` | `.lotus` |
| `Reflection` | empty states: lotus on still water | `RefArt.Reflection` | `.reflection` |
| `Video` | available, unused since Home adopted `Pearl` | `RefArt.Video` | `.video` |
| `Clarity` | available, unused since Home adopted the orb | `RefArt.Clarity` | `.clarity` |

Rules:

1. A new state gets a scene only if the product really has that state; the set
   is not decoration.
2. Scenes are composed from `drawPetal`, `drawBurst`, `drawCrescent`,
   `drawBubble` and `drawSparkle` — no ad-hoc shapes that break the material.
3. `contentDescription` is a short sentence about the scene, or `""` when the
   art is decorative and the adjacent text already says it.
4. No bitmap illustrations and no generated raster art; if it cannot be drawn
   in the shared language, it does not ship.

### 4b-bis. Home page contract (measured from sheet 3 of the reference set)

Every number below was read off the 1024x1536 sheet, not guessed. Keep them in
sync across `home/HomeDashboardScreen` and `Features/Home/HomeDashboardView`.

| Element | Value |
|---|---|
| Gutter | 20dp |
| Header | avatar 38, wordmark 18 semibold, subtitle 13, two 40dp glass circles |
| Greeting | 32 semibold, 36 leading, one four-point sparkle 26 |
| Subtitle | 15/22, muted |
| Pills | 12sp, 11dp vertical inset (42dp tall), 8dp horizontal, 5dp gaps |
| Bento | 200dp tall, 1.22 : 1 columns, 0.64 : 1 right stack (39/61) |
| Video card | near-white fill `#FDFAF8 -> #F7EAF1 -> #F9EEE5`, title 16/21, body 12/17, pearl art 200dp bleeding bottom-right |
| Mind Guide | orb 38, title 13.5 semibold, body 11.5/15 |
| Reflection | title 13.5 semibold, body 11.5/15, lotus 84 bleeding bottom-right |
| Today's Used | 18 semibold, See All 14 |
| Suggestions | 32dp lightbulb tile, title 14, body 12, no chevron |
| Dock | 14dp frame and gaps, 46dp wells, 60dp Generate well around a 48dp pill, 15sp label |
| Rhythm | 30 before greeting, 30 before pills, 28 before bento, 32 before Today's Used, 8 before suggestions |

## 4c. Chart interactivity coverage

Every chart resolves a tap to a `ChartDatum`; nothing swallows a gesture and
nothing selects the whole conversation.

| Family | Charts | Hit test |
|---|---|---|
| Core | bar, line, area, pie/donut | band / point / slice |
| Charts 2 | grouped bar, box plot, heatmap, treemap, waterfall, candlestick, gantt | band, cell, tile, row |
| Charts 3 | sankey, network, force, wordcloud, sunburst, bullet, quadrant, violin, contour, parallel, dendrogram, depth | node, ring, word, line, cell, level |

`ChartInspectBar` is a sibling of the plot, never an overlay: the datum the
reader selected stays visible while the chart stays readable.

## 4d. Reference sheets without a system behind them

Five reference screens are deliberately unbuilt, because the app has no system
they would read or write. Copying their pixels would produce convincing lies:

| Reference screen | Why it is not built |
|---|---|
| Mentions (@you / @project / @health) | No mention graph exists; nothing to list. |
| Quick actions (simplify / shorter / longer / change tone) | The chat contract has no transform instruction; every tile would be a no-op. |
| Usage & Limits | Nothing meters tokens, images or uploads. |
| Subscription ($12.99 / month) | No billing backend, no SKUs, no purchase path. |
| Themes (Aurora / Onyx / Forest palettes) | The token set has light/dark plus six accents; there are no alternate palettes. |

When one of those systems lands, its screen goes in against this table — not
before.

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
| AI Intelligence | `settings/ReferenceSettingsScreens.AiIntelligenceScreen` | `Features/Settings/AiIntelligenceView` |
| Model Details | `settings/ModelDetailsScreen` | `Features/Settings/AiIntelligenceView` (sheet) |
| Chat Details | `chat/ChatInfoScreen` | `Features/Chats/ChatDetailView` (sheet) |
| Chats multi-select | `chat/ChatsScreen` (long-press) | `Features/Chats/ChatsListView` (Select in menu) |
| Home resume card | `home/HomeDashboardScreen.RecentPreviewCard` | `Features/Home/HomeDashboardView.resumeCard` |

## 7. Definition of done for a surface

1. Built from the kit + tokens; no raw hex/radius/spacing.
2. Glass by role; no local border over a rim.
3. Icons via `GsIcons` (opaque) / SF Symbols in glass tiles.
4. Interactive blocks outside any `SelectionContainer`; taps resolve to data.
5. Honest empty/error states; every row routes to a real screen.
6. Static gates + the platform compile (Android `android-app`, iOS
   `ios-native` device+simulator) are green.
