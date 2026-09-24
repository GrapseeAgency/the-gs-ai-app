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
 * capability as the routing key.
 *
 * SHORTFALL-MATCHING REVISION (2026-09-24, HyDRA-style): the registry is now
 * STRUCTURED, not flat. Each capability carries query-level sub-scores
 * (register → casual/sarcasm/emotional/teasing/banter; reasoning →
 * arithmetic/logic/multiStep/counterfactual; instruction →
 * oneWord/wordCount/format/negation/conditional/unseenConstraint; search →
 * retrieval/citation/freshness/diversity) and every measured cell carries its
 * evidence (artifact source, n, Wilson 95% CI). The router predicts
 * per-query requirements and serves only models that meet ALL of them —
 * see planRoute() in router.ts.
 *
 * RULES THIS FILE OBEYS:
 *  - Every score cites its eval artifact. A capability with no measured data
 *    is 'UNMEASURED' — never guessed, never inferred from tier labels.
 *  - Scores are literals derived by scripts/compute-capability-registry.ts
 *    (run with --json) from the committed artifacts. Re-run it after new eval
 *    runs and refresh this file — the numbers are the script's, not ours.
 *  - `latency` is the production turn p50/p95 (ms) across the model's
 *    recorded eval traces — mixed turn types; directional, not an SLA.
 *    p95 uses nearest-rank over the same trace population.
 *  - canServe = capability tags this model may serve (measured ≥ threshold,
 *    or its standing tier role for unmeasured tags).
 *  - neverServe = tags this model is BARRED from serving (measured < 0.5).
 *    The bar is hard: the router must not route a register turn to a model
 *    with measured register < 0.5, no matter what a tier label says.
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

/** A sub-capability score, or UNMEASURED where no artifact covers it. */
export type SubMeasured = number | 'UNMEASURED'

/** Query-level register granularity (the router's class detectors). */
export interface RegisterSubs {
  casual: SubMeasured
  sarcasm: SubMeasured
  emotional: SubMeasured
  teasing: SubMeasured
  banter: SubMeasured
}
/** Query-level reasoning granularity. */
export interface ReasoningSubs {
  arithmetic: SubMeasured
  logic: SubMeasured
  multiStep: SubMeasured
  counterfactual: SubMeasured
}
/** Query-level instruction granularity. */
export interface InstructionSubs {
  oneWord: SubMeasured
  wordCount: SubMeasured
  format: SubMeasured
  negation: SubMeasured
  conditional: SubMeasured
  unseenConstraint: SubMeasured
}
/** Query-level search granularity. */
export interface SearchSubs {
  retrieval: SubMeasured
  citation: SubMeasured
  freshness: SubMeasured
  diversity: SubMeasured
}

export interface CapabilitySubs {
  register: RegisterSubs
  reasoning: ReasoningSubs
  instruction: InstructionSubs
  search: SearchSubs
}

/** Evidence for one measured aggregate: artifact + n + Wilson 95% CI. */
export interface Measurement {
  /** The measured pass rate (point estimate). */
  rate: number
  /** Artifact the score is derived from. A score without an artifact is forbidden. */
  source: string
  /** Judged trials behind the rate. */
  n: number
  /** Wilson 95% confidence interval [lo, hi]. */
  ci: [number, number]
}

export interface ModelProfile {
  /** Internal catalogue id — NEVER exposed to a client. */
  modelId: string
  provider: 'zai' | 'openrouter'
  /** Concrete provider model (zai) or chain head (openrouter) for reference. */
  providerModel: string
  /** Per-capability, query-level granularity. UNMEASURED where no artifact covers a cell. */
  capabilities: CapabilitySubs
  /** Production turn latency percentiles across the model's recorded traces. */
  latency: { p50: number | null; p95: number | null }
  /**
   * Aggregate measured scores per capability, each with artifact + n + CI.
   * Tags without a measured column (chat/code/vision/document/research) are
   * absent — never invented.
   */
  measured: Partial<Record<CapabilityTag, Measurement>>
  canServe: CapabilityTag[]
  neverServe: CapabilityTag[]
  measuredAt: string
  /** THINKING TIER (flash-mode task, Phase 1) — how this model thinks. */
  thinking: ThinkingProfile
}

