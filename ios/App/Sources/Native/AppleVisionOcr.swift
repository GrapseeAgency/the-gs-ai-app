import Foundation
import Vision
import CoreImage
import UIKit

/// OCR on iOS, with Apple Vision and no third-party engine.
///
/// The Android decision is to bundle ML Kit plus a Tesseract fallback, because
/// roughly 1% of Android devices have no Google Play Services and therefore no
/// ML Kit. iOS has no equivalent gap: `Vision` ships in the OS, is present on
/// every iPhone since iOS 13, and is what the platform is tuned for on Apple
/// silicon. So there is no fallback to bundle and no Google dependency, and
/// adding one would be a strictly worse version of the same function.
///
/// The public shape matches `GsNative.runOcr` exactly, so the caller cannot tell
/// which engine ran — which is the same contract the Android ML Kit / Tesseract
/// pair has to honour.
///
/// ## Why Vision and not a bundled Tesseract
///
/// Tesseract on iOS is a ~15 MB static library, needs traineddata shipped as an
/// asset, and is measurably slower on Apple silicon than the framework Apple
/// wrote for the same silicon. There is no device class on iOS that Vision
/// fails to cover, so there is no case for the fallback that the Android side
/// genuinely has.
public enum AppleVisionOcr {

    public enum OcrError: Error, CustomStringConvertible {
        case unreadableImage(String)
        case noTextFound
        case visionFailed(String)

        public var description: String {
            switch self {
            case .unreadableImage(let d): return "could not read the image: \(d)"
            case .noTextFound: return "no text was found in the image"
            case .visionFailed(let d): return "Vision failed: \(d)"
            }
        }
    }

    /// Recognise text in an image file.
    ///
    /// Throws rather than returning an empty string: a caller given "" cannot
    /// distinguish "the page has no text" from "the engine is broken", and will
    /// render a blank bubble instead of falling back.
    public static func recogniseText(atPath path: String) throws -> String {
        guard let ciImage = CIImage(contentsOf: URL(fileURLWithPath: path)) else {
            throw OcrError.unreadableImage(path)
        }
        return try recogniseText(in: ciImage)
    }

    /// Recognise text in an already-decoded image.
    public static func recogniseText(in ciImage: CIImage) throws -> String {
        let request = VNRecognizeTextRequest()
        // .accurate is the one that matters here. The fast path drops small text
        // and is tuned for live camera frames, not for a photographed invoice.
        request.recognitionLevel = .accurate
        // Language correction assumes a dictionary and makes short strings WORSE
        // -- it "corrects" a SKUs into words. Off for document-style images.
        request.usesLanguageCorrection = false
        // Both scripts on by default; a receipt may contain either.
        request.recognitionLanguages = ["en-US"]

        let handler = VNImageRequestHandler(ciImage: ciImage, options: [:])
        do {
            try handler.perform([request])
        } catch {
            throw OcrError.visionFailed("\(error)")
        }

        guard let observations = request.results, !observations.isEmpty else {
            throw OcrError.noTextFound
        }

        // Vision returns observations in no guaranteed order and, within one,
        // arbitrary top-to-bottom. Sorting by bounding box is what turns a bag
        // of words into a document, and getting it wrong produces text that
        // reads as scrambled rather than as wrong.
        let lines = observations.compactMap { obs -> (box: CGRect, text: String)? in
            guard let candidate = obs.topCandidates(1).first else { return nil }
            return (obs.boundingBox, candidate.string)
        }
        let sorted = lines.sorted { a, b in
            // Vision's origin is bottom-left, so a LARGER midY is HIGHER up.
            let dy = abs(a.box.midY - b.box.midY)
            if dy > 0.012 { return a.box.midY > b.box.midY }
            return a.box.minX < b.box.minX
        }
        let text = sorted.map(\.text).joined(separator: "\n")
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw OcrError.noTextFound
        }
        return text
    }

    /// Recognise text from raw image bytes, for callers that already have the
    /// data rather than a path. The attachment pipeline has exactly that.
    public static func recogniseText(data: Data) throws -> String {
        guard let ciImage = CIImage(data: data) else {
            throw OcrError.unreadableImage("\(data.count) bytes")
        }
        return try recogniseText(in: ciImage)
    }
}
