"use strict";
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
Object.defineProperty(exports, "__esModule", { value: true });
exports.DESIGN_REMINDER = exports.DESIGN_RULEBOOK = exports.SURFACES = exports.RADIUS = exports.SPACE = exports.TYPE = exports.SURFACE = exports.INK = exports.PALETTES = exports.DATA_HUES = void 0;
/** Twelve data hues. Same hexes as WidgetPalette on the clients. */
exports.DATA_HUES = [
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
];
exports.PALETTES = [
    { id: 'aurora', name: 'Aurora', hues: ['#4C6FFF', '#0CA678', '#5F3DC4'], mood: 'technology, growth, product' },
    { id: 'citrus', name: 'Citrus', hues: ['#E8590C', '#F08C00', '#66A80F'], mood: 'money, food, commerce' },
    { id: 'reef', name: 'Reef', hues: ['#1098AD', '#0B7285', '#4C6FFF'], mood: 'water, travel, calm' },
    { id: 'orchid', name: 'Orchid', hues: ['#9C36B5', '#6741D9', '#D6336C'], mood: 'creative, people, brand' },
    { id: 'ember', name: 'Ember', hues: ['#E03131', '#D6336C', '#E8590C'], mood: 'risk, urgency, incidents' },
    { id: 'forest', name: 'Forest', hues: ['#0CA678', '#66A80F', '#1098AD'], mood: 'health, nature, sustainability' },
    { id: 'dusk', name: 'Dusk', hues: ['#6741D9', '#5F3DC4', '#9C36B5'], mood: 'premium, evening, editorial' },
    { id: 'sunrise', name: 'Sunrise', hues: ['#F08C00', '#E8590C', '#D6336C'], mood: 'energy, sport, warmth' },
    { id: 'harbour', name: 'Harbour', hues: ['#0B7285', '#1098AD', '#5F3DC4'], mood: 'finance, corporate, research' },
    { id: 'meadow', name: 'Meadow', hues: ['#66A80F', '#0CA678', '#F08C00'], mood: 'education, growth plans' },
    { id: 'berry', name: 'Berry', hues: ['#D6336C', '#9C36B5', '#E03131'], mood: 'social, entertainment, fun' },
    { id: 'glacier', name: 'Glacier', hues: ['#4C6FFF', '#1098AD', '#0B7285'], mood: 'data, infrastructure, systems' },
];
exports.INK = {
    light: { primary: '#14171C', secondary: '#5B6472', tertiary: '#8A93A0', rule: '#E5E5E1' },
    dark: { primary: '#F2F5F9', secondary: '#B4BCC9', tertiary: '#8B93A1', rule: '#2A3038' },
};
exports.SURFACE = {
    light: { canvas: '#FFFFFF', card: '#F7F7F5', raised: '#EFEFEC' },
    dark: { canvas: '#0F1216', card: '#171B21', raised: '#20262E' },
};
exports.TYPE = {
    display: 28,
    title: 22,
    heading: 17,
    body: 15,
    label: 13,
    micro: 11,
    weights: [400, 600, 700],
};
exports.SPACE = [4, 8, 12, 16, 24, 32];
exports.RADIUS = [12, 14, 16, 999];
/**
 * The surfaces a widget may declare. Mirrors CardTone in WidgetBlocks.kt and
 * the compiler's art director — one vocabulary, three places, one test.
 */
exports.SURFACES = ['plain', 'tinted', 'hero', 'outline', 'editorial', 'inset'];
/**
 * The rulebook the model reads. Kept tight on purpose: a rulebook that is
 * longer than the answer it describes gets ignored.
 */
exports.DESIGN_RULEBOOK = `DESIGN RULEBOOK — read before composing any visual.

COLOUR
- Twelve data hues, in order: ${exports.DATA_HUES.join(' ')}.
- Twelve palettes, one per reply mood: ${exports.PALETTES.map((p) => `${p.id} (${p.hues.join('/')})`).join('; ')}.
- A reply picks ONE palette and draws every widget's accent from it — name it
  per reply with "palette":"id", or let the subject decide. Never more than
  five hues in one visual, never a rainbow for ordered data.
- A chart uses ONE hue plus neutrals for a single series; two to four for a
  comparison.
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
- Sizes ${exports.TYPE.micro}/${exports.TYPE.label}/${exports.TYPE.body}/${exports.TYPE.heading}/${exports.TYPE.title}/${exports.TYPE.display}px.
  Weight 400 body, 600 labels and headings, 700 only for hero numbers.
- Spacing on the ${exports.SPACE.join('/')}px scale only. Radius ${exports.RADIUS.slice(0, 3).join('/')}px; pills 999.
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
- The visual would repeat the sentence above it. Say it once, in the better form.`;
/**
 * Compact reminder appended to the widget contract. The rulebook is long; this
 * is what the model sees on every single turn.
 */
exports.DESIGN_REMINDER = `Art direction is yours: pick the reply's palette ("palette":"${exports.PALETTES.map((p) => p.id).join('|')}"), and every widget accepts "accent":"#hex" plus "variant":"${exports.SURFACES.join('|')}". Use them to make each block belong to its subject, vary the accent across a reply, and never let two adjacent blocks share an accent or a surface.`;
