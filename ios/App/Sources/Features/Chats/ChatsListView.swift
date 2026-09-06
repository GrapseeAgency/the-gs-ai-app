import SwiftUI

/// Chats tab root — quick access cards, filter chips, conversation list.
/// Live data from `APIClient.shared.conversations()` (Task 7-a): Room-equivalent
/// freshness via pull-to-refresh + on-appear reload; falls back to the sample
/// seed when the backend is unreachable (OfflineBanner shown).
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

    @State private var filter: Filter = .all
    @State private var liveConversations: [ConversationRow] = []
    @State private var isLoading = false
    @State private var isOffline = false
    @State private var reloadToken = 0
    @State private var conversations: [ConversationRow] = [
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

    private func load() async {
        isLoading = true
        defer { isLoading = false }
        do {
            let remote = try await APIClient.shared.conversations(limit: 30)
            liveConversations = remote.map { conversation in
                ConversationRow(
                    id: conversation.id,
                    title: conversation.title,
                    preview: "Synced with GS",
                    time: Self.relativeTime(from: conversation.updatedAt),
                    pinned: conversation.pinned ?? false
                )
            }
            isOffline = false
        } catch {
            // Backend unreachable (or simulator against a stopped server):
            // keep the sample seed visible so the surface never feels dead.
            isOffline = true
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

    /// Live rows win when present; samples only fill an offline/empty first run.
    private var rows: [ConversationRow] {
        liveConversations.isEmpty ? conversations : liveConversations
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
                }
            }
        }
    }
}
