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

Measured with `PIL.ImageFont` at matched cap height, over the string the reference sets
largest — "Good morning," at 26 pt:

| face | width at 26 pt | vs Poppins |
|---|---|---|
| the reference's own face | 159.0 pt | 0.824 |
| **Poppins SemiBold (shipped)** | **193.5 pt** | **1.000** |
| Liberation Sans (Arial metrics) | 172.0 pt | 0.885 |
| Adwaita Sans / Inter | 182.8 pt | 0.889 |
| Fira Sans | 174.3 pt | 0.901 |

Consequences of that 22 %: the video card's body sets **three lines where the reference
sets two** ("beautiful videos in minutes." needs 164 pt at 12 pt and the column is
131), and the headline's strokes read heavier at the same point size. Every other
difference in the type table has been closed by size; this one cannot be.

The reference's letterforms were re-checked at 6× after this file's first draft claimed a
double-storey `g`: it is **single-storey**, with round geometric bowls — Poppins' shape,
narrower. So the face to source is a *narrow geometric grotesque*, not a neo-grotesque.

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
