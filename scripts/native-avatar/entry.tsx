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
  // Keep the original 144px backing canvas. The Android host CSS maps the
  // entire square canvas to the native slot, independent of the vendor's
  // negative margins and inline positioning for ordinary web chat layouts.
  // Fixed vendor layout preserves the high resolution backing store.
  const fit = () => Math.max(0.01, Math.min(innerWidth, innerHeight) / 72)
  const [scale, setScale] = useState(fit)
  React.useEffect(() => {
    const resize = () => setScale(fit())
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
    style={{ transform: 'scale(' + scale + ')', transformOrigin: '0 0' }}
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
