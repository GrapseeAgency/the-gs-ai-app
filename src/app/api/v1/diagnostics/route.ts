/**
 * DEVICE DIAGNOSTICS — the endpoint the phone-side acceptance harness pings.
 *
 * Purpose (forensic closure TASK 4): prove the PHYSICAL DEVICE is talking to
 * the SHIPPED backend before trusting any on-device test result. Returns:
 *
 *   - clientVersion:   the X-GS-App-Version the request itself carried
 *   - currentVersion:  the backend's current expected app version
 *   - versionMatch:    whether the two agree ('web' callers are exempt, as
 *                      everywhere else in the API)
 *   - backendRevision: env override > live git revision — the proof of WHICH
 *                      backend this is
 *   - trace:           the last N GS-CAP rows (the structured trace store,
 *                      src/lib/trace.ts) for ONE conversation, newest first,
 *                      each with the user prompt preview so the operator can
 *                      match rows to the 6 acceptance prompts by eye
 *
 * ARCHITECTURE LOCK: modelRoute stays INTERNAL ONLY and is deliberately NOT
 * returned — no plan field, model id or provider name reaches a client.
 *
 * Scoping: without ?conversationId only the version block is returned (no
 * cross-conversation content). With ?conversationId the trace rows are scoped
 * to that conversation — the same visibility GET /conversations/:id/messages
 * already grants, no new exposure class.
 */

import { NextRequest, NextResponse } from 'next/server'
import { db } from '@/lib/db'
import { getBackendRevision } from '@/lib/revision'

export const dynamic = 'force-dynamic'

const CURRENT_APP_VERSION = '0.68.2'
const DEFAULT_LIMIT = 10
const MAX_LIMIT = 50

function parseLimit(raw: string | null): number {
  if (raw === null) return DEFAULT_LIMIT
  const parsed = Number.parseInt(raw, 10)
  if (Number.isNaN(parsed)) return DEFAULT_LIMIT
  return Math.min(Math.max(parsed, 1), MAX_LIMIT)
}

// GET /api/v1/diagnostics?conversationId=<id>&limit=10
export async function GET(req: NextRequest) {
  const url = new URL(req.url)
  const clientVersion = req.headers.get('x-gs-app-version') ?? 'web'
  const backendRevision = await getBackendRevision()
  const conversationId = url.searchParams.get('conversationId')
  const limit = parseLimit(url.searchParams.get('limit'))

  const base = {
    clientVersion,
    currentVersion: CURRENT_APP_VERSION,
    versionMatch: clientVersion === 'web' || clientVersion === CURRENT_APP_VERSION,
    backendRevision,
    conversationId,
  }

  if (!conversationId) {
    return NextResponse.json({ ...base, trace: [] })
  }

  const rows = await db.turnTrace.findMany({
    where: { conversationId },
    orderBy: { timestamp: 'desc' },
    take: limit,
  })

  // Prompt previews: join each trace row's user message so the operator can
  // match rows to the acceptance prompts without another call.
  const messageIds = Array.from(new Set(rows.map((r) => r.messageId)))
  const messages = await db.message.findMany({
    where: { id: { in: messageIds } },
    select: { id: true, role: true, content: true },
  })
  const promptById = new Map(
    messages.filter((m) => m.role === 'user').map((m) => [m.id, m.content.slice(0, 120)])
  )

  const trace = rows.map((r) => ({
    requestId: r.requestId,
    messageId: r.messageId,
    timestamp: r.timestamp.toISOString(),
    clientVersion: r.clientVersion,
    backendRevision: r.backendRevision,
    capability: r.capability,
    trigger: r.trigger,
    searchExecuted: r.searchExecuted,
    researchExecuted: r.researchExecuted,
    evidenceCount: r.evidenceCount,
    sourceCount: r.sourceCount,
    sourcesRead: r.sourcesRead,
    sourcesFailed: r.sourcesFailed,
    domains: JSON.parse(r.domains) as string[],
    finalStatus: r.finalStatus,
    cited: JSON.parse(r.cited) as number[],
    latencyMs: r.latencyMs,
    errorType: r.errorType,
    promptPreview: promptById.get(r.messageId) ?? null,
  }))

  return NextResponse.json({ ...base, trace })
}
