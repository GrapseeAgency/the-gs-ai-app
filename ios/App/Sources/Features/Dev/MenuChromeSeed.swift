#if DEBUG
import Foundation

/// Deterministic conversations for `MenuChromeTests`.
///
/// The menu harness opens the REAL menus — the chats row's context menu, the
/// bubble menus, the drawer's recent-row menu — and those surfaces only exist
/// once the store has rows. An XCUITest runs outside the app and can set no
/// store state by any other route, so the app seeds itself when launched with
/// `-gs_test_seed_chats`. Three rows, fixed ids and fixed timestamps: the test
/// can name them, and a second launch cannot duplicate them.
enum MenuChromeSeed {

    struct Row {
        let id: String
        let title: String
        let userSays: String
        let assistantSays: String
        let pinned: Bool
    }

    /// The first row is pinned so the chats context menu can prove
    /// "Unpin" as well as "Pin to top" — and so the harvester sees the
    /// pinned glyph path too.
    static let rows: [Row] = [
        Row(id: "seed-menu-1",
            title: "Weekend in Kyoto",
            userSays: "What should I pack for Kyoto in November?",
            assistantSays: "Layers, a compact umbrella, and shoes you can walk a city in.",
            pinned: true),
        Row(id: "seed-menu-2",
            title: "Explain quantum physics",
            userSays: "Explain superposition without maths.",
            assistantSays: "A coin spinning on a table is neither heads nor tails until it lands.",
            pinned: false),
        Row(id: "seed-menu-3",
            title: "Plan my tomorrow",
            userSays: "Give me a calm plan for tomorrow.",
            assistantSays: "One deep block before noon, one walk, one thing you can drop.",
            pinned: false),
    ]

    /// Idempotent: a row already in the store is left exactly as it is.
    @MainActor
    static func install() {
        let store = ConversationStore.shared
        for (index, row) in rows.enumerated() where store.conversation(withID: row.id) == nil {
            // Fixed, distinct stamps: the thread's order and every relative
            // label in the UI become properties of the fixture, not of the
            // clock at launch.
            let created = String(format: "2026-01-01T09:%02d:00.000Z", index)
            let replied = String(format: "2026-01-01T09:%02d:30.000Z", index)
            store.upsert(StoredConversation(
                id: row.id,
                title: row.title,
                modelId: nil,
                pinned: row.pinned,
                archived: false,
                createdAt: created,
                updatedAt: replied))
            store.append(StoredMessage(
                id: "\(row.id)-user",
                conversationId: row.id,
                role: "user",
                content: row.userSays,
                createdAt: created))
            store.append(StoredMessage(
                id: "\(row.id)-assistant",
                conversationId: row.id,
                role: "assistant",
                content: row.assistantSays,
                createdAt: replied))
        }
    }
}
#endif
