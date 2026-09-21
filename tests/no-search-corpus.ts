/**
 * FORENSIC AUDIT [11] — NO-SEARCH REGRESSION CORPUS.
 *
 * Thirty prompts that MUST route to searchPlanned=false (no capability
 * forces a web search): arithmetic, divisibility, capitals, common sense,
 * logic puzzles, small talk, definitions, meta-questions.
 *
 * Run: bun tests/no-search-corpus.ts — exits 1 on any violation.
 * (The mirror check — search-capable prompts MUST search — lives in the
 * black-box matrix [31].)
 */

import { decideCapability } from '../src/lib/capability'
import { detectHistoricalReligious } from '../src/lib/search/planner'

const NO_SEARCH_PROMPTS: string[] = [
  // arithmetic / numbers
  'what is 2+2?',
  'what is 15% of 240?',
  'is 91 divisible by 7?',
  'how many days are in a leap year?',
  'if I have 3 apples and buy 5 more, how many do I have?',
  // capitals / stable trivia
  "what is the capital of France?",
  'what language do they speak in Brazil?',
  'how many continents are there?',
  'who wrote Romeo and Juliet?',
  'what is the boiling point of water in celsius?',
  // common sense
  'should I open an umbrella?',
  'is it a good idea to touch a hot stove?',
  'do fish need water to survive?',
  'will the sun rise tomorrow?',
  'if I drop a glass on the floor will it break?',
  // logic puzzles
  'if all roses are flowers and some flowers fade quickly, can I conclude all roses fade quickly?',
  'what comes next in the sequence 2, 4, 8, 16?',
  'a bat and a ball cost 1.10 together; the bat costs 1 more than the ball — how much is the ball?',
  'which weighs more, a kilogram of feathers or a kilogram of steel?',
  'if yesterday was Monday, what day is tomorrow?',
  // small talk / chat
  'hi',
  "hey, what's up?",
  'tell me a joke',
  'how was your day?',
  'good morning!',
  // meta / instructions
  'write a two-line rhyme about the sea',
  'answer this question 5 times: why is the sky blue?',
  'explain recursion in one sentence',
  'give me one word to describe happiness',
  'count the words in this sentence: the cat sat on the mat',
]

let failures = 0
for (const prompt of NO_SEARCH_PROMPTS) {
  const decision = decideCapability({
    content: prompt,
    hasImages: false,
    hasDocuments: false,
    historicalReligious: detectHistoricalReligious(prompt),
  })
  const ok = decision.searchPlanned === false
  if (!ok) {
    failures++
    console.log(
      `FAIL "${prompt}" → capability=${decision.capability} trigger=${decision.trigger} searchPlanned=${decision.searchPlanned} (${decision.reason})`
    )
  } else {
    console.log(`ok   "${prompt}" → ${decision.capability}`)
  }
}

if (failures > 0) {
  console.log(`\nCORPUS RESULT: ${failures}/${NO_SEARCH_PROMPTS.length} FAILED — search fired on a no-search prompt`)
  process.exit(1)
}
console.log(`\nCORPUS RESULT: all ${NO_SEARCH_PROMPTS.length} no-search prompts routed to searchPlanned=false`)
