import XCTest
import SwiftUI
import UIKit
@testable import GSApp

/// Captures the actual native response renderer with a controlled append.
/// The fixture is confined to tests. Frames and real elapsed capture times
/// are evidence for inspection, not a claim about live provider/device FPS.
@MainActor
final class FluidResponseRenderTests: XCTestCase {
    private final class Fixture: ObservableObject {
        @Published var text = "Already read.\n"
        @Published var live = true
    }

    private struct Host: View {
        @ObservedObject var fixture: Fixture
        var body: some View {
            FluidResponseText(AttributedString(fixture.text), ink: .black)
                .font(.system(size: 20))
                .foregroundStyle(Color.black)
                .frame(width: 320, height: 112, alignment: .topLeading)
                .environment(\.fluidResponseStreaming, fixture.live)
                .background(Color.white)
        }
    }

    func testNativeIncomingInkProducesMultipleFramesAndSettles() async throws {
        let model = Fixture()
        let size = CGSize(width: 320, height: 112)
        let host = UIHostingController(rootView: Host(fixture: model))
        let window = UIWindow(frame: CGRect(origin: .zero, size: size))
        let old = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }.first { $0.isKeyWindow }
        defer { window.isHidden = true; window.rootViewController = nil; old?.makeKeyAndVisible() }
        window.rootViewController = host
        window.makeKeyAndVisible()
        host.view.frame = CGRect(origin: .zero, size: size)
        try await Task.sleep(nanoseconds: 200_000_000)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 2
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        let dir = URL(fileURLWithPath: "/tmp/gs-nexly-renders", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        var timings: [[String: Any]] = []
        var captured: [Data] = []
        func frame(_ name: String, since start: TimeInterval) throws -> Data {
            var drew = false
            let image = renderer.image { _ in
                drew = host.view.drawHierarchy(in: CGRect(origin: .zero, size: size), afterScreenUpdates: true)
            }
            XCTAssertTrue(drew, "Native view must really composite")
            let data = try XCTUnwrap(image.pngData())
            try data.write(to: dir.appendingPathComponent("fluid-response-ios-\(name).png"))
            timings.append(["frame": name, "elapsedMs": (ProcessInfo.processInfo.systemUptime - start) * 1000])
            return data
        }
        let start = ProcessInfo.processInfo.systemUptime
        model.text += "New words flow into place."
        for tick in 0..<5 {
            try await Task.sleep(nanoseconds: 25_000_000)
            captured.append(try frame("live-\(tick)", since: start))
        }
        try await Task.sleep(nanoseconds: 420_000_000)
        let settled = try frame("settled", since: start)
        model.live = false
        try await Task.sleep(nanoseconds: 80_000_000)
        let final = try frame("final", since: start)
        try JSONSerialization.data(withJSONObject: timings, options: .prettyPrinted)
            .write(to: dir.appendingPathComponent("fluid-response-ios-timings.json"))
        XCTAssertGreaterThanOrEqual(Set(captured + [settled]).count, 3, "Static or one-step text cannot pass")
        XCTAssertEqual(settled, final, "Completed text must not flash or reanimate")
    }
}
