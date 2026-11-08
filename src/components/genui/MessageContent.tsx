'use client'

import { parseTableNumber, chartFromTableCells, plainTableCell } from '@/lib/nexly/table-number'
import { escapeCodeDisplay } from '@/lib/code/display-source'
import { downloadResponseFile, responseSourceArchive, responseSourceFile } from '@/lib/code/response-export'

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

import { Fragment, useMemo, useRef, useState, useId, type ReactNode } from 'react'
import { parseStream, type Segment } from './stream'
import { WidgetRenderer } from './WidgetRenderer'
import { SpontaneousConceptDeck } from './SpontaneousConceptDeck'
import { extractConceptDeck } from '@/lib/nexly/concept-deck'
import { Skeleton } from './primitives/Skeleton'
import { MermaidBlock } from './blocks/MermaidBlock'
import { HtmlBlock } from './blocks/HtmlBlock'
import { SvgBlock } from './blocks/SvgBlock'
import { CodeBlock } from './blocks/CodeBlock'
import { themeFor, TYPE } from './theme/tokens'
import { canonicalIconName, emojiToIconTokens, ICON_TOKEN_RE, extractLeadingIcon, cleanPlainText, semanticIconFor, sanitizeReplyEmojis } from '@/lib/nexly/icons'
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
          {escapeCodeDisplay(code)}
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
      const label = escapeCodeDisplay(imgAlt || imgUrl)
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
            {escapeCodeDisplay(bareUrl)}
          </a>
        )
      } else {
        out.push(<Fragment key={`${keyPrefix}-bu${k++}`}>{escapeCodeDisplay(bareUrl)}</Fragment>)
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
      return { tone: 'warning', icon: 'alert-triangle', title: kind === 'IMPORTANT' ? 'Important' : 'Warning', body }
    }
    return { tone: 'fact', icon: 'bulb', title: kind === 'TIP' ? 'Key Insight' : 'Note', body }
  }
  const normalized = emojiToIconTokens(trimmed)
  const leadMatch = normalized.match(
    /^(?:(?::[a-z0-9-]+:|✦)\s*)?\*\*(Quick summary|Summary|TL;DR|Important|Warning|Caution|Key Insights?|Quick Fact|Pro Tip|Tip|Note)\s*:?\*\*\s*:?\s*([\s\S]+)$/i
  )
  if (!leadMatch) return null
  const label = leadMatch[1].trim()
  const body = leadMatch[2].trim()
  const lower = label.toLowerCase()
  if (lower === 'important' || lower === 'warning' || lower === 'caution') {
    return { tone: 'warning', icon: 'alert-triangle', title: label, body }
  }
  if (lower.includes('fact') || lower.includes('insight') || lower.includes('tip') || lower === 'note') {
    return { tone: 'fact', icon: 'bulb', title: label, body }
  }
  return { tone: 'summary', icon: 'bolt', title: label, body }
}

