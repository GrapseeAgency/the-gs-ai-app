import SwiftUI

/// Attach picker for ChatDetail — six quick sources. Every option hands its
/// label back to the caller and closes the sheet; attaching is a local
/// placeholder this pass (real imports land with device builds).
struct AttachmentSheetView: View {

    var onSelect: (String) -> Void = { _ in }

    @Environment(\.dismiss) private var dismiss

    /// Explicit init — keeps the sheet callable with a trailing closure from
    /// other files regardless of memberwise-initializer access rules.
    init(onSelect: @escaping (String) -> Void = { _ in }) {
        self.onSelect = onSelect
    }

    private struct Option: Identifiable {
        let id = UUID()
        let label: String
        let icon: String
    }

    private let options: [Option] = [
        .init(label: "Camera", icon: "camera.fill"),
        .init(label: "Gallery", icon: "photo"),
        .init(label: "Files", icon: "folder"),
        .init(label: "Document", icon: "doc.text"),
        .init(label: "Code", icon: "curlybraces"),
        .init(label: "Prompt template", icon: "lightbulb")
    ]

    private let columns = [
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible(), spacing: 12)
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            Text("Attach")
                .font(Aero.headline())
                .foregroundStyle(Aero.text)
            Text("Everything lands in the conversation as a local placeholder.")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
            LazyVGrid(columns: columns, spacing: 12) {
                ForEach(options) { option in
                    Button {
                        onSelect(option.label)
                        dismiss()
                    } label: {
                        VStack(spacing: Aero.Spacing.s) {
                            Image(systemName: option.icon)
                                .font(.system(size: 18, weight: .medium))
                                .foregroundStyle(Aero.text)
                                .frame(width: 52, height: 52)
                                .background(RoundedRectangle(cornerRadius: 16).fill(Aero.container))
                            Text(option.label)
                                .font(Aero.label())
                                .foregroundStyle(Aero.text)
                                .lineLimit(1)
                                .minimumScaleFactor(0.7)
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(KineticPressStyle())
                }
            }
            Spacer(minLength: 0)
        }
        .padding(Aero.Spacing.m)
        .presentationDetents([.height(340)])
    }
}
