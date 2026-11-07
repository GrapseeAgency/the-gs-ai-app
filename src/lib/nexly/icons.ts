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
  eyeglasses: 'eyeglass',
  glasses: 'eyeglass',
  'circle-filled': 'circle-dot',
  dot: 'circle-dot',
  bullet: 'circle-dot',
  lightning: 'bolt',
  zap: 'bolt',
  flash: 'bolt',
  energy: 'bolt',
  money: 'coin',
  dollar: 'currency-dollar',
  currency: 'coin',
  price: 'coin',
  graph: 'chart-bar',
  chart: 'chart-bar',
  analytics: 'chart-line',
  stats: 'chart-bar',
  todo: 'checkbox',
  task: 'checkbox',
  tasks: 'list-check',
  checklist: 'list-check',
  tip: 'bulb',
  insight: 'bulb',
  magic: 'sparkles',
  ai: 'sparkles',
  edu: 'school',
  academic: 'school',
  university: 'building-bank',
  security: 'shield-check',
  safe: 'shield-check',
  verified: 'shield-check',
  error: 'alert-circle',
  danger: 'alert-triangle',
  info: 'info-circle',
  help: 'help-circle',
  question: 'help-circle',
  checkmark: 'checks',
}

/** Normalise arbitrary model output into a Tabler kebab-case id. */
export function canonicalIconName(raw: unknown): string | null {
  if (typeof raw !== 'string') return null
  const name = raw
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
  const stripped = name.replace(/-(?:filled|outline)$/, '')
  if (NAME_SET.has(stripped)) return stripped
  const strippedAlias = ICON_ALIASES[stripped]
  if (strippedAlias && NAME_SET.has(strippedAlias)) return strippedAlias
  if (stripped.endsWith('s') && stripped.length > 3) {
    const singular = stripped.slice(0, -1)
    if (NAME_SET.has(singular)) return singular
    const sAlias = ICON_ALIASES[singular]
    if (sAlias && NAME_SET.has(sAlias)) return sAlias
  }
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
  '✦': 'sparkles',
  '🧠': 'brain',
  '⚙️': 'settings',
  '⚙': 'settings',
  '💪': 'flame',
  '🔥': 'flame',
  '❤️': 'heart',
  '❤': 'heart',
  '✅': 'circle-check',
  '✔️': 'circle-check',
  '☑️': 'checkbox',
  '☑': 'checkbox',
  '⭐': 'star',
  '🎯': 'target',
  '☕': 'coffee',
  '🎨': 'palette',
  '🎵': 'music',
  '🚶': 'walk',
  '📅': 'calendar',
  '⏰': 'clock',
  '⏱️': 'clock',
  '⏳': 'hourglass',
  '⚡': 'bolt',
  '⚠️': 'alert-triangle',
  '⚠': 'alert-triangle',
  '💡': 'bulb',
  '📊': 'chart-bar',
  '📈': 'trending-up',
  '📉': 'trending-down',
  '👥': 'users',
  '👤': 'user',
  '🧮': 'calculator',
  '🔗': 'link',
  '🌐': 'world',
  '🌍': 'world',
  '🌎': 'world',
  '🌏': 'world',
  '🏛️': 'building-bank',
  '🏛': 'building-bank',
  '🚀': 'rocket',
  '🔒': 'lock',
  '🔓': 'lock-open',
  '🛡️': 'shield',
  '🛡': 'shield',
  '🐍': 'brand-python',
  '🔍': 'search',
  '🔎': 'search',
  '📌': 'pin',
  '📎': 'paperclip',
  '📋': 'clipboard',
  '📁': 'folder',
  '📂': 'folder-open',
  '💬': 'message-circle',
  '💭': 'message-circle',
  '🔔': 'bell',
  '💰': 'coin',
  '💵': 'cash',
  '💳': 'credit-card',
  '🏆': 'trophy',
  '🥇': 'trophy',
  '🎉': 'sparkles',
  '🧪': 'flask',
  '🧬': 'dna',
  '🔬': 'microscope',
  '🎓': 'school',
  '✈️': 'plane',
  '🚗': 'car',
  '🏠': 'home',
  '🌱': 'plant',
  '🌿': 'leaf',
  '🔋': 'battery',
  '🔌': 'plug',
  '🧭': 'compass',
  '🗺️': 'map',
  '🔑': 'key',
  '🛠️': 'tools',
  '🛠': 'tools',
  '🔧': 'tool',
  '📱': 'device-mobile',
  '🛒': 'shopping-cart',
  '🧾': 'receipt',
  '📧': 'mail',
  '✉️': 'mail',
  '📞': 'phone',
  '🎥': 'video',
  '📷': 'camera',
  '🖼️': 'photo',
  '🖼': 'photo',
  '🎬': 'movie',
  '👍': 'thumb-up',
  '👎': 'thumb-down',
  '😊': 'mood-smile',
  '😀': 'mood-smile',
  '😃': 'mood-smile',
  '😄': 'mood-smile',
  '😁': 'mood-smile',
  '🙂': 'mood-smile',
  '😉': 'mood-wink',
  '😍': 'heart',
  '🥰': 'heart',
  '😎': 'sunglasses',
  '🤓': 'eyeglass',
  '🧐': 'search',
  '🥳': 'sparkles',
  '😞': 'mood-sad',
  '😔': 'mood-sad',
  '😟': 'mood-sad',
  '😕': 'mood-sad',
  '🙁': 'mood-sad',
  '☹️': 'mood-sad',
  '☹': 'mood-sad',
  '😢': 'mood-sad',
  '😭': 'mood-sad',
  '😤': 'flame',
  '😠': 'alert-circle',
  '😡': 'alert-circle',
  '🤯': 'brain',
  '😱': 'alert-triangle',
  '🤗': 'heart',
  '🙌': 'sparkles',
  '👏': 'sparkles',
  '🙏': 'heart',
  '🤝': 'users',
  '👀': 'eye',
  '👁️': 'eye',
  '👁': 'eye',
  '💯': 'circle-check',
  '💎': 'diamond',
  '👑': 'crown',
  '🎁': 'gift',
  '🎈': 'sparkles',
  '🎊': 'sparkles',
  '🥈': 'medal',
  '🥉': 'medal',
  '🏅': 'medal',
  '⚽': 'ball-football',
  '🏀': 'ball-basketball',
  '🎾': 'ball-tennis',
  '💧': 'droplet',
  '🌊': 'wave-sine',
  '❄️': 'snowflake',
  '❄': 'snowflake',
  '🌈': 'sparkles',
  '🌳': 'tree',
  '🌲': 'tree',
  '🌴': 'tree',
  '🌵': 'plant',
  '🌸': 'flower',
  '🌺': 'flower',
  '🌻': 'flower',
  '🌹': 'flower',
  '🌷': 'flower',
  '🍀': 'leaf',
  '🍁': 'leaf',
  '🍂': 'leaf',
  '🍃': 'leaf',
  '🍎': 'apple',
  '🍏': 'apple',
  '🍋': 'lemon',
  '🥗': 'salad',
  '🍕': 'pizza',
  '🍵': 'mug',
  '🍺': 'beer',
  '🍷': 'glass-full',
  '🚌': 'bus',
  '🚆': 'train',
  '🚇': 'train',
  '🚲': 'bike',
  '🚢': 'ship',
  '⚓': 'anchor',
  '⛽': 'gas-station',
  '🚨': 'urgent',
  '🛑': 'octagon-off',
  '🚧': 'barrier-block',
  '🏥': 'building-hospital',
  '🏫': 'school',
  '🏢': 'building',
  '🏭': 'building-factory-2',
  '⛰️': 'mountain',
  '⛰': 'mountain',
  '🏔️': 'mountain',
  '🏖️': 'beach',
  '🎧': 'headphones',
  '🎤': 'microphone',
  '🎹': 'music',
  '🎸': 'music',
  '🥁': 'music',
  '📺': 'device-tv',
  '🖨️': 'printer',
  '⌨️': 'keyboard',
  '🖱️': 'mouse',
  '💾': 'device-floppy',
  '💿': 'disc',
  '🔭': 'telescope',
  '📡': 'satellite',
  '💊': 'pill',
  '🩺': 'stethoscope',
  '🛏️': 'bed',
  '🚿': 'bath',
  '🛁': 'bath',
  '🧹': 'brush',
  '🧯': 'fire-extinguisher',
  '🔴': 'circle-dot',
  '🟠': 'circle-dot',
  '🟡': 'circle-dot',
  '🟢': 'circle-dot',
  '🔵': 'circle-dot',
  '🟣': 'circle-dot',
  '⚫': 'circle-dot',
  '⚪': 'circle-dot',
  '🟤': 'circle-dot',
  '⬛': 'square',
  '⬜': 'square',
  '▪️': 'square',
  '▫️': 'square',
  '◾': 'square',
  '◽': 'square',
  '◼️': 'square',
  '◻️': 'square',
  '🔶': 'diamond',
  '🔷': 'diamond',
  '🔸': 'diamond',
  '🔹': 'diamond',
  '🔺': 'triangle',
  '🔻': 'triangle-inverted',
  '▶️': 'player-play',
  '▶': 'player-play',
  '◀️': 'player-skip-back',
  '◀': 'player-skip-back',
  '⏩': 'player-track-next',
  '⏪': 'player-track-prev',
  '⏸️': 'player-pause',
  '⏹️': 'player-stop',
  '🔄': 'refresh',
  '🔁': 'repeat',
  '🔀': 'arrows-shuffle',
  '➡️': 'arrow-right',
  '⬅️': 'arrow-left',
  '⬆️': 'arrow-up',
  '⬇️': 'arrow-down',
  '↗️': 'arrow-up-right',
  '↘️': 'arrow-down-right',
  '↙️': 'arrow-down-left',
  '↖️': 'arrow-up-left',
  '↔️': 'arrows-left-right',
  '↕️': 'arrows-up-down',
  '↩️': 'arrow-back-up',
  '↪️': 'arrow-forward-up',
  '⤴️': 'arrow-ramp-right',
  '⤵️': 'arrow-ramp-left',
  '➕': 'plus',
  '➖': 'minus',
  '➗': 'divide',
  '✖️': 'x',
  '✖': 'x',
  '♾️': 'infinity',
  '♾': 'infinity',
  '💲': 'currency-dollar',
  '💱': 'arrows-exchange',
  '™️': 'trademark',
  '™': 'trademark',
  '©️': 'copyright',
  '©': 'copyright',
  '®️': 'registered',
  '®': 'registered',
  '♻️': 'recycle',
  '♻': 'recycle',
  '‼️': 'alert-circle',
  '⁉️': 'help-circle',
  '〰️': 'wave-sine',
  '〽️': 'chart-line',
  '❇️': 'sparkles',
  '✳️': 'asterisk',
  '✴️': 'star',
  '☣️': 'biohazard',
  '☢️': 'radioactive',
  '☮️': 'peace',
  '☯️': 'yin-yang',
  '☸️': 'steering-wheel',
  '☺️': 'mood-smile',
  '☺': 'mood-smile',
  '⚔️': 'swords',
  '⚖️': 'scale',
  '⚖': 'scale',
  '⚗️': 'flask',
  '⚱️': 'award',
  '⚰️': 'box',
  '⛏️': 'pick',
  '⚒️': 'hammer',
  '⛓️': 'link',
  '⛓': 'link',
  '⚛️': 'atom',
  '⚛': 'atom',
  '⚜️': 'crown',
  '⚱': 'award',
  '0️⃣': 'number-0',
  '1️⃣': 'number-1',
  '2️⃣': 'number-2',
  '3️⃣': 'number-3',
  '4️⃣': 'number-4',
  '5️⃣': 'number-5',
  '6️⃣': 'number-6',
  '7️⃣': 'number-7',
  '8️⃣': 'number-8',
  '9️⃣': 'number-9',
  '🔟': 'numbers',
  '🤔': 'brain',
  '❌': 'circle-x',
  '❓': 'help-circle',
  '❗': 'alert-circle',
  'ℹ️': 'info-circle',
  'ℹ': 'info-circle',
}

