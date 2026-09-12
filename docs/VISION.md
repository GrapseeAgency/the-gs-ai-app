# VISION — Image Understanding (PHASE 6)

One real multimodal capability, end-to-end: **the user attaches an image in the native Chat workspace → the backend genuinely hands the image to a vision-capable model → the model genuinely analyses it → the streamed answer renders in the existing transcript.** No simulation anywhere in the path.

This document is the audit + contract + state + failure record (Phase 6 deliverables A–K). It sits beside `ATTACHMENTS.md` (Phase 5, the receive/staging/upload layer this capability is built on).

---

## 1. Provider audit — proven, not assumed (§1)

Everything below was **verified against the installed `z-ai-web-dev-sdk@0.0.18` and live probes** during this phase. No capability was inferred from a model name.

| Question | Proven answer |
|---|---|
| Provider | Z.AI gateway via `z-ai-web-dev-sdk` (backend-only; `src/lib/ai.ts` is the sole wrapper) |
| Vision entry point | `zai.chat.completions.createVision(body)` → `POST {baseUrl}/chat/completions/vision` (SDK `dist/index.js`, read directly) |
| Serving model | **`glm-5v-turbo`** — read from the live response `model` field of real probes |
| Request format | `{ messages: VisionMessage[], stream?, thinking? }`; `VisionMessage.content` is `string` **or** an array of typed parts (`dist/index.d.ts`: `VisionMultimodalContentItem`) |
| Image input format | `{ type: 'image_url', image_url: { url } }` where `url` is a **base64 data URL** (the SDK's own vision CLI converts local files exactly this way); remote URLs also permitted |
| Model key | **Omitted** — the SDK's `model` type is required but the SDK's own CLI omits it; the endpoint then serves its compatible default. Omission keeps the pipeline resilient to gateway-side upgrades; the serving model is observable in every response |
| Streaming | **Yes** — `stream: true` + SSE/text-plain content-type returns the raw Web `ReadableStream`; chunks are OpenAI-ish `choices[0].delta.content` (probe: 12 deltas on a real image) |
| MIME support (empirical) | JPEG ✅, PNG ✅, WebP ✅ natively. **GIF ❌ provider-rejected (HTTP 400, code `1210`)** → transcoded to PNG/JPEG server-side. HEIC/HEIF: sharp decodes them (verified `heif` decoder present) → normalized to JPEG |
| Corrupt bytes | Provider answers `400 {"error":{"code":"1210","message":"图片输入格式/解析错误"}}` → pre-validated server-side with the real decoder so users get an honest message instead |
| Multi-image | ✅ verified order-preserving ("first=Red, second=Green" probe) |
| Token accounting | `usage.prompt_tokens` includes image tokens (~424 for a 640×400 probe image); payload size is bounded by normalization (below) |
| Error behaviour | Non-OK → SDK throws `Error("API request failed with status N: {body}")`; mapped to honest client sentences in the messages route |

### Empirical proof log (abridged)

| Probe | Image content (known only to the tester) | Model answer |
|---|---|---|
| Counting | 4 red circles + 2 green squares (rendered, never described in text) | "4 circles, red, 2 squares." |
| Colour+shape | single red-outline triangle | "The shape is red" (streamed) |
| Real screenshot | ChatGPT app home screen (720×1520 JPEG) | App name + "Get Plus" / "Create an image or sticker" / "Ask ChatGPT" + battery 56% — **all exact** |
| Multi-image order | red GIF first, green JPEG second | "first=Red, second=Green" |
| Honesty control | prompt asks about an image that was never sent | "I don't see any screenshot attached…" — the model does not fabricate when no image exists |
| Blank-image control | white PNG (text failed to render in the harness) | "The image is completely blank/white. There is no text visible" — no hallucinated OCR |

---

## 2. Message contract (§2)

**The wire contract did not change.** Phase 5 already shipped the canonical multimodal message: `{ content, stream?, modelId?, attachments: [attachmentIds ≤ 6] }`. The backend remains the **single source of truth** for what those ids mean:

1. Which attachments belong to the message (claim-once ownership validation, unchanged).
2. Which are images (`Attachment.kind === 'image'`, server-derived at upload).
3. Which model receives them (see §3).
4. How the bytes become provider input (see §4).

Android and iOS still send the **same logical message** as Phase 5 — zero client wire changes in Phase 6.

---

## 3. Model selection & fallback (§4/§12)

- Ordinary users never see provider names, capability matrices, or context windows. The composer, chips, and picker are unchanged.
- If the message carries images, the backend **automatically** routes the request to the vision-capable endpoint (`createVision` → `glm-5v-turbo`). This is the directive's preferred behaviour: "automatically choose the appropriate compatible model."
- There is no user-selectable non-vision model to fall back FROM: the apps send a consumer tier word (`modelId`), which the gateway-side default already supersedes for vision turns. If the vision endpoint itself fails, the user gets the honest error from §6 — never a silent text-only guess.

## 4. Image retrieval & preparation (§3/§9/§10) — `src/lib/vision.ts`

For every claimed image attachment, in order:

1. **Retrieve** the stored bytes from the private uploads root (path-escape guard inherited from Phase 5). Missing file → honest failure, no crash, no path leak.
2. **Decode with the real decoder** (sharp). This is the security boundary: the client-declared MIME type is irrelevant here — only what sharp actually decodes counts. Undecodable → `corrupt_image`.
3. **Format check** on the decoder-derived format (`jpeg|png|webp|gif|heif`); anything else → `unsupported_format`.
4. **Normalize** (bounded, real transformation): EXIF-rotate → longest edge ≤ **1568 px** (`withoutEnlargement`) → **JPEG q85**. GIF (incl. animated → first frame) and HEIC/HEIF are transcoded here because the provider rejects GIF natively and HEIC support varies.
5. **Encode** as `data:image/jpeg;base64,…` — the exact representation the SDK itself uses for local files.

Bounded memory: sequential per-image processing, normalization output ≤ ~1568 px JPEG (a few hundred KB), source buffers released after encoding. No full-size copies retained.

**Follow-up policy (§13):** the most recent `VISION_HISTORY_IMAGE_TURNS = 2` user turns that carried images get their images re-included from storage, within a per-request budget of `VISION_MAX_IMAGES_PER_REQUEST = 6` (current turn wins the budget first). This is what makes "follow-up question without re-uploading" genuinely work — proven by probe A2/A3.

## 5. Chat UX (§5/§6)

Unchanged Phase 3 workspace, Phase 5 composer: **attach image → chip/preview → optional question → send → streamed answer in the transcript.** The user never leaves Chat. Chips keep thumbnail, remove, retry (Phase 5). Image-only sends (no text) are valid; the server supplies the real instruction "Describe this image." to the model — a documented product policy, not fabricated content.

## 6. Real processing states (§7/§15) & orb mapping

| Real event | Chip/orb |
|---|---|
| Uploading | attachment chip upload state (Phase 5; no orb change) |
| Request with images at the model, pre-first-token | **orb WORKING "Working…"** — the vision model is genuinely receiving/analysing the image |
| Tokens flowing | orb COMPOSING "Composing…" (unchanged) |
| Complete / failed | orb disappears (unchanged) |

Mapping lives only in the honest mapping layer (`OrbStateMapping.kt` / `orbStateForChatStreaming` in `ThinkingOrbView.swift`), driven by a real fact of the real request (`hasImages` from `ChatStreamController.StreamState` / the pending turn's attachments). SEARCHING/SOLVING/CONNECTING/WEAVING/SHAPING remain deliberately unmapped. Unit-tested on both platforms (`OrbStateMappingTest` 8 cases / `ChatVisionMappingTests` 6 cases).

## 7. Failure handling (§8) — never a fake answer

| Failure | Behaviour |
|---|---|
| Unsupported/corrupt image (decoder) | Turn short-circuits **before** the model: `422 unsupported_media` (non-stream) or an SSE `error` event (stream) naming the file — "appears to be corrupted" / "format the assistant can't read — try JPG, PNG or WebP" |
| Mixed success (some of N images decode) | Request proceeds with the decodable ones; the text part honestly notes which file could not be opened |
| Storage bytes missing | Honest per-file note ("no longer available") |
| Provider image rejection (400/1210) | "The image could not be processed — try a JPG, PNG or WebP version." |
| Provider/network outage | "Image understanding is unavailable right now. Please try again." |
| Oversized upload | Rejected at upload time as before (413) — never reaches vision |
| MIME spoofing | Rejected at upload time (415 magic-byte sniff) and again at decode time (§4 step 2) |

Every error surfaces through the existing transcript error path on both platforms. Nothing fabricates content.

## 8. Security summary (§9)

Client-declared MIME never trusted (upload-time magic-byte sniff from Phase 5 **and** decode-time decoder validation here). Storage paths never exposed. Attachment ownership re-validated per request (claim-once). Provider payloads contain only the image bytes as data URLs — no filesystem context. Probe S1 (oversize → 413) and the spoof test (→ 415) both re-verified this phase.

## 9. Performance summary (§10/§11)

Device: unchanged Phase 5 thumbnails/staging (no full-res decode for preview, upload off the UI thread). Server: normalization bounds provider payload and image-token cost; sequential processing bounds peak memory; streaming preserved end-to-end (`stream: true` → SSE deltas → existing rich-content rendering; probe D1). The transcript is not blocked while the image is analysed — the WORKING orb communicates the wait.

## 10. Deliberately not built (STOP condition)

PDF/document intelligence (still the honest "contents not readable" note — real vision now covers images only), OCR as a product feature (the model reads text in images natively; no separate OCR pipeline exists or is simulated), Research, web search, image generation, tool execution, agent workflows, auth on uploads/files (pre-existing app-wide gap), attachment GC.
