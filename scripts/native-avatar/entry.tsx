import React, { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { BotAvatar } from 'bot-avatars'

type Shape = 'clover' | 'flower' | 'triangle' | 'square'
type AvatarState = 'default' | 'working'
type Config = { type: Shape; state: AvatarState; paused: boolean }
const types = new Set<Shape>(['clover', 'flower', 'triangle', 'square'])
function normalize(value: Partial<Config>): Config {
  return {
    type: types.has(value.type as Shape) ? value.type as Shape : 'clover',
    state: value.state === 'working' ? 'working' : 'default',
    paused: value.paused === true,
  }
}
declare global { interface Window {
  gsAvatarUpdate?: (value: Partial<Config>) => void
  gsAvatarGetState?: () => AvatarState
  gsAvatarDiagnostics?: () => object
} }

let latest = normalize({})
let onChange: ((value: Config) => void) | null = null
window.gsAvatarUpdate = (value) => {
  latest = normalize(value ?? {})
  onChange?.(latest)
}
window.gsAvatarGetState = () => latest.state

function Avatar() {
  const [config, setConfig] = useState<Config>(() => latest)
  React.useEffect(() => {
    onChange = setConfig
    setConfig(latest)
    return () => { onChange = null }
  }, [])
  // The original library draws a 1.5x overscan canvas. Render it at 48 CSS px
  // body size (72 CSS px canvas), then fit the WHOLE canvas into the actual
  // native slot with CSS. Do not crop its jumps or replace its motion.
  const [scale, setScale] = useState(() => Math.min(innerWidth, innerHeight) / 72)
  React.useEffect(() => {
    const resize = () => setScale(Math.min(innerWidth, innerHeight) / 72)
    window.addEventListener('resize', resize)
    resize()
    return () => window.removeEventListener('resize', resize)
  }, [])
  return <BotAvatar
    type={config.type}
    state={config.state}
    paused={config.paused}
    size={48}
    color={config.type === 'clover' ? '#2FCB7A' : undefined}
    shading="fabric"
    face="eyes"
    theme="light"
    interactive={false}
    speed={1}
    style={{ margin: 0, transform: 'scale(' + scale + ')', transformOrigin: 'center' }}
  />
}
createRoot(document.getElementById('root')!).render(<Avatar />)

// Debugging only. No per-frame work or bridge in the production renderer.
window.gsAvatarDiagnostics = () => {
  const canvas = document.querySelector('canvas')
  const rect = canvas?.getBoundingClientRect()
  return {
    state: canvas?.dataset.state,
    opacity: canvas ? Number(getComputedStyle(canvas).opacity) : 0,
    width: canvas?.width, height: canvas?.height,
    viewport: [innerWidth, innerHeight],
    bounds: rect && [rect.left, rect.top, rect.right, rect.bottom],
    reducedMotion: matchMedia('(prefers-reduced-motion: reduce)').matches,
    paused: latest.paused
  }
}
