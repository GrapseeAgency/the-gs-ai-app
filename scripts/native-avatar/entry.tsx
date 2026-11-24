import React, { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { BotAvatar } from 'bot-avatars'

type Shape = 'clover' | 'flower' | 'triangle' | 'square'
type AvatarState = 'default' | 'working'
type Config = { type: Shape; state: AvatarState }
const types = new Set<Shape>(['clover','flower','triangle','square'])
function normalize(value: Partial<Config>): Config {
  return {
    type: types.has(value.type as Shape) ? value.type as Shape : 'clover',
    state: value.state === 'working' ? 'working' : 'default'
  }
}
declare global { interface Window { gsAvatarUpdate?: (value: Partial<Config>) => void } }
let latest = normalize({})
let onChange: ((value: Config) => void) | null = null
window.gsAvatarUpdate = value => {
  latest = normalize(value ?? {})
  onChange?.(latest)
}
function Avatar() {
  const [config, setConfig] = useState(() => latest)
  React.useEffect(() => {
    onChange = setConfig
    setConfig(latest)
    return () => { onChange = null }
  }, [])
  return <BotAvatar type={config.type} state={config.state} size={48}
    color={config.type === 'clover' ? '#2FCB7A' : undefined}
    shading="fabric" face="eyes" theme="light" interactive={false}
    speed={1} style={{ margin: 0 }} />
}
createRoot(document.getElementById('root')!).render(<Avatar />)
