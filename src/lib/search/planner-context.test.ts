import { expect, test } from 'bun:test'
import { plannerExcerpt, plannerHistory, plannerLatest } from './planner-context'

test('latest request retains final corrections after long pasted text', () => {
  const raw = 'Review this document. ' + 'background '.repeat(500) + 'Correction: do not search. Answer in Bengali.'
  const parsed = JSON.parse(plannerLatest(raw))
  expect(parsed.content).toContain('Review this document.')
  expect(parsed.content).toContain('Correction: do not search. Answer in Bengali.')
  expect(parsed.content).toContain('omitted')
  expect(parsed.content.length).toBeLessThanOrEqual(2000)
})

test('recent turns preserve role and end of long user messages', () => {
  const rows = Array.from({length:10},(_,i)=>({role:i%2?'assistant':'user',content:'Turn '+i+' '+ 'details '.repeat(100)+' FINAL-'+i}))
  const lines = plannerHistory(rows)
  expect(lines.length).toBe(6)
  expect(lines.join('\n').length).toBeLessThanOrEqual(3000)
  expect(JSON.parse(lines[0]).role).toBe('user')
  expect(JSON.parse(lines[0]).content).toContain('FINAL-4')
  expect(JSON.parse(lines[5]).content).toContain('FINAL-9')
})

test('escaped content stays valid structured data and bounded', () => {
  const rows=Array.from({length:6},()=>({role:'user',content:'"\\\n'.repeat(300)+'শেষ নির্দেশ: বাংলায় উত্তর দিন'}))
  const lines=plannerHistory(rows)
  expect(lines.join('\n').length).toBeLessThanOrEqual(3000)
  for(const line of lines) expect(JSON.parse(line).content).toContain('বাংলায় উত্তর দিন')
})

test('short messages remain verbatim and cuts do not split emoji', () => {
  expect(plannerExcerpt('বাংলা 🧪',100)).toBe('বাংলা 🧪')
  const result=plannerExcerpt('😀'.repeat(300),99)
  expect(result.length).toBeLessThanOrEqual(99)
  expect(result.isWellFormed()).toBe(true)
})