function fallbackIconForCodePoint(cp: number): string | null {
  if (cp >= 0x1f3fb && cp <= 0x1f3ff) return null
  if ((cp >= 0x1f600 && cp <= 0x1f64f) || (cp >= 0x1f910 && cp <= 0x1f92f) || (cp >= 0x1f970 && cp <= 0x1f97a) || (cp >= 0x1fae0 && cp <= 0x1faef)) {
    return 'mood-smile'
  }
  if ((cp >= 0x1f440 && cp <= 0x1f487) || (cp >= 0x1f930 && cp <= 0x1f93f) || (cp >= 0x1faf0 && cp <= 0x1faff)) {
    return 'user'
  }
  if ((cp >= 0x1f330 && cp <= 0x1f343) || (cp >= 0x1f400 && cp <= 0x1f43f) || (cp >= 0x1f980 && cp <= 0x1f9ae) || (cp >= 0x1fab0 && cp <= 0x1fabf)) {
    return 'leaf'
  }
  if ((cp >= 0x1f32d && cp <= 0x1f32f) || (cp >= 0x1f344 && cp <= 0x1f37f) || (cp >= 0x1f950 && cp <= 0x1f96f) || (cp >= 0x1fad0 && cp <= 0x1fadf)) {
    return 'cup'
  }
  if ((cp >= 0x1f380 && cp <= 0x1f3cf) || (cp >= 0x1f93c && cp <= 0x1f94f)) {
    return 'trophy'
  }
  if ((cp >= 0x1f680 && cp <= 0x1f6ff) || (cp >= 0x1f3d0 && cp <= 0x1f3ff)) {
    return 'compass'
  }
  if (cp >= 0x1f300 && cp <= 0x1f32c) {
    return 'sun'
  }
  if ((cp >= 0x1f488 && cp <= 0x1f5ff) || (cp >= 0x1fa70 && cp <= 0x1fa9f)) {
    return 'sparkles'
  }
  return null
}

