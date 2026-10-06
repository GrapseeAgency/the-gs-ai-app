/**
 * SILENT-FAILURE GATE — hermetic contract tests (no network, no provider).
 * Mirrors scripts/test-eval-graders.ts: the gate must be a pure function of
 * its input; every assertion class has a firing fixture and a clean fixture.
 * CI: `bun scripts/test-silent-failure-gate.ts` exits 1 on any violation.
 */

import { runSilentFailureGate, type SilentFailureInput } from '../src/lib/silent-failure-gate'

let failures = 0
function expectViolations(name: string, input: SilentFailureInput, expectedFragment: string): void {
  const v = runSilentFailureGate(input)
  if (v.ok || !v.violations.some((x) => x.includes(expectedFragment))) {
    failures++
    console.error(`FAIL ${name}: expected a violation containing "${expectedFragment}", got [${v.violations.join(' | ') || 'CLEAN'}]`)
  } else {
    console.log(`ok   ${name} → ${v.violations[0].slice(0, 100)}`)
  }
}
function expectClean(name: string, input: SilentFailureInput): void {
  const v = runSilentFailureGate(input)
  if (!v.ok) {
    failures++
    console.error(`FAIL ${name}: expected clean, got [${v.violations.join(' | ')}]`)
  } else {
    console.log(`ok   ${name} → clean`)
  }
}

const base: SilentFailureInput = {
  turnKind: 'none',
  finalStatus: 'done',
  searchExecuted: false,
  noResults: false,
  registerClass: false,
  sourceCount: 0,
  sourcesRead: 0,
  sourcesFailed: 0,
  evidenceCount: 0,
  citedOrdinals: [],
  freshSourceStatuses: [],
  historySourceCount: 0,
  evidenceBlock: null,
  answerText: 'Here is what I found.',
}

// [SF-1] citation↔read integrity — directional: cited ordinals must be grounded
// in sources actually presented as usable evidence.
expectViolations(
  'cites an ordinal beyond the read set (cited [1,2,3], read 2)',
  { ...base, turnKind: 'research', searchExecuted: true, sourceCount: 5, sourcesRead: 2, citedOrdinals: [1, 2, 3], freshSourceStatuses: ['retrieved', 'retrieved', 'failed', 'failed', 'failed'], evidenceCount: 5, evidenceBlock: 'evidence' },
  'citation_mismatch',
)
expectClean('citations match reads', { ...base, turnKind: 'research', searchExecuted: true, sourceCount: 2, sourcesRead: 2, citedOrdinals: [1, 2], freshSourceStatuses: ['retrieved', 'retrieved'], evidenceCount: 2, evidenceBlock: 'evidence' })
// Production case 2026-10-06: selective synthesis reads 4, cites the 2 that
// mattered. The old count-equality check false-blocked this shape.
expectClean('selective synthesis (cited 2 of 4 read) is not a failure', { ...base, turnKind: 'research', searchExecuted: true, sourceCount: 4, sourcesRead: 4, citedOrdinals: [1, 3], freshSourceStatuses: ['retrieved', 'retrieved', 'retrieved', 'retrieved'], evidenceCount: 4, evidenceBlock: 'evidence' })
// Citing a source whose retrieval FAILED is the silent-failure class itself —
// the count-based predecessor passed this shape when counts coincided.
expectViolations(
  'cites a failed-retrieval source (cited [1,2], one failed)',
  { ...base, turnKind: 'research', searchExecuted: true, sourceCount: 2, sourcesRead: 1, sourcesFailed: 1, citedOrdinals: [1, 2], freshSourceStatuses: ['retrieved', 'failed'], evidenceCount: 2, evidenceBlock: 'evidence' },
  'citation_mismatch',
)
// History sources sit beyond the fresh range; only injected history survives
// the upstream sanitize + parser bound, so a history-range cite is grounded.
expectClean('cites an injected history source (cited [1,3], history 2)', { ...base, turnKind: 'research', searchExecuted: true, sourceCount: 1, sourcesRead: 1, citedOrdinals: [1, 3], freshSourceStatuses: ['retrieved'], historySourceCount: 2, evidenceCount: 3, evidenceBlock: 'evidence' })
// snippet_only sources are presented under the SF-3 label and citable — note
// the evidence block must still carry the label (SF-3) since the source is unread.
expectClean('cites a snippet_only source (cited [1,2], one snippet)', { ...base, turnKind: 'research', searchExecuted: true, sourceCount: 2, sourcesRead: 1, citedOrdinals: [1, 2], freshSourceStatuses: ['retrieved', 'snippet_only'], evidenceCount: 2, evidenceBlock: '(2) STATUS: HEADLINE/SNIPPET ONLY — snippet not full text' })

