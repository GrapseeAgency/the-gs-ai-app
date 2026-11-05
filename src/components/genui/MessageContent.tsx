'use client'

/**
 * MessageContent — the full assistant answer: rich markdown prose and widgets
 * inline, streamed-aware. Unclosed fences render as skeletons; a tap on any
 * prompt affordance flows up through onPrompt.
 *
 * Prose is parsed into the same block + inline grammar as the Android
 * (ContentBlocks.kt / InlineMarkdown.kt) and iOS (ContentBlocks.swift /
 * InlineMarkdown.swift) twins: headings, bullet/ordered/nested/task lists,
 * blockquotes, markdown tables with alignment, dividers, $$math$$ blocks,
 * <details>/<summary> collapsibles, standalone images, inline code, math,
 * safe http(s) links, bold/italic/strike, [N] citation chips, and :icon:
 * glass glyphs.
 */

import { Fragment, useMemo, type ReactNode } from 'react'
import { parseStream, type Segment } from './stream'
import { WidgetRenderer } from './WidgetRenderer'
import { Skeleton } from './primitives/Skeleton'
import { MermaidBlock } from './blocks/MermaidBlock'
import { HtmlBlock } from './blocks/HtmlBlock'
import { SvgBlock } from './blocks/SvgBlock'
import { CodeBlock } from './blocks/CodeBlock'
import { themeFor, TYPE } from './theme/tokens'
import { canonicalIconName, emojiToIconTokens, ICON_TOKEN_RE } from '@/lib/nexly/icons'
import { GlassIcon } from '@/components/nexly/GlassIcon'

/** A persisted citation the host knows about — [N] markers resolve to these. */
export interface Citation {
  ordinal: number
  title: string
  url: string
  domain?: string
}

type Theme = ReturnType<typeof themeFor>
type TableAlign = 'left' | 'center' | 'right'

interface ListItemNode {
  number?: number
  checked?: boolean
  text: string
  continuations: string[]
  children: ProseBlock[]
}

type ProseBlock =
  | { kind: 'paragraph'; text: string }
  | { kind: 'heading'; level: number; text: string }
  | { kind: 'bulletList'; items: ListItemNode[] }
  | { kind: 'orderedList'; start: number; items: ListItemNode[] }
  | { kind: 'blockquote'; blocks: ProseBlock[] }
  | { kind: 'table'; headers: string[]; aligns: TableAlign[]; rows: string[][] }
  | { kind: 'divider' }
  | { kind: 'math'; latex: string }
  | { kind: 'image'; alt: string; url: string }
  | { kind: 'collapsible'; summary: string; blocks: ProseBlock[] }

const HEADING_RE = /^(#{1,6})\s+(.+?)\s*#*\s*$/
const HR_RE = /^ {0,3}((\* *){3,}|(- *){3,}|(_ *){3,})$/
const BULLET_RE = /^(\s*)([-*+•·])\s+(.*)$/
const ORDERED_RE = /^(\s*)(\d{1,9})[.)]\s+(.*)$/
const TASK_PREFIX_RE = /^\[([ xX])\]\s+(.*)$/
const QUOTE_RE = /^\s{0,3}>\s?(.*)$/
const IMAGE_ONLY_RE = /^!\[([^\]]*)\]\(([^)\s]+)(?:\s+"[^"]*")?\)\s*$/
const DETAILS_OPEN_RE = /^\s*<details[^>]*>\s*$/i
const DETAILS_INLINE_RE = /^\s*<details[^>]*>\s*<summary>(.*?)<\/summary>\s*$/i
const SUMMARY_RE = /^\s*<summary>(.*?)<\/summary>\s*$/i
const DETAILS_CLOSE_RE = /^\s*<\/details>\s*$/i

const BLOCKED_SCHEMES = ['javascript:', 'data:', 'file:', 'vbscript:', 'blob:']

function isSafeUrl(url: string): boolean {
  const lower = url.trim().toLowerCase()
  if (BLOCKED_SCHEMES.some((s) => lower.startsWith(s))) return false
  return lower.startsWith('https://') || lower.startsWith('http://') || lower.startsWith('mailto:')
}

