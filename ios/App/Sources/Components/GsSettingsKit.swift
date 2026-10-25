import SwiftUI

/// SETTINGS KIT — the three Settings mocks' shared primitives, measured from
/// the committed references (docs/reference/settings/settings-geometry.json):
/// card inset 21.8pt · pastel wells 28–30pt · rows 52pt min with a 50pt-indented
/// hairline · section title 17 + right caption 12 · compact peach pills.
///
/// Additive on purpose: `AeroListRow`, `AeroCard` and `SectionHeader` are
/// FROZEN and structurally wrong for these mocks (each row paints its own
/// tile), and ~8 other screens compose them. The settings screens compose this
/// kit instead. Cards use `.cardLight` — the measured 45% role with NO
/// material: `.card`'s `.regularMaterial` is both the wrong fill and the
/// expensive path (three offscreen layers per card, backdrop re-sampled).
struct GsSettingsKit {}

// MARK: - Backdrop

/// The pearl field behind all three settings screens. Light keeps the authored
/// backdrop; dark falls back to the gradient + pearls rule the rest of the app
/// already obeys. New view on purpose: `ExploreView.swift` is pixel-gated and
/// must not be edited for reuse.
struct GsSettingsBackdrop: View {
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        if scheme == .dark {
            ZStack {
                LinearGradient(
                    colors: [Color(red: 1, green: 0.70, blue: 0.43).opacity(0.30), Aero.background],
                    startPoint: .topLeading, endPoint: .center
                )
                PearlBackdrop()
            }
            .ignoresSafeArea()
        } else {
            Image("GSHomeBackdrop")
                .resizable()
                .scaledToFill()
                .ignoresSafeArea()
        }
    }
}

// MARK: - Page header

/// Back well + title + subtitle, laid out like the mocks: a 36pt glass circle
/// at the leading edge, then the title block offset 62pt from that edge.
struct GsSettingsHeader: View {
    let title: String
    var subtitle: String?
    var onBack: (() -> Void)?

    var body: some View {
        HStack(alignment: .center, spacing: 14) {
            if let onBack {
                Button(action: onBack) {
                    NexlyGlyph(name: "chevron-left", size: 16, color: Aero.text)
                        .frame(width: 36, height: 36)
                        .gsGlass(.floatingControl, corner: 18)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Back")
            } else {
                Color.clear.frame(width: 36, height: 36)
            }
            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                    .font(.custom(Aero.productName(.semibold), size: 18, relativeTo: .title3))
                    .foregroundStyle(Aero.text)
                if let subtitle {
                    Text(subtitle)
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textTertiary)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 22)
        .padding(.top, 6)
        .padding(.bottom, 10)
    }
}

// MARK: - Section head

/// Section title with the mock's right-aligned whisper caption.
struct GsGroupHeader: View {
    let title: String
    var caption: String?

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title)
                .font(.custom(Aero.productName(.semibold), size: 17, relativeTo: .headline))
                .foregroundStyle(Aero.text)
                .accessibilityAddTraits(.isHeader)
            Spacer()
            if let caption {
                Text(caption)
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textTertiary)
                    .lineLimit(1)
            }
        }
        .padding(.horizontal, 4)
        .padding(.top, 14)
        .padding(.bottom, 7)
    }
}

// MARK: - Card + rows

/// One grouped glass card. Rows carry their own bottom hairline so the mock's
/// internal separators survive any content shape (pass `showDivider: false`
/// on the last row).
struct GsGroupCard<Content: View>: View {
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(spacing: 0) {
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .modifier(GsGroupCardGlass())
    }
}

private struct GsGroupCardGlass: ViewModifier {
    func body(content: Content) -> some View {
        content.gsGlass(.cardLight, corner: 22)
    }
}

/// The mock's row: leading well slot, title + detail, trailing slot.
struct GsSettingsRow<Leading: View, Trailing: View>: View {
    let title: String
    var detail: String?
    var showDivider: Bool = true
    var action: (() -> Void)?
    @ViewBuilder var leading: () -> Leading
    @ViewBuilder var trailing: () -> Trailing

    var body: some View {
        Group {
            if let action {
                Button(action: action) { row }
                    .buttonStyle(KineticPressStyle())
                    .accessibilityLabel(detail.map { "\(title), \($0)" } ?? title)
            } else {
                row
            }
        }
    }

    private var row: some View {
        HStack(spacing: 11) {
            leading()
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(Aero.bodyMedium().weight(.medium))
                    .foregroundStyle(Aero.text)
                    .lineLimit(1)
                if let detail {
                    Text(detail)
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textTertiary)
                        .lineLimit(2)
                }
            }
            Spacer(minLength: 8)
            trailing()
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 7)
        .frame(minHeight: 52)
        .overlay(alignment: .bottom) {
            if showDivider {
                Rectangle()
                    .fill(Aero.divider.opacity(0.7))
                    .frame(height: 1.0 / 3.0)
                    .padding(.leading, 50)
                    .padding(.trailing, 12)
            }
        }
    }
}

// MARK: - Wells, pills, strips

enum GsWellTone {
    case peach, rose, lilac, blue, mint

