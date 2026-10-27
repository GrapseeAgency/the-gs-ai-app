/**
 * BENCHMARK INFRA — PHASE 1: OpenAI-compatible shim (GET /models).
 * Standard discovery endpoint so harnesses can list what the shim serves.
 */

import { NextResponse } from 'next/server'

export const runtime = 'nodejs'

const MODELS = [
  { id: 'gs-ai', note: 'GS AI provider router, generator role' },
  { id: 'gs-ai-flash', note: 'GS AI provider router, cheap role' },
  { id: 'gs-ai-thinking', note: 'GS AI provider router, planner role' },
  { id: 'openai/gpt-5.5', note: 'OpenRouter passthrough' },
  { id: 'anthropic/claude-opus-4.8', note: 'OpenRouter passthrough' },
  { id: 'google/gemini-3.1-pro', note: 'OpenRouter passthrough' },
]

export async function GET() {
  const created = Math.floor(Date.now() / 1000)
  return NextResponse.json(
    {
      object: 'list',
      data: MODELS.map((m) => ({
        id: m.id,
        object: 'model',
        created,
        owned_by: 'gs-bench-shim',
        note: m.note,
      })),
    },
    { headers: { 'x-gs-bench': 'openai-shim' } }
  )
}
