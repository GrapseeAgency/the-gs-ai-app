import XCTest
import SwiftUI
import UIKit
@testable import GSApp

/**
 * The REAL model-response composition rendered on the simulator: the same
 * NexlyAssistantBlocksView, Thinking card, Plan card, glass cards, orb and
 * pearlescent backdrop the chat screen mounts.
 *
 * The reference wire is the exact nine-row plan from
 * docs/reference/nexly/chat-plan-reference.png. The PNG is the evidence; this
 * test merely guarantees something was actually composited.
 */
@MainActor
final class NexlyChatRenderTests: XCTestCase {

    private static let targets: [(width: CGFloat, height: CGFloat, name: String)] = [
        (393, 852, "393x852"),
        (411, 914, "411x914"),
        (360, 780, "360x780"),
    ]

    private static let scale: CGFloat = 3

    func testNexlyResponseRendersAtEveryWidth() async throws {
        let dir = Self.outputDirectory()
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)

        for target in Self.targets {
            let size = CGSize(width: target.width, height: target.height)
            guard let image = try await Self.render(size: size) else {
                XCTFail("drawHierarchy reported failure at \(target.name) — nothing was composited")
                continue
            }
            XCTAssertEqual(Int(image.size.width), Int(target.width))
            guard let data = image.pngData() else {
                XCTFail("no PNG data at \(target.name)")
                continue
            }
            XCTAssertGreaterThan(data.count, 20_000,
                                 "\(target.name) is suspiciously small for a full response render")
            let url = dir.appendingPathComponent("nexly-chat-ios-\(target.name).png")
            FileManager.default.createFile(atPath: url.path, contents: data)
            XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
        }
    }

    func testFullChatRenders() async throws {
        let dir = Self.outputDirectory()
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let targets: [(CGFloat, CGFloat, String)] = [
            (411, 914, "411x914"),
            (393, 852, "393x852"),
        ]
        for target in targets {
            let size = CGSize(width: target.0, height: target.1)
            guard let image = try await Self.renderFull(size: size) else {
                XCTFail("full chat did not composite at \(target.2)")
                continue
            }
            guard let data = image.pngData() else {
                XCTFail("no PNG data for the full chat at \(target.2)")
                continue
            }
            XCTAssertGreaterThan(data.count, 20_000)
            let url = dir.appendingPathComponent("nexly-chat-ios-full-\(target.2).png")
            FileManager.default.createFile(atPath: url.path, contents: data)
            XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
        }
    }

    func testAdaptiveThinkingRenders() async throws {
        let dir = Self.outputDirectory()
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let size = CGSize(width: 393, height: 852)
        guard let image = try await Self.renderAdaptive(size: size) else {
            return XCTFail("the adaptive-thinking render did not composite")
        }
        guard let data = image.pngData() else {
            return XCTFail("no PNG data for the adaptive-thinking render")
        }
        XCTAssertGreaterThan(data.count, 20_000)
        let url = dir.appendingPathComponent("nexly-chat-ios-thinking-393x852.png")
        FileManager.default.createFile(atPath: url.path, contents: data)
        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
    }

    private static func render(size: CGSize) async throws -> UIImage? {
        let view = NexlyChatReferenceView(wire: NEXLY_REFERENCE_WIRE)
            .frame(width: size.width, height: size.height)
            .environment(\.layoutDirection, .leftToRight)
        return try await capture(view: view, size: size)
    }

    private static func renderAdaptive(size: CGSize) async throws -> UIImage? {
        let view = NexlyAdaptiveThinkingView()
            .frame(width: size.width, height: size.height)
            .environment(\.layoutDirection, .leftToRight)
        return try await capture(view: view, size: size)
    }

    private static func renderFull(size: CGSize) async throws -> UIImage? {
        let view = NexlyFullChatView()
            .frame(width: size.width, height: size.height)
            .environment(\.layoutDirection, .leftToRight)
        return try await capture(view: view, size: size)
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
            drew = host.view.drawHierarchy(in: CGRect(origin: .zero, size: size),
                                           afterScreenUpdates: true)
        }
        return drew ? image : nil
    }

    private static func outputDirectory() -> URL {
        // The simulator shares the host /tmp; a CI env var never reaches the
        // simulator-launched test host (the Home harness learned this the hard
        // way). Keep the same channel and the same directory prefix.
        if let custom = ProcessInfo.processInfo.environment["GS_NEXLY_RENDER_DIR"], !custom.isEmpty {
            return URL(fileURLWithPath: custom, isDirectory: true)
        }
        return URL(fileURLWithPath: "/tmp/gs-nexly-renders", isDirectory: true)
    }
}

private struct NexlyChatReferenceView: View {
    let wire: String

