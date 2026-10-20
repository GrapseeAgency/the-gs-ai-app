"use strict";
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
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.parseKeys = parseKeys;
exports.readDbVault = readDbVault;
exports.writeDbVault = writeDbVault;
exports.writeAllLayers = writeAllLayers;
exports.loadKeyPool = loadKeyPool;
const node_fs_1 = __importDefault(require("node:fs"));
const node_path_1 = __importDefault(require("node:path"));
const KEY_FILE_PATH = '/home/z/my-project/.secrets/openrouter.keys';
const KEY_VAULT_DB_PATH = '/home/z/my-project/db/vault.db';
const KEY_GS_VAULT_PATH = '/home/z/.gs-vault/openrouter.keys';
const KEY_VAULT_NAME = 'OPENROUTER_API_KEYS';
function parseKeys(raw) {
    return raw
        .split(/[\n,]/)
        .map((k) => k.trim())
        .filter((k) => k.startsWith('sk-or-'));
}
function readEnv() {
    return parseKeys(process.env.OPENROUTER_API_KEYS ?? '');
}
function readSecretsFile() {
    try {
        return parseKeys(node_fs_1.default.readFileSync(KEY_FILE_PATH, 'utf8'));
    }
    catch {
        return [];
    }
}
/** Layer (c) — gitignored bun:sqlite vault (never committed, never pushed). */
async function readDbVault() {
    try {
        // dynamic specifier: tsc has no bun:sqlite types; bun resolves it at runtime
        const specifier = 'bun:sqlite';
        const mod = (await Promise.resolve(`${specifier}`).then(s => __importStar(require(s))));
        const exists = node_fs_1.default.existsSync(KEY_VAULT_DB_PATH);
        const vault = new mod.Database(KEY_VAULT_DB_PATH, { readonly: exists });
        try {
            if (!exists) {
                vault.query('CREATE TABLE IF NOT EXISTS vault (name TEXT PRIMARY KEY, value TEXT NOT NULL)').run();
            }
            const row = vault.query('SELECT value FROM vault WHERE name = ?').get(KEY_VAULT_NAME);
            if (!row?.value)
                return [];
            return parseKeys(String(row.value));
        }
        finally {
            vault.close();
        }
    }
    catch {
        // Vault file missing (fresh boot) or runtime without bun:sqlite — the
        // pool just falls back to being empty; never break the request path.
        return [];
    }
}
function readGsVault() {
    try {
        return parseKeys(node_fs_1.default.readFileSync(KEY_GS_VAULT_PATH, 'utf8'));
    }
    catch {
        return [];
    }
}
// ---------------------------------------------------------------------------
// writers (provision + self-heal)
// ---------------------------------------------------------------------------
function writeSecretsFile(keys) {
    try {
        const dir = node_path_1.default.dirname(KEY_FILE_PATH);
        node_fs_1.default.mkdirSync(dir, { recursive: true, mode: 0o700 });
        node_fs_1.default.chmodSync(dir, 0o700);
        node_fs_1.default.writeFileSync(KEY_FILE_PATH, keys.join('\n') + '\n', { mode: 0o600 });
        node_fs_1.default.chmodSync(KEY_FILE_PATH, 0o600);
        return true;
    }
    catch {
        return false;
    }
}
async function writeDbVault(keys) {
    try {
        const specifier = 'bun:sqlite';
        const mod = (await Promise.resolve(`${specifier}`).then(s => __importStar(require(s))));
        const vault = new mod.Database(KEY_VAULT_DB_PATH);
        try {
            vault.query('CREATE TABLE IF NOT EXISTS vault (name TEXT PRIMARY KEY, value TEXT NOT NULL)').run();
            vault.query('INSERT INTO vault (name, value) VALUES (?, ?) ON CONFLICT(name) DO UPDATE SET value = excluded.value').run(KEY_VAULT_NAME, keys.join(','));
            return true;
        }
        finally {
            vault.close();
        }
    }
    catch {
        return false;
    }
}
function writeGsVault(keys) {
    try {
        const dir = node_path_1.default.dirname(KEY_GS_VAULT_PATH);
        node_fs_1.default.mkdirSync(dir, { recursive: true, mode: 0o700 });
        node_fs_1.default.chmodSync(dir, 0o700);
        node_fs_1.default.writeFileSync(KEY_GS_VAULT_PATH, keys.join('\n') + '\n', { mode: 0o600 });
        node_fs_1.default.chmodSync(KEY_GS_VAULT_PATH, 0o600);
        return true;
    }
    catch {
        return false;
    }
}
/** Write the pool to EVERY file/vault layer (provisioning = one paste, all layers). */
async function writeAllLayers(keys) {
    const joined = keys.join('\n');
    const results = [
        { layer: 'secrets-file', ok: writeSecretsFile([joined]) },
        { layer: 'db-vault', ok: await writeDbVault(keys) },
        { layer: 'gs-vault', ok: writeGsVault([joined]) },
    ];
    return results;
}
// ---------------------------------------------------------------------------
// loader
// ---------------------------------------------------------------------------
const mask = (k) => `***${k.slice(-4)}`;
/**
 * Ordered four-layer read with logging + self-heal. Returns the pool and the
 * layer that supplied it ('none' when every layer is empty — GS Free then
 * reports the honest offline error).
 */
async function loadKeyPool() {
    const attempts = [
        { layer: 'env', keys: readEnv() },
        { layer: 'secrets-file', keys: readSecretsFile() },
        { layer: 'db-vault', keys: await readDbVault() },
        { layer: 'gs-vault', keys: readGsVault() },
    ];
    const found = attempts.find((a) => a.keys.length > 0);
    if (!found) {
        console.log('KEYPOOL layer=none count=0 — every key layer is empty (platform wipe); re-provision keys');
        return { keys: [], layer: 'none' };
    }
    console.log(`KEYPOOL layer=${found.layer} count=${found.keys.length} keys=${found.keys.map(mask).join(',')}`);
    // SELF-HEAL: restore every empty file/vault layer from the surviving pool so
    // the next platform reboot (which wipes one layer at a time) still lands on
    // live keys. env cannot be back-filled from inside the process.
    if (found.layer !== 'env' || true) {
        const joined = found.keys.join('\n');
        if (attempts[1].keys.length === 0 && writeSecretsFile([joined])) {
            console.log('KEYPOOL self-heal: restored .secrets/openrouter.keys');
        }
        if (attempts[2].keys.length === 0 && (await writeDbVault(found.keys))) {
            console.log('KEYPOOL self-heal: restored db/vault.db');
        }
        if (attempts[3].keys.length === 0 && writeGsVault([joined])) {
            console.log('KEYPOOL self-heal: restored /home/z/.gs-vault/openrouter.keys');
        }
    }
    return { keys: found.keys, layer: found.layer };
}
