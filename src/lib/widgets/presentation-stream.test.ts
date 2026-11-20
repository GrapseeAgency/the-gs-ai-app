import { expect, test } from 'bun:test'
import { presentationStream } from './presentation-stream'

test('deferred presentation keeps activity, replaces a rejected draft, then releases one final answer', () => {
  const emitted: { event: string; data: string }[] = []
  const stream = presentationStream('Reply in plain text only.', (event, data) => emitted.push({ event, data }))

  stream.push?.('status', 'generating')
  stream.push?.('delta', 'provisional provider text')
  stream.push?.('status', 'checking')
  stream.push?.('reset', '')
  expect(emitted).toEqual([
    { event: 'status', data: 'generating' },
    { event: 'status', data: 'checking' },
    { event: 'reset', data: '' },
  ])

  stream.release('The finalized plain-text answer.')
  stream.release('duplicate')
  expect(emitted.at(-1)).toEqual({ event: 'delta', data: 'The finalized plain-text answer.' })
  expect(emitted.filter((event) => event.event === 'delta')).toHaveLength(1)
})

test('automatic presentation forwards genuine provider deltas without replaying a final chunk', () => {
  const emitted: { event: string; data: string }[] = []
  const stream = presentationStream('Explain this briefly.', (event, data) => emitted.push({ event, data }))

  stream.push?.('delta', 'first')
  stream.push?.('delta', ' second')
  stream.release('first second')

  expect(emitted).toEqual([
    { event: 'delta', data: 'first' },
    { event: 'delta', data: ' second' },
  ])
})