/** Below this measured rate a model is barred from the capability. */
export const CAPABILITY_THRESHOLD = 0.5

// ---------------------------------------------------------------------------
// THINKING TIER (flash-mode task, Phase 1) — ADDITIVE dimension.
//
// MEASUREMENT STATUS (honest): scripts/measure-thinking-latency.ts ran the
// 'hi'-x5-per-mode TTFT protocol and recorded ZERO successful samples — the
// z-ai account is inside the documented account-level 429 window AND the
// OpenRouter keypool was wiped to zero across all four layers (raw artifact:
// tests/thinking-latency-measurements.json, notes[]). Every flashLatencyMs /
// thinkingLatencyMs below is therefore PROVISIONAL (provider-doc + wire
// estimate) until a complete run lands; the mode feature does not depend on
// these numbers — they feed observability only. The measured capability
// scores above are NOT touched by this task.
// ---------------------------------------------------------------------------

export interface ThinkingProfile {
  /** Model has a thinking path at all. */
  supportsThinking: boolean
  /** GLM-5.3-class models do NOT allow disabled → Flash must remap. */
  thinkingCanBeDisabled: boolean
  /** Provider accepts reasoning_effort on the thinking path. */
  supportsReasoningEffort: boolean
  /** Documented effort levels (defaultEffort is used when thinking). */
  effortLevels: string[]
  defaultEffort: string
  /** Provisional TTFT p50 with thinking OFF (ms) — see MEASUREMENT STATUS. */
  flashLatencyMs: number
  /** Provisional TTFT p50 with thinking ON (ms); null = no thinking path. */
  thinkingLatencyMs: number | null
}

/**
 * Thinking profiles keyed by CONCRETE provider model id. Provider-doc facts:
 *  - z-ai (GLM): flash = { thinking: { type: 'disabled' } }; thinking =
 *    { thinking: { type: 'enabled' }, reasoning_effort: 'high' }.
 *    GLM-5.3 / GLM-5.3-FLASH are FORCED-thinking (cannot be disabled — Flash
 *    must remap, GS-MODE-REMAP); GLM-4.6 is hybrid (both paths); GLM-4.5
 *    toggles. GLM-4.7+ adds turn-level control.
 *  - OpenRouter: the unified `reasoning` param ({ enabled }) is sent per
 *    mode; models without a documented toggle are marked supportsThinking
 *    =false and the resolver forces Flash (per task spec).
 */
const PROVIDER_MODEL_THINKING: Record<string, ThinkingProfile> = {
  'glm-4.5-flash': {
    supportsThinking: true, // GLM-4.5 family toggles
    thinkingCanBeDisabled: true,
    supportsReasoningEffort: false,
    effortLevels: [],
    defaultEffort: 'high',
    flashLatencyMs: 750, // provisional
    thinkingLatencyMs: 1900, // provisional
  },
  'glm-4.6': {
    supportsThinking: true, // hybrid thinking (auto) — both paths exist
    thinkingCanBeDisabled: true,
    supportsReasoningEffort: true,
    effortLevels: ['high', 'low'],
    defaultEffort: 'high',
    flashLatencyMs: 780, // provisional
    thinkingLatencyMs: 2100, // provisional
  },
  'glm-5v-turbo': {
    supportsThinking: false, // vision endpoint — observed thinking-disabled only
    thinkingCanBeDisabled: true,
    supportsReasoningEffort: false,
    effortLevels: [],
    defaultEffort: 'high',
    flashLatencyMs: 900, // provisional
    thinkingLatencyMs: null,
  },
  'glm-5.3': {
    supportsThinking: true,
    thinkingCanBeDisabled: false, // FORCED thinking — Flash must remap
    supportsReasoningEffort: true,
    effortLevels: ['max', 'high', 'low'],
    defaultEffort: 'high',
    flashLatencyMs: 800, // provisional (served via remap)
    thinkingLatencyMs: 1700, // provisional
  },
  'glm-5.3-flash': {
    supportsThinking: true,
    thinkingCanBeDisabled: false, // also forced-thinking per Z.AI docs
    supportsReasoningEffort: false,
    effortLevels: [],
    defaultEffort: 'high',
    flashLatencyMs: 600, // provisional (served via remap)
    thinkingLatencyMs: 1600, // provisional
  },
  'nvidia/nemotron-3-super-120b-a12b:free': {
    supportsThinking: true, // nemotron-3 documents a reasoning toggle
    thinkingCanBeDisabled: true,
    supportsReasoningEffort: false,
    effortLevels: [],
    defaultEffort: 'high',
    flashLatencyMs: 1500, // provisional
    thinkingLatencyMs: 3200, // provisional
  },
  'nvidia/nemotron-3-ultra-550b-a55b:free': {
    supportsThinking: true,
    thinkingCanBeDisabled: true,
    supportsReasoningEffort: false,
    effortLevels: [],
    defaultEffort: 'high',
    flashLatencyMs: 2200, // provisional
    thinkingLatencyMs: 4400, // provisional
  },
  'nex-agi/nex-n2.5-pro:free': {
    supportsThinking: false, // no documented toggle → force Flash (task spec)
    thinkingCanBeDisabled: true,
    supportsReasoningEffort: false,
    effortLevels: [],
    defaultEffort: 'high',
    flashLatencyMs: 1500, // provisional
    thinkingLatencyMs: null,
  },
  'openrouter/free': {
    supportsThinking: false, // meta-router — heterogeneous backends, no toggle contract
    thinkingCanBeDisabled: true,
    supportsReasoningEffort: false,
    effortLevels: [],
    defaultEffort: 'high',
    flashLatencyMs: 1500, // provisional
    thinkingLatencyMs: null,
  },
}

