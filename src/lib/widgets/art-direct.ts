/**
 * Deterministic art direction.
 *
 * The model may choose `accent` and `variant` for every widget, and when it
 * does those choices win. But models routinely omit both — the live probe
 * caught whole replies of default-blue cards. This pass makes a designed
 * result the floor, not the hope: a subject-fit hue, a surface that matches
 * the widget's role, no accent or surface repeated next to itself, and no
 * derived accent reused from the previous turn.
 *
 * It is deterministic by construction: the same reply and the same prior text
 * always compile to the same colours. No randomness, so tests and replays are
 * stable. The model still owns intent — this only breaks ties and fills gaps.
 */

import { DATA_HUES, PALETTES, SURFACES, type Palette, type Surface } from '../design/rulebook'
import type { WidgetKind } from './catalog'

export interface ArtDirection {
  accent: string
  variant: Surface
  /** The palette this reply draws from. Clients use it for coherent theming. */
  palette?: string
}

export interface ArtInput {
  kind: WidgetKind
  payload: Record<string, unknown>
  /** The widget directly above this one in the same reply, if any. */
  prior?: ArtDirection | null
  /** Every direction already used in this reply, so derived faces stay distinct. */
  seen?: readonly ArtDirection[]
  /** The previous assistant turn, used to avoid repeating its palette. */
  priorText?: string
}

/**
 * Subject lexicon. Ordered: the first family with a matching word wins, so
 * "customer churn" is risk, not people, and "energy costs" is money, not
 * energy. Every hue is a member of DATA_HUES — the same hexes the clients
 * render, so the sentence "money tangerine" in the rulebook is literally true.
 */
export const SUBJECT_HUES: ReadonlyArray<{
  keywords: readonly string[]
  hues: readonly string[]
}> = [
  {
    keywords: ['risk', 'churn', 'loss', 'losses', 'error', 'incident', 'outage', 'threat', 'fraud', 'debt', 'security', 'failure', 'downtime', 'breach', 'attack', 'bug', 'defect'],
    hues: ['#E03131', '#D6336C', '#E8590C'], // crimson, rose, tangerine
  },
  {
    keywords: ['revenue', 'profit', 'cost', 'price', 'budget', 'sales', 'income', 'spend', 'tax', 'loan', 'mortgage', 'payment', 'cash', 'margin', 'salary', 'invoice', 'pricing', 'earning', 'valuation', 'funding', 'burn', 'mrr', 'arr'],
    hues: ['#E8590C', '#F08C00', '#66A80F'], // tangerine, amber, olive
  },
  {
    keywords: ['health', 'medical', 'patient', 'fitness', 'sleep', 'calorie', 'wellness', 'recovery', 'heart', 'blood', 'glucose', 'nutrition', 'diagnosis', 'treatment'],
    hues: ['#0CA678', '#1098AD', '#66A80F'], // jade, teal, olive
  },
  {
    keywords: ['growth', 'increase', 'adoption', 'user', 'traffic', 'engagement', 'retention', 'conversion', 'signup', 'subscriber', 'download', 'active', 'mau', 'dau'],
    hues: ['#4C6FFF', '#0CA678', '#5F3DC4'], // azure, jade, indigo
  },
  {
    keywords: ['schedule', 'timeline', 'deadline', 'milestone', 'plan', 'roadmap', 'sprint', 'calendar', 'quarter'],
    hues: ['#6741D9', '#5F3DC4', '#1098AD'], // violet, indigo, teal
  },
  {
    keywords: ['route', 'map', 'trip', 'location', 'delivery', 'distance', 'travel', 'commute', 'shipping', 'logistics', 'store', 'city'],
    hues: ['#1098AD', '#4C6FFF', '#66A80F'], // teal, azure, olive
  },
  {
    keywords: ['energy', 'carbon', 'emission', 'climate', 'water', 'temperature', 'renewable', 'electricity', 'fuel', 'co2'],
    hues: ['#66A80F', '#0CA678', '#0B7285'], // olive, jade, deep teal
  },
  {
    keywords: ['team', 'employee', 'hiring', 'headcount', 'customer', 'audience', 'staff', 'talent'],
    hues: ['#9C36B5', '#6741D9', '#D6336C'], // orchid, violet, rose
  },
  {
    keywords: ['product', 'inventory', 'stock', 'order', 'shipment', 'catalog', 'sku', 'warehouse', 'supply'],
    hues: ['#F08C00', '#E8590C', '#66A80F'], // amber, tangerine, olive
  },
  {
    keywords: ['course', 'student', 'learning', 'score', 'grade', 'quiz', 'exam', 'lesson'],
    hues: ['#5F3DC4', '#6741D9', '#9C36B5'], // indigo, violet, orchid
  },
]

