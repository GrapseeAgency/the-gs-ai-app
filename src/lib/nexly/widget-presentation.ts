import { canonicalIconName, emojiToIconTokens, ICON_TOKEN_RE } from './icons'

/** Machine strings are data, not decoration. Never rewrite expressions, IDs,
 * executable documents, navigation targets, or submitted user prompts. */
const RAW_KEYS = new Set([
  'id', 'key', 'type', 'kind', 'expr', 'expression', 'formula',
  'code', 'source', 'script', 'html', 'svg', 'css', 'sql', 'query',
  'url', 'href', 'link', 'src', 'route', 'action', 'method', 'prompt',
  'language', 'format', 'accent', 'palette', 'variant', 'color',
  'before', 'after', 'old', 'new', 'original', 'modified', 'patch', 'diff', 'unified', 'z', 'pattern', 'regex',
])

/** Runs AFTER JSON decoding: escaped Unicode must not bypass the emoji rule.
 * Titles retain icon tokens for glass rendering; plain labels cannot render
 * those tokens and must strip them. The source payload is never mutated. */
export function sanitizeWidgetPresentation<T>(value: T, key = ''): T {
  if (typeof value === 'string') {
    if (RAW_KEYS.has(key)) return value
    const normalized = emojiToIconTokens(value)
    if (key === 'icon' || key === 'closingIcon') {
      const token = /:([a-z0-9-]+):/.exec(normalized)?.[1]
      return (canonicalIconName(token ?? value) ?? 'sparkles') as T
    }
    return (key === 'title' ? normalized : normalized.replace(ICON_TOKEN_RE, '')) as T
  }
  if (Array.isArray(value)) return value.map((item) => sanitizeWidgetPresentation(item, key)) as T
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([name, item]) =>
      [name, sanitizeWidgetPresentation(item, name)])) as T
  }
  return value
}
