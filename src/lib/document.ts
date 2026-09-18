/**
 * PHASE 7 — real document understanding. Turns stored PDF/TXT/MD/CSV
 * attachments into bounded, page-preserving text context that the chat model
 * can actually answer from.
 *
 * Honest-capability rules (phase §2–§5):
 * - Text is extracted from the real stored bytes by a real parser (unpdf's
 *   pdf.js build for PDFs; UTF-8 decode for text; a strict RFC 4180 parser
 *   for CSV). Nothing here invents, infers or OCRs content.
 * - A PDF whose loaded pages contain no text at all is reported as scanned
 *   imagery — never silently treated as readable.
 * - Parsing is bounded (page cap, char cap, timeout) and cached per
 *   attachment so follow-up messages do not re-parse the same file.
 * - Context injection is deterministic: leading pages in full, a bounded
 *   excerpt map for the rest, and full text of any page the user explicitly
 *   references by number. No vector database, no retrieval infrastructure.
 */

import type { Attachment } from '@prisma/client'
import { readAttachmentFile } from '@/lib/attachments'

// ---- bounds (documented in docs/ATTACHMENTS.md §Document understanding) ----

/** Hard page cap for a single PDF — pages beyond this are never parsed. */
export const MAX_PDF_PAGES = 200
/** Hard cap on extracted characters kept per document (extraction + cache). */
export const MAX_CHARS_PER_DOC = 30_000
/** Total characters of document context injected per chat request. */
export const MESSAGE_DOC_BUDGET = 60_000
/** Characters per page in the excerpt map shown for pages not rendered in full. */
const PAGE_MAP_EXCERPT = 160
/** Maximum number of individual excerpt lines in a page map. */
const MAX_MAP_LINES = 25
/** Follow-up window: document-bearing user turns re-included for follow-ups. */
export const DOC_HISTORY_TURNS = 10
/** Parser wall-clock bound per document. */
export const PARSE_TIMEOUT_MS = 20_000
/** A PDF whose loaded pages carry fewer than this many chars total is scanned. */
const MIN_SCANNED_CHARS = 20
/** Maximum rows rendered from a CSV. */
const CSV_MAX_ROWS = 500
/** In-memory extraction cache bound (attachments). */
const CACHE_MAX = 24

// ---- types ----

export type DocumentPage = { n: number; text: string; empty: boolean; loaded: boolean }

export type ExtractedDocument = {
  attachmentId: string
  displayName: string
  mimeType: string
  /** 1 for text/CSV "documents" — real page count for PDFs. */
  totalPages: number
  pages: DocumentPage[]
  charsLoaded: number
  /** Page numbers that were never parsed (page cap / char cap). */
  notLoadedPages: number[]
  truncated: boolean
  csv: { columns: string[]; rowCount: number; truncatedRows: boolean } | null
  extractedAtMs: number
}

export type DocumentFailureReason = 'scanned' | 'empty' | 'parse_failed' | 'timeout' | 'file_missing'

export type DocumentFailure = {
  attachmentId: string
  displayName: string
  reason: DocumentFailureReason
}

/** §16 — one honest sentence per failure class; never a fabricated answer. */
export function documentFailureMessage(f: DocumentFailure): string {
  const name = `"${f.displayName}"`
  switch (f.reason) {
    case 'scanned':
      return `${name} contains no extractable text — its pages appear to be scanned images, which I can't read yet.`
    case 'empty':
      return `${name} contains no readable text.`
    case 'parse_failed':
      return `${name} could not be parsed as a document.`
    case 'timeout':
      return `${name} took too long to process and was not read.`
    case 'file_missing':
      return `${name} is no longer available on the server and can't be read.`
  }
}

// ---- in-memory cache (single server process; bounded) ----

type CacheEntry = ExtractedDocument & { byteSize: number; storedMime: string }

const cache = new Map<string, CacheEntry>()
const inFlight = new Map<string, Promise<ExtractedDocument | DocumentFailure>>()

function cacheGet(att: Attachment): ExtractedDocument | null {
  const hit = cache.get(att.id)
  if (!hit) return null
  // Invalidate if the record no longer matches the bytes it was extracted from.
  if (hit.byteSize !== att.byteSize || hit.storedMime !== att.mimeType) {
    cache.delete(att.id)
    return null
  }
  // LRU touch.
  cache.delete(att.id)
  cache.set(att.id, hit)
  return hit
}

