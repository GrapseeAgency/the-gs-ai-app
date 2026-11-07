"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.formatOutput = exports.evaluateExpr = void 0;
/**
 * Re-export: the expression engine now lives in src/lib/calc/evaluate so the
 * backend validator (stage3d z) and the web widgets share one implementation.
 */
var evaluate_1 = require("../../../lib/calc/evaluate");
Object.defineProperty(exports, "evaluateExpr", { enumerable: true, get: function () { return evaluate_1.evaluateExpr; } });
Object.defineProperty(exports, "formatOutput", { enumerable: true, get: function () { return evaluate_1.formatOutput; } });
