import BotAvatarsKit
import SwiftUI

/// Persistent four-way avatar choice used by every assistant turn.
struct AssistantAvatarSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var settings = SettingsStore.shared

    private let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

    private var selectedStyle: AssistantAvatarStyle {
        AssistantAvatarStyle.fromStoredValue(settings.assistantAvatar)
    }

    var body: some View {
        GsSettingsPage(
            title: "Bot avatar",
            subtitle: "Choose how Nexly appears beside replies.",
            onBack: { dismiss() }
        ) {
            GsGroupHeader(title: "Assistant identity", caption: "Choose one of four styles")
            GsGroupCard {
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(AssistantAvatarStyle.allCases) { style in
                        avatarChoice(style)
                    }
                }
                .padding(14)
            }

            Text("Your choice is saved on this device and used across every chat.")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 4)
                .padding(.top, Aero.Spacing.s)
        }
    }

    private func avatarChoice(_ style: AssistantAvatarStyle) -> some View {
        let isSelected = selectedStyle == style
        return Button {
            settings.assistantAvatar = style.rawValue
            GSHaptics.select()
        } label: {
            VStack(spacing: 8) {
                BotAvatar(
                    type: style.botAvatarType,
                    state: .default,
                    size: 72,
                    interactive: false
                )
                Text(style.title)
                    .font(Aero.label())
                    .foregroundStyle(Aero.text)
                Label(
                    isSelected ? "Selected" : "Tap to select",
                    systemImage: isSelected ? "checkmark.circle.fill" : "circle"
                )
                .font(Aero.caption())
                .foregroundStyle(isSelected ? Aero.accent : Aero.textMuted)
            }
            .frame(maxWidth: .infinity, minHeight: 138)
            .padding(10)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(isSelected ? Aero.accent.opacity(0.12) : Aero.container)
            )
            .overlay {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .strokeBorder(isSelected ? Aero.accent : Aero.outline, lineWidth: isSelected ? 1.5 : 1)
            }
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityLabel("\(style.title) bot avatar")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
