# ATTACHMENTS.md — GS input & multimodal foundation (PHASE 5)

The cross-platform reference for the real attachment/input layer. Both the
Android (Kotlin/Compose) and iOS (SwiftUI) implementations MUST satisfy this
spec. Like `docs/ORBS.md`, this doc is the anti-drift contract.

## 0. Honest-capability statement (read first)

- **Reception is real.** Picking, staging, validating, uploading and storing
  user content are genuine, verifiable operations against the real backend.
- **Analysis does not exist.** There is NO vision, NO OCR, NO document
  intelligence, NO transcription of attached audio. The model context receives
  only an honest note: *files were attached; contents are not readable yet.*
  No UI may claim, hint or simulate otherwise.
- **No invented states.** If the client cannot distinguish a state, the UI does
  not show it. There is no server-side processing step today, so no
  "processing" state exists anywhere.
- **No invented media kinds.** `image | pdf | document` are shipped.
  `audio | video | screenshot` are reserved in the model but MUST NOT be
  offered by any picker until a real consumer exists.

## 1. Attachment data model (logical — identical on both platforms)

```text
Attachment
- id            : String   — server id once uploaded (client uuid before that)
- kind          : image | pdf | document            (extensible, see §0)
- source        : gallery | camera | files          (where it came from)
- displayName   : String  — sanitized, ≤ 200 chars
- mimeType      : String  — one of the allowlist (§3)
- byteSize      : Long/Int64
- localURL      : app-private staged copy (survives picker permission expiry)
- remoteURL     : "/api/v1/files/<id>" once uploaded (optional)
- thumbState    : none | ready | failed   (thumbnail pipeline)
- phase         : see §5 state machine
- error         : structured reason when phase == failed
```

Wire shape (server → client, exact JSON keys):

```json
{
  "id": "…", "kind": "image", "displayName": "tiny.png",
  "mimeType": "image/png", "byteSize": 33,
  "createdAt": "2026-09-12T03:03:20.500Z",
  "url": "/api/v1/files/cmtxsvxdf0003rp9gr8v93oa5"
}
```

`Message` gains optional `attachments: Attachment[]` (max 6).

## 2. Backend contract (verified end-to-end on 2026-09-12)

| Endpoint | Method | Body | Returns | Errors |
|---|---|---|---|---|
| `/api/v1/uploads` | POST | `multipart/form-data`: `file` (required), `conversationId?`, `displayName?` | `201 {attachment}` | 400, 404 (unknown conversation), 413 (>10 MB), 415 (type not allowed / spoofed), 429 |
| `/api/v1/files/{id}` | GET | — | bytes with recorded MIME | 404, 410 (bytes missing) |
| `/api/v1/conversations/{id}/messages` | POST | `{content, stream, modelId?, attachments?: string[]}` | SSE `delta/done` or JSON | 400 `invalid_attachments` (unknown / foreign / already-claimed id) |

Rules enforced by the server (clients may pre-check to fail fast, but the
server is the authority — never trust client validation):

- Size cap: **10 MB per attachment** (checked before write, re-checked on bytes).
- Max **6 attachments per message**.
- MIME allowlist: `image/jpeg, image/png, image/webp, image/gif, image/heic,
  image/heif, application/pdf, text/plain, text/markdown, text/csv`.
- Magic-byte sniff rejects spoofed types.
- An attachment can be claimed by exactly one message.
- Content may be empty **only** when `attachments` is non-empty.
- Attachment bytes are NEVER sent to the model.

## 3. Client pre-flight validation (identical rules, both platforms)

1. MIME/kind: must map to `image | pdf | document`; else → honest
   "unsupported" failure (or the picker simply cannot select it).
2. Size: > 10 MB → fail BEFORE any upload with reason `too_large`.
3. Existence + readability: the staged copy must exist and be non-empty.
4. displayName: sanitize path separators/control chars, cap length.
5. Per-message cap: 6; picker/attach flows disable further additions.

## 4. Local staging

- Picked content is COPIED into app-private storage on a background dispatcher
  (never keep picker URIs — they expire; never read originals repeatedly).
  - Android: `filesDir/attachments/<uuid>/<displayName>`
  - iOS: `Application Support/Attachments/<uuid>/<displayName>`
- byteSize comes from the staged copy (the truth), not from picker metadata.
- Staged files persist until the conversation is deleted (or the user removes
  the attachment before sending — removal deletes the staged copy when the
  attachment was never uploaded, and is purely a draft operation if it was).

