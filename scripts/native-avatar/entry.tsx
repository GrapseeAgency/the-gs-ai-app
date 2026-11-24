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
  AndroidAvatarFrames?: { frame(dataUrl: string): void; diagnostics(): string }
  gsAvatarFrameDiagnostics?: () => string
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
  // Match the original web chat's green clover and original fabric material.
  // Render a high-resolution complete 1.5x overscan canvas (not a cropped
  // screenshot). The actual transcript slot is smaller and scaled once.
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
    style={{ margin: 0 }}
  />
}
createRoot(document.getElementById('root')!).render(<Avatar />)

// The vendor's requestAnimationFrame rig is untouched. Sample its original
// changing pixels at a 30 fps target (instead of the old 10 fps bottleneck).
// Never retransmit an identical image, including when reduced motion freezes
// the avatar. All visual motion still originates in bot-avatars itself.
const frameInterval = 1000 / 30 - 3
let lastFrame = 0
let lastImage = ''
let sendAttempts = 0
let sentFrames = 0
let frameErrors = 0
let lastFrameError = ''
window.gsAvatarFrameDiagnostics = () =>
  'attempts=' + sendAttempts + ',sent=' + sentFrames +
  ',errors=' + frameErrors + ',last=' + lastFrameError
const deliverCanvas = (time: number) => {
  requestAnimationFrame(deliverCanvas)
  if (time - lastFrame < frameInterval) return
  lastFrame = time
  const bridge = window.AndroidAvatarFrames
  const canvas = document.querySelector('canvas')
  if (!bridge || !canvas || !canvas.width || !canvas.height) return
  // Respect the real library's initial fabric-material readiness/fade.
  if (Number(getComputedStyle(canvas).opacity) < 0.99) return
  sendAttempts++
  try {
    const png = canvas.toDataURL('image/png')
    if (png === lastImage) return
    bridge.frame(png)
    lastImage = png
    sentFrames++
  } catch (e) {
    frameErrors++
    lastFrameError = String(e)
  }
}
requestAnimationFrame(deliverCanvas)
