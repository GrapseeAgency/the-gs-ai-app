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

    /// Durable layer: SQLite + FTS5 (imports the legacy JSON document once).
    private let sql = SQLiteChatStore()

    /// Fixed-width ISO-8601 keeps lexicographic createdAt ordering chronological.
    private static let timestamp: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    static func now() -> String { timestamp.string(from: Date()) }

    private init() {
        conversations = sql.loadConversations()
        messages = sql.loadMessages()
        if conversations.isEmpty && messages.isEmpty {
            // Storage hiccup or fresh install with a legacy document — keep the
            // old in-memory JSON path alive so nothing ever looks "lost".
            loadLegacyJSON()
        }
    }

    private func loadLegacyJSON() {
        let directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        guard let data = try? Data(contentsOf: directory.appendingPathComponent("gsai-store.json")),
              let document = try? JSONDecoder().decode(StoreDocument.self, from: data) else { return }
        conversations = document.conversations
        messages = document.messages
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
    func append(_ message: StoredMessage) {
        if conversation(withID: message.conversationId) == nil {
            let snippet = message.role == "user" ? String(message.content.prefix(40)) : "New chat"
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
        sql.deleteConversation(id: id)
    }

    /// Benchmark edit flow: the matching user turn and everything after it
    /// leave the store; the resend rebuilds the tail. Content-matched (most
    /// recent occurrence) because in-memory rows do not carry persisted ids.
    func truncateMessages(fromUserContent content: String, in conversationID: String) {
        guard let stamp = sql.latestUserStamp(content: content, conversationId: conversationID) else { return }
        sql.deleteMessages(fromInclusive: stamp, conversationId: conversationID)
        messages.removeAll { $0.conversationId == conversationID && $0.createdAt >= stamp }
    }

    func touch(id: String) { mutate(id) { $0.updatedAt = Self.now() } }

    private func mutate(_ id: String, _ change: (inout StoredConversation) -> Void) {
        guard let index = conversations.firstIndex(where: { $0.id == id }) else { return }
        change(&conversations[index])
        sql.upsertConversation(conversations[index])
    }

    // MARK: - Legacy document (in-memory fallback only)

    private struct StoreDocument: Codable {
        var conversations: [StoredConversation]
        var messages: [StoredMessage]
    }
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

    /// Real "Save to Library" — message content lands here from the chat surface.
    func saveToLibrary(content: String) {
        let trimmed = content.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        var current = savedLibraryItems()
        current.insert(
            LibraryItem(
                id: UUID().uuidString,
                kind: "message",
                title: String(trimmed.prefix(48)),
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
}
