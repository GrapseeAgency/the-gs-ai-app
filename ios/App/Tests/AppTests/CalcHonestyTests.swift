import XCTest
import SwiftUI
import WebKit
import Network
@testable import GSApp

final class CalcHonestyTests: XCTestCase {
    func testNativeJSONDelimiterRecoveryPreservesActualSourceBytesAndQuotedPunctuation() throws {
        let source = #"const text = ",} ,] True False None"; const escaped = "\\\" ,}"; const smart = "“literal”";"#
        let original: [String: Any] = ["code": source, "title": "Keep ,} and ,] and “curly” quotes", "values": [1, 2]]
        let data = try JSONSerialization.data(withJSONObject: original, options: [.sortedKeys])
        let valid = try XCTUnwrap(String(data: data, encoding: .utf8))
        XCTAssertEqual(Data(stripTrailingJSONCommasIOS(valid).utf8), Data(valid.utf8))
        let malformed = String(valid.dropLast()) + ",}"
        let actual = try XCTUnwrap(parseRelaxedJSONObject(kind: "app", raw: malformed))
        let actualCode = try XCTUnwrap(actual["code"] as? String)
        XCTAssertEqual(Data(actualCode.utf8), Data(source.utf8))
        XCTAssertEqual(actual["title"] as? String, original["title"] as? String)
        XCTAssertEqual(stripTrailingJSONCommasIOS(#"{"a":[1,2,],"b":"literal ,}","c":{"ok":true,},}"#),
                       #"{"a":[1,2],"b":"literal ,}","c":{"ok":true}}"#)
    }

    @MainActor
    func testActualMessagePreviewIdentitySurvivesRehydrationWithoutInventingServerIDs() {
        let live = ChatViewModel.ChatMessage(role: "assistant", content: "actual source", serverId: "actual-server-row")
        let reopened = ChatViewModel.ChatMessage(role: "assistant", content: "actual source", durableID: "actual-server-row")
        XCTAssertNotEqual(live.id, reopened.id, "UI identity really changes on reload")
        XCTAssertEqual(live.reactPreviewIdentity, reopened.reactPreviewIdentity)
        XCTAssertNil(reopened.serverId, "A cached row ID is not proof of a feedback API row")
        let empty = ChatViewModel.ChatMessage(role: "assistant", content: "source", serverId: "", durableID: "")
        let otherEmpty = ChatViewModel.ChatMessage(role: "assistant", content: "source", serverId: "", durableID: "")
        XCTAssertEqual(empty.reactPreviewIdentity, empty.id.uuidString)
        XCTAssertNotEqual(empty.reactPreviewIdentity, otherEmpty.reactPreviewIdentity)
        XCTAssertEqual(ChatViewModel.ChatMessage(role: "assistant", content: "source", serverId: "", durableID: "actual-cache-row").reactPreviewIdentity, "actual-cache-row")
        let local = ChatViewModel.ChatMessage(role: "assistant", content: "offline source")
        let localReopened = ChatViewModel.ChatMessage(role: "assistant", content: "offline source", durableID: local.reactPreviewIdentity)
        XCTAssertEqual(local.reactPreviewIdentity, localReopened.reactPreviewIdentity)
        XCTAssertNotEqual(local.reactPreviewIdentity, live.reactPreviewIdentity)
        XCTAssertNotEqual(reopened.reactPreviewIdentity, ChatViewModel.ChatMessage(role: "assistant", content: "another answer").reactPreviewIdentity)
    }

    /// Capture an actual SQLite snapshot before later main-actor mutations.
    /// Applying that stored snapshot explicitly makes the real startup race
    /// deterministic, without mocking SQL or erasing the shared app history.
    @MainActor
    private func actualSQLiteBootstrapFixture() throws -> (directory: URL, sql: SQLiteChatStore, store: ConversationStore, conversations: [StoredConversation], messages: [StoredMessage]) {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("gs-bootstrap-" + UUID().uuidString)
        let sql = SQLiteChatStore(databaseURL: directory.appendingPathComponent("history.sqlite"))
        XCTAssertTrue(sql.isAvailable)
        let old = StoredConversation(id: "actual-existing", title: "Original title", modelId: nil, pinned: false, archived: false,
                                     createdAt: "2026-10-01T10:00:00.000Z", updatedAt: "2026-10-01T10:00:00.000Z")
        let untouched = StoredConversation(id: "actual-untouched", title: "Untouched title", modelId: nil, pinned: false, archived: false,
                                           createdAt: "2026-10-20T10:00:00.000Z", updatedAt: "2026-10-20T10:00:00.000Z")
        sql.upsertConversation(old)
        sql.upsertConversation(untouched)
        for (index, content) in ["Original question", "Original answer", "Follow-up", "Original follow-up answer"].enumerated() {
            sql.upsertMessage(StoredMessage(id: "actual-message-\(index)", conversationId: old.id,
                                           role: index % 2 == 0 ? "user" : "assistant", content: content,
                                           createdAt: String(format: "2026-10-01T10:00:%02d.000Z", index)))
        }
        sql.upsertMessage(StoredMessage(id: "untouched-message", conversationId: untouched.id, role: "assistant",
                                       content: "Untouched actual answer", createdAt: untouched.createdAt))
        let conversations = sql.loadConversations()
        let messages = sql.loadMessages()
        XCTAssertEqual(conversations.count, 2)
        XCTAssertEqual(messages.count, 5)
        return (directory, sql, ConversationStore(sql: sql), conversations, messages)
    }

    @MainActor
    func testActualSQLiteLegacyImportKeepsActualRowsAndCannotReviveClearedHistoryOnReopen() throws {
        let fixture = try actualSQLiteBootstrapFixture()
        defer { try? FileManager.default.removeItem(at: fixture.directory) }
        struct ActualLegacySnapshot: Encodable {
            let conversations: [StoredConversation]
            let messages: [StoredMessage]
        }
        let legacyURL = fixture.directory.appendingPathComponent("actual-legacy.json")
        let data = try JSONEncoder().encode(ActualLegacySnapshot(conversations: fixture.conversations, messages: fixture.messages))
        try data.write(to: legacyURL)
        let databaseURL = fixture.directory.appendingPathComponent("imported.sqlite")
        let imported = SQLiteChatStore(databaseURL: databaseURL, legacyURL: legacyURL)
        XCTAssertTrue(imported.isAvailable)
        XCTAssertEqual(imported.loadConversations(), fixture.conversations)
        XCTAssertEqual(imported.loadMessages(), fixture.messages)
        imported.deleteAllContent()
        let reopened = SQLiteChatStore(databaseURL: databaseURL, legacyURL: legacyURL)
        XCTAssertTrue(reopened.isAvailable)
        XCTAssertTrue(reopened.loadConversations().isEmpty)
        XCTAssertTrue(reopened.loadMessages().isEmpty)
        XCTAssertTrue(FileManager.default.fileExists(atPath: legacyURL.path), "The unchanged legacy fixture still exists; the genuine migration marker, not missing input, prevents resurrection")
    }

    @MainActor
    func testActualSQLiteDelayedBootstrapKeepsNewerRenamePinArchiveAndSourceBytes() throws {
        let fixture = try actualSQLiteBootstrapFixture()
        defer { try? FileManager.default.removeItem(at: fixture.directory) }
        let original = try XCTUnwrap(fixture.conversations.first { $0.id == "actual-existing" })
        fixture.store.upsert(original)
        fixture.store.rename(id: original.id, to: "Actual newer title")
        fixture.store.setPinned(id: original.id, true)
        fixture.store.setArchived(id: original.id, true)
        let source = "Actual newer source with “curly quotes” and literal ,} ,]"
        fixture.store.append(StoredMessage(id: "actual-message-1", conversationId: original.id, role: "assistant",
                                          content: source, createdAt: "2026-10-20T10:00:00.000Z"))
        fixture.store.applyBootstrap(conversations: fixture.conversations, messages: fixture.messages)
        let actual = try XCTUnwrap(fixture.store.conversation(withID: original.id))
        XCTAssertEqual(actual.title, "Actual newer title")
        XCTAssertTrue(actual.pinned)
        XCTAssertTrue(actual.archived)
        let messages = fixture.store.messages(for: original.id)
        XCTAssertEqual(messages.count, 4)
        XCTAssertEqual(messages.first { $0.id == "actual-message-1" }?.content, source)
        XCTAssertEqual(fixture.sql.loadConversations().first { $0.id == original.id }, actual)
        XCTAssertEqual(fixture.store.messages(for: "actual-untouched").map(\.content), ["Untouched actual answer"])
    }

    @MainActor
    func testActualSQLiteDelayedBootstrapCannotResurrectDeletedConversationOrMessages() throws {
        let fixture = try actualSQLiteBootstrapFixture()
        defer { try? FileManager.default.removeItem(at: fixture.directory) }
        fixture.store.delete(id: "actual-existing")
        fixture.store.applyBootstrap(conversations: fixture.conversations, messages: fixture.messages)
        XCTAssertNil(fixture.store.conversation(withID: "actual-existing"))
        XCTAssertTrue(fixture.store.messages(for: "actual-existing").isEmpty)
        XCTAssertEqual(fixture.store.conversations.map(\.id), ["actual-untouched"])
        XCTAssertEqual(fixture.sql.loadConversations(), fixture.store.conversations)
        XCTAssertEqual(fixture.sql.loadMessages(), fixture.store.messages)
    }

    @MainActor
    func testActualSQLiteDelayedBootstrapCannotUndoClearHistoryOrDropPostClearAnswer() throws {
        let fixture = try actualSQLiteBootstrapFixture()
        defer { try? FileManager.default.removeItem(at: fixture.directory) }
        fixture.store.clearHistory()
        XCTAssertTrue(fixture.sql.loadConversations().isEmpty)
        XCTAssertTrue(fixture.sql.loadMessages().isEmpty)
        let new = fixture.store.createLocalConversation(title: "Actual post-clear conversation")
        fixture.store.append(StoredMessage(id: "actual-post-clear", conversationId: new.id, role: "assistant",
                                          content: "Actual post-clear answer", createdAt: new.createdAt))
        fixture.store.applyBootstrap(conversations: fixture.conversations, messages: fixture.messages)
        XCTAssertEqual(fixture.store.conversations.map(\.id), [new.id])
        XCTAssertEqual(fixture.store.messages.map(\.content), ["Actual post-clear answer"])
        XCTAssertEqual(fixture.sql.loadConversations(), fixture.store.conversations)
        XCTAssertEqual(fixture.sql.loadMessages(), fixture.store.messages)
    }

    @MainActor
    func testActualSQLiteDelayedBootstrapCannotRestoreEditedAwayMessageTail() throws {
        let fixture = try actualSQLiteBootstrapFixture()
        defer { try? FileManager.default.removeItem(at: fixture.directory) }
        let original = try XCTUnwrap(fixture.conversations.first { $0.id == "actual-existing" })
        fixture.store.upsert(original)
        for message in fixture.messages where message.conversationId == original.id { fixture.store.append(message) }
        fixture.store.truncateMessages(fromUserContent: "Follow-up", in: original.id)
        fixture.store.append(StoredMessage(id: "actual-replacement", conversationId: original.id, role: "assistant",
                                          content: "Actual replacement", createdAt: "2026-10-20T10:00:00.000Z"))
        fixture.store.applyBootstrap(conversations: fixture.conversations, messages: fixture.messages)
        XCTAssertEqual(fixture.store.messages(for: original.id).map(\.content), ["Original question", "Original answer", "Actual replacement"])
        XCTAssertEqual(fixture.sql.loadMessages().filter { $0.conversationId == original.id }, fixture.store.messages(for: original.id))
    }

    @MainActor
    func testActualSQLiteDelayedBootstrapCannotRestoreRetentionPurgedHistory() throws {
        let fixture = try actualSQLiteBootstrapFixture()
        defer { try? FileManager.default.removeItem(at: fixture.directory) }
        fixture.store.upsert(try XCTUnwrap(fixture.conversations.first { $0.id == "actual-existing" }))
        XCTAssertEqual(fixture.store.purgeConversations(updatedBefore: "2026-10-10T00:00:00.000Z"), 1)
        fixture.store.applyBootstrap(conversations: fixture.conversations, messages: fixture.messages)
        XCTAssertEqual(fixture.store.conversations.map(\.id), ["actual-untouched"])
        XCTAssertEqual(fixture.store.messages.map(\.id), ["untouched-message"])
        XCTAssertEqual(fixture.sql.loadConversations(), fixture.store.conversations)
        XCTAssertEqual(fixture.sql.loadMessages(), fixture.store.messages)
    }

    @MainActor
    func testActualSQLiteHistoryAndOlderPagesKeepDurablePreviewIdentity() throws {
        let store = ConversationStore.shared
        let conversation = store.createLocalConversation(title: "Preview identity acceptance")
        defer { store.delete(id: conversation.id) }
        let prefix = "actual-stored-row-" + UUID().uuidString
        for index in 0...60 {
            store.append(StoredMessage(
                id: "\(prefix)-\(index)", conversationId: conversation.id,
                role: "assistant", content: "Supplied answer \(index)",
                createdAt: String(format: "2026-10-20T10:%02d:%02d.000Z", index / 60, index % 60)
            ))
        }
        let firstOpen = ChatViewModel(conversationID: conversation.id)
        let reopened = ChatViewModel(conversationID: conversation.id)
        XCTAssertEqual(firstOpen.messages.count, 60)
        XCTAssertEqual(reopened.messages.count, 60)
        let first = try XCTUnwrap(firstOpen.messages.first)
        let again = try XCTUnwrap(reopened.messages.first)
        XCTAssertNotEqual(first.id, again.id)
        XCTAssertEqual(first.reactPreviewIdentity, "\(prefix)-1")
        XCTAssertEqual(first.reactPreviewIdentity, again.reactPreviewIdentity)
        XCTAssertNil(again.serverId)
        firstOpen.armOlderPages()
        firstOpen.loadOlder()
        XCTAssertEqual(firstOpen.messages.count, 61)
        XCTAssertEqual(firstOpen.messages.first?.reactPreviewIdentity, "\(prefix)-0")
        XCTAssertEqual(firstOpen.messages[1].reactPreviewIdentity, first.reactPreviewIdentity)
        XCTAssertFalse(firstOpen.hasOlder)
    }

    @MainActor
    func testChartDataInkUsesActualSuppliedAccentAndKeepsDifferentSeriesDistinct() {
        let supplied = chartSeriesColorIOS(["accent": "#00FF00"], index: 0)
        var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
        XCTAssertTrue(UIColor(supplied).getRed(&red, green: &green, blue: &blue, alpha: &alpha))
        XCTAssertEqual(red, 0, accuracy: 0.001)
        XCTAssertEqual(green, 1, accuracy: 0.001)
        XCTAssertEqual(blue, 0, accuracy: 0.001)
        XCTAssertNotEqual(UIColor(supplied), UIColor(chartSeriesColorIOS(["accent": "#00FF00"], index: 1)))
        XCTAssertEqual(UIColor(chartSeriesColorIOS([:], index: 0)), UIColor(chartSeriesColorIOS(["accent": "invalid"], index: 0)))
    }

    func testAxisLabelsCoverTheCompleteSourceDomainWithoutInventingObservationIndices() {
        XCTAssertEqual(chartAxisLabelIndicesIOS(count: 31), [0, 4, 9, 13, 17, 21, 26, 30])
        XCTAssertEqual(chartAxisLabelIndicesIOS(count: 6), Array(0..<6))
        XCTAssertEqual(chartAxisLabelIndicesIOS(count: 0), [])
        XCTAssertEqual(chartAxisLabelIndicesIOS(count: 31, limit: 1), [30])
    }

    func testMarkdownNumbersKeepActualDirectionalSignsAndRejectMalformedCells() {
        for (raw, expected) in [("↓ 1.2%", -1.2), ("↘ -1.2%", -1.2), ("↑ +0.5%", 0.5), ("−0.3%", -0.3), ("-$1,200.50", -1200.5), ("$-1.2k", -1200), ("৳2M", 2e6), ("2x", 2), ("0", 0)] {
            XCTAssertEqual(parseSortableNumberIOS(raw), expected, raw)
        }
        for raw in ["1,00", "1+2", "1 2", "1e3", "NaN", "Infinity", "↓+1%", "↑-1%", "--1", "+-1", "growth 12%", "1.2.3"] {
            XCTAssertNil(parseSortableNumberIOS(raw), raw)
        }
        XCTAssertEqual(NexlyIcons.cleanPlainText("↘️1.5%"), "↓1.5%")
        XCTAssertEqual(NexlyIcons.cleanPlainText("↗1.5%"), "↑1.5%")
    }

    func testMarkdownChartUsesActualMeasureNamesUnitsAndAllThirtyOneRows() throws {
        func cell(_ text: String) -> [InlineSpan] { [.text(text)] }
        let headers = ["#", "Language", "Popularity"].map(cell)
        var rows = (1...31).map { [cell("\($0)"), cell("Language \($0)"), cell("\($0)%")] }
        let json = try XCTUnwrap(chartJsonFromTableIOS(headers: headers, rows: rows))
        let payload = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any])
        let data = try XCTUnwrap(payload["data"] as? [[String: Any]])
        XCTAssertEqual(payload["title"] as? String, "Popularity")
        XCTAssertEqual(payload["unit"] as? String, "%")
        XCTAssertEqual(data.count, 31)
        XCTAssertEqual(data[30]["label"] as? String, "Language 31")
        XCTAssertEqual(data[30]["value"] as? Double, 31)
        rows[2][2] = cell("not recorded")
        XCTAssertNil(chartJsonFromTableIOS(headers: headers, rows: rows), "No silently dropped observations")
        rows[2][2] = cell("$3")
        XCTAssertNil(chartJsonFromTableIOS(headers: headers, rows: rows), "Incompatible units cannot become one numerical series")
    }