/** Charts that read better as a figure than as a filled card. */
const EDITORIAL_CHARTS = new Set(['line', 'area', 'stackedArea', 'scatter', 'bubble', 'candlestick'])

/**
 * Subject → palette families. One step above the hue lexicon: the reply picks
 * a three-hue FAMILY first, then the widgets pick hues inside it, so a reply
 * reads as one designed object instead of three unrelated cards. Deterministic
 * tie-break: the turn fence count rotates within the family so two consecutive
 * replies on the same subject still change their face.
 */
const SUBJECT_PALETTES: ReadonlyArray<{ keywords: readonly string[]; palettes: readonly string[] }> = [
  { keywords: ['risk', 'churn', 'loss', 'losses', 'error', 'incident', 'outage', 'threat', 'fraud', 'debt', 'security', 'failure', 'downtime', 'breach', 'attack', 'bug', 'defect'], palettes: ['ember', 'berry'] },
  { keywords: ['revenue', 'profit', 'cost', 'price', 'budget', 'sales', 'income', 'spend', 'tax', 'loan', 'mortgage', 'payment', 'cash', 'margin', 'salary', 'invoice', 'pricing', 'earning', 'valuation', 'funding', 'burn', 'mrr', 'arr'], palettes: ['citrus', 'harbour'] },
  { keywords: ['health', 'medical', 'patient', 'fitness', 'sleep', 'calorie', 'wellness', 'recovery', 'heart', 'blood', 'glucose', 'nutrition', 'diagnosis', 'treatment'], palettes: ['forest', 'reef'] },
  { keywords: ['growth', 'increase', 'adoption', 'user', 'traffic', 'engagement', 'retention', 'conversion', 'signup', 'subscriber', 'download', 'active', 'mau', 'dau'], palettes: ['aurora', 'meadow'] },
  { keywords: ['schedule', 'timeline', 'deadline', 'milestone', 'plan', 'roadmap', 'sprint', 'calendar', 'quarter'], palettes: ['dusk', 'harbour'] },
  { keywords: ['route', 'map', 'trip', 'location', 'delivery', 'distance', 'travel', 'commute', 'shipping', 'logistics', 'store', 'city'], palettes: ['reef', 'glacier'] },
  { keywords: ['energy', 'carbon', 'emission', 'climate', 'water', 'temperature', 'renewable', 'electricity', 'fuel', 'co2'], palettes: ['sunrise', 'forest'] },
  { keywords: ['team', 'employee', 'hiring', 'headcount', 'customer', 'audience', 'staff', 'talent'], palettes: ['orchid', 'berry'] },
  { keywords: ['product', 'inventory', 'stock', 'order', 'shipment', 'catalog', 'sku', 'warehouse', 'supply'], palettes: ['citrus', 'glacier'] },
  { keywords: ['course', 'student', 'learning', 'score', 'grade', 'quiz', 'exam', 'lesson'], palettes: ['dusk', 'meadow'] },
]

/**
 * The reply's palette. Explicit subject match wins; otherwise the full
 * twelve-palette wheel rotates by the prior turn's fence count, skipping any
 * palette whose lead hue the previous turn already wore.
 */
