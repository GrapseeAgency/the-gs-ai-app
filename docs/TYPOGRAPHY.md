# Typography

## What ships

**Poppins** (SIL Open Font License 1.1), four weights — 400 / 500 / 600 / 700 — as static
files, identical bytes on both platforms:

| | Android | iOS |
|---|---|---|
| files | `app/src/main/res/font/poppins_{regular,medium,semibold,bold}.ttf` | `App/Resources/Poppins-{Regular,Medium,SemiBold,Bold}.ttf` |
| family | `GsFonts.Poppins` (`ui/theme/GsFonts.kt`) | `UIAppFonts` in `project.yml`, resolved by `Aero.poppinsName(_:)` |
| fallback | — | `Font.system` when the face is missing (`Aero.poppinsLoaded`) |

Sizes, weights and tracking are not per-file decisions: they live in `GsTypography`
(Android) and `Aero` (iOS) and are held together by
`.github/scripts/check_home_type_parity.py` against `shared-contracts/home-type-roles.json`
— 15 Home roles, both platforms, failing on any drift. Monospace stays on the platform
face; code must look like code.

## The known gap: Poppins is wider than the reference

Measured in CI (`.github/workflows/font-eval.yml`) at matched cap height, over the
string the reference sets largest — "Good morning," at 26 pt. Every number is from the
same script, on the same runner, so they are comparable to each other:

| face | width ÷ Poppins | x-height ÷ cap | vs reference (0.824 / 0.850) |
|---|---|---|---|
| the reference's own face | 0.824 | 0.850 | — |
| **Poppins SemiBold (shipped)** | **1.000** | 0.826 | +0.176 wide, −0.024 x-height |
| **Inter Tight** | **0.844** | 0.777 | **+0.020 wide**, −0.073 x-height |
| Manrope | 0.893 | 0.792 | +0.069, −0.058 |
| Outfit | 0.879 | 0.718 | +0.055, −0.132 |
| Figtree | 0.883 | 0.746 | +0.059, −0.104 |
| Hanken Grotesk | 0.877 | 0.728 | +0.053, −0.122 |
| DM Sans | 0.900 | 0.786 | +0.076, −0.064 |

Scored with width weighted double because line breaks are what the eye reads first,
**Inter Tight** is the clear winner — 0.020 off the reference's width against Poppins'
0.176. Three claims made earlier from prose, not measurement, died here: Manrope is not
semi-condensed (0.893), Outfit is not narrow (0.879), and Poppins' own x-height is 0.826,
not the 0.804 I had recorded, which puts the reference only 0.024 above it.

Consequences of the shipped gap: the video card's body sets **three lines where the
reference sets two** ("beautiful videos in minutes." needs 164 pt at 12 pt in a 131 pt
column), and the headline's strokes read heavier at the same point size.

The reference's letterforms were re-checked at 6× after an earlier draft claimed a
double-storey `g`: it is **single-storey**, with round geometric bowls — Poppins' shape,
narrower. Inter Tight is single-storey too, which is why it can substitute without the
letterforms changing family.

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
2. `GsFonts.Poppins` → the new `FontFamily`; `Aero.poppinsName(_:)` → the new PostScript
   names; `Aero.poppinsLoaded` → a probe on one of them.
3. Add the four files to `UIAppFonts` in `ios/project.yml`.
4. Run `python3 .github/scripts/check_home_type_parity.py` — it must stay green with no
   contract edits.
5. Push and read the CI captures. The pixel gate measures glass and does not care; the
   type table in `docs/HOME_REFERENCE_STATUS.md` does, so re-measure the bands there.
6. If the render is worse, `git revert` the one commit — the family is a single token on
   each platform precisely so this stays cheap.
