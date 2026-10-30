import SwiftUI

/// Model Selection — docs/reference/pending/model-selection-v2.png.
///
/// The reference's inventory, in its order: the four-tab capability bar, the
/// "Available Models" list with the Recommended card lit, the Reasoning Effort
/// control, the Auto Select row and the Continue action.
///
/// WHAT EACH CONTROL DOES (see `ModelRouter` for the rule):
///  - a tab filters the list by the capability the model declares;
///  - picking a card stores the model AND moves the effort chip to that model's
///    default, so the tap has a visible consequence;
///  - the effort control writes the wire mode the next reply uses;
///  - Auto Select, when on, lets the send path choose the model per message and
///    take that model's effort; when off, the effort control is the authority.
struct ModelSelectionView: View {

    var onBack: () -> Void
    var onContinue: () -> Void

    @ObservedObject private var settings = SettingsStore.shared
    @Environment(\.colorScheme) private var scheme
    @State private var tab = "all"

    private struct Tab { let id, label, glyph: String }
    private let tabs: [Tab] = [
        Tab(id: "all", label: "All", glyph: "sparkles"),
        Tab(id: "text", label: "Text", glyph: "message-circle"),
        Tab(id: "vision", label: "Vision", glyph: "eye"),
        Tab(id: "code", label: "Code", glyph: "code"),
    ]

    private var selected: GsModel { GsModel.byId(settings.preferredModel) }
    private var shown: [GsModel] {
        GsModel.allCases.filter { tab == "all" || $0.capabilities.contains(tab) }
    }

    var body: some View {
        GsSettingsPage(
            title: "Model Selection",
            subtitle: "Choose the best model for your needs.",
            onBack: onBack
        ) {
            tabBar
            availableHeader
            VStack(spacing: 7) {
                ForEach(shown) { model in
                    modelCard(model)
                }
            }
            reasoningBlock
            continueButton
        }
        .toolbar(.hidden, for: .navigationBar)
    }

    // MARK: Tabs

