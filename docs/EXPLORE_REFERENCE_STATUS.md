# EXPLORE + DISCOVER — reference status

The parity contract for the Explore page is the user's production mock,
committed rectified: `docs/reference/explore/explore-home-reference.png`
(the phone-screen interior cropped from the 1024×1536 mock; crop box and
probe-space note in the sidecar JSON). The Discover page's contract is the
second mock, committed the same way: `discover-home-reference.png`
(crop 181-763 × 20-1585 of its 941×1671 canvas). This page records what the
shipped iOS and Android builds measure against them, what intentionally
differs, and what the CI gate now enforces.

Audit date: 2026-10-25 (commits `57a51938`…`c48aa78f`, verified against
the CI renders of `c48aa78f`: runs 40177639269 android-app, 40177187833
ios.yml). Discover pass: commits `e1f37058`/`d8b37ec9`/`ba22f169`, graded
from the first CI renders of `d8b37ec9` (runs 40181861869, 40181847570).

## Explore vs Discover — what the two pages are

They are two surfaces, not two names for one screen. **Explore** is the
curated showcase reached from Home's assistant orb: hero → Popular Tools →
Creative Collections → the Featured journey carousel, with the floating dock
at its foot. **Discover** is the full catalogue browser reached from
Explore's "See All": the "Discover what you can do with Nexly" hero, the
category chips that filter the page, the mock's own four Featured Tools,
the Popular prompt rows, and the "Ask me anything…" composer that
auto-sends into a fresh chat. Each has its own mock, copy and cards.

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

## The Discover page against its mock

| Element | Status |
|---|---|
| Header, hero card (EXPLORE eyebrow → Start Exploring pill, bubble bleed) | shipped — copy matches the mock line for line |
| Category chips — All/Productivity/Creativity/Learning/Lifestyle, peach active | shipped; inactive chips are translucent glass (the first iOS attempt rendered them pitch black — a shape-as-view's default fill sits ON TOP of its own glass background; fixed on render evidence) |
| Featured Tools + "See all" (lowercase a — the mock writes it differently from Explore's "See All") | shipped; opens the search field, the page's honest whole-catalogue gesture |
| Featured Tools cards — the mock's OWN four: Image Studio (sparkles/apricot), Voice (lavender), Writing Assistant + Code Helper (vermilion, new sampled token) | shipped as `discoverFeatured`, separate from the Explore catalog so neither mock drifts the other |
| Arrow in a glass well beside the subtitle | shipped both platforms (22pt, subtitle shrinks before ellipsizing) |
| Popular rows + "See all", subtitles with their periods | shipped; Android's `school` id now resolves GraduationCap — it was painting a schoolhouse where the mock paints a mortarboard |
| Composer — sparkles in a white well, "Ask me anything…", white paper plane | shipped |

## The polish pass (2026-10-25, commits 5935b3bc…04ae8e94)

- **Hero CTAs are flat fills, not gradients**: column medians of the committed
  crops read #FEE7E0 (Explore "Explore All") and a paler pearl #FEF4E6
  (Discover "Start Exploring") — both shipped as `ctaPink`/`ctaPearl` tokens
  on both platforms.
- **The Featured bubble measured back as already correct**: ~28pt sphere,
  8pt inside the card edge, vertically centred (the 89pt mask hit the sakura
  art). The shipped 30pt/8pt stays; this closes the claim, not a defect.
- **Discover draws its own hero art**: `gs_discover_hero` is cropped from the
  Discover mock itself (purple bloom over the orange sphere). Its left 148px
  carried the mock's headline tail — mirror-patched and blurred, which is
  veil territory. Registered in `prepare_home_art` with the 3.7x draw
  declared; byte-identical on both platforms.
- **iOS hero layering was a real bug the renders caught**: the chain ran
  ExploreGlass BEFORE the art background, so the 90%-opaque fill painted over
  the bubbles — both heroes read washed-out on iOS while Compose drew them
  correctly. Order swapped (art directly behind the text, glass behind the
  art); the post-fix renders show the mock's saturation on both platforms.

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

- **Typeface**: the shipped family is Inter Tight, measured +0.011 off the
  mock's width — the only candidate inside the acceptance band (see
  `TYPOGRAPHY.md`). The mock's x-height (x/cap 0.850) is unreachable in the
  OFL pool, so a few line wraps still differ; closing that is a licensing
  decision, not a code gap.
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
- **`discoverFeatured` is a deliberate fork** of the shared catalog, not a
  duplicate to be merged: the two mocks give the same concepts different
  copy, icons and tones (Explore's Image Studio is "Create stunning images
  with AI" with the photo glyph; Discover's is "Turn ideas into stunning
  visuals with AI." with sparkles). Search and Popular rows still read
  `tools`; a copy edit to a shared concept must decide which mock owns it.
