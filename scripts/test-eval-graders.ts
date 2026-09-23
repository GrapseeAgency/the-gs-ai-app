/**
 * EVAL INFRASTRUCTURE — GRADER CONTRACT TESTS (Phase 4, hermetic).
 *
 * The graders are the JUDGES. A judge with a bug acquits every future bug —
 * so the grader logic itself gets fixed fixtures, run in CI with zero network.
 * Each fixture pins a verdict the grader MUST return, including the
 * false-pass traps found in real incidents:
 *   - a throttled turn (errorType=provider_429, no answer) must NOT satisfy
 *     a searchExecuted=false assertion via ERROR laundering
 *   - cited ordinals above sourceCount must fail citationsWithinSources
 *   - read-rate is undefined (FAIL) when sourceCount=0, not a division crash
 *
 * Usage: bun scripts/test-eval-graders.ts   (exit 1 on any violation)
 */

import { evalAssertion, gradeAll, lineCount, wordCount, type TraceRow } from '../src/lib/eval-grader'

interface Fixture {
  name: string
  assertion: { type: string; value?: unknown; values?: unknown[]; flags?: string }
  trace: TraceRow | null
  answer: string
  expectOk: boolean
  expectDetailIncludes?: string
}

function row(over: Partial<TraceRow>): TraceRow {
  return {
    requestId: 'fixture',
    capability: 'CHAT',
    trigger: 'none',
    searchExecuted: 0,
    sourceCount: 0,
    sourcesRead: 0,
    sourcesFailed: 0,
    domains: '[]',
    modelRoute: 'zai/gs-swift',
    finalStatus: 'done',
    cited: '[]',
    latencyMs: 500,
    errorType: null,
    ...over,
  }
}

