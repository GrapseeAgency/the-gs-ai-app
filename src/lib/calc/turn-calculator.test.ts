import { describe, expect, test } from 'bun:test'
import { explicitCalculation, executeCalculation, calculationEvidence } from './turn-calculator'
describe('read-only calculation tool', () => {
  test('executes real arithmetic with precedence', () => {
    expect(executeCalculation('(1250*0.18)+37')).toEqual({expression:'(1250*0.18)+37',ok:true,value:262})
    expect(executeCalculation('sqrt(144)+2^3')).toEqual({expression:'sqrt(144)+2^3',ok:true,value:20})
  })
  test('rejects arbitrary code, prototype names and unrepresentable results', () => {
    for (const expression of ['process.exit(0)', 'constructor(1)', 'toString()', '1/0', '2^100', 'a=1', '1+'.repeat(200)])
      expect(executeCalculation(expression).ok).toBe(false)
  })
  test('direct arithmetic excludes ordinary prose and ambiguous notation', () => {
    expect(explicitCalculation('Calculate 1250*0.18 + 37?')).toBe('1250*0.18 + 37')
    for (const text of ['2026-11-24', '20%', '10-20', 'Write a function for 2+2', 'hello'])
      expect(explicitCalculation(text)).toBeNull()
  })
  test('failed operations are explicitly marked', () => {
    expect(calculationEvidence([executeCalculation('1/0')])).toContain('"ok":false')
  })
})
