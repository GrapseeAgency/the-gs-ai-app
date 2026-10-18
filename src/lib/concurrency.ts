/**
 * Bounded-concurrency map that preserves input order.
 *
 * Used where work is CPU/IO heavy and serial execution is the latency bug but
 * unbounded parallelism is the memory bug — image preparation being the case
 * that motivated it (each concurrent 12 MP decode holds a full raw frame).
 */
export async function mapWithConcurrency<T, R>(
  items: T[],
  limit: number,
  fn: (item: T, index: number) => Promise<R>
): Promise<R[]> {
  const out = new Array<R>(items.length)
  let next = 0
  const workers = Array.from({ length: Math.min(Math.max(1, limit), items.length) }, async () => {
    for (;;) {
      const i = next++
      if (i >= items.length) return
      out[i] = await fn(items[i], i)
    }
  })
  await Promise.all(workers)
  return out
}
