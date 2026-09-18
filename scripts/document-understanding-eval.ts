/**
 * PHASE 7 — document understanding evaluation.
 *
 * Two layers, run in order:
 *   UNIT   — pure-function bounds on the extraction engine (CSV parser,
 *            page-reference extraction, page cap, CSV row cap). No server.
 *   E2E    — REAL documents with pre-planted unique facts, uploaded through
 *            the live HTTP API, answered through the real streaming chat
 *            path. Every predicate below was fixed BEFORE the first run.
 *
 * The decisive §18 proof lives here: atlas.pdf carries
 *   "Project Atlas" / "Budget: $42,750" / "Launch date: 18 October" /
 *   "Owner: Grapsee"
 * and the grounded-answer + follow-up-without-re-upload predicates are
 * asserted against those exact strings.
 *
 * Usage: bun scripts/document-understanding-eval.ts [--base http://localhost:3000]
 * Exit code 0 only if every pre-registered predicate passed.
 */

import { PDFDocument, StandardFonts, rgb } from 'pdf-lib'
import fs from 'fs'
import path from 'path'
import { db } from '../src/lib/db'
import { uploadsRoot } from '../src/lib/attachments'
import { parseCsv, referencedPages, clearDocumentCache, MAX_PDF_PAGES } from '../src/lib/document'

const BASE = process.argv.includes('--base')
  ? process.argv[process.argv.indexOf('--base') + 1]
  : 'http://localhost:3000'
const SESSION = 'doc-eval-phase7'
const UPLOADS = uploadsRoot()

// ---------------------------------------------------------------- result book
type CaseResult = { id: string; label: string; pass: boolean; detail: string }
const results: CaseResult[] = []
function record(id: string, label: string, pass: boolean, detail: string) {
  results.push({ id, label, pass, detail })
  console.log(`${pass ? 'PASS' : 'FAIL'}  ${id}  ${label}${pass ? '' : `\n      ↳ ${detail}`}`)
}

// ---------------------------------------------------------------- fixtures
function textPage(doc: PDFDocument, font: PDFFontHolder, lines: string[], y0 = 160, mono = false) {
  const page = doc.addPage([420, 240])
  const f = mono ? font.mono : font.helv
  lines.forEach((line, i) => {
    page.drawText(line, { x: 24, y: y0 - i * 18, size: 11, font: f, color: rgb(0, 0, 0) })
  })
  return page
}
type PDFFontHolder = { helv: PDFStandardFont; mono: PDFStandardFont }
type PDFStandardFont = Awaited<ReturnType<PDFDocument['embedFont']>>

async function buildPdf(pages: string[][]): Promise<Uint8Array> {
  const doc = await PDFDocument.create()
  const helv = await doc.embedFont(StandardFonts.Helvetica)
  const mono = await doc.embedFont(StandardFonts.Courier)
  for (const lines of pages) textPage(doc, { helv, mono }, lines)
  return doc.save()
}

/** A "scanned" PDF: pages with vector ink but zero text operators. */
async function buildScannedPdf(pageCount = 2): Promise<Uint8Array> {
  const doc = await PDFDocument.create()
  for (let i = 0; i < pageCount; i++) {
    const page = doc.addPage([420, 240])
    page.drawRectangle({ x: 20, y: 20, width: 380, height: 200, borderColor: rgb(0, 0, 0), borderWidth: 1 })
    page.drawLine({ start: { x: 40, y: 180 }, end: { x: 380, y: 60 }, thickness: 2 })
  }
  return doc.save()
}

// ---------------------------------------------------------------- http helpers
type AttachmentJson = { id: string; kind: string; displayName: string; mimeType: string; byteSize: number }

async function upload(name: string, mime: string, bytes: Uint8Array): Promise<AttachmentJson> {
  const form = new FormData()
  form.append('file', new Blob([new Uint8Array(bytes)], { type: mime }), name)
  form.append('displayName', name)
  let res = await fetch(`${BASE}/api/v1/uploads`, {
    method: 'POST',
    headers: { 'x-session-id': SESSION },
    body: form,
  })
  if (res.status === 429) {
    await new Promise((r) => setTimeout(r, 2500))
    res = await fetch(`${BASE}/api/v1/uploads`, {
      method: 'POST',
      headers: { 'x-session-id': SESSION },
      body: form,
    })
  }
  if (res.status !== 201) throw new Error(`upload ${name} → ${res.status} ${await res.text()}`)
  const json = (await res.json()) as { attachment: AttachmentJson }
  return json.attachment
}