/** Replace known emoji with :icon: tokens, map any remaining emoji block to a semantic :icon: token, and strip pictographs. */
export function emojiToIconTokens(text: string): string {
  let out = text
  // Longest first so ZWJ sequences (runner, keycaps, etc.) win over their base glyph.
  const entries = Object.entries(EMOJI_TO_ICON).sort((a, b) => b[0].length - a[0].length)
  for (const [emoji, icon] of entries) {
    out = out.split(emoji).join(`:${icon}:`)
  }
  let mapped = ''
  let lastWasFallback = false
  for (const ch of out) {
    const cp = ch.codePointAt(0) ?? 0
    const fb = fallbackIconForCodePoint(cp)
    if (fb) {
      if (!lastWasFallback) {
        mapped += `:${fb}:`
        lastWasFallback = true
      }
      continue
    }
    if ((cp >= 0xe0020 && cp <= 0xe007f) || cp === 0x200d || (cp >= 0xfe00 && cp <= 0xfe0f) || (cp >= 0x1f3fb && cp <= 0x1f3ff)) {
      continue
    }
    mapped += ch
    if (!/\s/.test(ch)) lastWasFallback = false
  }
  // Any remaining Extended_Pictographic or symbol emoji codepoint is stripped.
  return mapped
    .replace(/\p{Extended_Pictographic}/gu, '')
    .replace(
      /[\u203C\u2049\u2122\u2139\u2194-\u21AA\u2300-\u23FF\u25AA-\u25FE\u2600-\u27BF\u2934\u2935\u2B05-\u2B07\u2B1B\u2B1C\u2B50\u2B55\u3030\u303D\u3297\u3299\uFE00-\uFE0F\u200D\u20E3\u{1F1E6}-\u{1F1FF}\u{1F3FB}-\u{1F3FF}]/gu,
      ''
    )
}

