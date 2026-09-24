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
  citationCount: 0,
  evidenceBlock: null,
  answerText: 'Here is what I found.',
}

// [SF-1] citation↔read integrity
expectViolations('citation mismatch (cited 3, read 2)', { ...base, turnKind: 'research', searchExecuted: true, sourceCount: 5, sourcesRead: 2, citationCount: 3, evidenceCount: 5, evidenceBlock: 'evidence' }, 'citation_mismatch')
expectClean('citations match reads', { ...base, turnKind: 'research', searchExecuted: true, sourceCount: 2, sourcesRead: 2, citationCount: 2, evidenceCount: 2, evidenceBlock: 'evidence' })

// [SF-2] zero-source grounding
expectViolations('answered with zero sources read', { ...base, turnKind: 'research', searchExecuted: true, sourceCount: 4, sourcesRead: 0, sourcesFailed: 4, citationCount: 0, evidenceCount: 4, evidenceBlock: 'evidence' }, 'no_source_read')
expectClean('honest no-results outcome is exempt', { ...base, turnKind: 'research', searchExecuted: true, sourceCount: 0, sourcesRead: 0, citationCount: 0, evidenceCount: 0, noResults: true })

// [SF-3] unread sources must be labeled
expectViolations('unread sources unlabeled', { ...base, turnKind: 'research', searchExecuted: true, sourceCount: 3, sourcesRead: 1, citationCount: 1, evidenceCount: 3, evidenceBlock: '(1) Source A — full text' }, 'unlabeled_unread_sources')
expectClean('unread sources carry the HEADLINE label', { ...base, turnKind: 'research', searchExecuted: true, sourceCount: 3, sourcesRead: 1, citationCount: 1, evidenceCount: 3, evidenceBlock: '(2) STATUS: HEADLINE/SNIPPET ONLY — the article was NOT retrieved' })
expectClean('unread sources carry the NOT RETRIEVED label', { ...base, turnKind: 'research', searchExecuted: true, sourceCount: 3, sourcesRead: 1, citationCount: 1, evidenceCount: 3, evidenceBlock: '(3) STATUS: NOT RETRIEVED (timeout)' })

// [SF-5]/[SF-2b] evidence integrity
expectViolations('evidence without sources', { ...base, turnKind: 'research', searchExecuted: true, sourceCount: 0, sourcesRead: 0, citationCount: 0, evidenceCount: 5, evidenceBlock: 'evidence' }, 'evidence_without_source')
expectViolations('search executed but no evidence, not no-results', { ...base, turnKind: 'research', searchExecuted: true, sourceCount: 0, sourcesRead: 0, citationCount: 0, evidenceCount: 0 }, 'search_without_evidence')

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
