import SwiftUI
import UIKit
import XCTest
@testable import GSApp

/// The six owner sheets rendered on the simulator — the iOS counterpart of
/// `ReferenceSheetsScreenshotTest` on Android, and the PNG evidence each mock
/// under docs/reference/pending is compared against.
///
/// Same windowed `drawHierarchy` channel as the Settings/Explore harnesses.
/// A `Router` is injected because five of the six views own real navigation
/// (`@EnvironmentObject`), exactly as the shell provides it.
@MainActor
final class ReferenceSheetsRenderTests: XCTestCase {

    private static let scale: CGFloat = 3

    func testNotificationsAlertsRenders() async throws {
        try await capturePage(
            view: { NotificationsAlertsView() },
            prefix: "nexly-alerts-ios")
    }

    func testNotificationsInboxRenders() async throws {
        // The shipped inbox starts empty; the mock draws rows. Seeding here is
        // the iOS twin of Android's ReferenceSheetsScreenshotTest fixture and
        // is cleared again in `defer`, so no later launch sees invented data.
        // The fixed 18:00 keeps the Today/Earlier split deterministic: a run at
        // 00:30 must not collapse the day's rows into the three-row Earlier
        // window and change the card count the gate measures.
        let fixedNow = Self.fixedClock()
        seedInbox(now: fixedNow)
        defer { GsNoticeInbox.shared.removeAllForTesting() }
        try await capturePage(
            view: { NotificationsView(now: fixedNow) },
            prefix: "nexly-inbox-ios")
    }

    func testPrivacyDataControlsRenders() async throws {
        try await capturePage(
            view: { PrivacyDataControlsView() },
            prefix: "nexly-privacy-ios")
    }

    func testDataExportRenders() async throws {
        // The mock draws auto-delete ON. The safe default is OFF, so the
        // capture states the chosen state and restores it afterwards.
        let settings = SettingsStore.shared
        let previousAuto = settings.autoDeleteInactive
        let previousMonths = settings.dataRetentionMonths
        settings.autoDeleteInactive = true
        settings.dataRetentionMonths = 12
        defer {
            settings.autoDeleteInactive = previousAuto
            settings.dataRetentionMonths = previousMonths
        }
        try await capturePage(
            view: { DataExportAccountDeletionView() },
            prefix: "nexly-data-export-ios")
    }

    func testAccountSecurityRenders() async throws {
        try await capturePage(
            view: { AccountSecurityView() },
            prefix: "nexly-security-ios")
    }

    func testSubscriptionUsageRenders() async throws {
        try await capturePage(
            view: { SubscriptionUsageView() },
            prefix: "nexly-subscription-ios")
    }

    func testHelpSupportRenders() async throws {
        try await capturePage(
            view: { HelpSupportView() },
            prefix: "nexly-help-ios")
    }

    func testConnectedAppsRenders() async throws {
        try await capturePage(
            view: { ConnectedAppsView() },
            prefix: "nexly-integrations-ios")
    }

    /// The last two pending references to reach iOS. Both read the real store —
    /// an install with no chats renders its honest empty states, and the
    /// capture is the page as it ships, not a fixture dressed as one.
    func testModelSelectionRenders() async throws {
        try await capturePage(
            view: { AiIntelligenceView() },
            prefix: "nexly-model-ios")
    }

    func testChatHistorySearchRenders() async throws {
        try await capturePage(
            view: { ChatSearchView() },
            prefix: "nexly-chat-history-ios")
    }

    func testVoiceSpeechRenders() async throws {
        try await capturePage(
            view: { VoiceSpeechView() },
            prefix: "nexly-voice-ios")
    }

    /// The pre-chat page and the model picker. The picker's fixture states the
    /// same pair the mock draws (Pro, Medium) because the shared store may hold
    /// a neighbour test's choice; the pre-chat capture is the honest empty
    /// recent list an install with no chats sees.
    func testNewChatRenders() async throws {
        // The mock draws four recent rows; the shipped store starts empty. The
        // fixture seeds the same four titles and previews Android's capture
        // seeds, and removes them in `defer` so no later launch sees them.
        let seeded = seedRecentChats()
        defer { seeded.forEach { ConversationStore.shared.delete(id: $0) } }
        try await capturePage(
            view: { NewChatView(
                onStartChat: { _, _ in }, onOpenConversation: { _ in },
                onOpenAllChats: {}, onOpenModels: {}, onOpenMenu: {},
                onOpenExplore: {}) },
            prefix: "nexly-new-chat-ios")
    }

