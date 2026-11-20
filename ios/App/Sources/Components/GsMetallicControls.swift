import SwiftUI

/// A compact, manually drawn silver-metal label for premium Settings rows.
/// Directional steel bands, a bright rim and a top specular line provide the
/// same readable fallback material as the WebGL badge without a native shader
/// dependency. The label is deliberately product-specific (PRO, not NEW).
struct GsMetallicBadge: View {
    let label: String

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        Text(label.uppercased())
            .font(.custom(Aero.productName(.semibold), size: 8))
            .tracking(0.55)
            .foregroundStyle(colorScheme == .dark
                ? Color(red: 0.16, green: 0.19, blue: 0.23)
                : Color(red: 0.22, green: 0.26, blue: 0.30))
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .background {
                Capsule().fill(
                    LinearGradient(
                        colors: [
                            Color(red: 1.00, green: 1.00, blue: 1.00),
                            Color(red: 0.73, green: 0.77, blue: 0.82),
                            Color(red: 0.98, green: 0.99, blue: 1.00),
                            Color(red: 0.66, green: 0.71, blue: 0.77),
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            }
            .overlay {
                Capsule().strokeBorder(
                    LinearGradient(
                        colors: [.white.opacity(0.98), Color.gray.opacity(0.55), .white.opacity(0.90)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    lineWidth: 0.8
                )
            }
            .overlay(alignment: .top) {
                Capsule()
                    .fill(.white.opacity(0.88))
                    .frame(height: 0.7)
                    .padding(.horizontal, 5)
                    .padding(.top, 1)
            }
            .shadow(color: .black.opacity(0.12), radius: 2, y: 1)
            .accessibilityLabel(label.uppercased() == "PRO" ? "Nexly Pro" : label)
    }
}

/// A silver, stateful Auto control. The visual is drawn in SwiftUI gradients;
/// an accessibility Toggle representation preserves the expected switch API.
struct GsMetallicAutoControl: View {
    @Binding var isOn: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Button(action: toggle) {
            HStack(spacing: 5) {
                Text("Auto")
                    .font(.custom(Aero.productName(.semibold), size: 9))
                    .textCase(.uppercase)
                ZStack {
                    Circle()
                        .fill(LinearGradient(
                            colors: [Color.white, Color(red: 0.69, green: 0.74, blue: 0.80)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ))
                    Circle().strokeBorder(.white.opacity(0.92), lineWidth: 0.8)
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
            .background {
                Capsule().fill(
                    LinearGradient(
                        colors: [
                            Color.white,
                            Color(red: 0.74, green: 0.79, blue: 0.84),
                            Color(red: 0.98, green: 0.99, blue: 1.00),
                            Color(red: 0.65, green: 0.70, blue: 0.76),
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            }
            .overlay {
                Capsule().strokeBorder(
                    LinearGradient(colors: [.white.opacity(0.98), Color.gray.opacity(0.55)],
                                   startPoint: .top, endPoint: .bottom),
                    lineWidth: 0.9
                )
            }
            .overlay(alignment: .top) {
                Capsule().fill(.white.opacity(0.88)).frame(height: 0.8)
                    .padding(.horizontal, 7).padding(.top, 1)
            }
            .shadow(color: Color(red: 0.31, green: 0.36, blue: 0.42).opacity(isOn ? 0.22 : 0.13),
                    radius: isOn ? 4 : 2, y: 1)
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityRepresentation {
            Toggle("Auto Select Model", isOn: $isOn)
                .labelsHidden()
                .toggleStyle(.switch)
        }
    }

    private func toggle() {
        withAnimation(reduceMotion ? nil : .spring(response: 0.28, dampingFraction: 0.62)) {
            isOn.toggle()
        }
        GSHaptics.select()
    }
}
