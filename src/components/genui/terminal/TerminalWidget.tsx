'use client'

import { useMemo } from 'react'
import { terminalSandboxDocument } from '@/lib/code/terminal-sandbox'
import { HtmlBlock } from '../blocks/HtmlBlock'
import { TitleRow } from '../primitives/TitleRow'

type TerminalPayload = Record<string, unknown> & {
  title?: string
  welcome?: string
  commands?: Array<{ name: string; description: string }>
  note?: string
}

/** Same actual Worker and local virtual filesystem on Web, Android and iOS.
 * No main-thread Function evaluator, identifier blacklist or pretend shell. */
export function TerminalWidget({ payload }: { payload: TerminalPayload }) {
  const document = useMemo(() => terminalSandboxDocument(payload), [payload])
  return (
    <div>
      <TitleRow title={payload.title ?? 'Terminal'} right="local virtual session" />
      <HtmlBlock source={document} fallbackHeight={490} />
    </div>
  )
}
