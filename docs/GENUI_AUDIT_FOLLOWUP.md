# GenUI audit follow-up — 2026-11-08

This is another corrective pass, not a declaration that the full device audit is
finished. Platform notification tests from PR #26 did not prove model-generated
widget correctness.

## Reproductions and corrections

- Web widget presentation lacked a post-JSON-decoding boundary. An escaped
  Unicode emoji survived raw-text sanitation and reached plain widget labels.
  The shared widget entry now sanitizes decoded presentation recursively.
- Native sanitizers already processed decoded JSON, but their hand-written
  Unicode ranges missed emoji-property symbols and subdivision flag tags.
  They now use Unicode emoji properties as well as the existing mapping, and
  share one policy for titles, plain labels and icon-name fields.
- Presentation sanitation previously also cleaned machine identifiers. The
  consolidated policy preserves keys/IDs, expressions, source, regexes, URLs
  and submitted prompts exactly. Titles use glass icon tokens; plain text
  controls strip tokens they cannot render. No source payload mutation.
- Percentage output guessed whether a number was a ratio from its magnitude:
  0.99 -> 99%, 1 -> 100%, 1.01 -> 1.01%. Across all three clients, percent/pct
  now always takes a ratio, including values greater than one and negatives.
  A '%' unit with number format means percentage points. Scaffold prompts teach
  this contract explicitly.
- Native charts had a separate zero-baseline defect: Android replaced zero
  with one, while iOS reported zero-percent growth. Both now show undefined
  growth with a neutral state instead of invented percentages or trend arrows.
- The widget prompt's loan example referenced undefined r and n. Its replacement
  defines every field used by its expression, labels its assumptions and tells
  the model never to copy those example values as real user inputs.
- Web calculators had sliders without direct number entry, guessed 0–100
  bounds, stale state on changed payloads and a conditional-hook-order hazard.
  Native calculators also guessed slider bounds. Inputs now accept actual typed
  numbers; sliders only appear for supplied valid bounds. Missing/cleared or
  out-of-domain inputs are invalid, not invented zero. Payload changes reset
  calculator state. iOS no longer rounds the user's input text to two decimals.
- Native response-style scaffolds teach useful source-backed spontaneous tools,
  explicitly labeled assumptions and complete calculator variable definitions.
  The lean system operator contract was not expanded into application logic.

## Evidence

Local: 64 behavioral interaction checks; existing renderer 170, catalogue 208,
pipeline 24 and compiler 146 checks pass. Web CI 42405021335 passed. Android
CI 42405021263 passed all four mandatory device cases before the additional
zero-baseline chart correction; its successor must establish the final source.
The iOS final-source runtime result is still pending. Swift static/structure checks and the runtime-result
checker self-test pass. These local checks do not substitute for native runtime.

Added mandatory device cases:
- Android: parse an escaped-emoji calculator, mount the real CalcWidgetView,
  type 12.3456, observe the calculated result, clear, observe invalid input,
  reset, and verify no invented slider or raw emoji text.
- iOS: the same path through WidgetBlockView, actual XCUITest keyboard entry,
  exact draft preservation, clear/reset, and screenshot attachment.
- Native unit checks cover percent boundaries and presentation/machine-data
  separation. CI must establish their actual execution; no skip counts as pass.

## Delivery and remaining work

`download/update-manifest.json` still points to code 148 / v0.86.25, built from
old e3bc50c2. It is NOT updated by these source changes. A replacement installable
release and deployed backend are not produced here.

Still outstanding: exhaustive all-widget semantics, native sandbox interaction
and containment, visual comparison to every supplied reference, actual deployed
model behavior across providers, and release/device acceptance. Unsupported
Python/SQL/React/TypeScript execution remains explicitly unsupported rather than
simulated. No blanket 'all resolved' claim is warranted.
