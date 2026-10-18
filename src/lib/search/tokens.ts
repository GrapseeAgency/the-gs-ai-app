/**
 * Script-aware tokenisation, in ONE place.
 *
 * WHY THIS MODULE EXISTS: the same ASCII-only regex
 * (`replace(/[^a-z0-9\s]/g, ' ')`) had been copy-pasted into five different
 * relevance paths. For any non-Latin language it produces an EMPTY token set,
 * and each copy failed differently:
 *
 *   aggregate.ts   termOverlap() = 0 -> ranking lost its relevance signal;
 *                  titleWords() empty -> syndication never grouped;
 *                  isPoisonedSerp() discarded whole engine responses for a
 *                  Latin query answered in Bengali.
 *   engines.ts     the bing decoy guard compared an empty query set to an empty
 *                  title set and threw away real results.
 *   extract.ts     the query-centred window never found its query, so long
 *                  Bengali articles were always read from the top.
 *   research.ts    same, for the evidence window sent to the model.
 *   ledger.ts      key terms were empty -> no claims, no gap detection.
 *
 * One implementation, one set of rules, one place to be wrong.
 */

/** Scripts that do not space-delimit words: match on character bigrams. */
const CJK_RE = /[\u3040-\u30ff\u3400-\u4dbf\u4e00-\u9fff\uac00-\ud7af]/
const LATIN_RE = /[a-z0-9]/

/**
 * Tokens for relevance comparison.
 *
 * `\p{M}` is NOT optional: Bengali, Hindi and Arabic vowel signs are COMBINING
 * MARKS, not letters. Without it "খেলার" shatters into "খ ল র", every piece
 * falls under the length floor, and the whole title tokenises to nothing.
 *
 * The length floor is script-dependent: Latin needs 4+ characters to be
 * meaningful (drops "the", "for"), while Bengali/Hindi/Arabic words are often
 * 2-3 characters and still carry the entire meaning.
 */
export function tokensOf(text: string, minLen = 2): Set<string> {
  const out = new Set<string>()
  if (!text) return out
  const cleaned = text.toLowerCase().replace(/[^\p{L}\p{M}\p{N}\s]/gu, ' ')
  for (const w of cleaned.split(/\s+/)) {
    if (w.length === 0) continue
    if (CJK_RE.test(w)) {
      for (let i = 0; i + 1 < w.length; i++) out.add(w.slice(i, i + 2))
      continue
    }
    const floor = LATIN_RE.test(w) ? Math.max(minLen, 4) : minLen
    if (w.length >= floor) out.add(w)
  }
  return out
}

/** Does this token set contain any Latin-script token? */
export function hasLatin(tokens: Iterable<string>): boolean {
  for (const t of tokens) if (LATIN_RE.test(t)) return true
  return false
}

/**
 * True when comparing the two sets is meaningless because they are written in
 * different scripts — i.e. one side is translated, not unrelated.
 */
export function crossScript(a: Set<string>, b: Set<string>): boolean {
  return hasLatin(a) !== hasLatin(b)
}

/** How many of the query's tokens appear in the title, as a 0..1 ratio. */
export function overlapRatio(queryTokens: Set<string>, titleTokens: Set<string>): number {
  if (queryTokens.size === 0) return 0
  let hits = 0
  for (const w of queryTokens) if (titleTokens.has(w)) hits += 1
  return hits / queryTokens.size
}