function cacheSet(doc: ExtractedDocument, att: Attachment): void {
  cache.set(att.id, { ...doc, byteSize: att.byteSize, storedMime: att.mimeType })
  if (cache.size > CACHE_MAX) {
    const oldest = cache.keys().next().value
    if (oldest !== undefined) cache.delete(oldest)
  }
}

// ---- text normalisation ----

/** Bounded normalisation: CRLF → LF, strip NULs, collapse 3+ blank lines. */
function normalizeText(raw: string): string {
  return raw
    .replace(/\u0000/g, '')
    .replace(/\r\n?/g, '\n')
    .split('\n')
    .map((line) => line.replace(/[ \t]+$/g, ''))
    .join('\n')
    .replace(/\n{3,}/g, '\n\n')
}

// ---- CSV (RFC 4180, tolerant of an unterminated final quote) ----

export function parseCsv(input: string): string[][] {
  const rows: string[][] = []
  let row: string[] = []
  let field = ''
  let inQuotes = false
  let i = 0
  const pushField = () => {
    row.push(field)
    field = ''
  }
  const pushRow = () => {
    pushField()
    rows.push(row)
    row = []
  }
  while (i < input.length) {
    const ch = input[i]
    if (inQuotes) {
      if (ch === '"') {
        if (input[i + 1] === '"') {
          field += '"'
          i += 2
          continue
        }
        inQuotes = false
        i += 1
        continue
      }
      field += ch
      i += 1
      continue
    }
    if (ch === '"') {
      inQuotes = true
      i += 1
      continue
    }
    if (ch === ',') {
      pushField()
      i += 1
      continue
    }
    if (ch === '\n' || ch === '\r') {
      if (ch === '\r' && input[i + 1] === '\n') i += 1
      pushRow()
      i += 1
      continue
    }
    field += ch
    i += 1
  }
  // Tolerant EOF: an unterminated quote still yields the field/row collected.
  if (field.length > 0 || row.length > 0 || input.endsWith('"')) pushRow()
  // Drop a single trailing fully-empty row produced by a final newline.
  if (rows.length > 0 && rows[rows.length - 1].length === 1 && rows[rows.length - 1][0] === '') {
    rows.pop()
  }
  return rows
}

function renderCsv(rows: string[][]): { text: string; columns: string[]; rowCount: number; truncatedRows: boolean } {
  const header = rows[0] ?? []
  const dataRows = rows.slice(1)
  const shown = dataRows.slice(0, CSV_MAX_ROWS)
  const lines = [`Columns (${header.length}): ${header.join(' | ')}`]
  shown.forEach((r, idx) => lines.push(`Row ${idx + 2}: ${r.join(' | ')}`))
  return {
    text: lines.join('\n'),
    columns: header,
    rowCount: dataRows.length,
    truncatedRows: dataRows.length > shown.length,
  }
}

// ---- PDF extraction (real parser: unpdf's bundled pdf.js) ----

async function extractPdfPages(bytes: Uint8Array): Promise<{
  totalPages: number
  pages: DocumentPage[]
  charsLoaded: number
  notLoadedPages: number[]
  truncated: boolean
}> {
  const { getDocumentProxy } = await import('unpdf')
  const pdf = await getDocumentProxy(bytes)
  const totalPages = pdf.numPages
  if (!Number.isFinite(totalPages) || totalPages <= 0) {
    throw new Error('pdf has no pages')
  }
  const limit = Math.min(totalPages, MAX_PDF_PAGES)
  const pages: DocumentPage[] = []
  let charsLoaded = 0
  let truncated = limit < totalPages

  for (let n = 1; n <= limit; n++) {
    if (charsLoaded >= MAX_CHARS_PER_DOC) {
      truncated = true
      break
    }
    const page = await pdf.getPage(n)
    const tc = await page.getTextContent()
    let text = ''
    for (const item of tc.items as { str?: unknown; hasEOL?: unknown }[]) {
      const str = typeof item.str === 'string' ? item.str : ''
      text += str + (item.hasEOL ? '\n' : ' ')
    }
    text = normalizeText(text)
    if (charsLoaded + text.length > MAX_CHARS_PER_DOC) {
      text = text.slice(0, Math.max(0, MAX_CHARS_PER_DOC - charsLoaded))
      truncated = true
    }
    charsLoaded += text.length
    pages.push({ n, text, empty: text.trim().length === 0, loaded: true })
  }
  // Every page between the last processed one and the end was never parsed.
  const notLoadedPages: number[] = []
  for (let m = pages.length + 1; m <= totalPages; m++) notLoadedPages.push(m)
  return { totalPages, pages, charsLoaded, notLoadedPages, truncated }
}

