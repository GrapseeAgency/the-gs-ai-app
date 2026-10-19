/**
 * The design rulebook.
 *
 * This is the GS equivalent of the guidelines Claude loads before it draws
 * anything: a fixed set of colours, sizes, dark-mode rules and hard bans that
 * the model reads BEFORE composing a visual. It is not a template and it is
 * not a layout — it is the vocabulary the model composes with. Without it the
 * model defaults to grey boxes on grey cards, which is exactly what "every
 * widget looks the same" means.
 *
 * The token values are duplicated (deliberately) from
 * android/.../ui/chat/content/WidgetBlocks.kt (WidgetPalette) so a colour the
 * model names renders identically in the native widgets and in ```html.
 */

/** Twelve data hues. Same hexes as WidgetPalette on the clients. */
export const DATA_HUES = [
  '#4C6FFF', // azure
  '#0CA678', // jade
  '#F08C00', // amber
  '#E8590C', // tangerine
  '#9C36B5', // orchid
  '#1098AD', // teal
  '#D6336C', // rose
  '#6741D9', // violet
  '#66A80F', // olive
  '#E03131', // crimson
  '#0B7285', // deep teal
  '#5F3DC4', // indigo
] as const

export const INK = {
  light: { primary: '#14171C', secondary: '#5B6472', tertiary: '#8A93A0', rule: '#E5E5E1' },
  dark: { primary: '#F2F5F9', secondary: '#B4BCC9', tertiary: '#8B93A1', rule: '#2A3038' },
} as const

export const SURFACE = {
  light: { canvas: '#FFFFFF', card: '#F7F7F5', raised: '#EFEFEC' },
  dark: { canvas: '#0F1216', card: '#171B21', raised: '#20262E' },
} as const

export const TYPE = {
  display: 28,
  title: 22,
  heading: 17,
  body: 15,
  label: 13,
  micro: 11,
  weights: [400, 600, 700],
} as const

export const SPACE = [4, 8, 12, 16, 24, 32] as const
export const RADIUS = [12, 14, 16, 999] as const

/**
 * The surfaces a widget may declare. Mirrors CardTone in WidgetBlocks.kt and
 * the compiler's art director — one vocabulary, three places, one test.
 */
export const SURFACES = ['plain', 'tinted', 'hero', 'outline', 'editorial', 'inset'] as const
export type Surface = (typeof SURFACES)[number]

/**
 * The rulebook the model reads. Kept tight on purpose: a rulebook that is
 * longer than the answer it describes gets ignored.
 */
export const DESIGN_RULEBOOK = `DESIGN RULEBOOK — read before composing any visual.

COLOUR
- Data hues, in order: ${DATA_HUES.join(' ')}.
- A chart uses ONE hue plus neutrals for a single series; two to four hues for
  a comparison; never more than five in one visual, never a rainbow for ordered
  data (that is what sequential light-to-dark is for).
- Accent is a decision, not a default: name it per widget with "accent":"#hex".
  Pick the hue that matches the subject (money tangerine, health jade, risk
  crimson, growth azure) rather than reusing the same one every time.
- The compiler assigns a subject-fit hue when you omit one, but it only reads
  the text; you know the intent. Name the accent when it matters, and never
  let two adjacent widgets share one.
- Never put a coloured fill behind coloured text of the same hue.
- Dark mode is a real target: surfaces are rgba(255,255,255,0.06), never pure
  black; text never pure #FFFFFF; hairlines at rgba(255,255,255,0.10).

TYPE AND SPACE
- Sizes ${TYPE.micro}/${TYPE.label}/${TYPE.body}/${TYPE.heading}/${TYPE.title}/${TYPE.display}px.
  Weight 400 body, 600 labels and headings, 700 only for hero numbers.
- Spacing on the ${SPACE.join('/')}px scale only. Radius ${RADIUS.slice(0, 3).join('/')}px; pills 999.
- One idea per visual. If it needs a paragraph of explanation, it is two visuals.

SURFACES (the "variant" key, pick by role)
- hero      one KPI or gauge — a single number that owns the block.
- tinted    a light accent wash — charts, card grids, media.
- editorial no card at all: transparent, a 2px accent rule on the left. Line and
            area charts, timelines, panels. It reads as a figure in an article.
- inset     a card inside a card — forms, quizzes, calculators, app surfaces.
- outline   hairline only — progress, compare, follow-ups, quiet controls.
- plain     one calm solid surface — tables, dense lists, media.
- Never use the same variant twice in a row. Three tinted cards in one reply is
  the exact failure this vocabulary exists to prevent.

COMPOSITION
- A reply is a LAYOUT, not one widget: usually a lead sentence, one hero visual,
  then at most two supporting blocks. Never stack more than three.
- Never place two blocks of the same kind next to each other. Alternate the
  rhythm: numbers -> chart -> table.
- Never give two adjacent blocks the same accent or the same surface. Vary
  them deliberately.
- Put prose between blocks. A wall of cards is not an answer.

BANNED
- Emoji anywhere. ASCII charts, box-drawing bars, "▇▇▇" meters.
- More than three accent colours in one answer.
- A shadow AND a border on the same element.
- Fixed pixel widths wider than a phone (max 360px content in html).
- Tables wider than 8 columns, more than 7 bars in a bar chart, more than 5
  series in a line chart.
- Decorative images, stock photos, gradients used as filler.

WHEN A VISUAL IS RIGHT
- The user asks to see something ("show", "chart", "compare", "diagram").
- The answer is a comparison, a trend, a distribution, a process, or a set of
  numbers that is easier to scan than to read.
- The user will want to change a value and see the result (use calc or form).
WHEN IT IS WRONG
- A one-line fact, an opinion, code, a greeting, a definition. Answer in prose.
- The visual would repeat the sentence above it. Say it once, in the better form.`

/**
 * Compact reminder appended to the widget contract. The rulebook is long; this
 * is what the model sees on every single turn.
 */
export const DESIGN_REMINDER = `Art direction is yours: every widget accepts "accent":"#hex" and "variant":"${SURFACES.join('|')}". Use them to make each block look like it belongs to its subject, vary the accent across a reply, and never let two adjacent blocks share an accent or a surface.`
