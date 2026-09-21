/**
 * PHASE 8.3 — POST-GENERATION EXECUTION-STATE VALIDATION (spec §5/§17/§18).
 *
 * The forensic baseline proved (CASE I): the tool ran, real evidence was
 * injected and cited — and the model STILL told the user it could not search.
 * Prompt instructions alone cannot guarantee this; the ORCHESTRATION layer
 * validates the synthesized text against the authoritative execution state
 * and regenerates with a corrected evidence-state instruction on violation.
 *
 *   if searchExecuted == true
 *   AND model claims it did not search / cannot access evidence
 *   → INVALID RESPONSE → regenerate (≤2 corrections) with the execution
 *     facts restated. The application state is authoritative.
 *
 * This module is pure text-in → verdict-out so it is trivially auditable.
 */

export interface ExecutionState {
  capability: string
  /** The application actually ran a web search this turn. */
  searchExecuted: boolean
  /** Numbered search evidence was injected into the model context. */
  evidenceProvided: boolean
  /** The application clock evidence was injected. */
  timeProvided: boolean
  /** Extracted document text was injected. */
  documentProvided: boolean
  /** A stored ResearchContext was injected for reuse. */
  reuseProvided: boolean
}

export type GuardViolationKind =
  | 'denies_search'
  | 'denies_evidence'
  | 'denies_time'
  | 'denies_document'
  | 'claims_unexecuted_search'

export interface GuardViolation {
  kind: GuardViolationKind
  match: string
}

/**
 * Denial shapes proven in the wild ("I don't have the ability to perform new
 * searches", "I cannot access the internet", "I can't browse…"). Matched
 * ONLY when the corresponding execution actually happened — a genuine
 * capability failure keeps its honest wording.
 */
