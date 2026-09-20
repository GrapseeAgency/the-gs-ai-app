/**
 * PHASE 8.1 — real provider-model wiring for the gs-* catalogue.
 *
 * HISTORY: before this module every gs-* tier silently used the provider
 * default (the `model` key was never sent), so the in-app model selector was
 * cosmetic. The user hit this during the 2026-09-20 quota window: switching
 * tiers changed nothing because all tiers were the same call.
 *
 * MAPPING POLICY:
 *  - Text tiers map onto the provider's documented GLM text models: the fast
 *    tier onto the flash-class model, everything else onto the flagship.
 *  - `gs-vision` is intentionally UNMAPPED — the vision endpoint omits
 *    `model` by proven design (Phase 6, observed serving `glm-5v-turbo`);
 *    touching it risks a regression in an audited phase.
 *  - The chat layer treats a mapped model as an OPTIMIZATION, never a
 *    dependency: on a provider rejection that looks like an invalid/unknown
 *    model (HTTP 400/404) it retries once WITHOUT the `model` key (the exact
 *    pre-8.1 behavior), so a wrong mapping can never break chat. Account-level
 *    429s are NOT retried modelless (proven pointless — see the 2026-09-20
 *    window where all 10 probed model IDs returned the same 429).
 */

const PROVIDER_MODELS: Record<string, string> = {
  'gs-swift': 'glm-4.5-flash',
  'gs-voice': 'glm-4.5-flash',
  'gs-balanced': 'glm-4.6',
  'gs-creative': 'glm-4.6',
  'gs-coder': 'glm-4.6',
  'gs-deep': 'glm-4.6',
  'gs-research': 'glm-4.6',
}

/**
 * Resolve a catalogue model id to a concrete provider model. Unknown or
 * vision ids return null → the request omits `model` (provider default).
 */
export function resolveProviderModel(gsModelId: string | null | undefined): string | null {
  if (!gsModelId) return null
  return PROVIDER_MODELS[gsModelId] ?? null
}

// ---------------------------------------------------------------------------
// PHASE 8.1 — OpenRouter free tiers (user-supplied keys in .env, 2026-09-20).
// These tiers bypass the primary provider's account-level quota entirely.
//
// PHASE 8.1b — MODEL CHAINS. Each tier pins a PROVEN quality model first and
// keeps the `openrouter/free` meta-router as a last-resort capacity net:
//  - Live probing (2026-09-20) showed specific free models can saturate at the
//    PROVIDER level (qwen3.8-27b and gemma-4-31b returned 429 even on fresh
//    keys) — key rotation cannot fix that.
//  - The router auto-picks whichever free model has capacity, but its
//    per-call quality is unpredictable (observed: a junk model answering
//    "User Safety: safe").
// So: quality model first, router only if the pinned model fails the whole
// key pool.
// ---------------------------------------------------------------------------

const OPENROUTER_MODELS: Record<string, string[]> = {
  'gs-free': [
    'nvidia/nemotron-3-super-120b-a12b:free', // persona-faithful in live probes
    'nex-agi/nex-n2.5-pro:free', // proven quality; leaks its own brand on identity questions
    'openrouter/free', // last-resort capacity net
  ],
  'gs-free-big': ['nvidia/nemotron-3-ultra-550b-a55b:free', 'openrouter/free'],
}

// Exported for the PHASE 8.1 search planner (planner reuses the free chain
// so intent planning never depends on a single LLM provider).
export { OPENROUTER_MODELS };

/** Resolve a catalogue id to an OpenRouter model chain, or null if not one. */
export function resolveOpenRouterModel(gsModelId: string | null | undefined): string[] | null {
  if (!gsModelId) return null
  return OPENROUTER_MODELS[gsModelId] ?? null
}

export type ModelRoute =
  | { backend: 'openrouter'; models: string[] }
  | { backend: 'zai'; providerModel: string | null }

/**
 * Single routing decision for the messages route: OpenRouter tiers win;
 * everything else stays on the primary provider (z-ai) with its optional
 * concrete model mapping.
 */
export function resolveModelRoute(gsModelId: string | null | undefined): ModelRoute {
  const orModels = resolveOpenRouterModel(gsModelId)
  if (orModels) return { backend: 'openrouter', models: orModels }
  return { backend: 'zai', providerModel: resolveProviderModel(gsModelId) }
}
