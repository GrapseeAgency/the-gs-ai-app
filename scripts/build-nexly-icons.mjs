/**
 * Build the Nexly glass icon library.
 *
 * Source: Tabler Icons outline nodes (MIT). We generate one SVG sprite with a
 * <symbol> per icon, plus a JSON name manifest. The renderer never imports
 * 5,000 React components: it references a symbol by name and inherits
 * currentColor from the glass tile.
 *
 * Run: node scripts/build-nexly-icons.mjs
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const nodesPath = path.join(root, 'node_modules/@tabler/icons/tabler-nodes-outline.json')
const licensePath = path.join(root, 'node_modules/@tabler/icons/LICENSE')

const nodes = JSON.parse(fs.readFileSync(nodesPath, 'utf8'))
const names = Object.keys(nodes).sort()

const escapeAttr = (value) =>
  String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('"', '&quot;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')

/** One Tabler node -> one SVG child. */
function nodeToSvg([tag, attrs]) {
  const parts = Object.entries(attrs ?? {})
    .filter(([key]) => key !== 'key')
    .map(([key, value]) => `${key}="${escapeAttr(value)}"`)
  return `<${tag}${parts.length ? ` ${parts.join(' ')}` : ''}/>`
}

const symbols = names.map((name) => {
  const children = nodes[name].map(nodeToSvg).join('')
  return `<symbol id="gs-${name}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${children}</symbol>`
})

const sprite = `<svg xmlns="http://www.w3.org/2000/svg" style="position:absolute;width:0;height:0;overflow:hidden" aria-hidden="true">${symbols.join('')}</svg>\n`

const outDir = path.join(root, 'public/icons')
fs.mkdirSync(outDir, { recursive: true })
fs.writeFileSync(path.join(outDir, 'gs-sprite.svg'), sprite)
fs.writeFileSync(path.join(outDir, 'TABLER-LICENSE.txt'), fs.readFileSync(licensePath, 'utf8'))

// Keep the name manifest small and ordered. This is the model-facing vocabulary.
fs.mkdirSync(path.join(root, 'src/lib/nexly'), { recursive: true })
fs.writeFileSync(
  path.join(root, 'src/lib/nexly/icon-names.json'),
  `${JSON.stringify(names)}\n`
)

const bytes = Buffer.byteLength(sprite)
console.log(`Nexly icons: ${names.length}`)
console.log(`Sprite: public/icons/gs-sprite.svg (${(bytes / 1024 / 1024).toFixed(2)} MB)`)
console.log(`Manifest: src/lib/nexly/icon-names.json`)
