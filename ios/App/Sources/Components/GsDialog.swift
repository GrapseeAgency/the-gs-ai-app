import SwiftUI

/*
 * THE LAST STOCK SURFACE — iOS twin of `GsDialogs.kt`.
 *
 * Every confirmation, picker and "here is the answer" popup in this app was a
 * stock `.alert` or a half-height `.sheet`: system chrome inside a surface made
 * of light. The sheet detent is the wrong shape for a decision (it covers the
 * row that caused it), and an alert's grey box is not this app's material.
 *
 * These three views replace both. One slab, three jobs:
 *
 *  - the panel is a floating piece of glass that sits LOW on the page, clears
 *    the edges and throws a real shadow, so it reads as an object above the
 *    content rather than a screen you were sent to;
 *  - it grows out of its own centre on a spring (0.92 → 1) instead of sliding;
 *  - the scrim is thin — a decision needs to see what it is deciding about;
 *  - the confirm action is the app's primary pill, the dismiss a quiet line
 *    under it, and a destructive confirm is the only red on screen.
 *
 * Call sites present them with `.gsDialog(isPresented:)`, which is an overlay —
 * no navigation push, no sheet detent, no system alert.
 */

// MARK: - The frame

/// The scrim + slab. `content` describes only the words and controls.
struct GsDialogFrame<Content: View>: View {
    var onDismiss: () -> Void
    @ViewBuilder var content: () -> Content

    @Environment(\.colorScheme) private var scheme
    @State private var shown = false

    var body: some View {
        ZStack(alignment: .bottom) {
            (scheme == .dark ? Color.black.opacity(0.52) : Color.black.opacity(0.26))
                .opacity(shown ? 1 : 0)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture { onDismiss() }

            content()
                .padding(.horizontal, 18)
                .padding(.bottom, 26)
                .background(
                    RoundedRectangle(cornerRadius: 28, style: .continuous)
                        .fill(Aero.dialogSurface.opacity(scheme == .dark ? 0.94 : 0.92))
                )
                .gsGlass(.modal, corner: 28)
                .overlay(
                    RoundedRectangle(cornerRadius: 28, style: .continuous)
                        .strokeBorder(
                            Color.white.opacity(scheme == .dark ? 0.10 : 0.55),
                            lineWidth: 0.8)
                )
                .shadow(color: Color.black.opacity(scheme == .dark ? 0.55 : 0.22), radius: 30, x: 0, y: 12)
                .scaleEffect(shown ? 1 : 0.92, anchor: .bottom)
                .opacity(shown ? 1 : 0)
        }
        .onAppear {
            withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) { shown = true }
        }
        .accessibilityAddTraits(.isModal)
    }
}

extension View {
    /// Present a glass dialog as an overlay. No sheet, no alert.
    func gsDialog<C: View>(
        isPresented: Bool,
        @ViewBuilder _ content: @escaping () -> C
    ) -> some View {
        overlay {
            if isPresented { content() }
        }
    }
}

// MARK: - Yes / no

struct GsDialog: View {
    let title: String
    let message: String
    let confirmLabel: String
    var onConfirm: () -> Void
    var onDismiss: () -> Void
    var dismissLabel: String? = "Cancel"
    var destructive: Bool = false
    var glyph: String? = nil
    var tone: GsWellTone = .peach
    var footnote: String? = nil

    var body: some View {
        GsDialogFrame(onDismiss: onDismiss) {
            VStack(spacing: 0) {
                if let glyph {
                    GsWellTile(glyph: glyph, tone: tone, size: 46)
                        .padding(.bottom, 14)
                }
                Text(title)
                    .font(.custom(Aero.productName(.semibold), size: 16, relativeTo: .headline))
                    .foregroundStyle(Aero.text)
                    .multilineTextAlignment(.center)
                Spacer().frame(height: 7)
                Text(message)
                    .font(.custom(Aero.productName(.regular), size: 12.5, relativeTo: .footnote))
                    .lineSpacing(3)
                    .foregroundStyle(Aero.textSecondary)
                    .multilineTextAlignment(.center)
                Spacer().frame(height: 18)

                Button {
                    onConfirm()
                    onDismiss()
                } label: {
                    Text(confirmLabel)
                        .font(.custom(Aero.productName(.semibold), size: 13.5, relativeTo: .callout))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(
                            Capsule().fill(
                                LinearGradient(
                                    colors: destructive
                                        ? [Aero.danger, Aero.danger.opacity(0.86)]
                                        : [Aero.accentStrong, Aero.accent],
                                    startPoint: .top, endPoint: .bottom))
                        )
                        .overlay(Capsule().strokeBorder(Color.white.opacity(0.30), lineWidth: 1))
                        .shadow(
                            color: (destructive ? Aero.danger : Aero.accent).opacity(0.42),
                            radius: 10, x: 0, y: 5)
                }
                .buttonStyle(KineticPressStyle())

                if let dismissLabel {
                    Button(action: onDismiss) {
                        Text(dismissLabel)
                            .font(.custom(Aero.productName(.regular), size: 12.5, relativeTo: .footnote))
                            .foregroundStyle(Aero.textSecondary)
                            .padding(.horizontal, 18)
                            .padding(.vertical, 11)
                    }
                    .buttonStyle(.plain)
                }
                if let footnote {
                    Text(footnote)
                        .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2))
                        .foregroundStyle(Aero.textTertiary)
                        .multilineTextAlignment(.center)
                        .padding(.top, 2)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 22)
        }
    }
}

// MARK: - Pick one

struct GsChoice: Identifiable {
    let id: String
    let label: String
    var detail: String? = nil
    var glyph: String? = nil
    var tone: GsWellTone = .peach
    var selected: Bool = false
}

