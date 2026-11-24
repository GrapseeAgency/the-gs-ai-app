/**
 * FORENSIC AUDIT [22]/[20] — OUTPUT-DIRECTIVE HANDLING ("just say why").
 *
 * The literalist bug: a turn whose FORM commands a minimal output ("just say
 * why", "say the word banana and nothing else") is answered with a full
 * sentence. Routing to the stronger class did NOT fix it (verified live —
 * glm-4.6 still replied with a sentence). This module attacks it three ways
 * as directed by the audit follow-up:
 *
 *   A. Post-processing interception — detectOutputDirective() parses the
 *      directive form; enforceDirective() deterministically reduces a
 *      non-compliant draft to the target. Does not depend on the model.
 *   B. Pre-processing rewrite — buildDirectiveRewrite() rewrites the user
 *      turn into an unambiguous directive before the model sees it.
 *   C. Force retry with a constraint token — buildDirectiveRetryMessages()
 *      regenerates once with a system message that ONLY contains the repeat
 *      instruction. If the retry still fails, the caller ships (A).
 *
 * Detection is strictly scoped: short turns, imperative "say/one word"
 * forms, optional "and nothing else" suffix, end-anchored — so "say why you
 * did that" (an explanation request) never matches.
 */

import { appendSystemInstruction } from './system-instruction'
import { honestConstraintFailure } from './constraint-guard'

export interface OutputDirective {
  /** 'token' — output exactly this one word; 'wordCount' — output exactly N words. */
  kind: 'token' | 'wordCount'
  /** token: the exact demanded word, preserving case.
   *  wordCount: the demanded count as a string (kept for log compatibility). */
  target: string
  /** Set only for kind='wordCount'. */
  count?: number
}

