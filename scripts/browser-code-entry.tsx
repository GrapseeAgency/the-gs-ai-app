import React from 'react'
import { createRoot } from 'react-dom/client'
import { TerminalWidget } from '../src/components/genui/terminal/TerminalWidget'
import { HtmlBlock } from '../src/components/genui/blocks/HtmlBlock'
import { CodeBlock } from '../src/components/genui/blocks/CodeBlock'

declare global {
  interface Window {
    gsRenderCode: (code: string, language: string) => void
    gsRenderTerminal: (payload: Record<string, unknown>) => void
    gsRenderHTML: (source: string) => void
  }
}
const root = createRoot(document.getElementById('app')!)
window.gsRenderCode = (code, language) => root.render(<CodeBlock code={code} language={language} />)

window.gsRenderTerminal = payload => root.render(<TerminalWidget payload={payload} />)
window.gsRenderHTML = source => root.render(<HtmlBlock source={source} fallbackHeight={490} />)
