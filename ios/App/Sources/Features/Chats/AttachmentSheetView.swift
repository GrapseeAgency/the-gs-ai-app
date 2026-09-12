import SwiftUI
import PhotosUI
import AVFoundation
import UniformTypeIdentifiers

/// REAL attach sheet (PHASE 5, docs/ATTACHMENTS.md §6) — replaces the mock
/// placeholder flow. What is REAL: Gallery (system PhotosPicker), Camera
/// (system UIImagePickerController over a permission-checked gate), Files
/// (document picker for allowlisted document types), Voice note (routes to
/// the real voice experience — no engine duplication). Code / Prompt
/// template are deliberately marked unavailable ("Not available yet", muted)
/// and keep the honest toast — nothing is faked, nothing silently deleted.
///
/// Cancellation is silent everywhere: dismissing a picker never toasts.
struct AttachmentSheetView: View {

    /// Gallery/camera bytes + resolved MIME + source. The caller stages them
    /// through AttachmentStore.
    var onImageData: (Data, String, AttachmentSource) -> Void = { _, _, _ in }
    /// A security-scoped document URL from the Files picker.
    var onFileURL: (URL) -> Void = { _ in }
    /// Honest toast for the deliberately-unavailable tiles.
    var onUnavailable: (String) -> Void = { _ in }
    /// Remaining per-message slots — additions beyond the cap are DISABLED,
    /// never silently dropped (spec §6).
    var remainingSlots: Int = AttachmentRules.maxPerMessage

    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var router: Router

    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var showFileImporter = false
    @State private var showCamera = false
    @State private var loadingGallery = false

    /// Explicit init — the private @State properties make the synthesized
    /// memberwise initializer fileprivate, so other files could not call it.
    init(
        onImageData: @escaping (Data, String, AttachmentSource) -> Void = { _, _, _ in },
        onFileURL: @escaping (URL) -> Void = { _ in },
        onUnavailable: @escaping (String) -> Void = { _ in },
        remainingSlots: Int = AttachmentRules.maxPerMessage
    ) {
        self.onImageData = onImageData
        self.onFileURL = onFileURL
        self.onUnavailable = onUnavailable
        self.remainingSlots = remainingSlots
    }

    /// Files stays documents-only (spec: images come through Gallery).
    private static let fileTypes: [UTType] = [
        .pdf, .plainText, .commaSeparatedText, .utf8PlainText, .text,
    ]

    private struct Tile: Identifiable {
        let id: String
        let label: String
        let icon: String
        let available: Bool
    }

    private var tiles: [Tile] {
        [
            Tile(id: "gallery", label: "Gallery", icon: "photo", available: true),
            Tile(id: "camera", label: "Camera", icon: "camera.fill", available: true),
            Tile(id: "files", label: "Files", icon: "folder", available: true),
            Tile(id: "voice", label: "Voice note", icon: "mic", available: true),
            Tile(id: "code", label: "Code", icon: "curlybraces", available: false),
            Tile(id: "template", label: "Prompt template", icon: "lightbulb", available: false),
        ]
    }

    private var atCapacity: Bool { remainingSlots <= 0 }

