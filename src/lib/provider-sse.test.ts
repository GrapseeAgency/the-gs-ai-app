import { describe, expect, test } from 'bun:test'
import { consumeSseStream } from './provider-sse'
const stream = (...parts: string[]) => new ReadableStream<Uint8Array>({
  start(controller) {
    for (const part of parts) controller.enqueue(new TextEncoder().encode(part))
    controller.close()
  },
})
const event = (content: string) => 'data: ' + JSON.stringify({ choices: [{ delta: { content } }] }) + '\n\n'
describe('provider answer integrity', () => {
  test('preserves text and separates reasoning and control events', async () => {
    const deltas: string[] = [], reasoning: string[] = []
    const result = await consumeSseStream(stream(
      'id: 1\nretry: 10\n: heartbeat\n',
      'data: {"choices":[{"delta":{"reasoning":"trace"}}]}\n\n',
      event('hello'), event(' বিশ্ব '), 'data: [DONE]\n',
    ), d => { deltas.push(d) }, undefined, r => { reasoning.push(r) })
    expect(result).toBe('hello বিশ্ব ')
    expect(deltas.join('')).toBe(result)
    expect(reasoning).toEqual(['trace'])
  })
  test('truncated structured responses fail rather than persist as complete', async () => {
    await expect(consumeSseStream(stream(event('partial')), () => {})).rejects.toThrow('completion marker')
  })
  test('explicit stop permits normal EOF', async () => {
    expect(await consumeSseStream(stream(event('complete'), 'data: {"choices":[{"finish_reason":"stop"}]}'), () => {})).toBe('complete')
  })
  test('provider error is not answer content', async () => {
    const deltas: string[] = []
    await expect(consumeSseStream(stream(event('partial'), 'data: {"error":{"message":"internal"}}\n'), d => { deltas.push(d) })).rejects.toThrow('stream error')
    expect(deltas).toEqual(['partial'])
  })
  test('length and unsupported tool calls fail', async () => {
    await expect(consumeSseStream(stream(event('partial'), 'data: {"choices":[{"finish_reason":"length"}]}\n'), () => {})).rejects.toThrow('before successful completion')
    await expect(consumeSseStream(stream('data: {"choices":[{"delta":{"tool_calls":[{}]}}]}\n'), () => {})).rejects.toThrow('tool')
  })
  test('consumer errors propagate', async () => {
    await expect(consumeSseStream(stream(event('hello')), () => { throw new Error('consumer failed') })).rejects.toThrow('consumer failed')
  })
  test('idle streams cancel', async () => {
    let cancelled = false
    const hanging = new ReadableStream<Uint8Array>({ cancel() { cancelled = true } })
    await expect(consumeSseStream(hanging, () => {}, undefined, undefined, 10)).rejects.toThrow('idle timeout')
    expect(cancelled).toBe(true)
  })
})
