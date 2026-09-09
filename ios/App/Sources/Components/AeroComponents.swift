import SwiftUI

/**
 * AERUO KINETIC shared components — FROZEN API. All screens compose these.
 *
 * Task 86-e — High contrast: the components below that resolve `Aero.textMuted`
 * or `Aero.outline` carry an `hc` observation handle on `SettingsStore` and
 * touch `hc.highContrast` at the top of `body`, so a settings flip
 * re-evaluates the component and the computed tokens (DesignSystem.swift)
 * resolve afresh. Call-site shape is untouched.
 */

// MARK: - Section header

struct SectionHeader: View {
    let title: String
    var actionTitle: String? = nil
    var action: (() -> Void)? = nil

    var body: some View {
        HStack {
            Text(title).font(Aero.title()).foregroundStyle(Aero.text)
                .accessibilityAddTraits(.isHeader)
            Spacer()
            if let actionTitle, let action {
                Button(actionTitle, action: action)
                    .font(Aero.label())
                    .foregroundStyle(Aero.accent)
                    .buttonStyle(KineticPressStyle())
            }
        }
    }
}

// MARK: - Card

struct AeroCard<Content: View>: View {
    var action: (() -> Void)? = nil
    @ViewBuilder let content: () -> Content

    /// High-contrast re-resolve handle (Task 86-e).
    @ObservedObject var hc = SettingsStore.shared

    var body: some View {
        let _ = hc.highContrast
        Group {
            if let action {
                Button(action: action) {
                    cardBody
                }.buttonStyle(KineticPressStyle())
            } else {
                cardBody
            }
        }
    }

    private var cardBody: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            content()
        }
        .padding(Aero.Spacing.m)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: Aero.Radius.card).fill(Aero.surface))
        .overlay(RoundedRectangle(cornerRadius: Aero.Radius.card).stroke(Aero.outline, lineWidth: 1))
        .aeroCardShadow()
    }
}

// MARK: - Chip

struct AeroChip: View {
    let text: String
    var selected: Bool = false
    var action: () -> Void = {}

    var body: some View {
        Button(action: action) {
            Text(text)
                .font(Aero.label())
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(
                    Capsule().fill(selected ? AnyShapeStyle(Aero.accent) : AnyShapeStyle(Aero.raisedSurface))
                )
                .foregroundStyle(selected ? Aero.onAccent : Aero.text)
        }
        .buttonStyle(KineticPressStyle())
        // Filter/toggle chips are buttons that carry selection state —
        // VoiceOver announces "selected" instead of silently dropping it.
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}

// MARK: - List row

struct AeroListRow<Leading: View, Trailing: View>: View {
    let title: String
    var subtitle: String? = nil
    @ViewBuilder var leading: () -> Leading
    @ViewBuilder var trailing: () -> Trailing
    var action: (() -> Void)? = nil

    /// High-contrast re-resolve handle (Task 86-e).
    @ObservedObject var hc = SettingsStore.shared

    var body: some View {
        let _ = hc.highContrast
        Group {
            if let action {
                Button(action: action) { row }.buttonStyle(KineticPressStyle())
            } else {
                row
            }
        }
    }

    private var row: some View {
        HStack(spacing: Aero.Spacing.s + 4) {
            leading()
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(Aero.title()).foregroundStyle(Aero.text).lineLimit(1)
                if let subtitle {
                    Text(subtitle).font(Aero.caption()).foregroundStyle(Aero.textMuted).lineLimit(1)
                }
            }
            Spacer()
            trailing()
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(RoundedRectangle(cornerRadius: Aero.Radius.md).fill(Aero.raisedSurface))
    }
}

extension AeroListRow where Leading == DefaultLeading, Trailing == EmptyView {
    init(title: String, subtitle: String? = nil, action: (() -> Void)? = nil) {
        self.title = title
        self.subtitle = subtitle
        self.action = action
        self.leading = { DefaultLeading() }
        self.trailing = { EmptyView() }
    }
}

struct DefaultLeading: View {
    /// High-contrast re-resolve handle (Task 86-e).
    @ObservedObject var hc = SettingsStore.shared

    var body: some View {
        let _ = hc.highContrast
        Image(systemName: "circle.fill")
            .foregroundStyle(Aero.outline)
            .font(.system(size: 8))
    }
}

// MARK: - Universal input bar

struct AeroInputBar: View {
    @Binding var text: String
    var placeholder: String = "Ask anything…"
    var action: (() -> Void)? = nil
    /// Exposed so parents can raise (or drop) the keyboard programmatically —
    /// ChatDetailView focuses the composer when a starter chip seeds the draft.
    var focus: FocusState<Bool>.Binding? = nil

