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
            (320, 568, "320x568"),
            (360, 640, "360x640"),
            (375, 667, "375x667"),
            (430, 932, "430x932"),
            (768, 1024, "768x1024"),
            (1024, 768, "1024x768"),
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

    func testFullProseChatRenders() async throws {
        let dir = Self.outputDirectory()
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        for target in [(393, 852, "393x852"), (411, 914, "411x914")] {
            let size = CGSize(width: target.0, height: target.1)
            guard let image = try await Self.renderFullProse(size: size) else {
                XCTFail("full prose chat did not composite at \(target.2)")
                continue
            }
            guard let data = image.pngData() else {
                XCTFail("no PNG data for the full prose chat at \(target.2)")
                continue
            }
            XCTAssertGreaterThan(data.count, 20_000)
            let url = dir.appendingPathComponent("nexly-chat-ios-full-prose-\(target.2).png")
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
            .ignoresSafeArea()
            .environment(\.layoutDirection, .leftToRight)
        return try await capture(view: view, size: size)
    }

    private static func renderFullProse(size: CGSize) async throws -> UIImage? {
        let view = NexlyFullProseView()
            .frame(width: size.width, height: size.height)
            .ignoresSafeArea()
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
                VStack(spacing: 10) {
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
        GeometryReader { geo in
            let column = min(640, geo.size.width)
            ZStack(alignment: .bottom) {
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
                        VStack(spacing: 14) {
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
                        .padding(.vertical, 12)
                        .padding(.bottom, 82)
                    }
                }
                .frame(width: column, height: geo.size.height, alignment: .top)

                // Anchor the composer to the geometry's bottom edge, not to
                // the scroll content. The transcript then absorbs the slack and
                // the capsule cannot be pushed below the screen.
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
                .padding(.bottom, 14)
                .frame(width: column)
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
    }
}

/// The whole screen for the DIFFERENT real-life prose question: a real user
/// turn and the real model answer, no plan card, with the adaptive status card.
private struct NexlyFullProseView: View {
    @FocusState private var composerFocused: Bool

    var body: some View {
        GeometryReader { geo in
            let column = min(640, geo.size.width)
            ZStack(alignment: .bottom) {
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
                        VStack(spacing: 14) {
                            MessageBubble(
                                message: ChatViewModel.ChatMessage(
                                    role: "user",
                                    content: NEXLY_PROSE_USER,
                                    createdAt: "2026-10-22T21:41:00Z"
                                ),
                                editEnabled: false
                            )
                            NexlyAssistantBlocksView(
                                blocks: parseBlocks(NEXLY_THINKING_WIRE),
                                isStreaming: false,
                                showStatus: true,
                                thinkingPhase: .reasoning,
                                thinkingEffort: "high",
                                isPlanTurn: false,
                                statusAtEnd: false
                            )
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                        .padding(.bottom, 82)
                    }
                }
                .frame(width: column, height: geo.size.height, alignment: .top)

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
                .padding(.bottom, 14)
                .frame(width: column)
            }
            .frame(width: geo.size.width, height: geo.size.height)
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
                VStack(spacing: 10) {
                    NexlyAssistantBlocksView(
                        blocks: parseBlocks(NEXLY_THINKING_WIRE),
                        isStreaming: true,
                        showStatus: true,
                        thinkingPhase: .reasoning,
                        thinkingEffort: "high",
                        isPlanTurn: false,
                        statusAtEnd: false
                    )
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 24)
            }
        }
    }
}

/// Captured from a REAL OpenRouter call (scripts/live-nexly-model.ts)
/// using the production SYSTEM_PROMPT + WIDGET_PROMPT and a DIFFERENT
/// real-life question, not the sheet's sample. No hand-authored plan rows.
private let NEXLY_REFERENCE_WIRE = """
Here’s a balanced schedule that fits your 9–5 shift, a gym session, meal prep, and two solid hours of study.

```plan
{"title":"Here's your balanced schedule for tomorrow:","rows":[{"time":"6:30 – 7:00 AM","text":"Wake up + light stretch","icon":"sunrise","tone":"amber"},{"time":"7:00 – 7:30 AM","text":"Breakfast & coffee","icon":"coffee","tone":"amber"},{"time":"7:30 – 8:15 AM","text":"Commute to work","icon":"bus","tone":"blue"},{"time":"8:15 – 9:00 AM","text":"Prep for shift","icon":"briefcase","tone":"blue"},{"time":"9:00 – 5:00 PM","text":"Work shift","icon":"device-laptop","tone":"blue"},{"time":"5:15 – 6:15 PM","text":"Gym session","icon":"activity","tone":"pink"},{"time":"6:30 – 7:15 PM","text":"Meal prep for tomorrow","icon":"tools-kitchen-2","tone":"green"},{"time":"7:30 – 9:30 PM","text":"Exam study (2 hrs)","icon":"book-2","tone":"lavender"},{"time":"9:45 – 10:15 PM","text":"Wind down & sleep prep","icon":"moon","tone":"lilac"}],"closing":"You've got this! :flame:","closingIcon":"flame","timestamp":"10:00 PM","accent":"#66a80f","variant":"tinted","palette":"meadow"}
```
"""

/// A non-plan thinking turn, written in the wire contract.
private let NEXLY_THINKING_WIRE = """
Procrastination on a thesis usually isn't laziness — it's a response to the scale and ambiguity of the work. A thesis is an open-ended, high-stakes project with no daily deadlines, so your brain treats it as a threat and steers you toward tasks that feel finishable (email, formatting, reading one more paper). Perfectionism, fear of evaluation, and the lack of immediate feedback all reinforce the loop.

**One realistic change this week:** pick a *tiny*, time-boxed writing window — 30 minutes, same time each day — and commit to producing *bad* words only. No editing, no research, no formatting. Just move the cursor forward. The goal isn't quality; it's breaking the avoidance signal. After seven sessions you'll have 3.5 hours of raw material and, more importantly, proof that you can show up without the project collapsing.
"""

/// A real-life question, deliberately different from the sheet's sample.
private let NEXLY_REFERENCE_USER =
    "I work a 9–5 shift tomorrow and need to fit in a gym session, meal prep, and two hours of study for an exam. Can you plan my day?"

/// The real-life non-plan question rendered by the prose harness.
private let NEXLY_PROSE_USER =
    "I keep procrastinating on my thesis. Why does that happen and what is one realistic change I can make this week?"