/**
 * Sanitize a full markdown reply or delta chunk so no raw Unicode emoji ever reaches
 * a client:
 *  - Inside fenced code blocks: for widget JSON fences, converts emojis cleanly; for source code fences, replaces ❌/✅/⚠️ with ASCII and strips pictographs.
 *  - Outside fenced blocks: converts known emojis into :icon: tokens and strips unknown pictographs.
 */
export function sanitizeReplyEmojis(markdown: string): string {
  if (!markdown) return markdown
  const parts = markdown.split(/(```[\s\S]*?```)/g)
  return parts
    .map((part) => {
      if (part.startsWith('```')) {
        const firstLine = part.slice(3, part.indexOf('\n') > 0 ? part.indexOf('\n') : undefined).trim().toLowerCase()
        const isCodeLang = /^(python|py|js|javascript|ts|typescript|jsx|tsx|java|kotlin|kt|swift|c|cpp|c\+\+|rust|rs|go|golang|ruby|rb|php|sql|bash|sh|zsh|shell|html|css|xml|yaml|yml|toml|dockerfile|makefile|r|matlab|scala|dart|lua|perl|haskell|elixir|clojure|csharp|cs|fsharp|vb|powershell|ps1)$/.test(
          firstLine
        )
        if (isCodeLang) {
          return part
            .replace(/❌/g, '[x]')
            .replace(/✅|✔️|✔/g, '[ok]')
            .replace(/⚠️|⚠/g, '[!]')
            .replace(/\p{Extended_Pictographic}/gu, '')
            .replace(
              /[\u203C\u2049\u2122\u2139\u2194-\u21AA\u2300-\u23FF\u25AA-\u25FE\u2600-\u27BF\u2934\u2935\u2B05-\u2B07\u2B1B\u2B1C\u2B50\u2B55\u3030\u303D\u3297\u3299\uFE00-\uFE0F\u200D\u20E3\u{1F1E6}-\u{1F1FF}\u{1F3FB}-\u{1F3FF}]/gu,
              ''
            )
        }
        return emojiToIconTokens(part)
      }
      return emojiToIconTokens(part)
    })
    .join('')
}