    private let columns = [
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible(), spacing: 12),
    ]

    var body: some View {
        AeroSheetShell(title: "Attach") {
            VStack(alignment: .leading, spacing: Aero.Spacing.m) {
                if atCapacity {
                    Text("Six attachments per message — remove one to add another.")
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                } else if loadingGallery {
                    HStack(spacing: Aero.Spacing.s) {
                        ProgressView()
                        Text("Preparing your photos…")
                            .font(Aero.caption())
                            .foregroundStyle(Aero.textMuted)
                    }
                } else {
                    Text("Photos, documents and voice — files are uploaded to the conversation when you send.")
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                }

                LazyVGrid(columns: columns, spacing: 12) {
                    galleryTile
                    cameraTile
                    filesTile
                    voiceTile
                    ForEach(tiles.filter { !$0.available }) { tile in
                        unavailableTile(tile)
                    }
                }
                Spacer(minLength: 0)
            }
        }
        .presentationDetents([.height(360)])
        .onChange(of: pickerItems) { items in
            guard !items.isEmpty else { return }
            pickerItems = [] // re-arm the picker for the next visit
            loadGalleryItems(items)
        }
        .fileImporter(
            isPresented: $showFileImporter,
            allowedContentTypes: Self.fileTypes,
            allowsMultipleSelection: false
        ) { result in
            // Cancelled picker → silent.
            guard case .success(let urls) = result, let url = urls.first else { return }
            onFileURL(url)
            dismiss()
        }
        .fullScreenCover(isPresented: $showCamera) {
            CameraPicker(
                onCapture: { data in
                    onImageData(data, "image/jpeg", .camera)
                    dismiss()
                },
                onCancel: {
                    // Cancelled camera → silent.
                })
        }
    }

    // MARK: Real tiles

    /// System photo picker — matching .images only, capped at the remaining
    /// per-message slots.
    private var galleryTile: some View {
        PhotosPicker(
            selection: $pickerItems,
            maxSelectionCount: max(1, remainingSlots),
            matching: .images
        ) {
            tileContent(for: tiles[0])
        }
        .buttonStyle(KineticPressStyle())
        .disabled(atCapacity || loadingGallery)
        .accessibilityLabel(atCapacity ? "Gallery — six attachments per message" : "Add from Gallery")
    }

    private var cameraTile: some View {
        Button {
            openCamera()
        } label: {
            tileContent(for: tiles[1])
        }
        .buttonStyle(KineticPressStyle())
        .disabled(atCapacity)
        .accessibilityLabel(atCapacity ? "Camera — six attachments per message" : "Take a photo")
    }

    private var filesTile: some View {
        Button {
            showFileImporter = true
        } label: {
            tileContent(for: tiles[2])
        }
        .buttonStyle(KineticPressStyle())
        .disabled(atCapacity)
        .accessibilityLabel(atCapacity ? "Files — six attachments per message" : "Add a document")
    }

    /// Voice note → the dedicated voice experience (the same route the
    /// composer mic uses). The engine stays the single source of truth.
    private var voiceTile: some View {
        Button {
            dismiss()
            router.path.append(.voice)
        } label: {
            tileContent(for: tiles[3])
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityLabel("Record a voice note")
    }

    // MARK: Honestly unavailable tiles

    /// Muted, non-functional, labelled — tapping keeps the honest toast.
    private func unavailableTile(_ tile: Tile) -> some View {
        Button {
            onUnavailable(tile.label)
        } label: {
            tileContent(for: tile)
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityLabel("\(tile.label). Not available yet")
    }

    // MARK: Tile chrome

    /// Functional tiles render in full ink; unavailable ones are muted with
    /// the honest "Not available yet" marker.
    private func tileContent(for tile: Tile) -> some View {
        VStack(spacing: Aero.Spacing.s) {
            Image(systemName: tile.icon)
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(tile.available ? Aero.text : Aero.textDisabled)
                .frame(width: 52, height: 52)
                .background(RoundedRectangle(cornerRadius: 16).fill(Aero.container))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(Aero.outline, lineWidth: 1))
            Text(tile.label)
                .font(Aero.label())
                .foregroundStyle(tile.available ? Aero.text : Aero.textDisabled)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            if !tile.available {
                Text("Not available yet")
                    .font(Aero.metadata())
                    .foregroundStyle(Aero.textDisabled)
            }
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: Gallery loading

    /// Loads each picked item's bytes off the sheet, then hands them to the
    /// store. MIME resolves from the item's content types; a load failure is
    /// skipped quietly — the user simply picked nothing yet, no fake error.
    private func loadGalleryItems(_ items: [PhotosPickerItem]) {
        loadingGallery = true
        Task {
            for item in items {
                let mime = item.supportedContentTypes.first?.preferredMIMEType?.lowercased()
                    ?? "image/jpeg"
                let resolved = AttachmentRules.allowedMIMETypes.contains(mime) ? mime : "image/jpeg"
                if let data = try? await item.loadTransferable(type: Data.self), !data.isEmpty {
                    onImageData(data, resolved, .gallery)
                }
            }
            loadingGallery = false
            dismiss()
        }
    }

    // MARK: Camera gate (honest permission state — no fake retry loop)

    private func openCamera() {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
            onUnavailable("No camera on this device")
            return
        }
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            showCamera = true
        case .notDetermined:
            Task {
                let granted = await AVCaptureDevice.requestAccess(for: .video)
                if granted {
                    showCamera = true
                } else {
                    onUnavailable("Camera access is off — enable it in Settings")
                }
            }
        default:
            // Denied / restricted — say exactly that, once. No retry loop.
            onUnavailable("Camera access is off — enable it in Settings")
        }
    }
}
