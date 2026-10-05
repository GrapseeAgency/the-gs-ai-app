import { NextResponse } from 'next/server'
import { backgroundRunsEnabled } from '@/lib/turn-producer'
import { completeChat } from '@/lib/ai'
import { loadKeyPool } from '@/lib/keypool'
import { orHealthProbe } from '@/lib/openrouter'

export const dynamic = 'force-dynamic'

/**
 * OUTAGE 2026-10-05 (ITEM 2) — the old handler returned a hardcoded
 * { status: 'ok' } without contacting ANY upstream, so it reported healthy
 * for the entire duration of a total send-path outage (every POST messages
 * returned 502) and fooled the eval-gate CI pre-flight into treating the
 * origin as reachable.
 *
 * The new contract: health reflects the REAL message path with one cheap
 * upstream call per layer, and 503 when the product cannot answer.
 *
 * Probe order mirrors the turn path (src/lib/turn-executor.ts):
 *   1. keypool non-empty -> one 1-token OpenRouter call (the free chain).
 *      200 -> the fallback layer is alive; report 200.
 *      failing -> probe the primary provider before declaring an outage
 *      (turns fall back to it when OpenRouter is exhausted).
 *   2. keypool empty -> the free tier is offline; the primary provider is
 *      the only serving path, so probe it. Alive -> 200 (body states the
 *      pool is empty so monitors can distinguish full from partial health).
 *      Dead -> 503 — this is the state where the old endpoint lied.
 *
 * Successes are cached for 15s (failures 5s) so an aggressive poller cannot
 * burn the key pool; the cache key includes the current second and any pool
 * change re-probes on the next call. No key material is ever returned.
 */

type PrimaryProbe = { ok: boolean; latencyMs: number; error: string | null }

const HEALTH_CACHE_OK_MS = 15_000
const HEALTH_CACHE_FAIL_MS = 5_000
const PRIMARY_PROBE_MESSAGES = [{ role: 'user', content: 'Reply with the single word: ok' }]

let cache: { at: number; ok: boolean; body: Record<string, unknown> } | null = null

async function probePrimary(): Promise<PrimaryProbe> {
  const t0 = Date.now()
  try {
    const text = await completeChat(PRIMARY_PROBE_MESSAGES, null)
    return {
      ok: typeof text === 'string' && text.trim().length > 0,
      latencyMs: Date.now() - t0,
      error: null,
    }
  } catch (e) {
    return {
      ok: false,
      latencyMs: Date.now() - t0,
      error: (e instanceof Error ? e.message : String(e)).slice(0, 200),
    }
  }
}

export async function GET() {
  const now = Date.now()
  if (cache && now - cache.at < (cache.ok ? HEALTH_CACHE_OK_MS : HEALTH_CACHE_FAIL_MS)) {
    return NextResponse.json({ ...cache.body, cached: true }, { status: cache.ok ? 200 : 503 })
  }

  const pool = await loadKeyPool()
  const or = await orHealthProbe()

  let primary: PrimaryProbe | null = null
  // Pool loaded and OpenRouter answered: the fallback layer is serving.
  let upstreamAlive = or.ok

  if (or.probed && !or.ok) {
    // Fallback layer down (invalid keys / provider failure) — the turn path
    // falls back to the primary provider, so probe it before declaring 503.
    primary = await probePrimary()
    upstreamAlive = primary.ok
  }
  if (!or.probed) {
    // Pool empty: free tier offline. The primary is the only serving path.
    primary = await probePrimary()
    upstreamAlive = primary.ok
  }

  const body: Record<string, unknown> = {
    status: upstreamAlive ? 'ok' : 'unavailable',
    backgroundRuns: backgroundRunsEnabled(),
    keypool: { layer: pool.layer, size: pool.keys.length },
    upstream: {
      openrouter: {
        probed: or.probed,
        ok: or.ok,
        status: or.status,
        latencyMs: or.latencyMs,
        error: or.error,
      },
      ...(primary !== null ? { primary } : {}),
    },
    checkedAt: new Date(now).toISOString(),
  }
  cache = { at: now, ok: upstreamAlive, body }
  return NextResponse.json(body, { status: upstreamAlive ? 200 : 503 })
}
