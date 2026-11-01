import SwiftUI
import UIKit

/// Conversation Details (docs/reference/pending/conversation-details.png) —
/// the conversation hero (icon tile, title, pin mark, started time, message
/// count and span), the Conversation Actions group — rename, the pin switch,
/// archive, share, export, delete — the Memory & Context group, the About
/// card, and the closing Delete Conversation action. Every value is measured
/// from the stored conversation; every action is the real operation.
struct ConversationDetailsView: View {
    let conversationID: String

    @ObservedObject private var conversations = ConversationStore.shared
    @EnvironmentObject private var router: Router
    @Environment(\.dismiss) private var dismiss
    @State private var renameOpen = false
    @State private var renameDraft = ""
    @State private var deleteOpen = false

    private var conversation: StoredConversation? {
        conversations.conversations.first { $0.id == conversationID }
    }

    private var messages: [StoredMessage] {
        conversations.messages(for: conversationID)
    }

    private var title: String {
        conversation.map { gsConversationTitle($0.title) } ?? "This chat"
    }

    var body: some View {
        GsSheetPage(
            title: "Conversation Details",
            subtitle: "Manage your conversation.",
            onBack: { dismiss() },
            headerTrailing: {
                AnyView(
                    Button {
                        renameDraft = title
                        renameOpen = true
                    } label: {
                        NexlyGlyph(name: "dots-vertical", size: 16, color: Aero.text)
                            .frame(width: 36, height: 36)
                            .gsGlass(.floatingControl, corner: 18)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("More options")
                )
            }
        ) {
            heroCard.padding(.bottom, 8)

            GsSheetGroupHeader("Conversation Actions", caption: "Quick actions for this chat")
            VStack(spacing: 0) {
                actionRow(glyph: "pencil", tone: .lilac, title: "Rename Conversation", subtitle: "Give this chat a new name.") {
                    renameDraft = title
                    renameOpen = true
                }
                divider
                pinRow
                divider
                actionRow(glyph: "archive", tone: .mint, title: "Archive Conversation", subtitle: "Move this chat to archive.") {
                    archiveConversation()
                }
                divider
                actionRow(glyph: "share-2", tone: .lilac, title: "Share Conversation", subtitle: "Share this chat link.") {
                    shareConversation()
                }
                divider
                actionRow(glyph: "download", tone: .peach, title: "Export Conversation", subtitle: "Download this chat as a file.") {
                    exportConversation()
                }
                divider
                actionRow(glyph: "trash", tone: .mint, title: "Delete Conversation", subtitle: "Remove this chat from your history.") {
                    deleteOpen = true
                }
            }
            .gsSheetCard()

            GsSheetGroupHeader("Memory & Context", caption: "Manage related memory")
            VStack(spacing: 0) {
                actionRow(glyph: "brain", tone: .lilac, title: "Manage Memory", subtitle: "View or clear memories used in this chat.") {
                    router.path.append(.memory)
                }
            }
            .gsSheetCard()

            aboutCard.padding(.top, 10)

            deleteButton.padding(.top, 14)
            Spacer(minLength: 10)
        }
        .alert("Rename Conversation", isPresented: $renameOpen) {
            TextField("Conversation name", text: $renameDraft)
            Button("Rename") {
                let trimmed = renameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty, conversation != nil {
                    conversations.rename(id: conversationID, to: trimmed)
                    sync(title: trimmed)
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Give this conversation a name you'll recognise.")
        }
        .alert("Delete conversation?", isPresented: $deleteOpen) {
            Button("Delete", role: .destructive) { deleteConversation() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This removes \"\(title)\" and its messages from this device. This cannot be undone.")
        }
    }

    // MARK: Pieces

    private var heroCard: some View {
        HStack(spacing: 14) {
            NexlyGlyph(name: "message-circle", size: 26, color: Aero.refAccent)
                .frame(width: 56, height: 56)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(GsWellTone.lilac.wash)
                )
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 8) {
                    Text(title)
                        .font(.custom(Aero.productName(.semibold), size: 15, relativeTo: .title3))
                        .foregroundStyle(Aero.text)
                        .lineLimit(1)
                    if conversation?.pinned == true {
                        NexlyGlyph(name: "pin", size: 15, color: Aero.refAccent)
                            .accessibilityLabel("Pinned")
                    }
                }
                Text("Started \(Self.longDateTime(from: conversation?.createdAt))")
                    .font(.custom(Aero.productName(.regular), size: 9.5, relativeTo: .caption))
                    .foregroundStyle(Aero.textSecondary)
                Text("\(messages.count) messages  •  ~\(span)")
                    .font(.custom(Aero.productName(.regular), size: 9.5, relativeTo: .caption))
                    .foregroundStyle(Aero.textSecondary)
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .gsSheetCard()
    }

    private var pinRow: some View {
        HStack(spacing: 12) {
            NexlyGlyph(name: "pin", size: 20, color: GsWellTone.peach.ink)
                .frame(width: 40, height: 40)
                .background(
                    RoundedRectangle(cornerRadius: 13, style: .continuous)
                        .fill(GsWellTone.peach.wash)
                )
            VStack(alignment: .leading, spacing: 2) {
                Text("Pin Conversation")
                    .font(.custom(Aero.productName(.semibold), size: 12, relativeTo: .body))
                    .foregroundStyle(Aero.text)
                Text("Keep this chat at the top.")
                    .font(.custom(Aero.productName(.regular), size: 9.5, relativeTo: .caption))
                    .foregroundStyle(Aero.textSecondary)
            }
            Spacer(minLength: 6)
            Toggle("", isOn: Binding(
                get: { conversation?.pinned ?? false },
                set: { on in
                    conversations.setPinned(id: conversationID, on)
                    sync(pinned: on)
                }
            ))
            .tint(Aero.refAccent)
            .labelsHidden()
            .accessibilityLabel("Pin Conversation")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
    }

    private var aboutCard: some View {
        HStack(spacing: 12) {
            NexlyGlyph(name: "bulb", size: 20, color: Aero.refAccent)
                .frame(width: 40, height: 40)
                .background(
                    RoundedRectangle(cornerRadius: 13, style: .continuous)
                        .fill(GsWellTone.lilac.wash)
                )
            VStack(alignment: .leading, spacing: 2) {
                Text("About This Conversation")
                    .font(.custom(Aero.productName(.semibold), size: 12, relativeTo: .body))
                    .foregroundStyle(Aero.text)
                Text("This conversation may include personal or sensitive information. Manage it responsibly.")
                    .font(.custom(Aero.productName(.regular), size: 9.5, relativeTo: .caption))
                    .foregroundStyle(Aero.textSecondary)
            }
            Spacer(minLength: 0)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(GsWellTone.lilac.wash.opacity(0.55))
        .gsSheetCard()
    }

    private var deleteButton: some View {
        Button {
            deleteOpen = true
        } label: {
            HStack(spacing: 8) {
                NexlyGlyph(name: "trash", size: 16, color: .white)
                Text("Delete Conversation")
                    .font(.custom(Aero.productName(.semibold), size: 13, relativeTo: .body))
                    .foregroundStyle(.white)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 13)
            .background(
                Capsule().fill(
                    LinearGradient(colors: [Aero.refAccent.opacity(0.72), Aero.refAccent],
                                   startPoint: .leading, endPoint: .trailing)
                )
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Delete Conversation")
    }

    private var divider: some View {
        Divider().background(Aero.outline.opacity(0.5)).padding(.leading, 66)
    }

    private func actionRow(glyph: String, tone: GsWellTone, title: String, subtitle: String,
                           onTap: @escaping () -> Void) -> some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                NexlyGlyph(name: glyph, size: 20, color: tone.ink)
                    .frame(width: 40, height: 40)
                    .background(
                        RoundedRectangle(cornerRadius: 13, style: .continuous)
                            .fill(tone.wash)
                    )
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.custom(Aero.productName(.semibold), size: 12, relativeTo: .body))
                        .foregroundStyle(Aero.text)
                    Text(subtitle)
                        .font(.custom(Aero.productName(.regular), size: 9.5, relativeTo: .caption))
                        .foregroundStyle(Aero.textSecondary)
                }
                Spacer(minLength: 4)
                NexlyGlyph(name: "chevron-right", size: 15, color: Aero.textTertiary)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
    }

    // MARK: Actions

    private func archiveConversation() {
        conversations.setArchived(id: conversationID, true)
        sync(archived: true)
        dismiss()
    }

    private func shareConversation() {
        guard let conversation else { return }
        // There are no recipients on this build, so the record keeps what is
        // true: what was shared, and when. The Shared Conversations page
        // lists it.
        SharedConversationsStore.shared.record(
            conversationId: conversationID,
            title: gsConversationTitle(conversation.title)
        )
        let body = "# \(title)\n\n" + messages.map { message in
            let who = message.role == "user" ? "You" : "Nexly"
            return "\(who): \(message.content)"
        }.joined(separator: "\n\n")
        let activity = UIActivityViewController(
            activityItems: [body.isEmpty ? "No messages yet." : body],
            applicationActivities: nil
        )
        present(activity)
    }

    private func exportConversation() {
        let body = "# \(title)\n\n" + messages.map { message in
            let who = message.role == "user" ? "You" : "Nexly"
            return "\(who): \(message.content)"
        }.joined(separator: "\n\n")
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("\(title).txt")
        try? body.write(to: url, atomically: true, encoding: .utf8)
        let activity = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        present(activity)
    }

    private func deleteConversation() {
        conversations.delete(id: conversationID)
        syncDelete()
        dismiss()
    }

    private func present(_ activity: UIActivityViewController) {
        UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow?.rootViewController }
            .first?.present(activity, animated: true)
    }

    private func sync(pinned: Bool? = nil, archived: Bool? = nil, title: String? = nil) {
        guard !conversationID.hasPrefix("demo-"), !conversationID.hasPrefix("local-") else { return }
        Task { try? await APIClient.shared.updateConversation(id: conversationID, pinned: pinned, archived: archived, title: title) }
    }

    private func syncDelete() {
        guard !conversationID.hasPrefix("demo-"), !conversationID.hasPrefix("local-") else { return }
        Task { try? await APIClient.shared.deleteConversation(id: conversationID) }
    }

    // MARK: Formatting

    /// First-to-last message span, in the shortest honest unit.
    private var span: String {
        let stamps = messages.compactMap { try? Date($0.createdAt, strategy: .iso8601) }
        guard stamps.count >= 2, let first = stamps.min(), let last = stamps.max() else { return "1 min" }
        let minutes = Int(last.timeIntervalSince(first) / 60)
        if minutes < 1 { return "1 min" }
        if minutes < 60 { return "\(minutes) min" }
        if minutes < 1_440 { return "\(minutes / 60)h \(minutes % 60)m" }
        return "\(minutes / 1_440)d"
    }

    static func longDateTime(from iso: String?) -> String {
        guard let iso, let date = try? Date(iso, strategy: .iso8601) else { return "earlier" }
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d, yyyy, h:mm a"
        return formatter.string(from: date)
    }
}
