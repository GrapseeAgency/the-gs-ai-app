/**
 * FORENSIC AUDIT [2] — FOUR-LAYER DURABLE KEY POOL.
 *
 * The hosting platform rewrites .env, deletes .secrets/, and rolls back git
 * history on every container reboot; API keys were destroyed at least four
 * times mid-session. The pool therefore lives in FOUR redundant layers, tried
 * in this order by loadKeyPool():
 *
 *   (a) process.env.OPENROUTER_API_KEYS
 *   (b) gitignored .secrets/openrouter.keys   (dir chmod 700, file chmod 600)
 *   (c) gitignored SQLite vault db/vault.db   ("vault" table — survives
 *       process restarts; NEVER the tracked main db)
 *   (d) out-of-project copy /home/z/.gs-vault/openrouter.keys (chmod 600) —
 *       outside the project tree so git-history rollbacks cannot reach it
 *
 * loadKeyPool() logs WHICH layer succeeded and SELF-HEALS: keys found in a
 * later layer are written back into every empty earlier file/vault layer, so
 * a single operator paste (scripts/provision-keys.ts) restores all layers and
 * every later wipe is repaired on the first request without manual action.
 *
 * Keys are never logged in full (last-4 only), never committed, never pushed.
 */

import fs from 'node:fs'
import path from 'node:path'

const KEY_FILE_PATH = '/home/z/my-project/.secrets/openrouter.keys'
const KEY_VAULT_DB_PATH = '/home/z/my-project/db/vault.db'
const KEY_GS_VAULT_PATH = '/home/z/.gs-vault/openrouter.keys'
const KEY_VAULT_NAME = 'OPENROUTER_API_KEYS'

export type KeyLayer = 'env' | 'secrets-file' | 'db-vault' | 'gs-vault'

export function parseKeys(raw: string): string[] {
  return raw
    .split(/[\n,]/)
    .map((k) => k.trim())
    .filter((k) => k.startsWith('sk-or-'))
}

function readEnv(): string[] {
  return parseKeys(process.env.OPENROUTER_API_KEYS ?? '')
}

function readSecretsFile(): string[] {
  try {
    return parseKeys(fs.readFileSync(KEY_FILE_PATH, 'utf8'))
  } catch {
    return []
  }
}

/** Layer (c) — gitignored bun:sqlite vault (never committed, never pushed). */
export async function readDbVault(): Promise<string[]> {
  try {
    // dynamic specifier: tsc has no bun:sqlite types; bun resolves it at runtime
    const specifier = 'bun:sqlite'
    type VaultDb = {
      query: (sql: string) => {
        get: (...args: unknown[]) => unknown
        run: (...args: unknown[]) => unknown
      }
      close: () => void
    }
    const mod = (await import(specifier)) as {
      Database: new (path: string, opts?: { readonly?: boolean }) => VaultDb
    }
    const exists = fs.existsSync(KEY_VAULT_DB_PATH)
    const vault = new mod.Database(KEY_VAULT_DB_PATH, { readonly: exists })
    try {
      if (!exists) {
        vault.query(
          'CREATE TABLE IF NOT EXISTS vault (name TEXT PRIMARY KEY, value TEXT NOT NULL)'
        ).run()
      }
      const row = vault.query('SELECT value FROM vault WHERE name = ?').get(KEY_VAULT_NAME) as
        | { value: string }
        | null
      if (!row?.value) return []
      return parseKeys(String(row.value))
    } finally {
      vault.close()
    }
  } catch {
    // Vault file missing (fresh boot) or runtime without bun:sqlite — the
    // pool just falls back to being empty; never break the request path.
    return []
  }
}

function readGsVault(): string[] {
  try {
    return parseKeys(fs.readFileSync(KEY_GS_VAULT_PATH, 'utf8'))
  } catch {
    return []
  }
}

// ---------------------------------------------------------------------------
// writers (provision + self-heal)
// ---------------------------------------------------------------------------

function writeSecretsFile(keys: string[]): boolean {
  try {
    const dir = path.dirname(KEY_FILE_PATH)
    fs.mkdirSync(dir, { recursive: true, mode: 0o700 })
    fs.chmodSync(dir, 0o700)
    fs.writeFileSync(KEY_FILE_PATH, keys.join('\n') + '\n', { mode: 0o600 })
    fs.chmodSync(KEY_FILE_PATH, 0o600)
    return true
  } catch {
    return false
  }
}

