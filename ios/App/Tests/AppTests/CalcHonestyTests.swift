import XCTest
@testable import GSApp

final class CalcHonestyTests: XCTestCase {
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