async function createConversation(): Promise<string> {
  const res = await fetch(`${BASE}/api/v1/conversations`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'x-session-id': SESSION },
    body: JSON.stringify({}),
  })
  if (res.status !== 201 && res.status !== 200) throw new Error(`conversation → ${res.status}`)
  const json = (await res.json()) as { conversation?: { id: string }; id?: string }
  return json.conversation?.id ?? json.id!
}

type StreamResult = { text: string; error: string | null; done: boolean }

async function sendMessage(convId: string, content: string, attachmentIds: string[] | null): Promise<StreamResult> {
  const post = () =>
    fetch(`${BASE}/api/v1/conversations/${convId}/messages`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', 'x-session-id': SESSION },
      body: JSON.stringify({
        content,
        stream: true,
        ...(attachmentIds && attachmentIds.length > 0 ? { attachments: attachmentIds } : {}),
      }),
    })
  // The upstream model provider throttles bursty probe traffic — one spaced retry.
  let res = await post()
  if (res.status === 429) {
    await new Promise((r) => setTimeout(r, 20_000))
    res = await post()
  }
  if (res.status !== 200) throw new Error(`message → ${res.status} ${await res.text()}`)
  const reader = res.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let text = ''
  let error: string | null = null
  let done = false
  const deadline = Date.now() + 120_000
  while (!done && !error && Date.now() < deadline) {
    const { value, done: streamDone } = await reader.read()
    if (streamDone) break
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''
    for (const line of lines) {
      const trimmed = line.trim()
      if (!trimmed.startsWith('data:')) continue
      try {
        const evt = JSON.parse(trimmed.slice(5).trim()) as { event: string; data: string }
        if (evt.event === 'delta') text += evt.data
        else if (evt.event === 'error') error = evt.data
        else if (evt.event === 'done') done = true
      } catch {
        /* keep-alive noise */
      }
    }
  }
  return { text, error, done }
}

const createdConversations: string[] = []
const uploadedIds: string[] = []
async function cleanup() {
  for (const id of createdConversations) {
    await fetch(`${BASE}/api/v1/conversations/${id}`, {
      method: 'DELETE',
      headers: { 'x-session-id': SESSION },
    }).catch(() => undefined)
  }
  for (const id of uploadedIds) {
    await db.attachment.deleteMany({ where: { id } }).catch(() => undefined)
    fs.rmSync(path.join(UPLOADS, 'attachments', id), { recursive: true, force: true })
  }
}

const has = (text: string, ...needles: string[]) =>
  needles.every((n) => text.toLowerCase().includes(n.toLowerCase()))
/** Breathe between model calls — the upstream provider throttles bursts. */
const pause = (ms = 8000) => new Promise((r) => setTimeout(r, ms))

