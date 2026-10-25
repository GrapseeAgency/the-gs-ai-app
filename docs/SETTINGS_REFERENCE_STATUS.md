# SETTINGS / PROFILE / MEMORY — reference status

The parity contract for the three settings mocks is committed under
`docs/reference/settings/` (`settings-hub.png`, `profile-preferences.png`,
`memory-management.png`) with the measured ratios in `settings-geometry.json`.
The mocks are 0.441-aspect canvases — horizontal ratios are device-true,
vertical ones are re-derived from renders (the Explore rule).

Audit (2026-10-25, pre-implementation): iOS Settings was one page of nine
inline sections with a 4-row hub stub; Android's hub existed but with a
"Company" identity card, 7 micro-labelled groups and square tiles; iOS had no
sign-out; iOS had no Memory screen; neither platform grouped rows into one
card with internal hairlines on iOS.

## What shipped, per element

| Element | iOS | Android |
|---|---|---|
| Page frame | `GsSettingsPage` (`GsSettingsKit.swift`): pearl backdrop, 36pt glass back well, 18/12 header, `LazyVStack` content inset 22 | `RefSettingsPage` (`ReferenceGroupKit.kt`): `gs_home_backdrop` in light, 36dp back well, same header, scroll inset 22 |
| Grouped card | `GsGroupCard` — one `.gsGlass(.cardLight, corner: 22)` per group, hairlines as row overlays (1/3 pt, 50 pt indent) | `RefCard(role = GsGlassRole.CardLight)` + `RefRowDivider(50.dp)` |
| Identity card | initials avatar + edit pencil badge, name/email from `AccountStore`, `Free Plan` pill, `GSHomeLotusNew` bleed at 45 % masked into the right edge | same, `RefAvatar` + `gs_home_lotus_new` at `LOTUS_BLEED_ALPHA` |
| Upgrade card | `GsWellTile(peach)` + copy + `GsInlinePill("Upgrade")` | `RefWellTile(GsWellTone.Peach)` + `RefInlinePill` |
| Section heads | `GsGroupHeader` 17/12 with right caption | `RefGroupHeader` 17/12 with right caption |
| Wells | `GsWellTile` 28–34 pt pastel circles | `RefWellTile` 28–34 dp pastel circles |
| Rows | `GsSettingsRow` 52 pt min, 14/10 padding | `RefNavRow` 52 dp min, 14/10 padding |
| Capability strip | `GsTileStrip` + `GsStripTile` | `RefTileStrip` + `RefStripTile` |
| Closing info card | `GsInfoCard` | `RefInfoCard` |
| Log Out | `SettingsView` row → `confirmationDialog` → `@AppStorage("gs.session.active") = false`, the existing gate in `GSApp.swift` signs in/out | `SettingsHubScreen(onLogout)` → `SessionStore.signOut` (already wired at `GsNavHost.kt`) |

The two kits are additive on purpose. `AeroListRow` / `AeroCard` / `SectionHeader`
(iOS) and `RefRow` / `RefIconTile` / `RefSectionLabel` / `RefPrimaryButton` /
`RefDivider` (Android) are untouched — ~15 screens compose them and no mock asks
those screens to move. `GlassMaterial.*`, `ExploreView.swift`, `ExploreScreen.kt`,
`GsIcons.*`, `DesignSystem.swift` and the Home/Drawer gates are frozen and were
not edited.

## The measured numbers (Android captures, 2026-10-25)

Rendered on the JVM by `SettingsScreenshotTest` (`-PgsShots=true`), 393×852 and
360×780 at 3×, then measured by `scripts/check_settings_render.py`.

