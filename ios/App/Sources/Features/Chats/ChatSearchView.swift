import SwiftUI

/// Chat History & Search — docs/reference/pending/chat-history-search.png.
///
/// The Android twin is `ChatSearchScreen`: same title, subtitle, chips,
/// totals card, ≤7 recent rows, 2×2 Quick Actions and footer CTA. Both read
/// the same local SQLite corpus; nothing here is seeded or invented.
///
/// The chips are the shipped classifier's buckets (`DrawerTopic.topic(for:)`,
/// Android's `drawerTopic()`), so "Chats" is the general-chat bucket and is a
/// different list from "All". Totals come from the ACTIVE conversation count —
/// an archived chat is counted on the archived facet, not in the headline.
struct ChatSearchView: View {

    private struct ChatHit: Identifiable {
        let id: String            // conversation id — one row per conversation
        let conversationId: String
        let title: String
        let snippet: String
        let moment: String
        let archived: Bool
        let hasFiles: Bool
        let glyph: String
        let tone: GsWellTone
    }

    private struct HistoryAction: Identifiable {
        let id: String
        let glyph: String
        let tone: GsWellTone
        let title: String
        let detail: String
    }

    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var router: Router
    @ObservedObject private var store = ConversationStore.shared

    @State private var query = ""
    @State private var chip = "All"
    @State private var archivedOnly = false
    @State private var filesOnly = false
    @State private var showFacets = false
    @State private var hits: [ChatHit] = []
    @State private var searched = false
    @State private var filesByConversation: Set<String> = []
    @State private var renameTarget: StoredConversation?
    @State private var renameDraft = ""
    @State private var exportURL: URL?
    @State private var showExport = false
    @FocusState private var searchFocused: Bool

    /// The mock's six chips. "More" is a sheet of real facets, not a topic.
    private static let chips = ["All", "Chats", "Research", "Ideas", "Planning", "More"]

    /// The four topic labels mapped onto the SHIPPED classifier — the same
    /// approximate buckets Android records in docs/reference/pending/README.md.
    private static let chipTopics: [String: Set<DrawerTopic>] = [
        "Chats": [.chat],
        "Research": [.science, .learning, .data, .code],
        "Ideas": [.ideas, .design, .writing],
        "Planning": [.plan, .work, .money, .travel],
    ]

    /// Title → topic, process-wide. A title's topic never changes, so the
    /// classifier runs once per distinct headline.
    private static var topicCache: [String: DrawerTopic] = [:]
    private static func topic(for title: String) -> DrawerTopic {
        if let cached = topicCache[title] { return cached }
        let value = DrawerTopic.topic(for: title)
        topicCache[title] = value
        return value
    }

