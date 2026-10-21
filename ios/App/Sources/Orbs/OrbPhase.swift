// THE LUMINOUS ORB — the iOS twin of `android/.../ui/orbs/OrbPhase.kt`.
//
// Two layers, and the split is deliberate (mandate: 12-state luminous orb):
//
//   1. GEOMETRY — the nine dot-field animations in `OrbState`. They are
//      GENERATED (OrbSpec.swift) and golden-locked; nothing here touches them.
//      Geometry is HOW the orb moves.
//
//   2. PRESENTATION — the twelve product PHASES below and the LuminousProfile
//      that colours and lights them. Presentation is HOW the orb GLOWS, mapped
//      ONTO the existing geometries: every phase names an existing `OrbState`
//      as its geometry, so the honest animation is reused verbatim while the
//      luminous layer (the accent bloom, the iridescent wash, the reactive
//      core) is drawn around it, additively, in ThinkingOrbView.
//
// Same reconciliation as Android, same numbers: keep the iridescent material
// the reference calls for without forking the motion engine.

import SwiftUI

/// The twelve product phases the interface can be in. Nine describe live
/// assistant work; the last three are non-motion / connectivity states.
enum OrbPhase {
    /// Present, waiting; no request in flight.
    case idle
    /// The recogniser is open and quiet — listening for speech to start.
    case listening
    /// The recogniser is capturing — the live microphone level drives amplitude.
    case recording
    /// A turn has begun; the model is engaged but nothing is back yet.
    case thinking
    /// Multi-step work: retrieval, source reading, reasoning chains in flight.
    case reasoning
    /// The first tokens are landing; the answer is being composed.
    case generating
    /// A settled stream is flowing to the transcript.
    case streaming
    /// The turn completed cleanly.
    case success
    /// The turn failed or the model returned an error.
    case error
    /// Motion frozen by the user or the OS; shape survives.
    case paused
    /// No reachable backend / capability absent — present but not alive.
    case offline
    /// Reduced motion honoured: one static frame, never hidden.
    case reducedMotion
}

/// The hue family a phase borrows — resolved to real `Aero` colors at draw
/// time, so an accent or appearance flip relights every orb with no change.
enum OrbLuminousRole {
    /// The calm standby wash — the iridescent aurora, low intensity.
    case calm
    /// Live work — the accent, brightest, breathing.
    case active
    /// A clean completion — the success hue, one soft lift.
    case success
    /// A failure — the error hue, no breathing.
    case alert
    /// Present but inert — a muted, desaturated ember.
    case muted
}

/// How a phase lights the geometry beneath it.
struct LuminousProfile {
    let role: OrbLuminousRole
    /// Outer bloom strength (0…1) behind the whole field.
    let glowAlpha: Double
    /// Inner halo directly behind the dots; amplitude lifts it.
    let coreAlpha: Double
    /// Whether intensity breathes with the shared orb clock.
    let pulse: Bool
    /// Radians/second of that breath when pulsing.
    var pulseSpeed: Double = 1.6

    /// The hue the role borrows at draw time.
    var color: Color {
        switch role {
        case .calm: return Aero.aurora.first ?? Aero.aiActive
        case .active: return Aero.accentStrong
        case .success: return Aero.success
        case .alert: return Aero.danger
        case .muted: return Aero.textMuted
        }
    }
}

extension OrbPhase {
    /// The geometry (dot field) a product phase projects onto — always one
    /// of the nine, exactly as Android's contract maps them.
    var geometry: OrbState {
        switch self {
        case .idle: return .breathing
        case .listening, .recording: return .listening
        case .thinking: return .breathing
        case .reasoning: return .solving
        case .generating, .streaming: return .composing
        case .success: return .shaping
        case .error, .offline: return .connecting
        case .paused: return .breathing
        case .reducedMotion: return .breathing
        }
    }

    /// The luminous treatment a product phase carries over its geometry —
    /// identical profiles to the Android table.
    var luminance: LuminousProfile {
        switch self {
        case .idle: return LuminousProfile(role: .calm, glowAlpha: 0.16, coreAlpha: 0.10, pulse: true, pulseSpeed: 0.9)
        case .listening: return LuminousProfile(role: .calm, glowAlpha: 0.20, coreAlpha: 0.14, pulse: true, pulseSpeed: 1.1)
        case .recording: return LuminousProfile(role: .active, glowAlpha: 0.26, coreAlpha: 0.18, pulse: false)
        case .thinking: return LuminousProfile(role: .active, glowAlpha: 0.24, coreAlpha: 0.16, pulse: true, pulseSpeed: 1.3)
        case .reasoning: return LuminousProfile(role: .active, glowAlpha: 0.30, coreAlpha: 0.20, pulse: true, pulseSpeed: 1.8)
        case .generating: return LuminousProfile(role: .active, glowAlpha: 0.28, coreAlpha: 0.22, pulse: true, pulseSpeed: 2.0)
        case .streaming: return LuminousProfile(role: .active, glowAlpha: 0.26, coreAlpha: 0.20, pulse: true, pulseSpeed: 1.7)
        case .success: return LuminousProfile(role: .success, glowAlpha: 0.34, coreAlpha: 0.24, pulse: false)
        case .error: return LuminousProfile(role: .alert, glowAlpha: 0.30, coreAlpha: 0.22, pulse: false)
        case .paused: return LuminousProfile(role: .muted, glowAlpha: 0.12, coreAlpha: 0.08, pulse: false)
        case .offline: return LuminousProfile(role: .muted, glowAlpha: 0.10, coreAlpha: 0.06, pulse: false)
        case .reducedMotion: return LuminousProfile(role: .calm, glowAlpha: 0.16, coreAlpha: 0.10, pulse: false)
        }
    }
}

/// The default luminous treatment for a bare geometry — screens that render
/// the orb through the honest state mapping get the luminous material for
/// free: each of the nine geometries borrows the presentation of the product
/// phase it most closely describes (the same table as Android).
func luminousPhaseFor(_ state: OrbState) -> OrbPhase {
    switch state {
    case .working, .searching, .solving: return .reasoning
    case .listening: return .recording
    case .connecting: return .offline
    case .weaving: return .generating
    case .composing: return .streaming
    case .breathing: return .thinking
    case .shaping: return .success
    }
}

/// The luminous profile a geometry presents by default.
func luminousProfileFor(_ state: OrbState) -> LuminousProfile {
    luminousPhaseFor(state).luminance
}