/** Thinking profile for an internal model id (via its provider model), with a fail-safe flash-only default. */
export function thinkingProfileFor(modelId: string): ThinkingProfile {
  const profile = profileFor(modelId)
  const key = profile?.providerModel ?? modelId
  return (
    PROVIDER_MODEL_THINKING[key] ?? {
      supportsThinking: false,
      thinkingCanBeDisabled: true,
      supportsReasoningEffort: false,
      effortLevels: [],
      defaultEffort: 'high',
      flashLatencyMs: 900,
      thinkingLatencyMs: null,
    }
  )
}

/**
 * Nearest flash-capable PROVIDER model for a model whose thinking cannot be
 * disabled. GLM-5.3-class → the hybrid flagship (glm-4.6). null = no remap
 * known (caller sends the disabled flag best-effort).
 */
export function remapToFlashCapable(providerModel: string): string | null {
  const t = PROVIDER_MODEL_THINKING[providerModel]
  if (!t || t.thinkingCanBeDisabled) return null
  if (providerModel.startsWith('glm-')) return 'glm-4.6'
  return null
}

/**
 * Register turns demand MORE than the bar: they are exactly the class the
 * tier router broke, so a shortfall-matched route requires the measured
 * sub-register (or aggregate fallback) to be ≥ 0.7. Product policy, not a
 * measurement — the measurement is the score; this is the requirement.
 */
export const REGISTER_SUBCAP_REQUIREMENT = 0.7

function subs(all: Partial<CapabilitySubs>): CapabilitySubs {
  return {
    register: { casual: 'UNMEASURED', sarcasm: 'UNMEASURED', emotional: 'UNMEASURED', teasing: 'UNMEASURED', banter: 'UNMEASURED', ...(all.register ?? {}) },
    reasoning: { arithmetic: 'UNMEASURED', logic: 'UNMEASURED', multiStep: 'UNMEASURED', counterfactual: 'UNMEASURED', ...(all.reasoning ?? {}) },
    instruction: {
      oneWord: 'UNMEASURED',
      wordCount: 'UNMEASURED',
      format: 'UNMEASURED',
      negation: 'UNMEASURED',
      conditional: 'UNMEASURED',
      unseenConstraint: 'UNMEASURED',
      ...(all.instruction ?? {}),
    },
    search: { retrieval: 'UNMEASURED', citation: 'UNMEASURED', freshness: 'UNMEASURED', diversity: 'UNMEASURED', ...(all.search ?? {}) },
  }
}

