import { evaluateExpr } from './evaluate'

/** Bounded, read-only invocation of the existing calculator. No eval, shell,
 * property access, assignments, network or arbitrary code execution. */
export type CalculationResult =
  | { expression: string; ok: true; value: number }
  | { expression: string; ok: false; error: string }

const ALLOWED_NAMES = new Set(['pi','e','sqrt','abs','ln','log','log10','exp','sin','cos','tan','floor','ceil','round','min','max','pow','clamp'])
export function executeCalculation(expression: string): CalculationResult {
  const fail = (error: string): CalculationResult => ({ expression, ok: false, error })
  if (expression.includes('%')) return fail('Ambiguous percentage notation; express the ratio explicitly')
  if (!expression || expression.length > 200 || !/^[\d\s.+\-*/%^(),a-z]+$/i.test(expression))
    return fail('Unsupported or oversized numeric expression')
  if ([...expression.matchAll(/[a-z][a-z0-9]*/gi)].some(m => !ALLOWED_NAMES.has(m[0].toLowerCase())))
    return fail('Unknown numeric function or constant')
  try {
    const value = evaluateExpr(expression, Object.create(null) as Record<string, number>)
    if (!Number.isFinite(value)) return fail('Result is undefined or outside the supported numeric range')
    if (Number.isInteger(value) && !Number.isSafeInteger(value)) return fail('Integer is outside the exact supported range')
    return { expression, ok: true, value }
  } catch { return fail('Invalid numeric expression') }
}

/** Only explicit, fully numeric standalone asks are eligible for direct
 * answers; natural-language arithmetic keeps the real question intact. */
export function explicitCalculation(text: string): string | null {
  const trimmed = text.trim()
  const expression = trimmed.replace(/^(?:please\s+)?(?:calculate|compute|evaluate|what is)\s+/i, '').replace(/[?=]\s*$/, '').trim()
  if (!/^[\d\s.+\-*/%^()]+$/.test(expression) || !/\d/.test(expression) || !/[+\-*/%^]/.test(expression)) return null
  // '%' in ordinary prose means percentage, whereas this engine implements
  // modulo. Do not silently reinterpret a percentage as a remainder.
  if (expression.includes('%')) return null
  if (/^\d{4}-\d{1,2}-\d{1,2}$/.test(expression)) return null
  // A bare number range, phone number, identifier or date is not a request.
  if (trimmed === expression && !/[+*/^]/.test(expression)) return null
  return executeCalculation(expression).ok ? expression : null
}

export function calculationEvidence(results: readonly CalculationResult[]): string | null {
  if (!results.length) return null
  return 'CALCULATOR RESULTS — actual read-only application execution (IEEE 754 numeric arithmetic). Use a result only if its expression correctly represents the user’s request; never treat a failed calculation as a value.\n' +
    results.map(result => JSON.stringify(result)).join('\n')
}
