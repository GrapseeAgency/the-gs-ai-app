import SwiftUI

/// ONE NOTICE, ON ITS OWN PAGE — iOS twin of `NotificationDetailScreen.kt`.
///
/// The inbox opened a half-height `.sheet` holding a title and a paragraph.
/// A notice has a kind, a time, a full body and usually somewhere it wants to
/// take you; a detent cannot hold any of that, and it made the inbox the one
/// place where tapping a row did not push a page.
///
/// Opening this page is still what marks the notice read, because it is true:
/// you have now seen it. "Mark as unread" puts the dot back and leaves.
struct NotificationDetailView: View {

    let noticeID: Double
    var now: Date = Date()

    @ObservedObject private var inbox = GsNoticeInbox.shared
    @Environment(\.dismiss) private var dismiss

    @State private var confirmDelete = false

    init(noticeID: Double, now: Date = Date()) {
        self.noticeID = noticeID
        self.now = now
    }

    private var notice: GsNoticeEntry? {
        inbox.entries.first { $0.id == noticeID }
    }

    var body: some View {
        Group {
            if let notice {
                page(notice)
            } else {
                missing
            }
        }
        .onAppear {
            if let notice, !notice.read { inbox.markRead(id: notice.id) }
        }
        .gsDialog(isPresented: $confirmDelete) {
            GsDialog(
                title: "Delete this notification?",
                message: "It disappears from the inbox on this device. Nexly cannot send it again — the notice was a one-time message.",
                confirmLabel: "Delete",
                onConfirm: {
                    if let notice { inbox.delete(id: notice.id) }
                    dismiss()
                },
                onDismiss: { confirmDelete = false },
                destructive: true,
                glyph: "trash",
                tone: .rose)
        }
    }

    // MARK: Page

    private func page(_ notice: GsNoticeEntry) -> some View {
        let style = GsNoticeStyle.forKind(notice.kindId)
        let destination = NoticeDestination.forKind(notice.kindId)
        return GsSheetPage(
            title: "Notification",
            subtitle: "\(GsNoticeStyle.relative(notice.createdAt, now: now)) • \(style.label)",
            onBack: { dismiss() }
        ) {
            // ---- The notice itself -------------------------------------
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .top, spacing: 11) {
                    GsWellTile(glyph: style.glyph, tone: style.tone, size: 38)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(notice.title)
                            .font(.custom(Aero.productName(.semibold), size: 15, relativeTo: .headline))
                            .foregroundStyle(Aero.text)
                            .fixedSize(horizontal: false, vertical: true)
                        HStack(spacing: 6) {
                            GsTagPill(text: style.label, tone: style.tone.ink)
                            Text(GsNoticeStyle.relative(notice.createdAt, now: now))
                                .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2))
                                .foregroundStyle(Aero.textTertiary)
                        }
                    }
                    Spacer(minLength: 0)
                }
                Spacer().frame(height: 12)
                Divider().overlay(Aero.outline.opacity(0.5))
                Spacer().frame(height: 12)
                Text(notice.body)
                    .font(.custom(Aero.productName(.regular), size: 13, relativeTo: .body))
                    .lineSpacing(5)
                    .foregroundStyle(Aero.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer().frame(height: 10)
                Text("Sent \(Self.absolute(notice.createdAt))")
                    .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2))
                    .foregroundStyle(Aero.textTertiary)
            }
            .padding(16)
            .gsSheetCard(corner: 20)

            // ---- Where it points ---------------------------------------
            GsSheetGroupHeader("Related", caption: "The screen this notice is about")
            GsSheetRow(
                title: destination.label,
                detail: destination.detail,
                action: { destination.route?(dismiss) }
            ) {
                GsWellTile(glyph: destination.glyph, tone: .blue, size: 28)
            } trailing: {
                NexlyGlyph(name: "chevron-right", size: 12, color: Aero.textTertiary)
            }
            .gsSheetCard(corner: 20)

            // ---- What you can do ---------------------------------------
            GsSheetGroupHeader("This notice", caption: "Kept on this device only")
            VStack(spacing: 0) {
                GsSheetRow(
                    title: "Mark as unread",
                    detail: "Puts the dot back in the inbox",
                    action: {
                        inbox.markUnread(id: notice.id)
                        dismiss()
                    }
                ) {
                    GsWellTile(glyph: "bell", tone: .peach, size: 28)
                } trailing: {
                    NexlyGlyph(name: "chevron-right", size: 12, color: Aero.textTertiary)
                }
                Divider().overlay(Aero.outline.opacity(0.4)).padding(.leading, 12)
                GsSheetRow(
                    title: "Delete this notification",
                    detail: "Removes it from the inbox on this device",
                    action: { confirmDelete = true }
                ) {
                    GsWellTile(glyph: "trash", tone: .rose, size: 28)
                } trailing: {
                    NexlyGlyph(name: "chevron-right", size: 12, color: Aero.textTertiary)
                }
            }
            .gsSheetCard(corner: 20)

            Spacer().frame(height: 12)
            Text("Nexly only writes a notice here after the system has actually posted it, so this list cannot contain a message you were never sent.")
                .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2))
                .lineSpacing(2)
                .foregroundStyle(Aero.textTertiary)
                .padding(.horizontal, 6)
        }
    }

    private var missing: some View {
        GsSheetPage(
            title: "Notification",
            subtitle: "This notice is no longer on this device",
            onBack: { dismiss() }
        ) {
            VStack(spacing: 10) {
                GsWellTile(glyph: "bell", tone: .blue, size: 40)
                Text("It was deleted, or the inbox rotated it out.")
                    .font(.custom(Aero.productName(.regular), size: 12, relativeTo: .footnote))
                    .foregroundStyle(Aero.textSecondary)
                Button("Back to notifications") { dismiss() }
                    .font(.custom(Aero.productName(.medium), size: 12, relativeTo: .footnote))
            }
            .frame(maxWidth: .infinity)
            .padding(18)
            .gsSheetCard(corner: 20)
        }
    }

    private static func absolute(_ date: Date) -> String {
        let f = DateFormatter()
        f.dateFormat = "d MMM yyyy, HH:mm"
        return f.string(from: date)
    }
}

/// Where a notice of this kind wants to take the reader.
private struct NoticeDestination {
    let label: String
    let detail: String
    let glyph: String
    /// nil when the destination is a root the sheet cannot reach; the row then
    /// reads as information rather than pretending to navigate.
    let route: ((DismissAction) -> Void)?

    static func forKind(_ kindId: String) -> NoticeDestination {
        switch kindId {
        case "messages":
            return .init(label: "Open your chats", detail: "The conversation this message was about",
                         glyph: "message-circle", route: { dismiss in dismiss() })
        case "account-activity":
            return .init(label: "Account & Security", detail: "Sessions, biometrics and the address on this install",
                         glyph: "user", route: nil)
        case "product-updates", "ai-updates":
            return .init(label: "About Nexly", detail: "Version, build and what changed",
                         glyph: "sparkles", route: nil)
        case "tips":
            return .init(label: "Help & Support", detail: "Guides for what this build can do",
                         glyph: "bulb", route: nil)
        default:
            return .init(label: "Notification settings", detail: "Choose what Nexly is allowed to tell you",
                         glyph: "bell", route: nil)
        }
    }
}
