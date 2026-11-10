import { presentationPreference } from './readable'

type Push = (event: string, data: string) => void

/** Format opt-outs must hold during streaming, not just in the saved bubble.
 * Keep reasoning, real tool progress, errors and lifecycle events streaming.
 * Only unvalidated answer deltas wait for the compiler's final projection.
 */
export function presentationStream(prompt: string, transport: Push | null): {
  push: Push | null
  release: (chunks: Iterable<string>) => void
} {
  const defer = presentationPreference(prompt) !== 'automatic'
  let released = false
  return {
    push: !transport ? null : !defer ? transport : (event, data) => {
      if (event !== 'delta') transport(event, data)
    },
    release(chunks) {
      if (!defer || !transport || released) return
      released = true
      for (const chunk of chunks) transport('delta', chunk)
    },
  }
}
