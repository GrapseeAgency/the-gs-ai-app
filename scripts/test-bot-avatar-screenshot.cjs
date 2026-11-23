/*
 * Mobile browser regression for the real bot-avatars component in the actual
 * chat page. The backend transport is held behind deterministic Playwright
 * request interception; the React chat, BotAvatar canvas, CSS layout and state
 * transitions are real. A screenshot is only accepted after the browser paints
 * non-transparent green pixels from the library canvas and proves that canvas
 * sits beside the assistant reply, outside the composer.
 */
const assert = require('node:assert/strict')
const { spawn } = require('node:child_process')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const zlib = require('node:zlib')
const chromium = require('@sparticuz/chromium')
const puppeteer = require('puppeteer-core')

const ROOT = path.resolve(__dirname, '..')
const HOST = '127.0.0.1'
const PORT = Number(process.env.PORT || 3217)
const ORIGIN = `http://${HOST}:${PORT}`
const ANSWER = 'They only sign annual, Dana approves, and pricing is the same thread as last quarter. I answered without waiting on you.'
const OUTPUT_DIR = process.env.SCREENSHOT_DIR || path.join(os.tmpdir(), 'bot-avatar-screenshots')

function json(request, body) {
  return request.respond({
    status: 200,
    contentType: 'application/json; charset=utf-8',
    body: JSON.stringify(body),
  })
}

async function withTimeout(promise, milliseconds, message) {
  let timer
  try {
    return await Promise.race([
      promise,
      new Promise((_, reject) => { timer = setTimeout(() => reject(new Error(message)), milliseconds) }),
    ])
  } finally {
    clearTimeout(timer)
  }
}

async function waitForServer(child, url) {
  const deadline = Date.now() + 90_000
  let lastError
  while (Date.now() < deadline) {
    if (child.exitCode !== null) throw new Error(`Next.js exited early with code ${child.exitCode}`)
    try {
      const response = await fetch(url)
      if (response.ok) return
      lastError = new Error(`Next.js returned HTTP ${response.status}`)
    } catch (error) {
      lastError = error
    }
    await new Promise((resolve) => setTimeout(resolve, 500))
  }
  throw new Error(`Next.js did not become ready at ${url}: ${lastError?.message ?? 'timeout'}`)
}

async function inspectPaintedAvatar(page, state) {
  const selector = `[data-testid="chat-panel"] canvas[data-bot-avatar="clover"][data-state="${state}"]`
  await page.waitForSelector(selector, { timeout: 20_000 })
  await page.waitForFunction((query) => {
    const canvas = document.querySelector(query)
    if (!(canvas instanceof HTMLCanvasElement) || getComputedStyle(canvas).opacity !== '1') return false
    const context = canvas.getContext('2d')
    if (!context || !canvas.width || !canvas.height) return false
    const pixels = context.getImageData(0, 0, canvas.width, canvas.height).data
    for (let i = 3; i < pixels.length; i += 4) if (pixels[i] > 0) return true
    return false
  }, { timeout: 20_000 }, selector)

  return page.$eval(selector, (canvas) => {
    const context = canvas.getContext('2d')
    const pixels = context.getImageData(0, 0, canvas.width, canvas.height).data
    let paintedPixels = 0
    let greenPixels = 0
    for (let i = 0; i < pixels.length; i += 4) {
      const r = pixels[i]
      const g = pixels[i + 1]
      const b = pixels[i + 2]
      const a = pixels[i + 3]
      if (a > 0) paintedPixels++
      if (a > 0 && g > r * 1.08 && g > b * 1.04) greenPixels++
    }
    const rect = canvas.getBoundingClientRect()
    return {
      state: canvas.dataset.state,
      role: canvas.getAttribute('role'),
      ariaLabel: canvas.getAttribute('aria-label'),
      opacity: getComputedStyle(canvas).opacity,
      cssSize: { width: rect.width, height: rect.height },
      backingSize: { width: canvas.width, height: canvas.height },
      paintedPixels,
      greenPixels,
    }
  })
}

async function scrollChatIntoView(page) {
  await page.$eval('[data-testid="chat-panel"]', (panel) => {
    const transcript = panel.querySelector('.gs-scroll.overflow-y-auto')
    if (transcript) transcript.scrollTop = transcript.scrollHeight
    panel.scrollIntoView({ block: 'end', inline: 'nearest' })
  })
  await new Promise((resolve) => setTimeout(resolve, 100))
}

