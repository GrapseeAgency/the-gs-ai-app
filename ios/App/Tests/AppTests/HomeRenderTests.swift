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
 * /tmp/gs-home-renders, or `GS_RENDER_DIR` if a caller sets it. Note that a
 * workflow *step*-level env var never reaches the simulator-launched test
 * host, which is why the default is a shared absolute path rather than an
 * injected one — see `outputDirectory()`.
 * Files: home-ios-393x852.png, home-ios-411x914.png, home-ios-360x780.png.
 */
@MainActor
final class HomeRenderTests: XCTestCase {

    /// The identity the sheet greets. Restored after the run — see `tearDown`.
    private static let fixtureName = "Arafat"

    private var priorDisplayName = ""
    private var priorFontScale: CGFloat = 1
    private var windows: [UIWindow] = []

    override func setUp() async throws {
        // Snapshot before mutating. This test writes a persisted display name
        // and a process-global font scale; without restoring them the next test
        // class — and a developer's simulator — inherits "Arafat" and a pinned
        // 1.0 scale that disagrees with whatever SettingsStore persisted.
        priorDisplayName = AccountStore.shared.displayName
        priorFontScale = GSAccessibilityFlags.shared.fontScale
    }

    override func tearDown() async throws {
        AccountStore.shared.setDisplayName(priorDisplayName)
        GSAccessibilityFlags.shared.fontScale = priorFontScale
        // Release every key window this test made visible, or they stay in the
        // process holding live @ObservedObject graphs that react to later
        // store writes.
        for window in windows {
            window.rootViewController = nil
            window.isHidden = true
        }
        windows.removeAll()
    }

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
        // `tearDown` puts back whatever was there before.
        AccountStore.shared.setDisplayName(Self.fixtureName)
        // Determinism: the greeting follows the wall clock and every type role
        // follows the Settings font-scale multiplier, so a capture taken at
        // 14:00 with the slider at 1.2 is not the same image as one taken at
        // 09:00 at 1.0. Freezing both is what makes the artifact diffable.
        GSAccessibilityFlags.shared.fontScale = 1.0
        let hour = 9

        // The resume card appears above the greeting and pushes everything
        // below it down. The gate's probe rects assume it is absent, so a
        // capture taken after some other test seeded the store would be
        // silently mis-registered. Say so instead of shipping a shifted frame.
        XCTAssertTrue(ConversationStore.shared.activeConversations.isEmpty,
                      "ConversationStore must be empty for the probe rects to land "
                      + "on the elements they were measured against")

        // Typography asserted, not assumed. `Font.custom` falls back to the
        // system font SILENTLY when a name does not resolve, so a bundle that
        // lost its fonts would keep producing plausible captures and a green
        // gate forever. Android's Type.kt sets Poppins on all sixteen roles;
        // this is the line that proves iOS does too.
        XCTAssertTrue(Aero.poppinsLoaded,
                      "Poppins is not registered, so iOS is rendering SF Pro. Check "
                      + "UIAppFonts in ios/project.yml and ios/App/Resources/Fonts/.")

