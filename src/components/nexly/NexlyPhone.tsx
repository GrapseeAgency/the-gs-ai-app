'use client'

import { GlassIcon } from './GlassIcon'
import { NexlyAssistantTurn } from './NexlyAssistantTurn'
import { NexlyComposer } from './NexlyComposer'
import { NexlyHeader } from './NexlyHeader'
import { NexlyUserTurn } from './NexlyUserTurn'
import {
  NEXLY_DEMO_THINKING,
  NEXLY_DEMO_USER,
  NEXLY_DEMO_WIRE,
} from '@/lib/nexly/demo'
import { parseNexlyReply } from '@/lib/nexly/plan'

/** The reference screen, assembled. Response components are reusable; the
 *  header/user/composer here are demo context, not the deliverable. */
export function NexlyPhone() {
  // The demo goes through the real wire parser, not a hard-coded component tree.
  const reply = parseNexlyReply(NEXLY_DEMO_WIRE)

  return (
    <div className="nx-phone-shell">
      <div className="nx-phone">
        <div className="nx-status">
          <span className="nx-status-time">9:41</span>
          <span className="nx-status-icons">
            <GlassIcon bare name="antenna-bars-5" size={13} />
            <GlassIcon bare name="wifi" size={14} />
            <GlassIcon bare name="battery-4" size={15} />
          </span>
        </div>

        <NexlyHeader />

        <main className="nx-phone-scroll">
          <NexlyUserTurn
            text={NEXLY_DEMO_USER.text}
            time={NEXLY_DEMO_USER.time}
            delivered={NEXLY_DEMO_USER.delivered}
          />
          <NexlyAssistantTurn
            preamble={reply.preamble}
            thinking={NEXLY_DEMO_THINKING}
          />
          <NexlyAssistantTurn plan={reply.plan} />
        </main>

        <NexlyComposer />
        <div className="nx-home-indicator" aria-hidden="true" />
      </div>
    </div>
  )
}
