# Home against the reference — measured status

Every number here came from `scripts/check_home_render.py` on the three captures it
gates: the reference sheet, the Android render from `HomeScreenshotTest`, and the
iOS render from the `ios.yml` harness. Nothing in this file is an impression; if a
number moves, the gate that produced it either moves with it or fails.

Re-measure with:

```sh
python3 scripts/check_home_render.py --render android/app/build/screenshots/home-393x852.png \
  --probe-set android --label android
python3 scripts/check_home_render.py --render ios/build/home-renders/home-ios-393x852.png \
  --probe-set ios --label ios
python3 scripts/prepare_home_art.py --check      # artwork: resolution, parity, budget
python3 scripts/test_home_render_gate.py --render <capture> --probe-set <kind>
```

A layout change moves the probes. When a rim or hairline check fails, the gate prints
where the nearest edge actually is — the brightest row, and the largest positive step
in the column median, each against the `rim_y` the probe carries — so re-deriving a
rect is a copy from the log rather than a search.

## What matches

| quantity | sheet | Android | iOS |
|---|---|---|---|
| suggestions Δtint (card − page) | −2.79 | −2.86 | −3.93 |
| suggestions rim (hairline − glass) | +2.58 | +1.72 | +2.81 |
| suggestions hairline, absolute L | 241.09 | 238.98 | 240.91 |
| bento material-law residual | +0.12 | +0.78 | +0.23 |
| bento rim (hairline − glass) | +19.94 | +19.71 | +17.46 |
| bento hairline, absolute L | 252.00 | 252.14 | 252.14 |
| bento left edge, pt from frame | 16.76¹ | 20.00 | 20.00 |
| pill material-law residual | +0.16 | +0.80 | −0.08 |
| pill rim (hairline − glass) | +11.82 | +15.03 | +12.91 |
| pill hairline, absolute L | 252.43 | 253.21 | 253.21 |

¹ printed, not gated: the mock's crop is not rectified, so its absolute x carries
the crop. The value a device must hit is the 20 pt margin token, and both do.

The iOS bento's rim *excess* is the one cell visibly below the sheet's, and it is
the ground rather than the rim: the same hairline measures 252.0 / 252.1 / 252.4 on
the three frames, while the glass under it is 230.8 / 232.7 / 234.5 — the iPhone's
59 pt safe area puts that card on a brighter band of the bloom. A9 gates the
absolute level for exactly this reason.

Also verified on every run: the shipped backdrop is the reference's own atmosphere
(left margin, median |Δ| 0.04 L on the sheet); the artwork is one authored source,
one resolution, identical bytes on both platforms, inside a decoded budget; Home's
twelve type roles and its glass corners agree across platforms; and the gate itself
is proven able to fail by `test_home_render_gate.py`, which doctors the capture
eight ways (flat page, cards painted out, hairline erased, frame shifted, probes
slipped both ways, backdrop dimmed) and requires each to be caught.

## Type measured against the sheet, not against each other

The type contract was derived from the two platforms, so it can only ever prove they
agree. These came from measuring the same string's glyph band on the sheet and on the
render and scaling the size by the ratio — the one comparison a different typeface
cannot distort, because it is the same string at the same rendering.

| role | sheet band | ours, before | ours, now |
|---|---|---|---|
| greeting line 1 | 28.5 pt | 27.3 at 26 | 26 kept (0.7 pt cap apart) |
| greeting subtitle | 11.5 pt, 17.6 pt pitch | 15.3 at 15/22 | 12.7, 17.7 pitch (**12.5/18**) |
| "Today's Used" | 15.78 pt | 19.00 at 18 | **16.00 at 15** |
| "Nexly" | 12.14 pt | 13.33 at 18 | 16.5 (see below) |
| video chip | 7.21 pt | 11.0 (13 dp icon) | 7.67 (10 sp, 8 dp icon) |
| card headline | 16 pt, same line break | 16 | 16 kept |
| card body | 12 pt, two lines | 12, three lines | unchanged — the face, not the size |

