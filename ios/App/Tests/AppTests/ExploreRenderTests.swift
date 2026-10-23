import XCTest
import SwiftUI
import UIKit
@testable import GSApp

/**
 * The REAL Explore AI page rendered on the simulator: hero, chips, search,
 * Featured Tools grid, Popular rows, Collections, Featured journey, composer
 * — over the same pearl backdrop Home mounts. No mockup.
 *
 * The references are the Explore screenshots in docs/reference/explore; the
 * PNGs are the evidence.
 */
@MainActor
final class ExploreRenderTests: XCTestCase {

    private static let scale: CGFloat = 3

    func testExploreRenders() async throws {
        let dir = Self.outputDirectory()
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)

        let targets: [(CGFloat, CGFloat, String)] = [
            (393, 852, "393x852"),
            (411, 914, "411x914"),
            (360, 780, "360x780"),
            (393, 1400, "393x1400"),
        ]
        for target in targets {
            let size = CGSize(width: target.0, height: target.1)
            let view = ExploreView(onOpenChat: { _ in })
                .frame(width: size.width, height: size.height)
                .ignoresSafeArea()
                .environment(\.layoutDirection, .leftToRight)
            guard let image = try await Self.capture(view: view, size: size) else {
                XCTFail("explore did not composite at \(target.2) — nothing was drawn")
                continue
            }
            guard let data = image.pngData() else {
                XCTFail("no PNG data at \(target.2)")
                continue
            }
            XCTAssertGreaterThan(
                data.count, 20_000,
                "\(target.2) is suspiciously small for a full Explore render"
            )
            let url = dir.appendingPathComponent("nexly-explore-ios-\(target.2).png")
            FileManager.default.createFile(atPath: url.path, contents: data)
            XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
        }
    }

    func testExploreSearchStateRenders() async throws {
        let dir = Self.outputDirectory()
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let size = CGSize(width: 393, height: 852)
        let view = ExploreViewSearchHarness()
            .frame(width: size.width, height: size.height)
            .ignoresSafeArea()
            .environment(\.layoutDirection, .leftToRight)
        guard let image = try await Self.capture(view: view, size: size) else {
            XCTFail("explore search state did not composite")
            return
        }
        guard let data = image.pngData() else {
            XCTFail("no PNG data for the search state")
            return
        }
        let url = dir.appendingPathComponent("nexly-explore-ios-search-393x852.png")
        FileManager.default.createFile(atPath: url.path, contents: data)
        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
    }

    // MARK: helpers (same windowed drawHierarchy channel as the chat harness)

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
            drew = host.view.drawHierarchy(in: CGRect(origin: .zero, size: size),
                                           afterScreenUpdates: true)
        }
        return drew ? image : nil
    }
}

/// The live search state: field open, a real query filtering the real catalog,
/// driven through the same init parameters the Android harness uses.
struct ExploreViewSearchHarness: View {
    var body: some View {
        ExploreView(onOpenChat: { _ in }, initialQuery: "study", initialSearchOpen: true)
    }
}
