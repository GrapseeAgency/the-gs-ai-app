# Native AI Activity / Thinking Orb System (Phase 4 → luminous contract)

The shared, cross-platform specification for GS's activity orb. It has **two
layers**, and the split is the whole point of the current revision:

1. **Geometry — how the orb moves.** Nine dot-field animations vendored from
   **thinking-orbs@0.3.1** (Jakub Antalik, MIT), *generated* and locked by the
   golden-vector test. This layer does not change here.
2. **Presentation — how the orb glows.** A **twelve-state luminous contract**
   that lights the geometry with the iridescent material the reference is made
   of: an accent bloom, an aurora wash, a reactive core. Presentation is driven
   *entirely through* the existing state machine — every product phase names an
   existing geometry as its `OrbPhase.geometry`, so the honest motion is reused
   verbatim while the luminous layer is drawn **additively, behind the dots.**

The design review landed on this reconciliation deliberately: keep the
iridescent, luminous material, but do not fork the motion engine to get it. Map
twelve product states onto nine generated geometries and light them from one
place. The golden test stays green because no geometry changes.

- Android: `android/.../ui/orbs/` — `OrbPhase.kt` (presentation), `OrbSpec.kt`
  + `OrbEngine.kt` (geometry), `ThinkingOrb.kt` (render), `OrbStateMapping.kt`
  (honest geometry resolver)
- iOS: `ios/App/Sources/Orbs/` (SwiftUI `Canvas` + `TimelineView(.animation)`)
- Upstream vendored spec: `docs/orbs/orbs-spec.json` (specVersion 1.0.0)
- Golden vectors: `docs/orbs/orbs-golden-subset.json` (9 states × 2 sizes × 4
  timestamps; dot counts + first-3/last-2 draw-order dots, tolerance 1e-4)
- Regenerate constants + fixtures: `node scripts/gen-orb-spec.mjs` (emits
  `OrbSpec.kt`, `OrbSpec.swift`, `OrbGoldenData.kt`, `OrbGoldenData.swift`)

## The rule that governs everything

> The orb is a picture of what the app is REALLY doing.
> The mapping is application state → orb state, never the reverse.
> The glow never fabricates a phase the pipeline does not have.

## The 12-state luminous contract (presentation)

The product vocabulary is wider than the nine geometries it projects onto. Each
phase borrows one geometry and carries a `LuminousProfile` (role + bloom +
core + pulse). This lives in `OrbPhase.kt`; `OrbPhaseTest.kt` locks it.

| OrbPhase | geometry (OrbState) | role | bloom breathes? | meaning |
|---|---|---|---|---|
| IDLE | breathing | CALM | slow, gentle | present, waiting, no request in flight |
| LISTENING | listening | CALM | yes | recogniser open and quiet |
| RECORDING | listening | ACTIVE | amplitude | the live mic level drives the core |
| THINKING | breathing | ACTIVE | yes | a turn begun, no token/tool back yet |
| REASONING | solving | ACTIVE | fast | retrieval / source reading / reasoning chains |
| GENERATING | composing | ACTIVE | fast | first tokens landing |
| STREAMING | composing | ACTIVE | yes | a settled stream flowing |
| SUCCESS | shaping | SUCCESS | no | the turn completed cleanly |
| ERROR | connecting | ALERT | no | the turn failed (alarm never looks playful) |
| PAUSED | breathing | MUTED | no | motion frozen by user/OS; shape survives |
| OFFLINE | connecting | MUTED | no | present but not alive (no reachable backend) |
| REDUCED_MOTION | breathing | CALM | no | one static representative frame, never hidden |

Roles resolve to real token colours **at composition** (a role, not a literal),
so an accent or appearance flip relights every orb with no code change:
`CALM → aiActive`, `ACTIVE → accentStrong`, `SUCCESS → success`,
`ALERT → error`, `MUTED → textSecondary`, and the aurora tail rides the
`CALM`/`ACTIVE` ring.

## Geometry catalogue (9 states, 9 distinct geometries — unchanged, golden-locked)

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

## Visual contract (luminous over a restrained dot field)

- **Dots** keep the original discipline: plain source-over fills in the shared
  AI-active accent, z-sorted far→near; identity still carried by **shape +
  motion + density**.
- **The luminous pass sits behind the dots** and adds light without touching the
  locked geometry: a radial bloom in the phase hue, an aurora outer ring for the
  alive/calm roles, and a reactive core halo. It uses a `Screen` blend so it
  *glows over the atmosphere* rather than tinting it.
