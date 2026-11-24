import SwiftUI

/// Actual ArgentUI v0.4.1 optional native shader in ArgentRim.metal.
/// Only the alpha of an inset 0.75pt shape stroke is fed to the shader;
/// there is no interior paint, bloom, or change to hit testing.
private struct ArgentComponentRim<S: InsettableShape>: ViewModifier {
    let shape: S
    let enabled: Bool
    @ObservedObject private var settings = SettingsStore.shared
    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityContrast) private var contrast
    @Environment(\.scenePhase) private var scenePhase
    private static var epoch: Date { ArgentRimClock.epoch }

    func body(content: Content) -> some View {
        content.overlay {
            if enabled && !settings.highContrast && contrast != .increased {
                GeometryReader { geo in
                    let size = geo.size
                    if size.width >= 16 && size.height >= 16 &&
                        size.width <= 720 && size.height <= 320 {
                        let paused = reduceMotion || settings.reduceMotion ||
                            settings.animationReduced || scenePhase != .active
                        TimelineView(.animation(minimumInterval: 0.05, paused: paused)) { timeline in
                            let time = paused ? Float(3.4) :
                                Float(timeline.date.timeIntervalSince(Self.epoch) * 0.12 + 3.4)
                            rim(size: size, time: time)
                        }
                    }
                }
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            }
        }
    }

    private func rim(size: CGSize, time: Float) -> some View {
        let dark = scheme == .dark
        return shape.strokeBorder(Color.white, lineWidth: 0.75)
            .colorEffect(ShaderLibrary.gsArgentRim(
                .float2(Float(size.width), Float(size.height)),
                .float(time),
                .float3(dark ? 182/255 : 248/255, dark ? 190/255 : 250/255, dark ? 201/255 : 252/255),
                .float3(dark ? 20/255 : 35/255, dark ? 22/255 : 38/255, dark ? 26/255 : 43/255),
                .float(dark ? 0.0125 : 0.015)
            ))
            .opacity(dark ? 0.32 : 0.24)
    }
}

private enum ArgentRimClock {
    static let epoch = Date()
}

extension View {
    func gsArgentComponentRim<S: InsettableShape>(_ shape: S, enabled: Bool = true) -> some View {
        modifier(ArgentComponentRim(shape: shape, enabled: enabled))
    }
}
