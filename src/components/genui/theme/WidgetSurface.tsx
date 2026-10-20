'use client'

/**
 * WidgetSurface — the card frame every GenUI block renders in.
 *
 * It resolves the block's face (accent + variant + palette) once and hands it
 * to children through context, so charts, controls and text inside all follow
 * the same identity without re-deriving it.
 */

import { createContext, useContext, useMemo, type CSSProperties, type ReactNode } from 'react'
import type { Surface } from '@/lib/design/rulebook'
import { accentOf, FONT, paletteById, rampOf, themeFor, type GsTheme } from './tokens'
import { surfaceStyle } from './surfaces'

export interface WidgetIdentity {
  accent: string
  variant: Surface | undefined
  theme: GsTheme
  paletteId: string | undefined
  ramp: (count: number) => string[]
}

const IdentityContext = createContext<WidgetIdentity | null>(null)

export function useWidgetIdentity(): WidgetIdentity {
  const ctx = useContext(IdentityContext)
  // Renderers used outside a surface (gallery scaffolding) fall back to the
  // page's dark identity with the default accent.
  return (
    ctx ?? {
      accent: accentOf(undefined, undefined),
      variant: undefined,
      theme: themeFor(true),
      paletteId: undefined,
      ramp: (count: number) => rampOf(accentOf(undefined, undefined), undefined, count),
    }
  )
}

export interface WidgetSurfaceProps {
  accent?: string
  variant?: Surface
  palette?: string
  dark?: boolean
  /** editorial surfaces draw no card background — just the rule. */
  bare?: boolean
  style?: CSSProperties
  className?: string
  children: ReactNode
}

export function WidgetSurface({
  accent,
  variant,
  palette,
  dark = true,
  bare = false,
  style,
  className,
  children,
}: WidgetSurfaceProps) {
  const theme = useMemo(() => themeFor(dark), [dark])
  const resolvedAccent = accentOf(accent, palette)
  const paletteHues = paletteById(palette)?.hues

  const identity = useMemo<WidgetIdentity>(
    () => ({
      accent: resolvedAccent,
      variant,
      theme,
      paletteId: palette,
      ramp: (count: number) => rampOf(resolvedAccent, palette, count),
    }),
    [resolvedAccent, variant, theme, palette, paletteHues]
  )

  const face = surfaceStyle(variant, resolvedAccent, theme)

  const outer: CSSProperties = {
    fontFamily: FONT,
    color: theme.text,
    position: 'relative',
    ...face,
    ...(bare ? { background: 'transparent', border: 'none', boxShadow: 'none', padding: 0 } : {}),
    ...style,
  }

  return (
    <IdentityContext.Provider value={identity}>
      <div className={className} style={outer}>
        {face.ruleColor && !bare && (
          <span
            aria-hidden
            style={{
              position: 'absolute',
              left: 0,
              top: 12,
              bottom: 12,
              width: 3,
              borderRadius: 2,
              background: face.ruleColor,
            }}
          />
        )}
        {children}
      </div>
    </IdentityContext.Provider>
  )
}
