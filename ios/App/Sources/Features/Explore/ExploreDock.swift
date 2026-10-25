import SwiftUI

/// The Explore page's floating dock — the reference mock's bottom bar:
/// a glass capsule carrying Home, the lit Explore pill, and Settings.
///
/// Overlay only. It does NOT rewrite the navigation contract: the
/// drawer stays the app's section switcher, Home pops the pushed path
/// back to the root, Settings is a plain push. The capsule wears the
/// measured `floatingControl` glass, which ships material-free on
/// purpose — Apple vibrancy moves every pixel under it (see
/// Theme/GlassMaterial.swift; the same law the Home dock obeys).
struct ExploreDock: View {
    var onOpenHome: () -> Void = {}
    var onScrollToTop: () -> Void = {}
    var onOpenSettings: () -> Void = {}

    var body: some View {
        HStack(spacing: 0) {
            dockCircle(glyph: "home", label: "Home", action: onOpenHome)
            Spacer(minLength: 8)
            explorePill
            Spacer(minLength: 8)
            dockCircle(glyph: "settings", label: "Settings", action: onOpenSettings)
        }
        .padding(10)
        .gsCapsuleGlass(.floatingControl)
    }

    /// The mock's lit action: a peach capsule with the compass seated in
    /// a small white well, labelled, reading as the page you are on.
    private var explorePill: some View {
        Button(action: onScrollToTop) {
            HStack(spacing: 8) {
                ZStack {
                    Circle()
                        .fill(.white.opacity(0.55))
                        .frame(width: 30, height: 30)
                    NexlyGlyph(name: "compass", size: 15, color: ExplorePalette.inkStrong)
                }
                Text("Explore")
                    .font(Aero.bodyMedium().weight(.semibold))
                    .foregroundStyle(ExplorePalette.inkStrong)
                    .lineLimit(1)
                    .minimumScaleFactor(0.9)
            }
            .frame(maxWidth: 157, minHeight: 46)
            .background(
                Capsule().fill(
                    LinearGradient(
                        colors: [ExplorePalette.ctaTop, ExplorePalette.peach],
                        startPoint: .leading, endPoint: .trailing
                    )
                )
            )
            .overlay(
                Capsule().strokeBorder(
                    LinearGradient(
                        colors: [.white.opacity(0.85), .white.opacity(0.35)],
                        startPoint: .top, endPoint: .bottom
                    ),
                    lineWidth: 1
                )
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Explore. Current tab. Scrolls to top.")
    }

    private func dockCircle(glyph: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            NexlyGlyph(name: glyph, size: 17, color: ExplorePalette.inkStrong)
                .frame(width: 46, height: 46)
                .gsGlass(.floatingControl, corner: 23)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}
