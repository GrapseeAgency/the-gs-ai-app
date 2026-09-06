import SwiftUI

/// Chats hub — `ConversationStore` is the source of truth (JSON-persisted,
/// offline-first); a pull-to-refresh best-effort syncs server rows on top.
/// Falls back to the sample seed on a fresh install. Rows carry the benchmark
/// action set (pin / archive / delete) via context menu, mirroring the
/// Android drawer.
struct ChatsListView: View {

    private enum Filter: String, CaseIterable, Identifiable {
        case all = "All"
        case pinned = "Pinned"
        case unread = "Unread"
        var id: String { rawValue }
    }

    private struct ConversationRow: Identifiable {
        let id: String
        let title: String
        let preview: String
        let time: String
        var pinned: Bool = false
        var unread: Bool = false
    }

    @ObservedObject private var store = ConversationStore.shared

    @State private var filter: Filter = .all
    @State private var isLoading = false
    @State private var isOffline = false
    @State private var reloadToken = 0

    private let demoRows: [ConversationRow] = [
        ConversationRow(id: "demo-1", title: "Q3 pricing strategy", preview: "You: send the revised deck?", time: "2h", pinned: true),
        ConversationRow(id: "demo-2", title: "Kyoto trip planning", preview: "GS: temples worth the early train…", time: "5h", unread: true),
        ConversationRow(id: "demo-3", title: "Kotlin coroutines debug", preview: "You: why does launch block here?", time: "1d"),
        ConversationRow(id: "demo-4", title: "Brand voice workshop", preview: "GS: three tone pillars emerged…", time: "3d")
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                header
                OfflineBanner(isVisible: isOffline)
                quickAccess
                filterChips
                conversationList
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
        .task(id: reloadToken) { await load() }
        .refreshable { await load() }
        .onAppear { reloadToken += 1 }
    }

    // MARK: Live data

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

    /// Best-effort server merge: remote rows upserted on top of the local
    /// store (server flags win for server-owned rows; local-only rows stay).
    private func load() async {
        isLoading = true
        defer { isLoading = false }
        do {
            let remote = try await APIClient.shared.conversations(limit: 30)
            for conversation in remote {
                let existing = store.conversation(withID: conversation.id)
                let row = StoredConversation(
                    id: conversation.id,
                    title: conversation.title,
                    modelId: conversation.modelId,
                    pinned: conversation.pinned ?? existing?.pinned ?? false,
                    archived: conversation.archived ?? existing?.archived ?? false,
                    createdAt: conversation.createdAt.isEmpty ? (existing?.createdAt ?? ConversationStore.now()) : conversation.createdAt,
                    updatedAt: conversation.updatedAt.isEmpty ? (existing?.updatedAt ?? ConversationStore.now()) : conversation.updatedAt)
                store.upsert(row)
            }
            isOffline = false
        } catch {
            // Backend unreachable: the store (and demo seed) keeps the surface alive.
            isOffline = true
        }
    }

    private func sync(_ id: String, pinned: Bool? = nil, archived: Bool? = nil) {
        guard !id.hasPrefix("demo-"), !id.hasPrefix("local-") else { return }
        Task {
            try? await APIClient.shared.updateConversation(id: id, pinned: pinned, archived: archived)
        }
    }

    // MARK: Header

