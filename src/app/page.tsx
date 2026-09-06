'use client'

import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * GS AI — Platform Command Centre (web preview)
 * Aeruo Kinetic design language: obsidian surfaces, aurora accent,
 * serif editorial display. This page exercises the SAME live API
 * contract (shared-contracts/openapi.yaml) that the Android/iOS
 * clients consume — including real SSE streaming.
 */

const C = {
  bg: '#0A0D12',
  surface: '#11151C',
  raised: '#181E28',
  container: '#141923',
  text: '#EDEFF2',
  muted: '#8B93A1',
  outline: '#232B37',
  accent: '#2DD4A8',
  accentDeep: '#0FA37E',
} as const

const AURORA = 'linear-gradient(90deg, #2DD4A8, #4CC3FF, #9D7BFF)'

type ModelInfo = {
  id: string
  displayName: string
  capabilities: string[]
  contextWindow?: number
  speedTier?: string
}

type ConversationInfo = {
  id: string
  title: string
  updatedAt: string
}

type ChatMessage = { role: 'user' | 'assistant'; content: string }

function speedDots(tier?: string) {
  const filled = tier === 'fast' ? 3 : tier === 'balanced' ? 2 : 1
  return (
    <span className="inline-flex gap-1" aria-label={`speed ${tier ?? 'unknown'}`}>
      {[1, 2, 3].map((i) => (
        <span
          key={i}
          className="h-1.5 w-1.5 rounded-full"
          style={{ background: i <= filled ? C.accent : C.outline }}
        />
      ))}
    </span>
  )
}

function StatCard({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div
      className="rounded-2xl border p-4"
      style={{ background: C.surface, borderColor: C.outline }}
    >
      <p className="text-xs font-medium uppercase tracking-wider" style={{ color: C.muted }}>
        {label}
      </p>
      <p className="mt-1 font-serif text-2xl font-semibold" style={{ color: C.text }}>
        {value}
      </p>
      {sub && (
        <p className="mt-0.5 text-xs" style={{ color: C.muted }}>
          {sub}
        </p>
      )}
    </div>
  )
}

