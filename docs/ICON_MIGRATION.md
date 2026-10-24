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
| Menu labels (`Label(_:systemImage:)`) | 29 → **0**, capped at zero by rules 15–17 |
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

## The menu chrome — harness before conversion (2026-10-25)

A `Label`'s icon inside a `Menu`, a `.contextMenu` or `.swipeActions` is drawn
by UIKit's menu chrome, which decides whether an icon appears at all. No static
check can see that, so the 29 rows were converted only once an instrument
existed to prove where the glyph lands.

`ios/App/Tests/UITests/MenuChromeTests.swift` launches the app with
`-gs_test_seed_chats` (three fixed conversations — one pinned, one user and one
assistant turn each) and opens all EIGHT real menus:

| Menu | Rows | Trigger |
|---|---|---|
| chats row context menu | 5 | long-press the row |
| chats row leading swipe | 1 | swipe right |
| chats row trailing swipe | 2 | swipe left |
| header sparkle menu | 3 | long-press the sparkle |
| header menu button | 3 | long-press the menu button |
| user bubble menu | 2 | long-press 10 pt left of the text |
| assistant bubble menu | 7 | long-press the reply |
| drawer recent-row menu | 4 | long-press a recent |

The two inline sheet labels (Translation header, Copy translation) are not
chrome — ordinary `Label` layout, the position the family already draws
everywhere else.

Per row, on the presented menu's screenshot: ink exists (≥ 90 px at 2x), its
height is 9–24 pt, and it sits in the slot the label implies — left of the
title, or above it inside a swipe button. Every number is printed as
`MEASURE …` and written to `report.json`, every menu is attached to the result
bundle as `menu-<name>`, and the workflow exports them BEFORE asserting and
fails if the test did not pass or a capture is missing.