## 5. State machine (honest, real transitions only)

```text
idle → selected → preparing → uploading → ready
                    │            │
                    └── failed ←─┘   (retryable: preparing/uploading)
```

| Phase | Meaning | Trigger | UI |
|---|---|---|---|
| `selected` | bytes known, staging queued | picker returned | chip appears |
| `preparing` | copying/validating on background | staging started | chip + indeterminate spinner |
| `uploading` | real HTTP multipart in flight | prepare done | chip + indeterminate spinner (NO fabricated percent) |
| `ready` | server record + `remoteURL` exist | 201 received | thumbnail/icon + name + size |
| `failed` | validation, staging, network or HTTP error | any failure | chip + error state + **Retry** + **Remove** |

- Failure taxonomy surfaced honestly: `too_large`, `unsupported`,
  `read_failed`, `network`, `server(<code>)`, `conversation_missing`.
- Retry re-runs the failed step only (prepare OR upload), never re-picks.
- Send gate: message may be sent only when every attachment is `ready`
  (or removed). Failed attachments block send with a visible reason.
- Draft persistence: staged (ready-or-failed) attachments persist with the
  draft; on restore, `ready` attachments rehydrate from local record.

## 6. Composer integration (additive only — composer architecture unchanged)

`attach → choose → preview → remove → send`:

- Attachment chips live in a single horizontal row **above** the text field,
  inside the existing composer zone. Chip: 48–56 dp/pt square thumb (real
  thumbnail for images, monochrome kind-icon for pdf/document) + name
  (1 line, ellipsized) + human byte size + remove (×) 44 pt min target.
- Max height of the row ≈ 2 chip heights; scroll horizontally; adding beyond 6
  is disabled, not silently dropped.
- The attach sheet exposes ONLY real capabilities plus honest "unavailable"
  entries:
  - REAL: Gallery (photo picker), Camera (system camera), Files (document picker)
  - UNAVAILABLE (labelled, non-functional): Voice note → actually routes to the
    real voice experience; anything without a backend consumer is either not
    shown or explicitly marked unavailable. NO fake uploads, NO simulations.

## 7. Transcript rendering (additive only — message anatomy unchanged)

- User messages render their attachments as chips/thumbnails above/with the
  text bubble. Images: real thumbnail from the staged local file (fallback:
  remoteURL). PDF/document: monochrome kind icon + name + size.
- Assistant messages carry no attachments today.
- No "analyzing…" affordances anywhere.

## 8. Thumbnails & performance safeguards

- Thumbnails: decode scaled (target ≈ 128 px), background decode, small
  in-memory LRU/NSCache, cancellation on chip removal, lifecycle-aware.
- NEVER decode full-size images on the UI/main thread; NEVER allocate
  full-size copies; NEVER rebuild the whole transcript when a chip changes;
  attachment state is scoped so recomposition stays at chip level.
- No heavy work during transcript scrolling.

## 9. Security rules (clients)

- Never trust picker display names as paths; sanitize before use.
- Never pass picker URIs across processes beyond their lifetime.
- remoteURL is always the relative `/api/v1/files/<id>`; resolve against the
  configured base URL. Never render arbitrary remote URLs from message text.
- The staged copy is app-private (no world-readable files, no FileProvider
  sharing of staged attachments except the camera capture handoff, which uses
  a dedicated cache path granted to the camera app only).

## 10. Voice (§6 decision)

- The composer mic launches the dedicated voice experience on BOTH platforms —
  the existing engines (Android `SpeechRecognizer` VoiceScreen; iOS
  `VoiceDictation`/VoiceView) remain the single source of truth. No engine
  duplication.
- Handoff harmonized: the voice result seeds the composer draft WITHOUT
  auto-send on both platforms (Android already did; iOS switches from
  auto-send-new-chat to prefill-draft).
- Activity-orb mapping unchanged: listening/processing (voice) and
  streaming states only. Upload/preparing are surfaced by the attachment chip
  states, NOT by the orb — adding upload→orb would require a new orb surface
  in the composer, which Phase 4 §7 forbids (orb never overwhelms the
  composer). No fake state transitions.

## 11. Platform parity

Logical model, state machine, limits, copy semantics and honest-capability
rules are identical; picker UI and transport implementations are native
(Android: PickVisualMedia / TakePicture+FileProvider / OpenDocument, manual
multipart over Ktor; iOS: PhotosPicker / UIImagePickerController / fileImporter,
URLSession multipart). No WebView anywhere. No cross-platform rendering code.

