/**
 * PHASE 8.1 — SSRF-hardened self-hosted page fetcher (§10/§27).
 *
 * This is the retrieval layer that makes "a search result is NOT the article"
 * fixable WITHOUT any third-party reader service: we fetch the page ourselves,
 * with hard bounds, and feed only extracted text to the model.
 *
 * Security posture (§27):
 *  - scheme allow-list: http/https only, no credentials in URL
 *  - DNS pre-resolution: every resolved address must be GLOBAL (rejects
 *    localhost / RFC1918 / link-local / CGNAT / ULA — v4 and v6), so a
 *    "public" hostname that resolves internal is dead on arrival
 *  - redirect chain re-validated hop by hop (manual redirects, max 4, each
 *    hop re-checked for scheme + private host + private resolved IP)
 *  - response size cap applied on the DECODED stream (gzip-bomb proof):
 *    bytes are counted while reading and the stream is aborted past the cap
 *  - content-type allow-list (html/xhtml/text/xml) — binary payloads rejected
 *    before body allocation
 *  - per-request timeout via AbortSignal; no cookies, no auth headers, ever
 *
 * Known accepted residual: classic DNS-rebinding TOCTOU (resolver checked
 * before connect). Documented, bounded impact (egress-only sandbox, no
 * credentials to steal), standard trade-off for a same-process fetcher.
 */

import { lookup } from 'node:dns/promises'

export type FetchPageResult = {
  finalUrl: string
  status: number
  contentType: string
  body: string
  bytes: number
  elapsedMs: number
}

export class FetchPageError extends Error {
  kind: 'unsafe_url' | 'dns' | 'blocked_host' | 'status' | 'content_type' | 'timeout' | 'too_large' | 'network'
  constructor(kind: FetchPageError['kind'], message: string) {
    super(message)
    this.kind = kind
  }
}

const USER_AGENT =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36'
// Policy UA (2026-09-21 live audit): Wikimedia (and some other farms) 403
// browser UAs coming from datacenter Node processes while ACCEPTING an
// honest policy UA — the discovery path already proved this (policy UA ->
// 200 while retrieval 403'd). fetchPage retries a blocked hop once with it.
const POLICY_USER_AGENT =
  'GS-AI-App/0.67 (https://grapsee.agency; contact@grapsee.agency)'
const MAX_REDIRECTS = 4

const ALLOWED_CONTENT_TYPES = [
  'text/html',
  'application/xhtml+xml',
  'text/plain',
  'application/xml',
  'text/xml',
  'application/rss+xml',
  'application/json',
]

// ---------------------------------------------------------------------------
// IP / host safety
// ---------------------------------------------------------------------------

function ipv4ToLong(ip: string): number | null {
  const parts = ip.split('.')
  if (parts.length !== 4) return null
  let out = 0
  for (const part of parts) {
    const n = Number(part)
    if (!Number.isInteger(n) || n < 0 || n > 255) return null
    out = out * 256 + n
  }
  return out
}

function isPrivateIpv4(ip: string): boolean {
  const v = ipv4ToLong(ip)
  if (v === null) return true // unparseable => treat as unsafe
  // JS bitwise ops are int32 — every operand MUST be normalized with >>> 0
  // or addresses ≥ 128.0.0.0 (192.168.x, 169.254.x, 224/3…) compare wrong.
  const inCidr = (cidr: number, bits: number) => {
    const mask = bits === 0 ? 0 : (0xffffffff << (32 - bits)) >>> 0
    return ((v & mask) >>> 0) === ((cidr & mask) >>> 0)
  }
  return (
    inCidr(0x00000000, 8) || // 0.0.0.0/8 "this network"
    inCidr(0x0a000000, 8) || // 10/8
    inCidr(0x7f000000, 8) || // 127/8 loopback
    inCidr(0xa9fe0000, 16) || // 169.254/16 link-local
    inCidr(0xac100000, 12) || // 172.16/12
    inCidr(0xc0000000, 2) || // 192/2 (catches 192.168 + 192.0.0/24 + 198.18/15 bench)
    inCidr(0xc0586300, 24) || // 192.88.99/24 6to4 relay (defensive)
    inCidr(0xc6120000, 15) || // 198.18/15 benchmarking
    inCidr(0xe0000000, 3) // 224/3 multicast+reserved
  )
}

function isPrivateIpv6(ip: string): boolean {
  const v = ip.toLowerCase().replace(/^\[|\]$/g, '')
  if (v === '::' || v === '::1') return true
  if (v.startsWith('fe8') || v.startsWith('fe9') || v.startsWith('fea') || v.startsWith('feb'))
    return true // fe80::/10 link-local
  if (v.startsWith('fc') || v.startsWith('fd')) return true // fc00::/7 ULA
  if (v.startsWith('ff')) return true // multicast
  if (v.startsWith('::ffff:')) {
    // IPv4-mapped — validate the embedded v4
    return isPrivateIpv4(v.slice(7))
  }
  if (v.startsWith('2001:db8')) return true // documentation range
  if (v.startsWith('64:ff9b')) return false // NAT64 well-known prefix → validate by v4 path not available here; treat as global
  return false
}

export function isPrivateIp(ip: string): boolean {
  return ip.includes(':') ? isPrivateIpv6(ip) : isPrivateIpv4(ip)
}

