import { expect, test } from 'bun:test'
import { needsQualityChain } from './quality-routing'
const ordinary = {route:'TEXT_SIMPLE',requestedMode:'flash',effectiveMode:'flash',registerClass:false,hasHistory:false,hasConstraints:false,content:'Hello'}
test('lightweight free chain is only for standalone simple chat', () => {
  expect(needsQualityChain(ordinary)).toBe(false)
  for (const change of [{hasHistory:true},{requestedMode:'thinking'},{effectiveMode:'thinking'},{hasConstraints:true},{registerClass:true},{route:'CODING'},{content:'x'.repeat(241)}])
    expect(needsQualityChain({...ordinary,...change})).toBe(true)
})
