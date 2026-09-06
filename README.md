# The GS AI App

> **Grapsee Agency** — AI-powered application platform. Built with Next.js 16.

This repository is the single source of truth for the project. All development happens in the sandbox environment and is continuously pushed here, so the latest code is always available on GitHub.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Next.js 16 (App Router) + TypeScript 5 |
| Styling | Tailwind CSS 4 + shadcn/ui (New York) |
| Database | Prisma ORM + SQLite |
| State | Zustand + TanStack Query |
| Icons | Lucide Icons |
| AI | z-ai-web-dev-sdk (LLM, VLM, TTS, ASR, Image/Video Generation) |

## Project Structure

```
├── src/
│   ├── app/            # App Router pages & API routes
│   ├── components/     # UI components (shadcn/ui)
│   ├── hooks/          # Custom React hooks
│   └── lib/            # Utilities, db client
├── prisma/             # Database schema
├── db/                 # SQLite database file
├── mini-services/      # Independent services (WebSocket, etc.)
├── public/             # Static assets
└── tests/              # Test utilities
```

## Getting Started

```bash
# Install dependencies
bun install

# Push database schema
bun run db:push

# Start development server
bun run dev
```

## Git Workflow

- Branch: `main`
- All commits are authored by **Grapsee-Official** and pushed directly from the sandbox.
- GitHub Actions can be attached later for CI/CD and mobile (iOS/Android) packaging.

---

© Grapsee Agency. All rights reserved.
