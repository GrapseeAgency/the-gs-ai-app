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
                    NexlyGlyph(name: "arrow-left", size: 19, color: NexlyInk.header)
                        .frame(width: 44, height: 44)
                        .gsGlass(.elevated, corner: 22, metalEdge: true)
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

            // Preserve both brand lines from the supplied response references.
            VStack(alignment: .leading, spacing: 2) {
                Text("Nexly")
                    .font(.system(size: 16.5, weight: .semibold))
                    .foregroundStyle(NexlyInk.header)
                Text("Your AI companion")
                    .font(.system(size: 10.5))
                    .foregroundStyle(NexlyInk.headerMeta)
            }
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
            NexlyGlyph(name: glyph, size: 19, color: NexlyInk.header)
                .frame(width: 44, height: 44)
                .gsGlass(.elevated, corner: 22, metalEdge: true)
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
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var selectedMode: String = "auto"
    var onModeSelected: (String) -> Void = { _ in }
    var onAgentMenu: (() -> Void)? = nil
    var onModeMenu: (() -> Void)? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            TextField("Ask anything...", text: $text, axis: .vertical)
                .font(.system(size: 14))
                .foregroundStyle(Aero.text)
                .lineLimit(1...6)
                .focused(focused)
                .submitLabel(.send)
                .onSubmit(onSubmit)
                .accessibilityIdentifier("chat-composer-input")
                .onChange(of: text) { newValue in
                    guard SettingsStore.shared.enterToSend, newValue.hasSuffix("\n") else { return }
                    text = String(newValue.dropLast())
                    onSubmit()
                }
                .frame(minHeight: 24, alignment: .topLeading)
                .frame(maxWidth: .infinity, alignment: .leading)

            HStack(alignment: .center, spacing: 6) {

                Button(action: onAttach) {
                    NexlyGlyph(
                        name: "plus",
                        size: 19,
                        color: Color(red: 0.56, green: 0.48, blue: 1.0)
                    )
                    .frame(width: 38, height: 38)
                    .gsGlass(.floatingControl, corner: 19)
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

                Spacer(minLength: 0)
                if let onAgentMenu, let onModeMenu {
                    Button(action: onAgentMenu) { choiceLabel("Agent") }
                        .buttonStyle(KineticPressStyle())
                        .accessibilityLabel("Agent tasks")
                    Button(action: onModeMenu) { choiceLabel(selectedMode.capitalized) }
                        .buttonStyle(KineticPressStyle())
                        .accessibilityLabel("Response mode, \(selectedMode.capitalized)")
                } else {
                    // Standalone previews retain native menus; the actual chat
                    // supplies its full-screen glass menu host.
                    Menu {
                        Button("Web Search") { seed("Search the web for ") }
                        Button("Code") { seed("Help me code ") }
                        Button("Write") { seed("Help me write ") }
                        Button("Analyze") { seed("Analyze ") }
                    } label: {
                        choiceLabel("Agent")
                    }
                    .accessibilityLabel("Agent tasks")
    
                    Menu {
                        ForEach(["auto", "flash", "thinking"], id: \.self) { mode in
                            Button {
                                onModeSelected(mode)
                            } label: {
                                if selectedMode == mode {
                                    Label(mode.capitalized, systemImage: "checkmark")
                                } else {
                                    Text(mode.capitalized)
                                }
                            }
                        }
                    } label: {
                        choiceLabel(selectedMode.capitalized)
                    }
                    .accessibilityLabel("Response mode, \(selectedMode.capitalized)")
                    }
                if isStreaming {
                    Button(action: onStop) {
                        NexlyGlyph(name: "player-stop", size: 17, color: Aero.text)
                            .frame(width: 36, height: 36)
                            .background(Circle().fill(Aero.surface.opacity(0.55)))
                            .gsArgentComponentBorder(Circle(), fallback: Color.white.opacity(0.7))
                    }
                    .buttonStyle(KineticPressStyle())
                    .accessibilityLabel("Stop generating")
                } else {
                    // ONE trailing control, two verbs. The mic and the send disc
                    // used to sit side by side; the mic now BECOMES the send arrow
                    // the moment there is something to send, and becomes the mic
                    // again when the field empties. Mount the original MetalFx
                    // shader only in send mode, so an idle voice control does not
                    // keep a hidden motion sensor or metal ring running.
                    Button(action: canSend ? onSend : onVoice) {
                        ZStack {
                            if canSend {
                                GsMetallicSendFace()
                                    .transition(.opacity.combined(with: .scale(scale: 0.85)))
                            } else {
                                NexlyGlyph(name: "microphone", size: 17, color: Aero.text.opacity(0.75))
                                    .frame(width: 36, height: 36)
                                    .background(Circle().fill(Aero.surface.opacity(0.55)))
                                    .gsArgentComponentBorder(Circle(), fallback: Color.white.opacity(0.7))
                                    .transition(.opacity.combined(with: .scale(scale: 0.85)))
                            }
                        }
                        .frame(width: 44, height: 44)
                        .contentShape(Circle())
                        .animation(
                            (SettingsStore.shared.reduceMotion || SettingsStore.shared.animationReduced || reduceMotion)
                                ? nil
                                : .spring(response: 0.34, dampingFraction: 0.72),
                            value: canSend
                        )
                    }
                    .buttonStyle(KineticPressStyle())
                    .accessibilityLabel(canSend ? "Send" : "Voice")
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .gsGlass(.floatingControl, corner: 24)
    }

    private func seed(_ prefix: String) {
        if !text.hasPrefix(prefix) { text = prefix + text }
        focused.wrappedValue = true
    }

    private func choiceLabel(_ title: String) -> some View {
        HStack(spacing: 4) {
            Text(title)
                .font(.custom(Aero.productName(.medium), size: 11))
                .lineLimit(1)
            Image(systemName: "chevron.down")
                .font(.system(size: 10, weight: .medium))
        }
        .foregroundStyle(Aero.text)
        .padding(.horizontal, 10)
        .frame(height: 36)
        .gsGlass(.floatingControl, corner: 18)
    }
}
