import CoreText
import Foundation
import UIKit

/// The three formats and three scopes the reference offers. The screen owns
/// the choice; this file owns the serialization so every combination shares one
/// truthful corpus.
enum DataExportFormat: String, CaseIterable, Identifiable {
    case json
    case pdf
    case csv

    var id: String { rawValue }
    var label: String {
        switch self {
        case .json: return "JSON"
        case .pdf: return "PDF"
        case .csv: return "CSV"
        }
    }
    var fileExtension: String { rawValue }

    var caption: String {
        switch self {
        case .json: return "Structured data"
        case .pdf: return "Readable document"
        case .csv: return "Spreadsheet format"
        }
    }
}

enum DataExportScope: String {
    case conversations
    case personalArchive
    case complete

    var fileStem: String {
        switch self {
        case .conversations: return "gs-ai-conversations"
        case .personalArchive: return "gs-ai-personal-archive"
        case .complete: return "gs-ai-complete-archive"
        }
    }

    var reportTitle: String {
        switch self {
        case .conversations: return "Conversations"
        case .personalArchive: return "Personal data archive"
        case .complete: return "Complete archive"
        }
    }
}

/// A value snapshot taken on the main actor, then rendered off it. Attachments
/// travel as their persisted records; their bytes are not part of a chat
/// export, which is why the screen says "attachment details".
struct DataExportSnapshot {
    let scope: DataExportScope
    let exportedAt: String
    let appVersion: String
    let conversations: [StoredConversation]
    /// Filled by the export task off the main actor; the main-actor snapshot
    /// only carries the light store reads.
    var messages: [StoredMessage]
    let library: [LibraryItem]
    let displayName: String
    let email: String
    let planId: String
    let planActivated: Bool
    let planSinceMillis: Double
    let settings: [String: String]
}

enum DataExport {

    /// Read the shipped stores once. Everything here is real state: no mock
    /// profile, no invented plan, no settings the app does not keep.
    @MainActor
    static func snapshot(scope: DataExportScope) -> DataExportSnapshot {
        let store = ConversationStore.shared
        let includesChats = scope != .personalArchive
        let settings = SettingsStore.shared
        let plan = PlanStore.shared
        let account = AccountStore.shared
        return DataExportSnapshot(
            scope: scope,
            exportedAt: ConversationStore.now(),
            appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown",
            conversations: includesChats ? store.conversations : [],
            messages: [],
            library: includesChats ? store.savedLibraryItems() : [],
            displayName: account.displayName,
            email: account.email,
            planId: plan.planId,
            planActivated: plan.activated,
            planSinceMillis: plan.sinceMillis,
            settings: [
                "theme": settings.theme,
                "preferredModel": settings.preferredModel,
                "reasoningEffort": settings.reasoningEffort,
                "rememberChoices": String(settings.rememberChoices),
                "preferLocal": String(settings.preferLocal),
                "memory": String(settings.memory),
                "pushNotifications": String(settings.pushNotifications),
                "biometricUnlock": String(settings.biometricUnlock),
                "quietHoursEnabled": String(settings.quietHoursEnabled),
                "quietStartMinute": String(settings.quietStartMinute),
                "quietEndMinute": String(settings.quietEndMinute),
                "fontScale": String(format: "%.2f", settings.fontScale),
                "highContrast": String(settings.highContrast),
                "reduceMotion": String(settings.reduceMotion),
                "screenReaderHints": String(settings.screenReaderHints),
                "haptics": String(settings.haptics),
                "dataRetentionMonths": String(settings.dataRetentionMonths),
                "autoDeleteInactive": String(settings.autoDeleteInactive),
            ])
    }

    /// Render the chosen scope/format into a temporary file the share sheet can
    /// hand to Files, Mail or AirDrop. Safe to call off the main actor.
    static func write(snapshot: DataExportSnapshot, format: DataExportFormat) -> URL? {
        let data: Data?
        switch format {
        case .json: data = jsonData(snapshot)
        case .csv: data = csvData(snapshot).data(using: .utf8)
        case .pdf: data = pdfData(snapshot)
        }
        guard let data else { return nil }
        let filename = "\(snapshot.scope.fileStem)-\(fileStamp()).\(format.fileExtension)"
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(filename)
        do {
            try data.write(to: url, options: .atomic)
            return url
        } catch {
            return nil
        }
    }

    // MARK: - JSON

