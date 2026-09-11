import SwiftUI

/// Inline sample model — shared with ModelCompareView.
struct ModelInfo: Identifiable, Hashable {
    let id: String
    let name: String
    let tagline: String
    let capabilities: [String]
    let contextK: Int
    let speedTier: String
    let modes: [String]
    let isDefault: Bool

    static let catalog: [ModelInfo] = [
        ModelInfo(id: "gs-swift", name: "GS Swift", tagline: "Instant answers", capabilities: ["Web search", "Memory"], contextK: 32, speedTier: "Fast", modes: ["Fast"], isDefault: false),
        ModelInfo(id: "gs-balanced", name: "GS Balanced", tagline: "Everyday intelligence", capabilities: ["Web search", "Files", "Memory"], contextK: 128, speedTier: "Balanced", modes: ["Fast", "Balanced"], isDefault: true),
        ModelInfo(id: "gs-deep", name: "GS Deep", tagline: "Extended reasoning", capabilities: ["Web search", "Files", "Memory", "Code"], contextK: 200, speedTier: "Deep", modes: ["Deep reasoning"], isDefault: false),
        ModelInfo(id: "gs-research", name: "GS Research", tagline: "Multi-source synthesis", capabilities: ["Web search", "Files", "Memory"], contextK: 128, speedTier: "Balanced", modes: ["Research"], isDefault: false),
        ModelInfo(id: "gs-coder", name: "GS Coder", tagline: "Code generation & review", capabilities: ["Code", "Files", "Memory"], contextK: 128, speedTier: "Balanced", modes: ["Coding"], isDefault: false),
        ModelInfo(id: "gs-creative", name: "GS Creative", tagline: "Writing & ideation", capabilities: ["Memory"], contextK: 64, speedTier: "Balanced", modes: ["Creative"], isDefault: false),
        ModelInfo(id: "gs-vision", name: "GS Vision", tagline: "Images, charts, OCR", capabilities: ["Vision", "Files"], contextK: 64, speedTier: "Fast", modes: ["Vision"], isDefault: false),
        ModelInfo(id: "gs-voice", name: "GS Voice", tagline: "Real-time voice", capabilities: ["Voice", "Memory"], contextK: 32, speedTier: "Fast", modes: ["Voice"], isDefault: false)
    ]
}

/// Consumer tier — one shelf of the 3-tier model store (Phase 2). Tier
/// membership is the product contract's consumer mapping, NOT the raw
/// speedTier string: GS Vision answers fast but sits on the everyday shelf;
/// GS Research runs at balanced speed under "difficult questions". Shared
/// verbatim by the chat's model sheet and the Model Centre.
struct ModelTier: Identifiable {
    let id: String
    let title: String
    let subtitle: String
    let modelIDs: [String]

    var models: [ModelInfo] {
        modelIDs.compactMap { id in ModelInfo.catalog.first { $0.id == id } }
    }
}

extension ModelInfo {
    /// The consumer shelves, in display order.
    static let consumerTiers: [ModelTier] = [
        ModelTier(
            id: "fast",
            title: "Fast",
            subtitle: "Quick answers when speed matters",
            modelIDs: ["gs-swift", "gs-voice"]),
        ModelTier(
            id: "everyday",
            title: "Everyday",
            subtitle: "Smart help for daily questions",
            modelIDs: ["gs-balanced", "gs-coder", "gs-creative", "gs-vision"]),
        ModelTier(
            id: "deep",
            title: "Best for difficult questions",
            subtitle: "Takes its time, thinks deeper",
            modelIDs: ["gs-deep", "gs-research"])
    ]
}

/// Model centre — current default card, then the catalogue grouped into the
/// 3 consumer tiers. A tap on a row makes it the default; the pick persists
/// in UserDefaults ("gs.models.defaultId") — the same key the chat send path
/// reads (ChatViewModel gates it against the remote registry, so unknown ids
/// silently fall back to the server default). Technical detail (context
/// window, speed, capabilities, modes) lives ONLY inside each row's
/// collapsed "Advanced details"; "Compare models" is demoted to a quiet row
/// in the Advanced section.
struct ModelCentreView: View {

    @State private var defaultID = UserDefaults.standard.string(forKey: "gs.models.defaultId") ?? "gs-balanced"
    @State private var expandedDetails: String?

    private var defaultModel: ModelInfo {
        ModelInfo.catalog.first { $0.id == defaultID } ?? ModelInfo.catalog[0]
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                header
                defaultCard
                ForEach(ModelInfo.consumerTiers) { tier in
                    tierSection(tier)
                }
                advancedSection
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
    }

    // MARK: Header

