import { expect, test } from 'bun:test'
import { readFileSync } from 'node:fs'
import { readerFeedbackContext } from './reader-feedback'

test('unrated immediately previous answers do not reuse an older rating', () => {
  expect(readerFeedbackContext(null)).toBe('')
  expect(readerFeedbackContext({feedback:null})).toBe('')
  expect(readerFeedbackContext({feedback:'unknown'})).toBe('')
  const executor = readFileSync('src/lib/turn-executor.ts', 'utf8')
  const query = executor.slice(executor.indexOf('const lastVerdict ='), executor.indexOf('const feedbackContext ='))
  expect(query).not.toContain('not: null')
  expect(query).toContain("role: 'assistant'")
  expect(query).toContain("orderBy: { createdAt: 'desc' }")
  expect(executor).toContain('readerFeedbackContext(lastVerdict)')
})

test('ratings do not invent what was wrong or dictate the next answer format', () => {
  for (const feedback of ['up','down']) {
    const context = readerFeedbackContext({feedback})
    expect(context).toContain('immediately preceding assistant answer')
    expect(context).not.toMatch(/do not repeat|keep .*approach|assumptions|level of detail|format|structure/i)
  }
})

test('the actual turn pipeline gates streaming and dispatch with assembled context', () => {
  const executor = readFileSync('src/lib/turn-executor.ts', 'utf8')
  expect(executor).toContain('presentationStream(content, push)')
  expect(executor).toContain('push = presentation.push')
  expect(executor).toContain('presentation.release(chunkDeltas(finalText))')
  expect(executor).toContain('catalogContextFits(route, msgs)')
  expect(executor.indexOf('const thinking = resolveThinkingConfig(')).toBeLessThan(executor.indexOf('const catalogFastRoutes = selectCatalogFastRoutes('))
  expect(executor).toContain("requiresReasoning: requestedMode === 'thinking' || thinking.config?.mode === 'thinking'")
})
