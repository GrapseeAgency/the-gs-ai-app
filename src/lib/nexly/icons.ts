/**
 * Nexly icon vocabulary.
 *
 * 5,184 Tabler outline glyphs, rendered as glass chips. The model addresses
 * them by kebab-case name (``:sunrise:`` inline, or ``"icon":"sunrise"`` in a
 * plan row); the renderer resolves aliases, then falls back to a sparkle.
 */

import NAMES from './icon-names.json'

export const NEXLY_ICON_NAMES = NAMES as string[]
export const NEXLY_ICON_COUNT = NEXLY_ICON_NAMES.length
const NAME_SET = new Set(NEXLY_ICON_NAMES)

/**
 * Human/model aliases for the semantic names a planner naturally reaches for.
 * The first matching alias wins; every value exists in the Tabler set.
 */
export const ICON_ALIASES: Record<string, string> = {
  // study / class
  study: 'book-2',
  studying: 'book-2',
  class: 'book-2',
  classes: 'book-2',
  lecture: 'book-2',
  reading: 'book-2',
  homework: 'file-text',
  assignment: 'file-text',
  assignments: 'file-text',
  notes: 'notebook',
  // work
  work: 'device-laptop',
  laptop: 'device-laptop',
  computer: 'device-laptop',
  coding: 'code',
  email: 'mail',
  meeting: 'users',
  // food
  food: 'tools-kitchen-2',
  lunch: 'tools-kitchen-2',
  dinner: 'tools-kitchen-2',
  breakfast: 'sunrise',
  utensils: 'tools-kitchen-2',
  'fork-knife': 'tools-kitchen-2',
  cafe: 'coffee',
  coffee: 'coffee',
  // health
  workout: 'run',
  exercise: 'run',
  gym: 'barbell',
  run: 'run',
  running: 'run',
  walk: 'walk',
  yoga: 'stretching',
  health: 'heart',
  // rest
  sleep: 'moon',
  rest: 'moon',
  winddown: 'moon',
  'wind-down': 'moon',
  night: 'moon',
  // play
  game: 'device-gamepad-2',
  games: 'device-gamepad-2',
  gaming: 'device-gamepad-2',
  gamepad: 'device-gamepad-2',
  hobby: 'palette',
  hobbies: 'palette',
  music: 'music',
  // thinking
  think: 'sparkles',
  thinking: 'sparkles',
  reasoning: 'brain',
  effort: 'settings',
  gear: 'settings',
  cog: 'settings',
  // closing
  muscle: 'flame',
  flex: 'flame',
  biceps: 'flame',
  'biceps-flexed': 'flame',
  strong: 'flame',
  motivate: 'flame',
  motivation: 'flame',
  // misc
  sparkle: 'sparkles',
  sparkles: 'sparkles',
  sun: 'sunrise',
  sunrise: 'sunrise',
  morning: 'sunrise',
  book: 'book-2',
  'book-open': 'book-2',
  moon: 'moon',
  star: 'star',
  check: 'circle-check',
  done: 'circle-check',
  warning: 'alert-triangle',
  calendar: 'calendar',
  clock: 'clock',
  time: 'clock',
  target: 'target',
  idea: 'bulb',
  lightbulb: 'bulb',
  heart: 'heart',
  smile: 'mood-smile',
  person: 'user',
  user: 'user',
  home: 'home',
  school: 'school',
  travel: 'plane',
  plane: 'plane',
  map: 'map',
  phone: 'device-mobile',
  mobile: 'device-mobile',
}

/** Normalise arbitrary model output into a Tabler kebab-case id. */
export function canonicalIconName(raw: unknown): string | null {
  if (typeof raw !== 'string') return null
  let name = raw
    .trim()
    .toLowerCase()
    .replace(/^[:[]+|[:\]]+$/g, '')
    .replace(/^(icon|ti|tabler|gs)[-_:]/g, '')
    .replace(/[_\s]+/g, '-')
    .replace(/-+/g, '-')
  if (!name) return null
  if (NAME_SET.has(name)) return name
  const alias = ICON_ALIASES[name]
  if (alias && NAME_SET.has(alias)) return alias
  return null
}

/** Never returns null: unknown names become the sparkle. */
export function iconNameFor(raw: unknown, fallback = 'sparkles'): string {
  return canonicalIconName(raw) ?? fallback
}

export type GlassTone =
  | 'amber'
  | 'lavender'
  | 'blue'
  | 'green'
  | 'pink'
  | 'peach'
  | 'mint'
  | 'lilac'

export const GLASS_TONES: Record<
  GlassTone,
  { top: string; bottom: string; halo: string; ink: string }