    func testLearningKeepsCompleteDefinitionsAndDoesNotCreateAmbiguousDuplicateAnswerChoices() {
        let definition = Array(repeating: "A supplied definition with an important source qualification.", count: 6).joined(separator: " ")
        let source = ["Alpha", "Beta", "Gamma"].map { "- **\($0)**: \(definition) \($0)." }.joined(separator: "\n")
        let concepts = extractSpontaneousConceptsFromBlocksIOS(parseBlocks(source))
        XCTAssertEqual(concepts.count, 3)
        XCTAssertEqual(concepts.first?.definition, definition + " Alpha.")
        let duplicates = ["Alpha", "Beta", "Gamma"].map { "- **\($0)**: \(definition)" }.joined(separator: "\n")
        XCTAssertEqual(extractSpontaneousConceptsFromBlocksIOS(parseBlocks(duplicates)).count, 1)
    }

    func testSpontaneousLearningUsesDefinitionsWithoutQuizzingSourceRecordsOrActionPlans() {
        let definitions = "- Atom: A basic unit of a chemical element.\n- Molecule: Two or more atoms bonded together.\n- Element: A substance made of one type of atom."
        XCTAssertEqual(extractSpontaneousConceptsFromBlocksIOS(parseBlocks("## Core concepts\n\n\(definitions)")).count, 3)
        for heading in ["Sources", "Key Sources", "References", "Step-by-step plan", "Next actions"] {
            XCTAssertTrue(extractSpontaneousConceptsFromBlocksIOS(parseBlocks("## \(heading)\n\n\(definitions)")).isEmpty, heading)
        }
        XCTAssertEqual(extractSpontaneousConceptsFromBlocksIOS(parseBlocks("## Sources\n\n- A supplied source record.\n\n## Core concepts\n\n\(definitions)")).count, 3)
    }

