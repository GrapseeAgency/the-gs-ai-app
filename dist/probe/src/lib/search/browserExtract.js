"use strict";
/**
 * PHASE 8.2 (§13/§14) — bounded browser-automation FALLBACK for source
 * retrieval. A search result is not evidence; when plain HTTP cannot extract
 * readable content (JS-rendered pages), the backend MAY open the page in a
 * real browser — SECONDARY, bounded, cancellable, server-side, never the
 * primary search engine.
 *
 * Implementation: the sandbox's `agent-browser` CLI, one-shot `read` command
 * (navigate + agent-readable text). Hard limits (§14):
 *   - deep-research turns only (caller-enforced)
 *   - max 2 pages per research turn (caller-enforced budget)
 *   - max 1 concurrent browser page (module-level lock)
 *   - hard wall-clock kill per page
 *   - response size cap
 *   - no user cookies/credentials (fresh automation context, nothing shared)
 *   - best-effort `close` afterwards so no chrome process lingers (memory)
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.browserFallbackEnabled = browserFallbackEnabled;
exports.browserExtract = browserExtract;
const node_child_process_1 = require("node:child_process");
const BROWSER_READ_TIMEOUT_MS = 30000;
const MAX_TEXT_CHARS = 200000;
let inFlight = 0;
function runCli(args, timeoutMs) {
    return new Promise((resolve, reject) => {
        const child = (0, node_child_process_1.spawn)('agent-browser', args, { stdio: ['ignore', 'pipe', 'pipe'] });
        let out = '';
        let settled = false;
        const timer = setTimeout(() => {
            if (settled)
                return;
            settled = true;
            child.kill('SIGKILL');
            reject(new Error('browser fallback timeout'));
        }, timeoutMs);
        child.stdout.on('data', (d) => {
            if (out.length < MAX_TEXT_CHARS)
                out += d.toString('utf8');
        });
        child.stderr.on('data', () => {
            /* noise ignored */
        });
        child.on('error', (e) => {
            if (settled)
                return;
            settled = true;
            clearTimeout(timer);
            reject(e);
        });
        child.on('close', (code) => {
            if (settled)
                return;
            settled = true;
            clearTimeout(timer);
            if (code === 0)
                resolve(out);
            else
                reject(new Error(`browser fallback exit ${code}`));
        });
    });
}
function browserFallbackEnabled() {
    return process.env.GS_BROWSER_FALLBACK !== '0';
}
/**
 * Open ONE page in the automation browser and return agent-readable text.
 * Throws on any failure — the caller keeps the honest failed/snippet state.
 */
async function browserExtract(url) {
    if (!/^https?:\/\//i.test(url))
        throw new Error('browser fallback: bad url scheme');
    if (inFlight >= 1)
        throw new Error('browser fallback busy');
    inFlight += 1;
    try {
        const raw = await runCli(['read', url], BROWSER_READ_TIMEOUT_MS);
        const text = raw.replace(/\s+\n/g, '\n').trim();
        if (text.length < 120)
            throw new Error('browser fallback: no readable text');
        return { text, chars: text.length };
    }
    finally {
        inFlight -= 1;
        // §14 memory discipline: never leave the automation browser resident.
        (0, node_child_process_1.spawn)('agent-browser', ['close'], { stdio: 'ignore' }).unref();
    }
}
