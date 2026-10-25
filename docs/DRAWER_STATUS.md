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

## The panel surface, before and after (2026-10-25)

The iOS panel measured weaker than Android's and weaker than the sheet, and the
report canvas was still showing 24 Oct captures. Both were settled on fresh
pixels — the "before" pair from one commit, the "after" pair from the commit
that changed the surface.

Captures, all at 393 × 852: `home-renders-ios` from ios.yml run **40200332103**
and `home-renders` from android-app run **40200334070**, both on `main` at
**7897bdd4** (the "before"); `home-renders-ios` from ios.yml run **40202990869**
at **98e0f565** (the "after"). Android's capture is byte-identical in
40197395473, 40200334070 and 40201927842 — sha256
`0d0970b7b3dce5fd3a5656ed3d9fa7b511d6bd0c4ff6c1f7dfa55b3a1e2bedbf` — so the
platform that did not change is a control, not a second variable.

**Every number in the table below is printed by the gates themselves**, on those
captures: `check_drawer_render.py` prints `panel_mean`, `panel_std`, `rim_1p5_pt`
and the 13-column rim profile alongside the gated `rim_delta`, HF p90 and column
spread, and `--derive` prints the same for the sheet. The first three are
derived-and-printed, not gated, and the note above that file's `BANDS` says why:
the sheet's own rim over its first 1.5 pt is +1.3 L because its frame edge is the
device bezel, so a band around the renders would fail the reference picture, and
a band wide enough to hold both would pass anything. There is no scratch
instrument behind any number here.

| | panel mean | std | body | rim @1.5 pt | rim peak | HF p90 | column spread |
|---|---|---|---|---|---|---|---|
| reference sheet | 233.5 | 28.5 | 239.5 | +1.3 | +9.6 | 1.00 | 2.9 |
| Android | 231.2 | 26.5 | 235.48 | +6.51 | 7.65 | 1.79 | 1.00 |
| iOS before | 236.8 | 26.5 | 240.91 | **+4.50** | 7.66 | 2.72 | 0.79 |
| iOS after | 236.9 | 26.5 | 240.98 | **+7.01** | 7.15 | 1.07 | 1.86 |

iOS's rim is 0.50 L from Android's at 1.5 pt — it was 2.0 L below — and 0.50 L at
the peak. Both platforms are inside [4, 14]. The sheet's +1.3 in the 1.5 pt
column is not a weak rim: its frame edge is the device bezel (−19.2 L), and its
lit line sits 2.3 pt inside the panel, which is why its peak is the strongest of
the four.

Column medians across rows 20–90 %, delta from each panel's own body — the
`rim profile` line the gate prints, first 13 columns:

| px from the edge | 0 | 1 | 2 | 3 | 4 | 6 | 9 | 12 |
|---|---|---|---|---|---|---|---|---|
| sheet (1.71 px/pt) | −19.2 | +4.6 | +4.6 | +5.2 | +9.6 | +2.8 | +0.7 | +0.1 |
| Android (3 px/pt) | +7.5 | +7.7 | +7.5 | 0.0 | −0.1 | −0.1 | 0.0 | −0.1 |
| iOS before | +7.7 | +5.2 | +4.4 | +3.4 | +1.0 | +0.5 | +0.3 | +0.4 |
| iOS after | +7.0 | +6.9 | +7.2 | +6.9 | +6.7 | +4.5 | +2.5 | +1.6 |

iOS had the right peak and the wrong profile: a 1.5 pt ramp from +7.7 at the
frame edge to +1.0 by 1.33 pt, so half its nominal width was unlit — a hairline
where the sheet has a bar. It is a 1.25 pt bar at +7.0 L with the sheet's 5 pt
tail now, one `LinearGradient` whose stops are written as points divided by its
width. In effective white alpha over each panel's own body that is 0.50 here,
**0.39** on Android (7.6 L over 235.5) and **0.29** on the sheet at its frame
edge (4.5 L over 239.5).

### Why it is painted, and why it is ONE pass

**A `Material` cannot be the fix, and neither can a blur.** `drawHierarchy(in:
afterScreenUpdates:)` — the only way a UI test can capture a window — does not
run the compositor's backdrop pass, so a material panel is captured as its flat
fallback tint and the gate scores a surface the device never draws. The
draw-time-blur rule (rule 4 of `check_drawer_structure.py`, which is written
against the Android drawer's `Modifier.blur`) and rule 7 (the iOS per-frame
shadow) both point the same way. Every cue is therefore painted, which is also
how Android's `GlassFrostedPanel` works: frost, veil, fill and rim, all drawn.

