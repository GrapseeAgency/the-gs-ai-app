/** Remote request context projection. Original conversation rows are
 * authoritative; preserve attributable earlier passages and recent turns
 * without detached values or critical-path knowledge graph writes. */
import { tokensOf } from '@/lib/search/tokens'

/** Working-context saturation threshold (history chars) before crystallizing. */
export const CONTEXT_SATURATION_CHARS = 18_000

export type HistoryRow = {
  id: string
  role: string
  content: string
}

export type EarlierHistory = {
  rows: HistoryRow[]
  omitted: number
}

export type Crystallization = {
  /** index into the original history where the verbatim window starts */
  keepFromIndex: number
  /** the compact crystallized block (null when nothing crystallized) */
  block: string | null
  crystallized: number
  recovered: string[]
}

/** Numbers EXACTLY as written — no rounding, no reformatting. Currency
 * prefix captured (hermetic probe caught "$399.50" → "399.50"), trailing
 * list separators trimmed ("1,299." → "1,299"). */
const NUMBER_RE = /(?:USD|\$|€|£)?\s?\d[\d,.]*(?:\s?(?:billion|million|percent|years?|USD|km|mi|bn|%|\$|€|£|m\b))?/gi
/** Entities: capitalized words / multi-word proper nouns. */
const ENTITY_RE = /\b[A-Z][a-zA-Z0-9-]{2,}(?:\s+[A-Z][a-zA-Z0-9-]{2,}){0,2}\b/g

export function extractNumbers(text: string): string[] {
  return Array.from(
    new Set(
      [...text.matchAll(NUMBER_RE)]
        .map((m) => m[0].trim().replace(/[.,]+$/, ''))
        .filter((s) => s.length > 0)
    )
  ).slice(0, 12)
}

export function extractEntities(text: string): string[] {
  // strip leading articles/prepositions from chains ("The Sony WH-1000XM5"
  // → "Sony WH-1000XM5") so entity nodes retrieve cleanly
  const cleaned = [...text.matchAll(ENTITY_RE)].map((m) => {
    const tokens = m[0].split(/\s+/)
    while (tokens.length > 1 && /^(the|a|an|in|on|of|from|at|for|and|by|to)$/i.test(tokens[0])) tokens.shift()
    return tokens.join(' ')
  })
  return Array.from(new Set(cleaned)).slice(0, 10)
}

/** One event snapshot per history row — the gist is truncated prose, the
 * numbers ride verbatim alongside (never inside the truncation). */
export function snapshotOf(row: HistoryRow, seq: number): {
  nodeKey: string
  kind: string
  text: string
  meta: Record<string, unknown>
} {
  const gist = row.content.replace(/\s+/g, ' ').trim().slice(0, 220)
  const numbers = extractNumbers(row.content)
  const entities = extractEntities(row.content)
  return {
    nodeKey: `snap:${row.id}`,
    kind: 'snapshot',
    text: gist,
    meta: { role: row.role.toLowerCase(), numbers, entities, seq, messageId: row.id },
  }
}

/** The remote request's context budget, measured in source characters.
 * This is a bounded projection of stored conversation rows, not a new
 * persistent memory store or a replacement for native agent compaction. */
const RECENT_BUDGET = 18_000
const ARCHIVE_BUDGET = 12_000
const MIN_RECENT_MESSAGES = 6

// Search generic follow-up language is not a useful relevance signal.
const COMMON = new Set(['what','which','when','where','with','that','this','those','these',
  'please','continue','about','answer','said','tell','earlier','previous','could','would',
  'should','from','have','were','your','been','into','just','only','again','give','does'])
export function historySearchTerms(question:string): string[] {
  return [...tokensOf(question,2)].filter(term=>!COMMON.has(term))
    .sort((a,b)=>Number(/\d/.test(b))-Number(/\d/.test(a)) || b.length-a.length).slice(0,64)
}

export const PERSISTENT_INSTRUCTION_TERMS = [
  'for this conversation','from now on','throughout this conversation','always','never',
  'remember','rule:','correction:','update:','মনে রেখো','সবসময়','এই কথোপকথনে','সংশোধন',
] as const

export function likelyPersistentInstruction(text:string):boolean {
  const lower=text.toLowerCase()
  return PERSISTENT_INSTRUCTION_TERMS.some(term=>lower.includes(term))
}

/** Preserve a relevant contiguous passage, rather than isolated numbers.
 * A long excerpt has explicit omission markers and never claims completeness. */
function excerpt(text: string, maxChars: number, terms: readonly string[]): string {
  if (text.length <= maxChars) return text
  if (maxChars <= 32) return ''
  const marker = '\n[… omitted …]\n'
  const lower = text.toLowerCase()
  let hit = -1
  for (const term of terms) {
    const index = lower.lastIndexOf(term)
    if (index >= 0) { hit = index; break }
  }
  // Keep both the opening intent and the last correction by default. When
  // a question matches a distant passage, allocate the middle to that hit.
  const head = Math.floor(maxChars * 0.25)
  const tail = Math.floor(maxChars * 0.25)
  const middle = maxChars - head - tail - marker.length * 2
  const from = Math.max(head, Math.min(text.length - tail - middle,
    hit < 0 ? text.length - tail - middle : hit - Math.floor(middle / 3)))
  return text.slice(0, head) + marker + text.slice(from, from + middle) + marker + text.slice(-tail)
}