export default function Home() {
  const [health, setHealth] = useState<'checking' | 'ok' | 'down'>('checking')
  const [models, setModels] = useState<ModelInfo[]>([])
  const [conversations, setConversations] = useState<ConversationInfo[]>([])
  const [conversationId, setConversationId] = useState<string | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [draft, setDraft] = useState('')
  const [streamText, setStreamText] = useState('')
  const [isStreaming, setIsStreaming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const abortRef = useRef<AbortController | null>(null)
  const scrollRef = useRef<HTMLDivElement | null>(null)

  const loadStatus = useCallback(async () => {
    try {
      const [h, m, c] = await Promise.all([
        fetch('/api/health').then((r) => r.json()),
        fetch('/api/v1/models').then((r) => r.json()),
        fetch('/api/v1/conversations?limit=8').then((r) => r.json()),
      ])
      setHealth(h?.status === 'ok' ? 'ok' : 'down')
      setModels(m?.models ?? [])
      setConversations(c?.items ?? [])
    } catch {
      setHealth('down')
    }
  }, [])

  useEffect(() => {
    loadStatus()
  }, [loadStatus])

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' })
  }, [messages, streamText])

  const send = useCallback(async () => {
    const content = draft.trim()
    if (!content || isStreaming) return
    setDraft('')
    setError(null)
    setMessages((prev) => [...prev, { role: 'user', content }])
    setIsStreaming(true)
    setStreamText('')

    const controller = new AbortController()
    abortRef.current = controller
    try {
      let convId = conversationId
      if (!convId) {
        const created = await fetch('/api/v1/conversations', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ title: content.slice(0, 40) }),
        }).then((r) => r.json())
        convId = created?.id ?? null
        setConversationId(convId)
      }
      if (!convId) throw new Error('Could not create conversation')

      const res = await fetch(`/api/v1/conversations/${convId}/messages`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content, stream: true }),
        signal: controller.signal,
      })
      if (!res.ok || !res.body) throw new Error(`Backend responded ${res.status}`)

      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let acc = ''
      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() ?? ''
        for (const line of lines) {
          const trimmed = line.trim()
          if (!trimmed.startsWith('data:')) continue
          try {
            const event = JSON.parse(trimmed.slice(5).trim())
            if (event.event === 'delta') {
              acc += event.data ?? ''
              setStreamText(acc)
            } else if (event.event === 'done') {
              const saved = typeof event.data === 'string' ? JSON.parse(event.data) : event.data
              setMessages((prev) => [
                ...prev,
                { role: 'assistant', content: saved?.content ?? acc },
              ])
              setStreamText('')
            } else if (event.event === 'error') {
              throw new Error(event.data || 'Stream error')
            }
          } catch {
            /* keep-alive or partial line — skip */
          }
        }
      }
      if (acc && !streamText) {
        setMessages((prev) => [...prev, { role: 'assistant', content: acc }])
        setStreamText('')
      }
      loadStatus()
    } catch (e) {
      if ((e as Error).name !== 'AbortError') {
        setError(e instanceof Error ? e.message : 'Something went wrong')
      } else if (streamText) {
        setMessages((prev) => [...prev, { role: 'assistant', content: streamText }])
        setStreamText('')
      }
    } finally {
      setIsStreaming(false)
      abortRef.current = null
    }
  }, [conversationId, draft, isStreaming, loadStatus, streamText])

  const stop = useCallback(() => {
    abortRef.current?.abort()
  }, [])

  return (
    <div
      className="flex min-h-screen flex-col"
      style={{ background: C.bg, color: C.text }}
    >
      {/* Header */}
      <header
        className="border-b px-6 py-5"
        style={{ borderColor: C.outline, background: C.surface }}
      >
        <div className="mx-auto flex max-w-6xl items-center gap-4">
          <div className="flex h-11 w-11 items-center justify-center rounded-2xl font-serif text-lg font-semibold"
            style={{ background: C.container, color: C.accent }}>
            G
          </div>
          <div className="flex-1">
            <h1 className="font-serif text-xl font-semibold leading-tight">GS AI</h1>
            <p className="text-xs" style={{ color: C.muted }}>
              Grapsee Agency · platform command centre
            </p>
          </div>
          <span
            className="inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-medium"
            style={{
              borderColor: health === 'ok' ? C.accent : C.outline,
              color: health === 'ok' ? C.accent : C.muted,
              background: health === 'ok' ? 'rgba(45,212,168,0.08)' : 'transparent',
            }}
          >
            <span
              className="h-2 w-2 rounded-full"
              style={{ background: health === 'ok' ? C.accent : C.muted }}
            />
            {health === 'ok' ? 'API live' : health === 'checking' ? 'Checking…' : 'API down'}
          </span>
        </div>
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-6 py-8">
        {/* Greeting */}
        <section className="mb-8">
          <h2 className="font-serif text-4xl font-semibold">Good day</h2>
          <p className="mt-1 text-sm" style={{ color: C.muted }}>
            One platform · two native clients · one contract. This page drives the same
            API your Android and iOS apps use.
          </p>
        </section>

        {/* Stats */}
        <section className="mb-8 grid grid-cols-2 gap-4 lg:grid-cols-4">
          <StatCard label="Models" value={String(models.length)} sub="GS catalogue" />
          <StatCard label="Conversations" value={String(conversations.length)} sub="recent" />
          <StatCard label="Streaming" value="SSE" sub="stop-generation supported" />
          <StatCard label="Clients" value="2" sub="Android · iOS (Aeruo Kinetic)" />
        </section>

        <div className="grid gap-6 lg:grid-cols-5">
          {/* Left: models + conversations */}
          <section className="space-y-6 lg:col-span-2">
            <div>
              <h3 className="mb-3 font-serif text-lg font-semibold">Model catalogue</h3>
              <div className="space-y-2">
                {models.map((m) => (
                  <div
                    key={m.id}
                    className="flex items-center gap-3 rounded-xl border px-4 py-3"
                    style={{ background: C.container, borderColor: C.outline }}
                  >
                    <span
                      className="flex h-8 w-8 items-center justify-center rounded-full text-xs font-semibold"
                      style={{ background: C.raised, color: C.accent }}
                    >
                      {m.displayName.split(' ')[1]?.[0] ?? 'G'}
                    </span>
                    <div className="flex-1">
                      <p className="text-sm font-semibold">{m.displayName}</p>
                      <p className="text-xs" style={{ color: C.muted }}>
                        {m.contextWindow ? `${Math.round(m.contextWindow / 1000)}K context · ` : ''}
                        {m.capabilities?.join(' · ')}
                      </p>
                    </div>
                    {speedDots(m.speedTier)}
                  </div>
                ))}
                {models.length === 0 && (
                  <p className="text-sm" style={{ color: C.muted }}>
                    Loading catalogue…
                  </p>
                )}
              </div>
            </div>

            <div>
              <h3 className="mb-3 font-serif text-lg font-semibold">Recent conversations</h3>
              <div className="space-y-2">
                {conversations.map((c) => (
                  <button
                    key={c.id}
                    onClick={() => {
                      setConversationId(c.id)
                      setMessages([])
                      setError(null)
                    }}
                    className="w-full rounded-xl border px-4 py-3 text-left transition-transform active:scale-[0.98]"
                    style={{ background: C.container, borderColor: C.outline }}
                  >
                    <p className="truncate text-sm font-medium">{c.title}</p>
                    <p className="text-xs" style={{ color: C.muted }}>
                      {new Date(c.updatedAt).toLocaleString()}
                    </p>
                  </button>
                ))}
                {conversations.length === 0 && (
                  <p className="text-sm" style={{ color: C.muted }}>
                    No conversations yet — say hello on the right.
                  </p>
                )}
              </div>
            </div>
          </section>

          {/* Right: live chat */}
          <section
            className="flex flex-col rounded-2xl border lg:col-span-3"
            style={{ background: C.surface, borderColor: C.outline }}
          >
            <div
              className="flex items-center gap-2 border-b px-5 py-4"
              style={{ borderColor: C.outline }}
            >
              <span className="font-serif text-lg font-semibold">Live streaming chat</span>
              {conversationId && (
                <span
                  className="rounded-full px-2 py-0.5 text-[10px] font-medium"
                  style={{ background: C.container, color: C.muted }}
                >
                  {conversationId.slice(0, 10)}…
                </span>
              )}
              <div className="flex-1" />
              {isStreaming && (
                <button
                  onClick={stop}
                  className="rounded-full border px-3 py-1 text-xs font-medium transition-transform active:scale-95"
                  style={{ borderColor: C.accent, color: C.accent }}
                >
                  Stop generating
                </button>
              )}
            </div>

            <div ref={scrollRef} className="flex-1 space-y-4 overflow-y-auto p-5" style={{ maxHeight: 420 }}>
              {messages.length === 0 && !streamText && (
                <div className="py-10 text-center">
                  <div
                    className="mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-full"
                    style={{ background: C.container }}
                  >
                    <span style={{ color: C.accent }}>✦</span>
                  </div>
                  <p className="font-serif text-lg">Ask anything</p>
                  <p className="mt-1 text-sm" style={{ color: C.muted }}>
                    Responses stream token-by-token from the same orchestrator the native apps use.
                  </p>
                </div>
              )}
              {messages.map((m, i) => (
                <div key={i} className={m.role === 'user' ? 'flex justify-end' : 'flex justify-start'}>
                  <div
                    className="max-w-[85%] rounded-2xl px-4 py-3 text-sm leading-relaxed whitespace-pre-wrap"
                    style={
                      m.role === 'user'
                        ? { background: 'rgba(45,212,168,0.12)', color: C.text }
                        : { background: C.raised, color: C.text, border: `1px solid ${C.outline}` }
                    }
                  >
                    {m.content}
                  </div>
                </div>
              ))}
              {streamText && (
                <div className="flex justify-start">
                  <div
                    className="max-w-[85%] rounded-2xl px-4 py-3 text-sm leading-relaxed whitespace-pre-wrap"
                    style={{ background: C.raised, color: C.text, border: `1px solid ${C.outline}` }}
                  >
                    {streamText}
                    <span className="ml-0.5 inline-block h-4 w-0.5 animate-pulse align-middle"
                      style={{ background: C.accent }} />
                  </div>
                </div>
              )}
              {isStreaming && !streamText && (
                <div className="flex justify-start">
                  <div className="h-2 w-40 animate-pulse rounded-full" style={{ background: AURORA }} />
                </div>
              )}
              {error && (
                <div
                  className="rounded-xl border px-4 py-3 text-sm"
                  style={{ borderColor: '#E5484D', color: '#E5484D', background: 'rgba(229,72,77,0.06)' }}
                >
                  {error}
                </div>
              )}
            </div>

            <div className="border-t p-4" style={{ borderColor: C.outline }}>
              <div
                className="flex items-center gap-2 rounded-full border px-4 py-2"
                style={{ background: C.container, borderColor: C.outline }}
              >
                <span style={{ color: C.accent }}>✦</span>
                <input
                  value={draft}
                  onChange={(e) => setDraft(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && !e.shiftKey) {
                      e.preventDefault()
                      send()
                    }
                  }}
                  placeholder="Ask anything…"
                  className="flex-1 bg-transparent text-sm outline-none"
                  style={{ color: C.text }}
                  aria-label="Message GS AI"
                />
                <button
                  onClick={send}
                  disabled={!draft.trim() || isStreaming}
                  className="rounded-full px-3 py-1.5 text-xs font-semibold transition-all active:scale-95 disabled:opacity-40"
                  style={{ background: C.accent, color: '#06231C' }}
                >
                  Send
                </button>
              </div>
            </div>
          </section>
        </div>
      </main>

      <footer className="mt-auto border-t px-6 py-5" style={{ borderColor: C.outline }}>
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-2 text-xs"
          style={{ color: C.muted }}>
          <span>© Grapsee Agency — The GS AI App</span>
          <span>
            Aeruo Kinetic · obsidian surfaces · aurora accent · one contract, two native clients
          </span>
        </div>
      </footer>
    </div>
  )
}