export async function writeDbVault(keys: string[]): Promise<boolean> {
  try {
    const specifier = 'bun:sqlite'
    type VaultDb = {
      query: (sql: string) => { run: (...args: unknown[]) => unknown }
      close: () => void
    }
    const mod = (await import(specifier)) as {
      Database: new (path: string) => VaultDb
    }
    const vault = new mod.Database(KEY_VAULT_DB_PATH)
    try {
      vault.query('CREATE TABLE IF NOT EXISTS vault (name TEXT PRIMARY KEY, value TEXT NOT NULL)').run()
      vault.query(
        'INSERT INTO vault (name, value) VALUES (?, ?) ON CONFLICT(name) DO UPDATE SET value = excluded.value'
      ).run(KEY_VAULT_NAME, keys.join(','))
      return true
    } finally {
      vault.close()
    }
  } catch {
    return false
  }
}

function writeGsVault(keys: string[]): boolean {
  try {
    const dir = path.dirname(KEY_GS_VAULT_PATH)
    fs.mkdirSync(dir, { recursive: true, mode: 0o700 })
    fs.chmodSync(dir, 0o700)
    fs.writeFileSync(KEY_GS_VAULT_PATH, keys.join('\n') + '\n', { mode: 0o600 })
    fs.chmodSync(KEY_GS_VAULT_PATH, 0o600)
    return true
  } catch {
    return false
  }
}

/** Write the pool to EVERY file/vault layer (provisioning = one paste, all layers). */
export async function writeAllLayers(
  keys: string[]
): Promise<{ layer: KeyLayer; ok: boolean }[]> {
  const joined = keys.join('\n')
  const results: { layer: KeyLayer; ok: boolean }[] = [
    { layer: 'secrets-file', ok: writeSecretsFile([joined]) },
    { layer: 'db-vault', ok: await writeDbVault(keys) },
    { layer: 'gs-vault', ok: writeGsVault([joined]) },
  ]
  return results
}

// ---------------------------------------------------------------------------
// loader
// ---------------------------------------------------------------------------

const mask = (k: string): string => `***${k.slice(-4)}`

/**
 * Ordered four-layer read with logging + self-heal. Returns the pool and the
 * layer that supplied it ('none' when every layer is empty — GS Free then
 * reports the honest offline error).
 */
export async function loadKeyPool(): Promise<{
  keys: string[]
  layer: KeyLayer | 'none'
}> {
  const attempts: { layer: KeyLayer; keys: string[] }[] = [
    { layer: 'env', keys: readEnv() },
    { layer: 'secrets-file', keys: readSecretsFile() },
    { layer: 'db-vault', keys: await readDbVault() },
    { layer: 'gs-vault', keys: readGsVault() },
  ]

  const found = attempts.find((a) => a.keys.length > 0)
  if (!found) {
    console.log('KEYPOOL layer=none count=0 — every key layer is empty (platform wipe); re-provision keys')
    return { keys: [], layer: 'none' }
  }

  console.log(
    `KEYPOOL layer=${found.layer} count=${found.keys.length} keys=${found.keys.map(mask).join(',')}`
  )

  // SELF-HEAL: restore every empty file/vault layer from the surviving pool so
  // the next platform reboot (which wipes one layer at a time) still lands on
  // live keys. env cannot be back-filled from inside the process.
  if (found.layer !== 'env' || true) {
    const joined = found.keys.join('\n')
    if (attempts[1].keys.length === 0 && writeSecretsFile([joined])) {
      console.log('KEYPOOL self-heal: restored .secrets/openrouter.keys')
    }
    if (attempts[2].keys.length === 0 && (await writeDbVault(found.keys))) {
      console.log('KEYPOOL self-heal: restored db/vault.db')
    }
    if (attempts[3].keys.length === 0 && writeGsVault([joined])) {
      console.log('KEYPOOL self-heal: restored /home/z/.gs-vault/openrouter.keys')
    }
  }

  return { keys: found.keys, layer: found.layer }
}
