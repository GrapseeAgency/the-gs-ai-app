/** A display boundary, never a source-code transformer. Copy/Run retain the
 * original string. Unicode symbols forbidden by the presentation policy are
 * shown as UTF-16 escapes, which also round-trip through a JSON-string editor. */
export function escapeCodeDisplay(source: string): string {
  const displaySymbol = /\p{Emoji}|[\u200D\u20E3\uFE0E\uFE0F\u{E0020}-\u{E007F}]/u
  return Array.from(source, char => {
    const cp = char.codePointAt(0)!
    if (cp <= 0x7f || (!displaySymbol.test(char) && !(cp >= 0x1f000 && cp <= 0x1faff))) return char
    return char.split('').map(unit => `\\u${unit.charCodeAt(0).toString(16).padStart(4, '0')}`).join('')
  }).join('')
}
export function requiresEncodedEditor(source: string): boolean {
  return escapeCodeDisplay(source) !== source
}
export function encodeSourceEditor(source: string): string {
  return escapeCodeDisplay(JSON.stringify(source))
}
export function decodeSourceEditor(draft: string): string {
  const value: unknown = JSON.parse(draft)
  if (typeof value !== 'string') throw new Error('The source editor requires one JSON string, not an object or array.')
  return value
}