**The SF baseline** (runs 40131109721, 40132162258, 40133180902): menu rows
measure h 12.3–19.3 pt at a leading inset of 27.7–29.7 pt. Those are the
calibration numbers; the family's rows are held to the same bounds. The first
family measurement (the drawer's recents, run 40134206951) lands on them:
h 15.3–16.7 pt, inset 27.7–30.0 pt — same column, same optical size, and the
destructive row's glyph reddens like SF's did (see `menu-chrome-ios`).

The harness paid for itself before a single row was converted, by finding four
things the code hid:

1. iOS's menu icon column starts 27–29 pt into the row — the first bound of
   22 pt failed SF's own glyphs.
2. A swipe action stacks icon over title INSIDE one button, so a band above the
   text has to be clamped inside the button; unclamped it measured 26.3 pt of
   page.
3. The user bubble's text is selectable — a press on it starts a selection, not
   the bubble's menu; the press moves onto the bubble's padding.
4. An element-level long press on the two HEADER buttons fires their ACTION (the
   sparkle opened a new chat, the menu button opened the drawer). The same
   gesture through a coordinate opens the menu instead — both header menus were
   measured that way in run 40135074617, so nothing is stranded.

## The 29 rows → the family

Every row is `GsMenuLabel(title, icon:)`, and the icon is a **template image**
rasterised from the same 24-unit geometry `GsIconView` strokes — deliberately
not a `Canvas`. A menu paints its icon with the label's own tint, which is how a
destructive row turns red and a disabled one dims; a Canvas drawing its own
colour would keep that colour in every row.

| Menu | Was | Now (Android's twin) |
|---|---|---|
| chats row | pin · trash · archivebox · checkmark.circle · pencil | pin · trash2 · archive · circleCheck · squarePen |
| swipe leading | pin | pin |
| swipe trailing | trash · archivebox | trash2 · archive |
| header sparkle | square.and.pencil · magnifyingglass · mic | squarePen · search · mic |
| header menu | ellipsis · magnifyingglass · square.and.pencil | ellipsis · search · squarePen |
| user bubble | doc.on.doc · pencil | copy · squarePen |
| assistant bubble | doc.on.doc · speaker.wave.2/stop.fill · arrow.clockwise · translate · bookmark · arrow.triangle.branch · square.and.arrow.up | copy · volume2/volumeX · refreshCw · languages · bookmark · split · share |
| drawer recent | pin · pencil · archivebox · trash | pin · squarePen · archive · trash2 |
| translation sheet | translate · checkmark/doc.on.doc | languages · check/doc (inline, sides 17/14) |

`Label(_:systemImage:)` is now **ZERO** in all three files, and rules 15–17 of
`check_drawer_structure.py` fail the build on the first one that returns.

## Evidence state — read before claiming these done

Step 5 of the recipe is a capture, and only Home has a harness that can make
one. The `home-renders-ios` artifact of run 40118659001 is that proof for the
12 Home sites: the dock's filled house, filled book and gear-with-hole, the
family `sparkles` on Assistant and Generate, `menu`, the four pills, the
outline `play` chip over the filled disc triangle, both chevrons, `arrowRight`
and `lightbulb` — all rendered by Xcode, not asserted by a script.

The 9 Chats sites have NO capture behind them: no test renders `ChatsListView`,
the options sheet, the find bar, the offline pill or Chat Info (checked —
`ios/App/Tests` never names those views). They are verified by (a) the real
Xcode compile and XCTest run, which proves the wiring and nothing about the
glyph, and (b) line-by-line comparison against Android's twin rows, which is
how the `share` / `squarePen` / `trash2` / filled-disc corrections above were
found. A chats render harness is the missing evidence step; until it exists,
do not describe those 9 as capture-verified.

## What the two workflows say (step 4, run 2026-10-24)

**ios.yml — the iOS compile gate — is green**: run 40120041698 on `b563f113`
(static gates → real Xcode build → the app's XCTests → the Home, Nexly-chat and
Explore renders with their pixel probes). Run 40118659001 is the same verdict
one commit earlier and is where the Home PNG above came from.

**ios-native's app job was already red on `main` before this migration**, and
the control proves it: run 40121938239 (branch at `84fa992a`, the commit this
work started from) fails at the DEVICE build with
`NexlyPlan.swift:1003: the compiler is unable to type-check this expression in
reasonable time` — a pre-existing defect, not an icon one. This branch fixes
that expression, so the job now gets as far as the tests.

There it fails one test, and a control run says it is not this branch's
failure: `ci/uitest-control` (`2af5ae98` = `84fa992a` + ONLY the NexlyPlan
compile fix, no icon change) reports the identical result —
`passed=80 failed=1`, `GsChatUiTests.testSendHelloRendersARealAssistantReply`,
"composer field never appeared", with XCUITest's own note
`Automation type mismatch: computed TextField from legacy attributes vs TextView
from modern attribute` on `XC_kAXXCAttributeElementType =
SwiftUI.VerticalTextView`. The composer is `AeroInputBar`
(`Components/AeroComponents.swift`), which no commit here touches; the runner
now boots an iPhone 17 Pro image where `app.textFields["Ask anything…"]` no
longer matches a `VerticalTextView`, and the last green ios-native (run
39673169516, 2026-10-21) predates both that image and the five model-backed
tests that now run. **Open defect, separate from this migration:**
`GsChatUiTests` needs a query that accepts the modern automation type (or the
test needs `.textViews` fallback) before ios-native can go green again.

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
7. Check the twin's NAME, not the SF shape. `archivebox`/`trash`/`pencil` are
   SF spellings of shapes the family draws under different names, and Android
   already picked them: `archive`, `trash2`, `squarePen`, `share`. Reading the
   Kotlin row for the same control settled every one of these.
8. The two iOS workflows are NOT the same compile. An expression that sails
   through ios.yml's build can time out under ios-native's (that is exactly
   what `NexlyPlan`'s bubble did). And when a workflow fails on a line you
   never touched, run a control — a branch at the base commit plus only the
   fix in question — before believing your diff caused it.

## Also open

The report canvas at `~/.qoder/projects/-mnt-new-volume-Grapsee-Products-GS-AI/canvases/drawer-completion.canvas.tsx`
still shows the pre-fix icons (hash-era). Refreshing it means downloading the run's
capture artifacts and rewriting the icon section.


## The 29 menu labels — converted and measured

All 29 are on the family, capped at zero by rules 15–17, and every one of the
eight menus is driven and measured by `MenuChromeTests` on every run. The
family's own numbers, from run 40135074617: menu rows h 13.3–16.7 pt at a
leading inset of 27.7–30.0 pt — the same column and the same optical size as
SF's 12.3–19.3 at 27.7–29.7, with the destructive row's glyph still red.

The harness also carries a bound it does not currently need: a menu that cannot
be opened is recorded in `unreachableMenus` rather than silently skipped, and
the workflow fails if that list ever names anything beyond the two header menus
it was written for. A skip list that can grow is how a gate turns itself off.