export const MODEL_REGISTRY: ModelProfile[] = [
  {
    modelId: 'gs-swift',
    provider: 'zai',
    providerModel: 'glm-4.5-flash',
    capabilities: subs({
      register: { casual: 1.0, emotional: 1.0 }, // 10/10 casual (J57+J60 ×5), 5/5 emotional (J54 ×5)
      // sarcasm/teasing/banter: UNMEASURED — phase-1 direct + phase-3 production trials pending
    }),
    latency: { p50: 749, p95: 2879 }, // n=15 production traces
    measured: {
      register: {
        rate: 1.0, // 15/15 multi-trial (J54/J57/J60 × 5)
        source: 'tests/register-multitrial-v1.json (z-ai column, judge glm-4.6 temp 0)',
        n: 15,
        ci: [0.796, 1.0],
      },
    },
    canServe: ['register', 'chat'],
    neverServe: [],
    measuredAt: '2026-09-23',
    thinking: PROVIDER_MODEL_THINKING['glm-4.5-flash'],
  },
  {
    modelId: 'gs-balanced',
    provider: 'zai',
    providerModel: 'glm-4.6',
    capabilities: subs({
      register: {
        casual: 'UNMEASURED', // J57/J60 never served by gs-balanced in any artifact
        banter: 0.333, // 5/15 (J51 5/5, J52 0/5, J59 0/5)
        sarcasm: 0.0, // 0/5 (J55)
        teasing: 0.0, // 0/10 (J53 + J58)
        emotional: 0.0, // 0/5 (J56 social-nuance)
      },
      instruction: { oneWord: 1.0 }, // 2/2 (I12, I14)
    }),
    latency: { p50: 776, p95: 3719 }, // n=42 production traces
    measured: {
      register: {
        rate: 0.143, // 5/35 — SYSTEMATIC fail (J51 5/5 only; six cases 0/5 each)
        source: 'tests/register-multitrial-v1.json + tests/baseline-v4.json (z-ai column, 5 trials × 7 cases)',
        n: 35,
        ci: [0.063, 0.294],
      },
      reasoning: {
        rate: 1.0, // 2/2 deterministic (CONTEXT_ISOLATION subset)
        source: 'tests/baseline-v3.json cases trace zai/gs-balanced (CONTEXT_ISOLATION n=2)',
        n: 2,
        ci: [0.342, 1.0],
      },
      instruction: {
        rate: 1.0, // 3/3 deterministic (INSTRUCTION+ROUTING subset — literalist route-around cases)
        source: 'tests/baseline-v3.json cases trace zai/gs-balanced (INSTRUCTION+ROUTING n=3)',
        n: 3,
        ci: [0.439, 1.0],
      },
    },
    canServe: ['chat', 'reasoning', 'instruction'],
    neverServe: ['register'],
    measuredAt: '2026-09-23',
    thinking: PROVIDER_MODEL_THINKING['glm-4.6'],
  },
  {
    modelId: 'gs-free-big',
    provider: 'openrouter',
    providerModel: 'nvidia/nemotron-3-ultra-550b-a55b:free',
    capabilities: subs({
      register: {
        casual: 'UNMEASURED', // J57/J60 ride the gs-free chain in every artifact
        banter: 0.833, // 5/6 across baseline-v3 (3/3) + baseline-v3-gsfree (2/3)
        sarcasm: 1.0, // 2/2 (J55 in both runs)
        teasing: 1.0, // 4/4 (J53+J58 in both runs)
        emotional: 0.5, // 1/2 (J56 ✓ v3, ✗ v3-gsfree)
      },
      instruction: { oneWord: 1.0 }, // 2/2
      search: {
        retrieval: 1.0, // 2/2 (S40 sourcesReadMin)
        citation: 1.0, // 20/20 (citationsWithinSources across both runs)
        diversity: 1.0, // 14/14 (domainsMin≥3, S31–S37)
        freshness: 'UNMEASURED', // no date assertion exists in any artifact
      },
    }),
    latency: { p50: 22_873, p95: 80_215 }, // n=63 — deep/research turns dominate; slow
    measured: {
      register: {
        rate: 0.857, // 12/14 single-samples across two suite runs — multi-trial pending (phase 4)
        source: 'tests/baseline-v3.json (keys-present register, 7/7) + tests/baseline-v3-gsfree.json (forced free, 5/7)',
        n: 14,
        ci: [0.601, 0.96],
      },
      reasoning: {
        rate: 1.0, // 18/18 deterministic
        source: 'tests/baseline-v3.json + tests/baseline-v3-gsfree.json (CONTEXT_ISOLATION+FAILURE_HONESTY n=18)',
        n: 18,
        ci: [0.824, 1.0],
      },
      instruction: {
        rate: 1.0, // 11/11 deterministic
        source: 'tests/baseline-v3.json (INSTRUCTION+ROUTING n=11)',
        n: 11,
        ci: [0.741, 1.0],
      },
      search: {
        rate: 1.0, // 20/20 deterministic (SOURCE_QUALITY)
        source: 'tests/baseline-v3.json + tests/baseline-v3-gsfree.json (SOURCE_QUALITY n=20)',
        n: 20,
        ci: [0.839, 1.0],
      },
    },
    canServe: ['chat', 'register', 'reasoning', 'instruction', 'search'],
    neverServe: [],
    measuredAt: '2026-09-23',
    thinking: PROVIDER_MODEL_THINKING['nvidia/nemotron-3-ultra-550b-a55b:free'],
  },
  {
    modelId: 'gs-free',
    provider: 'openrouter',
    providerModel: 'nvidia/nemotron-3-super-120b-a12b:free',
    capabilities: subs({
      register: {
        casual: 0.75, // 3/4 (J57 ✗ on forced-free run, J60 ✓ both runs)
        banter: 'UNMEASURED', // J51/J52/J59 ride the big chain in every artifact
        sarcasm: 'UNMEASURED',
        teasing: 'UNMEASURED',
        emotional: 1.0, // 2/2 (J54 both runs)
      },
      instruction: {
        oneWord: 1.0, // 6/6 (I11/I17/I18 across both runs)
        wordCount: 1.0, // 2/2 (I13)
        format: 1.0, // 2/2 (I19 lineCount)
        conditional: 1.0, // 2/2 (I16 if-then)
        negation: 'UNMEASURED', // no negation case exists in any artifact
        unseenConstraint: 'UNMEASURED', // the whole v1 suite is seen — phase 4 adds this
      },
    }),
    latency: { p50: 3049, p95: 14_384 }, // n=48
    measured: {
      register: {
        rate: 0.833, // 5/6 single-samples (J57 fail on the forced-free run) — thin evidence
        source: 'tests/baseline-v3.json (3/3) + tests/baseline-v3-gsfree.json (2/3)',
        n: 6,
        ci: [0.436, 0.97],
      },
      reasoning: {
        rate: 1.0, // 16/16 deterministic
        source: 'tests/baseline-v3.json + tests/baseline-v3-gsfree.json (CONTEXT_ISOLATION+FAILURE_HONESTY n=16)',
        n: 16,
        ci: [0.806, 1.0],
      },
      instruction: {
        rate: 1.0, // 26/26 deterministic
        source: 'tests/baseline-v3.json + tests/baseline-v3-gsfree.json (INSTRUCTION+ROUTING n=26)',
        n: 26,
        ci: [0.871, 1.0],
      },
    },
    canServe: ['chat', 'register', 'reasoning', 'instruction'],
    neverServe: [],
    measuredAt: '2026-09-23',
    thinking: PROVIDER_MODEL_THINKING['nvidia/nemotron-3-super-120b-a12b:free'],
  },
]