const DIRECTIVE_PATTERNS: RegExp[] = [
  /^(?:ok(?:ay)?[,.]?\s+)?just\s+say\s+(?:the\s+word\s+)?["']?([a-z0-9'’-]{1,24})["']?\s*(?:and\s+nothing\s+else|nothing\s+else|only|that'?s\s+(?:it|all))?[.!?]*$/i,
  /^say\s+(?:the\s+word\s+)?["']?([a-z0-9'’-]{1,24})["']?\s+and\s+nothing\s+else[.!?]*$/i,
  /^say\s+(?:the\s+word\s+)?["']?([a-z0-9'’-]{1,24})["']?\s*[,;:]?\s*(?:and\s+nothing\s+else|nothing\s+else|only)?[.!?]*$/i,
  /^(?:one\s+word|single\s+word)\s*[:\-]\s*["']?([a-z0-9'’-]{1,24})["']?[.!?]*$/i,
  /^(?:reply|answer|respond)\s+with\s+(?:just\s*[:,]?\s*|only\s+|the\s+single\s+word\s+)?["']?([a-z0-9'’-]{1,24})["']?\s*(?:and\s+nothing\s+else|nothing\s+else)?[.!?]*$/i,
]

// Baseline I13 class: WORD-COUNT directives ("reply with exactly three words").
// [22][20] live run: the swift model answered the meta-request ("Three words.")
// because the token patterns above cannot express a count. Scoped: short
// imperative forms with an explicit count, end-anchored.
const NUM_WORDS: Record<string, number> = {
  one: 1, two: 2, three: 3, four: 4, five: 5, six: 6, seven: 7, eight: 8,
  nine: 9, ten: 10, eleven: 11, twelve: 12, thirteen: 13, fourteen: 14,
  fifteen: 15, sixteen: 16, seventeen: 17, eighteen: 18, nineteen: 19, twenty: 20,
}
const NUM = String.raw`(?:one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|\d{1,2})`
const COUNT_PATTERNS: RegExp[] = [
  new RegExp(String.raw`^(?:ok(?:ay)?[,.]?\s+)?(?:reply|answer|respond|say)\s+with\s+exactly\s+(${NUM})\s+words?\s*(?:and\s+nothing\s+else|nothing\s+else|only|no\s+more|that'?s\s+(?:it|all))?[.!?]*$`, 'i'),
  new RegExp(String.raw`^(?:ok(?:ay)?[,.]?\s+)?(?:reply|answer|respond|say)\s+with\s+(${NUM})\s+words?\s+exactly\s*(?:and\s+nothing\s+else|nothing\s+else|only)?[.!?]*$`, 'i'),
  new RegExp(String.raw`^(?:ok(?:ay)?[,.]?\s+)?(?:reply|answer|respond|say)\s+in\s+(?:exactly\s+)?(${NUM})\s+words?\s*(?:and\s+nothing\s+else|nothing\s+else|only)?[.!?]*$`, 'i'),
  new RegExp(String.raw`^(?:ok(?:ay)?[,.]?\s+)?in\s+exactly\s+(${NUM})\s+words?\s*[,;]?\s*(?:and\s+nothing\s+else|nothing\s+else|only)?[.!?]*$`, 'i'),
  new RegExp(String.raw`^exactly\s+(${NUM})\s+words?\s*(?:and\s+nothing\s+else|nothing\s+else|only|no\s+more)?[.!?]*$`, 'i'),
  new RegExp(String.raw`^(?:ok(?:ay)?[,.]?\s+)?(one|two|three|four|five|six|seven|eight|nine|ten|\d{1,2})\s+words?\s+(?:and\s+nothing\s+else|nothing\s+else|only|no\s+more|no\s+less)[.!?]*$`, 'i'),
]

// Baseline I16 class: CONDITIONAL WORD GAME ("if I say red you say blue. I
// say red."). The demanded token is the response word ("blue"). Tolerates
// the repeated cue sentence (". I say red.") and "when/whenever I say".
const GAME_PATTERNS: RegExp[] = [
  /^(?:if|when|whenever)\s+i\s+say\s+["']?([a-z0-9'’-]{1,24})["']?\s*[,;:]*\s*(?:then\s+)?you\s+(?:say|reply|respond|answer)\s+["']?([a-z0-9'’-]{1,24})["']?\s*(?:and\s+nothing\s+else|nothing\s+else|only)?[.!?]+\s*i\s+say\s+["']?([a-z0-9'’-]{1,24})["']?[.!?]*$/i,
]

function toCount(raw: string): number | null {
  if (/^\d{1,2}$/.test(raw)) {
    const n = Number(raw)
    return n >= 1 && n <= 20 ? n : null
  }
  return NUM_WORDS[raw.toLowerCase()] ?? null
}

/** Deterministic directive detector (Approach A gate). */
export function detectOutputDirective(userText: string): OutputDirective | null {
  const text = userText.trim()
  if (text.length === 0 || text.length > 80) return null // directives are short
  for (const p of GAME_PATTERNS) {
    const m = p.exec(text)
    if (m?.[2] && m[1].toLowerCase() === m[3]?.toLowerCase()) {
      return {kind:'token',target:m[2]}
    }
  }
  for (const p of DIRECTIVE_PATTERNS) {
    const m = p.exec(text)
    if (m?.[1]) {
      const target = m[1].replace(/[.!?]+$/, '')
      if (target.length > 0) return { kind: 'token', target }
    }
  }
  for (const p of COUNT_PATTERNS) {
    const m = p.exec(text)
    if (m?.[1]) {
      const count = toCount(m[1])
      if (count !== null && count >= 1) return { kind: 'wordCount', target: String(count), count }
    }
  }
  return null
}

/** Literal repetition can be corrected without inventing content. A bare
 * word-count request may refer to earlier context, so never pad or truncate
 * its answer to fake compliance. */
export function enforceDirective(draft: string, d: OutputDirective): string | null {
  const raw = draft.trim()
  if (d.kind === 'wordCount') {
    const n = d.count ?? Number(d.target)
    if (raw.split(/\s+/).filter(Boolean).length === n) return null
    return honestConstraintFailure(raw,{exactWords:n})
  }
  return raw === d.target ? null : d.target
}

/** Approach B — unambiguous pre-processing rewrite of the user turn. */
export function buildDirectiveRewrite(d: OutputDirective): string {
  if (d.kind === 'wordCount') {
    return `Output exactly ${d.count} words and nothing else.`
  }
  return `Output exactly one word and nothing else: ${d.target}`
}

/** Preserve the original request and all evidence, including prior context. */
export function buildDirectiveMessages<T extends {role:string}>(messages: T[], d: OutputDirective): T[] {
  return appendSystemInstruction(messages,buildDirectiveRewrite(d))
}

/** Retry without replacing any of the original system contract. */
export function buildDirectiveRetryMessages<T extends {role:string}>(messages:T[],d:OutputDirective):T[] {
  return appendSystemInstruction(messages, d.kind === 'wordCount'
    ? `Output exactly ${d.count} words and nothing else. Do not count out loud.`
    : `Repeat the following exactly and output nothing else: ${d.target}`)
}
