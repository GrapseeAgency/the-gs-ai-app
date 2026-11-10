import { expect, test } from 'bun:test'
import { effectiveModeFor, parseThinkingMode, resolveThinkingConfig } from './model-capabilities'

test('explicit thinking survives a short prompt classified TEXT_SIMPLE', () => {
  expect(resolveThinkingConfig('thinking', 'gs-swift', 'TEXT_SIMPLE', 'CHAT').config?.mode).toBe('thinking')
})

test('automatic complex, coding and research routes retain thinking depth', () => {
  for (const route of ['TEXT_COMPLEX','CODING','DEEP_RESEARCH','DOCUMENT']) {
    const resolved = resolveThinkingConfig('auto','gs-balanced',route,'CHAT')
    expect(resolved.effective).toBe('thinking')
    expect(resolved.config?.mode).toBe('thinking')
  }
})

test('a user flash preference is preserved on ordinary work', () => {
  expect(resolveThinkingConfig('flash','gs-balanced','TEXT_COMPLEX','CHAT').config?.mode).toBe('flash')
})

test('research retains its existing thinking override and time stays deterministic', () => {
  expect(effectiveModeFor('flash','DEEP_RESEARCH','DEEP_RESEARCH')).toBe('thinking')
  expect(effectiveModeFor('thinking','TEXT_SIMPLE','TIME')).toBe('flash')
})

test('unknown or absent wire modes keep the existing backward-compatible default', () => {
  expect(parseThinkingMode(undefined)).toBe('flash')
  expect(parseThinkingMode('auto')).toBe('auto')
  expect(parseThinkingMode('thinking')).toBe('thinking')
})
