import SwiftUI

/// REFERENCE SHEET KIT — the six owner sheets' shared primitives.
///
/// The settings kit (`GsSettingsKit`) is the hub's scale: 17pt group titles,
/// 15/12.5 rows, Material-sized switches. The six new sheets are denser. Their
/// ink, measured at 1.644 px/pt on the 393pt canvas:
///
///   page title    16.0 (Semibold)   page subtitle 10.5
///   group title   13.0              group caption 8.5–9.0
///   row title     10.0              row detail    8.0
///   one-line rows 45.0pt pitch      two-line rows 56.0pt pitch
///   switch        31×18, 16pt thumb
///
/// Composing the hub's 15pt rows here would make every sheet half again as
/// tall as its reference, which is why this kit exists rather than a flag on
/// the old one.
struct GsSheetKit {}

// MARK: - Measured type

enum GsSheetType {
    static func pageTitle() -> Font {
        .custom(Aero.productName(.semibold), size: 16, relativeTo: .headline)
    }
    static func pageSubtitle() -> Font {
        .custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2)
    }
    static func groupTitle() -> Font {
        .custom(Aero.productName(.semibold), size: 13, relativeTo: .subheadline)
    }
    static func groupCaption() -> Font {
        .custom(Aero.productName(.regular), size: 9, relativeTo: .caption2)
    }
    static func rowTitle() -> Font {
        .custom(Aero.productName(.medium), size: 10, relativeTo: .caption2)
    }
    static func rowDetail() -> Font {
        .custom(Aero.productName(.regular), size: 8, relativeTo: .caption2)
    }
    static func rowValue() -> Font {
        .custom(Aero.productName(.regular), size: 8.5, relativeTo: .caption2)
    }
    static func tileTitle() -> Font {
        .custom(Aero.productName(.medium), size: 10.5, relativeTo: .caption2)
    }
    static func tileCaption() -> Font {
        .custom(Aero.productName(.regular), size: 8.5, relativeTo: .caption2)
    }
    static func button() -> Font {
        .custom(Aero.productName(.semibold), size: 9.5, relativeTo: .caption2)
    }
    static func factRow() -> Font {
        .custom(Aero.productName(.regular), size: 9.5, relativeTo: .caption2)
    }
}

// MARK: - Page + header

/// Dense page shell: pearl backdrop, 22pt inset, back well + 16pt title.
struct GsSheetPage<Content: View>: View {
    let title: String
    var subtitle: String?
    var onBack: (() -> Void)?
    var headerTrailing: (() -> AnyView)?
    @ViewBuilder var content: () -> Content

