'use client'

/**
 * MapBlock — Leaflet with OSM tiles, driven imperatively (no react-leaflet —
 * one less React-version coupling). Renders pins, polylines and routes inside
 * the widget surface.
 */

import { useEffect, useRef } from 'react'
import type { ComponentType } from 'react'
import type { Map as LeafletMap, LayerGroup } from 'leaflet'
import { TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'

export interface MapPayload {
  title?: string
  center?: [number, number]
  zoom?: number
  pins?: Array<{ lat: number; lng: number; label?: string; detail?: string }>
  route?: Array<[number, number]>
  mode?: string
  stops?: Array<{ lat: number; lng: number; label?: string }>
}

export function MapBlock({ payload, height = 260 }: { payload: MapPayload; height?: number }) {
  const { accent, theme } = useWidgetIdentity()
  const holder = useRef<HTMLDivElement>(null)
  const mapRef = useRef<LeafletMap | null>(null)
  const layerRef = useRef<LayerGroup | null>(null)

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
        const pins = (payload.pins ?? []).concat(payload.stops ?? [])
        for (const pin of pins) {
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
        const line = payload.route ?? (payload.stops ?? []).map((s) => [s.lat, s.lng] as [number, number])
        if (line.length >= 2) {
          L.polyline(line, { color: accent, weight: 3.5, opacity: 0.85, dashArray: payload.mode ? '7 6' : undefined }).addTo(layer)
        }
        const pts = pins.map((p) => [p.lat, p.lng] as [number, number])
        if (pts.length > 1) map.fitBounds(L.latLngBounds(pts).pad(0.25))
        else if (pts.length === 1) map.setView(pts[0], payload.zoom ?? 13)
        else map.setView(payload.center ?? [23.8, 90.4], payload.zoom ?? 5)
      }
      draw()
      // redraw when the payload itself changes
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

  return (
    <div>
      <TitleRow title={payload.title} right={payload.mode} />
      <div
        ref={holder}
        style={{ height, borderRadius: 14, overflow: 'hidden', border: `1px solid ${theme.border}`, background: theme.raised }}
      />
      <style>{`.leaflet-container { font-family: inherit; background: ${theme.raised} }`}</style>
    </div>
  )
}

export type MapBlockType = ComponentType<{ payload: MapPayload; height?: number }>