> = {
  amber: { top: '#FFE5B8', bottom: '#F5B268', halo: '#E99A4E', ink: '#fff' },
  lavender: { top: '#E2D6FF', bottom: '#B79CF3', halo: '#9B7DE6', ink: '#fff' },
  blue: { top: '#D3E9FF', bottom: '#96C6F8', halo: '#75A9E6', ink: '#fff' },
  green: { top: '#D4F3DB', bottom: '#94DBA8', halo: '#6DC98A', ink: '#fff' },
  pink: { top: '#FFDCE5', bottom: '#F4A8BD', halo: '#E884A2', ink: '#fff' },
  peach: { top: '#FFE5D1', bottom: '#F5B083', halo: '#E89464', ink: '#fff' },
  mint: { top: '#D8F5EB', bottom: '#92DEC6', halo: '#6CC8AD', ink: '#fff' },
  lilac: { top: '#EADFFF', bottom: '#C6A9F6', halo: '#A786EA', ink: '#fff' },
}

const TONE_ORDER: GlassTone[] = [
  'amber',
  'lavender',
  'blue',
  'green',
  'pink',
  'peach',
  'mint',
  'lilac',
]

export function isGlassTone(value: unknown): value is GlassTone {
  return typeof value === 'string' && value in GLASS_TONES
}

/**
 * The reference response's semantic palette. The model may omit `tone`; the
 * fallback must still put sunrise on amber, study on lavender, work on blue,
 * food on green, movement on pink and sleep on lilac. Without this map the
 * stable hash painted sunrise green and book blue, which is not the sheet.
 */
const REFERENCE_ICON_TONES: Record<string, GlassTone> = {
  sunrise: 'amber',
  'book-2': 'lavender',
  'device-laptop': 'blue',
  'tools-kitchen-2': 'green',
  'file-text': 'lavender',
  run: 'pink',
  'device-gamepad-2': 'blue',
  moon: 'lilac',
  flame: 'peach',
}

/** Stable palette assignment for an icon name. */
export function glassToneFor(name: string, override?: unknown): GlassTone {
  if (isGlassTone(override)) return override
  const semantic = REFERENCE_ICON_TONES[name]
  if (semantic) return semantic
  let hash = 0
  for (let i = 0; i < name.length; i++) hash = (hash * 31 + name.charCodeAt(i)) | 0
  return TONE_ORDER[Math.abs(hash) % TONE_ORDER.length]
}

/** Curated shortlist for prompt examples and the demo's icon rail. */
export const FEATURED_ICONS: Array<{ name: string; label: string; tone: GlassTone }> = [
  { name: 'sunrise', label: 'Morning', tone: 'amber' },
  { name: 'book-2', label: 'Study', tone: 'lavender' },
  { name: 'device-laptop', label: 'Work', tone: 'blue' },
  { name: 'tools-kitchen-2', label: 'Lunch', tone: 'green' },
  { name: 'file-text', label: 'Assignment', tone: 'lavender' },
  { name: 'run', label: 'Workout', tone: 'pink' },
  { name: 'device-gamepad-2', label: 'Free time', tone: 'blue' },
  { name: 'moon', label: 'Sleep', tone: 'lilac' },
  { name: 'sparkles', label: 'Thinking', tone: 'peach' },
  { name: 'brain', label: 'Reasoning', tone: 'lavender' },
  { name: 'settings', label: 'Effort', tone: 'mint' },
  { name: 'flame', label: 'Motivation', tone: 'peach' },
  { name: 'heart', label: 'Health', tone: 'pink' },
  { name: 'coffee', label: 'Break', tone: 'amber' },
  { name: 'barbell', label: 'Gym', tone: 'blue' },
  { name: 'palette', label: 'Hobby', tone: 'mint' },
  { name: 'music', label: 'Music', tone: 'lavender' },
  { name: 'walk', label: 'Walk', tone: 'green' },
  { name: 'target', label: 'Focus', tone: 'peach' },
  { name: 'calendar', label: 'Plan', tone: 'blue' },
]

/**
 * The compact vocabulary the model is allowed to reason about.
 *
 * The full registry is 5,184 names; sending all of them would cost more than
 * the answer. These 130+ names cover the semantics a planner, tutor, coach or
 * assistant actually reaches for, and every entry is an exact Tabler id. The
 * renderer still accepts any other valid kebab-case id, so this is a guide,
 * not a ceiling.
 */
