# Native AI Activity / Thinking Orb System (Phase 4)

The shared, cross-platform specification for GS's activity-orb visual language.
Extracted from **thinking-orbs@0.3.1** (Jakub Antalik, MIT) — the *specification*
only, never the React/canvas package. Both native implementations are verified
against the same golden vectors, so they cannot drift.

- Android: `android/.../ui/orbs/` (Compose `Canvas`, one shared epoch clock)
- iOS: `ios/App/Sources/Orbs/` (SwiftUI `Canvas` + `TimelineView(.animation)`)
- Upstream vendored spec: `docs/orbs/orbs-spec.json` (specVersion 1.0.0)
- Golden vectors: `docs/orbs/orbs-golden-subset.json` (9 states × 2 sizes × 4
  timestamps; dot counts + first-3/last-2 draw-order dots, tolerance 1e-4)
- Regenerate constants + fixtures: `node scripts/gen-orb-spec.mjs` (emits
  `OrbSpec.kt`, `OrbSpec.swift`, `OrbGoldenData.kt`, `OrbGoldenData.swift`)

## The rule that governs everything

> The orb is a picture of what the app is REALLY doing.
> The mapping is application state → orb state, never the reverse.

## State catalogue (9 states, 9 distinct geometries)

| OrbState | Mode (geometry) | Label | Motion signature |
|---|---|---|---|
| working | orbits | Working… | particles on tilted orbits, ghost paths |
| searching | globe | Searching… | scan meridian sweeps a dotted globe |
| solving | rubik | Solving… | bands scramble, click back solved (palindrome) |
| listening | wave | Listening… | two-tempi waveform rolls through the rings |
| connecting | web | Connecting… | constellation wires itself + signal packets |
| weaving | braid | Weaving… | three strands plait pole-to-pole |
| composing | ribbon | Composing… | undulating multi-band sash (frozen tumble) |
| breathing | ring | Thinking… | face-on ring, radius slowly undulates |
| shaping | morph | Shaping… | dotted outline: circle → triangle → square |

## Size system (two purpose-tuned designs — never a scale factor)

| OrbSize | px | Role |
|---|---|---|
| INLINE | 20 | message-status / inline activity |
| STANDARD | 64 | assistant identity / active generation |

Voice/full-screen may reuse STANDARD; no other sizes exist by design.

## Visual contract (monochrome, always)

- Ink mirrors the theme: dark canvas → light ink, light canvas → dark ink.
- Ink value per dot: `g = round((dark ? 1−white : white) × 255)`, alpha per dot.
- Plain source-over fills; no blur, no filters, no blend modes, no colour.
- Identity comes from **shape + motion + density + geometry** — never colour.
- Semantic status hues (error/success) are outside the orb system entirely.

## Timing / clock

- `t = elapsedSeconds × presetSpeed × userSpeed` (per-state baked speed).
- ONE shared epoch per process: all same-speed orbs stay in phase.
- Reduced motion: ONE static representative frame at **t = 0.6** — identity
  survives through shape; the orb is never hidden.
- Paused/backgrounded/offscreen: the frame clock stops entirely (Compose:
  composition-disposal + lifecycle observer; SwiftUI: TimelineView's own
  offscreen/background pausing). Nothing ticks invisibly.

## Performance contract

- Geometry is pure math into a **preallocated buffer** — the per-frame path
  allocates nothing (no per-dot objects, no collections).
- The frame time is read **inside the draw phase only** — frame updates
  invalidate drawing, never recomposition (Android) / no view diffing (iOS).
- Measured upstream geometry cost: ≤ 0.12 ms/frame for the densest mode
  (~590 dots); painting is plain circle fills z-sorted far→near.
- No blur, no filters, no image decoding, no Lottie, no web view, no Skia.

## Honest state mapping (the product layer)

CURRENT MAPPINGS — the only real activity states the app has:

| Application state | OrbState | Where |
|---|---|---|
| Chat streaming, buffer still empty (request with the model, no token yet) | breathing ("Thinking…") | chat: composer life-sign + live bubble |
| Chat streaming, tokens flowing | composing ("Composing…") | chat: composer life-sign + live bubble |
| Stream Finalizing/Done/Cancelled | none | chat |
| Voice recognizer live | listening | voice status |
| Voice recognizer processing | working | voice status |
| Voice idle/result/paused/error/permission | none | voice |
| Fresh workspace (no messages) | breathing (identity, "GS assistant") | chat empty state |

DELIBERATELY UNMAPPED — available in the catalogue, no real app state yet:
`searching` (no tool/retrieval phase in the chat pipeline), `solving` (no
distinct reasoning phase), `connecting` (no connection handshake), `weaving`,
`shaping` (no artifact-assembly state). When the product grows the real
behaviour, map it in the mapping layer — never at the call site.

- Android mapping: `ui/orbs/OrbStateMapping.kt`
- iOS mapping: `OrbStateMapping` helpers in `ThinkingOrbView.swift`

## Accessibility

- Every orb exposes a per-state label ("Working…", "Listening…", …) as its
  accessibility description (Compose `contentDescription` with
  `Role.Image`; SwiftUI `accessibilityLabel` + `.isImage`).
- The label is the state's honest name — the animation is never the only
  indication of state.
- The empty-workspace identity orb reads "GS assistant" (presence, not task).
- Reduced motion renders the static t = 0.6 frame; it never hides the orb.

## Anti-drift

`scripts/gen-orb-spec.mjs` regenerates the platform constants from the
vendored spec; both platforms' unit suites assert the SAME 72 golden frames
(11,288-dot parity upstream, 1e-4). A tuning change upstream means:
vendor the new spec → regenerate → both golden suites must be green together.
