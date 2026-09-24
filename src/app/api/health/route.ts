import { NextResponse } from 'next/server'
import { backgroundRunsEnabled } from '@/lib/turn-producer'

export const dynamic = 'force-dynamic'

export async function GET() {
  return NextResponse.json({
    status: 'ok',
    // PHASE 1 — detached turn execution flag (ADR-117). Clients use it to
    // pick the resumable transport (messages/start + /stream) vs legacy SSE.
    backgroundRuns: backgroundRunsEnabled(),
  })
}