    private var term: String {
        query.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    // MARK: Rows

    private var recentSource: [StoredConversation] {
        archivedOnly ? store.archivedConversations : store.activeConversations
    }

    private var visibleRecent: [StoredConversation] {
        recentSource.filter { conversation in
            if filesOnly && !filesByConversation.contains(conversation.id) { return false }
            guard let topics = Self.chipTopics[chip] else { return true }
            return topics.contains(Self.topic(for: conversation.title))
        }
    }

    private var visibleHits: [ChatHit] {
        hits.filter { hit in
            if filesOnly && !hit.hasFiles { return false }
            if archivedOnly && !hit.archived { return false }
            guard let topics = Self.chipTopics[chip] else { return true }
            return topics.contains(Self.topic(for: hit.title))
        }
    }

    private var totalConversations: Int { store.activeConversations.count }

    private var lastActive: String {
        guard let newest = store.activeConversations.map(\.updatedAt).max() else {
            return "—"
        }
        return Self.moment(newest, longDate: true)
    }

    var body: some View {
        GsSettingsPage(
            title: "Chat History & Search",
            subtitle: "Find, revisit, and manage your conversations",
            onBack: { dismiss() }
        ) {
            searchRow
            chipRow
            totalsCard

            if term.count < 2 {
                recentSection
                quickActions
                newConversationCard
            } else if searched && visibleHits.isEmpty {
                emptyResults
            } else {
                resultsSection
            }
        }
        .task(id: store.messages.count) {
            // One pass over the corpus per store change, not per row: the
            // paperclip glyph and the "Has files" facet both read this set.
            var files = Set<String>()
            for message in store.messages where !(message.attachments ?? []).isEmpty {
                files.insert(message.conversationId)
            }
            filesByConversation = files
        }
        .task(id: "\(term)|\(archivedOnly)|\(filesOnly)|\(filesByConversation.count)") {
            guard term.count >= 2 else {
                hits = []
                searched = false
                return
            }
            do {
                try await Task.sleep(nanoseconds: 220_000_000)
            } catch { return }
            guard !Task.isCancelled else { return }
            let page = await store.searchAsync(term)
            guard !Task.isCancelled else { return }
            hits = buildHits(page, term: term)
            searched = true
        }
        .sheet(isPresented: $showFacets) {
            facetSheet
                .presentationDetents([.medium])
        }
        .sheet(isPresented: $showExport) {
            if let url = exportURL {
                ActivitySheet(items: [url])
            }
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
                    GSHaptics.success()
                }
                renameTarget = nil
            }
            Button("Cancel", role: .cancel) { renameTarget = nil }
        } message: {
            Text("Give this conversation a name you'll recognise.")
        }
    }

    // MARK: Search field + chips

    private var searchRow: some View {
        HStack(spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Aero.textTertiary)
                TextField("Search your conversations…", text: $query)
                    .font(.custom(Aero.productName(.regular), size: 13, relativeTo: .body))
                    .foregroundStyle(Aero.text)
                    .focused($searchFocused)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .submitLabel(.search)
                    .onSubmit { searchFocused = false }
                if !query.isEmpty {
                    Button {
                        query = ""
                        searchFocused = false
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 14))
                            .foregroundStyle(Aero.textTertiary)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Clear search")
                }
            }
            .padding(.horizontal, 14)
            .frame(height: 46)
            .background(Capsule().fill(.white.opacity(0.55)))
            .overlay(Capsule().strokeBorder(.white.opacity(0.70), lineWidth: 1))

            Button { showFacets = true } label: {
                NexlyGlyph(
                    name: "adjustments",
                    size: 17,
                    color: archivedOnly || filesOnly ? Aero.accent : Aero.textSecondary
                )
                .frame(width: 46, height: 46)
                .background(
                    Circle().fill(
                        archivedOnly || filesOnly
                            ? Aero.accentSoft
                            : Color.white.opacity(0.55)))
                .overlay(
                    Circle().strokeBorder(
                        archivedOnly || filesOnly
                            ? Aero.accent.opacity(0.40)
                            : Color.white.opacity(0.70),
                        lineWidth: 1))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Search filters")
        }
        .padding(.top, 2)
    }

    private var chipRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(Self.chips, id: \.self) { label in
                    if label == "More" {
                        chipButton(
                            label,
                            selected: archivedOnly || filesOnly,
                            trailingChevron: true) { showFacets = true }
                    } else {
                        chipButton(label, selected: chip == label) {
                            chip = chip == label ? "All" : label
                        }
                    }
                }
            }
            .padding(.vertical, 1)
        }
        .padding(.top, 10)
    }

    private func chipButton(
        _ label: String,
        selected: Bool,
        trailingChevron: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Text(label)
                    .font(.custom(Aero.productName(.medium), size: 11.5, relativeTo: .caption))
                if trailingChevron {
                    NexlyGlyph(name: "chevron-down", size: 10, color: Aero.textSecondary)
                }
            }
            .foregroundStyle(selected ? ExplorePalette.apricotFg : Aero.textSecondary)
            .padding(.horizontal, 13)
            .padding(.vertical, 7)
            .background(
                Capsule().fill(selected ? Aero.accentSoft : Color.white.opacity(0.50)))
            .overlay(
                Capsule().strokeBorder(
                    selected ? Aero.accent.opacity(0.35) : Color.white.opacity(0.65),
                    lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }

    // MARK: Totals

    private var totalsCard: some View {
        HStack(spacing: 0) {
            historyStat(
                glyph: "message-circle", tone: .peach,
                label: "Total Conversations",
                value: "\(totalConversations)",
                caption: "Your chat history with Nexly")
            Rectangle()
                .fill(Aero.divider)
                .frame(width: 1)
                .padding(.vertical, 8)
            historyStat(
                glyph: "calendar", tone: .peach,
                label: "Last Active",
                value: lastActive,
                caption: "Most recent conversation")
        }
        .frame(maxWidth: .infinity)
        .gsGlass(.cardLight, corner: 20)
        .padding(.top, 14)
    }

    private func historyStat(
        glyph: String, tone: GsWellTone,
        label: String, value: String, caption: String
    ) -> some View {
        HStack(alignment: .top, spacing: 10) {
            GsWellTile(glyph: glyph, tone: tone, size: 30)
            VStack(alignment: .leading, spacing: 1) {
                Text(label)
                    .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2))
                    .foregroundStyle(Aero.textTertiary)
                Text(value)
                    .font(.custom(Aero.productName(.semibold), size: 15, relativeTo: .subheadline))
                    .foregroundStyle(Aero.text)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                Text(caption)
                    .font(.custom(Aero.productName(.regular), size: 9, relativeTo: .caption2))
                    .foregroundStyle(Aero.textTertiary)
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: Recent chats

    private var recentSection: some View {
        Group {
            HStack(alignment: .firstTextBaseline) {
                Text("Recent Chats")
                    .font(.custom(Aero.productName(.semibold), size: 17, relativeTo: .headline))
                    .foregroundStyle(Aero.text)
                    .accessibilityAddTraits(.isHeader)
                Spacer()
                Button { router.path.append(.chats) } label: {
                    HStack(spacing: 4) {
                        Text("View all")
                            .font(.custom(Aero.productName(.medium), size: 10.5, relativeTo: .caption2))
                        NexlyGlyph(name: "arrow-right", size: 11, color: Aero.accent)
                    }
                    .foregroundStyle(Aero.accent)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("View all chats")
            }
            .padding(.horizontal, 4)
            .padding(.top, 14)
            .padding(.bottom, 7)

            if visibleRecent.isEmpty {
                GsInfoCard(
                    text: archivedOnly
                        ? "No archived chats. Archive a conversation from its ⋮ menu and it waits here."
                        : (recentSource.isEmpty
                            ? "No conversations yet. Your chats appear here as you start them."
                            : "No chats match this filter. Try All, or clear the filters."),
                    glyph: "message-circle")
            } else {
                GsGroupCard {
                    ForEach(Array(visibleRecent.prefix(7).enumerated()), id: \.element.id) { index, conversation in
                        recentRow(conversation, showDivider: index < min(visibleRecent.count, 7) - 1)
                    }
                }
            }
        }
    }

    private func recentRow(_ conversation: StoredConversation, showDivider: Bool) -> some View {
        let title = gsConversationTitle(conversation.title)
        let hasFiles = filesByConversation.contains(conversation.id)
        // Glyph from real signals only — pinned and has-files are columns, a
        // topic would be a guess this row cannot prove.
        let glyph = conversation.pinned ? "star" : (hasFiles ? "paperclip" : "message-circle")
        let tone: GsWellTone = conversation.pinned ? .mint : (hasFiles ? .blue : .lilac)
        return HStack(spacing: 0) {
            Button { router.path.append(.chat(conversation.id)) } label: {
                HStack(spacing: 10) {
                    GsWellTile(glyph: glyph, tone: tone, size: 28)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(title)
                            .font(.custom(Aero.productName(.medium), size: 13.5, relativeTo: .body))
                            .foregroundStyle(Aero.text)
                            .lineLimit(1)
                        Text(Self.previewLine(store.lastMessage(for: conversation.id)))
                            .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2))
                            .foregroundStyle(Aero.textTertiary)
                            .lineLimit(1)
                    }
                    Spacer(minLength: 6)
                    Text(Self.moment(conversation.updatedAt))
                        .font(.custom(Aero.productName(.regular), size: 9.5, relativeTo: .caption2))
                        .foregroundStyle(Aero.textTertiary)
                        .lineLimit(1)
                }
                .padding(.leading, 12)
                .padding(.vertical, 9)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(title)

            actionsMenu(conversation)
        }
        .overlay(alignment: .bottom) {
            if showDivider {
                Rectangle()
                    .fill(Aero.divider.opacity(0.85))
                    .frame(height: 1.0 / 3.0)
                    .padding(.leading, 50)
                    .padding(.trailing, 12)
            }
        }
    }

    /// The real ⋮ menu: the same four mutations the Chats list exposes, against
    /// this store. No invented actions.
    private func actionsMenu(_ conversation: StoredConversation) -> some View {
        Menu {
            Button(conversation.pinned ? "Unpin" : "Pin to top") {
                store.setPinned(id: conversation.id, !conversation.pinned)
            }
            Button("Rename") {
                renameDraft = gsConversationTitle(conversation.title)
                renameTarget = conversation
            }
            Button(conversation.archived ? "Restore" : "Archive") {
                store.setArchived(id: conversation.id, !conversation.archived)
            }
            Button("Delete", role: .destructive) {
                store.delete(id: conversation.id)
            }
        } label: {
            NexlyGlyph(name: "dots-vertical", size: 16, color: Aero.textTertiary)
                .frame(width: 34, height: 34)
                .contentShape(Rectangle())
        }
        .accessibilityLabel("Chat actions")
    }

    // MARK: Quick actions

    private var quickActions: some View {
        Group {
            GsGroupHeader(title: "Quick Actions", caption: nil)
            HStack(spacing: 8) {
                actionCard(HistoryAction(
                    id: "search", glyph: "search", tone: .peach,
                    title: "Search Chats", detail: "Find specific topics")) {
                        searchFocused = true
                    }
                actionCard(HistoryAction(
                    id: "manage", glyph: "archive", tone: .rose,
                    title: "Manage History", detail: "Delete or archive chats")) {
                        router.path.append(.chatArchive)
                    }
            }
            HStack(spacing: 8) {
                actionCard(HistoryAction(
                    id: "saved", glyph: "bookmark", tone: .lilac,
                    title: "Saved items", detail: "Your saved answers")) {
                        router.path.append(.memory)
                    }
                actionCard(HistoryAction(
                    id: "export", glyph: "download", tone: .blue,
                    title: "Export Chat History", detail: "Download your data")) {
                        buildExportFile()
                    }
            }
            .padding(.top, 8)
        }
    }

    private func actionCard(_ action: HistoryAction, perform: @escaping () -> Void) -> some View {
        Button(action: perform) {
            HStack(spacing: 9) {
                GsWellTile(glyph: action.glyph, tone: action.tone, size: 28)
                VStack(alignment: .leading, spacing: 1) {
                    Text(action.title)
                        .font(.custom(Aero.productName(.medium), size: 12.5, relativeTo: .caption))
                        .foregroundStyle(Aero.text)
                        .lineLimit(1)
                    Text(action.detail)
                        .font(.custom(Aero.productName(.regular), size: 10, relativeTo: .caption2))
                        .foregroundStyle(Aero.textTertiary)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .gsGlass(.cardLight, corner: 20)
        .accessibilityLabel("\(action.title), \(action.detail)")
    }

    private var newConversationCard: some View {
        Button {
            router.resetWorkspace()
        } label: {
            HStack(spacing: 10) {
                GsWellTile(glyph: "sparkles", tone: .peach, size: 28)
                VStack(alignment: .leading, spacing: 1) {
                    Text("Start a new conversation")
                        .font(.custom(Aero.productName(.medium), size: 13.5, relativeTo: .body))
                        .foregroundStyle(Aero.text)
                    Text("Get back to what matters")
                        .font(.custom(Aero.productName(.regular), size: 11, relativeTo: .caption))
                        .foregroundStyle(Aero.textTertiary)
                }
                Spacer(minLength: 8)
                NexlyGlyph(name: "arrow-right", size: 15, color: Aero.accent)
                    .frame(width: 30, height: 30)
                    .background(Circle().fill(Aero.accent.opacity(0.16)))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 13)
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .gsGlass(.cardLight, corner: 22)
        .padding(.top, 14)
        .accessibilityLabel("Start a new conversation, Get back to what matters")
    }

    // MARK: Results

    private var resultsSection: some View {
        Group {
            Text("\(visibleHits.count) result\(visibleHits.count == 1 ? "" : "s")")
                .font(.custom(Aero.productName(.medium), size: 10.5, relativeTo: .caption2))
                .foregroundStyle(Aero.textTertiary)
                .padding(.leading, 4)
                .padding(.top, 14)
                .padding(.bottom, 8)

            GsGroupCard {
                ForEach(Array(visibleHits.enumerated()), id: \.element.id) { index, hit in
                    hitRow(hit, showDivider: index < visibleHits.count - 1)
                }
            }

            Text("Search runs across every conversation on this device. Nothing is sent to a server.")
                .font(.custom(Aero.productName(.regular), size: 10, relativeTo: .caption2))
                .foregroundStyle(Aero.textTertiary)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 6)
                .padding(.vertical, 16)
        }
    }

    private func hitRow(_ hit: ChatHit, showDivider: Bool) -> some View {
        Button { router.path.append(.chat(hit.conversationId)) } label: {
            HStack(spacing: 10) {
                GsWellTile(glyph: hit.glyph, tone: hit.tone, size: 28)
                VStack(alignment: .leading, spacing: 1) {
                    Text(gsConversationTitle(hit.title))
                        .font(.custom(Aero.productName(.medium), size: 13.5, relativeTo: .body))
                        .foregroundStyle(Aero.text)
                        .lineLimit(1)
                    Text("\(hit.snippet) · \(hit.moment)")
                        .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2))
                        .foregroundStyle(Aero.textTertiary)
                        .lineLimit(2)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .frame(minHeight: 52, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(gsConversationTitle(hit.title))
        .overlay(alignment: .bottom) {
            if showDivider {
                Rectangle()
                    .fill(Aero.divider.opacity(0.85))
                    .frame(height: 1.0 / 3.0)
                    .padding(.leading, 50)
                    .padding(.trailing, 12)
            }
        }
    }

    private var emptyResults: some View {
        VStack(spacing: 10) {
            GsWellTile(glyph: "search", tone: .lilac, size: 54)
            Text("No matches")
                .font(.custom(Aero.productName(.semibold), size: 16, relativeTo: .headline))
                .foregroundStyle(Aero.text)
            Text(noMatchesMessage)
                .font(.custom(Aero.productName(.regular), size: 11.5, relativeTo: .caption))
                .foregroundStyle(Aero.textTertiary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 20)
        .padding(.top, 54)
    }

    private var noMatchesMessage: String {
        var text = "Nothing matched \"\(term)\""
        if chip != "All" { text += " in \"\(chip)\"" }
        if archivedOnly { text += " in archived chats" }
        if filesOnly { text += " with files" }
        return text + ". Try a shorter word or clear the filters."
    }

    // MARK: Facets

    private var facetSheet: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Filter chats")
                .font(.custom(Aero.productName(.semibold), size: 16, relativeTo: .headline))
                .foregroundStyle(Aero.text)
                .padding(.bottom, 8)

            GsSheetToggle(
                title: "Archived conversations",
                detail: "Only chats you have archived",
                isOn: Binding(
                    get: { archivedOnly },
                    set: { archivedOnly = $0; GSHaptics.select() })
            ) {
                GsWellTile(glyph: "archive", tone: .rose, size: 28)
            }
            Rectangle().fill(Aero.divider).frame(height: 1.0 / 3.0).padding(.leading, 50)
            GsSheetToggle(
                title: "Has files",
                detail: "Only chats with attachments",
                isOn: Binding(
                    get: { filesOnly },
                    set: { filesOnly = $0; GSHaptics.select() })
            ) {
                GsWellTile(glyph: "paperclip", tone: .blue, size: 28)
            }

            if archivedOnly || filesOnly {
                Button {
                    archivedOnly = false
                    filesOnly = false
                } label: {
                    Text("Clear filters")
                        .font(.custom(Aero.productName(.medium), size: 12, relativeTo: .caption))
                        .foregroundStyle(Aero.accent)
                        .padding(.vertical, 10)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Clear filters")
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 20)
        .padding(.top, 18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(GsSettingsBackdrop().ignoresSafeArea())
    }

    // MARK: Helpers

    private func buildHits(_ page: ConversationStore.SearchPage, term: String) -> [ChatHit] {
        var rows: [ChatHit] = []
        var seen = Set<String>()
        for conversation in page.titles {
            guard !seen.contains(conversation.id) else { continue }
            seen.insert(conversation.id)
            let hasFiles = filesByConversation.contains(conversation.id)
            let glyph = conversation.pinned ? "star" : (hasFiles ? "paperclip" : "message-circle")
            let tone: GsWellTone = conversation.pinned ? .mint : (hasFiles ? .blue : .lilac)
            rows.append(ChatHit(
                id: conversation.id,
                conversationId: conversation.id,
                title: conversation.title,
                snippet: Self.previewLine(store.lastMessage(for: conversation.id)),
                moment: Self.moment(conversation.updatedAt),
                archived: conversation.archived,
                hasFiles: hasFiles,
                glyph: glyph,
                tone: tone))
        }
        for hit in page.messages {
            guard !seen.contains(hit.conversation.id) else { continue }
            seen.insert(hit.conversation.id)
            let hasFiles = filesByConversation.contains(hit.conversation.id)
            rows.append(ChatHit(
                id: hit.conversation.id,
                conversationId: hit.conversation.id,
                title: hit.conversation.title,
                snippet: Self.snippet(hit.content, term: term),
                moment: Self.moment(hit.createdAt),
                archived: hit.conversation.archived,
                hasFiles: hasFiles,
                glyph: "message-circle",
                tone: .lilac))
        }
        return Array(rows.prefix(24))
    }

    /// The last message as one flattened line — markdown markers removed, the
    /// same 96-character contract Android's `previewLine` uses.
    private static func previewLine(_ message: StoredMessage?) -> String {
        guard let raw = message?.content else { return "No messages yet" }
        let flattened = raw
            .replacingOccurrences(of: "\n", with: " ")
            .replacingOccurrences(of: "[*_`|>]", with: " ", options: .regularExpression)
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespaces)
        guard !flattened.isEmpty else { return "No messages yet" }
        return String(flattened.prefix(96))
    }

    private static func snippet(_ content: String, term: String) -> String {
        let clean = content.replacingOccurrences(of: "\n", with: " ").trimmingCharacters(in: .whitespaces)
        guard let range = clean.range(of: term, options: .caseInsensitive) else {
            return String(clean.prefix(80))
        }
        let index = clean.distance(from: clean.startIndex, to: range.lowerBound)
        let start = max(0, index - 24)
        let end = min(clean.count, index + term.count + 48)
        let lower = clean.index(clean.startIndex, offsetBy: start)
        let upper = clean.index(clean.startIndex, offsetBy: end)
        let prefix = start > 0 ? "…" : ""
        let suffix = end < clean.count ? "…" : ""
        return prefix + clean[lower..<upper].trimmingCharacters(in: .whitespaces) + suffix
    }

    /// "Today, 9:12 AM" for the totals card; "9:12 AM", "Yesterday", "Sep 16"
    /// for a row. Same buckets Android's `historyMoment` draws.
    private static func moment(_ iso: String, longDate: Bool = false) -> String {
        guard let date = GSFormatters.date(from: iso) else { return "earlier" }
        let calendar = Calendar.current
        let time = DateFormatter()
        time.dateFormat = "h:mm a"
        if calendar.isDateInToday(date) {
            return longDate ? "Today, \(time.string(from: date))" : time.string(from: date)
        }
        if calendar.isDateInYesterday(date) {
            return longDate ? "Yesterday, \(time.string(from: date))" : "Yesterday"
        }
        let day = DateFormatter()
        day.dateFormat = "MMM d"
        return day.string(from: date)
    }

    /// The JSON contract the Settings privacy page writes: the full SQLite
    /// corpus plus library saves, assembled off the main thread.
    private func buildExportFile() {
        let store = self.store
        let conversations = store.conversations
        let library = store.savedLibraryItems()
        let exportedAt = ConversationStore.now()
        Task {
            let url: URL? = await Task.detached(priority: .userInitiated) { () -> URL? in
                let messages = store.exportMessages()
                let payload = ExportPayload(
                    exportedAt: exportedAt,
                    conversations: conversations,
                    messages: messages,
                    library: library)
                guard let data = try? JSONEncoder().encode(payload) else { return nil }
                let url = FileManager.default.temporaryDirectory
                    .appendingPathComponent("gs-chat-history-export.json")
                do { try data.write(to: url, options: .atomic) } catch { return nil }
                return url
            }.value
            guard let url else { return }
            exportURL = url
            GSHaptics.success()
            showExport = true
        }
    }
}
