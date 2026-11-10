import SwiftUI
import UIKit
import ImageIO
import PDFKit
import AVFoundation
import QuickLook

// MARK: - PHASE 5 composer + transcript attachment UI (docs/ATTACHMENTS.md §6-§8)
//
// Additive to the composer architecture: a chips row ABOVE AeroInputBar inside
// the existing composerZone, and read-only chips on user bubbles in the
// transcript. Chrome stays monochrome (Aero tokens only); a real decoded
// thumbnail of USER content may carry colour — it is content, not chrome.
// No fabricated progress: preparing/uploading show an indeterminate spinner;
// there is no "processing" state anywhere (the server has none).

// MARK: - Thumbnail loader (spec §8: decode scaled ≈128px target, off-main,
// small NSCache, cancellation on chip removal)

@MainActor
final class ThumbLoader: ObservableObject {

    @Published private(set) var image: UIImage?

    private static let cache: NSCache<NSString, UIImage> = {
        let cache = NSCache<NSString, UIImage>()
        cache.totalCostLimit = 24 * 1024 * 1024
        cache.countLimit = 96
        return cache
    }()
    private let url: URL

    init(url: URL) {
        self.url = url
        if let cached = Self.cache.object(forKey: url.path as NSString) {
            image = cached
        }
    }

    /// SwiftUI calls this from `.task(id:)` — cancelling the task (chip
    /// removed, row recycled) cancels the publish, not the decode.
    func loadIfNeeded() async {
        if image != nil { return }
        let decoded = await Self.decode(from: url)
        guard !Task.isCancelled else { return }
        guard let decoded else { return }
        Self.cache.setObject(decoded, forKey: url.path as NSString,
                             cost: Int(decoded.size.width * decoded.size.height * 4))
        image = decoded
    }

    /// Scaled thumbnail decode (~256px max pixel) via ImageIO — never a
    /// full-size allocation, never on the main thread.
    private nonisolated static func decode(from url: URL) async -> UIImage? {
        await Task.detached(priority: .utility) { () -> UIImage? in
            let ext = url.pathExtension.lowercased()
            if ext == "pdf" {
                return PDFDocument(url: url)?.page(at: 0)?.thumbnail(of: CGSize(width: 256, height: 320), for: .mediaBox)
            }
            if ["mp4", "m4v", "mov", "webm"].contains(ext) {
                let generator = AVAssetImageGenerator(asset: AVURLAsset(url: url))
                generator.appliesPreferredTrackTransform = true
                generator.maximumSize = CGSize(width: 320, height: 320)
                guard let frame = try? generator.copyCGImage(at: .zero, actualTime: nil) else { return nil }
                return UIImage(cgImage: frame)
            }
            if ["txt", "text", "md", "markdown", "csv"].contains(ext) {
                guard let handle = try? FileHandle(forReadingFrom: url) else { return nil }
                defer { try? handle.close() }
                guard let data = try? handle.read(upToCount: 2048),
                      let text = String(data: data, encoding: .utf8), !text.isEmpty else { return nil }
                return UIGraphicsImageRenderer(size: CGSize(width: 256, height: 320)).image { context in
                    UIColor.white.setFill()
                    context.fill(CGRect(x: 0, y: 0, width: 256, height: 320))
                    (text as NSString).draw(in: CGRect(x: 18, y: 18, width: 220, height: 284),
                        withAttributes: [.font: UIFont.systemFont(ofSize: 13), .foregroundColor: UIColor.darkGray])
                }
            }
            guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
            let options: [CFString: Any] = [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceThumbnailMaxPixelSize: 256,
            ]
            guard let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else {
                return nil
            }
            return UIImage(cgImage: cgImage)
        }.value
    }
}

// MARK: - Kind icon (monochrome)

/// Monochrome SF Symbol per shipped kind — pdf/document never get a fake
/// preview, images fall back here only when no staged copy exists.
struct AttachmentKindIcon: View {
    let kind: AttachmentKind
    var pointSize: CGFloat = 20

    private var symbol: String {
        switch kind {
        case .image: return "photo"
        case .pdf: return "doc.richtext"
        case .document: return "doc.plaintext"
        case .audio: return "waveform"
        case .video: return "play.rectangle"
        }
    }

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: pointSize, weight: .medium))
            .foregroundStyle(Aero.text)
    }
}

// MARK: - Square thumb (real decoded image for image kind, else kind icon)

struct AttachmentThumb: View {
    let kind: AttachmentKind
    /// Staged local copy to decode from — nil renders the kind icon.
    var localURL: URL?
    var size: CGFloat = 52

