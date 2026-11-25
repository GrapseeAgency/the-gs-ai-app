import SwiftUI

/// ArgentUI v0.4.1 original reflection field, muted and fixed in place.
/// Component outline only: no halo, fill, idle animation or layout change.
private struct ArgentComponentRim<S: InsettableShape>: ViewModifier {
    let shape: S
    let enabled: Bool
    var overlayPanel: Bool = false
    var fallback: Color? = nil
    var preserveBorder: Bool = false
    var fallbackWidth: CGFloat = 1
    @ObservedObject private var settings = SettingsStore.shared
    @Environment(\.colorScheme) private var scheme
    @Environment(\.colorSchemeContrast) private var contrast

    init(shape: S, enabled: Bool, fallback: Color? = nil, preserveBorder: Bool = false, fallbackWidth: CGFloat = 1, overlayPanel: Bool = false) {
        self.shape = shape
        self.enabled = enabled
        self.overlayPanel = overlayPanel
        self.fallback = fallback
        self.preserveBorder = preserveBorder
        self.fallbackWidth = fallbackWidth
    }

    func body(content: Content) -> some View {
        content.overlay {
            if enabled {
                GeometryReader { geo in
                    if !preserveBorder && !settings.highContrast && contrast != .increased &&
                        gsArgentComponentSize(geo.size, overlayPanel: overlayPanel) {
                        rim(size: geo.size)
                    } else if let fallback {
                        shape.strokeBorder(fallback, lineWidth: fallbackWidth)
                    }
                }
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            }
        }
    }

    private func rim(size: CGSize) -> some View {
        let dark = scheme == .dark
        let light: Float = dark ? 0.86 : 0.60
        let shadow: Float = dark ? 0.58 : 0.35
        return shape.strokeBorder(Color.white, lineWidth: 0.75)
            .colorEffect(ShaderLibrary.gsArgentRim(
                .float2(Float(size.width), Float(size.height)),
                .float(3.4),
                .float3(light, light, light),
                .float3(shadow, shadow, shadow),
                .float(0),
                .float2(dark ? 0.07 : 0.045, dark ? 0.38 : 0.24)
            ))
    }
}

func gsArgentComponentSize(_ size: CGSize, overlayPanel: Bool = false) -> Bool {
    size.width >= 16 && size.height >= 16 && size.width <= 720 && size.height <= (overlayPanel ? 1100 : 320)
}

extension View {
    func gsArgentComponentRim<S: InsettableShape>(_ shape: S, enabled: Bool = true, overlayPanel: Bool = false) -> some View {
        modifier(ArgentComponentRim(shape: shape, enabled: enabled, overlayPanel: overlayPanel))
    }

    func gsArgentComponentBorder<S: InsettableShape>(_ shape: S, fallback: Color, preserveBorder: Bool = false, fallbackWidth: CGFloat = 1) -> some View {
        modifier(ArgentComponentRim(shape: shape, enabled: true, fallback: fallback, preserveBorder: preserveBorder, fallbackWidth: fallbackWidth))
    }
}