    private var tabBar: some View {
        HStack(spacing: 4) {
            ForEach(tabs, id: \.id) { item in
                let active = item.id == tab
                Button { tab = item.id } label: {
                    VStack(spacing: 3) {
                        NexlyGlyph(name: item.glyph, size: 15,
                                   color: active ? Aero.accent : Aero.textSecondary)
                        Text(item.label)
                            .font(.custom(Aero.productName(active ? .semibold : .regular), size: 8.5))
                            .foregroundStyle(active ? Aero.text : Aero.textSecondary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 7)
                    .background(
                        RoundedRectangle(cornerRadius: 15)
                            .fill(active ? Aero.elevatedSurface.opacity(0.95) : .clear)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 15)
                            .strokeBorder(active ? Aero.accent.opacity(0.18) : .clear, lineWidth: 1)
                    )
                }
                .buttonStyle(.plain)
                .accessibilityLabel(item.label)
                .accessibilityAddTraits(active ? [.isSelected] : [])
                .accessibilityIdentifier("model-tab-\(item.id)")
            }
        }
        .padding(4)
        .background(RoundedRectangle(cornerRadius: 18).fill(.white.opacity(scheme == .dark ? 0.10 : 0.42)))
        .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(.white.opacity(scheme == .dark ? 0.14 : 0.65), lineWidth: 1))
    }

    private var availableHeader: some View {
        HStack {
            Text("Available Models")
                .font(.custom(Aero.productName(.semibold), size: 13))
                .foregroundStyle(Aero.text)
                .accessibilityAddTraits(.isHeader)
            Spacer()
            HStack(spacing: 4) {
                NexlyGlyph(name: "sparkles", size: 11, color: Aero.accent)
                Text("Recommended")
                    .font(.custom(Aero.productName(.regular), size: 9))
                    .foregroundStyle(Aero.textTertiary)
            }
        }
        .padding(.top, 16)
        .padding(.bottom, 7)
    }

    // MARK: Model card

    private func modelCard(_ model: GsModel) -> some View {
        let isSelected = model == selected
        return Button { settings.applyPreferredModel(model.rawValue) } label: {
            HStack(alignment: .top, spacing: 11) {
                GsWellTile(glyph: model.glyph, tone: model.tone, size: 38)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 7) {
                        Text(model.title)
                            .font(.custom(Aero.productName(.semibold), size: 11))
                            .foregroundStyle(Aero.text)
                        if model == .pro {
                            Text("Recommended")
                                .font(.custom(Aero.productName(.regular), size: 7.5))
                                .foregroundStyle(Aero.accent)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Capsule().fill(Aero.accentSoft))
                        }
                    }
                    Text(model.detail)
                        .font(.custom(Aero.productName(.regular), size: 8.5))
                        .foregroundStyle(Aero.textTertiary)
                        .fixedSize(horizontal: false, vertical: true)
                    HStack(spacing: 5) {
                        ForEach(model.tagList, id: \.self) { tag in
                            Text(tag)
                                .font(.custom(Aero.productName(.regular), size: 7.5))
                                .foregroundStyle(Aero.textSecondary)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 3)
                                .background(RoundedRectangle(cornerRadius: 7).fill(.white.opacity(0.55)))
                        }
                    }
                    .padding(.top, 5)
                }
                Spacer(minLength: 8)
                radio(isSelected)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 11)
            .background(
                RoundedRectangle(cornerRadius: 20)
                    .fill(
                        isSelected
                            ? (scheme == .dark ? Aero.accentSoft.opacity(0.55) : model.tone.wash.opacity(0.55))
                            : .white.opacity(scheme == .dark ? 0.07 : 0.40)
                    )
            )
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .strokeBorder(
                        isSelected ? Aero.accent.opacity(0.55) : .white.opacity(scheme == .dark ? 0.10 : 0.55),
                        lineWidth: isSelected ? 1.2 : 1
                    )
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(model.title), \(isSelected ? "selected" : "not selected")")
        .accessibilityIdentifier("model-card-\(model.rawValue)")
    }

    private func radio(_ isSelected: Bool) -> some View {
        ZStack {
            Circle()
                .fill(isSelected ? Aero.accent : .clear)
            Circle()
                .strokeBorder(isSelected ? .clear : Aero.textTertiary.opacity(0.45), lineWidth: 1.4)
            if isSelected {
                NexlyGlyph(name: "check", size: 11, color: .white)
            }
        }
        .frame(width: 19, height: 19)
    }

    // MARK: Effort + auto select

    private struct Effort { let id, label: String }
    private let efforts: [Effort] = [
        Effort(id: "minimal", label: "Minimal"),
        Effort(id: "low", label: "Low"),
        Effort(id: "standard", label: "Medium"),
        Effort(id: "high", label: "High"),
    ]

    private var reasoningBlock: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                NexlyGlyph(name: "adjustments", size: 14, color: Aero.textSecondary)
                Text("Reasoning Effort")
                    .font(.custom(Aero.productName(.semibold), size: 13))
                    .foregroundStyle(Aero.text)
            }
            .padding(.top, 18)
            Text("Control how much time and thought the model spends on its response.")
                .font(.custom(Aero.productName(.regular), size: 9))
                .foregroundStyle(Aero.textTertiary)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 3)
            effortSegments.padding(.top, 9)
            GsGroupCard {
                HStack(spacing: 11) {
                    GsWellTile(glyph: "sparkles", tone: .lilac, size: 34)
                    VStack(alignment: .leading, spacing: 1) {
                        Text("Auto Select Model")
                            .font(.custom(Aero.productName(.medium), size: 10.5))
                            .foregroundStyle(Aero.text)
                        Text("Let Nexly choose the best model for your message.")
                            .font(.custom(Aero.productName(.regular), size: 8))
                            .foregroundStyle(Aero.textTertiary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 8)
                    Toggle("", isOn: Binding(
                        get: { settings.autoSelectModel },
                        set: { settings.autoSelectModel = $0 }))
                        .labelsHidden()
                        .tint(Aero.accent)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 11)
            }
            .padding(.top, 14)
        }
    }

    private var effortSegments: some View {
        HStack(spacing: 3) {
            ForEach(efforts, id: \.id) { option in
                let active = option.id == settings.reasoningEffort
                Button { settings.applyReasoningEffort(option.id) } label: {
                    Text(option.label)
                        .font(.custom(Aero.productName(active ? .semibold : .regular), size: 9))
                        .foregroundStyle(active ? .white : Aero.textSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(
                            Capsule().fill(
                                active
                                    ? AnyShapeStyle(LinearGradient(
                                        colors: [Aero.accent.opacity(0.78), Aero.accent],
                                        startPoint: .leading, endPoint: .trailing))
                                    : AnyShapeStyle(Color.clear)
                            )
                        )
                }
                .buttonStyle(.plain)
                .accessibilityLabel(option.label)
                .accessibilityAddTraits(active ? [.isSelected] : [])
                .accessibilityIdentifier("effort-\(option.id)")
            }
        }
        .padding(4)
        .background(Capsule().fill(.white.opacity(scheme == .dark ? 0.10 : 0.55)))
        .overlay(Capsule().strokeBorder(.white.opacity(scheme == .dark ? 0.14 : 0.70), lineWidth: 1))
    }

    private var continueButton: some View {
        Button(action: onContinue) {
            HStack(spacing: 7) {
                NexlyGlyph(name: "sparkles", size: 14, color: .white)
                Text("Continue")
                    .font(.custom(Aero.productName(.semibold), size: 12))
                    .foregroundStyle(.white)
                NexlyGlyph(name: "arrow-right", size: 13, color: .white.opacity(0.9))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 13)
            .background(
                Capsule().fill(LinearGradient(
                    colors: [Aero.accent.opacity(0.78), Aero.accent],
                    startPoint: .leading, endPoint: .trailing))
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Continue")
        .accessibilityIdentifier("model-continue")
        .padding(.top, 18)
    }
}

private extension GsModel {
    var tone: GsWellTone {
        switch self {
        case .pro: return .lilac
        case .lite: return .peach
        case .vision: return .blue
        case .code: return .mint
        }
    }

    var glyph: String {
        switch self {
        case .pro: return "sparkles"
        case .lite: return "leaf"
        case .vision: return "eye"
        case .code: return "code"
        }
    }

    var detail: String {
        switch self {
        case .pro: return "Our most capable model. Great for complex tasks, creative work and deep analysis."
        case .lite: return "Fast, efficient and perfect for everyday questions and quick tasks."
        case .vision: return "Advanced image understanding and multimodal capabilities."
        case .code: return "Specialized for programming, debugging and technical tasks."
        }
    }

    var tagList: [String] {
        switch self {
        case .pro: return ["Best for complex tasks", "Creative", "Balanced"]
        case .lite: return ["Fast", "Efficient", "Everyday"]
        case .vision: return ["Image", "Vision", "Multimodal"]
        case .code: return ["Coding", "Debugging", "Technical"]
        }
    }
}
