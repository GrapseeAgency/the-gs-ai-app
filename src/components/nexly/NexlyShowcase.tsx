'use client'

import { useMemo, useState } from 'react'
import { GlassIcon } from './GlassIcon'
import { NexlyBackdrop } from './NexlyBackdrop'
import { NexlyPhone } from './NexlyPhone'
import {
  FEATURED_ICONS,
  NEXLY_ICON_COUNT,
  NEXLY_ICON_NAMES,
} from '@/lib/nexly/icons'

const WIRE = `\`\`\`plan
{
  "title": "Here's your personalized plan for tomorrow:",
  "rows": [
    { "time": "8:00 – 8:30 AM", "icon": "sunrise", "text": "Morning routine + light exercise" },
    { "time": "9:00 – 11:00 AM", "icon": "book-2", "text": "Classes" }
  ],
  "closing": "You got this, Arafat!",
  "closingIcon": "flame"
}
\`\`\``

export function NexlyShowcase() {
  const [query, setQuery] = useState('')
  const [limit, setLimit] = useState(96)
  const [copied, setCopied] = useState('')

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return NEXLY_ICON_NAMES
    return NEXLY_ICON_NAMES.filter((name) => name.includes(q))
  }, [query])

  const shown = filtered.slice(0, limit)

  const copy = async (name: string) => {
    const token = `:${name}:`
    try {
      await navigator.clipboard.writeText(token)
    } catch {
      // Clipboard can be blocked in a preview iframe; the label still teaches.
    }
    setCopied(name)
    window.setTimeout(() => setCopied(''), 1400)
  }

  return (
    <div className="nx-page">
      <NexlyBackdrop />

      <div className="nx-hero">
        <div className="nx-phone-column">
          <div className="nx-phone-caption">
            <span className="nx-live-dot" />
            Response renderer · live preview
          </div>
          <NexlyPhone />
        </div>

        <aside className="nx-panel">
          <div className="nx-eyebrow">NEXLY · RESPONSE SYSTEM</div>
          <h1 className="nx-title">
            The reply is
            <br />
            the interface.
          </h1>
          <p className="nx-lede">
            A validated <code>plan</code> block, a{' '}
            <strong>{NEXLY_ICON_COUNT.toLocaleString('en-US')}-glyph</strong> glass
            icon vocabulary, and a Thinking card that survives the first token.
            No emoji reaches the DOM.
          </p>

          <div className="nx-stats">
            <div className="nx-stat">
              <div className="nx-stat-value">{NEXLY_ICON_COUNT.toLocaleString('en-US')}</div>
              <div className="nx-stat-label">glass icons</div>
            </div>
            <div className="nx-stat">
              <div className="nx-stat-value">0</div>
              <div className="nx-stat-label">emoji allowed</div>
            </div>
            <div className="nx-stat">
              <div className="nx-stat-value">9</div>
              <div className="nx-stat-label">icon rows</div>
            </div>
          </div>

          <div className="nx-rail">
            {FEATURED_ICONS.slice(0, 12).map((icon) => (
              <button
                key={icon.name}
                type="button"
                className="nx-rail-item"
                onClick={() => copy(icon.name)}
                title={`Copy :${icon.name}:`}
              >
                <GlassIcon name={icon.name} tone={icon.tone} tile={42} size={20} flat />
                <span>{icon.label}</span>
              </button>
            ))}
          </div>

          <div className="nx-contract">
            <div className="nx-contract-head">
              <span>Wire contract</span>
              <span className="nx-contract-badge">plan</span>
            </div>
            <pre>{WIRE}</pre>
            <div className="nx-contract-foot">
              Inline <code>:sunrise:</code> · emoji are normalised to a glass glyph,
              unknown pictographs are dropped.
            </div>
          </div>

          <p className="nx-scope">
            Scope: the assistant response slice. Header, user turn and composer
            are demo context; the iOS/Android chat shells are untouched.
          </p>
        </aside>
      </div>

      <section className="nx-library" aria-label="Glass icon library">
        <div className="nx-library-head">
          <div>
            <div className="nx-eyebrow">THE VOCABULARY</div>
            <h2 className="nx-library-title">Every glyph is glass.</h2>
          </div>
          <div className="nx-search-wrap">
            <GlassIcon bare name="search" size={16} />
            <input
              className="nx-search"
              value={query}
              onChange={(event) => {
                setQuery(event.target.value)
                setLimit(96)
              }}
              placeholder={`Search ${NEXLY_ICON_COUNT.toLocaleString('en-US')} icons…`}
              aria-label="Search glass icons"
            />
          </div>
        </div>

        <div className="nx-library-meta">
          <span>
            Showing {shown.length.toLocaleString('en-US')} of{' '}
            {filtered.length.toLocaleString('en-US')}
          </span>
          <span className="nx-copy-hint">
            {copied ? `Copied :${copied}:` : 'Tap any glyph to copy its :name: token'}
          </span>
        </div>

        <div className="nx-icon-grid">
          {shown.map((name) => (
            <button
              key={name}
              type="button"
              className="nx-icon-cell"
              onClick={() => copy(name)}
              title={`Copy :${name}:`}
            >
              <GlassIcon name={name} tile={44} size={20} flat />
              <span className="nx-icon-name">{name}</span>
            </button>
          ))}
        </div>

        {shown.length < filtered.length ? (
          <div className="nx-library-more">
            <button type="button" onClick={() => setLimit((value) => value + 96)}>
              Show 96 more
            </button>
          </div>
        ) : null}

        <p className="nx-attribution">
          Glyphs: Tabler Icons (MIT). Glass tile system, icon grammar and plan
          renderer: Nexly.
        </p>
      </section>
    </div>
  )
}
