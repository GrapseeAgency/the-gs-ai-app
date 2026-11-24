import MetalFxKit
import SwiftUI

/// The product's compact PRO chip, rendered by Libraries.dev's real
/// shader-driven MetalBadge instead of the former hand-painted gradient.
struct GsMetallicBadge: View {
    let label: String
    @ObservedObject private var settings = SettingsStore.shared
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        MetalBadge(
            label.uppercased(),
            preset: .silver,
            theme: .auto,
            strength: 0.9,
            scale: 0.68,
            metalOpacity: 0.82,
            glow: 0.18,
            textColor: Color(red: 0.22, green: 0.26, blue: 0.30),
            paused: settings.animationReduced || reduceMotion
        )
        .accessibilityLabel(label.uppercased() == "PRO" ? "Nexly Pro" : label)
    }
}

/// The stateful Auto control, backed by the upstream SwiftUI MetalFx ring.
/// The parent still owns the setting binding and Toggle accessibility role.
struct GsMetallicAutoControl: View {
    @Binding var isOn: Bool
    @ObservedObject private var settings = SettingsStore.shared
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Button(action: toggle) {
            MetalFx(
                variant: .button,
                preset: .silver,
                theme: .auto,
                strength: 0.88,
                innerShadow: true,
                paused: settings.animationReduced || reduceMotion,
                tilt: false,
                source: .none
            ) {
                HStack(spacing: 5) {
                    Text("Auto")
                        .font(.custom(Aero.productName(.semibold), size: 9))
                        .textCase(.uppercase)
                    ZStack {
                        Circle().fill(Color.white.opacity(0.9))
                        Circle().strokeBorder(Color.white.opacity(0.95), lineWidth: 0.8)
                        if isOn {
                            Image(systemName: "checkmark")
                                .font(.system(size: 7, weight: .heavy))
                                .foregroundStyle(Color(red: 0.29, green: 0.34, blue: 0.40))
                        }
                    }
                    .frame(width: 14, height: 14)
                    .shadow(color: .black.opacity(0.12), radius: 1, y: 0.5)
                }
                .foregroundStyle(Color(red: 0.27, green: 0.32, blue: 0.38))
                .padding(.horizontal, 8)
                .frame(minWidth: 62, minHeight: 34)
            }
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityRepresentation {
            Toggle("Automatic reasoning", isOn: $isOn)
                .labelsHidden()
                .toggleStyle(.switch)
        }
    }

    private func toggle() {
        withAnimation((settings.animationReduced || reduceMotion) ? nil : .spring(response: 0.28, dampingFraction: 0.62)) {
            isOn.toggle()
        }
        GSHaptics.select()
    }
}


/// The real Libraries.dev chromatic send ring. The enclosing Button owns
/// the action, accessibility label and press feedback; the shader is visual
/// only. No custom gradient or WebView is involved.
struct GsMetallicSendFace: View {
    var size: CGFloat = 40
    var enabled: Bool = true

    @ObservedObject private var settings = SettingsStore.shared
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase

    private var motionAllowed: Bool {
        enabled && !reduceMotion && !settings.animationReduced &&
            !settings.reduceMotion && scenePhase == .active
    }

    var body: some View {
        MetalFx(
            variant: .circle,
            preset: .chromatic,
            theme: .auto,
            strength: enabled ? 0.9 : 0.35,
            innerShadow: true,
            paused: !motionAllowed,
            source: motionAllowed ? .tilt : .none
        ) {
            Image(systemName: "arrow.up")
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(Color.primary.opacity(enabled ? 1 : 0.5))
                .frame(width: size, height: size)
        }
        .accessibilityHidden(true)
    }
}
