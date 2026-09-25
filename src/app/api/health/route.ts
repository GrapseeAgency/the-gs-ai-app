import { NextResponse } from 'next/server'
import { backgroundRunsEnabled } from '@/lib/turn-producer'
import { ensureProviderCatalogSync, getProviderCatalogCache } from '@/lib/provider-catalog-store'

export const dynamic = 'force-dynamic'

export async function GET() {
  // Catalog synchronization is metadata-only, non-blocking, and exposes only
  // aggregate counts here. Provider/model identities remain server-private.
  ensureProviderCatalogSync()
  const catalog = getProviderCatalogCache()
  const models = Object.values(catalog?.providers ?? {}).flatMap((providerModels) => providerModels ?? [])
  return NextResponse.json({
    status: 'ok',
    // PHASE 1 — detached turn execution flag (ADR-117). Clients use it to
    // pick the resumable transport (messages/start + /stream) vs legacy SSE.
    backgroundRuns: backgroundRunsEnabled(),
    catalog: {
      modelCount: models.length,
      freeModelCount: models.filter((model) => model.freePolicy === 'free').length,
      candidateModelCount: models.filter((model) => model.isCandidate).length,
      syncedAt: catalog?.syncedAt ?? null,
    },
  })
}
