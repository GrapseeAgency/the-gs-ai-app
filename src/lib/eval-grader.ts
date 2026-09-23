/**
 * EVALUATION INFRASTRUCTURE — DETERMINISTIC GRADER CORE (Layer 4).
 *
 * Single source of truth for evaluating eval-suite assertions. Consumed by:
 *   - scripts/run-eval-suite.ts        (Phase 3 — suite runner)
 *   - scripts/test-eval-graders.ts     (Phase 4 — hermetic contract tests)
 *   - the production monitor           (Phase 5 — sampled-turn grading)
 *
 * DETERMINISM CONTRACT: given the same trace row + answer text, the verdict
 * is a pure function — no network, no clocks, no randomness. The trace row
 * is the Layer-1 record; nothing here may re-derive facts the trace already
 * captured (grading re-derivation is exactly how false passes happen).
 */

export interface Assertion {
  type: string
  op?: string
  value?: unknown
  values?: unknown[]
  flags?: string
}

/** One row of the Layer-1 `turns` table (SQLite booleans arrive as 0/1). */
export interface TraceRow {
  requestId: string
  capability: string
  trigger: string
  searchExecuted: number
  sourceCount: number
  sourcesRead: number
  sourcesFailed: number
  domains: string
  modelRoute: string
  finalStatus: string
  cited: string
  latencyMs: number
  errorType: string | null
}

export interface Verdict {
  ok: boolean
  detail: string
}

export function wordCount(text: string): number {
  return text.trim().split(/\s+/).filter((w) => w.length > 0).length
}

export function lineCount(text: string): number {
  return text.split('\n').filter((l) => l.trim().length > 0).length
}

