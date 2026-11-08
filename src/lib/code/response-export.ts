/** Lossless source export. These files are response snippets, not a fabricated
 * runnable project: no generated dependencies, execution results or metadata. */
export interface ResponseSourceFile { name: string; source: string }
const extensions: Record<string, string> = {
  js: 'js', javascript: 'js', node: 'js', typescript: 'ts', ts: 'ts',
  jsx: 'jsx', tsx: 'tsx', python: 'py', python3: 'py', py: 'py',
  html: 'html', htm: 'html', svg: 'svg', json: 'json', sql: 'sql',
  bash: 'sh', shell: 'sh', sh: 'sh', swift: 'swift', kotlin: 'kt',
  java: 'java', cpp: 'cpp', c: 'c', rust: 'rs', go: 'go', css: 'css',
}
export function responseSourceFile(source: string, language: string | undefined, index: number): ResponseSourceFile {
  return { name: `snippet-${index + 1}.${extensions[(language ?? '').toLowerCase()] ?? 'txt'}`, source }
}

/** Standards-compliant stored ZIP (UTF-8 names, CRC-32, central directory).
 * No external service receives the source; no compression dependency required. */
export function responseSourceArchive(files: ResponseSourceFile[]): Uint8Array {
  if (!files.length || files.length > 65534) throw new Error('No exportable source files, or too many files.')
  const encoder = new TextEncoder()
  const entries = [
    { name: 'README.txt', source: 'GS AI response source snippets\n\nThese are source snippets, not a complete runnable project.\nNo dependencies, build configuration, credentials or execution results have been invented.\nReview each file and supply its required runtime before executing it.\n' },
    ...files,
  ]
  const chunks: Uint8Array[] = [], central: Uint8Array[] = []
  let offset = 0, centralSize = 0
  const header = (size: number) => new Uint8Array(size)
  for (const file of entries) {
    if (!/^[A-Za-z0-9][A-Za-z0-9_.-]*$/.test(file.name)) throw new Error('Unsafe export filename.')
    const name = encoder.encode(file.name), data = encoder.encode(file.source)
    let crc = 0xffffffff
    for (const byte of data) {
      crc ^= byte
      for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0)
    }
    crc = (crc ^ 0xffffffff) >>> 0
    const local = header(30), l = new DataView(local.buffer)
    l.setUint32(0, 0x04034b50, true); l.setUint16(4, 20, true); l.setUint16(6, 0x800, true)
    l.setUint16(12, 33, true) // ZIP's minimum date: 1980-01-01, not a claimed creation date.
    l.setUint32(14, crc, true); l.setUint32(18, data.length, true); l.setUint32(22, data.length, true); l.setUint16(26, name.length, true)
    chunks.push(local, name, data)
    const dir = header(46), d = new DataView(dir.buffer)
    d.setUint32(0, 0x02014b50, true); d.setUint16(4, 20, true); d.setUint16(6, 20, true); d.setUint16(8, 0x800, true)
    d.setUint16(14, 33, true); d.setUint32(16, crc, true); d.setUint32(20, data.length, true); d.setUint32(24, data.length, true)
    d.setUint16(28, name.length, true); d.setUint32(42, offset, true)
    central.push(dir, name); centralSize += dir.length + name.length
    offset += local.length + name.length + data.length
  }
  const end = header(22), e = new DataView(end.buffer)
  e.setUint32(0, 0x06054b50, true); e.setUint16(8, entries.length, true); e.setUint16(10, entries.length, true)
  e.setUint32(12, centralSize, true); e.setUint32(16, offset, true)
  const result = new Uint8Array(offset + centralSize + end.length)
  let cursor = 0
  for (const chunk of [...chunks, ...central, end]) { result.set(chunk, cursor); cursor += chunk.length }
  return result
}

export function downloadResponseFile(name: string, data: string | Uint8Array, mime: string): void {
  const bytes = typeof data === 'string' ? data : new Uint8Array(data).buffer
  const url = URL.createObjectURL(new Blob([bytes], { type: mime }))
  const link = document.createElement('a')
  link.href = url; link.download = name; link.hidden = true
  document.body.appendChild(link)
  try { link.click() } finally { link.remove(); setTimeout(() => URL.revokeObjectURL(url), 30_000) }
}
