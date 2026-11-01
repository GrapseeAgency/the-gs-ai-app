import Foundation
import SwiftUI

/// The local record of conversations this reader shared out — the iOS twin of
/// Android's `SharedConversationsStore`. There is no share backend in this
/// build: a share is the moment the reader handed a conversation to the system
/// share sheet, and the record keeps what is true — which conversation, when.
/// The mock's "With N people" and avatar chips describe recipients no install
/// has, so the rows carry only what the device knows.
@MainActor
final class SharedConversationsStore: ObservableObject {
    static let shared = SharedConversationsStore()

    struct Share: Identifiable, Equatable, Codable {
        let id: String
        let conversationId: String
        let title: String
        let sharedAt: Date
    }

    @Published private(set) var entries: [Share] = []

    private static let key = "gs.shared.entries"

    private init() {
        if let raw = UserDefaults.standard.string(forKey: Self.key),
           let data = raw.data(using: .utf8),
           let decoded = try? JSONDecoder().decode([Share].self, from: data) {
            entries = decoded.sorted { $0.sharedAt > $1.sharedAt }
        }
    }

    func record(conversationId: String, title: String, at: Date = Date()) {
        entries.removeAll { $0.conversationId == conversationId }
        entries.insert(Share(id: "share-\(at.timeIntervalSince1970)", conversationId: conversationId, title: title, sharedAt: at), at: 0)
        persist()
    }

    func remove(id: String) {
        entries.removeAll { $0.id == id }
        persist()
    }

    private func persist() {
        if let data = try? JSONEncoder().encode(entries) {
            UserDefaults.standard.set(String(data: data, encoding: .utf8), forKey: Self.key)
        }
    }
}
