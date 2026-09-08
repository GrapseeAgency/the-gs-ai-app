import Foundation

/// One persisted conversation row (mirror of the backend contract, tolerant defaults).
struct StoredConversation: Codable, Identifiable, Equatable {
    var id: String
    var title: String
    var modelId: String?
    var pinned: Bool
    var archived: Bool
    var createdAt: String
    var updatedAt: String
}

/// One persisted chat turn.
struct StoredMessage: Codable, Identifiable, Equatable {
    var id: String
    var conversationId: String
    var role: String
    var content: String
    var createdAt: String
}

/**
 * Offline-first source of truth on iOS: a durable SQLite document in
 * Application Support (one-time import from the legacy JSON document; the
 * higher-level object store is off-limits on the iOS 16.0 target). Every
 * chat turn, pin, archive, rename and delete lands here first; the backend
 * syncs when reachable. The drawer recents, Chats list and Archive shelf all
 * observe this store — exactly the Room+Flow contract the Android build uses.
 */
@MainActor
final class ConversationStore: ObservableObject {

    static let shared = ConversationStore()

    @Published private(set) var conversations: [StoredConversation] = []
    @Published private(set) var messages: [StoredMessage] = []

    /// O(1) last-message lookups for inbox previews — the list used to call
    /// messages(for:).last PER ROW, which is O(rows × total messages) per render.
    /// Kept in step with every mutation of `messages` (init, append, delete,
    /// truncate); rebuilt wholesale only at load time.
    private var lastMessageByConversation: [String: StoredMessage] = [:]

    /// Durable layer: SQLite + FTS5 (imports the legacy JSON document once).
    /// Its internal serial queue makes every statement safe from any thread.
    private let sql = SQLiteChatStore()

    /// Fixed-width ISO-8601 keeps lexicographic createdAt ordering chronological.
    private static let timestamp: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    static func now() -> String { timestamp.string(from: Date()) }

    private init() {
        // Deep-perf pass 80-b: the full-table bootstrap — both loads plus the
        // one-time legacy JSON import — used to run synchronously on whatever
        // thread first touched `shared` (the main thread for every launch),
        // with a cost that grows with the whole corpus. It now loads on a
        // background task and lands with one merge; reads that must not wait
        // (windowed history on conversation open) go straight to SQLite.
        let sql = self.sql
        Task.detached(priority: .userInitiated) { [weak self] in
            var loadedConversations = sql.loadConversations()
            var loadedMessages = sql.loadMessages()
            if loadedConversations.isEmpty && loadedMessages.isEmpty {
                // Storage hiccup or fresh install with a legacy document —
                // keep the old in-memory JSON path alive so nothing ever
                // looks "lost". File IO + decode stay off-main too.
                if let legacy = Self.loadLegacyDocument() {
                    loadedConversations = legacy.conversations
                    loadedMessages = legacy.messages
                }
            }
            await self?.applyBootstrap(
                conversations: loadedConversations,
                messages: loadedMessages)
        }
    }

    /// Applies the off-main bootstrap. Merge, not replace: a first-millisecond
    /// mutation that raced the load (its SQLite write serialized AFTER the
    /// load read) stays in memory exactly once.
    private func applyBootstrap(conversations: [StoredConversation], messages: [StoredMessage]) {
        var mergedConversations = conversations
        for existing in self.conversations
        where !mergedConversations.contains(where: { $0.id == existing.id }) {
            mergedConversations.append(existing)
        }
        var mergedMessages = messages
        for existing in self.messages
        where !mergedMessages.contains(where: { $0.id == existing.id }) {
            mergedMessages.append(existing)
        }
        self.conversations = mergedConversations
        self.messages = mergedMessages
        rebuildLastMessageIndex()
    }

    /// Legacy document read + decode for the bootstrap fallback — nonisolated
    /// static so the file IO happens on the bootstrap task, never the main thread.
    private nonisolated static func loadLegacyDocument() -> StoreDocument? {
        let directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        guard let data = try? Data(contentsOf: directory.appendingPathComponent("gsai-store.json")),
              let document = try? JSONDecoder().decode(StoreDocument.self, from: data) else { return nil }
        return document
    }

