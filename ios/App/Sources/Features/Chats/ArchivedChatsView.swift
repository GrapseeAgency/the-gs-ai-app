import SwiftUI

/// Archived Conversations (docs/reference/pending/archived-conversations.png) —
/// the totals card with the reference's own lotus art, the archived rows with
/// their Restore pills and per-row menus, the About card, and the gated Clear
/// Archived action. Every row is a live ConversationStore row; the count, the
/// dates and the restores are measured, never drawn.
struct ArchivedChatsView: View {
    @ObservedObject private var store = ConversationStore.shared
    @EnvironmentObject private var router: Router
    @Environment(\.dismiss) private var dismiss
    @State private var menuOpen = false
    @State private var aboutOpen = false
    @State private var clearOpen = false
    @State private var renameTarget: StoredConversation?
    @State private var renameDraft = ""
    @State private var actionsTarget: StoredConversation?

    var body: some View {
        GsSheetPage(
            title: "Archived Conversations",
            subtitle: "View and restore your archived chats.",
            onBack: { dismiss() },
            headerTrailing: {
                AnyView(
                    Button {
                        menuOpen = true
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
            totalsCard.padding(.bottom, 10)

            if store.archivedConversations.isEmpty {
                EmptyStateView(
                    icon: "archivebox",
                    title: "Nothing archived",
                    message: "Conversations you archive will rest here until you bring them back."
                )
                .padding(.vertical, 8)
            } else {
                VStack(spacing: 0) {
                    ForEach(store.archivedConversations.indices, id: \.self) { index in
                        let conversation = store.archivedConversations[index]
                        if index > 0 {
                            Divider().background(Aero.outline.opacity(0.5)).padding(.leading, 66)
                        }
                        archivedRow(conversation)
                    }
                }
                .gsSheetCard()
            }

            aboutCard.padding(.top, 10)

            clearButton.padding(.top, 12)
            Spacer(minLength: 10)
        }
        .confirmationDialog("Archived chats", isPresented: $menuOpen, titleVisibility: .visible) {
            Button("Restore all") { restoreAll() }
            Button("Clear archived…", role: .destructive) { clearOpen = true }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Bring every archived conversation back, or clear the shelf for good. Both apply to all \(store.archivedConversations.count) archived chats.")
        }
        .alert("About Archived Chats", isPresented: $aboutOpen) {
            Button("Got it", role: .cancel) {}
        } message: {
            Text("Archived conversations are hidden from your main chat list, but you can restore them anytime. Restoring puts the chat back exactly where it was — history, attachments and all. Clearing archived chats removes the transcripts and their messages from this device for good.")
        }
        .alert("Clear archived?", isPresented: $clearOpen) {
            Button("Clear archived", role: .destructive) { clearAll() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This permanently removes all \(store.archivedConversations.count) archived conversations and their messages from this device. This cannot be undone.")
        }
        .alert("Rename chat", isPresented: Binding(
            get: { renameTarget != nil },
            set: { if !$0 { renameTarget = nil } }
        )) {
            TextField("Chat name", text: $renameDraft)
            Button("Rename") {
                let trimmed = renameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
                if let target = renameTarget, !trimmed.isEmpty {
                    store.rename(id: target.id, to: trimmed)
                    sync(target.id, title: trimmed)
                }
                renameTarget = nil
            }
            Button("Cancel", role: .cancel) { renameTarget = nil }
        } message: {
            Text("Give this conversation a name you'll recognise.")
        }
        .confirmationDialog(
            actionsTarget.map { gsConversationTitle($0.title) } ?? "",
            isPresented: Binding(
                get: { actionsTarget != nil },
                set: { if !$0 { actionsTarget = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Rename…") {
                if let target = actionsTarget {
                    renameDraft = target.title
                    renameTarget = target
                }
                actionsTarget = nil
            }
            Button(actionsTarget?.pinned == true ? "Unpin" : "Pin") {
                if let target = actionsTarget {
                    store.setPinned(id: target.id, !target.pinned)
                    sync(target.id, pinned: !target.pinned)
                }
                actionsTarget = nil
            }
            Button("Unarchive") {
                if let target = actionsTarget { restore(target) }
                actionsTarget = nil
            }
            Button("Delete", role: .destructive) {
                if let target = actionsTarget { deleteRow(target) }
                actionsTarget = nil
            }
            Button("Cancel", role: .cancel) { actionsTarget = nil }
        }
    }

    // MARK: Pieces

    private var totalsCard: some View {
        HStack(spacing: 12) {
            NexlyGlyph(name: "archive", size: 22, color: Aero.refAccent)
                .frame(width: 44, height: 44)
                .background(
                    RoundedRectangle(cornerRadius: 13, style: .continuous)
                        .fill(GsWellTone.lilac.wash)
                )
            VStack(alignment: .leading, spacing: 2) {
                Text("\(store.archivedConversations.count) Archived Conversation\(store.archivedConversations.count == 1 ? "" : "s")")
                    .font(.custom(Aero.productName(.semibold), size: 12, relativeTo: .body))
                    .foregroundStyle(Aero.text)
                Text("Keep your space tidy. Restore anytime.")
                    .font(.custom(Aero.productName(.regular), size: 9.5, relativeTo: .caption))
                    .foregroundStyle(Aero.textSecondary)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 12)
        .padding(.leading, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(alignment: .trailing) {
            Canvas { context, _ in
                GsRefScenes.archivedLotus(&context, CGSize(width: 128, height: 87))
            }
            .frame(width: 128, height: 87)
            .accessibilityHidden(true)
        }
        .gsSheetCard()
        .clipped()
    }

    private func archivedRow(_ conversation: StoredConversation) -> some View {
        let title = gsConversationTitle(conversation.title)
        let tone = topicTone(conversation.title)
        return HStack(spacing: 12) {
            NexlyGlyph(name: topicGlyph(conversation), size: 20, color: tone.ink)
                .frame(width: 44, height: 44)
                .background(
                    RoundedRectangle(cornerRadius: 13, style: .continuous)
                        .fill(tone.wash)
                )
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.custom(Aero.productName(.semibold), size: 12, relativeTo: .body))
                    .foregroundStyle(Aero.text)
                    .lineLimit(1)
                Text("Archived  •  \(Self.longDate(from: conversation.updatedAt))")
                    .font(.custom(Aero.productName(.regular), size: 9.5, relativeTo: .caption))
                    .foregroundStyle(Aero.textSecondary)
            }
            Spacer(minLength: 6)
            Button {
                restore(conversation)
            } label: {
                HStack(spacing: 6) {
                    NexlyGlyph(name: "restore", size: 14, color: Aero.textSecondary)
                    Text("Restore")
                        .font(.custom(Aero.productName(.medium), size: 10.5, relativeTo: .caption))
                        .foregroundStyle(Aero.text)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(Capsule().fill(Color.white.opacity(0.72)))
                .overlay(Capsule().strokeBorder(Color.white.opacity(0.85), lineWidth: 1))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Restore \(title)")
            Button {
                actionsTarget = conversation
            } label: {
                NexlyGlyph(name: "dots-vertical", size: 16, color: Aero.textTertiary)
                    .frame(width: 30, height: 30)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("More options for \(title)")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .contentShape(Rectangle())
        .onTapGesture { router.path.append(.chat(conversation.id)) }
    }

    private var aboutCard: some View {
        Button {
            aboutOpen = true
        } label: {
            HStack(spacing: 12) {
                NexlyGlyph(name: "info-circle", size: 20, color: Aero.textSecondary)
                    .frame(width: 40, height: 40)
                    .background(
                        RoundedRectangle(cornerRadius: 13, style: .continuous)
                            .fill(Color.white.opacity(0.6))
                    )
                VStack(alignment: .leading, spacing: 2) {
                    Text("About Archived Chats")
                        .font(.custom(Aero.productName(.semibold), size: 12, relativeTo: .body))
                        .foregroundStyle(Aero.text)
                    Text("Archived conversations are hidden from your main chat list, but you can restore them anytime.")
                        .font(.custom(Aero.productName(.regular), size: 9.5, relativeTo: .caption))
                        .foregroundStyle(Aero.textSecondary)
                        .multilineTextAlignment(.leading)
                }
                Spacer(minLength: 4)
                NexlyGlyph(name: "chevron-right", size: 15, color: Aero.textTertiary)
            }
            .padding(12)
        }
        .buttonStyle(.plain)
        .gsSheetCard()
        .accessibilityLabel("About archived chats")
    }

    private var clearButton: some View {
        Button {
            clearOpen = true
        } label: {
            VStack(spacing: 3) {
                HStack(spacing: 8) {
                    NexlyGlyph(name: "trash", size: 16, color: Color(red: 0.894, green: 0.341, blue: 0.420))
                    Text("Clear Archived")
                        .font(.custom(Aero.productName(.semibold), size: 12, relativeTo: .body))
                        .foregroundStyle(Color(red: 0.894, green: 0.341, blue: 0.420))
                }
                Text("Permanently remove all archived conversations.")
                    .font(.custom(Aero.productName(.regular), size: 9.5, relativeTo: .caption))
                    .foregroundStyle(Aero.textSecondary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(Color(red: 0.894, green: 0.341, blue: 0.420).opacity(0.10))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .strokeBorder(Color(red: 0.894, green: 0.341, blue: 0.420).opacity(0.22), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Clear archived")
    }

    // MARK: Mutations

    private func restoreAll() {
        for conversation in store.archivedConversations {
            store.setArchived(id: conversation.id, false)
            sync(conversation.id, archived: false)
        }
    }

    private func clearAll() {
        for conversation in store.archivedConversations {
            store.delete(id: conversation.id)
            syncDelete(conversation.id)
        }
    }

    private func restore(_ conversation: StoredConversation) {
        withAnimation(Aero.snappy) {
            store.setArchived(id: conversation.id, false)
        }
        sync(conversation.id, archived: false)
    }

    private func deleteRow(_ conversation: StoredConversation) {
        store.delete(id: conversation.id)
        syncDelete(conversation.id)
    }

    private func sync(_ id: String, pinned: Bool? = nil, archived: Bool? = nil, title: String? = nil) {
        guard !id.hasPrefix("demo-"), !id.hasPrefix("local-") else { return }
        Task { try? await APIClient.shared.updateConversation(id: id, pinned: pinned, archived: archived, title: title) }
    }

    private func syncDelete(_ id: String) {
        guard !id.hasPrefix("demo-"), !id.hasPrefix("local-") else { return }
        Task { try? await APIClient.shared.deleteConversation(id: id) }
    }

    // MARK: Topic mapping — the shipped classifier, the pre-chat page's map.

    private func topicGlyph(_ conversation: StoredConversation) -> String {
        if conversation.pinned { return "pin" }
        switch DrawerTopic.topic(for: conversation.title) {
        case .planner, .learning: return "book"
        case .travel, .itinerary: return "plane"
        case .code, .review: return "code"
        case .data, .money: return "chart-bar"
        default: return "sparkles"
        }
    }

    private func topicTone(_ title: String) -> GsWellTone {
        switch DrawerTopic.topic(for: title) {
        case .code, .review, .planner: return .peach
        case .learning, .data, .money: return .mint
        default: return .lilac
        }
    }

    static func longDate(from iso: String) -> String {
        guard let date = try? Date(iso, strategy: .iso8601) else { return "earlier" }
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d, yyyy"
        return formatter.string(from: date)
    }
}
