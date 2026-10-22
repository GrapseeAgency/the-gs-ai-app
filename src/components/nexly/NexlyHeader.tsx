'use client'

import { GlassIcon } from './GlassIcon'
import { NexlyOrb } from './NexlyOrb'

/** Demo chrome: the reference header, included so the response is seen in context. */
export function NexlyHeader() {
  return (
    <header className="nx-header">
      <NexlyOrb size={42} />
      <div className="nx-brand">
        <div className="nx-brand-name">Nexly</div>
        <div className="nx-brand-sub">AI Companion</div>
      </div>
      <div className="nx-header-actions">
        <button className="nx-glass-button" type="button" aria-label="New spark">
          <GlassIcon bare name="sparkles" size={18} />
        </button>
        <button className="nx-glass-button" type="button" aria-label="Menu">
          <GlassIcon bare name="menu-2" size={18} />
        </button>
      </div>
    </header>
  )
}