    var body: some View {
        ZStack {
            NexlyChatBackdrop()
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 12) {
                    NexlyAssistantBlocksView(
                        blocks: parseBlocks(wire),
                        isStreaming: false,
                        showStatus: true,
                        thinkingPhase: .done,
                        thinkingEffort: "high",
                        isPlanTurn: true,
                        planTimestamp: "2026-10-22T21:45:00Z"
                    )
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 24)
            }
        }
    }
}

/// The WHOLE reference screen built from production pieces: the shared brand
/// header, the real MessageBubble user turn, the real assistant block view and
/// the shared composer capsule.
private struct NexlyFullChatView: View {
    @FocusState private var composerFocused: Bool

    var body: some View {
        ZStack {
            NexlyChatBackdrop()
            VStack(spacing: 0) {
                NexlyBrandHeader(
                    sparkleLabel: "New chat",
                    menuGlyph: "menu-2",
                    menuLabel: "Open menu",
                    onSparkle: {},
                    onMenu: {},
                    sparkleMenu: { EmptyView() },
                    menuMenu: { EmptyView() }
                )
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: 20) {
                        MessageBubble(
                            message: ChatViewModel.ChatMessage(
                                role: "user",
                                content: NEXLY_REFERENCE_USER,
                                createdAt: "2026-10-22T21:41:00Z"
                            ),
                            editEnabled: false
                        )
                        NexlyAssistantBlocksView(
                            blocks: parseBlocks(NEXLY_REFERENCE_WIRE),
                            isStreaming: false,
                            showStatus: true,
                            thinkingPhase: .done,
                            thinkingEffort: "high",
                            isPlanTurn: true,
                            planTimestamp: "2026-10-22T21:45:00Z"
                        )
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 20)
                }
                NexlyComposerBar(
                    text: .constant(""),
                    isStreaming: false,
                    canSend: false,
                    focused: $composerFocused,
                    onAttach: {},
                    onVoice: {},
                    onSearch: {},
                    onSend: {},
                    onStop: {},
                    onSubmit: {}
                )
                .padding(.horizontal, Aero.Spacing.m)
                .padding(.top, 6)
                .padding(.bottom, 6)
            }
        }
    }
}

/// A non-plan thinking turn: the card copy and active row must adapt instead
/// of repeating the schedule copy for every question.
private struct NexlyAdaptiveThinkingView: View {
    var body: some View {
        ZStack {
            NexlyChatBackdrop()
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 12) {
                    NexlyAssistantBlocksView(
                        blocks: parseBlocks(NEXLY_THINKING_WIRE),
                        isStreaming: true,
                        showStatus: true,
                        thinkingPhase: .reasoning,
                        thinkingEffort: "high",
                        thinkingReasoningHint: "Checking that the correlation cannot carry a signal.",
                        isPlanTurn: false
                    )
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 24)
            }
        }
    }
}

/// The exact response in the reference sheet, written in the wire contract.
private let NEXLY_REFERENCE_WIRE = """
Got it! I'll create a balanced plan for your tomorrow — focused on studies, productivity, health and rest.

Let me think for a moment...

```plan
{
  "title": "Here's your personalized plan for tomorrow:",
  "rows": [
    { "time": "8:00 – 8:30 AM", "icon": "sunrise", "tone": "amber", "text": "Morning routine + light exercise" },
    { "time": "9:00 – 11:00 AM", "icon": "book-2", "tone": "lavender", "text": "Classes" },
    { "time": "11:30 – 1:00 PM", "icon": "device-laptop", "tone": "blue", "text": "Assignment / Study time" },
    { "time": "1:00 – 2:00 PM", "icon": "tools-kitchen-2", "tone": "green", "text": "Lunch + short break" },
    { "time": "2:00 – 4:00 PM", "icon": "file-text", "tone": "lavender", "text": "Continue assignments" },
    { "time": "4:00 – 5:00 PM", "icon": "run", "tone": "pink", "text": "Workout / Physical activity" },
    { "time": "5:00 – 7:00 PM", "icon": "device-gamepad-2", "tone": "blue", "text": "Free time / Hobbies" },
    { "time": "7:00 – 9:00 PM", "icon": "book-2", "tone": "lavender", "text": "Light study / Review" },
    { "time": "9:30 – 10:30 PM", "icon": "moon", "tone": "lilac", "text": "Wind down + Sleep" }
  ],
  "closing": "You got this, Arafat!",
  "closingIcon": "flame",
  "timestamp": "9:45 PM"
}
```
"""

/// A non-plan thinking turn, written in the wire contract.
private let NEXLY_THINKING_WIRE = """
Quantum entanglement links two particles so their measured states stay
correlated, no matter how far apart they are.

The important part is that the correlation is not a signal: you cannot use
it to send information faster than light.
"""

/// The exact user turn from the reference sheet.
private let NEXLY_REFERENCE_USER =
    "Can you help me plan a productive day tomorrow? I have classes, " +
    "assignments and also want to stay healthy and get some rest."
