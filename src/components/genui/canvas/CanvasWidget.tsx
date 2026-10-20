'use client'

/**
 * The canvas kind: four freeform pointer surfaces.
 * whiteboard (draw) · mindmap (draggable nodes) · diagram (connect nodes) ·
 * polygons (draw polygons over a live Leaflet map)
 */

import { useEffect, useRef, useState } from 'react'
type Rec<K extends string = string, V = unknown> = Record<K, V>
import { alpha, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'

type CanvasPayload = Rec<string, unknown> & {
  surface?: string
  title?: string
  nodes?: Array<{ id: number; label: string; x: number; y: number; group?: string }>
  edges?: Array<[number, number]>
  note?: string
}

export function CanvasWidget({ payload }: { payload: CanvasPayload }) {
  const surface = payload.surface ?? 'whiteboard'
  if (surface === 'mindmap') return <MindMapSurface payload={payload} />
  if (surface === 'diagram') return <DiagramSurface payload={payload} />
  if (surface === 'polygons') return <PolygonSurface payload={payload} />
  return <WhiteboardSurface payload={payload} />
}

// ---------------------------------------------------------------- whiteboard

const PENS = ['#4C6FFF', '#0CA678', '#E8590C', '#D6336C', '#6741D9']

function WhiteboardSurface({ payload }: { payload: CanvasPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const drawing = useRef(false)
  const last = useRef<{ x: number; y: number } | null>(null)
  const [pen, setPen] = useState(accent)
  const [eraser, setEraser] = useState(false)
  const [strokes, setStrokes] = useState(0)

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    const dpr = Math.min(2, window.devicePixelRatio || 1)
    const resize = () => {
      const snapshot = canvas.toDataURL()
      canvas.width = canvas.clientWidth * dpr
      canvas.height = 260 * dpr
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
      ctx.lineCap = 'round'
      ctx.lineJoin = 'round'
      const img = new Image()
      img.onload = () => ctx.drawImage(img, 0, 0, canvas.clientWidth, 260)
      img.src = snapshot
    }
    resize()
    const ro = new ResizeObserver(resize)
    ro.observe(canvas)
    return () => ro.disconnect()
  }, [])

  const pos = (e: React.PointerEvent) => {
    const r = (e.currentTarget as HTMLCanvasElement).getBoundingClientRect()
    return { x: e.clientX - r.left, y: e.clientY - r.top }
  }
  return (
    <div>
      <TitleRow title={payload.title ?? 'Whiteboard'} right={`${strokes} strokes`} />
      <canvas
        ref={canvasRef}
        style={{ width: '100%', height: 260, display: 'block', borderRadius: RADIUS.md, border: `1px solid ${theme.border}`, background: theme.dark ? 'rgba(0,0,0,0.32)' : '#fff', touchAction: 'none', cursor: 'crosshair' }}
        onPointerDown={(e) => {
          drawing.current = true
          last.current = pos(e)
          ;(e.currentTarget as HTMLCanvasElement).setPointerCapture(e.pointerId)
        }}
        onPointerMove={(e) => {
          if (!drawing.current || !last.current) return
          const ctx = canvasRef.current?.getContext('2d')
          if (!ctx) return
          const p = pos(e)
          ctx.strokeStyle = eraser ? (theme.dark ? 'rgba(0,0,0,1)' : '#ffffff') : pen
          ctx.lineWidth = eraser ? 22 : 3
          ctx.beginPath()
          ctx.moveTo(last.current.x, last.current.y)
          ctx.lineTo(p.x, p.y)
          ctx.stroke()
          last.current = p
          setStrokes((s) => s + 1)
        }}
        onPointerUp={() => {
          drawing.current = false
          last.current = null
        }}
      />
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 8, flexWrap: 'wrap' }}>
        {PENS.map((c) => (
          <button
            key={c}
            type="button"
            aria-label={`pen ${c}`}
            onClick={() => {
              setPen(c)
              setEraser(false)
            }}
            style={{ width: 24, height: 24, borderRadius: 999, background: c, border: `2px solid ${pen === c && !eraser ? theme.text : 'transparent'}`, cursor: 'pointer' }}
          />
        ))}
        <button
          type="button"
          onClick={() => setEraser((v) => !v)}
          style={{ padding: '4px 12px', borderRadius: 999, border: `1px solid ${eraser ? accent : theme.border}`, background: eraser ? `${accent}1f` : 'transparent', color: eraser ? accent : theme.text2, fontSize: TYPE.micro, fontWeight: 700, cursor: 'pointer' }}
        >
          Eraser
        </button>
        <button
          type="button"
          onClick={() => {
            const canvas = canvasRef.current
            const ctx = canvas?.getContext('2d')
            if (canvas && ctx) ctx.clearRect(0, 0, canvas.clientWidth, 260)
            setStrokes(0)
          }}
          style={{ padding: '4px 12px', borderRadius: 999, border: `1px solid ${theme.border}`, background: 'transparent', color: theme.text2, fontSize: TYPE.micro, fontWeight: 600, cursor: 'pointer' }}
        >
          Clear
        </button>
      </div>
    </div>
  )
}