    /// Four conversations at the mock's ages, with the mock's previews.
    private func seedRecentChats() -> [String] {
        let store = ConversationStore.shared
        let rows: [(String, String, Double)] = [
            ("Website Design Ideas", "Here are some modern and clean design directions for your landing page.", 2),
            ("Study Plan for Next Month", "Here's a personalized study plan based on your goals and the time you have.", 4),
            ("Travel Itinerary - Japan", "A 10-day itinerary for Tokyo and Kyoto with a calm pace and one day trip.", 24),
            ("Code Review Help", "Let's go through your code and improve its structure and naming together.", 48),
        ]
        let now = Date()
        var ids: [String] = []
        for (index, row) in rows.enumerated() {
            let id = "render-recent-\(index)"
            let stamp = ConversationStore.isoString(now.addingTimeInterval(-row.2 * 3600))
            // Message first: `append` touches an existing conversation's
            // `updatedAt` to now, which would make every row read "now". The
            // conversation is written after it, with the mock's own stamp.
            store.append(StoredMessage(
                id: "render-msg-\(index)", conversationId: id, role: "assistant",
                content: row.1, createdAt: stamp))
            store.upsert(StoredConversation(
                id: id, title: row.0, modelId: nil, pinned: false, archived: false,
                createdAt: stamp, updatedAt: stamp))
            ids.append(id)
        }
        return ids
    }

    /// The three conversation-management pages. The archived capture seeds
    /// the mock's three rows at the mock's own dates, the shared capture the
    /// mock's three records at its ages (the shipped store starts empty — the
    /// fixtures are TEST-ONLY and removed in `defer`), and the details capture
    /// the mock's one conversation ("Project Inspiration", today, 9:24 AM).
    func testArchivedRenders() async throws {
        let seeded = seedArchivedChats()
        defer { seeded.forEach { ConversationStore.shared.delete(id: $0) } }
        try await capturePage(
            view: { ArchivedChatsView() },
            prefix: "nexly-archived-ios")
    }

    func testSharedRenders() async throws {
        let seeded = seedSharedRecords()
        defer { seeded.forEach { SharedConversationsStore.shared.remove(id: $0) } }
        try await capturePage(
            view: { SharedConversationsView() },
            prefix: "nexly-shared-ios")
    }

    func testConversationDetailsRenders() async throws {
        let id = seedDetailsConversation()
        defer { ConversationStore.shared.delete(id: id) }
        try await capturePage(
            view: { ConversationDetailsView(conversationID: id) },
            prefix: "nexly-conversation-details-ios")
    }

    /// The archived mock's three rows, with its own dates.
    private func seedArchivedChats() -> [String] {
        let store = ConversationStore.shared
        let rows: [(String, String)] = [
            ("Project Aurora", "2025-04-14T09:00:00Z"),
            ("Code Review Guide", "2025-03-28T11:00:00Z"),
            ("Study Plan 2025", "2025-03-15T15:00:00Z"),
        ]
        var ids: [String] = []
        for (index, row) in rows.enumerated() {
            let id = "render-archived-\(index)"
            store.upsert(StoredConversation(
                id: id, title: row.0, modelId: nil, pinned: false, archived: true,
                createdAt: row.1, updatedAt: row.1))
            ids.append(id)
        }
        return ids
    }

    /// The shared mock's three records at its ages.
    private func seedSharedRecords() -> [String] {
        let store = SharedConversationsStore.shared
        let rows: [(String, Double)] = [
            ("AI Tools for Productivity", 2),
            ("Web Development Guide", 24),
            ("Study Plan for Exams", 48),
        ]
        let now = Date()
        var ids: [String] = []
        for row in rows {
            store.record(
                conversationId: "render-shared-\(row.0.hashValue.magnitude)",
                title: row.0,
                at: now.addingTimeInterval(-row.1 * 3600))
        }
        ids = store.entries.map { $0.id }
        return ids
    }

    /// The details mock's hero: "Project Inspiration", today 9:24 AM, two
    /// messages about a minute apart.
    private func seedDetailsConversation() -> String {
        let store = ConversationStore.shared
        let id = "render-details-0"
        // bySettingHour keeps TODAY's date — date(from:) with hour/minute
        // alone fills the missing fields with year 1, which the hero then
        // honestly printed as "Started Jan 1, 0001".
        let today924 = Calendar.current.date(bySettingHour: 9, minute: 24, second: 0, of: Date()) ?? Date()
        let first = ConversationStore.isoString(today924)
        let second = ConversationStore.isoString(today924.addingTimeInterval(65))
        store.append(StoredMessage(
            id: "render-details-msg-0", conversationId: id, role: "user",
            content: "I need fresh ideas for the landing page hero.", createdAt: first))
        store.append(StoredMessage(
            id: "render-details-msg-1", conversationId: id, role: "assistant",
            content: "Here are three directions worth exploring.", createdAt: second))
        store.upsert(StoredConversation(
            id: id, title: "Project Inspiration", modelId: nil, pinned: true, archived: false,
            createdAt: first, updatedAt: second))
        return id
    }

