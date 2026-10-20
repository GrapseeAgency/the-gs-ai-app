'use client'

/**
 * WidgetRenderer — one validated widget payload -> one themed component.
 * Payloads arrive pre-validated by the backend compiler; this side still
 * guards: an unknown kind or a throw degrades to a code block, never a crash.
 */

import type { Surface } from '@/lib/design/rulebook'
import { rendererFor, type PromptHandler } from './registry'
import { WidgetSurface } from './theme/WidgetSurface'
import { CodeBlock } from './blocks/CodeBlock'

export interface WidgetBlock {
  kind: string
  payload: Record<string, unknown>
}

export function WidgetRenderer({
  kind,
  payload,
  dark = true,
  onPrompt,
}: {
  kind: string
  payload: Record<string, unknown>
  dark?: boolean
  onPrompt?: PromptHandler
}) {
  const Renderer = rendererFor(kind)
  const accent = typeof payload.accent === 'string' ? payload.accent : undefined
  const variant = typeof payload.variant === 'string' ? (payload.variant as Surface) : undefined
  const palette = typeof payload.palette === 'string' ? payload.palette : undefined

  if (!Renderer) {
    return (
      <WidgetSurface accent={accent} variant={variant} palette={palette} dark={dark}>
        <CodeBlock language={kind} code={JSON.stringify(payload, null, 2)} />
      </WidgetSurface>
    )
  }
  return (
    <WidgetSurface accent={accent} variant={variant} palette={palette} dark={dark}>
      <Renderer payload={payload} onPrompt={onPrompt} />
    </WidgetSurface>
  )
}
