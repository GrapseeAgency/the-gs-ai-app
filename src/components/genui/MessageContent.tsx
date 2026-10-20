'use client'

/**
 * MessageContent — the full assistant answer: prose and widgets inline,
 * streamed-aware. Unclosed fences render as skeletons; a tap on any prompt
 * affordance flows up through onPrompt.
 */

import { useMemo } from 'react'
import { parseStream, type Segment } from './stream'
import { WidgetRenderer } from './WidgetRenderer'
import { Skeleton } from './primitives/Skeleton'
import { MermaidBlock } from './blocks/MermaidBlock'
import { HtmlBlock } from './blocks/HtmlBlock'
import { SvgBlock } from './blocks/SvgBlock'
import { CodeBlock } from './blocks/CodeBlock'
import { themeFor, TYPE } from './theme/tokens'

/** A persisted citation the host knows about — [N] markers resolve to these. */
export interface Citation {
  ordinal: number
  title: string
  url: string
  domain?: string
}

/** Prose with tappable [N] citations, matching the host page's behaviour. */
function Prose({ text, theme, accent, citations }: { text: string; theme: ReturnType<typeof themeFor>; accent: string; citations?: Citation[] }) {
  if (!citations || citations.length === 0) {
    return <div style={{ color: theme.text, whiteSpace: 'pre-wrap' }}>{text}</div>
  }
  const nodes: React.ReactNode[] = []
  const re = /[\[【〚［]\s*(\d{1,2})\s*[\]】〛］]/g
  let last = 0
  let key = 0
  for (const m of text.matchAll(re)) {
    const idx = m.index ?? 0
    if (idx > last) nodes.push(<span key={`t${key++}`}>{text.slice(last, idx)}</span>)
    const n = Number.parseInt(m[1], 10)
    const src = citations.find((c) => c.ordinal === n)
    if (src) {
      nodes.push(
        <button
          key={`c${key++}`}
          onClick={() => window.open(src.url, '_blank', 'noopener,noreferrer')}
          aria-label={`Open citation ${n}: ${src.title}`}
          title={`${src.title} — ${src.domain ?? ''}`}
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
      nodes.push(<span key={`c${key++}`}>{m[0]}</span>)
    }
    last = idx + m[0].length
  }
  if (last < text.length) nodes.push(<span key={`t${key++}`}>{text.slice(last)}</span>)
  return <div style={{ color: theme.text, whiteSpace: 'pre-wrap' }}>{nodes}</div>
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