    /// Search-field conventions: `.search` return key, no autocapitalisation,
    /// no autocorrect — visuals unchanged. Chat composers stay as they were.
    var searchField: Bool = false
    /// Return-key commit for search fields — defaults to retiring the
    /// keyboard, the behaviour a reader expects when they hit "search".
    var onCommit: (() -> Void)? = nil

    /// Returns are read at submit time: Enter-to-send fires the send closure
    /// only when the setting is on; when it is off the vertical-axis field's
    /// default applies — Return inserts a newline and nothing sends.
    private var enterToSend: Bool { SettingsStore.shared.enterToSend }

    /// High-contrast re-resolve handle (Task 86-e) — the input bar strokes
    /// `Aero.outline` around its capsule.
    @ObservedObject var hc = SettingsStore.shared

    @State private var lastSubmitAt = Date.distantPast
    @FocusState private var searchFieldFocused: Bool

    var body: some View {
        let _ = hc.highContrast
        HStack(spacing: Aero.Spacing.s) {
            Image(systemName: "sparkles").foregroundStyle(Aero.accent)
            field
            if let action {
                Button(action: action) {
                    Image(systemName: "arrow.up.circle.fill")
                        .font(.system(size: 24))
                        .foregroundStyle(text.isEmpty ? Aero.textMuted : Aero.accent)
                }
                .buttonStyle(KineticPressStyle())
                .disabled(text.isEmpty)
                .accessibilityLabel(searchField ? "Search" : "Send")
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Capsule().fill(Aero.inputSurface))
        .overlay(Capsule().stroke(Aero.outline, lineWidth: 1))
    }

    /// The send gate is the send button's gate, verbatim: non-empty text,
    /// Enter-to-send on, and one send per keypress (in-flight guarding stays
    /// with the caller's send closure, exactly as before).
    private func submitFromKeyboard() {
        guard enterToSend, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        let now = Date()
        guard now.timeIntervalSince(lastSubmitAt) > 0.15 else { return }
        lastSubmitAt = now
        action?()
    }

    @ViewBuilder
    private var field: some View {
        if searchField {
            TextField(placeholder, text: $text)
                .font(Aero.body())
                .foregroundStyle(Aero.text)
                .focused($searchFieldFocused)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.search)
                .onSubmit {
                    if let onCommit { onCommit() }
                    searchFieldFocused = false
                }
        } else {
            TextField(placeholder, text: $text, axis: .vertical)
                .font(Aero.body())
                .foregroundStyle(Aero.text)
                .lineLimit(1...8)
                .submitLabel(.send)
                .onSubmit(submitFromKeyboard)
                .gsFocus(focus)
                .onChange(of: text) { newValue in
                    // Multi-line fields land Return as a trailing newline
                    // instead of calling onSubmit on some iOS builds — the
                    // newline is the submit signal when Enter-to-send is on.
                    // The 150 ms window makes an onSubmit+newline double fire
                    // (both paths for one keypress) a single send.
                    guard enterToSend, newValue.hasSuffix("\n") else { return }
                    text = String(newValue.dropLast())
                    submitFromKeyboard()
                }
        }
    }
}

/// Optional focus wiring — `.focused(_:)` needs a non-optional binding, so
/// the nil case (search bars without programmatic focus) passes through.
private struct GSFocusModifier: ViewModifier {
    let focus: FocusState<Bool>.Binding?

    func body(content: Content) -> some View {
        if let focus {
            content.focused(focus)
        } else {
            content
        }
    }
}

private extension View {
    func gsFocus(_ focus: FocusState<Bool>.Binding?) -> some View {
        modifier(GSFocusModifier(focus: focus))
    }
}

// MARK: - States (part of the product)

struct EmptyStateView: View {
    let icon: String
    let title: String
    var message: String = ""

    /// High-contrast re-resolve handle (Task 86-e).
    @ObservedObject var hc = SettingsStore.shared

    var body: some View {
        let _ = hc.highContrast
        VStack(spacing: Aero.Spacing.s) {
            Image(systemName: icon)
                .font(.system(size: 30))
                .foregroundStyle(Aero.textMuted)
                .frame(width: 72, height: 72)
                .background(Circle().fill(Aero.raisedSurface))
            Text(title).font(Aero.title()).foregroundStyle(Aero.text)
            if !message.isEmpty {
                Text(message)
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, Aero.Spacing.l)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Aero.Spacing.xl)
    }
}

/// Aurora-pulsed loading — the AI is alive.
struct LoadingView: View {
    var label: String = "Thinking"
    @State private var phase: CGFloat = 0

