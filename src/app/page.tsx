'use client'

import { useCallback, useEffect, useRef, useState, type MouseEvent as ReactMouseEvent, type ReactNode } from 'react'

/**
 * GS AI — Platform Command Centre (web preview)
 * Aeruo Kinetic design language: obsidian surfaces, aurora accent,
 * serif editorial display. This page exercises the SAME live API
 * contract (shared-contracts/openapi.yaml) that the Android/iOS
 * clients consume — including real SSE streaming, the assistant
 * catalogue, personas and per-conversation controls.
 *
 * PHASE 8.1 — REAL RESEARCH TRACE. The page now renders the backend's
 * actual search events (search.started → query → results → source
 * opening/reading/completed/failed → composing): every visible research
 * step corresponds 1:1 to a real backend action (§31 — no fake visuals,
 * no timers). Source cards open the real canonical URLs; [N] citation
 * markers in answers are tappable and resolve to the persisted source;
 * ambiguous news requests surface the backend's native quick choices.
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
  danger: '#E5484D',
} as const

const AURORA = 'linear-gradient(90deg, #2DD4A8, #4CC3FF, #9D7BFF)'

type ConversationInfo = {
  id: string
  title: string
  modelId?: string
  pinned: boolean
  archived: boolean
  assistantId?: string
  assistantName?: string
  updatedAt: string
}

type AssistantInfo = {
  id: string
  slug: string
  name: string
  description: string
  category: string
  starters: string[]
  published: boolean
  favourite: boolean
  uses: number
  rating: number
}

/* ---------- PHASE 8.1 research-trace types (wire: docs/search-event-protocol.md) ---------- */

type SourceCard = {
  ordinal: number
  title: string
  url: string
  domain: string
  snippet?: string
  publishedDate?: string | null
  status?: string
  used?: boolean
}

type TraceStep = { id: string; icon: string; text: string; tone?: 'ok' | 'fail' | 'live' }

// PHASE 8.2 §16 — engine display names for the per-engine trace rows.
const ENGINE_LABELS: Record<string, string> = {
  'bing-news-rss': 'Bing News',
  'google-news-rss': 'Google News',
  'bing-web': 'Bing Web',
  'duckduckgo-lite': 'DuckDuckGo',
  wikipedia: 'Wikipedia',
  searxng: 'SearXNG',
  'z-ai': 'GS Web',
  openlibrary: 'Open Library',
  gutenberg: 'Gutenberg',
  arxiv: 'arXiv',
  crossref: 'Crossref',
}

type ClarifyChoices = { question: string; options: { id: string; label: string }[] }

type ChatMessage = {
  role: 'user' | 'assistant'
  content: string
  sources?: SourceCard[]
  traceSummary?: string
  clarifyOptions?: ClarifyChoices | null
}

/* ---------- small helpers ---------- */

function relTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime()
  const m = Math.floor(diff / 60_000)
  if (m < 1) return 'now'
  if (m < 60) return `${m}m ago`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}h ago`
  const d = Math.floor(h / 24)
  return `${d}d ago`
}

function greet(): string {
  const h = new Date().getHours()
  if (h < 5) return 'Working late'
  if (h < 12) return 'Good morning'
  if (h < 18) return 'Good afternoon'
  return 'Good evening'
}

function fmtUses(n: number): string {
  return n >= 1000 ? `${(n / 1000).toFixed(n >= 10_000 ? 0 : 1)}k` : String(n)
}

function domainInitial(domain: string): string {
  return (domain.replace(/^www\./, '')[0] ?? 'W').toUpperCase()
}

function shortDate(iso?: string | null): string | null {
  if (!iso) return null
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return null
  return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}

const CATEGORY_LABELS: Record<string, string> = {
  research: 'Research',
  writing: 'Writing',
  coding: 'Coding',
  productivity: 'Productivity',
  learning: 'Learning',
  general: 'General',
}

/* ---------- PHASE 8.1 building blocks ---------- */

/** One real research step — rendered verbatim from backend events. */
function SearchTracePanel({ steps, phase }: { steps: TraceStep[]; phase: string | null }) {
  const phaseLabel =
    phase === 'searching'
      ? 'Searching the web'
      : phase === 'working'
        ? 'Reading sources'
        : phase === 'search_failed'
          ? 'Search failed'
          : phase === 'composing'
            ? 'Composing'
            : null
  return (
    <div
      className="rounded-xl border px-3.5 py-3"
      style={{ background: C.container, borderColor: C.outline }}
      aria-label="Live research trace"
      aria-live="polite"
    >
      <div className="mb-2 flex items-center gap-2">
        <span
          className="inline-block h-1.5 w-1.5 animate-pulse rounded-full"
          style={{ background: C.accent }}
        />
        <span className="text-[11px] font-semibold uppercase tracking-wider" style={{ color: C.accent }}>
          {phaseLabel ?? 'Researching'}
        </span>
      </div>
      <ul className="space-y-1.5">
        {steps.map((s) => (
          <li key={s.id} className="flex items-start gap-2 text-xs leading-relaxed" style={{ color: s.tone === 'fail' ? C.danger : s.tone === 'ok' ? C.text : C.muted }}>
            <span className="mt-[1px] w-3.5 shrink-0 text-center" style={{ color: s.tone === 'fail' ? C.danger : C.accent }} aria-hidden="true">
              {s.icon}
            </span>
            <span className="min-w-0 flex-1">
              <span className="font-medium">{s.text}</span>
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}

/** Compact source card — favicon when reachable, monogram always. */
function SourceCardView({ s }: { s: SourceCard }) {
  const [imgOk, setImgOk] = useState(true)
  const cited = s.used !== false
  return (
    <a
      href={s.url}
      target="_blank"
      rel="noopener noreferrer"
      className="gs-card block w-full min-w-0 rounded-xl border p-3"
      style={{
        background: C.container,
        borderColor: cited ? C.outline : C.outline,
        opacity: cited ? 1 : 0.75,
      }}
      aria-label={`Source ${s.ordinal}: ${s.title} (${s.domain})`}
    >
      <div className="flex items-start gap-2.5">
        <span
          className="relative flex h-8 w-8 shrink-0 items-center justify-center overflow-hidden rounded-lg text-[11px] font-semibold"
          style={{ background: C.raised, color: C.accent }}
        >
          {imgOk && (
            <img
              src={`https://icons.duckduckgo.com/ip3/${s.domain}.ico`}
              alt=""
              width={16}
              height={16}
              className="absolute inset-0 m-auto"
              onError={() => setImgOk(false)}
            />
          )}
          <span aria-hidden={imgOk}>{domainInitial(s.domain)}</span>
        </span>
        <span className="min-w-0 flex-1">
          <span className="flex items-center gap-1.5">
            <span
              className="flex h-4 w-4 shrink-0 items-center justify-center rounded text-[9px] font-bold"
              style={{ background: 'rgba(45,212,168,0.14)', color: C.accent }}
            >
              {s.ordinal}
            </span>
            <span className="truncate text-[11px] font-medium" style={{ color: C.muted }}>
              {s.domain}
              {shortDate(s.publishedDate) ? ` · ${shortDate(s.publishedDate)}` : ''}
            </span>
          </span>
          <span className="mt-0.5 line-clamp-2 block text-xs leading-snug" style={{ color: C.text }}>
            {s.title}
          </span>
          {s.status === 'snippet_only' && (
            <span className="mt-0.5 block text-[10px]" style={{ color: C.muted }}>
              headline only — not retrieved
            </span>
          )}
        </span>
      </div>
    </a>
  )
}

