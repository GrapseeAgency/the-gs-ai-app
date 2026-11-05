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

const KEYWORD_RE =
  /\b(const|let|var|function|return|if|else|for|while|switch|case|break|continue|new|class|extends|import|from|export|default|async|await|try|catch|finally|throw|typeof|instanceof|in|of|def|elif|lambda|yield|with|as|pass|raise|True|False|None|fun|val|when|object|interface|struct|enum|guard|protocol|impl|pub|fn|match|mut|use|SELECT|FROM|WHERE|JOIN|LEFT|RIGHT|INNER|GROUP|BY|ORDER|INSERT|INTO|VALUES|UPDATE|SET|DELETE|CREATE|TABLE|LIMIT)\b/g

const RUNNABLE_JS = new Set(['js', 'javascript', 'ts', 'typescript', 'jsx', 'tsx', 'node'])
const RUNNABLE_HTML = new Set(['html', 'htm', 'svg'])
const ERROR_LANGS = new Set(['error', 'stderr', 'traceback', 'stacktrace', 'exception'])

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
  const { accent, theme } = useWidgetIdentity()
  const lang = (language ?? 'code').toLowerCase().trim()
  const [source, setSource] = useState(code)
  const [editing, setEditing] = useState(false)
  const [output, setOutput] = useState<string | null>(null)
  const [errorText, setErrorText] = useState<string | null>(
    ERROR_LANGS.has(lang) ? code : null
  )
  const [htmlPreview, setHtmlPreview] = useState<string | null>(null)

  const canRun = RUNNABLE_JS.has(lang) || RUNNABLE_HTML.has(lang) || lang === 'json'

  const highlighted = useMemo(() => {
    const lines = source.split('\n')
    return lines.map((line, idx) => (
      <div key={idx} style={{ minHeight: '1.35em' }}>
        {highlightLine(line, theme.dark)}
      </div>
    ))
  }, [source, theme.dark])

  const runCode = () => {
    setErrorText(null)
    setOutput(null)
    setHtmlPreview(null)
    if (RUNNABLE_HTML.has(lang)) {
      setHtmlPreview(source)
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
      const fn = new Function('console', `"use strict";\n${source}`)
      const ret = fn(mockConsole)
      if (ret !== undefined) logs.push(`→ ${fmt(ret)}`)
      setOutput(logs.length > 0 ? logs.join('\n') : '(executed with no console output)')
    } catch (err) {
      if (logs.length > 0) setOutput(logs.join('\n'))
      setErrorText(err instanceof Error ? `${err.name}: ${err.message}` : String(err))
    }
  }

  const chipStyle = {
    fontSize: 11,
    fontWeight: 600,
    padding: '2px 8px',
    borderRadius: 6,
    border: `1px solid ${theme.border}`,
    background: theme.raised,
    color: theme.text2,
    cursor: 'pointer',
  } as const

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6, gap: 6 }}>
        <span style={{ fontSize: TYPE.micro, fontWeight: 700, color: theme.text3, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
          {lang}
        </span>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
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
          {canRun ? (
            <button
              type="button"
              onClick={runCode}
              style={{
                ...chipStyle,
                background: alpha(accent, 0.16),
                color: accent,
                borderColor: alpha(accent, 0.35),
              }}
            >
              {RUNNABLE_HTML.has(lang) ? 'Preview' : lang === 'json' ? 'Validate' : 'Run'}
            </button>
          ) : null}
          <CopyChip value={source} />
        </div>
      </div>
      {editing ? (
        <textarea
          value={source}
          onChange={(e) => setSource(e.target.value)}
          spellCheck={false}
          rows={Math.min(18, Math.max(4, source.split('\n').length + 1))}
          style={{
            width: '100%',
            boxSizing: 'border-box',
            padding: 12,
            borderRadius: RADIUS.md,
            border: `1px solid ${accent}`,
            background: theme.dark ? 'rgba(0,0,0,0.4)' : 'rgba(0,0,0,0.03)',
            color: theme.text,
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
            padding: 14,
            borderRadius: RADIUS.md,
            border: `1px solid ${theme.border}`,
            background: theme.dark ? 'rgba(0,0,0,0.32)' : 'rgba(0,0,0,0.03)',
            color: theme.text2,
            fontSize: TYPE.micro,
            lineHeight: 1.6,
            overflowX: 'auto',
            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
          }}
        >
          <code>{highlighted}</code>
        </pre>
      )}
      {htmlPreview ? (
        <iframe
          title="Code preview"
          sandbox="allow-scripts"
          srcDoc={htmlPreview}
          style={{
            width: '100%',
            height: 220,
            marginTop: 8,
            borderRadius: RADIUS.md,
            border: `1px solid ${theme.border}`,
            background: '#fff',
          }}
        />
      ) : null}
      {output ? (
        <pre
          style={{
            margin: '8px 0 0',
            padding: '10px 12px',
            borderRadius: RADIUS.md,
            border: `1px solid ${theme.border}`,
            background: theme.raised,
            color: theme.text,
            fontSize: TYPE.micro,
            lineHeight: 1.5,
            whiteSpace: 'pre-wrap',
            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
          }}
        >
          {output}
        </pre>
      ) : null}
      {errorText ? (
        <div
          style={{
            marginTop: 8,
            padding: '9px 12px',
            borderRadius: RADIUS.md,
            border: '1px solid rgba(224,49,49,0.4)',
            background: 'rgba(224,49,49,0.10)',
            color: '#E03131',
            fontSize: TYPE.micro,
            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
            whiteSpace: 'pre-wrap',
          }}
        >
          {errorText}
        </div>
      ) : null}
    </div>
  )
}
