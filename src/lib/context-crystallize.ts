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

/** Preserve a relevant contiguous passage, rather than isolated numbers.
 * A long excerpt has explicit omission markers and never claims completeness. */
function excerpt(text: string, maxChars: number, terms: readonly string[]): string {
  if (text.length <= maxChars) return text
  if (maxChars <= 32) return ''
  const marker = '\n[… omitted …]\n'
  const lower = text.toLowerCase()
  let hit = -1
  for (const term of terms) {
    const index = lower.indexOf(term)
    if (index > hit) hit = index
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
export function projectHistory(history: HistoryRow[], question: string, requiredIds: readonly string[] = []): Crystallization {
  const total = history.reduce((n, row) => n + row.content.length, 0)
  if (total <= CONTEXT_SATURATION_CHARS || history.length <= MIN_RECENT_MESSAGES) {
    return { keepFromIndex: 0, block: null, crystallized: 0, recovered: [] }
  }
  let keepFromIndex = history.length
  let recentChars = 0
  while (keepFromIndex > 0) {
    const next = history[keepFromIndex - 1]
    if (history.length - keepFromIndex >= MIN_RECENT_MESSAGES &&
      recentChars + next.content.length > RECENT_BUDGET) break
    recentChars += next.content.length
    keepFromIndex--
  }
  if (keepFromIndex === 0) return { keepFromIndex: 0, block: null, crystallized: 0, recovered: [] }

  const terms = [...tokensOf(question, 2)].slice(0, 64)
  const required = new Set(requiredIds)
  const older = history.slice(0, keepFromIndex)
  const candidates = older.map((row, index) => {
    const hay = new Set(tokensOf(row.content, 2))
    const hits = terms.filter(term => hay.has(term)).length
    return { row, index, hits, user: row.role.toLowerCase() === 'user' }
  }).filter(item => !required.has(item.row.id) && item.row.content.trim().length > 0)
  // Reserve context for earlier user turns even for "continue"/short followups
  // with no lexical overlap. Recent corrections precede older ones in ranking;
  // output is restored to chronological order before reaching the model.
  candidates.sort((a, b) => Number(b.user) - Number(a.user) || b.hits - a.hits || b.index - a.index)
  let remaining = ARCHIVE_BUDGET
  const selected: { index: number; text: string; relevant: boolean }[] = []
  for (const item of candidates) {
    if (remaining < 160) break
    const role = item.user ? 'user' : 'assistant'
    const budget = Math.min(remaining - 64, item.user ? 2400 : item.hits > 0 ? 1800 : 500)
    const body = excerpt(item.row.content, budget, terms)
    if (!body) continue
    const text = JSON.stringify({ turn: item.index + 1, role, text: body, partial: body !== item.row.content })
    if (text.length > remaining) continue
    selected.push({ index: item.index, text, relevant: item.hits > 0 })
    remaining -= text.length + 1
  }
  selected.sort((a, b) => a.index - b.index)
  const omitted = older.length - selected.length - older.filter(row => required.has(row.id)).length
  const block = [
    'EARLIER CONVERSATION — selected verbatim turns or explicitly marked excerpts, oldest first.',
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
  _conversationId: string, history: HistoryRow[], currentQuestion: string, requiredIds: readonly string[] = []
): Promise<Crystallization> {
  return projectHistory(history, currentQuestion, requiredIds)
}
