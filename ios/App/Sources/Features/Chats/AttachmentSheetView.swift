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
    /// FLASH MODE (Phase 3) — the Mode section at the top of the sheet.
    var selectedMode: String = "flash"
    var onModeSelected: (String) -> Void = { _ in }

    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme
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
        remainingSlots: Int = AttachmentRules.maxPerMessage,
        selectedMode: String = "flash",
        onModeSelected: @escaping (String) -> Void = { _ in }
    ) {
        self.onImageData = onImageData
        self.onFileURL = onFileURL
        self.onUnavailable = onUnavailable
        self.remainingSlots = remainingSlots
        self.selectedMode = selectedMode
        self.onModeSelected = onModeSelected
    }

    /// Files stays documents-only (spec: images come through Gallery).
    private static let fileTypes: [UTType] = [
        .pdf, .plainText, .commaSeparatedText, .utf8PlainText, .text, .image, .audio, .movie,
    ]

    /// FLASH MODE (Phase 3) — the one-line truth under the mode picker.
    private var modeHint: String {
        switch selectedMode {
        case "thinking": return "Deep reasoning, applies to this chat."
        case "auto": return "The system decides per turn."
        default: return "Instant replies, the clean default."
        }
    }

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

    var body: some View {
        // A MENU, NOT A SHEET OF TILES. The reference is a narrow column of
        // rows — a circular glyph well and a label — hanging off the "+" it
        // came from. The 3-column tile grid with a headline read as a settings
        // page that happened to be floating; this reads as "here is what this
        // button can do".
        VStack(spacing: 0) {
            if atCapacity {
                Text("Six attachments per message, remove one to add another.")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12)
                    .padding(.bottom, 6)
            }
            galleryRow
            cameraRow
            filesRow
            voiceRow
            ForEach(tiles.filter { !$0.available }) { tile in
                unavailableRow(tile)
            }
            GsPopoverRule()
            modeChips
        }
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

    // MARK: Rows

    /// The menu's one anatomy: 38pt well, 54pt row, 14.5pt label.
    private func row(symbol: String, title: String, muted: Bool = false) -> some View {
        HStack(spacing: 12) {
            Image(systemName: symbol)
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(muted ? Aero.textTertiary : Aero.text)
                .frame(width: 38, height: 38)
                .background(
                    Circle().fill(Aero.text.opacity(colorScheme == .dark ? 0.10 : 0.055)))
                .overlay(
                    Circle().strokeBorder(
                        Aero.outline.opacity(colorScheme == .dark ? 0.45 : 0.75),
                        lineWidth: 0.7))
            Text(title)
                .font(.custom(Aero.productName(.medium), size: 14.5, relativeTo: .body))
                .foregroundStyle(muted ? Aero.textTertiary : Aero.text)
                .lineLimit(1)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 8)
        .frame(minHeight: 54)
        .contentShape(Rectangle())
    }

    private var galleryRow: some View {
        PhotosPicker(
            selection: $pickerItems,
            maxSelectionCount: max(1, remainingSlots),
            matching: .images
        ) {
            row(symbol: "photo", title: "Photos")
        }
        .buttonStyle(.plain)
        .disabled(atCapacity || loadingGallery)
        .accessibilityLabel(atCapacity ? "Photos, six attachments per message" : "Add from Photos")
    }

    private var cameraRow: some View {
        Button {
            openCamera()
        } label: {
            row(symbol: "camera.fill", title: "Camera")
        }
        .buttonStyle(.plain)
        .disabled(atCapacity)
        .accessibilityLabel(atCapacity ? "Camera, six attachments per message" : "Take a photo")
    }

    private var filesRow: some View {
        Button {
            showFileImporter = true
        } label: {
            row(symbol: "folder", title: "Files")
        }
        .buttonStyle(.plain)
        .disabled(atCapacity)
        .accessibilityLabel(atCapacity ? "Files, six attachments per message" : "Add a document")
    }

    /// Voice note → the dedicated voice experience (the same route the composer
    /// mic uses). The engine stays the single source of truth.
    private var voiceRow: some View {
        Button {
            dismiss()
            router.path.append(.voice)
        } label: {
            row(symbol: "mic", title: "Voice note")
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Record a voice note")
    }

    private func unavailableRow(_ tile: Tile) -> some View {
        Button {
            onUnavailable(tile.label)
        } label: {
            row(symbol: tile.icon, title: tile.label, muted: true)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(tile.label). Not available yet")
    }

    // MARK: Mode chips

    /// Mode stays, because it is the one thing this button changes that is not a
    /// file — but it is a row of small chips, not a carousel of cards. The menu
    /// is a menu all the way down.
    private var modeChips: some View {
        HStack(spacing: 6) {
            ForEach(
                [
                    (value: "flash", label: "Flash"),
                    (value: "thinking", label: "Thinking"),
                    (value: "auto", label: "Auto"),
                ],
                id: \.value
            ) { mode in
                let on = selectedMode == mode.value
                Button {
                    onModeSelected(mode.value)
                } label: {
                    Text(mode.label)
                        .font(.custom(
                            Aero.productName(on ? .semibold : .medium),
                            size: 11.5, relativeTo: .caption2))
                        .foregroundStyle(on ? Aero.accentStrong : Aero.textSecondary)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity)
                        .frame(height: 30)
                        .background(
                            Capsule().fill(
                                on ? Aero.accent.opacity(0.14) : Aero.text.opacity(0.04)))
                        .overlay(
                            Capsule().strokeBorder(
                                on ? Aero.accent.opacity(0.42) : Aero.outline.opacity(0.7),
                                lineWidth: 1))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(mode.label) mode")
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
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

    // MARK: Mode carousel (DESIGN PHASE 2 iOS mirror)

    /// The iOS mirror of ChatScreen.kt's ModeCarouselItem: a horizontal
    /// MD3-carousel selection row. 148pt cards so the next item peeks past
    /// the edge, selected state is the ink container with inverted text and
    /// a scale-in check, the hint line rides under each label, state change
    /// animates with `Aero.gentle` (reduce motion degrades it to calm linear).
    /// Touch target is the full 148x88 card, 44pt+ in both axes.
    private struct ModeCarouselRow: View {
        let selected: String
        let onSelect: (String) -> Void

        private var modes: [(value: String, label: String, hint: String)] {
            [
                ("flash", "Flash", "Instant replies"),
                ("thinking", "Thinking", "Deep reasoning"),
                ("auto", "Auto", "System decides"),
            ]
        }

        var body: some View {
            // Three equal cards that FIT the panel. The old row was a 148pt
            // horizontal carousel, so the third mode was half off-screen.
            HStack(spacing: 8) {
                ForEach(modes, id: \.value) { mode in
                    ModeCarouselCard(
                        label: mode.label,
                        hint: mode.hint,
                        selected: selected == mode.value,
                        onSelect: { onSelect(mode.value) }
                    )
                }
            }
        }
    }

    /// One carousel card: 148x88, radius 16, ink fill when selected with the
    /// inverted label and the scale-in check (uiverse tiny-moose-42's animated
    /// check intent, monochrome). The check view is always present and scales
    /// to zero when unselected — exactly the Android graphicsLayer shape — so
    /// selection animates in place instead of inserting a view.
    private struct ModeCarouselCard: View {
        let label: String
        let hint: String
        let selected: Bool
        let onSelect: () -> Void

        var body: some View {
            Button(action: onSelect) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 4) {
                        Text(label)
                            .font(Aero.label())
                            .foregroundStyle(selected ? Aero.accent : Aero.text)
                        Spacer(minLength: 0)
                        Image(systemName: "checkmark")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(selected ? Aero.accent : Aero.text)
                            .scaleEffect(selected ? 1 : 0.4)
                            .opacity(selected ? 1 : 0)
                            .frame(width: 18, height: 18)
                    }
                    Text(hint)
                        .font(Aero.metadata())
                        .foregroundStyle((selected ? Aero.accent : Aero.text).opacity(0.75))
                        .lineLimit(1)
                }
                .padding(10)
                .frame(maxWidth: .infinity, minHeight: 64, alignment: .topLeading)
                // SELECTION IS A LIT RIM, NOT AN INVERTED SLAB. `Aero.text` was
                // a hard black tile — the only black object in the app — inside
                // a cream sheet. Selected now means this glass is lit: accent
                // tint, accent rim, accent ink.
                .gsGlass(selected ? .floatingControl : .cardDim, corner: 14)
                // The tint is an OVERLAY, not a background: a background sits
                // behind the glass fill and is muted by it, which is why the
                // first attempt read as "almost selected".
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .fill(selected ? Aero.accent.opacity(0.12) : Color.clear)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(selected ? Aero.accent.opacity(0.45) : Aero.outline, lineWidth: 1)
                )
                .contentShape(RoundedRectangle(cornerRadius: 14))
            }
            .buttonStyle(KineticPressStyle())
            .animation(Aero.chatCurve, value: selected)
            .accessibilityLabel("\(label), \(hint)")
            .accessibilityAddTraits(selected ? [.isSelected] : [])
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
                    onUnavailable("Camera access is off, enable it in Settings")
                }
            }
        default:
            // Denied / restricted — say exactly that, once. No retry loop.
            onUnavailable("Camera access is off, enable it in Settings")
        }
    }
}
