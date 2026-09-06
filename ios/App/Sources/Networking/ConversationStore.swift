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
 * Offline-first source of truth on iOS: a single JSON document in Application
 * Support (SwiftData is off-limits on the iOS 16.0 target). Every chat turn,
 * pin, archive, rename and delete lands here first; the backend syncs when
 * reachable. The drawer recents, Chats list and Archive shelf all observe
 * this store — exactly the Room+Flow contract the Android build uses.
 */
@MainActor
final class ConversationStore: ObservableObject {

    static let shared = ConversationStore()

    @Published private(set) var conversations: [StoredConversation] = []
    @Published private(set) var messages: [StoredMessage] = []

    private let fileURL: URL

    /// Fixed-width ISO-8601 keeps lexicographic createdAt ordering chronological.
    private static let timestamp: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    static func now() -> String { timestamp.string(from: Date()) }

    private init() {
        let directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        fileURL = directory.appendingPathComponent("gsai-store.json")
        load()
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

    // MARK: - Mutations (local-first, persisted on every change)

    func upsert(_ conversation: StoredConversation) {
        if let index = conversations.firstIndex(where: { $0.id == conversation.id }) {
            conversations[index] = conversation
        } else {
            conversations.append(conversation)
        }
        persist()
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
        persist()
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
        persist()
    }

    func touch(id: String) { mutate(id) { $0.updatedAt = Self.now() } }

    private func mutate(_ id: String, _ change: (inout StoredConversation) -> Void) {
        guard let index = conversations.firstIndex(where: { $0.id == id }) else { return }
        change(&conversations[index])
        persist()
    }

    // MARK: - Persistence

    private func load() {
        guard let data = try? Data(contentsOf: fileURL) else { return }
        guard let document = try? JSONDecoder().decode(StoreDocument.self, from: data) else { return }
        conversations = document.conversations
        messages = document.messages
    }

    private func persist() {
        let document = StoreDocument(conversations: conversations, messages: messages)
        guard let data = try? JSONEncoder().encode(document) else { return }
        try? data.write(to: fileURL, options: .atomic)
    }

    private struct StoreDocument: Codable {
        var conversations: [StoredConversation]
        var messages: [StoredMessage]
    }
}