/** Extract a leading :icon: token (or converted leading emoji, even inside leading **bold**) and return { icon, rest }. */
export function extractLeadingIcon(raw: string): { icon: string | null; rest: string } {
  const normalized = emojiToIconTokens(raw).trimStart()
  const m = normalized.match(/^(\*{1,3}|_{1,3})?\s*:([a-z0-9][a-z0-9-]{1,42}):\s*([\s\S]*)$/)
  if (!m) return { icon: null, rest: normalized }
  const prefix = m[1] ?? ''
  return { icon: canonicalIconName(m[2]) ?? m[2], rest: `${prefix}${m[3]}` }
}

/** Strip both raw emojis and :icon: tokens for clean plain-text headings/labels. */
export function cleanPlainText(raw: string): string {
  return emojiToIconTokens(raw)
    .replace(/:([a-z0-9][a-z0-9-]{1,42}):/g, '')
    .replace(/\s{2,}/g, ' ')
    .trim()
}

const SEMANTIC_KEYWORD_ICONS: Array<{ re: RegExp; icon: string; tone: GlassTone }> = [
  { re: /\b(python|django|flask|pandas|numpy|pytorch)\b/i, icon: 'brand-python', tone: 'blue' },
  { re: /\b(java|spring|jvm|maven|coffee|cafe|espresso)\b/i, icon: 'coffee', tone: 'peach' },
  { re: /\b(javascript|node\.?js|express|npm|v8)\b/i, icon: 'brand-javascript', tone: 'amber' },
  { re: /\b(typescript|tsx|deno)\b/i, icon: 'brand-typescript', tone: 'blue' },
  { re: /\b(react|next\.?js|jsx|redux)\b/i, icon: 'brand-react', tone: 'blue' },
  { re: /\b(swift|swiftui|ios|xcode|apple)\b/i, icon: 'brand-apple', tone: 'lavender' },
  { re: /\b(android|kotlin|jetpack|gradle)\b/i, icon: 'brand-android', tone: 'green' },
  { re: /\b(rust|cargo)\b/i, icon: 'brand-rust', tone: 'peach' },
  { re: /\b(golang|go\b)/i, icon: 'brand-golang', tone: 'mint' },
  { re: /(?:^|\b)(c\+\+|cpp|clang)(?:\b|$)/i, icon: 'brand-cpp', tone: 'blue' },
  { re: /^(c|c#|csharp)$/i, icon: 'code', tone: 'blue' },
  { re: /\b(docker|container|kubernetes|k8s)\b/i, icon: 'brand-docker', tone: 'blue' },
  { re: /\b(git|github|commit|branch|pull request)\b/i, icon: 'brand-github', tone: 'lavender' },
  { re: /\b(database|sql|postgres|mysql|sqlite|redis|mongodb|schema|query)\b/i, icon: 'database', tone: 'blue' },
  { re: /\b(cloud|aws|azure|gcp|serverless|cdn|deploy)\b/i, icon: 'cloud-computing', tone: 'blue' },
  { re: /\b(security|auth|jwt|oauth|encrypt|password|privacy|shield|safe)\b/i, icon: 'shield-check', tone: 'green' },
  { re: /\b(performance|speed|fast|latency|throughput|optimize|cache|bolt)\b/i, icon: 'bolt', tone: 'amber' },
  { re: /\b(api|webhook|endpoint|rest|graphql|http|network|socket)\b/i, icon: 'plug-connected', tone: 'mint' },
  { re: /\b(bug|error|issue|warning|risk|pitfall|caution|alert|fail)\b/i, icon: 'alert-triangle', tone: 'amber' },
  { re: /\b(test|qa|verify|validation|check|assert|pass)\b/i, icon: 'circle-check', tone: 'green' },
  { re: /\b(loan|mortgage|apr|emi|amortiz|bank|debt|interest rate)\b/i, icon: 'building-bank', tone: 'blue' },
  { re: /\b(invest|compound|portfolio|dividend|stock|etf|roi|wealth|return|growth|profit)\b/i, icon: 'trending-up', tone: 'green' },
  { re: /\b(budget|salary|income|saving|expense|cost|price|revenue|money|payment|tax|fee)\b/i, icon: 'coin', tone: 'amber' },
  { re: /\b(formula|equation|math|calculate|calculator|sum|ratio|average)\b/i, icon: 'calculator', tone: 'amber' },
  { re: /\b(chart|metric|analytics|stat|benchmark|trend|data|distribution)\b/i, icon: 'chart-bar', tone: 'lavender' },
  { re: /\b(calorie|protein|macro|nutrition|diet|meal|food|vitamin|carbs|fat)\b/i, icon: 'salad', tone: 'green' },
  { re: /\b(workout|exercise|gym|fitness|strength|cardio|muscle|training|run)\b/i, icon: 'barbell', tone: 'peach' },
  { re: /\b(health|medical|heart|sleep|recovery|wellness|symptom|doctor)\b/i, icon: 'heartbeat', tone: 'pink' },
  { re: /\b(study|learn|course|book|read|exam|lesson|education|concept)\b/i, icon: 'book-2', tone: 'lavender' },
  { re: /\b(time|schedule|timeline|deadline|duration|hour|minute|morning|daily)\b/i, icon: 'clock', tone: 'amber' },
  { re: /\b(date|calendar|week|month|year|quarter|roadmap|milestone)\b/i, icon: 'calendar', tone: 'blue' },
  { re: /\b(travel|flight|trip|hotel|airport|vacation|tour)\b/i, icon: 'plane', tone: 'blue' },
  { re: /\b(location|map|city|country|region|route|address|global|world)\b/i, icon: 'world', tone: 'mint' },
  { re: /\b(ai|llm|neural|model|machine learning|gpt|agent|brain|smart)\b/i, icon: 'brain', tone: 'lavender' },
  { re: /\b(code|function|algorithm|class|module|compile|syntax|script)\b/i, icon: 'code', tone: 'lavender' },
  { re: /\b(user|team|customer|client|people|community|audience|member)\b/i, icon: 'users', tone: 'blue' },
  { re: /\b(goal|target|objective|focus|mission|priority|kpi)\b/i, icon: 'target', tone: 'pink' },
  { re: /\b(winner|best|top|rank|award|champion|leader|advantage)\b/i, icon: 'trophy', tone: 'amber' },
  { re: /\b(launch|start|ship|release|scale|boost|upgrade|future)\b/i, icon: 'rocket', tone: 'peach' },
  { re: /\b(setting|config|setup|install|tool|environment|option)\b/i, icon: 'settings', tone: 'mint' },
  { re: /\b(why|tip|insight|idea|note|summary|takeaway|key|pro tip)\b/i, icon: 'bulb', tone: 'amber' },
]

/**
 * Resolve any heading, bullet label, step title, or table entity to a semantic
 * custom glassy icon + tone from the 5,247 Tabler registry.
 */
export function semanticIconFor(raw: string, fallbackIcon = 'sparkles'): { icon: string; tone: GlassTone } {
  const extracted = extractLeadingIcon(raw)
  if (extracted.icon) {
    const resolved = iconNameFor(extracted.icon, fallbackIcon)
    return { icon: resolved, tone: glassToneFor(resolved) }
  }
  const clean = cleanPlainText(raw)
  for (const rule of SEMANTIC_KEYWORD_ICONS) {
    if (rule.re.test(clean)) {
      const resolved = iconNameFor(rule.icon, fallbackIcon)
      return { icon: resolved, tone: rule.tone }
    }
  }
  // Direct lookup of words against the 5,247-icon vocabulary & brand-* icons.
  const words = clean
    .toLowerCase()
    .replace(/[^a-z0-9\s-]/g, ' ')
    .split(/\s+/)
    .filter((w) => w.length >= 3)
  for (const w of words) {
    const brand = canonicalIconName(`brand-${w}`)
    if (brand) return { icon: brand, tone: glassToneFor(brand) }
    const direct = canonicalIconName(w)
    if (direct) return { icon: direct, tone: glassToneFor(direct) }
  }
  if (!fallbackIcon) return { icon: '', tone: 'lavender' }
  const resolved = iconNameFor(fallbackIcon, 'sparkles')
  return { icon: resolved, tone: glassToneFor(resolved) }
}