    static func jsonData(_ snapshot: DataExportSnapshot) -> Data? {
        var root: [String: Any] = [
            "exportedAt": snapshot.exportedAt,
            "appVersion": snapshot.appVersion,
            "scope": snapshot.scope.rawValue,
            "profile": [
                "displayName": snapshot.displayName,
                "email": snapshot.email,
            ],
            "plan": [
                "id": snapshot.planId,
                "activated": snapshot.planActivated,
                "sinceMillis": snapshot.planSinceMillis,
            ],
            "settings": snapshot.settings,
        ]
        if snapshot.scope != .personalArchive {
            root["conversations"] = snapshot.conversations.map { conversation in
                [
                    "id": conversation.id,
                    "title": conversation.title,
                    "modelId": conversation.modelId ?? NSNull(),
                    "pinned": conversation.pinned,
                    "archived": conversation.archived,
                    "createdAt": conversation.createdAt,
                    "updatedAt": conversation.updatedAt,
                ] as [String: Any]
            }
            root["messages"] = snapshot.messages.map(messageDictionary)
            root["library"] = snapshot.library.map { item in
                [
                    "id": item.id,
                    "kind": item.kind,
                    "title": item.title,
                    "content": item.content,
                    "createdAt": item.createdAt,
                ]
            }
        }
        return try? JSONSerialization.data(
            withJSONObject: root,
            options: [.prettyPrinted, .sortedKeys])
    }

    private static func messageDictionary(_ message: StoredMessage) -> [String: Any] {
        var row: [String: Any] = [
            "id": message.id,
            "conversationId": message.conversationId,
            "role": message.role,
            "content": message.content,
            "createdAt": message.createdAt,
        ]
        if let attachments = message.attachments, !attachments.isEmpty {
            row["attachments"] = attachments.map { attachment in
                [
                    "id": attachment.id,
                    "kind": attachment.kind,
                    "displayName": attachment.displayName,
                    "mimeType": attachment.mimeType,
                    "byteSize": attachment.byteSize,
                    "createdAt": attachment.createdAt,
                    "url": attachment.url,
                ] as [String: Any]
            }
        }
        return row
    }

    // MARK: - CSV

    static func csvData(_ snapshot: DataExportSnapshot) -> String {
        var rows: [String] = [
            "recordType,id,parentId,title,role,createdAt,content,attachments"
        ]
        if snapshot.scope != .personalArchive {
            for conversation in snapshot.conversations {
                rows.append(csvRow([
                    "conversation", conversation.id, "", conversation.title,
                    "", conversation.createdAt, "", "",
                ]))
            }
            for message in snapshot.messages {
                let attachments = message.attachments.flatMap { records in
                    try? JSONSerialization.data(withJSONObject: records.map { attachment in
                        [
                            "id": attachment.id,
                            "displayName": attachment.displayName,
                            "mimeType": attachment.mimeType,
                        ]
                    })
                }.flatMap { String(data: $0, encoding: .utf8) } ?? ""
                rows.append(csvRow([
                    "message", message.id, message.conversationId, "", message.role,
                    message.createdAt, message.content, attachments,
                ]))
            }
            for item in snapshot.library {
                rows.append(csvRow([
                    "library", item.id, "", item.title, item.kind,
                    item.createdAt, item.content, "",
                ]))
            }
        }
        rows.append(csvRow(["profile", "", "", "displayName", "", "", snapshot.displayName, ""]))
        rows.append(csvRow(["profile", "", "", "email", "", "", snapshot.email, ""]))
        rows.append(csvRow(["plan", "", "", "id", "", "", snapshot.planId, ""]))
        rows.append(csvRow(["plan", "", "", "activated", "", "", String(snapshot.planActivated), ""]))
        rows.append(csvRow(["plan", "", "", "sinceMillis", "", "", String(snapshot.planSinceMillis), ""]))
        for key in snapshot.settings.keys.sorted() {
            rows.append(csvRow([
                "setting", "", "", key, "", "", snapshot.settings[key] ?? "", "",
            ]))
        }
        return rows.joined(separator: "\n")
    }

    private static func csvRow(_ fields: [String]) -> String {
        fields.map { "\"" + $0.replacingOccurrences(of: "\"", with: "\"\"") + "\"" }
            .joined(separator: ",")
    }

    // MARK: - PDF