const SEARCH_DENIAL_RES: RegExp[] = [
  /\bI\s+(?:cannot|can't|don't|do\s+not|won't)\b[^.\n]{0,32}\b(?:perform|execute|run|do|conduct|make)\b[^.\n]{0,24}\b(?:new\s+|web\s+|live\s+|real[- ]time\s+)?search(?:es|ing)?\b/i,
  /\bI\s+(?:don't|do\s+not|cannot|can't)\s+have\s+(?:the\s+)?(?:ability|capability|capacity|tools?|feature)\b[^.\n]{0,40}\b(?:search|browse|internet|web|online)\b/i,
  /\bI\b[^.\n]{0,60}\b(?:ability|capability)\s+to\s+(?:perform|execute|run|do|make)?\s*(?:new\s+|web\s+|live\s+|any\s+)?search(?:es|ing)?\b/i,
  /\bI\s+(?:cannot|can't|don't|do\s+not|unable\s+to|am\s+unable\s+to|'m\s+unable\s+to)\b[^.\n]{0,32}\b(?:access|reach|connect\s+to|browse|use|search)\b[^.\n]{0,32}\b(?:the\s+)?(?:internet|web|online|live\s+internet|real[- ]time\s+(?:internet|web|data|information|search)|live\s+web)\b/i,
  /\bI\s+(?:don't|do\s+not)\s+have\b[^.\n]{0,32}\b(?:internet|web|online|live|real[- ]time)\b[^.\n]{0,24}\b(?:access|connection|connectivity|capability|ability)\b/i,
  /\bno\s+(?:ability|capability|access|connection)\b[^.\n]{0,32}\b(?:to\s+)?(?:search|browse|the\s+internet|the\s+web|online)\b/i,
  /\bI\s+(?:cannot|can't)\s+browse\b/i,
  /\bI\s+(?:cannot|can't|am\s+unable\s+to|'m\s+unable\s+to)\s+(?:retrieve|fetch|pull|get|gather|collect)\b[^.\n]{0,32}\b(?:live|new|fresh|real[- ]time|additional|more|current)\b[^.\n]{0,32}\b(?:results?|sources?|data|information|pages?|articles?|evidence)\b/i,
  /\bI\s+(?:don't|do\s+not|cannot|can't)\b[^.\n]{0,24}\b(?:real[- ]time|live|internet|web)\s+(?:access|browsing|search|searching|retrieval|capability|abilities)\b/i,
  /\bI\s+am\s+(?:not\s+able|unable)\s+to\s+(?:search|browse|access\s+the\s+(?:internet|web))\b/i,
  /\bI\s+(?:do\s+not|don't)\s+have\s+the\s+ability\s+to\s+perform\s+new\s+searches\b/i,
]

const EVIDENCE_DENIAL_RES: RegExp[] = [
  /\bI\s+(?:cannot|can't|don't|do\s+not)\s+(?:see|have|find|access)\b[^.\n]{0,32}\b(?:any\s+)?(?:search\s+results?|sources?|evidence|citations?|links?)\b[^.\n]{0,32}\b(?:provided|above|injected|supplied|here)\b/i,
]

const TIME_DENIAL_RES: RegExp[] = [
  /\bI\s+(?:cannot|can't|don't|do\s+not)\b[^.\n]{0,28}\b(?:know|tell|determine|provide|give)\b[^.\n]{0,28}\b(?:the\s+)?(?:current\s+|exact\s+|present\s+)?(?:time|date|day)\b/i,
  /\bI\s+(?:don't|do\s+not)\s+have\s+access\s+to\s+(?:the\s+)?(?:current\s+)?(?:time|date)\b/i,
]

const DOCUMENT_DENIAL_RES: RegExp[] = [
  /\bI\s+(?:cannot|can't|don't|do\s+not|am\s+unable\s+to|'m\s+unable\s+to)\b[^.\n]{0,28}\b(?:read|open|access|view|see|process|extract)\b[^.\n]{0,28}\b(?:the\s+|this\s+|your\s+|attached\s+)?(?:document|pdf|file|attachment)\b/i,
]

/**
 * Reverse direction (§11/§12): the model must not CLAIM a search that never
 * ran. Narrow first-person past-tense shapes only — descriptions of the
 * product's abilities ("you can ask me to search the web") never match.
 */
const FALSE_SEARCH_CLAIM_RES: RegExp[] = [
  /\bI\s+(?:searched|scanned|browsed|crawled)\s+(?:the\s+)?(?:web|internet|net)\b/i,
  /\bmy\s+(?:web\s+)?search(?:es)?\s+(?:returned|found|showed|revealed|produced)\b/i,
  /\baccording\s+to\s+my\s+(?:web\s+)?search\b/i,
  /\bI\s+(?:found|gathered|collected)\s+(?:the\s+following|these|\d+)\s+(?:web\s+)?(?:results?|sources?|links?|articles?)\b/i,
  /\b(?:search|web)\s+results?\s+(?:show|indicate|say|suggest)\b[^.\n]{0,40}\b(?:that|the)\b/i,
]

function firstMatch(regexes: RegExp[], text: string): GuardViolation | null {
  for (const re of regexes) {
    const m = text.match(re)
    if (m) return { kind: 'denies_search', match: m[0] } // kind refined by caller
  }
  return null
}

/**
 * Validate a synthesized answer against the authoritative execution state.
 * Returns the first violation found, or null when the response is consistent.
 */
export function detectExecutionContradiction(
  text: string,
  state: ExecutionState
): GuardViolation | null {
  if (!text) return null

  if (state.searchExecuted) {
    const v = firstMatch(SEARCH_DENIAL_RES, text)
    if (v) return { kind: 'denies_search', match: v.match }
    if (state.evidenceProvided) {
      const v2 = firstMatch(EVIDENCE_DENIAL_RES, text)
      if (v2) return { kind: 'denies_evidence', match: v2.match }
    }
  } else if (
    !state.searchExecuted &&
    !state.reuseProvided &&
    !state.evidenceProvided
  ) {
    // Only police false claims on turns where the model received NO evidence
    // at all (it cannot be talking about its own search otherwise).
    const v = firstMatch(FALSE_SEARCH_CLAIM_RES, text)
    if (v) return { kind: 'claims_unexecuted_search', match: v.match }
  }

  if (state.timeProvided) {
    const v = firstMatch(TIME_DENIAL_RES, text)
    if (v) return { kind: 'denies_time', match: v.match }
  }
  if (state.documentProvided) {
    const v = firstMatch(DOCUMENT_DENIAL_RES, text)
    if (v) return { kind: 'denies_document', match: v.match }
  }
  return null
}

/**
 * The corrected evidence-state instruction injected before regeneration (§5).
 * It rides INSIDE the tool-result half of the final user message so the
 * verbatim CURRENT USER REQUEST stays last (§7).
 */
export function buildCorrectionInstruction(
  state: ExecutionState,
  violation: GuardViolation,
  attempt: number
): string {
  const facts: string[] = []
  if (state.searchExecuted) {
    facts.push(
      'searchExecuted=true — the application DID run the web search for this turn'
    )
    if (state.evidenceProvided)
      facts.push('the TOOL RESULT above contains its real retrieved evidence')
    if (state.reuseProvided)
      facts.push('the TOOL RESULT above contains the real stored research evidence you were told to reuse')
  }
  if (state.timeProvided) facts.push('the application clock above is the authoritative current time')
  if (state.documentProvided) facts.push('the document text above was successfully extracted and supplied')

  return [
    `EXECUTION-STATE CORRECTION (attempt ${attempt}): Your previous draft contradicted the application's execution state — it was rejected before reaching the user.`,
    `Authoritative facts: ${facts.join('; ')}.`,
    `The rejected draft said things like "${violation.match.slice(0, 80)}". That claim is factually wrong for this turn.`,
    'Rewrite the answer now: use the tool result above as real data, answer the user\'s request naturally in your own wording, and do NOT claim you cannot search, browse, access the internet, or read the supplied evidence. Do not mention this correction or the rejection.',
  ].join(' ')
}

/**
 * Insert a correction instruction into the final user message BEFORE the
 * CURRENT USER REQUEST header, keeping the verbatim request last (§7).
 */
export function injectCorrection(
  finalUserContent: string,
  correction: string
): string {
  const marker = `\n\n${'CURRENT USER REQUEST'}\n`
  const idx = finalUserContent.lastIndexOf(marker)
  if (idx === -1) {
    // No evidence marker (defensive) — append; request stays the final line.
    return `${finalUserContent}\n\n${correction}`
  }
  return `${finalUserContent.slice(0, idx)}\n\n${correction}${finalUserContent.slice(idx)}`
}
