'use client'

import type { StepsPayload } from '../types'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { EmptyState } from '../primitives/EmptyState'

/** Steps — a vertical stepper: done / current / todo states. */
export function StepsWidget({ payload }: { payload: StepsPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const steps = (payload.steps ?? []).slice(0, 12)
  if (!steps.length) return <EmptyState label="No steps" />
  return (
    <div>
      <TitleRow title={payload.title} right={`${steps.filter((s) => s.state === 'done').length}/${steps.length}`} />
      <div style={{ position: 'relative', paddingLeft: 30 }}>
        <span aria-hidden style={{ position: 'absolute', left: 11, top: 8, bottom: 8, width: 2, background: theme.border }} />
        {steps.map((step, i) => {
          const done = step.state === 'done'
          const current = step.state === 'current'
          return (
            <div key={i} style={{ position: 'relative', paddingBottom: i === steps.length - 1 ? 0 : 16 }}>
              <span
                aria-hidden
                style={{
                  position: 'absolute',
                  left: -30,
                  top: 0,
                  width: 24,
                  height: 24,
                  borderRadius: 999,
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: TYPE.micro,
                  fontWeight: 700,
                  background: done ? accent : current ? alpha(accent, 0.16) : theme.card,
                  border: `2px solid ${done || current ? accent : theme.border}`,
                  color: done ? theme.dark ? '#0F1216' : '#fff' : current ? accent : theme.text3,
                }}
              >
                {done ? '✓' : i + 1}
              </span>
              <div style={{ fontSize: TYPE.body, fontWeight: current ? 700 : 600, color: current ? accent : theme.text }}>{step.title}</div>
              {step.body ? <div style={{ fontSize: TYPE.label, color: theme.text2, lineHeight: 1.55, marginTop: 3 }}>{step.body}</div> : null}
            </div>
          )
        })}
      </div>
    </div>
  )
}
