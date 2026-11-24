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

/** Auto can deepen a short, context-dependent request. Explicit user
 * flash/thinking choices and deterministic time/research overrides remain
 * owned by the existing thinking resolver. Greetings are not escalated. */
export function contextualReasoningRoute(input:{route:string;requestedMode:string;hasHistory:boolean;hasConstraints:boolean;content:string}):string {
  if(input.requestedMode!=='auto'||input.route!=='TEXT_SIMPLE'||!input.hasHistory)return input.route
  const refersBack=/\b(?:continue|go on|go for it|carry on|as before|same format|same way|previous|earlier|above|that one|those|do (?:it|them|that)|correct (?:it|that)|revise (?:it|that))\b/i.test(input.content) ||
    /আগের|চালিয়ে যাও|চালিয়ে যান|একইভাবে|সংশোধন/.test(input.content)
  return refersBack||input.hasConstraints?'TEXT_COMPLEX':input.route
}
