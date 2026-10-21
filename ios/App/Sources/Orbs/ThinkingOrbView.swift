// Native SwiftUI activity-orb view (Phase 4) — Canvas + TimelineView, per the
// reference port plan. The orb shows what the app is REALLY doing via the
// honest mapping helpers at the bottom of this file; it never fabricates a
// state, using the shared warm AI-active accent, and
// never steals attention from the conversation, the composer or the answer.

import SwiftUI

/// Shared clock epoch: every mounted orb derives t from the same reference
/// instant, so same-speed instances stay in phase — the native equivalent of
/// the reference's shared performance.now clock.
enum OrbClock {
    static let epoch = Date.timeIntervalSinceReferenceDate
    static func seconds(from date: Date) -> Double {
        date.timeIntervalSinceReferenceDate - epoch
    }
}

public struct ThinkingOrbView: View {
    let state: OrbState
    var size: OrbSize = .standard
    var speed: Double = 1
    var paused: Bool = false
    var contentDescription: String? = nil
    /// The luminous presentation drawn AROUND the geometry — nil resolves to
    /// the default profile of the state (the 12-phase contract, iOS twin of
    /// Android's ThinkingOrb `luminance` parameter).
    var luminance: LuminousProfile? = nil
    /// Live level 0…1 lifted from the microphone tap (voice); -1 = not
    /// reactive, the glow only breathes with the clock.
    var amplitude: Double = -1

    @Environment(\.accessibilityReduceMotion) private var systemReduceMotion
    @State private var buffer = OrbFrameBuffer()

    private var reduceMotion: Bool {
        systemReduceMotion || SettingsStore.shared.animationReduced
    }

    public var body: some View {
        let resolved = OrbSpec.resolve(state: state, size: size)
        let px = CGFloat(size.rawValue)
        Group {
            if reduceMotion {
                // One static representative frame — the state survives through
                // shape; the orb is never simply hidden.
                orbCanvas(resolved: resolved, t: OrbSpec.staticT)
            } else {
                // TimelineView pauses by itself when the view is offscreen and
                // when the app is backgrounded; `paused:` freezes on the
                // current frame and resumes in phase from the shared epoch.
                TimelineView(.animation(minimumInterval: nil, paused: paused)) { timeline in
                    orbCanvas(
                        resolved: resolved,
                        t: OrbClock.seconds(from: timeline.date) * resolved.speed * speed
                    )
                }
            }
        }
        .frame(width: px, height: px)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(contentDescription ?? state.label)
        .accessibilityAddTraits(.isImage)
    }

    private func orbCanvas(resolved: ResolvedOrb, t: Double) -> some View {
        let profile = luminance ?? luminousProfileFor(state)
        return Canvas { context, _ in
            drawLuminousGlow(in: context, profile: profile, t: t)
            OrbEngine.render(size: Double(size.rawValue), t: t, resolved: resolved, buf: buffer)
            // Upstream paint contract: lines first (only `connecting` emits
            // them), then dots far→near; warm accent; plain source-over fills,
            // no filters, no blend modes.
            for i in 0..<buffer.lineCount {
                let w = min(1, max(0, buffer.lWhite[i]))
                let alpha = min(1, max(0, buffer.lAlpha[i]))
                var path = Path()
                path.move(to: CGPoint(x: buffer.lx1[i], y: buffer.ly1[i]))
                path.addLine(to: CGPoint(x: buffer.lx2[i], y: buffer.ly2[i]))
                context.stroke(
                    path,
                    with: .color(Aero.aiActive.opacity(alpha * (0.35 + w * 0.65))),
                    lineWidth: buffer.lWidth[i]
                )
            }
            for k in 0..<buffer.dotCount {
                let i = buffer.drawOrder[k]
                let w = min(1, max(0, buffer.pWhite[i]))
                let alpha = min(1, max(0, buffer.pAlpha[i]))
                let r = buffer.pr[i]
                let rect = CGRect(x: buffer.px[i] - r, y: buffer.py[i] - r, width: r * 2, height: r * 2)
                context.fill(
                    Path(ellipseIn: rect),
                    with: .color(Aero.aiActive.opacity(alpha * (0.35 + w * 0.65)))
                )
            }
        }
    }

