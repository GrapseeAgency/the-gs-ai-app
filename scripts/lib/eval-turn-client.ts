/**
 * EVAL INFRASTRUCTURE — PRODUCTION-PATH TURN CLIENT (shared).
 *
 * The EXACT production-path helpers used by the Layer-4 suite runner
 * (scripts/run-eval-suite.ts), extracted verbatim so the LLM-judge runner
 * (scripts/run-register-judge.ts) exercises the identical wire path:
 *   1. POST /api/v1/conversations                     (fresh conversation)
 *   2. POST /api/v1/conversations/:id/messages        (SSE streaming)
 *   3. X-GS-Request-Id capture
 *   4. persisted-answer recovery (30s proxy cap on SSE views — audit [4])
 *   5. structured trace row read from the `turns` table (Layer 1)
 *
 * NO behavior change from the Phase-3 runner code — moved, not rewritten.
 */

import { Database } from 'bun:sqlite'
import path from 'node:path'

const ROOT = path.resolve(import.meta.dir, '..', '..')

interface ClientConfig {
  origin: string
  appVersion: string
  dbPath: string
}

const cfg: ClientConfig = {
  origin: 'http://localhost:3000',
  appVersion: '0.68.1',
  dbPath: path.join(ROOT, 'db', 'custom.db'),
}

/** Point the client at a specific origin/app version before running cases. */
export function initTurnClient(overrides: Partial<ClientConfig>): void {
  Object.assign(cfg, overrides)
}

export function turnClientConfig(): ClientConfig {
  return { ...cfg }
}

// --- production-path turn -----------------------------------------------------

export interface TurnResult {
  requestId: string | null
  answer: string
  done: boolean
  errorEvent: string | null
  events: string[]
  httpError: string | null
  /** FLASH MODE (Phase 6) — runner-measured ms to the first streamed delta. */
  ttftMs: number | null
}