export function evalAssertion(a: Assertion, trace: TraceRow | null, answer: string): Verdict {
  const hasTrace = trace !== null
  switch (a.type) {
    case 'trace.searchExecuted': {
      if (!hasTrace) return { ok: false, detail: 'no trace row' }
      const actual = trace!.searchExecuted === 1
      return actual === a.value
        ? { ok: true, detail: `searchExecuted=${actual}` }
        : { ok: false, detail: `searchExecuted=${actual} expected=${a.value}` }
    }
    case 'trace.capability': {
      if (!hasTrace) return { ok: false, detail: 'no trace row' }
      return trace!.capability === a.value
        ? { ok: true, detail: `capability=${trace!.capability}` }
        : { ok: false, detail: `capability=${trace!.capability} expected=${a.value}` }
    }
    case 'trace.trigger': {
      if (!hasTrace) return { ok: false, detail: 'no trace row' }
      return trace!.trigger === a.value
        ? { ok: true, detail: `trigger=${trace!.trigger}` }
        : { ok: false, detail: `trigger=${trace!.trigger} expected=${a.value}` }
    }
    case 'trace.sourceCount': {
      if (!hasTrace) return { ok: false, detail: 'no trace row' }
      return trace!.sourceCount === a.value
        ? { ok: true, detail: `sourceCount=${trace!.sourceCount}` }
        : { ok: false, detail: `sourceCount=${trace!.sourceCount} expected=${a.value}` }
    }
    case 'trace.finalStatus': {
      if (!hasTrace) return { ok: false, detail: 'no trace row' }
      return trace!.finalStatus === a.value
        ? { ok: true, detail: `finalStatus=${trace!.finalStatus}` }
        : { ok: false, detail: `finalStatus=${trace!.finalStatus} expected=${a.value}` }
    }
    case 'trace.domainsMin': {
      if (!hasTrace) return { ok: false, detail: 'no trace row' }
      const domains = JSON.parse(trace!.domains) as string[]
      return domains.length >= Number(a.value)
        ? { ok: true, detail: `domains=${domains.length}` }
        : { ok: false, detail: `domains=${domains.length} expected>=${a.value} (${domains.join(',')})` }
    }
    case 'trace.sourcesReadMin': {
      if (!hasTrace) return { ok: false, detail: 'no trace row' }
      return trace!.sourcesRead >= Number(a.value)
        ? { ok: true, detail: `sourcesRead=${trace!.sourcesRead}` }
        : { ok: false, detail: `sourcesRead=${trace!.sourcesRead} expected>=${a.value} (sourceCount=${trace!.sourceCount} failed=${trace!.sourcesFailed})` }
    }
    case 'trace.readRate': {
      if (!hasTrace) return { ok: false, detail: 'no trace row' }
      if (trace!.sourceCount === 0) return { ok: false, detail: 'sourceCount=0 — read rate undefined' }
      const rate = trace!.sourcesRead / trace!.sourceCount
      return rate >= Number(a.value)
        ? { ok: true, detail: `readRate=${rate.toFixed(2)}` }
        : { ok: false, detail: `readRate=${rate.toFixed(2)} expected>=${a.value}` }
    }
    case 'citationsWithinSources': {
      if (!hasTrace) return { ok: false, detail: 'no trace row' }
      const cited = JSON.parse(trace!.cited) as number[]
      const bad = cited.filter((n) => n > trace!.sourceCount)
      return bad.length === 0
        ? { ok: true, detail: `cited=${cited.join(',')} within sourceCount=${trace!.sourceCount}` }
        : { ok: false, detail: `cited ${bad.join(',')} exceed sourceCount=${trace!.sourceCount}` }
    }
    case 'response.matchesRegex': {
      const re = new RegExp(a.value as string, a.flags ?? 'i')
      return re.test(answer)
        ? { ok: true, detail: `answer=${JSON.stringify(answer.slice(0, 40))} matched` }
        : { ok: false, detail: `answer=${JSON.stringify(answer.slice(0, 60))} did not match /${a.value}/${a.flags ?? 'i'}` }
    }
    case 'response.wordCount': {
      const actual = wordCount(answer)
      return actual === a.value
        ? { ok: true, detail: `wordCount=${actual}` }
        : { ok: false, detail: `wordCount=${actual} expected=${a.value} answer=${JSON.stringify(answer.slice(0, 60))}` }
    }
    case 'response.lineCount': {
      const actual = lineCount(answer)
      return actual === a.value
        ? { ok: true, detail: `lineCount=${actual}` }
        : { ok: false, detail: `lineCount=${actual} expected=${a.value}` }
    }
    case 'response.contains': {
      return answer.toLowerCase().includes(String(a.value).toLowerCase())
        ? { ok: true, detail: `contains "${a.value}"` }
        : { ok: false, detail: `missing "${a.value}" in ${JSON.stringify(answer.slice(0, 80))}` }
    }
    case 'response.notContains': {
      return !answer.toLowerCase().includes(String(a.value).toLowerCase())
        ? { ok: true, detail: `no "${a.value}"` }
        : { ok: false, detail: `forbidden "${a.value}" present in ${JSON.stringify(answer.slice(0, 80))}` }
    }
    case 'response.containsAny': {
      const values = (a.values ?? []) as string[]
      const hit = values.find((v) => answer.toLowerCase().includes(v.toLowerCase()))
      return hit
        ? { ok: true, detail: `contains "${hit}"` }
        : { ok: false, detail: `none of [${values.join('|')}] in ${JSON.stringify(answer.slice(0, 80))}` }
    }
    case 'anyOf': {
      const nested = (a.values ?? []) as Assertion[]
      const results = nested.map((n) => evalAssertion(n, trace, answer))
      const ok = results.some((r) => r.ok)
      return ok
        ? { ok: true, detail: results.find((r) => r.ok)!.detail }
        : { ok: false, detail: `anyOf failed: ${results.map((r) => r.detail).join(' AND ')}` }
    }
    default:
      return { ok: false, detail: `unknown assertion type ${a.type}` }
  }
}

/** Grade a full assertion list; returns the failing verdicts. */
export function gradeAll(assertions: Assertion[], trace: TraceRow | null, answer: string): Verdict[] {
  return assertions.map((a) => evalAssertion(a, trace, answer)).filter((v) => !v.ok)
}