    func testProseScenariosPreserveRealAmountsVATAndUnsupportedSourceTerms() throws {
        XCTAssertEqual(try XCTUnwrap(parseQuickCalculatorSpec(titleRaw: "Calculator", bodyText: "$100 markup at 10%")).initialAmount, 100)
        XCTAssertEqual(try XCTUnwrap(parseQuickCalculatorSpec(titleRaw: "Calculator", bodyText: "€1.2k with 10% tax")).initialAmount, 1200)
        XCTAssertEqual(try XCTUnwrap(parseQuickCalculatorSpec(titleRaw: "Calculator", bodyText: "$2M loan at 6% for 5 years")).initialAmount, 2000000)
        XCTAssertEqual(try XCTUnwrap(parseQuickCalculatorSpec(titleRaw: "Calculator", bodyText: "$100 VAT at 10%")).mode, .markup)
        for wire in ["$1e6 loan at 10% for 5 years", "$1,00 discount 10%", "-$100 discount 10%", "$100 discount -10%", "$100 discount −10%", "$100 discount 1e2%", "$100 loan at 10%", "$100 loan at 10% for 0 years", "$100 loan at 10% for 99 years"] {
            XCTAssertNil(parseQuickCalculatorSpec(titleRaw: "Calculator", bodyText: wire), wire)
        }
    }

    func testActualScenarioFieldSeedsNeverRoundSmallAPRToZeroOrSourceAmountsToCents() {
        XCTAssertEqual(sourceScenarioNumberTextIOS(0.0012), "0.0012")
        XCTAssertEqual(sourceScenarioNumberTextIOS(0.0000000012), "0.0000000012")
        XCTAssertEqual(sourceScenarioNumberTextIOS(100.999), "100.999")
        XCTAssertEqual(sourceScenarioNumberTextIOS(1.2e20), "120000000000000000000")
        XCTAssertEqual(sourceScenarioNumberTextIOS(0), "0")
        XCTAssertEqual(sourceScenarioNumberTextIOS(.nan), "")
    }

    func testSourceLoanPaymentsKeepActualZeroAndSmallRateInterest() throws {
        // Independent 90-digit Decimal amortization references.
        XCTAssertEqual(try XCTUnwrap(sourceLoanMonthlyPaymentIOS(principal: 1000, apr: 0, years: 1)), 1000.0 / 12, accuracy: 1e-9)
        XCTAssertEqual(try XCTUnwrap(sourceLoanMonthlyPaymentIOS(principal: 1000, apr: 12, years: 1)), 88.84878867834170734, accuracy: 1e-9)
        XCTAssertEqual(try XCTUnwrap(sourceLoanMonthlyPaymentIOS(principal: 1e9, apr: 0.0012, years: 30)), 2778279.1966664201205, accuracy: 1e-6)
        XCTAssertEqual(try XCTUnwrap(sourceLoanMonthlyPaymentIOS(principal: 1e9, apr: 0.0000000012, years: 30)), 2777777.7782791666667, accuracy: 1e-6)
        XCTAssertNil(sourceLoanMonthlyPaymentIOS(principal: -1, apr: 5, years: 1))
        XCTAssertNil(sourceLoanMonthlyPaymentIOS(principal: 1000, apr: -5, years: 1))
        XCTAssertNil(sourceLoanMonthlyPaymentIOS(principal: 1000, apr: 5, years: 0))
        XCTAssertNil(sourceLoanMonthlyPaymentIOS(principal: .nan, apr: 5, years: 1))
    }

    func testOrdinaryMarkdownSourcesUseOneHeadingAndOnlyActualCompleteSourceDestinations() throws {
        for marker in ["1.", "-"] {
            let second = marker == "1." ? "2." : "-"
            let parsed = parseBlocks("## Key Sources\n\n\(marker) [IEA](https://www.iea.org/) - Supplied report.\n\(second) [IRENA](https://www.irena.org/) - Supplied publication.")
            let card = try XCTUnwrap(sourceCardForHeadingIOS(parsed[0], following: parsed[1]))
            XCTAssertEqual(card.title, "Key Sources")
            XCTAssertEqual(card.citations.map { $0.url }, ["https://www.iea.org/", "https://www.irena.org/"])
        }
        for name in ["IEA", "IRENA", "Nature", "React", "Tailwind", "MDN", "Python", "Nexly", "arXiv", "IEEE"] {
            let parsed = parseBlocks("## Sources\n\n1. \(name): supplied name only")
            XCTAssertNil(sourceCardForHeadingIOS(parsed[0], following: parsed[1]))
        }
        let mixed = parseBlocks("## Sources\n\n1. [Actual source](https://example.org/report)\n2. Missing destination must remain visible.")
        XCTAssertNil(sourceCardForHeadingIOS(mixed[0], following: mixed[1]))
        let unrelated = parseBlocks("## Priorities\n\n1. [Actual link](https://example.org/)")
        XCTAssertNil(sourceCardForHeadingIOS(unrelated[0], following: unrelated[1]))
    }