    /// O(1)-per-row inbox previews are rebuilt in one pass over the loaded table.
    private func rebuildLastMessageIndex() {
        lastMessageByConversation = [:]
        for message in messages {
            if let existing = lastMessageByConversation[message.conversationId],
               existing.createdAt > message.createdAt { continue }
            lastMessageByConversation[message.conversationId] = message
        }
    }

    // MARK: - Queries

    /// Inbox ordering: archived hidden, pins float — same language as Android's Room query.
    var activeConversations: [StoredConversation] {
        conversations
            .filter { !$0.archived }
            .sorted { lhs, rhs in
                if lhs.pinned != rhs.pinned { return lhs.pinned }
                return lhs.updatedAt > rhs.updatedAt
            }
    }

    var archivedConversations: [StoredConversation] {
        conversations
            .filter { $0.archived }
            .sorted { $0.updatedAt > $1.updatedAt }
    }

    func conversation(withID id: String) -> StoredConversation? {
        conversations.first { $0.id == id }
    }

    func messages(for conversationID: String) -> [StoredMessage] {
        messages
            .filter { $0.conversationId == conversationID }
            .sorted { $0.createdAt < $1.createdAt }
    }

    /// O(1) inbox preview lookup — the newest message of a conversation from
    /// the maintained index, no per-row filter+sort over the whole table.
    func lastMessage(for conversationID: String) -> StoredMessage? {
        lastMessageByConversation[conversationID]
    }

    /// Newest-window read for conversation open (deep-perf pass #2b, Android's
    /// `historyRecent` twin) — SQLite does the indexed scan and only the page
    /// crosses into Swift, so opening a 2,000-turn thread no longer
    /// materialises the whole transcript. If SQLite has nothing for this
    /// thread but the in-memory table does (legacy-JSON hiccup path), the
    /// memory suffix answers instead — nothing ever looks lost.
    func recentMessages(for conversationID: String, limit: Int) -> [StoredMessage] {
        let window = sql.loadRecentMessages(conversationId: conversationID, limit: limit)
        if !window.isEmpty || messages.isEmpty { return window }
        return Array(messages(for: conversationID).suffix(limit))
    }

    /// Scroll-up page (Android's `historyBefore` twin) — up to `limit` turns
    /// strictly older than `before` (the oldest loaded stamp), oldest first.
    /// Pure local, never waits on the network; same legacy fallback contract.
    func olderMessages(for conversationID: String, before: String, limit: Int) -> [StoredMessage] {
        let page = sql.loadMessagesBefore(conversationId: conversationID, before: before, limit: limit)
        if !page.isEmpty || messages.isEmpty { return page }
        return Array(messages(for: conversationID).filter { $0.createdAt < before }.suffix(limit))
    }

    // MARK: - Search (FTS5, mirrors Android's Room FTS contract)

    struct MessageSearchHit {
        let conversation: StoredConversation
        let messageID: String
        let content: String
        let createdAt: String
    }

    func searchTitles(_ term: String) -> [StoredConversation] {
        sql.searchTitleIDs(term).compactMap { id in
            conversations.first { $0.id == id }
        }
    }

    func searchMessages(_ term: String) -> [MessageSearchHit] {
        sql.searchMessageHits(term).compactMap { hit in
            guard let conversation = conversations.first(where: { $0.id == hit.conversationID }) else { return nil }
            return MessageSearchHit(
                conversation: conversation,
                messageID: hit.messageID,
                content: hit.content,
                createdAt: hit.createdAt)
        }
    }

    /// Both search reads in one off-main page (Task 85-e I15). The FTS/LIKE
    /// pair runs on the SQLite serial queue via a detached task — never on the
    /// main actor — while the id→row join hops back to the main actor where
    /// `conversations` lives. Content/ordering/caps are exactly the
    /// synchronous pair's; callers add their own UI caps.
    struct SearchPage {
        let titles: [StoredConversation]
        let messages: [MessageSearchHit]
    }

