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
import { HtmlBlock } from './HtmlBlock'
import { GlassIcon } from '@/components/nexly/GlassIcon'
import { arithmeticPreview } from '@/lib/code/arithmetic-preview'
import { javascriptSandboxDocument } from '@/lib/code/javascript-sandbox'
import { escapeCodeDisplay, requiresEncodedEditor, encodeSourceEditor, decodeSourceEditor } from '@/lib/code/display-source'

const KEYWORD_RE =
  /\b(const|let|var|function|return|if|else|for|while|switch|case|break|continue|new|class|extends|import|from|export|default|async|await|try|catch|finally|throw|typeof|instanceof|in|of|def|elif|lambda|yield|with|as|pass|raise|True|False|None|fun|val|when|object|interface|struct|enum|guard|protocol|impl|pub|fn|match|mut|use|SELECT|FROM|WHERE|JOIN|LEFT|RIGHT|INNER|GROUP|BY|ORDER|INSERT|INTO|VALUES|UPDATE|SET|DELETE|CREATE|TABLE|LIMIT)\b/g

const RUNNABLE_JS = new Set(['js', 'javascript', 'ts', 'typescript', 'jsx', 'tsx', 'node'])
const RUNNABLE_HTML = new Set(['html', 'htm', 'svg'])
const RUNNABLE_PY = new Set(['py', 'python', 'python3'])
const RUNNABLE_SQL = new Set(['sql', 'sqlite', 'postgres', 'postgresql', 'mysql'])
const RUNNABLE_SH = new Set(['sh', 'bash', 'zsh', 'shell'])
const ERROR_LANGS = new Set(['error', 'stderr', 'traceback', 'stacktrace', 'exception'])

function previewNonJsExecution(lang: string, source: string): { output: string; error?: string } {
  if (RUNNABLE_PY.has(lang)) return arithmeticPreview(source)
  if (RUNNABLE_SQL.has(lang)) return { output: '', error: 'Not executed: no database is connected. Copy this SQL into your database client.' }
  return { output: '', error: `Not executed: no ${lang.toUpperCase()} runtime is available here.` }
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
  return <CodeControls key={JSON.stringify([code, language])} code={code} language={language} />
}

function CodeControls({ code, language }: { code: string; language?: string }) {
  const { accent } = useWidgetIdentity()
  const lang = (language ?? 'code').toLowerCase().trim()
  const [source, setSource] = useState(code)
  const [editing, setEditing] = useState(false)
  const [editorDraft, setEditorDraft] = useState('')
  const [editorEncoded, setEditorEncoded] = useState(false)
  const [editorError, setEditorError] = useState<string | null>(null)
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
        <span style={{ flex: 1, minWidth: 0 }}>{highlightLine(escapeCodeDisplay(line), true)}</span>
      </div>
    ))
  }, [visibleLines])

  const runCode = () => {
    if (editing && editorError) { setErrorText('Not executed: correct the JSON-encoded source draft first.'); return }
    setRunCount((c) => c + 1)
    setErrorText(null)
    setOutput(null)
    setHtmlPreview(null)
    if (RUNNABLE_HTML.has(lang)) {
      setHtmlPreview(source)
      setOutput('Live HTML/SVG preview opened in sandbox')
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
      const sim = previewNonJsExecution(lang, source)
      if (sim.error) setErrorText(sim.error)
      if (sim.output) setOutput(sim.output)
      return
    }
    if (['ts', 'typescript', 'jsx', 'tsx'].includes(lang)) {
      setErrorText('Not executed: no TypeScript/React compiler is available here. Use a self-contained HTML/JavaScript sandbox for a live preview.')
      return
    }
    setHtmlPreview(javascriptSandboxDocument(source))
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
          <button type="button" onClick={() => {
              if (editing && editorError) return
              if (!editing) {
                const encoded = requiresEncodedEditor(source)
                setEditorEncoded(encoded)
                setEditorDraft(encoded ? encodeSourceEditor(source) : source)
                setEditorError(null)
              }
              setEditing(value => !value)
            }} style={chipStyle}>
            {editing ? 'Done' : 'Edit'}
          </button>
          {source !== code ? (
            <button
              type="button"
              onClick={() => {
                setSource(code)
                const encoded = requiresEncodedEditor(code)
                setEditorEncoded(encoded)
                setEditorDraft(encoded ? encodeSourceEditor(code) : code)
                setEditorError(null)
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
      {requiresEncodedEditor(source) && <div style={{ padding: '6px 12px', fontSize: 11, color: '#C5BCFF' }}>Unicode symbols are shown as escapes; Copy and Run use the original source.</div>}
      {editing && editorEncoded && <div style={{ padding: '6px 12px', fontSize: 11, color: '#C5BCFF' }}>Unicode-safe editor: edit one JSON-encoded source string.</div>}
      {editorError && <div role="alert" style={{ padding: '6px 12px', color: '#FCA5A5' }}>{editorError}</div>}
      {editing ? (
        <textarea
          aria-label={editorEncoded ? 'JSON-encoded source' : 'Source code'}
          value={editorDraft}
          onChange={(e) => {
            const draft = e.target.value
            if (!editorEncoded && requiresEncodedEditor(draft)) {
              setEditorEncoded(true); setEditorDraft(encodeSourceEditor(draft)); setSource(draft); setEditorError(null); return
            }
            setEditorDraft(escapeCodeDisplay(draft))
            try {
              setSource(editorEncoded ? decodeSourceEditor(draft) : draft)
              setEditorError(null)
            } catch { setEditorError('Enter one valid JSON string. The invalid draft has not replaced executable source.') }
          }}
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
      {htmlPreview ? <HtmlBlock source={htmlPreview} fallbackHeight={220} /> : null}

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
              {escapeCodeDisplay(output)}
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
              {escapeCodeDisplay(errorText)}
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
