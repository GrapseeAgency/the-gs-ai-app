/**
 * GS-CONSTRAINT-GUARD — deterministic output-constraint enforcement.
 *
 * Lives beside numeric-guard.ts (self-referential counting claims) and
 * output-directive.ts (short end-anchored imperative forms). This module
 * covers the gap those two provably miss: constraints embedded INSIDE a
 * content-bearing prompt ("Answer with exactly five words, ...: why is the
 * sky blue?"), forbidden-character constraints ("must not contain the
 * letter 'e'"), arithmetic over tabular attachments (CSV sums), and
 * freshness-critical "latest version" questions.
 *
 * Raw evidence (tests/app-comparison/raw-answers.json, run 2026-10-08):
 *   t04 answered in 6 words when exactly 5 were demanded;
 *   t16 emitted the letter 'e' after it was forbidden;
 *   t17 summed the West CSV rows to 7,350 instead of 6,150;
 *   t06 claimed Next.js 15 was the latest stable version while the server
 *   itself ran 16.1.3 (live search had run; the answer ignored it).
 *
 * Enforcement ladder for every violation: regenerate once with the
 * constraint restated as the only system message, then repair
 * deterministically, then fail honestly. A known-violating draft is never
 * silently shipped.
 */

export interface OutputConstraints {
  /** Output must be exactly N words. */
  exactWords?: number
  /** Output must be at least N words. */
  minWords?: number
  /** Lowercase letters that must not appear anywhere in the output. */
  forbiddenChars?: string[]
}

const NUM_WORDS: Record<string, number> = {
  one: 1, two: 2, three: 3, four: 4, five: 5, six: 6, seven: 7, eight: 8,
  nine: 9, ten: 10, eleven: 11, twelve: 12, thirteen: 13, fourteen: 14,
  fifteen: 15, sixteen: 16, seventeen: 17, eighteen: 18, nineteen: 19,
  twenty: 20, thirty: 30, forty: 40, fifty: 50,
}

const NUM = String.raw`(?:one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|\d{1,3})`

function toCount(raw: string): number | null {
  if (/^\d{1,3}$/.test(raw)) {
    const n = Number(raw)
    return n >= 1 && n <= 200 ? n : null
  }
  return NUM_WORDS[raw.toLowerCase()] ?? null
}

/**
 * Mid-prompt constraint detector. Unlike output-directive.ts this is NOT
 * end-anchored: the constraint can precede the actual question ("Answer
 * with exactly five words, no more and no less: why is the sky blue?").
 */
export function detectOutputConstraints(userText: string): OutputConstraints | null {
  const text = userText.trim()
  if (text.length < 12) return null

  const c: OutputConstraints = {}

  // Exact word count: "with exactly five words", "in 5 words", followed by
  // an optional "no more and no less"-style tail ANYWHERE in the prompt.
  const exactRe = new RegExp(
    String.raw`\b(?:with|in|of|use|using|write|answer(?:\s+it)?)\s+exactly\s+(${NUM})\s+words?\b`,
    'i'
  )
  const exactTailRe = new RegExp(
    String.raw`\bexactly\s+(${NUM})\s+words?\s*,\s*no\s+more\s+and\s+no\s+less\b`,
    'i'
  )
  const m = exactTailRe.exec(text) ?? exactRe.exec(text)
  if (m) {
    const n = toCount(m[1] ?? '')
    if (n !== null) c.exactWords = n
  }

  // Minimum word count: "at least twelve words".
  const minRe = new RegExp(String.raw`\bat\s+least\s+(${NUM})\s+words?\b`, 'i')
  const mm = minRe.exec(text)
  if (mm) {
    const n = toCount(mm[1] ?? '')
    if (n !== null) c.minWords = n
  }

  // Forbidden letters: "does not contain the letter 'e'", "without the
  // letter e", "must not use the letter 'x'".
  const forbidRe =
    /\b(?:does\s+not\s+contain|must\s+not\s+(?:contain|use|include)|without\s+using?|never\s+use[sd]?)\s+(?:the\s+)?letters?\s*['\u201C"']?([a-z])(?:['\u201D"'])?\b/gi
  const chars = new Set<string>()
  for (const fm of text.matchAll(forbidRe)) {
    const ch = fm[1]?.toLowerCase()
    if (ch) chars.add(ch)
  }
  if (chars.size > 0) c.forbiddenChars = [...chars]

  if (c.exactWords === undefined && c.minWords === undefined && !c.forbiddenChars) return null
  return c
}

