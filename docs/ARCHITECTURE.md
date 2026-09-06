# Architecture

The five-layer model applied to this repository.

## The five layers

```
┌──────────────────────────────────────────────────────────┐
│  1. PRODUCT      android/ (Kotlin · Compose)             │
│                  ios/     (Swift · SwiftUI)              │
├──────────────────────────────────────────────────────────┤
│  2. BRAIN        orchestrator: system prompts, RAG,      │
│                  tool calling, model routing             │
│                  (lives in backend, src/lib/ai)          │
├──────────────────────────────────────────────────────────┤
│  3. BACKEND      Next.js route handlers = API gateway +  │
│                  orchestrator (auth, rate limit,         │
│                  streaming, persistence)                 │
├──────────────────────────────────────────────────────────┤
│  4. INFERENCE    model providers (rented brains)         │
├──────────────────────────────────────────────────────────┤
│  5. DATA         SQLite now → Postgres + Redis + vector  │
│                  DB at scale; S3-compatible object store │
└──────────────────────────────────────────────────────────┘
```

## Repo mapping

| Folder | Layer | Notes |
|--------|-------|-------|
| `android/` | Product | Kotlin, Jetpack Compose, Hilt, Clean Architecture + MVVM |
| `ios/` | Product | Swift, SwiftUI, SwiftData-ready, XcodeGen-generated project |
| root (Next.js) | Backend | API gateway + orchestrator + streaming (route handlers) |
| `shared-contracts/` | Contract | OpenAPI — the only thing platforms share |
| `infrastructure/` | Ops | Docker/Postgres/Redis definitions (planned) |
| `.github/workflows/` | CI | Android (ubuntu), iOS (macOS), backend (ubuntu) |

**Principle:** Android remains Android. iOS remains iOS. Backend remains platform-independent. No pseudo-cross-platform UI layer.

## Request lifecycle (chat message)

1. Client hits API gateway — auth, rate limit, validation
2. Orchestrator loads conversation history, applies system prompt, decides retrieval/tools
3. Assembled prompt goes to the inference layer
4. Tokens stream back (SSE) as generated
5. Finished message persisted; long-term memory updated

## Streaming rule

- **SSE** for question-in / answer-streams-out (default)
- **WebSocket** when the client must talk mid-stream: stop-generating, live tool updates, voice mode

## Memory ladder (planned, in order)

1. Last-N verbatim messages (Phase 1)
2. Rolling session summary (Phase 2)
3. Vector long-term memory, cross-session (Phase 3)
4. Hybrid: verbatim buffer + summary + retrieved facts (target state)

## Speed levers (owned by backend, not clients)

Continuous batching + PagedAttention (provider-side), prompt caching of the system prompt prefix, speculative decoding (provider-side), model routing (fast/balanced/deep tiers — surfaces in the Model Centre UX).

## CI/CD

- Every push to `main` builds what changed:
  - `android/**` → APK artifact on Ubuntu runners
  - `ios/**` → unsigned simulator build on macOS runners (free: public repo)
  - backend paths → lint/typecheck
- Releases/signing come later via Fastlane + secrets (no secrets in repo).
