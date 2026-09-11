import Foundation
import SQLite3

/**
 * Durable SQLite chat store with an FTS5 full-text index — the iOS twin of
 * Android's Room + FTS tables. One-time import from the legacy JSON document
 * keeps every existing chat; every write path is silent on failure so a
 * storage hiccup can never surface as an error in the UI. The system
 * libsqlite3 build ships FTS5, so this stays dependency-free.
 *
 * Deep-perf pass 80-b: every operation is serialized through one private
 * dispatch queue, so the bootstrap loads (and the Settings export) can run
 * OFF the main thread while main-thread writes keep their strict ordering —
 * a write queued after the load lands after it, never interleaved. The
 * connection is opened with SQLITE_OPEN_FULLMUTEX and statements are
 * prepare/step/finalize per call, so queue execution is safe.
 */
final class SQLiteChatStore: @unchecked Sendable {

    struct MessageHit {
        let conversationID: String
        let messageID: String
        let content: String
        let createdAt: String
    }

    private var db: OpaquePointer?

    /// Serializes every statement against the single connection, and keeps
    /// bootstrap/load/write order deterministic across threads.
    private let queue = DispatchQueue(label: "gsai-app.sqlite.store")

    private static let transient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

    private let schemaSQL = """
    CREATE TABLE IF NOT EXISTS conversations(
        id TEXT PRIMARY KEY NOT NULL,
        title TEXT NOT NULL,
        modelId TEXT,
        pinned INTEGER NOT NULL DEFAULT 0,
        archived INTEGER NOT NULL DEFAULT 0,
        createdAt TEXT NOT NULL,
        updatedAt TEXT NOT NULL
    );
    CREATE TABLE IF NOT EXISTS messages(
        id TEXT PRIMARY KEY NOT NULL,
        conversationId TEXT NOT NULL,
        role TEXT NOT NULL,
        content TEXT NOT NULL,
        createdAt TEXT NOT NULL
    );
    CREATE INDEX IF NOT EXISTS messages_by_conversation ON messages(conversationId, createdAt);
    CREATE TABLE IF NOT EXISTS store_meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
    CREATE VIRTUAL TABLE IF NOT EXISTS conversations_fts USING fts5(title, content='conversations', content_rowid='rowid');
    CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts5(content, content='messages', content_rowid='rowid');
    CREATE TRIGGER IF NOT EXISTS conversations_ai AFTER INSERT ON conversations BEGIN
        INSERT INTO conversations_fts(rowid, title) VALUES (new.rowid, new.title);
    END;
    CREATE TRIGGER IF NOT EXISTS conversations_ad AFTER DELETE ON conversations BEGIN
        INSERT INTO conversations_fts(conversations_fts, rowid, title) VALUES('delete', old.rowid, old.title);
    END;
    CREATE TRIGGER IF NOT EXISTS conversations_au AFTER UPDATE OF title ON conversations BEGIN
        INSERT INTO conversations_fts(conversations_fts, rowid, title) VALUES('delete', old.rowid, old.title);
        INSERT INTO conversations_fts(rowid, title) VALUES (new.rowid, new.title);
    END;
    CREATE TRIGGER IF NOT EXISTS messages_ai AFTER INSERT ON messages BEGIN
        INSERT INTO messages_fts(rowid, content) VALUES (new.rowid, new.content);
    END;
    CREATE TRIGGER IF NOT EXISTS messages_ad AFTER DELETE ON messages BEGIN
        INSERT INTO messages_fts(messages_fts, rowid, content) VALUES('delete', old.rowid, old.content);
    END;
    CREATE TRIGGER IF NOT EXISTS messages_au AFTER UPDATE OF content ON messages BEGIN
        INSERT INTO messages_fts(messages_fts, rowid, content) VALUES('delete', old.rowid, old.content);
        INSERT INTO messages_fts(rowid, content) VALUES (new.rowid, new.content);
    END;
    """

    init() {
        let directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let path = directory.appendingPathComponent("gsai-store.sqlite").path
        var handle: OpaquePointer?
        let flags = SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX
        guard sqlite3_open_v2(path, &handle, flags, nil) == SQLITE_OK, let opened = handle else {
            sqlite3_close(handle)
            return
        }
        db = opened
        sqlite3_exec(opened, "PRAGMA journal_mode=WAL;", nil, nil, nil)
        queue.sync {
            exec(schemaSQL)
            importLegacyJSONIfNeeded()
        }
    }

    deinit {
        if let db = db { sqlite3_close(db) }
    }