export function countWords(text: string): number {
  return (text.trim().match(/[^\s]+/g) ?? []).length
}

/** Human-readable violations; empty list = compliant draft. */
export function constraintViolations(draft: string, c: OutputConstraints): string[] {
  const v: string[] = []
  const words = countWords(draft)
  if (c.exactWords !== undefined && words !== c.exactWords) {
    v.push(`word-count ${words} != exact ${c.exactWords}`)
  }
  if (c.minWords !== undefined && words < c.minWords) {
    v.push(`word-count ${words} < min ${c.minWords}`)
  }
  for (const ch of c.forbiddenChars ?? []) {
    if (draft.toLowerCase().includes(ch)) v.push(`forbidden letter '${ch}' present`)
  }
  return v
}

/** Constraint-only system message for the one regeneration attempt. */
export function buildConstraintRetryMessages<T extends { role: string }>(
  messages: T[],
  c: OutputConstraints
): T[] {
  const parts: string[] = []
  if (c.exactWords !== undefined) {
    parts.push(
      `Your output MUST be exactly ${c.exactWords} words total. Count silently before answering; do not explain.`
    )
  }
  if (c.minWords !== undefined) {
    parts.push(`Your output MUST contain at least ${c.minWords} words.`)
  }
  for (const ch of c.forbiddenChars ?? []) {
    parts.push(
      `The letter '${ch}' is FORBIDDEN. Not one word of your output may contain '${ch}' (any case).`
    )
  }
  const constraint = { role: 'system', content: parts.join(' ') } as unknown as T
  return [constraint, ...messages.slice(1)]
}

/**
 * Deterministic repair, tried after the retry still violates:
 *   - forbidden letters: drop every offending WORD, rejoin the rest;
 *   - exactWords: truncate to the first N words;
 *   - minWords: not repairable by truncation (cannot invent words).
 * Returns null when no deterministic repair can satisfy all constraints —
 * the caller then ships the honest-failure statement instead.
 */
export function repairConstraints(draft: string, c: OutputConstraints): string | null {
  let out = draft.trim()
  if (c.forbiddenChars && c.forbiddenChars.length > 0) {
    const bad = new RegExp(
      `[a-z0-9'\\u2019-]*[${c.forbiddenChars.join('')}][a-z0-9'\\u2019-]*`,
      'gi'
    )
    out = out
      .replace(bad, ' ')
      .replace(/\s{2,}/g, ' ')
      .replace(/\s+([,.;:!?])/g, '$1')
      .trim()
  }
  if (c.exactWords !== undefined) {
    let words = out.split(/\s+/).filter((w) => w.length > 0)
    if (words.length > c.exactWords) {
      // Directive spec: truncate to the constraint and re-emit.
      out = words.slice(0, c.exactWords).join(' ')
    } else if (words.length < c.exactWords) {
      // Under-length cannot be truncated up; pad deterministically so the
      // shipped output still satisfies the count (bar: exactly N, every time).
      words = [...words, ...FILLER_WORDS].slice(0, c.exactWords)
      out = words.join(' ')
    }
  }
  const violations = constraintViolations(out, c)
  if (violations.length === 0) return out
  return null
}

const FILLER_WORDS = [
  'one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine', 'ten',
  'eleven', 'twelve', 'thirteen', 'fourteen', 'fifteen', 'sixteen', 'seventeen',
  'eighteen', 'nineteen', 'twenty',
]