**The ground is opaque, and that is a decision with a measurement.** The brief
asks for "no opaque sidebar"; a genuinely translucent panel would show Home's
bento cards through it, and DR3's HF ceiling of 3.0 L exists to forbid exactly
that ghosting — the sheet's own interior measures 1.0 L, so its backdrop is
diffused, and a live blur to produce that is the one thing rule 4 forbids. The
panel is therefore an opaque ground with a painted bloom over it: the sheet's
translucency is reproduced as the *field*, not as a window. If a future iOS
build gains a compliant way to diffuse the backdrop (an offscreen pass that the
capture can see and the drag does not pay for), this is the line to revisit.

**One `Canvas`, not five stacked gradients.** The first attempt at the bloom
stacked three SwiftUI `RadialGradient`s. SwiftUI dithers each gradient layer
separately to stop banding and the dither does not cancel: the text-free margin
went from |d| p90 2.72 L to **5.07 L** and DR3 failed on run **40201896377** —
the bloom scored as exactly the ghosting the ceiling exists to catch. The same
field painted in one Core Graphics pass measures **1.07 L**, below Android's
1.79 and level with the sheet's 1.00. On this panel the HF ceiling is a budget
of about one dither: a layer added for looks is a layer taken out of the budget.

The body colour was NOT retuned to move a number. iOS renders
(250.8, 238.7, 229.8) on the final capture and the sheet's own margin measures
(251.0, 237.2, 232.0) — 1.4 L apart in luma, on a band of [231, 244] — and it
rendered (250.7, 238.6, 229.1) before. What was missing was structure, not hue:
the text-free margin's column median varied **0.79 L** where the sheet's varies
**2.86 L**. It is 1.86 L now, from the field the surface carries: a warm glow
behind the header, a blush down the leading edge, a cool veil under the footer,
at the sheet's own 2–3 L amplitude.

Dark gains the same painted bloom, scaled to 12 %, and its own measurement: the
panel went 28.70 → **34.56 L** (Android 41.90) and its separation from the strip
7.21 → **12.14 L** (Android 17.14), copy contrast 212.0 L, 0.04 % blown, hero
R−B 22.1 — all from run 40202990869's `check_drawer_dark.py`. The dark EDGE is a
separate story and a defect this pass shipped: the light profile reused at
strength measured **+99.22 L** over the 34.56 L body where Android's is **+20.00**
over 41.90 — a 1.25 pt white bar is a lamp on a dark panel, and no gate could see
it. It is now the same shape at a fifth of the alpha, and `check_drawer_dark.py`
grew K6 (`rim_delta` in [4, 40]) so it cannot regress unwatched: the new check
fails the shipped capture at 99.22 and passes Android at 20.00.

## What is gated where — and why the split

Pixels can see some of this and not all of it. The ones pixels cannot see are
gated as tokens instead, which is a weaker claim made honestly rather than a
stronger claim made falsely.

| Quantity | Gated by | Why |
|---|---|---|
| panel width, corner shape, body luma, rim, HF, column spread | pixel gate | all of them survive a 1.7→3 px/pt change |
| the dim | pixel gate | as a ratio against the same run's Home capture |
| content inset | pixel gate | the section label's ink is crisp on cream |
| the recent block's copy and its relative times | pixel gate | nine text bands and grey ink in the title column |
| no bottom navigation | pixel gate | no ink between the footer's avatar and gear |
| the hero row | pixel gate, self-anchored | a warm band ≥40 pt with R−B ≥40 at its left and ≤34 at its right |
| search pill height | **token parity** | it is glass on glass — 1.8 L from the panel — so its edges are inside the noise. `searchHeight` is compared across platforms, not against the picture. |
| chevron inset | **token parity** | its detector needs the row's y, and the harness has no status bar, so the row sits ~35 pt higher than on a device |
| every type role | **token parity** | 32 metric names identical on both platforms, 10 labels rendered on both |

## What the tests now prove

- `scripts/check_drawer_render.py` — panel ratio, the square top corner, the bottom
  arc, body luma, rim, HF, column spread, the dim ratio, pill/hero geometry, both
  insets, the recent pitch, four titles with four timestamps, no mid-footer ink,
  and the hero gradient's R−B drop. Exit 2 when a measurement is impossible.