    var body: some View {
        ZStack {
            GsSettingsBackdrop().allowsHitTesting(false)
            VStack(spacing: 0) {
                header
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

    private var header: some View {
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
                    .font(GsSheetType.pageTitle())
                    .foregroundStyle(Aero.text)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                if let subtitle {
                    Text(subtitle)
                        .font(GsSheetType.pageSubtitle())
                        .foregroundStyle(Aero.textTertiary)
                        .lineLimit(2)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer(minLength: 0)
            if let headerTrailing {
                headerTrailing()
            }
        }
        .padding(.horizontal, 22)
        .padding(.top, 6)
        .padding(.bottom, 10)
    }
}

/// The sheet's section header: 13pt title, 9pt whisper, optional leading
/// glyph and an optional real trailing link.
struct GsSheetGroupHeader: View {
    let title: String
    var caption: String?
    var glyph: String?
    var onCaption: (() -> Void)?
    var trailing: AnyView?

    init(
        _ title: String,
        caption: String? = nil,
        glyph: String? = nil,
        onCaption: (() -> Void)? = nil,
        trailing: AnyView? = nil
    ) {
        self.title = title
        self.caption = caption
        self.glyph = glyph
        self.onCaption = onCaption
        self.trailing = trailing
    }

    var body: some View {
        HStack(spacing: 6) {
            if let glyph {
                NexlyGlyph(name: glyph, size: 13, color: ExplorePalette.apricotFg)
            }
            Text(title)
                .font(GsSheetType.groupTitle())
                .foregroundStyle(Aero.text)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 8)
            if let caption {
                if let onCaption {
                    Button(action: onCaption) {
                        HStack(spacing: 4) {
                            Text(caption)
                                .font(GsSheetType.groupCaption())
                                .foregroundStyle(Aero.textTertiary)
                            NexlyGlyph(name: "arrow-right", size: 11, color: Aero.textTertiary)
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(caption)
                } else {
                    Text(caption)
                        .font(GsSheetType.groupCaption())
                        .foregroundStyle(Aero.textTertiary)
                        .lineLimit(1)
                }
            }
            if let trailing {
                trailing
            }
        }
        .padding(.horizontal, 0)
        .padding(.top, 16)
        .padding(.bottom, 7)
    }
}

// MARK: - Switch

/// The mock's 31×18 switch. `Toggle`'s default is the 51×31 system switch.
struct GsSheetSwitch: View {
    @Binding var isOn: Bool
    var enabled: Bool = true

    var body: some View {
        Button {
            guard enabled else { return }
            withAnimation(.spring(response: 0.28, dampingFraction: 0.8)) {
                isOn.toggle()
            }
        } label: {
            Capsule()
                .fill(
                    isOn
                        ? AnyShapeStyle(
                            LinearGradient(
                                colors: [ExplorePalette.peachDeep, ExplorePalette.peach],
                                startPoint: .leading, endPoint: .trailing))
                        : AnyShapeStyle(Color.white.opacity(0.62))
                )
                .frame(width: 34, height: 20)
                .overlay(
                    Capsule().strokeBorder(
                        isOn ? Color.white.opacity(0.42) : Aero.outline.opacity(0.55),
                        lineWidth: 1)
                )
                .overlay(alignment: isOn ? .trailing : .leading) {
                    Circle()
                        .fill(.white)
                        .frame(width: 16, height: 16)
                        .padding(.horizontal, 2)
                        .shadow(color: .black.opacity(0.14), radius: 1.5, y: 1)
                }
                .opacity(enabled ? 1 : 0.55)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityRepresentation {
            Toggle("", isOn: $isOn).labelsHidden().disabled(!enabled)
        }
    }
}

// MARK: - Pills, dots, progress

struct GsTagPill: View {
    let text: String
    var tone: Color = Aero.success

    var body: some View {
        Text(text)
            .font(.custom(Aero.productName(.medium), size: 8.5, relativeTo: .caption2))
            .foregroundStyle(tone)
            .lineLimit(1)
            .padding(.horizontal, 7)
            .padding(.vertical, 2.5)
            .background(Capsule().fill(tone.opacity(0.13)))
    }
}

/// The header's glass filter control.
struct GsHeaderAction: View {
    let glyph: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            NexlyGlyph(name: glyph, size: 15, color: Aero.textSecondary)
                .frame(width: 34, height: 34)
                .gsGlass(.floatingControl, corner: 17)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

/// The provider rows' "...".
struct GsDotsButton: View {
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            NexlyGlyph(name: "dots", size: 12, color: Aero.textSecondary)
                .frame(width: 26, height: 26)
                .background(Circle().fill(Aero.outline.opacity(0.25)))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

struct GsProgressBar: View {
    /// nil = not countable; draws the empty track and no fill.
    let fraction: Double?
    var tone: Color

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Capsule().fill(Aero.outline.opacity(0.30))
                if let fraction {
                    Capsule()
                        .fill(
                            LinearGradient(
                                colors: [tone.opacity(0.72), tone],
                                startPoint: .leading, endPoint: .trailing))
                        .frame(width: max(2, proxy.size.width * min(max(fraction, 0), 1)))
                }
            }
        }
        .frame(height: 4)
        .accessibilityHidden(true)
    }
}

// MARK: - Rows

/// The sheet's row: 10pt title, 8pt detail, 11pt of air above and below. The
/// 44/56pt heights are the mock's own pitch, not a guess.
struct GsSheetRow<Leading: View, Trailing: View>: View {
    let title: String
    var detail: String?
    /// A third line in the text column — the provider sheets draw their status
    /// pill under the scope, not in the trailing slot. Defaulted so no existing
    /// row changes height.
    var detailTrailing: AnyView? = nil
    /// A pill that belongs beside the title rather than in the trailing slot.
    /// The inbox's category tag is drawn inline in the mock; keeping it in the
    /// trailing column put it under the timestamp instead.
    var titleTrailing: AnyView? = nil
    var action: (() -> Void)?
    var showDivider: Bool = false
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
            VStack(alignment: .leading, spacing: 1) {
                HStack(spacing: 5) {
                    Text(title)
                        .font(GsSheetType.rowTitle())
                        .foregroundStyle(Aero.text)
                        .lineLimit(1)
                    if let titleTrailing {
                        titleTrailing
                    }
                }
                if let detail {
                    Text(detail)
                        .font(GsSheetType.rowDetail())
                        .foregroundStyle(Aero.textTertiary)
                        .lineLimit(2)
                        .fixedSize(horizontal: false, vertical: true)
                }
                if let detailTrailing {
                    Spacer().frame(height: 4)
                    detailTrailing
                }
            }
            Spacer(minLength: 8)
            trailing()
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10.5)
        .frame(minHeight: 44)
        .overlay(alignment: .bottom) {
            if showDivider {
                Rectangle()
                    .fill(Aero.divider.opacity(0.85))
                    .frame(height: 1.0 / 3.0)
                    .padding(.leading, 50)
                    .padding(.trailing, 12)
            }
        }
    }
}

struct GsSheetToggle<Leading: View>: View {
    let title: String
    var detail: String?
    @Binding var isOn: Bool
    var enabled: Bool = true
    @ViewBuilder var leading: () -> Leading

    var body: some View {
        HStack(spacing: 11) {
            leading()
            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                    .font(GsSheetType.rowTitle())
                    .foregroundStyle(enabled ? Aero.text : Aero.textSecondary)
                    .lineLimit(1)
                if let detail {
                    Text(detail)
                        .font(GsSheetType.rowDetail())
                        .foregroundStyle(Aero.textTertiary)
                        .lineLimit(2)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer(minLength: 8)
            GsSheetSwitch(isOn: $isOn, enabled: enabled)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10.5)
        .frame(minHeight: 44)
    }
}

// MARK: - Hero + tiles

private let gsHeroWash = [
    Color(red: 0.906, green: 0.859, blue: 0.984),
    Color(red: 0.976, green: 0.863, blue: 0.910),
    Color(red: 0.992, green: 0.910, blue: 0.847),
]

private let gsQuietWash = [
    Color(red: 0.937, green: 0.902, blue: 0.988),
    Color(red: 0.984, green: 0.914, blue: 0.945),
]

/// The pastel hero card, with the mock's white bloom in its upper-left third.
struct GsHeroCard<Content: View>: View {
    var quiet: Bool = false
    @ViewBuilder var content: () -> Content

    var body: some View {
        content()
            .padding(.horizontal, 12)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 20)
                    .fill(
                        LinearGradient(
                            colors: quiet ? gsQuietWash : gsHeroWash,
                            startPoint: .leading, endPoint: .trailing))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .strokeBorder(.white.opacity(0.55), lineWidth: 1)
            )
            .overlay(alignment: .topLeading) {
                RadialGradient(
                    colors: [.white.opacity(0.50), .clear],
                    center: .center, startRadius: 0, endRadius: 90)
                .frame(width: 150, height: 110)
                .offset(x: 8, y: -4)
                .allowsHitTesting(false)
            }
            .clipShape(RoundedRectangle(cornerRadius: 20))
    }
}

/// The mock's 3-up tile with its well, corner affordance and two-line caption.
struct GsSheetTile: View {
    enum Corner { case chevron, plus, bottomChevron, none }

