# GS AI engineering rules

- `native/` is the authoritative C++ layer for heavy local agent loops, context shaping, tool dispatch, and local backend selection. Do not duplicate those mechanisms in TypeScript.
- TypeScript/Bun owns remote provider HTTP, secret resolution, catalog synchronization, durable queue/worker orchestration, and public API translation.
- The product identity is **GS AI**. Never expose provider names, concrete model IDs, internal route names, or capability scores through public client responses.
- Free-only is the default spend policy. A free-capacity failure is a queue condition; never silently fall back to a paid route. `BACKGROUND_RUNS_ENABLED=0` is an explicit legacy-transport opt-out.
- Newly discovered provider models are candidates until metadata, health, capability evidence, and free-policy validation pass. Provider-advertised metadata is not application quality evidence.
- Keep secrets out of logs, source control, model registries, and client-visible errors. Rotate exposed credentials before production use.
- Run `make -C native smoke` for C++ changes and focused Bun tests for routing/catalog/queue changes.