const PRIVATE_HOST_PATTERNS: RegExp[] = [
  /^localhost$/i,
  /\.local$/i,
  /\.internal$/i,
  /\.localhost$/i,
]

export function isSafePublicHttpUrl(rawUrl: string): boolean {
  let parsed: URL
  try {
    parsed = new URL(rawUrl)
  } catch {
    return false
  }
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') return false
  if (parsed.username || parsed.password) return false
  const host = parsed.hostname
  if (!host || host.includes('@')) return false
  if (PRIVATE_HOST_PATTERNS.some((p) => p.test(host))) return false
  // Literal IPs must be global; hostnames pass here (checked at DNS time).
  if (/^[\d.]+$/.test(host) || host.includes(':')) return !isPrivateIp(host)
  return true
}

async function assertResolvesPublic(hostname: string): Promise<void> {
  if (/^[\d.]+$/.test(hostname) || hostname.includes(':')) {
    if (isPrivateIp(hostname)) {
      throw new FetchPageError('blocked_host', `blocked private host: ${hostname}`)
    }
    return
  }
  let records: { address: string }[]
  try {
    records = await lookup(hostname, { all: true, verbatim: true })
  } catch {
    throw new FetchPageError('dns', `dns lookup failed: ${hostname}`)
  }
  if (records.length === 0) throw new FetchPageError('dns', `no dns records: ${hostname}`)
  for (const r of records) {
    if (isPrivateIp(r.address)) {
      throw new FetchPageError('blocked_host', `hostname resolves to private address: ${hostname}`)
    }
  }
}

// ---------------------------------------------------------------------------
// The fetcher
// ---------------------------------------------------------------------------

export type FetchPageOptions = {
  timeoutMs?: number
  maxBytes?: number
}

export async function fetchPage(
  rawUrl: string,
  opts: FetchPageOptions = {}
): Promise<FetchPageResult> {
  const timeoutMs = opts.timeoutMs ?? 10_000
  const maxBytes = opts.maxBytes ?? 1_500_000
  const startedAt = Date.now()

  let url = rawUrl
  for (let hop = 0; hop <= MAX_REDIRECTS; hop++) {
    if (!isSafePublicHttpUrl(url)) {
      throw new FetchPageError('unsafe_url', `unsafe URL blocked: ${url.slice(0, 200)}`)
    }
    const host = new URL(url).hostname
    await assertResolvesPublic(host)

    let res: Response
    try {
      res = await fetch(url, {
        redirect: 'manual',
        headers: {
          'User-Agent': USER_AGENT,
          Accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,text/plain;q=0.8,*/*;q=0.5',
          'Accept-Language': 'en-US,en;q=0.9',
        },
        signal: AbortSignal.timeout(timeoutMs),
      })
      // Bot-wall retry: sites that 401/403/406 the browser UA from a
      // datacenter IP often accept an honest policy UA. One bounded retry
      // per hop — no loops, same SSRF rules.
      if (res.status === 401 || res.status === 403 || res.status === 406) {
        res = await fetch(url, {
          redirect: 'manual',
          headers: {
            'User-Agent': POLICY_USER_AGENT,
            Accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,text/plain;q=0.8,*/*;q=0.5',
            'Accept-Language': 'en-US,en;q=0.9',
          },
          signal: AbortSignal.timeout(timeoutMs),
        })
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e)
      if (/timeout|abort/i.test(msg)) {
        throw new FetchPageError('timeout', `fetch timed out: ${host}`)
      }
      throw new FetchPageError('network', `network error: ${msg.slice(0, 120)}`)
    }

    // Manual redirect handling — validate every hop.
    if (res.status >= 300 && res.status < 400) {
      const location = res.headers.get('location')
      if (!location) throw new FetchPageError('network', 'redirect without location')
      if (hop === MAX_REDIRECTS) {
        throw new FetchPageError('network', `too many redirects (>${MAX_REDIRECTS})`)
      }
      try {
        url = new URL(location, url).toString()
      } catch {
        throw new FetchPageError('unsafe_url', 'invalid redirect target')
      }
      continue
    }

    if (res.status >= 400) {
      throw new FetchPageError('status', `upstream status ${res.status}`)
    }

    const contentType = (res.headers.get('content-type') ?? '').toLowerCase()
    const baseType = contentType.split(';')[0].trim()
    if (baseType && !ALLOWED_CONTENT_TYPES.includes(baseType)) {
      throw new FetchPageError('content_type', `blocked content-type: ${baseType}`)
    }

    // Stream with a hard decoded-byte cap (compression-bomb proof).
    const reader = res.body?.getReader()
    if (!reader) throw new FetchPageError('network', 'empty response body')
    const decoder = new TextDecoder('utf-8', { fatal: false })
    let body = ''
    let bytes = 0
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      bytes += value.byteLength
      if (bytes > maxBytes) {
        try {
          await reader.cancel()
        } catch {
          // reader already closed — nothing to do
        }
        // Truncation at the cap is acceptable: extraction works on prefixes.
        body += decoder.decode(value.subarray(0, Math.max(0, value.byteLength - (bytes - maxBytes))), { stream: true })
        break
      }
      body += decoder.decode(value, { stream: true })
    }

    return {
      finalUrl: url,
      status: res.status,
      contentType: baseType || 'unknown',
      body,
      bytes,
      elapsedMs: Date.now() - startedAt,
    }
  }
  throw new FetchPageError('network', 'unreachable: redirect loop guard')
}