// [SF-2] zero-source grounding
expectViolations('answered with zero sources read', { ...base, turnKind: 'research', searchExecuted: true, sourceCount: 4, sourcesRead: 0, sourcesFailed: 4, citedOrdinals: [], freshSourceStatuses: ['failed', 'failed', 'failed', 'failed'], evidenceCount: 4, evidenceBlock: 'evidence' }, 'no_source_read')
expectClean('honest no-results outcome is exempt', { ...base, turnKind: 'research', searchExecuted: true, sourceCount: 0, sourcesRead: 0, citedOrdinals: [], noResults: true })

// [SF-3] unread sources must be labeled
expectViolations('unread sources unlabeled', { ...base, turnKind: 'research', searchExecuted: true, sourceCount: 3, sourcesRead: 1, citedOrdinals: [1], freshSourceStatuses: ['retrieved', 'snippet_only', 'snippet_only'], evidenceCount: 3, evidenceBlock: '(1) Source A — full text' }, 'unlabeled_unread_sources')
expectClean('unread sources carry the HEADLINE label', { ...base, turnKind: 'research', searchExecuted: true, sourceCount: 3, sourcesRead: 1, citedOrdinals: [1], freshSourceStatuses: ['retrieved', 'snippet_only', 'snippet_only'], evidenceCount: 3, evidenceBlock: '(2) STATUS: HEADLINE/SNIPPET ONLY — the article was NOT retrieved' })
expectClean('unread sources carry the NOT RETRIEVED label', { ...base, turnKind: 'research', searchExecuted: true, sourceCount: 3, sourcesRead: 1, citedOrdinals: [1], freshSourceStatuses: ['retrieved', 'snippet_only', 'snippet_only'], evidenceCount: 3, evidenceBlock: '(3) STATUS: NOT RETRIEVED (timeout)' })

// [SF-5]/[SF-2b] evidence integrity
expectViolations('evidence without sources', { ...base, turnKind: 'research', searchExecuted: true, sourceCount: 0, sourcesRead: 0, citedOrdinals: [], evidenceCount: 5, evidenceBlock: 'evidence' }, 'evidence_without_source')
expectViolations('search executed but no evidence, not no-results', { ...base, turnKind: 'research', searchExecuted: true, sourceCount: 0, sourcesRead: 0, citedOrdinals: [], evidenceCount: 0 }, 'search_without_evidence')

// [SF-4] false offline label
expectViolations('false offline label on persisted answer', { ...base, answerText: "I'm offline right now, try again later." }, 'false_offline_label')
expectClean('offline as topic (not self-claim) is fine', { ...base, answerText: 'To work offline, your device stores data locally.' })
expectClean('error turns persist no answer — nothing to silently fail', { ...base, finalStatus: 'error', answerText: null })

// [SF-6] register refusals
expectViolations('register turn refuses with "I cannot"', { ...base, registerClass: true, answerText: 'I cannot help with that.' }, 'register_refusal')
expectViolations('register turn claims no access', { ...base, registerClass: true, answerText: "I don't have access to jokes." }, 'register_refusal')
expectClean('register turn plays along', { ...base, registerClass: true, answerText: "My bad—sorry it missed the mark. Here's another one…" })
expectClean('non-register turn may decline (safety refusals are legal elsewhere)', { ...base, answerText: 'I cannot assist with that request.' })

// error/clarify turns persist no answer → gate stays silent
expectClean('clarify turn', { ...base, turnKind: 'clarify', finalStatus: 'clarify', answerText: null })
expectClean('time turn exempt from source counts', { ...base, turnKind: 'time', searchExecuted: false, evidenceCount: 1, sourceCount: 0 })

console.log(failures === 0 ? '\nALL GATE CONTRACT TESTS PASS' : `\n${failures} CONTRACT TEST(S) FAILED`)
process.exit(failures === 0 ? 0 : 1)