    var wash: Color {
        switch self {
        case .peach: return Color(red: 0.992, green: 0.910, blue: 0.855)
        case .rose: return Color(red: 0.984, green: 0.878, blue: 0.902)
        case .lilac: return Color(red: 0.929, green: 0.914, blue: 0.996)
        case .blue: return Color(red: 0.867, green: 0.922, blue: 0.988)
        case .mint: return Color(red: 0.867, green: 0.953, blue: 0.902)
        }
    }

    var ink: Color {
        switch self {
        case .peach: return Color(red: 0.722, green: 0.361, blue: 0.071)
        case .rose: return Color(red: 0.769, green: 0.200, blue: 0.400)
        case .lilac: return Color(red: 0.353, green: 0.314, blue: 0.839)
        case .blue: return Color(red: 0.086, green: 0.388, blue: 0.769)
        case .mint: return Color(red: 0.090, green: 0.478, blue: 0.314)
        }
    }
}

/// The 28–30pt pale circle beside a settings row.
struct GsWellTile: View {
    let glyph: String
    let tone: GsWellTone
    var size: CGFloat = 30

    var body: some View {
        ZStack {
            Circle()
                .fill(
                    LinearGradient(
                        colors: [.white.opacity(0.55), tone.wash],
                        startPoint: .top, endPoint: .bottom
                    )
                )
            Circle()
                .strokeBorder(.white.opacity(0.85), lineWidth: 1)
            NexlyGlyph(name: glyph, size: size * 0.46, color: tone.ink)
        }
        .frame(width: size, height: size)
        .shadow(color: tone.ink.opacity(0.16), radius: 3, x: 0, y: 2)
    }
}

/// The compact inline pill ("Upgrade →", "Manage →").
struct GsInlinePill: View {
    let label: String
    var trailingGlyph: String?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Text(label)
                    .font(Aero.caption().weight(.semibold))
                    .foregroundStyle(.white)
                if let trailingGlyph {
                    NexlyGlyph(name: trailingGlyph, size: 11, color: .white)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(
                Capsule().fill(
                    LinearGradient(
                        colors: [ExplorePalette.peachDeep, ExplorePalette.peach],
                        startPoint: .leading, endPoint: .trailing
                    )
                )
            )
            .overlay(
                Capsule().strokeBorder(
                    LinearGradient(
                        colors: [.white.opacity(0.85), .white.opacity(0.30)],
                        startPoint: .top, endPoint: .bottom
                    ),
                    lineWidth: 1
                )
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

/// One cell of the memory screen's four-up capability strip.
struct GsStripTile: Identifiable {
    let id: String
    let glyph: String
    let tone: GsWellTone
    let title: String
    let caption: String
}

/// Equal columns with vertical hairlines between them.
struct GsTileStrip: View {
    let tiles: [GsStripTile]

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            ForEach(tiles) { tile in
                VStack(spacing: 6) {
                    GsWellTile(glyph: tile.glyph, tone: tile.tone, size: 34)
                    Text(tile.title)
                        .font(.custom(Aero.productName(.semibold), size: 11.5, relativeTo: .caption))
                        .foregroundStyle(Aero.text)
                        .lineLimit(1)
                    Text(tile.caption)
                        .font(.custom(Aero.productName(.regular), size: 10, relativeTo: .caption2))
                        .foregroundStyle(Aero.textTertiary)
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                if tile.id != tiles.last?.id {
                    Rectangle()
                        .fill(Aero.divider.opacity(0.7))
                        .frame(width: 1.0 / 3.0, height: 60)
                        .padding(.top, 16)
                }
            }
        }
        .padding(.horizontal, 2)
    }
}

/// The quiet info card that closes Profile & Preferences and Memory.
struct GsInfoCard: View {
    let text: String
    var glyph: String = "sparkles"

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            NexlyGlyph(name: glyph, size: 15, color: ExplorePalette.apricotFg)
            Text(text)
                .font(.custom(Aero.productName(.regular), size: 11.5, relativeTo: .caption))
                .foregroundStyle(Aero.textTertiary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 14)
        .padding(.vertical, 13)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(Aero.glassSurface.opacity(0.55))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .strokeBorder(.white.opacity(0.55), lineWidth: 1)
        )
    }
}

// MARK: - Shared page frame

/// The three screens' common shell: pearl backdrop, header, scrolling content
/// in a LazyVStack so glass-card cost stays viewport-bounded.
struct GsSettingsPage<Content: View>: View {
    let title: String
    var subtitle: String?
    var onBack: (() -> Void)?
    @ViewBuilder var content: () -> Content

    var body: some View {
        ZStack {
            GsSettingsBackdrop().allowsHitTesting(false)
            VStack(spacing: 0) {
                GsSettingsHeader(title: title, subtitle: subtitle, onBack: onBack)
                ScrollView(showsIndicators: false) {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        content()
                        Spacer(minLength: 28)
                    }
                    .padding(.horizontal, 22)
                    .padding(.top, 2)
                }
            }
        }
        .toolbar(.hidden, for: .navigationBar)
    }
}