// ================================================================ UNIT LAYER
async function unitLayer() {
  console.log('\n——— UNIT: pure extraction bounds ———')

  // CSV parser: quotes, embedded commas, escaped quotes, CRLF, trailing newline
  const rows = parseCsv('a,b\n"x,y",z\r\n"say ""hi""",1\n')
  record(
    'U1',
    'parseCsv RFC4180 (quotes, CRLF, escaped quotes)',
    rows.length === 3 &&
      rows[0][0] === 'a' &&
      rows[0][1] === 'b' &&
      rows[1][0] === 'x,y' &&
      rows[1][1] === 'z' &&
      rows[2][0] === 'say "hi"' &&
      rows[2][1] === '1',
    JSON.stringify(rows)
  )

  // Page-reference extraction
  const refs = referencedPages('What does page 14 say about pricing? Also check page 3, and page 012 vs page 999.')
  record(
    'U2',
    'referencedPages deterministic (14, 3, 12; excludes 999)',
    refs.join(',') === '14,3,12',
    JSON.stringify(refs)
  )

  // PDF page cap: a 220-page PDF parses only 200 pages, rest reported unloaded
  const manyPages = Array.from({ length: 220 }, (_, i) => [`Filler page ${i + 1} of the cap fixture.`])
  const capBytes = await buildPdf(manyPages)
  const { extractDocumentFromBytes } = await import('./doc-eval-helpers')
  const capDoc = await extractDocumentFromBytes('cap.pdf', 'application/pdf', 'pdf', capBytes)
  record(
    'U3',
    `PDF page cap (${MAX_PDF_PAGES}) + honest notLoaded tail`,
    capDoc.ok &&
      capDoc.doc.totalPages === 220 &&
      capDoc.doc.pages.length === MAX_PDF_PAGES &&
      capDoc.doc.notLoadedPages[0] === MAX_PDF_PAGES + 1 &&
      capDoc.doc.notLoadedPages[capDoc.doc.notLoadedPages.length - 1] === 220 &&
      capDoc.doc.truncated,
    capDoc.ok
      ? `pages=${capDoc.doc.pages.length} notLoaded=${capDoc.doc.notLoadedPages.length} truncated=${capDoc.doc.truncated}`
      : 'extraction failed'
  )

  // CSV row cap: 700 data rows → rendered rows capped, flag set
  const bigCsv = ['Region,Revenue,Units', ...Array.from({ length: 700 }, (_, i) => `R${i + 1},${1000 + i},${i % 9}`)].join('\n')
  const csvDoc = await extractDocumentFromBytes('big.csv', 'text/csv', 'document', new TextEncoder().encode(bigCsv))
  record(
    'U4',
    'CSV row cap (500 rendered of 700) + truncatedRows flag',
    csvDoc.ok && csvDoc.doc.csv?.rowCount === 700 && csvDoc.doc.csv.truncatedRows === true && !csvDoc.doc.pages[0].text.includes('Row 502:'),
    csvDoc.ok ? `rowCount=${csvDoc.doc.csv?.rowCount} truncated=${csvDoc.doc.csv?.truncatedRows}` : 'extraction failed'
  )

  clearDocumentCache()
}

