# GenUI behavior audit — 2026-11-07

This is a corrective audit, **not** a statement that every device surface is complete.
Earlier compiler/static-green reports did not prove behavioral correctness.

## Confirmed defects corrected in this patch

- SQL code cards on Web, Android and iOS manufactured people, orders, values,
  dates and success summaries without a database. They now disclose that no
  database is connected and return no invented results.
- Native React previews inferred a counter from source and mapped arbitrary
  buttons to increment/decrement, or displayed synthetic HTTP 200/abort events.
  Those previews are removed. No React compiler is claimed. Android's existing
  JavaScript/HTML WebView sandbox remains the real execution path.
- Python previews skipped unsupported statements, substituted missing lists or
  variables, and reported successful script execution. The supported arithmetic
  subset now stops on unsupported syntax and is explicitly labeled **not a
  Python runtime**. No loops, imports, libraries or SQL engines are simulated.
- Web JavaScript ran inside the application window and replaced timers with
  immediate callbacks. It now runs in an opaque-origin sandbox iframe using a
  disposable worker, network-denying CSP, output cap and two-second watchdog.
  Async work must be awaited; pending background work stops at completion.
- Native automatic calculators used arbitrary nearby numbers or defaulted to
  1200, 10%, and invented loan terms. Generation now requires a single explicit
  currency amount/rate and explicit term for term-based calculations. Ambiguous
  health, margin, budget and ROI contexts do not get a guessed generic tool.
  Changing financial inputs into weight by cycling modes is no longer possible.
- Expression parsers accepted trailing garbage or converted invalid arithmetic
  to zero. Complete parsing, NaN/error handling and exponent precedence are fixed.
- Native quantitative explorers no longer sum mixed-unit or percentage lists.
- Web table “All” previously stopped at 25; it now includes the complete filtered
  dataset. The empty-filter footer starts at zero, not one.
- Web chart “Last 3M”/“YTD” previously selected arbitrary point counts. Explicit
  ISO dates now use calendar windows; undated categories use honest point-count
  labels. Zero-baseline growth is undefined, not computed from an invented 1.
- Web concept decks now derive flashcards and quiz answers from actual explicit
  term/definition pairs in the completed response, without fabricated facts.
- Web title icon tokens render through NexlyRichText; check/cross/camera chrome
  and Android trace-status glyphs use the shared glass icon registry.

## Repeatable evidence

`npm run test:interactions` compiles the renderer probe and runs 46 assertions:
real DOM clicks for card flips, quiz scoring, calculator preset/reset, SQL
failure honesty, table paging/sorting/filtering/All; arithmetic failure cases;
actual generated JavaScript worker code with awaited timers; icon normalization
and rendering; deterministic calendar-window cases.

Additional existing suites: widget compiler (146), SSR renderer (170), catalogue
(208) and v3 pipeline (24). These are **not** device touch or screenshot proofs.

Native `CalcHonestyTest` / `CalcHonestyTests` exercise the real platform parsers.
Android benchmark CI now runs the targeted JUnit suite before emulator checks;
iOS CI discovers the XCTest file through the existing XcodeGen test source glob.
The Python parity audit now bans the fabricated-execution sentinels instead of
requiring the fake React parser to exist.

## Delivery and remaining limits

- No APK, IPA, or deployed backend is produced by this patch. At audit time the
  download manifest is still v0.86.25 / code 148, built from e3bc50c2. Do not
  assume a device installing that artifact has these source changes.
- Full Python, SQL, React and TypeScript execution is not implemented in these
  native code cards. Unsupported capability must be disclosed, never imitated.
- Web DOM tests do not prove WKWebView/Android WebView CSP and touch behavior.
  Those need on-device tests using a newly built app.
- Exact visual fidelity to all six supplied references, every widget kind's
  interaction semantics, and end-to-end behavior from the deployed model remain
  separate acceptance checks. Static presence/SSR tests cannot certify them.
