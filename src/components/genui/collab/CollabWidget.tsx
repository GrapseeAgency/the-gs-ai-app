'use client'

/**
 * The collab kind — LIVE shared state across everyone viewing the answer.
 *
 * Four surfaces over one SSE room:
 *   counter — taps apply server-side deltas (concurrent taps never lose state)
 *   vote    — shared options, live counts, one local vote per option
 *   todo    — shared checklist (list-replace, LWW: the widget says so)
 *   pointer — presence dots + live cursors only, nothing persisted
 *
 * HONEST TIER 2: the status row always labels it a prototype — per-server
 * memory, last-write-wins, resets when the server restarts.
 */

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { alpha, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { UiButton } from '../controls/UiButton'
import { UiTextInput } from '../controls/UiTextInput'

export type CollabPayload = {
  surface?: string
  room?: string
  title?: string
  label?: string
  items?: string[]
  start?: number
  note?: string
  accent?: string
  variant?: string
  palette?: string
}

type Presence = { id: string; name: string; color: string }
type Shared = { status: 'connecting' | 'live' | 'offline'; state: Record<string, unknown>; presence: Presence[] }

function useCollabRoom(room: string, me: Presence): Shared {
  const [shared, setShared] = useState<Shared>({ status: 'connecting', state: {}, presence: [] })
  const meRef = useRef(me)

  useEffect(() => {
    meRef.current = me
  })

  useEffect(() => {
    const q = new URLSearchParams({ room, client: me.id, name: me.name, color: me.color })
    const es = new EventSource(`/api/v1/collab?${q}`)
    es.addEventListener('snapshot', (ev) => {
      const d = JSON.parse((ev as MessageEvent).data) as { state: Record<string, unknown>; presence: Presence[] }
      setShared({ status: 'live', state: d.state ?? {}, presence: d.presence ?? [] })
    })
    es.addEventListener('rev', (ev) => {
      const d = JSON.parse((ev as MessageEvent).data) as { state: Record<string, unknown>; presence: Presence[] }
      setShared({ status: 'live', state: d.state ?? {}, presence: d.presence ?? [] })
    })
    es.onopen = () => setShared((s) => ({ ...s, status: 'live' }))
    es.onerror = () => setShared((s) => (s.status === 'live' ? { ...s, status: 'offline' } : s))

    // presence heartbeat — the broker expires us after 30s without one
    const beat = setInterval(() => {
      void fetch('/api/v1/collab', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ room, heartbeat: { id: meRef.current.id, name: meRef.current.name, color: meRef.current.color } }),
      }).catch(() => {})
    }, 10_000)
    // first beat immediately so everyone sees us before the first interval
    void fetch('/api/v1/collab', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ room, heartbeat: { id: me.id, name: me.name, color: me.color } }),
    }).catch(() => {})

    return () => {
      clearInterval(beat)
      es.close()
    }
  }, [room, me.id, me.name, me.color])

  return shared
}

function usePublish(room: string) {
  return useCallback(
    async (body: Record<string, unknown>) => {
      try {
        const res = await fetch('/api/v1/collab', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ room, ...body }),
        })
        if (!res.ok) return null
        return (await res.json()) as { state: Record<string, unknown> }
      } catch {
        return null
      }
    },
    [room],
  )
}

const GUEST_NAMES = ['Falcon', 'Otter', 'Heron', 'Lynx', 'Marmot', 'Ibis', 'Gecko', 'Tapir']
function makeMe(rampSeed: number): Presence {
  const id = Math.random().toString(36).slice(2, 10)
  const name = `${GUEST_NAMES[Math.abs(rampSeed) % GUEST_NAMES.length]}-${id.slice(0, 3)}`
  const hue = 190 + (Math.abs(rampSeed * 7919) % 140)
  return { id, name, color: `hsl(${hue} 70% 55%)` }
}

export function CollabWidget({ payload }: { payload: CollabPayload }) {
  const { ramp } = useWidgetIdentity()
  const me = useMemo(() => makeMe((payload.room ?? '').length + 1), [payload.room])
  const room = payload.room ?? 'lobby'
  const shared = useCollabRoom(room, me)
  const publish = usePublish(room)
  const surface = payload.surface ?? 'counter'

  return (
    <div>
      <TitleRow
        title={payload.title ?? 'Shared space'}
        right={
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: TYPE.micro, color: 'inherit' }}>
            {shared.presence.slice(0, 4).map((p) => (
              <span key={p.id} title={p.name} style={{ width: 10, height: 10, borderRadius: 999, background: p.color, display: 'inline-block' }} />
            ))}
            {shared.presence.length > 4 ? `+${shared.presence.length - 4}` : ''}
            {shared.presence.length ? ` · ${shared.presence.length} live` : ' · you'}
          </span>
        }
      />
      {surface === 'counter' && <CounterSurface payload={payload} shared={shared} publish={publish} />}
      {surface === 'vote' && <VoteSurface payload={payload} shared={shared} publish={publish} />}
      {surface === 'todo' && <TodoSurface payload={payload} shared={shared} publish={publish} />}
      {surface === 'pointer' && <PointerSurface payload={payload} shared={shared} me={me} publish={publish} />}
      <StatusRow room={room} status={shared.status} note={payload.note} />
    </div>
  )
}

