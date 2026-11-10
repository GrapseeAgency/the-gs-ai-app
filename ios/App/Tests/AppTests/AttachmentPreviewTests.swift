import XCTest
import UIKit
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
        }
    }

    func testUnreadableFileCannotBecomeInventedImagePreview() async {
        let missing = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".png")
        let loader = ThumbLoader(url: missing)
        await loader.loadIfNeeded()
        XCTAssertNil(loader.image)
    }
}
