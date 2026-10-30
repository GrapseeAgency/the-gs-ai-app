import SwiftUI

/// Model Selection & Reasoning Effort — docs/reference/pending/model-selection.png.
///
/// The Android twin is `AiIntelligenceScreen`: same title, same subtitle, same
/// group order (AI Model → Reasoning Effort → Model Preferences → closing
/// card) and the same mock copy. Both write the same three preferences
/// (`settings.preferredModel`, `.reasoningEffort`, `.rememberChoices`) and the
/// reasoning tile also writes the new-chat wire mode, so the lit chip and what
/// the next reply does cannot disagree.
///
/// The page continues below the mock's closing card with the surfaces this
/// build can honestly back: the GS Intelligence hero, the model-source pair,
/// the on-device details sheet and the battery footnote. The mock's three
/// groups fit one 393×852 view; the extras scroll.
struct AiIntelligenceView: View {

    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var settings = SettingsStore.shared

    @State private var confirmReset = false
    @State private var showModelDetails = false

    private var engineAvailable: Bool { GsNativeLoader.isAvailable }

    // MARK: The mock's own inventory

    private struct ModelChoice: Identifiable {
        let id: String
        let title: String
        let detail: String
        let glyph: String
        let tone: GsWellTone
        var recommended: Bool = false
    }

    /// The mock's four rows, in its order, with its copy and tone.
    private static let models: [ModelChoice] = [
        ModelChoice(
            id: "pro",
            title: "Nexly Pro",
            detail: "Best balance of speed, intelligence and creativity. Great for most everyday tasks.",
            glyph: "sparkles", tone: .peach, recommended: true),
        ModelChoice(
            id: "advanced",
            title: "Nexly Advanced",
            detail: "Deep reasoning, complex problem solving and analysis. Ideal for research, coding and detailed tasks.",
            glyph: "atom", tone: .lilac),
        ModelChoice(
            id: "code",
            title: "Nexly Code",
            detail: "Specialized for programming and technical tasks. Great for developers and code-related work.",
            glyph: "code", tone: .mint),
        ModelChoice(
            id: "creative",
            title: "Nexly Creative",
            detail: "Focused on imagination, visuals and creative content. Best for design, writing and artistic tasks.",
            glyph: "palette", tone: .rose),
    ]

    private struct ReasoningEffort: Identifiable {
        let id: String
        let label: String
        let detail: String
        let timing: String
        let glyph: String
        let tone: GsWellTone
    }

    /// The mock's four-across tiles. `id` is what the store persists; the
    /// timings are the owner's copy from the art, not measurements of this
    /// build — a label on the choice, not a promise.
    private static let efforts: [ReasoningEffort] = [
        ReasoningEffort(id: "minimal", label: "Minimal",
                        detail: "Quick answers for simple questions.",
                        timing: "~ 1–2 seconds", glyph: "bolt", tone: .peach),
        ReasoningEffort(id: "light", label: "Light",
                        detail: "Balanced speed and depth.",
                        timing: "~ 2–5 seconds", glyph: "leaf", tone: .mint),
        ReasoningEffort(id: "standard", label: "Standard",
                        detail: "Best for most tasks and questions.",
                        timing: "~ 5–10 seconds", glyph: "sparkles", tone: .peach),
        ReasoningEffort(id: "high", label: "High",
                        detail: "Deeper analysis and detailed reasoning.",
                        timing: "~ 10–30 seconds", glyph: "brain", tone: .lilac),
    ]