    static func pdfData(_ snapshot: DataExportSnapshot) -> Data? {
        let report = attributedReport(snapshot)
        guard report.length > 0 else { return nil }
        let pageRect = CGRect(x: 0, y: 0, width: 595, height: 842)
        let renderer = UIGraphicsPDFRenderer(bounds: pageRect)
        return renderer.pdfData { context in
            let framesetter = CTFramesetterCreateWithAttributedString(report as CFAttributedString)
            var location = 0
            while location < report.length {
                context.beginPage()
                let path = CGPath(
                    rect: pageRect.insetBy(dx: 40, dy: 40),
                    transform: nil)
                let frame = CTFramesetterCreateFrame(
                    framesetter,
                    CFRange(location: location, length: 0),
                    path,
                    nil)
                CTFrameDraw(frame, context.cgContext)
                let visible = CTFrameGetVisibleStringRange(frame)
                // A page that cannot fit one line would loop forever; stop
                // rather than spin if the layout ever gets that small.
                if visible.length <= 0 { break }
                location += visible.length
            }
        }
    }

    private static func attributedReport(_ snapshot: DataExportSnapshot) -> NSAttributedString {
        let report = NSMutableAttributedString()
        append(report, "GS AI data export", size: 20, weight: .bold, after: 4)
        append(
            report,
            "\(snapshot.scope.reportTitle) · \(snapshot.exportedAt) · v\(snapshot.appVersion)",
            size: 9, after: 14)

        if snapshot.scope != .personalArchive {
            append(report, "Conversations", size: 14, weight: .semibold, after: 8)
            if snapshot.conversations.isEmpty {
                append(report, "No conversations are stored on this device.", size: 10, after: 10)
            }
            let byConversation = Dictionary(
                grouping: snapshot.messages,
                by: \.conversationId)
            for conversation in snapshot.conversations {
                let turns = byConversation[conversation.id] ?? []
                append(report, conversation.title, size: 12, weight: .semibold, after: 2)
                append(
                    report,
                    "id \(conversation.id) · updated \(conversation.updatedAt) · \(turns.count) messages",
                    size: 8.5,
                    after: 4)
                for message in turns {
                    append(
                        report,
                        "\(message.role) · \(message.createdAt)",
                        size: 9,
                        weight: .semibold,
                        after: 1)
                    append(report, message.content.isEmpty ? "(empty turn)" : message.content, size: 9.5, after: 2)
                    if let names = message.attachments?.map(\.displayName), !names.isEmpty {
                        append(report, "Attachments: \(names.joined(separator: ", "))", size: 8.5, after: 3)
                    }
                }
                append(report, "", size: 8, after: 8)
            }
            if !snapshot.library.isEmpty {
                append(report, "Library", size: 12, weight: .semibold, after: 4)
                for item in snapshot.library {
                    append(report, "\(item.title) (\(item.kind)) · \(item.createdAt)", size: 9.5, after: 2)
                    append(report, item.content, size: 9, after: 3)
                }
                append(report, "", size: 8, after: 8)
            }
        }

        append(report, "Account", size: 14, weight: .semibold, after: 6)
        append(report, "Name: \(snapshot.displayName.isEmpty ? "Not set" : snapshot.displayName)", size: 10, after: 2)
        append(report, "Email: \(snapshot.email.isEmpty ? "Not set" : snapshot.email)", size: 10, after: 2)
        append(
            report,
            "Plan: \(snapshot.planId) · activated \(snapshot.planActivated)",
            size: 10,
            after: 10)
        append(report, "Settings", size: 14, weight: .semibold, after: 6)
        for key in snapshot.settings.keys.sorted() {
            append(report, "\(key): \(snapshot.settings[key] ?? "")", size: 9.5, after: 1)
        }
        return report
    }

    private static func append(
        _ target: NSMutableAttributedString,
        _ text: String,
        size: CGFloat,
        weight: UIFont.Weight = .regular,
        after: CGFloat = 0
    ) {
        let paragraph = NSMutableParagraphStyle()
        paragraph.lineSpacing = 1
        paragraph.paragraphSpacing = after
        target.append(NSAttributedString(
            string: text + "\n",
            attributes: [
                .font: UIFont.systemFont(ofSize: size, weight: weight),
                .foregroundColor: UIColor(red: 0.14, green: 0.11, blue: 0.10, alpha: 1),
                .paragraphStyle: paragraph,
            ]))
    }

    private static func fileStamp() -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: Date())
    }
}
