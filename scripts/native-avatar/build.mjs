import { build } from 'esbuild'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { mkdir, stat } from 'node:fs/promises'
const here = dirname(fileURLToPath(import.meta.url))
const outfile = resolve(here, '../../android/app/src/main/assets/bot-avatar/avatar.bundle.js')
await mkdir(dirname(outfile), { recursive: true })
await build({
  entryPoints: [resolve(here, 'entry.tsx')],
  outfile,
  platform: 'browser', format: 'iife', bundle: true, minify: true,
  jsx: 'automatic', target: ['chrome90'], legalComments: 'none',
})
const bytes = (await stat(outfile)).size
if (bytes < 20_000 || bytes > 2_000_000) {
  throw new Error('Invalid original bot-avatars runtime size: ' + bytes)
}
console.log('Bundled original bot-avatars:', bytes, 'bytes')
