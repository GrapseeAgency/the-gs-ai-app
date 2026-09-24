/**
 * STAGED ROLLOUT — shadow → canary → percentage → full (2026 rollout playbook).
 *
 * The deploy model was "commit and push to production" — no staged rollout.
 * This module adds the ladder WITHOUT changing today's behavior: the stage
 * comes from GS_ROLLOUT_STAGE and UNSET means `full` (the registry route
 * serves 100% of traffic, exactly as shipped).
 *
 *   shadow      the candidate (registry/shortfall) route is computed and
 *               SCORED on 100% of traffic but NEVER served — the control
 *               (standing tier) route answers; the logs carry both plans so
 *               the eval layers can compare candidate vs control offline.
 *   canary      the candidate serves 5% of traffic (deterministic traffic-key
 *               hash), control serves the rest.
 *   percentage  the candidate serves GS_ROLLOUT_PERCENT % (default 25; move
 *               to 50 by env, never by code change).
 *   full        the candidate serves 100% — current production behavior.
 *
 * ROLLBACK: GS_ROLLOUT_ROLLBACK=1 forces the control route on 100% of
 * traffic regardless of stage (the operator switch the quality gate pulls).
 * The stage gate is measured by the eval suites: if quality drops below
 * baseline (the CI gate's Welch rule or the monitor), set the rollback var —
 * the router honors it on the next turn, no deploy needed.
 *
 * Additionally, at every non-full stage a PER-QUERY measured gate applies:
 * when both candidate and control hold numeric registry measurements for the
 * required capability and the candidate's is LOWER than the control's, the
 * query rolls back to control (a measured regression never serves, even
 * inside a canary bucket).
 *
 * CONTRACT: stage, buckets, candidate/control labels and scores are INTERNAL
 * ONLY — logged, never sent to a client. The user sees one continuous GS AI.
 */

import type { CapabilityTag } from './model-capabilities'

export type RolloutStage = 'shadow' | 'canary' | 'percentage' | 'full'

export interface RolloutState {
  stage: RolloutStage
  /** Traffic share the candidate serves at this stage (0–100). */
  candidatePercent: number
  /** GS_ROLLOUT_ROLLBACK=1 — control serves 100% regardless of stage. */
  rollback: boolean
}

/** The standing tier control plan (pre-registry tier mapping). */
export interface TierControlPlan {
  route: 'TEXT_SIMPLE' | 'TEXT_COMPLEX' | 'CODING' | 'VISION' | 'DOCUMENT' | 'WEB' | 'DEEP_RESEARCH'
  internalModelId: string
}

const DEFAULT_PERCENT: Record<RolloutStage, number> = {
  shadow: 0,
  canary: 5,
  percentage: 25,
  full: 100,
}

export function resolveRolloutState(): RolloutState {
  const raw = (process.env.GS_ROLLOUT_STAGE ?? '').trim().toLowerCase()
  const stage: RolloutStage =
    raw === 'shadow' || raw === 'canary' || raw === 'percentage' || raw === 'full' ? raw : 'full'
  const override = Number.parseInt(process.env.GS_ROLLOUT_PERCENT ?? '', 10)
  const candidatePercent =
    stage === 'percentage' && Number.isFinite(override) && override >= 0 && override <= 100
      ? override
      : DEFAULT_PERCENT[stage]
  const rollback = process.env.GS_ROLLOUT_ROLLBACK?.trim() === '1'
  return { stage, candidatePercent, rollback }
}

/**
 * Deterministic traffic bucket: sha256(trafficKey) → 0..99. Same key always
 * lands in the same bucket (a conversation never flip-flops between routes).
 */
export function trafficBucket(trafficKey: string): number {
  // FNV-1a 32-bit — deterministic, no deps, uniformly distributed enough for
  // bucketing; sha256 would be stronger but this is a 0-99 bucket not a secret.
  let h = 0x811c9dc5
  for (let i = 0; i < trafficKey.length; i++) {
    h ^= trafficKey.charCodeAt(i)
    h = Math.imul(h, 0x01000193)
  }
  return (h >>> 0) % 100
}

/** Does THIS traffic key get the candidate at this stage? */
export function shouldServeCandidate(state: RolloutState, trafficKey: string | null): boolean {
  if (state.stage === 'full') return true
  if (state.rollback) return false
  if (state.stage === 'shadow') return false
  if (!trafficKey) return false // conservative: unkeyed traffic stays on control
  return trafficBucket(trafficKey) < state.candidatePercent
}

/** The standing tier CONTROL plan for a capability (pre-registry mapping). */
export function tierControlFor(capability: CapabilityTag): TierControlPlan {
  switch (capability) {
    case 'vision':
      return { route: 'VISION', internalModelId: 'gs-vision' }
    case 'document':
      return { route: 'DOCUMENT', internalModelId: 'gs-balanced' }
    case 'research':
      return { route: 'DEEP_RESEARCH', internalModelId: 'gs-deep' }
    case 'search':
      return { route: 'WEB', internalModelId: 'gs-balanced' }
    case 'code':
      return { route: 'CODING', internalModelId: 'gs-coder' }
    case 'register':
      return { route: 'TEXT_COMPLEX', internalModelId: 'gs-balanced' } // the tier-era mapping — kept as CONTROL ONLY
    case 'reasoning':
      return { route: 'TEXT_COMPLEX', internalModelId: 'gs-balanced' }
    default:
      return { route: 'TEXT_SIMPLE', internalModelId: 'gs-swift' }
  }
}
