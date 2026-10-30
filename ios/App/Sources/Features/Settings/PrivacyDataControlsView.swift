import SwiftUI
import UIKit

/// Privacy & Data Controls — docs/reference/pending/privacy-data-controls.png.
///
/// The Android reference screen (`PrivacySettingsScreen`) is the source of the
/// structure and the honest deltas; this is its iOS counterpart on the settings
/// kit's density. Every row resolves: App Permissions opens the app's system
/// settings, Third-Party Integrations opens Connected Apps, Chat History opens
/// the search page, Export hands the JSON to the system share sheet, and Delete
/// wipes this device and signs the session out. The two rows with no
/// destination say so and draw no chevron.
struct PrivacyDataControlsView: View {

    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var router: Router

    @State private var confirmClear = false

    private let lotusAlpha: Double = 0.55

    var body: some View {
        GsSettingsPage(
            title: "Privacy & Data Controls",
            subtitle: "Your data, your control",
            onBack: { dismiss() }
        ) {
            hero

            GsGroupHeader(title: "Data & Permissions", caption: "Manage what Nexly can access")
            GsGroupCard {
                GsSettingsRow(
                    title: "App Permissions",
                    detail: "Microphone, notifications, Do-Not-Disturb and biometric unlock",
                    action: openAppSettings
                ) {
                    GsWellTile(glyph: "camera", tone: .lilac, size: 28)
                } trailing: {
                    chevron
                }
                GsSettingsRow(
                    title: "Personalization & Ads",
                    detail: "No ad profile is built from your activity",
                    action: nil
                ) {
                    GsWellTile(glyph: "user", tone: .mint, size: 28)
                } trailing: {
                    EmptyView()
                }
                GsSettingsRow(
                    title: "Third-Party Integrations",
                    detail: "No services are connected",
                    showDivider: false,
                    action: { router.path.append(.connectedApps) }
                ) {
                    GsWellTile(glyph: "paperclip", tone: .blue, size: 28)
                } trailing: {
                    chevron
                }
            }

            GsGroupHeader(title: "Conversation & Activity", caption: "Control your chat data")
            GsGroupCard {
                GsSettingsRow(
                    title: "Chat History",
                    detail: "View, search, and manage your conversations",
                    action: { router.path.append(.chatSearch) }
                ) {
                    GsWellTile(glyph: "message-circle", tone: .peach, size: 28)
                } trailing: {
                    chevron
                }
                GsSettingsRow(
                    title: "Search History",
                    detail: "Searches are not recorded",
                    action: nil
                ) {
                    GsWellTile(glyph: "search", tone: .lilac, size: 28)
                } trailing: {
                    EmptyView()
                }
                GsSettingsRow(
                    title: "Data Retention",
                    // The owner page can now choose a period and auto-delete.
                    detail: "Choose how long we keep your data",
                    showDivider: false,
                    action: { router.path.append(.dataExportAccountDeletion) }
                ) {
                    GsWellTile(glyph: "clock", tone: .rose, size: 28)
                } trailing: {
                    chevron
                }
            }

            GsGroupHeader(title: "Export & Deletion", caption: "Download or delete your data")
            GsGroupCard {
                GsSettingsRow(
                    title: "Export Your Data",
                    detail: "Download a copy of your information",
                    action: { router.path.append(.dataExportAccountDeletion) }
                ) {
                    GsWellTile(glyph: "download", tone: .blue, size: 28)
                } trailing: {
                    chevron
                }
                GsSettingsRow(
                    title: "Clear Local Data",
                    detail: "Remove chats, files and drafts without signing out",
                    action: { confirmClear = true }
                ) {
                    GsWellTile(glyph: "eraser", tone: .peach, size: 28)
                } trailing: {
                    chevron
                }
                GsSettingsRow(
                    title: "Delete Your Account",
                    detail: "Permanently remove everything this app stores on this device",
                    showDivider: false,
                    action: { router.path.append(.dataExportAccountDeletion) }
                ) {
                    GsWellTile(glyph: "trash", tone: .rose, size: 28)
                } trailing: {
                    chevron
                }
            }

            footer
            Spacer(minLength: 10)
        }
        .confirmationDialog(
            "Clear local data?",
            isPresented: $confirmClear,
            titleVisibility: .visible
        ) {
            Button("Clear local data", role: .destructive) {
                ConversationStore.shared.wipeAllContent()
                GSHaptics.warning()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Cached chats, files and drafts on this device will be removed. Your account is untouched.")
        }
    }

    // MARK: Pieces

    private var hero: some View {
        ZStack(alignment: .leading) {
            Image("GSHomeLotusNew")
                .resizable()
                .scaledToFill()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .opacity(lotusAlpha)
            LinearGradient(
                colors: [Aero.glassSurface,
                         Aero.glassSurface.opacity(0.55),
                         .clear],
                startPoint: .leading,
                endPoint: .trailing
            )
            HStack(spacing: 12) {
                GsWellTile(glyph: "shield", tone: .peach, size: 56)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Your privacy matters")
                        .font(.custom(Aero.productName(.semibold), size: 15, relativeTo: .headline))
                        .foregroundStyle(Aero.text)
                    Text("Nexly is designed with your privacy in mind. You're always in control of your data, conversations, and personal information.")
                        .font(.custom(Aero.productName(.regular), size: 12.5, relativeTo: .caption))
                        .foregroundStyle(Aero.textTertiary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 14)
        }
        .frame(height: 96)
        .clipShape(RoundedRectangle(cornerRadius: 22))
        .overlay(
            RoundedRectangle(cornerRadius: 22)
                .strokeBorder(.white.opacity(0.55), lineWidth: 1)
        )
    }

    private var footer: some View {
        HStack(alignment: .top, spacing: 10) {
            GsWellTile(glyph: "lock", tone: .lilac, size: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text("We respect your privacy")
                    .font(.custom(Aero.productName(.medium), size: 10.5, relativeTo: .caption2))
                    .foregroundStyle(Aero.text)
                Text("Your data is private to this app, never sold, and only used to improve your experience.")
                    .font(.custom(Aero.productName(.regular), size: 9, relativeTo: .caption2))
                    .foregroundStyle(Aero.textTertiary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 14)
        .padding(.vertical, 13)
        .gsSheetCard(corner: 20)
    }

    private var chevron: some View {
        NexlyGlyph(name: "chevron-right", size: 13, color: Aero.textTertiary)
    }

    private func openAppSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}