    let title: String
    let caption: String
    var glyph: String?
    var tone: GsWellTone = .lilac
    var brandGlyph: String?
    var brandTint: Color?
    var corner: Corner = .chevron
    var centered: Bool = false
    var wellSize: CGFloat = 34
    var minHeight: CGFloat = 0
    /// Push the corner affordance to the card's bottom edge instead of letting
    /// it ride under the caption — the five "Available Integrations" tiles use
    /// it so their "+" marks share one baseline even when a caption wraps.
    var pinCorner: Bool = false
    var action: (() -> Void)?

    var body: some View {
        Group {
            if let action {
                Button(action: action) { tile }
                    .buttonStyle(KineticPressStyle())
                    .accessibilityLabel("\(title), \(caption)")
            } else {
                tile
            }
        }
    }

    private var tile: some View {
        VStack(alignment: centered ? .center : .leading, spacing: 0) {
            HStack {
                if let brandGlyph {
                    GsBrandWell(glyph: brandGlyph, size: wellSize, tint: brandTint)
                } else if let glyph {
                    GsWellTile(glyph: glyph, tone: tone, size: wellSize)
                }
                if centered { Spacer(minLength: 0) }
                if !centered { Spacer(minLength: 0) }
                switch corner {
                case .chevron:
                    NexlyGlyph(name: "chevron-right", size: 12, color: Aero.textTertiary)
                case .plus, .bottomChevron, .none:
                    EmptyView()
                }
            }
            Spacer().frame(height: centered ? 6 : 9)
            Text(title)
                .font(GsSheetType.tileTitle())
                .foregroundStyle(Aero.text)
                .lineLimit(1)
                .multilineTextAlignment(centered ? .center : .leading)
            Spacer().frame(height: 1)
            Text(caption)
                .font(GsSheetType.tileCaption())
                .foregroundStyle(Aero.textTertiary)
                .lineLimit(2)
                .multilineTextAlignment(centered ? .center : .leading)
                .fixedSize(horizontal: false, vertical: true)
            if corner == .plus {
                if pinCorner {
                    Spacer(minLength: 0)
                } else {
                    Spacer().frame(height: 5)
                }
                NexlyGlyph(name: "plus", size: 11, color: Aero.textSecondary)
                    .frame(width: 18, height: 18)
                    .background(Circle().fill(Aero.outline.opacity(0.30)))
            }
            if corner == .bottomChevron {
                Spacer().frame(height: 5)
                NexlyGlyph(name: "chevron-right", size: 12, color: Aero.textTertiary)
            }
        }
        .frame(maxWidth: .infinity, minHeight: minHeight, alignment: centered ? .center : .leading)
        .padding(.horizontal, 10)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.white.opacity(0.42))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(.white.opacity(0.62), lineWidth: 1)
        )
    }
}