function isTableSeparator(line: string): boolean {
  const t = line.trim()
  if (!t || !t.includes('|') || !t.includes('-')) return false
  if (!/^[|\-: \t]+$/.test(t)) return false
  return (t.match(/-/g) ?? []).length >= 3
}

function splitTableCells(line: string): string[] {
  let t = line.trim()
  if (t.startsWith('|')) t = t.slice(1)
  if (t.endsWith('|') && !t.endsWith('\\|')) t = t.slice(0, -1)
  const cells: string[] = []
  let cur = ''
  let escaped = false
  for (const ch of t) {
    if (escaped) {
      cur += ch === '|' ? '|' : '\\' + ch
      escaped = false
    } else if (ch === '\\') {
      escaped = true
    } else if (ch === '|') {
      cells.push(cur.trim())
      cur = ''
    } else {
      cur += ch
    }
  }
  if (escaped) cur += '\\'
  cells.push(cur.trim())
  return cells
}

function isProseContinuation(lines: string[], idx: number): boolean {
  const raw = lines[idx]
  if (!raw || !raw.trim()) return false
  const t = raw.trimEnd()
  const lead = t.trimStart()
  if (lead.startsWith('$$')) return false
  if (HEADING_RE.test(t)) return false
  if (HR_RE.test(t)) return false
  if (QUOTE_RE.test(raw)) return false
  if (BULLET_RE.test(raw) || ORDERED_RE.test(raw)) return false
  if (DETAILS_OPEN_RE.test(t) || DETAILS_INLINE_RE.test(t)) return false
  if (IMAGE_ONLY_RE.test(t.trim())) return false
  if (t.includes('|') && idx + 1 < lines.length && isTableSeparator(lines[idx + 1])) return false
  return true
}

interface RawListRecord {
  level: number
  ordered: boolean
  number: number
  checked?: boolean
  text: string
  continuations: string[]
}

function buildListTree(records: RawListRecord[], from: number, level: number): [ProseBlock, number] {
  const ordered = records[from].ordered
  const items: ListItemNode[] = []
  let i = from
  while (i < records.length && records[i].level >= level) {
    if (records[i].level > level) {
      i++
      continue
    }
    if (records[i].ordered !== ordered) break
    const rec = records[i]
    let j = i + 1
    const childRecords: RawListRecord[] = []
    while (j < records.length && records[j].level > level) {
      childRecords.push(records[j])
      j++
    }
    const children: ProseBlock[] = []
    if (childRecords.length > 0) {
      children.push(buildListTree(childRecords, 0, childRecords[0].level)[0])
    }
    items.push({
      number: rec.ordered ? rec.number : undefined,
      checked: rec.checked,
      text: rec.text,
      continuations: rec.continuations,
      children,
    })
    i = j
  }
  const block: ProseBlock = ordered
    ? { kind: 'orderedList', start: records[from].number || 1, items }
    : { kind: 'bulletList', items }
  return [block, i - from]
}

