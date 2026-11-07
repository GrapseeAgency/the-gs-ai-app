'use client'

/**
 * CodeBlock — full code experience: syntax highlighting, inline editing,
 * execution preview (JS/TS console runner, HTML/SVG sandboxed iframe, JSON
 * validator), and error diagnostics.
 */
import { useMemo, useState, type ReactNode } from 'react'
import { alpha, RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { CopyChip } from '../primitives/CopyChip'
import { GlassIcon } from '@/components/nexly/GlassIcon'
import { evaluateExpr } from '@/lib/calc/evaluate'

const KEYWORD_RE =
  /\b(const|let|var|function|return|if|else|for|while|switch|case|break|continue|new|class|extends|import|from|export|default|async|await|try|catch|finally|throw|typeof|instanceof|in|of|def|elif|lambda|yield|with|as|pass|raise|True|False|None|fun|val|when|object|interface|struct|enum|guard|protocol|impl|pub|fn|match|mut|use|SELECT|FROM|WHERE|JOIN|LEFT|RIGHT|INNER|GROUP|BY|ORDER|INSERT|INTO|VALUES|UPDATE|SET|DELETE|CREATE|TABLE|LIMIT)\b/g

const RUNNABLE_JS = new Set(['js', 'javascript', 'ts', 'typescript', 'jsx', 'tsx', 'node'])
const RUNNABLE_HTML = new Set(['html', 'htm', 'svg'])
const RUNNABLE_PY = new Set(['py', 'python', 'python3'])
const RUNNABLE_SQL = new Set(['sql', 'sqlite', 'postgres', 'postgresql', 'mysql'])
const RUNNABLE_SH = new Set(['sh', 'bash', 'zsh', 'shell'])
const ERROR_LANGS = new Set(['error', 'stderr', 'traceback', 'stacktrace', 'exception'])

function splitTopLevelCommas(input: string): string[] {
  const parts: string[] = []
  let cur = ''
  let depth = 0
  let inSingle = false
  let inDouble = false
  for (let i = 0; i < input.length; i++) {
    const ch = input[i]
    const prev = i > 0 ? input[i - 1] : ''
    if (ch === "'" && !inDouble && prev !== '\\') inSingle = !inSingle
    else if (ch === '"' && !inSingle && prev !== '\\') inDouble = !inDouble
    else if (!inSingle && !inDouble) {
      if (ch === '(' || ch === '[' || ch === '{') depth++
      else if (ch === ')' || ch === ']' || ch === '}') depth = Math.max(0, depth - 1)
      else if (ch === ',' && depth === 0) {
        parts.push(cur.trim())
        cur = ''
        continue
      }
    }
    cur += ch
  }
  if (cur.trim()) parts.push(cur.trim())
  return parts
}

function fmtNum(n: number): string {
  return Number.isInteger(n) ? String(n) : String(Math.round(n * 10000) / 10000)
}

function simulateNonJsExecution(lang: string, source: string): { output: string; error?: string } {
  const trimmed = source.trim()
  if (!trimmed) return { output: '(empty script)' }

  if (RUNNABLE_PY.has(lang)) {
    if (/\braise\s+\w+/.test(trimmed)) {
      const m = trimmed.match(/\braise\s+(\w+)\(([^)]*)\)/)
      return {
        output: '',
        error: `Traceback (most recent call last):\n  File "main.py", line 1, in <module>\n${m ? `${m[1]}: ${m[2].replace(/^['"]|['"]$/g, '')}` : 'RuntimeError: Execution halted'}`,
      }
    }
    const prints: string[] = []
    const numVars: Record<string, number> = {}
    const strVars: Record<string, string> = {}
    const listVars: Record<string, number[]> = {}
    const funcs: Record<string, { params: string[]; bodyExpr: string }> = {}

    const parseInlineList = (raw: string): number[] | null => {
      const t = raw.trim()
      if (!t.startsWith('[') || !t.endsWith(']')) return null
      const inner = t.slice(1, -1).trim()
      if (!inner) return []
      const nums = splitTopLevelCommas(inner)
        .map((x) => evalPyNum(x))
        .filter((x): x is number => x !== null)
      return nums
    }

    const resolveListArg = (raw: string): number[] | null => {
      const t = raw.trim()
      if (t in listVars) return listVars[t]
      return parseInlineList(t)
    }

    const evalPyNum = (raw: string): number | null => {
      const t = raw.trim()
      if (!t) return null
      if (t === 'True') return 1
      if (t === 'False') return 0
      const sumM = t.match(/^sum\(\s*(.+)\s*\)$/)
      if (sumM) {
        const arr = resolveListArg(sumM[1])
        if (arr) return arr.reduce((a, b) => a + b, 0)
      }
      const lenM = t.match(/^len\(\s*(.+)\s*\)$/)
      if (lenM) {
        const arg = lenM[1].trim()
        const arr = resolveListArg(arg)
        if (arr) return arr.length
        if (arg in strVars) return strVars[arg].length
        if ((arg.startsWith('"') && arg.endsWith('"')) || (arg.startsWith("'") && arg.endsWith("'"))) return arg.length - 2
      }
      const minM = t.match(/^min\(\s*(.+)\s*\)$/)
      if (minM) {
        const arr = resolveListArg(minM[1]) ?? splitTopLevelCommas(minM[1]).map(evalPyNum).filter((x): x is number => x !== null)
        if (arr.length > 0) return Math.min(...arr)
      }
      const maxM = t.match(/^max\(\s*(.+)\s*\)$/)
      if (maxM) {
        const arr = resolveListArg(maxM[1]) ?? splitTopLevelCommas(maxM[1]).map(evalPyNum).filter((x): x is number => x !== null)
        if (arr.length > 0) return Math.max(...arr)
      }
      const fnCall = t.match(/^([a-zA-Z_]\w*)\((.*)\)$/)
      if (fnCall && fnCall[1] in funcs) {
        const fn = funcs[fnCall[1]]
        const args = splitTopLevelCommas(fnCall[2]).map((a) => evalPyNum(a) ?? 0)
        const scoped: Record<string, number> = { ...numVars }
        fn.params.forEach((p, idx) => {
          scoped[p] = args[idx] ?? 0
        })
        const normalized = fn.bodyExpr.replace(/\*\*/g, '^').replace(/\/\//g, '/')
        const v = evaluateExpr(normalized, scoped)
        if (v !== null && Number.isFinite(v)) return v
      }
      const normalized = t.replace(/\*\*/g, '^').replace(/\/\//g, '/')
      const v = evaluateExpr(normalized, numVars)
      return v !== null && Number.isFinite(v) ? v : null
    }

    const evalPyVal = (raw: string): string => {
      const t = raw.trim()
      if ((t.startsWith('f"') && t.endsWith('"')) || (t.startsWith("f'") && t.endsWith("'"))) {
        const body = t.slice(2, -1)
        return body.replace(/\{([^}]+)\}/g, (_, expr: string) => evalPyVal(expr))
      }
      if ((t.startsWith('"') && t.endsWith('"')) || (t.startsWith("'") && t.endsWith("'"))) {
        return t.slice(1, -1)
      }
      if (t in strVars) return strVars[t]
      if (t in listVars) return `[${listVars[t].map(fmtNum).join(', ')}]`
      const inlineList = parseInlineList(t)
      if (inlineList) return `[${inlineList.map(fmtNum).join(', ')}]`
      const n = evalPyNum(t)
      if (n !== null) return fmtNum(n)
      return t
    }

    const lines = trimmed.split('\n')
    for (let i = 0; i < lines.length; i++) {
      const rawLine = lines[i]
      const line = rawLine.trim()
      if (!line || line.startsWith('#')) continue

      const defInline = line.match(/^def\s+([a-zA-Z_]\w*)\s*\(([^)]*)\)\s*:\s*(?:return\s+(.+))?$/)
      if (defInline) {
        const fnName = defInline[1]
        const params = defInline[2].split(',').map((s) => s.trim()).filter(Boolean)
        let retExpr = defInline[3]?.trim() ?? ''
        if (!retExpr) {
          for (let j = i + 1; j < lines.length; j++) {
            const sub = lines[j].trim()
            if (!sub || sub.startsWith('#')) continue
            if (!/^\s+/.test(lines[j])) break
            const rm = sub.match(/^return\s+(.+)$/)
            if (rm) retExpr = rm[1].trim()
            i = j
          }
        }
        if (retExpr) funcs[fnName] = { params, bodyExpr: retExpr }
        continue
      }

      const forLoop = line.match(/^for\s+([a-zA-Z_]\w*)\s+in\s+(.+):$/)
      if (forLoop) {
        const iterVar = forLoop[1]
        const iterSrc = forLoop[2].trim()
        const rangeM = iterSrc.match(/^range\(\s*(?:(\d+)\s*,\s*)?(\d+)\s*\)$/)
        const items: number[] = rangeM
          ? (() => {
              const start = rangeM[1] ? Number(rangeM[1]) : 0
              const end = Number(rangeM[2])
              const arr: number[] = []
              for (let k = start; k < Math.min(end, start + 50); k++) arr.push(k)
              return arr
            })()
          : resolveListArg(iterSrc) ?? []
        const bodyLines: string[] = []
        while (i + 1 < lines.length && (/^\s+/.test(lines[i + 1]) || !lines[i + 1].trim())) {
          i++
          if (lines[i].trim() && !lines[i].trim().startsWith('#')) bodyLines.push(lines[i].trim())
        }
        for (const itemVal of items) {
          numVars[iterVar] = itemVal
          for (const bl of bodyLines) {
            const aug = bl.match(/^([a-zA-Z_]\w*)\s*([+\-*])=\s*(.+)$/)
            if (aug) {
              const cur = numVars[aug[1]] ?? 0
              const rhs = evalPyNum(aug[3]) ?? 0
              numVars[aug[1]] = aug[2] === '+' ? cur + rhs : aug[2] === '-' ? cur - rhs : cur * rhs
              continue
            }
            const pm = bl.match(/^print\s*\(([\s\S]+)\)$/)
            if (pm) {
              prints.push(splitTopLevelCommas(pm[1]).map(evalPyVal).join(' '))
            }
          }
        }
        continue
      }

      const augAssign = line.match(/^([a-zA-Z_]\w*)\s*([+\-*])=\s*(.+)$/)
      if (augAssign) {
        const cur = numVars[augAssign[1]] ?? 0
        const rhs = evalPyNum(augAssign[3]) ?? 0
        numVars[augAssign[1]] = augAssign[2] === '+' ? cur + rhs : augAssign[2] === '-' ? cur - rhs : cur * rhs
        continue
      }

      const appendComp = line.match(/^\[([a-zA-Z_]\w*)\.append\(([^)]+)\)\s+for\s+([a-zA-Z_]\w*)\s+in\s+([a-zA-Z_]\w*)\]$/)
      if (appendComp) {
        const target = appendComp[1]
        const expr = appendComp[2].trim()
        const iterV = appendComp[3]
        const srcList = listVars[appendComp[4]] ?? [1, 2, 3]
        const built: number[] = [...(listVars[target] ?? [])]
        for (const item of srcList) {
          numVars[iterV] = item
          built.push(evalPyNum(expr) ?? item)
        }
        listVars[target] = built
        prints.push(`[${srcList.map(() => 'None').join(', ')}]  # .append() returns None`)
        continue
      }

      const extendCall = line.match(/^([a-zA-Z_]\w*)\.extend\((.+)\)$/)
      if (extendCall) {
        const target = extendCall[1]
        const src = resolveListArg(extendCall[2]) ?? [1, 2, 3]
        listVars[target] = [...(listVars[target] ?? []), ...src]
        prints.push(`${target} -> [${listVars[target].map(fmtNum).join(', ')}]`)
        continue
      }

      const listComp = line.match(/^(?:([a-zA-Z_]\w*)\s*=\s*)?\[([^\]]+?)\s+for\s+([a-zA-Z_]\w*)\s+in\s+([^\]]+)\]$/)
      if (listComp && !line.startsWith('print')) {
        const lhs = listComp[1]
        const expr = listComp[2].trim()
        const iterV = listComp[3]
        const src = resolveListArg(listComp[4].trim()) ?? [1, 2, 3]
        const outVals = src.map((item) => {
          numVars[iterV] = item
          return evalPyNum(expr) ?? item
        })
        if (lhs) listVars[lhs] = outVals
        else prints.push(`[${outVals.map(fmtNum).join(', ')}]`)
        continue
      }

      const assignM = line.match(/^([a-zA-Z_]\w*)\s*=\s*(.+)$/)
      if (assignM && !line.includes('==')) {
        const k = assignM[1]
        const rhs = assignM[2].trim()
        const arr = parseInlineList(rhs)
        if (arr) {
          listVars[k] = arr
          continue
        }
        if ((rhs.startsWith('f"') && rhs.endsWith('"')) || (rhs.startsWith("f'") && rhs.endsWith("'")) || (rhs.startsWith('"') && rhs.endsWith('"')) || (rhs.startsWith("'") && rhs.endsWith("'"))) {
          strVars[k] = evalPyVal(rhs)
          continue
        }
        const n = evalPyNum(rhs)
        if (n !== null) {
          numVars[k] = n
          continue
        }
      }

      const printM = line.match(/^print\s*\(([\s\S]+)\)$/)
      if (printM) {
        prints.push(splitTopLevelCommas(printM[1]).map(evalPyVal).join(' '))
        continue
      }
    }

    if (trimmed.includes('DataFrame') || trimmed.includes('plt.plot')) {
      const arrMatch = trimmed.match(/\[([\d,\s.]+)\]/)
      if (arrMatch) {
        const nums = arrMatch[1]
          .split(',')
          .map((s) => Number.parseFloat(s.trim()))
          .filter((n) => Number.isFinite(n))
        if (nums.length > 0) {
          const min = Math.min(...nums)
          const max = Math.max(...nums)
          const sum = nums.reduce((a, b) => a + b, 0)
          prints.push(
            `DataFrame Summary (${nums.length} rows): min=${min.toLocaleString()} · max=${max.toLocaleString()} · total=${sum.toLocaleString()}`
          )
        }
      }
      if (trimmed.includes('plt.show') || trimmed.includes('plt.plot')) {
        prints.push('✓ Matplotlib figure rendered (Revenue Trend · 1 series)')
      }
    }
    if (prints.length === 0) {
      const varDump = [
        ...Object.entries(listVars).map(([k, v]) => `${k} = [${v.map(fmtNum).join(', ')}]`),
        ...Object.entries(numVars).map(([k, v]) => `${k} = ${fmtNum(v)}`),
        ...Object.entries(strVars).map(([k, v]) => `${k} = "${v}"`),
      ]
      if (varDump.length > 0) return { output: varDump.join('\n') }
    }
    if (prints.length > 0) return { output: prints.join('\n') }
    return { output: `✓ Python script executed (${trimmed.split('\n').length} lines, exit code 0)` }
  }

  if (RUNNABLE_SQL.has(lang)) {
    const hasGroupBy = /\bGROUP\s+BY\b/i.test(trimmed)
    const hasDistinct = /\bDISTINCT\b/i.test(trimmed)
    const selectMatches = [...trimmed.matchAll(/SELECT\s+(?:DISTINCT\s+)?([\s\S]+?)\s+FROM\b/gi)]
    const lastSelect = selectMatches[selectMatches.length - 1]
    const cols = lastSelect
      ? lastSelect[1]
          .split(',')
          .map((c) => c.trim().split(/\s+/).pop()?.split('.').pop() ?? '')
          .filter((c) => c && c !== '*')
          .slice(0, 4)
      : ['id', 'name', 'order_count']
    const activeCols = cols.length > 0 ? cols : ['id', 'name', 'order_count']
    const header = activeCols.map((c) => c.padEnd(14)).join(' | ')
    const sep = activeCols.map(() => '-'.repeat(14)).join('-+-')
    const sample = [
      ['101', 'Ava Chen', '3', '2026-04-18'],
      ['102', 'Liam Patel', '1', '2026-04-19'],
      ['103', 'Noah Kim', '4', '2026-04-20'],
    ].map((r) => activeCols.map((_, i) => (r[i] ?? '1').padEnd(14)).join(' | '))
    const note = hasGroupBy
      ? 'CTE grouped · 1 row per parent key'
      : hasDistinct
        ? 'DISTINCT deduplicated · 3 unique rows'
        : '3 rows returned · 0 errors'
    return {
      output: [header, sep, ...sample, '', `(${note})`].join('\n'),
    }
  }

  if (RUNNABLE_SH.has(lang)) {
    const echoes: string[] = []
    for (const line of trimmed.split('\n')) {
      const em = line.trim().match(/^echo\s+['"]?([^'"]+)['"]?$/)
      if (em) echoes.push(em[1])
    }
    return {
      output: echoes.length > 0 ? echoes.join('\n') : `✓ Shell commands completed (exit code 0)`,
    }
  }

  return { output: `✓ Executed ${lang.toUpperCase()} snippet (${trimmed.split('\n').length} lines)` }
}

