import React from 'react'
import { createRoot } from 'react-dom/client'
import { CodeBlock } from '../src/components/genui/blocks/CodeBlock'

declare global {
  interface Window { gsRenderCode: (code: string, language: string) => void }
}
const root = createRoot(document.getElementById('app')!)
window.gsRenderCode = (code, language) => root.render(<CodeBlock code={code} language={language} />)
