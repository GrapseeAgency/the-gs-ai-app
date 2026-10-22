'use client'

import { Fragment, type ReactNode } from 'react'
import { canonicalIconName, emojiToIconTokens, ICON_TOKEN_RE } from '@/lib/nexly/icons'
import { GlassIcon } from './GlassIcon'

/**
 * Model prose with two guarantees:
 *  - :icon-name: renders a glass glyph, never literal punctuation;
 *  - known emoji become the same glyphs, unknown pictographs are dropped.
 * The prompt already bans emoji. This is the renderer keeping that promise.
 */
export function NexlyRichText({
  text,
  iconTile = 20,
  iconSize = 11,
  className,
}: {
  text: string
  iconTile?: number
  iconSize?: number
  className?: string
}) {
  const normalised = emojiToIconTokens(text)
  const nodes: ReactNode[] = []
  let last = 0
  let key = 0

  for (const match of normalised.matchAll(ICON_TOKEN_RE)) {
    const index = match.index ?? 0
    if (index > last) {
      nodes.push(<Fragment key={`t${key++}`}>{normalised.slice(last, index)}</Fragment>)
    }
    const raw = match[1]
    const name = canonicalIconName(raw)
    if (name) {
      nodes.push(
        <GlassIcon
          key={`i${key++}`}
          name={name}
          tile={iconTile}
          size={iconSize}
          className="nx-inline-icon"
        />
      )
    } else {
      nodes.push(<Fragment key={`u${key++}`}>{match[0]}</Fragment>)
    }
    last = index + match[0].length
  }
  if (last < normalised.length) {
    nodes.push(<Fragment key={`t${key++}`}>{normalised.slice(last)}</Fragment>)
  }

  return <span className={className}>{nodes}</span>
}