- The bloom breathes on the **shared clock** for `pulse` phases; `SUCCESS`,
  `ERROR`, `PAUSED`, `OFFLINE` and `REDUCED_MOTION` are held **static** — a
  failure or an inert connection must not look playful.
- While `RECORDING`, a real 0–1 microphone amplitude (`RMS`) lifts the core —
  the orb visibly reacts to the voice, on both platforms (Android
  `RecognitionListener.onRmsChanged` → `ThinkingOrb(amplitude=…)`; iOS RMS in
  the `AVAudioEngine` install tap).
- Semantic status hues (success/error) now live *inside* the orb system as a
  presentation role — this supersedes the earlier "colour is outside the orb"
  rule, which predated the luminous mandate.

## Timing / clock

- `t = elapsedSeconds × presetSpeed × userSpeed` (per-state baked speed).
- ONE shared epoch per process: all same-speed orbs stay in phase — the bloom
  breathes in phase with the dots because both read the same `t`.
- Reduced motion: ONE static representative frame at **t = 0.6** — identity
  survives through shape; the orb is never hidden.
- Paused/backgrounded/offscreen: the frame clock stops entirely (Compose:
  composition-disposal + lifecycle observer; SwiftUI: TimelineView's own
  offscreen/background pausing). Nothing ticks invisibly.

## Performance contract

- Geometry is pure math into a **preallocated buffer** — the per-frame path
  allocates nothing (no per-dot objects, no collections).
- The luminous pass is **three cached `drawCircle` calls with a radial brush**
  behind the dot loop — O(1) per frame, no bitmap, no shader, no image decode.
  The `Modifier`-level frost tiers live in the glass system, not here.
- The frame time is read **inside the draw phase only** — frame updates
  invalidate drawing, never recomposition (Android) / no view diffing (iOS).
- Measured upstream geometry cost: ≤ 0.12 ms/frame for the densest mode
  (~590 dots); painting is plain circle fills z-sorted far→near.

## Honest state mapping (the product layer)

Two resolvers, both honest, one vocabulary wider than the other:

- `orbStateForChatStream` / `orbStateForVoice` → the nine **OrbStates** (the
  tested geometry resolver, `OrbStateMapping.kt`). Unchanged.
- `orbPhaseForChatStream` / `orbPhaseForVoice` → the twelve **OrbPhases**
  (`OrbPhase.kt`). Each phase's `.geometry` is the same OrbState the tested
  resolver returns where the two overlap, so adopting the product layer never
  contradicts the golden-locked behaviour.

| Application state | OrbPhase | geometry | Where |
|---|---|---|---|
| Chat streaming, buffer still empty | THINKING | breathing | composer life-sign + live bubble |
| Chat streaming, tokens flowing | STREAMING | composing | composer life-sign + live bubble |
| Retrieval / source reading in flight | REASONING | solving | chat (only when the wire says so) |
| Turn completes cleanly | SUCCESS | shaping | brief settle |
| Turn errors | ERROR | connecting | chat / voice |
| Stream Finalizing/Done/Cancelled | (none) | — | chat settled turn = no orb |
| Voice recogniser live & quiet | LISTENING | listening | voice status |
| Voice recogniser capturing (level > 0) | RECORDING | listening | voice status, `amplitude = RMS` |
| Voice recogniser processing | REASONING | solving | voice status |
| Voice idle/result/paused/error/permission | (none) | — | voice |
| Fresh workspace (no messages) | IDLE | breathing | chat empty state (identity) |

`connecting` and `offline` render the CONNECTING geometry; `weaving` stays a
catalogue geometry with no product phase yet. When the product grows the real
behaviour, map it in the resolver layer — never at the call site.

## Accessibility

- Every orb exposes a per-state label ("Working…", "Listening…", …) as its
  accessibility description (Compose `contentDescription` with
  `Role.Image`; SwiftUI `accessibilityLabel` + `.isImage`).
- The label is the state's honest name — the animation is never the only
  indication of state.
- The empty-workspace identity orb reads "GS assistant" (presence, not task).
- Reduced motion renders the static t = 0.6 frame and a static bloom; it never
  hides the orb.

## Anti-drift

`scripts/gen-orb-spec.mjs` regenerates the platform constants from the
vendored spec; both platforms' unit suites assert the SAME 72 golden frames
(11,288-dot parity upstream, 1e-4). A tuning change upstream means:
vendor the new spec → regenerate → both golden suites must be green together.
The **presentation** layer is guarded separately by `OrbPhaseTest` (twelve
phases, each onto one of the nine geometries, in-range profiles) — it can evolve
without touching a single golden vector.
