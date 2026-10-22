'use client'

import { GlassIcon } from './GlassIcon'
import type { NexlyThinking } from '@/lib/nexly/plan'

/**
 * The three-stage status card. This is a sibling of the answer, not a
 * pre-first-token placeholder: callers can keep it mounted while the preamble
 * and the plan are already on screen, which is exactly the reference order.
 */
export function NexlyThinkingCard({ thinking }: { thinking: NexlyThinking }) {
  return (
    <div className="nx-thinking" data-active={thinking.active ? 'true' : 'false'}>
      {thinking.rows.map((row, index) => (
        <div className="nx-thinking-row" key={`${row.title}-${index}`}>
          <GlassIcon
            name={row.icon}
            tone={row.tone}
            tile={34}
            size={17}
            title={row.title}
          />
          <div className="nx-thinking-copy">
            <div className="nx-thinking-title">{row.title}</div>
            <div className="nx-thinking-subtitle">{row.subtitle}</div>
          </div>
        </div>
      ))}
    </div>
  )
}
