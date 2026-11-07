import { cleanPlainText } from './icons'

export interface Concept { term: string; definition: string }

/** Active recall from explicit term/definition pairs only. No invented facts,
 * instructions interpreted as concepts, truncation, or external quiz answers. */
export function extractConceptDeck(text: string): Concept[] {
  if (/^\s*(?:```|~~~)|^\s*[-*+]\s+\[[ xX]\]/m.test(text)) return []
  const concepts: Concept[] = []
  for (const line of text.split('\n')) {
    const item = /^\s*(?:[-*+]\s+|\d+[.)]\s+)(?:\*\*([^*\n]+?)\*\*\s*[:—–-]?|([^:—–\n]+?)\s*[:—–])\s*(.+)$/.exec(line)
    if (!item) continue
    const term = cleanPlainText(item[1] ?? item[2]).replace(/[:—–-]+$/, '').trim()
    const definition = cleanPlainText(item[3])
    if (term.length < 3 || term.length > 44 || definition.length < 14 || definition.length > 600 || /https?:|\[[0-9]+\]/.test(definition)) continue
    if (concepts.some((c) => c.term.toLowerCase() === term.toLowerCase() || c.definition === definition)) continue
    concepts.push({ term, definition })
    if (concepts.length === 8) break
  }
  return concepts.length >= 3 ? concepts : []
}

/** Deterministic rotation varies answer placement. All distractors are terms
 * from this same answer; feedback repeats the original definition. */
export function conceptQuestions(concepts: Concept[]) {
  return concepts.map((c, i) => {
    const indices = [i, ...concepts.map((_, j) => j).filter((j) => j !== i).slice(0, 3)]
    const shift = i % indices.length
    const order = [...indices.slice(shift), ...indices.slice(0, shift)]
    return { q: `Which term matches: ${c.definition}`, options: order.map((j) => concepts[j].term), answer: order.indexOf(i), explain: `${c.term}: ${c.definition}` }
  })
}
