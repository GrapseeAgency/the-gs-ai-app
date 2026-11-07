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
  Point tooltips also substituted an average when the previous point was absent
  or zero; they now require a real nonzero prior point. Android uses the actual
  selected series rather than silently comparing to the first series.
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

## Additional defects confirmed in the current pass

- Android and iOS had separate generated-widget and ordinary-Markdown table
  paths. All four still offered 25 instead of All. They now use a distinct All
  selection over the full current dataset, including datasets above 25 rows.
- iOS generated-table descending sort returned true for equal cells. Its strict
  comparator now preserves equality in both directions; unit tests cover
  duplicate numeric/string values and equivalent numeric formats.
- Native inline-code and link renderers restored the original raw string when
  sanitization returned empty. Emoji-only labels therefore bypassed the ban.
  Those fallbacks are removed; empty links receive a safe textual label instead.
- The final widget scaffold section contradicted its canonical fields/key/value
  calculator example by demanding inputs/id/default, forced slider bounds and
  at least 5–8 table/chart rows. It now teaches one schema, meaningful supplied
  bounds and no dataset padding. A regression assertion checks this contract.
- Mandatory Android and iOS generated-table device cases now exercise the actual
  5 -> 10 -> All controls with 31 rows, then repeat through ordinary Markdown.
  Android also exercises filtering while All is selected. These cases must run;
  unit/SSR results alone do not certify them.

## Current follow-up: dynamic HTML and honest chart/poll semantics

The renewed audit found HTML entity and post-load JavaScript emoji bypasses.
One shared display-only DOM policy now runs in the actual Web iframe, Android
WebView and iOS WKWebView, preserving executable source, machine state and
custom vector icons. Native resource loading fails visibly closed. This is not
a hostile-code security boundary; canvas pixels, CSS pseudo-content and shadow
roots are outside its guarantee.

Additional source corrections:
- Android ranges no longer restore short series that have no observations in
  the selected window. Web range counts use the longest actual series.
- iOS charts select rather than concatenate independent series, preserve signed
  totals/domains/zero baselines, stop truncating observations, preserve category
  labels, and reject missing, boolean or nonfinite numeric measurements instead
  of silently fabricating zero. Lone observations have no fake minimum mark.
- Web pie switching preserves all supplied observations and rejects negative or
  nonpositive-total datasets rather than silently dropping signed data.
- Pie slice differences are not labeled as growth. Web metrics explicitly name
  their source series; a generic sum is not presented as revenue or volume.
- iOS polls no longer start with an invented vote per option; invalid vote
  counts are rejected. Web polls show zero actual local votes, add one on a
  real selection, and reset when options change. Both disclose local semantics
  rather than claiming a live audience poll.

## Evidence

Current local checks: **79 interaction checks**, renderer **170**, catalogue
**208**, pipeline **24**, compiler **146**. The interaction suite executes the
actual iframe srcDoc and its button handler, observes later DOM mutations,
checks preserved executable state/vector paths, and clicks real poll/chart
controls. JSDOM checks do not prove rasterized layout or browser containment.
Swift static/structure checks and the runtime-result checker self-test pass;
they are supplementary, not native execution proof.

PR #28 adds a UiDevice tap on the production Android HTML WebView and a hosted
production WKWebView test on iOS. Both check real authored JavaScript, entity
and dynamic emoji removal, preserved state and SVG paths. The CI gates reject
missing/skipped named cases. Android workflow **42420103461** passed on earlier
revision **e4b1963**, including the new mandatory HTML WebView case. Final-source
native CI results must still be established; that earlier pass is not a final
revision acceptance claim.

Historical PR #27 final checks all passed before merge (Android 42411253871,
iOS 42411253869, Web 42411253861, eval 42411253865). Those bounded checks did not
prove the renewed defects absent or establish exhaustive GenUI acceptance.

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
