import { NextResponse } from 'next/server'

export const dynamic = 'force-dynamic'

/**
 * INTERNAL capability registry (Architecture Lock, 2026-09-21).
 *
 * PRODUCT RULE: GS AI is ONE assistant experience. The user never selects a
 * model and never sees model names. This endpoint previously served a NAMED
 * catalogue (GS Free / GS Balanced / GS Deep / …) that legacy clients
 * rendered as a visible Model Centre — the last user-visible model surface
 * in the product. It is now a NAMELESS capability registry:
 *   - `models: []` — legacy selection UIs render an empty list, harmlessly.
 *   - `searchCapabilities` stays so capability-driven clients keep working.
 * No model identity, display name, context window or tier leaves the server.
 */
export async function GET() {
  // PHASE 8.2 §25 — ONE capability contract for EVERY internal route.
  // Search is a backend capability: the internal model may differ per turn,
  // the SEARCH SERVICE must not.
  const searchCapabilities = { webSearch: true, research: true, sourceOpen: true, sourceRead: true }
  return NextResponse.json({
    models: [] as unknown[],
    searchCapabilities,
  })
}
