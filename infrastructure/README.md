# Infrastructure

Runtime infrastructure definitions live here (Docker Compose, environment configs, deploy recipes).

## Current state

| Concern | Sandbox (now) | Production target |
|---------|--------------|-------------------|
| Database | SQLite via Prisma (`db/custom.db`) | Postgres (+pgvector) |
| Session state | In-process memory | Redis |
| Object storage | Local `upload/` folder | S3-compatible |
| Inference | z-ai-web-dev-sdk (rented) | Provider APIs / self-hosted vLLM |
| Gateway | Caddy (sandbox) | API gateway w/ rate limiting |

## Planned

- `docker-compose.yml` — Postgres + Redis for parity with production shape
- Backup recipe for database + object storage
- Observability stack notes (Grafana + LLM tracing)

Nothing here is deployed yet — placeholders document intent, per the phase plan in `docs/ROADMAP.md`.
