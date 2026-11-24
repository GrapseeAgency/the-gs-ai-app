import { describe, expect, test } from 'bun:test'
import { projectHistory, type HistoryRow } from './context-crystallize'

const row = (id: string, role: string, content: string): HistoryRow => ({ id, role, content })
const recent = Array.from({ length: 8 }, (_, i) => row('recent' + i, i % 2 ? 'assistant' : 'user', 'recent ' + 'detail '.repeat(650)))

describe('conversation continuity', () => {
  test('short turns are left untouched', () => {
    expect(projectHistory([row('a', 'user', 'Remember the address.')], 'continue').block).toBeNull()
  })
  test('older constraints and relevant facts retain attribution and exact source text', () => {
    const history = [
      row('a', 'user', 'Rule: use Bengali, do not use bullet lists. The budget is $399.50 for rent only.'),
      row('b', 'assistant', 'The budget is $500 for groceries.'),
      row('c', 'user', 'Correction: rent is $420.75, not $399.50.'),
      ...recent,
    ]
    const result = projectHistory(history, 'continue')
    expect(result.keepFromIndex).toBeGreaterThan(0)
    expect(result.block).toContain('Rule: use Bengali, do not use bullet lists.')
    expect(result.block).toContain('Correction: rent is $420.75, not $399.50.')
    expect(result.block!.indexOf('Rule:')).toBeLessThan(result.block!.indexOf('Correction:'))
    expect(result.block).toContain('newer user corrections supersede older ones')
  })
  test('old long assistant passage is clearly partial and budget remains bounded', () => {
    const history = [row('a', 'assistant', 'prefix '.repeat(900) + 'Unique reference: ZXCV9876 costs €12.50. ' + 'suffix '.repeat(900)), ...recent]
    const result = projectHistory(history, 'ZXCV9876')
    expect(result.block).toContain('ZXCV9876 costs €12.50')
    expect(result.block).toContain('"partial":true')
    expect(result.block!.length).toBeLessThan(12500)
    expect(result.recovered.length).toBeGreaterThan(0)
  })
  test('irrelevant long user turns cannot starve a matched earlier answer', () => {
    const history = [
      row('old-answer', 'assistant', 'The confirmed LUMEN8831 deployment target is the staging cluster.'),
      ...Array.from({ length: 12 }, (_, i) => row('long-user-' + i, 'user', 'Unrelated notebook discussion. ' + 'paper '.repeat(500))),
      ...recent,
    ]
    const result = projectHistory(history, 'LUMEN8831')
    expect(result.block).toContain('LUMEN8831 deployment target is the staging cluster')
    expect(result.block!.length).toBeLessThan(12500)
    const archived = result.block!.split('\n').filter(line => line.startsWith('{')).map(line => JSON.parse(line))
    expect(archived.filter(item => item.text.includes('LUMEN8831')).length).toBe(1)
    expect(archived.every((item, i) => i === 0 || archived[i-1].turn < item.turn)).toBe(true)
  })
  test('matching escaped source is not discarded because JSON expands it', () => {
    const answer = 'QREF9981: ' + '"\\\n'.repeat(3000) + ' QREF9981 final value is 71.'
    const result = projectHistory([row('a', 'assistant', answer), ...recent], 'QREF9981')
    expect(result.block).toContain('QREF9981')
    expect(result.recovered.length).toBeGreaterThan(0)
    expect(result.block!.length).toBeLessThan(12500)
  })
  test('required historical image turn is not duplicated in archive', () => {
    const result = projectHistory([row('image', 'user', 'image description prompt'), ...recent], 'continue', ['image'])
    expect(result.block).not.toContain('image description prompt')
  })
})
