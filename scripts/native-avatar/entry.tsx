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
} }

let latest = normalize({})
let onChange: ((value: Config) => void) | null = null
// Register BEFORE React's asynchronous first paint. The Android page-finished
// bridge can fire before Avatar() mounts; its initial working state must stick.
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
  return <BotAvatar
    type={config.type}
    state={config.state}
    paused={config.paused}
    size={40}
    color={config.type === 'clover' ? '#24DF79' : undefined}
    shading="fabric"
    face="eyes"
    theme="light"
    interactive={false}
    speed={1}
  />
}
createRoot(document.getElementById('root')!).render(<Avatar />)