// ================================================================== E2E LAYER
async function e2eLayer() {
  console.log('\n——— E2E: real upload → real extraction → real grounded answers ———')

  // §18 fixture: Project Atlas
  const atlasBytes = await buildPdf([
    ['Project Atlas — charter'],
    ['Budget: $42,750'],
    ['Launch date: 18 October'],
    ['Owner: Grapsee'],
  ])
  const pagesBytes = await buildPdf([['Company: Apple'], ['Company: Microsoft'], ['Company: Amazon']])
  // 40 dense pages ≈ 36k chars — EXCEEDS the 30k per-doc extraction cap, so
  // this fixture exercises the bounded strategy: leading pages in full,
  // excerpt map for the rest, honest notLoaded tail, page-ref expansion.
  const bigPages = Array.from({ length: 40 }, (_, i) => {
    const lines = [`Page ${i + 1} of the long report.`]
    if (i === 16) lines.push('Unique marker: PAGE17-TOKEN-ZEBRA stands here.')
    for (let j = 0; j < 15; j++) {
      lines.push(`Routine paragraph ${i + 1}.${j + 1} — ${'lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod '.repeat(1)}`)
    }
    return lines
  })
  const bigBytes = await buildPdf(bigPages)
  const tableBytes = await buildPdf([
    ['Region | Revenue | Units', 'West   | 41250   | 7', 'East   | 39800   | 6', 'North  | 31500   | 5'],
  ])
  const scannedBytes = await buildScannedPdf(2)
  const malformedBytes = new TextEncoder().encode('%PDF-1.4\n%âãÏÓ\nthis file claims to be a pdf but the body is truncated garbage with no xref')
  const notesBytes = new TextEncoder().encode('# Release Notes\n\nCodename: Bluebird.\nShip window: November.\n')
  const csvBytes = new TextEncoder().encode('Region,Revenue,Units\nNorth,31500,5\nWest,41250,7\nEast,39800,6\nSouth,28900,4\n')
  const pricingBytes = await buildPdf([
    ['Pricing schedule'],
    ['Annual subscription price: $1,200 per workspace.'],
    ['Monthly plan: $120.'],
  ])

  const atlas = await upload('atlas.pdf', 'application/pdf', atlasBytes)
  const pages = await upload('pages.pdf', 'application/pdf', pagesBytes)
  const big = await upload('long-report.pdf', 'application/pdf', bigBytes)
  const table = await upload('table.pdf', 'application/pdf', tableBytes)
  const scanned = await upload('scanned.pdf', 'application/pdf', scannedBytes)
  const malformed = await upload('malformed.pdf', 'application/pdf', malformedBytes)
  const notes = await upload('notes.md', 'text/markdown', notesBytes)
  const csv = await upload('data.csv', 'text/csv', csvBytes)
  const contract = await upload('contract.pdf', 'application/pdf', atlasBytes)
  const pricing = await upload('pricing.pdf', 'application/pdf', pricingBytes)
  uploadedIds.push(atlas.id, pages.id, big.id, table.id, scanned.id, malformed.id, notes.id, csv.id, contract.id, pricing.id)

  // --- §18: grounded answer + follow-up WITHOUT re-upload --------------------
  const c1 = await createConversation()
  createdConversations.push(c1)
  const r1 = await sendMessage(c1, 'What is the budget?', [atlas.id])
  await pause()
  record(
    'E1',
    'PDF grounded answer ("What is the budget?") → $42,750',
    r1.error === null && (has(r1.text, '42,750') || has(r1.text, '42750')),
    r1.error ?? r1.text.slice(0, 300)
  )

  const r2 = await sendMessage(c1, 'When is the launch date?', null) // NO re-upload
  await pause()
  record('E2', 'Follow-up without re-upload → 18 October (grounded, no re-upload)', r2.error === null && has(r2.text, '18 October'), r2.error ?? r2.text.slice(0, 300))

  const r3 = await sendMessage(c1, 'Who is the owner named in the document?', null)
  await pause()
  record('E3', 'Second follow-up still grounded → Grapsee', r3.error === null && has(r3.text, 'Grapsee'), r3.error ?? r3.text.slice(0, 300))

  // --- §4: page-specific question --------------------------------------------
  const c2 = await createConversation()
  createdConversations.push(c2)
  const r4 = await sendMessage(c2, 'Which company is mentioned on page 2?', [pages.id])
  await pause()
  record('E4', 'Page-specific question → Microsoft (from page 2)', r4.error === null && has(r4.text, 'Microsoft'), r4.error ?? r4.text.slice(0, 300))

  // --- §11: large doc + explicit page expansion -------------------------------
  const r5 = await sendMessage(c2, 'What does page 17 of the long report say?', [big.id])
  await pause()
  record('E5', 'Page-ref expansion on over-cap PDF → PAGE17-TOKEN-ZEBRA', r5.error === null && has(r5.text, 'PAGE17-TOKEN-ZEBRA'), r5.error ?? r5.text.slice(0, 300))

  // --- §17: table-containing PDF ----------------------------------------------
  const c3 = await createConversation()
  createdConversations.push(c3)
  const r6 = await sendMessage(c3, 'Which region has the highest revenue in the attached document?', [table.id])
  await pause()
  record(
    'E6',
    'Table PDF grounded → West / 41250',
    r6.error === null && has(r6.text, 'West') && (has(r6.text, '41250') || has(r6.text, '41,250')),
    r6.error ?? r6.text.slice(0, 300)
  )

  // --- §9: CSV numeric + row lookup -------------------------------------------
  const r7 = await sendMessage(c3, 'What is the highest value in the Revenue column of the CSV?', [csv.id])
  await pause()
  record(
    'E7',
    'CSV numeric question → 41250',
    r7.error === null && (has(r7.text, '41250') || has(r7.text, '41,250')),
    r7.error ?? r7.text.slice(0, 300)
  )
  const r8 = await sendMessage(c3, 'How many units does South have?', null)
  await pause()
  record('E8', 'CSV row lookup follow-up → 4', r8.error === null && /\b4\b/.test(r8.text), r8.error ?? r8.text.slice(0, 300))

  // --- §9: Markdown ------------------------------------------------------------
  const c4 = await createConversation()
  createdConversations.push(c4)
  const r9 = await sendMessage(c4, 'What is the release codename?', [notes.id])
  await pause()
  record('E9', 'Markdown grounded → Bluebird', r9.error === null && has(r9.text, 'Bluebird'), r9.error ?? r9.text.slice(0, 300))

  // --- §7: document-only message (no question written) -------------------------
  // Attachments are single-claim — a document-only turn needs its own upload.
  const atlas2 = await upload('atlas-brief.pdf', 'application/pdf', atlasBytes)
  uploadedIds.push(atlas2.id)
  const r10 = await sendMessage(c4, '', [atlas2.id])
  await pause()
  record(
    'E10',
    'Document-only message → real summary grounded in Atlas',
    r10.error === null && r10.text.trim().length > 40 && has(r10.text, 'Atlas'),
    r10.error ?? (r10.text.trim().length > 0 ? r10.text.slice(0, 300) : 'EMPTY REPLY')
  )

  // --- §8: multiple documents, distinguishable ---------------------------------
  const c5 = await createConversation()
  createdConversations.push(c5)
  const r11 = await sendMessage(c5, 'Which document mentions the annual subscription price?', [contract.id, pricing.id])
  await pause()
  record(
    'E11',
    'Multi-document: correct document identified (pricing.pdf)',
    r11.error === null && has(r11.text, 'pricing.pdf') && (has(r11.text, '1,200') || has(r11.text, '1200')),
    r11.error ?? r11.text.slice(0, 300)
  )

  // --- §16: scanned PDF → honest limitation, never fabricated -------------------
  // The predicate keys on the scanned-CLASS sentence ("…scanned images, which
  // I can't read yet") — never on the fixture's filename.
  const c6 = await createConversation()
  createdConversations.push(c6)
  const r12 = await sendMessage(c6, 'Summarise this document.', [scanned.id])
  record(
    'E12',
    'Scanned PDF → honest scanned-page failure (no fabricated content)',
    r12.error !== null && r12.error.toLowerCase().includes('scanned images'),
    r12.error ?? `MODEL ANSWERED INSTEAD OF FAILING: ${r12.text.slice(0, 200)}`
  )
  await pause()

  // --- §16: malformed PDF → safe failure ----------------------------------------
  const r13 = await sendMessage(c6, 'What does this document say?', [malformed.id])
  record(
    'E13',
    'Malformed PDF → honest parse failure',
    r13.error !== null && r13.error.toLowerCase().includes('could not be parsed'),
    r13.error ?? `MODEL ANSWERED INSTEAD OF FAILING: ${r13.text.slice(0, 200)}`
  )

  // --- §16: empty text file → honest failure ------------------------------------
  const emptyTxt = await upload('blank.txt', 'text/plain', new TextEncoder().encode('   \n\n   \n'))
  uploadedIds.push(emptyTxt.id)
  const r14 = await sendMessage(c6, 'Summarise this document.', [emptyTxt.id])
  record(
    'E14',
    'Empty text file → honest "no readable text" failure',
    r14.error !== null && r14.error.toLowerCase().includes('no readable text'),
    r14.error ?? `MODEL ANSWERED INSTEAD OF FAILING: ${r14.text.slice(0, 200)}`
  )
  await pause()

  // --- §6: follow-up on a document-only conversation (no re-upload) --------------
  await pause()
  const r15 = await sendMessage(c4, 'What is the ship window in the markdown file?', null)
  record('E15', 'Follow-up after doc-only message → November (no re-upload)', r15.error === null && has(r15.text, 'November'), r15.error ?? r15.text.slice(0, 300))
}

// ---------------------------------------------------------------- main
async function main() {
  console.log(`PHASE 7 document-understanding eval → ${BASE}`)
  await unitLayer()
  await e2eLayer()
  const failed = results.filter((r) => !r.pass)
  console.log(`\n==== ${results.length - failed.length}/${results.length} predicates passed ====`)
  if (failed.length > 0) {
    console.log('FAILED:')
    for (const f of failed) console.log(`  ${f.id} ${f.label}`)
  }
  await cleanup()
  console.log('probe artifacts cleaned (conversations + attachments + files)')
  process.exit(failed.length > 0 ? 1 : 0)
}

main().catch(async (e) => {
  console.error('eval crashed:', e)
  await cleanup()
  process.exit(2)
})
