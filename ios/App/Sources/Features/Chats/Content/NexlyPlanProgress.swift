import Foundation

/// Durable, per-conversation progress for the Nexly plan checklist.
///
/// The rings used to be pure `@State`: they reset on scroll/reload and the
/// model never saw them. This store makes them real:
///  - persisted in UserDefaults, keyed by conversation,
///  - fingerprinted to the plan so an old checklist cannot restore onto a
///    different timetable,
///  - exposed as the compact, non-visible `turnContext` sent on the next turn.
///
/// Completion is progress, not a preference rating. Only rows the user
/// actually marked are reported.
struct NexlyPlanProgressStore {
    static let shared = NexlyPlanProgressStore()

    private let defaults = UserDefaults.standard

    private struct Item: Codable {
        let key: String
        let label: String
    }

    private struct Entry: Codable {
        let fingerprint: String
        let title: String
        let items: [Item]
        let done: [String]
        let preference: String?
        let updatedAt: TimeInterval
    }

    func fingerprint(for plan: NexlyPlanData) -> String {
        var hash: UInt64 = 0xcbf29ce484222325 // FNV-1a, stable across launches
        func mix(_ string: String) {
            for byte in string.utf8 {
                hash ^= UInt64(byte)
                hash = hash &* 0x100000001b3
            }
            hash ^= 0x1f
            hash = hash &* 0x100000001b3
        }
        mix(plan.title)
        for row in plan.rows {
            mix(row.time)
            mix(row.text)
        }
        return String(hash, radix: 16) + "-\(plan.rows.count)"
    }

    func load(conversationID: String?, fingerprint: String) -> Set<String> {
        guard let entry = read(conversationID), entry.fingerprint == fingerprint else {
            return []
        }
        return Set(entry.done)
    }

    func save(
        conversationID: String?,
        plan: NexlyPlanData,
        done: Set<String>,
        preference: String? = nil
    ) {
        guard let conversationID, !conversationID.isEmpty else { return }
        let items = plan.rows.map {
            Item(key: key(time: $0.time, text: $0.text),
                 label: $0.time.isEmpty ? $0.text : "\($0.time) — \($0.text)")
        }
        let entry = Entry(
            fingerprint: fingerprint(for: plan),
            title: plan.title,
            items: items,
            done: Array(done),
            preference: preference,
            updatedAt: Date().timeIntervalSince1970
        )
        guard let data = try? JSONEncoder().encode(entry) else { return }
        defaults.set(data, forKey: storageKey(conversationID))
    }

    func loadPreference(conversationID: String?, fingerprint: String) -> String? {
        guard let entry = read(conversationID), entry.fingerprint == fingerprint else {
            return nil
        }
        guard let preference = entry.preference,
              preference == "up" || preference == "down" else { return nil }
        return preference
    }

    /// The exact text sent as `turnContext` on the next message. Nil when no
    /// row has been completed yet — there is nothing for the model to adapt to.
    func context(for conversationID: String?) -> String? {
        guard let entry = read(conversationID),
              !entry.done.isEmpty || entry.preference != nil else { return nil }
        let done = Set(entry.done)
        let completed = entry.items.filter { done.contains($0.key) }.map(\.label)
        let pending = entry.items.filter { !done.contains($0.key) }.map(\.label)
        let total = completed.count + pending.count
        guard total > 0 else { return nil }
        let feedback: String
        switch entry.preference {
        case "up": feedback = "\n- User feedback: the plan was marked helpful."
        case "down": feedback = "\n- User feedback: the plan was marked not helpful."
        default: feedback = ""
        }
        return """
        Plan: \(entry.title.isEmpty ? "today's plan" : entry.title)
        - Completed (\(completed.count)/\(total)): \(completed.isEmpty ? "none" : completed.joined(separator: "; "))
        - Pending (\(pending.count)): \(pending.isEmpty ? "none" : pending.joined(separator: "; "))\(feedback)
        """
    }

    private func read(_ conversationID: String?) -> Entry? {
        guard let conversationID, !conversationID.isEmpty,
              let data = defaults.data(forKey: storageKey(conversationID)) else {
            return nil
        }
        return try? JSONDecoder().decode(Entry.self, from: data)
    }

    private func key(time: String, text: String) -> String {
        "\(time)|\(text)"
    }

    private func storageKey(_ conversationID: String) -> String {
        "nexly.planProgress.\(conversationID)"
    }
}
