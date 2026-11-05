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

import { Fragment, useMemo, useState, type ReactNode } from 'react'
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
  snippet?: string
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

type CalloutMatch = {
  tone: 'summary' | 'warning' | 'fact'
  icon: string
  title: string
  body: string
}

function matchCalloutText(raw: string): CalloutMatch | null {
  const trimmed = raw.trim()
  const alertMatch = trimmed.match(/^\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\]\s*([\s\S]*)$/i)
  if (alertMatch) {
    const kind = alertMatch[1].toUpperCase()
    const body = alertMatch[2].trim()
    if (kind === 'WARNING' || kind === 'CAUTION' || kind === 'IMPORTANT') {
      return { tone: 'warning', icon: '⚠️', title: kind === 'IMPORTANT' ? 'Important' : 'Warning', body }
    }
    return { tone: 'fact', icon: '💡', title: kind === 'TIP' ? 'Key Insight' : 'Note', body }
  }
  const leadMatch = trimmed.match(
    /^(?:(?:⚡|⚠️|💡|✦|📌)\s*)?\*\*(Quick summary|Summary|TL;DR|Important|Warning|Caution|Key Insights?|Quick Fact|Pro Tip|Tip|Note)\s*:?\*\*\s*:?\s*([\s\S]+)$/i
  )
  if (!leadMatch) return null
  const label = leadMatch[1].trim()
  const body = leadMatch[2].trim()
  const lower = label.toLowerCase()
  if (lower === 'important' || lower === 'warning' || lower === 'caution') {
    return { tone: 'warning', icon: '⚠️', title: label, body }
  }
  if (lower.includes('fact') || lower.includes('insight') || lower.includes('tip') || lower === 'note') {
    return { tone: 'fact', icon: '💡', title: label, body }
  }
  return { tone: 'summary', icon: '⚡', title: label, body }
}