    func testModelPickerRenders() async throws {
        let settings = SettingsStore.shared
        let previousModel = settings.preferredModel
        let previousEffort = settings.reasoningEffort
        settings.preferredModel = "pro"
        settings.reasoningEffort = "standard"
        defer {
            settings.preferredModel = previousModel
            settings.reasoningEffort = previousEffort
        }
        try await capturePage(
            view: { ModelSelectionView(onBack: {}, onContinue: {}) },
            prefix: "nexly-model-picker-ios")
    }

    /// Travel Agent is reachable from the trip planner now; its capture is the
    /// visual half of that wiring (the UI test drives the tap).
    func testTravelAgentRenders() async throws {
        try await capturePage(
            view: { TravelAgentView(onBack: {}, onSendToChat: { _ in }) },
            prefix: "nexly-travel-ios")
    }

    // MARK: helpers

    /// 18:00 today, so a capture at any hour sees the same Today/Earlier split.
    private static func fixedClock() -> Date {
        var components = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        components.hour = 18
        components.minute = 0
        components.second = 0
        return Calendar.current.date(from: components) ?? Date()
    }

    /// The same six notices Android's screenshot fixture seeds, with the same
    /// kinds and ages, so the two platforms' inbox renders can be compared.
    private func seedInbox(now: Date) {
        let inbox = GsNoticeInbox.shared
        inbox.removeAllForTesting()
        let rows: [(kind: String, title: String, body: String, hours: Double)] = [
            ("ai-updates", "New feature available",
             "You can now use image generation in chat. Explore the new creative tools and bring your ideas to life.", 2),
            ("account-activity", "Account update",
             "Your profile information has been updated. We've saved your latest changes successfully.", 4),
            ("product-updates", "System update",
             "Nexly is now more powerful. Improved performance, better reasoning and more.", 12),
            ("messages", "New message",
             "Alex mentioned you in a conversation. \"That's a great idea! Let's try it.\"", 8),
            ("tips", "Personalization applied",
             "Your preferences have been updated. We'll now tailor responses to your style and interests.", 24),
            ("account-activity", "Privacy reminder",
             "Review your data and privacy settings. Keep your information safe and in your control.", 48),
        ]
        // `record` prepends, so feed oldest first and the newest ends on top.
        for row in rows.sorted(by: { $0.hours > $1.hours }) {
            inbox.record(
                kindId: row.kind,
                title: row.title,
                body: row.body,
                now: now.addingTimeInterval(-row.hours * 3_600))
        }
        let readTitles: Set<String> = ["Account update", "New message", "Personalization applied"]
        for entry in inbox.entries where readTitles.contains(entry.title) {
            inbox.markRead(id: entry.id)
        }
    }

    private func capturePage(
        view: @escaping () -> some View,
        prefix: String
    ) async throws {
        let size = CGSize(width: 393, height: 852)
        let dir = Self.outputDirectory()
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let v = view()
            .environmentObject(Router())
            .frame(width: size.width, height: size.height)
            .ignoresSafeArea()
            .environment(\.layoutDirection, .leftToRight)
        guard let image = try await Self.capture(view: v, size: size) else {
            XCTFail("\(prefix) did not composite — nothing was drawn")
            return
        }
        guard let data = image.pngData() else {
            XCTFail("no PNG data for \(prefix)")
            return
        }
        XCTAssertGreaterThan(data.count, 20_000, "\(prefix) is suspiciously small")
        let url = dir.appendingPathComponent("\(prefix)-393x852.png")
        FileManager.default.createFile(atPath: url.path, contents: data)
        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
    }

    private static func outputDirectory() -> URL {
        if let custom = ProcessInfo.processInfo.environment["GS_NEXLY_RENDER_DIR"], !custom.isEmpty {
            return URL(fileURLWithPath: custom, isDirectory: true)
        }
        return URL(fileURLWithPath: "/tmp/gs-nexly-renders", isDirectory: true)
    }

    private static func capture<V: View>(view: V, size: CGSize) async throws -> UIImage? {
        let host = UIHostingController(rootView: view)
        host.view.frame = CGRect(origin: .zero, size: size)

        let window = UIWindow(frame: CGRect(origin: .zero, size: size))
        window.rootViewController = host
        window.layoutIfNeeded()
        window.makeKeyAndVisible()

        for _ in 0..<20 {
            try? await Task.sleep(nanoseconds: 16_000_000)
            host.view.layer.layoutIfNeeded()
            host.view.layer.displayIfNeeded()
        }

        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        var drew = false
        let image = renderer.image { _ in
            drew = host.view.drawHierarchy(
                in: CGRect(origin: .zero, size: size),
                afterScreenUpdates: true)
        }
        return drew ? image : nil
    }
}
