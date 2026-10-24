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
| `Features/Chats/ChatsListView.swift` | 8 → 3 → **0** |
| `Features/Chats/ChatDetailView.swift` | 11 → 6 → **0** |
| `Features/Home/HomeDashboardView.swift` | 12 → **0** |
| Android | `GsIcons` throughout; nothing to migrate |

Gated by rule 14 of `scripts/check_drawer_structure.py`: **one `systemName:` in
`AeroDrawer.swift` fails the build**, with its own counter-example in `--self-test`.
Read that rule before adding an icon to the drawer.

## The 21 sites — done (2026-10-24)

All 21 `Image(systemName:)` sites are migrated; the three surfaces now carry
**0** `systemName:`. The parameterised helpers no longer take a name string at
all — `quickCard` / `selectionAction` / `optionLabel` / `row` /
`GlassCircleButton` / `HomePill` take a `GsIconPaths`, so the compiler checks
what a lookup table used to check at runtime.

What the sites became, surface by surface:

- **ChatsListView** — quick-access card `archivebox` → `GsIcons.archive`;
  selection tick `checkmark.circle.fill / circle` → a filled `Aero.accent` disc
  carrying `GsIcons.check` (14) over `Aero.onAccent`, unselected `GsIcons.circle`
  — the construction Android's selection Box uses, and the picture SF drew;
  `circleCheck` was rejected as a stroked ring, which would read as an outline
  on both phones. Selection bar → `star / archive / trash2`, the exact trio
  Android's `SelectionAction` row draws.
- **ChatDetailView** — `wifi.slash` → `wifiOff` (size 12); options sheet →
  `share / squarePen / info / fileText / eraser / trash2 / x`, each named for
  Android's option row rather than for the SF shape it replaced (the sheet's
  Share is `Share`, its rename is `SquarePen`, its delete is `Trash2`); find
  bar → `chevronUp / chevronDown / x` (14), matching ChatScreen; Chat Info
  rows → `squarePen / cpu / brain`, matching ChatInfoScreen.kt.
- **HomeDashboardView** — `arrow.right` → `arrowRight`, `sparkle` → `sparkle`
  (one four-point star), `lightbulb` → `lightbulb`, `sparkles` → `sparkles`,
  `chevron.right` → `chevronRight`, chip `play` → `play` (Android's
  `ReferenceChip` draws the same outline). Header circles → `sparkles` / `menu`
  (Android's `GsIcons.Menu` twin); pills → `plus` / `compass` / `sparkles` /
  `ellipsis` (Android: `Plus` / `Compass` / `Sparkles` / `Ellipsis`).

Glyph size is the spec number wrapped in `Aero.scaledLength(_, relativeTo:)`, so
an icon keeps the accessibility contract the page's text already honours — the
same rule as the drawer's `DrawerMetrics` glyph constants.

The handoff's list of verified names mostly held (`arrowRight`, `sparkles`,
`lightbulb`, `play`, `chevronRight`, `chevronUp`, `chevronDown`, `x`, `wifiOff`,
`circle`), with two corrections found by reading Android's twins instead of the
SF shapes it replaced: the selection mark is a filled disc + `check`, not
`circleCheck`, and the options sheet is `share` / `squarePen` / `trash2`, not
`squareArrowUp` / `pencil` / `trash`. Where a suggested name and the twin's
shape disagree, the twin wins — the two apps ship one icon language.

Four silhouettes are hand-drawn in the 24-unit space beside `OpenBookGlyph`,
because the generated set is stroke-only by contract: the dock's `FilledHouse`
and `FilledGear` and the video disc's `FilledPlay` — each the point-for-point
twin of Android's `Filled*` in `HomeDashboardScreen.kt`. The gear's hub hole
needs no fill-rule API: Android reaches it with `PathFillType.EvenOdd`, but
this SDK exposes even-odd nowhere below iOS 17 — runs 40117076973 and
40117461200 rejected `eo:` and `isEOFilled:` on `Shape.fill`, and run
40118125849 rejected `eo:` on the Canvas context's fill too. The shoelace
signs of Android's two contours (+215.6 ring, −28.8 hub) show they already
wind opposite, and oppositely-wound nonzero paints the same picture for nested
contours, so the geometry ships in Android's order with a plain fill.
Reversing the hub to "create" the hole would have filled it solid — a wrong
glyph no compile can catch, which is why the winding was measured first.

## Remaining — the menu chrome

29 `Label(systemImage:)` sites still draw SF glyphs: 8 in ChatsListView (row
context menu + swipes), 17 in ChatDetailView (message menu, translation menu,
overflow), 4 in AeroDrawer (recents menu). They have survived every pass
because their icons are drawn by SwiftUI's menu chrome, and a `GsIconView`
inside a menu-item label is not guaranteed a leading icon slot — the migration
must not trade an SF glyph for an icon-less row. Verify on a capture before
converting. Rule 14 of `check_drawer_structure.py` counts `systemName:` only,
so it neither catches these nor protects against re-introducing them.

## The recipe that worked

1. Read the three lines around the site — the size lives on the `.font` line and
   the colour on `.foregroundStyle` / `.foregroundColor`.
2. Replace with `GsIconView(GsIcons.<lucide>, size: <same number>, color: <same expr>)`.
3. Keep whatever `.frame(...)` followed, so layout does not move.
4. Run `python3 scripts/check_icon_catalogue.py`, then both workflows.
5. Capture the screen. A screenshot is the only evidence a glyph is the right
   glyph — see `docs/DRAWER_STATUS.md` for the capture pattern. The Home
   capture from run 40118659001 is that evidence for this page: dock house /
   book / gear-with-hole, the `sparkles` on Assistant and Generate, `menu`,
   the four pills (`plus` / `compass` / `sparkles` / `ellipsis`), the outline
   `play` chip over the filled disc triangle, both `chevronRight`s,
   `arrowRight` and `lightbulb`.
6. A parameterised site is not done until its CALLER stops naming an SF Symbol.
   Changing `Image(systemName: icon)` to a `GsIconView` while the caller still
   passes `"archivebox"` just moves the symbol name into a string that nothing
   reads — these helpers now take a `GsIconPaths`, so the compiler refuses the
   old name outright.

## Also open

The report canvas at `~/.qoder/projects/-mnt-new-volume-Grapsee-Products-GS-AI/canvases/drawer-completion.canvas.tsx`
still shows the pre-fix icons (hash-era). Refreshing it means downloading the run's
capture artifacts and rewriting the icon section.
