'use client'

import { useState } from 'react'
import type { TreeNode, TreePayload } from '../types'
import { alpha, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'
import { EmptyState } from '../primitives/EmptyState'

/** Tree — collapsible hierarchy (files, org, taxonomy). */
export function TreeWidget({ payload }: { payload: TreePayload }) {
  const { accent, theme } = useWidgetIdentity()
  if (!(payload.roots ?? []).length) return <EmptyState label="No tree" />
  return (
    <div>
      <TitleRow title={payload.title} />
      {(payload.roots ?? []).map((root, i) => (
        <TreeBranch key={i} node={root} depth={0} />
      ))}
    </div>
  )
}

function TreeBranch({ node, depth }: { node: TreeNode; depth: number }) {
  const { accent, theme } = useWidgetIdentity()
  const hasKids = (node.children ?? []).length > 0
  const [open, setOpen] = useState(depth < 2)
  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '4px 0', paddingLeft: depth * 16 }}>
        {hasKids ? (
          <button
            type="button"
            onClick={() => setOpen((o) => !o)}
            aria-expanded={open}
            style={{ border: 'none', background: 'transparent', cursor: 'pointer', color: accent, fontSize: 11, width: 16, padding: 0, transition: 'transform .15s ease', transform: open ? 'rotate(90deg)' : 'none' }}
          >
            ▸
          </button>
        ) : (
          <span style={{ width: 16, textAlign: 'center', color: theme.text3, fontSize: 11 }}>·</span>
        )}
        <span style={{ fontSize: TYPE.label, color: theme.text, fontWeight: depth === 0 ? 700 : 400 }}>{node.label}</span>
        {node.detail ? <span style={{ fontSize: TYPE.micro, color: theme.text3 }}>{node.detail}</span> : null}
      </div>
      {open && (node.children ?? []).map((child, i) => <TreeBranch key={i} node={child} depth={depth + 1} />)}
    </div>
  )
}
