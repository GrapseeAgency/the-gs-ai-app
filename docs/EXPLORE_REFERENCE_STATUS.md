# EXPLORE — reference status

The parity contract for the Explore page is the user's production mock,
committed rectified: `docs/reference/explore/explore-home-reference.png`
(the phone-screen interior cropped from the 1024×1536 mock; crop box and
probe-space note in the sidecar JSON). This page records what the shipped
iOS and Android builds measure against it, what intentionally differs,
and what the CI gate now enforces.

Audit date: 2026-10-25 (commits `57a51938`…`c48aa78f`, verified against
the CI renders of `c48aa78f`: runs 40177639269 android-app, 40177187833
ios.yml).

## What the mock shows, and where it is enforced

| Element | Status | Evidence |
|---|---|---|
| Header: orb, "Nexly", "AI Companion", search circle | shipped | `ExploreView.swift` header / `ExploreScreen.kt` ExplorePageHeader |
| Hero: Discover pill, headline, 3-line body, Explore All pill, bubble bleed | shipped | copy matches; art `GSExploreHero` / `gs_explore_hero` |
| Popular Tools 2×2, See All | shipped | — |
| Voice Assistant = waveform | shipped | iOS draws `GsIcons.audioWaveform` for catalog id `waveform` (was `chart-bar`); Android unchanged |
| Video Generator = filled purple play triangle | shipped | iOS `toolCardHorizontal` now passes `playGlyph` (was the outline font glyph) |
| Creative Collections + **See All** | shipped | both headers pass the action; art keyed by stable id |
| Featured carousel, glass bubble next control | shipped | 3 journeys (Kyoto, Studio Bloom, Lotus Lesson) on already-shipping art; TabView(.page) / HorizontalPager |
| Featured **page dots** | **not in the mock** | measured: no dots anywhere between card and dock — the earlier audit claim was wrong; none shipped |
| Floating dock: home circle / Explore peach pill / settings circle | shipped | `ExploreDock.swift` + `ExploreDock` in `ExploreScreen.kt`; ADR-008; blur-free `floatingControl` glass |
| Pearlescent bubble backdrop | shipped | `GSExploreBackdrop`, dark-mode fallback both platforms |
| Hero headline scale | shipped — **no change needed** | mock measures 16.3 pt caps at 24.8 pt pitch; the built 17.5 pt is inside it. The suspected "much smaller" gap did not survive pixel measurement |

## Measured numbers (the pixel contract)

`shared-contracts/explore-geometry.json`, run by
`scripts/check_explore_render.py` in both workflows (plus the gate's
doctoring self-test, which is what caught the Featured-glow contamination
of the rim band — floor 249.5, not the page level):

| probe | android | ios | band |
|---|---|---|---|
| dock capsule hairline | 253.16 | 252.88 | [249.5, 254.5] |
| dock pill luma | 164.66 | 161.69 | [135, 205] |
| dock pill saturation (R−B) | 109.14 | 79.16 | [55, 140] |

## Known differences, on purpose

- **Typeface**: the mock's face is not Poppins; app-wide frozen debt,
  same as `HOME_REFERENCE_STATUS.md` records — no per-page decision here.
- **Status bar 9:41**: render harnesses capture without system chrome.
- **Vertical rhythm**: the mock fits everything above the dock at ~891 pt
  of stretched canvas; a real 852 pt screen scrolls, with 96 pt of
  clearance so the Featured card clears the capsule.
- **Capsule interior tint** is reported, not gated (position-dependent
  numbers over a varying bloom — needs the home gate's material law
  treatment first; see `_not_gated_yet` in the contract).
- **Dark mode**: the bubble backdrop is a light-mode artifact on both
  platforms (gradient + pearls fallback); ExploreGlass carries Android's
  0.85/0.74 dark alphas on iOS too; the dock pill keeps its fixed
  deep-brown ink in either scheme, per Home's Generate pill.