function SourceCards({ sources }: { sources: SourceCard[] }) {
  if (sources.length === 0) return null
  return (
    <div className="mt-2">
      <p className="mb-1.5 text-[10px] font-semibold uppercase tracking-wider" style={{ color: C.muted }}>
        Sources · {sources.filter((s) => s.used !== false).length} used
      </p>
      <div className="grid gap-2 sm:grid-cols-2">
        {sources.map((s) => (
          <SourceCardView key={`${s.ordinal}-${s.url}`} s={s} />
        ))}
      </div>
    </div>
  )
}

/**
 * Answer text with tappable [N] citations. A marker resolves ONLY to a
 * persisted source with that ordinal — unmatched markers stay plain text
 * (the server already strips dead ones before persistence).
 */
function AnswerText({ text, sources }: { text: string; sources?: SourceCard[] }) {
  const nodes: ReactNode[] = []
  const re = /[\[【〚［]\s*(\d{1,2})\s*[\]】〛］]/g
  let last = 0
  let key = 0
  for (const m of text.matchAll(re)) {
    const idx = m.index ?? 0
    if (idx > last) nodes.push(<span key={`t${key++}`}>{text.slice(last, idx)}</span>)
    const n = Number.parseInt(m[1], 10)
    const src = sources?.find((s) => s.ordinal === n)
    if (src) {
      nodes.push(
        <button
          key={`c${key++}`}
          onClick={() => window.open(src.url, '_blank', 'noopener,noreferrer')}
          aria-label={`Open citation ${n}: ${src.title}`}
          title={`${src.title} — ${src.domain}`}
          className="mx-0.5 inline-flex h-4 min-w-4 cursor-pointer items-center justify-center rounded px-1 align-[2px] text-[10px] font-bold transition-colors hover:brightness-125"
          style={{ background: 'rgba(45,212,168,0.16)', color: C.accent }}
        >
          {n}
        </button>
      )
    } else {
      nodes.push(<span key={`c${key++}`}>{m[0]}</span>)
    }
    last = idx + m[0].length
  }
  if (last < text.length) nodes.push(<span key={`t${key++}`}>{text.slice(last)}</span>)
  return <>{nodes}</>
}

