import SwiftUI

/// The reference brand header, shared by the chat screen and the full-screen
/// render harness: orb, “Nexly / AI Companion”, sparkle and menu controls.
/// The context-menu content is injected so production keeps its real actions
/// while the harness renders the same visual with empty menus.
struct NexlyBrandHeader<SparkleMenu: View, MenuMenu: View>: View {
    var sparkleLabel: String
    var menuGlyph: String
    var menuLabel: String
    var onSparkle: () -> Void
    var onMenu: () -> Void
    @ViewBuilder var sparkleMenu: () -> SparkleMenu
    @ViewBuilder var menuMenu: () -> MenuMenu

    var body: some View {
        HStack(spacing: 10) {
            AnimatedGsIllustration(
                imageName: "GSCompanionOrb",
                size: 42,
                breathDuration: 3.2,
                rotationDegrees: 0.8,
                lift: -1,
                startDelay: 0.1
            )
            .overlay(Circle().strokeBorder(Color.white.opacity(0.55), lineWidth: 1))
            .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 1) {
                Text("Nexly")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Aero.text)
                    .lineLimit(1)
                Text("AI Companion")
                    .font(.system(size: 10.5, weight: .regular))
                    .foregroundStyle(Aero.textSecondary)
                    .lineLimit(1)
            }

            Spacer(minLength: 8)

            headerIconButton(
                glyph: "sparkles",
                label: sparkleLabel,
                action: onSparkle,
                menu: sparkleMenu
            )
            headerIconButton(
                glyph: menuGlyph,
                label: menuLabel,
                action: onMenu,
                menu: menuMenu
            )
        }
        .padding(.horizontal, Aero.Spacing.m)
        .padding(.vertical, 5)
        .background(
            LinearGradient(
                colors: [
                    Color(red: 1.0, green: 0.95, blue: 0.92).opacity(0.62),
                    Color(red: 1.0, green: 0.93, blue: 0.90).opacity(0.34),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
        )
    }

    private func headerIconButton<Menu: View>(
        glyph: String,
        label: String,
        action: @escaping () -> Void,
        @ViewBuilder menu: () -> Menu
    ) -> some View {
        Button(action: action) {
            NexlyGlyph(name: glyph, size: 19, color: Aero.text)
                .frame(width: 40, height: 40)
                .background(Circle().fill(.clear))
                .gsGlass(.sunken, corner: 20)
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityLabel(label)
        .contextMenu { menu() }
    }
}

/// The reference composer capsule, shared by the chat screen and the
/// full-screen render harness. One capsule: lilac sparkle, field, peach
/// paper-plane. Attachments/voice/search live in the sparkle menu.
struct NexlyComposerBar: View {
    @Binding var text: String
    var isStreaming: Bool
    var canSend: Bool
    var focused: FocusState<Bool>.Binding
    var onAttach: () -> Void
    var onVoice: () -> Void
    var onSearch: () -> Void
    var onSend: () -> Void
    var onStop: () -> Void
    var onSubmit: () -> Void

    var body: some View {
        HStack(alignment: .bottom, spacing: 4) {
            Button(action: onAttach) {
                NexlyGlyph(
                    name: "sparkles",
                    size: 21,
                    color: Color(red: 0.56, green: 0.48, blue: 1.0)
                )
                .frame(width: 40, height: 40)
            }
            .buttonStyle(KineticPressStyle())
            .accessibilityLabel("Nexly tools, attachments and modes")
            .contextMenu {
                Button(action: onAttach) {
                    Label("Attachments & modes", systemImage: "paperclip")
                }
                Button(action: onVoice) {
                    Label("Voice input", systemImage: "mic")
                }
                Button(action: onSearch) {
                    Label("Search in chat", systemImage: "magnifyingglass")
                }
            }

            TextField("Ask me anything...", text: $text, axis: .vertical)
                .font(.system(size: 15.5))
                .foregroundStyle(Aero.text)
                .lineLimit(1...6)
                .focused(focused)
                .submitLabel(.send)
                .onSubmit(onSubmit)
                .onChange(of: text) { newValue in
                    guard SettingsStore.shared.enterToSend, newValue.hasSuffix("\n") else { return }
                    text = String(newValue.dropLast())
                    onSubmit()
                }
                .padding(.vertical, 11)

            if isStreaming {
                Button(action: onStop) {
                    NexlyGlyph(name: "player-stop", size: 18, color: Aero.text)
                        .frame(width: 38, height: 38)
                        .background(Circle().fill(Aero.surface.opacity(0.55)))
                        .overlay(Circle().strokeBorder(Color.white.opacity(0.7), lineWidth: 1))
                }
                .buttonStyle(KineticPressStyle())
                .accessibilityLabel("Stop generating")
            } else {
                Button(action: onSend) {
                    NexlyGlyph(name: "send-2", size: 18, color: .white)
                        .frame(width: 38, height: 38)
                        .background(
                            Circle().fill(
                                LinearGradient(
                                    colors: [Color(red: 1.0, green: 0.78, blue: 0.58),
                                             Color(red: 0.95, green: 0.53, blue: 0.27)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                        )
                        .overlay(Circle().strokeBorder(Color.white.opacity(0.55), lineWidth: 1))
                        .shadow(color: Color(red: 0.86, green: 0.42, blue: 0.20).opacity(0.30), radius: 6, y: 2)
                        .opacity(canSend ? 1 : 0.75)
                }
                .buttonStyle(KineticPressStyle())
                .disabled(!canSend)
                .accessibilityLabel("Send")
            }
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 5)
        .gsCapsuleGlass(.floatingControl)
    }
}
