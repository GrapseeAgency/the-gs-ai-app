/** Quality-first free chain eligibility. This is a policy preference,
 * not a claim that a recently serving concrete model passed an eval.
 * Preserve the free-only spend guard and keep contextual work off the
 * auto-router-first fast path. */
export function needsQualityChain(input: {
  route: string
  requestedMode: string
  effectiveMode: string
  registerClass: boolean
  hasHistory: boolean
  hasConstraints: boolean
  content: string
}): boolean {
  return input.route !== 'TEXT_SIMPLE' || input.requestedMode === 'thinking' ||
    input.effectiveMode === 'thinking' || input.registerClass || input.hasHistory ||
    input.hasConstraints || input.content.length > 240
}
