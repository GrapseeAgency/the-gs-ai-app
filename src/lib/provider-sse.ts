/** Remote-provider SSE integrity. Control frames, errors, reasoning and
 * answer deltas are separate. This transport has no tool dispatcher. */
type Choice = {
  delta?: { content?: unknown; reasoning?: unknown; reasoning_content?: unknown; tool_calls?: unknown }
  message?: { content?: unknown; reasoning_content?: unknown; tool_calls?: unknown }
  finish_reason?: unknown
}
type WireEvent = { model?: unknown; choices?: Choice[]; delta?: { content?: unknown }; error?: unknown }

export async function consumeSseStream(
  body: ReadableStream<Uint8Array>,
  onDelta: (text: string) => Promise<void> | void,
  onModel?: (model: string) => void,
  onReasoning?: (text: string) => void,
  idleTimeoutMs = 45_000,
  progressTimeoutMs = 90_000,
): Promise<string> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = '', full = '', eventType = ''
  let terminated = false, structured = false, finished = false
  let lastProgress = performance.now()
  async function append(text: string) {
    if (text) { full += text; await onDelta(text); lastProgress=performance.now() }
  }
  async function line(raw: string): Promise<boolean> {
    const input = raw.endsWith('\r') ? raw.slice(0, -1) : raw
    if (!input.trim()) { eventType = ''; return false }
    if (input.startsWith(':')) return false
    if (input.startsWith('event:')) { eventType = input.slice(6).trim(); return false }
    if (/^(id|retry):/.test(input)) return false
    const payload = input.startsWith('data:') ? input.slice(5).replace(/^ /, '') : input
    if (eventType === 'error') throw new Error('Provider emitted a stream error')
    if (payload.trim() === '[DONE]') return true
    if (!payload) return false
    let parsed: WireEvent | string | null
    try { parsed = JSON.parse(payload) as WireEvent | string | null }
    catch {
      // Legacy plain text remains supported. Broken JSON is not prose.
      if (/^\s*[{\[]/.test(payload)) throw new Error('Invalid provider stream payload')
      await append(payload)
      return false
    }
    if (typeof parsed === 'string') { await append(parsed); return false }
    if (!parsed || typeof parsed !== 'object') return false
    if (parsed.error != null) throw new Error('Provider emitted a stream error')
    if (typeof parsed.model === 'string' && parsed.model) onModel?.(parsed.model)
    const choice = parsed.choices?.[0]
    if (choice) structured = true
    const reason = choice?.delta?.reasoning_content ?? choice?.delta?.reasoning ?? choice?.message?.reasoning_content
    if (typeof reason === 'string' && reason) {
      onReasoning?.(reason)
      lastProgress=performance.now()
    }
    const toolCalls = choice?.delta?.tool_calls ?? choice?.message?.tool_calls
    if ((Array.isArray(toolCalls) ? toolCalls.length > 0 : toolCalls != null) || choice?.finish_reason === 'tool_calls')
      throw new Error('Provider requested a tool outside the text stream contract')
    const content = choice?.delta?.content ?? choice?.message?.content ?? parsed.delta?.content
    if (typeof content === 'string') await append(content)
    if (typeof choice?.finish_reason === 'string') {
      // An explicit successful terminal reason is authoritative; a provider
      // that keeps its socket alive after stop must not delay completion.
      if (choice.finish_reason === 'stop') { finished=true; return true }
      else throw new Error('Provider ended the answer before successful completion')
    }
    return false
  }
  async function read() {
    let timer: ReturnType<typeof setTimeout> | undefined
    const remaining=progressTimeoutMs-(performance.now()-lastProgress)
    if(remaining<=0)throw new Error('Provider stream progress timeout')
    const progressBound=remaining<idleTimeoutMs
    try {
      return await Promise.race([
        reader.read(),
        new Promise<never>((_, reject) => {
          timer=setTimeout(()=>reject(new Error(progressBound?'Provider stream progress timeout':'Provider stream idle timeout')),
            Math.min(idleTimeoutMs,remaining))
        }),
      ])
    } finally { if (timer !== undefined) clearTimeout(timer) }
  }
  try {
    while (!terminated) {
      const chunk = await read()
      if (chunk.done) break
      buffer += decoder.decode(chunk.value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() ?? ''
      for (const item of lines) {
        if (await line(item)) { terminated = true; break }
      }
    }
    if (!terminated) {
      buffer += decoder.decode()
      if (buffer) terminated = await line(buffer)
    }
    if (structured && !terminated && !finished)
      throw new Error('Provider stream closed before its completion marker')
    return full
  } finally {
    void reader.cancel().catch(() => undefined)
    try { reader.releaseLock() } catch { /* best effort */ }
  }
}