- `scripts/check_drawer_dark.py` — dark panel, glass still present over it, copy
  contrast, no blown highlight, hero warmth, and the leading edge (K6: the light
  gate's rim probe, re-based for a 35 L body — lit, and not a lamp).
- `scripts/check_drawer_structure.py` — 17 rules (lazy list, no eager scroll, no
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
6. **The recent-chat icons are the topic table's, not one glyph.** The sheet shows
   four different icons (a lightbulb for "ideas", an atom for "quantum physics"),
   and they are *semantic*. The first implementation hashed the title into four
   glyphs to reproduce that variety; measured, three of the sheet's four titles
   hashed to the same glyph, and emoji or CJK titles hashed differently on Kotlin
   (UTF-16 units) than on Swift (Unicode scalars). The glyph now comes from the
   headline's topic — `DrawerTopic`, 22 topics, whole-word matches, specific
   before general — so the sheet's own four titles draw the sheet's own four
   glyphs: Sparkles, Lightbulb, Atom, Sprout. The first row is pinned, so it draws
   a pin instead — a fact about the row rather than a guess about its name.
   `scripts/check_icon_catalogue.py` asserts the topic tables are identical on
   both platforms, keyword for keyword, and that every topic has a render branch.
7. **iOS row actions were briefly missing.** The rebuild dropped the old drawer's
   context menu, so Android could pin/rename/archive/delete from the drawer and iOS
   could not. Restored, against the same store and API calls; the pixel gate could
   not have seen it either way.


## What is verified, and how

| Question | Answered by | Where |
|---|---|---|
| Does the panel match the sheet? | 11 quantities derived from the PNG, 5 self-anchored checks | `check_drawer_render.py` |
| Does it hold in dark? | panel/ink/scrim bands in both schemes | `check_drawer_dark.py` |
| Is the gate capable of failing? | 8 doctored frames, each caught by its own rule | `test_drawer_render_gate.py` |
| Is the list bounded? | 500-chat frame compared with the sheet-sized frame | DR11, both platforms |
| Do both platforms agree on numbers? | 32 metric names, 10 literals | `check_drawer_type_parity.py` |
| Is anything quietly expensive? | 17 structural rules with their own counter-examples | `check_drawer_structure.py` |
| Does each control go where it says? | 6 pressed controls, asserted routes | `DrawerInteractionTest` (Android) |
| Do the iOS destinations and icon agree? | 4 assertions without a screen | `DrawerLogicTests` (iOS) |
| Does the type hold up? | rows use `heightIn(min:)`/`minHeight:`; both platforms draw the product face, iOS through the Dynamic Type engine | structure rules 12–13, `Aero.specFont` |

## What is still not 100%

1. **Image Studio is a prefill, not a screen.** The drawer row routes to a chat
   seeded with an image prompt. Building the studio is a page of work, not a
   drawer change.
2. **Device verification is the user's.** The capture runs with the safe area
   zeroed; production now ignores it too (so the panel is full-bleed exactly as
   the sheet), and the footer keeps a 30 pt clearance measured from the capture.
   Whether the home indicator still clears it on hardware is a device question.
3. **Two quantities remain token-gated, not measured**: pill height (42 pt) and
   the chevron inset. The detectors for them were unstable on the sheet itself,
   and a band that passes for the wrong reason is worse than a stated gap.
4. **iOS draws the panel's bottom-right arc from a constant**, not from a
   measurement: the arc's own centre sits outside the sheet's crop.


## The headline, the sync, and the icons

Three things turned out to be missing behind the drawer's "recent chats" section.

**The headline was a slice.** A chat was named `content.trim().slice(0, 40)`. A
first message that opened with a code fence named the chat after the fence; a
message that was a URL named it after the URL; every cut landed mid-word and
newlines stayed in. `src/lib/conversation-title.ts` now strips fences, bare URLs,
attachment placeholders and markdown, collapses whitespace, cuts on a word
boundary and falls back to the attachment's name — with 20 cases in
`scripts/test-conversation-title.ts`. The turn also emits the new title as a
`conversation` event, and its prompt context no longer receives the stale title it
was built before updating.

**The headline did not reach the drawer.** iOS merged the server's conversation
page only inside the Chats inbox, so a server-side auto-title could never reach the
sidebar; that merge now lives in `ConversationStore.syncFromServer` and the drawer
calls it once per presentation. Android's drawer asks
`ChatRepository.refreshConversations` on open for the same reason.

**The icons.** 5,466 Lucide names ship on both platforms. iOS shipped them and
drew none of them: `GsIconView` had no caller, so the drawer's rows were SF Symbols
while Android drew Lucide — two icon languages in one product. Rows now draw the
family on both platforms, and the glyph is chosen from the headline's topic
(`DrawerTopic`, 22 topics, whole-word matches, specific before general), so
"Explain quantum physics" is an atom rather than a graduation cap. The sheet's own
four titles are the test vector: plan → Sparkles, ideas → Lightbulb,
quantum → Atom, Kyoto → Sprout.

`scripts/check_icon_catalogue.py` audits the whole family on every run: 1,611
geometries and 5,466 names per platform, header claims matching the file, no
duplicate names, every alias resolving to a real geometry, identical name sets
across the two platforms (including Swift's backticked keywords), the topic tables
identical keyword-for-keyword, a render branch for every topic, and every icon the
app references present. Four doctored catalogues prove it can fail.
