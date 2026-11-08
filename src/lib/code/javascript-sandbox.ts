import contract from '../../../shared-contracts/javascript-worker-sandbox.json'

/** All clients load this exact bounded, network-disabled Worker document.
 * Source enters one JSON string slot, never the host executable script. */
export function javascriptSandboxDocument(source: string): string {
  return contract.template.replace(contract.sourcePlaceholder, JSON.stringify(source).replace(/</g, '\\u003c'))
}