async function main() {
  fs.mkdirSync(OUTPUT_DIR, { recursive: true })
  const libDir = fs.mkdtempSync(path.join(os.tmpdir(), 'bot-avatar-browser-libs-'))
  const chromiumBin = path.resolve(path.dirname(require.resolve('@sparticuz/chromium')), '../../bin')
  const archive = path.join(libDir, 'al2023.tar')
  fs.writeFileSync(archive, zlib.brotliDecompressSync(fs.readFileSync(path.join(chromiumBin, 'al2023.tar.br'))))
  require('node:child_process').execFileSync('tar', ['-xf', archive, '-C', libDir])

  const next = spawn(process.execPath, [
    require.resolve('next/dist/bin/next'), 'dev', '--hostname', HOST, '--port', String(PORT),
  ], {
    cwd: ROOT,
    env: {
      ...process.env,
      DATABASE_URL: process.env.DATABASE_URL || `file:${path.join(os.tmpdir(), 'bot-avatar-ci.db')}`,
      NEXT_TELEMETRY_DISABLED: '1',
    },
    stdio: 'inherit',
  })

  let browser
  let releaseMessageResponse
  let notifyMessageRequest
  const messageRequestStarted = new Promise((resolve) => { notifyMessageRequest = resolve })
  const messageResponseGate = new Promise((resolve) => { releaseMessageResponse = resolve })
  let messageRequestCount = 0

  try {
    await waitForServer(next, ORIGIN)
    browser = await puppeteer.launch({
      executablePath: await chromium.executablePath(),
      args: chromium.args.filter((arg) => arg !== '--disable-web-security'),
      headless: true,
      env: {
        ...process.env,
        LD_LIBRARY_PATH: `${path.join(libDir, 'lib')}:${process.env.LD_LIBRARY_PATH || ''}`,
      },
    })

    const page = await browser.newPage()
    await page.setViewport({ width: 390, height: 844, deviceScaleFactor: 2, isMobile: true, hasTouch: true })
    const pageErrors = []
    page.on('pageerror', (error) => pageErrors.push(error.message))
    page.on('requestfailed', (request) => console.error('[browser request failed]', request.method(), request.url(), request.failure()?.errorText))
    await page.setRequestInterception(true)
    page.on('request', (request) => {
      if (request.url().includes('/api/')) console.log('[browser API request]', request.method(), request.url())
      ;(async () => {
        const url = new URL(request.url())
        const method = request.method()
        if (url.pathname === '/api/health' && method === 'GET') {
          await json(request, { status: 'ok', backgroundRuns: false })
          return
        }
        if (url.pathname === '/api/v1/conversations' && method === 'GET') {
          await json(request, { items: [], nextCursor: null })
          return
        }
        if (url.pathname === '/api/v1/conversations' && method === 'POST') {
          await json(request, { id: 'bot-avatar-ci-conversation' })
          return
        }
        if (url.pathname === '/api/v1/assistants' && method === 'GET') {
          await json(request, { items: [] })
          return
        }
        if (/^\/api\/v1\/conversations\/[^/]+\/messages$/.test(url.pathname) && method === 'GET') {
          await json(request, { items: [] })
          return
        }
        if (/^\/api\/v1\/conversations\/[^/]+\/messages$/.test(url.pathname) && method === 'POST') {
          messageRequestCount++
          notifyMessageRequest()
          await messageResponseGate
          const frames = [
            { event: 'delta', data: ANSWER },
            { event: 'done', data: { id: 'bot-avatar-ci-reply', content: ANSWER, sources: [] } },
          ]
          const body = frames.map((frame) => `data: ${JSON.stringify(frame)}\n\n`).join('')
          await request.respond({
            status: 200,
            headers: {
              'Content-Type': 'text/event-stream; charset=utf-8',
              'Cache-Control': 'no-cache, no-transform',
            },
            body,
          })
          return
        }
        await request.continue()
      })().catch((error) => {
        console.error('Request interception failed:', error)
        if (!request.isInterceptResolutionHandled()) request.abort('failed').catch(() => {})
      })
    })

    await page.goto(ORIGIN, { waitUntil: 'networkidle2', timeout: 90_000 })
    const inputSelector = '[data-testid="chat-composer"] input[aria-label="Message GS AI"]'
    const sendSelector = '[data-testid="chat-composer"] button[aria-label="Send"]'
    await page.waitForSelector(inputSelector, { timeout: 30_000 })
    await page.type(inputSelector, 'Can you summarise the thread with Acme?')
    await page.waitForFunction((selector) => {
      const button = document.querySelector(selector)
      return button instanceof HTMLButtonElement && !button.disabled
    }, { timeout: 15_000 }, sendSelector)
    await page.focus(inputSelector)
    await page.keyboard.press('Enter')

    await withTimeout(messageRequestStarted, 30_000, 'Chat did not start its message request')
    assert.equal(messageRequestCount, 1, 'expected exactly one real chat send request')

    await page.waitForSelector('[data-testid="assistant-streaming-row"] canvas[data-state="working"]', { timeout: 20_000 })
    await scrollChatIntoView(page)
    const working = await inspectPaintedAvatar(page, 'working')
    assert.equal(working.role, 'img')
    assert.ok(working.paintedPixels > 100, `working canvas should paint real pixels: ${JSON.stringify(working)}`)
    assert.ok(working.greenPixels > 20, `working canvas should paint the green clover: ${JSON.stringify(working)}`)
    assert.equal(await page.$('[data-testid="chat-composer"] canvas[data-bot-avatar]'), null,
      'the avatar must never be rendered inside the composer')
    await page.screenshot({ path: path.join(OUTPUT_DIR, 'bot-avatar-working-mobile.png') })
    console.log('PASS: real working BotAvatar painted beside the active chat reply on a 390px mobile viewport', JSON.stringify(working))

    releaseMessageResponse()
    await page.waitForFunction((answer) => {
      const panel = document.querySelector('[data-testid="chat-panel"]')
      return panel?.innerText.includes(answer) && panel.querySelector('[data-testid="assistant-reply-row"] canvas[data-state="default"]')
    }, { timeout: 30_000 }, ANSWER)
    await scrollChatIntoView(page)
    const reply = await inspectPaintedAvatar(page, 'default')
    assert.equal(reply.role, 'img')
    assert.ok(reply.paintedPixels > 100, `reply canvas should paint real pixels: ${JSON.stringify(reply)}`)
    assert.ok(reply.greenPixels > 20, `reply canvas should paint the green clover: ${JSON.stringify(reply)}`)

    const placement = await page.$eval('[data-testid="assistant-reply-row"]', (row) => {
      const avatar = row.querySelector('canvas[data-bot-avatar="clover"]')
      const reply = row.querySelector('[data-testid="assistant-reply"]')
      if (!avatar || !reply) throw new Error('Assistant reply row is missing its library avatar or reply content')
      const avatarRect = avatar.getBoundingClientRect()
      const replyRect = reply.getBoundingClientRect()
      return {
        avatarLeft: avatarRect.left,
        avatarRight: avatarRect.right,
        replyLeft: replyRect.left,
        replyTop: replyRect.top,
        avatarTop: avatarRect.top,
        insideComposer: Boolean(avatar.closest('[data-testid="chat-composer"]')),
      }
    })
    assert.ok(placement.avatarRight <= placement.replyLeft + 2, `avatar must sit to the left of its reply: ${JSON.stringify(placement)}`)
    assert.ok(!placement.insideComposer, 'assistant avatar must be outside the composer')
    assert.ok(placement.avatarTop < placement.replyTop + 30, `avatar should align with the start of the reply: ${JSON.stringify(placement)}`)
    const replyTextColor = await page.$eval('[data-testid="assistant-reply"]', (reply) => {
      const prose = reply.querySelector(':scope > div > div')
      if (!prose) throw new Error('Assistant reply has no rendered prose block')
      return getComputedStyle(prose).color
    })
    assert.equal(replyTextColor, 'rgb(20, 23, 28)',
      `reply prose should use readable ink on the light chat surface, got ${replyTextColor}`)
    assert.deepEqual(pageErrors, [], `browser page errors: ${pageErrors.join('; ')}`)
    await page.screenshot({ path: path.join(OUTPUT_DIR, 'bot-avatar-reply-mobile.png') })
    console.log('PASS: actual assistant reply has the library avatar at its left, outside composer', JSON.stringify(placement))
    console.log('PASS: assistant reply prose has high-contrast text on the light app surface', replyTextColor)
    console.log('PASS: mobile screenshots saved in', OUTPUT_DIR)
  } finally {
    releaseMessageResponse?.()
    await browser?.close().catch(() => {})
    next.kill('SIGTERM')
    await new Promise((resolve) => {
      if (next.exitCode !== null) return resolve()
      const timer = setTimeout(resolve, 5_000)
      next.once('exit', () => { clearTimeout(timer); resolve() })
    })
    fs.rmSync(libDir, { recursive: true, force: true })
  }
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
