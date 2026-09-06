# Project Worklog — the-gs-ai-app

Shared handover document for all agents working on this project.

---

Task ID: 1
Agent: Z.ai Code (main)
Task: Set up GitHub repository sync for the sandbox project

Work Log:
- Configured git identity: Grapsee-Official <graphesee@gmail.com> (local config in /home/z/my-project)
- Added remote `origin` → https://github.com/GrapseeAgency/the-gs-ai-app.git (auth token embedded in remote URL for persistent push access)
- Created README.md (project description + tech stack), LICENSE (MIT, Grapsee Agency), .env.example
- Untracked machine-specific `.env` (contains only local SQLite path; file remains on disk)
- Committed full project baseline and pushed `main` → `origin/main` (commit 03119b4)
- Verified remote: ls-remote confirms origin/main == local main

Stage Summary:
- ✅ GitHub sync is LIVE: every future change should be committed as Grapsee-Official and pushed to origin/main
- Push workflow: `git add -A && git commit -m "..." && git push origin main`
- Remote repo was empty at first push — clean baseline established
- Planned next: GitHub Actions for CI/CD and iOS/Android app conversion (user will define what to build next)
- No website development has started yet — user wants to discuss requirements first

---
Task ID: 2
Agent: Z.ai Code (main)
Task: Agree on mobile architecture stack for the-gs-ai-app

Work Log:
- User defined the target mobile stack; reviewed and agreed (no blocking concerns)
- Logged the full stack table for future sessions (see Stage Summary)

Stage Summary:
- AGREED MOBILE STACK:
  - Android: Kotlin + Jetpack Compose, Clean Architecture + MVVM, Room/SQLite + FTS5, Ktor, Hilt, WorkManager, Android Keystore, C++ core engine via JNI, Gradle + GitHub Actions
  - iOS: Swift + SwiftUI, Clean Architecture + MVVM, SwiftData/SQLite + FTS5, Ktor shared layer, native/lightweight DI, BackgroundTasks, Keychain/Secure Enclave, C++ core via Objective-C++, Xcode/SPM + GitHub Actions
  - Shared: Kotlin Multiplatform (KMM) for shared logic where useful
- Dev loop in this sandbox: write/scaffold Kotlin/Swift/C++ code + GitHub Actions workflows here → push to GitHub (as Grapsee-Official) → Actions runners compile/test (macOS runners needed for iOS builds)
- Next: user to provide further assessment (app requirements/domain) before implementation

---
Task ID: 3
Agent: Z.ai Code (main)
Task: Lesson — AI chat app end-to-end architecture (taught by user, NO build yet)

Work Log:
- User taught the full AI chat app playbook. Studied and internalized. No development started (explicitly forbidden until lessons complete).

Stage Summary:
- KNOWLEDGE BASE ACQUIRED (key models to apply later):
  1. REFRAME: model = rented commodity (API); product = orchestration layer. Moat = UX, memory, integrations, trust, niche — never the model.
  2. 5 LAYERS: Product (features/UX) → Brain (prompts, RAG, tools, agents) → Backend (gateway, orchestrator, streaming) → Inference (token gen) → Data (Postgres/Redis/vector/S3).
  3. SPEED = 5 compounding tricks: continuous batching + PagedAttention (~20x throughput), prompt caching (~90% cost / ~85% latency cut on cached prefix), speculative decoding (2-3x, identical output), MoE (Kimi K2: 1T params, ~32B active/token), model routing (~60% savings). Plus quantization + geo-distribution.
  4. STREAMING RULE: start SSE (simple, auto-reconnect); move to WebSockets only for mid-stream client input (stop button, live tools, collab/voice).
  5. REQUEST LIFECYCLE: gateway (auth/rate-limit) → orchestrator (history+retrieval+tools+system prompt) → inference → stream tokens → persist + memory update.
  6. RAG PIPELINE: ingest → chunk (300-500 tok) → embed → vector store → hybrid search (keyword+vector) + rerank. Skip RAG if KB fits in context window.
  7. MEMORY LADDER: truncation → rolling summarization → vector long-term memory → hybrid (verbatim recent buffer + session summary + retrieved cross-session facts).
  8. RELIABILITY: smart LB, cost-aware autoscaling, rate limiting/queues, circuit breakers/failover, health checks, multi-region, observability (Grafana + Langfuse/LangSmith).
  9. SAFETY: guardrails at gateway (input/output filtering, LlamaGuard-class classifiers), tool permissioning — destructive tools need confirmation.
  10. DATA DEFAULTS: Postgres (+pgvector first), Redis for sessions/rate-limits/semantic cache, S3 for files.
  11. COSTS: inference = utility bill, 40-50% of early revenue; API $0.20-21/M tok; open MoE <$1/M; build phases 0-3 (prove → MVP/SSE → RAG+guardrails+billing → routing/scale).
- RELEVANCE TO OUR STACK: mobile (Ktor) + Next.js backend; z-ai-web-dev-sdk = "rented brain" in sandbox; stop-generating button → WebSocket (matches sandbox socket.io setup); hybrid memory for mobile assistant; single /chat endpoint with filter-style params (lesson: API design from previous lesson).
- NEXT: user will teach more lessons before any build begins.


