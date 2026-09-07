import Foundation
import SwiftUI

/// Local store for user-created assistants — the builder form's persistence
/// layer (create / edit / delete, all on-device). Backed by UserDefaults JSON,
/// the same local-first contract as chats: no backend required, store hiccups
/// resolve to an empty list, never an error. Sample assistants stay curated
/// (not editable, not deletable) — user data lives alongside them.
final class AssistantsStore: ObservableObject {
    static let shared = AssistantsStore()

    @Published private(set) var userAssistants: [AssistantSample] = []

    private static let key = "gs.assistants.user"

    private init() {
        if let data = UserDefaults.standard.data(forKey: Self.key),
           let saved = try? JSONDecoder().decode([AssistantSample].self, from: data) {
            userAssistants = saved.filter { !$0.id.isEmpty && !$0.name.isEmpty }
        }
    }

    func find(_ id: String) -> AssistantSample? {
        userAssistants.first { $0.id == id }
    }

    /// Insert or update by id, newest last; order is stable across launches.
    func upsert(_ assistant: AssistantSample) {
        userAssistants = userAssistants.filter { $0.id != assistant.id } + [assistant]
        save()
    }

    func remove(_ id: String) {
        userAssistants = userAssistants.filter { $0.id != id }
        save()
    }

    private func save() {
        if let data = try? JSONEncoder().encode(userAssistants) {
            UserDefaults.standard.set(data, forKey: Self.key)
        }
    }
}
