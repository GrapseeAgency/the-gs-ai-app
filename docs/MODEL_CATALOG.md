# GS AI model catalog and routing policy

## Runtime flow

1. `/api/health` and the durable worker start one non-blocking catalog refresh.
2. Each configured provider is queried with a metadata `GET` only; no completion or image-generation request is made during discovery.
3. Successful responses are normalized, deduplicated, and written to the ignored `data/.provider-catalog.json` cache. A failed provider never removes the last known-good snapshot.
4. The next refresh runs every six hours. The public `/api/v1/models` response remains nameless; only aggregate catalog counts are exposed by health diagnostics.

Configured providers currently include OpenRouter, Groq, Google Gemini, Cerebras, Blablador, Hugging Face, SiliconFlow, DMXAPI, and BFL. Provider/model identities and credentials remain server-private.

## Free-only and queueing

`GS_FREE_ONLY` is enabled by default. The production turn path does not silently call the primary z-ai provider or a paid fallback. Capacity failures are represented by `free_capacity_unavailable` and returned to the durable worker queue with bounded exponential backoff.

`BACKGROUND_RUNS_ENABLED=0` is the explicit opt-out for the legacy inline transport. The default detached transport keeps the run and stream resumable while free capacity is unavailable.

The policy does **not** promise that third-party free tiers are permanent or unmetered. Provider quotas, invalid keys, trial limits, and credit policies remain external constraints. `free`, `trial`, `credit`, `unknown`, and `paid` are kept distinct in the catalog.

## New models

A model discovered from a provider is an untrusted `candidate`. Provider metadata can establish advertised modalities, context, and price, but it is not treated as GS AI quality evidence. A model must pass health and application-capability validation before it can become a routing candidate for production traffic.

## C++ boundary

The C++ engine in `native/` is the heavy local execution layer: agent loops, context shaping, tool dispatch, and local backend selection. Remote provider HTTP, secrets, catalog synchronization, queueing, and public API translation stay in the Bun/Next backend. The C++ registry is not a replacement for `src/lib/router.ts` and never receives provider keys.
