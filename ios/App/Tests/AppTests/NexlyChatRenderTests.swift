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

    func testFlashQuestionsRender() async throws {
        let dir = Self.outputDirectory()
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let questions: [(String, String, String)] = [
            ("coding", NEXLY_RANDOM_CODING_USER, NEXLY_RANDOM_CODING_WIRE),
            ("life", NEXLY_RANDOM_LIFE_USER, NEXLY_RANDOM_LIFE_WIRE),
        ]
        for (id, question, wire) in questions {
            let size = CGSize(width: 393, height: 1400)
            guard let image = try await Self.renderFlash(
                question: question, wire: wire, size: size
            ) else {
                XCTFail("flash \(id) did not composite")
                continue
            }
            guard let data = image.pngData() else {
                XCTFail("no PNG data for flash \(id)")
                continue
            }
            XCTAssertGreaterThan(data.count, 20_000)
            let url = dir.appendingPathComponent("nexly-chat-ios-random-\(id)-393x1400.png")
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

    private static func renderFlash(
        question: String, wire: String, size: CGSize
    ) async throws -> UIImage? {
        let view = NexlyFlashQuestionView(question: question, wire: wire)
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
        // The geometry is the real device size. The column is pinned to that
        // exact height (and the host ignores the simulator safe area), so the
        // transcript absorbs the slack and the composer stays on screen.
        GeometryReader { geo in
            let column = min(640, geo.size.width)
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
                                // Flash: the timetable is the answer.
                                showStatus: false,
                                isPlanTurn: true,
                                planTimestamp: "2026-10-22T21:45:00Z"
                            )
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
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
                    .padding(.top, 3)
                    .padding(.bottom, 10)
                }
                .frame(width: column, height: geo.size.height, alignment: .top)
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
    }
}

/// A fresh flash question: no reasoning stages, just the real user turn and
/// the real flash answer. The two questions are captured by the one-shot live
/// CI job, not hand-authored.
private struct NexlyFlashQuestionView: View {
    let question: String
    let wire: String
    @FocusState private var composerFocused: Bool

