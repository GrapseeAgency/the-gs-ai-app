'use client'

import type { AppPayload } from '../types'
import { ChecklistWidget } from '../widgets/ChecklistWidget'

/** App task surfaces use the same real editing/deletion/completion controls.
 * Preserve supplied metadata and completion, never fabricate dates or priority. */
export function AppTasks({ payload }: { payload: AppPayload }) {
  const items = (payload.items ?? []).map(item => ({
    label: String(item.title ?? item.label ?? item.text ?? ''),
    detail: [...new Set([item.subtitle, item.detail, item.description, item.meta, item.badge, item.date]
      .filter(value => typeof value === 'string' && value.trim()).map(String))].join(' · '),
    done: item.done === true || item.checked === true || item.completed === true || item.status === 'done',
  }))
  return <ChecklistWidget payload={{ title: payload.title, items }} />
}
