/**
 * SILENT-FAILURE GATE — multi-layer validation, runs on EVERY turn before
 * persistence (ScienceDirect fault-injection study, 2026: 146,016 traces
 * showed answer-level checks miss evidence and citation degradations).
 *
 * The GS app's known silent-failure classes — audited in production:
 *   [SF-1] citation-without-source / uncited-read: the answer cites ordinals
 *          that do not match the sources actually read;
 *   [SF-2] search answered with zero sources read (the "headline only"
 *          pipeline pretending at grounding) unless the turn is the HONEST
 *          no-results outcome;
 *   [SF-3] an unread source presented to the model without the visible
 *          HEADLINE/SNIPPET / NOT RETRIEVED label (§34: a headline is not
 *          the article);
 *   [SF-4] the false "offline" label — an answer persisted as done while the
 *          text claims the service is offline;
 *   [SF-5] evidence without any source backing (fabricated ordinals);
 *   [SF-6] a register-class turn answered with a refusal/decline boilerplate
 *          ("I cannot", "I don't have access") — the exact register failure
 *          the capability router exists to prevent.
 *
 * CONTRACT:
 *  - Pure and deterministic: same input → same violations. No network, no
 *    clocks. Unit-testable without a provider.
 *  - The gate NEVER edits the answer. On violation the caller logs the
 *    failure with the turn trace and marks the turn FAILED in the trace row
 *    (silentFailures) — a silently-failed turn is never persisted as a
 *    silently-clean one. Regeneration happens upstream where the pipeline
 *    still owns the draft.
 *  - Scoping is explicit (documented per assertion): the evidence/citation
 *    layers apply to source-backed turns (research/reuse) that persisted an
 *    answer; error/clarify/blocked turns persist no answer and cannot
 *    silently fail. The TIME capability's clock evidence is not a web source
 *    and is exempt from source-count checks.
 */

export interface SilentFailureInput {
  /** Turn execution shape — 'research' | 'reuse' are the source-backed kinds. */
  turnKind: 'research' | 'reuse' | 'time' | 'clarify' | 'none'
  /** done | clarify | error | blocked — 'done' means an answer was persisted. */
  finalStatus: string
  /** True when the search phase executed this turn (turn.kind === 'research'). */
  searchExecuted: boolean
  /** True when the search pipeline ended in the HONEST no-results outcome. */
  noResults: boolean
  /** Register-class turn (router shortfall classification). */
  registerClass: boolean
  /** Distinct sources returned by the search phase (fresh + reused). */
  sourceCount: number
  /** Sources actually read: fresh retrieved + reuse-context sources. */
  sourcesRead: number
  /** Sources whose retrieval failed outright. */
  sourcesFailed: number
  /** Evidence items presented to the model (maxOrdinal / reuse sources). */
  evidenceCount: number
  /** Distinct [N] ordinals cited by the final answer. */
  citationCount: number
  /** The evidence block shown to the model (null when the pipeline produced none). */
  evidenceBlock: string | null
  /** The final persisted answer text (null when no answer was persisted). */
  answerText: string | null
}

export interface SilentFailureVerdict {
  ok: boolean
  violations: string[]
}

/** Markers the research pipeline MUST emit for unread sources (§34 label). */
const HEADLINE_LABEL = 'HEADLINE/SNIPPET ONLY'
const NOT_RETRIEVED_LABEL = 'NOT RETRIEVED'

/** Self-referential offline claims — the false-offline failure class. */
const FALSE_OFFLINE_RE = /\b(?:i'?m|we'?re|we\s+are|gs(?:\s+ai)?\s+is|the\s+service\s+is|currently)\s+offline\b/i

/** Register refusal boilerplate — the register-turn failure class. */
const REGISTER_REFUSAL_RES = [/\bI cannot\b/i, /\bI don'?t have access\b/i]

export function runSilentFailureGate(input: SilentFailureInput): SilentFailureVerdict {
  const violations: string[] = []
  const answerPersisted = input.finalStatus === 'done' && input.answerText !== null
  const sourceBacked = input.turnKind === 'research' || input.turnKind === 'reuse'

  // --- search turns ----------------------------------------------------------
  if (input.searchExecuted) {
    // [SF-1] citation↔read integrity: every ordinal cited must correspond to
    // a source actually read, and every read source must be cited — a
    // mismatch in either direction is evidence degradation.
    if (answerPersisted && input.citationCount !== input.sourcesRead) {
      violations.push(
        `citation_mismatch: cited=${input.citationCount} sourcesRead=${input.sourcesRead} (citation-count must equal sources-read)`,
      )
    }
    // [SF-2] zero-source grounding: a search turn with an answer must have
    // read ≥1 source, unless the turn is the honest no-results outcome.
    if (answerPersisted && !input.noResults && input.sourcesRead < 1) {
      violations.push(
        `no_source_read: sourcesRead=${input.sourcesRead} sourceCount=${input.sourceCount} finalStatus=${input.finalStatus} (expected ≥1 read source or the honest no_results outcome)`,
      )
    }
    // [SF-3] the unread must be labeled: when the pipeline read fewer sources
    // than it returned, the evidence block must carry the visible
    // HEADLINE/SNIPPET / NOT RETRIEVED markers — silent headline grounding is
    // exactly the fault the §34 rule forbids.
    const unread = input.sourceCount - input.sourcesRead
    if (answerPersisted && unread > 0 && input.evidenceBlock !== null) {
      const labeled =
        input.evidenceBlock.includes(HEADLINE_LABEL) || input.evidenceBlock.includes(NOT_RETRIEVED_LABEL)
      if (!labeled) {
        violations.push(
          `unlabeled_unread_sources: ${unread} unread source(s) and no "${HEADLINE_LABEL}"/"${NOT_RETRIEVED_LABEL}" marker in the evidence block`,
        )
      }
    }
  }

  // --- all source-backed turns with a persisted answer ------------------------
  if (sourceBacked && answerPersisted) {
    // [SF-5] evidence without a source backing it is fabricated by definition.
    if (input.evidenceCount > 0 && input.sourceCount === 0) {
      violations.push(`evidence_without_source: evidenceCount=${input.evidenceCount} sourceCount=0`)
    }
    // [SF-2b] a completed search turn must have produced evidence from the
    // search (honest no-results exempt — the answer claims nothing grounded).
    if (input.searchExecuted && input.evidenceCount === 0 && !input.noResults) {
      violations.push(`search_without_evidence: searchExecuted=true evidenceCount=0 finalStatus=${input.finalStatus}`)
    }
  }

  // --- false-offline label (any persisted answer) -----------------------------
  if (answerPersisted && input.answerText && FALSE_OFFLINE_RE.test(input.answerText)) {
    // [SF-4] the turn PERSISTED as done — claiming offline while doing so is
    // the false-offline failure (real outages end in error/no_results, not a
    // persisted done answer).
    violations.push(`false_offline_label: answer persisted as done while claiming offline`)
  }

  // --- register turns ----------------------------------------------------------
  if (input.registerClass && answerPersisted && input.answerText) {
    for (const re of REGISTER_REFUSAL_RES) {
      if (re.test(input.answerText)) {
        violations.push(`register_refusal: answer matched /${re.source}/${re.flags} — register turns are served, not declined`)
        break
      }
    }
  }

  return { ok: violations.length === 0, violations }
}