/** Native quick choices for an ambiguous news request (§6) — conversation controls, not screens. */
function ClarifyChips({ clarify, onPick }: { clarify: ClarifyChoices; onPick: (label: string) => void }) {
  return (
    <div className="flex justify-start">
      <div
        className="max-w-[92%] rounded-2xl rounded-bl-md border px-4 py-3"
        style={{ background: C.raised, borderColor: C.outline }}
      >
        <p className="text-sm leading-relaxed">{clarify.question}</p>
        <div className="mt-2.5 flex flex-wrap gap-1.5">
          {clarify.options.map((o) => (
            <button
              key={o.id}
              onClick={() => onPick(o.label)}
              className="rounded-full border px-3 py-1.5 text-xs font-medium transition-all hover:border-[#2DD4A8] active:scale-95"
              style={{ borderColor: C.outline, color: C.text, background: C.container }}
              aria-label={`Ask for ${o.label} news`}
            >
              {o.label}
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}

/* ---------- page ---------- */

export default function Home() {
  const [health, setHealth] = useState<'checking' | 'ok' | 'down'>('checking')
  const [conversations, setConversations] = useState<ConversationInfo[]>([])
  const [assistants, setAssistants] = useState<AssistantInfo[]>([])
  const [category, setCategory] = useState<string>('all')

  const [conversationId, setConversationId] = useState<string | null>(null)
  const [activeAssistant, setActiveAssistant] = useState<{ id: string; name: string } | null>(null)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [draft, setDraft] = useState('')
  const [streamText, setStreamText] = useState('')
  const [isStreaming, setIsStreaming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [copiedIdx, setCopiedIdx] = useState<number | null>(null)
  const [startingChat, setStartingChat] = useState<string | null>(null)

  // PHASE 8.1 live research state — every entry originates from a real SSE event.
  const [trace, setTrace] = useState<TraceStep[]>([])
  const [liveSources, setLiveSources] = useState<SourceCard[]>([])
  const [phase, setPhase] = useState<string | null>(null)
  const [clarify, setClarify] = useState<ClarifyChoices | null>(null)
  const traceIdRef = useRef(0)

  const abortRef = useRef<AbortController | null>(null)
  const accRef = useRef('')
  const scrollRef = useRef<HTMLDivElement | null>(null)
  // Latest `send`, callable from deferred starter sends without stale closures.
  const sendRef = useRef<(override?: string) => void>(() => undefined)

  /* ----- data loading ----- */

  const loadStatus = useCallback(async () => {
    try {
      const [h, c, a] = await Promise.all([
        fetch('/api/health').then((r) => r.json()),
        fetch('/api/v1/conversations?limit=8').then((r) => r.json()),
        fetch('/api/v1/assistants').then((r) => r.json()),
      ])
      setHealth(h?.status === 'ok' ? 'ok' : 'down')
      setConversations(c?.items ?? [])
      setAssistants(a?.items ?? [])
      // ARCHITECTURE LOCK: no model catalogue fetch — the user never sees or
      // selects models; routing is entirely backend-side (GS Router).
    } catch {
      setHealth('down')
    }
  }, [])

  useEffect(() => {
    loadStatus()
  }, [loadStatus])

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' })
  }, [messages, streamText, trace])

  const loadHistory = useCallback(async (id: string) => {
    setHistoryLoading(true)
    try {
      const data = await fetch(`/api/v1/conversations/${id}/messages`).then((r) => r.json())
      const items = (data?.items ?? []) as {
        role: string
        content: string
        sources?: SourceCard[]
        clarifyOptions?: string | null
      }[]
      setMessages(
        items
          .filter((m) => m.role === 'user' || m.role === 'assistant')
          .map((m) => {
            let clarifyChoices: ClarifyChoices | null = null
            if (m.clarifyOptions) {
              try {
                const parsed = JSON.parse(m.clarifyOptions) as ClarifyChoices
                if (parsed?.question && Array.isArray(parsed.options)) clarifyChoices = parsed
              } catch {
                // malformed persisted options — ignore
              }
            }
            return {
              role: m.role as 'user' | 'assistant',
              content: m.content,
              ...(m.sources && m.sources.length > 0 ? { sources: m.sources } : {}),
              ...(clarifyChoices ? { clarifyOptions: clarifyChoices } : {}),
            }
          })
      )
    } catch {
      setError('Could not load this conversation.')
    } finally {
      setHistoryLoading(false)
    }
  }, [])

  const openConversation = useCallback(
    (c: ConversationInfo) => {
      if (isStreaming) abortRef.current?.abort()
      setConversationId(c.id)
      setActiveAssistant(c.assistantId ? { id: c.assistantId, name: c.assistantName ?? 'Assistant' } : null)
      setMessages([])
      setStreamText('')
      setTrace([])
      setLiveSources([])
      setPhase(null)
      setClarify(null)
      setError(null)
      void loadHistory(c.id)
    },
    [isStreaming, loadHistory]
  )

  const newChat = useCallback(() => {
    if (isStreaming) abortRef.current?.abort()
    setConversationId(null)
    setActiveAssistant(null)
    setMessages([])
    setStreamText('')
    setTrace([])
    setLiveSources([])
    setPhase(null)
    setClarify(null)
    setError(null)
  }, [isStreaming])

  const deleteConversation = useCallback(
    (id: string, e: ReactMouseEvent) => {
      e.stopPropagation()
      setConversations((prev) => prev.filter((c) => c.id !== id))
      if (conversationId === id) newChat()
      fetch(`/api/v1/conversations/${id}`, { method: 'DELETE' }).catch(() => undefined)
    },
    [conversationId, newChat]
  )

  const toggleFavourite = useCallback((a: AssistantInfo) => {
    setAssistants((prev) => prev.map((x) => (x.id === a.id ? { ...x, favourite: !x.favourite } : x)))
    fetch(`/api/v1/assistants/${a.id}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ favourite: !a.favourite }),
    }).catch(() => undefined)
  }, [])

  const startAssistantChat = useCallback(
    (a: AssistantInfo, starter?: string) => {
      setStartingChat(a.id)
      fetch('/api/v1/conversations', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title: starter ?? a.name, assistantId: a.id }),
      })
        .then((r) => r.json())
        .then((created) => {
          setConversations((prev) => [
            {
              id: created.id,
              title: created.title ?? a.name,
              pinned: false,
              archived: false,
              assistantId: a.id,
              assistantName: a.name,
              updatedAt: new Date().toISOString(),
            },
            ...prev,
          ])
          setConversationId(created.id)
          setActiveAssistant({ id: a.id, name: a.name })
          setMessages([])
          setStreamText('')
          setTrace([])
          setLiveSources([])
          setPhase(null)
          setClarify(null)
          if (starter) {
            setTimeout(() => sendRef.current(starter), 60)
          }
        })
        .catch(() => setError('Could not start this chat.'))
        .finally(() => setStartingChat(null))
    },
    []
  )

  /* ----- the send path (PHASE 8.1: full research-trace event handling) ----- */

  const send = useCallback(
    async (override?: string) => {
      const text = (override ?? draft).trim()
      if (!text || isStreaming) return
      setDraft('')
      setError(null)
      setTrace([])
      setLiveSources([])
      setPhase(null)
      setClarify(null)
      setMessages((prev) => [...prev, { role: 'user', content: text }])
      setIsStreaming(true)

      const controller = new AbortController()
      abortRef.current = controller
      let convId = conversationId
      const traceSteps: TraceStep[] = []
      const sourceByOrdinal = new Map<number, SourceCard>()
      let traceSummary: string | undefined
      let finalClarify: ClarifyChoices | null = null

      const pushStep = (icon: string, text: string, tone?: TraceStep['tone']) => {
        traceSteps.push({ id: `s${++traceIdRef.current}`, icon, text, tone })
        setTrace([...traceSteps])
      }

      try {
        if (!convId) {
          const created = await fetch('/api/v1/conversations', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ title: text.slice(0, 40) }),
          }).then((r) => r.json())
          convId = created?.id ?? null
          setConversationId(convId)
        }
        if (!convId) throw new Error('Could not create conversation')

        const res = await fetch(`/api/v1/conversations/${convId}/messages`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            content: text,
            stream: true,
          }),
          signal: controller.signal,
        })
        if (!res.ok || !res.body) throw new Error(`Backend responded ${res.status}`)

        const reader = res.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        let acc = ''
        let finalized = false
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
                accRef.current = acc
                setStreamText(acc)
              } else if (event.event === 'status') {
                setPhase(event.data)
                if (event.data === 'search_failed') {
                  pushStep('✕', 'The search could not be completed', 'fail')
                }
              } else if (event.event === 'search') {
                const p = JSON.parse(event.data)
                if (p.type === 'started') {
                  pushStep('◎', `Searching — “${p.label ?? ''}”`)
                } else if (p.type === 'engines') {
                  // 8.2 §16 — per-engine truth (✓ ok · ✕ failed · – empty).
                  for (const e of Array.isArray(p.engines) ? p.engines : []) {
                    const name = ENGINE_LABELS[e.id] ?? e.id
                    if (e.status === 'ok') pushStep('✓', `${name} — ${e.count ?? 0} results`, 'ok')
                    else if (e.status === 'failed') pushStep('✕', `${name} — ${e.error ?? 'failed'}`, 'fail')
                    else pushStep('–', `${name} — no results`, undefined)
                  }
                } else if (p.type === 'results') {
                  pushStep('✓', `Found ${p.found} results${Array.isArray(p.engines) && p.engines.length ? ` · ${p.engines.join(', ')}` : ''}`, 'ok')
                } else if (p.type === 'round') {
                  pushStep('✓', `${p.sourcesVerified} source${p.sourcesVerified === 1 ? '' : 's'} verified${p.syndicatedGroups ? ` · ${p.syndicatedGroups} syndicated group${p.syndicatedGroups === 1 ? '' : 's'}` : ''}`, 'ok')
                } else if (p.type === 'failed') {
                  pushStep('✕', `Search failed — ${p.reason ?? 'unknown'}`, 'fail')
                } else if (p.type === 'completed') {
                  traceSummary = `Searched ${p.queries} ${p.queries === 1 ? 'query' : 'queries'} · read ${p.retrieved} of ${p.sources} sources`
                }
              } else if (event.event === 'research') {
                // 8.2 §28 — research-level truth (protocol v2).
                const p = JSON.parse(event.data)
                if (p.type === 'round_completed') {
                  pushStep('✓', `Round ${p.round}: ${p.read ?? 0} read · ${p.failed ?? 0} failed${p.syndicatedGroups ? ` · ${p.syndicatedGroups} syndicated` : ''}`, 'ok')
                } else if (p.type === 'synthesis_started') {
                  pushStep('✎', 'Composing grounded answer…', 'live')
                } else if (p.type === 'completed') {
                  const secs = p.totalMs ? ` · ${(p.totalMs / 1000).toFixed(1)}s` : ''
                  traceSummary = `Searched ${p.queries} ${p.queries === 1 ? 'query' : 'queries'} · read ${p.retrieved} of ${p.sources} sources${secs}`
                } else if (p.type === 'failed') {
                  const last = traceSteps[traceSteps.length - 1]
                  if (!last || !last.text.startsWith('Search failed')) {
                    pushStep('✕', `Research failed — ${p.reason ?? 'unknown'}`, 'fail')
                  }
                }
              } else if (event.event === 'source') {
                const p = JSON.parse(event.data)
                const ordinal = p.source?.ordinal ?? p.ordinal
                if (p.type === 'discovered' && p.source) {
                  sourceByOrdinal.set(ordinal, p.source as SourceCard)
                  setLiveSources([...sourceByOrdinal.values()].sort((a, b) => a.ordinal - b.ordinal))
                } else if (p.type === 'opening' || p.type === 'reading') {
                  const s = sourceByOrdinal.get(ordinal)
                  pushStep('↗', `Reading ${s?.domain ?? `source ${ordinal}`}…`, 'live')
                } else if (p.type === 'completed' || p.type === 'read') {
                  // protocol v2 — `read` is canonical, `completed` the v1 alias;
                  // both arrive, the transition is idempotent (one step only).
                  const s = sourceByOrdinal.get(ordinal)
                  const alreadyRead = s?.status === 'retrieved'
                  if (s) {
                    s.status = 'retrieved'
                    if (p.publishedDate) s.publishedDate = p.publishedDate
                    setLiveSources([...sourceByOrdinal.values()].sort((a, b) => a.ordinal - b.ordinal))
                  }
                  if (!alreadyRead) {
                    pushStep('✓', `Read ${s?.domain ?? `source ${ordinal}`} — ${(s?.title ?? '').slice(0, 70)}`, 'ok')
                  }
                } else if (p.type === 'failed') {
                  const s = sourceByOrdinal.get(ordinal)
                  pushStep('✕', `${s?.domain ?? `source ${ordinal}`} — ${p.reason ?? 'failed'}`, 'fail')
                }
              } else if (event.event === 'clarify') {
                const p = JSON.parse(event.data)
                if (p?.question && Array.isArray(p.options)) {
                  finalClarify = { question: p.question, options: p.options }
                  setClarify(finalClarify)
                }
              } else if (event.event === 'done') {
                finalized = true
                const saved = typeof event.data === 'string' ? JSON.parse(event.data) : event.data
                const doneSources: SourceCard[] = Array.isArray(saved?.sources)
                  ? saved.sources.map((s: SourceCard) => ({ ...s, used: s.used ?? true }))
                  : [...sourceByOrdinal.values()].sort((a, b) => a.ordinal - b.ordinal)
                // PHASE 8.1 — a clarification turn persists its quick choices;
                // attach them to the rendered message so the chips survive done.
                let doneClarify: ClarifyChoices | null = null
                if (typeof saved?.clarifyOptions === 'string' && saved.clarifyOptions) {
                  try {
                    const parsed = JSON.parse(saved.clarifyOptions) as ClarifyChoices
                    if (parsed?.question && Array.isArray(parsed.options)) doneClarify = parsed
                  } catch {
                    // malformed — ignore
                  }
                }
                setMessages((prev) => [
                  ...prev,
                  {
                    role: 'assistant',
                    content: saved?.content ?? acc,
                    ...(doneSources.length > 0 ? { sources: doneSources } : {}),
                    ...(doneClarify ? { clarifyOptions: doneClarify } : {}),
                    ...(traceSummary ? { traceSummary } : {}),
                  },
                ])
                setStreamText('')
                setTrace([])
                setLiveSources([])
                setPhase(null)
                setClarify(null)
              } else if (event.event === 'error') {
                throw new Error(event.data || 'Stream error')
              }
            } catch {
              /* keep-alive or partial line — skip */
            }
          }
        }
        if (!finalized && convId) {
          // PHASE 8.1 — the turn runs detached server-side (the public
          // origin's outer proxy caps responses at ~30s; a research turn can
          // legitimately outlive that). The view may end without `done` while
          // the server still finishes and persists the answer — poll the
          // conversation briefly and adopt the completed message instead of
          // keeping a partial.
          let adopted = false
          for (let attempt = 0; attempt < 9 && !adopted; attempt++) {
            await new Promise((r) => setTimeout(r, attempt === 0 ? 600 : 4_000))
            try {
              const data = await fetch(`/api/v1/conversations/${convId}/messages`).then((r) => r.json())
              const items = (data?.items ?? []) as {
                role: string
                content: string
                sources?: SourceCard[]
              }[]
              // Our turn's assistant message = the first assistant message
              // after OUR user message (matched by exact content).
              let after = false
              let landed: (typeof items)[number] | null = null
              for (const m of items) {
                if (!after && m.role === 'user' && m.content === text) after = true
                else if (after && m.role === 'assistant' && m.content.trim().length > 0) landed = m
              }
              if (landed) {
                const landedSources: SourceCard[] = Array.isArray(landed.sources)
                  ? landed.sources.map((s) => ({ ...s, used: s.used ?? true }))
                  : []
                setMessages((prev) => [
                  ...prev,
                  {
                    role: 'assistant',
                    content: landed.content,
                    ...(landedSources.length > 0 ? { sources: landedSources } : {}),
                  },
                ])
                adopted = true
              }
            } catch {
              // transient — keep polling
            }
          }
          if (!adopted && acc) {
            setMessages((prev) => [...prev, { role: 'assistant', content: acc }])
          }
          setStreamText('')
          setTrace([])
          setLiveSources([])
          setPhase(null)
          setClarify(null)
        }
        loadStatus()
      } catch (e) {
        if ((e as Error).name !== 'AbortError') {
          setError(e instanceof Error ? e.message : 'Something went wrong')
        } else if (accRef.current) {
          // Stop-generation: keep whatever streamed before the interrupt.
          setMessages((prev) => [...prev, { role: 'assistant', content: accRef.current }])
          setStreamText('')
        }
      } finally {
        setIsStreaming(false)
        abortRef.current = null
      }
    },
    [conversationId, draft, isStreaming, loadStatus]
  )

  // Keep the ref fresh — the deferred starter path always calls the latest send.
  useEffect(() => {
    sendRef.current = send
  }, [send])

  const stop = useCallback(() => {
    abortRef.current?.abort()
  }, [])

  const copyMessage = useCallback((idx: number, text: string) => {
    navigator.clipboard?.writeText(text).then(
      () => {
        setCopiedIdx(idx)
        setTimeout(() => setCopiedIdx((cur) => (cur === idx ? null : cur)), 1500)
      },
      () => undefined
    )
  }, [])

  const visibleAssistants =
    category === 'all' ? assistants : assistants.filter((a) => a.category === category)
  const categories = ['all', ...Array.from(new Set(assistants.map((a) => a.category)))]
  const activeConv = conversations.find((c) => c.id === conversationId)

  return (
    <div className="flex min-h-screen flex-col" style={{ background: C.bg, color: C.text }}>
      <style>{`
        .gs-scroll::-webkit-scrollbar { width: 8px; height: 8px; }
        .gs-scroll::-webkit-scrollbar-thumb { background: ${C.outline}; border-radius: 9999px; }
        .gs-scroll::-webkit-scrollbar-thumb:hover { background: ${C.accentDeep}; }
        .gs-scroll::-webkit-scrollbar-track { background: transparent; }
        .gs-card { transition: transform .18s ease, border-color .18s ease; }
        .gs-card:hover { transform: translateY(-2px); border-color: ${C.accentDeep}; }
        .gs-card .gs-hairline { transform: scaleX(0); transform-origin: left; transition: transform .25s ease; }
        .gs-card:hover .gs-hairline { transform: scaleX(1); }
      `}</style>

      {/* Header */}
      <header
        className="sticky top-0 z-20 border-b px-6 py-5"
        style={{ borderColor: C.outline, background: `${C.surface}F2`, backdropFilter: 'blur(10px)' }}
      >
        <div className="mx-auto flex max-w-6xl items-center gap-4">
          <div
            className="flex h-11 w-11 items-center justify-center rounded-2xl font-serif text-lg font-semibold"
            style={{ background: C.container, color: C.accent }}
          >
            G
          </div>
          <div className="flex-1">
            <h1 className="font-serif text-xl font-semibold leading-tight">GS AI</h1>
            <p className="text-xs" style={{ color: C.muted }}>
              Grapsee Agency · platform command centre
            </p>
          </div>
          <button
            onClick={newChat}
            className="mr-1 hidden rounded-full border px-3.5 py-1.5 text-xs font-medium transition-transform active:scale-95 sm:block"
            style={{ borderColor: C.outline, color: C.text }}
          >
            + New chat
          </button>
          <span
            className="inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-medium"
            style={{
              borderColor: health === 'ok' ? C.accent : C.outline,
              color: health === 'ok' ? C.accent : C.muted,
              background: health === 'ok' ? 'rgba(45,212,168,0.08)' : 'transparent',
            }}
          >
            <span
              className={`h-2 w-2 rounded-full ${health === 'ok' ? 'animate-pulse' : ''}`}
              style={{ background: health === 'ok' ? C.accent : C.muted }}
            />
            {health === 'ok' ? 'API live' : health === 'checking' ? 'Checking…' : 'API down'}
          </span>
        </div>
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-6 py-8">
        {/* Greeting */}
        <section className="mb-8">
          <h2 className="font-serif text-4xl font-semibold">{greet()}</h2>
          <p className="mt-1 text-sm" style={{ color: C.muted }}>
            One platform · two native clients · one contract. This page drives the same
            API your Android and iOS apps use.
          </p>
        </section>

        {/* Stats */}
        <section className="mb-8 grid grid-cols-2 gap-4 lg:grid-cols-4">
          <StatCard label="Conversations" value={String(conversations.length)} sub="recent" />
          <StatCard label="Assistants" value={String(assistants.length)} sub="live catalogue" />
          <StatCard label="Clients" value="2" sub="Android · iOS (Aeruo Kinetic)" />
        </section>

        <div className="grid gap-6 lg:grid-cols-5">
          {/* Left: conversations */}
          <section className="space-y-6 lg:col-span-2">
            <div>
              <SectionHeader title="Recent conversations" hint="click to reopen" />
              <div className="gs-scroll max-h-[22rem] space-y-2 overflow-y-auto pr-1">
                {conversations.map((c) => {
                  const active = c.id === conversationId
                  return (
                    <div key={c.id} className="group relative">
                      <button
                        onClick={() => openConversation(c)}
                        aria-current={active ? 'true' : undefined}
                        className="w-full rounded-xl border px-4 py-3 text-left transition-colors"
                        style={{
                          background: active ? C.raised : C.container,
                          borderColor: active ? C.accentDeep : C.outline,
                          borderLeft: active ? `2px solid ${C.accent}` : `2px solid transparent`,
                        }}
                      >
                        <span className="flex items-center gap-2 pr-7">
                          {c.pinned && (
                            <span
                              className="rounded px-1 py-0.5 text-[9px] font-semibold uppercase tracking-wide"
                              style={{ background: 'rgba(45,212,168,0.12)', color: C.accent }}
                            >
                              pinned
                            </span>
                          )}
                          {c.assistantName && (
                            <span
                              className="truncate rounded px-1 py-0.5 text-[9px] font-semibold uppercase tracking-wide"
                              style={{ background: C.raised, color: C.muted }}
                            >
                              ✦ {c.assistantName}
                            </span>
                          )}
                        </span>
                        <span className="block truncate pr-7 text-sm font-medium">{c.title}</span>
                        <span className="block text-xs" style={{ color: C.muted }}>
                          {relTime(c.updatedAt)}
                        </span>
                      </button>
                      <button
                        onClick={(e) => deleteConversation(c.id, e)}
                        aria-label={`Delete ${c.title}`}
                        className="absolute right-2 top-2.5 hidden h-7 w-7 items-center justify-center rounded-lg border text-xs transition-colors hover:border-[#E5484D] hover:text-[#E5484D] group-hover:flex"
                        style={{ background: C.surface, borderColor: C.outline, color: C.muted }}
                      >
                        ✕
                      </button>
                    </div>
                  )
                })}
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
            <div className="flex items-center gap-2 border-b px-5 py-4" style={{ borderColor: C.outline }}>
              <span className="font-serif text-lg font-semibold">
                {activeConv?.title ?? 'Live streaming chat'}
              </span>
              {activeAssistant && (
                <span
                  className="inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[10px] font-medium"
                  style={{ borderColor: C.accentDeep, color: C.accent, background: 'rgba(45,212,168,0.08)' }}
                >
                  ✦ {activeAssistant.name}
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

            <div
              ref={scrollRef}
              className="gs-scroll flex-1 space-y-4 overflow-y-auto p-5"
              style={{ maxHeight: 520 }}
            >
              {historyLoading && (
                <div className="space-y-3 py-2" aria-label="Loading conversation">
                  {[80, 55, 70].map((w, i) => (
                    <div key={i} className="flex justify-start">
                      <div
                        className="h-9 animate-pulse rounded-2xl"
                        style={{ width: `${w}%`, background: C.raised }}
                      />
                    </div>
                  ))}
                </div>
              )}

              {!historyLoading && messages.length === 0 && !streamText && !isStreaming && (
                <div className="py-10 text-center">
                  <div
                    className="mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-full"
                    style={{ background: C.container }}
                  >
                    <span style={{ color: C.accent }}>✦</span>
                  </div>
                  <p className="font-serif text-lg">
                    {activeAssistant ? `Chatting with ${activeAssistant.name}` : 'Ask anything'}
                  </p>
                  <p className="mt-1 text-sm" style={{ color: C.muted }}>
                    {activeAssistant
                      ? 'This conversation follows the assistant persona — its instructions steer every reply.'
                      : 'Try “today’s technology news” — you will see the real research trace: what was searched, opened, read and cited.'}
                  </p>
                </div>
              )}

              {!historyLoading &&
                messages.map((m, i) =>
                  m.role === 'user' ? (
                    <div key={i} className="flex justify-end">
                      <div
                        className="max-w-[85%] rounded-2xl rounded-br-md px-4 py-3 text-sm leading-relaxed whitespace-pre-wrap"
                        style={{ background: 'rgba(45,212,168,0.12)', color: C.text }}
                      >
                        {m.content}
                      </div>
                    </div>
                  ) : (
                    <div key={i} className="group">
                      <div className="flex justify-start">
                        <div
                          className="max-w-[92%] rounded-2xl rounded-bl-md px-4 py-3 text-sm leading-relaxed whitespace-pre-wrap"
                          style={{
                            background: C.raised,
                            color: C.text,
                            border: `1px solid ${C.outline}`,
                          }}
                        >
                          <AnswerText text={m.content} sources={m.sources} />
                          <span className="mt-2 flex items-center gap-1.5 opacity-0 transition-opacity group-hover:opacity-100">
                            <button
                              onClick={() => copyMessage(i, m.content)}
                              aria-label="Copy reply"
                              className="inline-flex items-center gap-1 rounded-md border px-1.5 py-0.5 text-[10px] font-medium transition-colors"
                              style={{ borderColor: C.outline, color: copiedIdx === i ? C.accent : C.muted }}
                            >
                              <CopyIcon />
                              {copiedIdx === i ? 'Copied' : 'Copy'}
                            </button>
                          </span>
                        </div>
                      </div>
                      {m.traceSummary && (
                        <p className="mt-1.5 pl-1 text-[11px]" style={{ color: C.muted }}>
                          <span style={{ color: C.accent }}>✓</span> {m.traceSummary}
                        </p>
                      )}
                      {m.sources && m.sources.length > 0 && <SourceCards sources={m.sources} />}
                      {m.clarifyOptions && (
                        <div className="mt-2">
                          <ClarifyChips clarify={m.clarifyOptions} onPick={(label) => sendRef.current(label)} />
                        </div>
                      )}
                    </div>
                  )
                )}

              {isStreaming && trace.length > 0 && (
                <SearchTracePanel steps={trace} phase={phase} />
              )}

              {isStreaming && trace.length > 0 && liveSources.length > 0 && (
                <SourceCards sources={liveSources} />
              )}

              {isStreaming && clarify && (
                <ClarifyChips clarify={clarify} onPick={(label) => sendRef.current(label)} />
              )}

              {streamText && (
                <div className="flex justify-start">
                  <div
                    className="max-w-[92%] rounded-2xl rounded-bl-md px-4 py-3 text-sm leading-relaxed whitespace-pre-wrap"
                    style={{ background: C.raised, color: C.text, border: `1px solid ${C.outline}` }}
                  >
                    {streamText}
                    <span
                      className="ml-0.5 inline-block h-4 w-0.5 animate-pulse align-middle"
                      style={{ background: C.accent }}
                    />
                  </div>
                </div>
              )}

              {isStreaming && !streamText && trace.length === 0 && (
                <div className="flex justify-start">
                  <div className="h-2 w-40 animate-pulse rounded-full" style={{ background: AURORA }} />
                </div>
              )}

              {error && (
                <div
                  className="rounded-xl border px-4 py-3 text-sm"
                  style={{
                    borderColor: C.danger,
                    color: C.danger,
                    background: 'rgba(229,72,77,0.06)',
                  }}
                  role="alert"
                >
                  {error}
                </div>
              )}
            </div>

            {/* Composer */}
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
                  placeholder={activeAssistant ? `Message ${activeAssistant.name}…` : 'Ask anything…'}
                  className="min-w-0 flex-1 bg-transparent text-sm outline-none"
                  style={{ color: C.text }}
                  aria-label="Message GS AI"
                />
                <button
                  onClick={() => send()}
                  disabled={!draft.trim() || isStreaming}
                  className="rounded-full px-3.5 py-1.5 text-xs font-semibold transition-all hover:shadow-[0_0_14px_rgba(45,212,168,0.35)] active:scale-95 disabled:opacity-40"
                  style={{ background: C.accent, color: '#06231C' }}
                >
                  Send
                </button>
              </div>
            </div>
          </section>
        </div>

        {/* Assistants */}
        <section className="mt-10">
          <SectionHeader title="Assistant catalogue" hint="Prisma-backed · start one below" />
          <div className="gs-scroll mb-4 flex gap-2 overflow-x-auto pb-1">
            {categories.map((cat) => {
              const active = cat === category
              return (
                <button
                  key={cat}
                  onClick={() => setCategory(cat)}
                  aria-pressed={active}
                  className="shrink-0 rounded-full border px-3.5 py-1.5 text-xs font-medium transition-all active:scale-95"
                  style={{
                    borderColor: active ? C.accent : C.outline,
                    color: active ? C.accent : C.muted,
                    background: active ? 'rgba(45,212,168,0.08)' : 'transparent',
                  }}
                >
                  {cat === 'all' ? 'All' : (CATEGORY_LABELS[cat] ?? cat)}
                </button>
              )
            })}
          </div>

          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {visibleAssistants.map((a) => (
              <div
                key={a.id}
                className="gs-card relative overflow-hidden rounded-2xl border p-4"
                style={{ background: C.surface, borderColor: C.outline }}
              >
                <div className="gs-hairline absolute inset-x-0 top-0 h-[2px]" style={{ background: AURORA }} />
                <div className="flex items-start gap-3">
                  <span
                    className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl font-serif text-sm font-semibold"
                    style={{ background: C.container, color: C.accent }}
                  >
                    {a.name.slice(0, 1)}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold">{a.name}</p>
                    <p className="text-[11px]" style={{ color: C.muted }}>
                      {CATEGORY_LABELS[a.category] ?? a.category} · ★ {a.rating.toFixed(1)} · {fmtUses(a.uses)} uses
                    </p>
                  </div>
                  <button
                    onClick={() => toggleFavourite(a)}
                    aria-label={a.favourite ? `Unfavourite ${a.name}` : `Favourite ${a.name}`}
                    aria-pressed={a.favourite}
                    className="p-1 transition-transform active:scale-90"
                    style={{ color: a.favourite ? C.accent : C.muted }}
                  >
                    <StarIcon filled={a.favourite} />
                  </button>
                </div>
                <p className="mt-3 line-clamp-2 min-h-[2.5rem] text-xs leading-relaxed" style={{ color: C.muted }}>
                  {a.description}
                </p>
                {a.starters.length > 0 && (
                  <div className="mt-3 flex flex-wrap gap-1.5">
                    {a.starters.slice(0, 2).map((s) => (
                      <button
                        key={s}
                        onClick={() => startAssistantChat(a, s)}
                        className="max-w-full truncate rounded-full border px-2.5 py-1 text-[11px] transition-colors hover:border-[#2DD4A8]"
                        style={{ borderColor: C.outline, color: C.muted }}
                      >
                        {s}
                      </button>
                    ))}
                  </div>
                )}
                <button
                  onClick={() => startAssistantChat(a)}
                  disabled={startingChat === a.id || isStreaming}
                  className="mt-3 w-full rounded-xl border py-2 text-xs font-semibold transition-all active:scale-[0.98] disabled:opacity-50"
                  style={{ borderColor: C.accentDeep, color: C.accent, background: 'rgba(45,212,168,0.06)' }}
                >
                  {startingChat === a.id ? 'Opening…' : 'Start chat'}
                </button>
              </div>
            ))}
            {assistants.length === 0 && (
              <p className="text-sm" style={{ color: C.muted }}>
                Catalogue loading…
              </p>
            )}
          </div>
        </section>
      </main>

      <footer className="mt-auto border-t px-6 py-5" style={{ borderColor: C.outline }}>
        <div
          className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-2 text-xs"
          style={{ color: C.muted }}
        >
          <span>© Grapsee Agency — The GS AI App</span>
          <span>Aeruo Kinetic · obsidian surfaces · aurora accent · one contract, two native clients</span>
        </div>
      </footer>
    </div>
  )
}

/* ---------- shared building blocks (unchanged design) ---------- */

function StatCard({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div
      className="rounded-2xl border p-4 transition-colors hover:border-[#2DD4A8]/40"
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

function SectionHeader({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="mb-3 flex items-baseline justify-between gap-3">
      <h3 className="font-serif text-lg font-semibold">{title}</h3>
      {hint && (
        <span className="text-xs" style={{ color: C.muted }}>
          {hint}
        </span>
      )}
    </div>
  )
}

function CopyIcon() {
  return (
    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
      <rect x="9" y="9" width="12" height="12" rx="2" />
      <path d="M5 15V5a2 2 0 0 1 2-2h10" />
    </svg>
  )
}

function StarIcon({ filled }: { filled: boolean }) {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill={filled ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
      <path d="M12 2.5l2.9 6 6.6.9-4.8 4.6 1.2 6.5-5.9-3.2-5.9 3.2 1.2-6.5L2.5 9.4l6.6-.9z" strokeLinejoin="round" />
    </svg>
  )
}