// ------------------------------------------------------------------- mindmap

function MindMapSurface({ payload }: { payload: CanvasPayload }) {
  const { accent, ramp, theme } = useWidgetIdentity()
  const [nodes, setNodes] = useState(() =>
    (payload.nodes ?? []).map((n, i) => ({ ...n, id: n.id ?? i, label: n.label ?? `Node ${i + 1}`, x: n.x ?? 0.2 + (i % 4) * 0.2, y: n.y ?? 0.25 + Math.floor(i / 4) * 0.3 }))
  )
  const [edges] = useState<Array<[number, number]>>(() => (payload.edges ?? []).filter(([a, b]) => a < (payload.nodes?.length ?? 0) && b < (payload.nodes?.length ?? 0)))
  const drag = useRef<number | null>(null)
  const boxRef = useRef<HTMLDivElement>(null)
  const groups = [...new Set(nodes.map((n) => n.group).filter(Boolean))]
  const colors = ramp(Math.max(1, groups.length || 1))
  const move = (e: React.PointerEvent) => {
    if (drag.current === null || !boxRef.current) return
    const r = boxRef.current.getBoundingClientRect()
    const x = Math.max(0.02, Math.min(0.98, (e.clientX - r.left) / r.width))
    const y = Math.max(0.04, Math.min(0.96, (e.clientY - r.top) / r.height))
    setNodes((ns) => ns.map((n) => (n.id === drag.current ? { ...n, x, y } : n)))
  }
  const addChild = (parent: number) => {
    setNodes((ns) => {
      const id = Math.max(-1, ...ns.map((n) => n.id)) + 1
      const p = ns.find((n) => n.id === parent)
      const node = { id, label: `Idea ${ns.length + 1}`, x: (p?.x ?? 0.5) + 0.16, y: (p?.y ?? 0.5) + (ns.length % 2 ? 0.14 : -0.14), group: p?.group }
      edges.push([parent, id])
      setEdgesReal([...edges, [parent, id]])
      return [...ns, node]
    })
  }
  const [realEdges, setEdgesReal] = useState<Array<[number, number]>>(edges)
  const posOf = (id: number) => nodes.find((n) => n.id === id)
  return (
    <div>
      <TitleRow title={payload.title ?? 'Mind map'} right="drag nodes · tap ⊕ to branch" />
      <div
        ref={boxRef}
        style={{ position: 'relative', height: 280, borderRadius: RADIUS.md, border: `1px solid ${theme.border}`, background: theme.dark ? 'rgba(0,0,0,0.28)' : 'rgba(0,0,0,0.03)', overflow: 'hidden', touchAction: 'none' }}
        onPointerMove={move}
        onPointerUp={() => (drag.current = null)}
      >
        <svg width="100%" height="100%" style={{ position: 'absolute', inset: 0 }}>
          {realEdges.map(([a, b], i) => {
            const na = posOf(a)
            const nb = posOf(b)
            if (!na || !nb) return null
            const x1 = na.x * 100
            const y1 = na.y * 100
            const x2 = nb.x * 100
            const y2 = nb.y * 100
            return (
              <path
                key={i}
                d={`M ${x1}% ${y1}% C ${(x1 + x2) / 2}% ${y1}%, ${(x1 + x2) / 2}% ${y2}%, ${x2}% ${y2}%`}
                fill="none"
                stroke={alpha(accent, 0.5)}
                strokeWidth={2}
              />
            )
          })}
        </svg>
        {nodes.map((n) => (
          <div
            key={n.id}
            style={{
              position: 'absolute',
              left: `${n.x * 100}%`,
              top: `${n.y * 100}%`,
              transform: 'translate(-50%,-50%)',
              padding: '6px 12px',
              borderRadius: 999,
              background: n.group && groups.length ? alpha(colors[groups.indexOf(n.group) % colors.length], 0.16) : alpha(accent, 0.14),
              border: `1.5px solid ${n.group && groups.length ? colors[groups.indexOf(n.group) % colors.length] : accent}`,
              fontSize: TYPE.label,
              fontWeight: 600,
              color: theme.text,
              cursor: 'grab',
              userSelect: 'none',
              whiteSpace: 'nowrap',
              display: 'flex',
              alignItems: 'center',
              gap: 6,
            }}
            onPointerDown={(e) => {
              drag.current = n.id
              ;(e.target as Element).setPointerCapture?.(e.pointerId)
            }}
          >
            {n.label}
            <button
              type="button"
              aria-label="add branch"
              onPointerDown={(e) => e.stopPropagation()}
              onClick={() => addChild(n.id)}
              style={{ width: 16, height: 16, borderRadius: 999, border: 'none', background: accent, color: theme.dark ? '#0F1216' : '#fff', fontSize: 11, lineHeight: 1, cursor: 'pointer', fontWeight: 800 }}
            >
              +
            </button>
          </div>
        ))}
      </div>
    </div>
  )
}