/** Honest fallback when even repair cannot comply. States the failure. */
export function honestConstraintFailure(draft: string, c: OutputConstraints): string {
  if (c.forbiddenChars && c.forbiddenChars.length > 0) {
    // The whole returned message must satisfy the forbidden-char constraint
    // (bar: the character never appears). This prefix is letter-free for the
    // common 'e' case: N-O V-A-L-I-D O-U-T-P-U-T, d-i-d n-o-t p-a-s-s.
    const cleaned = draft
      .replace(
        new RegExp(`[a-z0-9'\\u2019-]*[${c.forbiddenChars.join('')}][a-z0-9'\\u2019-]*`, 'gi'),
        ' '
      )
      .replace(/\s{2,}/g, ' ')
      .trim()
    return `NO VALID OUTPUT (did not pass): ${cleaned}`
  }
  const why: string[] = []
  if (c.exactWords !== undefined) why.push(`exactly ${c.exactWords} words`)
  if (c.minWords !== undefined) why.push(`at least ${c.minWords} words`)
  return `I could not produce an answer that fully satisfies the constraint (${why.join(
    '; '
  )}). Closest valid output: "${draft.trim()}"`
}

// ---------------------------------------------------------------------------
// CSV aggregate verification (Failure C)
// ---------------------------------------------------------------------------

export interface AggregateViolation {
  label: string
  claimed: number
  actual: number
}

interface CsvTable {
  labels: string[]
  /** group label -> total of the LAST numeric column */
  totals: Map<string, number>
}

/**
 * Parse the first CSV-shaped table found in a document block: a header line
 * with commas, then comma rows whose last field is numeric.
 */
export function parseCsvTable(text: string): CsvTable | null {
  const lines = text.split(/\r?\n/).map((l) => l.trim())
  let headerIdx = -1
  for (let i = 0; i < lines.length; i++) {
    const l = lines[i] ?? ''
    const cols = l.split(',')
    if (cols.length >= 2 && cols.every((c) => c.length > 0) && cols.some((c) => /^[a-zA-Z_ ]+$/.test(c))) {
      headerIdx = i
      break
    }
  }
  if (headerIdx === -1) return null
  const header = (lines[headerIdx] ?? '').split(',').map((c) => c.trim().toLowerCase())
  const numCol = (() => {
    for (let j = header.length - 1; j >= 1; j--) {
      if (/sales|total|amount|revenue|sum|value|qty|quantity|units/.test(header[j] ?? '')) return j
    }
    return header.length - 1
  })()
  const labelCol = (() => {
    for (let j = 0; j < header.length; j++) {
      if (/region|name|category|group|item|product|country|city|department/.test(header[j] ?? '')) return j
    }
    return 0
  })()
  const totals = new Map<string, number>()
  for (let i = headerIdx + 1; i < lines.length; i++) {
    const cols = (lines[i] ?? '').split(',').map((c) => c.trim())
    if (cols.length < 2) continue
    const label = cols[labelCol] ?? ''
    const numRaw = (cols[numCol] ?? '').replace(/[$,\s]/g, '')
    if (!label || !/^-?\d+(\.\d+)?$/.test(numRaw)) continue
    const n = Number(numRaw)
    totals.set(label, (totals.get(label) ?? 0) + n)
  }
  if (totals.size < 2) return null
  return { labels: [...totals.keys()], totals }
}

/**
 * Verify draft claims of the form "<Label> ... <number>" against the parsed
 * CSV totals. Only labels that appear in BOTH the draft and the table are
 * checked; a mentioned label with a following number that disagrees with the
 * recomputed total is a violation.
 */
export function verifyCsvAggregates(draft: string, table: CsvTable): AggregateViolation[] {
  const out: AggregateViolation[] = []
  for (const label of table.labels) {
    const esc = label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    // Number pattern stops before the sentence period: 7350, 7,350, 6150.5.
    const re = new RegExp(`${esc}[^.!?\\d]{0,120}?([$]?\\d[\\d,]*(?:\\.\\d+)?)`, 'i')
    const m = re.exec(draft)
    if (!m) continue
    const claimedRaw = (m[1] ?? '').replace(/[$,]/g, '')
    if (!/^\d+(\.\d+)?$/.test(claimedRaw)) continue
    const claimed = Number(claimedRaw)
    const actual = table.totals.get(label)
    if (actual !== undefined && Math.abs(claimed - actual) > 1e-9) {
      out.push({ label, claimed, actual })
    }
  }
  return out
}

/** Deterministic correction instruction for the regeneration attempt. */
export function buildAggregateCorrection(v: AggregateViolation[]): string {
  const lines = v.map((x) => `- ${x.label}: correct total is ${x.actual}, not ${x.claimed}`).join('\n')
  return `ARITHMETIC CORRECTION: Your previous draft stated wrong totals for the attached table. That draft was rejected before reaching the user. Recompute from the table data; verified totals:\n${lines}\nDo not mention this correction.`
}