    // MARK: - Row writes (write-through from ConversationStore)

    func upsertConversation(_ conversation: StoredConversation) {
        queue.sync {
            guard let stmt = prepare(
                "INSERT OR REPLACE INTO conversations(id, title, modelId, pinned, archived, createdAt, updatedAt) " +
                "VALUES(?, ?, ?, ?, ?, ?, ?)") else { return }
            defer { sqlite3_finalize(stmt) }
            bind(stmt, 1, conversation.id)
            bind(stmt, 2, conversation.title)
            bind(stmt, 3, conversation.modelId)
            sqlite3_bind_int(stmt, 4, conversation.pinned ? 1 : 0)
            sqlite3_bind_int(stmt, 5, conversation.archived ? 1 : 0)
            bind(stmt, 6, conversation.createdAt)
            bind(stmt, 7, conversation.updatedAt)
            sqlite3_step(stmt)
        }
    }

    func upsertMessage(_ message: StoredMessage) {
        queue.sync {
            guard let stmt = prepare(
                "INSERT OR REPLACE INTO messages(id, conversationId, role, content, createdAt) " +
                "VALUES(?, ?, ?, ?, ?)") else { return }
            defer { sqlite3_finalize(stmt) }
            bind(stmt, 1, message.id)
            bind(stmt, 2, message.conversationId)
            bind(stmt, 3, message.role)
            bind(stmt, 4, message.content)
            bind(stmt, 5, message.createdAt)
            sqlite3_step(stmt)
        }
    }

    func deleteConversation(id: String) {
        queue.sync {
            deleteRows("DELETE FROM messages WHERE conversationId = ?", id)
            deleteRows("DELETE FROM conversations WHERE id = ?", id)
        }
    }

    /// Privacy pass: every conversation and message row leaves the store.
    /// Settings and assistants are not stored here and stay untouched.
    func deleteAllContent() {
        queue.sync {
            exec("DELETE FROM messages")
            exec("DELETE FROM conversations")
        }
    }

    // MARK: - Row reads

    func loadConversations() -> [StoredConversation] {
        queue.sync {
            guard let stmt = prepare(
                "SELECT id, title, modelId, pinned, archived, createdAt, updatedAt FROM conversations") else { return [] }
            defer { sqlite3_finalize(stmt) }
            var rows: [StoredConversation] = []
            while sqlite3_step(stmt) == SQLITE_ROW {
                rows.append(StoredConversation(
                    id: text(stmt, 0),
                    title: text(stmt, 1),
                    modelId: sqlite3_column_type(stmt, 2) == SQLITE_NULL ? nil : text(stmt, 2),
                    pinned: sqlite3_column_int(stmt, 3) != 0,
                    archived: sqlite3_column_int(stmt, 4) != 0,
                    createdAt: text(stmt, 5),
                    updatedAt: text(stmt, 6)))
            }
            return rows
        }
    }

    func loadMessages() -> [StoredMessage] {
        queue.sync {
            guard let stmt = prepare(
                "SELECT id, conversationId, role, content, createdAt FROM messages") else { return [] }
            defer { sqlite3_finalize(stmt) }
            var rows: [StoredMessage] = []
            while sqlite3_step(stmt) == SQLITE_ROW {
                rows.append(StoredMessage(
                    id: text(stmt, 0),
                    conversationId: text(stmt, 1),
                    role: text(stmt, 2),
                    content: text(stmt, 3),
                    createdAt: text(stmt, 4)))
            }
            return rows
        }
    }

    // MARK: - Windowed reads (deep-perf pass #2b — Android's paged-history twin)

    /**
     * Newest `limit` turns of one conversation, oldest first — the index-backed
     * window read behind conversation open. The (conversationId, createdAt)
     * index serves the scan and only the page crosses into Swift, so a
     * 2,000-turn thread opens as cheaply as a 20-turn one. Fixed-width ISO
     * stamps keep the lexicographic DESC order chronological.
     */
    func loadRecentMessages(conversationId: String, limit: Int) -> [StoredMessage] {
        queue.sync {
            guard let stmt = prepare(
                "SELECT id, conversationId, role, content, createdAt FROM messages " +
                "WHERE conversationId = ? ORDER BY createdAt DESC LIMIT ?") else { return [] }
            defer { sqlite3_finalize(stmt) }
            bind(stmt, 1, conversationId)
            sqlite3_bind_int(stmt, 2, Int32(clamping: limit))
            var rows: [StoredMessage] = []
            while sqlite3_step(stmt) == SQLITE_ROW {
                rows.append(StoredMessage(
                    id: text(stmt, 0),
                    conversationId: text(stmt, 1),
                    role: text(stmt, 2),
                    content: text(stmt, 3),
                    createdAt: text(stmt, 4)))
            }
            return rows.reversed()
        }
    }