// ------------------------------------------------------------------- diagram

function DiagramSurface({ payload }: { payload: CanvasPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const shapes = ['start', 'step', 'decision', 'end'] as const
  const [nodes, setNodes] = useState(() =>
    (payload.nodes ?? []).map((n, i) => ({ id: n.id ?? i, label: n.label ?? `Step ${i + 1}`, x: n.x ?? 0.15 + (i % 3) * 0.3, y: n.y ?? 0.3 + Math.floor(i / 3) * 0.35, shape: shapes[Math.min(shapes.length - 1, i % 4)] as string }))
  )
  const [links, setLinks] = useState<Array<[number, number]>>(() =>
    (payload.edges ?? []).filter(([a, b]) => a < (payload.nodes?.length ?? 0) && b < (payload.nodes?.length ?? 0))
  )
  const [linkFrom, setLinkFrom] = useState<number | null>(null)
  const drag = useRef<number | null>(null)
  const boxRef = useRef<HTMLDivElement>(null)
  const move = (e: React.PointerEvent) => {
    if (drag.current === null || !boxRef.current) return
    const r = boxRef.current.getBoundingClientRect()
    const x = Math.max(0.08, Math.min(0.92, (e.clientX - r.left) / r.width))
    const y = Math.max(0.08, Math.min(0.92, (e.clientY - r.top) / r.height))
    setNodes((ns) => ns.map((n) => (n.id === drag.current ? { ...n, x, y } : n)))
  }
  const tap = (id: number) => {
    if (linkFrom === null) {
      setLinkFrom(id)
      return
    }
    if (linkFrom !== id) setLinks((ls) => (ls.some(([a, b]) => a === linkFrom && b === id) ? ls : [...ls, [linkFrom, id]]))
    setLinkFrom(null)
  }
  return (
    <div>
      <TitleRow title={payload.title ?? 'Diagram editor'} right={linkFrom !== null ? 'tap a target node…' : 'drag to move · tap two nodes to connect'} />
      <div
        ref={boxRef}
        style={{ position: 'relative', height: 300, borderRadius: RADIUS.md, border: `1px solid ${theme.border}`, background: theme.dark ? 'rgba(0,0,0,0.28)' : 'rgba(0,0,0,0.03)', overflow: 'hidden', touchAction: 'none' }}
        onPointerMove={move}
        onPointerUp={() => (drag.current = null)}
      >
        <svg width="100%" height="100%" style={{ position: 'absolute', inset: 0, pointerEvents: 'none' }}>
          <defs>
            <marker id="gs-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
              <path d="M 0 0 L 10 5 L 0 10 z" fill={alpha(accent, 0.8)} />
            </marker>
          </defs>
          {links.map(([a, b], i) => {
            const na = nodes.find((n) => n.id === a)
            const nb = nodes.find((n) => n.id === b)
            if (!na || !nb) return null
            const x1 = na.x * 100
            const y1 = na.y * 100
            const x2 = nb.x * 100
            const y2 = nb.y * 100
            return <line key={i} x1={`${x1}%`} y1={`${y1}%`} x2={`${x2}%`} y2={`${y2}%`} stroke={alpha(accent, 0.75)} strokeWidth={2} markerEnd="url(#gs-arrow)" />
          })}
        </svg>
        {nodes.map((n) => {
          const isDecision = n.shape === 'decision'
          const selected = linkFrom === n.id
          return (
            <button
              key={n.id}
              type="button"
              style={{
                position: 'absolute',
                left: `${n.x * 100}%`,
                top: `${n.y * 100}%`,
                transform: 'translate(-50%,-50%)',
                padding: isDecision ? '10px 18px' : '8px 14px',
                borderRadius: isDecision ? 4 : n.shape === 'start' || n.shape === 'end' ? 999 : 10,
                background: selected ? accent : alpha(accent, 0.13),
                border: `1.5px solid ${selected ? accent : alpha(accent, 0.5)}`,
                color: selected ? (theme.dark ? '#0F1216' : '#fff') : theme.text,
                fontSize: TYPE.label,
                fontWeight: 600,
                cursor: 'grab',
                userSelect: 'none',
                whiteSpace: 'nowrap',
              }}
              onPointerDown={(e) => {
                drag.current = n.id
                ;(e.target as Element).setPointerCapture?.(e.pointerId)
              }}
              onClick={() => tap(n.id)}
            >
              {n.label}
            </button>
          )
        })}
      </div>
      {links.length > 0 && (
        <button
          type="button"
          onClick={() => setLinks([])}
          style={{ marginTop: 8, padding: '4px 12px', borderRadius: 999, border: `1px solid ${theme.border}`, background: 'transparent', color: theme.text2, fontSize: TYPE.micro, cursor: 'pointer' }}
        >
          Clear connections ({links.length})
        </button>
      )}
    </div>
  )
}

// ------------------------------------------------------------------ polygons

function PolygonSurface({ payload }: { payload: CanvasPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const holder = useRef<HTMLDivElement>(null)
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const mapRef = useRef<any>(null)
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const polyRef = useRef<any>(null)
  const [pts, setPts] = useState<Array<[number, number]>>([])
  const [mode, setMode] = useState(false)

  useEffect(() => {
    let cancelled = false
    const boot = async () => {
      const L = (await import('leaflet')).default
      if (cancelled || !holder.current || mapRef.current) return
      const map = L.map(holder.current, { scrollWheelZoom: false, attributionControl: false })
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      ;(window as any).__gsL = L
      L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19 }).addTo(map)
      map.setView([23.8103, 90.4125], 12)
      map.on('click', (e: { latlng: { lat: number; lng: number } }) => {
        if (!mode) return
        setPts((p) => [...p, [e.latlng.lat, e.latlng.lng]])
      })
      mapRef.current = map
    }
    boot()
    return () => {
      cancelled = true
      mapRef.current?.remove()
      mapRef.current = null
    }
  }, [])

  useEffect(() => {
    const L = (window as { __gsL?: typeof import('leaflet') }).__gsL
    const map = mapRef.current
    if (!L || !map) return
    if (polyRef.current) {
      polyRef.current.remove()
      polyRef.current = null
    }
    if (pts.length >= 3) {
      polyRef.current = L.polygon(pts, { color: accent, weight: 2, fillColor: accent, fillOpacity: 0.25 }).addTo(map)
    } else if (pts.length >= 1) {
      polyRef.current = L.polyline(pts, { color: accent, dashArray: '5 6' }).addTo(map)
    }
  }, [pts, accent])

  return (
    <div>
      <TitleRow title={payload.title ?? 'Zone editor'} right={`${pts.length} vertices`} />
      <div ref={holder} style={{ height: 260, borderRadius: RADIUS.md, overflow: 'hidden', border: `1px solid ${theme.border}`, cursor: mode ? 'crosshair' : 'grab' }} />
      <div style={{ display: 'flex', gap: 6, marginTop: 8 }}>
        <button
          type="button"
          onClick={() => setMode((m) => !m)}
          style={{ padding: '5px 13px', borderRadius: 999, border: `1px solid ${mode ? accent : theme.border}`, background: mode ? `${accent}1f` : 'transparent', color: mode ? accent : theme.text2, fontSize: TYPE.micro, fontWeight: 700, cursor: 'pointer' }}
        >
          {mode ? 'Drawing — tap the map' : 'Draw zone'}
        </button>
        <button
          type="button"
          onClick={() => setPts([])}
          style={{ padding: '5px 13px', borderRadius: 999, border: `1px solid ${theme.border}`, background: 'transparent', color: theme.text2, fontSize: TYPE.micro, fontWeight: 600, cursor: 'pointer' }}
        >
          Reset
        </button>
        {pts.length >= 3 && (
          <span style={{ alignSelf: 'center', fontSize: TYPE.micro, color: theme.text3 }}>
            ≈ {Math.round(polygonAreaKm2(pts))} km²
          </span>
        )}
      </div>
    </div>
  )
}

function polygonAreaKm2(pts: Array<[number, number]>): number {
  let a = 0
  for (let i = 0; i < pts.length; i++) {
    const [lat1, lng1] = pts[i]
    const [lat2, lng2] = pts[(i + 1) % pts.length]
    a += (lng2 - lng1) * Math.cos(((lat1 + lat2) / 2) * (Math.PI / 180)) * (lat2 + lat1)
  }
  return (Math.abs(a) / 2) * (111.32 * 111.32)
}
