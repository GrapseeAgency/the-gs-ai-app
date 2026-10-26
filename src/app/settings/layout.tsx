import type { Metadata } from 'next'
import './settings.css'

export const metadata: Metadata = {
  title: 'Nexly — Settings',
  description: 'Privacy, conversation history, model selection and reasoning controls for Nexly.',
}

export default function SettingsLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return children
}