export function paletteFor(text: string, priorText = ''): Palette {
  const words = new Set(text.split(/[^a-z0-9]+/).filter(Boolean))
  const matched: string[] = []
  for (const family of SUBJECT_PALETTES) {
    if (family.keywords.some((word) => words.has(word) || words.has(`${word}s`))) {
      matched.push(...family.palettes)
      break
    }
  }
  const pool = matched.length > 0 ? PALETTES.filter((p) => matched.includes(p.id)) : PALETTES
  const start = fenceCount(priorText) % pool.length
  const avoid = accentsIn(priorText)
  for (let i = 0; i < pool.length; i++) {
    const palette = pool[(start + i) % pool.length]
    if (!avoid.has(palette.hues[0].toLowerCase())) return palette
  }
  return pool[start % pool.length]
}

/** The style keys a payload may already carry, excluded from subject text. */
const STYLE_KEYS = new Set(['accent', 'variant'])
const URL_KEYS = new Set(['url', 'image', 'thumb', 'src', 'href', 'icon'])

export function isSurface(value: unknown): value is Surface {
  return typeof value === 'string' && (SURFACES as readonly string[]).includes(value)
}

/** Every accent already used in the previous assistant turn. */
export function accentsIn(text: string): Set<string> {
  const out = new Set<string>()
  for (const match of text.matchAll(/"accent"\s*:\s*"#([0-9a-fA-F]{6})"/g)) {
    out.add(`#${match[1].toLowerCase()}`)
  }
  return out
}

/**
 * One widget's direction. Model-supplied values survive unless they repeat the
 * block directly above them — the one collision the user can actually see.
 */
export function directWidgetArt({ kind, payload, prior = null, seen = [], priorText = '' }: ArtInput): ArtDirection {
  const text = subjectText(payload)
  const seed = hash(`${kind}|${text}`)
  // The reply's palette decides the candidate hues; the subject lexicon has
  // already chosen WHICH palette. Emitted accents are lowercase, matching the
  // validator's normalisation.
  const palette = paletteFor(text, priorText)
  const candidates = palette.hues.map((hue) => hue.toLowerCase())
  const turn = fenceCount(priorText)
  const used = accentsIn(priorText)
  const seenAccents = new Set(seen.map((direction) => direction.accent))
  const seenVariants = new Set(seen.map((direction) => direction.variant))

  const modelAccent = typeof payload.accent === 'string' ? normalizeHex(payload.accent) : null
  const derivedAccent = chooseHue({
    candidates,
    start: (seed + turn) % candidates.length,
    previous: prior?.accent ?? null,
    used,
    seen: seenAccents,
  })

  const modelVariant = isSurface(payload.variant) ? payload.variant : null
  const order = roleSurfaces(kind, payload)
  const derivedVariant =
    order.find((surface) => surface !== prior?.variant && !seenVariants.has(surface)) ??
    order.find((surface) => surface !== prior?.variant) ??
    order[0]

  return {
    accent: modelAccent && modelAccent !== prior?.accent ? modelAccent : derivedAccent,
    variant: modelVariant && modelVariant !== prior?.variant ? modelVariant : derivedVariant,
    palette: palette.id,
  }
}

function chooseHue(args: {
  candidates: readonly string[]
  start: number
  previous: string | null
  used: Set<string>
  seen: Set<string>
}): string {
  const { candidates, start, previous, used, seen } = args
  // First pass: skip every block in this reply and the previous turn.
  for (let i = 0; i < candidates.length; i++) {
    const hue = candidates[(start + i) % candidates.length]
    if (hue !== previous && !used.has(hue) && !seen.has(hue)) return hue
  }
  // The subject family is exhausted: widen to the full palette rather than
  // replay the previous turn or an earlier block. A money answer with three
  // money widgets can burn all three family hues by itself.
  for (const hue of DATA_HUES.map((value) => value.toLowerCase())) {
    if (hue !== previous && !used.has(hue) && !seen.has(hue)) return hue
  }
  // The palette is exhausted: never repeat the adjacent block, at least.
  for (let i = 0; i < candidates.length; i++) {
    const hue = candidates[(start + i) % candidates.length]
    if (hue !== previous) return hue
  }
  return candidates[start % candidates.length]
}

