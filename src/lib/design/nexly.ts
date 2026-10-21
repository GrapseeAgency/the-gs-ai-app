/**
 * Nexly — the chrome design tokens.
 *
 * ONE SOURCE OF TRUTH for the product shell: canvas, glass, ink, radii, blur,
 * shadows, motion and ambient quality. Before this file existed the warm
 * palette lived as a `const C = {...}` literal inside page.tsx and
 * globals.css was untouched stock shadcn greyscale, so "adjust globally
 * without rewriting screens" was not actually possible.
 *
 * AUTHORED, NOT EXTRACTED. The reference PNGs communicate visual
 * relationships; they do not contain parameters. These values are deliberate
 * starting points to be tuned against screenshots of the running app — do not
 * cite them as pixel-perfect reads from the artwork.
 *
 * NOT THE WIDGET LANGUAGE. This governs chrome (nav, composer, sheets, cards
 * the app draws). Assistant-generated content is governed by
 * src/lib/design/rulebook.ts, which is mirrored to the web GenUI renderer and
 * to Android's WidgetPalette ("one vocabulary, three renderers"). The two
 * layers must be reconciled deliberately — glass borders + soft shadows and
 * gradient atmosphere are both required here and both banned there.
 */

/* ------------------------------------------------------------------ palette */

const LIGHT = {
  /** Window canvas — the warm field everything sits in. */
  canvas: '#F7EAE5',
  /** Slightly deeper peach for the secondary/grouped canvas. */
  canvasDeep: '#F6D8CA',
  /** Rose undertone used only in gradient stops, never as a fill. */
  rose: '#F3C7C7',

  /** The single action hue: CTA, send, selected, active nav. Nothing else. */
  accent: '#FF914D',
  /** Pressed / gradient far stop. */
  accentDeep: '#F36F2C',
  /** Gradient near stop and specular sweep. */
  accentLift: '#FFAB70',

  text: '#292323',
  text2: '#776C6A',
  /** Hairline dividers and quiet outlines on opaque surfaces. */
  outline: '#F0D2C6',

  danger: '#D95C55',
  success: '#0CA678',
} as const

/**
 * Dark is the same environment at night, not an inversion: warm charcoal
 * base, plum surfaces, muted aubergine raise, restrained peach illumination.
 * Never pure black, never pure white text — the rulebook already agrees.
 */
const DARK = {
  canvas: '#171317',
  canvasDeep: '#1D1720',
  rose: '#2A1F2E',

  accent: '#FF914D',
  accentDeep: '#E8763B',
  accentLift: '#FFB380',

  text: '#F5EDE9',
  text2: '#B9A8A6',
  outline: 'rgba(255, 255, 255, 0.12)',

  danger: '#E2726A',
  success: '#22C09A',
} as const

/* -------------------------------------------------------------------- glass */

/**
 * Glass is chrome only. Anything carrying 15px body text at reading length
 * gets an opaque surface instead — the reference gets this right and it is
 * the one rule that keeps the atmosphere from becoming a legibility tax.
 */
const GLASS = {
  light: {
    /** Sheets, popovers, dialogs, docks. */
    elevated: 'rgba(255, 255, 255, 0.55)',
    /** Standard cards and panels. */
    primary: 'rgba(255, 255, 255, 0.38)',
    /** Quiet wells inside a glass surface. */
    sunken: 'rgba(255, 255, 255, 0.22)',
    border: 'rgba(255, 255, 255, 0.65)',
    /** Internal top highlight. A gradient, never a thick white outline. */
    rim: 'rgba(255, 255, 255, 0.75)',
  },
  dark: {
    elevated: 'rgba(255, 255, 255, 0.10)',
    primary: 'rgba(255, 255, 255, 0.06)',
    sunken: 'rgba(0, 0, 0, 0.18)',
    border: 'rgba(255, 255, 255, 0.14)',
    rim: 'rgba(255, 255, 255, 0.20)',
  },
} as const

/** Blur is a radius, not a mood. Three steps, no ad-hoc values. */
const BLUR = { standard: 20, elevated: 28, sheet: 32 } as const

/* ----------------------------------------------------------------- geometry */

const RADIUS = {
  control: 14,
  controlLg: 18,
  card: 22,
  cardLg: 26,
  sheet: 32,
  frame: 36,
  pill: 999,
} as const

/** Broad, soft, warm-tinted, low contrast. Never grey. */
const SHADOW = {
  light: {
    /** Elevation for cards and controls. */
    soft: '0 2px 10px rgba(120, 62, 30, 0.06)',
    /** Sheets, dialogs, floating dock. */
    lift: '0 12px 40px rgba(120, 62, 30, 0.10)',
    /** Primary CTA only — the one place a shadow carries hue. */
    glow: '0 8px 24px rgba(255, 145, 77, 0.32)',
  },
  dark: {
    soft: '0 2px 10px rgba(0, 0, 0, 0.24)',
    lift: '0 12px 40px rgba(0, 0, 0, 0.42)',
    glow: '0 8px 24px rgba(255, 145, 77, 0.22)',
  },
} as const

/* ------------------------------------------------------------------- motion */

/**
 * Mood: heavenly, weightless, liquid, luminous. Slow and organic.
 * No bounce, no spring overshoot, no synchronized loops — every ambient
 * element gets its own phase so the field never pulses as one unit.
 */
