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
  /** Exact-count graders (punctuation/word occurrence). */
  count?: number
  /** Range graders (bullet counts, sentence bounds). */
  min?: number
  max?: number
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

    // --- INSTRUCTION GENERALIZATION graders (IFBench-style unseen constraints).
    // Pure string functions over the answer; deterministic by contract.
    case 'response.everyWordStartsWith': {
      const letter = String(a.value).toLowerCase()
      const words = answer.trim().split(/\s+/).filter((w) => /[a-z0-9]/i.test(w))
      const bad = words.filter((w) => !w.toLowerCase().startsWith(letter))
      return bad.length === 0 && words.length > 0
        ? { ok: true, detail: `all ${words.length} words start with "${letter}"` }
        : { ok: false, detail: `words not starting with "${letter}": ${bad.slice(0, 5).join(', ')}` }
    }
    case 'response.punctuationCount': {
      const ch = String(a.value)
      const actual = answer.split(ch).length - 1
      const expected = a.count ?? 0
      return actual === expected
        ? { ok: true, detail: `"${ch}" count=${actual}` }
        : { ok: false, detail: `"${ch}" count=${actual} expected=${expected}` }
    }
    case 'response.endsWith': {
      const suffix = String(a.value)
      return answer.trimEnd().endsWith(suffix)
        ? { ok: true, detail: `ends with "${suffix}"` }
        : { ok: false, detail: `does not end with "${suffix}": ${JSON.stringify(answer.slice(-30))}` }
    }
    case 'response.startsWith': {
      const prefix = String(a.value)
      return answer.trimStart().toLowerCase().startsWith(prefix.toLowerCase())
        ? { ok: true, detail: `starts with "${prefix}"` }
        : { ok: false, detail: `does not start with "${prefix}": ${JSON.stringify(answer.slice(0, 30))}` }
    }
    case 'response.forbiddenLetter': {
      const letter = String(a.value).toLowerCase()
      const hits = answer.toLowerCase().split(letter).length - 1
      return hits === 0
        ? { ok: true, detail: `letter "${letter}" absent` }
        : { ok: false, detail: `forbidden letter "${letter}" present ${hits}×` }
    }
    case 'response.forbiddenWord': {
      const word = String(a.value).toLowerCase()
      const hits = (answer.toLowerCase().match(new RegExp(`\\b${word.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`, 'g')) ?? []).length
      return hits === 0
        ? { ok: true, detail: `word "${word}" absent` }
        : { ok: false, detail: `forbidden word "${word}" present ${hits}×` }
    }
    case 'response.forbiddenChar': {
      const ch = String(a.value)
      const hits = answer.split(ch).length - 1
      return hits === 0
        ? { ok: true, detail: `char "${ch}" absent` }
        : { ok: false, detail: `forbidden char "${ch}" present ${hits}×` }
    }
    case 'response.caseRule': {
      if (a.value === 'lowercase') {
        const uppers = answer.replace(/[^A-Z]/g, '')
        return uppers.length === 0
          ? { ok: true, detail: 'all lowercase' }
          : { ok: false, detail: `uppercase letters present: ${uppers.slice(0, 10)}` }
      }
      return { ok: false, detail: `unknown caseRule ${String(a.value)}` }
    }
    case 'response.sentenceWordMax': {
      const max = Number(a.value)
      const sentences = answer.split(/[.!?]+(?:\s|$)/).map((s) => s.trim()).filter((s) => s.length > 0)
      const bad = sentences.map((s) => wordCount(s)).filter((n) => n >= max)
      return bad.length === 0 && sentences.length > 0
        ? { ok: true, detail: `all ${sentences.length} sentences under ${max} words` }
        : { ok: false, detail: `sentences with ≥${max} words: ${bad.join(', ')}` }
    }
    case 'response.wordOccurrences': {
      const word = String(a.value).toLowerCase()
      const expected = a.count ?? 0
      const actual = (answer.toLowerCase().match(new RegExp(`\\b${word.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`, 'g')) ?? []).length
      return actual === expected
        ? { ok: true, detail: `"${word}" ×${actual}` }
        : { ok: false, detail: `"${word}" ×${actual} expected ×${expected}` }
    }
    case 'response.bulletCount': {
      const lines = answer.split('\n').map((l) => l.trim())
      const bullets = lines.filter((l) => /^[-*•]\s+\S/.test(l))
      const min = a.min ?? 1
      const max = a.max ?? Number.MAX_SAFE_INTEGER
      return bullets.length >= min && bullets.length <= max
        ? { ok: true, detail: `bullets=${bullets.length}` }
        : { ok: false, detail: `bullets=${bullets.length} expected in [${min},${max}]` }
    }
    case 'response.syllablesPerWordMax': {
      // Deterministic approximation: a syllable ≈ one vowel group (aeiouy+),
      // minus a silent trailing 'e' (classic heuristic; e.g. 'one' → 1).
      const max = Number(a.value)
      const words = answer.toLowerCase().split(/[^a-z']+/).filter((w) => w.length > 0)
      const syllables = (w: string): number => {
        let g = (w.match(/[aeiouy]+/g) ?? []).length
        if (w.length > 2 && w.endsWith('e') && !w.endsWith('le')) g -= 1
        return Math.max(1, g)
      }
      const bad = words.filter((w) => syllables(w) > max)
      return bad.length === 0 && words.length > 0
        ? { ok: true, detail: `all ${words.length} words ≤${max} syllable(s)` }
        : { ok: false, detail: `multi-syllable words: ${bad.slice(0, 6).join(', ')}` }
    }
    case 'response.sentenceCount': {
      const expected = Number(a.value)
      const actual = answer.split(/[.!?]+(?:\s|$)/).map((s) => s.trim()).filter((s) => s.length > 0).length
      return actual === expected
        ? { ok: true, detail: `sentences=${actual}` }
        : { ok: false, detail: `sentences=${actual} expected=${expected}` }
    }
    case 'response.minWordLength': {
      const min = Number(a.value)
      const words = answer.trim().split(/\s+/).filter((w) => /[a-z]/i.test(w))
      const bad = words.filter((w) => w.replace(/\W/g, '').length < min)
      return bad.length === 0 && words.length > 0
        ? { ok: true, detail: `all ${words.length} words ≥${min} letters` }
        : { ok: false, detail: `words shorter than ${min}: ${bad.slice(0, 6).join(', ')}` }
    }
    case 'response.maxWords': {
      const max = Number(a.value)
      const actual = wordCount(answer)
      return actual <= max
        ? { ok: true, detail: `words=${actual} ≤${max}` }
        : { ok: false, detail: `words=${actual} expected ≤${max}` }
    }
    case 'response.quoted': {
      const t = answer.trim()
      return t.startsWith('"') && t.endsWith('"') && t.length >= 2
        ? { ok: true, detail: 'response wrapped in double quotes' }
        : { ok: false, detail: `not quoted: ${JSON.stringify(t.slice(0, 30))}…${JSON.stringify(t.slice(-15))}` }
    }
    case 'response.numberedSentences': {
      const numbers = (answer.match(/(\d+)\.\s/g) ?? []).map((m) => Number(m.replace(/\.\s*/, '')))
      const segments = answer
        .split(/\d+\.\s*/)
        .map((s) => s.trim())
        .filter((s) => s.length > 0)
      const sequential = numbers.every((n, i) => n === i + 1)
      return sequential && numbers.length >= 2 && segments.length === numbers.length
        ? { ok: true, detail: `numbered 1..${numbers.length} sequentially` }
        : { ok: false, detail: `numbers=[${numbers.join(',')}] segments=${segments.length} sequential=${sequential}` }
    }
    default:
      return { ok: false, detail: `unknown assertion type ${a.type}` }
  }
}

/** Grade a full assertion list; returns the failing verdicts. */
export function gradeAll(assertions: Assertion[], trace: TraceRow | null, answer: string): Verdict[] {
  return assertions.map((a) => evalAssertion(a, trace, answer)).filter((v) => !v.ok)
}
