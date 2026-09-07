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

    var initials: String {
        name.split(separator: " ").map { String($0.prefix(1)) }.joined()
    }

    static let catalog: [ModelInfo] = [
        ModelInfo(id: "gs-swift", name: "GS Swift", tagline: "Instant answers and quick drafting", capabilities: ["Web search", "Memory"], contextK: 32, speedTier: "Fast", modes: ["Fast"], isDefault: false),
        ModelInfo(id: "gs-balanced", name: "GS Balanced", tagline: "The everyday workhorse for chat and writing", capabilities: ["Web search", "Files", "Memory"], contextK: 128, speedTier: "Balanced", modes: ["Fast", "Balanced"], isDefault: true),
        ModelInfo(id: "gs-deep", name: "GS Deep", tagline: "Long-horizon reasoning on hard problems", capabilities: ["Web search", "Files", "Memory", "Code"], contextK: 200, speedTier: "Deep", modes: ["Deep reasoning"], isDefault: false),
        ModelInfo(id: "gs-research", name: "GS Research", tagline: "Multi-source research with live citations", capabilities: ["Web search", "Files", "Memory"], contextK: 128, speedTier: "Balanced", modes: ["Research"], isDefault: false),
        ModelInfo(id: "gs-coder", name: "GS Coder", tagline: "Repo-aware coding, review and refactors", capabilities: ["Code", "Files", "Memory"], contextK: 128, speedTier: "Balanced", modes: ["Coding"], isDefault: false),
        ModelInfo(id: "gs-creative", name: "GS Creative", tagline: "Expressive writing with a wider imagination", capabilities: ["Memory"], contextK: 64, speedTier: "Balanced", modes: ["Creative"], isDefault: false),
        ModelInfo(id: "gs-vision", name: "GS Vision", tagline: "Screenshots, charts, photos and OCR", capabilities: ["Vision", "Files"], contextK: 64, speedTier: "Fast", modes: ["Vision"], isDefault: false),
        ModelInfo(id: "gs-voice", name: "GS Voice", tagline: "Real-time speech with sub-second latency", capabilities: ["Voice", "Memory"], contextK: 32, speedTier: "Fast", modes: ["Voice"], isDefault: false)
    ]
}

/// Model centre — default card, reasoning modes, expandable model browser.
/// The pick persists in UserDefaults ("gs.models.defaultId" / "gs.models.mode");
/// the chat send path reads the same id (ChatViewModel gates it against the
/// remote registry, so unknown ids silently fall back to the server default).
struct ModelCentreView: View {

    @State private var expandedID: String?
    @State private var defaultID = UserDefaults.standard.string(forKey: "gs.models.defaultId") ?? "gs-balanced"
    @State private var selectedMode = UserDefaults.standard.string(forKey: "gs.models.mode") ?? "Balanced"

    private let modes = [
        "Fast", "Balanced", "Deep reasoning", "Research",
        "Coding", "Creative", "Vision", "Voice"
    ]

    private var defaultModel: ModelInfo {
        ModelInfo.catalog.first { $0.id == defaultID } ?? ModelInfo.catalog[0]
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                header
                defaultCard
                modesSection
                modelsSection
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                NavigationLink(value: AeroRoute.modelCompare) {
                    Text("Compare")
                        .font(Aero.label())
                        .foregroundStyle(Aero.accent)
                }
            }
        }
    }

    // MARK: Header

    private var header: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.xs) {
            Text("Models")
                .font(Aero.displayTitle())
                .foregroundStyle(Aero.text)
            Text("Pick the right brain for the job.")
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

    // MARK: Reasoning modes

    private var modesSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            SectionHeader(title: "Reasoning mode")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Aero.Spacing.s) {
                    ForEach(modes, id: \.self) { mode in
                        AeroChip(text: mode, selected: selectedMode == mode) {
                            selectedMode = mode
                            UserDefaults.standard.set(mode, forKey: "gs.models.mode")
                        }
                    }
                }
            }
        }
    }

    // MARK: Model browser

    private var modelsSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            SectionHeader(title: "All models")
            ForEach(ModelInfo.catalog) { model in
                VStack(spacing: Aero.Spacing.xs) {
                    modelRow(model)
                    if expandedID == model.id {
                        expandedCard(model)
                            .transition(.opacity.combined(with: .move(edge: .top)))
                    }
                }
            }
        }
    }

    private func modelRow(_ model: ModelInfo) -> some View {
        AeroListRow(
            title: model.name,
            subtitle: "\(model.tagline) · \(model.contextK)K context",
            leading: {
                Text(model.initials)
                    .font(Aero.label())
                    .foregroundStyle(Aero.accent)
                    .frame(width: 36, height: 36)
                    .background(Circle().fill(Aero.container))
            },
            trailing: {
                HStack(spacing: Aero.Spacing.s) {
                    speedDots(model.speedTier)
                    if model.id == defaultID {
                        Image(systemName: "checkmark")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(Aero.accent)
                    }
                }
            },
            action: {
                withAnimation(Aero.snappy) {
                    expandedID = expandedID == model.id ? nil : model.id
                }
            }
        )
    }

    private func expandedCard(_ model: ModelInfo) -> some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                Text("Capabilities")
                    .font(Aero.label())
                    .foregroundStyle(Aero.textMuted)
                chipFlow(model.capabilities)
                Text("Modes")
                    .font(Aero.label())
                    .foregroundStyle(Aero.textMuted)
                chipFlow(model.modes)
                setDefaultButton(model)
            }
        }
    }

    private func setDefaultButton(_ model: ModelInfo) -> some View {
        let isCurrent = model.id == defaultID
        return Button {
            withAnimation(Aero.snappy) {
                defaultID = model.id
                UserDefaults.standard.set(model.id, forKey: "gs.models.defaultId")
            }
        } label: {
            Text(isCurrent ? "Current default" : "Set as default")
                .font(Aero.title())
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(isCurrent ? AnyShapeStyle(Aero.container) : AnyShapeStyle(Aero.accent))
                )
                .foregroundStyle(isCurrent ? Aero.text : Color.white)
        }
        .buttonStyle(KineticPressStyle())
        .disabled(isCurrent)
    }

    // MARK: Helpers

    private func chipFlow(_ items: [String]) -> some View {
        LazyVGrid(
            columns: [GridItem(.adaptive(minimum: 86), spacing: Aero.Spacing.xs)],
            alignment: .leading,
            spacing: Aero.Spacing.xs
        ) {
            ForEach(items, id: \.self) { item in
                AeroChip(text: item)
            }
        }
    }

    /// 1–3 filled dots: Fast = 3, Balanced = 2, Deep = 1.
    private func speedDots(_ tier: String) -> some View {
        let filled = tier == "Fast" ? 3 : (tier == "Balanced" ? 2 : 1)
        return HStack(spacing: 3) {
            ForEach(0..<3, id: \.self) { index in
                Circle()
                    .fill(index < filled ? AnyShapeStyle(Aero.accent) : AnyShapeStyle(Aero.container))
                    .frame(width: 6, height: 6)
            }
        }
    }
}
