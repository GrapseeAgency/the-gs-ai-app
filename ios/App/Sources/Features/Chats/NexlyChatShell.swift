import SwiftUI

/// The reference brand header, shared by the chat screen and the full-screen
/// render harness: a lilac sparkle in a lit glass circle, “Nexly / Your AI
/// companion”, sparkle and menu controls. The context-menu content is injected
/// so production keeps its real actions while the harness renders the same
/// visual with empty menus.
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
            // The reference brand mark: a lilac sparkle in a lit glass circle —
            // the same white-tile + glyph language the pre-chat header uses.
            ZStack {
                Circle()
                    .fill(Color.white.opacity(0.94))
                    .frame(width: 38, height: 38)
                    .overlay(Circle().strokeBorder(Color.white, lineWidth: 1))
                    .shadow(color: Color(red: 0.50, green: 0.40, blue: 0.85).opacity(0.18), radius: 4, y: 1)
                NexlyGlyph(name: "sparkles", size: 18, color: Color(red: 0.56, green: 0.48, blue: 1.0))
            }
            .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 1) {
                Text("Nexly")
                    .font(.system(size: 16.5, weight: .semibold))
                    .foregroundStyle(NexlyInk.header)
                    .lineLimit(1)
                Text("Your AI companion")
                    .font(.system(size: 10, weight: .regular))
                    .foregroundStyle(NexlyInk.headerMeta)
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
                    Color(red: 1.0, green: 0.96, blue: 0.935).opacity(0.66),
                    Color(red: 1.0, green: 0.94, blue: 0.915).opacity(0.30),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
        )
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(Color.white.opacity(0.42))
                .frame(height: 0.75)
        }
    }

    private func headerIconButton<Menu: View>(
        glyph: String,
        label: String,
        action: @escaping () -> Void,
        @ViewBuilder menu: () -> Menu
    ) -> some View {
        Button(action: action) {
            NexlyGlyph(name: glyph, size: 18, color: Aero.text)
                .frame(width: 36, height: 36)
                .background(Circle().fill(.clear))
                .gsGlass(.sunken, corner: 20)
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityLabel(label)
        .contextMenu { menu() }
    }
}

/// The reference composer capsule, shared by the chat screen and the
/// full-screen render harness. One capsule: plus, field, violet paper-plane,
/// mic. Attachments/modes/search live in the plus menu.
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
                    name: "plus",
                    size: 19,
                    color: Color(red: 0.56, green: 0.48, blue: 1.0)
                )
                .frame(width: 38, height: 38)
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

            TextField("Ask anything...", text: $text, axis: .vertical)
                .font(.system(size: 14))
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
                .padding(.vertical, 8)

            if isStreaming {
                Button(action: onStop) {
                    NexlyGlyph(name: "player-stop", size: 17, color: Aero.text)
                        .frame(width: 36, height: 36)
                        .background(Circle().fill(Aero.surface.opacity(0.55)))
                        .overlay(Circle().strokeBorder(Color.white.opacity(0.7), lineWidth: 1))
                }
                .buttonStyle(KineticPressStyle())
                .accessibilityLabel("Stop generating")
            } else {
                Button(action: onSend) {
                    NexlyGlyph(name: "send-2", size: 17, color: .white)
                        .frame(width: 36, height: 36)
                        .background(
                            Circle().fill(
                                LinearGradient(
                                    colors: [Color(red: 0.655, green: 0.545, blue: 1.0),
                                             Color(red: 0.486, green: 0.361, blue: 1.0)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                        )
                        .overlay(Circle().strokeBorder(Color.white.opacity(0.55), lineWidth: 1))
                        .shadow(color: Color(red: 0.486, green: 0.361, blue: 1.0).opacity(0.30), radius: 6, y: 2)
                        .opacity(canSend ? 1 : 0.75)
                }
                .buttonStyle(KineticPressStyle())
                .disabled(!canSend)
                .accessibilityLabel("Send")
            }

            // The reference composer carries a trailing mic; it opens the real
            // voice flow.
            Button(action: onVoice) {
                Image(systemName: "mic")
                    .font(.system(size: 17))
                    .foregroundStyle(Aero.text.opacity(0.7))
                    .frame(width: 38, height: 38)
            }
            .buttonStyle(KineticPressStyle())
            .accessibilityLabel("Voice")
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 5)
        .gsCapsuleGlass(.floatingControl)
    }
}
