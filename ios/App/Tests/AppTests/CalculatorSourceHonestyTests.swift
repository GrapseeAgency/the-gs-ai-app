import XCTest
@testable import GSApp

final class CalculatorSourceHonestyTests: XCTestCase {
    func testActualFencedCalculatorAcceptsSourceAliasesAndScientificInitialValuesWithoutInventedDefaults() throws {
        let source = #"{"title":"Custom source calculation","parameters":[{"key":"distance","label":"Distance","initial":"1e3"},{"key":"rate","label":"Rate","default":0}],"formulas":[{"label":"Actual total","expression":"distance*rate"}]}"#
        let blocks = parseBlocks("```calc\n" + source + "\n```")
        let payloads = blocks.compactMap { block -> String? in
            if case .widget(let kind, let json, false, _) = block, kind == "calc" { return json }
            return nil
        }
        let payload = try XCTUnwrap(payloads.first, "Actual markdown parsing must reach the calculator, not a substitute code block")
        XCTAssertTrue(isValidWidget(kind: "calc", json: payload))
        let object = try XCTUnwrap(parseRelaxedJSONObject(kind: "calc", raw: payload))
        let fields = try XCTUnwrap(object["parameters"] as? [[String: Any]])
        let outputs = try XCTUnwrap(object["formulas"] as? [[String: Any]])
        XCTAssertEqual(calcInitialValueIOS(fields[0]), 1000, "Scientific notation is not 13")
        XCTAssertEqual(calcInitialValueIOS(fields[1]), 0, "Explicit zero is not replaced")
        let expression = calcOutputExpressionIOS(outputs[0])
        XCTAssertEqual(expression, "distance*rate")
        XCTAssertEqual(CalcExprIOS.eval(expression, vars: ["distance": calcInitialValueIOS(fields[0]), "rate": calcInitialValueIOS(fields[1])]), 0)
        XCTAssertTrue(calcInitialValueIOS(["min": 10]).isNaN, "A range bound is not a supplied input value")
        XCTAssertTrue(calcInitialValueIOS([:]).isNaN, "Missing input must not become zero")
        XCTAssertTrue(calcInitialValueIOS(["value": "n/a"]).isNaN)
        XCTAssertTrue(calcInitialValueIOS(["value": true]).isNaN, "A JSON boolean is not a numeric input")
        XCTAssertEqual(calcInitialValueIOS(["options": [["label": "Actual first option", "value": 7]]]), 7)
        XCTAssertEqual(calcInitialValueIOS(["initial": -12.5]), -12.5)
        XCTAssertEqual(calcNumberIOS(["value": "-2.5e-3"], "value"), -0.0025)
        XCTAssertTrue(calcNumberIOS(["value": "$42"], "value").isNaN, "Do not silently reinterpret prose as a supplied numeric value")
    }

    func testActualCalculatorFormatterDoesNotInventDollarsAndKeepsExplicitSourceUnits() {
        XCTAssertEqual(formatCalcResultIOS(100, unit: "", format: "currency"), "100.00")
        XCTAssertEqual(formatCalcResultIOS(100, unit: "currency"), "100.00")
        XCTAssertEqual(formatCalcResultIOS(100, unit: "money"), "100.00")
        XCTAssertEqual(formatCalcResultIOS(100, unit: "BDT", format: "currency"), "100.00 BDT")
        XCTAssertEqual(formatCalcResultIOS(100, unit: "EUR", format: "currency"), "100.00 EUR")
        XCTAssertEqual(formatCalcResultIOS(100, unit: "৳"), "৳100.00")
        XCTAssertEqual(formatCalcResultIOS(100, unit: "$"), "$100.00")
        XCTAssertEqual(formatCalcResultIOS(100, unit: "USD"), "$100.00")
        XCTAssertEqual(formatCalcResultIOS(0.25, unit: "", format: "percent"), "25%")
        XCTAssertEqual(formatCalcResultIOS(.infinity, unit: ""), "Undefined for these inputs")
        XCTAssertEqual(formatCalcResultIOS(.greatestFiniteMagnitude, unit: "", format: "percent"), "Undefined for these inputs")
        XCTAssertEqual(calcOutputExpressionIOS(["calc": "x1+x2"]), "x1+x2")
    }
}
