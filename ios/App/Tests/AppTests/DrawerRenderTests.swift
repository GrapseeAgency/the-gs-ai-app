import XCTest
import SwiftUI
import UIKit
@testable import GSApp

/// The drawer rendered over the real Home page, on a real window, written where
/// the workflow can collect it.
///
/// This file does not assert the drawer's geometry — `scripts/check_drawer_render.py`
/// does that, from the PNG, against the sheet. What it asserts is the small set
/// of things a capture can get wrong about itself before any measurement is
/// possible: that it is not blank, that the panel occupies the left ~79% of the
/// frame, and that the strip beside it is darker than the same strip of Home
/// (i.e. the scrim actually composited). Without those, a black PNG would score
/// "could not measure" at best and a wrong one could pass.
final class DrawerRenderTests: XCTestCase {

    private var windows: [UIWindow] = []

    override func tearDown() {
        for window in windows {
            window.isHidden = true
            window.rootViewController = nil
        }
        windows.removeAll()
        super.tearDown()
    }

    private static func outputDirectory() -> URL {
        URL(fileURLWithPath: "/tmp/gs-drawer-renders", isDirectory: true)
    }

    /// The sheet's own rows, so the capture compares with it line for line.
    private static let fixture = DrawerFixture(
        name: "Arafat",
        workspace: "Personal workspace",
        initials: "A",
        recents: [
            // the production mapping, not hand-picked symbols
            DrawerRecent(id: "1", title: "Plan my tomorrow", timestamp: "Just now",
                         symbol: DrawerTopic.iconName(title: "Plan my tomorrow", pinned: true), pinned: true),
            DrawerRecent(id: "2", title: "Ideas for a brand launch", timestamp: "Yesterday",
                         symbol: DrawerTopic.iconName(title: "Ideas for a brand launch", pinned: false),
                         pinned: false),
            DrawerRecent(id: "3", title: "Explain quantum physics", timestamp: "Yesterday",
                         symbol: DrawerTopic.iconName(title: "Explain quantum physics", pinned: false),
                         pinned: false),
            DrawerRecent(id: "4", title: "Weekend in Kyoto", timestamp: "Mon",
                         symbol: DrawerTopic.iconName(title: "Weekend in Kyoto", pinned: false),
                         pinned: false),
        ]
    )

    @MainActor
    private func render(size: CGSize, dark: Bool,
                        fixture: DrawerFixture = DrawerRenderTests.fixture) throws -> UIImage? {
        let home = HomeDashboardView(
            onNewChat: {}, onOpenDrawer: {}, onOpenChats: {}, onOpenAgent: {},
            onOpenConversation: { _ in }, onStartPrompt: { _ in },
            onOpenVoice: {}, onOpenSettings: {}, greetingHour: 9
        )
        let view = ZStack {
            home
            AeroDrawer(
                entryOffset: .constant(0),
                onRoute: { _ in },
                onClose: {},
                activeRoute: nil,
                recentFixture: fixture
            )
        }
        .frame(width: size.width, height: size.height)
        .ignoresSafeArea()
        .environment(\.layoutDirection, .leftToRight)

        let host = UIHostingController(rootView: view)
        host.overrideUserInterfaceStyle = dark ? .dark : .light
        host.view.frame = CGRect(origin: .zero, size: size)

        let window = UIWindow(frame: CGRect(origin: .zero, size: size))
        window.overrideUserInterfaceStyle = dark ? .dark : .light
        window.rootViewController = host
        window.makeKeyAndVisible()
        window.layoutIfNeeded()
        host.view.setNeedsLayout()
        host.view.layoutIfNeeded()

        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        var drew = false
        let image = renderer.image { _ in
            drew = host.view.drawHierarchy(in: host.view.bounds, afterScreenUpdates: true)
        }
        windows.append(window)
        return drew ? image : nil
    }

