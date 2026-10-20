import SwiftUI

enum NativeIllustrationVariant {
    case companion, research, vision, code, documents, ideas
}

/// Small code-drawn companion illustration for utility states. It uses native
/// SwiftUI geometry and gradients instead of a stock glyph or bitmap.
struct NativeGsIllustration: View {
    var size: CGFloat = 72
    var variant: NativeIllustrationVariant = .companion
    var accessibilityLabel: String = "GS companion illustration"

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var alive = false

    var body: some View {
        ZStack {
            Circle()
                .stroke(Aero.accent.opacity(0.26), lineWidth: max(1, size * 0.018))
                .frame(width: size * 0.62, height: size * 0.62)
                .rotationEffect(.degrees(reduceMotion ? 0 : (alive ? 16 : -8)))

            Circle()
                .fill(Aero.accentStrong)
                .frame(width: size * 0.09, height: size * 0.09)
                .offset(x: size * 0.27, y: reduceMotion ? -size * 0.04 : (alive ? -size * 0.10 : size * 0.02))

            if variant != .companion {
                Circle()
                    .fill(Aero.accent.opacity(0.78))
                    .frame(width: size * 0.055, height: size * 0.055)
                    .offset(satelliteOffset)
            }

            Circle()
                .fill(
                    RadialGradient(
                        colors: [Aero.accentStrong, Aero.accent, Aero.accent.opacity(0.70)],
                        center: UnitPoint(x: 0.32, y: 0.28),
                        startRadius: 1,
                        endRadius: size * 0.40
                    )
                )
                .frame(width: size * 0.58, height: size * 0.58)
                .scaleEffect(reduceMotion ? 1 : (alive ? 1.035 : 0.98))

            Circle()
                .fill(Aero.onAccent.opacity(0.38))
                .frame(width: size * 0.095, height: size * 0.095)
                .offset(x: -size * 0.08, y: -size * 0.09)
        }
        .frame(width: size, height: size)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityLabel)
        .accessibilityAddTraits(.isImage)
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 2.1).repeatForever(autoreverses: true)) {
                alive = true
            }
        }
    }

    private var satelliteOffset: CGSize {
        let angle: CGFloat
        switch variant {
        case .research: angle = 38
        case .vision: angle = 112
        case .code: angle = 176
        case .documents: angle = 224
        case .ideas: angle = 292
        case .companion: angle = 0
        }
        let radians = angle * .pi / 180
        return CGSize(width: size * 0.24 * cos(radians), height: size * 0.24 * sin(radians))
    }
}
