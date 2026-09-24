import { NextRequest, NextResponse } from 'next/server'

/**
 * EVAL INFRASTRUCTURE — internal LLM relay for the measurement harnesses.
 *
 * WHY THIS EXISTS (2026-09-24): the z-ai gateway began discriminating by
 * CLIENT PROCESS — every FRESH process (bun scripts, `node -e`, persistent
 * retriers) receives an instant 429 on every model, while THIS long-lived
 * dev-server process reaches the same gateway with the same config, same
 * SDK and same headers (evidence: dev.log zai/gs-balanced turn done at the
 * same minute script probes 429'd; 6-retry cadence over 100s from a fresh
 * process: 6×429). The eval harnesses (register judge, controlled
 * experiments) therefore need their z-ai calls to LEAVE FROM THIS PROCESS.
 *
 * WHAT IT IS: a dumb transport. The caller supplies the EXACT completion
 * body (model, messages, temperature, thinking); this route calls the SDK
 * server-side and returns the raw content. It NEVER consults the router
 * (src/lib/router.ts is not imported), never alters prompts, never selects
 * a model itself — it is a wire, not a brain. It is how the pinned register
 * judge (glm-4.6, temp 0) and the gs-swift DIRECT experiment reach the
 * gateway; the judge logic and the experiment semantics live unchanged in
 * the scripts.
 *
 * CONTRACT: DEV-ONLY (404 outside development, like the other fixtures),
 * never linked from the app, never exposed on the public wire, requires the
 * x-gs-eval-key header. Response: { content, model } or 502 { error }.
 */

export const dynamic = 'force-dynamic'

import ZAI from 'z-ai-web-dev-sdk'

export async function POST(req: NextRequest) {
  if (process.env.NODE_ENV === 'production') {
    return NextResponse.json({ code: 'not_found', message: 'Not found' }, { status: 404 })
  }
  const expectedKey = process.env.GS_EVAL_KEY ?? 'gs-eval-local'
  if (req.headers.get('x-gs-eval-key') !== expectedKey) {
    return NextResponse.json({ code: 'forbidden', message: 'eval relay key mismatch' }, { status: 403 })
  }

  let body: {
    model?: string
    messages?: { role: string; content: string }[]
    temperature?: number
    thinking?: { type: 'disabled' | 'enabled' }
    timeoutMs?: number
  }
  try {
    body = await req.json()
  } catch {
    return NextResponse.json({ code: 'bad_request', message: 'invalid JSON' }, { status: 400 })
  }
  if (!body.model || !Array.isArray(body.messages) || body.messages.length === 0) {
    return NextResponse.json({ code: 'bad_request', message: 'model and messages are required' }, { status: 400 })
  }

  const timeoutMs = Math.min(Math.max(body.timeoutMs ?? 120_000, 1_000), 300_000)
  const startedAt = Date.now()
  try {
    const zai = await ZAI.create()
    const completion = (await Promise.race([
      zai.chat.completions.create({
        model: body.model,
        messages: body.messages,
        stream: false,
        thinking: body.thinking ?? { type: 'disabled' },
        ...(typeof body.temperature === 'number' ? { temperature: body.temperature } : {}),
      } as never),
      new Promise((_, reject) => setTimeout(() => reject(new Error('relay timeout')), timeoutMs)),
    ])) as { choices?: { message?: { content?: unknown } }[] } | null
    const content = completion?.choices?.[0]?.message?.content
    if (typeof content !== 'string') {
      return NextResponse.json({ code: 'upstream_error', error: 'empty completion' }, { status: 502 })
    }
    return NextResponse.json({ content, model: body.model, latencyMs: Date.now() - startedAt })
  } catch (e) {
    return NextResponse.json(
      { code: 'upstream_error', error: (e instanceof Error ? e.message : String(e)).slice(0, 300) },
      { status: 502 }
    )
  }
}