        let dir = Self.outputDirectory()
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)

        for target in Self.targets {
            let size = CGSize(width: target.width, height: target.height)
            // The window comes back as a value rather than being appended
            // through `inout`: Swift forbids passing an actor-isolated stored
            // property inout across an async call, and the capture must stay
            // alive until the PNG is written.
            var problems = ["never captured"]
            var image = UIImage()
            // Up to three captures. A half-composited page is a timing failure,
            // not a design verdict, and reporting one as a red is how a gate gets
            // ignored; re-capturing is also the only way to tell a slow runner from
            // a genuinely broken render.
            for _ in 1...3 {
                guard let capture = try await Self.renderHome(size: size, greetingHour: hour) else {
                    problems = ["drawHierarchy reported failure — nothing was composited"]
                    continue
                }
                windows.append(capture.window)
                image = capture.image
                let pixels = try Self.grayscale(image)
                problems = Self.renderProblems(image: image, gray: pixels, label: target.name)
                if problems.isEmpty { break }
                try? await Task.sleep(nanoseconds: 250_000_000)
            }
            for problem in problems { XCTFail("\(target.name): \(problem)") }

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
    private static func renderHome(size: CGSize, greetingHour: Int)
        async throws -> (image: UIImage, window: UIWindow)? {
        let view = HomeDashboardView(
            onNewChat: {},
            onOpenDrawer: {},
            onOpenChats: {},
            onOpenAgent: {},
            onOpenConversation: { _ in },
            onStartPrompt: { _ in },
            onOpenVoice: {},
            onOpenSettings: {},
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
        // Returned to the caller so tearDown can dismantle it; a key window
        // left visible keeps its SwiftUI graph alive and subscribed for the
        // rest of the run.

        // A short settle, then the caller verifies the pixels and re-captures if
        // they are not there yet. The old loop slept 20 x 16 ms, which caps the
        // wait at ~320 ms no matter what the machine is doing: on a loaded runner
        // that is a red that no code change caused, and on an idle one it is 320 ms
        // thrown away per size. Three frames gets the layer tree committed; the
        // retry is what actually absorbs the variance.

        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        var drew = false
        let image = renderer.image { _ in
            drew = host.view.drawHierarchy(in: CGRect(origin: .zero, size: size),
                                           afterScreenUpdates: true)
        }
        // Return the sRGB re-encode, not the renderer's own bitmap: see inSRGB.
        return drew ? (try inSRGB(image), window) : nil
    }

    /// Redraw the capture into an sRGB bitmap, so the PNG written from it is the
    /// same kind of file as the two it is compared against.
    ///
    /// The renderer follows the simulator's screen, which is Display P3, and
    /// `pngData()` carries that profile out as iCCP/cICP chunks. Android's
    /// screenshot PNG and the reference sheet are untagged sRGB, and PIL does no
    /// colour management — so the gate applied sRGB Rec.709 weights to
    /// P3-encoded values. That error has a signature, and the data had it:
    /// neutrals agreed to 0.15 L while the saturated bloom came in +2.2 L bright,
    /// which is where the bento card's ground is read from and enough to put the
    /// glass outside its own band.
    private static func inSRGB(_ image: UIImage) throws -> UIImage {
        guard let cg = image.cgImage,
              let space = CGColorSpace(name: CGColorSpace.sRGB),
              let ctx = CGContext(data: nil, width: cg.width, height: cg.height,
                                  bitsPerComponent: 8, bytesPerRow: cg.width * 4,
                                  space: space,
                                  bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
        else { throw RenderError.noImage }
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: cg.width, height: cg.height))
        guard let out = ctx.makeImage() else { throw RenderError.noImage }
        return UIImage(cgImage: out, scale: image.scale, orientation: .up)
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
        // GS_RENDER_DIR is the developer's escape hatch, not a CI channel: run
        // the harness from Xcode's scheme editor, where environment variables DO
        // reach the process, and the PNGs land wherever you ask. No workflow sets
        // it — the line above is why one could not.
        if let custom = ProcessInfo.processInfo.environment["GS_RENDER_DIR"], !custom.isEmpty {
            return URL(fileURLWithPath: custom, isDirectory: true)
        }
        return URL(fileURLWithPath: "/tmp/gs-home-renders", isDirectory: true)
    }

    // MARK: - Pixel helpers

    /// Luminance per point-sized cell, sampled at the capture's own scale.
    private static func grayscale(_ image: UIImage) throws -> [[Double]] {
        guard let cg = image.cgImage,
              // DeviceRGB is the screen's space, which on a P3 simulator is not
              // the sRGB the weights below assume. Name the space the arithmetic
              // is for; renderHome already hands this a re-encoded bitmap, so a
              // mismatch here would be the harness lying to its own assertions.
              let space = CGColorSpace(name: CGColorSpace.sRGB)
        else { throw RenderError.noImage }
        let w = cg.width, h = cg.height
        var buf = [UInt8](repeating: 0, count: w * h * 4)
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
    private static func meanLuma(_ gray: [[Double]], x: CGFloat, y: CGFloat, w: CGFloat, h: CGFloat) -> Double {
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
    /// What is wrong with this capture. Empty means it is a real render.
    /// The label is the CALLER's job (it prefixes every XCTFail), so a problem
    /// string here never repeats the size it came from.
    private static func renderProblems(image: UIImage, gray: [[Double]], label: String) -> [String] {
        var problems: [String] = []
        let size = image.size
        _ = label  // kept in the signature for the width check below
        let expectedWidth = targets.first(where: { $0.name == label })?.width ?? 0
        if Int(size.width) != Int(expectedWidth) {
            problems.append("capture width \(Int(size.width)) != expected \(Int(expectedWidth))")
        }

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
        if variance <= 90 {
            problems.append("frame is near-uniform (variance \(Int(variance))) — render is blank")
        }

        // 2. hero pearl, upper right of the page.
        let pearl = meanLuma(gray, x: size.width * 0.78, y: size.height * 0.20,
                             w: size.width * 0.16, h: size.height * 0.10)
        let pageLeft = meanLuma(gray, x: size.width * 0.06, y: size.height * 0.20,
                                w: size.width * 0.16, h: size.height * 0.10)
        if abs(pearl - pageLeft) < 2.0 {
            problems.append("hero-pearl region matches the page — the backdrop art did not decode")
        }

        // 3. the peach Generate pill sits in the dock, centred-right.
        let dockY = size.height - 44
        let pill = meanLuma(gray, x: size.width * 0.55, y: dockY - 12, w: size.width * 0.18, h: 24)
        let dockEdge = meanLuma(gray, x: size.width * 0.06, y: dockY - 12, w: size.width * 0.10, h: 24)
        if pill >= dockEdge {
            problems.append("Generate pill is not darker/warmer than the dock beside it "
                            + "— dock did not composite")
        }

        // 4. bento glass vs the page it sits on, at the same height.
        let bentoY = size.height * 0.55
        let card = meanLuma(gray, x: size.width * 0.30, y: bentoY, w: size.width * 0.18, h: 20)
        let margin = meanLuma(gray, x: size.width * 0.02, y: bentoY, w: size.width * 0.05, h: 20)
        if abs(card - margin) < 1.2 {
            problems.append("card interior equals the page beside it — the glass is not rasterising")
        }
        return problems
    }

    private enum RenderError: Error { case noImage }
}
