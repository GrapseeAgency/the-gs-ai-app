'use client'

/**
 * The terminal kind: an in-chat shell with a toy filesystem and a sandboxed
 * JS evaluator. Runs entirely in the browser — nothing escapes the widget.
 */

import { useEffect, useRef, useState } from 'react'
type Rec<K extends string = string, V = unknown> = Record<K, V>
import { alpha, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'

type TerminalPayload = Rec<string, unknown> & {
  title?: string
  welcome?: string
  commands?: Array<{ name: string; description: string }>
  note?: string
}

type Line = { kind: 'in' | 'out' | 'err' | 'sys'; text: string }

// blocked identifiers: any attempt to reach network / storage / eval-escape
const BLOCKED = /\b(fetch|XMLHttpRequest|WebSocket|localStorage|sessionStorage|indexedDB|document|window|globalThis|import|require|eval|Function|postMessage|location|navigator|document\.cookie|serviceWorker|Notification|geolocation)\b/

export function TerminalWidget({ payload }: { payload: TerminalPayload }) {
  const { accent, theme } = useWidgetIdentity()
  const cwd = useRef('/home/guest')
  const fs = useRef<Record<string, string | true>>({
    '/home/guest': true,
    '/home/guest/README.md': '# Guest shell\nThis filesystem lives in your browser tab only.',
    '/home/guest/notes.txt': 'try: echo hi · ls · cat notes.txt · js 6*7 · tree',
  })
  const [lines, setLines] = useState<Line[]>(() => [
    { kind: 'sys', text: payload.welcome ?? 'guest shell — type `help` for commands' },
  ])
  const [input, setInput] = useState('')
  const [hist, setHist] = useState<string[]>([])
  const [histIdx, setHistIdx] = useState(-1)
  const scrollRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight })
  }, [lines])

  const push = (...ls: Line[]) => setLines((prev) => [...prev, ...ls].slice(-120))

  const run = (cmdline: string) => {
    const cmd = cmdline.trim()
    push({ kind: 'in', text: `${cwd.current} $ ${cmd}` })
    if (!cmd) return
    setHist((h) => [cmd, ...h].slice(0, 40))
    setHistIdx(-1)
    const [name, ...args] = cmd.split(/\s+/)
    const path = (p: string) => {
      if (!p) return cwd.current
      if (p.startsWith('/')) return p.replace(/\/+$/, '') || '/'
      if (p === '..') return cwd.current.replace(/\/[^/]+$/, '') || '/'
      if (p === '.') return cwd.current
      if (p === '~') return '/home/guest'
      return `${cwd.current.replace(/\/$/, '')}/${p}`
    }
    switch (name) {
      case 'help':
        push(
          { kind: 'out', text: 'built-ins: help clear echo date whoami pwd ls tree cd cat touch rm mkdir' },
          { kind: 'out', text: 'js <expr> — sandboxed JavaScript (no network, no storage)' },
          ...[...(payload.commands ?? [])].map((c) => ({ kind: 'out' as const, text: `${c.name} — ${c.description}` }))
        )
        break
      case 'clear':
        setLines([])
        break
      case 'echo':
        push({ kind: 'out', text: args.join(' ') })
        break
      case 'date':
        push({ kind: 'out', text: new Date().toString() })
        break
      case 'whoami':
        push({ kind: 'out', text: 'guest' })
        break
      case 'pwd':
        push({ kind: 'out', text: cwd.current })
        break
      case 'ls': {
        const dir = path(args.find((a) => !a.startsWith('-')) ?? '')
        const entries = Object.entries(fs.current)
          .filter(([k]) => k.startsWith(dir === '/' ? '/' : `${dir}/`))
          .filter(([k]) => k !== dir)
          .map(([k, v]) => ({ name: k.split('/').pop()!, dir: v === true }))
        if (!entries.length) push({ kind: 'out', text: '(empty)' })
        else push({ kind: 'out', text: entries.map((e) => (e.dir ? `${e.name}/` : e.name)).join('  ') })
        break
      }
      case 'tree': {
        const rows = Object.keys(fs.current).sort().map((k) => `${k}${fs.current[k] === true ? '/' : ''}`)
        push({ kind: 'out', text: rows.join('\n') })
        break
      }
      case 'cd': {
        const target = path(args[0] ?? '~')
        if (fs.current[target] === true) cwd.current = target
        else push({ kind: 'err', text: `cd: no such directory: ${target}` })
        break
      }
      case 'cat': {
        const f = fs.current[path(args[0] ?? '')]
        if (typeof f === 'string') push({ kind: 'out', text: f })
        else push({ kind: 'err', text: `cat: ${args[0] ?? ''}: not a file` })
        break
      }
      case 'touch': {
        const p = path(args[0] ?? '')
        if (!p) push({ kind: 'err', text: 'touch: name required' })
        else fs.current[p] = fs.current[p] ?? ''
        break
      }
      case 'mkdir': {
        const p = path(args[0] ?? '')
        if (!p) push({ kind: 'err', text: 'mkdir: name required' })
        else fs.current[p] = true
        break
      }
      case 'rm': {
        const p = path(args.filter((a) => !a.startsWith('-'))[0] ?? '')
        if (p && p in fs.current) {
          for (const k of Object.keys(fs.current)) if (k.startsWith(`${p}/`) || k === p) delete fs.current[k]
        } else push({ kind: 'err', text: `rm: not found: ${args[0] ?? ''}` })
        break
      }
      case 'js': {
        const src = args.join(' ')
        if (!src) {
          push({ kind: 'err', text: 'js: expression required, e.g. js [1,2,3].map(x => x * 2)' })
          break
        }
        if (BLOCKED.test(src)) {
          push({ kind: 'err', text: 'js: blocked identifier — this sandbox has no network, storage or DOM access' })
          break
        }
        try {
          // eslint-disable-next-line no-new-func
          const out = Function('"use strict"; const Math2=Math; return (' + src + ')')()
          push({ kind: 'out', text: typeof out === 'object' ? JSON.stringify(out) : String(out) })
        } catch (e) {
          push({ kind: 'err', text: `js: ${(e as Error).message}` })
        }
        break
      }
      default: {
        const custom = (payload.commands ?? []).find((c) => c.name === name)
        if (custom) push({ kind: 'out', text: `${custom.name}: ${custom.description}` })
        else push({ kind: 'err', text: `${name}: command not found — try \`help\`` })
      }
    }
  }

  return (
    <div>
      <TitleRow title={payload.title ?? 'Terminal'} right="sandboxed · browser-only" />
      <div
        ref={scrollRef}
        onClick={() => inputRef.current?.focus()}
        style={{
          height: 250,
          overflowY: 'auto',
          borderRadius: RADIUS.md,
          border: `1px solid ${theme.border}`,
          background: '#0B0E12',
          padding: 12,
          fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
          fontSize: 12,
          lineHeight: 1.6,
        }}
      >
        {lines.map((l, i) => (
          <div
            key={i}
            style={{
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-word',
              color: l.kind === 'in' ? '#9BA3AF' : l.kind === 'err' ? '#FF8787' : l.kind === 'sys' ? alpha(accent, 1) : '#E9ECEF',
            }}
          >
            {l.text}
          </div>
        ))}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: '#9BA3AF' }}>
          <span style={{ color: accent, whiteSpace: 'nowrap' }}>{cwd.current} $</span>
          <input
            ref={inputRef}
            value={input}
            spellCheck={false}
            autoComplete="off"
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                run(input)
                setInput('')
              } else if (e.key === 'ArrowUp') {
                e.preventDefault()
                const next = Math.min(hist.length - 1, histIdx + 1)
                if (hist[next] !== undefined) {
                  setHistIdx(next)
                  setInput(hist[next])
                }
              } else if (e.key === 'ArrowDown') {
                e.preventDefault()
                const next = histIdx - 1
                setHistIdx(next)
                setInput(next >= 0 ? hist[next] ?? '' : '')
              }
            }}
            style={{ flex: 1, background: 'transparent', border: 'none', outline: 'none', color: '#E9ECEF', fontFamily: 'inherit', fontSize: 'inherit', caretColor: accent }}
          />
        </div>
      </div>
    </div>
  )
}
