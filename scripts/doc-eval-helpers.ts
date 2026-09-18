/**
 * Test helper for scripts/document-understanding-eval.ts — runs the REAL
 * extraction engine on in-memory bytes by staging them through the real
 * storage layout (uploads/attachments/<id>/<name>), then cleaning up.
 * No production code surface is opened for tests.
 */

import fs from 'fs'
import path from 'path'
import { uploadsRoot } from '../src/lib/attachments'
import { extractDocument, type ExtractedDocument, type DocumentFailure } from '../src/lib/document'

export async function extractDocumentFromBytes(
  displayName: string,
  mimeType: string,
  kind: string,
  bytes: Uint8Array
): Promise<{ ok: true; doc: ExtractedDocument } | { ok: false; failure: DocumentFailure }> {
  const id = `doc-eval-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  const rel = path.join('attachments', id, displayName)
  const abs = path.join(uploadsRoot(), rel)
  fs.mkdirSync(path.dirname(abs), { recursive: true })
  fs.writeFileSync(abs, bytes)
  try {
    const att = {
      id,
      conversationId: null,
      messageId: null,
      kind,
      displayName,
      mimeType,
      byteSize: bytes.byteLength,
      storagePath: rel,
      width: null,
      height: null,
      createdAt: new Date(),
      // Extraction touches only the fields above on the Attachment shape.
    } as never
    return await extractDocument(att)
  } finally {
    fs.rmSync(path.dirname(abs), { recursive: true, force: true })
  }
}