    var body: some View {
        Group {
            if kind != .audio, let localURL {
                StagedThumb(url: localURL, kind: kind)
            } else {
                AttachmentKindIcon(kind: kind)
            }
        }
        .frame(width: size, height: size)
        .background(RoundedRectangle(cornerRadius: Aero.Radius.sm).fill(Aero.containerHigh))
        .overlay(RoundedRectangle(cornerRadius: Aero.Radius.sm).stroke(Aero.outline, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: Aero.Radius.sm))
    }
}

/// Real decoded thumbnail from the app-private staged copy — user content,
/// so colour is allowed here. Loader instance lives per view (NSCache is
/// shared).
private struct StagedThumb: View {
    let url: URL
    let kind: AttachmentKind

    @StateObject private var loader: ThumbLoader

    init(url: URL, kind: AttachmentKind) {
        self.url = url
        self.kind = kind
        _loader = StateObject(wrappedValue: ThumbLoader(url: url))
    }

    var body: some View {
        Group {
            if let image = loader.image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                AttachmentKindIcon(kind: kind, pointSize: 18)
            }
        }
        .task(id: url.path) { await loader.loadIfNeeded() }
    }
}

// MARK: - Composer chips row (spec §6 — single horizontal row above the field)

struct AttachmentChipRow: View {
    let drafts: [AttachmentDraft]
    let onRetry: (String) -> Void
    let onRemove: (String) -> Void

    var body: some View {
        // ≈2 chip heights cap — the row scrolls horizontally, never stacks.
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Aero.Spacing.s) {
                ForEach(drafts) { draft in
                    AttachmentChip(
                        draft: draft,
                        onRetry: { onRetry(draft.id) },
                        onRemove: { onRemove(draft.id) })
                }
            }
            .padding(.vertical, 2)
        }
        .frame(maxHeight: 156)
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Attachments")
    }
}

/// One composer chip: 48–56pt thumb, one-line name + human byte size, phase
/// line (indeterminate spinner while preparing/uploading, honest failure with
/// Retry on failure), remove target ≥44pt.
struct AttachmentChip: View {
    let draft: AttachmentDraft
    let onRetry: () -> Void
    let onRemove: () -> Void
    @State private var previewURL: URL?

    var body: some View {
        HStack(spacing: Aero.Spacing.s) {
            Button { previewURL = draft.localURL } label: {
                AttachmentThumb(kind: draft.kind, localURL: draft.localURL,
                    size: draft.kind == .image || draft.kind == .video ? 104 : 76)
            }
            .buttonStyle(.plain)
            .disabled(draft.localURL == nil)
            .accessibilityLabel("Preview \(draft.displayName)")
            .quickLookPreview($previewURL)

            VStack(alignment: .leading, spacing: 2) {
                Text(draft.displayName)
                    .font(Aero.label())
                    .foregroundStyle(Aero.text)
                    .lineLimit(1)
                    .frame(maxWidth: 120, alignment: .leading)
                phaseLine
            }

            actions
        }
        .padding(6)
        .background(RoundedRectangle(cornerRadius: Aero.Radius.md).fill(Aero.elevatedSurface))
        .overlay(RoundedRectangle(cornerRadius: Aero.Radius.md).stroke(Aero.outline, lineWidth: 1))
        .accessibilityElement(children: .contain)
        .accessibilityLabel(accessibilitySummary)
    }

    private var accessibilitySummary: String {
        var summary = "\(draft.displayName), \(AttachmentRules.humanBytes(draft.byteSize))"
        switch draft.phase {
        case .preparing: summary += ", preparing"
        case .uploading: summary += ", uploading"
        case .ready: summary += ", ready"
        case .failed: summary += ", failed. \(draft.error?.label ?? "")"
        case .selected: break
        }
        return summary
    }

    /// Indeterminate ProgressView for preparing/uploading — NEVER a fabricated
    /// percent (spec §5: the HTTP round-trip IS the upload lifecycle).
    @ViewBuilder
    private var phaseLine: some View {
        switch draft.phase {
        case .selected, .preparing:
            HStack(spacing: 6) {
                ProgressView().scaleEffect(0.6)
                Text("Preparing…")
                    .font(Aero.metadata())
                    .foregroundStyle(Aero.textMuted)
            }
        case .uploading:
            HStack(spacing: 6) {
                ProgressView().scaleEffect(0.6)
                Text("Uploading…")
                    .font(Aero.metadata())
                    .foregroundStyle(Aero.textMuted)
            }
        case .ready:
            Text(AttachmentRules.humanBytes(draft.byteSize))
                .font(Aero.metadata())
                .foregroundStyle(Aero.textMuted)
        case .failed:
            HStack(spacing: 4) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 10))
                    .foregroundStyle(Aero.danger)
                Text(draft.error?.label ?? "Failed")
                    .font(Aero.metadata())
                    .foregroundStyle(Aero.textMuted)
                    .lineLimit(1)
            }
        }
    }

    /// Ready/moving drafts expose the ≥44pt remove target; failed drafts add
    /// Retry (re-runs ONLY the failed step).
    private var actions: some View {
        HStack(spacing: 0) {
            if draft.phase == .failed {
                Button(action: onRetry) {
                    Image(systemName: "arrow.clockwise")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Aero.text)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(KineticPressStyle())
                .accessibilityLabel("Retry attachment upload")
            }
            Button(action: onRemove) {
                Image(systemName: "xmark")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Aero.textMuted)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(KineticPressStyle())
            .accessibilityLabel("Remove attachment \(draft.displayName)")
        }
    }
}

