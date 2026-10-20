/**
 * Node-side alias hook: lets a compiled-out probe require the app's '@/...'
 * imports. Compile with tsconfig.probe.json (emits to dist/probe), then:
 *   node dist/probe/scripts/test-genui-pipeline.js
 */
/* eslint-disable @typescript-eslint/no-explicit-any */
import Module from 'module'
import { join } from 'path'
const M = Module as any
const orig = M._resolveFilename
const compiledSrc = join(__dirname, '..', 'src')
M._resolveFilename = function (request: string, ...rest: any[]) {
  if (request.startsWith('@/')) request = join(compiledSrc, request.slice(2))
  return orig.call(this, request, ...rest)
}
