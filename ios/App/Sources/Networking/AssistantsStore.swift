import Foundation
import SwiftUI

/// Local store for user-created assistants — the builder form's persistence
/// layer (create / edit / delete / pin / archive, all on-device) plus the
/// favourites set shared by samples and user assistants. Backed by
/// UserDefaults JSON, the same local-first contract as chats: no backend
/// required, store hiccups resolve to an empty list, never an error. Sample
/// assistants stay curated (not editable, not deletable) — user data lives
/// alongside them.
final class AssistantsStore: ObservableObject {
    static let shared = AssistantsStore()

    @Published private(set) var userAssistants: [AssistantSample] = []

    /// Favourite ids for any assistant (sample or user-created); survives relaunches.
    @Published private(set) var favourites: Set<String> = []

    private static let key = "gs.assistants.user"
    private static let favKey = "gs.assistants.favs"

    /// Favourites before the first real toggle — today's curated defaults.
    private static let defaultFavourites: Set<String> = ["asst-1", "asst-6"]

    private init() {
        if let data = UserDefaults.standard.data(forKey: Self.key),
           let saved = try? JSONDecoder().decode([AssistantSample].self, from: data) {
            userAssistants = saved.filter { !$0.id.isEmpty && !$0.name.isEmpty }
        }
        if let saved = UserDefaults.standard.stringArray(forKey: Self.favKey) {
            favourites = Set(saved)
        } else {
            favourites = Self.defaultFavourites
        }
    }

    func find(_ id: String) -> AssistantSample? {
        userAssistants.first { $0.id == id }
    }

    /// Insert or update by id, newest last; order is stable across launches.
    /// The builder form doesn't know pin/archive state — an edit carries the
    /// existing flags over instead of resetting them.
    func upsert(_ assistant: AssistantSample) {
        var carried = assistant
        if let existing = userAssistants.first(where: { $0.id == assistant.id }) {
            carried.pinned = existing.pinned
            carried.archived = existing.archived
        }
        userAssistants = userAssistants.filter { $0.id != carried.id } + [carried]
        save()
    }

    func remove(_ id: String) {
        userAssistants = userAssistants.filter { $0.id != id }
        save()
    }

    func toggleFavourite(_ id: String) {
        if favourites.contains(id) {
            favourites.remove(id)
        } else {
            favourites.insert(id)
        }
        UserDefaults.standard.set(favourites.sorted(), forKey: Self.favKey)
    }

    /// Pinned assistants sort to the top of My assistants (user-owned only).
    func togglePin(_ id: String) {
        mutate(id) { $0.pinned.toggle() }
    }

    /// Archived assistants leave the default segments and rest in Archived.
    func setArchived(_ id: String, _ archived: Bool) {
        mutate(id) { $0.archived = archived }
    }

    private func mutate(_ id: String, _ change: (inout AssistantSample) -> Void) {
        guard let index = userAssistants.firstIndex(where: { $0.id == id }) else { return }
        change(&userAssistants[index])
        save()
    }

    private func save() {
        if let data = try? JSONEncoder().encode(userAssistants) {
            UserDefaults.standard.set(data, forKey: Self.key)
        }
    }
}
