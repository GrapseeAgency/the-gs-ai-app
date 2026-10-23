#!/usr/bin/env node
/**
 * Bundle the Nexly icon vocabulary for the native clients.
 *
 * The web renderer can address every Tabler glyph through an SVG sprite. The
 * native clients cannot: a 5,184-file vector set would bloat both apps and
 * make every plan row a resource lookup. So the same 3.49.0 outline font is
 * bundled as one TTF, with the CSS class-to-codepoint table reduced to a small
 * JSON map. Both platforms use the identical file and the identical map, which
 * is the only way a plan emitted once can look the same on Android and iOS.
 *
 * Usage:
 *   npm pack @tabler/icons-webfont@3.49.0
 *   tar -xzf tabler-icons-webfont-3.49.0.tgz
 *   node scripts/build-native-nexly-icons.mjs ./package
 *
 * Committed outputs make this a one-time asset step; CI never needs the
 * package installed.
 */

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const pkg = path.resolve(process.argv[2] ?? '/tmp/opencode/tabler/package')
const dist = path.join(pkg, 'dist')
const cssPath = path.join(dist, 'tabler-icons.css')
const fontPath = path.join(dist, 'fonts', 'tabler-icons.ttf')
const licensePath = path.join(pkg, 'LICENSE')

for (const file of [cssPath, fontPath, licensePath]) {
  if (!fs.existsSync(file)) {
    console.error(`missing ${file}`)
    process.exit(1)
  }
}

const css = fs.readFileSync(cssPath, 'utf8')
const codepoints = new Map()
const rule = /^\.ti-([a-z0-9-]+):before\s*\{\s*content:\s*"\\([0-9a-f]+)";/gm
for (const match of css.matchAll(rule)) {
  const cp = Number.parseInt(match[2], 16)
  if (!Number.isFinite(cp) || cp <= 0 || cp > 0x10ffff) continue
  codepoints.set(match[1], cp.toString(16).padStart(4, '0'))
}
if (codepoints.size < 5000) {
  console.error(`only ${codepoints.size} glyphs parsed from ${cssPath}`)
  process.exit(1)
}

const json = JSON.stringify(Object.fromEntries([...codepoints.entries()].sort(([a], [b]) => a.localeCompare(b))))
const license = fs.readFileSync(licensePath)
const font = fs.readFileSync(fontPath)

function write(file, data) {
  fs.mkdirSync(path.dirname(file), { recursive: true })
  fs.writeFileSync(file, data)
  const bytes = Buffer.isBuffer(data) ? data.length : Buffer.byteLength(data)
  console.log(`${bytes.toString().padStart(8)}  ${path.relative(root, file)}`)
}

const android = path.join(root, 'android', 'app', 'src', 'main')
const ios = path.join(root, 'ios', 'App', 'Resources')

write(path.join(android, 'assets', 'nexly-icons.json'), json)
write(path.join(android, 'assets', 'TABLER-LICENSE.txt'), license)
write(path.join(android, 'res', 'font', 'tabler_icons.ttf'), font)
write(path.join(ios, 'nexly-icons.json'), json)
write(path.join(ios, 'TABLER-LICENSE.txt'), license)
write(path.join(ios, 'TablerIcons.ttf'), font)

console.log(`\n${codepoints.size} glyphs · family "tabler-icons"`)
