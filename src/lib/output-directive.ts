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

export interface OutputDirective {
  /** The exact token the turn demands (lowercased, punctuation-stripped). */
  target: string
}

const DIRECTIVE_PATTERNS: RegExp[] = [
  /^(?:ok(?:ay)?[,.]?\s+)?just\s+say\s+(?:the\s+word\s+)?["']?([a-z0-9'’-]{1,24})["']?\s*(?:and\s+nothing\s+else|nothing\s+else|only|that'?s\s+(?:it|all))?[.!?]*$/i,
  /^say\s+(?:the\s+word\s+)?["']?([a-z0-9'’-]{1,24})["']?\s+and\s+nothing\s+else[.!?]*$/i,
  /^say\s+(?:the\s+word\s+)?["']?([a-z0-9'’-]{1,24})["']?\s*[,;:]?\s*(?:and\s+nothing\s+else|nothing\s+else|only)?[.!?]*$/i,
  /^(?:one\s+word|single\s+word)\s*[:\-]\s*["']?([a-z0-9'’-]{1,24})["']?[.!?]*$/i,
  /^(?:reply|answer|respond)\s+with\s+(?:just\s*[:,]?\s*|only\s+|the\s+single\s+word\s+)?["']?([a-z0-9'’-]{1,24})["']?\s*(?:and\s+nothing\s+else|nothing\s+else)?[.!?]*$/i,
]

/** Deterministic directive detector (Approach A gate). */
export function detectOutputDirective(userText: string): OutputDirective | null {
  const text = userText.trim()
  if (text.length === 0 || text.length > 80) return null // directives are short
  for (const p of DIRECTIVE_PATTERNS) {
    const m = p.exec(text)
    if (m?.[1]) {
      const target = m[1].toLowerCase().replace(/[.!?]+$/, '')
      if (target.length > 0) return { target }
    }
  }
  return null
}

/**
 * Approach A — deterministic enforcement. Returns null when the draft IS the
 * target (compliant — ship as-is); otherwise returns the target itself, which
 * the caller ships in place of the non-compliant draft.
 */
export function enforceDirective(
  draft: string,
  d: OutputDirective
): string | null {
  const normalized = draft
    .trim()
    .toLowerCase()
    .replace(/^["']+|["']+$/g, '')
    .replace(/[.!?]+$/, '')
  return normalized === d.target ? null : d.target
}

/** Approach B — unambiguous pre-processing rewrite of the user turn. */
export function buildDirectiveRewrite(d: OutputDirective): string {
  return `Output exactly one word and nothing else: ${d.target}`
}

/**
 * Approach B message builder — the FULL conversation with ONLY the final user
 * turn replaced by the unambiguous directive rewrite. (The first cut of the
 * route wiring truncated the array to the rewritten turn alone — a system-less
 * one-message request — which made B ramblier and C a system-only array that
 * the provider rejects with 400; this helper is the single correct builder.)
 */
export function buildDirectiveMessages<T extends { role: string }>(
  messages: T[],
  d: OutputDirective
): T[] {
  let lastUserIdx = -1
  for (let i = messages.length - 1; i >= 0; i--) {
    if (messages[i].role === 'user') {
      lastUserIdx = i
      break
    }
  }
  if (lastUserIdx === -1) {
    return [...messages, { role: 'user', content: buildDirectiveRewrite(d) } as unknown as T]
  }
  return messages.map((m, i) =>
    i === lastUserIdx ? ({ ...m, content: buildDirectiveRewrite(d) } as unknown as T) : m
  )
}

/**
 * Approach C — force-retry messages: the SAME conversation with the leading
 * system message REPLACED by a constraint-only system message, so nothing
 * dilutes the repeat instruction.
 */
export function buildDirectiveRetryMessages<T extends { role: string }>(
  messages: T[],
  d: OutputDirective
): T[] {
  const constraint = {
    role: 'system',
    content: `Repeat the following exactly and output nothing else: ${d.target}`,
  } as unknown as T
  return [constraint, ...messages.slice(1)]
}
