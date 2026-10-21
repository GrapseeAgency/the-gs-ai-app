import SwiftUI

/// AI INTELLIGENCE (reference sheet 3) — where answers run and how deeply they
/// think. Every control is a real setting: model source is the same
/// `preferLocal` preference the composer consults, and the effort tiles write
/// the same `gs.mode.new` fallback the chat view reads for a new conversation.
struct AiIntelligenceView: View {

    @ObservedObject private var settings = SettingsStore.shared
    /// `gs.mode.new` is the shared default for every brand-new conversation —
    /// the same key ChatViewModel falls back to when a chat has no saved mode.
    @State private var mode: String = UserDefaults.standard.string(forKey: "gs.mode.new") ?? "flash"
    @State private var showModelDetails = false

    private var engineAvailable: Bool { GsNativeLoader.isAvailable }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.m) {
                Text("AI Intelligence")
                    .font(Aero.headline())
                    .foregroundStyle(Aero.text)
                    .accessibilityAddTraits(.isHeader)
                Text("Configure how Nexly thinks, responds and helps you.")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)

                AeroCard {
                    HStack(spacing: 12) {
                        GsArtwork(art: .bloom, size: 84, label: "Nexly")
                        VStack(alignment: .leading, spacing: 3) {
                            Text("Nexly Intelligence")
                                .font(Aero.label())
                                .foregroundStyle(Aero.text)
                            Text("One assistant, two engines. The cloud model handles depth; the on-device model keeps working offline.")
                                .font(Aero.caption())
                                .foregroundStyle(Aero.textMuted)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(16)
                }

                SectionHeader(title: "Model source")
                HStack(spacing: 8) {
                    GsChoiceTile(
                        icon: "globe",
                        title: "Nexly Cloud",
                        subtitle: "Full-quality answers through the GS backend",
                        selected: !settings.preferLocal) {
                            settings.preferLocal = false
                            GSHaptics.select()
                        }
                    GsChoiceTile(
                        icon: "cpu",
                        title: "On-device",
                        subtitle: engineAvailable
                            ? "Private, and works with the network off"
                            : "No local model on this device yet",
                        selected: settings.preferLocal && engineAvailable) {
                            guard engineAvailable else { return }
                            settings.preferLocal = true
                            GSHaptics.select()
                        }
                }

                SectionHeader(title: "Reasoning effort")
                HStack(spacing: 8) {
                    GsChoiceTile(
                        icon: "bolt",
                        title: "Fast",
                        subtitle: "Immediate, concise replies",
                        selected: mode == "flash") { select("flash") }
                    GsChoiceTile(
                        icon: "brain",
                        title: "Thinking",
                        subtitle: "Spends longer on hard problems",
                        selected: mode == "thinking") { select("thinking") }
                    GsChoiceTile(
                        icon: "arrow.triangle.branch",
                        title: "Auto",
                        subtitle: "Chooses per question",
                        selected: mode == "auto") { select("auto") }
                }
                Text("This is the same switch the composer exposes — changing it here changes the next reply.")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)

                SectionHeader(title: "On this device")
                AeroCard {
                    AeroListRow(
                        title: "View model details",
                        subtitle: "What is loaded, and what it can do",
                        leading: {
                            Image(systemName: "cpu")
                                .font(.system(size: 14))
                                .foregroundStyle(Aero.accent)
                                .frame(width: 34, height: 34)
                                .background(Circle().fill(Aero.container))
                        },
                        trailing: {
                            Image(systemName: "chevron.right")
                                .font(.system(size: 12))
                                .foregroundStyle(Aero.textMuted)
                        },
                        action: { showModelDetails = true })
                }

                Text("On-device generation is slower and uses battery. It is the honest choice when the network is gone, not a silent replacement for the cloud engine.")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
                    .padding(.top, 4)
            }
            .padding(Aero.Spacing.m)
        }
        .background(Aero.background.ignoresSafeArea())
        .navigationTitle("AI Intelligence")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showModelDetails) {
            ModelDetailsSheet(engineAvailable: engineAvailable)
                .presentationDetents([.medium, .large])
        }
    }

    private func select(_ next: String) {
        guard ["flash", "thinking", "auto"].contains(next) else { return }
        mode = next
        UserDefaults.standard.set(next, forKey: "gs.mode.new")
        GSHaptics.select()
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
            ("square.grid.2x2", "Renders the same widget vocabulary as cloud answers")
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