// ---- main entry ----

async function extractOnce(att: Attachment): Promise<ExtractedDocument | DocumentFailure> {
  const startedAt = Date.now()
  const base = { attachmentId: att.id, displayName: att.displayName }
  let bytes: Buffer
  try {
    bytes = await readAttachmentFile(att.storagePath)
  } catch {
    return { ...base, reason: 'file_missing' }
  }

  try {
    const result = await Promise.race([
      extractByMime(att, bytes),
      new Promise<never>((_, reject) =>
        setTimeout(() => reject(new Error('document parse timeout')), PARSE_TIMEOUT_MS)
      ),
    ])
    const doc = result as ExtractedDocument
    cacheSet(doc, att)
    console.log(
      `[documents] extracted id=${att.id} mime=${att.mimeType} pages=${doc.totalPages} chars=${doc.charsLoaded} truncated=${doc.truncated} ms=${Date.now() - startedAt}`
    )
    return doc
  } catch (e) {
    const raw = e instanceof Error ? e.message : String(e)
    const flags = e as { scanned?: unknown; empty?: unknown }
    let reason: DocumentFailureReason = 'parse_failed'
    if (raw === 'document parse timeout') reason = 'timeout'
    else if (flags.scanned === true) reason = 'scanned'
    else if (flags.empty === true) reason = 'empty'
    console.log(`[documents] extraction failed id=${att.id} reason=${reason} raw=${raw.slice(0, 120)}`)
    return { ...base, reason }
  }
}

async function extractByMime(att: Attachment, bytes: Buffer): Promise<ExtractedDocument> {
  const base = {
    attachmentId: att.id,
    displayName: att.displayName,
    mimeType: att.mimeType,
    extractedAtMs: Date.now(),
  }

  if (att.mimeType === 'application/pdf' || att.kind === 'pdf') {
    const { totalPages, pages, charsLoaded, notLoadedPages, truncated } = await extractPdfPages(
      new Uint8Array(bytes)
    )
    // Scanned detection: the pages we actually loaded (all of them, unless the
    // 30k-char cap hit — which scanned PDFs never reach) carry no text at all.
    const loadedText = pages.map((p) => p.text).join('')
    if (loadedText.trim().length < MIN_SCANNED_CHARS) {
      throw Object.assign(new Error('scanned pdf'), { scanned: true })
    }
    return { ...base, totalPages, pages, charsLoaded, notLoadedPages, truncated, csv: null }
  }

  // text/plain · text/markdown · text/csv — the simplest correct path: real
  // text read (CSV additionally gets a real RFC 4180 parse), no PDF tooling.
  const raw = bytes.subarray(0, Math.min(bytes.length, MAX_CHARS_PER_DOC * 4)).toString('utf-8').replace(/^\uFEFF/, '')
  const text = normalizeText(raw)
  if (text.trim().length === 0) {
    throw Object.assign(new Error('empty document'), { empty: true })
  }
  if (att.mimeType === 'text/csv') {
    const rows = parseCsv(text)
    if (rows.length === 0) {
      throw Object.assign(new Error('csv had no rows'), { empty: true })
    }
    const rendered = renderCsv(rows)
    return {
      ...base,
      totalPages: 1,
      pages: [{ n: 1, text: rendered.text, empty: false, loaded: true }],
      charsLoaded: rendered.text.length,
      notLoadedPages: [],
      truncated: rendered.truncatedRows,
      csv: { columns: rendered.columns, rowCount: rendered.rowCount, truncatedRows: rendered.truncatedRows },
    }
  }
  const capped = text.slice(0, MAX_CHARS_PER_DOC)
  return {
    ...base,
    totalPages: 1,
    pages: [{ n: 1, text: capped, empty: false, loaded: true }],
    charsLoaded: capped.length,
    notLoadedPages: [],
    truncated: text.length > capped.length,
    csv: null,
  }
}

/**
 * Extract (cache-first, in-flight deduped) one attachment.
 * Resolves `{ ok: true, doc }` or `{ ok: false, failure }` — never throws.
 */
