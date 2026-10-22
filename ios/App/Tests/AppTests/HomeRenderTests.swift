import XCTest
import SwiftUI
import UIKit
@testable import GSApp

/**
 * iOS Home render harness — the pixel evidence this page has never had.
 *
 * Every real defect found on Home so far (ghost text bleeding through glass,
 * the truncated Mind Guide, the chevron grazing the title, copy shoved into
 * the wrong corner) was invisible to code review and to static gates, and only
 * appeared once something actually DREW the page. Android got that ability via
 * Roborazzi; iOS had none, so three of Wave 0's four criticals shipped green.
 *
 * This renders the real `HomeDashboardView` into a real UIWindow on the
 * simulator and writes a PNG per device width.
 *
 * WHY IT ALSO ASSERTS, INSTEAD OF JUST EMITTING
 * ---------------------------------------------
 * A snapshot that silently comes out blank, or comes out with the glass
 * missing because `UIVisualEffectView` never composited, is WORSE than no
 * snapshot: it looks like evidence. So every render is checked for the things
 * that would prove it is a lie before the PNG is trusted — see
 * `assertRenderIsReal`. The precise geometry and material deltas are measured
 * against the reference by scripts/check_home_render.py in CI, not here.
 *
 * OUTPUT
 * ------
 * `GS_RENDER_DIR` (set by .github/workflows/ios.yml) or the temp directory.
 * Files: home-ios-393x852.png, home-ios-411x914.png, home-ios-360x780.png.
 */
@MainActor
final class HomeRenderTests: XCTestCase {

    /// Point sizes matching Android's HomeScreenshotTest widths.
    private static let targets: [(width: CGFloat, height: CGFloat, name: String)] = [
        (393, 852, "393x852"),
        (411, 914, "411x914"),
        (360, 780, "360x780"),
    ]

    private static let scale: CGFloat = 3

    func testHomeRendersAtEveryWidth() async throws {
        // The reference greets a person; pin the identity so the hero renders
        // the same words the sheet shows instead of the "there" fallback.
        AccountStore.shared.setDisplayName("Arafat")
        // Determinism: the greeting follows the wall clock and every type role
        // follows the Settings font-scale multiplier, so a capture taken at
        // 14:00 with the slider at 1.2 is not the same image as one taken at
        // 09:00 at 1.0. Freezing both is what makes the artifact diffable.
        GSAccessibilityFlags.shared.fontScale = 1.0
        let hour = 9

        let dir = Self.outputDirectory()
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)

        for target in Self.targets {
            let size = CGSize(width: target.width, height: target.height)
            guard let image = try await Self.renderHome(size: size, greetingHour: hour) else {
                XCTFail("drawHierarchy reported failure at \(target.name) — nothing was composited")
                continue
            }

            let pixels = try Self.grayscale(image)
            assertRenderIsReal(image: image, gray: pixels, label: target.name)

            let url = dir.appendingPathComponent("home-ios-\(target.name).png")
            guard let data = image.pngData() else {
                XCTFail("no PNG data at \(target.name)"); continue
            }
            FileManager.default.createFile(atPath: url.path, contents: data)
            XCTAssertGreaterThan(data.count, 20_000,
                                 "\(url.lastPathComponent) is suspiciously small for a full-page render")
        }