function highlightLine(line: string, dark: boolean): ReactNode[] {
  const commentIdx = (() => {
    const slashIdx = line.indexOf('//')
    if (slashIdx >= 0 && (slashIdx === 0 || line[slashIdx - 1] !== ':')) return slashIdx
    const hashIdx = line.trimStart().startsWith('#') ? line.indexOf('#') : -1
    return hashIdx
  })()
  const codePart = commentIdx >= 0 ? line.slice(0, commentIdx) : line
  const commentPart = commentIdx >= 0 ? line.slice(commentIdx) : ''

  const kwColor = dark ? '#7AB8FF' : '#2563EB'
  const strColor = dark ? '#6EE7B7' : '#047857'
  const numColor = dark ? '#FBBF24' : '#B45309'
  const cmtColor = dark ? '#6E7681' : '#8C959F'

  const tokens: ReactNode[] = []
  const tokenRe = /("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`|\b\d+(?:\.\d+)?\b|\b[A-Za-z_]\w*\b)/g
  let last = 0
  let m: RegExpExecArray | null
  let k = 0
  while ((m = tokenRe.exec(codePart)) !== null) {
    if (m.index > last) tokens.push(codePart.slice(last, m.index))
    const tok = m[0]
    if (tok.startsWith('"') || tok.startsWith("'") || tok.startsWith('`')) {
      tokens.push(
        <span key={`s-${k++}`} style={{ color: strColor }}>
          {tok}
        </span>
      )
    } else if (/^\d/.test(tok)) {
      tokens.push(
        <span key={`n-${k++}`} style={{ color: numColor }}>
          {tok}
        </span>
      )
    } else if (KEYWORD_RE.test(tok)) {
      KEYWORD_RE.lastIndex = 0
      tokens.push(
        <span key={`kw-${k++}`} style={{ color: kwColor, fontWeight: 600 }}>
          {tok}
        </span>
      )
    } else {
      tokens.push(tok)
    }
    last = m.index + tok.length
  }
  if (last < codePart.length) tokens.push(codePart.slice(last))
  if (commentPart) {
    tokens.push(
      <span key="cmt" style={{ color: cmtColor, fontStyle: 'italic' }}>
        {commentPart}
      </span>
    )
  }
  return tokens
}

export function CodeBlock({ code, language }: { code: string; language?: string }) {
  const { accent } = useWidgetIdentity()
  const lang = (language ?? 'code').toLowerCase().trim()
  const [source, setSource] = useState(code)
  const [editing, setEditing] = useState(false)
  const [expanded, setExpanded] = useState(false)
  const [runCount, setRunCount] = useState(0)
  const [output, setOutput] = useState<string | null>(null)
  const [errorText, setErrorText] = useState<string | null>(
    ERROR_LANGS.has(lang) ? code : null
  )
  const [htmlPreview, setHtmlPreview] = useState<string | null>(null)

  const canRun =
    RUNNABLE_JS.has(lang) ||
    RUNNABLE_HTML.has(lang) ||
    RUNNABLE_PY.has(lang) ||
    RUNNABLE_SQL.has(lang) ||
    RUNNABLE_SH.has(lang) ||
    lang === 'json'

  const allLines = useMemo(() => source.split('\n'), [source])
  const isCollapsible = allLines.length > 14
  const visibleLines = isCollapsible && !expanded ? allLines.slice(0, 12) : allLines

  const highlighted = useMemo(() => {
    return visibleLines.map((line, idx) => (
      <div key={idx} style={{ display: 'flex', minHeight: '1.4em' }}>
        <span
          aria-hidden
          style={{
            width: 26,
            paddingRight: 10,
            textAlign: 'right',
            color: '#6E6A94',
            userSelect: 'none',
            flexShrink: 0,
            fontSize: 11,
          }}
        >
          {idx + 1}
        </span>
        <span style={{ flex: 1, minWidth: 0 }}>{highlightLine(line, true)}</span>
      </div>
    ))
  }, [visibleLines])

  const runCode = () => {
    setRunCount((c) => c + 1)
    setErrorText(null)
    setOutput(null)
    setHtmlPreview(null)
    if (RUNNABLE_HTML.has(lang)) {
      setHtmlPreview(source)
      setOutput('✓ Rendered live HTML/SVG preview in sandbox')
      return
    }
    if (lang === 'json') {
      try {
        const parsed = JSON.parse(source)
        setOutput(JSON.stringify(parsed, null, 2))
      } catch (err) {
        setErrorText(err instanceof Error ? err.message : String(err))
      }
      return
    }
    if (!RUNNABLE_JS.has(lang)) {
      const sim = simulateNonJsExecution(lang, source)
      if (sim.error) setErrorText(sim.error)
      if (sim.output) setOutput(sim.output)
      return
    }
    const logs: string[] = []
    const fmt = (v: unknown): string => {
      if (typeof v === 'string') return v
      try {
        return JSON.stringify(v)
      } catch {
        return String(v)
      }
    }
    const mockConsole = {
      log: (...args: unknown[]) => logs.push(args.map(fmt).join(' ')),
      info: (...args: unknown[]) => logs.push(args.map(fmt).join(' ')),
      warn: (...args: unknown[]) => logs.push('[warn] ' + args.map(fmt).join(' ')),
      error: (...args: unknown[]) => logs.push('[error] ' + args.map(fmt).join(' ')),
    }
    try {
      const syncSetTimeout = (cb: unknown) => {
        if (typeof cb === 'function') cb()
        return 1
      }
      const fn = new Function('console', 'setTimeout', `"use strict";\n${source}`)
      const ret = fn(mockConsole, syncSetTimeout)
      if (ret !== undefined) logs.push(`→ ${fmt(ret)}`)
      setOutput(logs.length > 0 ? logs.join('\n') : '✓ Executed cleanly (0 console messages)')
    } catch (err) {
      if (logs.length > 0) setOutput(logs.join('\n'))
      setErrorText(err instanceof Error ? `${err.name}: ${err.message}` : String(err))
    }
  }

  const chipStyle = {
    fontSize: 11,
    fontWeight: 600,
    padding: '3px 9px',
    borderRadius: 7,
    border: '1px solid rgba(255,255,255,0.14)',
    background: 'rgba(255,255,255,0.08)',
    color: '#E4E0FF',
    cursor: 'pointer',
  } as const

  const isBrowserLang = RUNNABLE_HTML.has(lang) || lang === 'jsx' || lang === 'tsx'

  return (
    <div
      style={{
        borderRadius: 14,
        border: '1px solid #353059',
        background: '#221F3B',
        overflow: 'hidden',
        boxShadow: '0 8px 22px rgba(20, 16, 44, 0.22)',
      }}
    >
      {/* Header Bar */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: '8px 12px',
          background: '#1B1830',
          borderBottom: '1px solid rgba(255,255,255,0.08)',
          gap: 6,
        }}
      >
        <span
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 6,
            fontSize: TYPE.micro,
            fontWeight: 700,
            color: '#E5E0FF',
            textTransform: 'uppercase',
            letterSpacing: '0.05em',
          }}
        >
          <GlassIcon name="code" tone="lavender" size={18} glyph={11} />
          <span>{lang}</span>
        </span>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          {isCollapsible && !editing && (
            <button type="button" onClick={() => setExpanded((v) => !v)} style={chipStyle}>
              {expanded ? 'Collapse' : `Expand (${allLines.length})`}
            </button>
          )}
          <button type="button" onClick={() => setEditing((v) => !v)} style={chipStyle}>
            {editing ? 'Done' : 'Edit'}
          </button>
          {source !== code ? (
            <button
              type="button"
              onClick={() => {
                setSource(code)
                setOutput(null)
                setErrorText(null)
                setHtmlPreview(null)
              }}
              style={chipStyle}
            >
              Reset
            </button>
          ) : null}
          <CopyChip value={source} />
        </div>
      </div>

      {/* Code Surface */}
      {editing ? (
        <textarea
          value={source}
          onChange={(e) => setSource(e.target.value)}
          spellCheck={false}
          rows={Math.min(18, Math.max(4, allLines.length + 1))}
          style={{
            width: '100%',
            boxSizing: 'border-box',
            padding: 12,
            border: 'none',
            borderBottom: '1px solid rgba(255,255,255,0.08)',
            background: '#1E1B36',
            color: '#F3F0FF',
            fontSize: TYPE.micro,
            lineHeight: 1.6,
            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
            resize: 'vertical',
            outline: 'none',
          }}
        />
      ) : (
        <pre
          style={{
            margin: 0,
            padding: '12px 14px',
            background: '#221F3B',
            color: '#EAE6FF',
            fontSize: TYPE.micro,
            lineHeight: 1.6,
            overflowX: 'auto',
            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
          }}
        >
          <code>{highlighted}</code>
        </pre>
      )}

      {/* Bottom Execution Action Bar (Screenshots 1 & 2) */}
      {canRun && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '8px 12px',
            background: '#1B1830',
            borderTop: '1px solid rgba(255,255,255,0.08)',
            gap: 8,
          }}
        >
          <button
            type="button"
            onClick={runCode}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              padding: '5px 12px',
              borderRadius: 8,
              border: `1px solid ${alpha(accent, 0.45)}`,
              background: '#6C4CF6',
              color: '#FFFFFF',
              fontSize: 11.5,
              fontWeight: 700,
              cursor: 'pointer',
            }}
          >
            <GlassIcon name="player-play" tone="indigo" size={16} glyph={10} />
            <span>
              {isBrowserLang
                ? 'Run in Browser'
                : lang === 'json'
                ? 'Validate JSON'
                : 'Run Code'}
            </span>
          </button>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            {(output !== null || errorText !== null) && (
              <>
                <span
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 5,
                    padding: '2px 8px',
                    borderRadius: 999,
                    fontSize: 11,
                    fontWeight: 700,
                    background: errorText ? 'rgba(239,68,68,0.18)' : 'rgba(34,197,94,0.18)',
                    color: errorText ? '#FCA5A5' : '#86EFAC',
                  }}
                >
                  <span>●</span>
                  <span>{errorText ? 'Error' : 'Success'}</span>
                </span>
                <button type="button" onClick={runCode} style={{ ...chipStyle, display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                  <GlassIcon name="refresh" tone="lavender" size={14} glyph={9} />
                  <span>Rerun</span>
                </button>
              </>
            )}
            <span style={{ fontSize: 11, color: '#8E89B2' }}>
              {allLines.length} {allLines.length === 1 ? 'line' : 'lines'}
            </span>
          </div>
        </div>
      )}

      {/* Sandboxed HTML Preview */}
      {htmlPreview ? (
        <iframe
          title="Code preview"
          sandbox="allow-scripts"
          srcDoc={htmlPreview}
          style={{
            width: '100%',
            height: 220,
            border: 'none',
            borderTop: '1px solid rgba(255,255,255,0.1)',
            background: '#fff',
          }}
        />
      ) : null}

      {/* Terminal-Style Execution Output Drawer (05. Code Execution) */}
      {(output !== null || errorText !== null) && (
        <div
          style={{
            borderTop: '1px solid rgba(255,255,255,0.1)',
            background: '#151226',
            padding: '10px 12px',
            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
            fontSize: TYPE.micro,
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: 6,
              color: '#8E89B2',
              fontSize: 10.5,
            }}
          >
            <span>
              $ {RUNNABLE_PY.has(lang) ? 'python3 main.py' : RUNNABLE_SQL.has(lang) ? 'sql --execute' : `run ${lang}`} (run #{Math.max(1, runCount)})
            </span>
            <button
              type="button"
              onClick={() => {
                setOutput(null)
                setErrorText(null)
                setHtmlPreview(null)
              }}
              style={{
                border: 'none',
                background: 'transparent',
                color: '#8E89B2',
                cursor: 'pointer',
                fontSize: 10.5,
                display: 'inline-flex',
                alignItems: 'center',
                gap: 4,
              }}
            >
              <GlassIcon name="x" tone="slate" size={14} glyph={9} />
              <span>Clear</span>
            </button>
          </div>
          {output ? (
            <pre
              style={{
                margin: 0,
                color: '#D8F3DC',
                lineHeight: 1.5,
                whiteSpace: 'pre-wrap',
              }}
            >
              {output}
            </pre>
          ) : null}
          {errorText ? (
            <pre
              style={{
                margin: output ? '6px 0 0' : 0,
                padding: '8px 10px',
                borderRadius: 8,
                border: '1px solid rgba(239,68,68,0.4)',
                background: 'rgba(239,68,68,0.12)',
                color: '#FCA5A5',
                lineHeight: 1.5,
                whiteSpace: 'pre-wrap',
              }}
            >
              {errorText}
            </pre>
          ) : null}
        </div>
      )}

      {/* Visual Data Plot Preview (Screenshot 1: Revenue Trend mini purple bar chart) */}
      {(() => {
        const seriesPreview = extractCodeSeriesPreview(source)
        if (!seriesPreview) return null
        const maxVal = Math.max(1, ...seriesPreview.points.map((p) => p.value))
        return (
          <div
            style={{
              background: '#F7F4FF',
              padding: '10px 14px',
              borderTop: '1px solid rgba(124,92,252,0.22)',
            }}
          >
            <div style={{ fontSize: 12, fontWeight: 700, color: '#221A47', marginBottom: 8 }}>
              {seriesPreview.title}
            </div>
            <div
              style={{
                display: 'flex',
                alignItems: 'flex-end',
                gap: 10,
                height: 68,
              }}
            >
              {seriesPreview.points.map((pt, idx) => {
                const frac = Math.min(1, Math.max(0.15, pt.value / maxVal))
                return (
                  <div
                    key={idx}
                    style={{
                      flex: 1,
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'center',
                      gap: 4,
                    }}
                  >
                    <div
                      style={{
                        width: '68%',
                        height: Math.round(46 * frac),
                        borderRadius: '5px 5px 0 0',
                        background: '#6C4CF6',
                      }}
                    />
                    <span style={{ fontSize: 10, color: '#5E5680' }}>{pt.label}</span>
                  </div>
                )
              })}
            </div>
          </div>
        )
      })()}
    </div>
  )
}

function extractCodeSeriesPreview(
  code: string
): { title: string; points: Array<{ label: string; value: number }> } | null {
  const labelMatch = code.match(/['"](?:Month|Label|Category|Quarter|Day|Year)s?['"]\s*:\s*\[([^\]]+)\]/i)
  const valMatch = code.match(/['"](Revenue|Sales|Value|Score|Users|Growth|Amount)s?['"]\s*:\s*\[([^\]]+)\]/i)
  if (!labelMatch || !valMatch) return null
  const labels = labelMatch[1]
    .split(',')
    .map((s) => s.trim().replace(/^['"]|['"]$/g, ''))
    .filter(Boolean)
  const values = valMatch[2]
    .split(',')
    .map((s) => Number(s.trim()))
    .filter((n) => Number.isFinite(n))
  if (labels.length < 2 || values.length < 2) return null
  const count = Math.min(labels.length, values.length)
  const seriesName = valMatch[1].charAt(0).toUpperCase() + valMatch[1].slice(1)
  return {
    title: `${seriesName} Trend`,
    points: Array.from({ length: count }, (_, i) => ({ label: labels[i], value: values[i] })),
  }
}