export async function extractDocument(
  att: Attachment
): Promise<{ ok: true; doc: ExtractedDocument } | { ok: false; failure: DocumentFailure }> {
  const cached = cacheGet(att)
  if (cached) return { ok: true, doc: cached }
  const pending = inFlight.get(att.id)
  if (pending) {
    const result = await pending
    return isExtracted(result)
      ? { ok: true, doc: result }
      : { ok: false, failure: result as DocumentFailure }
  }
  const job = extractOnce(att).finally(() => inFlight.delete(att.id))
  inFlight.set(att.id, job)
  const result = await job
  return isExtracted(result)
    ? { ok: true, doc: result }
    : { ok: false, failure: result as DocumentFailure }
}

/** True when a stored attachment is a document kind this phase understands. */
export function isDocumentAttachment(a: { kind: string }): boolean {
  return a.kind === 'pdf' || a.kind === 'document'
}

function isExtracted(r: ExtractedDocument | DocumentFailure): r is ExtractedDocument {
  return 'pages' in r
}

// ---- context rendering ----

const DOCUMENT_GROUNDING_INSTRUCTION =
  'The sections below are text extracted server-side from documents attached to this conversation. They are primary evidence: ground every answer about the documents in this extracted text, cite PDF pages as [Page N] when a page supports your answer, and say plainly when the provided text does not contain the answer. Never invent document content. Attachments described as unreadable have no available text — do not guess them.'

function excerpt(text: string, len: number): string {
  const flat = text.replace(/\s+/g, ' ').trim()
  return flat.length > len ? `${flat.slice(0, len)}…` : flat
}

function pageLabel(doc: ExtractedDocument, p: DocumentPage): string {
  if (!p.loaded) return `[Page ${p.n}] (not loaded — document exceeds the extraction cap)`
  if (p.empty) return `[Page ${p.n}] (no extractable text on this page — possibly scanned imagery)`
  return `[Page ${p.n}]\n${p.text}`
}

function renderDocumentBlock(doc: ExtractedDocument, allowance: number, refPages: number[]): { block: string; used: number } {
  const meta = doc.csv
    ? `${doc.csv.rowCount} data rows, ${doc.csv.columns.length} columns`
    : `${doc.totalPages} page${doc.totalPages === 1 ? '' : 's'}`
  const parts: string[] = [`=== Document: "${doc.displayName}" (${doc.mimeType} · ${meta}) ===`]
  let used = 0
  const rendered = new Set<number>()

  // CSV renders as one pre-structured block (columns + rows), bounded.
  if (doc.csv) {
    const only = doc.pages[0]
    const text = only?.text ?? ''
    if (text.length <= allowance) {
      parts.push(text)
      used = text.length
    } else {
      parts.push(
        `${text.slice(0, Math.max(0, allowance - 120))}\n[…CSV truncated for context — total rows: ${doc.csv.rowCount}.]`
      )
      used = allowance
    }
    if (doc.csv.truncatedRows) parts.push(`(Only the first ${CSV_MAX_ROWS} rows are shown.)`)
    return { block: parts.join('\n\n'), used }
  }

  const pushPage = (p: DocumentPage): boolean => {
    const renderedText = pageLabel(doc, p)
    if (used + renderedText.length > allowance) return false
    parts.push(renderedText)
    used += renderedText.length
    rendered.add(p.n)
    return true
  }

  // Pages the user explicitly referenced win the budget first.
  for (const n of refPages) {
    const p = doc.pages.find((pg) => pg.n === n)
    if (!p) {
      const notLoaded = doc.notLoadedPages.includes(n)
      if (notLoaded) parts.push(`[Page ${n}] (not loaded — document exceeds the extraction cap)`)
      else if (n > doc.totalPages) parts.push(`[Page ${n}] (the document has only ${doc.totalPages} page${doc.totalPages === 1 ? '' : 's'})`)
      continue
    }
    if (!pushPage(p)) {
      parts.push(`[Page ${n}] (context budget reached — excerpt) ${excerpt(p.text, PAGE_MAP_EXCERPT)}`)
      rendered.add(p.n)
    }
  }

  // Leading pages in full, oldest first, until the allowance is spent.
  for (const p of doc.pages) {
    if (rendered.has(p.n)) continue
    if (p.empty) continue
    if (!pushPage(p)) break
  }

  // Excerpt map for loaded pages not rendered in full.
  const unmapped = doc.pages.filter((p) => !rendered.has(p.n))
  const mapLines: string[] = unmapped.slice(0, MAX_MAP_LINES).map((p) =>
    p.empty
      ? `[Page ${p.n}] (no extractable text — possibly scanned imagery)`
      : `[Page ${p.n}] ${excerpt(p.text, PAGE_MAP_EXCERPT)}`
  )
  if (unmapped.length > MAX_MAP_LINES) {
    mapLines.push(`[… ${unmapped.length - MAX_MAP_LINES} more pages not shown individually — ask for a specific page number.]`)
  }
  if (doc.notLoadedPages.length > 0) {
    const first = doc.notLoadedPages[0]
    const last = doc.notLoadedPages[doc.notLoadedPages.length - 1]
    mapLines.push(`[Pages ${first}–${last}] (not loaded — document exceeds the extraction cap)`)
  }
  if (mapLines.length > 0) {
    parts.push(
      `(Document context is bounded; the excerpt map below covers the pages not shown in full — ask about a specific page number for its text.)\n${mapLines.join('\n')}`
    )
  }
  return { block: parts.join('\n\n'), used }
}

