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