/** The numeric measured aggregate for a capability, or null when unmeasured. */
export function measuredScore(profile: ModelProfile, cap: CapabilityTag): number | null {
  return profile.measured[cap]?.rate ?? null
}

/** The numeric sub-capability score, or null when that cell is UNMEASURED. */
export function subScore(profile: ModelProfile, cap: CapabilityTag, sub: string): number | null {
  const cell = (profile.capabilities as Record<string, Record<string, SubMeasured>>)[cap]?.[sub]
  return typeof cell === 'number' ? cell : null
}

/** Wilson 95% CI for a measured aggregate, or null when unmeasured. */
export function measuredCi(profile: ModelProfile, cap: CapabilityTag): [number, number] | null {
  return profile.measured[cap]?.ci ?? null
}

/**
 * Best MEASURED model for a capability: canServe + not neverServe + numeric
 * measured score, highest score wins (registry order breaks ties). Returns
 * null when no model holds a measured score for the capability — the caller
 * decides BLOCKED vs fallback.
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

// ---------------------------------------------------------------------------
// THINKING MODE RESOLUTION (flash-mode task, Phases 1/2/4)
// ---------------------------------------------------------------------------

/** Wire mode sent by clients (absent = 'flash' — backward compatible). */
export type ThinkingMode = 'flash' | 'thinking' | 'auto'

