'use client'

/**
 * HtmlBlock — the bespoke escape hatch. One self-contained HTML document in a
 * sandboxed iframe: allow-scripts, NO allow-same-origin (no cookies, no DOM
 * access to the parent), explicit height via <body data-gs-height="Npx">.
 */

import { useEffect, useMemo, useRef, useState } from 'react'
import { RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { Skeleton } from '../primitives/Skeleton'

export function HtmlBlock({ source, fallbackHeight = 320 }: { source: string; fallbackHeight?: number }) {
  const { accent, theme } = useWidgetIdentity()
  const [loaded, setLoaded] = useState(false)
  const [height, setHeight] = useState(fallbackHeight)
  const frame = useRef<HTMLIFrameElement>(null)

  const themed = useMemo(() => {
    const hueVars = Array.from({ length: 12 }, (_, i) => `--gs-hue-${i + 1}`)
    const doc = `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>
      :root {
        --gs-text:${theme.text}; --gs-text-2:${theme.text2}; --gs-text-3:${theme.text3};
        --gs-surface:${theme.card}; --gs-raised:${theme.raised}; --gs-border:${theme.border};
        --gs-accent:${accent};
        --gs-palette:var(--gs-accent);
        --gs-radius:14px; --gs-space:8px;
        color-scheme:${theme.dark ? 'dark' : 'light'};
      }
      html,body { margin:0; padding:12px; background:transparent; color:var(--gs-text);
        font-family:-apple-system,'SF Pro Text','Segoe UI',Roboto,sans-serif; font-size:14px; }
      * { box-sizing:border-box; max-width:100%; }
    </style></head><body>${source}</body></html>`
    return { doc, hueVars }
  }, [source, accent, theme])

  useEffect(() => {
    const m = /data-gs-height="(\d+)px"/.exec(source)
    if (m) setHeight(Math.min(900, Math.max(80, Number(m[1]))))
  }, [source])

  return (
    <div style={{ position: 'relative' }}>
      {!loaded && <Skeleton label="Rendering…" height={Math.min(height, 200)} />}
      <iframe
        ref={frame}
        title="interactive view"
        sandbox="allow-scripts allow-forms allow-pointer-lock"
        srcDoc={themed.doc}
        onLoad={() => setLoaded(true)}
        style={{
          width: '100%',
          height,
          border: `1px solid ${theme.border}`,
          borderRadius: RADIUS.lg,
          background: theme.card,
          display: loaded ? 'block' : 'none',
        }}
      />
    </div>
  )
}
