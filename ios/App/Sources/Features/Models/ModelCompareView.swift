import SwiftUI

// MARK: - Compare helpers (derived from the shared model catalogue)

extension ModelInfo {
    var supportsTools: Bool { capabilities.contains("Files") || capabilities.contains("Code") }
    var supportsVision: Bool { capabilities.contains("Vision") }
    var supportsVoice: Bool { capabilities.contains("Voice") }

    var reasoningLabel: String {
        if modes.contains("Deep reasoning") { return "Deep" }
        if modes.contains("Research") { return "Research" }
        if modes.contains("Balanced") { return "Balanced" }
        return "Fast"
    }

    var speedDots: Int { speedTier == "Fast" ? 3 : (speedTier == "Balanced" ? 2 : 1) }
    var contextLabel: String { "\(contextK)K" }
}

/// Side-by-side model comparison — any three models.
struct ModelCompareView: View {

    private static let dangerRed = Color(red: 0.9, green: 0.28, blue: 0.28)

    @State private var selectedIDs = ["gs-swift", "gs-balanced", "gs-deep"]

    private var selected: [ModelInfo] {
        selectedIDs.compactMap { id in ModelInfo.catalog.first { $0.id == id } }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                header
                pickerChips
                AeroCard {
                    table
                }
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
            Text("Compare models")
                .font(Aero.displayTitle())
                .foregroundStyle(Aero.text)
            Text("Side-by-side — pick any three.")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
        }
    }

    // MARK: Picker chips

    private var pickerChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Aero.Spacing.s) {
                ForEach(ModelInfo.catalog) { model in
                    AeroChip(text: model.name, selected: selectedIDs.contains(model.id)) {
                        toggleSelection(model.id)
                    }
                }
            }
        }
    }

    private func toggleSelection(_ id: String) {
        if let index = selectedIDs.firstIndex(of: id) {
            guard selectedIDs.count > 1 else { return }
            selectedIDs.remove(at: index)
        } else {
            if selectedIDs.count < 3 {
                selectedIDs.append(id)
            } else {
                selectedIDs.removeFirst()
                selectedIDs.append(id)
            }
        }
    }

    // MARK: Comparison table

    private var table: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            HStack(alignment: .top, spacing: Aero.Spacing.s) {
                Text("Model")
                    .font(Aero.label())
                    .foregroundStyle(Aero.textMuted)
                    .frame(width: 72, alignment: .leading)
                ForEach(selected) { model in
                    Text(model.name)
                        .font(Aero.title())
                        .foregroundStyle(Aero.text)
                        .lineLimit(2)
                        .minimumScaleFactor(0.7)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            Rectangle()
                .fill(Aero.outline)
                .frame(height: 1)
            compareRow("Context") { model in
                Text(model.contextLabel)
                    .font(Aero.caption())
                    .foregroundStyle(Aero.text)
            }
            compareRow("Speed") { model in
                dots(model.speedDots)
            }
            compareRow("Tools") { model in
                boolCell(model.supportsTools)
            }
            compareRow("Reasoning") { model in
                Text(model.reasoningLabel)
                    .font(Aero.caption())
                    .foregroundStyle(Aero.text)
            }
            compareRow("Vision") { model in
                boolCell(model.supportsVision)
            }
            compareRow("Voice") { model in
                boolCell(model.supportsVoice)
            }
        }
    }

    private func compareRow<Cell: View>(
        _ label: String,
        @ViewBuilder cell: (ModelInfo) -> Cell
    ) -> some View {
        HStack(alignment: .center, spacing: Aero.Spacing.s) {
            Text(label)
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
                .frame(width: 72, alignment: .leading)
            ForEach(selected) { model in
                cell(model)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    // MARK: Cells

    private func boolCell(_ on: Bool) -> some View {
        Image(systemName: on ? "checkmark" : "xmark")
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(on ? AnyShapeStyle(Aero.accent) : AnyShapeStyle(Self.dangerRed))
    }

    private func dots(_ filled: Int) -> some View {
        HStack(spacing: 3) {
            ForEach(0..<3, id: \.self) { index in
                Circle()
                    .fill(index < filled ? AnyShapeStyle(Aero.accent) : AnyShapeStyle(Aero.container))
                    .frame(width: 6, height: 6)
            }
        }
    }
}
