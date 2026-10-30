import Foundation
import XCTest
@testable import GSApp

/// The format tiles must change the bytes, not just the selected chip. These
/// tests build the same value snapshot the screen builds and read the output
/// back, without touching the live stores.
@MainActor
final class DataExportLogicTests: XCTestCase {

    private func snapshot(scope: DataExportScope = .complete) -> DataExportSnapshot {
        DataExportSnapshot(
            scope: scope,
            exportedAt: "2026-10-31T00:00:00.000Z",
            appVersion: "0.0.0-test",
            conversations: [
                StoredConversation(
                    id: "c1",
                    title: "Retention design",
                    modelId: "pro",
                    pinned: false,
                    archived: false,
                    createdAt: "2026-10-30T09:00:00.000Z",
                    updatedAt: "2026-10-30T10:00:00.000Z"),
            ],
            messages: [
                StoredMessage(
                    id: "m1",
                    conversationId: "c1",
                    role: "user",
                    content: "Export this, \"quoted\".",
                    createdAt: "2026-10-30T09:01:00.000Z"),
            ],
            library: [
                LibraryItem(
                    id: "l1",
                    kind: "message",
                    title: "Saved answer",
                    content: "Kept",
                    createdAt: "2026-10-30T09:02:00.000Z"),
            ],
            displayName: "Arafat",
            email: "arafat@grapsee.com",
            planId: "pro",
            planActivated: false,
            planSinceMillis: 0,
            settings: ["theme": "System", "dataRetentionMonths": "12"])
    }

    func testJSONCarriesCorpusProfileAndPlan() throws {
        let data = try XCTUnwrap(DataExport.jsonData(snapshot()))
        let root = try XCTUnwrap(
            JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertEqual(root["scope"] as? String, "complete")
        let profile = try XCTUnwrap(root["profile"] as? [String: Any])
        XCTAssertEqual(profile["displayName"] as? String, "Arafat")
        let conversations = try XCTUnwrap(root["conversations"] as? [[String: Any]])
        XCTAssertEqual(conversations.first?["title"] as? String, "Retention design")
        let messages = try XCTUnwrap(root["messages"] as? [[String: Any]])
        XCTAssertEqual(messages.first?["content"] as? String, "Export this, \"quoted\".")
    }

    func testCSVCarriesOneRowPerRecordAndEscapesQuotes() {
        let csv = DataExport.csvData(snapshot())
        let lines = csv.split(separator: "\n").map(String.init)
        XCTAssertEqual(
            lines.first ?? "",
            "recordType,id,parentId,title,role,createdAt,content,attachments")
        XCTAssertTrue(lines.contains { $0.hasPrefix("\"conversation\",\"c1\"") })
        XCTAssertTrue(lines.contains { $0.contains("\"Export this, \"\"quoted\"\".\"") })
        XCTAssertTrue(lines.contains { $0.hasPrefix("\"profile\",\"\",\"\",\"email\"") })
    }

    func testPersonalArchiveOmitsChatRows() throws {
        let data = try XCTUnwrap(DataExport.jsonData(snapshot(scope: .personalArchive)))
        let root = try XCTUnwrap(
            JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertNil(root["conversations"])
        XCTAssertNotNil(root["settings"])
    }

    func testRetentionCutoffUsesCalendarMonths() throws {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let now = try XCTUnwrap(formatter.date(from: "2026-10-31T00:00:00.000Z"))
        XCTAssertEqual(
            DataRetention.cutoffIso(months: 12, now: now),
            "2025-10-31T00:00:00.000Z")
    }
}