    /**
     * Up to `limit` turns strictly older than `before` (the oldest loaded
     * stamp), oldest first — the scroll-up page load. Pure local SQLite: the
     * reader never waits on the network to page back through history.
     */
    func loadMessagesBefore(conversationId: String, before: String, limit: Int) -> [StoredMessage] {
        queue.sync {
            guard let stmt = prepare(
                "SELECT id, conversationId, role, content, createdAt FROM messages " +
                "WHERE conversationId = ? AND createdAt < ? ORDER BY createdAt DESC LIMIT ?") else { return [] }
            defer { sqlite3_finalize(stmt) }
            bind(stmt, 1, conversationId)
            bind(stmt, 2, before)
            sqlite3_bind_int(stmt, 3, Int32(clamping: limit))
            var rows: [StoredMessage] = []
            while sqlite3_step(stmt) == SQLITE_ROW {
                rows.append(StoredMessage(
                    id: text(stmt, 0),
                    conversationId: text(stmt, 1),
                    role: text(stmt, 2),
                    content: text(stmt, 3),
                    createdAt: text(stmt, 4)))
            }
            return rows.reversed()
        }
    }

    // MARK: - Full-text search (FTS5, mirrors Android's ftsMatchQuery contract)

    /// Symbol-heavy or non-ASCII queries (CJK) fall back to LIKE; plain word
    /// queries go through the FTS5 index with each word quoted.
    ///
    /// Character-level classification (Android twin: ChatDatabase.ftsMatchQuery,
    /// whose `it` is also a Char). The previous scalar-based version never
    /// compiled: Unicode.Scalar has no isLetter/isNumber/isWhitespace — those
    /// live on Character. isASCII covers the >0x7F scalar test exactly
    /// (a Character is ASCII only when every scalar it carries is).
    static func matchQuery(_ raw: String) -> String? {
        let term = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !term.isEmpty else { return nil }
        if term.contains(where: { !$0.isLetter && !$0.isNumber && !$0.isWhitespace }) { return nil }
        if term.contains(where: { !$0.isASCII }) { return nil }
        let words = term
            .split(whereSeparator: { $0.isWhitespace })
            .compactMap { (word: Substring) -> String? in
                let cleaned = word.filter { $0.isLetter || $0.isNumber }
                return cleaned.isEmpty ? nil : String(cleaned)
            }
        guard !words.isEmpty else { return nil }
        return words.map { "\"\($0)\"" }.joined(separator: " ")
    }

    /// Conversation ids whose titles match — best hits first, capped like Android (20).
    func searchTitleIDs(_ term: String) -> [String] {
        queue.sync {
            if let match = Self.matchQuery(term) {
                return queryColumn(
                    "SELECT c.id FROM conversations c JOIN conversations_fts f ON c.rowid = f.rowid " +
                    "WHERE conversations_fts MATCH ? ORDER BY rank LIMIT 20", match)
            }
            return queryColumn(
                "SELECT id FROM conversations WHERE title LIKE ? ESCAPE '\\' ORDER BY updatedAt DESC LIMIT 20",
                likePattern(term))
        }
    }

    /// Message bodies that match — best hits first, capped like Android (20).
    func searchMessageHits(_ term: String) -> [MessageHit] {
        queue.sync {
            if let match = Self.matchQuery(term) {
                return queryMessages(
                    "SELECT m.conversationId, m.id, m.content, m.createdAt FROM messages m " +
                    "JOIN messages_fts f ON m.rowid = f.rowid WHERE messages_fts MATCH ? ORDER BY rank LIMIT 20", match)
            }
            return queryMessages(
                "SELECT conversationId, id, content, createdAt FROM messages " +
                "WHERE content LIKE ? ESCAPE '\\' ORDER BY createdAt DESC LIMIT 20", likePattern(term))
        }
    }

    // MARK: - Legacy JSON import (runs once, flag-guarded)

