# Typography

## What ships

**Inter Tight** (SIL Open Font License 1.1), four weights — 400 / 500 / 600 /
700 — as static files, identical bytes on both platforms. It replaced Poppins
(commit "type: vendor Inter Tight as the product family"); the swap story and
the italic-cut near-miss are under "The face the app ships", below.

| | Android | iOS |
|---|---|---|
| files | `app/src/main/res/font/inter_tight_{regular,medium,semibold,bold}.ttf` | `App/Resources/InterTight-*.ttf` |
| family | `GsFonts.Product` (`ui/theme/GsFonts.kt`) | `UIAppFonts` in `project.yml`, resolved by `Aero.productName(_:)` |
| fallback | — | `Font.system` when the face is missing |

Sizes, weights and tracking are not per-file decisions: they live in
`GsTypography` (Android) and `Aero` (iOS) and are held together by
`.github/scripts/check_home_type_parity.py` against
`shared-contracts/home-type-roles.json` — 15 Home roles, both platforms,
failing on any drift. Monospace stays on the platform face; code must look
like code.

## The measured table, re-run upright (2026-10-25, run 40199378450)

The correction that was owed: the first table was measured on italic cuts and
never re-run because the eval's denominator pointed at the Poppins file the
vendoring had deleted. The eval now fetches Poppins itself as the denominator
(it is no longer shipped), italics are excluded, and this is the honest
upright re-measurement — width of "Good morning," at matched cap height
relative to Poppins SemiBold, and x-height/cap:

| face | width ÷ Poppins | x-height ÷ cap | vs reference (0.824 / 0.850) |
|---|---|---|---|
| the reference's own face | 0.824 | 0.850 | — |
| Poppins SemiBold (baseline) | 1.000 | 0.818 | +0.176 wide |
| **Inter Tight (shipped)** | **0.835** | 0.777 | **+0.011 wide** — the only face inside the band |
| Hanken Grotesk | 0.866 | 0.732 | +0.042 wide |
| Outfit | 0.870 | 0.718 | +0.046 wide |
| Figtree | 0.879 | 0.750 | +0.055 wide |
| Manrope | 0.885 | 0.792 | +0.061 wide |
| DM Sans | 0.893 | 0.786 | +0.069 wide |
| Onest | 0.897 | 0.771 | +0.073 wide |
| Karla | 0.979 | 0.799 | +0.155 wide |

(Bricolage Grotesk was requested and never fetched — the google/fonts tree
names it under `ofl/bricolagegrotesk` with multi-axis files; it stays
unmeasured, not rejected.)

Two verdicts, stated as numbers:

1. **The width decision is settled and was right.** Inter Tight lands +0.011
   off the reference's width where Poppins sat +0.176 — the only candidate
   inside the 0.80–0.85 acceptance band, and the reason line wraps now match
   the mock far more often.
2. **The x-height gate is unreachable, and that is the residual gap.** The
   reference's face carries x/cap 0.850; not one candidate in the OFL pool
   clears even the gate's own 0.82 floor — the highest readings are Poppins
   0.818 and Karla 0.799, Inter Tight 0.777. So the gate prints "x-height"
   for every row and must be read as: *no free geometric sans has the
   reference's proportions.* Closing that fully means commissioning or
   licensing a non-OFL face — a product decision, not a CI failure. The gate
   stays as written; a candidate that clears it would be news, not noise.

## Accepting a replacement face

A candidate ships only if all of these hold:

1. **Width ratio 0.80–0.85** of Poppins at matched cap height, measured the same way
   (see the table above). Outside that band it is a different error, not a smaller one.
2. **Single-storey `g`**, round bowls, uniform stroke.
3. **Four static weights** (400/500/600/700). A variable-only font such as Adwaita Sans
   cannot be bundled without instance generation on both platforms.
4. **OFL or Apache-2.0**, with the licence text committed beside the files.
5. Both platforms already resolve sizes from the contract; the swap must not change any
   number in `shared-contracts/home-type-roles.json` — only the family.

Candidates measured and **rejected** so far: Liberation Sans (no semibold; Bold is 0.959),
Adwaita Sans (variable font, 0.889), Fira Sans (humanist terminals and a different `a`;
0.901, so it trades shape fidelity for width and reaches 10 % instead of 22 %).

## Swapping the family (one commit)

1. Drop the four TTFs into `android/app/src/main/res/font/` (lowercase, underscores) and
   `ios/App/Resources/`.
2. `GsFonts.Product` → the new `FontFamily`; `Aero.productName(_:)` → the new PostScript
   names; the fallback probe likewise.
3. Add the four files to `UIAppFonts` in `ios/project.yml`.
4. Run `python3 .github/scripts/check_home_type_parity.py` — it must stay green with no
   contract edits.
5. Push and read the CI captures. The pixel gate measures glass and does not care; the
   type table in `docs/HOME_REFERENCE_STATUS.md` does, so re-measure the bands there.
6. If the render is worse, `git revert` the one commit — the family is a single token on
   each platform precisely so this stays cheap.

## The face the app ships

`InterTight-{Regular,Medium,SemiBold,Bold}`, 400/500/600/700, vendored by
`.github/workflows/font-vendor.yml` from `ofl/intertight` and instanced from the
**upright** variable font.

The first vendoring took the italic cut. `ofl/intertight` ships

- `InterTight[wght].ttf`
- `InterTight-Italic[wght].ttf`

and the step selected "the first `.ttf`", which was the second one. `varLib.instancer`
keeps the source's name table, so the four files announced themselves as
`InterTight-Italic`; the job then rewrote name IDs 1/2/4/6/16/17/21/22 and `OS/2`, which
made iOS resolve `InterTight-Regular` and draw it. Registration tests passed, the 15-role
type contract passed, both pixel gates passed, and **every glyph on Home was slanted**
(`italicAngle` −9.40 in all four files). It was found by cropping the video card and
looking at it.

Three things now stand in the way of a repeat:

1. the vendor and eval steps exclude any filename containing `italic`;
2. the vendor job reads `italicAngle` back after instancing and refuses a non-zero one;
3. `scripts/check_font_faces.py` runs in `android-app`, `ios` and the vendor job, and
   asserts for every shipped weight: `italicAngle` ≈ 0, the `OS/2` italic bit clear, the
   subfamily matching `usWeightClass`, and byte-identical files on both platforms.

A capture can show a slanted word; no gate we had could say so. That is why the font
contract exists now, and why it reads the font rather than the screenshot.