    private var header: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.xs) {
            Text("Models")
                .font(Aero.displayTitle())
                .foregroundStyle(Aero.text)
            Text("Choose the default model your conversations use.")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
        }
    }

    // MARK: Current default

    private var defaultCard: some View {
        AeroCard {
            HStack(spacing: Aero.Spacing.m) {
                VStack(alignment: .leading, spacing: Aero.Spacing.xs) {
                    Text(defaultModel.name)
                        .font(Aero.headline())
                        .foregroundStyle(Aero.text)
                    Text(defaultModel.tagline)
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: Aero.Spacing.s) {
                    AuroraIndicator()
                    AeroChip(text: "Default", selected: true)
                }
            }
        }
    }

    // MARK: Consumer tiers

    private func tierSection(_ tier: ModelTier) -> some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            SectionHeader(title: tier.title)
            Text(tier.subtitle)
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
                .padding(.horizontal, 4)
            ForEach(tier.models) { model in
                modelRow(model)
            }
        }
    }

    /// One tier row: display name + plain tagline (+ checkmark on the
    /// default). Tapping the row writes the existing default-model pref.
    /// Technical specs stay folded inside "Advanced details".
    private func modelRow(_ model: ModelInfo) -> some View {
        VStack(spacing: Aero.Spacing.xs) {
            Button {
                guard model.id != defaultID else { return }
                GSHaptics.tap()   // committed — the next turn travels with this model
                withAnimation(Aero.snappy) {
                    defaultID = model.id
                    UserDefaults.standard.set(model.id, forKey: "gs.models.defaultId")
                }
            } label: {
                HStack(spacing: Aero.Spacing.s) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(model.name)
                            .font(Aero.title())
                            .foregroundStyle(Aero.text)
                        Text(model.tagline)
                            .font(Aero.caption())
                            .foregroundStyle(Aero.textMuted)
                    }
                    Spacer()
                    if model.id == defaultID {
                        Image(systemName: "checkmark")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Aero.accent)
                    }
                }
                .padding(.horizontal, Aero.Spacing.m)
                .padding(.vertical, 12)
                .background(RoundedRectangle(cornerRadius: Aero.Radius.md).fill(Aero.raisedSurface))
                .contentShape(Rectangle())
            }
            .buttonStyle(KineticPressStyle())
            .accessibilityLabel("\(model.name). \(model.tagline)\(model.id == defaultID ? ". Current default" : "")")
            .accessibilityAddTraits(model.id == defaultID ? [.isSelected] : [])

            DisclosureGroup(isExpanded: Binding(
                get: { expandedDetails == model.id },
                set: { expandedDetails = $0 ? model.id : nil }
            )) {
                advancedDetails(model)
            } label: {
                Text("Advanced details")
                    .font(Aero.label())
                    .foregroundStyle(Aero.textMuted)
            }
            .padding(.horizontal, Aero.Spacing.s)
        }
    }

    /// The technical sheet for one model — context window, speed,
    /// capabilities, modes. This is the ONLY place a consumer meets these
    /// numbers, and only behind an explicit tap.
    private func advancedDetails(_ model: ModelInfo) -> some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.xs) {
            detailLine("Context window", value: "\(model.contextK)K tokens")
            detailLine("Speed", value: model.speedTier)
            if !model.capabilities.isEmpty {
                detailLine("Capabilities", value: model.capabilities.joined(separator: ", "))
            }
            if !model.modes.isEmpty {
                detailLine("Modes", value: model.modes.joined(separator: ", "))
            }
        }
        .padding(.top, Aero.Spacing.xs)
    }

    private func detailLine(_ label: String, value: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: Aero.Spacing.s) {
            Text(label)
                .font(Aero.label())
                .foregroundStyle(Aero.textMuted)
                .frame(width: 110, alignment: .leading)
            Text(value)
                .font(Aero.caption())
                .foregroundStyle(Aero.text)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .accessibilityElement(children: .combine)
    }

    // MARK: Advanced

    /// Compare is no longer a top action — it is one quiet row here (and in
    /// the drawer's Advanced group), reachable by readers who want the specs.
    private var advancedSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            SectionHeader(title: "Advanced")
            NavigationLink(value: AeroRoute.modelCompare) {
                AeroListRow(
                    title: "Compare models",
                    subtitle: "Side-by-side specifications",
                    leading: {
                        Image(systemName: "rectangle.on.rectangle")
                            .font(.system(size: 14))
                            .foregroundStyle(Aero.accent)
                            .frame(width: 34, height: 34)
                            .background(Circle().fill(Aero.container))
                    },
                    trailing: {
                        Image(systemName: "chevron.right")
                            .font(.system(size: 12))
                            .foregroundStyle(Aero.textMuted)
                    }
                )
            }
            .buttonStyle(KineticPressStyle())
        }
    }
}