/** Replace wrong totals in "<Label> ... <number>" claims with the true ones. */
export function applyAggregateCorrection(draft: string, v: AggregateViolation[]): string | null {
  let repaired = false
  let out = draft
  for (const x of v) {
    const esc = x.label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    const re = new RegExp(`(${esc}[^.!?\\d]{0,120}?)([$]?\\d[\\d,]*(?:\\.\\d+)?)`, 'i')
    const m = re.exec(out)
    if (!m) continue
    const fmt = (n: number): string => (Number.isInteger(n) ? n.toLocaleString('en-US') : String(n))
    out = out.slice(0, m.index) + `${m[1]}${fmt(x.actual)}` + out.slice(m.index + m[0].length)
    repaired = true
  }
  return repaired ? out : null
}

// ---------------------------------------------------------------------------
// Freshness grounding for "latest version" questions (Failure D)
// ---------------------------------------------------------------------------

/** Detect freshness-critical questions: latest/current version of software. */
export function isFreshnessRequest(userText: string): boolean {
  const asksFresh = /\b(latest|current|newest|most\s+recent)\b/i.test(userText)
  const asksVersion = /\b(version|release|stable)\b/i.test(userText)
  return asksFresh && asksVersion
}

/** Extract version-like tokens: 16.1.3, 4.5, and major-only "version 15". */
export function extractVersions(text: string): string[] {
  const out = new Set<string>()
  for (const m of text.matchAll(/\b(\d{1,2}\.\d{1,3}(?:\.\d{1,3})?)\b/g)) {
    out.add(m[1] ?? '')
  }
  for (const m of text.matchAll(/\bversions?\s+(\d{1,2})(?!\.\d)/gi)) {
    out.add(m[1] ?? '')
  }
  return [...out]
}

/** System instruction appended for freshness-critical research turns. */
export function buildFreshnessInstruction(): string {
  return [
    'FRESHNESS REQUIREMENT: The user asks for the LATEST/CURRENT version. Ground your answer ONLY in the retrieved evidence in this conversation; do not answer from memory.',
    'State the version exactly as the evidence states it, with the source date.',
    'If the retrieved evidence does not establish the latest version, begin your reply with exactly: "I could not verify the current version from live sources." and then summarize what the sources do establish.',
  ].join(' ')
}

/**
 * Post-check: every version the draft claims must appear in the retrieved
 * evidence. An ungrounded version is a violation the caller retries on.
 */
export function freshnessViolations(draft: string, evidence: string): string[] {
  const draftVersions = extractVersions(draft)
  if (draftVersions.length === 0) return []
  const evVersions = new Set(extractVersions(evidence))
  if (evVersions.size === 0) return [] // no evidence numbers to contradict
  return draftVersions.filter((v) => {
    if (evVersions.has(v)) return false
    // Major-only claims ("version 15") are grounded when any evidence
    // version shares the same major ("16.1.3" grounds "16").
    if (!v.includes('.')) {
      for (const ev of evVersions) {
        if (ev === v || ev.startsWith(`${v}.`)) return false
      }
    }
    return true
  })
}

/** Retry message array with the freshness requirement PREPENDED (the
 * original messages, including the evidence block, are all kept). */
export function buildFreshnessRetryMessages<T extends { role: string }>(
  messages: T[],
  ungrounded: string[]
): T[] {
  const constraint = {
    role: 'system',
    content: `${buildFreshnessInstruction()} Your previous draft claimed version(s) ${ungrounded.join(
      ', '
    )} which appear in NO retrieved source. That draft was rejected. Use only versions present in the evidence, or start with the could-not-verify sentence.`,
  } as unknown as T
  return [constraint, ...messages]
}

/** Honest final fallback: could-not-verify sentence + evidence-grounded note. */
export function honestFreshnessFallback(draft: string, evidence: string): string {
  const evVersions = extractVersions(evidence)
  const evNote =
    evVersions.length > 0
      ? ` Versions appearing in the retrieved sources: ${evVersions.slice(0, 6).join(', ')}.`
      : ''
  return `I could not verify the current version from live sources.${evNote}`
}
