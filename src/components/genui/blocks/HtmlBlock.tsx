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
import emojiDOMPolicy from '../../../../shared-contracts/emoji-dom-policy.json'

export function HtmlBlock({ source, fallbackHeight = 320, onSandboxMessage }: { source: string; fallbackHeight?: number; onSandboxMessage?: (data: unknown) => unknown }) {
  const { accent, theme } = useWidgetIdentity()
  const [loaded, setLoaded] = useState(false)
  const [height, setHeight] = useState(fallbackHeight)
  const frame = useRef<HTMLIFrameElement>(null)

  useEffect(() => {
    if (!onSandboxMessage) return
    const receive = (event: MessageEvent) => {
      if (event.source !== frame.current?.contentWindow) return
      const reply = onSandboxMessage(event.data)
      if (reply !== undefined) frame.current?.contentWindow?.postMessage(reply, '*')
    }
    window.addEventListener('message', receive)
    return () => window.removeEventListener('message', receive)
  }, [onSandboxMessage])

  const themed = useMemo(() => {
    const hueVars = Array.from({ length: 12 }, (_, i) => `--gs-hue-${i + 1}`)
    const head = `<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>
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
    </style><script>${emojiDOMPolicy.script}</script>`
    // A document's CSP meta must remain in HEAD. Nesting a complete document
    // inside BODY makes browsers ignore that policy and silently enables IO.
    const fullDocument = /^\s*(?:<!--[\s\S]*?-->\s*)*(?:<!doctype\s+html\b|<html\b|<head\b)/i.test(source)
    let doc: string
    if (fullDocument && /<head(?:\s[^>]*)?>/i.test(source)) {
      doc = source.replace(/<head(?:\s[^>]*)?>/i, match => `${match}${head}`)
    } else if (fullDocument && /<html(?:\s[^>]*)?>/i.test(source)) {
      doc = source.replace(/<html(?:\s[^>]*)?>/i, match => `${match}<head>${head}</head>`)
    } else if (fullDocument) {
      doc = source.replace(/<!doctype[^>]*>/i, match => `${match}<head>${head}</head>`)
    } else {
      doc = `<!doctype html><html><head>${head}</head><body>${source}</body></html>`
    }
    return { doc, hueVars }
  }, [source, accent, theme])

  useEffect(() => {
    const m = /data-gs-height="(\d+)px"/.exec(source)
    setLoaded(false)
    setHeight(m ? Math.min(900, Math.max(80, Number(m[1]))) : fallbackHeight)
  }, [source, fallbackHeight])

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
