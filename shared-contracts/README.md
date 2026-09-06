# Shared API Contracts

Single source of truth for the API between the native clients (Android/iOS) and the backend.

## Principle

**Contract-first, platform-independent.** Android remains Android, iOS remains iOS — the only thing they share is this contract.

- `openapi.yaml` — REST + SSE endpoints
- Backend implements it (Next.js route handlers)
- Clients consume it (Ktor on Android/iOS)
- Codegen (later): `openapi-generator` → Kotlin data classes, Swift OpenAPI Generator → Swift types

## Current surface (v0.1.0)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/health` | GET | Liveness probe |
| `/v1/models` | GET | Available models + capabilities |
| `/v1/conversations` | GET / POST | List / create conversations |
| `/v1/conversations/{id}` | GET / DELETE | Fetch / delete one conversation |
| `/v1/conversations/{id}/messages` | GET / POST | History / send message (SSE stream when `stream=true`) |

## Streaming rule (from architecture lessons)

- `POST /v1/conversations/{id}/messages` with `stream: true` responds `text/event-stream`
- Server → client only; client interrupt (stop-generating) will move to WebSocket when that feature lands
