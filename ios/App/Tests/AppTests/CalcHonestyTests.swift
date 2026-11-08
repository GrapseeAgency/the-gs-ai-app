import XCTest
import SwiftUI
import WebKit
import Network
@testable import GSApp

final class CalcHonestyTests: XCTestCase {
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
        for allowed in [false, true] {
            let document = allowed ? production.replacingOccurrences(of: "connect-src 'none'", with: "connect-src *") : production
            let host = UIHostingController(rootView: HtmlBlockView(kind: "html", source: document, open: false))
            let old = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap { $0.windows }.first { $0.isKeyWindow }
            let window: UIWindow
            if let scene = old?.windowScene { window = UIWindow(windowScene: scene) }
            else { window = UIWindow(frame: UIScreen.main.bounds) }
            defer { window.isHidden = true; window.rootViewController = nil; old?.makeKeyAndVisible() }
            window.rootViewController = host
            window.makeKeyAndVisible()
            func findWeb(_ view: UIView) -> WKWebView? {
                if let web = view as? WKWebView { return web }
                for child in view.subviews { if let web = findWeb(child) { return web } }
                return nil
            }
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
            XCTAssertEqual(server.requestCount, allowed ? 1 : 0, "CSP must prevent the actual request, not merely fail DNS, CORS or ATS")
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
        let source = #"<button id="run" title="Increase &#x1F600;">Increase &#x1F600;</button><output id="result">0</output><svg data-custom-glass="true"><path d="M0 0L8 8"/></svg><script>window.marker=String.fromCodePoint(0x1F600);window.count=0;document.getElementById('run').onclick=function(){window.count++;document.getElementById('result').textContent=window.count+window.marker;};</script>"#
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
        let vector = try await web.evaluateJavaScript("!!document.querySelector('svg[data-custom-glass] path')")
        XCTAssertEqual(vector as? Bool, true)
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