    /// The luminous layer — additive glow BEHIND the untouched dot geometry
    /// (iOS twin of Android ThinkingOrb's Screen-blend overlay): a wide role
    /// bloom, the iridescent aurora ring for calm/active phases, and a tight
    /// reactive core the microphone amplitude lifts directly. Alphas are
    /// pulse-modulated on the shared clock and clamped, so a frozen reduced
    /// frame can never blaze.
    private func drawLuminousGlow(in context: GraphicsContext, profile: LuminousProfile, t: Double) {
        let side = CGFloat(size.rawValue)
        let center = CGPoint(x: side / 2, y: side / 2)
        let breathe = profile.pulse ? 0.5 + 0.5 * sin(t * profile.pulseSpeed) : 1.0
        let boost = max(0, amplitude)
        let iridescent = profile.role == .calm || profile.role == .active
        let glow = min(1, profile.glowAlpha * (0.55 + 0.45 * breathe) + 0.20 * boost)
        let core = min(1, profile.coreAlpha * (0.55 + 0.45 * breathe) + 0.45 * boost)

        // Additive pass: the glow lightens what is under it (the atmosphere),
        // never tints. GraphicsContext has no fillLayer — a copy with its
        // blendMode set composites every fill/stroke below with .plusLighter,
        // the SwiftUI Canvas equivalent of Android's BlendMode.Screen.
        var g = context
        g.blendMode = .plusLighter
        g.fill(
            Path(ellipseIn: CGRect(x: 0, y: 0, width: side, height: side)),
            with: .radialGradient(
                Gradient(colors: [profile.color.opacity(glow * 0.85), .clear]),
                center: center,
                startRadius: 0,
                endRadius: side * 0.5
            )
        )
        if iridescent, let wash = Aero.aurora.last {
            let ring = side * 0.46
            g.stroke(
                Path(ellipseIn: CGRect(x: (side - ring) / 2, y: (side - ring) / 2, width: ring, height: ring)),
                with: .color(wash.opacity(glow * 0.35)),
                lineWidth: side * 0.055
            )
        }
        let spot = side * 0.30
        g.fill(
            Path(ellipseIn: CGRect(x: (side - spot) / 2, y: (side - spot) / 2, width: spot, height: spot)),
            with: .radialGradient(
                Gradient(colors: [profile.color.opacity(core), .clear]),
                center: center,
                startRadius: 0,
                endRadius: spot / 2
            )
        )
    }
}

// MARK: - Honest state mapping (Phase 4 §6)

/// Application state → orb state. The orb is a picture of what the app is
/// REALLY doing; nothing here may fabricate a state the pipeline does not
/// have. CURRENT MAPPINGS:
///
///   Chat streaming:  real search turn, wire-driven (PHASE 8.1):
///                    `search started`/status searching → .searching
///                    ("Searching…"), source opening/reading → .working
///                    ("Working…") — only while page retrieval is genuinely
///                    in flight, synthesis start (status composing / first
///                    delta) → .composing ("Composing…"). A failed search is
///                    not an orb state: the turn honestly falls through to
///                    the waiting/composing states below (the disclosure is
///                    the answer).
///                    waiting for first token, request carried attachments
///                                         → .working ("Working…") —
///                    PHASE 6: the vision model genuinely receives and
///                    analyses the attached image(s) before the first token.
///                    PHASE 7: document requests (PDF/TXT/MD/CSV) go through
///                    real server-side extraction before the first token.
///                    Both are real, distinct pipeline phases — mapped, not
///                    invented.
///                    waiting for first token, text-only → .breathing ("Thinking…"),
///                    tokens flowing                     → .composing ("Composing…"),
///                    not streaming                      → nil (the turn is settled).
///   Voice session:   .listening → .listening, .processing → .working,
///                    everything else (idle/result/error/permission) → nil.
///
/// DELIBERATELY UNMAPPED (exist in the catalogue, no real app state yet):
/// solving / connecting / weaving / shaping. When the product grows the real
/// behaviour, map it HERE — never at the call site.
func orbStateForChatStreaming(
    isStreaming: Bool,
    liveContentEmpty: Bool,
    requestHasAttachments: Bool = false,
    searchPhase: ChatSearchPhase = .idle
) -> OrbState? {
    guard isStreaming else { return nil }
    switch searchPhase {
    case .searching: return .searching
    case .working: return .working
    case .composing: return .composing
    case .failed, .idle: break // no search orb state — the turn keeps flowing
    }
    if liveContentEmpty {
        return requestHasAttachments ? .working : .breathing
    }
    return .composing
}

func orbStateForVoiceSession(_ sessionState: VoiceSessionState) -> OrbState? {
    switch sessionState {
    case .listening: return .listening
    case .processing: return .working
    default: return nil
    }
}
