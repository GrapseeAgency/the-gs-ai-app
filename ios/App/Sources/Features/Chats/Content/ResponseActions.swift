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
private final class ResponseSystemControllerHostIOS: UIViewController {
    let systemController: UIViewController
    private var presented = false
    private weak var systemDismissDelegate: UIAdaptivePresentationControllerDelegate?
    init(_ controller: UIViewController, delegate: UIAdaptivePresentationControllerDelegate) {
        systemController = controller
        systemDismissDelegate = delegate
        super.init(nibName: nil, bundle: nil)
    }
    required init?(coder: NSCoder) { return nil }
    override func viewDidLoad() { super.viewDidLoad(); view.backgroundColor = .clear }
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        guard !presented, view.window != nil else { return }
        presented = true
        if let popover = systemController.popoverPresentationController {
            popover.sourceView = view
            popover.sourceRect = CGRect(x: view.bounds.midX, y: view.bounds.midY, width: 1, height: 1)
            popover.permittedArrowDirections = []
        }
        present(systemController, animated: true)
        systemController.presentationController?.delegate = systemDismissDelegate
    }
}

struct ResponseDocumentPickerIOS: UIViewControllerRepresentable {
    let sourceURL: URL
    let onCompletion: (URL?) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(onCompletion: onCompletion) }
    func makeUIViewController(context: Context) -> UIViewController {
        let picker = UIDocumentPickerViewController(forExporting: [sourceURL], asCopy: true)
        picker.delegate = context.coordinator
        picker.presentationController?.delegate = context.coordinator
        return ResponseSystemControllerHostIOS(picker, delegate: context.coordinator)
    }
    func updateUIViewController(_ controller: UIViewController, context: Context) {}
    final class Coordinator: NSObject, UIDocumentPickerDelegate, UIAdaptivePresentationControllerDelegate {
        let onCompletion: (URL?) -> Void
        private var completed = false
        init(onCompletion: @escaping (URL?) -> Void) { self.onCompletion = onCompletion }
        private func finish(_ url: URL?) { guard !completed else { return }; completed = true; onCompletion(url) }
        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) { finish(urls.first) }
        func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) { finish(nil) }
        func presentationControllerDidDismiss(_ controller: UIPresentationController) { finish(nil) }
    }
}

struct ResponseActivityPresenterIOS: UIViewControllerRepresentable {
    let items: [Any]
    let onDismiss: () -> Void
    func makeCoordinator() -> Coordinator { Coordinator(onDismiss: onDismiss) }
    func makeUIViewController(context: Context) -> UIViewController {
        let activity = UIActivityViewController(activityItems: items, applicationActivities: nil)
        let coordinator = context.coordinator
        activity.completionWithItemsHandler = { _, _, _, _ in coordinator.finish() }
        activity.presentationController?.delegate = coordinator
        return ResponseSystemControllerHostIOS(activity, delegate: coordinator)
    }
    func updateUIViewController(_ controller: UIViewController, context: Context) {}
    final class Coordinator: NSObject, UIAdaptivePresentationControllerDelegate {
        let onDismiss: () -> Void
        private var completed = false
        init(onDismiss: @escaping () -> Void) { self.onDismiss = onDismiss }
        func finish() { guard !completed else { return }; completed = true; onDismiss() }
        func presentationControllerDidDismiss(_ controller: UIPresentationController) { finish() }
    }
}
