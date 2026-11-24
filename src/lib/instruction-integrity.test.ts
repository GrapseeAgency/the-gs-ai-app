import { expect,test } from 'bun:test'
import { detectOutputDirective, enforceDirective, buildDirectiveMessages, buildDirectiveRetryMessages } from './output-directive'
import { buildConstraintRetryMessages, repairConstraints, honestConstraintFailure } from './constraint-guard'
import { appendSystemInstruction } from './system-instruction'

const messages = [
  {role:'system',content:'Platform contract: never fabricate tool results.'},
  {role:'system',content:'Assistant preferences: reply in Bengali.'},
  {role:'user',content:'Earlier evidence is important.'},
  {role:'assistant',content:'I have noted it.'},
  {role:'user',content:'CURRENT USER REQUEST\nAnswer with exactly five words.'},
]
test('constraint and directive retries preserve every original message and instruction',()=>{
  const token={kind:'token' as const,target:'Blue'}
  for(const result of [
    buildConstraintRetryMessages(messages,{exactWords:5}),
    buildDirectiveMessages(messages,token),
    buildDirectiveRetryMessages(messages,token),
  ]){
    expect(result.length).toBe(messages.length)
    expect(result[0].content).toStartWith(messages[0].content)
    expect(result.slice(1)).toEqual(messages.slice(1))
    expect(messages[0].content).toBe('Platform contract: never fabricate tool results.')
  }
  expect(appendSystemInstruction(messages.slice(2),'Constraint')[1]).toEqual(messages[2])
})
test('word-count compliance cannot be manufactured with filler or deletion',()=>{
  expect(repairConstraints('Not safe for children.',{exactWords:3})).toBeNull()
  expect(repairConstraints('No evidence.',{exactWords:5})).toBeNull()
  expect(repairConstraints('It is not safe.',{forbiddenChars:['n']})).toBeNull()
  expect(repairConstraints('  Two words.  ',{exactWords:2})).toBe('Two words.')
  expect(honestConstraintFailure('Not safe.',{exactWords:3})).toContain('could not satisfy')
  expect(enforceDirective('Not safe.',{kind:'wordCount',target:'3',count:3})).toContain('could not satisfy')
})
test('literal output preserves case and a conditional game requires the actual cue',()=>{
  const d=detectOutputDirective('Say Alpha7 and nothing else.')!
  expect(d.target).toBe('Alpha7')
  expect(enforceDirective('alpha7',d)).toBe('Alpha7')
  expect(enforceDirective('Alpha7',d)).toBeNull()
  expect(detectOutputDirective('If I say red you say Blue. I say green.')).toBeNull()
  expect(detectOutputDirective('If I say red you say Blue.')).toBeNull()
  expect(detectOutputDirective('If I say red you say Blue. I say red.')?.target).toBe('Blue')
})