    func searchAsync(_ term: String) async -> SearchPage {
        let sql = self.sql
        let raw = await Task.detached(priority: .userInitiated) { () -> ([String], [SQLiteChatStore.MessageHit]) in
            (sql.searchTitleIDs(term), sql.searchMessageHits(term))
        }.value
        return SearchPage(
            titles: raw.0.compactMap { id in
                conversations.first { $0.id == id }
            },
            messages: raw.1.compactMap { hit in
                guard let conversation = conversations.first(where: { $0.id == hit.conversationID }) else { return nil }
                return MessageSearchHit(
                    conversation: conversation,
                    messageID: hit.messageID,
                    content: hit.content,
                    createdAt: hit.createdAt)
            })
    }

    // MARK: - Mutations (local-first, persisted on every change)

    func upsert(_ conversation: StoredConversation) {
        if let index = conversations.firstIndex(where: { $0.id == conversation.id }) {
            conversations[index] = conversation
        } else {
            conversations.append(conversation)
        }
        sql.upsertConversation(conversation)
    }

    /// Offline-safe stand-in for `POST /conversations` — visible in recents immediately.
    @discardableResult
    func createLocalConversation(title: String) -> StoredConversation {
        let now = Self.now()
        let conversation = StoredConversation(
            id: "local-\(UUID().uuidString)",
            title: title,
            modelId: nil,
            pinned: false,
            archived: false,
            createdAt: now,
            updatedAt: now)
        upsert(conversation)
        return conversation
    }

    /// Appends a turn and auto-materialises the owning conversation row so the
    /// drawer/Chats list light up on the very first message (demo ids included).
    /// Task 86-e: the first-message snippet title honours Settings →
    /// "Auto-title chats" — off, unknown threads materialise under the
    /// default "New chat" title instead of the message prefix.
    func append(_ message: StoredMessage) {
        if conversation(withID: message.conversationId) == nil {
            let snippet: String
            if message.role == "user", SettingsStore.shared.autoTitle {
                snippet = String(message.content.prefix(40))
            } else {
                snippet = "New chat"
            }
            let now = Self.now()
            upsert(StoredConversation(
                id: message.conversationId,
                title: snippet,
                modelId: nil,
                pinned: false,
                archived: false,
                createdAt: now,
                updatedAt: now))
        } else {
            touch(message.conversationId)
        }
        messages.append(message)
        if let existing = lastMessageByConversation[message.conversationId],
           existing.createdAt > message.createdAt { /* older-than-last write keeps the preview */ }
        else { lastMessageByConversation[message.conversationId] = message }
        sql.upsertMessage(message)
    }

    func setPinned(id: String, _ pinned: Bool) { mutate(id) { $0.pinned = pinned } }
    func setArchived(id: String, _ archived: Bool) { mutate(id) { $0.archived = archived } }

    func rename(id: String, to title: String) {
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        mutate(id) { $0.title = trimmed }
    }

    func delete(id: String) {
        conversations.removeAll { $0.id == id }
        messages.removeAll { $0.conversationId == id }
        lastMessageByConversation[id] = nil
        sql.deleteConversation(id: id)
    }

    /// Privacy → Clear local data: every conversation, message and library
    /// save leaves the device for good — SQLite rows, in-memory state and the
    /// UserDefaults-backed library. Assistants and settings stay.
    func wipeAllContent() {
        sql.deleteAllContent()
        conversations.removeAll()
        messages.removeAll()
        lastMessageByConversation.removeAll()
        UserDefaults.standard.removeObject(forKey: Self.savedLibraryKey)
    }

    /// Settings export: the full message corpus from the durable store —
    /// not just the in-memory window — so the export is complete. Nonisolated
    /// (the SQLite queue makes the cross-thread read safe and ordered): the
    /// export task assembles the corpus OFF the main thread.
    nonisolated func exportMessages() -> [StoredMessage] {
        sql.loadMessages()
    }

