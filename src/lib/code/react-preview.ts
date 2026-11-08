import runtime from '../../../shared-contracts/react-preview-runtime.json'

export function reactPreviewDocument(source: string, storageBootstrap = ''): string {
  // Insert source last: source text cannot manufacture a template placeholder
  // that is subsequently substituted as executable host code.
  return runtime.template
    .replace('__GS_REACT_STORAGE_BOOTSTRAP__', () => storageBootstrap)
    .replace('__GS_REACT_SOURCE_LITERAL__', () => JSON.stringify(source).replace(/</g, '\\u003c'))
}