    func testSharedControlsRequireActualOptionsAndFiniteSourceStarts() throws {
        for items in ["[]", "[\"Only choice\"]", "[\"Actual\",\"\"]", "[\"Actual\",{}]"] {
            XCTAssertFalse(isValidWidget(kind: "collab", json: "{\"surface\":\"vote\",\"items\":\(items)}"))
        }
        XCTAssertTrue(isValidWidget(kind: "collab", json: #"{"surface":"vote","items":["First","Second"]}"#))
        XCTAssertTrue(isValidWidget(kind: "collab", json: #"{"surface":"counter","start":12.5}"#))
        XCTAssertFalse(isValidWidget(kind: "collab", json: #"{"surface":"counter","start":true}"#))
        XCTAssertFalse(isValidWidget(kind: "collab", json: #"{"surface":"unknown"}"#))
    }

    func testCitationAuthorityComesFromTheActualSafeHostNotModelLabelsOrURLSubstrings() {
        XCTAssertEqual(citationSourceURLIOS("https://www.nature.com/article")?.host, "www.nature.com")
        XCTAssertTrue(isAcademicCitationDomain("untrusted.example", "https://www.nature.com/article"))
        for raw in ["https://untrusted.example/nature.com?publisher=arxiv.org", "https://nature.com.untrusted.example/paper", "https://untrusted.example/?from=university.edu"] {
            XCTAssertFalse(isAcademicCitationDomain("nature.com", raw))
        }
        for raw in ["javascript:alert(1)", "file:///private/file", "https://nature.com@untrusted.example/paper"] {
            XCTAssertNil(citationSourceURLIOS(raw))
            XCTAssertFalse(isAcademicCitationDomain("nature.com", raw))
        }
    }

    func testTimersAcceptOnlyActualWholeSecondsWithoutPriceDefaults() {
        for json in [#"{"type":"timer"}"#, #"{"type":"timer","durationSeconds":5}"#, #"{"type":"timer","items":[{"price":25}]}"#] {
            XCTAssertTrue(isValidWidget(kind: "app", json: json))
            XCTAssertEqual(validatedWidgetKindIOS(kind: "app", json: json), "app")
        }
        for raw in ["0", "-1", "1.5", "604801", "true", "null", #""$25""#, #""25%""#, #""25""#] {
            let json = "{\"type\":\"timer\",\"durationSeconds\":" + raw + "}"
            XCTAssertFalse(isValidWidget(kind: "app", json: json))
            XCTAssertNil(validatedWidgetKindIOS(kind: "app", json: json))
        }
    }

    func testChecklistRefusesMalformedAndOversizedSourceWithoutKindHealing() throws {
        let overflow = try JSONSerialization.data(withJSONObject: ["items": (1...31).map { ["label": "Task \($0)"] }])
        for json in [#"{"items":[{"label":"Actual"},{"alien":42}]}"#, #"{"items":["Actual",42]}"#, String(decoding: overflow, as: UTF8.self)] {
            XCTAssertFalse(isValidWidget(kind: "checklist", json: json))
            XCTAssertNil(validatedWidgetKindIOS(kind: "checklist", json: json))
        }
    }

    func testWholeJSONPreservesMachineStringsBeforeMarkdownAndStreaming() {
        let sources = ["{}", "[]", "[1,2,3]", #"[{"literal":"**bold** :rocket: 😀"}]"#,
            " \r\n" + #"{"literal":"**bold** :rocket: 😀","nested":{"x":true}}"# + "\r\n "]
        for source in sources {
            let blocks = parseBlocks(source)
            XCTAssertEqual(blocks.count, 1)
            guard case .codeBlock(let language, let code, let open, _)? = blocks.first else {
                XCTFail("Non-widget JSON must stay source: \(source)"); continue
            }
            XCTAssertEqual(language, "json"); XCTAssertFalse(open)
            XCTAssertEqual(Array(code.utf8), Array(source.utf8))
            var previous = "", streamed: [Block] = []
            for end in 1...source.count {
                let current = String(source.prefix(end))
                streamed = parseStreamingBlocks(previousContent: previous, previousBlocks: streamed, content: current)
                XCTAssertEqual(streamed, parseBlocks(current))
                previous = current
            }
        }
    }

    func testInvalidWizardCannotBypassIOSWidgetValidation() {
        let source = #"{"type":"wizard","rows":[[42]],"columns":["Actual"]}"#
        XCTAssertFalse(isValidWidget(kind: "app", json: source))
        XCTAssertNil(inferWidgetKind(source))
        XCTAssertNil(validatedWidgetKindIOS(kind: "app", json: source))
    }

    func testContextualCodeActionsRetainActualDraftsAndStayResponseScoped() {
        let first = ResponseCodeActionsIOS(), other = ResponseCodeActionsIOS()
        var edits = 0
        first.editFirst()
        first.register(7, entry: .init(language: "javascript", source: "original", edit: { edits += 1 }))
        XCTAssertEqual(edits, 1)
        first.update(7, source: "actually edited")
        XCTAssertEqual(first.source(7)?.1, "actually edited")
        first.remove(7)
        XCTAssertEqual(first.source(7)?.1, "actually edited")
        XCTAssertNil(other.source(7))
        first.register(7, entry: .init(language: "javascript", source: "actually edited", edit: { edits += 1 }))
        first.editFirst()
        XCTAssertEqual(edits, 2)
    }

    func testSourceArchivePreservesRealBytesAndKnownCRCWithoutInventingAProject() throws {
        let source = "বাংলা 日本語\r\n😀\u{0000}"
        let archive = try responseSourceArchiveIOS([("python", source), ("../../unsafe", "123456789")])
        func number(_ offset: Int, _ length: Int) -> UInt32 {
            (0..<length).reduce(0) { $0 | (UInt32(archive[offset + $1]) << UInt32($1 * 8)) }
        }
        var files: [String: Data] = [:], cursor = 0
        while number(cursor, 4) == 0x04034b50 {
            XCTAssertEqual(number(cursor + 8, 2), 0) // Stored ZIP, no fake compressed size.
            let size = Int(number(cursor + 18, 4)), nameSize = Int(number(cursor + 26, 2))
            let name = String(decoding: archive[(cursor + 30)..<(cursor + 30 + nameSize)], as: UTF8.self)
            let dataStart = cursor + 30 + nameSize + Int(number(cursor + 28, 2))
            files[name] = archive.subdata(in: dataStart..<(dataStart + size))
            if name == "snippet-2.txt" { XCTAssertEqual(number(cursor + 14, 4), 0xcbf43926) } // Published CRC-32 test vector.
            cursor = dataStart + size
        }
        XCTAssertEqual(number(cursor, 4), 0x02014b50)
        XCTAssertEqual(number(archive.count - 22, 4), 0x06054b50)
        XCTAssertEqual(number(archive.count - 12, 2), 3)
        XCTAssertEqual(number(archive.count - 6, 4), UInt32(cursor))
        XCTAssertEqual(files.keys.sorted(), ["README.txt", "snippet-1.py", "snippet-2.txt"])
        XCTAssertEqual(files["snippet-1.py"], Data(source.utf8))
        XCTAssertTrue(String(decoding: files["README.txt"]!, as: UTF8.self).contains("not a complete runnable project"))
        XCTAssertEqual(responseSourceExtensionIOS("JAVASCRIPT"), "js")
        XCTAssertThrowsError(try responseSourceArchiveIOS([]))
        let attachment = XCTAttachment(data: archive, uniformTypeIdentifier: "public.zip-archive")
        attachment.name = "response-source-archive.zip"; attachment.lifetime = .keepAlways; add(attachment)
    }

    func testSharedResponseReportIncludesActualTableAndWidgetData() {
        let blocks = parseBlocks("| Name | Amount |\n| --- | --- |\n| Actual | 42 |\n\n```chart\n{\"labels\":[\"A\"],\"values\":[73]}\n```")
        let report = extractCopyableProseSummary(blocks, citationsOnly: false)
        XCTAssertTrue(report.contains("Actual\t42"))
        XCTAssertTrue(report.contains("73"))
        XCTAssertEqual(extractCopyableProseSummary([], citationsOnly: true), "")
    }

    func testNativeFencesPreserveEverySourceSeparatorAndBlankLine() throws {
        let sources = ["", "\n", "\r\n", "\n\nconst value = 7;\n\n", "\r\nconst value = 7;\r\n\r\n", "বাংলা 日本語\r\n\n\r\n", "const key = \"😀\";\rkeep"]
        for marker in ["```", "````", "~~~~~"] {
            for separator in ["\n", "\r\n"] {
                for source in sources {
                    let blocks = parseBlocks(marker + "javascript" + separator + source + separator + marker)
                    guard blocks.count == 1, case .codeBlock(let language, let code, let open, _) = blocks[0] else {
                        XCTFail("Source must remain one code block"); continue
                    }
                    XCTAssertEqual(language, "javascript")
                    // Byte comparison is deliberate: Swift String equality
                    // otherwise permits canonically equivalent source changes.
                    XCTAssertEqual(Array(code.utf8), Array(source.utf8))
                    XCTAssertFalse(open)
                }
            }
        }
        let source = "first\n```\n~~~\nlast\n\n"
        guard case .codeBlock(_, let code, let open, _)? = parseBlocks("````javascript\n" + source + "\n`````").first else { return XCTFail("Expected wide code fence") }
        XCTAssertEqual(Array(code.utf8), Array(source.utf8))
        XCTAssertFalse(open)
        guard case .codeBlock(_, let partial, _, _)? = parseBlocks("```javascript\nconst value = 7;\r").first else { return XCTFail("Expected partial source") }
        XCTAssertEqual(Array(partial.utf8), Array("const value = 7;\r".utf8))
        let html = "<!doctype html>\r\n<html>\r\n\r\n<body>Actual source</body></html>\r\n"
        guard case .html(_, let parsedHTML, _, _)? = parseBlocks("~~~~html\r\n" + html + "\r\n~~~~").first else { return XCTFail("Expected HTML") }
        XCTAssertEqual(Array(parsedHTML.utf8), Array(html.utf8))
        guard case .widget(let kind, _, _, _)? = parseBlocks("~~~chart\n{\"labels\":[\"A\"],\"values\":[7]}\n~~~").first else { return XCTFail("Expected widget") }
        XCTAssertEqual(kind, "chart")
    }

    func testNativeStreamingMatchesFullParseAtEveryFencePrefix() {
        let prefix = "Intro.\n\nStable paragraph.\n\n"
        let documents = [
            prefix + "````javascript\r\n\r\nconst value = 7;\r\n\r\n```\r\nconsole.log(value);\n\n````\nAfter fence.\n\nFinal.",
            prefix + "~~~~html\r\n<!doctype html>\r\n\r\n<html><body><button>Run</button></body></html>\r\n~~~~\r\nAfter app.",
            prefix + "> ````javascript\r\n> \r\n> const value = 7;\r\n> \r\n> ````\r\n\r\nAfter quote."
        ]
        for document in documents {
            var previous = ""
            var blocks: [Block] = []
            let bytes = Array(document.utf8)
            // ASCII fixture bytes intentionally split CRLF between flushes.
            for end in 1...bytes.count {
                let current = String(decoding: bytes.prefix(end), as: UTF8.self)
                blocks = parseStreamingBlocks(previousContent: previous, previousBlocks: blocks, content: current)
                XCTAssertEqual(blocks, parseBlocks(current), "Streaming corrupted prefix \(end)")
                previous = current
            }
        }
    }

    func testCodeDisplayAndEditorPreserveActualSource() throws {
        let source = #"const data = {"😀":7,"🌅":9}; console.log(data["😀"]+data["🌅"], "😀".length);"#
        let blocks = parseBlocks("```javascript\n\(source)\n```")
        guard case .codeBlock(_, let parsed, _, _)? = blocks.first else { return XCTFail("Source must parse as code") }
        XCTAssertEqual(parsed, source)
        for value in [source, #"console.log("\ud83d\ude00")"#, "বাংলা 日本語", "🏳️‍🌈"] {
            let encoded = CodeDisplayIOS.encodeEditor(value)
            XCTAssertEqual(try CodeDisplayIOS.decodeEditor(encoded), value)
            XCTAssertEqual(CodeDisplayIOS.escape(encoded), encoded)
        }
        for invalid in ["'not JSON'", "\"unfinished", "\"ok\" false", "[]", "\"bad\\x\""] {
            XCTAssertThrowsError(try CodeDisplayIOS.decodeEditor(invalid))
        }
    }

    @MainActor
    func testGeneratedJavaScriptWorkerPreservesUnicodeStopsLoopsAndRejectsNetwork() async throws {
        let source = #"const data = {"😀":7,"🌅":9}; console.log(data["😀"]+data["🌅"], "😀".length);"#
        let cases = [
            (source, "16 2\nCompleted."),
            ("self.addEventListener('securitypolicyviolation',e=>console.log('BLOCKED '+e.effectiveDirective));try{await fetch('https://github.com/');}catch{console.log('FETCH REFUSED');}await new Promise(r=>setTimeout(r,100));", "FETCH REFUSED"),
            ("while(true) {}", "Stopped: execution exceeded 2 seconds.")
        ]
        let old = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }.first { $0.isKeyWindow }
        let window: UIWindow
        if let scene = old?.windowScene { window = UIWindow(windowScene: scene) }
        else { window = UIWindow(frame: UIScreen.main.bounds) }
        defer { window.isHidden = true; window.rootViewController = nil; old?.makeKeyAndVisible() }
        func findWeb(_ view: UIView) -> WKWebView? {
            if let web = view as? WKWebView { return web }
            for child in view.subviews { if let web = findWeb(child) { return web } }
            return nil
        }
        for (program, expected) in cases {
            let host = UIHostingController(rootView: HtmlBlockView(kind: "html", source: JavaScriptWorkerSandboxIOS.document(source: program), open: false))
            window.rootViewController = host; window.makeKeyAndVisible()
            let deadline = Date().addingTimeInterval(20)
            var observed = ""
            while Date() < deadline {
                host.view.layoutIfNeeded()
                if let web = findWeb(host.view), let output = try? await web.evaluateJavaScript("document.getElementById('out')?.textContent || ''") as? String {
                    observed = output
                    if output.contains(expected) { break }
                }
                try await Task.sleep(nanoseconds: 50_000_000)
            }
            XCTAssertTrue(observed.contains(expected), "Actual WKWebView worker: expected \(expected), observed \(observed)")
            XCTAssertFalse(observed.contains("😀"))
        }
    }

    @MainActor
    func testWorkerCSPPreventsActualHTTPAndPolicyOnlyCounterfactualCanFetch() async throws {
        // WebKit can refuse a Worker fetch without dispatching the Chromium
        // securitypolicyviolation event. A rejected fetch alone proves nothing:
        // count real HTTP requests and change ONLY the policy as a control.
        let server = try WorkerHTTPProbe()
        let ready = expectation(description: "HTTP probe listening")
        server.listener.stateUpdateHandler = { if case .ready = $0 { ready.fulfill() } }
        server.listener.start(queue: server.queue)
        defer { server.listener.cancel() }
        await fulfillment(of: [ready], timeout: 5)
        let port = try XCTUnwrap(server.listener.port)
        let endpoint = "http://127.0.0.1:\(port.rawValue)/worker-probe"
        let program = "try { const response = await fetch('\(endpoint)', {cache:'no-store'}); console.log('PROBE ' + await response.text()); } catch { console.log('FETCH REFUSED'); }"
        let production = JavaScriptWorkerSandboxIOS.document(source: program)
        XCTAssertTrue(production.contains("connect-src 'none'"))
        // Cold WebKit networking can take longer than the unchanged 2-second
        // worker execution limit. Initialize the actual network path before
        // comparing policies, using this same real view and data store. The
        // warm-up must receive the real response; a fake/unreachable server
        // cannot turn a blocked request into a passing CSP test.
        let warmup = "<output id='warmup'></output><script>fetch('\(endpoint)',{cache:'no-store'}).then(r=>r.text()).then(t=>document.getElementById('warmup').textContent=t).catch(e=>document.getElementById('warmup').textContent=String(e))</script>"
        let host = UIHostingController(rootView: HtmlBlockView(kind: "html", source: warmup, open: false))
        let old = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap { $0.windows }.first { $0.isKeyWindow }
        let window: UIWindow
        if let scene = old?.windowScene { window = UIWindow(windowScene: scene) }
        else { window = UIWindow(frame: UIScreen.main.bounds) }
        defer { window.isHidden = true; window.rootViewController = nil; old?.makeKeyAndVisible() }
        // Give the production WebView a real, nonzero foreground viewport.
        // A scene window can otherwise leave a freshly hosted WK view at its
        // initial zero bounds while WebKit waits for presentation readiness.
        window.frame = old?.frame ?? UIScreen.main.bounds
        window.rootViewController = host
        host.view.frame = window.bounds
        window.makeKeyAndVisible()
        window.layoutIfNeeded()
        host.view.layoutIfNeeded()
        func findWeb(_ view: UIView) -> WKWebView? {
            if let web = view as? WKWebView { return web }
            for child in view.subviews { if let web = findWeb(child) { return web } }
            return nil
        }
        var warmupResult = ""
        let warmupDeadline = Date().addingTimeInterval(45)
        while Date() < warmupDeadline {
            host.view.layoutIfNeeded()
            if let web = findWeb(host.view), let text = try? await web.evaluateJavaScript("document.getElementById('warmup')?.textContent || ''") as? String {
                warmupResult = text
                if text == "causal-response" { break }
            }
            try await Task.sleep(nanoseconds: 50_000_000)
        }
        let readiness: String
        if let web = findWeb(host.view) {
            readiness = (try? await web.evaluateJavaScript("JSON.stringify({ready:document.readyState,visibility:document.visibilityState,url:location.href,body:document.body?.innerText})")) as? String ?? "No evaluable document; loading=\(web.isLoading), bounds=\(web.bounds)"
        } else { readiness = "No production WKWebView; host bounds=\(host.view.bounds)" }
        XCTAssertEqual(warmupResult, "causal-response", "Actual WebKit network initialization must succeed: \(server.requestLines); \(readiness)")
        XCTAssertEqual(server.requestCount, 1)
        guard warmupResult == "causal-response", server.requestCount == 1 else { return }
        for allowed in [false, true] {
            let baseline = server.requestCount
            let document = allowed ? production.replacingOccurrences(of: "connect-src 'none'", with: "connect-src *") : production
            host.rootView = HtmlBlockView(kind: "html", source: document, open: false)
            var observed = ""
            let expected = allowed ? "PROBE causal-response" : "FETCH REFUSED"
            let deadline = Date().addingTimeInterval(15)
            while Date() < deadline {
                host.view.layoutIfNeeded()
                if let web = findWeb(host.view), let text = try? await web.evaluateJavaScript("document.getElementById('out')?.textContent || ''") as? String {
                    observed = text
                    if text.contains(expected) && text.contains("Completed.") { break }
                }
                try await Task.sleep(nanoseconds: 50_000_000)
            }
            XCTAssertTrue(observed.contains(expected), "Policy-only control allowed=\(allowed): \(observed); actual requests=\(server.requestLines)")
            XCTAssertEqual(server.requestCount - baseline, allowed ? 1 : 0, "CSP must prevent the actual request, not merely fail DNS, CORS or ATS")
        }
    }

    func testPieLabelsUseActualCategoryInsteadOfFirstAxisLabelForEverySeries() {
        XCTAssertEqual(chartSliceLabelIOS(name: "Organic", labels: ["A", "B"], seriesIndex: 1), "Organic")
        XCTAssertEqual(chartSliceLabelIOS(name: "", labels: ["A", "B"], seriesIndex: 1), "B")
        XCTAssertEqual(chartSliceLabelIOS(name: "", labels: [], seriesIndex: 2), "Series 3")
    }

    func testPollNeverInventsAnInitialVoteOrCrashesOnInvalidCounts() {
        XCTAssertEqual(pollSourceVotesIOS("First option"), 0)
        XCTAssertEqual(pollSourceVotesIOS(["label": "Second option"]), 0)
        XCTAssertEqual(pollSourceVotesIOS(["label": "Source", "votes": 12]), 12)
        let invalid: [Any] = [-1, 1.5, true, "invalid", Double.infinity, Double.nan]
        for value in invalid { XCTAssertNil(pollSourceVotesIOS(["votes": value])) }
    }

    func testChartNumbersRejectMissingBooleanAndNonFiniteMeasurements() {
        let invalidValues: [Any] = [NSNull(), true, false, "", "missing", "NaN", "Infinity", Double.infinity, Double.nan]
        for invalid in invalidValues {
            XCTAssertNil(chartValueIOS(invalid), "\(invalid)")
        }
        XCTAssertNil(chartValueIOS(nil))
        XCTAssertNil(chartObservationValueIOS(["label": "Missing"]))
        XCTAssertNil(chartObservationValueIOS([1]))
        XCTAssertNil(chartObservationValueIOS([1, "invalid"]))
        XCTAssertEqual(chartValueIOS(0), 0)
        XCTAssertEqual(chartValueIOS("-3.5"), -3.5)
        XCTAssertEqual(chartObservationValueIOS([1, -20]), -20)
        XCTAssertEqual(chartObservationValueIOS(["label": "Real", "value": 12.5]), 12.5)
    }

    func testSignedChartDomainPreservesZeroNegativeAndAllObservations() {
        let values: [Double] = [-20, 0, 10]
        let domain = chartDomainIOS(values)
        XCTAssertEqual(domain.lower, -20)
        XCTAssertEqual(domain.upper, 10)
        XCTAssertEqual(chartHeightFractionIOS(-20, domain: domain), 1, accuracy: 1e-9)
        XCTAssertEqual(chartHeightFractionIOS(0, domain: domain), 1.0 / 3, accuracy: 1e-9)
        XCTAssertEqual(chartHeightFractionIOS(10, domain: domain), 0, accuracy: 1e-9)
        XCTAssertEqual(chartSignedTotalIOS(values), -10)
        XCTAssertEqual(chartSignedTotalIOS(Array(repeating: 1, count: 31)), 31)
        let zero = chartDomainIOS([0, 0])
        XCTAssertEqual(chartHeightFractionIOS(0, domain: zero), 1)
        XCTAssertEqual(chartDomainIOS([-5, -2]).upper, 0)
    }

    @MainActor
    func testGeneratedHtmlExecutesAndCleansDynamicEmojiInProductionWebView() async throws {
        XCTAssertNotNil(DOMDisplayPolicyIOS.script, "The shared policy must be packaged in the actual app bundle")
        let source = #"<button id="run" title="Increase &#x1F600;">Increase &#x1F600;</button><output id="result">0</output><svg data-custom-glass="true"><path d="M0 0L8 8"/></svg><script>window.marker=String.fromCodePoint(0x1F600);window.count=0;document.getElementById('run').onclick=function(){window.count++;document.getElementById('result').textContent=window.count+window.marker;};window.closedRoot=document.body.appendChild(document.createElement('div')).attachShadow({mode:'closed'});closedRoot.innerHTML='<button>Shadow &#x1F600;</button><output>0</output>';window.shadowCount=0;closedRoot.querySelector('button').onclick=function(){shadowCount++;closedRoot.querySelector('output').textContent=shadowCount+marker;};var canvas=document.body.appendChild(document.createElement('canvas'));canvas.id='emoji-canvas';canvas.width=100;canvas.height=40;var ctx=canvas.getContext('2d');ctx.font='28px sans-serif';ctx.fillText(marker,0,30);ctx.strokeText(marker,40,30);</script>"#
        let host = UIHostingController(rootView: HtmlBlockView(kind: "html", source: source, open: false))
        let oldKeyWindow = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }.first { $0.isKeyWindow }
        let window: UIWindow
        if let scene = oldKeyWindow?.windowScene { window = UIWindow(windowScene: scene) }
        else { window = UIWindow(frame: UIScreen.main.bounds) }
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true; window.rootViewController = nil; oldKeyWindow?.makeKeyAndVisible() }
        func findWebView(_ view: UIView) -> WKWebView? {
            if let web = view as? WKWebView { return web }
            for child in view.subviews { if let web = findWebView(child) { return web } }
            return nil
        }
        var loadedWeb: WKWebView?
        let deadline = Date().addingTimeInterval(20)
        while Date() < deadline {
            host.view.layoutIfNeeded()
            if let web = findWebView(host.view),
               let ready = try? await web.evaluateJavaScript("document.readyState === 'complete' && typeof window.marker === 'string'"),
               (ready as? Bool) == true {
                loadedWeb = web
                break
            }
            try await Task.sleep(nanoseconds: 50_000_000)
        }
        let web = try XCTUnwrap(loadedWeb, "The production HtmlBlock must load and execute its real document")
        let initial = try await web.evaluateJavaScript("window.__gsDisplayEmojiPolicy === true && document.getElementById('run').textContent === 'Increase ' && document.getElementById('run').title === 'Increase '")
        XCTAssertEqual(initial as? Bool, true)
        // Real DOM event execution in WKWebView (not a mocked evaluator).
        // An OS-level pointer tap is covered separately on Android.
        _ = try await web.evaluateJavaScript("document.getElementById('run').click(); true")
        let afterClick = try await web.evaluateJavaScript("window.count === 1 && document.getElementById('result').textContent === '1'")
        XCTAssertEqual(afterClick as? Bool, true)
        let marker = try await web.evaluateJavaScript("window.marker.codePointAt(0)")
        XCTAssertEqual((marker as? NSNumber)?.intValue, 128512)
        let shadow = try await web.evaluateJavaScript("window.closedRoot.querySelector('button').textContent === 'Shadow '")
        XCTAssertEqual(shadow as? Bool, true)
        _ = try await web.evaluateJavaScript("window.closedRoot.querySelector('button').click(); true")
        let shadowClick = try await web.evaluateJavaScript("window.shadowCount === 1 && window.closedRoot.querySelector('output').textContent === '1'")
        XCTAssertEqual(shadowClick as? Bool, true)
        let canvasText = try await web.evaluateJavaScript("Array.from(document.getElementById('emoji-canvas').getContext('2d').getImageData(0,0,100,40).data).every(value=>value===0)")
        XCTAssertEqual(canvasText as? Bool, true, "Actual canvas fillText/strokeText must not display emoji glyphs")
        let canvasVector = try await web.evaluateJavaScript("(function(){const ctx=document.getElementById('emoji-canvas').getContext('2d');ctx.fillRect(0,0,4,4);return ctx.getImageData(0,0,1,1).data[3]===255;})()")
        XCTAssertEqual(canvasVector as? Bool, true, "The display policy must not disable actual canvas rendering")
        let vector = try await web.evaluateJavaScript("!!document.querySelector('svg[data-custom-glass] path')")
        XCTAssertEqual(vector as? Bool, true)
    }

    @MainActor
    func testGeneratedReactTsxUsesActualWebKitTasksPersistenceAndResponseIsolation() async throws {
        let contractURL = try XCTUnwrap(Bundle.main.url(forResource: "react-preview-acceptance", withExtension: "json"))
        let contract = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: contractURL)) as? [String: Any])
        let source = try XCTUnwrap(contract["source"] as? String)
        let scope = "react-acceptance:" + UUID().uuidString
        let old = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }.first { $0.isKeyWindow }
        let window: UIWindow
        if let scene = old?.windowScene { window = UIWindow(windowScene: scene) }
        else { window = UIWindow(frame: UIScreen.main.bounds) }
        defer { window.isHidden = true; window.rootViewController = nil; old?.makeKeyAndVisible() }
        func findWeb(_ view: UIView) -> WKWebView? {
            if let web = view as? WKWebView { return web }
            for child in view.subviews { if let web = findWeb(child) { return web } }
            return nil
        }
        func observe(_ web: WKWebView, _ expression: String) async throws {
            let deadline = Date().addingTimeInterval(20)
            var success = false
            while Date() < deadline {
                if let result = try? await web.evaluateJavaScript(expression), (result as? Bool) == true { success = true; break }
                try await Task.sleep(nanoseconds: 50_000_000)
            }
            let text = (try? await web.evaluateJavaScript("document.body.innerText")) as? String ?? "No document"
            XCTAssertTrue(success, "Actual React/WebKit expression failed: \(expression); document: \(text)")
        }
        func load(_ program: String, _ identity: String, error: Bool = false) async throws -> WKWebView {
            let host = UIHostingController(rootView: ReactPreviewIOS(source: program, scope: identity))
            window.rootViewController = host; window.makeKeyAndVisible(); host.view.layoutIfNeeded()
            let deadline = Date().addingTimeInterval(5)
            var rendered: WKWebView?
            while Date() < deadline {
                host.view.layoutIfNeeded(); rendered = findWeb(host.view)
                if rendered != nil { break }
                try await Task.sleep(nanoseconds: 50_000_000)
            }
            let web = try XCTUnwrap(rendered, "The production React renderer must create an actual WKWebView")
            try await observe(web, "document.getElementById('status')?.textContent === '\(error ? "React preview error" : "React preview running")'")
            return web
        }
        func replaceInput(_ web: WKWebView, _ selector: String, _ text: String) async throws {
            let selectorData = try JSONSerialization.data(withJSONObject: selector, options: [.fragmentsAllowed])
            let textData = try JSONSerialization.data(withJSONObject: text, options: [.fragmentsAllowed])
            let selectorJS = String(decoding: selectorData, as: UTF8.self), textJS = String(decoding: textData, as: UTF8.self)
            _ = try await web.evaluateJavaScript("(function(){const input=document.querySelector(\(selectorJS));Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,\(textJS));input.dispatchEvent(new Event('input',{bubbles:true}));return true;})()")
        }
        var web = try await load(source, scope)
        try await observe(web, "getComputedStyle(document.querySelector('section')).paddingTop === getComputedStyle(document.documentElement).fontSize && getComputedStyle(document.querySelector('h1')).fontWeight === '700'")
        try await replaceInput(web, "[aria-label='New task']", "iOS actual task")
        _ = try await web.evaluateJavaScript("document.querySelector('button[type=submit]').click();true")
        try await observe(web, "document.getElementById('count').textContent === '0/1 complete'")
        _ = try await web.evaluateJavaScript("document.querySelector('input[type=checkbox]').click();true")
        try await observe(web, "document.getElementById('count').textContent === '1/1 complete'")
        try await replaceInput(web, "[aria-label='Task title']", "Edited iOS task")
        try await observe(web, "JSON.parse(localStorage.getItem('tasks'))[0].title === 'Edited iOS task'")
        web = try await load(source, scope)
        try await observe(web, "document.querySelector('[aria-label=\"Task title\"]').value === 'Edited iOS task' && document.querySelector('input[type=checkbox]').checked")
        web = try await load(source, scope + ":other-response")
        try await observe(web, "document.getElementById('count').textContent === '0/0 complete'")
        web = try await load(source, scope)
        try await observe(web, "document.querySelector('[aria-label=\"Task title\"]').value === 'Edited iOS task'")
        _ = try await web.evaluateJavaScript("document.querySelector('li button').click();true")
        try await observe(web, "document.getElementById('count').textContent === '0/0 complete' && localStorage.getItem('tasks') === '[]'")
        web = try await load(source, scope)
        try await observe(web, "document.querySelectorAll('li').length === 0 && !window.Android && !window.webkit?.messageHandlers?.gsResize && window.__gsDisplayEmojiPolicy === true")
        // Execute the exact reference answer through the real parser and WK
        // runtime too: a source listing alone cannot prove its filters work.
        let referenceURL = try XCTUnwrap(Bundle.main.url(forResource: "response-section-references", withExtension: "json"))
        let referenceContract = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: referenceURL)) as? [String: Any])
        let references = try XCTUnwrap(referenceContract["fixtures"] as? [[String: Any]])
        let reference = try XCTUnwrap(references.first { $0["id"] as? String == "03-react-code" })
        let referenceWire = try XCTUnwrap(reference["wire"] as? String)
        let referenceSource = try XCTUnwrap(parseBlocks(referenceWire).compactMap { block -> String? in
            if case .codeBlock(let language, let code, _, _) = block, language == "jsx" { return code }
            return nil
        }.first)
        web = try await load(referenceSource, scope + ":reference")
        for title in ["Reference completed", "Reference active"] {
            try await replaceInput(web, "[aria-label='New task']", title)
            _ = try await web.evaluateJavaScript("document.querySelector('form button').click();true")
            try await observe(web, "JSON.parse(localStorage.getItem('tasks') || '[]').some(t=>t.title === '\(title)')")
        }
        _ = try await web.evaluateJavaScript("document.querySelector('article input[type=checkbox]').click();true")
        func selectFilter(_ value: String) async throws {
            _ = try await web.evaluateJavaScript("(function(){const select=document.querySelector('[aria-label=\"Task filter\"]');select.value='\(value)';select.dispatchEvent(new Event('change',{bubbles:true}));return true;})()")
        }
        try await selectFilter("completed")
        try await observe(web, "document.querySelectorAll('article').length === 1 && document.querySelector('article input[type=checkbox]').checked")
        try await selectFilter("active")
        try await observe(web, "document.querySelectorAll('article').length === 1 && document.querySelector('article input[type=checkbox]').getAttribute('aria-label') === 'Reference active'")
        try await replaceInput(web, "[aria-label='Edit Reference active']", "Reference edited")
        try await observe(web, "JSON.parse(localStorage.getItem('tasks')).some(t=>t.title==='Reference edited' && !t.done)")
        try await selectFilter("all")
        try await observe(web, "document.querySelectorAll('article').length === 2")
        web = try await load(referenceSource, scope + ":reference")
        try await observe(web, "document.querySelectorAll('article').length === 2 && document.querySelector('[aria-label=\"Reference completed\"]').checked && document.querySelector('[aria-label=\"Edit Reference edited\"]').value === 'Reference edited'")
        try await selectFilter("completed")
        try await observe(web, "document.querySelectorAll('article').length === 1")
        _ = try await web.evaluateJavaScript("document.querySelector('article button').click();true")
        try await observe(web, "document.querySelectorAll('article').length === 0 && JSON.parse(localStorage.getItem('tasks')).length === 1 && JSON.parse(localStorage.getItem('tasks'))[0].title === 'Reference edited'")
        let firstOrigin = ReactPreviewRuntimeIOS.origin(scope: scope)
        let secondOrigin = ReactPreviewRuntimeIOS.origin(scope: scope + ":other-response")
        XCTAssertNotEqual(firstOrigin, secondOrigin)
        XCTAssertEqual(firstOrigin.host?.split(separator: ".").count, 2, "Every snippet needs a distinct registrable domain, not a cookie-sharing parent domain")
        web = try await load("import missing from 'not-bundled';export default function App(){return <p>{missing}</p>}", scope, error: true)
        try await observe(web, "document.getElementById('error').textContent.includes('Unsupported dependency')")
        web = try await load("export default function App( { return <p>Invalid</p> }", scope, error: true)
        try await observe(web, "document.getElementById('error').textContent.includes('Unexpected')")
        web = try await load("export default function App(){return <p id='emoji'>{String.fromCodePoint(128512)} Clean</p>}", scope)
        try await observe(web, "!document.getElementById('emoji').textContent.includes(String.fromCodePoint(128512))")
    }

    func testAllRowsAndStrictDescendingTableSort() {
        XCTAssertEqual(widgetTablePageSizeIOS(selection: -1, rowCount: 73), 73)
        XCTAssertEqual(widgetTablePageSizeIOS(selection: -1, rowCount: 2), 2)
        XCTAssertEqual(widgetTablePageSizeIOS(selection: -1, rowCount: 0), 1)
        XCTAssertEqual(widgetTablePageSizeIOS(selection: 3, rowCount: 73), 3)
        for cell in ["20", "$20", "same"] {
            XCTAssertFalse(widgetTablePrecedesIOS(cell, cell, ascending: true))
            XCTAssertFalse(widgetTablePrecedesIOS(cell, cell, ascending: false))
        }
        XCTAssertFalse(widgetTablePrecedesIOS("1K", "1000", ascending: false))
        let values = ["2", "10", "2", "-3"]
        XCTAssertEqual(values.sorted { widgetTablePrecedesIOS($0, $1, ascending: false) }, ["10", "2", "2", "-3"])
    }

    func testTooltipDeltaRequiresAnActualPriorPoint() {
        XCTAssertNil(chartStepPercentIOS(values: [20, 30], index: 0))
        XCTAssertNil(chartStepPercentIOS(values: [0, 30], index: 1))
        XCTAssertNil(chartStepPercentIOS(values: [20], index: 5))
        XCTAssertEqual(chartStepPercentIOS(values: [20, 30], index: 1)!, 50, accuracy: 1e-9)
    }
    func testZeroBaselineChartGrowthIsUndefined() {
        XCTAssertNil(chartGrowthPercentIOS(first: 0, last: 25))
        XCTAssertNil(chartGrowthPercentIOS(first: 0, last: 0))
        XCTAssertEqual(chartGrowthPercentIOS(first: 20, last: 30)!, 50, accuracy: 1e-9)
        XCTAssertEqual(chartGrowthPercentIOS(first: -20, last: -10)!, 50, accuracy: 1e-9)
    }
    func testPercentRatiosHaveNoMagnitudeDiscontinuity() {
        XCTAssertEqual(calcPercentDisplayIOS(0.99, format: "percent"), 99, accuracy: 1e-9)
        XCTAssertEqual(calcPercentDisplayIOS(1.0, format: "percent"), 100, accuracy: 1e-9)
        XCTAssertEqual(calcPercentDisplayIOS(1.01, format: "percent"), 101, accuracy: 1e-9)
        XCTAssertEqual(calcPercentDisplayIOS(-1.2, format: "percent"), -120, accuracy: 1e-9)
        XCTAssertEqual(calcPercentDisplayIOS(0.99, format: "number"), 0.99, accuracy: 1e-9)
    }
    func testDecodedWidgetPresentationPreservesMachineStrings() throws {
        let data = #"{"title":"\ud83d\ude80 Launch","label":"\u00a9\ufe0f Result \ud83d\ude00","icon":"\ud83d\ude80","fields":[{"key":"amount","value":1200}],"expr":"amount*2","code":"print(\"\ud83d\ude00\")"}"#.data(using: .utf8)!
        let source = try JSONSerialization.jsonObject(with: data) as! [String: Any]
        let safe = sanitizeWidgetPresentationIOS(source) as! [String: Any]
        XCTAssertEqual(safe["code"] as? String, source["code"] as? String)
        XCTAssertEqual(safe["expr"] as? String, "amount*2")
        XCTAssertEqual(safe["icon"] as? String, "rocket")
        let label = safe["label"] as! String
        XCTAssertFalse(label.unicodeScalars.contains { $0.value > 0x7F && $0.properties.isEmoji })
        XCTAssertTrue((safe["title"] as! String).contains(":rocket:"))
    }
    func testRejectsInvalidExpressionsInsteadOfReturningZero() {
        for expr in ["2 garbage", "2 3", "(2+3", "missing + 1", "1/0", "sqrt(-1)"] {
            XCTAssertNil(CalcExprIOS.evalOptional(expr, vars: [:]), expr)
        }
    }
    func testExponentPrecedenceAndActualInputs() {
        XCTAssertEqual(CalcExprIOS.eval("-2**2", vars: [:]), -4, accuracy: 1e-9)
        XCTAssertEqual(CalcExprIOS.eval("2**3**2", vars: [:]), 512, accuracy: 1e-9)
        XCTAssertEqual(CalcExprIOS.eval("amount*(1+rate/100)", vars: ["amount": 240, "rate": 15]), 276, accuracy: 1e-9)
    }
}

