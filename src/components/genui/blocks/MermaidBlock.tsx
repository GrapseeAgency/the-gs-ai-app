'use client'

/**
 * MermaidBlock — renders ```mermaid via the mermaid package (lazy chunk),
 * theme-aware; falls back to a pre block when the source fails to parse.
 */

import { useEffect, useRef, useState } from 'react'
import { RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'

export function MermaidBlock({ source, height = 280 }: { source: string; height?: number }) {
  const { accent, theme } = useWidgetIdentity()
  const [svg, setSvg] = useState('')
  const [failed, setFailed] = useState(false)
  const idRef = useRef(`gs-mermaid-${Math.random().toString(36).slice(2, 9)}`)

  useEffect(() => {
    let cancelled = false
    const render = async () => {
      try {
        const mermaid = (await import('mermaid')).default
        mermaid.initialize({
          startOnLoad: false,
          theme: theme.dark ? 'dark' : 'default',
          themeVariables: {
            primaryColor: `${accent}33`,
            primaryBorderColor: accent,
            primaryTextColor: theme.text,
            lineColor: theme.text3,
            fontFamily: 'inherit',
          },
        })
        const { svg } = await mermaid.render(idRef.current, source)
        if (!cancelled) setSvg(svg)
      } catch {
        if (!cancelled) setFailed(true)
      }
    }
    render()
    return () => {
      cancelled = true
    }
  }, [source, accent, theme.dark, theme.text, theme.text3])

  if (failed) {
    return (
      <pre
        style={{
          margin: 0,
          padding: 14,
          borderRadius: RADIUS.md,
          border: `1px solid ${theme.border}`,
          background: theme.dark ? 'rgba(0,0,0,0.3)' : 'rgba(0,0,0,0.03)',
          color: theme.text2,
          fontSize: TYPE.micro,
          overflowX: 'auto',
          whiteSpace: 'pre-wrap',
        }}
      >
        {source}
      </pre>
    )
  }
  return (
    <div
      style={{ display: 'flex', justifyContent: 'center', overflowX: 'auto', minHeight: 80 }}
      dangerouslySetInnerHTML={svg ? { __html: svg } : undefined}
    />
  )
}
