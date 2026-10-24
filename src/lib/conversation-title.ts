/**
 * The conversation headline.
 *
 * One function decides what a chat is called, because three things depend on it
 * and they must agree: the Chats list, the drawer's recent rows, and the icon
 * that row draws (the icon is chosen from the headline's topic).
 *
 * What this replaces: `content.trim().slice(0, 40)` in turn-executor. That is a
 * byte-ish truncation of whatever the user typed, so the title of a chat that
 * begins with a code block was the first forty characters of the code fence, and
 * a chat that begins with a URL was the URL. It also cut mid-word, and it kept
 * newlines, so a two-line first message produced a title with a line break in it.
 *
 * The rules, in order:
 *   1. drop attachment placeholder lines and bare URLs — they are not the subject
 *   2. drop fenced code blocks entirely; keep inline code as words
 *   3. strip markdown markers (headings, emphasis, list bullets, quotes)
 *   4. collapse all whitespace, including newlines, to single spaces
 *   5. cut on a word boundary at the limit, with an ellipsis only when cut
 *   6. fall back to the attachment's name, then to "New chat"
 */

export const DEFAULT_CHAT_TITLE = 'New chat'
export const TITLE_LIMIT = 48

/** Placeholders the composers write when a message is only an attachment. */
const ATTACHMENT_LINE =
  /^(?:\[(?:image|file|attachment|document|audio|video)[^\]]*\]|📎.*|<attachment[^>]*>.*)$/i

const BARE_URL = /^(?:https?:\/\/|www\.)\S+$/i

export function deriveTitle(input: {
  content: string
  attachmentName?: string | null
}): string {
  const raw = (input.content ?? '').replace(/\r\n?/g, '\n')

  // Fenced blocks first: their contents are code, and the line that opens them
  // is not a subject either.
  const withoutFences = raw.replace(/```[\s\S]*?(?:```|$)/g, ' ')

  const kept = withoutFences
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
    .filter((line) => !ATTACHMENT_LINE.test(line) && !BARE_URL.test(line))
    .map((line) =>
      line
        // heading / blockquote / list markers at the start
        .replace(/^[>#*\-\u2022\d.)\s]+/, '')
        // emphasis and inline code markers, keeping the words inside
        .replace(/[*_~`]/g, '')
        // inline links: keep the label, drop the target
        .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    )
    .join(' ')

  const flat = kept.replace(/\s+/g, ' ').trim()
  const source = flat.length > 0 ? flat : (input.attachmentName ?? '').trim()

  if (source.length === 0) return DEFAULT_CHAT_TITLE
  if (source.length <= TITLE_LIMIT) return source

  const cut = source.slice(0, TITLE_LIMIT)
  const lastSpace = cut.lastIndexOf(' ')
  // A single long token (a URL, a hashed id) has no boundary to cut on: keep it
  // rather than returning one letter and an ellipsis.
  const head = lastSpace > TITLE_LIMIT * 0.4 ? cut.slice(0, lastSpace) : cut
  return `${head.trimEnd()}…`
}
