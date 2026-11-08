import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// One response owns its code drafts. No source is shared between chats.
final class ResponseCodeActionsIOS: ObservableObject {
    struct Entry { let language: String?; var source: String; let edit: () -> Void }
    private var entries: [AnyHashable: Entry] = [:]
    private var order: [AnyHashable] = []
    private var snapshots: [AnyHashable: (String?, String)] = [:]
    private var pendingEdit = false
    func register(_ id: AnyHashable, entry: Entry) {
        if entries[id] == nil { order.append(id) }
        entries[id] = entry
        if pendingEdit { pendingEdit = false; entry.edit() }
    }
    func update(_ id: AnyHashable, source: String) { entries[id]?.source = source }
    func source(_ id: AnyHashable) -> (String?, String)? {
        if let entry = entries[id] { return (entry.language, entry.source) }
        return snapshots[id]
    }
    func remove(_ id: AnyHashable) {
        snapshots[id] = source(id)
        entries.removeValue(forKey: id); order.removeAll { $0 == id }
    }
    func editFirst() {
        if let id = order.first, let entry = entries[id] { entry.edit() }
        else { pendingEdit = true }
    }
}
private struct ResponseCodeActionsKey: EnvironmentKey {
    static let defaultValue: ResponseCodeActionsIOS? = nil
}
extension EnvironmentValues {
    var responseCodeActions: ResponseCodeActionsIOS? {
        get { self[ResponseCodeActionsKey.self] }
        set { self[ResponseCodeActionsKey.self] = newValue }
    }
}

func responseSourceExtensionIOS(_ language: String?) -> String {
    let language = (language ?? "").lowercased()
    switch language {
    case "javascript", "js", "node": return "js"
    case "typescript", "ts": return "ts"
    case "python", "python3", "py": return "py"
    case "kotlin": return "kt"
    case "rust": return "rs"
    case "bash", "shell", "sh": return "sh"
    case "htm", "html": return "html"
    case "swift", "java", "cpp", "c", "go", "css", "json", "sql", "svg", "jsx", "tsx": return language
    default: return "txt"
    }
}

private extension Data {
    mutating func zipWord(_ value: UInt16) {
        append(UInt8(truncatingIfNeeded: value)); append(UInt8(truncatingIfNeeded: value >> 8))
    }
    mutating func zipDword(_ value: UInt32) {
        zipWord(UInt16(truncatingIfNeeded: value)); zipWord(UInt16(truncatingIfNeeded: value >> 16))
    }
}

/// Stored ZIP with real CRC-32 and central directory; no fabricated project.
func responseSourceArchiveIOS(_ sources: [(String?, String)]) throws -> Data {
    guard !sources.isEmpty, sources.count < 65535 else {
        throw NSError(domain: "GSResponseExport", code: 1, userInfo: [NSLocalizedDescriptionKey: "No source files are available, or too many files were supplied."])
    }
    var files: [(String, String)] = [("README.txt", "GS AI response source snippets\n\nThese are source snippets, not a complete runnable project.\nNo dependencies, build configuration, credentials or execution results have been invented.\nReview each file and supply its required runtime before executing it.\n")]
    files += sources.enumerated().map { index, item in ("snippet-\(index + 1).\(responseSourceExtensionIOS(item.0))", item.1) }
    var output = Data(), central = Data()
    for (filename, source) in files {
        let name = Data(filename.utf8), bytes = Data(source.utf8)
        guard bytes.count <= Int(UInt32.max), output.count <= Int(UInt32.max) else {
            throw NSError(domain: "GSResponseExport", code: 2, userInfo: [NSLocalizedDescriptionKey: "The source archive exceeds the supported ZIP size."])
        }
        var crc: UInt32 = 0xffffffff
        for byte in bytes {
            crc ^= UInt32(byte)
            for _ in 0..<8 { crc = (crc >> 1) ^ ((crc & 1) != 0 ? 0xedb88320 : 0) }
        }
        crc ^= 0xffffffff
        let offset = UInt32(output.count), size = UInt32(bytes.count)
        output.zipDword(0x04034b50); output.zipWord(20); output.zipWord(0x800); output.zipWord(0)
        output.zipWord(0); output.zipWord(33) // Minimum ZIP date, not a claimed creation date.
        output.zipDword(crc); output.zipDword(size); output.zipDword(size); output.zipWord(UInt16(name.count)); output.zipWord(0)
        output.append(name); output.append(bytes)
        central.zipDword(0x02014b50); central.zipWord(20); central.zipWord(20); central.zipWord(0x800); central.zipWord(0)
        central.zipWord(0); central.zipWord(33); central.zipDword(crc); central.zipDword(size); central.zipDword(size)
        central.zipWord(UInt16(name.count)); central.zipWord(0); central.zipWord(0); central.zipWord(0); central.zipWord(0)
        central.zipDword(0); central.zipDword(offset); central.append(name)
    }
    let centralOffset = UInt32(output.count), centralSize = UInt32(central.count)
    output.append(central)
    output.zipDword(0x06054b50); output.zipWord(0); output.zipWord(0)
    output.zipWord(UInt16(files.count)); output.zipWord(UInt16(files.count))
    output.zipDword(centralSize); output.zipDword(centralOffset); output.zipWord(0)
    return output
}

struct ResponseExportDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.zip, .plainText, UTType(filenameExtension: "md") ?? .plainText, .data] }
    var data: Data
    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}