    /// High-contrast re-resolve handle (Task 86-e).
    @ObservedObject var hc = SettingsStore.shared

    var body: some View {
        let _ = hc.highContrast
        VStack(spacing: Aero.Spacing.m) {
            LinearGradient(colors: Aero.aurora, startPoint: .leading, endPoint: .trailing)
                .frame(height: 8)
                .clipShape(RoundedRectangle(cornerRadius: Aero.Radius.card))
                .opacity(phase > 0.5 ? 1 : 0.45)
                .accessibilityHidden(true)
            Text(label).font(Aero.label()).foregroundStyle(Aero.textMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Aero.Spacing.l)
        .onAppear {
            // Reduce animations / Reduce motion: the pulse holds a static frame.
            guard !SettingsStore.shared.animationReduced else { return }
            withAnimation(.easeInOut(duration: 0.7).repeatForever(autoreverses: true)) {
                phase = 1
            }
        }
    }
}

struct ErrorStateView: View {
    let message: String
    var retry: () -> Void = {}

    @State private var announced = false

    /// High-contrast re-resolve handle (Task 86-e).
    @ObservedObject var hc = SettingsStore.shared

    var body: some View {
        let _ = hc.highContrast
        VStack(spacing: Aero.Spacing.s) {
            Text(message).font(Aero.body()).foregroundStyle(Aero.textMuted)
            Button("Try again", action: retry)
                .font(Aero.label())
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(Capsule().fill(Aero.raisedSurface))
                .foregroundStyle(Aero.text)
                .buttonStyle(KineticPressStyle())
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Aero.Spacing.l)
        .onAppear {
            // One error tick per presentation — a fresh instance mounts for
            // every new failure, so retry → fail ticks again.
            guard !announced else { return }
            announced = true
            GSHaptics.error()
        }
    }
}

struct OfflineBanner: View {
    var isVisible: Bool

    /// High-contrast re-resolve handle (Task 86-e).
    @ObservedObject var hc = SettingsStore.shared

    var body: some View {
        let _ = hc.highContrast
        if isVisible {
            HStack(spacing: Aero.Spacing.s) {
                Image(systemName: "wifi.slash").font(.system(size: 12))
                Text("You're offline — changes will sync when you reconnect.")
                    .font(Aero.label())
            }
            .foregroundStyle(Aero.textMuted)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(RoundedRectangle(cornerRadius: Aero.Radius.sm).fill(Aero.elevatedSurface))
        }
    }
}

struct SkeletonBlock: View {
    var height: CGFloat = 64
    @State private var on: Bool = false

    var body: some View {
        RoundedRectangle(cornerRadius: Aero.Radius.sm)
            .fill(Aero.raisedSurface.opacity(on ? 0.9 : 0.45))
            .frame(height: height)
            .accessibilityHidden(true)
            .onAppear {
                // Reduce animations / Reduce motion: the shimmer holds a static frame.
                guard !SettingsStore.shared.animationReduced else { return }
                withAnimation(.easeInOut(duration: 0.7).repeatForever(autoreverses: true)) {
                    on = true
                }
            }
    }
}

// MARK: - Quick action tile

struct QuickActionTile: View {
    let label: String
    let icon: String
    var action: () -> Void = {}

    /// High-contrast re-resolve handle (Task 86-e).
    @ObservedObject var hc = SettingsStore.shared

    var body: some View {
        let _ = hc.highContrast
        Button(action: action) {
            VStack(spacing: Aero.Spacing.s) {
                Image(systemName: icon)
                    .font(.system(size: 22))
                    .foregroundStyle(Aero.text)
                    .frame(width: 56, height: 56)
                    .background(RoundedRectangle(cornerRadius: Aero.Radius.card).fill(Aero.raisedSurface))
                Text(label)
                    .font(Aero.label())
                    .foregroundStyle(Aero.textMuted)
                    .lineLimit(1)
            }
        }
        .buttonStyle(KineticPressStyle())
    }
}

// MARK: - Aurora indicator (AI-active moments)

struct AuroraIndicator: View {
    @State private var shift: Bool = false