/** Strict mode parser for the wire field: absent/invalid → 'flash'. */
export function parseThinkingMode(raw: unknown): ThinkingMode {
  if (raw === 'thinking' || raw === 'auto' || raw === 'flash') return raw
  return 'flash'
}

/**
 * Resolve the REQUESTED mode into the turn's EFFECTIVE mode, before the
 * per-model capability check. Turn-class overrides (task Phase 4):
 *  - TIME turns are always flash (the clock is deterministic; depth is waste).
 *  - DEEP_RESEARCH always thinks (the user opting into research committed to
 *    depth — mode is ignored).
 *  - 'auto' lets the router decide: flash for the efficient TEXT_SIMPLE
 *    class, thinking for everything else.
 *  - Everything else honors the user's pick. Register and thinking are
 *    ORTHOGONAL — a register-class turn with mode=thinking still thinks.
 */
export function effectiveModeFor(
  mode: ThinkingMode,
  route: string,
  capability: string
): 'flash' | 'thinking' {
  if (capability === 'TIME') return 'flash'
  if (route === 'DEEP_RESEARCH' || capability === 'DEEP_RESEARCH') return 'thinking'
  if (mode === 'thinking') return 'thinking'
  if (mode === 'flash') return 'flash'
  // auto — the router decides (short/efficient turns stay flash).
  return route === 'TEXT_SIMPLE' ? 'flash' : 'thinking'
}

/** The per-turn thinking decision the provider call layers consume. */
export interface ResolvedThinking {
  /** 'flash' | 'thinking' — after turn-class overrides + model capability. */
  effective: 'flash' | 'thinking'
  /** Effort level sent on the thinking path (z-ai models that accept it). */
  effort?: string
  /** Non-null when a forced-thinking model was remapped for Flash. */
  providerModelOverride: string | null
  /** Human-readable remap/force note for the trace (null = clean). */
  remapNote: string | null
}

/**
 * Resolve the turn's mode into the provider-layer thinking decision — THE
 * single function the turn executor calls (task Phase 2's resolver, with the
 * Phase 4 turn-class overrides built in). The mode NEVER changes capability
 * routing — only the model's thinking config (and, for forced-thinking
 * models, the concrete provider model id). On the OpenRouter path both
 * modes map onto the unified reasoning param; models that ignore it degrade
 * gracefully to their own default.
 */
export function resolveThinkingConfig(
  mode: ThinkingMode,
  internalModelId: string,
  route: string,
  capability: string
): ResolvedThinking {
  const t = thinkingProfileFor(internalModelId)
  const wanted = effectiveModeFor(mode, route, capability)

  // Model cannot think (vision endpoint, toggle-less free models): flash forced.
  if (wanted === 'thinking' && !t.supportsThinking) {
    return {
      effective: 'flash',
      effort: undefined,
      providerModelOverride: null,
      remapNote: `${internalModelId} has no thinking path — flash forced`,
    }
  }

  // Flash on a FORCED-thinking model: remap to the nearest flash-capable tier.
  if (wanted === 'flash') {
    const profile = profileFor(internalModelId)
    const providerModel = profile?.providerModel ?? internalModelId
    if (!t.thinkingCanBeDisabled) {
      const remap = remapToFlashCapable(providerModel)
      if (remap) {
        return {
          effective: 'flash',
          effort: undefined,
          providerModelOverride: remap,
          remapNote: `${providerModel}→${remap} (forced thinking; flash requested)`,
        }
      }
      // No remap known — send disabled best-effort (provider may ignore).
      return { effective: 'flash', effort: undefined, providerModelOverride: null, remapNote: null }
    }
    return { effective: 'flash', effort: undefined, providerModelOverride: null, remapNote: null }
  }

  return {
    effective: 'thinking',
    effort: t.supportsReasoningEffort ? t.defaultEffort : undefined,
    providerModelOverride: null,
    remapNote: null,
  }
}
