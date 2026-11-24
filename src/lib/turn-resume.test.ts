import { describe, expect, test } from 'bun:test'
import { attachmentMatchesTurn, decodeStoredTurnFacts } from './turn-resume'
describe('durable turn replay', () => {
  test('preserves explicit reasoning mode, attachments and user context', () => {
    const original = { content: 'Analyze this', attachmentIds: ['a'], timezone: 'Asia/Dhaka', clientVersion: 'test', mode: 'thinking', turnContext: 'Use Bengali. Plan: completed step 2.' }
    expect(decodeStoredTurnFacts(JSON.stringify(original))).toEqual(original)
  })
  test('missing or malformed start events do not become empty turns', () => {
    expect(decodeStoredTurnFacts(undefined)).toBeNull()
    expect(decodeStoredTurnFacts('{}')).toBeNull()
    expect(decodeStoredTurnFacts('{"content":"x","attachmentIds":[1]}')).toBeNull()
  })
  test('fresh and resumed turns enforce exact attachment ownership', () => {
    expect(attachmentMatchesTurn({ conversationId: null, messageId: null }, 'chat')).toBe(true)
    expect(attachmentMatchesTurn({ conversationId: 'chat', messageId: 'user' }, 'chat')).toBe(false)
    expect(attachmentMatchesTurn({ conversationId: 'chat', messageId: 'user' }, 'chat', 'user')).toBe(true)
    expect(attachmentMatchesTurn({ conversationId: 'chat', messageId: 'other' }, 'chat', 'user')).toBe(false)
    expect(attachmentMatchesTurn({ conversationId: 'foreign', messageId: 'user' }, 'chat', 'user')).toBe(false)
    expect(attachmentMatchesTurn({ conversationId: 'chat', messageId: null }, 'chat', 'user')).toBe(false)
  })
})