/**
 * The surface order for a role. The second entry is the fallback when the
 * first would repeat the card above; both are intentional, not random.
 */
function roleSurfaces(kind: WidgetKind, payload: Record<string, unknown>): readonly Surface[] {
  switch (kind) {
    case 'chart':
      return EDITORIAL_CHARTS.has(String(payload.type ?? ''))
        ? ['editorial', 'tinted']
        : ['tinted', 'editorial']
    case 'stat':
      return ['hero', 'tinted']
    case 'gauge':
      return ['hero', 'inset']
    case 'table':
      return ['plain', 'inset']
    case 'compare':
      return ['outline', 'editorial']
    case 'progress':
      return ['outline', 'tinted']
    case 'timeline':
      return ['editorial', 'outline']
    case 'quiz':
      return ['inset', 'tinted']
    case 'form':
      return ['inset', 'plain']
    case 'carousel':
      return ['tinted', 'plain']
    case 'cards':
      return ['plain', 'tinted']
    case 'followups':
      return ['outline', 'plain']
    case 'media':
      return ['plain', 'tinted']
    case 'actions':
      return ['outline', 'plain']
    case 'panel':
      return ['editorial', 'outline']
    case 'voice':
      return ['outline', 'tinted']
    case 'calc':
      return ['inset', 'plain']
    case 'app':
      return ['inset', 'plain']
    case 'tabs':
      return ['plain', 'tinted']
    case 'accordion':
      return ['outline', 'tinted']
    case 'steps':
      return ['editorial', 'outline']
    case 'menu':
      return ['outline', 'plain']
    case 'chips':
      return ['outline', 'tinted']
    case 'rating':
      return ['inset', 'outline']
    case 'poll':
      return ['tinted', 'outline']
    case 'diff':
      return ['plain', 'inset']
    case 'tree':
      return ['editorial', 'plain']
    case 'flashcards':
      return ['inset', 'tinted']
    case 'checklist':
      return ['outline', 'tinted']
    // v3 — worlds and runtimes own dark inset stages by default
    case 'sim':
      return ['inset', 'hero']
    case 'stage3d':
      return ['inset', 'hero']
    case 'canvas':
      return ['inset', 'plain']
    case 'editor':
      return ['inset', 'plain']
    case 'terminal':
      return ['inset', 'outline']
    default:
      return ['tinted', 'plain']
  }
}

/**
 * Title, labels and short strings, flattened. Skips style keys, URLs and
 * nested depth beyond three so a 200-row table cannot dominate the read.
 */
function subjectText(payload: Record<string, unknown>): string {
  const parts: string[] = []
  const walk = (value: unknown, depth: number) => {
    if (parts.length > 60 || depth > 3) return
    if (typeof value === 'string') {
      if (!/^https?:/i.test(value)) parts.push(value)
      return
    }
    if (Array.isArray(value)) {
      for (const item of value) walk(item, depth + 1)
      return
    }
    if (value && typeof value === 'object') {
      for (const [key, item] of Object.entries(value as Record<string, unknown>)) {
        if (STYLE_KEYS.has(key) || URL_KEYS.has(key)) continue
        walk(item, depth + 1)
      }
    }
  }
  walk(payload, 0)
  return parts.join(' ').toLowerCase().slice(0, 1200)
}

function fenceCount(text: string): number {
  return (text.match(/^```[a-zA-Z][\w-]*[ \t]*$/gm) ?? []).length
}

function normalizeHex(raw: string): string | null {
  const value = raw.trim().replace(/^#/, '')
  return /^[0-9a-fA-F]{6}$/.test(value) ? `#${value.toLowerCase()}` : null
}

/** FNV-1a: small, stable, dependency-free. */
function hash(text: string): number {
  let h = 0x811c9dc5
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i)
    h = Math.imul(h, 0x01000193) >>> 0
  }
  return h >>> 0
}