export function sseTurn(conv: string, message: string, mode?: string | null): Promise<TurnResult> {
  return new Promise((resolve) => {
    const result: TurnResult = { requestId: null, answer: '', done: false, errorEvent: null, events: [], httpError: null, ttftMs: null }
    const startedAt = Date.now()
    fetch(`${cfg.origin}/api/v1/conversations/${conv}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-gs-app-version': cfg.appVersion },
      body: JSON.stringify({
        content: message,
        stream: true,
        timezone: 'Asia/Dhaka',
        // FLASH MODE (Phase 6) — per-case mode; undefined omits the field
        // (the backend default 'flash' applies).
        ...(mode ? { mode } : {}),
      }),
      signal: AbortSignal.timeout(240_000),
    })
      .then((res) => {
        result.requestId = res.headers.get('x-gs-request-id')
        if (!res.ok || !res.body) {
          result.httpError = `HTTP ${res.status}`
          res.text().then(() => resolve(result)).catch(() => resolve(result))
          return
        }
        const reader = res.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        const pump = (): void => {
          reader
            .read()
            .then(({ done, value }) => {
              if (done) {
                resolve(result)
                return
              }
              buffer += decoder.decode(value, { stream: true })
              const lines = buffer.split('\n')
              buffer = lines.pop() ?? ''
              for (const line of lines) {
                const trimmed = line.trim()
                if (!trimmed.startsWith('data:')) continue
                try {
                  const ev = JSON.parse(trimmed.slice(5).trim()) as { event: string; data: unknown }
                  result.events.push(ev.event)
                  if (ev.event === 'delta' && typeof ev.data === 'string') {
                    // FLASH MODE (Phase 6) — runner-measured TTFT (first delta).
                    if (result.ttftMs === null) result.ttftMs = Date.now() - startedAt
                    result.answer += ev.data
                  } else if (ev.event === 'done' && typeof ev.data === 'string') {
                    result.done = true
                    try {
                      const msg = JSON.parse(ev.data) as { content?: string }
                      if (msg.content) result.answer = msg.content
                    } catch { /* keep deltas */ }
                  } else if (ev.event === 'error') {
                    result.errorEvent = String(ev.data)
                  }
                } catch { /* non-JSON keep-alive comment */ }
              }
              pump()
            })
            .catch((e: unknown) => {
              // View cut (proxy cap / abort) — the detached turn continues
              // server-side; the caller polls for the persisted answer.
              result.httpError = e instanceof Error ? e.message : String(e)
              resolve(result)
            })
        }
        pump()
      })
      .catch((e: unknown) => {
        result.httpError = e instanceof Error ? e.message : String(e)
        resolve(result)
      })
  })
}

export async function newConversation(title: string): Promise<string> {
  const res = await fetch(`${cfg.origin}/api/v1/conversations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-gs-app-version': cfg.appVersion },
    body: JSON.stringify({ title }),
    signal: AbortSignal.timeout(30_000),
  })
  if (!res.ok) throw new Error(`conversation create failed: HTTP ${res.status}`)
  const body = (await res.json()) as { id: string }
  return body.id
}

export interface MessagesState {
  assistantCount: number
  lastAssistant: string | null
}

export async function fetchMessages(conv: string): Promise<MessagesState | null> {
  try {
    const res = await fetch(`${cfg.origin}/api/v1/conversations/${conv}/messages`, {
      headers: { 'x-gs-app-version': cfg.appVersion },
      signal: AbortSignal.timeout(30_000),
    })
    if (!res.ok) return null
    const messages = (await res.json()) as { role: string; content: string }[]
    const assistants = messages.filter((m) => m.role === 'assistant')
    return {
      assistantCount: assistants.length,
      lastAssistant: assistants.length > 0 ? assistants[assistants.length - 1].content : null,
    }
  } catch {
    return null
  }
}

/**
 * Persisted-answer recovery, settlement-safe: poll until the conversation
 * holds at least `expectedAssistants` assistant messages (setup turns + this
 * turn) so a cut view NEVER gets graded against the PREVIOUS turn's answer.
 */
export async function refetchSettledAnswer(conv: string, expectedAssistants: number, timeoutMs = 120_000): Promise<{ answer: string | null; settled: boolean }> {
  const deadline = Date.now() + timeoutMs
  let last: MessagesState | null = null
  while (Date.now() < deadline) {
    last = await fetchMessages(conv)
    if (last && last.assistantCount >= expectedAssistants && last.lastAssistant) {
      return { answer: last.lastAssistant, settled: true }
    }
    await new Promise((r) => setTimeout(r, 3_000))
  }
  return { answer: last?.lastAssistant ?? null, settled: false }
}

// --- trace read (Layer 1) ------------------------------------------------------

export interface TraceRow {
  requestId: string
  capability: string
  trigger: string
  searchExecuted: number
  sourceCount: number
  sourcesRead: number
  sourcesFailed: number
  domains: string
  modelRoute: string
  finalStatus: string
  cited: string
  latencyMs: number
  errorType: string | null
  // FLASH MODE (Phase 6) — mode columns (turns table).
  requestedMode?: string
  effectiveMode?: string
  thinkingLatencyMs?: number | null
  modeNote?: string | null
  /** Runner-measured (not a stored column) — injected by runCase before grading. */
  ttftMs?: number | null
}

export async function readTrace(requestId: string | null): Promise<TraceRow | null> {
  if (!requestId) return null
  const db = new Database(cfg.dbPath, { readonly: true })
  try {
    // The dev server's Prisma connection holds short write locks; a bare
    // readonly open fails fast with SQLITE_BUSY under contention. Wait for
    // writers instead of crashing the whole run (observed live at R08).
    try {
      db.run('PRAGMA busy_timeout = 10000')
    } catch { /* older bun: pragma may be unsupported — retry loop below still applies */ }
    for (let i = 0; i < 20; i++) {
      for (let attempt = 0; attempt < 6; attempt++) {
        try {
          const row = db
            .query('SELECT requestId, capability, trigger, searchExecuted, sourceCount, sourcesRead, sourcesFailed, domains, modelRoute, finalStatus, cited, latencyMs, errorType, requestedMode, effectiveMode, thinkingLatencyMs, modeNote FROM turns WHERE requestId = ?')
            .get(requestId) as TraceRow | undefined
          if (row) return row
          break
        } catch (e) {
          const code = (e as { code?: string }).code
          if (code !== 'SQLITE_BUSY') throw e
          await new Promise((r) => setTimeout(r, 1_000))
        }
      }
      await new Promise((r) => setTimeout(r, 500)) // trace write is fire-and-forget
    }
    return null
  } finally {
    db.close()
  }
}
