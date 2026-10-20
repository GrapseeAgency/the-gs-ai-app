'use client'

import { useState } from 'react'
import type { MediaPayload } from '../types'
import { alpha, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { EmptyState } from '../primitives/EmptyState'
import { CopyChip } from '../primitives/CopyChip'

/** Media — image/gallery/video/audio/code/file/link/citation/search/entity/product/listing/doc/embed/pdf. */
export function MediaWidget({ payload, onPrompt }: { payload: MediaPayload; onPrompt?: (p: string) => void }) {
  const { accent, theme } = useWidgetIdentity()
  const items = (payload.items ?? []).slice(0, 20)
  const type = payload.type ?? 'image'

  if (type === 'code' || payload.code) {
    return <CodePreview code={payload.code ?? ''} language={payload.language} title={payload.title} />
  }
  if (type === 'video') {
    const src = items[0]?.url
    return (
      <div>
        <TitleRow title={payload.title} />
        {src ? (
          <video src={src} controls preload="metadata" style={{ width: '100%', borderRadius: RADIUS.lg, border: `1px solid ${theme.border}`, maxHeight: 300, background: '#000' }} />
        ) : (
          <EmptyState label="No video URL" />
        )}
      </div>
    )
  }
  if (type === 'audio') {
    const src = items[0]?.url
    return (
      <div>
        <TitleRow title={payload.title} right={items[0]?.meta} />
        {src ? (
          <audio src={src} controls preload="metadata" style={{ width: '100%' }}>
            <track kind="captions" />
          </audio>
        ) : (
          <EmptyState label="No audio URL" />
        )}
      </div>
    )
  }
  if (type === 'gallery' && items.length > 1) {
    return (
      <div>
        <TitleRow title={payload.title} />
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
          {items.map((item, i) => (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              key={i}
              src={item.url ?? item.thumb}
              alt={item.caption ?? `image ${i + 1}`}
              loading="lazy"
              style={{ width: '100%', height: 110, objectFit: 'cover', borderRadius: RADIUS.md, border: `1px solid ${theme.border}` }}
            />
          ))}
        </div>
      </div>
    )
  }
  if (items.length > 0) {
    return (
      <div>
        <TitleRow title={payload.title} />
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {items.map((item, i) => (
            <MediaRow key={i} item={item} type={type} onPrompt={onPrompt} />
          ))}
        </div>
      </div>
    )
  }
  if (payload.text) {
    return (
      <div>
        <TitleRow title={payload.title} />
        <div style={{ fontSize: TYPE.body, color: theme.text, lineHeight: 1.6, whiteSpace: 'pre-wrap' }}>{payload.text}</div>
      </div>
    )
  }
  return <EmptyState label="No media" />
}

function MediaRow({ item, type, onPrompt }: { item: Record<string, string>; type: string; onPrompt?: (p: string) => void }) {
  const { accent, theme } = useWidgetIdentity()
  const clickable = Boolean(item.prompt || item.url)
  const action = () => {
    if (item.prompt) onPrompt?.(item.prompt)
    else if (item.url) window.open(item.url, '_blank', 'noopener')
  }
  const Row = (
    <div
      onClick={action}
      role={clickable ? 'button' : undefined}
      style={{
        display: 'flex',
        gap: 12,
        alignItems: 'center',
        padding: 10,
        borderRadius: RADIUS.md,
        border: `1px solid ${theme.border}`,
        cursor: clickable ? 'pointer' : 'default',
        transition: 'border-color .15s ease',
      }}
      onMouseEnter={(e) => clickable && (e.currentTarget.style.borderColor = alpha(accent, 0.5))}
      onMouseLeave={(e) => clickable && (e.currentTarget.style.borderColor = theme.border)}
    >
      {item.thumb ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={item.thumb} alt="" loading="lazy" style={{ width: 46, height: 46, objectFit: 'cover', borderRadius: 10, flexShrink: 0 }} />
      ) : null}
      <span style={{ flex: 1, minWidth: 0 }}>
        <span style={{ display: 'block', fontSize: TYPE.label, fontWeight: 600, color: theme.text, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
          {item.title ?? item.domain ?? item.url}
        </span>
        {item.subtitle || item.snippet ? (
          <span style={{ display: '-webkit-box', fontSize: TYPE.micro, color: theme.text3, marginTop: 2, overflow: 'hidden', textOverflow: 'ellipsis', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical' as never }}>
            {item.subtitle ?? item.snippet}
          </span>
        ) : null}
        {(item.meta || item.price || item.rating || item.badge || item.domain) && (
          <span style={{ display: 'flex', gap: 8, marginTop: 4, fontSize: TYPE.micro, color: theme.text3, flexWrap: 'wrap' }}>
            {item.price ? <b style={{ color: accent }}>{item.price}</b> : null}
            {item.rating ? <span>★ {item.rating}</span> : null}
            {item.badge ? <span style={{ fontWeight: 700, color: accent }}>{item.badge}</span> : null}
            <span>{item.meta ?? item.domain}</span>
          </span>
        )}
      </span>
      {clickable ? <span style={{ color: accent, flexShrink: 0 }}>→</span> : null}
    </div>
  )
  if (type === 'citation' && item.url && !item.prompt) {
    return (
      <a href={item.url} target="_blank" rel="noopener noreferrer" style={{ textDecoration: 'none' }}>
        {Row}
      </a>
    )
  }
  return Row
}

function CodePreview({ code, language, title }: { code: string; language?: string; title?: string }) {
  const { accent, theme } = useWidgetIdentity()
  const [src, setSrc] = useState(code)
  const [running, setRunning] = useState(false)
  const runnable = ['javascript', 'js', 'html', 'svg'].includes((language ?? '').toLowerCase())
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <TitleRow title={title} style={{ marginBottom: 0 }} />
        <span style={{ display: 'inline-flex', gap: 8 }}>
          <CopyChip value={code} />
          {runnable && (
            <button
              type="button"
              onClick={() => setRunning((r) => !r)}
              style={{
                padding: '5px 12px',
                borderRadius: 999,
                border: `1px solid ${accent}`,
                background: running ? alpha(accent, 0.16) : 'transparent',
                color: accent,
                fontSize: TYPE.label,
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              {running ? 'Stop' : 'Run'}
            </button>
          )}
        </span>
      </div>
      <textarea
        value={src}
        onChange={(e) => setSrc(e.target.value)}
        rows={Math.min(16, Math.max(3, src.split('\n').length))}
        spellCheck={false}
        style={{
          width: '100%',
          borderRadius: RADIUS.md,
          border: `1px solid ${theme.border}`,
          background: theme.dark ? 'rgba(0,0,0,0.32)' : 'rgba(0,0,0,0.03)',
          color: theme.text,
          fontSize: TYPE.micro,
          lineHeight: 1.6,
          fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
          padding: 12,
          resize: 'vertical',
          outline: 'none',
        }}
      />
      {running && (
        <iframe
          title="preview"
          sandbox="allow-scripts"
          srcDoc={`<!doctype html><html><body style="margin:0;background:transparent;font-family:sans-serif"><script>${src.replace(/<\/script>/g, '<\\/script>')}<\/script></body></html>`}
          style={{ width: '100%', height: 180, border: `1px solid ${theme.border}`, borderRadius: RADIUS.md, marginTop: 8, background: theme.card }}
        />
      )}
    </div>
  )
}
