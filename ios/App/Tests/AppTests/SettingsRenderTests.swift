import XCTest
import SwiftUI
import UIKit
@testable import GSApp

/**
 * The three Settings screens rendered on the simulator — the PNGs are the
 * evidence, compared against the three mocks under docs/reference/settings. Same windowed
 * drawHierarchy channel as the Explore/Home harnesses.
 */
@MainActor
final class SettingsRenderTests: XCTestCase {

    private static let scale: CGFloat = 3

    func testSettingsHubRenders() async throws {
        try await capturePage(
            view: { SettingsView(onRoute: { _ in }) },
            prefix: "nexly-settings-ios",
            targets: [(393, 852, "393x852"), (360, 780, "360x780")]
        )
    }

    func testProfilePreferencesRenders() async throws {
        try await capturePage(
            view: { ProfilePreferencesView() },
            prefix: "nexly-profile-ios",
            targets: [(393, 852, "393x852"), (360, 780, "360x780")]
        )
    }

    func testMemoryManagementRenders() async throws {
        try await capturePage(
            view: { MemoryManagementView() },
            prefix: "nexly-memory-ios",
            targets: [(393, 852, "393x852"), (360, 780, "360x780")]
        )
    }

    // MARK: helpers (the Explore harness's channel, verbatim)

    private func capturePage(
        view: @escaping () -> some View,
        prefix: String,
        targets: [(CGFloat, CGFloat, String)]
    ) async throws {
        let dir = Self.outputDirectory()
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        for target in targets {
            let size = CGSize(width: target.0, height: target.1)
            let v = view()
                .frame(width: size.width, height: size.height)
                .ignoresSafeArea()
                .environment(\.layoutDirection, .leftToRight)
            guard let image = try await Self.capture(view: v, size: size) else {
                XCTFail("\(prefix) did not composite at \(target.2) — nothing was drawn")
                continue
            }
            guard let data = image.pngData() else {
                XCTFail("no PNG data at \(target.2)")
                continue
            }
            XCTAssertGreaterThan(
                data.count, 20_000,
                "\(target.2) is suspiciously small for a full render"
            )
            let url = dir.appendingPathComponent("\(prefix)-\(target.2).png")
            FileManager.default.createFile(atPath: url.path, contents: data)
            XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
        }
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
            drew = host.view.drawHierarchy(in: CGRect(origin: .zero, size: size),
                                           afterScreenUpdates: true)
        }
        return drew ? image : nil
    }
}
