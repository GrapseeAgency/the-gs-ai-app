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
// `openrouter/free` is the platform's meta-router: it forwards to whichever
// free model currently has capacity (proven live: served nex-n2.5-pro while
// qwen/glm free tiers were individually rate-limited).
// ---------------------------------------------------------------------------

const OPENROUTER_MODELS: Record<string, string> = {
  'gs-free': 'openrouter/free',
  'gs-free-big': 'nvidia/nemotron-3-ultra-550b-a55b:free',
}

/** Resolve a catalogue id to an OpenRouter model, or null if it is not one. */
export function resolveOpenRouterModel(gsModelId: string | null | undefined): string | null {
  if (!gsModelId) return null
  return OPENROUTER_MODELS[gsModelId] ?? null
}

export type ModelRoute =
  | { backend: 'openrouter'; model: string }
  | { backend: 'zai'; providerModel: string | null }

/**
 * Single routing decision for the messages route: OpenRouter tiers win;
 * everything else stays on the primary provider (z-ai) with its optional
 * concrete model mapping.
 */
export function resolveModelRoute(gsModelId: string | null | undefined): ModelRoute {
  const orModel = resolveOpenRouterModel(gsModelId)
  if (orModel) return { backend: 'openrouter', model: orModel }
  return { backend: 'zai', providerModel: resolveProviderModel(gsModelId) }
}
