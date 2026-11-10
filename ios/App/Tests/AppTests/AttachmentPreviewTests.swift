import XCTest
import UIKit
import SwiftUI
@testable import GSApp

/// Actual file decoders, not icon placeholders or simulated preview payloads.
@MainActor
final class AttachmentPreviewTests: XCTestCase {
    func testPhotoPDFAndTextProduceBoundedPreviewsFromActualBytes() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let photo = directory.appendingPathComponent("photo.png")
        let image = UIGraphicsImageRenderer(size: CGSize(width: 1200, height: 800)).image { context in
            UIColor.systemBlue.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 1200, height: 800))
        }
        try XCTUnwrap(image.pngData()).write(to: photo)
        let pdf = directory.appendingPathComponent("source.pdf")
        let pdfBytes = UIGraphicsPDFRenderer(bounds: CGRect(x: 0, y: 0, width: 612, height: 792)).pdfData { context in
            context.beginPage()
            ("Actual PDF attachment source" as NSString).draw(at: CGPoint(x: 40, y: 40),
                withAttributes: [.font: UIFont.systemFont(ofSize: 24)])
        }
        try pdfBytes.write(to: pdf)
        let text = directory.appendingPathComponent("notes.md")
        try Data("Actual source text, not a generated file summary.\nSecond source line.".utf8).write(to: text)
        for file in [photo, pdf, text] {
            let loader = ThumbLoader(url: file)
            await loader.loadIfNeeded()
            let preview = try XCTUnwrap(loader.image, "No actual decoder preview for \(file.lastPathComponent)")
            XCTAssertGreaterThan(preview.size.width, 0)
            XCTAssertGreaterThan(preview.size.height, 0)
            XCTAssertLessThanOrEqual(max(preview.size.width, preview.size.height), 512)
            let mime = try XCTUnwrap(AttachmentRules.mimeForFileName(file.lastPathComponent))
            let draft = AttachmentDraft(id: file.lastPathComponent, kind: try XCTUnwrap(AttachmentRules.kindForMime(mime)),
                source: .files, displayName: file.lastPathComponent, mimeType: mime, phase: .ready,
                localPath: file.path, byteSize: try Data(contentsOf: file).count)
            try await captureCard(draft)
        }
    }

    func testUnreadableFileCannotBecomeInventedImagePreview() async {
        let missing = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".png")
        let loader = ThumbLoader(url: missing)
        await loader.loadIfNeeded()
        XCTAssertNil(loader.image)
    }
    /// Capture the actual composer card after the real decoder runs, not a mockup.
    private func captureCard(_ draft: AttachmentDraft) async throws {
        let size = CGSize(width: 320, height: 340)
        let host = UIHostingController(rootView: AttachmentChipRow(drafts: [draft], onRetry: { _ in }, onRemove: { _ in }))
        host.view.frame = CGRect(origin: .zero, size: size)
        let previous = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }.first { $0.isKeyWindow }
        let window = UIWindow(frame: host.view.frame)
        defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKeyAndVisible() }
        window.rootViewController = host
        window.makeKeyAndVisible()
        try await Task.sleep(nanoseconds: 350_000_000)
        host.view.layoutIfNeeded()
        var drew = false
        let image = UIGraphicsImageRenderer(size: size).image { _ in
            drew = host.view.drawHierarchy(in: host.view.bounds, afterScreenUpdates: true)
        }
        XCTAssertTrue(drew, "Actual attachment card did not composite")
        XCTAssertGreaterThan(try XCTUnwrap(image.pngData()).count, 2000)
        let capture = XCTAttachment(image: image)
        capture.name = "native-attachment-card-\(draft.displayName)"
        capture.lifetime = .keepAlways
        add(capture)
    }

}
