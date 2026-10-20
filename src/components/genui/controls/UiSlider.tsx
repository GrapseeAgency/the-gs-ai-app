'use client'

import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

/** The labelled range slider — the workhorse of calc and form. */
export function UiSlider({
  label,
  value,
  min = 0,
  max = 100,
  step = 1,
  unit,
  onChange,
}: {
  label?: string
  value: number
  min?: number
  max?: number
  step?: number
  unit?: string
  onChange: (v: number) => void
}) {
  const { accent, theme } = useWidgetIdentity()
  const pct = max > min ? ((value - min) / (max - min)) * 100 : 0
  return (
    <label style={{ display: 'block', padding: '6px 0' }}>
      {(label || unit) && (
        <span style={{ display: 'flex', justifyContent: 'space-between', fontSize: TYPE.label, color: theme.text2, marginBottom: 6 }}>
          <span>{label}</span>
          <span style={{ fontWeight: 700, color: accent }}>
            {formatNumber(value)}
            {unit ? ` ${unit}` : ''}
          </span>
        </span>
      )}
      <span style={{ position: 'relative', display: 'block', height: 24 }}>
        <span
          aria-hidden
          style={{
            position: 'absolute',
            left: 0,
            right: 0,
            top: 10,
            height: 4,
            borderRadius: 2,
            background: theme.raised,
          }}
        />
        <span
          aria-hidden
          style={{
            position: 'absolute',
            left: 0,
            width: `${pct}%`,
            top: 10,
            height: 4,
            borderRadius: 2,
            background: accent,
          }}
        />
        <input
          type="range"
          min={min}
          max={max}
          step={step}
          value={value}
          onChange={(e) => onChange(Number(e.target.value))}
          style={{
            position: 'absolute',
            inset: 0,
            width: '100%',
            margin: 0,
            appearance: 'none' as const,
            WebkitAppearance: 'none' as const,
            background: 'transparent',
            cursor: 'pointer',
          }}
        />
        <span
          aria-hidden
          style={{
            position: 'absolute',
            left: `calc(${pct}% - 9px)`,
            top: 3,
            width: 18,
            height: 18,
            borderRadius: 999,
            background: accent,
            border: `2px solid ${theme.dark ? '#0F1216' : '#FFFFFF'}`,
            boxShadow: `0 0 0 1px ${alpha(accent, 0.4)}`,
            pointerEvents: 'none',
          }}
        />
        <style>{`input[type=range]::-webkit-slider-thumb { appearance:none; width:18px; height:18px; border-radius:999px; background:transparent; } input[type=range]::-moz-range-thumb { appearance:none; width:18px; height:18px; border:none; border-radius:999px; background:transparent; }`}</style>
      </span>
    </label>
  )
}

export function formatNumber(n: number): string {
  if (!Number.isFinite(n)) return String(n)
  if (Math.abs(n) >= 1_000_000) return `${(n / 1_000_000).toFixed(n % 1_000_000 === 0 ? 0 : 1)}M`
  if (Math.abs(n) >= 10_000) return `${(n / 1_000).toFixed(0)}k`
  return n.toLocaleString(undefined, { maximumFractionDigits: 2 })
}