Three of these were 20% or more off the picture while both platforms agreed with each
other. That is the failure mode this table exists to catch: the contract cannot see it,
because the contract is a mirror.

One difference is spacing, not size, and is left open: the sheet has a 9.7 pt gap
between "Nexly" and "AI Companion" and ours has none — the two lines merge into a
single glyph band at every threshold. Closing it moves everything below, so it wants
its own pass with the probes re-derived around it.

## The video card's internals, measured against the sheet

The pixel gate measures glass at three probes; it does not see inside a card. These
came from cropping the sheet's card and ours to the same 187x214 pt box, viewing them
side by side at the same scale, and then measuring what the eye caught.

| element | sheet | ours | how it was measured |
|---|---|---|---|
| chip pill contents | 7.21 pt tall | 7.67 pt | dark pixels over the card; icon and label are the same height in the sheet |
| play disc | 37.0 pt | 37.0 pt | brightest-blob extent in the card's bottom-left |
| play glyph | 9.2 x 11.5 pt, solid (65% ink) | 8.7 x 11.0 pt, solid (51% ink) | dark bbox inside the disc; a filled triangle is 50% of its box by area |
| headline | 16 pt semibold | 16 pt semibold | cap-height bands; same line break after "Create Stunning" |
| body copy | 12 pt, two lines | 12 pt, three lines | see below |

The body wraps to three lines on Android because Poppins needs ~155 pt for "beautiful
videos in minutes." where the column is 131 pt; the sheet's narrower face fits it.
Forcing two lines would mean ~10 pt type, which trades a wrong size for a wrong face.

The play control was a 44 dp disc around an outline glyph on Android and SF's solid
`play` in the same 44 pt disc on iOS — 7 pt over the sheet on both, and disagreeing
with each other. It is now a 37 pt disc with a drawn filled triangle on both.

## What does not match, and why it is not a code change

**The reference is not set in Poppins.** At matched cap height "Good morning,"
spans 159 pt in the mock, 171 pt in SF Pro and 193 pt in Poppins, and the mock's
`g` is double-storey where Poppins' is single. Poppins is the product's declared
family and the only face bundled for both platforms, so it is what the parity
mandate can satisfy — but the letterforms are not the mock's. Deciding otherwise
means choosing and licensing another family for both platforms.

**The video card's artwork is the right kind of picture, shipped upside down to be
less wrong.** Its saturation and lightness match the reference (chroma mean 22.1
against 20.5 over the whole card, 30.1 against 29.2 over the art's own region), and
the art region's chroma map correlates with the reference's at **r = +0.604** on
Android and **+0.616** on iOS with the orientation we ship, against **+0.372** with
the same art the other way up.

That 0.604 is a placement story only in the weak sense: it is the best any transform
of our asset reaches, and it is short of the ~0.85 that would mean "the same picture
placed differently". The reference's colour mass peaks at y 167 of the card's 214,
ours at y 63.

The history of this number is worth keeping, because two earlier answers were wrong
in instructive ways. A sub-window match reported r = +0.694 for a flip — matching a
blob. A search over eight dihedral transforms, seventeen scales and twenty-five
offsets reported r ≤ 0.108 for everything — but it forced the art to cover the whole
card, and this card is art in the bottom-right corner under a horizontal scrim, so it
measured the model. And a *retraction* of the flip was based on the card's mean
chroma, p90 and luminance, which move by 0.0 between the two orientations because they
cannot see arrangement. Only the correlation between real captures and the reference,
restricted to the art region, ranks the options — and it says: keep the flip.

**Three dead imagesets ship on iOS.** `GSVideoCreate`, `GSHomeOrb` and
`GSHomeLotus` are referenced by no source; Android's resource shrinking strips the
drawables from the APK, but actool compiles every imageset into `Assets.car`, so
16.6 MB decoded ships for nothing. They are named and costed in
`scripts/prepare_home_art.py`'s allow-list rather than removed, because this
workspace's file tooling reports success on deleting a PNG and leaves the file —
and a half-deleted imageset is a broken build.