// MARK: - Transcript chips (read-only, spec §7 — message anatomy unchanged)

/// Read-only attachment chips for sent user turns: real thumbnail from the
/// staged local copy when the session still knows it, otherwise the honest
/// monochrome kind icon — NO remote image pipeline is built for this.
struct MessageAttachmentChips: View {
    let attachments: [Attachment]

    var body: some View {
        VStack(alignment: .trailing, spacing: 6) {
            ForEach(attachments) { attachment in
                chip(attachment)
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Attachments")
    }

    private func chip(_ attachment: Attachment) -> some View {
        MessageAttachmentPreview(attachment: attachment)
    }
}

/// Real content first, metadata second. Works after relaunch, not only after upload.
private struct MessageAttachmentPreview: View {
    let attachment: Attachment
    @State private var localURL: URL?
    @State private var previewURL: URL?
    @State private var failed = false
    @State private var attempt = 0
    private var kind: AttachmentKind { AttachmentKind(rawValue: attachment.kind) ?? .document }
    private var visual: Bool { kind == .image || kind == .video }

    var body: some View {
        Button {
            if let localURL { previewURL = localURL } else { attempt += 1 }
        } label: {
            VStack(alignment: .leading, spacing: 8) {
                if visual {
                    AttachmentThumb(kind: kind, localURL: localURL, size: 208)
                        .overlay {
                            if kind == .video { Image(systemName: "play.circle.fill").font(.system(size: 32)).foregroundStyle(.white) }
                        }
                }
                HStack(spacing: 10) {
                    if !visual { AttachmentThumb(kind: kind, localURL: localURL, size: 64) }
                    VStack(alignment: .leading, spacing: 3) {
                        Text(attachment.displayName).font(Aero.label()).foregroundStyle(Aero.text).lineLimit(2)
                        Text(AttachmentRules.humanBytes(attachment.byteSize)).font(Aero.metadata()).foregroundStyle(Aero.textMuted)
                        if failed {
                            Text("Preview unavailable · Tap to retry").font(Aero.metadata()).foregroundStyle(Aero.danger)
                        } else if localURL == nil {
                            ProgressView().scaleEffect(0.7)
                        } else if kind == .audio || kind == .video {
                            Text("Tap to play · Media preview").font(Aero.metadata()).foregroundStyle(Aero.textMuted)
                        }
                    }
                }
            }
            .frame(maxWidth: visual ? 208 : 248, alignment: .leading)
            .padding(10)
            .background(RoundedRectangle(cornerRadius: 20).fill(Aero.elevatedSurface.opacity(0.72)))
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(Aero.outline, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .quickLookPreview($previewURL)
        .accessibilityLabel("Preview \(attachment.displayName), \(AttachmentRules.humanBytes(attachment.byteSize))")
        .task(id: "\(attachment.id):\(attempt)") {
            failed = false
            do { localURL = try await AttachmentStore.shared.previewURL(for: attachment) }
            catch { if !Task.isCancelled { failed = true } }
        }
    }
}

// MARK: - Camera (UIImagePickerController wrapper, spec §11 native picker)

/// The system camera, full screen. Hands back the ORIGINAL image compressed
/// to JPEG 0.9 — the store stages those bytes like any other source. Cancel
/// is silent. (The camera permission prompt itself is driven by the system
/// from NSCameraUsageDescription; a denied grant never reaches this view —
/// the sheet answers with the honest "Camera access is off" state first.)
struct CameraPicker: UIViewControllerRepresentable {

    var onCapture: (Data) -> Void
    var onCancel: () -> Void

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        if UIImagePickerController.isSourceTypeAvailable(.camera) {
            picker.sourceType = .camera
        }
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onCapture: onCapture, onCancel: onCancel)
    }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let onCapture: (Data) -> Void
        let onCancel: () -> Void

        init(onCapture: @escaping (Data) -> Void, onCancel: @escaping () -> Void) {
            self.onCapture = onCapture
            self.onCancel = onCancel
        }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            picker.dismiss(animated: true)
            guard let image = info[.originalImage] as? UIImage,
                  let data = image.jpegData(compressionQuality: 0.9),
                  !data.isEmpty else {
                onCancel()
                return
            }
            onCapture(data)
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            picker.dismiss(animated: true)
            onCancel()
        }
    }
}
