'use client'

/**
 * MapBlock — Leaflet with OSM tiles, driven imperatively (no react-leaflet —
 * one less React-version coupling). Renders pins, polylines, routes, place
 * cards, and turn-by-turn itineraries inside the widget surface.
 */

import { useEffect, useMemo, useRef } from 'react'
import type { ComponentType } from 'react'
import type { Map as LeafletMap, LayerGroup } from 'leaflet'
import { alpha, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'

export interface MapPinItem {
  lat: number
  lng: number
  label?: string
  detail?: string
}

export interface MapStepItem {
  instruction: string
  distance?: string
}

export interface MapPayload {
  title?: string
  center?: [number, number]
  zoom?: number
  pins?: MapPinItem[]
  markers?: Array<Record<string, unknown>>
  locations?: Array<Record<string, unknown>>
  places?: Array<Record<string, unknown>>
  route?: Array<[number, number]>
  mode?: string
  stops?: MapPinItem[]
  waypoints?: Array<Record<string, unknown>>
  itinerary?: Array<Record<string, unknown>>
  steps?: Array<Record<string, unknown> | string>
  directions?: Array<Record<string, unknown> | string>
  cards?: boolean
  lat?: number
  latitude?: number
  lng?: number
  lon?: number
  longitude?: number
  label?: string
  address?: string
  detail?: string
}

function normPin(raw: unknown): MapPinItem | null {
  if (!raw || typeof raw !== 'object') return null
  const o = raw as Record<string, unknown>
  const coords = Array.isArray(o.coordinates ?? o.position) ? (o.coordinates ?? o.position) as unknown[] : null
  const lat = Number(o.lat ?? o.latitude ?? (coords ? coords[0] : NaN))
  const lng = Number(o.lng ?? o.lon ?? o.longitude ?? (coords ? coords[1] : NaN))
  if (!Number.isFinite(lat) || !Number.isFinite(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) return null
  const label = String(o.label ?? o.title ?? o.name ?? o.place ?? '').trim()
  const detail = String(o.detail ?? o.description ?? o.address ?? o.subtitle ?? o.category ?? '').trim()
  return { lat, lng, label: label || undefined, detail: detail || undefined }
}

function normStep(raw: unknown): MapStepItem | null {
  if (typeof raw === 'string' && raw.trim()) return { instruction: raw.trim() }
  if (!raw || typeof raw !== 'object') return null
  const o = raw as Record<string, unknown>
  const instruction = String(o.instruction ?? o.text ?? o.step ?? o.title ?? o.description ?? '').trim()
  if (!instruction) return null
  const distance = String(o.distance ?? o.dist ?? o.duration ?? o.time ?? '').trim()
  return { instruction, distance: distance || undefined }
}

export function MapBlock({ payload, height = 260 }: { payload: MapPayload; height?: number }) {
  const { accent, theme } = useWidgetIdentity()
  const holder = useRef<HTMLDivElement>(null)
  const mapRef = useRef<LeafletMap | null>(null)
  const layerRef = useRef<LayerGroup | null>(null)

  const normalized = useMemo(() => {
    const rawPins = payload.pins ?? payload.markers ?? payload.locations ?? payload.places ?? []
    const pins = rawPins.map(normPin).filter((p): p is MapPinItem => p !== null)
    if (pins.length === 0) {
      const single = normPin(payload)
      if (single) pins.push(single)
    }
    const rawStops = payload.stops ?? payload.waypoints ?? payload.itinerary ?? []
    const stops = rawStops.map(normPin).filter((p): p is MapPinItem => p !== null)
    const rawSteps = payload.steps ?? payload.directions ?? []
    const steps = rawSteps.map(normStep).filter((s): s is MapStepItem => s !== null)
    return { pins, stops, steps }
  }, [payload])

  useEffect(() => {
    let cancelled = false
    const setup = async () => {
      const L = (await import('leaflet')).default
      if (cancelled || !holder.current || mapRef.current) return
      const map = L.map(holder.current, { scrollWheelZoom: false, attributionControl: false })
      L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19 }).addTo(map)
      layerRef.current = L.layerGroup().addTo(map)
      mapRef.current = map
      const draw = () => {
        const layer = layerRef.current
        if (!layer) return
        layer.clearLayers()
        const allPins = normalized.pins.concat(normalized.stops)
        for (const pin of allPins) {
          const marker = L.circleMarker([pin.lat, pin.lng], {
            radius: 7,
            color: '#fff',
            weight: 2,
            fillColor: accent,
            fillOpacity: 1,
          })
          marker.bindTooltip(pin.label ?? '', { direction: 'top', offset: [0, -8] })
          layer.addLayer(marker)
        }
        const line = payload.route ?? normalized.stops.map((s) => [s.lat, s.lng] as [number, number])
        if (line.length >= 2) {
          L.polyline(line, { color: accent, weight: 3.5, opacity: 0.85, dashArray: payload.mode ? '7 6' : undefined }).addTo(layer)
        }
        const pts = allPins.map((p) => [p.lat, p.lng] as [number, number])
        if (pts.length > 1) map.fitBounds(L.latLngBounds(pts).pad(0.25))
        else if (pts.length === 1) map.setView(pts[0], payload.zoom ?? 13)
        else map.setView(payload.center ?? [23.8, 90.4], payload.zoom ?? 5)
      }
      draw()
      ;(mapRef.current as unknown as { __gsPayload?: unknown }).__gsPayload = payload
      mapRef.current.invalidateSize()
    }
    setup()
    return () => {
      cancelled = true
      mapRef.current?.remove()
      mapRef.current = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const placeCards = normalized.pins.length > 0 ? normalized.pins : normalized.stops

  return (
    <div>
      <TitleRow title={payload.title} right={payload.mode} />
      <div
        ref={holder}
        style={{ height, borderRadius: 14, overflow: 'hidden', border: `1px solid ${theme.border}`, background: theme.raised }}
      />
      {placeCards.some((p) => p.label || p.detail) ? (
        <div style={{ display: 'flex', gap: 8, overflowX: 'auto', paddingTop: 10 }}>
          {placeCards.map((p, idx) => (
            <button
              key={idx}
              type="button"
              onClick={() => mapRef.current?.setView([p.lat, p.lng], Math.max(payload.zoom ?? 14, 14))}
              style={{
                flex: '0 0 auto',
                maxWidth: 220,
                textAlign: 'left',
                padding: '8px 10px',
                borderRadius: RADIUS.md,
                border: `1px solid ${theme.border}`,
                background: theme.raised,
                cursor: 'pointer',
              }}
            >
              <div style={{ fontSize: TYPE.micro, fontWeight: 700, color: theme.text }}>{p.label ?? `Stop ${idx + 1}`}</div>
              {p.detail ? (
                <div style={{ fontSize: 11, color: theme.text3, marginTop: 2, lineHeight: 1.35 }}>{p.detail}</div>
              ) : null}
            </button>
          ))}
        </div>
      ) : null}
      {normalized.steps.length > 0 ? (
        <div style={{ marginTop: 10, display: 'flex', flexDirection: 'column', gap: 4 }}>
          {normalized.steps.map((s, idx) => (
            <div
              key={idx}
              style={{
                display: 'flex',
                alignItems: 'baseline',
                gap: 8,
                padding: '5px 0',
                fontSize: TYPE.micro,
                borderBottom: `1px solid ${alpha(theme.dark ? '#FFFFFF' : '#14171C', 0.06)}`,
              }}
            >
              <span style={{ color: accent, fontWeight: 700, minWidth: 16 }}>{idx + 1}.</span>
              <span style={{ color: theme.text, flex: 1 }}>{s.instruction}</span>
              {s.distance ? <span style={{ color: theme.text3, fontFamily: 'ui-monospace, monospace' }}>{s.distance}</span> : null}
            </div>
          ))}
        </div>
      ) : null}
      <style>{`.leaflet-container { font-family: inherit; background: ${theme.raised} }`}</style>
    </div>
  )
}

export type MapBlockType = ComponentType<{ payload: MapPayload; height?: number }>