export function parseProseBlocks(rawText: string): ProseBlock[] {
  const lines = rawText.replace(/\r\n/g, '\n').split('\n')
  const blocks: ProseBlock[] = []
  let i = 0

  while (i < lines.length) {
    const line = lines[i]
    const trimmed = line.trimEnd()
    const lead = trimmed.trimStart()

    if (!line.trim()) {
      i++
      continue
    }

    if (lead.startsWith('$$')) {
      const rest = lead.slice(2)
      let body = ''
      const closeSame = rest.indexOf('$$')
      if (closeSame !== -1) {
        body = rest.slice(0, closeSame)
        i++
      } else {
        if (rest.trim()) body = rest.trim()
        i++
        while (i < lines.length) {
          const l = lines[i]
          const cIdx = l.indexOf('$$')
          if (cIdx !== -1) {
            const before = l.slice(0, cIdx).trim()
            if (before) body += (body ? '\n' : '') + before
            i++
            break
          }
          body += (body ? '\n' : '') + l.trim()
          i++
        }
      }
      if (body.trim()) blocks.push({ kind: 'math', latex: body.trim() })
      continue
    }

    const hMatch = HEADING_RE.exec(trimmed)
    if (hMatch) {
      blocks.push({ kind: 'heading', level: hMatch[1].length, text: hMatch[2] })
      i++
      continue
    }

    if (HR_RE.test(trimmed)) {
      blocks.push({ kind: 'divider' })
      i++
      continue
    }

    if (line.includes('|') && i + 1 < lines.length && isTableSeparator(lines[i + 1])) {
      const headerCells = splitTableCells(lines[i])
      const alignCells = splitTableCells(lines[i + 1])
      const colCount = Math.max(headerCells.length, alignCells.length)
      const headers = Array.from({ length: colCount }, (_, c) => headerCells[c] ?? '')
      const aligns: TableAlign[] = Array.from({ length: colCount }, (_, c) => {
        const cell = (alignCells[c] ?? '').trim()
        const l = cell.startsWith(':')
        const r = cell.endsWith(':')
        return l && r ? 'center' : r ? 'right' : 'left'
      })
      const rows: string[][] = []
      i += 2
      while (i < lines.length) {
        const rowLine = lines[i]
        if (!rowLine.trim() || !rowLine.includes('|')) break
        if (isTableSeparator(rowLine)) {
          i++
          continue
        }
        const cells = splitTableCells(rowLine)
        rows.push(Array.from({ length: colCount }, (_, c) => cells[c] ?? ''))
        i++
      }
      blocks.push({ kind: 'table', headers, aligns, rows })
      continue
    }

    if (QUOTE_RE.test(line)) {
      const inner: string[] = []
      while (i < lines.length) {
        const qm = QUOTE_RE.exec(lines[i])
        if (!qm) break
        inner.push(qm[1])
        i++
      }
      blocks.push({ kind: 'blockquote', blocks: parseProseBlocks(inner.join('\n')) })
      continue
    }

    const detInline = DETAILS_INLINE_RE.exec(trimmed)
    if (detInline || DETAILS_OPEN_RE.test(trimmed)) {
      let summary = detInline ? detInline[1].trim() : ''
      i++
      if (!detInline) {
        while (i < lines.length) {
          const sm = SUMMARY_RE.exec(lines[i].trimEnd())
          if (sm) {
            summary = sm[1].trim()
            i++
            break
          }
          if (lines[i].trim()) break
          i++
        }
      }
      const inner: string[] = []
      while (i < lines.length) {
        if (DETAILS_CLOSE_RE.test(lines[i].trimEnd())) {
          i++
          break
        }
        inner.push(lines[i])
        i++
      }
      blocks.push({
        kind: 'collapsible',
        summary: summary || 'Details',
        blocks: parseProseBlocks(inner.join('\n')),
      })
      continue
    }

    if (BULLET_RE.test(line) || ORDERED_RE.test(line)) {
      const records: RawListRecord[] = []
      while (i < lines.length) {
        const l = lines[i]
        const bm = BULLET_RE.exec(l)
        const om = ORDERED_RE.exec(l)
        if (bm) {
          const indent = (bm[1].match(/ /g) ?? []).length + (bm[1].match(/\t/g) ?? []).length * 2
          const rawItem = bm[3]
          const tm = TASK_PREFIX_RE.exec(rawItem)
          records.push({
            level: Math.floor(indent / 2),
            ordered: false,
            number: 0,
            checked: tm ? tm[1].toLowerCase() === 'x' : undefined,
            text: tm ? tm[2] : rawItem,
            continuations: [],
          })
          i++
        } else if (om) {
          const indent = (om[1].match(/ /g) ?? []).length + (om[1].match(/\t/g) ?? []).length * 2
          records.push({
            level: Math.floor(indent / 2),
            ordered: true,
            number: Number.parseInt(om[2], 10) || 1,
            text: om[3],
            continuations: [],
          })
          i++
        } else if (l.trim() && records.length > 0) {
          const last = records[records.length - 1]
          const req = ' '.repeat(Math.max(last.level * 2 + 2, 1))
          if (l.startsWith(req)) {
            last.continuations.push(l.trim())
            i++
          } else {
            break
          }
        } else {
          break
        }
      }
      let r = 0
      while (r < records.length) {
        const [listBlock, used] = buildListTree(records, r, records[r].level)
        blocks.push(listBlock)
        r += used
      }
      continue
    }

    const imgMatch = IMAGE_ONLY_RE.exec(trimmed.trim())
    if (imgMatch && isSafeUrl(imgMatch[2])) {
      blocks.push({ kind: 'image', alt: imgMatch[1], url: imgMatch[2] })
      i++
      continue
    }

    const paraLines: string[] = []
    while (i < lines.length && isProseContinuation(lines, i)) {
      paraLines.push(lines[i].trimEnd())
      i++
    }
    if (paraLines.length > 0) {
      blocks.push({ kind: 'paragraph', text: paraLines.join('\n') })
    } else {
      blocks.push({ kind: 'paragraph', text: line })
      i++
    }
  }

  return blocks
}

