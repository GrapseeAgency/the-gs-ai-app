import {expect,test} from 'bun:test'
import {ProviderRateLimit,rateLimitDeadline} from './provider-rate-limit'
test('missing Retry-After uses conservative shared cooldown',()=>expect(rateLimitDeadline(null,'HTTP 429',1000)).toBe(61000))
test('seconds and HTTP dates are honored',()=>{
  expect(rateLimitDeadline('120','',1000)).toBe(121000)
  expect(rateLimitDeadline(new Date(181000).toUTCString(),'',1000)).toBe(181000)
})
test('daily quota does not retry before next UTC reset',()=>{
  const now=Date.parse('2026-11-24T16:00:00Z')
  expect(rateLimitDeadline(null,'free-models-per-day',now)).toBe(Date.parse('2026-11-25T00:05:00Z'))
})
test('later observations never shorten a shared cooldown',()=>{
  const limit=new ProviderRateLimit()
  limit.observe('300','',1000)
  limit.observe('30','',2000)
  expect(limit.remaining(3000)).toBe(298000)
  expect(limit.remaining(301000)).toBe(0)
})