        for target in Self.targets {
            let url = dir.appendingPathComponent("home-ios-\(target.name).png")
            XCTAssertTrue(FileManager.default.fileExists(atPath: url.path),
                          "expected \(url.lastPathComponent) on disk for CI to archive")
        }
    }

    // MARK: - Rendering

    /// Hosts the real view in a key, visible window and captures the
    /// COMPOSITED layer tree.
    ///
    /// `drawHierarchy(afterScreenUpdates: true)` rather than SwiftUI's
    /// `ImageRenderer`: ImageRenderer does not rasterise `Material`
    /// (UIVisualEffectView), so an ImageRenderer snapshot of this page would
    /// show every glass surface as empty and look perfectly fine while proving
    /// nothing. Drawing the live window goes through the same compositing path
    /// the device uses.
    private static func renderHome(size: CGSize, greetingHour: Int) async throws -> UIImage? {
        let view = HomeDashboardView(
            onNewChat: {},
            onOpenDrawer: {},
            onOpenChats: {},
            onOpenAgent: {},
            onOpenConversation: { _ in },
            onStartPrompt: { _ in },
            greetingHour: greetingHour
        )
        .frame(width: size.width, height: size.height)
        .environment(\.layoutDirection, .leftToRight)

        let host = UIHostingController(rootView: view)
        host.view.frame = CGRect(origin: .zero, size: size)

        let window = UIWindow(frame: CGRect(origin: .zero, size: size))
        window.rootViewController = host
        window.layoutIfNeeded()
        window.makeKeyAndVisible()

        // SwiftUI lays out and the render server composites asynchronously;
        // without settling frames the capture can land before the glass does.
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
        // A workflow env var does NOT reach here: xcodebuild launches the test
        // host through the simulator with its own environment, so a
        // GS_RENDER_DIR set on the CI step is invisible to the process and the
        // first version of this harness silently wrote its PNGs into the
        // simulator container's tmp dir and CI found nothing.
        //
        // Simulator processes are not sandboxed and share the host filesystem,
        // so /tmp is the same directory on both sides of the boundary — the one
        // location the test can write to that the job can then read from
        // without plumbing an environment variable through Xcode.
        if let custom = ProcessInfo.processInfo.environment["GS_RENDER_DIR"], !custom.isEmpty {
            return URL(fileURLWithPath: custom, isDirectory: true)
        }
        return URL(fileURLWithPath: "/tmp/gs-home-renders", isDirectory: true)
    }

    // MARK: - Pixel helpers

    /// Luminance per point-sized cell, sampled at the capture's own scale.
    private static func grayscale(_ image: UIImage) throws -> [[Double]] {
        guard let cg = image.cgImage else { throw RenderError.noImage }
        let w = cg.width, h = cg.height
        var buf = [UInt8](repeating: 0, count: w * h * 4)
        let space = CGColorSpaceCreateDeviceRGB()
        guard let ctx = CGContext(data: &buf, width: w, height: h, bitsPerComponent: 8,
                                  bytesPerRow: w * 4, space: space,
                                  bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
        else { throw RenderError.noImage }
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))

        var rows = [[Double]](repeating: [Double](repeating: 0, count: w), count: h)
        for y in 0..<h {
            for x in 0..<w {
                let i = (y * w + x) * 4
                rows[y][x] = 0.2126 * Double(buf[i]) + 0.7152 * Double(buf[i + 1]) + 0.0722 * Double(buf[i + 2])
            }
        }
        return rows
    }

    /// Mean luminance over a rectangle expressed in POINTS (top-left origin).
    private func meanLuma(_ gray: [[Double]], x: CGFloat, y: CGFloat, w: CGFloat, h: CGFloat) -> Double {
        let s = HomeRenderTests.scale
        let x0 = Int(x * s), y0 = Int(y * s)
        let x1 = min(gray[0].count, Int((x + w) * s))
        let y1 = min(gray.count, Int((y + h) * s))
        var total = 0.0, n = 0
        for row in y0..<y1 {
            for col in x0..<x1 { total += gray[row][col]; n += 1 }
        }
        return n == 0 ? 0 : total / Double(n)
    }

    // MARK: - Self-validation

    /**
     * Fail loudly if the capture is not a real render of this page.
     *
     * Each probe is a thing that would be TRUE of a blank, transparent or
     * partially-composited image, so a broken harness cannot pass:
     *   1. the frame has real tonal range (a blank canvas does not);
     *   2. the backdrop's hero pearl decoded (upper right is warm and
     *      saturated — a missing asset is flat cream);
     *   3. the dock's peach Generate pill composited (the single most
     *      saturated object on the page);
     *   4. the bento glass is distinguishable from the page behind it, which
     *      is exactly what a non-rasterising Material would destroy.
     */
    private func assertRenderIsReal(image: UIImage, gray: [[Double]], label: String) {
        let size = image.size
        // Hoisted out of the assertion: a nested closure plus force-unwrap plus
        // interpolation inside a macro argument is what made the compiler give
        // up on this file the first time.
        let expectedWidth = Self.targets.first(where: { $0.name == label })?.width ?? 0
        XCTAssertEqual(Int(size.width), Int(expectedWidth), "capture width for \(label)")

        // Mean and variance in one explicit pass. The obvious
        // `gray.flatMap { $0 }.reduce(0) { ... }` form makes the compiler
        // time out — it cannot infer the accumulator type through the closure
        // and the string interpolation at the call site — and it also builds two
        // three-million-element arrays for a 1179x2556 capture.
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for row in gray {
            for v in row {
                sum += v
                sumSq += v * v
                n += 1
            }
        }
        let count = Double(n)
        let flat = sum / count
        let variance = max(0.0, sumSq / count - flat * flat)
        XCTAssertGreaterThan(variance, 90,
                             "\(label): frame is near-uniform (variance \(Int(variance))) — render is blank")

        // 2. hero pearl, upper right of the page.
        let pearl = meanLuma(gray, x: size.width * 0.78, y: size.height * 0.20,
                             w: size.width * 0.16, h: size.height * 0.10)
        let pageLeft = meanLuma(gray, x: size.width * 0.06, y: size.height * 0.20,
                                w: size.width * 0.16, h: size.height * 0.10)
        XCTAssertNotEqual(pearl, pageLeft, accuracy: 2.0,
                          "\(label): hero-pearl region matches the page — the backdrop art did not decode")

        // 3. the peach Generate pill sits in the dock, centred-right.
        let dockY = size.height - 44
        let pill = meanLuma(gray, x: size.width * 0.55, y: dockY - 12, w: size.width * 0.18, h: 24)
        let dockEdge = meanLuma(gray, x: size.width * 0.06, y: dockY - 12, w: size.width * 0.10, h: 24)
        XCTAssertLessThan(pill, dockEdge,
                          "\(label): Generate pill is not darker/warmer than the dock beside it — dock did not composite")

        // 4. bento glass vs the page it sits on, at the same height.
        let bentoY = size.height * 0.55
        let card = meanLuma(gray, x: size.width * 0.30, y: bentoY, w: size.width * 0.18, h: 20)
        let margin = meanLuma(gray, x: size.width * 0.02, y: bentoY, w: size.width * 0.05, h: 20)
        XCTAssertNotEqual(card, margin, accuracy: 1.2,
                          "\(label): card interior equals the page beside it — the glass is not rasterising")
    }

    private enum RenderError: Error { case noImage }
}