/// UIKit owns the real Files export. The URL is an actual, already-written
/// source file; the picker copies it only after the user chooses a destination.
/// System pickers must be PRESENTED controllers, not embedded as SwiftUI child
/// views. In particular, their remote Files/activity UI needs a real presenter.
/// A non-modal anchor in the answer presents Files from the actual window's
/// visible controller. Files is the single presentation, never a second modal
/// stacked inside a SwiftUI sheet with a different remote-UI coordinate space.
struct ResponseDocumentPickerIOS: UIViewControllerRepresentable {
    let sourceURL: URL?
    let onCompletion: (URL?) -> Void

    func makeUIViewController(context: Context) -> DocumentPresentationHost {
        DocumentPresentationHost()
    }
    func updateUIViewController(_ host: DocumentPresentationHost, context: Context) {
        host.update(sourceURL: sourceURL, onCompletion: onCompletion)
    }

    final class DocumentPresentationHost: UIViewController, UIDocumentPickerDelegate, UIAdaptivePresentationControllerDelegate {
        private var requestedURL: URL?
        private var activePicker: UIDocumentPickerViewController?
        private var completion: ((URL?) -> Void)?
        private var completed = false

        override func viewDidLoad() { super.viewDidLoad(); view.backgroundColor = .clear }
        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            presentIfReady()
        }
        func update(sourceURL: URL?, onCompletion: @escaping (URL?) -> Void) {
            requestedURL = sourceURL
            completion = onCompletion
            // SwiftUI may be updating the view hierarchy in this call. Wait
            // until that transaction has installed the anchor in its window.
            DispatchQueue.main.async { [weak self] in self?.presentIfReady() }
        }
        private func presentIfReady() {
            guard let source = requestedURL, activePicker == nil,
                  var presenter = viewIfLoaded?.window?.rootViewController else { return }
            while let presented = presenter.presentedViewController {
                presenter = presented
            }
            guard !presenter.isBeingDismissed, !presenter.isBeingPresented else { return }
            let picker = UIDocumentPickerViewController(forExporting: [source], asCopy: true)
            picker.modalPresentationStyle = .fullScreen
            picker.delegate = self
            activePicker = picker
            completed = false
            presenter.present(picker, animated: true)
            picker.presentationController?.delegate = self
        }
        private func finish(_ destination: URL?) {
            guard !completed, let picker = activePicker else { return }
            completed = true
            requestedURL = nil
            activePicker = nil
            // The delegate, not a timeout or an app-owned Cancel button, is the
            // only source of the export receipt. Dismiss the same controller.
            picker.dismiss(animated: true)
            completion?(destination)
        }
        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) { finish(urls.first) }
        func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) { finish(nil) }
        func presentationControllerDidDismiss(_ controller: UIPresentationController) { finish(nil) }
    }
}

/// Present the real system activity controller from a stable, non-modal
/// anchor. Never stack it inside another transient SwiftUI sheet, especially
/// while a report-details sheet is still completing its UIKit dismissal.
struct ResponseActivityPresenterIOS: UIViewControllerRepresentable {
    let items: [Any]?
    let onDismiss: () -> Void
    func makeUIViewController(context: Context) -> ActivityPresentationHost { ActivityPresentationHost() }
    func updateUIViewController(_ host: ActivityPresentationHost, context: Context) {
        host.update(items: items, onDismiss: onDismiss)
    }
    final class ActivityPresentationHost: UIViewController, UIAdaptivePresentationControllerDelegate {
        private var requestedItems: [Any]?
        private var activeActivity: UIActivityViewController?
        private var completion: (() -> Void)?
        private var completed = false
        override func viewDidLoad() { super.viewDidLoad(); view.backgroundColor = .clear }
        override func viewDidAppear(_ animated: Bool) { super.viewDidAppear(animated); presentIfReady() }
        func update(items: [Any]?, onDismiss: @escaping () -> Void) {
            requestedItems = items; completion = onDismiss
            DispatchQueue.main.async { [weak self] in self?.presentIfReady() }
        }
        private func afterTransition(_ controller: UIViewController) {
            if let transition = controller.transitionCoordinator {
                transition.animate(alongsideTransition: nil) { [weak self] _ in
                    DispatchQueue.main.async { self?.presentIfReady() }
                }
            } else {
                // Re-check actual UIKit presentation state, never synthesize a
                // share/Copy completion receipt or mutate the system sheet.
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) { [weak self] in self?.presentIfReady() }
            }
        }
        private func presentIfReady() {
            guard let items = requestedItems, !items.isEmpty, activeActivity == nil,
                  var presenter = viewIfLoaded?.window?.rootViewController else { return }
            while let presented = presenter.presentedViewController {
                if presented.isBeingDismissed || presented.isBeingPresented { afterTransition(presented); return }
                presenter = presented
            }
            if presenter.isBeingDismissed || presenter.isBeingPresented { afterTransition(presenter); return }
            let activity = UIActivityViewController(activityItems: items, applicationActivities: nil)
            activity.completionWithItemsHandler = { [weak self] _, _, _, _ in self?.finish() }
            if let popover = activity.popoverPresentationController {
                popover.sourceView = presenter.view
                popover.sourceRect = CGRect(x: presenter.view.bounds.midX, y: presenter.view.bounds.midY, width: 1, height: 1)
                popover.permittedArrowDirections = []
            }
            activeActivity = activity; completed = false
            presenter.present(activity, animated: true)
            activity.presentationController?.delegate = self
        }
        private func finish() {
            guard activeActivity != nil, !completed else { return }
            completed = true; requestedItems = nil; activeActivity = nil
            completion?()
        }
        func presentationControllerDidDismiss(_ controller: UIPresentationController) { finish() }
    }
}
