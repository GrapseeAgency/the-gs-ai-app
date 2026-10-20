"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.mapWithConcurrency = mapWithConcurrency;
/**
 * Bounded-concurrency map that preserves input order.
 *
 * Used where work is CPU/IO heavy and serial execution is the latency bug but
 * unbounded parallelism is the memory bug — image preparation being the case
 * that motivated it (each concurrent 12 MP decode holds a full raw frame).
 */
async function mapWithConcurrency(items, limit, fn) {
    const out = new Array(items.length);
    let next = 0;
    const workers = Array.from({ length: Math.min(Math.max(1, limit), items.length) }, async () => {
        for (;;) {
            const i = next++;
            if (i >= items.length)
                return;
            out[i] = await fn(items[i], i);
        }
    });
    await Promise.all(workers);
    return out;
}
