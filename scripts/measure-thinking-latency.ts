/**
 * PHASE 1 (FLASH MODE) — live thinking-latency measurement.
 *
 * Protocol (task spec): send "hi" with thinking OFF and thinking ON through
 * the REAL provider (z-ai-web-dev-sdk), 5 samples per mode, measure TTFT
 * (time to first streamed delta) and full completion time; record the p50/p95
 * into tests/thinking-latency-measurements.json — the committed artifact that
 * backs the numbers in src/lib/model-capabilities.ts.
 *
 * Run: bun scripts/measure-thinking-latency.ts
 * Honest failure: if the provider is quota-throttled (429) the script records
 * the failure class in the artifact instead of inventing numbers.
 *
 * The z-ai account can sit inside a multi-hour account-level 429 window (the
 * documented 2026-09-20 class). The script therefore ALSO measures the
 * OpenRouter free chain (nemotron-3-super, reasoning toggle documented) with
 * the identical protocol — those keys are quota-independent — so the artifact
 * always carries real measured wire numbers for at least one provider.
 */

import ZAI from 'z-ai-web-dev-sdk'
import { writeFileSync } from 'node:fs'
import { loadKeyPool } from '../src/lib/keypool'

const OR_MODEL = 'nvidia/nemotron-3-super-120b-a12b:free'
const OR_BASE = 'https://openrouter.ai/api/v1'

async function measureOpenRouterOnce(key: string, thinking: boolean): Promise<{ ttftMs: number; totalMs: number }> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 30_000)
  const started = Date.now()
  try {
    const response = await fetch(`${OR_BASE}/chat/completions`, {
      method: 'POST',
      signal: controller.signal,
      headers: {
        Authorization: `Bearer ${key}`,
        'Content-Type': 'application/json',
        'HTTP-Referer': 'https://grapsee.agency',
        'X-Title': 'GS AI',
      },
      body: JSON.stringify({
        model: OR_MODEL,
        messages: [
          { role: 'system', content: 'You are GS AI. Answer the user.' },
          { role: 'user', content: PROMPT },
        ],
        stream: true,
        reasoning: thinking ? { enabled: true } : { enabled: false },
        temperature: 0.2,
      }),
    })
    if (!response.ok || !response.body) throw new Error(`HTTP ${response.status}`)
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let ttft: number | null = null
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      if (ttft === null) ttft = Date.now() - started
      buffer += decoder.decode(value, { stream: true })
      if (buffer.includes('[DONE]')) break
    }
    const total = Date.now() - started
    return { ttftMs: ttft ?? total, totalMs: total }
  } finally {
    clearTimeout(timer)
  }
}

async function measureOpenRouterMode(keys: string[], thinking: boolean): Promise<{
  thinking: boolean
  ttftSamples: number[]
  totalSamples: number[]
  error: string | null
}> {
  const ttftSamples: number[] = []
  const totalSamples: number[] = []
  let lastError: string | null = null
  for (let i = 0; i < SAMPLES; i++) {
    const key = keys[i % keys.length]
    try {
      const r = await measureOpenRouterOnce(key, thinking)
      ttftSamples.push(r.ttftMs)
      totalSamples.push(r.totalMs)
      console.log(
        `  sample ${i + 1}/${SAMPLES} mode=${thinking ? 'thinking' : 'flash'} ttft=${r.ttftMs}ms total=${r.totalMs}ms`
      )
    } catch (e) {
      lastError = e instanceof Error ? e.message : String(e)
      console.log(`  sample ${i + 1}/${SAMPLES} mode=${thinking ? 'thinking' : 'flash'} FAILED: ${lastError}`)
    }
    await new Promise((r) => setTimeout(r, 400))
  }
  return { thinking, ttftSamples, totalSamples, error: lastError }
}

const SAMPLES = 5
const PROMPT = 'hi'

function percentile(values: number[], p: number): number | null {
  if (values.length === 0) return null
  const sorted = [...values].sort((a, b) => a - b)
  const idx = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)
  return Math.round(sorted[idx])
}

async function measureOnce(thinking: boolean): Promise<{ ttftMs: number; totalMs: number; model: string | null }> {
  const zai = await ZAI.create()
  const body: Record<string, unknown> = {
    messages: [
      { role: 'system', content: 'You are GS AI. Answer the user.' },
      { role: 'user', content: PROMPT },
    ],
    stream: true,
    thinking: thinking ? { type: 'enabled', reasoning_effort: 'high' } : { type: 'disabled' },
    temperature: 0.2,
  }
  const started = Date.now()
  const response: unknown = await Promise.race([
    zai.chat.completions.create(body as never),
    new Promise((_, reject) => setTimeout(() => reject(new Error('connect timeout')), 30_000)),
  ])
  if (!(response && typeof response === 'object' && 'getReader' in (response as object))) {
    // Non-streaming response — TTFT == total.
    const completion = response as { choices?: { message?: { content?: unknown } }[]; model?: unknown }
    const text = completion?.choices?.[0]?.message?.content
    return {
      ttftMs: Date.now() - started,
      totalMs: Date.now() - started,
      model: typeof completion?.model === 'string' ? completion.model : null,
      ...(typeof text === 'string' ? {} : {}),
    }
  }
  const reader = (response as ReadableStream<Uint8Array>).getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let ttft: number | null = null
  let total = Date.now() - started
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    if (ttft === null) ttft = Date.now() - started
    buffer += decoder.decode(value, { stream: true })
    if (buffer.includes('[DONE]')) break
  }
  total = Date.now() - started
  return { ttftMs: ttft ?? total, totalMs: total, model: null }
}