struct GsChoiceDialog: View {
    let title: String
    var subtitle: String? = nil
    let options: [GsChoice]
    var onSelect: (GsChoice) -> Void
    var onDismiss: () -> Void

    var body: some View {
        GsDialogFrame(onDismiss: onDismiss) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 10) {
                    VStack(alignment: .leading, spacing: 1) {
                        Text(title)
                            .font(.custom(Aero.productName(.semibold), size: 14, relativeTo: .subheadline))
                            .foregroundStyle(Aero.text)
                        if let subtitle {
                            Text(subtitle)
                                .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2))
                                .foregroundStyle(Aero.textTertiary)
                        }
                    }
                    Spacer()
                    Button(action: onDismiss) {
                        NexlyGlyph(name: "x", size: 13, color: Aero.textTertiary)
                            .frame(width: 26, height: 26)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Close")
                }
                .padding(.bottom, 10)

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 2) {
                        ForEach(options) { option in
                            Button {
                                onSelect(option)
                            } label: {
                                HStack(spacing: 10) {
                                    if let glyph = option.glyph {
                                        GsWellTile(glyph: glyph, tone: option.tone, size: 30)
                                    } else {
                                        ZStack {
                                            RoundedRectangle(cornerRadius: 10)
                                                .fill(Aero.accent.opacity(0.10))
                                            NexlyGlyph(name: "check", size: 13, color: Aero.accentStrong)
                                        }
                                        .frame(width: 30, height: 30)
                                    }
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(option.label)
                                            .font(.custom(
                                                Aero.productName(option.selected ? .semibold : .medium),
                                                size: 12, relativeTo: .footnote))
                                            .foregroundStyle(Aero.text)
                                        if let detail = option.detail {
                                            Text(detail)
                                                .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2))
                                                .foregroundStyle(Aero.textTertiary)
                                                .lineLimit(2)
                                        }
                                    }
                                    Spacer(minLength: 6)
                                    if option.selected {
                                        NexlyGlyph(name: "circle-check", size: 15, color: Aero.accentStrong)
                                    }
                                }
                                .padding(.horizontal, 8)
                                .padding(.vertical, 9)
                                .background(
                                    RoundedRectangle(cornerRadius: 14)
                                        .fill(option.selected ? Aero.accent.opacity(0.10) : .clear)
                                )
                            }
                            .buttonStyle(.plain)
                            .accessibilityAddTraits(option.selected ? .isSelected : [])
                        }
                    }
                }
                .frame(maxHeight: 380)
            }
            .padding(18)
        }
    }
}

// MARK: - Ask for one line

struct GsPromptDialog: View {
    let title: String
    let fieldLabel: String
    var initial: String
    var message: String? = nil
    var placeholder: String = ""
    var confirmLabel: String = "Save"
    var onConfirm: (String) -> Void
    var onDismiss: () -> Void

    @State private var draft: String
    @FocusState private var focused: Bool

    init(
        title: String,
        fieldLabel: String,
        initial: String,
        message: String? = nil,
        placeholder: String = "",
        confirmLabel: String = "Save",
        onConfirm: @escaping (String) -> Void,
        onDismiss: @escaping () -> Void
    ) {
        self.title = title
        self.fieldLabel = fieldLabel
        self.initial = initial
        self.message = message
        self.placeholder = placeholder
        self.confirmLabel = confirmLabel
        self.onConfirm = onConfirm
        self.onDismiss = onDismiss
        _draft = State(initialValue: initial)
    }

    private var submit: () -> Void {
        {
            let trimmed = draft.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty else { return }
            onConfirm(trimmed)
            onDismiss()
        }
    }

    var body: some View {
        GsDialogFrame(onDismiss: onDismiss) {
            VStack(alignment: .leading, spacing: 0) {
                Text(title)
                    .font(.custom(Aero.productName(.semibold), size: 14, relativeTo: .subheadline))
                    .foregroundStyle(Aero.text)
                if let message {
                    Text(message)
                        .font(.custom(Aero.productName(.regular), size: 11, relativeTo: .caption))
                        .foregroundStyle(Aero.textTertiary)
                        .padding(.top, 3)
                }
                Spacer().frame(height: 12)

                TextField(placeholder.isEmpty ? fieldLabel : placeholder, text: $draft)
                    .font(.custom(Aero.productName(.regular), size: 13, relativeTo: .body))
                    .foregroundStyle(Aero.text)
                    .focused($focused)
                    .submitLabel(.done)
                    .onSubmit(submit)
                    .padding(.horizontal, 14)
                    .frame(height: 46)
                    .background(
                        RoundedRectangle(cornerRadius: 16)
                            .fill(Aero.inputSurface)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .strokeBorder(Aero.outline, lineWidth: 1)
                    )
                    .accessibilityLabel(fieldLabel)

                Spacer().frame(height: 14)

                HStack(spacing: 8) {
                    Button(action: onDismiss) {
                        Text("Cancel")
                            .font(.custom(Aero.productName(.regular), size: 12.5, relativeTo: .footnote))
                            .foregroundStyle(Aero.textSecondary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 13)
                    }
                    .buttonStyle(.plain)

                    Button(action: submit) {
                        Text(confirmLabel)
                            .font(.custom(Aero.productName(.semibold), size: 12.5, relativeTo: .footnote))
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 46)
                            .background(
                                Capsule().fill(
                                    LinearGradient(
                                        colors: [Aero.accentStrong, Aero.accent],
                                        startPoint: .top, endPoint: .bottom))
                            )
                            .overlay(Capsule().strokeBorder(Color.white.opacity(0.28), lineWidth: 1))
                    }
                    .buttonStyle(KineticPressStyle())
                    .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    .opacity(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0.5 : 1)
                }
            }
            .padding(18)
        }
        .onAppear { focused = true }
    }
}