    var body: some View {
        GeometryReader { geo in
            let column = min(640, geo.size.width)
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
                        VStack(spacing: 14) {
                            MessageBubble(
                                message: ChatViewModel.ChatMessage(
                                    role: "user",
                                    content: question,
                                    createdAt: "2026-10-23T10:30:00Z"
                                ),
                                editEnabled: false
                            )
                            NexlyAssistantBlocksView(
                                blocks: parseBlocks(wire),
                                isStreaming: false,
                                showStatus: false,
                                isPlanTurn: false,
                                planTimestamp: "2026-10-23T10:31:00Z"
                            )
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
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
                    .padding(.top, 3)
                    .padding(.bottom, 10)
                }
                .frame(width: column, height: geo.size.height, alignment: .top)
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
                    .padding(.top, 3)
                    .padding(.bottom, 10)
                }
                .frame(width: column, height: geo.size.height, alignment: .top)
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

/// Fresh flash questions captured by the one-shot live CI job
/// (`scripts/live-random-questions-v2.ts`, through the live server). They are
/// unrelated to the sheet.
private let NEXLY_RANDOM_CODING_USER =
    "Why does my SQL query return duplicate rows when I join orders with order_items, and what is the cleanest fix?"

private let NEXLY_RANDOM_CODING_WIRE = """
The duplicate rows happen because the join is many‑to‑many: one order can have multiple order items, and the join repeats the order data for each matching item. When you select columns from the **orders** table without aggregating them, each order item creates a separate row, so the same order appears multiple times.

**Cleanest fix**

Wrap the query in a **subquery** (or CTE) that groups the orders first, then join to the items. This collapses the order rows before the many‑to‑many relationship is introduced.

```sql
WITH order_summary AS (
  SELECT
    order_id,
    customer_id,
    order_date,
    total_amount,
    -- any other order columns you need
  FROM orders
  GROUP BY order_id, customer_id, order_date, total_amount
)
SELECT
  os.order_id,
  os.customer_id,
  os.order_date,
  os.total_amount,
  oi.item_id,
  oi.product_name,
  oi.quantity,
  oi.unit_price
FROM order_summary os
JOIN order_items oi
  ON os.order_id = oi.order_id
ORDER BY os.order_date, os.order_id;
```

**Why this works**

1. **`order_summary`** aggregates the orders table, producing one row per `order_id`.  
2. The **join** now pairs each distinct order with its items, so you get one order row per item but no duplicate order rows.  
3. If you need totals per order (e.g., sum of `quantity * unit_price`), add an aggregate in the CTE or a final `GROUP BY` after the join.

**Optional: keep the original order rows as a separate column**

If you still want the original order row repeated for each item (e.g., for reporting), use a **window function** instead of a subquery:

```sql
SELECT
  order_id,
  customer_id,
  order_date,
  total_amount,
  item_id,
  product_name,
  quantity,
  unit_price
FROM orders o
JOIN order_items oi
  ON o.order_id = oi.order_id
ORDER BY order_date, order_id;
```

Here the order columns are taken directly from `orders`, but because the join is many‑to‑many you still see duplicates. To avoid that while preserving the original order rows, you can **`DISTINCT`** on the order columns plus the item columns if you only need unique combinations:

```sql
SELECT DISTINCT
  o.order_id,
  o.customer_id,
  o.order_date,
  o.total_amount,
  oi.item_id,
  oi.product_name,
  oi.quantity,
  oi.unit_price
FROM orders o
JOIN order_items oi
  ON o.order_id = oi.order_id
ORDER BY o.order_date, o.order_id;
```

**Bottom line**

- Use a **CTE/grouped subquery** when you need one order row per item without repeating the order data.  
- Use **`DISTINCT`** only if you truly need unique combinations and don’t need aggregation.  
- Choose the approach that matches whether you need aggregated totals or just de‑duplicated rows.
"""

private let NEXLY_RANDOM_LIFE_USER =
    "The bottom leaves of my monstera are turning yellow. What is most likely wrong and what should I do first?"

private let NEXLY_RANDOM_LIFE_WIRE = """
The most common cause of yellowing lower leaves on a Monstera is **over‑watering or poor drainage**, which leads to root rot and reduced nutrient uptake. The plant’s lower leaves are the first to show stress because they’re farthest from the crown and receive the least light.

**What to do first**

1. **Check the soil moisture** – Stick your finger 2–3 cm into the pot. If it feels soggy or the pot sits in a saucer of water, the roots are likely water‑logged.
2. **Let the soil dry out** – Move the plant to a brighter spot (bright indirect light is ideal) and stop watering until the top 5 cm of soil feels dry to the touch. In most indoor conditions this is every 7–10 days.
3. **Improve drainage** – If the pot lacks drainage holes, repot into a pot with holes and a well‑draining mix (e.g., 1 part perlite or orchid bark to 2 parts potting soil). This prevents water from pooling at the bottom.
4. **Inspect roots** – When you repot, gently tease out the root ball. Healthy roots are firm and white; brown, mushy, or blackened roots indicate rot. Trim away any rotten sections with clean scissors.
5. **Adjust watering routine** – After repotting, water thoroughly to flush excess salts, then let the soil dry out completely before the next watering. Consider using a moisture‑meter or a simple “finger test” to keep watering consistent.

**Why this works**

- Excess water deprives roots of oxygen, causing them to suffocate and decay.
- Root rot reduces the plant’s ability to absorb water and nutrients, so the lower leaves—being the oldest—turn yellow first.
- Fixing drainage and allowing the soil to dry restores healthy root function, and the plant will usually recover new growth within a few weeks.

**Next steps (if needed)**

- If many leaves are yellowing or the plant looks wilted despite drier soil, look for other stressors: low humidity, nutrient deficiencies, or pests. A balanced house‑plant fertilizer (diluted to half strength) can help once the roots are healthy.
- Keep the plant in bright, indirect light, avoid direct sun, and maintain moderate humidity (40‑60 %). These conditions support vigorous growth and prevent further leaf stress.

By addressing watering and drainage first, you give your Monstera the best chance to bounce back and keep producing those iconic split leaves.
"""
