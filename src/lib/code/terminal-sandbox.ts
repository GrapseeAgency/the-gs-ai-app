import terminal from '../../../shared-contracts/terminal-runtime.json'
import worker from '../../../shared-contracts/javascript-worker-sandbox.json'

/** Source/metadata enter JSON string slots, never executable object literals.
 * Replace both slots in ONE pass: payload text may itself contain either token
 * or JavaScript replacement patterns such as $& and $$. */
export function terminalSandboxDocument(payload: Record<string, unknown>): string {
  const literal = (value: unknown) => JSON.stringify(JSON.stringify(value)).replace(/</g, '\\u003c')
  const slots: Record<string, string> = {
    [terminal.payloadPlaceholder]: literal(payload),
    [terminal.workerPlaceholder]: literal(worker),
  }
  const names = Object.keys(slots)
  if (names.some(name => terminal.template.split(name).length !== 2)) {
    return '<p>Terminal unavailable: invalid runtime contract. Nothing was executed.</p>'
  }
  const pattern = names.map(name => name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|')
  return terminal.template.replace(new RegExp(pattern, 'g'), token => slots[token])
}
