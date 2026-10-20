/**
 * The GenUI theme tokens — the web twin of the design rulebook.
 *
 * The values are the rulebook's values (src/lib/design/rulebook.ts), which are
 * the Android WidgetPalette's values. One vocabulary, three renderers. A widget
 * compiled on the backend names an accent/variant/palette; this file is where
 * the web client turns those names into pixels.
 */

import { DATA_HUES, INK, PALETTES, SURFACE, type Palette, type Surface } from '@/lib/design/rulebook'

export { DATA_HUES, PALETTES, SURFACES } from '@/lib/design/rulebook'
export type { Palette, Surface }

/** The resolved theme a widget renders under. Dark is the page default. */
export interface GsTheme {
  dark: boolean
  text: string
  text2: string
  text3: string
  canvas: string
  card: string
  raised: string
  border: string
}

export function themeFor(dark: boolean): GsTheme {
  const ink = dark ? INK.dark : INK.light
  const surf = dark ? SURFACE.dark : SURFACE.light
  return {
    dark,
    text: ink.primary,
    text2: ink.secondary,
    text3: ink.tertiary,
    canvas: surf.canvas,
    card: surf.card,
    raised: surf.raised,
    border: ink.rule,
  }
}

/** Hue by palette position (1-12) — mirrors the --gs-hue-N custom properties. */
export function hueAt(index: number): string {
  return DATA_HUES[((index % DATA_HUES.length) + DATA_HUES.length) % DATA_HUES.length]
}

export function paletteById(id: string | undefined): Palette | null {
  if (!id) return null
  return PALETTES.find((p) => p.id === id.toLowerCase()) ?? null
}

/** A safe accent: the named hex, or the palette lead, or azure. */
export function accentOf(accent: string | undefined, paletteId: string | undefined): string {
  if (accent && /^#[0-9a-fA-F]{6}$/.test(accent)) return accent.toLowerCase()
  const palette = paletteById(paletteId)
  return palette ? palette.hues[0] : DATA_HUES[0]
}

/** The palette's hues with the accent first — the series colour ramp. */
export function rampOf(accent: string, paletteId: string | undefined, count: number): string[] {
  const palette = paletteById(paletteId)
  const rest = palette ? palette.hues.filter((h) => h.toLowerCase() !== accent) : []
  const pool = [accent, ...rest, ...DATA_HUES.filter((h) => h.toLowerCase() !== accent)]
  return Array.from({ length: Math.max(1, count) }, (_, i) => pool[i % pool.length])
}

/** Alpha compositing on any hex — the tinted surface wash, chart fills. */
export function alpha(hex: string, a: number): string {
  const value = hex.replace('#', '')
  const r = parseInt(value.slice(0, 2), 16)
  const g = parseInt(value.slice(2, 4), 16)
  const b = parseInt(value.slice(4, 6), 16)
  return `rgba(${r},${g},${b},${a})`
}

/** Perceived luminance 0-1, so text on an accent stays readable. */
export function luminance(hex: string): number {
  const value = hex.replace('#', '')
  const channel = (i: number) => {
    const c = parseInt(value.slice(i, i + 2), 16) / 255
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)
  }
  return 0.2126 * channel(0) + 0.7152 * channel(2) + 0.0722 * channel(4)
}

/** Black or white text on a coloured fill — never hue-on-hue. */
export function inkOn(hex: string): string {
  return luminance(hex) > 0.4 ? '#14171C' : '#FFFFFF'
}

export const TYPE = {
  display: 28,
  title: 22,
  heading: 17,
  body: 15,
  label: 13,
  micro: 11,
} as const

export const SPACE = [4, 8, 12, 16, 24, 32] as const
export const RADIUS = { md: 12, lg: 14, xl: 16, pill: 999 } as const
export const FONT =
  "-apple-system, 'SF Pro Text', 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif"