export const ICON_GUIDE_GROUPS: Array<{ label: string; names: string[] }> = [
  {
    label: 'morning',
    names: ['sunrise', 'sun', 'alarm', 'coffee', 'bath', 'dental', 'bed', 'moon', 'stars', 'cloud'],
  },
  {
    label: 'study',
    names: ['book-2', 'notebook', 'file-text', 'pencil', 'school', 'certificate', 'notes', 'clipboard', 'list-check', 'target'],
  },
  {
    label: 'work',
    names: ['device-laptop', 'code', 'terminal-2', 'mail', 'users', 'briefcase', 'building', 'presentation', 'calendar-event', 'clock'],
  },
  {
    label: 'food',
    names: ['tools-kitchen-2', 'coffee', 'cup', 'glass', 'pizza', 'apple', 'salad', 'cake', 'shopping-cart', 'shopping-bag'],
  },
  {
    label: 'health',
    names: ['heart', 'activity', 'heartbeat', 'run', 'walk', 'bike', 'barbell', 'stretching', 'swimming', 'ball-basketball', 'pill', 'first-aid-kit'],
  },
  {
    label: 'rest',
    names: ['moon', 'stars', 'device-gamepad-2', 'device-tv', 'music', 'headphones', 'movie', 'book', 'chess', 'puzzle'],
  },
  {
    label: 'home',
    names: ['home', 'sofa', 'bed', 'lamp', 'plant', 'tools', 'wash-machine', 'trash', 'basket'],
  },
  {
    label: 'travel',
    names: ['plane', 'car', 'bus', 'train', 'ship', 'map', 'map-pin', 'route', 'compass', 'luggage'],
  },
  {
    label: 'money',
    names: ['wallet', 'coin', 'credit-card', 'cash', 'receipt', 'chart-pie', 'chart-bar', 'trending-up'],
  },
  {
    label: 'tech',
    names: ['device-mobile', 'device-laptop', 'device-desktop', 'wifi', 'bluetooth', 'battery', 'plug', 'server', 'database', 'cloud', 'code', 'bug', 'robot'],
  },
  {
    label: 'nature',
    names: ['leaf', 'tree', 'flower', 'plant-2', 'sun', 'moon', 'cloud', 'cloud-rain', 'snowflake', 'wind', 'flame', 'droplet', 'mountain'],
  },
  {
    label: 'people',
    names: ['user', 'users', 'mood-smile', 'message-circle', 'phone', 'send', 'mail'],
  },
  {
    label: 'focus',
    names: ['target', 'flag', 'check', 'circle-check', 'bookmark', 'pin', 'star', 'sparkles', 'brain', 'bulb', 'rocket'],
  },
  {
    label: 'admin',
    names: ['shield', 'lock', 'key', 'bell', 'paperclip', 'link', 'external-link', 'share', 'copy', 'edit', 'trash', 'download', 'upload', 'refresh', 'repeat'],
  },
]

/** One-line prompt form: ``morning: sunrise, sun, ... · study: ...``. */
export const NEXLY_ICON_GUIDE = ICON_GUIDE_GROUPS.map(
  (group) => `${group.label}: ${group.names.join(', ')}`
).join(' · ')


/** Inline shortcode grammar: :sunrise: */
export const ICON_TOKEN_RE = /:([a-z0-9][a-z0-9-]{1,42}):/g

/** Emoji -> icon fallback. The prompt bans emoji; this is the renderer's belt. */
export const EMOJI_TO_ICON: Record<string, string> = {
  '🌅': 'sunrise',
  '🌄': 'sunrise',
  '☀️': 'sunrise',
  '☀': 'sunrise',
  '📚': 'book-2',
  '📖': 'book-2',
  '💻': 'device-laptop',
  '🖥️': 'device-laptop',
  '🍽️': 'tools-kitchen-2',
  '🍴': 'tools-kitchen-2',
  '🍔': 'tools-kitchen-2',
  '📝': 'file-text',
  '📄': 'file-text',
  '🏃': 'run',
  '🏃‍♂️': 'run',
  '🏃‍♀️': 'run',
  '🏋️': 'barbell',
  '🎮': 'device-gamepad-2',
  '🕹️': 'device-gamepad-2',
  '🌙': 'moon',
  '😴': 'moon',
  '✨': 'sparkles',
  '🧠': 'brain',
  '⚙️': 'settings',
  '⚙': 'settings',
  '💪': 'flame',
  '🔥': 'flame',
  '❤️': 'heart',
  '❤': 'heart',
  '✅': 'circle-check',
  '✔️': 'circle-check',
  '⭐': 'star',
  '🎯': 'target',
  '☕': 'coffee',
  '🎨': 'palette',
  '🎵': 'music',
  '🚶': 'walk',
  '📅': 'calendar',
  '⏰': 'clock',
}

/** Replace known emoji with :icon: tokens; unknown pictographs are dropped. */
export function emojiToIconTokens(text: string): string {
  let out = text
  // Longest first so ZWJ sequences (runner, etc.) win over their base glyph.
  const entries = Object.entries(EMOJI_TO_ICON).sort((a, b) => b[0].length - a[0].length)
  for (const [emoji, icon] of entries) {
    out = out.split(emoji).join(`:${icon}:`)
  }
  // Any remaining Extended_Pictographic codepoint is not allowed in the DOM.
  return out
    .replace(/\p{Extended_Pictographic}/gu, '')
    .replace(/[\u2640\u2642\uFE0F\u200D]/g, '')
}
