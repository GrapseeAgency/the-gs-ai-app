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

## What matches

| quantity | sheet | Android | iOS |
|---|---|---|---|
| suggestions Δtint (card − page) | −2.79 | −2.93 | −3.22 |
| suggestions rim (hairline − glass) | +2.58 | +1.54 | +2.18 |
| suggestions hairline, absolute L | 241.09 | 239.06 | 240.91 |
| bento material-law residual | +0.12 | +0.93 | −0.03 |
| bento rim (hairline − glass) | +19.94 | +18.96 | +17.03 |
| bento hairline, absolute L | 252.00 | 252.14 | 252.35 |
| bento left edge, pt from frame | 16.76¹ | 20.00 | 20.00 |
| pill material-law residual | +0.16 | +0.94 | −0.62 |
| pill rim (hairline − glass) | +11.82 | +14.31 | +12.53 |
| pill hairline, absolute L | 252.43 | 253.21 | 253.57 |

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
