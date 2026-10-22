import type { Metadata } from 'next'
import './nexly.css'

export const metadata: Metadata = {
  title: 'Nexly Response — GS AI',
  description:
    'A glass response slice: the Thinking card, a validated plan block, and a 5,184-glyph glass icon vocabulary.',
}

export default function NexlyLayout({ children }: { children: React.ReactNode }) {
  return children
}
