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

    func testProductionCalculatorExpressionsSupportScientificNumbersAndRejectInvalidArityOrRecursiveSource() {
        XCTAssertEqual(CalcExprIOS.eval("1e3 + 2.5E-2", vars: [:]), 1000.025)
        XCTAssertEqual(CalcExprIOS.evalOptional("1e3 + 2.5E-2", vars: [:]), 1000.025)
        XCTAssertEqual(CalcExprIOS.eval("pow(2,3)", vars: [:]), 8)
        XCTAssertTrue(CalcExprIOS.eval("pow(2)", vars: [:]).isNaN, "Missing exponent must not silently become zero")
        XCTAssertTrue(CalcExprIOS.eval("sqrt(4,16)", vars: [:]).isNaN, "Do not silently ignore supplied arguments")
        XCTAssertTrue(CalcExprIOS.eval("min(2,1/0)", vars: [:]).isNaN, "Undefined arguments must not become a plausible minimum")
        XCTAssertTrue(CalcExprIOS.eval("1e+", vars: [:]).isNaN)
        XCTAssertTrue(CalcExprIOS.eval(String(repeating: "(", count: 1000) + "1" + String(repeating: ")", count: 1000), vars: [:]).isNaN,
                      "Generated pathological nesting must not crash the UI thread")
        XCTAssertTrue(CalcExprIOS.eval(String(repeating: "1+", count: 5000) + "1", vars: [:]).isNaN)
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
