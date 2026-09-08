import SwiftUI

/// Archive shelf — live from `ConversationStore` (everything the user archived,
/// not a demo subset) with restore + delete affordances as native swipe
/// actions (Task 86-e) + context menu; the server PATCH is a best-effort echo
/// once the backend is reachable.
struct ArchivedChatsView: View {

    @ObservedObject private var store = ConversationStore.shared
    @State private var reloadToken = 0
    @State private var renameTarget: StoredConversation?
    @State private var renameDraft = ""

    /// Card-row insets (Task 86-e) — same shape as ChatsListView's.
    private var rowInsets: EdgeInsets {
        EdgeInsets(top: 4, leading: Aero.Spacing.m, bottom: 4, trailing: Aero.Spacing.m)
    }

    var body: some View {
        List {
            Text("Archived")
                .font(Aero.displayTitle())
                .foregroundStyle(Aero.text)
                .accessibilityAddTraits(.isHeader)
                .gsListRowChrome(EdgeInsets(
                    top: Aero.Spacing.s, leading: Aero.Spacing.m, bottom: 0, trailing: Aero.Spacing.m))
            Text("Archived chats stay searchable but leave your main list.")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
                .gsListRowChrome(EdgeInsets(
                    top: Aero.Spacing.l, leading: Aero.Spacing.m, bottom: Aero.Spacing.l - 4, trailing: Aero.Spacing.m))

            if store.archivedConversations.isEmpty {
                EmptyStateView(
                    icon: "archivebox",
                    title: "Nothing archived",
                    message: "Conversations you archive will rest here until you bring them back."
                )
                .gsListRowChrome(rowInsets)
            } else {
                ForEach(store.archivedConversations) { conversation in
                    archiveRow(conversation)
                }
            }

            // Tail spacer reproduces the old LazyVStack bottom padding.
            Color.clear
                .frame(height: Aero.Spacing.xl - 4)
                .gsListRowChrome(EdgeInsets())
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .environment(\.defaultMinListRowHeight, 1)
        .background(Aero.background.ignoresSafeArea())
        .onAppear { reloadToken += 1 }
        .alert("Rename chat", isPresented: Binding(
            get: { renameTarget != nil },
            set: { if !$0 { renameTarget = nil } }
        )) {
            TextField("Chat name", text: $renameDraft)
            Button("Rename") {
                let trimmed = renameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
                if let target = renameTarget, !trimmed.isEmpty {
                    store.rename(id: target.id, to: trimmed)
                    syncSet(target.id, title: trimmed)
                }
                renameTarget = nil
            }
            Button("Cancel", role: .cancel) { renameTarget = nil }
        } message: {
            Text("Give this conversation a name you'll recognise.")
        }
    }

    /// One archived card as a native List row (Task 86-e): leading swipe
    /// restores, trailing swipe deletes — the same mutation paths as the
    /// inline buttons and the context menu (`restore` / `deleteRow`).
    private func archiveRow(_ conversation: StoredConversation) -> some View {
        AeroListRow(
            title: gsConversationTitle(conversation.title),
            subtitle: "Archived · \(Self.relativeTime(from: conversation.updatedAt))",
            leading: {
                Image(systemName: "archivebox")
                    .font(.system(size: 14))
                    .foregroundStyle(Aero.text)
                    .frame(width: 36, height: 36)
                    .background(Circle().fill(Aero.containerHigh))
            },
            trailing: {
                HStack(spacing: 10) {
                    Button {
                        restore(conversation)
                    } label: {
                        Image(systemName: "arrow.up.circle")
                            .font(.system(size: 18))
                            .foregroundStyle(Aero.accent)
                    }
                    .buttonStyle(KineticPressStyle())
                    .accessibilityLabel("Unarchive chat")
                    Button {
                        deleteRow(conversation)
                    } label: {
                        Image(systemName: "trash")
                            .font(.system(size: 16))
                            .foregroundStyle(Aero.textMuted)
                    }
                    .buttonStyle(KineticPressStyle())
                    .accessibilityLabel("Delete chat")
                }
            }
        )
        .swipeActions(edge: .leading, allowsFullSwipe: true) {
            Button {
                restore(conversation)
            } label: {
                Label("Unarchive", systemImage: "arrow.up.circle")
            }
            .tint(Aero.accent)
        }
        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
            Button(role: .destructive) {
                deleteRow(conversation)
            } label: {
                Label("Delete", systemImage: "trash")
            }
        }
        .contextMenu {
            Button {
                restore(conversation)
            } label: {
                Label("Unarchive", systemImage: "arrow.up.circle")
            }
            Button {
                renameTarget = conversation
                renameDraft = conversation.title
            } label: {
                Label("Rename…", systemImage: "pencil")
            }
            Button(role: .destructive) {
                deleteRow(conversation)
            } label: {
                Label("Delete", systemImage: "trash")
            }
        }
        .gsListRowChrome(rowInsets)
    }

    /// Shared by the inline trash button, the context menu and the swipe —
    /// one mutation path (the row's own buttons carry no haptic, unchanged).
    private func deleteRow(_ conversation: StoredConversation) {
        store.delete(id: conversation.id)
        syncDelete(conversation.id)
    }

    private func restore(_ conversation: StoredConversation) {
        withAnimation(Aero.snappy) {
            store.setArchived(id: conversation.id, false)
        }
        syncSet(conversation.id, archived: false)
    }

    private func syncSet(_ id: String, archived: Bool? = nil, title: String? = nil) {
        guard !id.hasPrefix("demo-"), !id.hasPrefix("local-") else { return }
        Task { try? await APIClient.shared.updateConversation(id: id, archived: archived, title: title) }
    }

    private func syncDelete(_ id: String) {
        guard !id.hasPrefix("demo-"), !id.hasPrefix("local-") else { return }
        Task { try? await APIClient.shared.deleteConversation(id: id) }
    }

    /// ISO-8601 → "2h ago"-style relative label (graceful fallback to raw string).
    /// Shared cached formatters — was a fresh ISO8601DateFormatter +
    /// RelativeDateTimeFormatter PER ROW PER RENDER.
    private static func relativeTime(from iso: String) -> String {
        GSFormatters.relativeTime(from: iso)
    }
}
