# Icon migration — state and handoff

The app ships one icon family: 1,611 Lucide geometries, 5,466 public names, in
`GsIcons.kt` (Compose vectors) and `GsIcons.swift` (stroke paths + `GsIconView`).
This file records where the migration of the **iOS** surfaces onto that family
stands, so a fresh session does not have to rediscover it.

Audited on every CI run by `scripts/check_icon_catalogue.py`: geometry and name
counts against each file's own claim, duplicate names, alias resolution, identical
name sets across platforms (Swift's backticked keywords included), the topic tables
keyword-for-keyword, a render branch per topic, and every icon the app references.
Four doctored catalogues prove the check can fail.

## Done

| Surface | State |
|---|---|
| `Components/AeroDrawer.swift` | **0** `systemName:` — panel chrome, destinations, recent rows, account sheet |
| `Features/Chats/ChatsListView.swift` | 8 → 3 |
| `Features/Chats/ChatDetailView.swift` | 11 → 6 |
| Android | `GsIcons` throughout; nothing to migrate |

Gated by rule 14 of `scripts/check_drawer_structure.py`: **one `systemName:` in
`AeroDrawer.swift` fails the build**, with its own counter-example in `--self-test`.
Read that rule before adding an icon to the drawer.

## Remaining — 21 sites, all listed

Each was left alone because it does not match the three-line shape
(`Image(systemName:)` + `.font(...)` + `.foregroundStyle(...)`) that the safe
mechanical pass handled: a `Label(systemImage:)`, a ternary, or a parameterised
`icon:` / `system:` argument.

```
Chats/ChatsListView.swift:215   Image(systemName: icon)                  (param)
Chats/ChatsListView.swift:313   checkmark.circle.fill / circle           (ternary)
Chats/ChatsListView.swift:418   Image(systemName: icon)                  (param)
Chats/ChatDetailView.swift:674  wifi.slash
Chats/ChatDetailView.swift:810  Image(systemName: icon)                  (param)
Chats/ChatDetailView.swift:1007 chevron.up
Chats/ChatDetailView.swift:1015 chevron.down
Chats/ChatDetailView.swift:1025 xmark
Chats/ChatDetailView.swift:1807 Image(systemName: icon)                  (param)
Home/HomeDashboardView.swift:205  arrow.right
Home/HomeDashboardView.swift:283  sparkle
Home/HomeDashboardView.swift:358  arrow.right
Home/HomeDashboardView.swift:381  lightbulb
Home/HomeDashboardView.swift:417  sparkles
Home/HomeDashboardView.swift:451  Image(systemName: system)              (param)
Home/HomeDashboardView.swift:475  Image(systemName: system)              (param)
Home/HomeDashboardView.swift:541  play
Home/HomeDashboardView.swift:578  play.fill
Home/HomeDashboardView.swift:629  chevron.right
Home/HomeDashboardView.swift:663  chevron.right
Home/HomeDashboardView.swift:718  Image(systemName: system)              (param)
```

Verified names for these: `arrowRight`, `sparkles`, `lightbulb`, `play`,
`chevronRight`, `chevronUp`, `chevronDown`, `x`, `wifiOff`, `circleCheck`,
`circle`. For the parameterised ones, change the caller's string to the Lucide
name and resolve it with `DrawerTopic.paths(named:)` (it already covers Compass /
Activity / Bookmark / User / Settings / Bell / CreditCard), or add the name there.

## The recipe that worked

1. Read the three lines around the site — the size lives on the `.font` line and
   the colour on `.foregroundStyle` / `.foregroundColor`.
2. Replace with `GsIconView(GsIcons.<lucide>, size: <same number>, color: <same expr>)`.
3. Keep whatever `.frame(...)` followed, so layout does not move.
4. Run `python3 scripts/check_icon_catalogue.py`, then both workflows.
5. Capture the screen. A screenshot is the only evidence a glyph is the right
   glyph — see `docs/DRAWER_STATUS.md` for the capture pattern.

## Also open

The report canvas at `~/.qoder/projects/-mnt-new-volume-Grapsee-Products-GS-AI/canvases/drawer-completion.canvas.tsx`
still shows the pre-fix icons (hash-era). Refreshing it means downloading the run's
capture artifacts and rewriting the icon section.
