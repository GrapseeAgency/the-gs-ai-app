# The GS AI App

> **Grapsee Agency** — a complete AI platform: native Android & iOS clients, contract-first backend, GitHub Actions as the build farm.

![Android CI](https://github.com/GrapseeAgency/the-gs-ai-app/actions/workflows/android-ci.yml/badge.svg)
![iOS CI](https://github.com/GrapseeAgency/the-gs-ai-app/actions/workflows/ios-ci.yml/badge.svg)
![Backend CI](https://github.com/GrapseeAgency/the-gs-ai-app/actions/workflows/backend-ci.yml/badge.svg)

## Monorepo layout

```
the-gs-ai-app/
├── android/              # Kotlin · Jetpack Compose · Hilt · Clean Architecture + MVVM
├── ios/                  # Swift · SwiftUI · XcodeGen (project generated in CI)
├── src/                  # Backend — Next.js 16 API gateway + orchestrator (this sandbox runs it)
├── prisma/               # Database schema (SQLite now, Postgres-shaped)
├── shared-contracts/     # OpenAPI — the single API truth both platforms code against
├── infrastructure/       # Docker/Postgres/Redis definitions (planned)
├── docs/                 # ARCHITECTURE · DECISIONS · ROADMAP
├── scripts/              # Environment verification & utilities
├── mini-services/        # Independent services (WebSocket, etc.)
└── .github/workflows/    # CI: Android (ubuntu) · iOS (macOS) · Backend (ubuntu)
```

**Principle:** Android remains Android. iOS remains iOS. Backend remains platform-independent. The only thing platforms share is the contract.

## Platforms

| Platform | Stack | Build |
|----------|-------|-------|
| Android | Kotlin 2.0.20, Compose BOM 2024.09.03, Material3, Hilt, minSdk 26 | GitHub Actions → debug APK artifact |
| iOS | Swift 5.9, SwiftUI, iOS 16+, XcodeGen | GitHub Actions → macOS runner, unsigned simulator build |
| Backend | Next.js 16, TypeScript 5, Prisma + SQLite, Tailwind 4 + shadcn/ui | GitHub Actions → lint |

## Getting started (sandbox/web)

```bash
bun install        # install dependencies
bun run db:push    # push Prisma schema
bun run dev        # start dev server
bash scripts/verify-env.sh   # verify toolchain + git identity
```

## Native builds

Both native apps build **in CI on every push** — artifacts appear under the workflow run:

- Android: `Actions → Android CI → gsai-debug-apk`
- iOS: project generated via `xcodegen generate`, built for the iOS Simulator
- Local native builds need Android Studio / Xcode (see `android/README.md`, `ios/README.md`)

## Documentation

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — five-layer model, request lifecycle, memory ladder
- [`docs/DECISIONS.md`](docs/DECISIONS.md) — ADRs (pure native, SSE-first, XcodeGen, open BYOK question)
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — phased delivery of the platform blueprint
- [`shared-contracts/openapi.yaml`](shared-contracts/openapi.yaml) — API v0.1.0

## Git workflow

- Branch: `main` — all commits authored by **Grapsee-Official** and pushed from the sandbox
- CI runs automatically on path-filtered pushes

---

© Grapsee Agency. All rights reserved.