| probe | mock | android 393 | android 360 | band |
|---|---|---|---|---|
| hub card rim level | 249.4 | 252.66 | 251.65 | 242–255 |
| hub card rim over fill | +10.2 | +11.99 | +12.65 | ≥ 6 |
| hub card residual vs the law | law-defined | +0.15 | −0.15 | ±2.5 |
| hub card lift* (diagnostic) | +3.8 … +5.0 | −4.20 | −3.39 | not gated |
| hub CTA saturation (R−B p90) | ~113 (#FEB58D) | 143 | 143 | 70–160 |
| profile card rim level | 248.1 | 252.47 | 250.63 | 242–255 |
| profile card rim over fill | +10.7 | +11.14 | +9.63 | ≥ 6 |
| profile card residual vs the law | law-defined | +1.04 | +0.64 | ±2.5 |
| profile card lift* (diagnostic) | +3.8 … +5.0 | −3.13 | −3.58 | not gated |
| memory card rim level | 243.9 | 253.19 | 252.03 | 242–255 |
| memory card rim over fill | +6.4 | +16.85 | +16.03 | ≥ 6 |
| memory card residual vs the law | law-defined | n/a (sens 1.3) | n/a (sens 0.8) | ±2.5 |
| memory card lift* (diagnostic) | +3.8 … +5.0 | −1.68 | −0.93 | not gated |
| memory CTA saturation | ~113 | 128 | 141 | 70–160 |

The three cards' *residual* is the gated number (see the next section); the
mock's own **lift** over its ground is +3.8…+5.0 L and is printed as a
diagnostic only. "law-defined" is not a measurement of the mock: the 0.45 / 235.2
law was solved off the home sheet, so a settings card built from that same role
is what the residual is measured *against*.

The iOS numbers are **not yet measured**: this work was done on Linux, where the
simulator renders cannot run. `ios.yml` runs the same gate over
`SettingsRenderTests`' six captures and is the measurement — the first CI run
after this commit fills in this table's other half. The probe table itself is
platform-neutral because every window hangs off the rim the capture FOUND, not
off a hard-coded y, so iOS's safe-area difference moves nothing.

## The gate, and one deliberate change to it

`scripts/check_settings_render.py` blocks `ios.yml` and `android-app.yml` beside
the Explore gate, and each run is followed by `--self-test`, which doctors the
capture (card painted flat onto its ground, rim buried through its whole search
window, pill desaturated) and requires every gated probe to go red. A probe that
cannot fail is not a gate.

The plan asked the card probe to read **lift over the page beside it** (+1…+6 L).
That number is not measurable on these screens, and the ground is the reason:

- the page beside the card is a photograph whose left edge is 10–14 L brighter
  than its centre. On one capture the same card read **−5.5 L against its left
  gutter and +4.3 L against its right one**;
- the ground moves between captures: the backdrop is scaled to *cover* the
  screen, so one window sits over 228.7 L at 393×852 and 235.8 L at 360×780.

So `lift` is printed as a diagnostic (`lift*`, ungated) and what is gated is the
**material law** the Home gate already solved — `interior = 0.45·235.2 +
0.55·ground`, residual ±2.5 L — with the ground read out of the SHIPPED artwork
(`gs_home_backdrop.png`, 590×1278, identical bytes on both platforms, cropped
the way `ContentScale.Crop` / `scaledToFill` crop it). Verified against the
captures, that model reproduces the visible page to **±0.01 L** in both gutters,
which is smaller than one 8-bit step.

Two limits of the residual are stated rather than implied. It can only see the
fill where the ground differs from the glass token by more than the band
(`A·|F − ground|`), so the probe prints `n/a — coverage not identifiable` on a
ground that meets the fill instead of quoting a verdict; that is why Memory's two
captures are gated on its rim and its CTA. And the residual does not need to
carry a screen alone: the rim `level` is ground-independent (the Home gate's A9
law), which is what makes "the card lost its glass" visible at all.

## Deliberate differences from the mocks

1. **No stock portrait.** The identity card uses the app's own avatar treatment
   (initials in `RefAvatar`) with the mock's edit-pencil badge. Shipping a
   stranger's face as the reader's avatar is the one thing the mock cannot ask
   for. The blossom bleed behind it is the shipped `GSHomeLotusNew` photograph
   drawn at 45 % over the card's fill: in that corner the raw photograph measures
   R−B 44 and an 18.9 L spread where the mock measures 33.5 and 9.6, and 45 %
   brings both within ~2 L of the mock. Before this pass Android drew it opaque
   (twice the mock's contrast) and iOS drew no bleed at all.
2. **Recent Memories are real.** The mock's four seeded rows ("You're a student",
   "Sep 16") do not exist. Both platforms render `savedItemDao` /
   `ConversationStore.savedLibraryItems()` (date + snippet) and an honest empty
   state — "Nothing saved yet — memories appear here as you save answers and gems
   from chats" — which is what these captures show, because the harness has no
   library.
3. **No dead chevrons.** Date of Birth, Location, Tone and Detail Level render
   "Not set" / their stored value with **no** chevron: the mock paints one on
   every row, and a chevron with no destination is a dead end.
4. **Language has no page of its own yet.** The row is real and opens Appearance
   (the closest existing surface) on Android and the Language detail page on iOS;
   a dedicated page is a follow-up.
5. **"View all" on Recent Memories is omitted** on iOS for the same dead-end
   reason; there is no library destination to route to yet.
6. **Vertical ratios are not the mock's.** The mock canvas is 0.441 aspect
   against the device's 0.4613 — a 5.7 % vertical stretch — so only horizontal
   ratios were taken from it. The card inset (21.8 pt / 0.0554 W), card corner
   (22), well diameters (28–34), 50 pt divider indent and 12 pt captions come
   from the mock; every y in the gate comes from a render.

## Known follow-ups

- Delete the dead 945-line `SettingsScreen.kt` in its own commit (zero call
  sites verified) rather than mixing it into this change.
- A dedicated Language screen, and a library destination for "View all".
- The first iOS CI run's numbers belong in the table above.
