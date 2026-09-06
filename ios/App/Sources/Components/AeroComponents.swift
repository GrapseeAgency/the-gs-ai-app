import SwiftUI

/**
 * AERUO KINETIC shared components — FROZEN API. All screens compose these.
 */

// MARK: - Section header

struct SectionHeader: View {
    let title: String
    var actionTitle: String? = nil
    var action: (() -> Void)? = nil

    var body: some View {
        HStack {
            Text(title).font(Aero.title()).foregroundStyle(Aero.text)
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

    var body: some View {
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
                    Capsule().fill(selected ? AnyShapeStyle(Aero.accent) : AnyShapeStyle(Aero.container))
                )
                .foregroundStyle(selected ? Color.white : Aero.text)
        }
        .buttonStyle(KineticPressStyle())
    }
}

// MARK: - List row

struct AeroListRow<Leading: View, Trailing: View>: View {
    let title: String
    var subtitle: String? = nil
    @ViewBuilder var leading: () -> Leading
    @ViewBuilder var trailing: () -> Trailing
    var action: (() -> Void)? = nil

    var body: some View {
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
        .background(RoundedRectangle(cornerRadius: 14).fill(Aero.container))
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
    var body: some View {
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

    var body: some View {
        HStack(spacing: Aero.Spacing.s) {
            Image(systemName: "sparkles").foregroundStyle(Aero.accent)
            TextField(placeholder, text: $text)
                .font(Aero.body())
                .foregroundStyle(Aero.text)
            if let action {
                Button(action: action) {
                    Image(systemName: "arrow.up.circle.fill")
                        .font(.system(size: 24))
                        .foregroundStyle(text.isEmpty ? Aero.textMuted : Aero.accent)
                }
                .buttonStyle(KineticPressStyle())
                .disabled(text.isEmpty)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Capsule().fill(Aero.container))
        .overlay(Capsule().stroke(Aero.outline, lineWidth: 1))
    }
}

// MARK: - States (part of the product)

struct EmptyStateView: View {
    let icon: String
    let title: String
    var message: String = ""

    var body: some View {
        VStack(spacing: Aero.Spacing.s) {
            Image(systemName: icon)
                .font(.system(size: 30))
                .foregroundStyle(Aero.textMuted)
                .frame(width: 72, height: 72)
                .background(Circle().fill(Aero.container))
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

    var body: some View {
        VStack(spacing: Aero.Spacing.m) {
            LinearGradient(colors: Aero.aurora, startPoint: .leading, endPoint: .trailing)
                .frame(height: 8)
                .clipShape(RoundedRectangle(cornerRadius: Aero.Radius.card))
                .opacity(phase > 0.5 ? 1 : 0.45)
            Text(label).font(Aero.label()).foregroundStyle(Aero.textMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Aero.Spacing.l)
        .onAppear {
            withAnimation(.easeInOut(duration: 0.7).repeatForever(autoreverses: true)) {
                phase = 1
            }
        }
    }
}

struct ErrorStateView: View {
    let message: String
    var retry: () -> Void = {}

    var body: some View {
        VStack(spacing: Aero.Spacing.s) {
            Text(message).font(Aero.body()).foregroundStyle(Aero.textMuted)
            Button("Try again", action: retry)
                .font(Aero.label())
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(Capsule().fill(Aero.container))
                .foregroundStyle(Aero.text)
                .buttonStyle(KineticPressStyle())
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Aero.Spacing.l)
    }
}

struct OfflineBanner: View {
    var isVisible: Bool
    var body: some View {
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
            .background(RoundedRectangle(cornerRadius: 10).fill(Aero.containerHigh))
        }
    }
}

struct SkeletonBlock: View {
    var height: CGFloat = 64
    @State private var on: Bool = false

    var body: some View {
        RoundedRectangle(cornerRadius: 12)
            .fill(Aero.container.opacity(on ? 0.9 : 0.45))
            .frame(height: height)
            .onAppear {
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

    var body: some View {
        Button(action: action) {
            VStack(spacing: Aero.Spacing.s) {
                Image(systemName: icon)
                    .font(.system(size: 22))
                    .foregroundStyle(Aero.text)
                    .frame(width: 56, height: 56)
                    .background(RoundedRectangle(cornerRadius: 18).fill(Aero.container))
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
                        .easeInOut(duration: 0.6).repeatForever(autoreverses: true)
                            .delay(Double(i) * 0.18),
                        value: shift
                    )
            }
        }
        .onAppear { shift = true }
    }
}