const FIXTURES: Fixture[] = [
  // --- trace.searchExecuted -----------------------------------------------------
  {
    name: 'searchExecuted=false passes on a clean no-search turn',
    assertion: { type: 'trace.searchExecuted', value: false },
    trace: row({ searchExecuted: 0 }),
    answer: 'Hello! How can I help you today?',
    expectOk: true,
  },
  {
    name: 'searchExecuted=false FAILS when search ran (contamination class)',
    assertion: { type: 'trace.searchExecuted', value: false },
    trace: row({ searchExecuted: 1, sourceCount: 4 }),
    answer: 'Here is what I found [1].',
    expectOk: false,
  },
  {
    name: 'searchExecuted=true passes on an explicit search turn',
    assertion: { type: 'trace.searchExecuted', value: true },
    trace: row({ searchExecuted: 1, sourceCount: 4, capability: 'WEB', trigger: 'explicit' }),
    answer: 'Grounded answer [1].',
    expectOk: true,
  },
  {
    name: 'searchExecuted=false on MISSING trace must FAIL (never silent-pass)',
    assertion: { type: 'trace.searchExecuted', value: false },
    trace: null,
    answer: 'Hello!',
    expectOk: false,
    expectDetailIncludes: 'no trace row',
  },

  // --- trace.capability -----------------------------------------------------------
  {
    name: 'capability=TIME passes on the clock turn',
    assertion: { type: 'trace.capability', value: 'TIME' },
    trace: row({ capability: 'TIME' }),
    answer: 'It is 14:32.',
    expectOk: true,
  },
  {
    name: 'capability=TIME fails when routed as CHAT',
    assertion: { type: 'trace.capability', value: 'TIME' },
    trace: row({ capability: 'CHAT' }),
    answer: 'It is 14:32.',
    expectOk: false,
  },

  // --- trace.sourceCount (context isolation) ----------------------------------------
  {
    name: 'sourceCount=0 passes on the follow-up small-talk turn (isolation)',
    assertion: { type: 'trace.sourceCount', value: 0 },
    trace: row({ searchExecuted: 0, sourceCount: 0 }),
    answer: 'Hi again!',
    expectOk: true,
  },
  {
    name: 'sourceCount=0 FAILS on cross-context contamination (audit [9])',
    assertion: { type: 'trace.sourceCount', value: 0 },
    trace: row({ searchExecuted: 1, sourceCount: 8, domains: '["en.wikipedia.org"]' }),
    answer: 'About that history question...',
    expectOk: false,
  },

  // --- trace.finalStatus / honesty ---------------------------------------------------
  {
    name: 'finalStatus=done passes on an honest no-results turn',
    assertion: { type: 'trace.finalStatus', value: 'done' },
    trace: row({ searchExecuted: 1, sourceCount: 0 }),
    answer: 'The search returned no usable results.',
    expectOk: true,
  },
  {
    name: 'finalStatus=error fails the honesty assertion',
    assertion: { type: 'trace.finalStatus', value: 'done' },
    trace: row({ finalStatus: 'error', errorType: 'provider_429' }),
    answer: '',
    expectOk: false,
  },

  // --- trace.domainsMin / readRate / sourcesReadMin ------------------------------------
  {
    name: 'domainsMin=3 passes with 3+ distinct domains',
    assertion: { type: 'trace.domainsMin', value: 3 },
    trace: row({ searchExecuted: 1, sourceCount: 6, domains: '["a.com","b.org","c.net","d.io"]' }),
    answer: 'Answer [1].',
    expectOk: true,
  },
  {
    name: 'domainsMin=3 fails on single-domain collapse',
    assertion: { type: 'trace.domainsMin', value: 3 },
    trace: row({ searchExecuted: 1, sourceCount: 5, domains: '["en.wikipedia.org","en.wikipedia.org"]' }),
    answer: 'Answer [1].',
    expectOk: false,
  },
  {
    name: 'readRate=0.7 passes at exactly 7/10',
    assertion: { type: 'trace.readRate', value: 0.7 },
    trace: row({ searchExecuted: 1, sourceCount: 10, sourcesRead: 7, sourcesFailed: 3 }),
    answer: 'Answer [1].',
    expectOk: true,
  },
  {
    name: 'readRate fails at 6/10',
    assertion: { type: 'trace.readRate', value: 0.7 },
    trace: row({ searchExecuted: 1, sourceCount: 10, sourcesRead: 6, sourcesFailed: 4 }),
    answer: 'Answer [1].',
    expectOk: false,
  },
  {
    name: 'readRate with sourceCount=0 FAILS cleanly (no crash, no pass)',
    assertion: { type: 'trace.readRate', value: 0.7 },
    trace: row({ searchExecuted: 1, sourceCount: 0 }),
    answer: 'Answer.',
    expectOk: false,
    expectDetailIncludes: 'undefined',
  },
  {
    name: 'sourcesReadMin=1 fails on all-failed reads (audit [16])',
    assertion: { type: 'trace.sourcesReadMin', value: 1 },
    trace: row({ searchExecuted: 1, sourceCount: 4, sourcesRead: 0, sourcesFailed: 4 }),
    answer: 'Answer.',
    expectOk: false,
  },

  // --- citationsWithinSources ------------------------------------------------------------
  {
    name: 'cited within sourceCount passes',
    assertion: { type: 'citationsWithinSources' },
    trace: row({ searchExecuted: 1, sourceCount: 6, cited: '[1,2,4]' }),
    answer: 'Answer [1] [2] [4].',
    expectOk: true,
  },
  {
    name: 'cited ordinal ABOVE sourceCount fails (fabricated citation class)',
    assertion: { type: 'citationsWithinSources' },
    trace: row({ searchExecuted: 1, sourceCount: 3, cited: '[1,2,5]' }),
    answer: 'Answer [5].',
    expectOk: false,
    expectDetailIncludes: 'exceed',
  },

  // --- response graders ------------------------------------------------------------------
  {
    name: 'matchesRegex anchored one-word passes on "banana"',
    assertion: { type: 'response.matchesRegex', value: '^(why|banana)$', flags: 'i' },
    trace: null,
    answer: 'banana',
    expectOk: true,
  },
  {
    name: 'matchesRegex one-word FAILS on chatty answer (audit [22] class)',
    assertion: { type: 'response.matchesRegex', value: '^(why|banana)$', flags: 'i' },
    trace: null,
    answer: 'Why not? Here is an explanation...',
    expectOk: false,
  },
  {
    name: 'wordCount=3 passes on exactly three words',
    assertion: { type: 'response.wordCount', value: 3 },
    trace: null,
    answer: 'I am a fish'.split(' ').slice(0, 3).join(' '),
    expectOk: true,
  },
  {
    name: 'wordCount=3 fails on four words',
    assertion: { type: 'response.wordCount', value: 3 },
    trace: null,
    answer: 'one two three four',
    expectOk: false,
  },
  {
    name: 'lineCount=3 passes on a 5-7-5 haiku shape',
    assertion: { type: 'response.lineCount', value: 3 },
    trace: null,
    answer: 'An old silent pond\nA frog jumps into the water\nSound of water returns',
    expectOk: true,
  },
  {
    name: 'contains passes case-insensitively',
    assertion: { type: 'response.contains', value: 'I am a fish' },
    trace: null,
    answer: 'Sure: I AM A FISH.',
    expectOk: true,
  },
  {
    name: 'notContains catches the dishonest read claim (audit [403] class)',
    assertion: { type: 'response.notContains', value: "I read the page" },
    trace: row({ searchExecuted: 1, sourceCount: 2, sourcesRead: 0, sourcesFailed: 2 }),
    answer: 'I read the page and it says...',
    expectOk: false,
  },
  {
    name: 'notContains passes when the claim is absent',
    assertion: { type: 'response.notContains', value: "I read the page" },
    trace: row({ searchExecuted: 1, sourceCount: 2, sourcesRead: 0, sourcesFailed: 2 }),
    answer: 'The sources could not be retrieved, so here is what I know.',
    expectOk: true,
  },
  {
    name: 'containsAny passes on honest no-results phrasing',
    assertion: { type: 'response.containsAny', values: ['no usable results', 'no results', "couldn't find"] },
    trace: row({ searchExecuted: 1, sourceCount: 0 }),
    answer: 'The search came back with no usable results.',
    expectOk: true,
  },
  {
    name: 'containsAny fails when nothing matches',
    assertion: { type: 'response.containsAny', values: ['no usable results', 'no results'] },
    trace: row({ searchExecuted: 1, sourceCount: 0 }),
    answer: 'Here is a made-up answer anyway.',
    expectOk: false,
  },

  // --- anyOf / unknown ----------------------------------------------------------------------
  {
    name: 'anyOf passes when the nested regex branch holds',
    assertion: {
      type: 'anyOf',
      values: [
        { type: 'response.matchesRegex', value: '^\\d+$', flags: 'i' },
        { type: 'response.contains', value: 'temporarily unavailable' },
      ],
    },
    trace: null,
    answer: '63',
    expectOk: true,
  },
  {
    name: 'anyOf fails when every branch fails (details ANDed)',
    assertion: {
      type: 'anyOf',
      values: [
        { type: 'response.matchesRegex', value: '^\\d+$', flags: 'i' },
        { type: 'response.contains', value: 'temporarily unavailable' },
      ],
    },
    trace: null,
    answer: 'The answer is sixty-three.',
    expectOk: false,
    expectDetailIncludes: 'anyOf failed',
  },
  {
    name: 'unknown assertion type fails LOUD (no vacuous pass)',
    assertion: { type: 'trace.futureField', value: 1 },
    trace: row({}),
    answer: 'x',
    expectOk: false,
    expectDetailIncludes: 'unknown assertion type',
  },
  {
    name: 'throttled turn: empty answer fails a regex assertion (no ERROR laundering)',
    assertion: { type: 'response.matchesRegex', value: '^(why)$', flags: 'i' },
    trace: row({ finalStatus: 'error', errorType: 'provider_429' }),
    answer: '',
    expectOk: false,
  },
]