const INLINE_RE =
  /`([^`\n]+)`|\\\(([\s\S]+?)\\\)|\\\[([\s\S]+?)\\\]|\$\$(?!\s)([\s\S]+?)\$\$|\$(?=[^$\n]*[\\^_])([^$\n]+?)\$|!\[([^\]]*)\]\(([^)\s]+)\)|\[([^\]\n]+)\]\(([^)\s]+)\)|(https?:\/\/[^\s<>[\]{}"'()]+[^\s<>[\]{}"'().,;:!?])|\*\*([\s\S]+?)\*\*|__([\s\S]+?)__|(?<!\*)\*([^*\n]+?)\*(?!\*)|(?<![\w])_(?!_)([^_\n]+?)_(?![\w])|~~([\s\S]+?)~~|\\([*_#`[\]()~\\-])|[\[【〚［]\s*(\d{1,2})\s*[\]】〛］]/g

function renderTextLeaf(text: string, keyPrefix: string): ReactNode {
  const normalised = emojiToIconTokens(text)
  if (!normalised.includes(':')) return normalised
  const nodes: ReactNode[] = []
  let last = 0
  let k = 0
  for (const m of normalised.matchAll(ICON_TOKEN_RE)) {
    const idx = m.index ?? 0
    if (idx > last) nodes.push(<Fragment key={`${keyPrefix}-t${k++}`}>{normalised.slice(last, idx)}</Fragment>)
    const name = canonicalIconName(m[1])
    if (name) {
      nodes.push(<GlassIcon key={`${keyPrefix}-i${k++}`} name={name} tile={18} size={11} className="nx-inline-icon" />)
    } else {
      nodes.push(<Fragment key={`${keyPrefix}-u${k++}`}>{m[0]}</Fragment>)
    }
    last = idx + m[0].length
  }
  if (last < normalised.length) nodes.push(<Fragment key={`${keyPrefix}-t${k++}`}>{normalised.slice(last)}</Fragment>)
  return nodes
}

