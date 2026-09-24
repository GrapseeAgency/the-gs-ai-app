/**
 * PHASE 7 — CONTEXT-NOISE MITIGATION (Cognitive Scaffold, ACL 2026).
 *
 * Linear history accumulation degrades reasoning and dilutes fine-grained
 * evidence — the "context-noise trade-off". Factorized memory:
 *
 *   - Fluid Working Context  — the immediate reasoning window (recent turns
 *                              + evidence, untouched).
 *   - Persistent Knowledge   — KnowledgeNodes: event snapshots crystallized
 *     Graph                    from saturated history. NUMBERS AND ENTITIES
 *                              ARE PRESERVED BYTE-EXACT — never summarized,
 *                              never paraphrased.
 *
 * CRYSTALLIZATION: when the working context saturates, the OLDEST history
 * is crystallized into exact event snapshots and collapses into one compact
 * block; the model messages keep only recent turns verbatim.
 *
 * DUAL-PATH RETRIEVAL: during reasoning the agent proactively recovers
 * precise evidence from the graph — nodes matching the current question's
 * terms ride back into the context as an explicit RECOVERED block.
 */

import { db } from '@/lib/db'

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

/**
 * Crystallize if needed. Writes KnowledgeNodes (idempotent per messageId via
 * the nodeKey unique constraint), returns the reduction plan for the model
 * context. Short conversations: untouched (keepFromIndex 0, block null).
 */
export async function crystallizeIfNeeded(
  conversationId: string,
  history: HistoryRow[],
  currentQuestion: string
): Promise<Crystallization> {
  const totalChars = history.reduce((n, m) => n + m.content.length, 0)
  if (totalChars <= CONTEXT_SATURATION_CHARS || history.length < 4) {
    return { keepFromIndex: 0, block: null, crystallized: 0, recovered: [] }
  }

  // crystallize the oldest half; keep the recent half verbatim
  const crystallizeCount = Math.ceil(history.length / 2)
  const toCrystallize = history.slice(0, crystallizeCount)
  const keepFromIndex = crystallizeCount

  const existing = await db.knowledgeNode.findMany({
    where: { conversationId, nodeKey: { in: toCrystallize.map((m) => `snap:${m.id}`) } },
    select: { nodeKey: true },
  })
  const existingKeys = new Set(existing.map((n) => n.nodeKey))

  let written = 0
  const lines: string[] = []
  let seq = 0
  for (const row of toCrystallize) {
    const snap = snapshotOf(row, seq++)
    lines.push(
      `[${snap.meta.role}] "${snap.text}"` +
        ((snap.meta.numbers as string[]).length > 0
          ? ` (exact figures: ${(snap.meta.numbers as string[]).join(', ')})`
          : '')
    )
    if (existingKeys.has(snap.nodeKey)) continue
    await db.knowledgeNode
      .upsert({
        where: { conversationId_nodeKey: { conversationId, nodeKey: snap.nodeKey } },
        create: { conversationId, nodeKey: snap.nodeKey, kind: snap.kind, text: snap.text, meta: JSON.stringify(snap.meta) },
        update: {},
      })
      .catch(() => undefined)
    // entities/values as individually retrievable nodes (deduped by key)
    for (const ent of snap.meta.entities as string[]) {
      await db.knowledgeNode
        .upsert({
          where: { conversationId_nodeKey: { conversationId, nodeKey: `ent:${ent.toLowerCase()}` } },
          create: { conversationId, nodeKey: `ent:${ent.toLowerCase()}`, kind: 'entity', text: ent, meta: JSON.stringify({ sourceMessageId: row.id }) },
          update: {},
        })
        .catch(() => undefined)
    }
    for (const num of snap.meta.numbers as string[]) {
      await db.knowledgeNode
        .upsert({
          where: { conversationId_nodeKey: { conversationId, nodeKey: `val:${row.id}:${num}` } },
          create: { conversationId, nodeKey: `val:${row.id}:${num}`, kind: 'value', text: num, meta: JSON.stringify({ sourceMessageId: row.id, role: snap.meta.role }) },
          update: {},
        })
        .catch(() => undefined)
    }
    written++
  }

  // DUAL-PATH RETRIEVAL — recover precise nodes matching the current question
  const questionTerms = currentQuestion
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, ' ')
    .split(/\s+/)
    .filter((w) => w.length >= 4)
  const nodes = await db.knowledgeNode.findMany({
    where: { conversationId, kind: { in: ['snapshot', 'value', 'claim'] } },
    orderBy: { createdAt: 'desc' },
    take: 120,
  })
  const scored = nodes
    .map((n) => {
      const hay = `${n.text} ${n.meta}`.toLowerCase()
      const hits = questionTerms.filter((t) => hay.includes(t)).length
      return { n, hits }
    })
    .filter((x) => x.hits > 0)
    .sort((a, b) => b.hits - a.hits)
    .slice(0, 6)
  const recovered = scored.map((x) =>
    x.n.kind === 'value'
      ? `exact figure on record: ${x.n.text}`
      : `[${(safeParse(x.n.meta).role as string) ?? 'earlier'}] ${x.n.text.slice(0, 160)}`
  )

  const block = [
    'EARLIER CONVERSATION — CRYSTALLIZED EVENT SNAPSHOTS. Numbers and names below are EXACT, not paraphrases; rely on them as on the original turns.',
    ...lines,
    ...(recovered.length > 0
      ? ['', 'RECOVERED CONTEXT (retrieved from the knowledge graph for this question):', ...recovered.map((r) => `- ${r}`)]
      : []),
  ].join('\n')

  console.log(
    `GS-CRYSTALLIZE conv=${conversationId} historyChars=${totalChars} crystallized=${written} keep=${history.length - keepFromIndex} recovered=${recovered.length}`
  )
  return { keepFromIndex, block, crystallized: written, recovered }
}

const safeParse = (raw: string): Record<string, unknown> => {
  try {
    return JSON.parse(raw) as Record<string, unknown>
  } catch {
    return {}
  }
}
