import SwiftUI

/// Archive shelf — live from `ConversationStore` (everything the user archived,
/// not a demo subset) with restore + delete affordances; the server PATCH is a
/// best-effort echo once the backend is reachable.
struct ArchivedChatsView: View {

    @ObservedObject private var store = ConversationStore.shared
    @State private var reloadToken = 0

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                Text("Archived")
                    .font(Aero.displayTitle())
                    .foregroundStyle(Aero.text)
                Text("Archived chats stay searchable but leave your main list.")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)

                if store.archivedConversations.isEmpty {
                    EmptyStateView(
                        icon: "archivebox",
                        title: "Nothing archived",
                        message: "Conversations you archive will rest here until you bring them back."
                    )
                } else {
                    VStack(spacing: Aero.Spacing.s) {
                        ForEach(store.archivedConversations) { conversation in
                            AeroListRow(
                                title: conversation.title,
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
                                        Button {
                                            store.delete(id: conversation.id)
                                            syncDelete(conversation.id)
                                        } label: {
                                            Image(systemName: "trash")
                                                .font(.system(size: 16))
                                                .foregroundStyle(Aero.textMuted)
                                        }
                                        .buttonStyle(KineticPressStyle())
                                    }
                                }
                            )
                            .contextMenu {
                                Button {
                                    restore(conversation)
                                } label: {
                                    Label("Unarchive", systemImage: "arrow.up.circle")
                                }
                                Button(role: .destructive) {
                                    store.delete(id: conversation.id)
                                    syncDelete(conversation.id)
                                } label: {
                                    Label("Delete", systemImage: "trash")
                                }
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
        .onAppear { reloadToken += 1 }
    }

    private func restore(_ conversation: StoredConversation) {
        withAnimation(Aero.snappy) {
            store.setArchived(id: conversation.id, false)
        }
        syncSet(conversation.id, archived: false)
    }

    private func syncSet(_ id: String, archived: Bool) {
        guard !id.hasPrefix("demo-"), !id.hasPrefix("local-") else { return }
        Task { try? await APIClient.shared.updateConversation(id: id, archived: archived) }
    }

    private func syncDelete(_ id: String) {
        guard !id.hasPrefix("demo-"), !id.hasPrefix("local-") else { return }
        Task { try? await APIClient.shared.deleteConversation(id: id) }
    }

    /// ISO-8601 → "2h ago"-style relative label (graceful fallback to raw string).
    private static func relativeTime(from iso: String) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        var date = formatter.date(from: iso)
        if date == nil {
            formatter.formatOptions = [.withInternetDateTime]
            date = formatter.date(from: iso)
        }
        guard let date else { return "" }
        let relative = RelativeDateTimeFormatter()
        relative.unitsStyle = .abbreviated
        return relative.localizedString(for: date, relativeTo: Date())
    }
}