    var body: some View {
        HStack(spacing: 6) {
            ForEach(0..<3, id: \.self) { i in
                Circle()
                    .fill(Aero.aurora[i % Aero.aurora.count])
                    .frame(width: 8, height: 8)
                    .opacity(shift ? 1 : 0.35)
                    .animation(
                        SettingsStore.shared.animationReduced
                            ? nil
                            : .easeInOut(duration: 0.6).repeatForever(autoreverses: true)
                                .delay(Double(i) * 0.18),
                        value: shift
                    )
            }
        }
        .accessibilityHidden(true)
        .onAppear {
            // Reduce animations / Reduce motion: the dots hold a static frame.
            guard !SettingsStore.shared.animationReduced else { return }
            shift = true
        }
    }
}


// MARK: - Button hierarchy (foundation primitives — Step 1)
//
// Primary → the one dominant action. Secondary → supporting. Tertiary →
// contextual text action. Destructive → irreversible operations only.
// Structured md corners — pills stay reserved for chips/status.

enum AeroButtonVariant {
    case primary, secondary, tertiary, destructive
}

struct AeroButton: View {
    let label: String
    let action: () -> Void
    var variant: AeroButtonVariant = .primary
    var compact: Bool = false
    var enabled: Bool = true
    var systemImage: String? = nil

    var body: some View {
        Button(action: action) {
            HStack(spacing: Aero.Spacing.s) {
                if let systemImage {
                    Image(systemName: systemImage)
                        .font(.system(size: compact ? 13 : 15, weight: .medium))
                }
                Text(label).font(Aero.button())
            }
            .padding(.horizontal, compact ? Aero.Spacing.control : Aero.Spacing.m)
            .frame(minHeight: compact ? 32 : 44)
            .background(
                RoundedRectangle(cornerRadius: Aero.Radius.md)
                    .fill(backgroundFill)
            )
            .overlay(
                RoundedRectangle(cornerRadius: Aero.Radius.md)
                    .stroke(borderColor ?? .clear, lineWidth: borderColor == nil ? 0 : 1)
            )
            .foregroundStyle(foreground)
            .opacity(enabled ? 1 : 0.45)
        }
        .buttonStyle(KineticPressStyle())
        .disabled(!enabled)
        .accessibilityLabel(label)
    }

    private var backgroundFill: Color {
        switch variant {
        case .primary: return Aero.accent
        case .secondary: return Aero.raisedSurface
        case .tertiary: return .clear
        case .destructive: return Aero.danger
        }
    }
    private var foreground: Color {
        switch variant {
        case .primary: return Aero.onAccent
        case .secondary: return Aero.text
        case .tertiary: return Aero.accent
        case .destructive: return Aero.onError
        }
    }
    private var borderColor: Color? {
        switch variant {
        case .secondary: return Aero.outline
        default: return nil
        }
    }
}

/// 44pt circular icon button (touch-target rule).
struct AeroIconButton: View {
    let systemImage: String
    let accessibilityLabel: String
    let action: () -> Void
    var variant: AeroButtonVariant = .secondary
    var enabled: Bool = true

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 17, weight: .medium))
                .frame(width: 44, height: 44)
                .background(Circle().fill(variant == .primary ? AnyShapeStyle(Aero.accent) : AnyShapeStyle(Aero.raisedSurface)))
                .overlay(Circle().stroke(variant == .secondary ? Aero.outline : .clear, lineWidth: 1))
                .foregroundStyle(variant == .primary ? Aero.onAccent : Aero.text)
                .opacity(enabled ? 1 : 0.45)
        }
        .buttonStyle(KineticPressStyle())
        .disabled(!enabled)
        .accessibilityLabel(accessibilityLabel)
    }
}

// MARK: - Sheet shell (the one bottom-sheet language)
//
// Wrap the content of every .sheet / fullScreenCover: same grabber, surface,
// radius, padding and title treatment everywhere.

struct AeroSheetShell<Content: View>: View {
    var title: String? = nil
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            Capsule()
                .fill(Aero.outlineStrong)
                .frame(width: 36, height: 4)
                .frame(maxWidth: .infinity)
                .padding(.top, Aero.Spacing.s)
            if let title {
                Text(title)
                    .font(Aero.headline())
                    .foregroundStyle(Aero.text)
            }
            content()
        }
        .padding(.horizontal, Aero.Spacing.l)
        .padding(.bottom, Aero.Spacing.l)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Aero.sheetSurface.ignoresSafeArea(edges: .bottom))
    }
}

// MARK: Edge states

/// Blank or whitespace-only titles (legacy rows, interrupted syncs, stray
/// server data) can never render as an empty line: every conversation title
/// surface funnels through this fallback.
func gsConversationTitle(_ raw: String?) -> String {
    let trimmed = raw?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    return trimmed.isEmpty ? "Untitled chat" : trimmed
}