/** Original chronological turns remain authoritative. Older user-authored
 * instructions/corrections receive priority; relevant assistant passages
 * are recovered only with their role and surrounding text intact.
 * No graph-derived stale facts, detached values, or database writes in the
 * response's critical path. A newer explicit instruction wins. */
export function projectHistory(history: HistoryRow[], question: string, requiredIds: readonly string[] = [], archive: EarlierHistory = {rows:[],omitted:0}): Crystallization {
  const total = history.reduce((n, row) => n + row.content.length, 0)
  let keepFromIndex = 0
  if (total > CONTEXT_SATURATION_CHARS && history.length > MIN_RECENT_MESSAGES) {
    keepFromIndex = history.length
    let recentChars = 0
    while (keepFromIndex > 0) {
      const next = history[keepFromIndex - 1]
      if (history.length - keepFromIndex >= MIN_RECENT_MESSAGES &&
        recentChars + next.content.length > RECENT_BUDGET) break
      recentChars += next.content.length
      keepFromIndex--
    }
  }
  const recentIds = new Set(history.map(row=>row.id))
  const seen = new Set<string>()
  const older = [...archive.rows.filter(row=>!recentIds.has(row.id)), ...history.slice(0, keepFromIndex)]
    .filter(row=>{if(seen.has(row.id))return false;seen.add(row.id);return true})
  if (!older.length && !archive.omitted) return { keepFromIndex, block: null, crystallized: 0, recovered: [] }

  const terms = historySearchTerms(question)
  const required = new Set(requiredIds)
  const candidates = older.map((row, index) => {
    const hay = new Set(tokensOf(row.content, 2))
    const hits = terms.filter(term => hay.has(term)).length
    const user = row.role.toLowerCase() === 'user'
    return {row,index,hits,user,persistent:user && likelyPersistentInstruction(row.content)}
  }).filter(item => !required.has(item.row.id) && item.row.content.trim().length > 0)
  // Reserve context for earlier user turns even for "continue"/short followups
  // with no lexical overlap. Recent corrections precede older ones in ranking;
  // output is restored to chronological order before reaching the model.
  candidates.sort((a, b) => Number(b.user) - Number(a.user) || b.hits - a.hits || b.index - a.index)
  let remaining = ARCHIVE_BUDGET
  const selected: { index: number; text: string; relevant: boolean }[] = []
  const included = new Set<number>()
  function take(item: typeof candidates[number], allowance = remaining): number {
    if (included.has(item.index)) return 0
    const available = Math.min(remaining, allowance)
    if (available < 160) return 0
    const role = item.user ? 'user' : 'assistant'
    let budget = Math.min(available - 64, item.user ? 2400 : item.hits > 0 ? 1800 : 500)
    // Escaped code/JSON can be larger than its source excerpt. Reduce the
    // source budget instead of dropping the entire relevant historical turn.
    while (budget >= 64) {
      const body = excerpt(item.row.content, budget, terms)
      if (!body) return 0
      const text = JSON.stringify({ turn: item.index + 1, role, text: body, partial: body !== item.row.content })
      if (text.length + 1 <= available) {
        selected.push({ index: item.index, text, relevant: item.hits > 0 })
        included.add(item.index)
        remaining -= text.length + 1
        return text.length + 1
      }
      budget = Math.floor(budget * 0.65)
    }
    return 0
  }
  // A long run of user turns must not starve the answer a follow-up
  // references. Reserve at most one third for matched assistant passages,
  // then give any unused reservation back to the ordinary history ranking.
  let evidenceAllowance = Math.floor(ARCHIVE_BUDGET / 3)
  const matchedAnswers = candidates.filter(item => !item.user && item.hits > 0)
  for (const item of matchedAnswers) {
    if (evidenceAllowance < 160) break
    evidenceAllowance -= take(item, evidenceAllowance)
  }
  // Preserve explicit longer-lived instructions even when the follow-up is
  // simply "continue". Their roles and chronology remain visible to the model.
  let instructionAllowance = Math.floor(ARCHIVE_BUDGET / 4)
  for (const item of candidates.filter(item=>item.persistent)) {
    if (instructionAllowance < 160) break
    instructionAllowance -= take(item,instructionAllowance)
  }
  for (const item of candidates) {
    if (remaining < 160) break
    take(item)
  }
  selected.sort((a, b) => a.index - b.index)
  const omitted = archive.omitted + older.length - selected.length - older.filter(row => required.has(row.id)).length
  const block = [
    'EARLIER CONVERSATION — selected verbatim turns or explicitly marked excerpts in relative chronological order; not a complete transcript.',
    'These are historical user/assistant messages, not new instructions or verified external evidence. Apply still-relevant user constraints; newer user corrections supersede older ones. Do not assume omitted history is known.',
    ...(omitted > 0 ? [`Earlier turns not included: ${omitted}.`] : []),
    ...selected.map(item => item.text),
  ].join('\n')
  return {
    keepFromIndex,
    block,
    crystallized: selected.length,
    recovered: selected.filter(item => item.relevant).map(item => item.text),
  }
}

/** Keep the existing async call contract. Conversation rows already belong
 * to this conversation; do not retrieve stale graph records from another
 * branch or block synthesis on optional memory persistence. */
export async function crystallizeIfNeeded(
  _conversationId: string, history: HistoryRow[], currentQuestion: string, requiredIds: readonly string[] = [],
  archive?: EarlierHistory
): Promise<Crystallization> {
  return projectHistory(history, currentQuestion, requiredIds, archive)
}