function renderInline(
  text: string,
  theme: Theme,
  accent: string,
  citations?: Citation[],
  keyPrefix = 'in',
  depth = 0
): ReactNode[] {
  if (!text) return []
  if (depth > 6) return [renderTextLeaf(text, keyPrefix)]
  const out: ReactNode[] = []
  let last = 0
  let k = 0
  const re = new RegExp(INLINE_RE.source, 'g')

  for (const m of text.matchAll(re)) {
    const idx = m.index ?? 0
    if (idx > last) {
      out.push(<Fragment key={`${keyPrefix}-p${k++}`}>{renderTextLeaf(text.slice(last, idx), `${keyPrefix}-l${k}`)}</Fragment>)
    }

    const [
      full,
      code,
      mathParen,
      mathBracket,
      mathDisplay,
      mathDollar,
      imgAlt,
      imgUrl,
      linkText,
      linkUrl,
      bareUrl,
      boldStar,
      boldUnder,
      italicStar,
      italicUnder,
      strike,
      escaped,
      citeNum,
    ] = m

    if (code !== undefined) {
      out.push(
        <code
          key={`${keyPrefix}-c${k++}`}
          style={{
            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
            fontSize: '0.88em',
            padding: '1px 5px',
            borderRadius: 5,
            background: theme.raised,
            border: `1px solid ${theme.border}`,
          }}
        >
          {code}
        </code>
      )
    } else if (mathParen !== undefined || mathBracket !== undefined || mathDisplay !== undefined || mathDollar !== undefined) {
      const latex = mathParen ?? mathBracket ?? mathDisplay ?? mathDollar ?? ''
      out.push(
        <span
          key={`${keyPrefix}-m${k++}`}
          style={{
            fontFamily: 'ui-serif, Georgia, Cambria, "Times New Roman", serif',
            fontStyle: 'italic',
            padding: '0 2px',
          }}
        >
          {latex}
        </span>
      )
    } else if (imgUrl !== undefined) {
      const label = imgAlt || imgUrl
      if (isSafeUrl(imgUrl)) {
        out.push(
          <a
            key={`${keyPrefix}-im${k++}`}
            href={imgUrl}
            target="_blank"
            rel="noopener noreferrer"
            style={{ color: accent, textDecoration: 'underline' }}
          >
            {label}
          </a>
        )
      } else {
        out.push(<Fragment key={`${keyPrefix}-im${k++}`}>{label}</Fragment>)
      }
    } else if (linkUrl !== undefined) {
      if (isSafeUrl(linkUrl)) {
        out.push(
          <a
            key={`${keyPrefix}-lk${k++}`}
            href={linkUrl}
            target="_blank"
            rel="noopener noreferrer"
            style={{ color: accent, textDecoration: 'underline' }}
          >
            {renderInline(linkText, theme, accent, citations, `${keyPrefix}-lkt${k}`, depth + 1)}
          </a>
        )
      } else {
        out.push(
          <Fragment key={`${keyPrefix}-lk${k++}`}>
            {renderInline(linkText, theme, accent, citations, `${keyPrefix}-lkt${k}`, depth + 1)}
          </Fragment>
        )
      }
    } else if (bareUrl !== undefined) {
      if (isSafeUrl(bareUrl)) {
        out.push(
          <a
            key={`${keyPrefix}-bu${k++}`}
            href={bareUrl}
            target="_blank"
            rel="noopener noreferrer"
            style={{ color: accent, textDecoration: 'underline' }}
          >
            {bareUrl}
          </a>
        )
      } else {
        out.push(<Fragment key={`${keyPrefix}-bu${k++}`}>{bareUrl}</Fragment>)
      }
    } else if (boldStar !== undefined || boldUnder !== undefined) {
      const inner = boldStar ?? boldUnder ?? ''
      out.push(
        <strong key={`${keyPrefix}-b${k++}`} style={{ fontWeight: 650 }}>
          {renderInline(inner, theme, accent, citations, `${keyPrefix}-bi${k}`, depth + 1)}
        </strong>
      )
    } else if (italicStar !== undefined || italicUnder !== undefined) {
      const inner = italicStar ?? italicUnder ?? ''
      out.push(
        <em key={`${keyPrefix}-i${k++}`}>
          {renderInline(inner, theme, accent, citations, `${keyPrefix}-ii${k}`, depth + 1)}
        </em>
      )
    } else if (strike !== undefined) {
      out.push(
        <del key={`${keyPrefix}-s${k++}`}>
          {renderInline(strike, theme, accent, citations, `${keyPrefix}-si${k}`, depth + 1)}
        </del>
      )
    } else if (escaped !== undefined) {
      out.push(<Fragment key={`${keyPrefix}-e${k++}`}>{escaped}</Fragment>)
    } else if (citeNum !== undefined) {
      const n = Number.parseInt(citeNum, 10)
      const src = citations?.find((c) => c.ordinal === n)
      if (src) {
        out.push(
          <button
            key={`${keyPrefix}-ct${k++}`}
            onClick={() => window.open(src.url, '_blank', 'noopener,noreferrer')}
            aria-label={`Open citation ${n}: ${src.title}`}
            title={`${src.title}${src.domain ? ` — ${src.domain}` : ''}`}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              minWidth: 16,
              height: 16,
              margin: '0 2px',
              padding: '0 3px',
              borderRadius: 4,
              verticalAlign: '2px',
              border: 'none',
              background: `${accent}29`,
              color: accent,
              fontSize: 10,
              fontWeight: 700,
              cursor: 'pointer',
            }}
          >
            {n}
          </button>
        )
      } else {
        out.push(<Fragment key={`${keyPrefix}-ct${k++}`}>{full}</Fragment>)
      }
    }

    last = idx + full.length
  }

  if (last < text.length) {
    out.push(<Fragment key={`${keyPrefix}-p${k++}`}>{renderTextLeaf(text.slice(last), `${keyPrefix}-l${k}`)}</Fragment>)
  }
  return out
}

