/**
 * Hermetic regression tests for the live diagnostics export blocker.
 * Run with `bun scripts/test-silent-failure-ci.mjs` (or Node 22+ with
 * `--experimental-strip-types`). No network or deployed origin is used.
 */

import assert from 'node:assert/strict'
import { runSilentFailureCI } from './silent-failure-ci.ts'

function makeLogger() {
  const messages = { log: [], error: [] }
  return {
    messages,
    logger: {
      log: (message) => messages.log.push(message),
      error: (message) => messages.error.push(message),
    },
  }
}

function response(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

async function test(name, run) {
  try {
    await run()
    console.log(`ok   ${name}`)
  } catch (error) {
    console.error(`FAIL ${name}: ${error instanceof Error ? error.stack ?? error.message : String(error)}`)
    failures++
  }
}

let failures = 0
const noWait = async () => {}

await test('an export failure that succeeds on retry stays clean', async () => {
  let calls = 0
  const urls = []
  const { logger, messages } = makeLogger()
  const result = await runSilentFailureCI({
    origin: 'https://diagnostics.invalid',
    requestIds: ['retry once'],
    fetchImpl: async (url) => {
      urls.push(url)
      calls++
      if (calls === 1) return response({}, 503)
      return response({ trace: [{ requestId: 'retry once', silentFailures: [] }] })
    },
    sleep: noWait,
    logger,
  })

  assert.equal(calls, 2)
  assert.match(urls[0], /requestId=retry%20once$/)
  assert.equal(result.exitCode, 0)
  assert.deepEqual(result.exportErrors, [])
  assert.deepEqual(result.violations, [])
  assert.match(messages.log.join('\n'), /PASS — 1 turn\(s\) clean/)
  assert.deepEqual(messages.error, [])
})

await test('a trace array without the matching request row blocks after retries', async () => {
  let calls = 0
  const { logger, messages } = makeLogger()
  const result = await runSilentFailureCI({
    origin: 'https://diagnostics.invalid',
    requestIds: ['wanted-request'],
    fetchImpl: async () => {
      calls++
      return response({ trace: [{ requestId: 'some-other-request', silentFailures: [] }] })
    },
    sleep: noWait,
    logger,
  })

  assert.equal(calls, 3)
  assert.equal(result.exitCode, 1)
  assert.match(result.exportErrors[0], /no persisted trace row/)
  assert.match(messages.error.join('\n'), /BLOCK — 1 turn\(s\) could not be exported/)
})

for (const [name, makeRow] of [
  ['missing silentFailures', (requestId) => ({ requestId })],
  ['non-array silentFailures', (requestId) => ({ requestId, silentFailures: 'clean' })],
  ['non-string silentFailures entry', (requestId) => ({ requestId, silentFailures: [7] })],
]) {
  await test(`${name} blocks instead of being treated as clean`, async () => {
    let calls = 0
    const { logger, messages } = makeLogger()
    const result = await runSilentFailureCI({
      origin: 'https://diagnostics.invalid',
      requestIds: ['malformed-request'],
      fetchImpl: async () => {
        calls++
        return response({ trace: [makeRow('malformed-request')] })
      },
      sleep: noWait,
      logger,
    })

    assert.equal(calls, 3)
    assert.equal(result.exitCode, 1)
    assert.match(result.exportErrors[0], /missing a valid silentFailures array/)
    assert.match(messages.error.join('\n'), /Diagnostics export failures \(1\)/)
  })
}

await test('a persisted silent-failure verdict blocks even with a valid trace', async () => {
  const { logger, messages } = makeLogger()
  const result = await runSilentFailureCI({
    origin: 'https://diagnostics.invalid',
    requestIds: ['violating-request'],
    fetchImpl: async () => response({
      trace: [{ requestId: 'violating-request', silentFailures: ['citation_mismatch'] }],
    }),
    sleep: noWait,
    logger,
  })

  assert.equal(result.exitCode, 1)
  assert.deepEqual(result.exportErrors, [])
  assert.match(result.violations[0], /citation_mismatch/)
  assert.match(messages.error.join('\n'), /BLOCK — 1 turn\(s\) with silent-failure violations/)
})

await test('an empty request list remains explicitly neutral', async () => {
  const { logger, messages } = makeLogger()
  const result = await runSilentFailureCI({
    origin: 'https://diagnostics.invalid',
    requestIds: [],
    fetchImpl: async () => {
      throw new Error('fetch must not run for an empty request list')
    },
    sleep: noWait,
    logger,
  })

  assert.equal(result.exitCode, 0)
  assert.equal(result.requestCount, 0)
  assert.match(messages.log.join('\n'), /NEUTRAL — no request ids/)
})

if (failures > 0) {
  console.error(`\n${failures} silent-failure CI regression test(s) failed`)
  process.exitCode = 1
} else {
  console.log('\nALL SILENT-FAILURE CI REGRESSION TESTS PASS')
}