/// Actual test-only HTTP endpoint, not a fetch stub or a WebKit scheme mock.
private final class WorkerHTTPProbe {
    let queue = DispatchQueue(label: "gs.tests.worker-http-probe")
    let listener: NWListener
    private let lock = NSLock()
    private var requests = 0
    private var lines: [String] = []
    var requestLines: [String] {
        lock.lock(); defer { lock.unlock() }; return lines
    }
    var requestCount: Int {
        lock.lock(); defer { lock.unlock() }
        return requests
    }

    init() throws {
        listener = try NWListener(using: .tcp, on: .any)
        listener.newConnectionHandler = { [weak self] connection in
            guard let self = self else { connection.cancel(); return }
            connection.start(queue: self.queue)
            self.receiveRequest(connection, buffer: Data())
        }
    }
    private func receiveRequest(_ connection: NWConnection, buffer: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65_536) { [weak self] data, _, complete, error in
            guard let self, let data, !data.isEmpty, buffer.count + data.count <= 65_536 else { connection.cancel(); return }
            let request = buffer + data
            guard request.range(of: Data("\r\n\r\n".utf8)) != nil else {
                if complete || error != nil { connection.cancel() }
                else { self.receiveRequest(connection, buffer: request) }
                return
            }
            self.lock.lock()
            self.requests += 1
            self.lines.append(String(decoding: request, as: UTF8.self).components(separatedBy: "\r\n").first ?? "")
            self.lock.unlock()
            let body = "causal-response"
            let response = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nAccess-Control-Allow-Origin: *\r\nCache-Control: no-store\r\nContent-Length: \(body.utf8.count)\r\nConnection: close\r\n\r\n" + body
            // Send a real TCP FIN after the body. Cancelling immediately after
            // contentProcessed can reset the socket before WebKit reads it.
            connection.send(content: Data(response.utf8), contentContext: .finalMessage, isComplete: true, completion: .contentProcessed { error in
                if error != nil { connection.cancel(); return }
                connection.receive(minimumIncompleteLength: 1, maximumLength: 1) { _, _, _, _ in connection.cancel() }
            })
        }
    }

}
