'use client'

import { NexlyOrb } from './NexlyOrb'
import { NexlyPlanCard } from './NexlyPlanCard'
import { NexlyRichText } from './NexlyRichText'
import { NexlyThinkingCard } from './NexlyThinkingCard'
import type { NexlyPlan, NexlyThinking } from '@/lib/nexly/plan'

/**
 * One assistant turn: orb outside, glass card(s) inside. The status card is a
 * child of the same card as the preamble, so it can remain visible while the
 * answer streams instead of vanishing at the first token.
 */
export function NexlyAssistantTurn({
  preamble,
  thinking,
  plan,
  className,
}: {
  preamble?: string
  thinking?: NexlyThinking
  plan?: NexlyPlan
  className?: string
}) {
  return (
    <div className={`nx-turn nx-turn-assistant ${className ?? ''}`}>
      <div className="nx-turn-orb">
        <NexlyOrb size={34} />
      </div>
      <div className="nx-assistant-stack">
        {preamble || thinking ? (
          <div className="nx-assistant-card">
            {preamble ? (
              <div className="nx-preamble">
                <NexlyRichText text={preamble} />
              </div>
            ) : null}
            {thinking ? <NexlyThinkingCard thinking={thinking} /> : null}
          </div>
        ) : null}
        {plan ? <NexlyPlanCard plan={plan} /> : null}
        {plan?.timestamp ? <div className="nx-turn-time">{plan.timestamp}</div> : null}
      </div>
    </div>
  )
}
