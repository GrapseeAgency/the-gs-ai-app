/**
 * Re-export: the expression engine now lives in src/lib/calc/evaluate so the
 * backend validator (stage3d z) and the web widgets share one implementation.
 */
export { evaluateExpr, formatOutput } from '../../../lib/calc/evaluate'