function sectionBadgeForHeading(raw: string): { icon: string; tone: string } | null {
  const { icon: explicitIcon, rest } = extractLeadingIcon(raw.replace(/[*_`~]/g, '').trim())
  if (explicitIcon) return { icon: explicitIcon, tone: 'lavender' }
  const clean = cleanPlainText(rest).trim()
  if (!clean) return null
  const lower = clean.toLowerCase()
  if (lower.includes('priorit') || lower.includes('goal') || lower.includes('objective')) return { icon: 'target', tone: 'rose' }
  if (lower.includes('step-by-step') || lower.includes('steps') || lower.includes('plan') || lower.includes('roadmap') || lower.includes('what to do')) return { icon: 'list-check', tone: 'lavender' }
  if (lower.includes('next action') || lower.includes('action item') || lower.includes('checklist') || lower.includes('todo') || lower.includes('next step')) return { icon: 'checkbox', tone: 'green' }
  if (lower.includes('key source') || lower.includes('sources')) return { icon: 'link', tone: 'blue' }
  if (lower.includes('reference') || lower.includes('citation') || lower.includes('further reading')) return { icon: 'book-2', tone: 'indigo' }
  if (lower.includes('feature') || lower.includes('highlight') || lower.includes('benefit')) return { icon: 'sparkles', tone: 'amber' }
  if (lower.startsWith('top ') || lower.includes('chart') || lower.includes('metric') || lower.includes('overview')) return { icon: 'chart-bar', tone: 'cyan' }
  if (lower.includes('calculator') || lower.includes('budget') || lower.includes('pricing')) return { icon: 'calculator', tone: 'amber' }
  if (lower.includes('fix') || lower.includes('solution') || lower.includes('repair') || lower.includes('troubleshoot')) return { icon: 'tool', tone: 'green' }
  if (lower.includes('why ') || lower.includes('how ') || lower.includes('explain') || lower.includes('reason')) return { icon: 'bulb', tone: 'amber' }
  if (lower.includes('bottom line') || lower.includes('conclusion') || lower.includes('verdict') || lower.includes('takeaway')) return { icon: 'circle-check', tone: 'green' }
  if (lower.includes('difference') || lower.includes('compare') || lower.includes('optional') || lower.includes('alternative')) return { icon: 'arrows-diff', tone: 'blue' }
  if (lower.includes('code') || lower.includes('implementation') || lower.includes('snippet') || lower.includes('sql') || lower.includes('query')) return { icon: 'code', tone: 'slate' }
  return semanticIconFor(clean, 'sparkles')
}

function splitStepTitleAndDetail(raw: string): { title: string; detail: string } | null {
  const match = raw.trim().match(/^\*\*([^*]+)\*\*\s*(?:[—–\-:]\s*|\n+)([\s\S]+)$/)
  if (!match) return null
  return { title: match[1].trim(), detail: match[2].trim() }
}

function classifyCalloutHeader(raw: string): { tone: 'summary' | 'warning' | 'fact'; icon: string; title: string } | null {
  const { rest } = extractLeadingIcon(raw.replace(/^[#*>`~\s]+|[*_`~:\s]+$/g, '').trim())
  const clean = cleanPlainText(rest).replace(/[:\s]+$/, '').trim()
  const lower = clean.toLowerCase()
  if (['important', 'warning', 'caution', 'attention', 'important notice'].includes(lower)) {
    return { tone: 'warning', icon: 'alert-triangle', title: clean }
  }
  if (['quick summary', 'summary', 'tl;dr', 'tldr', 'executive summary'].includes(lower)) {
    return { tone: 'summary', icon: 'bolt', title: clean }
  }
  if (
    ['key insight', 'key insights', 'quick fact', 'quick facts', 'pro tip', 'tip', 'note', 'key takeaway', 'key takeaways'].includes(
      lower
    )
  ) {
    return { tone: 'fact', icon: 'bulb', title: clean }
  }
  return null
}

type SectionListMode = 'default' | 'checkmark' | 'todo' | 'sources'

function classifySectionHeadingMode(raw: string): SectionListMode {
  const lower = raw.replace(/[*_`~]/g, '').trim().toLowerCase()
  if (
    lower.includes('next action') ||
    lower.includes('action item') ||
    lower.includes('todo') ||
    lower.includes('to-do') ||
    lower.includes('checklist') ||
    lower === 'tasks' ||
    lower.includes('your tasks') ||
    lower.includes('what to do') ||
    lower.includes('next step') ||
    lower.includes('first step') ||
    lower.includes('how to fix') ||
    lower.includes('routine')
  ) {
    return 'todo'
  }
  if (lower.includes('key source') || lower === 'sources' || lower.includes('trusted source')) {
    return 'sources'
  }
  if (
    lower.includes('priorit') ||
    lower.includes('feature') ||
    lower.includes('highlight') ||
    lower.includes('benefit') ||
    lower.startsWith('why ') ||
    lower.includes('bottom line') ||
    lower.includes('takeaway')
  ) {
    return 'checkmark'
  }
  return 'default'
}

function extractCitationFromProseItem(ordinal: number, raw: string): Citation | null {
  const linkMatch = raw.match(/\[([^\]\n]+)\]\((https?:\/\/[^)\s]+)\)/)
  const bareUrlMatch = raw.match(/(https?:\/\/[^\s)\]]+|www\.[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})/)
  const url = linkMatch ? linkMatch[2] : bareUrlMatch ? (bareUrlMatch[1].startsWith('http') ? bareUrlMatch[1] : `https://${bareUrlMatch[1]}`) : ''
  if (!url) return null
  const domainRaw = url.replace(/^https?:\/\//i, '').split('/')[0].replace(/^www\./i, '')
  const boldMatch = raw.match(/^\*\*([^*]+)\*\*/)
  const title = boldMatch
    ? boldMatch[1].replace(/[:\-—–\s]+$/, '').trim()
    : linkMatch
      ? linkMatch[1].trim()
      : raw.split(/[—–-]/)[0].replace(/[*_`]/g, '').trim()
  const snippet = raw
    .replace(/^\*\*[^*]+\*\*\s*[:\-—–]?\s*/, '')
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    .replace(/https?:\/\/[^\s)\]]+|www\.[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/g, '')
    .replace(/^[\s:\-—–()[\]]+|[\s:\-—–()[\]]+$/g, '')
    .trim()
  return {
    ordinal,
    title: title || domainRaw,
    url,
    domain: `www.${domainRaw}`,
    snippet,
  }
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
      <GlassIcon
        name={callout.icon}
        tone={isWarning ? 'amber' : callout.tone === 'fact' ? 'lavender' : 'indigo'}
        size={26}
        glyph={14}
        style={{ flexShrink: 0, marginTop: 1 }}
      />
      <div style={{ flex: 1, minWidth: 0, color: bodyColor, fontSize: 13.5, lineHeight: 1.55, display: 'flex', flexDirection: 'column', gap: 4 }}>
        <div style={{ fontWeight: 700, color: titleColor, fontSize: 14 }}>{callout.title}</div>
        {callout.body ? (
          <div>{renderInline(callout.body, theme, accent, citations, `co-${key}`)}</div>
        ) : null}
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
  checkedOverride,
  onToggleOverride,
  onEdit,
  onDelete,
}: {
  item: ListItemNode
  idx: number
  j: number
  theme: Theme
  accent: string
  citations?: Citation[]
  checkedOverride?: boolean
  onToggleOverride?: () => void
  onEdit?: () => void
  onDelete?: () => void
}) {
  const [localChecked, setLocalChecked] = useState(Boolean(item.checked))
  const checked = checkedOverride !== undefined ? checkedOverride : localChecked
  return (
    <li
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
      <button
        type="button"
        onClick={() => (onToggleOverride ? onToggleOverride() : setLocalChecked((prev) => !prev))}
        aria-label={`Complete task: ${cleanPlainText(item.text)}`}
        role="checkbox"
        aria-checked={checked}
        style={{
          padding: 0,
          cursor: 'pointer',
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
        {checked && <GlassIcon name="check" size={11} tile={16} />}
      </button>
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
      {onEdit && <button type="button" aria-label={`Edit task: ${cleanPlainText(item.text)}`} onClick={onEdit} style={{border: 0, background: 'transparent', cursor: 'pointer', padding: 4}}><GlassIcon name="pencil" size={14} tile={22}/></button>}
      {onDelete && <button type="button" aria-label={`Delete task: ${cleanPlainText(item.text)}`} onClick={onDelete} style={{border: 0, background: 'transparent', cursor: 'pointer', padding: 4}}><GlassIcon name="trash" size={14} tile={22}/></button>}
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
  const [sortCol, setSortCol] = useState<number | null>(null)
  const [sortAsc, setSortAsc] = useState<boolean>(true)
  const [showChart, setShowChart] = useState(false)
  const chart = useMemo(() => chartFromTableCells(block.headers, block.rows), [block.headers, block.rows])

  const sortedRows = useMemo(() => {
    if (sortCol === null) return block.rows
    return [...block.rows].sort((a, b) => {
      const ca = plainTableCell(a[sortCol] ?? '')
      const cb = plainTableCell(b[sortCol] ?? '')
      const na = parseTableNumber(ca)
      const nb = parseTableNumber(cb)
      const cmp = na !== null && nb !== null ? na - nb : ca.localeCompare(cb, undefined, { numeric: true, sensitivity: 'base' })
      return sortAsc ? cmp : -cmp
    })
  }, [block.rows, sortCol, sortAsc])

  const totalPages = Math.max(1, Math.ceil(sortedRows.length / pageSize))
  const safePage = Math.min(page, totalPages - 1)
  const visibleRows = sortedRows.slice(safePage * pageSize, safePage * pageSize + pageSize)
  const firstHeaderClean = plainTableCell(block.headers[0] ?? '')
  const primaryNameCol = ['#', 'Rank', 'No', 'ID'].includes(firstHeaderClean) ? 1 : 0

  const renderCellContent = (raw: string, colIdx: number, keyPrefix: string) => {
    const clean = plainTableCell(raw)
    const m = clean.match(/^([↑↓↗↘+-])?\s*([+-]?\d+(?:\.\d+)?%)$/)
    if (m && (clean.startsWith('+') || clean.startsWith('-') || clean.startsWith('↑') || clean.startsWith('↓') || clean.startsWith('↗') || clean.startsWith('↘'))) {
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
    if (colIdx === primaryNameCol) {
      const { icon: leadTok, rest: strippedCell } = extractLeadingIcon(clean)
      const entityName = cleanPlainText(strippedCell || clean).trim()
      const sem = leadTok ? { icon: leadTok, tone: 'lavender' } : semanticIconFor(entityName, '')
      if (sem.icon) {
        return (
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 7, fontWeight: 650, color: theme.text }}>
            <GlassIcon name={sem.icon} tone={sem.tone as never} size={22} glyph={13} />
            <span>{renderInline(leadTok ? strippedCell : raw, theme, accent, citations, keyPrefix)}</span>
          </span>
        )
      }
    }
    return renderInline(raw, theme, accent, citations, keyPrefix)
  }

  return (
    <>
      {chart && <div role="group" aria-label="Data view" style={{display:'flex', gap:4, marginBottom:6}}>
        {[false, true].map(isChart => <button key={String(isChart)} type="button" aria-pressed={showChart === isChart}
          onClick={() => setShowChart(isChart)}
          style={{border:0, borderRadius:999, padding:'4px 10px', fontSize:11, fontWeight:700, cursor:'pointer', background:showChart === isChart ? accent : 'rgba(124,92,252,0.10)', color:showChart === isChart ? '#fff' : theme.text}}>
          {isChart ? 'Chart' : 'Table'}
        </button>)}
      </div>}
      {showChart && chart ? <WidgetRenderer kind="chart" payload={chart} dark={theme.dark} /> : <div
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
                onClick={() => {
                  if (sortCol === c) {
                    setSortAsc((prev) => !prev)
                  } else {
                    setSortCol(c)
                    setSortAsc(true)
                  }
                  setPage(0)
                }}
                style={{
                  padding: '9px 12px',
                  textAlign: block.aligns[c] ?? 'left',
                  fontWeight: 700,
                  color: theme.text,
                  cursor: 'pointer',
                  userSelect: 'none',
                }}
              >
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                  <span>{renderInline(h, theme, accent, citations, `th${idx}-${c}`)}</span>
                  {sortCol === c && (
                    <span style={{ fontSize: 10, color: '#6C4CF6' }}>{sortAsc ? '↑' : '↓'}</span>
                  )}
                </span>
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
                    color: c === primaryNameCol ? theme.text : theme.text2,
                    fontWeight: c === primaryNameCol ? 600 : 400,
                  }}
                >
                  {renderCellContent(cell, c, `td${idx}-${r}-${c}`)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      {sortedRows.length >= 4 && (
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
            <span style={{ fontSize: 11, color: theme.text2, marginLeft: 4 }}>
              {safePage * pageSize + 1}–{Math.min(sortedRows.length, (safePage + 1) * pageSize)} of {sortedRows.length}
            </span>
          </div>
          <button
            type="button"
            onClick={() => {
              const next = pageSize === 3 ? 5 : pageSize === 5 ? 10 : pageSize === 10 ? 25 : 3
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
            Show: {pageSize >= sortedRows.length ? 'All' : pageSize} ▾
          </button>
        </div>
      )}
    </div>}
    </>
  )
}

function InteractiveProseTodoList({ block, idx, theme, accent, citations }: {
  block: Extract<ProseBlock, { kind: 'bulletList' }>
  idx: number
  theme: Theme
  accent: string
  citations?: Citation[]
}) {
  const [tasks, setTasks] = useState(() => block.items.map((item, index) => ({id: `source-${index}`, item, checked: Boolean(item.checked)})))
  const nextId = useRef(0)
  const [editor, setEditor] = useState<{id: string | null; draft: string} | null>(null)
  const completed = tasks.filter(task => task.checked).length
  const buttonStyle = {border: '1px solid rgba(124,92,252,0.24)', borderRadius: 999, background: 'rgba(124,92,252,0.10)', color: theme.text, padding: '5px 10px', cursor: 'pointer', fontSize: 12} as const
  return <div style={{display: 'flex', flexDirection: 'column', gap: 6}}>
    <div style={{display: 'flex', justifyContent: 'space-between', color: theme.text2, fontSize: 11}}>
      <span>{completed}/{tasks.length} completed</span>
      <span>{tasks.length ? Math.round(completed / tasks.length * 100) : 0}%</span>
    </div>
    <ul style={{margin: 0, padding: 0, listStyle: 'none', color: theme.text}}>
      {tasks.map((task, index) => <InteractiveProseChecklistItem key={task.id} item={task.item} idx={idx} j={index} theme={theme} accent={accent} citations={citations}
        checkedOverride={task.checked}
        onToggleOverride={() => setTasks(rows => rows.map(row => row.id === task.id ? {...row, checked: !row.checked} : row))}
        onEdit={() => setEditor({id: task.id, draft: task.item.text})}
        onDelete={() => {setTasks(rows => rows.filter(row => row.id !== task.id));setEditor(current => current?.id === task.id ? null : current)}}/>) }
    </ul>
    {editor ? <form aria-label={editor.id === null ? 'Add task' : 'Edit task'} onSubmit={event => {
      event.preventDefault()
      const text = sanitizeReplyEmojis(editor.draft).trim()
      if (!text) return
      if (editor.id === null) setTasks(rows => [...rows, {id: `local-${nextId.current++}`, item: {text, continuations: [], children: [], checked: false}, checked: false}])
      else setTasks(rows => rows.map(row => row.id === editor.id ? {...row, item: {...row.item, text}} : row))
      setEditor(null)
    }} style={{display: 'flex', gap: 6}}>
      <input autoFocus aria-label="Task text" value={editor.draft} onChange={event => setEditor({...editor, draft: event.target.value})} onKeyDown={event => {if(event.key === 'Escape')setEditor(null)}} style={{flex: 1, minWidth: 0, padding: '6px 10px', color: theme.text, background: theme.card, border: '1px solid rgba(124,92,252,0.35)', borderRadius: 8}}/>
      <button type="submit" disabled={!sanitizeReplyEmojis(editor.draft).trim()} style={buttonStyle}>{editor.id === null ? 'Add' : 'Save task'}</button>
      <button type="button" onClick={() => setEditor(null)} style={buttonStyle}>Cancel</button>
    </form> : <div><button type="button" onClick={() => setEditor({id: null, draft: ''})} style={buttonStyle}><GlassIcon name="plus" size={14} tile={18}/> Add Task</button></div>}
  </div>
}

function renderProseBlock(
  block: ProseBlock,
  idx: number,
  theme: Theme,
  accent: string,
  citations?: Citation[],
  sectionMode: SectionListMode = 'default'
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
      const { rest } = extractLeadingIcon(block.text.trim())
      const cleanHeadingText = badge ? cleanPlainText(rest).trim() || block.text : block.text
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
            gap: 8,
          }}
        >
          {badge && <GlassIcon name={badge.icon} tone={badge.tone as never} size={24} glyph={13} />}
          <span>{renderInline(cleanHeadingText, theme, accent, citations, `h${idx}`)}</span>
        </Tag>
      )
    }

    case 'orderedList': {
      const extractedSources = block.items
        .map((it, j) => extractCitationFromProseItem(block.start + j, it.text))
        .filter((x): x is Citation => x !== null)
      if (extractedSources.length > 0 && (sectionMode === 'sources' || extractedSources.length === block.items.length)) {
        return <CitationsSourceCards key={idx} citations={extractedSources} theme={theme} hideHeader={sectionMode === 'sources'} />
      }
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
                      {item.children.map((ch, chi) => renderProseBlock(ch, chi, theme, accent, citations, sectionMode))}
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
      if (sectionMode === 'sources') {
        const extractedSources = block.items
          .map((it, j) => extractCitationFromProseItem(j + 1, it.text))
          .filter((x): x is Citation => x !== null)
        if (extractedSources.length > 0) {
          return <CitationsSourceCards key={idx} citations={extractedSources} theme={theme} hideHeader />
        }
      }
      const isTodoSection = sectionMode === 'todo' || block.items.some((it) => it.checked !== undefined)
      if (isTodoSection) {
        return (
          <InteractiveProseTodoList
            key={`${idx}:${JSON.stringify(block.items)}`}
            block={block}
            idx={idx}
            theme={theme}
            accent={accent}
            citations={citations}
          />
        )
      }
      const isCheckmarkSection = sectionMode === 'checkmark'
      return (
        <div key={idx} style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <ul
            style={{
              margin: 0,
              paddingLeft: 0,
              display: 'flex',
              flexDirection: 'column',
              gap: 6,
              color: theme.text,
              listStyleType: 'none',
            }}
          >
            {block.items.map((item, j) => {
              const { icon: leadTok, rest: stripped } = extractLeadingIcon(item.text)
              const boldLeadMatch = !leadTok ? item.text.trim().match(/^\*\*([^*]+)\*\*/) : null
              const sem = leadTok
                ? { icon: leadTok, tone: 'lavender' }
                : boldLeadMatch
                  ? semanticIconFor(boldLeadMatch[1], '')
                  : { icon: '', tone: 'lavender' }
              const resolvedIcon = isCheckmarkSection ? (leadTok || sem.icon || 'circle-check') : (leadTok || (sem.icon && sem.icon !== 'sparkles' ? sem.icon : ''))
              const resolvedTone = isCheckmarkSection && !leadTok && !sem.icon ? 'green' : sem.tone
              const bodyStr = leadTok ? stripped : item.text
              return (
                <li key={j} style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
                  {resolvedIcon ? (
                    <GlassIcon name={resolvedIcon} tone={resolvedTone as never} size={20} glyph={12} style={{ marginTop: 2, flexShrink: 0 }} />
                  ) : (
                    <span style={{ color: accent, fontWeight: 800, marginTop: 1, flexShrink: 0 }}>•</span>
                  )}
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <span>{renderInline(bodyStr, theme, accent, citations, `li${idx}-${j}`)}</span>
                    {item.continuations.map((c, ci) => (
                      <div key={ci} style={{ marginTop: 2 }}>
                        {renderInline(c, theme, accent, citations, `lic${idx}-${j}-${ci}`)}
                      </div>
                    ))}
                    {item.children.length > 0 && (
                      <div style={{ marginTop: 4 }}>
                        {item.children.map((ch, chi) => renderProseBlock(ch, chi, theme, accent, citations, sectionMode))}
                      </div>
                    )}
                  </div>
                </li>
              )
            })}
          </ul>
        </div>
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
  const blocks = useMemo(() => {
    const raw = parseProseBlocks(text)
    return raw.map((b) => {
      if (b.kind === 'paragraph') {
        const m = b.text.trim().match(/^\*\*([^*]+)\*\*\s*:?$/)
        if (m) {
          const clean = cleanPlainText(m[1]).trim()
          if (clean.length >= 3 && clean.length <= 72 && !clean.endsWith('.') && !classifyCalloutHeader(b.text)) {
            return { kind: 'heading' as const, level: 3 as const, text: m[1].trim() }
          }
        }
      }
      return b
    })
  }, [text])
  const rendered: ReactNode[] = []
  let currentMode: SectionListMode = 'default'
  let i = 0
  while (i < blocks.length) {
    const block = blocks[i]
    const nextBlock = blocks[i + 1]

    // Coalesce Heading("Quick summary" | "Important" | "Key Insights" | "Quick Fact") + Paragraph/BulletList into one Callout Card
    const headerCallout =
      block.kind === 'heading'
        ? classifyCalloutHeader(block.text)
        : block.kind === 'paragraph' && /^\*\*[^*]+\*\*\s*:?$/.test(block.text.trim())
          ? classifyCalloutHeader(block.text)
          : null

    if (headerCallout && (nextBlock?.kind === 'paragraph' || nextBlock?.kind === 'bulletList')) {
      const bodyText = nextBlock.kind === 'paragraph' ? nextBlock.text : ''
      const extra =
        nextBlock.kind === 'bulletList'
          ? renderProseBlock(nextBlock, i + 1, theme, accent, citations, 'default')
          : undefined
      rendered.push(
        renderCalloutCard(
          {
            tone: headerCallout.tone,
            icon: headerCallout.icon,
            title: headerCallout.title,
            body: bodyText,
          },
          `coal-${i}`,
          theme,
          accent,
          citations,
          extra
        )
      )
      i += 2
      continue
    }

    if (block.kind === 'heading') {
      currentMode = classifySectionHeadingMode(block.text)
    } else if (block.kind !== 'bulletList' && block.kind !== 'orderedList') {
      currentMode = 'default'
    }

    rendered.push(renderProseBlock(block, i, theme, accent, citations, currentMode))
    i += 1
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10, color: theme.text }}>
      {rendered}
    </div>
  )
}

function citationSourceURL(raw: string): URL | null {
  try {
    const url = new URL(raw)
    return ['https:', 'http:'].includes(url.protocol) && url.hostname && !url.username && !url.password ? url : null
  } catch { return null }
}

function isAcademicCitationDomain(_domain: string, raw: string): boolean {
  const host = citationSourceURL(raw)?.hostname.toLowerCase() ?? ''
  const publishers = ['nature.com', 'arxiv.org', 'ieee.org', 'acm.org', 'springer.com', 'springernature.com', 'sciencedirect.com', 'oup.com', 'cambridge.org', 'nih.gov']
  return host.endsWith('.edu') || host.endsWith('.ac.uk') || publishers.some(domain => host === domain || host.endsWith('.' + domain))
}

function CitationsSourceCards({
  citations,
  theme,
  hideHeader = false,
}: {
  citations: Citation[]
  theme: Theme
  hideHeader?: boolean
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 4 }}>
      {!hideHeader && (
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
            <GlassIcon name="link" tone="blue" size={22} glyph={12} />
            <span>Key Sources</span>
          </span>
          <span style={{ fontSize: 12, fontWeight: 600, color: '#6C4CF6' }}>
            {citations.length} {citations.length === 1 ? 'source' : 'sources'}
          </span>
        </div>
      )}
      {citations.map((src) => {
        const link = citationSourceURL(src.url)
        const domain = link?.hostname.replace(/^www\./, '') ?? 'Invalid source URL'
        const academic = isAcademicCitationDomain(domain, src.url)
        return (
          <a
            key={src.ordinal}
            href={link?.href}
            aria-disabled={!link}
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
                  <GlassIcon name="external-link" tone="indigo" size={16} glyph={10} />
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
                    background: 'rgba(124, 92, 252, 0.10)',
                    color: '#5B3DF5',
                  }}
                >
                  <GlassIcon name={academic ? 'building-bank' : 'external-link'} tone="indigo" size={15} glyph={9} />
                  <span>{!link ? 'Invalid link' : academic ? 'Academic domain' : 'Source link'}</span>
                </span>
              </div>
            </div>
          </a>
        )
      })}
    </div>
  )
}

function deriveContextualActionPills(
  segments: Segment[],
  rawText: string,
  citations?: Citation[]
): Array<{ icon: string; label: string }> {
  const lastSeg = segments[segments.length - 1]
  if (lastSeg?.kind === 'widget' && ['actions', 'followups', 'chips', 'menu'].includes(lastSeg.widget)) {
    return []
  }
  const hasChart = segments.some((s) => s.kind === 'widget' && s.widget === 'chart')
  const hasTable =
    segments.some((s) => s.kind === 'widget' && s.widget === 'table') || /\|[^\n]+\|\s*\n\s*\|[\s:-]+\|/.test(rawText)
  const hasCode = segments.some((s) => s.kind === 'code')
  const normalizedText = emojiToIconTokens(rawText)
  const hasSources =
    (citations && citations.length > 0) ||
    /##+\s*(?::[a-z0-9-]+:\s*)?(?:Key\s+)?Sources/i.test(normalizedText)
  const hasPlanOrSteps =
    /^\s*1\.\s+\*\*/m.test(rawText) ||
    /^\s*[-*]\s+\[[ xX]\]/m.test(rawText) ||
    /##+\s*(?::[a-z0-9-]+:\s*)?(?:Next\s+actions|Your\s+Todo\s+List)/i.test(normalizedText)

  if (hasChart && hasTable && hasCode) {
    return [
      { icon: 'message-circle', label: 'Ask follow-up' },
      { icon: 'chart-bar', label: 'Show me a detailed report' },
      { icon: 'sparkles', label: 'Create a presentation' },
    ]
  }
  if (hasChart) {
    return [
      { icon: 'download', label: 'Download Report' },
      { icon: 'arrows-maximize', label: 'View Details' },
    ]
  }
  if (hasTable) {
    return [
      { icon: 'chart-bar', label: 'View Full Report' },
      { icon: 'share', label: 'Share' },
    ]
  }
  if (hasSources) {
    return [
      { icon: 'external-link', label: 'View All Sources' },
      { icon: 'clipboard', label: 'Copy Citations' },
      { icon: 'book-2', label: 'Learn More' },
    ]
  }
  if (hasCode) {
    return [
      { icon: 'code', label: 'Edit Code' },
      { icon: 'download', label: 'Download Project' },
      { icon: 'message-circle', label: 'Ask Follow Up' },
    ]
  }
  if (hasPlanOrSteps) {
    return [
      { icon: 'calendar', label: 'Make a timeline' },
      { icon: 'arrows-maximize', label: 'Expand plan' },
      { icon: 'share', label: 'Share' },
    ]
  }
  return []
}

export function MessageContent({
  text,
  dark = true,
  onPrompt,
  streaming = false,
  citations,
  responseScope,
}: {
  text: string
  responseScope?: string
  dark?: boolean
  onPrompt?: (prompt: string) => void
  streaming?: boolean
  citations?: Citation[]
}) {
  const sessionScope = useId()
  const contentRef = useRef<HTMLDivElement>(null)
  const [actionNotice, setActionNotice] = useState<{ response: string; text: string; error?: boolean } | null>(null)
  const [detailState, setDetailState] = useState<{ response: string; kind: 'report' | 'sources' } | null>(null)
  const detailView = detailState?.response === text ? detailState.kind : null
  const setDetailView = (kind: 'report' | 'sources' | null) => setDetailState(kind ? { response: text, kind } : null)
  const citationText = (citations ?? []).map(c => `[${c.ordinal}] ${c.title} — ${c.url}`).join('\n')
  const performAction = async (label: string) => {
    setActionNotice(null)
    try {
      switch (label) {
        case 'Share':
          if (navigator.share) await navigator.share({ title: 'GS AI response', text })
          else {
            if (!navigator.clipboard?.writeText) throw new Error('Sharing and clipboard access are unavailable in this browser.')
            await navigator.clipboard.writeText(text)
            setActionNotice({ response: text, text: 'Sharing is unavailable in this browser. Response copied; paste it where you want to share.' })
          }
          return
        case 'Copy Citations': {
          // Prefer persisted source records; otherwise copy only source-bearing
          // lines, never claim the entire answer is a bibliography.
          const sources = citationText || text.split('\n').filter(line => /https?:\/\//.test(line)).join('\n')
          if (!sources) throw new Error('No citation URLs are available to copy.')
          if (!navigator.clipboard?.writeText) throw new Error('Clipboard access is unavailable in this browser.')
          await navigator.clipboard.writeText(sources)
          setActionNotice({ response: text, text: 'Citations copied.' })
          return
        }
        case 'Download Report':
          downloadResponseFile('gs-ai-response.md', text + (citationText ? '\n\n## Sources\n' + citationText : ''), 'text/markdown;charset=utf-8')
          setActionNotice({ response: text, text: 'Response report exported as Markdown, including its original data.' })
          return
        case 'Download Project': {
          const blocks = Array.from(contentRef.current?.querySelectorAll<HTMLElement>('[data-code-block]') ?? [])
          const files = blocks.map((block, index) => responseSourceFile(block.dataset.codeSource ?? '', block.dataset.codeLanguage, index))
          downloadResponseFile('gs-ai-source-snippets.zip', responseSourceArchive(files), 'application/zip')
          setActionNotice({ response: text, text: 'Source snippets exported, including valid edits. This is not a complete runnable project; see README.txt.' })
          return
        }
        case 'Edit Code': {
          const editor = contentRef.current?.querySelector<HTMLTextAreaElement>('textarea[aria-label="Source code"], textarea[aria-label="JSON-encoded source"]')
          const edit = contentRef.current?.querySelector<HTMLButtonElement>('[data-code-edit]')
          if (editor) { editor.scrollIntoView?.({ block: 'center', behavior: 'smooth' }); editor.focus() }
          else if (edit) { edit.scrollIntoView?.({ block: 'center', behavior: 'smooth' }); edit.click() }
          else throw new Error('No editable code block is available.')
          return
        }
        case 'View All Sources': setDetailView('sources'); return
        case 'View Details': case 'View Full Report': setDetailView('report'); return
        default:
          if (!onPrompt) throw new Error('Follow-up requests are unavailable in this view.')
          onPrompt(label)
      }
    } catch (error) {
      if (error instanceof Error && error.name === 'AbortError') return // User cancelled the native share sheet.
      setActionNotice({ response: text, text: error instanceof Error ? error.message : 'The action failed. Please try again.', error: true })
    }
  }
  const segments = useMemo(() => parseStream(sanitizeReplyEmojis(text)), [text])
  const theme = useMemo(() => themeFor(dark), [dark])
  const concepts = useMemo(() => !streaming && segments.every((s) => s.kind === 'text') ? extractConceptDeck(text) : [], [text, segments, streaming])
  const accent = segments.find((s): s is Extract<Segment, { kind: 'widget' }> => s.kind === 'widget')?.payload.accent as string | undefined
  const contextualPills = useMemo(
    () => (streaming ? [] : deriveContextualActionPills(segments, text, citations)),
    [segments, text, citations, streaming]
  )
  return (
    <div ref={contentRef} style={{ display: 'flex', flexDirection: 'column', gap: 14, fontSize: TYPE.body, lineHeight: 1.65 }}>
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
            return <CodeBlock key={i} code={segment.code} language={segment.language} storageKey={`${responseScope ?? sessionScope}:snippet:${i}`} />
          case 'map':
            return <WidgetRenderer key={i} kind="map" payload={segment.payload} dark={dark} />
          default:
            return null
        }
      })}
      {concepts.length >= 3 && <SpontaneousConceptDeck key={text} concepts={concepts} dark={dark} />}
      {citations && citations.length > 0 && <CitationsSourceCards citations={citations} theme={theme} />}
      {actionNotice?.response === text && <div role={actionNotice.error ? 'alert' : 'status'}>{actionNotice.text}</div>}
      {detailView && (
        <section role="region" aria-label={detailView === 'sources' ? 'All sources' : 'Response report'} style={{ padding: 16, border: `1px solid ${theme.border}`, borderRadius: 16 }}>
          <button type="button" onClick={() => setDetailView(null)}>Close details</button>
          <h3>{detailView === 'sources' ? 'All sources' : 'Response report — original source and data'}</h3>
          {detailView === 'sources' && citations?.length
            ? <CitationsSourceCards citations={citations} theme={theme} />
            : <pre style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{escapeCodeDisplay(detailView === 'sources' ? citationText || text.split('\n').filter(line => /https?:\/\//.test(line)).join('\n') || 'No source URLs were supplied.' : text)}</pre>}
        </section>
      )}
      {contextualPills.length > 0 && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginTop: 2 }}>
          {contextualPills.map((pill, idx) => (
            <button
              key={idx}
              type="button"
              onClick={() => { void performAction(pill.label) }}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 6,
                padding: '6px 12px',
                borderRadius: 999,
                border: `1px solid ${theme.border}`,
                background: theme.card,
                color: theme.text,
                fontSize: 12,
                fontWeight: 650,
                cursor: 'pointer',
              }}
            >
              <GlassIcon name={pill.icon} tone="lavender" size={18} glyph={11} />
              <span>{pill.label}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