    var body: some View {
        GsSettingsPage(
            title: "Model Selection & Reasoning Effort",
            subtitle: "Choose the best model and how deeply Nexly thinks",
            onBack: { dismiss() }
        ) {
            // ---- AI Model -------------------------------------------------
            GsGroupHeader(title: "AI Model", caption: "Different models for different needs")
            GsGroupCard {
                ForEach(Array(Self.models.enumerated()), id: \.element.id) { index, model in
                    if index > 0 { cardDivider }
                    modelRow(model)
                }
            }
            footnote("One model is currently served; this preference is kept for when routing supports per-model choice.")

            // ---- Reasoning Effort -----------------------------------------
            GsGroupHeader(title: "Reasoning Effort", caption: "Control how much Nexly thinks")
            GsGroupCard {
                effortRow
                    .padding(.horizontal, 8)
                    .padding(.vertical, 10)
            }

            // ---- Model Preferences ----------------------------------------
            GsGroupHeader(title: "Model Preferences", caption: "Customize your experience")
            GsGroupCard {
                // The mock draws this switch OFF. It is off and DISABLED
                // because the backend always chooses the model — a tappable
                // switch that changes nothing is the failure this repo keeps
                // recording.
                toggleRow(
                    "Auto-select model",
                    detail: "Nexly always chooses the best model for each task.",
                    glyph: "settings", tone: .peach,
                    isOn: .constant(false), enabled: false)
                cardDivider
                toggleRow(
                    "Remember my choices",
                    detail: "Keep using your preferred model and reasoning effort.",
                    glyph: "adjustments", tone: .lilac,
                    isOn: Binding(
                        get: { settings.rememberChoices },
                        set: { settings.rememberChoices = $0; GSHaptics.select() }))
                cardDivider
                GsSettingsRow(
                    title: "Reset to default",
                    detail: "Go back to Nexly's recommended settings.",
                    showDivider: false,
                    action: { confirmReset = true }
                ) {
                    GsWellTile(glyph: "rotate-clockwise", tone: .rose, size: 28)
                } trailing: {
                    GsSheetChevron()
                }
            }

            // The mock's closing card, in its own position after Model
            // Preferences.
            closingCard

            hero
            modelSource

            GsGroupHeader(title: "On this device", caption: nil)
            GsGroupCard {
                GsSettingsRow(
                    title: "View model details",
                    detail: "Size, capability and what installing it changes",
                    showDivider: false,
                    action: { showModelDetails = true }
                ) {
                    GsWellTile(glyph: "cpu", tone: .blue, size: 28)
                } trailing: {
                    GsSheetChevron()
                }
            }

            footnote("On-device generation is slower and uses battery. It is the honest choice when the network is gone, not a silent replacement for the cloud model.")
        }
        .alert("Reset to Nexly's recommended settings?", isPresented: $confirmReset) {
            Button("Reset") {
                settings.resetModelPreferences()
                GSHaptics.success()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("The model returns to Nexly Pro, reasoning effort to Standard, and \"Remember my choices\" turns back on.")
        }
        .sheet(isPresented: $showModelDetails) {
            ModelDetailsSheet(engineAvailable: engineAvailable)
                .presentationDetents([.medium, .large])
        }
    }

    // MARK: AI Model rows

    /// One row of the mock's AI Model list: well, title (+ Recommended chip)
    /// and its wrapping detail, with the selection mark on the right.
    ///
    /// The check-in-a-soft-well is the mock's model idiom; the four-across
    /// effort tiles below keep radios. Both are drawn, so the two selection
    /// languages are not interchangeable.
    private func modelRow(_ model: ModelChoice) -> some View {
        let selected = settings.preferredModel == model.id
        return Button {
            settings.preferredModel = model.id
            GSHaptics.select()
        } label: {
            HStack(spacing: 11) {
                GsWellTile(glyph: model.glyph, tone: model.tone, size: 28)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(model.title)
                            .font(.custom(Aero.productName(.medium), size: 15, relativeTo: .subheadline))
                            .foregroundStyle(Aero.text)
                            .lineLimit(1)
                        if model.recommended {
                            GsTagPill(text: "Recommended", tone: ExplorePalette.apricotFg)
                        }
                    }
                    Text(model.detail)
                        .font(.custom(Aero.productName(.regular), size: 12.5, relativeTo: .caption))
                        .foregroundStyle(Aero.textTertiary)
                        .lineLimit(3)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 6)
                ZStack {
                    Circle()
                        .fill(selected ? Aero.accentSoft : Color.clear)
                    Circle()
                        .strokeBorder(
                            selected ? Aero.accent.opacity(0) : Aero.textTertiary.opacity(0.30),
                            lineWidth: 1.5)
                    if selected {
                        NexlyGlyph(name: "check", size: 14, color: Aero.accent)
                    }
                }
                .frame(width: 24, height: 24)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(model.detail.isEmpty ? model.title : "\(model.title), \(model.detail)")
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }

    // MARK: Reasoning Effort

    /// Four across in one card with hairline column dividers, the mock's order
    /// and copy. The tiles share a height so every timing line and radio sits
    /// on one baseline; without that the High tile's longer copy pushed its
    /// radio below the other three.
    private var effortRow: some View {
        HStack(alignment: .top, spacing: 0) {
            ForEach(Array(Self.efforts.enumerated()), id: \.element.id) { index, effort in
                if index > 0 {
                    Rectangle()
                        .fill(Aero.divider)
                        .frame(width: 1)
                }
                effortTile(effort)
                    .frame(maxWidth: .infinity)
            }
        }
        .fixedSize(horizontal: false, vertical: true)
    }

    private func effortTile(_ effort: ReasoningEffort) -> some View {
        let selected = settings.reasoningEffort == effort.id
        return Button {
            settings.applyReasoningEffort(effort.id)
            GSHaptics.select()
        } label: {
            VStack(spacing: 0) {
                GsWellTile(glyph: effort.glyph, tone: effort.tone, size: 26)
                Spacer().frame(height: 7)
                Text(effort.label)
                    .font(.custom(Aero.productName(.semibold), size: 11.5, relativeTo: .caption))
                    .foregroundStyle(Aero.text)
                    .lineLimit(1)
                Spacer().frame(height: 3)
                Text(effort.detail)
                    .font(.custom(Aero.productName(.regular), size: 9.5, relativeTo: .caption2))
                    .foregroundStyle(Aero.textTertiary)
                    .multilineTextAlignment(.center)
                    .lineLimit(3)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 8)
                Text(effort.timing)
                    .font(.custom(Aero.productName(.regular), size: 9, relativeTo: .caption2))
                    .foregroundStyle(Aero.textTertiary)
                    .lineLimit(1)
                Spacer().frame(height: 6)
                ZStack {
                    Circle()
                        .strokeBorder(
                            selected ? Aero.accent : Aero.textTertiary.opacity(0.30),
                            lineWidth: 1.5)
                    if selected {
                        Circle().fill(Aero.accent).frame(width: 7, height: 7)
                    }
                }
                .frame(width: 16, height: 16)
            }
            .padding(.horizontal, 4)
            .padding(.vertical, 6)
            .frame(maxWidth: .infinity, minHeight: 132, alignment: .top)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(selected ? Aero.accentSoft : Color.white.opacity(0.32))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(
                        selected ? Aero.accent.opacity(0.55) : Color.clear,
                        lineWidth: 1.5)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(effort.label), \(effort.detail), \(effort.timing)")
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }

    // MARK: Preferences

    /// The settings-scale toggle row (15 / 12.5, matching the hub's rows) —
    /// the sheet kit's 10pt toggle would read as a different page's furniture
    /// here.
    private func toggleRow(
        _ title: String,
        detail: String,
        glyph: String,
        tone: GsWellTone,
        isOn: Binding<Bool>,
        enabled: Bool = true
    ) -> some View {
        HStack(spacing: 11) {
            GsWellTile(glyph: glyph, tone: tone, size: 28)
            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                    .font(.custom(Aero.productName(.medium), size: 15, relativeTo: .subheadline))
                    .foregroundStyle(enabled ? Aero.text : Aero.textSecondary)
                Text(detail)
                    .font(.custom(Aero.productName(.regular), size: 12.5, relativeTo: .caption))
                    .foregroundStyle(Aero.textTertiary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 8)
            Toggle("", isOn: isOn)
                .labelsHidden()
                .tint(Aero.accent)
                .disabled(!enabled)
                .accessibilityLabel(title)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .frame(minHeight: 42.5)
        .opacity(enabled ? 1 : 0.55)
    }

    private var cardDivider: some View {
        Rectangle()
            .fill(Aero.divider.opacity(0.85))
            .frame(height: 1.0 / 3.0)
            .padding(.leading, 50)
            .padding(.trailing, 12)
    }

    private var closingCard: some View {
        HStack(spacing: 10) {
            GsWellTile(glyph: "sparkles", tone: .peach, size: 38)
            VStack(alignment: .leading, spacing: 2) {
                Text("Smarter choices. Better conversations.")
                    .font(.custom(Aero.productName(.semibold), size: 13, relativeTo: .subheadline))
                    .foregroundStyle(Aero.text)
                Text("The right model + the right thinking = the best Nexly experience.")
                    .font(.custom(Aero.productName(.regular), size: 11, relativeTo: .caption))
                    .foregroundStyle(Aero.textTertiary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 14)
        .padding(.vertical, 13)
        .gsGlass(.cardLight, corner: 22)
        .padding(.top, 14)
    }

    /// The reference's GS Intelligence card. One assistant, two engines — the
    /// same sentence Android draws.
    private var hero: some View {
        HStack(spacing: 12) {
            GsArtwork(art: .bloom, size: 84, label: "Nexly")
            VStack(alignment: .leading, spacing: 3) {
                Text("GS Intelligence")
                    .font(.custom(Aero.productName(.semibold), size: 15, relativeTo: .headline))
                    .foregroundStyle(Aero.text)
                Text("One assistant, two engines. The cloud model handles depth; the on-device model keeps working offline.")
                    .font(.custom(Aero.productName(.regular), size: 12.5, relativeTo: .caption))
                    .foregroundStyle(Aero.textTertiary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .gsGlass(.cardLight, corner: 22)
        .padding(.top, 10)
    }

    // MARK: Model source

    /// Cloud or on-device — the same `preferLocal` the chat consults. The
    /// on-device tile is only selectable when an engine is actually loaded;
    /// with none, it says so and opens the details sheet instead of pretending.
    private var modelSource: some View {
        Group {
            GsGroupHeader(title: "Model source", caption: "Where answers run")
            GsGroupCard {
                HStack(spacing: 8) {
                    sourceTile(
                        title: "Nexly Cloud",
                        detail: "Full-quality answers through the GS backend",
                        glyph: "cloud", tone: .peach,
                        selected: !settings.preferLocal
                    ) {
                        settings.preferLocal = false
                        GSHaptics.select()
                    }
                    sourceTile(
                        title: "On-device",
                        detail: engineAvailable
                            ? "Private, and works with the network off"
                            : "No model installed — tap to learn more",
                        glyph: "cpu", tone: .blue,
                        selected: settings.preferLocal && engineAvailable
                    ) {
                        if engineAvailable {
                            settings.preferLocal = true
                            GSHaptics.select()
                        } else {
                            showModelDetails = true
                        }
                    }
                }
                .padding(10)
            }
        }
    }

    private func sourceTile(
        title: String,
        detail: String,
        glyph: String,
        tone: GsWellTone,
        selected: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 5) {
                HStack(spacing: 6) {
                    GsWellTile(glyph: glyph, tone: tone, size: 26)
                    Spacer(minLength: 0)
                    if selected {
                        NexlyGlyph(name: "circle-check", size: 15, color: Aero.accent)
                    }
                }
                Text(title)
                    .font(.custom(Aero.productName(.medium), size: 12.5, relativeTo: .caption))
                    .foregroundStyle(Aero.text)
                    .lineLimit(1)
                Text(detail)
                    .font(.custom(Aero.productName(.regular), size: 10, relativeTo: .caption2))
                    .foregroundStyle(Aero.textTertiary)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(10)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(selected ? Aero.accentSoft : Color.white.opacity(0.34))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(selected ? Aero.accent.opacity(0.50) : Color.white.opacity(0.55), lineWidth: 1)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(title), \(detail)")
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }

    private func footnote(_ text: String) -> some View {
        Text(text)
            .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2))
            .foregroundStyle(Aero.textTertiary)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 4)
            .padding(.top, 7)
    }
}

/// The reference's "Model Details", limited to what this device can prove:
/// whether a GGUF is loaded, which file, how big. No invented specs.
private struct ModelDetailsSheet: View {
    let engineAvailable: Bool
    @Environment(\.dismiss) private var dismiss

    private var loadedPath: String? { GsNativeLoader.loadedModelPath }

    private var fileName: String {
        guard let path = loadedPath else { return "No model file" }
        return (path as NSString).lastPathComponent
    }

    private var sizeLabel: String {
        guard let path = loadedPath,
              let attributes = try? FileManager.default.attributesOfItem(atPath: path),
              let bytes = attributes[.size] as? Int64 else { return "—" }
        if bytes >= 1_000_000_000 { return String(format: "%.1f GB", Double(bytes) / 1e9) }
        return String(format: "%.0f MB", Double(bytes) / 1e6)
    }

    private var capabilities: [(String, String)] {
        [
            ("wifi.slash", "Works with the network off"),
            ("lock", "Prompts never leave this phone"),
            ("text.alignleft", "Streams tokens through the same chat surface"),
            ("square.grid.2x2", "Renders the same widget vocabulary as cloud answers"),
        ]
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.m) {
                Text("Model Details").font(Aero.headline()).foregroundStyle(Aero.text)
                Text("What runs on this phone, and what it can do.")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)

                AeroCard {
                    HStack(spacing: 12) {
                        GsArtwork(art: .bloom, size: 72, label: "On-device model")
                        VStack(alignment: .leading, spacing: 3) {
                            Text(engineAvailable ? fileName : "No local model yet")
                                .font(Aero.label())
                                .foregroundStyle(Aero.text)
                                .lineLimit(2)
                            Text(engineAvailable ? sizeLabel : "The cloud engine answers until a model is installed.")
                                .font(Aero.caption())
                                .foregroundStyle(Aero.textMuted)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(16)
                }

                SectionHeader(title: "Key capabilities")
                AeroCard {
                    VStack(alignment: .leading, spacing: 14) {
                        ForEach(Array(capabilities.enumerated()), id: \.offset) { entry in
                            HStack(spacing: 10) {
                                Image(systemName: entry.element.0)
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundStyle(Aero.accent)
                                    .frame(width: 20)
                                Text(entry.element.1)
                                    .font(Aero.body())
                                    .foregroundStyle(Aero.text)
                                Spacer(minLength: 0)
                            }
                        }
                    }
                    .padding(16)
                }

                Text("On-device generation is slower and uses battery. It is the honest choice when the network is gone.")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)

                Button {
                    dismiss()
                } label: {
                    Text("Done")
                        .font(Aero.label())
                        .foregroundStyle(Aero.onAccent)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(Capsule().fill(Aero.accent))
                }
                .buttonStyle(KineticPressStyle())
                .padding(.top, 4)
            }
            .padding(Aero.Spacing.m)
        }
        .background(Aero.background.ignoresSafeArea())
    }
}
