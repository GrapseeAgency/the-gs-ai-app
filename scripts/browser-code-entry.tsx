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

// The same production controls used by response-rendered task/app widgets.
import { ChecklistWidget } from '../src/components/genui/widgets/ChecklistWidget'
import { AppWizard } from '../src/components/genui/apps/AppWizard'
import { AppOnboarding } from '../src/components/genui/apps/AppOnboarding'
import { AppBooking } from '../src/components/genui/apps/AppBooking'
import { WidgetSurface } from '../src/components/genui/theme/WidgetSurface'
declare global {
  interface Window {
    gsRenderChecklist: (payload: Parameters<typeof ChecklistWidget>[0]['payload']) => void
    gsRenderApp: (kind: 'wizard' | 'onboarding' | 'booking', payload: Parameters<typeof AppWizard>[0]['payload']) => void
  }
}
window.gsRenderChecklist = payload => root.render(<WidgetSurface dark={false}><ChecklistWidget payload={payload} /></WidgetSurface>)
window.gsRenderApp = (kind, payload) => {
  const Component = kind === 'wizard' ? AppWizard : kind === 'onboarding' ? AppOnboarding : AppBooking
  root.render(<WidgetSurface dark={false}><Component key={JSON.stringify(payload)} payload={payload} /></WidgetSurface>)
}