// ------------------------------------------------------------------ surfaces

function CounterSurface({ payload, shared, publish }: { payload: CollabPayload; shared: Shared; publish: ReturnType<typeof usePublish> }) {
  const { accent, theme } = useWidgetIdentity()
  const n = typeof shared.state.n === 'number' ? shared.state.n : (payload.start ?? 0)
  return (
    <div style={{ textAlign: 'center', padding: '18px 0 14px' }}>
      <div style={{ fontSize: 54, fontWeight: 800, fontVariantNumeric: 'tabular-nums', color: accent, lineHeight: 1 }}>{n}</div>
      <div style={{ fontSize: TYPE.micro, color: theme.text3, margin: '6px 0 12px' }}>{payload.label ?? 'this number is the same for everyone, right now'}</div>
      <div style={{ display: 'flex', gap: 6, justifyContent: 'center' }}>
        {[-1, 1, 5].map((d) => (
          <UiButton key={d} buttonStyle={d > 0 ? 'filled' : 'tonal'} onClick={() => void publish({ delta: { n: d } })}>
            {d > 0 ? `+${d}` : d}
          </UiButton>
        ))}
      </div>
    </div>
  )
}

function VoteSurface({ payload, shared, publish }: { payload: CollabPayload; shared: Shared; publish: ReturnType<typeof usePublish> }) {
  const { accent, theme, ramp } = useWidgetIdentity()
  const items = (payload.items ?? []).slice(0, 12)
  const [mine, setMine] = useState<Set<number>>(new Set())
  const votes = items.map((_, i) => (typeof shared.state[`v${i}`] === 'number' ? (shared.state[`v${i}`] as number) : 0))
  const total = Math.max(1, votes.reduce((a, b) => a + b, 0))
  const leader = votes.indexOf(Math.max(...votes))
  const colors = ramp(Math.max(1, items.length))
  const cast = (i: number) => {
    if (mine.has(i)) return
    setMine((s) => new Set(s).add(i))
    void publish({ delta: { [`v${i}`]: 1 } })
  }
  if (!items.length) return <div style={{ padding: 12, color: theme.text3, fontSize: TYPE.micro }}>vote needs items</div>
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6, padding: '4px 0' }}>
      {items.map((label, i) => (
        <button
          key={i}
          type="button"
          onClick={() => cast(i)}
          disabled={mine.has(i)}
          style={{
            position: 'relative',
            border: `1px solid ${mine.has(i) ? accent : theme.border}`,
            borderRadius: 8,
            background: 'transparent',
            padding: '9px 12px',
            cursor: mine.has(i) ? 'default' : 'pointer',
            overflow: 'hidden',
            textAlign: 'left',
          }}
        >
          <span
            aria-hidden
            style={{ position: 'absolute', inset: 0, width: `${(votes[i] / total) * 100}%`, background: alpha(colors[i % colors.length], 0.28), transition: 'width .35s' }}
          />
          <span style={{ position: 'relative', display: 'flex', justifyContent: 'space-between', fontSize: TYPE.body, color: theme.text, fontWeight: i === leader && votes[i] > 0 ? 700 : 500 }}>
            <span>
              {label} {mine.has(i) ? <span style={{ color: accent }}>· yours</span> : ''}
            </span>
            <span style={{ fontVariantNumeric: 'tabular-nums', color: theme.text2 }}>{votes[i]}</span>
          </span>
        </button>
      ))}
    </div>
  )
}

function TodoSurface({ payload, shared, publish }: { payload: CollabPayload; shared: Shared; publish: ReturnType<typeof usePublish> }) {
  const { accent, theme } = useWidgetIdentity()
  type Row = { text: string; done?: boolean }
  const rows: Row[] = Array.isArray(shared.state.todos) ? (shared.state.todos as Row[]) : (payload.items ?? []).map((text) => ({ text }))
  const [draft, setDraft] = useState('')
  const replace = (next: Row[]) => void publish({ set: { todos: next.slice(0, 12) } })
  return (
    <div>
      <div style={{ display: 'flex', gap: 6, marginBottom: 8 }}>
        <UiTextInput
          value={draft}
          onChange={setDraft}
          ariaLabel="new shared item"
          placeholder="add a shared item…"
          onKeyDown={(e) => {
            if (e.key === 'Enter' && draft.trim()) {
              replace([...rows, { text: draft.trim() }])
              setDraft('')
            }
          }}
        />
        <UiButton buttonStyle="filled" onClick={() => { if (draft.trim()) { replace([...rows, { text: draft.trim() }]); setDraft('') } }}>
          Add
        </UiButton>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        {rows.length === 0 && <div style={{ color: theme.text3, fontSize: TYPE.micro }}>empty — add something, watch it appear for everyone</div>}
        {rows.map((row, i) => (
          <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '6px 8px', borderRadius: 8, border: `1px solid ${theme.border}` }}>
            <input
              type="checkbox"
              checked={!!row.done}
              onChange={() => replace(rows.map((r, ii) => (ii === i ? { ...r, done: !r.done } : r)))}
              style={{ accentColor: accent }}
            />
            <span style={{ flex: 1, fontSize: TYPE.body, color: theme.text, textDecoration: row.done ? 'line-through' : 'none', opacity: row.done ? 0.6 : 1 }}>{row.text}</span>
            <button type="button" aria-label="remove" onClick={() => replace(rows.filter((_, ii) => ii !== i))} style={{ border: 'none', background: 'transparent', color: theme.text3, cursor: 'pointer' }}>
              ×
            </button>
          </div>
        ))}
      </div>
    </div>
  )
}

