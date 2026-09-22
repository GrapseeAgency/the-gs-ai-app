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
 * user's message, or a quoted string inside the draft, or the draft's own
 * "Line N" artefact lines). The count is recomputed locally; a mismatch
 * invalidates the draft and it is regenerated once with the corrected number
 * injected. A "Line N" claim that still fails after regeneration is repaired
 * deterministically (applyNumericCorrection) — a provably wrong number is
 * never emitted to the user.
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

/** "Line N: <count> <unit>" claims — the draft's own produced artefact lines. */
const LINE_CLAIM_RE =
  /\bline\s*(\d{1,2})\s*[:,]?\s*(\d{1,6})\s*(words?|characters?|chars?|letters?)\b/gi

/** Deterministic counting-request detector (turns that must be buffered+checked). */
export function isCountingRequest(userText: string): boolean {
  return (
    /\b(count|how many|number of|no\.?\s*of)\b/i.test(userText) &&
    /\b(words?|characters?|chars?|letters?|vowels?|consonants?|digits?|sentences?|lines?)\b/i.test(
      userText
    )
  )
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

function draftLines(draft: string): string[] {
  return draft
    .split(/\n/)
    .map((l) => l.trim())
    .filter((l) => l.length > 0)
}

/**
 * Verify every counting claim in the draft against a resolvable target:
 *   1. "Line N" claims → the draft's OWN Nth non-empty line (most specific —
 *      generic targets would mismatch by construction),
 *   2. a quoted string inside the draft itself ("…has 8 words" for "…"),
 *   3. the user's message payload (text after a colon),
 *   4. the user's message with the counting question stripped — and when the
 *      strip consumes the WHOLE message (self-referential counting: "How many
 *      words are in this exact sentence?"), the user's message ITSELF is the
 *      counting target.
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
  const quoteRe = new RegExp('[\\u201C"]([^\\u201C\\u201D"]{4,400})[\\u201D"]', 'g')
  for (const m of draft.matchAll(quoteRe)) {
    targets.push(m[1] ?? '')
  }
  // (b) payload after a colon in the user's message ("count the words in: X")
  const colonIdx = userText.indexOf(':')
  if (colonIdx !== -1 && colonIdx + 1 < userText.length) {
    targets.push(userText.slice(colonIdx + 1).trim())
  }
  // (c) the user's message minus the counting question — when the strip eats
  // everything, the message IS the counted artefact (self-referential ask).
  const stripRe = /\b(count|how many|number of|no\.?\s*of)\b[^:]*:?/gi
  const stripped = userText.replace(stripRe, '').trim()
  targets.push(stripped.length >= 4 ? stripped : userText.trim())

  const lines = draftLines(draft)
  /** Content lines = non-empty lines that are NOT themselves count claims. */
  const contentLines = lines.filter(
    (l) => !/\d\s*(words?|characters?|chars?|letters?)\b/i.test(l) && !/\b(both|each)\s+lines?\b/i.test(l)
  )

  for (const claim of claims) {
    const claimed = Number.parseInt(claim[1] ?? '0', 10)
    const unit = claim[2] ?? ''

    // (d) "Line N" claims resolve ONLY against the draft's own lines.
    const lineClaimIdx = claim.index ?? -1
    const before = lineClaimIdx > 0 ? draft.slice(Math.max(0, lineClaimIdx - 20), lineClaimIdx) : ''
    const lineRef = /\bline\s*(\d{1,2})\s*(?:[:,\-–=]|is|has)?\s*$/i.exec(before)
    if (lineRef) {
      const lineNo = Number.parseInt(lineRef[1], 10)
      const line = lines[lineNo - 1]
      if (line && line.length >= 4) {
        const actual = countUnit(unit, line)
        if (actual >= 0 && actual !== claimed) {
          return { claimed, actual, unit: unit.toLowerCase(), target: line }
        }
        continue // verified (or unit uncountable) — next claim
      }
      // Line index unresolvable — fall through to generic targets.
    }

    // (e) equal-count claims over the produced lines — "Both lines: 20
    // characters" / "each line has 20 characters". True iff EVERY content
    // line counts to exactly the claimed number.
    if (/\b(?:both\s+lines?|each\s+line|lines?\s+are\s+each|the\s+two\s+lines?)\b\s*[:,]?\s*$/i.test(before)) {
      if (contentLines.length > 0) {
        const counts = contentLines.map((l) => countUnit(unit, l))
        const firstBad = counts.findIndex((c) => c !== claimed)
        if (firstBad >= 0 && counts[firstBad] >= 0) {
          return {
            claimed,
            actual: counts[firstBad],
            unit: unit.toLowerCase(),
            target: contentLines[firstBad],
          }
        }
        if (counts.every((c) => c === claimed)) continue // verified
      }
      // No content lines resolvable — fall through to generic targets.
    }

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

/**
 * FORENSIC AUDIT [21] — DETERMINISTIC REPAIR. When the regeneration retry
 * STILL states a provably wrong count over the draft's own artefact lines,
 * the draft is rewritten locally:
 *   - "Line N: <claimed> <unit>" spans get the recomputed number substituted;
 *   - equal-count claims ("Both lines: 20 characters") are replaced with the
 *     true per-line counts ("Line 1: 34 characters; Line 2: 29 characters").
 * Returns the corrected draft, or null when no claim was repairable (caller
 * then keeps the emitted draft).
 */
export function applyNumericCorrection(draft: string): string | null {
  const lines = draftLines(draft)
  const contentLines = lines.filter(
    (l) => !/\d\s*(words?|characters?|chars?|letters?)\b/i.test(l) && !/\b(both|each)\s+lines?\b/i.test(l)
  )
  let repaired = false
  let out = draft.replace(
    LINE_CLAIM_RE,
    (full: string, lineNoRaw: string, claimedRaw: string, unitRaw: string) => {
      const lineNo = Number.parseInt(lineNoRaw, 10)
      const line = lines[lineNo - 1]
      if (!line) return full
      const actual = countUnit(unitRaw, line)
      if (actual < 0 || actual === Number.parseInt(claimedRaw, 10)) return full
      repaired = true
      return full.replace(claimedRaw, String(actual))
    }
  )
  // Equal-count claims over the produced lines → true per-line counts.
  const ALL_EQUAL_RE =
    /\b(?:both\s+lines?|each\s+line|lines?\s+are\s+each|the\s+two\s+lines?)\b\s*[:,]?\s*\d{1,6}\s*(words?|characters?|chars?|letters?)\b/gi
  out = out.replace(ALL_EQUAL_RE, (full: string, unitRaw: string) => {
    if (contentLines.length === 0) return full
    const counts = contentLines.map((l) => countUnit(unitRaw, l))
    if (counts.some((c) => c < 0)) return full
    // Only rewrite when the stated claim is actually false.
    const stated = /\d{1,6}/.exec(full)
    if (!stated || counts.every((c) => c === Number.parseInt(stated[0], 10))) return full
    repaired = true
    const unit = unitRaw.toLowerCase()
    return counts.map((c, i) => `Line ${i + 1}: ${c} ${unit}`).join('; ')
  })
  return repaired ? out : null
}

/** Correction instruction for the one regeneration attempt (§5 pattern). */
export function buildNumericCorrection(v: CountingViolation, attempt: number): string {
  const subject =
    v.target.includes('\n') || v.target.length > 140
      ? 'the text in this turn'
      : `the text "${v.target.slice(0, 120)}"`
  return [
    `NUMERIC CORRECTION (attempt ${attempt}): Your previous draft stated "${v.claimed} ${v.unit}" but the actual count is ${v.actual} ${v.unit} for ${subject}.`,
    'That draft was rejected before reaching the user. Recompute and state the correct number, or hedge explicitly if you cannot count it. Do not mention this correction.',
  ].join(' ')
}
