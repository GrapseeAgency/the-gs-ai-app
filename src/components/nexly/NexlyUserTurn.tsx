'use client'

import { GlassIcon } from './GlassIcon'
import { NexlyRichText } from './NexlyRichText'

/** Demo chrome: the user turn the assistant is answering. */
export function NexlyUserTurn({
  text,
  time,
  delivered = true,
}: {
  text: string
  time: string
  delivered?: boolean
}) {
  return (
    <div className="nx-turn nx-turn-user">
      <div className="nx-user-main">
        <div className="nx-user-bubble">
          <NexlyRichText text={text} iconTile={18} iconSize={10} />
        </div>
        <span className="nx-user-avatar" aria-hidden="true">
          <GlassIcon bare name="user" size={17} />
        </span>
      </div>
      <div className="nx-user-meta">
        <span>{time}</span>
        {delivered ? (
          <GlassIcon bare name="checks" size={13} className="nx-delivered" />
        ) : null}
      </div>
    </div>
  )
}