function PointerSurface({ payload, shared, me, publish }: { payload: CollabPayload; shared: Shared; me: Presence; publish: ReturnType<typeof usePublish> }) {
  const { accent, theme } = useWidgetIdentity()
  const lastSent = useRef(0)
  const boxRef = useRef<HTMLDivElement>(null)
  const [selfPt, setSelfPt] = useState<{ x: number; y: number } | null>(null)
  const move = (e: React.PointerEvent) => {
    const box = boxRef.current?.getBoundingClientRect()
    if (!box) return
    const x = (e.clientX - box.left) / box.width
    const y = (e.clientY - box.top) / box.height
    setSelfPt({ x, y })
    const now = Date.now()
    if (now - lastSent.current < 90) return
    lastSent.current = now
    void publish({ set: { [`px.${me.id}`]: Math.round(x * 1000) / 1000, [`py.${me.id}`]: Math.round(y * 1000) / 1000 }, heartbeat: { id: me.id, name: me.name, color: me.color } })
  }
  const others = shared.presence.filter((p) => p.id !== me.id)
  const ptOf = (id: string): { x: number; y: number } | null => {
    const x = shared.state[`px.${id}`]
    const y = shared.state[`py.${id}`]
    return typeof x === 'number' && typeof y === 'number' ? { x, y } : null
  }
  return (
    <div>
      <div
        ref={boxRef}
        onPointerMove={move}
        style={{
          position: 'relative',
          height: 210,
          borderRadius: RADIUS.md,
          border: `1px solid ${theme.border}`,
          background: theme.dark ? 'rgba(0,0,0,0.28)' : 'rgba(0,0,0,0.03)',
          overflow: 'hidden',
          cursor: 'crosshair',
          touchAction: 'none',
        }}
      >
        <span style={{ position: 'absolute', left: '46%', top: '44%', fontSize: TYPE.micro, color: theme.text3, pointerEvents: 'none' }}>
          {payload.label ?? 'move here — everyone sees your cursor'}
        </span>
        {others.map((p) => {
          const pt = ptOf(p.id)
          if (!pt) return null
          return (
            <span key={p.id} style={{ position: 'absolute', left: `${pt.x * 100}%`, top: `${pt.y * 100}%`, transform: 'translate(-2px,-2px)', pointerEvents: 'none', transition: 'left .12s linear, top .12s linear' }}>
              <svg width="14" height="16" viewBox="0 0 14 16">
                <path d="M1 1 L13 7 L7 9 L5 15 Z" fill={p.color} stroke={theme.dark ? '#0F1216' : '#fff'} strokeWidth="1" />
              </svg>
              <span style={{ position: 'absolute', left: 14, top: 12, fontSize: 10, fontWeight: 700, color: '#fff', background: p.color, borderRadius: 999, padding: '1px 7px', whiteSpace: 'nowrap' }}>
                {p.name}
              </span>
            </span>
          )
        })}
        {selfPt && (
          <span style={{ position: 'absolute', left: `${selfPt.x * 100}%`, top: `${selfPt.y * 100}%`, pointerEvents: 'none' }}>
            <svg width="14" height="16" viewBox="0 0 14 16">
              <path d="M1 1 L13 7 L7 9 L5 15 Z" fill={accent} stroke={theme.dark ? '#0F1216' : '#fff'} strokeWidth="1" />
            </svg>
          </span>
        )}
      </div>
      {others.length > 0 && (
        <div style={{ fontSize: TYPE.micro, color: theme.text3, marginTop: 6 }}>
          cursors vanish 30s after someone stops moving (presence TTL) — nothing here is stored
        </div>
      )}
    </div>
  )
}

function StatusRow({ room, status, note }: { room: string; status: Shared['status']; note?: string }) {
  const { theme } = useWidgetIdentity()
  const dot = status === 'live' ? '#0CA678' : status === 'connecting' ? '#F08C00' : '#E03131'
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginTop: 10, fontSize: TYPE.micro, color: theme.text3, flexWrap: 'wrap' }}>
      <span style={{ width: 7, height: 7, borderRadius: 999, background: dot, display: 'inline-block' }} />
      <span>
        {status} · room <code style={{ color: theme.text2 }}>{room}</code> · shared prototype: per-server memory, last-write-wins, resets on restart
      </span>
      {note ? <span>· {note}</span> : null}
    </div>
  )
}