// --- helpers under test ----------------------------------------------------------

const HELPER_CHECKS: { name: string; got: unknown; want: unknown }[] = [
  { name: 'wordCount trims and collapses whitespace', got: wordCount('  a   b\tc\n d '), want: 4 },
  { name: 'wordCount of empty string is 0', got: wordCount('   '), want: 0 },
  { name: 'lineCount ignores blank lines', got: lineCount('a\n\nb\n'), want: 2 },
  { name: 'lineCount of blank text is 0', got: lineCount('\n \n'), want: 0 },
  { name: 'gradeAll returns only failures', got: gradeAll([{ type: 'response.wordCount', value: 1 }, { type: 'response.wordCount', value: 99 }], null, 'one').length, want: 1 },
]

// --- run ---------------------------------------------------------------------------

let failed = 0
for (const f of FIXTURES) {
  const v = evalAssertion(f.assertion as never, f.trace, f.answer)
  const ok = v.ok === f.expectOk && (!f.expectDetailIncludes || v.detail.includes(f.expectDetailIncludes))
  if (!ok) {
    failed++
    console.error(`GRADER TEST FAIL: ${f.name}\n  got ok=${v.ok} detail=${v.detail}\n  want ok=${f.expectOk}${f.expectDetailIncludes ? ` detail~${f.expectDetailIncludes}` : ''}`)
  } else {
    console.log(`ok   ${f.name}`)
  }
}
for (const h of HELPER_CHECKS) {
  const ok = JSON.stringify(h.got) === JSON.stringify(h.want)
  if (!ok) {
    failed++
    console.error(`GRADER TEST FAIL: ${h.name} — got ${JSON.stringify(h.got)} want ${JSON.stringify(h.want)}`)
  } else {
    console.log(`ok   ${h.name}`)
  }
}

console.log(`\ngrader contract: ${FIXTURES.length + HELPER_CHECKS.length - failed}/${FIXTURES.length + HELPER_CHECKS.length} fixtures hold`)
if (failed > 0) process.exit(1)
