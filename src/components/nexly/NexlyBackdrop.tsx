'use client'

/**
 * Pearlescent stage. Large soft bubbles and a stylised lotus, all CSS/SVG so
 * the response can sit on the same atmosphere as the reference without
 * shipping a photograph.
 */
export function NexlyBackdrop() {
  return (
    <div className="nx-bg" aria-hidden="true">
      <div className="nx-bg-wash" />
      <span className="nx-bubble nx-bubble-a" />
      <span className="nx-bubble nx-bubble-b" />
      <span className="nx-bubble nx-bubble-c" />
      <span className="nx-bubble nx-bubble-d" />
      <span className="nx-bubble nx-bubble-e" />
      <svg className="nx-lotus" viewBox="0 0 360 360" fill="none">
        <defs>
          <linearGradient id="nx-petal" x1="180" y1="40" x2="180" y2="340" gradientUnits="userSpaceOnUse">
            <stop offset="0" stopColor="#fff8f1" stopOpacity="0.95" />
            <stop offset="0.45" stopColor="#ffd9c7" stopOpacity="0.85" />
            <stop offset="1" stopColor="#f39a8f" stopOpacity="0.55" />
          </linearGradient>
          <linearGradient id="nx-petal-inner" x1="180" y1="110" x2="180" y2="330" gradientUnits="userSpaceOnUse">
            <stop offset="0" stopColor="#fffdf9" />
            <stop offset="0.6" stopColor="#ffc9b2" />
            <stop offset="1" stopColor="#ee847d" stopOpacity="0.7" />
          </linearGradient>
          <radialGradient id="nx-petal-core" cx="0.5" cy="0.45" r="0.6">
            <stop offset="0" stopColor="#fff6df" />
            <stop offset="0.55" stopColor="#ffc77f" />
            <stop offset="1" stopColor="#f08a5e" stopOpacity="0.2" />
          </radialGradient>
        </defs>
        <g opacity="0.9">
          {[-72, -48, -24, 0, 24, 48, 72].map((angle) => (
            <ellipse
              key={angle}
              cx="180"
              cy="180"
              rx="34"
              ry="128"
              transform={`rotate(${angle} 180 180)`}
              fill="url(#nx-petal)"
              stroke="#fff8f1"
              strokeOpacity="0.55"
            />
          ))}
          {[-58, -34, -10, 14, 38, 62].map((angle) => (
            <ellipse
              key={`inner-${angle}`}
              cx="180"
              cy="190"
              rx="24"
              ry="96"
              transform={`rotate(${angle} 180 190)`}
              fill="url(#nx-petal-inner)"
              stroke="#fffdf9"
              strokeOpacity="0.45"
            />
          ))}
          <circle cx="180" cy="194" r="34" fill="url(#nx-petal-core)" />
          <circle cx="180" cy="194" r="17" fill="#fff4dc" fillOpacity="0.85" />
        </g>
      </svg>
      <span className="nx-grain" />
    </div>
  )
}