    private static func luma(_ image: UIImage) throws -> [[Double]] {
        guard let cg = image.cgImage else { throw NSError(domain: "drawer", code: 1) }
        let w = cg.width, h = cg.height
        var pixels = [UInt8](repeating: 0, count: w * h * 4)
        guard let space = CGColorSpace(name: CGColorSpace.sRGB),
              let ctx = CGContext(data: &pixels, width: w, height: h, bitsPerComponent: 8,
                                  bytesPerRow: w * 4, space: space,
                                  bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
        else { throw NSError(domain: "drawer", code: 2) }
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        var out = [[Double]]()
        out.reserveCapacity(h)
        for y in stride(from: 0, to: h, by: 4) {
            var row = [Double]()
            row.reserveCapacity(w / 4)
            for x in stride(from: 0, to: w, by: 4) {
                let i = (y * w + x) * 4
                let r = Double(pixels[i]), g = Double(pixels[i + 1]), b = Double(pixels[i + 2])
                row.append(0.2126 * r + 0.7152 * g + 0.0722 * b)
            }
            out.append(row)
        }
        return out
    }

    @MainActor
    private func capture(size: CGSize, dark: Bool, name: String,
                         fixture: DrawerFixture = DrawerRenderTests.fixture) throws {
        var problems = ["the capture was never taken"]
        for _ in 1...3 {
            guard let image = try render(size: size, dark: dark) else {
                problems = ["drawHierarchy reported failure — nothing composited"]
                continue
            }
            let grey = try Self.luma(image)
            let h = grey.count, w = grey[0].count
            let panel = Double(w) * DrawerMetrics.panelRatio
            let strip = mean(grey, x: Int(panel) + 8, y: Int(Double(h) * 0.3),
                             w: w - Int(panel) - 14, h: Int(Double(h) * 0.5))
            let body = mean(grey, x: Int(Double(panel) * 0.03), y: Int(Double(h) * 0.3),
                            w: Int(Double(panel) * 0.05), h: Int(Double(h) * 0.5))
            let variance = grey.flatMap { $0 }.reduce(into: (0.0, 0.0, 0.0)) { acc, v in
                acc.0 += v; acc.1 += v * v; acc.2 += 1
            }
            let meanAll = variance.0 / max(variance.2, 1)
            let varAll = variance.1 / max(variance.2, 1) - meanAll * meanAll

            problems = []
            if varAll < 90 { problems.append("the frame is flat (variance \(Int(varAll)))") }
            if dark {
                if body > 90 { problems.append("dark capture has a light panel (\(Int(body)) L)") }
            } else {
                if body < 225 { problems.append("panel body reads \(Int(body)) L, expected cream") }
                if !(strip < body - 20) {
                    problems.append("the strip beside the panel is not dimmer than the panel "
                                    + "(\(Int(strip)) vs \(Int(body)) L) — the scrim did not composite")
                }
            }
            if problems.isEmpty { break }
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        }
        for problem in problems { XCTFail("drawer: \(problem)") }

        let dir = Self.outputDirectory()
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        guard let image = try render(size: size, dark: dark, fixture: fixture),
              let data = image.pngData() else {
            XCTFail("no PNG data")
            return
        }
        try data.write(to: dir.appendingPathComponent(name))
    }

    private func mean(_ grey: [[Double]], x: Int, y: Int, w: Int, h: Int) -> Double {
        var sum = 0.0, count = 0.0
        for row in max(0, y)..<min(grey.count, y + h) {
            for col in max(0, x)..<min(grey[row].count, x + w) {
                sum += grey[row][col]; count += 1
            }
        }
        return count > 0 ? sum / count : 0
    }

    @MainActor
    func testDrawerAtSheetSize() throws {
        try capture(size: CGSize(width: 393, height: 852), dark: false,
                    name: "drawer-ios-393x852.png")
    }

    @MainActor
    func testDrawerInDark() throws {
        try capture(size: CGSize(width: 393, height: 852), dark: true,
                    name: "drawer-ios-393x852-dark.png")
    }

    @MainActor
    func testDrawerAtNarrowWidth() throws {
        try capture(size: CGSize(width: 360, height: 780), dark: false,
                    name: "drawer-ios-360x780.png")
    }

    /// Five hundred conversations, captured so DR11 can compare this frame with
    /// the sheet-sized one. Android proved the fixture once bypassed the row
    /// limit and drew twelve bands; this is the same question asked of iOS, and
    /// the answer is checked rather than assumed.
    @MainActor
    func testDrawerWithFiveHundredConversations() throws {
        let many = DrawerFixture(
            name: "Arafat",
            workspace: "Personal workspace",
            initials: "A",
            recents: (0..<500).map { index in
                DrawerRecent(id: "m\(index)", title: "Conversation \(index + 1)",
                             timestamp: "Mon",
                             symbol: DrawerTopic.iconName(title: "Recent chat", pinned: false), pinned: false)
            }
        )
        try capture(size: CGSize(width: 393, height: 852), dark: false,
                    name: "drawer-ios-500chats-393x852.png", fixture: many)
    }
}
