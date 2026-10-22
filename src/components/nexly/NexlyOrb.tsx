'use client'

import { useId } from 'react'

/** The assistant identity: a small glass torus, not an emoji or a letter. */
export function NexlyOrb({ size = 34 }: { size?: number }) {
  const uid = useId().replace(/[^a-zA-Z0-9_-]/g, '')
  const bodyId = `nx-orb-body-${uid}`
  const swirlId = `nx-orb-swirl-${uid}`
  return (
    <span className="nx-orb" style={{ width: size, height: size }} aria-hidden="true">
      <svg viewBox="0 0 40 40" width={size} height={size}>
        <defs>
          <radialGradient id={bodyId} cx="34%" cy="28%" r="72%">
            <stop offset="0%" stopColor="#fffdf9" />
            <stop offset="28%" stopColor="#ffd7a8" />
            <stop offset="62%" stopColor="#ff9a4d" />
            <stop offset="100%" stopColor="#ef6425" />
          </radialGradient>
          <linearGradient id={swirlId} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#ffffff" stopOpacity="0.95" />
            <stop offset="55%" stopColor="#fff3e3" stopOpacity="0.4" />
            <stop offset="100%" stopColor="#ffffff" stopOpacity="0.05" />
          </linearGradient>
        </defs>
        <circle cx="20" cy="20" r="19" fill={`url(#${bodyId})`} />
        <circle cx="20" cy="20" r="18.4" fill="none" stroke="#ffffff" strokeOpacity="0.6" />
        <path
          d="M8 24c2.6-8.4 9.8-13.8 17.6-12.4 5.2.9 7.8 4.6 6.4 8.4-1.5 4-7.2 6.2-12.6 4.6-4.3-1.3-6.2-4.4-4.7-7.1 1.2-2.2 4.2-3.3 7.1-2.4"
          fill="none"
          stroke={`url(#${swirlId})`}
          strokeWidth="2.1"
          strokeLinecap="round"
        />
        <ellipse cx="13.5" cy="11.5" rx="5.2" ry="2.6" fill="#ffffff" opacity="0.55" transform="rotate(-28 13.5 11.5)" />
      </svg>
    </span>
  )
}
