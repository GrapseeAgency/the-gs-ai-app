'use client'

import { useState } from 'react'
import { conceptQuestions, type Concept } from '@/lib/nexly/concept-deck'
import { WidgetRenderer } from './WidgetRenderer'
import { GlassIcon } from '@/components/nexly/GlassIcon'

export function SpontaneousConceptDeck({ concepts, dark }: { concepts: Concept[]; dark: boolean }) {
  const [quiz, setQuiz] = useState(false)
  return <section aria-label="Practice concepts from this answer">
    <button type="button" onClick={() => setQuiz((v) => !v)} aria-pressed={quiz}
      style={{ display: 'inline-flex', gap: 6, alignItems: 'center', marginBottom: 8, cursor: 'pointer' }}>
      <GlassIcon name={quiz ? 'cards' : 'school'} tile={24} size={13} />
      {quiz ? 'Switch to flashcards' : 'Try a quick quiz'}
    </button>
    <WidgetRenderer key={quiz ? 'quiz' : 'cards'} kind={quiz ? 'quiz' : 'flashcards'} dark={dark}
      payload={quiz ? { title: 'Quick quiz · from this answer', questions: conceptQuestions(concepts) }
        : { title: 'Flashcards · from this answer', cards: concepts.map((c) => ({ front: c.term, back: c.definition })) }} />
  </section>
}
