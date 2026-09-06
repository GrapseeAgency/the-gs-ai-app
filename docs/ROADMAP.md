# Roadmap

Phased delivery of the AI platform blueprint. Each phase ships working, verified software — never a big-bang rewrite.

## Phase 0 — Skeleton green ✅ (current)

- [x] Monorepo structure (android / ios / backend / contracts / docs / CI)
- [x] Android app compiles: Kotlin 2.0.20, Compose BOM 2024.09.03, Hilt, Material3, editorial theme seed, Home command-centre screen
- [x] iOS app compiles: Swift 5.9, SwiftUI, XcodeGen project, editorial tokens, Home command-centre screen
- [x] OpenAPI contract v0.1.0 (health, models, conversations, messages, SSE)
- [x] CI: Android APK artifact, iOS simulator build, backend lint

## Phase 1 — Chat MVP (walking skeleton)

- [ ] Prisma schema: Conversation, Message (+ FTS5 index groundwork)
- [ ] Backend: `/v1/conversations` + `/v1/messages` per contract; SSE streaming via LLM SDK
- [ ] Android: chat screen (streaming bubbles, stop button), real navigation (Home ↔ Chat), Room cache
- [ ] iOS: chat screen (streaming bubbles, stop button), TabView navigation, SwiftData cache
- [ ] Memory: last-N messages window

## Phase 2 — Real product surface

- [ ] Conversation management: pin, archive, folders, search (FTS5), rename, delete
- [ ] Model Centre v1: list, capabilities, default model, fast/balanced/deep routing
- [ ] Assistants: create/edit persona, system-prompt templates
- [ ] Guardrails: input filtering at gateway, rate limiting per device
- [ ] Prompt caching for the system prompt prefix
- [ ] Settings architecture (appearance, chat, AI, privacy, security)
- [ ] Auth (device-based first), onboarding flow

## Phase 3 — Platform depth

- [ ] Explore marketplace + assistant publishing
- [ ] Create workspaces (image gen, doc generation, prompt builder)
- [ ] Library (saved messages, documents, collections, doc Q&A)
- [ ] Projects (chats + files + instructions bundle)
- [ ] Global search across entities
- [ ] Voice mode (full-screen, interrupt → WebSocket)
- [ ] Vision workspace (camera/gallery analysis, OCR)
- [ ] Research mode (web search, citations, source cards)
- [ ] Coding environment (interpreter, diffs, file tree)
- [ ] Sharing (links, QR, export PDF/MD/TXT/JSON)
- [ ] Billing tiers, usage metering
- [ ] Long-term vector memory (hybrid memory ladder)

## Sequencing rule

Phase order follows the playbook: prove → MVP → harden → scale. No phase starts until the previous one is verified in CI **and** browser/device QA.