/// A brand mark on white paper — the provider rows' slot.
struct GsBrandWell: View {
    let glyph: String
    var size: CGFloat = 34
    var tint: Color?

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.30)
                .fill(Color.white.opacity(0.86))
            RoundedRectangle(cornerRadius: size * 0.30)
                .strokeBorder(.white.opacity(0.65), lineWidth: 1)
            NexlyGlyph(name: glyph, size: size * 0.56, color: tint ?? Aero.text)
        }
        .frame(width: size, height: size)
    }
}

// MARK: - Facts + compact button

struct GsFactRow: View {
    let label: String
    let value: String
    var labelWidth: CGFloat = 118

    var body: some View {
        HStack(spacing: 8) {
            Text(label)
                .font(GsSheetType.factRow())
                .foregroundStyle(Aero.textSecondary)
                .frame(width: labelWidth, alignment: .leading)
            Text(value)
                .font(.custom(Aero.productName(.medium), size: 9.5, relativeTo: .caption2))
                .foregroundStyle(Aero.text)
                .lineLimit(1)
            Spacer(minLength: 0)
        }
        .padding(.vertical, 4)
    }
}

/// The mock's white pill button inside a hero ("Manage Plan ›").
struct GsSheetPillButton: View {
    let label: String
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 3) {
                Text(label)
                    .font(GsSheetType.button())
                    .foregroundStyle(Aero.text)
                NexlyGlyph(name: "chevron-right", size: 10, color: Aero.textTertiary)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .background(Capsule().fill(.white.opacity(0.80)))
            .overlay(Capsule().strokeBorder(.white.opacity(0.90), lineWidth: 1))
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityLabel(label)
    }
}

/// A compact chevron in the trailing slot, matching Android's DefaultChevron.
struct GsSheetChevron: View {
    var body: some View {
        NexlyGlyph(name: "chevron-right", size: 12, color: Aero.textTertiary)
    }
}

extension View {
    /// A card at the sheet's 22 corner with the measured light glass role.
    func gsSheetCard(corner: CGFloat = 22) -> some View {
        self.gsGlass(.cardLight, corner: corner)
    }
}