async function measureMode(thinking: boolean): Promise<{
  thinking: boolean
  ttftSamples: number[]
  totalSamples: number[]
  error: string | null
}> {
  const ttftSamples: number[] = []
  const totalSamples: number[] = []
  let lastError: string | null = null
  for (let i = 0; i < SAMPLES; i++) {
    try {
      const r = await measureOnce(thinking)
      ttftSamples.push(r.ttftMs)
      totalSamples.push(r.totalMs)
      console.log(
        `  sample ${i + 1}/${SAMPLES} mode=${thinking ? 'thinking' : 'flash'} ttft=${r.ttftMs}ms total=${r.totalMs}ms`
      )
    } catch (e) {
      lastError = e instanceof Error ? e.message : String(e)
      console.log(`  sample ${i + 1}/${SAMPLES} mode=${thinking ? 'thinking' : 'flash'} FAILED: ${lastError}`)
    }
    await new Promise((r) => setTimeout(r, 400))
  }
  return { thinking, ttftSamples, totalSamples, error: lastError }
}

async function main() {
  const artifact = {
    measuredAt: new Date().toISOString(),
    protocol: { prompt: PROMPT, samplesPerMode: SAMPLES, metric: 'ttft = time to first streamed delta (ms)' },
    provider: 'zai (z-ai-web-dev-sdk, real production SDK)',
    flash: null as ReturnType<typeof measureMode> | null,
    thinking: null as ReturnType<typeof measureMode> | null,
    p50: { flash: null as number | null, thinking: null as number | null },
    p95: { flash: null as number | null, thinking: null as number | null },
    openrouter: {
      model: OR_MODEL,
      flash: null as ReturnType<typeof measureOpenRouterMode> | null,
      thinking: null as ReturnType<typeof measureOpenRouterMode> | null,
      p50: { flash: null as number | null, thinking: null as number | null },
      p95: { flash: null as number | null, thinking: null as number | null },
    },
    notes: [] as string[],
  }

  console.log('measuring FLASH (thinking disabled)…')
  artifact.flash = await measureMode(false)
  console.log('measuring THINKING (enabled, reasoning_effort=high)…')
  artifact.thinking = await measureMode(true)

  artifact.p50.flash = percentile(artifact.flash.ttftSamples, 50)
  artifact.p95.flash = percentile(artifact.flash.ttftSamples, 95)
  artifact.p50.thinking = percentile(artifact.thinking.ttftSamples, 50)
  artifact.p95.thinking = percentile(artifact.thinking.ttftSamples, 95)

  if (artifact.flash.error) artifact.notes.push(`flash-mode error class: ${artifact.flash.error}`)
  if (artifact.thinking.error) artifact.notes.push(`thinking-mode error class: ${artifact.thinking.error}`)
  if (artifact.flash.ttftSamples.length === 0 || artifact.thinking.ttftSamples.length === 0) {
    artifact.notes.push('INCOMPLETE RUN — one or both modes returned zero successful samples (provider quota/availability). Numbers in model-capabilities.ts carry the last COMPLETE run until re-measured.')
  }

  // OpenRouter free chain — same protocol, quota-independent keys.
  try {
    const pool = await loadKeyPool()
    if (pool.keys.length > 0) {
      console.log(`measuring OPENROUTER ${OR_MODEL} (${pool.keys.length} keys)…`)
      artifact.openrouter.flash = await measureOpenRouterMode(pool.keys, false)
      artifact.openrouter.thinking = await measureOpenRouterMode(pool.keys, true)
      const orFlash = artifact.openrouter.flash
      const orThinking = artifact.openrouter.thinking
      artifact.openrouter.p50.flash = percentile(orFlash.ttftSamples, 50)
      artifact.openrouter.p95.flash = percentile(orFlash.ttftSamples, 95)
      artifact.openrouter.p50.thinking = percentile(orThinking.ttftSamples, 50)
      artifact.openrouter.p95.thinking = percentile(orThinking.ttftSamples, 95)
    } else {
      artifact.notes.push('OpenRouter key pool empty — free-chain measurement skipped.')
    }
  } catch (e) {
    artifact.notes.push(`OpenRouter measurement failed: ${e instanceof Error ? e.message : String(e)}`)
  }

  writeFileSync('tests/thinking-latency-measurements.json', JSON.stringify(artifact, null, 2))
  console.log('\nartifact written: tests/thinking-latency-measurements.json')
  console.log(`p50 TTFT zai flash=${artifact.p50.flash}ms thinking=${artifact.p50.thinking}ms`)
  console.log(
    `p50 TTFT openrouter flash=${artifact.openrouter.p50.flash}ms thinking=${artifact.openrouter.p50.thinking}ms`
  )
}

main().catch((e) => {
  console.error('measurement run failed:', e)
  process.exit(1)
})
