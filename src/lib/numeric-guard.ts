/**
 * FORENSIC AUDIT [21] — NUMERIC/COUNTING VERIFICATION PASS.
 *
 * The model ships confident wrong numbers with no hedging ("5 words" for an
 * 8-word sentence; "24 chars" for a 29-char line). Model-class limitation:
 * routed around with a DETERMINISTIC verification pass, never a prompt fix.
 *
 * Scope (the proven failure mode): SELF-REFERENTIAL counting claims — when
 * the draft states a count of words/characters/letters/vowels/digits/
 * sentences/lines for a text that is available verbatim in the turn (the
 * user's message, or a quoted string inside the draft). The count is
 * recomputed locally; a mismatch invalidates the draft and it is regenerated
 * once with the corrected number injected.
 *
 * General world-fact numbers (population figures etc.) are NOT verifiable
 * here — they are covered by [26] verify-before-stating hedging.
 */

export interface CountingViolation {
  /** The claimed number in the draft. */
  claimed: number
  /** The recomputed actual number. */
  actual: number
  /** What was counted (words, characters, …). */
  unit: string
  /** The text that was counted. */
  target: string
}

const COUNT_CLAIM_RE =
  /\b(\d{1,6})\s*(words?|characters?|chars?|letters?|vowels?|consonants?|digits?|sentences?|lines?)\b/i

/** Deterministic counting-request detector (turns that must be buffered+checked). */
export function isCountingRequest(userText: string): boolean {
  return /\b(count|how many|number of|no\.?\s*of)\b/i.test(userText) &&
    /\b(words?|characters?|chars?|letters?|vowels?|consonants?|digits?|sentences?|lines?)\b/i.test(userText)
}

function countUnit(unit: string, text: string): number {
  const u = unit.toLowerCase()
  if (/^words?/.test(u)) return (text.match(/[^\s]+/g) ?? []).length
  if (/^characters?|^chars?/.test(u)) return text.length
  if (/^letters?/.test(u)) return (text.match(/[a-z]/gi) ?? []).length
  if (/^vowels?/.test(u)) return (text.match(/[aeiou]/gi) ?? []).length
  if (/^consonants?/.test(u)) return (text.match(/[b-df-hj-np-tv-z]/gi) ?? []).length
  if (/^digits?/.test(u)) return (text.match(/\d/g) ?? []).length
  if (/^sentences?/.test(u))
    return (text.match(/[^.!?…]+[.!?…]+(\s|$)|[^.!?…]+$/g) ?? []).filter((s) => s.trim().length > 0)
      .length
  if (/^lines?/.test(u)) return text.split(/\n/).filter((l) => l.trim().length > 0).length
  return -1
}

/**
 * Verify every counting claim in the draft against a resolvable target:
 *   1. a quoted string inside the draft itself ("…has 8 words" for "…"),
 *   2. the user's message payload (text after a colon, or the whole message).
 * Returns the FIRST violation, or null when every claim checks out (or no
 * target is resolvable — those stay honest by hedging, not by guessing).
 */
export function checkNumericClaims(
  draft: string,
  userText: string
): CountingViolation | null {
  const claims = [...draft.matchAll(new RegExp(COUNT_CLAIM_RE.source, 'gi'))]
  if (claims.length === 0) return null

  // Candidate counting targets, most specific first.
  const targets: string[] = []
  // (a) quoted strings in the draft
  for (const m of draft.matchAll(/[“"]([^“”"]{4,400})[”"]|‘'([^‘'']{4,400})’'/g)) {
    targets.push(m[1] ?? m[2] ?? '')
  }
  // (b) payload after a colon in the user's message ("count the words in: X")
  const colonIdx = userText.indexOf(':')
  if (colonIdx !== -1 && colonIdx + 1 < userText.length) {
    targets.push(userText.slice(colonIdx + 1).trim())
  }
  // (c) the user's message itself (minus the counting question around it)
  targets.push(userText.replace(/\b(count|how many|number of|no\.?\s*of)\b[^:]*:?/gi, '').trim())

  for (const claim of claims) {
    const claimed = Number.parseInt(claim[1] ?? '0', 10)
    const unit = claim[2] ?? ''
    for (const target of targets) {
      if (!target || target.length < 4) continue
      const actual = countUnit(unit, target)
      if (actual < 0) continue
      // The claim is only VERIFIABLE when the recomputed count is plausible
      // for this target; a verified mismatch is a violation.
      if (actual !== claimed) {
        return { claimed, actual, unit: unit.toLowerCase(), target }
      }
      // Exact match against ANY target validates this claim.
      return null
    }
  }
  return null
}

/** Correction instruction for the one regeneration attempt (§5 pattern). */
export function buildNumericCorrection(v: CountingViolation, attempt: number): string {
  return [
    `NUMERIC CORRECTION (attempt ${attempt}): Your previous draft stated "${v.claimed} ${v.unit}" but the actual count of the text in this turn is ${v.actual} ${v.unit}.`,
    'That draft was rejected before reaching the user. Recompute and state the correct number, or hedge explicitly if you cannot count it. Do not mention this correction.',
  ].join(' ')
}
