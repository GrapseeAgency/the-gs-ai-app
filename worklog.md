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

---
Task ID: 4
Agent: Z.ai Code (main)
Task: Lesson — Full AI platform product scope (taught by user, NO build yet)

Work Log:
- User taught the complete product blueprint for a serious native AI chat app. Studied and internalized. No development started.

Stage Summary:
- PRODUCT SCALE TARGET: ~100-150 major screens, 300-500+ UI states/sheets/dialogs/flows, 50-100+ major features. It is an AI PLATFORM + ecosystem, NOT "chat screen + settings".
- MENTAL MODEL: App → Home / Chat / Explore / Create / Library / Projects / Assistants / Search / Profile / Settings. Each = a mini-product. Secondary functions nest inside; nav bar stays clean (7 primary: Home, Chats, Explore, Create, Library, Profile, Settings).
- KEY SUBSYSTEMS (compressed spec):
  1. HOME = AI command centre: universal input (text/voice/image/file), suggested prompts, recent/favourite chats, pinned assistants, projects, model recommendations, quick actions (new chat, voice, analyse image/doc, write, research, code, translate, summarise, brainstorm, generate image), resume-where-left-off, activity stats.
  2. CHAT SYSTEM: new/active/history/pinned/archived/shared/deleted/search/folders/projects/details. Message ops: stream, regenerate, stop, edit, retry, continue, copy, select, share, quote, translate, read aloud, feedback, save to library, branch-new-chat. Input: text/voice/camera/gallery/files/multi-attach/code/doc/context selection/prompt templates.
  3. MODEL CENTRE (not a dropdown): browser, comparison, details, capabilities, speed, context window, reasoning modes, tool support, favourites, default, auto-selection, per-model settings. Modes: Fast/Balanced/Deep reasoning/Research/Coding/Creative/Vision/Voice. (MAPS TO LESSON-1 ROUTING.)
  4. ASSISTANTS/PERSONAS ECOSYSTEM: marketplace, my assistants, profile, create/edit, instructions, knowledge, tools, capabilities, conversation starters, analytics, published/private/favourites. Users build their own AI.
  5. EXPLORE = discovery layer / AI app store: trending assistants, popular prompts, featured tools, categories (coding, education, business, writing, productivity, research, design, math, language, science, entertainment).
  6. CREATE: image gen, image edit, doc/presentation/spreadsheet/writing/code/diagram generation, prompt builder, assistant builder — EACH gets its own workspace, not dumped into chat.
  7. LIBRARY = personal knowledge space: saved messages, docs, images, generated files, prompts, templates, projects, favourites, collections, downloads. Doc functions: upload/preview/search/annotate/summarise/ask/compare/extract.
  8. PROJECTS = bundle of chats+files+instructions+assistants+models+generated content: dashboard, chats, files, instructions, members, activity, settings.
  9. GLOBAL SEARCH across conversations/messages/files/assistants/projects/library/prompts + filters (date, model, file type, project, assistant). (MAPS TO OUR SQLITE FTS5.)
  10. VOICE: full-screen mode, waveform, interrupt AI, voice selection/speed/language, transcript, background mode. (Interrupt → WebSocket per lesson 1.)
  11. VISION: camera/gallery/screenshot analysis, OCR, image Q&A, comparison, object ID, chart interpretation, extraction.
  12. RESEARCH: web search, source cards, citations, research mode, multi-source reasoning, history, saved sources, reports — visually distinct from chat.
  13. CODING: interpreter, code blocks, syntax highlighting, file tree, multi-file, execution output, error detection, diff viewer, explanation, debug, terminal UI.
  14. NOTIFICATION CENTRE: task completed, file processed, assistant updates, shared convo, project activity, system, security.
  15. PROFILE: account, avatar, usage, subscription, AI preferences, connected services, devices, security, sessions.
  16. SETTINGS ARCHITECTURE (8 categories): Appearance (light/dark/system, accent, typography, chat appearance, density, animations), Chat (default model, enter-to-send, history, auto-title), AI (memory, personalisation, reasoning prefs, voice), Privacy (data controls, training prefs, export, delete), Security (passcode, biometrics, 2FA, sessions, trusted devices), Notifications, Language (app/AI/voice), Accessibility (font scaling, contrast, reduce motion, screen reader, haptics).
  17. SHARING: share message/conversation, public/private links, QR, export PDF/MD/TXT/JSON, copy/revoke link.
  18. COLLABORATION: shared projects/chats, team workspaces, members, roles, permissions, comments, mentions, activity.
  19. BILLING: pricing, plans, usage, credits, manage sub, payment methods, invoices, upgrade/downgrade/cancel/restore.
  20. AUTH: welcome, sign in/up, email verify, password reset, passkeys, Google/Apple, 2FA, device verification, recovery, locked, sessions.
  21. ONBOARDING: Welcome → Account → Interests → AI preferences → Favourite capabilities → Notifications → Personalisation → Home. Highly designed.
- UI DIRECTION (LOCKED): "Premium intelligent editorial UI" = Apple-level native polish + sophisticated AI interface + editorial typography + subtle depth. Layered surfaces, subtle tonal diffs, strong dark mode (deep blacks/navy, not flat grey). Modern sans UI font + optional refined serif for branding/editorial. Moderate radius, soft elevation, clear hierarchy. Motion: spring animations, streaming text, morphing + shared-element transitions, gesture-driven sheets, haptics. AI responses: superb typography, beautiful code blocks, tables, citations, attachments, tool indicators, reasoning/status states. FORBIDDEN: cheap gradients, excessive glass, generic Material look, giant cards everywhere.
- STATES ARE PRODUCT: bottom/full-screen sheets, context/action menus, confirmations, permission prompts, empty/loading/skeleton/error/offline/retry/network-fail/rate-limit/upload-download progress/AI-generation progress/tool states/auth errors.
- NATIVE (reaffirmed): Android Kotlin+Compose+Room+WorkManager+Coroutines; iOS Swift+SwiftUI+SwiftData+BackgroundTasks+Swift Concurrency; shared backend contracts.
- NEXT: user will teach more lessons before any build begins.



