/**
 * FORENSIC AUDIT [2] — ONE-PASTE KEY PROVISIONING.
 *
 * Writes the OpenRouter key pool into ALL durable layers at once:
 *   (b) .secrets/openrouter.keys   (dir 700 / file 600)
 *   (c) db/vault.db SQLite vault   ("vault" table)
 *   (d) /home/z/.gs-vault/openrouter.keys (dir 700 / file 600, out-of-project)
 * Layer (a) process.env cannot be written from a child process — add it to
 * .env via the platform UI if desired; the loader prefers env when present.
 *
 * Usage:
 *   bun scripts/provision-keys.ts "sk-or-v1-aaa...,sk-or-v1-bbb"
 *   echo "sk-or-v1-aaa,sk-or-v1-bbb" | bun scripts/provision-keys.ts
 *
 * Keys are never printed in full (last-4 only).
 */

import { readFileSync } from 'node:fs'

import { writeAllLayers, parseKeys } from '../src/lib/keypool'

async function main(): Promise<void> {
  const arg = process.argv[2]
  const raw = (arg && arg.trim().length > 0 ? arg : readFileSync(0, 'utf8')).trim()
  const keys = parseKeys(raw)
  if (keys.length === 0) {
    console.error('ERROR: no sk-or- keys found in the input. Nothing written.')
    process.exit(1)
  }
  const results = await writeAllLayers(keys)
  for (const r of results) {
    console.log(`provision layer=${r.layer} ok=${r.ok}`)
  }
  console.log(
    `provisioned ${keys.length} key(s): ${keys.map((k) => `***${k.slice(-4)}`).join(', ')} — verify with a request; the loader self-heals wiped layers automatically.`
  )
}

await main()