/** Pull "page 14"-style references out of the user's message. Deterministic. */
export function referencedPages(userText: string): number[] {
  const refs: number[] = []
  const re = /\bpage\s+(\d{1,3})\b/gi
  let m: RegExpExecArray | null
  while ((m = re.exec(userText)) !== null) {
    const n = Number.parseInt(m[1], 10)
    if (Number.isFinite(n) && n >= 1 && n <= MAX_PDF_PAGES && !refs.includes(n)) refs.push(n)
  }
  return refs
}

export type CollectedDocumentContext = {
  /** Assembled, bounded document context (empty when nothing readable). */
  block: string | null
  /** Honest per-document failures to surface to the user. */
  failures: DocumentFailure[]
  /** Documents that produced readable context on THIS collection. */
  readableCount: number
}

/**
 * Build the bounded document context for one chat request.
 *
 * Order is deterministic: current-turn attachments first (attachment order),
 * then document attachments from the most recent user turns (follow-up
 * support, §6 — no re-upload required). Budget: MESSAGE_DOC_BUDGET total,
 * split evenly across remaining documents with the current turn winning
 * early allocations. Small documents fit entirely; large ones render their
 * leading pages in full plus a page excerpt map, and any page the user
 * explicitly referenced is always expanded when cached.
 */
export async function collectDocumentContext(
  currentTurnDocs: Attachment[],
  historyDocTurns: { attachments: Attachment[] }[], // most recent first
  userText: string
): Promise<CollectedDocumentContext> {
  const ordered: Attachment[] = []
  const seen = new Set<string>()
  for (const a of [...currentTurnDocs, ...historyDocTurns.flatMap((t) => t.attachments)]) {
    if (!isDocumentAttachment(a) || seen.has(a.id)) continue
    seen.add(a.id)
    ordered.push(a)
  }
  if (ordered.length === 0) return { block: null, failures: [], readableCount: 0 }

  const refs = referencedPages(userText)
  const sections: string[] = [DOCUMENT_GROUNDING_INSTRUCTION]
  const failures: DocumentFailure[] = []
  let readableCount = 0
  let budgetLeft = MESSAGE_DOC_BUDGET

  for (let i = 0; i < ordered.length; i++) {
    const att = ordered[i]
    const remainingDocs = ordered.length - i
    const share = Math.floor(budgetLeft / remainingDocs)
    const allowance = Math.max(Math.min(share, MAX_CHARS_PER_DOC), 1_200)
    const result = await extractDocument(att)
    if (!result.ok) {
      failures.push(result.failure)
      sections.push(`=== Document: "${att.displayName}" ===\n(not readable: ${documentFailureMessage(result.failure)})`)
      continue
    }
    readableCount += 1
    const { block, used } = renderDocumentBlock(result.doc, Math.min(allowance, budgetLeft), refs)
    budgetLeft = Math.max(0, budgetLeft - used)
    sections.push(block)
    if (budgetLeft <= 0 && i < ordered.length - 1) {
      sections.push(`(Document context budget exhausted — "${ordered[i + 1].displayName}" was not included in this request.)`)
      break
    }
  }

  return { block: sections.join('\n\n'), failures, readableCount }
}

/** Clear the extraction cache (used by probes/tests only). */
export function clearDocumentCache(): void {
  cache.clear()
  inFlight.clear()
}
