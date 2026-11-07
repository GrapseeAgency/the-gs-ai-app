'use client'

import { useState } from 'react'
import type { ChecklistPayload } from '../types'
import { TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { UiCheckbox } from '../controls/UiCheckbox'
import { GlassIcon } from '@/components/nexly/GlassIcon'
import { NexlyRichText } from '@/components/nexly/NexlyRichText'

/** Real local task state, not a static checklist illustration. No fabricated
 * dates/priorities and no claimed sync: tasks live in this mounted widget. */
export function ChecklistWidget({ payload }: { payload: ChecklistPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const [items, setItems] = useState(() => (payload.items ?? []).map((i) => ({ ...i })))
  const [draft, setDraft] = useState('')
  const [editing, setEditing] = useState<number | null>(null)
  const [editText, setEditText] = useState('')
  const done = items.filter((i) => i.done).length
  const pct = items.length ? Math.round((done / items.length) * 100) : 0
  return (
    <div>
      <TitleRow title={payload.title} right={`${done}/${items.length}`} />
      <div role="progressbar" aria-label="Task completion" aria-valuemin={0} aria-valuemax={100} aria-valuenow={pct}
        style={{ height: 6, borderRadius: 3, background: theme.raised, overflow: 'hidden', marginBottom: 12 }}>
        <div style={{ width: `${pct}%`, height: '100%', background: accent }} />
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {items.map((item, i) => (
          <div key={i} style={{ padding: '6px 0', borderBottom: `1px solid ${theme.border}` }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <UiCheckbox checked={item.done === true}
                onChange={(v) => setItems((s) => s.map((it, j) => j === i ? { ...it, done: v } : it))} />
              {editing === i ? <form style={{ flex: 1 }} onSubmit={(e) => {
                e.preventDefault()
                if (!editText.trim()) return
                setItems((s) => s.map((it, j) => j === i ? { ...it, label: editText.trim() } : it))
                setEditing(null)
              }}>
                <input aria-label="Edit task" value={editText} onChange={(e) => setEditText(e.target.value)} required />
                <button type="submit">Save</button>
                <button type="button" onClick={() => setEditing(null)}>Cancel</button>
              </form> : <>
                <span style={{ flex: 1, color: theme.text, textDecoration: item.done ? 'line-through' : 'none' }}><NexlyRichText text={item.label} /></span>
                <button type="button" aria-label={`Edit ${item.label}`} onClick={() => { setEditing(i); setEditText(item.label) }}><GlassIcon name="pencil" tile={22} size={12} /></button>
              </>}
              <button type="button" aria-label={`Delete ${item.label}`} onClick={() => {
                setItems((s) => s.filter((_, j) => j !== i)); setEditing(null)
              }}><GlassIcon name="trash" tile={22} size={12} /></button>
            </div>
            {item.detail && <div style={{ fontSize: TYPE.micro, color: theme.text3, marginLeft: 30, marginTop: 2 }}><NexlyRichText text={item.detail} /></div>}
          </div>
        ))}
      </div>
      <form style={{ display: 'flex', gap: 8, marginTop: 10 }} onSubmit={(e) => {
        e.preventDefault()
        if (!draft.trim()) return
        setItems((s) => [...s, { label: draft.trim(), done: false }]); setDraft('')
      }}>
        <input aria-label="New task" placeholder="Add your own task" value={draft} onChange={(e) => setDraft(e.target.value)} required />
        <button type="submit">Add Task</button>
      </form>
    </div>
  )
}
