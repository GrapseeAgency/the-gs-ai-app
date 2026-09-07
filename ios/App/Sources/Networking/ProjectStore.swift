import Foundation
import Combine

/// The projects contract — every project card, hero and detail tab answers
/// from user-created, on-device data. A project bundles what the reader
/// actually has: chats they linked, instructions they wrote, a timestamp
/// that moves when they touch it, and an activity log of real actions.
/// Nothing seeds, nothing pretends: files and teammates are honest gates
/// until their subsystems land. State lives in one @Published array and
/// writes through to UserDefaults the moment it changes — the same
/// local-first shape as SettingsStore and the assistants store, mirroring
/// the Android ProjectStore contract one to one.
@MainActor
final class ProjectStore: ObservableObject {

    static let shared = ProjectStore()

    struct ProjectEvent: Codable, Equatable {
        var text: String
        var at: String
    }

    struct Project: Codable, Identifiable, Equatable {
        var id: String
        var name: String
        var blurb: String
        var instructions: String
        var createdAt: String
        var updatedAt: String
        var chatIds: [String]
        var events: [ProjectEvent]
    }

    @Published private(set) var projects: [Project] { didSet { persist() } }

    private let defaults = UserDefaults.standard
    private let key = "gs.projects.v1"
    private let maxEvents = 30

    private init() {
        if let raw = defaults.data(forKey: key),
           let stored = try? JSONDecoder().decode([Project].self, from: raw) {
            projects = stored
        } else {
            projects = []
        }
    }

    func byId(_ id: String) -> Project? {
        projects.first { $0.id == id }
    }

    @discardableResult
    func create(name: String, blurb: String, instructions: String) -> Project? {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        let now = isoNow()
        let project = Project(
            id: "proj-" + UUID().uuidString,
            name: trimmed,
            blurb: blurb.trimmingCharacters(in: .whitespacesAndNewlines),
            instructions: instructions.trimmingCharacters(in: .whitespacesAndNewlines),
            createdAt: now,
            updatedAt: now,
            chatIds: [],
            events: [ProjectEvent(text: "Project created", at: now)]
        )
        projects.insert(project, at: 0)
        return project
    }

    /// Edit flow: rename, re-describe, rewrite instructions — logs one event.
    /// One array write per edit: the @Published didSet persists the whole list,
    /// so per-field writes meant 4 JSON encodes of the full store per edit.
    func update(id: String, name: String, blurb: String, instructions: String) {
        guard let index = projects.firstIndex(where: { $0.id == id }) else { return }
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        var project = projects[index]
        let renamed = trimmed != project.name
        project.name = trimmed
        project.blurb = blurb.trimmingCharacters(in: .whitespacesAndNewlines)
        project.instructions = instructions.trimmingCharacters(in: .whitespacesAndNewlines)
        project.updatedAt = isoNow()
        project.events.append(ProjectEvent(
            text: renamed ? "Renamed to \"\(trimmed)\"" : "Project details edited",
            at: isoNow()
        ))
        trimEvents(&project)
        projects[index] = project
    }

    func delete(id: String) {
        projects.removeAll { $0.id == id }
    }

    /// Link a batch of chats picked in the detail sheet — one event per batch,
    /// one store write (see `update`).
    func linkChats(id: String, chatIds: [String], chatTitles: [String: String]) {
        guard let index = projects.firstIndex(where: { $0.id == id }) else { return }
        let fresh = chatIds.filter { !projects[index].chatIds.contains($0) }
        guard !fresh.isEmpty else { return }
        let label = chatTitles[fresh.first!] ?? "a chat"
        var project = projects[index]
        project.chatIds.append(contentsOf: fresh)
        project.updatedAt = isoNow()
        project.events.append(ProjectEvent(
            text: fresh.count == 1 ? "Linked chat \"\(label)\"" : "Linked \(fresh.count) chats",
            at: isoNow()
        ))
        trimEvents(&project)
        projects[index] = project
    }

    func unlinkChat(id: String, chatId: String) {
        guard let index = projects.firstIndex(where: { $0.id == id }) else { return }
        guard projects[index].chatIds.contains(chatId) else { return }
        var project = projects[index]
        project.chatIds.removeAll { $0 == chatId }
        project.updatedAt = isoNow()
        project.events.append(ProjectEvent(text: "Removed a chat", at: isoNow()))
        trimEvents(&project)
        projects[index] = project
    }

    private func trimEvents(_ project: inout Project) {
        if project.events.count > maxEvents {
            project.events.removeFirst(project.events.count - maxEvents)
        }
    }

    private func persist() {
        if let data = try? JSONEncoder().encode(projects) {
            defaults.set(data, forKey: key)
        }
    }

    private func isoNow() -> String {
        GSFormatters.isoFractional.string(from: Date()) // cached formatter
    }
}
