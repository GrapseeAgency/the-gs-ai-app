import XCTest
@testable import GSApp

final class CalcHonestyTests: XCTestCase {
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
