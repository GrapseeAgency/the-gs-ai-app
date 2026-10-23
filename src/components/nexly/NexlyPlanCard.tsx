'use client'

import { GlassIcon } from './GlassIcon'
import { NexlyRichText } from './NexlyRichText'
import { emojiToIconTokens, ICON_TOKEN_RE } from '@/lib/nexly/icons'
import type { NexlyPlan } from '@/lib/nexly/plan'

/**
 * The timetable. One glass card, one row per time block, one glass glyph per
 * row, a closing line and a timestamp. This is the surface that used to fall
 * through to markdown bullets.
 */
export function NexlyPlanCard({ plan }: { plan: NexlyPlan }) {
  const closingHasIcon = plan.closing
    ? [...emojiToIconTokens(plan.closing).matchAll(ICON_TOKEN_RE)].length > 0
    : false

  return (
    <div className="nx-plan" aria-label={plan.title}>
      <div className="nx-plan-title">
        <NexlyRichText text={plan.title} iconTile={20} iconSize={11} />
      </div>

      <div className="nx-plan-rows">
        {plan.rows.map((row, index) => (
          <div className="nx-plan-row" key={`${row.time}-${index}`}>
            <GlassIcon
              name={row.icon}
              tone={row.tone}
              tile={28}
              size={14}
              title={row.icon}
            />
            <span className="nx-plan-time">{row.time}</span>
            <span className="nx-plan-text">
              <NexlyRichText text={row.text} iconTile={18} iconSize={10} />
            </span>
          </div>
        ))}
      </div>

      {plan.closing ? (
        <div className="nx-plan-closing">
          <NexlyRichText
            text={plan.closing}
            iconTile={22}
            iconSize={12}
            className="nx-plan-closing-text"
          />
          {plan.closingIcon && !closingHasIcon ? (
            <GlassIcon
              name={plan.closingIcon}
              tone="peach"
              tile={22}
              size={12}
              title={plan.closingIcon}
            />
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