    private func importLegacyJSONIfNeeded() {
        guard let db = db else { return }
        guard scalar("SELECT COUNT(*) FROM store_meta WHERE key = 'json_import'") == 0 else { return }
        defer {
            exec("INSERT OR REPLACE INTO store_meta(key, value) VALUES('json_import', 'done');")
        }
        let url = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("gsai-store.json")
        guard let data = try? Data(contentsOf: url),
              let document = try? JSONDecoder().decode(LegacyDocument.self, from: data) else { return }
        exec("BEGIN TRANSACTION;")
        for conversation in document.conversations { upsertConversation(conversation) }
        for message in document.messages { upsertMessage(message) }
        exec("COMMIT;")
        exec("INSERT INTO conversations_fts(conversations_fts) VALUES('rebuild');")
        exec("INSERT INTO messages_fts(messages_fts) VALUES('rebuild');")
    }

    private struct LegacyDocument: Codable {
        var conversations: [StoredConversation]
        var messages: [StoredMessage]
    }

    // MARK: - SQLite plumbing

    @discardableResult
    private func exec(_ sql: String) -> Bool {
        guard let db = db else { return false }
        return sqlite3_exec(db, sql, nil, nil, nil) == SQLITE_OK
    }

    private func prepare(_ sql: String) -> OpaquePointer? {
        guard let db = db else { return nil }
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else { return nil }
        return stmt
    }

    private func bind(_ stmt: OpaquePointer, _ index: Int32, _ value: String?) {
        if let value = value {
            sqlite3_bind_text(stmt, index, value, -1, Self.transient)
        } else {
            sqlite3_bind_null(stmt, index)
        }
    }

    private func text(_ stmt: OpaquePointer, _ index: Int32) -> String {
        guard let cString = sqlite3_column_text(stmt, index) else { return "" }
        return String(cString: cString)
    }

    private func deleteRows(_ sql: String, _ arg: String) {
        guard let stmt = prepare(sql) else { return }
        defer { sqlite3_finalize(stmt) }
        bind(stmt, 1, arg)
        sqlite3_step(stmt)
    }

    private func scalar(_ sql: String) -> Int64 {
        guard let stmt = prepare(sql) else { return 0 }
        defer { sqlite3_finalize(stmt) }
        guard sqlite3_step(stmt) == SQLITE_ROW else { return 0 }
        return sqlite3_column_int64(stmt, 0)
    }

    private func queryColumn(_ sql: String, _ arg: String) -> [String] {
        guard let stmt = prepare(sql) else { return [] }
        defer { sqlite3_finalize(stmt) }
        bind(stmt, 1, arg)
        var values: [String] = []
        while sqlite3_step(stmt) == SQLITE_ROW {
            values.append(text(stmt, 0))
        }
        return values
    }

    private func queryMessages(_ sql: String, _ arg: String) -> [MessageHit] {
        guard let stmt = prepare(sql) else { return [] }
        defer { sqlite3_finalize(stmt) }
        bind(stmt, 1, arg)
        var hits: [MessageHit] = []
        while sqlite3_step(stmt) == SQLITE_ROW {
            hits.append(MessageHit(
                conversationID: text(stmt, 0),
                messageID: text(stmt, 1),
                content: text(stmt, 2),
                createdAt: text(stmt, 3)))
        }
        return hits
    }

    private func likePattern(_ term: String) -> String {
        let escaped = term
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "%", with: "\\%")
            .replacingOccurrences(of: "_", with: "\\_")
        return "%\(escaped)%"
    }

    // MARK: - Edit flow (truncate a thread from a user turn onward)

    /// Stamp of the most recent persisted user turn with exactly this content.
    func latestUserStamp(content: String, conversationId: String) -> String? {
        queue.sync {
            guard let stmt = prepare(
                "SELECT createdAt FROM messages " +
                "WHERE role = 'user' AND content = ? AND conversationId = ? " +
                "ORDER BY createdAt DESC LIMIT 1") else { return nil }
            defer { sqlite3_finalize(stmt) }
            bind(stmt, 1, content)
            bind(stmt, 2, conversationId)
            guard sqlite3_step(stmt) == SQLITE_ROW else { return nil }
            return text(stmt, 0)
        }
    }

    /// Deletes the turn stamped `fromInclusive` and everything after it in the
    /// thread (fixed-width UTC stamps make >= lexicographic-safe). FTS syncs
    /// via the delete triggers.
    func deleteMessages(fromInclusive stamp: String, conversationId: String) {
        queue.sync {
            guard let stmt = prepare(
                "DELETE FROM messages WHERE conversationId = ? AND createdAt >= ?") else { return }
            defer { sqlite3_finalize(stmt) }
            bind(stmt, 1, conversationId)
            bind(stmt, 2, stamp)
            sqlite3_step(stmt)
        }
    }
}
