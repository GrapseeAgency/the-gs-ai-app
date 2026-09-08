import SwiftUI

/// Chats hub — `ConversationStore` is the source of truth (JSON-persisted,
/// offline-first); a native pull-to-refresh best-effort syncs server rows on
/// top. Falls back to the sample seed on a fresh install. Rows are a real
/// `List` (Task 86-e) carrying the benchmark action set (pin / archive /
/// delete) as native swipe actions + context menu, mirroring the Android
/// drawer.
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
    /// Programmatic navigation — inside the List, rows are plain Buttons
    /// appending to the shared `Router` path (a NavigationLink in a List row
    /// would render the system chevron and change the row look).
    @EnvironmentObject private var router: Router

    @State private var filter: Filter = .all
    @State private var isLoading = false
    @State private var isOffline = false
    @State private var reloadToken = 0
    @State private var renameTarget: StoredConversation?
    @State private var renameDraft = ""

    private let demoRows: [ConversationRow] = [
        ConversationRow(id: "demo-1", title: "Q3 pricing strategy", preview: "You: send the revised deck?", time: "2h", pinned: true),
        ConversationRow(id: "demo-2", title: "Kyoto trip planning", preview: "GS: temples worth the early train…", time: "5h", unread: true),
        ConversationRow(id: "demo-3", title: "Kotlin coroutines debug", preview: "You: why does launch block here?", time: "1d"),
        ConversationRow(id: "demo-4", title: "Brand voice workshop", preview: "GS: three tone pillars emerged…", time: "3d")
    ]

    /// Card-row insets (Task 86-e): horizontal margins reproduce the
    /// LazyVStack's `.padding(.horizontal, Aero.Spacing.m)`; the 4pt top/bottom
    /// pairs give consecutive cards their original `Aero.Spacing.s` (8pt) gap.
    private var rowInsets: EdgeInsets {
        EdgeInsets(top: 4, leading: Aero.Spacing.m, bottom: 4, trailing: Aero.Spacing.m)
    }

    var body: some View {
        List {
            // Lead block — header + offline banner share one row so a hidden
            // banner leaves no phantom gap (matches the LazyVStack layout).
            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                header
                OfflineBanner(isVisible: isOffline)
            }
            .gsListRowChrome(EdgeInsets(
                top: Aero.Spacing.s, leading: Aero.Spacing.m, bottom: 0, trailing: Aero.Spacing.m))

            quickAccess
                .gsListRowChrome(EdgeInsets(
                    top: Aero.Spacing.l, leading: Aero.Spacing.m, bottom: 0, trailing: Aero.Spacing.m))

            filterChips
                .gsListRowChrome(EdgeInsets(
                    top: Aero.Spacing.l, leading: Aero.Spacing.m, bottom: 0, trailing: Aero.Spacing.m))

            SectionHeader(title: "Recent", actionTitle: isLoading ? "Syncing…" : nil)
                .gsListRowChrome(EdgeInsets(
                    top: Aero.Spacing.l, leading: Aero.Spacing.m, bottom: 4, trailing: Aero.Spacing.m))

            if isLoading && rows.isEmpty {
                VStack(spacing: Aero.Spacing.s) {
                    SkeletonBlock(height: 64)
                    SkeletonBlock(height: 64)
                    SkeletonBlock(height: 64)
                }
                .gsListRowChrome(rowInsets)
            } else if filtered.isEmpty {
                EmptyStateView(
                    icon: "tray",
                    title: emptyTitle,
                    message: emptyMessage
                )
                .gsListRowChrome(rowInsets)
            } else {
                ForEach(filtered) { row in
                    conversationRow(row)
                }
            }

            // Tail spacer reproduces the old LazyVStack bottom padding (the
            // last row already carries a 4pt bottom inset).
            Color.clear
                .frame(height: Aero.Spacing.xl - 4)
                .gsListRowChrome(EdgeInsets())
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .environment(\.defaultMinListRowHeight, 1)
        .background(Aero.background.ignoresSafeArea())
        .task(id: reloadToken) { await load() }
        .refreshable { await load() }
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
                    sync(target.id, title: trimmed)
                }
                renameTarget = nil
            }
            Button("Cancel", role: .cancel) { renameTarget = nil }
        } message: {
            Text("Give this conversation a name you'll recognise.")
        }
    }

    // MARK: Live data

    /// ISO-8601 → "2h ago"-style relative label (graceful fallback to raw string).
    /// Shared cached formatters — a fresh ISO8601DateFormatter +
    /// RelativeDateTimeFormatter PER ROW PER RENDER was O(rows) locale-heavy
    /// allocations on every body pass.
    private static func relativeTime(from iso: String) -> String {
        GSFormatters.relativeTime(from: iso)
    }

    /// Best-effort server merge: remote rows upserted on top of the local
    /// store (server flags win for server-owned rows; local-only rows stay).
    /// Deep-perf pass 80-b: the page lands in ONE published write — per-row
    /// upserts invalidated the whole list once per row (O(rows²) row rebuilds
    /// with preview + relative-label work in every pass).
    private func load() async {
        isLoading = true
        defer { isLoading = false }
        do {
            let remote = try await APIClient.shared.conversations(limit: 30)
            var rows: [StoredConversation] = []
            rows.reserveCapacity(remote.count)
            for conversation in remote {
                let existing = store.conversation(withID: conversation.id)
                rows.append(StoredConversation(
                    id: conversation.id,
                    title: conversation.title,
                    modelId: conversation.modelId,
                    pinned: conversation.pinned ?? existing?.pinned ?? false,
                    archived: conversation.archived ?? existing?.archived ?? false,
                    createdAt: conversation.createdAt.isEmpty ? (existing?.createdAt ?? ConversationStore.now()) : conversation.createdAt,
                    updatedAt: conversation.updatedAt.isEmpty ? (existing?.updatedAt ?? ConversationStore.now()) : conversation.updatedAt))
            }
            store.upsert(rows)
            isOffline = false
        } catch {
            // Backend unreachable: the store (and demo seed) keeps the surface alive.
            isOffline = true
        }
    }

    private func sync(_ id: String, pinned: Bool? = nil, archived: Bool? = nil, title: String? = nil) {
        guard !id.hasPrefix("demo-"), !id.hasPrefix("local-") else { return }
        Task {
            try? await APIClient.shared.updateConversation(id: id, pinned: pinned, archived: archived, title: title)
        }
    }

    // MARK: Header

    private var header: some View {
        HStack(alignment: .center) {
            Text("Chats")
                .font(Aero.displayTitle())
                .foregroundStyle(Aero.text)
                .accessibilityAddTraits(.isHeader)
            Spacer()
            // Buttons appending to the shared Router path — identical
            // navigation to NavigationLink(value:) without the chevron a
            // List row adds to links (Task 86-e).
            Button {
                router.path.append(.chatSearch)
            } label: {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundStyle(Aero.text)
            }
            .buttonStyle(KineticPressStyle())
            .accessibilityLabel("Search chats")
            .padding(.trailing, 6)
            Button {
                router.path.append(.chat(nil))
            } label: {
                Image(systemName: "plus.circle.fill")
                    .font(.system(size: 24))
                    .foregroundStyle(Aero.accent)
            }
            .buttonStyle(KineticPressStyle())
            .accessibilityLabel("New chat")
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
            // Router-path append, not NavigationLink — a List row renders the
            // system chevron on links (Task 86-e).
            Button {
                router.path.append(route)
            } label: {
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
                    // Inline state flip inside a persistent chip set — the
                    // selection tick, not a full impact (Task 85-e I10).
                    GSHaptics.select()
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
            // O(1) preview from the store's maintained last-message index —
            // messages(for:) here meant a full filter+sort PER ROW every render.
            let last = store.lastMessage(for: conversation.id)
            let speaker = last?.role == "user" ? "You" : "GS"
            let body = last?.content.replacingOccurrences(of: "\n", with: " ") ?? "Synced with GS"
            let preview = body.count > 42 ? String(body.prefix(42)) + "…" : body
            return ConversationRow(
                id: conversation.id,
                title: gsConversationTitle(conversation.title),
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

    /// Per-filter empty copy mirrors the Android hub exactly.
    private var emptyTitle: String {
        switch filter {
        case .all: return "No conversations yet"
        case .pinned: return "Nothing pinned yet"
        case .unread: return "All caught up"
        }
    }

    private var emptyMessage: String {
        switch filter {
        case .all: return "Start a chat with the + button and it will show up here."
        case .pinned: return "Pin a chat from its context menu and it will live here."
        case .unread: return "Nothing unread — enjoy the quiet."
        }
    }

    /// One conversation card as a native List row (Task 86-e): the default row
    /// button style gives iOS's own tap highlight (the KineticPressStyle scale
    /// is gone on this surface only), and the benchmark action set (pin /
    /// archive / delete) rides the leading/trailing swipes — the same store
    /// mutations + haptics as the context menu, through the shared action
    /// funcs below.
    private func conversationRow(_ row: ConversationRow) -> some View {
        Button {
            router.path.append(.chat(row.id))
        } label: {
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
        .swipeActions(edge: .leading, allowsFullSwipe: true) {
            Button {
                togglePin(row)
            } label: {
                Label(row.pinned ? "Unpin" : "Pin", systemImage: "pin")
            }
            .tint(Aero.accent)
        }
        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
            Button(role: .destructive) {
                deleteRow(row)
            } label: {
                Label("Delete", systemImage: "trash")
            }
            Button {
                archiveRow(row)
            } label: {
                Label("Archive", systemImage: "archivebox")
            }
            .tint(Aero.accent)
        }
        .contextMenu {
            Button {
                togglePin(row)
            } label: {
                Label(row.pinned ? "Unpin" : "Pin to top", systemImage: "pin")
            }
            Button {
                startRename(row)
            } label: {
                Label("Rename…", systemImage: "pencil")
            }
            Button {
                archiveRow(row)
            } label: {
                Label("Archive", systemImage: "archivebox")
            }
            Button(role: .destructive) {
                deleteRow(row)
            } label: {
                Label("Delete", systemImage: "trash")
            }
        }
        .gsListRowChrome(rowInsets)
    }

    // MARK: Row actions (context menu + swipe share these exact paths)

    private func togglePin(_ row: ConversationRow) {
        let target = !row.pinned
        store.setPinned(id: row.id, target)
        GSHaptics.success()
        sync(row.id, pinned: target)
    }

    private func startRename(_ row: ConversationRow) {
        renameTarget = store.conversation(withID: row.id)
        renameDraft = store.conversation(withID: row.id)?.title ?? row.title
    }

    private func archiveRow(_ row: ConversationRow) {
        store.setArchived(id: row.id, true)
        GSHaptics.success()
        sync(row.id, archived: true)
    }

    private func deleteRow(_ row: ConversationRow) {
        store.delete(id: row.id)
        GSHaptics.warning()
        syncDelete(row.id)
    }

    private func syncDelete(_ id: String) {
        guard !id.hasPrefix("demo-"), !id.hasPrefix("local-") else { return }
        Task { try? await APIClient.shared.deleteConversation(id: id) }
    }
}
