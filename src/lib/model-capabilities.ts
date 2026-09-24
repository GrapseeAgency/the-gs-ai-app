/**
 * CAPABILITY REGISTRY — the measured-model routing source of truth.
 *
 * HISTORY (2026-09-24): the router routed by COMPLEXITY TIER ("stronger class"
 * = gs-balanced). The multi-trial register measurement (5 trials × 10 cases,
 * judge glm-4.6 temp 0, tests/register-multitrial-v1.json) proved the tier
 * assumption empirically false: the register-class detector correctly flags
 * register turns and then routes them to the model class that FAILS them
 * (zai/gs-balanced register 5/35 = 0.143) while the "efficient" tier model
 * passes every register case it is given (zai/gs-swift 15/15 = 1.000). That
 * is a routing inversion. This registry replaces tier labels with MEASURED
 * capability as the routing key: a turn requiring capability C goes to the
 * model with the best MEASURED score for C, and a model that measures < 0.5
 * on a capability is barred from serving it.
 *
 * RULES THIS FILE OBEYS:
 *  - Every score cites its eval artifact. A capability with no measured data
 *    is 'UNMEASURED' — never guessed, never inferred from tier labels.
 *  - Scores are literals derived by scripts/compute-capability-registry.ts
 *    from the committed artifacts (re-run it after new eval runs and refresh).
 *  - `latency` is the production turn p50 (ms) across the model's recorded
 *    eval traces — mixed turn types; directional, not an SLA.
 *  - canServe = capability tags this model may serve (measured ≥ threshold,
 *    or its standing tier role for unmeasured tags).
 *  - neverServe = tags this model is BARRED from serving (measured < 0.5).
 *    The bar is hard: the router must not route a register turn to a model
 *    with measured register < 0.5, no matter what a tier label says.
 *
 * Selection (selectModelForCapability): among profiles whose canServe has the
 * tag, that do not neverServe it, and that hold a NUMERIC measured score for
 * it, pick the highest score. No numeric score anywhere → null → the router
 * reports BLOCKED for that capability rather than serving an unmeasured model
 * under a capability pretense.
 */

export type CapabilityTag =
  | 'register' // jokes, banter, sarcasm, emotional/social nuance, AI-tease
  | 'reasoning' // analytical, multi-step, context-isolation, epistemic honesty
  | 'instruction' // literal instruction following, routing-shaped asks
  | 'search' // grounded search synthesis with sources
  | 'chat' // ordinary conversation (tier-default territory)
  | 'code' // coding-shaped asks
  | 'vision' // image understanding
  | 'document' // document synthesis
  | 'research' // deep-research pipeline synthesis

export type Measured = number | 'UNMEASURED'

export interface ModelProfile {
  /** Internal catalogue id — NEVER exposed to a client. */
  modelId: string
  provider: 'zai' | 'openrouter'
  /** Concrete provider model (zai) or chain head (openrouter) for reference. */
  providerModel: string
  measured: {
    register: Measured
    reasoning: Measured
    instruction: Measured
    search: Measured
    latency: Measured // production turn p50 ms
  }
  canServe: CapabilityTag[]
  neverServe: CapabilityTag[]
  /** artifact citation per measured cell — a score without an artifact is forbidden */
  evidence: Partial<Record<keyof ModelProfile['measured'], string>>
  measuredAt: string
}

/** Below this measured rate a model is barred from the capability. */
export const CAPABILITY_THRESHOLD = 0.5