    private var header: some View {
        HStack(alignment: .center) {
            Text("Chats")
                .font(Aero.displayTitle())
                .foregroundStyle(Aero.text)
            Spacer()
            NavigationLink(value: AeroRoute.chatSearch) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundStyle(Aero.text)
            }
            .buttonStyle(KineticPressStyle())
            .padding(.trailing, 6)
            NavigationLink(value: AeroRoute.chat(nil)) {
                Image(systemName: "plus.circle.fill")
                    .font(.system(size: 24))
                    .foregroundStyle(Aero.accent)
            }
            .buttonStyle(KineticPressStyle())
        }
    }

    // MARK: Quick access

    private var quickAccess: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            SectionHeader(title: "Quick access")
            HStack(spacing: Aero.Spacing.s) {
                quickCard("Folders", icon: "folder", route: .chatFolders)
                quickCard("Archived", icon: "archivebox", route: .chatArchive)
                quickCard("Shared", icon: "person.2", route: .chatShared)
            }
        }
    }

    private func quickCard(_ title: String, icon: String, route: AeroRoute) -> some View {
        AeroCard {
            NavigationLink(value: route) {
                VStack(alignment: .leading, spacing: 6) {
                    Image(systemName: icon)
                        .font(.system(size: 16, weight: .medium))
                        .foregroundStyle(Aero.accent)
                    Text(title)
                        .font(Aero.label())
                        .foregroundStyle(Aero.text)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(KineticPressStyle())
        }
    }

    // MARK: Filter chips

    private var filterChips: some View {
        HStack(spacing: Aero.Spacing.s) {
            ForEach(Filter.allCases) { candidate in
                AeroChip(text: candidate.rawValue, selected: filter == candidate) {
                    withAnimation(Aero.snappy) { filter = candidate }
                }
            }
            Spacer()
        }
    }

    // MARK: Conversation list

    /// Store rows win when present; samples only fill a fresh, offline first run.
    private var rows: [ConversationRow] {
        let active = store.activeConversations
        if active.isEmpty { return demoRows }
        return active.map { conversation in
            let last = store.messages(for: conversation.id).last
            let speaker = last?.role == "user" ? "You" : "GS"
            let body = last?.content.replacingOccurrences(of: "\n", with: " ") ?? "Synced with GS"
            let preview = body.count > 42 ? String(body.prefix(42)) + "…" : body
            return ConversationRow(
                id: conversation.id,
                title: conversation.title,
                preview: "\(speaker): \(preview)",
                time: Self.relativeTime(from: conversation.updatedAt),
                pinned: conversation.pinned)
        }
    }

    private var filtered: [ConversationRow] {
        rows.filter { row in
            switch filter {
            case .all: return true
            case .pinned: return row.pinned
            case .unread: return row.unread
            }
        }
    }

    private var conversationList: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            SectionHeader(title: "Recent", actionTitle: isLoading ? "Syncing…" : nil)
            if isLoading && rows.isEmpty {
                VStack(spacing: Aero.Spacing.s) {
                    SkeletonBlock(height: 64)
                    SkeletonBlock(height: 64)
                    SkeletonBlock(height: 64)
                }
            } else if filtered.isEmpty {
                EmptyStateView(
                    icon: "tray",
                    title: "Nothing here",
                    message: "No conversations match this filter."
                )
            } else {
                ForEach(filtered) { row in
                    NavigationLink(value: AeroRoute.chat(row.id)) {
                        AeroListRow(
                            title: row.title,
                            subtitle: "\(row.preview) · \(row.time)",
                            leading: {
                                Image(systemName: "bubble.left")
                                    .font(.system(size: 14))
                                    .foregroundStyle(Aero.text)
                                    .frame(width: 36, height: 36)
                                    .background(Circle().fill(Aero.containerHigh))
                            },
                            trailing: {
                                if row.pinned {
                                    Image(systemName: "star.fill")
                                        .font(.system(size: 13))
                                        .foregroundStyle(Aero.accent)
                                }
                            }
                        )
                    }
                    .buttonStyle(KineticPressStyle())
                    .contextMenu {
                        Button {
                            let target = !row.pinned
                            store.setPinned(id: row.id, target)
                            sync(row.id, pinned: target)
                        } label: {
                            Label(row.pinned ? "Unpin" : "Pin to top", systemImage: "pin")
                        }
                        Button {
                            store.setArchived(id: row.id, true)
                            sync(row.id, archived: true)
                        } label: {
                            Label("Archive", systemImage: "archivebox")
                        }
                        Button(role: .destructive) {
                            store.delete(id: row.id)
                            syncDelete(row.id)
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
                }
            }
        }
    }

    private func syncDelete(_ id: String) {
        guard !id.hasPrefix("demo-"), !id.hasPrefix("local-") else { return }
        Task { try? await APIClient.shared.deleteConversation(id: id) }
    }
}
