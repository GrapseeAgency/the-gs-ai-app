import XCTest
import SwiftUI
import WebKit
@testable import GSApp

final class CalcHonestyTests: XCTestCase {
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
