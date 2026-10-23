# The drawer — measured status

Every number here is derived from `assets/reference/drawer-reference.png` by
`scripts/check_drawer_render.py --derive`, which recomputes the whole table on each
CI run and fails if the sheet's sha256 is not the one the contract was written
against. Nothing in this file was estimated from the picture by eye.

The reference is the phone screen cropped from the design mock at native
resolution: **673 × 1493 px**, i.e. 393 pt wide at **1.7125 px/pt**.

## What the sheet actually specifies

| Quantity | Value | How it was measured |
|---|---|---|
| Panel width | **0.786 W** (529 px, 308.9 pt) | the most common largest luma step across 180 rows |
| Top-right corner | **square** | the right edge is at x=529 in every sampled row above the fold |
| Bottom-right corner | **27.5 pt** (r = 47.1 px) | three-point circle fit on the arc, large root |
| Panel body | **239.5 L** | median of the text-free left margin |
| Lit leading edge | **+9.6 L** over the body | brightest column median inside the rim band |
| Panel interior structure | **HF p90 1.0 L**, column spread **2.9 L** | derivative p90 in blank bands |
| Dim over Home | **0.80** of our own Home band (≈0.20 black) | sheet strip p90/p99 vs Home's same band |
| Search pill height | **42.0 pt** | vertical step profile through the pill |
| Hero row height | **53.7 pt**, gap from the pill **15.2 pt** | same profile |
| Content inset | **25.1 pt** (label ink) | first ink column of the section label |
| Chevron inset | **24.5 pt** from the panel's right | rightmost ink in the first row |
| Recent row pitch | **55.7 pt** | four title bands, evenly spaced |
| Recent ink | 4 titles, 4 relative times | 4 bands one pitch apart, each with grey ink beneath |
| Footer | no ink between avatar and gear | cluster centres inside 0.40–0.72 of the panel |

## Three findings that changed the design

**The panel is not a blur of Home.** Its internal high-frequency energy is 1.0 L
and its column median varies by 2.9 L — a smooth bloom field, with no card-shaped
steps. A live blur of our Home (whose bento cards sit directly behind the panel)
would put that structure *into* the glass, which is the opposite of what the sheet
shows. The panel is drawn with `GlassFrostedPanel` — the frosted backdrop behind a
translucent fill — and the gate asserts the outcome (HF ≤ 3.0 L, column spread
≤ 12 L), not the technique.

**The dim is 0.20, not 0.32 and not 0.50.** Material 3's default scrim (0.32) and
the app's `SCRIM_LIGHT` (0.50) were both darker than the sheet. That single number
is most of the difference between the old drawer reading as a veil and this one
reading as glass. Because alpha is not observable in a screenshot, the gate
measures the *resulting* luma as a ratio against the Home capture of the same run:
0.78–0.82.

**The panel is rounded on one corner only.** The sheet's top-right corner is
square at every sampled height; the current drawer rounded both ends. DR1 asserts
the top inset is 0 at 5% height, and an arc fit for the bottom.

## What the tests now prove

- `scripts/check_drawer_render.py` — panel ratio, the square top corner, the bottom
  arc, body luma, rim, HF, column spread, the dim ratio, pill/hero geometry, both
  insets, the recent pitch, four titles with four timestamps, no mid-footer ink,
  and the hero gradient's R−B drop. Exit 2 when a measurement is impossible.
- `scripts/check_drawer_dark.py` — dark panel, glass still present over it, copy
  contrast, no blown highlight, hero warmth.
- `scripts/check_drawer_structure.py` — 11 rules (lazy list, no eager scroll, no
  draw-time blur, no per-frame shadow, no store read from `body`, no infinite
  animation), each with a counter-example it must reject, run with `--self-test`.
- `.github/scripts/check_drawer_type_parity.py` — 32 metric names identical on both
  platforms, 10 labels rendered on both.

## The gaps, stated rather than smoothed over

1. **Image Studio has no screen.** The row lands in the composer with a real draft
   (`chatPrefill`, the same contract Home's Create tile uses). A dedicated studio
   screen is a future page; the row is not dead, but it is not a studio either.
2. **The Explore row points at the app's existing Explore route.** A concurrent
   workstream is building the Explore feature; this drawer touches exactly one
   string in `DrawerData.kt` and the matching `DrawerDestination.route` in Swift
   when that lands. No routing file is edited here.
3. **Absolute vertical positions are not gated.** The mock's screen works out to
   ~872 pt tall against 393 × 852, a ~2% stretch, so only gaps and pitches are
   compared; three coarse anchors are all that absolute y is used for.
4. **Text widths are not gated, only sizes.** The shipping face is not the mock's
   face (see `docs/TYPOGRAPHY.md`), so a width comparison would fail a correct
   layout.
5. **Dark mode is designed, not referenced.** The sheet has no dark frame; the dark
   page is scored against physical requirements (panel dark, glass present, copy
   legible, hero warm) rather than against a picture.
6. **The recent-chat icons are decorative.** The sheet shows four different glyphs;
   a chat has no icon of its own, so each row gets a stable hash of its title. Same
   family, same variety, no invented data.