function sectionBadgeForHeading(raw: string): string | null {
  const clean = raw.replace(/[*_`~]/g, '').trim()
  if (/^[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}✦⚡🎯☰☑️🔗📖📊💡⚠️]/u.test(clean)) return null
  const lower = clean.toLowerCase()
  if (lower.includes('priorit') || lower.includes('goal') || lower.includes('objective')) return '🎯'
  if (lower.includes('step-by-step') || lower.includes('steps') || lower.includes('plan') || lower.includes('roadmap')) return '☰'
  if (lower.includes('next action') || lower.includes('action item') || lower.includes('checklist') || lower.includes('todo')) return '☑️'
  if (lower.includes('key source') || lower.includes('sources')) return '🔗'
  if (lower.includes('reference') || lower.includes('citation') || lower.includes('further reading')) return '📖'
  if (lower.includes('feature') || lower.includes('highlight') || lower.includes('benefit')) return '✦'
  if (lower.startsWith('top ') || lower.includes('chart') || lower.includes('metric') || lower.includes('overview')) return '📊'
  return null
}

function splitStepTitleAndDetail(raw: string): { title: string; detail: string } | null {
  const match = raw.trim().match(/^\*\*([^*]+)\*\*\s*(?:[—–\-:]\s*|\n+)(.+)$/s)
  if (!match) return null
  return { title: match[1].trim(), detail: match[2].trim() }
}

function renderCalloutCard(
  callout: CalloutMatch,
  key: string | number,
  theme: Theme,
  accent: string,
  citations?: Citation[],
  extraBlocks?: ReactNode
): ReactNode {
  const isWarning = callout.tone === 'warning'
  const bg = isWarning ? 'rgba(255, 244, 230, 0.92)' : 'rgba(242, 236, 255, 0.92)'
  const border = isWarning ? 'rgba(245, 158, 11, 0.34)' : 'rgba(124, 92, 252, 0.28)'
  const titleColor = isWarning ? '#7C3A0A' : '#241B4B'
  const bodyColor = isWarning ? '#5C3412' : '#3B345F'
  return (
    <div
      key={key}
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 10,
        padding: '12px 14px',
        borderRadius: 14,
        background: bg,
        border: `1px solid ${border}`,
        boxShadow: '0 4px 14px rgba(90, 65, 180, 0.06)',
      }}
    >
      <span
        aria-hidden
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: 24,
          height: 24,
          borderRadius: 8,
          background: isWarning ? 'rgba(245, 158, 11, 0.18)' : 'rgba(124, 92, 252, 0.15)',
          fontSize: 13,
          flexShrink: 0,
          marginTop: 1,
        }}
      >
        {callout.icon}
      </span>
      <div style={{ flex: 1, minWidth: 0, color: bodyColor, fontSize: 13.5, lineHeight: 1.55 }}>
        <div>
          <strong style={{ fontWeight: 700, color: titleColor }}>{`${callout.title}: `}</strong>
          {callout.body ? renderInline(callout.body, theme, accent, citations, `co-${key}`) : null}
        </div>
        {extraBlocks}
      </div>
    </div>
  )
}

function InteractiveProseChecklistItem({
  item,
  idx,
  j,
  theme,
  accent,
  citations,
}: {
  item: ListItemNode
  idx: number
  j: number
  theme: Theme
  accent: string
  citations?: Citation[]
}) {
  const [checked, setChecked] = useState(Boolean(item.checked))
  return (
    <li
      onClick={() => setChecked((prev) => !prev)}
      style={{
        listStyleType: 'none',
        display: 'flex',
        alignItems: 'flex-start',
        gap: 10,
        padding: '5px 8px',
        borderRadius: 10,
        cursor: 'pointer',
        background: checked ? 'rgba(124, 92, 252, 0.08)' : 'transparent',
        transition: 'background 0.15s ease',
      }}
    >
      <span
        role="checkbox"
        aria-checked={checked}
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: 18,
          height: 18,
          marginTop: 2,
          borderRadius: 5,
          border: checked ? 'none' : '1.5px solid rgba(124, 92, 252, 0.45)',
          background: checked ? '#6C4CF6' : 'rgba(255, 255, 255, 0.85)',
          color: '#FFFFFF',
          fontSize: 11,
          fontWeight: 800,
          flexShrink: 0,
        }}
      >
        {checked ? '✓' : ''}
      </span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <span style={{ textDecoration: checked ? 'line-through' : 'none', opacity: checked ? 0.68 : 1 }}>
          {renderInline(item.text, theme, accent, citations, `li${idx}-${j}`)}
        </span>
        {item.continuations.map((c, ci) => (
          <div key={ci} style={{ marginTop: 2 }}>
            {renderInline(c, theme, accent, citations, `lic${idx}-${j}-${ci}`)}
          </div>
        ))}
      </div>
    </li>
  )
}

function ProseTableGrid({
  block,
  idx,
  theme,
  accent,
  citations,
}: {
  block: Extract<ProseBlock, { kind: 'table' }>
  idx: number
  theme: Theme
  accent: string
  citations?: Citation[]
}) {
  const [pageSize, setPageSize] = useState<number>(5)
  const [page, setPage] = useState<number>(0)
  const totalPages = Math.max(1, Math.ceil(block.rows.length / pageSize))
  const safePage = Math.min(page, totalPages - 1)
  const visibleRows = block.rows.slice(safePage * pageSize, safePage * pageSize + pageSize)

  const renderTrendCell = (raw: string, keyPrefix: string) => {
    const clean = raw.replace(/[*_`]/g, '').trim()
    const m = clean.match(/^([↑↓↗↘+-])?\s*([+-]?\d+(?:\.\d+)?%)$/)
    if (!m) return renderInline(raw, theme, accent, citations, keyPrefix)
    const isNeg = clean.startsWith('-') || clean.startsWith('↓') || clean.startsWith('↘')
    const arrow = isNeg ? '↓' : '↑'
    const num = m[2].startsWith('+') || m[2].startsWith('-') ? m[2] : `${isNeg ? '-' : '+'}${m[2]}`
    return (
      <span
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 3,
          fontWeight: 700,
          color: isNeg ? '#E04F5F' : '#129A5A',
        }}
      >
        <span>{arrow}</span>
        <span>{num}</span>
      </span>
    )
  }

  return (
    <div
      style={{
        overflowX: 'auto',
        borderRadius: 14,
        border: `1px solid ${theme.border}`,
        background: theme.card,
        boxShadow: '0 6px 18px rgba(70, 52, 150, 0.06)',
      }}
    >
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: TYPE.label }}>
        <thead>
          <tr style={{ background: 'rgba(124, 92, 252, 0.11)', borderBottom: `1px solid ${theme.border}` }}>
            {block.headers.map((h, c) => (
              <th
                key={c}
                style={{
                  padding: '9px 12px',
                  textAlign: block.aligns[c] ?? 'left',
                  fontWeight: 700,
                  color: theme.text,
                }}
              >
                {renderInline(h, theme, accent, citations, `th${idx}-${c}`)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {visibleRows.map((row, r) => (
            <tr key={r} style={{ borderTop: r > 0 ? `1px solid ${theme.border}` : undefined }}>
              {row.map((cell, c) => (
                <td
                  key={c}
                  style={{
                    padding: '8px 12px',
                    textAlign: block.aligns[c] ?? 'left',
                    color: c === 0 ? theme.text : theme.text2,
                    fontWeight: c === 0 ? 600 : 400,
                  }}
                >
                  {renderTrendCell(cell, `td${idx}-${r}-${c}`)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      {block.rows.length > 5 && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '8px 12px',
            borderTop: `1px solid ${theme.border}`,
            fontSize: 12,
            color: theme.text2,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <button
              type="button"
              onClick={() => setPage(Math.max(0, safePage - 1))}
              disabled={safePage === 0}
              style={{
                border: 'none',
                background: 'transparent',
                color: theme.text2,
                cursor: safePage === 0 ? 'default' : 'pointer',
                opacity: safePage === 0 ? 0.4 : 1,
                padding: '2px 6px',
              }}
            >
              ‹
            </button>
            {Array.from({ length: totalPages }, (_, p) => (
              <button
                key={p}
                type="button"
                onClick={() => setPage(p)}
                style={{
                  width: 24,
                  height: 24,
                  borderRadius: 12,
                  border: 'none',
                  background: p === safePage ? '#6C4CF6' : 'transparent',
                  color: p === safePage ? '#FFFFFF' : theme.text2,
                  fontWeight: 700,
                  fontSize: 11.5,
                  cursor: 'pointer',
                }}
              >
                {p + 1}
              </button>
            ))}
            <button
              type="button"
              onClick={() => setPage(Math.min(totalPages - 1, safePage + 1))}
              disabled={safePage >= totalPages - 1}
              style={{
                border: 'none',
                background: 'transparent',
                color: theme.text2,
                cursor: safePage >= totalPages - 1 ? 'default' : 'pointer',
                opacity: safePage >= totalPages - 1 ? 0.4 : 1,
                padding: '2px 6px',
              }}
            >
              ›
            </button>
          </div>
          <button
            type="button"
            onClick={() => {
              const next = pageSize === 5 ? 10 : pageSize === 10 ? 25 : 5
              setPageSize(next)
              setPage(0)
            }}
            style={{
              border: `1px solid ${theme.border}`,
              background: 'rgba(124, 92, 252, 0.08)',
              color: theme.text,
              borderRadius: 8,
              padding: '3px 8px',
              fontSize: 11.5,
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            Show: {pageSize} ▾
          </button>
        </div>
      )}
    </div>
  )
}

function renderProseBlock(
  block: ProseBlock,
  idx: number,
  theme: Theme,
  accent: string,
  citations?: Citation[]
): ReactNode {
  switch (block.kind) {
    case 'paragraph': {
      const callout = matchCalloutText(block.text)
      if (callout) {
        return renderCalloutCard(callout, idx, theme, accent, citations)
      }
      return (
        <div key={idx} style={{ color: theme.text, whiteSpace: 'pre-wrap' }}>
          {renderInline(block.text, theme, accent, citations, `b${idx}`)}
        </div>
      )
    }

    case 'heading': {
      const sizes: Record<number, number> = { 1: 21, 2: 17.5, 3: 15.5, 4: 14.5, 5: 13.5, 6: 13 }
      const Tag = (`h${Math.min(6, Math.max(1, block.level))}` as unknown) as 'h2'
      const badge = block.level >= 2 && block.level <= 3 ? sectionBadgeForHeading(block.text) : null
      return (
        <Tag
          key={idx}
          style={{
            margin: '6px 0 0',
            fontSize: sizes[block.level] ?? 15,
            fontWeight: 700,
            lineHeight: 1.35,
            color: block.level === 6 ? theme.text2 : theme.text,
            display: 'flex',
            alignItems: 'center',
            gap: 7,
          }}
        >
          {badge && (
            <span aria-hidden style={{ fontSize: Math.max(13, (sizes[block.level] ?? 15) - 2) }}>
              {badge}
            </span>
          )}
          <span>{renderInline(block.text, theme, accent, citations, `h${idx}`)}</span>
        </Tag>
      )
    }

    case 'orderedList': {
      return (
        <ol
          key={idx}
          start={block.start}
          style={{
            margin: 0,
            paddingLeft: 0,
            listStyleType: 'none',
            display: 'flex',
            flexDirection: 'column',
            gap: 8,
            color: theme.text,
          }}
        >
          {block.items.map((item, j) => {
            const stepNum = block.start + j
            const split = splitStepTitleAndDetail(item.text)
            return (
              <li
                key={j}
                style={{
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: 10,
                }}
              >
                <span
                  aria-hidden
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    width: 24,
                    height: 24,
                    borderRadius: 12,
                    background: 'rgba(124, 92, 252, 0.14)',
                    border: '1px solid rgba(124, 92, 252, 0.26)',
                    color: '#5B3DF5',
                    fontSize: 12,
                    fontWeight: 700,
                    flexShrink: 0,
                    marginTop: 1,
                  }}
                >
                  {stepNum}
                </span>
                <div style={{ flex: 1, minWidth: 0 }}>
                  {split ? (
                    <>
                      <div style={{ fontWeight: 700, color: theme.text, lineHeight: 1.35 }}>
                        {renderInline(split.title, theme, accent, citations, `olt${idx}-${j}`)}
                      </div>
                      <div style={{ fontSize: 13, color: theme.text2, marginTop: 2, lineHeight: 1.45 }}>
                        {renderInline(split.detail, theme, accent, citations, `old${idx}-${j}`)}
                      </div>
                    </>
                  ) : (
                    <span>{renderInline(item.text, theme, accent, citations, `li${idx}-${j}`)}</span>
                  )}
                  {item.continuations.map((c, ci) => (
                    <div key={ci} style={{ marginTop: 2, fontSize: 13, color: theme.text2 }}>
                      {renderInline(c, theme, accent, citations, `lic${idx}-${j}-${ci}`)}
                    </div>
                  ))}
                  {item.children.length > 0 && (
                    <div style={{ marginTop: 6 }}>
                      {item.children.map((ch, chi) => renderProseBlock(ch, chi, theme, accent, citations))}
                    </div>
                  )}
                </div>
              </li>
            )
          })}
        </ol>
      )
    }

    case 'bulletList': {
      const hasCheckboxes = block.items.some((it) => it.checked !== undefined)
      return (
        <ul
          key={idx}
          style={{
            margin: 0,
            paddingLeft: hasCheckboxes ? 0 : 20,
            display: 'flex',
            flexDirection: 'column',
            gap: hasCheckboxes ? 4 : 5,
            color: theme.text,
            listStyleType: hasCheckboxes ? 'none' : 'disc',
          }}
        >
          {block.items.map((item, j) =>
            item.checked !== undefined ? (
              <InteractiveProseChecklistItem
                key={j}
                item={item}
                idx={idx}
                j={j}
                theme={theme}
                accent={accent}
                citations={citations}
              />
            ) : (
              <li key={j}>
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
            )
          )}
        </ul>
      )
    }

    case 'blockquote': {
      const firstBlock = block.blocks[0]
      if (firstBlock?.kind === 'paragraph') {
        const callout = matchCalloutText(firstBlock.text)
        if (callout) {
          const rest = block.blocks.slice(1)
          return renderCalloutCard(
            callout,
            idx,
            theme,
            accent,
            citations,
            rest.length > 0 ? (
              <div style={{ marginTop: 6, display: 'flex', flexDirection: 'column', gap: 6 }}>
                {rest.map((inner, j) => renderProseBlock(inner, j, theme, accent, citations))}
              </div>
            ) : undefined
          )
        }
      }
      return (
        <blockquote
          key={idx}
          style={{
            margin: 0,
            padding: '10px 14px',
            borderRadius: 12,
            background: 'rgba(124, 92, 252, 0.08)',
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
    }

    case 'table':
      return <ProseTableGrid key={idx} block={block} idx={idx} theme={theme} accent={accent} citations={citations} />

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

function isAcademicCitationDomain(domain: string, url: string): boolean {
  const hay = `${domain} ${url}`.toLowerCase()
  return (
    hay.includes('.edu') ||
    hay.includes('nature.com') ||
    hay.includes('arxiv.org') ||
    hay.includes('ieee.org') ||
    hay.includes('acm.org') ||
    hay.includes('springer.') ||
    hay.includes('sciencedirect.') ||
    hay.includes('oxford') ||
    hay.includes('cambridge') ||
    hay.includes('nih.gov') ||
    hay.includes('pubmed')
  )
}

function CitationsSourceCards({ citations, theme }: { citations: Citation[]; theme: Theme }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 4 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          fontSize: 13.5,
          fontWeight: 700,
          color: theme.text,
        }}
      >
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <span aria-hidden>🔗</span>
          <span>Key Sources</span>
        </span>
        <span style={{ fontSize: 12, fontWeight: 600, color: '#6C4CF6' }}>
          {citations.length} {citations.length === 1 ? 'source' : 'sources'}
        </span>
      </div>
      {citations.map((src) => {
        const domain =
          src.domain ||
          (() => {
            try {
              return new URL(src.url).hostname.replace(/^www\./, '')
            } catch {
              return src.url
            }
          })()
        const academic = isAcademicCitationDomain(domain, src.url)
        return (
          <a
            key={src.ordinal}
            href={src.url}
            target="_blank"
            rel="noopener noreferrer"
            style={{
              display: 'flex',
              alignItems: 'flex-start',
              gap: 10,
              padding: '11px 13px',
              borderRadius: 14,
              background: theme.card,
              border: `1px solid ${theme.border}`,
              textDecoration: 'none',
              color: theme.text,
              boxShadow: '0 4px 14px rgba(70, 52, 150, 0.05)',
            }}
          >
            <span
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                width: 26,
                height: 26,
                borderRadius: 13,
                background: 'rgba(124, 92, 252, 0.14)',
                border: '1px solid rgba(124, 92, 252, 0.25)',
                color: '#5B3DF5',
                fontSize: 12,
                fontWeight: 700,
                flexShrink: 0,
                marginTop: 1,
              }}
            >
              {src.ordinal}
            </span>
            <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 4 }}>
              <div style={{ fontWeight: 700, fontSize: 13.5, color: theme.text, lineHeight: 1.35 }}>
                {src.title}
              </div>
              {src.snippet && (
                <div style={{ fontSize: 12.5, color: theme.text2, lineHeight: 1.45 }}>
                  {src.snippet}
                </div>
              )}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: 8,
                  marginTop: 2,
                  flexWrap: 'wrap',
                }}
              >
                <span
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 4,
                    fontSize: 12,
                    fontWeight: 600,
                    color: '#5B3DF5',
                  }}
                >
                  <span>↗</span>
                  <span>{domain}</span>
                </span>
                <span
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 4,
                    padding: '2px 8px',
                    borderRadius: 999,
                    fontSize: 11,
                    fontWeight: 700,
                    background: academic ? 'rgba(124, 92, 252, 0.14)' : 'rgba(18, 154, 90, 0.14)',
                    color: academic ? '#5B3DF5' : '#129A5A',
                  }}
                >
                  <span>{academic ? '🏛' : '✓'}</span>
                  <span>{academic ? 'Academic' : 'Trusted Source'}</span>
                </span>
              </div>
            </div>
          </a>
        )
      })}
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
      {citations && citations.length > 0 && <CitationsSourceCards citations={citations} theme={theme} />}
    </div>
  )
}
