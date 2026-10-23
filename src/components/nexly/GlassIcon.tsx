'use client'

import type { CSSProperties } from 'react'
import {
  GLASS_TONES,
  glassToneFor,
  iconNameFor,
  type GlassTone,
} from '@/lib/nexly/icons'

type ToneVars = CSSProperties & {
  '--tone-top'?: string
  '--tone-bottom'?: string
  '--tone-halo'?: string
}

export interface GlassIconProps {
  /** Tabler kebab-case name, or a friendly alias such as ``study``. */
  name: string
  /** Glyph box in px. */
  size?: number
  /** Glass tile diameter in px. Set 0 for a bare glyph. */
  tile?: number
  tone?: GlassTone | string
  /** Draw only the glyph, no tile (chrome controls). */
  bare?: boolean
  /** Skip backdrop-filter for dense grids (the icon explorer). */
  flat?: boolean
  className?: string
  title?: string
}

/**
 * The one icon primitive. The glyph is a <use> reference into the 5,184-symbol
 * sprite; the tile supplies the glass. No emoji, no per-icon React component.
 */
export function GlassIcon({
  name,
  size = 16,
  tile = 32,
  tone,
  bare = false,
  flat = false,
  className,
  title,
}: GlassIconProps) {
  const id = iconNameFor(name)
  const resolvedTone = glassToneFor(id, tone)
  const palette = GLASS_TONES[resolvedTone]

  const glyph = (
    <svg
      className="nx-glyph"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden={title ? undefined : true}
      role={title ? 'img' : undefined}
      focusable="false"
    >
      {title ? <title>{title}</title> : null}
      <use href={`#gs-${id}`} />
    </svg>
  )

  if (bare) {
    return (
      <span className={`nx-icon-bare ${className ?? ''}`} style={{ width: size, height: size }}>
        {glyph}
      </span>
    )
  }

  const vars: ToneVars = {
    width: tile,
    height: tile,
    color: palette.ink,
    '--tone-top': palette.top,
    '--tone-bottom': palette.bottom,
    '--tone-halo': palette.halo,
  }

  return (
    <span
      className={`nx-glass-icon ${flat ? 'is-flat' : ''} ${className ?? ''}`}
      style={vars}
      title={title}
      aria-hidden={title ? undefined : true}
    >
      {glyph}
    </span>
  )
}