export const MODEL_REGISTRY: ModelProfile[] = [
  {
    modelId: 'gs-swift',
    provider: 'zai',
    providerModel: 'glm-4.5-flash',
    measured: {
      register: 1.0, // 15/15 multi-trial (J54/J57/J60 × 5) — refreshed by register-swift-direct/production re-runs
      reasoning: 'UNMEASURED',
      instruction: 'UNMEASURED',
      search: 'UNMEASURED',
      latency: 749, // p50 production turn, n=15 (register multitrial traces)
    },
    canServe: ['register', 'chat'],
    neverServe: [],
    evidence: {
      register: 'tests/register-multitrial-v1.json (z-ai column, 5 trials × J54/J57/J60, judge glm-4.6 temp 0)',
      latency: 'tests/register-multitrial-v1.json trial units (production traces, n=15)',
    },
    measuredAt: '2026-09-23',
  },
  {
    modelId: 'gs-balanced',
    provider: 'zai',
    providerModel: 'glm-4.6',
    measured: {
      register: 0.143, // 5/35 multi-trial — SYSTEMATIC fail (J51 5/5 only; six cases 0/5 each)
      reasoning: 1.0, // 2/2 deterministic (CONTEXT_ISOLATION subset)
      instruction: 1.0, // 3/3 deterministic (INSTRUCTION+ROUTING subset — literalist route-around cases)
      search: 'UNMEASURED', // SOURCE_QUALITY never served by zai/gs-balanced (WEB tier rides the free chain)
      latency: 776, // p50 production turn, n=42
    },
    canServe: ['chat', 'reasoning', 'instruction'],
    neverServe: ['register'],
    evidence: {
      register: 'tests/register-multitrial-v1.json + tests/baseline-v4.json (5 trials × 7 cases on zai/gs-balanced)',
      reasoning: 'tests/baseline-v3.json cases trace zai/gs-balanced (CONTEXT_ISOLATION n=2)',
      instruction: 'tests/baseline-v3.json cases trace zai/gs-balanced (INSTRUCTION+ROUTING n=3)',
      latency: 'tests/register-multitrial-v1.json trial units + tests/baseline-v3.json traces (n=42)',
    },
    measuredAt: '2026-09-23',
  },
  {
    modelId: 'gs-free-big',
    provider: 'openrouter',
    providerModel: 'nvidia/nemotron-3-ultra-550b-a55b:free',
    measured: {
      register: 0.857, // 12/14 single-samples across two suite runs — multi-trial pending (phase 4)
      reasoning: 1.0, // 18/18 deterministic
      instruction: 1.0, // 11/11 deterministic
      search: 1.0, // 20/20 deterministic (SOURCE_QUALITY)
      latency: 22_873, // p50 production turn, n=63 — deep/research turns dominate; slow
    },
    canServe: ['chat', 'register', 'reasoning', 'instruction', 'search'],
    neverServe: [],
    evidence: {
      register: 'tests/baseline-v3.json (keys-present register, 7/7) + tests/baseline-v3-gsfree.json (forced free, 5/7)',
      reasoning: 'tests/baseline-v3.json + tests/baseline-v3-gsfree.json (CONTEXT_ISOLATION+FAILURE_HONESTY n=18)',
      instruction: 'tests/baseline-v3-gsfree.json (INSTRUCTION+ROUTING n=11)',
      search: 'tests/baseline-v3.json + tests/baseline-v3-gsfree.json (SOURCE_QUALITY n=20)',
      latency: 'tests/baseline-v3.json + tests/baseline-v3-gsfree.json traces (n=63)',
    },
    measuredAt: '2026-09-23',
  },
  {
    modelId: 'gs-free',
    provider: 'openrouter',
    providerModel: 'nvidia/nemotron-3-super-120b-a12b:free',
    measured: {
      register: 0.833, // 5/6 single-samples (J57 fail on the forced-free run) — thin evidence
      reasoning: 1.0, // 16/16 deterministic
      instruction: 1.0, // 26/26 deterministic
      search: 'UNMEASURED', // SOURCE_QUALITY never served by the gs-free chain
      latency: 3049, // p50 production turn, n=48
    },
    canServe: ['chat', 'register', 'reasoning', 'instruction'],
    neverServe: [],
    evidence: {
      register: 'tests/baseline-v3.json (3/3) + tests/baseline-v3-gsfree.json (2/3)',
      reasoning: 'tests/baseline-v3.json + tests/baseline-v3-gsfree.json (CONTEXT_ISOLATION+FAILURE_HONESTY n=16)',
      instruction: 'tests/baseline-v3.json + tests/baseline-v3-gsfree.json (INSTRUCTION+ROUTING n=26)',
      latency: 'tests/baseline-v3.json + tests/baseline-v3-gsfree.json traces (n=48)',
    },
    measuredAt: '2026-09-23',
  },
]

/** The numeric measured score for a capability, or null when unmeasured. */
export function measuredScore(profile: ModelProfile, cap: CapabilityTag): number | null {
  if (cap === 'chat' || cap === 'code' || cap === 'vision' || cap === 'document' || cap === 'research') {
    // tags without a measured column never carry a score
    return null
  }
  const v = profile.measured[cap]
  return typeof v === 'number' ? v : null
}

/**
 * Best MEASURED model for a capability: canServe + not neverServe + numeric
 * measured score, highest score wins. Returns null when no model holds a
 * measured score for the capability — the caller decides BLOCKED vs fallback.
 */
export function selectModelForCapability(cap: CapabilityTag): ModelProfile | null {
  const candidates = MODEL_REGISTRY.filter((m) => m.canServe.includes(cap))
    .filter((m) => !m.neverServe.includes(cap))
    .map((m) => ({ m, score: measuredScore(m, cap) }))
    .filter((x): x is { m: ModelProfile; score: number } => x.score !== null)
    .sort((a, b) => b.score - a.score)
  return candidates[0]?.m ?? null
}

/** Registry profile for an internal model id, if present. */
export function profileFor(modelId: string): ModelProfile | null {
  return MODEL_REGISTRY.find((m) => m.modelId === modelId) ?? null
}
