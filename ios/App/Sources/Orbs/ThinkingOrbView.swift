// Native SwiftUI activity-orb view (Phase 4) — Canvas + TimelineView, per the
// reference port plan. The orb shows what the app is REALLY doing via the
// honest mapping helpers at the bottom of this file; it never fabricates a
// state, never re-colours (strictly monochrome ink mirroring the theme), and
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
    var contentDescription: String?

    @Environment(\.colorScheme) private var colorScheme
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
        Canvas { context, _ in
            OrbEngine.render(size: Double(size.rawValue), t: t, resolved: resolved, buf: buffer)
            let dark = colorScheme == .dark
            // Upstream paint contract: lines first (only `connecting` emits
            // them), then dots far→near; ink mirrors the theme; plain
            // source-over fills, no filters, no blend modes.
            for i in 0..<buffer.lineCount {
                let w = min(1, max(0, buffer.lWhite[i]))
                let g = dark ? 1 - w : w
                let gray = (g * 255).rounded() / 255
                let alpha = min(1, max(0, buffer.lAlpha[i]))
                var path = Path()
                path.move(to: CGPoint(x: buffer.lx1[i], y: buffer.ly1[i]))
                path.addLine(to: CGPoint(x: buffer.lx2[i], y: buffer.ly2[i]))
                context.stroke(
                    path,
                    with: .color(Color(.sRGB, red: gray, green: gray, blue: gray, opacity: alpha)),
                    lineWidth: buffer.lWidth[i]
                )
            }
            for k in 0..<buffer.dotCount {
                let i = buffer.drawOrder[k]
                let w = min(1, max(0, buffer.pWhite[i]))
                let g = dark ? 1 - w : w
                let gray = (g * 255).rounded() / 255
                let alpha = min(1, max(0, buffer.pAlpha[i]))
                let r = buffer.pr[i]
                let rect = CGRect(x: buffer.px[i] - r, y: buffer.py[i] - r, width: r * 2, height: r * 2)
                context.fill(
                    Path(ellipseIn: rect),
                    with: .color(Color(.sRGB, red: gray, green: gray, blue: gray, opacity: alpha))
                )
            }
        }
    }
}

// MARK: - Honest state mapping (Phase 4 §6)

/// Application state → orb state. The orb is a picture of what the app is
/// REALLY doing; nothing here may fabricate a state the pipeline does not
/// have. CURRENT MAPPINGS:
///
///   Chat streaming:  waiting for first token, request carried images
///                                         → .working ("Working…") —
///                    PHASE 6: the vision model genuinely receives and
///                    analyses the attached image(s) before the first token;
///                    a real, distinct pipeline phase — mapped, not invented.
///                    waiting for first token, text-only → .breathing ("Thinking…"),
///                    tokens flowing                     → .composing ("Composing…"),
///                    not streaming                      → nil (the turn is settled).
///   Voice session:   .listening → .listening, .processing → .working,
///                    everything else (idle/result/error/permission) → nil.
///
/// DELIBERATELY UNMAPPED (exist in the catalogue, no real app state yet):
/// searching / solving / connecting / weaving / shaping. When the product
/// grows the real behaviour, map it HERE — never at the call site.
func orbStateForChatStreaming(
    isStreaming: Bool,
    liveContentEmpty: Bool,
    requestHasImages: Bool = false
) -> OrbState? {
    guard isStreaming else { return nil }
    if liveContentEmpty {
        return requestHasImages ? .working : .breathing
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
