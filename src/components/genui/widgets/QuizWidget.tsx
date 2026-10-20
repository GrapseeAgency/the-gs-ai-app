'use client'

import { useState } from 'react'
import type { QuizPayload } from '../types'
import { alpha, inkOn, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { EmptyState } from '../primitives/EmptyState'

/** Quiz — tap-to-answer, instant verdict, score kept across questions. */
export function QuizWidget({ payload, onPrompt }: { payload: QuizPayload; onPrompt?: (p: string) => void }) {
  const { accent, theme } = useWidgetIdentity()
  const [answers, setAnswers] = useState<Record<number, number>>({})
  const questions = (payload.questions ?? []).slice(0, 10)
  if (!questions.length) return <EmptyState label="No questions" />
  const answered = Object.keys(answers).length
  const score = questions.reduce((acc, q, qi) => acc + (answers[qi] === q.answer ? 1 : 0), 0)
  const done = answered === questions.length
  return (
    <div>
      <TitleRow title={payload.title} right={answered > 0 ? `${score}/${questions.length}` : `${questions.length} questions`} />
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        {questions.map((q, qi) => {
          const picked = answers[qi]
          const isAnswered = picked !== undefined
          return (
            <div key={qi}>
              <div style={{ fontSize: TYPE.body, fontWeight: 600, color: theme.text, marginBottom: 8 }}>
                {qi + 1}. {q.q}
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {q.options.map((opt, oi) => {
                  const correct = oi === q.answer
                  const pickedThis = picked === oi
                  let bg = 'transparent'
                  let border = theme.border
                  let fg = theme.text
                  if (isAnswered && correct) {
                    bg = alpha('#0CA678', theme.dark ? 0.16 : 0.1)
                    border = '#0CA678'
                  } else if (pickedThis && !correct) {
                    bg = alpha('#E03131', theme.dark ? 0.14 : 0.08)
                    border = '#E03131'
                  } else if (!isAnswered) {
                    bg = 'transparent'
                  } else {
                    fg = theme.text3
                  }
                  return (
                    <button
                      key={oi}
                      type="button"
                      disabled={isAnswered}
                      onClick={() => setAnswers((a) => ({ ...a, [qi]: oi }))}
                      style={{
                        textAlign: 'left',
                        padding: '9px 12px',
                        borderRadius: RADIUS.md,
                        border: `1.5px solid ${border}`,
                        background: bg,
                        color: fg,
                        fontSize: TYPE.label,
                        fontWeight: pickedThis || (isAnswered && correct) ? 700 : 400,
                        cursor: isAnswered ? 'default' : 'pointer',
                        opacity: isAnswered && !correct && !pickedThis ? 0.6 : 1,
                      }}
                    >
                      {opt}
                      {isAnswered && correct ? ' ✓' : ''}
                      {pickedThis && !correct ? ' ✗' : ''}
                    </button>
                  )
                })}
              </div>
              {isAnswered && q.explain ? (
                <div style={{ fontSize: TYPE.micro, color: theme.text2, marginTop: 6, lineHeight: 1.5 }}>{q.explain}</div>
              ) : null}
            </div>
          )
        })}
      </div>
      {done && (
        <div style={{ marginTop: 14, padding: 12, borderRadius: RADIUS.lg, background: alpha(accent, 0.12), border: `1px solid ${alpha(accent, 0.3)}` }}>
          <span style={{ fontSize: TYPE.body, fontWeight: 700, color: accent }}>
            {score}/{questions.length}
          </span>
          <span style={{ fontSize: TYPE.label, color: theme.text2, marginLeft: 8 }}>
            {score === questions.length ? 'Perfect.' : score >= questions.length / 2 ? 'Solid.' : 'Worth another pass.'}
          </span>
        </div>
      )}
    </div>
  )
}