## 12. Deliberately NOT built in Phase 5

Paste-image into composer, share extension, drag & drop, audio/video receive,
screenshot ingest, server-side analysis of any kind, presigned-URL uploads,
CDN. Each is listed with its blocker in the PHASE 5 report (deliverable O).

## 13. Document understanding (PHASE 7 — real extraction, honest bounds)

PDF / TXT / Markdown / CSV attachments are now actually READ. The Phase 5
reception pipeline (upload, storage, chips, claim lifecycle) is unchanged;
Phase 7 adds server-side extraction and grounded answering in the normal
chat flow. No separate mode, no new routes, no client protocol change.

### Pipeline
`attachment` → ownership/claim validation (unchanged Phase 5 gates) → file
retrieval from storage → magic-byte + MIME validation (upload-time; declared
MIME parameters like `;charset=` are stripped before the allowlist check) →
real parser → bounded, normalised text → model context → streamed answer.

### Parsers (real, no simulated extraction)
- **PDF**: `unpdf` (bundled pdf.js) — per-page `getTextContent` with page
  markers `[Page N]`, reading order per pdf.js, EOL flags honoured.
  Limitations (honest): layout/columns are not reconstructed; reading order
  follows the content stream; no OCR — see failure classes below.
- **TXT / Markdown**: UTF-8 decode (BOM stripped) + whitespace normalisation.
  No markdown re-parsing — the text is the evidence.
- **CSV**: hand-rolled RFC 4180 parser (quoted fields, escaped quotes, CRLF;
  tolerant of an unterminated final quote), re-rendered as
  `Columns (N): …` / `Row k: …` lines. No dataframe inference, no type
  coercion — values are exactly what the file contains.

### Bounds (documented, enforced server-side)
| Bound | Value | Behaviour when exceeded |
|---|---|---|
| Upload size | 10 MB | rejected at upload (Phase 5, unchanged) |
| PDF pages parsed | 200 | pages beyond reported as "not loaded" |
| Chars kept per doc | 30 000 | leading pages in full, rest via excerpt map |
| Doc context per request | 60 000 chars total | deterministic split; later docs may be noted as excluded |
| CSV rows rendered | 500 | `truncatedRows` flag + visible note |
| Page excerpt in map | 160 chars, ≤25 lines | overflow noted honestly |
| Parse timeout | 20 s | `timeout` failure class |
| Extraction cache | 24 attachments, in-memory | LRU; keyed by id + byteSize + mime |

### Context strategy
Deterministic, no RAG infrastructure: current-turn documents first, then
document attachments from the most recent user turns (window of 10 turns —
follow-up questions without re-upload). Leading pages render in full; pages
beyond the budget render as a one-line excerpt map; any page the user
explicitly references ("page 14") is expanded in full from the cache when it
was loaded. The model is instructed (inline, on document turns only) to cite
`[Page N]`, to say when the provided text lacks the answer, and never to
guess unreadable attachments. Text-only turns without documents are
byte-identical to before.

### Document-only messages
One documented default: an attachment turn with no written question asks the
model "Summarise this document." (multi-document turns get the same default
plus labelled per-document sections).

### Failure classes (one honest sentence each, never fabricated content)
- `scanned` — PDF pages carry no extractable text: "…pages appear to be
  scanned images, which I can't read yet."
- `empty` — text/CSV file contains no readable text.
- `parse_failed` — real parser rejected the bytes (e.g. malformed PDF).
- `timeout` — parse exceeded the 20 s bound.
- `file_missing` — stored file is gone from the server (sandbox storage
  wipes); surfaced honestly instead of a fake answer.
All current-turn documents unreadable + no images → the turn short-circuits
with `422 document_unreadable` (non-stream) / SSE `error` (stream) carrying
the per-document sentences.

### Multiple documents
Up to the Phase 5 limit of 6. Each block is labelled
`=== Document: "name.pdf" (mime · pages/rows) ===` so the model can attribute
answers to the right file (proven by the Phase 7 probe).

### Deliberately NOT built in Phase 7
OCR, DOCX/XLSX/PPTX/EPUB, vector databases / embedding retrieval, per-user
document quotas, server-side page rendering for chips, persistent
(extraction-beyond-restart) storage of extracted text.