const MOTION = {
  /** Feedback on touch — must stay snappy even when ambient motion is off. */
  press: 180,
  selection: 200,
  /** Sheets, drawers, popovers. */
  panel: 320,
  /** Route changes. */
  screen: 420,
  /** Orb idle breathing, 3–6s band. */
  orbBreath: 4500,
  /** Ambient bubble drift, 12–30s band. Per-element variance applied on top. */
  bubbleDrift: 18000,
  /** Easing that decelerates without ever returning. */
  ease: 'cubic-bezier(0.22, 0.61, 0.36, 1)',
  easeOut: 'cubic-bezier(0.16, 0.84, 0.44, 1)',
} as const

/* ----------------------------------------------------------------- ambient */

/**
 * Adaptive quality. The identity must survive in all three tiers — the
 * atmosphere is a warm gradient with pearlescent shapes, and only the
 * animation is negotiable.
 */
const AMBIENT = {
  /** Decorative elements by viewport, per the 5–8 / 3–5 targets. */
  count: { desktop: 6, mobile: 4 },
  tiers: {
    /** Glass, several bubbles, drift, occasional glints, subtle parallax. */
    high: { bubbles: 6, glints: 2, drift: true, parallax: true, glint: true },
    /** Fewer bubbles, slower, less frequent glints, reduced parallax. */
    standard: { bubbles: 4, glints: 1, drift: true, parallax: true, glint: false },
    /** Static gradients and static pearlescent shapes. No particles. */
    low: { bubbles: 3, glints: 0, drift: false, parallax: false, glint: false },
  },
} as const

/* ------------------------------------------------------------------- public */

export type NexlyTheme = 'light' | 'dark'

export const nx = {
  light: LIGHT,
  dark: DARK,
  glass: GLASS,
  blur: BLUR,
  radius: RADIUS,
  shadow: SHADOW,
  motion: MOTION,
  ambient: AMBIENT,
} as const

/** Drop-in replacement for the inline `const C` the app used to hardcode. */
export function palette(theme: NexlyTheme) {
  return theme === 'dark' ? DARK : LIGHT
}

export function glass(theme: NexlyTheme) {
  return GLASS[theme]
}

export function shadow(theme: NexlyTheme) {
  return SHADOW[theme]
}

/**
 * CSS custom properties for the shell. Applied once at the root so any
 * component — including plain CSS and the ambient background layers — reads
 * the same values instead of re-declaring literals.
 */
export function nexlyCssVars(theme: NexlyTheme): Record<string, string> {
  const c = palette(theme)
  const g = glass(theme)
  const s = shadow(theme)
  return {
    '--nx-canvas': c.canvas,
    '--nx-canvas-deep': c.canvasDeep,
    '--nx-rose': c.rose,
    '--nx-accent': c.accent,
    '--nx-accent-deep': c.accentDeep,
    '--nx-accent-lift': c.accentLift,
    '--nx-text': c.text,
    '--nx-text2': c.text2,
    '--nx-outline': c.outline,
    '--nx-danger': c.danger,
    '--nx-success': c.success,

    '--nx-glass-primary': g.primary,
    '--nx-glass-elevated': g.elevated,
    '--nx-glass-sunken': g.sunken,
    '--nx-glass-border': g.border,
    '--nx-glass-rim': g.rim,

    '--nx-blur-standard': `${BLUR.standard}px`,
    '--nx-blur-elevated': `${BLUR.elevated}px`,
    '--nx-blur-sheet': `${BLUR.sheet}px`,

    '--nx-r-control': `${RADIUS.control}px`,
    '--nx-r-control-lg': `${RADIUS.controlLg}px`,
    '--nx-r-card': `${RADIUS.card}px`,
    '--nx-r-card-lg': `${RADIUS.cardLg}px`,
    '--nx-r-sheet': `${RADIUS.sheet}px`,
    '--nx-r-frame': `${RADIUS.frame}px`,

    '--nx-shadow-soft': s.soft,
    '--nx-shadow-lift': s.lift,
    '--nx-shadow-glow': s.glow,

    '--nx-ease': MOTION.ease,
    '--nx-ease-out': MOTION.easeOut,
  }
}

/**
 * The one reusable glass recipe. Note the deliberate pairing of a hairline
 * border with a soft shadow — the rulebook bans that combination for widget
 * content, and chrome needs it. Resolving that contradiction is an explicit
 * decision, not something to discover screen by screen.
 */
export function glassStyle(
  theme: NexlyTheme,
  level: 'primary' | 'elevated' | 'sunken' = 'primary',
) {
  const g = glass(theme)
  const s = shadow(theme)
  const fill = level === 'elevated' ? g.elevated : level === 'sunken' ? g.sunken : g.primary
  return {
    background: fill,
    backdropFilter: `blur(${level === 'elevated' ? BLUR.elevated : BLUR.standard}px)`,
    WebkitBackdropFilter: `blur(${level === 'elevated' ? BLUR.elevated : BLUR.standard}px)`,
    border: `1px solid ${g.border}`,
    borderRadius: RADIUS.card,
    boxShadow: level === 'elevated' ? s.lift : s.soft,
  } as const
}