    /// Refresh path: merge a whole server page in ONE published write. The
    /// per-row upsert made the inbox re-render once per row per sync (each
    /// pass rebuilding every row's preview + relative label).
    func upsert(_ rows: [StoredConversation]) {
        guard !rows.isEmpty else { return }
        var merged = conversations
        for row in rows {
            if let index = merged.firstIndex(where: { $0.id == row.id }) {
                merged[index] = row
            } else {
                merged.append(row)
            }
            sql.upsertConversation(row)
        }
        conversations = merged
    }

    /// Benchmark edit flow: the matching user turn and everything after it
    /// leave the store; the resend rebuilds the tail. Content-matched (most
    /// recent occurrence) because in-memory rows do not carry persisted ids.
    func truncateMessages(fromUserContent content: String, in conversationID: String) {
        guard let stamp = sql.latestUserStamp(content: content, conversationId: conversationID) else { return }
        sql.deleteMessages(fromInclusive: stamp, conversationId: conversationID)
        messages.removeAll { $0.conversationId == conversationID && $0.createdAt >= stamp }
        // The removed tail may include the preview row — recompute for this one
        // conversation (rare edit-resend path; a single filtered pass is fine).
        lastMessageByConversation[conversationID] =
            messages.last { $0.conversationId == conversationID }
    }

    func touch(id: String) { mutate(id) { $0.updatedAt = Self.now() } }

    private func mutate(_ id: String, _ change: (inout StoredConversation) -> Void) {
        guard let index = conversations.firstIndex(where: { $0.id == id }) else { return }
        change(&conversations[index])
        sql.upsertConversation(conversations[index])
    }

    // MARK: - Legacy document (in-memory fallback only)
}

/// Legacy on-disk document (pre-SQLite install data). File-scope so the
/// nonisolated bootstrap can decode it off the main thread.
private struct StoreDocument: Codable {
    var conversations: [StoredConversation]
    var messages: [StoredMessage]
}

// MARK: - Library (real saved-from-chat items, JSON in UserDefaults)

/// One saved Library entry — mirrors Android's Room `saved_items` row.
struct LibraryItem: Codable, Identifiable, Equatable {
    let id: String
    let kind: String
    let title: String
    let content: String
    let createdAt: String
}

extension ConversationStore {

    private static let savedLibraryKey = "gs_saved_library_items"

    /// Real "Save to Library" — chat turns land as "message"; the studios
    /// save images and drafts under their own kind so Library filters catch
    /// them. An explicit title (e.g. "Invoice Jul 2025") beats the prefix.
    func saveToLibrary(content: String, kind: String = "message", title: String? = nil) {
        let trimmed = content.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        var current = savedLibraryItems()
        current.insert(
            LibraryItem(
                id: UUID().uuidString,
                kind: kind,
                title: title.flatMap { trimmedTitle -> String? in
                    let t = trimmedTitle.trimmingCharacters(in: .whitespacesAndNewlines)
                    return t.isEmpty ? nil : String(t.prefix(48))
                } ?? String(trimmed.prefix(48)),
                content: trimmed,
                createdAt: Self.now()),
            at: 0)
        if let data = try? JSONEncoder().encode(current) {
            UserDefaults.standard.set(data, forKey: Self.savedLibraryKey)
        }
    }

    func savedLibraryItems() -> [LibraryItem] {
        guard let data = UserDefaults.standard.data(forKey: Self.savedLibraryKey) else { return [] }
        return (try? JSONDecoder().decode([LibraryItem].self, from: data)) ?? []
    }

    /// Library housekeeping: a saved item leaves the JSON store for good.
    func deleteLibraryItem(id: String) {
        var current = savedLibraryItems()
        current.removeAll { $0.id == id }
        if let data = try? JSONEncoder().encode(current) {
            UserDefaults.standard.set(data, forKey: Self.savedLibraryKey)
        }
    }
}
