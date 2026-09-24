/**
 * FLASH MODE (Phase 7) — THE SYSTEM PROMPT IS NOT TOUCHED.
 *
 * The mode is a MODEL-LAYER parameter: it changes one thing, the `thinking`
 * field sent to the provider. It must never leak into the prompt layer —
 * no "when the user selects Thinking, do X", no "in flash mode, answer
 * shorter". That exact pattern (prompt-patching behaviour) is what the
 * whole 8.3 architecture exists to prevent.
 *
 * This test pins the sha256 of SYSTEM_PROMPT to the hash committed at the
 * time the flash-mode task started (Phase 7 baseline, v0.68.2-era prompt —
 * the 8.3 minimal product contract). ANY change to the prompt — one comma —
 * breaks this test and must be a deliberate, separately-audited act, never
 * a side effect of a feature commit.
 *
 * Run: bun test tests/system-prompt-unchanged.test.ts
 */

import { describe, expect, test } from 'bun:test'
import { SYSTEM_PROMPT } from '../src/lib/ai'

/** sha256(SYSTEM_PROMPT) — 590 chars, pinned 2026-09-24 (flash-mode Phase 7). */
const SYSTEM_PROMPT_SHA256 = '7451490f30ee274cc241d718fcdd0bc8a6275d7d72dff4ebe4e6ffc5c329b028'

/** sha256 of the Phase-7-era prompt under the ALSO-FORBIDDEN assistant-instructions wrapper. */
const FORBIDDEN_SNIPPETS = [
  'thinking mode',
  'flash mode',
  'when the user selects',
  'reasoning effort',
  'deep reasoning',
  'answer shorter',
]

function sha256(text: string): string {
  const hasher = new Bun.CryptoHasher('sha256')
  hasher.update(text)
  return hasher.digest('hex')
}

describe('system prompt is untouched by the mode feature', () => {
  test('sha256(SYSTEM_PROMPT) matches the pinned Phase 7 baseline', () => {
    expect(sha256(SYSTEM_PROMPT)).toBe(SYSTEM_PROMPT_SHA256)
  })

  test('the prompt carries no mode-layer instructions', () => {
    const lowered = SYSTEM_PROMPT.toLowerCase()
    for (const snippet of FORBIDDEN_SNIPPETS) {
      expect(lowered).not.toContain(snippet)
    }
  })

  test('the prompt stays the 8.3 minimal product contract (<= 10 lines, bounded length)', () => {
    // [29] — total contract stays ≤10 lines; the pinned prompt is ONE line.
    expect(SYSTEM_PROMPT.split('\n').filter((l) => l.trim().length > 0).length).toBeLessThanOrEqual(10)
    // Length bound: a prompt that quietly grows beyond the pinned +10% is a
    // contract change even before its hash is re-pinned deliberately.
    expect(SYSTEM_PROMPT.length).toBeLessThanOrEqual(Math.ceil(590 * 1.1))
  })
})
