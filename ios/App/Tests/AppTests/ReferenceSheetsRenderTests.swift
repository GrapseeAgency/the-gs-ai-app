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
