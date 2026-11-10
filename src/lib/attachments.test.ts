import { describe, expect, test } from 'bun:test'
import { ALLOWED_MIME_TYPES, kindForMime, sniffMatchesMime, MAX_ATTACHMENT_BYTES, sanitizeDisplayName } from './attachments'
import { isDocumentAttachment } from './document'

const ascii = (text: string) => new TextEncoder().encode(text)
describe('real previewable media receive contract', () => {
  test('server and native receive formats remain explicit and bounded', () => {
    for (const mime of ['audio/mpeg', 'audio/wav', 'audio/mp4', 'video/mp4', 'video/quicktime', 'video/webm']) {
      expect(ALLOWED_MIME_TYPES.has(mime)).toBe(true)
      expect(kindForMime(mime)).toBe(mime.startsWith('audio/') ? 'audio' : 'video')
    }
    expect(ALLOWED_MIME_TYPES.has('audio/unknown')).toBe(false)
    expect(MAX_ATTACHMENT_BYTES).toBe(10 * 1024 * 1024)
  })
  test('media is not silently interpreted as document text', () => {
    expect(isDocumentAttachment({ kind: 'audio' })).toBe(false)
    expect(isDocumentAttachment({ kind: 'video' })).toBe(false)
    expect(isDocumentAttachment({ kind: 'pdf' })).toBe(true)
  })
  test('valid audio and video headers pass; random text and wrong container fail', () => {
    const fixtures: [string, Uint8Array][] = [
      ['audio/mpeg', ascii('ID3\u0004\u0000')],
      ['audio/wav', ascii('RIFF0000WAVE')],
      ['audio/mp4', ascii('0000ftypM4A ')],
      ['video/mp4', ascii('0000ftypisom')],
      ['video/quicktime', ascii('0000ftypqt  ')],
      ['video/webm', new Uint8Array([0x1a, 0x45, 0xdf, 0xa3])],
    ]
    for (const [mime, bytes] of fixtures) {
      expect(sniffMatchesMime(bytes, mime)).toBe(true)
      expect(sniffMatchesMime(ascii('this is not media'), mime)).toBe(false)
      expect(sniffMatchesMime(new Uint8Array(), mime)).toBe(false)
    }
    expect(sniffMatchesMime(ascii('RIFF0000WEBP'), 'audio/wav')).toBe(false)
    expect(sniffMatchesMime(ascii('0000ftypheic'), 'video/mp4')).toBe(false)
  })
  test('preview names cannot escape their private storage directory', () => {
    expect(sanitizeDisplayName('../../voice.m4a')).toBe('voice.m4a')
    expect(sanitizeDisplayName('..\\clip.mp4')).toBe('clip.mp4')
  })
})
