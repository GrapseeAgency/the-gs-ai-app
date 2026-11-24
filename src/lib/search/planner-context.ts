/** Bounded remote intent-planner input. Keep both the beginning and the
 * final correction of long requests; do not silently classify a prefix as
 * if it were the whole message. Source roles are data, not instructions. */
const OMIT = '\n[… omitted …]\n'
export function plannerExcerpt(text: string, limit: number): string {
  if (text.length <= limit) return text
  if (limit <= OMIT.length + 2) return ''
  const available = limit - OMIT.length
  // Users commonly put the actual request or corrections after a pasted
  // passage. Reserve more space for the end, without losing the opening.
  let head = Math.floor(available * 0.4)
  let tail = text.length - (available - head)
  if (head > 0 && /[\uD800-\uDBFF]/.test(text[head - 1])) head--
  if (/[\uDC00-\uDFFF]/.test(text[tail])) tail++
  return text.slice(0, head) + OMIT + text.slice(tail)
}

export function plannerHistory(rows: readonly {role: string; content: string}[]): string[] {
  return rows.slice(-6).map(row => JSON.stringify({
    role: row.role.toLowerCase() === 'assistant' ? 'assistant' : 'user',
    content: plannerExcerpt(row.content, 430),
  })).map(line => {
    // JSON escaping can expand line breaks and quotes. If necessary, keep
    // a smaller source excerpt rather than cutting the serialized role.
    if (line.length <= 490) return line
    const row = JSON.parse(line) as {role:string;content:string}
    let limit = 200
    let bounded = JSON.stringify({role:row.role,content:plannerExcerpt(row.content,limit)})
    while (bounded.length > 490 && limit > 32) {
      limit = Math.floor(limit / 2)
      bounded = JSON.stringify({role:row.role,content:plannerExcerpt(row.content,limit)})
    }
    return bounded
  })
}

export function plannerLatest(text: string): string {
  return JSON.stringify({role:'user',content:plannerExcerpt(text, 2000)})
}
