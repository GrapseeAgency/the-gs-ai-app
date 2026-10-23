'use client'

import { GlassIcon } from './GlassIcon'

/** Demo chrome: the reference composer, one glass capsule. */
export function NexlyComposer() {
  return (
    <div className="nx-composer-wrap">
      <div className="nx-composer">
        <span className="nx-composer-spark">
          <GlassIcon bare name="sparkles" size={20} />
        </span>
        <span className="nx-composer-placeholder">Ask me anything...</span>
        <button className="nx-send" type="button" aria-label="Send">
          <GlassIcon bare name="send-2" size={18} />
        </button>
      </div>
    </div>
  )
}