function renderProseBlock(
  block: ProseBlock,
  idx: number,
  theme: Theme,
  accent: string,
  citations?: Citation[]
): ReactNode {
  switch (block.kind) {
    case 'paragraph':
      return (
        <div key={idx} style={{ color: theme.text, whiteSpace: 'pre-wrap' }}>
          {renderInline(block.text, theme, accent, citations, `b${idx}`)}
        </div>
      )

    case 'heading': {
      const sizes: Record<number, number> = { 1: 21, 2: 18, 3: 16, 4: 15, 5: 14, 6: 13 }
      const Tag = (`h${Math.min(6, Math.max(1, block.level))}` as unknown) as 'h2'
      return (
        <Tag
          key={idx}
          style={{
            margin: '4px 0 0',
            fontSize: sizes[block.level] ?? 15,
            fontWeight: 700,
            lineHeight: 1.35,
            color: block.level === 6 ? theme.text2 : theme.text,
          }}
        >
          {renderInline(block.text, theme, accent, citations, `h${idx}`)}
        </Tag>
      )
    }

    case 'bulletList':
    case 'orderedList': {
      const isOrdered = block.kind === 'orderedList'
      const ListTag = isOrdered ? 'ol' : 'ul'
      return (
        <ListTag
          key={idx}
          start={isOrdered ? block.start : undefined}
          style={{
            margin: 0,
            paddingLeft: 20,
            display: 'flex',
            flexDirection: 'column',
            gap: 4,
            color: theme.text,
            listStyleType: isOrdered ? 'decimal' : 'disc',
          }}
        >
          {block.items.map((item, j) => (
            <li key={j} style={{ listStyleType: item.checked !== undefined ? 'none' : undefined }}>
              {item.checked !== undefined && (
                <input
                  type="checkbox"
                  checked={item.checked}
                  readOnly
                  style={{ marginRight: 6, accentColor: accent, verticalAlign: 'middle' }}
                />
              )}
              <span>{renderInline(item.text, theme, accent, citations, `li${idx}-${j}`)}</span>
              {item.continuations.map((c, ci) => (
                <div key={ci} style={{ marginTop: 2 }}>
                  {renderInline(c, theme, accent, citations, `lic${idx}-${j}-${ci}`)}
                </div>
              ))}
              {item.children.length > 0 && (
                <div style={{ marginTop: 4 }}>
                  {item.children.map((ch, chi) => renderProseBlock(ch, chi, theme, accent, citations))}
                </div>
              )}
            </li>
          ))}
        </ListTag>
      )
    }

    case 'blockquote':
      return (
        <blockquote
          key={idx}
          style={{
            margin: 0,
            padding: '4px 0 4px 12px',
            borderLeft: `3px solid ${accent}`,
            color: theme.text2,
            display: 'flex',
            flexDirection: 'column',
            gap: 8,
          }}
        >
          {block.blocks.map((inner, j) => renderProseBlock(inner, j, theme, accent, citations))}
        </blockquote>
      )

    case 'table':
      return (
        <div
          key={idx}
          style={{
            overflowX: 'auto',
            borderRadius: 10,
            border: `1px solid ${theme.border}`,
            background: theme.card,
          }}
        >
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: TYPE.label }}>
            <thead>
              <tr style={{ borderBottom: `1px solid ${theme.border}` }}>
                {block.headers.map((h, c) => (
                  <th
                    key={c}
                    style={{
                      padding: '8px 10px',
                      textAlign: block.aligns[c] ?? 'left',
                      fontWeight: 650,
                      color: theme.text,
                    }}
                  >
                    {renderInline(h, theme, accent, citations, `th${idx}-${c}`)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {block.rows.map((row, r) => (
                <tr key={r} style={{ borderTop: r > 0 ? `1px solid ${theme.border}` : undefined }}>
                  {row.map((cell, c) => (
                    <td
                      key={c}
                      style={{
                        padding: '7px 10px',
                        textAlign: block.aligns[c] ?? 'left',
                        color: theme.text2,
                      }}
                    >
                      {renderInline(cell, theme, accent, citations, `td${idx}-${r}-${c}`)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )

    case 'divider':
      return <hr key={idx} style={{ border: 'none', borderTop: `1px solid ${theme.border}`, margin: '4px 0' }} />

    case 'math':
      return (
        <div
          key={idx}
          style={{
            padding: '10px 14px',
            borderRadius: 10,
            background: theme.card,
            border: `1px solid ${theme.border}`,
            fontFamily: 'ui-serif, Georgia, Cambria, "Times New Roman", serif',
            fontStyle: 'italic',
            color: theme.text,
            overflowX: 'auto',
          }}
        >
          {block.latex}
        </div>
      )

    case 'image':
      return (
        <figure key={idx} style={{ margin: 0 }}>
          <img
            src={block.url}
            alt={block.alt}
            loading="lazy"
            style={{ maxWidth: '100%', borderRadius: 10, border: `1px solid ${theme.border}` }}
          />
          {block.alt && (
            <figcaption style={{ fontSize: TYPE.micro, color: theme.text3, marginTop: 4 }}>{block.alt}</figcaption>
          )}
        </figure>
      )

    case 'collapsible':
      return (
        <details
          key={idx}
          style={{
            padding: '8px 12px',
            borderRadius: 10,
            background: theme.card,
            border: `1px solid ${theme.border}`,
            color: theme.text,
          }}
        >
          <summary style={{ cursor: 'pointer', fontWeight: 600 }}>
            {renderInline(block.summary, theme, accent, citations, `sum${idx}`)}
          </summary>
          <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 8 }}>
            {block.blocks.map((inner, j) => renderProseBlock(inner, j, theme, accent, citations))}
          </div>
        </details>
      )
  }
}

function Prose({
  text,
  theme,
  accent,
  citations,
}: {
  text: string
  theme: Theme
  accent: string
  citations?: Citation[]
}) {
  const blocks = useMemo(() => parseProseBlocks(text), [text])
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10, color: theme.text }}>
      {blocks.map((block, i) => renderProseBlock(block, i, theme, accent, citations))}
    </div>
  )
}

export function MessageContent({
  text,
  dark = true,
  onPrompt,
  streaming = false,
  citations,
}: {
  text: string
  dark?: boolean
  onPrompt?: (prompt: string) => void
  streaming?: boolean
  citations?: Citation[]
}) {
  const segments = useMemo(() => parseStream(text), [text])
  const theme = useMemo(() => themeFor(dark), [dark])
  const accent = segments.find((s): s is Extract<Segment, { kind: 'widget' }> => s.kind === 'widget')?.payload.accent as string | undefined
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, fontSize: TYPE.body, lineHeight: 1.65 }}>
      {segments.map((segment, i) => {
        switch (segment.kind) {
          case 'text':
            return (
              <Prose key={i} text={segment.text} theme={theme} accent={accent ?? '#2DD4A8'} citations={citations} />
            )
          case 'widget':
            return segment.complete ? (
              <WidgetRenderer key={i} kind={segment.widget} payload={segment.payload} dark={dark} onPrompt={onPrompt} />
            ) : (
              <Skeleton key={i} label={`Building ${segment.widget}…`} />
            )
          case 'mermaid':
            return (
              <MermaidBlock key={i} source={segment.source} />
            )
          case 'html':
            return <HtmlBlock key={i} source={segment.source} />
          case 'svg':
            return <SvgBlock key={i} source={segment.source} />
          case 'code':
            return <CodeBlock key={i} code={segment.code} language={segment.language} />
          case 'map':
            return <WidgetRenderer key={i} kind="map" payload={segment.payload} dark={dark} />
          default:
            return null
        }
      })}
    </div>
  )
}
