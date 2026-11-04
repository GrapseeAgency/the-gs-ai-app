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
    /// The reference leads with a back arrow at the far left inside a
    /// conversation; nil keeps it away (e.g. the workspace root).
    var onBack: (() -> Void)? = nil
    @ViewBuilder var sparkleMenu: () -> SparkleMenu
    @ViewBuilder var menuMenu: () -> MenuMenu
    @Environment(\.colorScheme) private var colorScheme

    /// Lilac is the BRAND, not the app accent: the accent is peach, and a peach
    /// sparkle in a peach disc is the product losing its one fixed mark.
    /// The lit rim of a control disc: white in light, a whisper of it in dark.
    /// There is no `glassHighlight` token on this side; the material's rim is
    /// drawn by `gsGlass`, and this is the hairline that closes it.
    private var rimColor: Color {
        Color.white.opacity(colorScheme == .dark ? 0.18 : 0.65)
    }

    private var brandInk: Color {
        colorScheme == .dark
            ? Color(red: 0.73, green: 0.66, blue: 1.0)
            : Color(red: 0.56, green: 0.48, blue: 1.0)
    }

    var body: some View {
        HStack(spacing: 10) {
            // NO PLATES. Back, brand, sparkle and menu used to sit in four
            // frosted circles in a row, which read as a toolbar rather than a
            // title with actions — and the brand disc was pure white, the
            // brightest object on a dark screen. The glyphs are the affordance;
            // the touch target is 44pt and invisible.
            if let onBack {
                Button(action: onBack) {
                    NexlyGlyph(name: "arrow-left", size: 19, color: Aero.text)
                        .frame(width: 44, height: 44)
                        .gsGlass(.elevated, corner: 22)
                        .overlay(Circle().strokeBorder(rimColor, lineWidth: 1))
                        .contentShape(Circle())
                }
                .buttonStyle(KineticPressStyle())
                .accessibilityLabel("Back")
            }
            // The brand: a lilac sparkle inside a lilac-tinted glass disc. The
            // disc is the design; what was wrong before was its fill (opaque
            // white) and the cream band behind the whole header.
            NexlyGlyph(name: "sparkles", size: 21, color: brandInk)
                .frame(width: 44, height: 44)
                .gsGlass(.elevated, corner: 22)
                .background(Circle().fill(brandInk.opacity(0.16)))
                .overlay(Circle().strokeBorder(brandInk.opacity(0.30), lineWidth: 1))
                .accessibilityHidden(true)

            // One word. The tagline was a second line of chrome that repeated
            // what the sparkle and the name already said, and it pushed the
            // title high in a row of 44pt discs.
            Text("Nexly")
                .font(.system(size: 16.5, weight: .semibold))
                .foregroundStyle(NexlyInk.header)
                .lineLimit(1)

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
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
        // NO BACKGROUND AT ALL — same rule as Android. A veil, a fade, a
        // hairline: all three were still a band drawn across the top of a page
        // that is meant to be one continuous bloom. The header sits above the
        // transcript, not over it, so it needs no surface of its own.
    }

    private func headerIconButton<Menu: View>(
        glyph: String,
        label: String,
        action: @escaping () -> Void,
        @ViewBuilder menu: () -> Menu
    ) -> some View {
        Button(action: action) {
            NexlyGlyph(name: glyph, size: 19, color: Aero.text)
                .frame(width: 44, height: 44)
                .gsGlass(.elevated, corner: 22)
                .overlay(Circle().strokeBorder(rimColor, lineWidth: 1))
                .contentShape(Circle())
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
                // ONE trailing control, two verbs. The mic and the send disc
                // used to sit side by side; the mic now BECOMES the send arrow
                // the moment there is something to send, and becomes the mic
                // again when the field empties. Both glyphs ride the same 0…1
                // progress so the exchange can never desynchronise.
                Button(action: canSend ? onSend : onVoice) {
                    ZStack {
                        Circle()
                            .fill(
                                LinearGradient(
                                    colors: [Color(red: 0.655, green: 0.545, blue: 1.0),
                                             Color(red: 0.486, green: 0.361, blue: 1.0)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                            .opacity(morph)
                            .overlay(
                                Circle()
                                    .strokeBorder(Color.white.opacity(0.55), lineWidth: 1)
                                    .opacity(morph)
                            )
                        Circle()
                            .fill(Aero.surface.opacity(0.55))
                            .overlay(Circle().strokeBorder(Color.white.opacity(0.7), lineWidth: 1))
                            .opacity(1 - morph)
                        NexlyGlyph(name: "microphone", size: 17, color: Aero.text.opacity(0.75))
                            .opacity(1 - morph)
                            .scaleEffect(1 - 0.40 * morph)
                            .rotationEffect(.degrees(-90 * Double(morph)))
                        NexlyGlyph(name: "arrow-up", size: 17, color: .white)
                            .opacity(morph)
                            .scaleEffect(0.55 + 0.45 * morph)
                            .rotationEffect(.degrees(90 * Double(1 - morph)))
                    }
                    .frame(width: 36, height: 36)
                    .animation(
                        SettingsStore.shared.reduceMotion
                            ? nil
                            : .spring(response: 0.34, dampingFraction: 0.72),
                        value: canSend
                    )
                }
                .buttonStyle(KineticPressStyle())
                .accessibilityLabel(canSend ? "Send" : "Voice")
            }
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 5)
        .gsCapsuleGlass(.floatingControl)
    }

    /// The morph progress: 1 = send, 0 = voice. Reading it from `canSend`
    /// (instead of storing it) keeps the two glyph layers on one clock.
    private var morph: CGFloat { canSend ? 1 : 0 }
}
