"use strict";
/**
 * BACKEND REVISION — the identity the device-acceptance harness verifies.
 *
 * Precedence: explicit env override (GS_BACKEND_REVISION, set at deploys)
 * > live `git rev-parse --short HEAD` (cached 60s — this endpoint exists so
 * an operator can prove WHICH backend a phone is talking to, so the value
 * must be real) > 'dev'.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.getBackendRevision = getBackendRevision;
const node_child_process_1 = require("node:child_process");
let cached = null;
const CACHE_MS = 60000;
function gitRevision() {
    return new Promise((resolve) => {
        (0, node_child_process_1.execFile)('git', ['rev-parse', '--short', 'HEAD'], { cwd: process.cwd(), timeout: 5000 }, (err, stdout) => {
            if (err)
                resolve(null);
            else
                resolve(stdout.trim().slice(0, 12) || null);
        });
    });
}
async function getBackendRevision() {
    const env = process.env.GS_BACKEND_REVISION;
    if (env && env.length > 0)
        return env;
    if (cached && Date.now() - cached.at < CACHE_MS)
        return cached.value;
    const rev = await gitRevision();
    const value = rev ?? 'dev';
    cached = { value, at: Date.now() };
    return value;
}
