import { expect, test } from 'bun:test'
import { needsQualityChain, contextualReasoningRoute } from './quality-routing'
const ordinary = {route:'TEXT_SIMPLE',requestedMode:'flash',effectiveMode:'flash',registerClass:false,hasHistory:false,hasConstraints:false,content:'Hello'}
test('lightweight free chain is only for standalone simple chat', () => {
  expect(needsQualityChain(ordinary)).toBe(false)
  for (const change of [{hasHistory:true},{requestedMode:'thinking'},{effectiveMode:'thinking'},{hasConstraints:true},{registerClass:true},{route:'CODING'},{content:'x'.repeat(241)}])
    expect(needsQualityChain({...ordinary,...change})).toBe(true)
})

test('auto deepens contextual requests without overriding explicit flash or social chat',()=>{
  const base={route:'TEXT_SIMPLE',requestedMode:'auto',hasHistory:true,hasConstraints:false,content:'Continue with the same format.'}
  expect(contextualReasoningRoute(base)).toBe('TEXT_COMPLEX')
  expect(contextualReasoningRoute({...base,requestedMode:'flash'})).toBe('TEXT_SIMPLE')
  expect(contextualReasoningRoute({...base,hasHistory:false})).toBe('TEXT_SIMPLE')
  expect(contextualReasoningRoute({...base,content:'Thanks'})).toBe('TEXT_SIMPLE')
  expect(contextualReasoningRoute({...base,content:'চালিয়ে যাও'})).toBe('TEXT_COMPLEX')
})
