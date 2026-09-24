# CAPABILITY REGISTRY REPORT — REGISTER-ROUTING-FIX-V1 (PHASE 5)

Generated: 2026-09-24 (refreshed when phases 1/3/4 complete — see tests/baseline-v5.json)
Registry source: `src/lib/model-capabilities.ts` (literals derived by `scripts/compute-capability-registry.ts` from the committed eval artifacts — a score without an artifact is forbidden).

## Registry table (measured scores per capability)

| model (provider) | register | reasoning | instruction | search | latency p50 | artifacts |
|---|---|---|---|---|---|---|
| `zai/gs-swift` (glm-4.5-flash) | **1.000** (15/15 multi-trial; + phase-1/3 pending) | UNMEASURED | UNMEASURED | UNMEASURED | 749 ms (n=15) | tests/register-multitrial-v1.json |
| `zai/gs-balanced` (glm-4.6) | **0.143** (5/35) — BARRED (`neverServe`) | 1.000 (2/2) | 1.000 (3/3) | UNMEASURED | 776 ms (n=42) | tests/register-multitrial-v1.json, tests/baseline-v4.json, tests/baseline-v3.json |
| `openrouter/gs-free-big` (nemotron-3-ultra-550b) | 0.857 (12/14 single-sample; phase-4 n=50 pending) | 1.000 (18/18) | 1.000 (11/11) | 1.000 (20/20) | 22 873 ms (n=63) | tests/baseline-v3.json, tests/baseline-v3-gsfree.json |
| `openrouter/gs-free` (nemotron-3-super-120b) | 0.833 (5/6 single-sample) | 1.000 (16/16) | 1.000 (26/26) | UNMEASURED | 3 049 ms (n=48) | tests/baseline-v3.json, tests/baseline-v3-gsfree.json |

All reasoning/instruction/search numbers are deterministic-suite results (n=1 per case, product-fixed suite); register numbers are LLM-judged (glm-4.6, temperature 0, suite rubric). Latency is the production turn p50 across recorded eval traces (mixed turn types — directional, not an SLA).

## Safe-to-route pairs (measured)

- **register → zai/gs-swift (1.000)** — the only model measured at n≥15 on register. The router now selects it for every register-class turn; `zai/gs-balanced` (0.143) is barred and can never be selected again.
- **reasoning → zai/gs-balanced (1.000, n=2)** — thin but measured; ties with ultra/super (both 1.000 at larger n). The registry tie-break keeps gs-balanced (tier-stable).
- **instruction → openrouter/gs-free (1.000, n=26)** / gs-balanced (n=3) / ultra (n=11) — all measured 1.000.
- **search → openrouter/gs-free-big (1.000, n=20)** — the ONLY model with measured search; the WEB tier already rides this chain, so no change.

## UNMEASURED pairs that need a new eval suite run

- **zai/gs-swift: reasoning, instruction, search** — the deterministic suite has never been served by gs-swift (TEXT_SIMPLE cases rode the openrouter chain whenever keys existed). Needs one full deterministic-suite run with the keypool parked.
- **zai/gs-balanced: search** — SOURCE_QUALITY never served by zai/gs-balanced (WEB always rides the free chain); needs a forced-primary search run.
- **openrouter/gs-free: search** — same as above for the super chain.
- **chat (as a scored capability)** — no chat suite exists; the tier default (gs-swift / super chain) is unmeasured by construction. A chat-quality suite would close the last gap.

Routing on unmeasured pairs stays on the standing tier model and is LOGGED as `unmeasured` (never presented as a measurement). The router refuses (BLOCKED) only when a capability has no measured model at all — currently impossible for register/reasoning/instruction/search.

## The inversion this fixes (before → after)

BEFORE (tier-label router): register-detector cases → TEXT_COMPLEX → zai/gs-balanced → **5/35** measured over 5 trials/case (tests/baseline-v4.json) while the "efficient" tier passed **15/15**. The detector was right; the destination was wrong — a routing inversion, empirically proven.

AFTER (capability-registry router): register → `selectModelForCapability('register')` → zai/gs-swift (measured 1.000 ≥ threshold 0.5); provider locked to zai; gs-balanced barred. Per-turn evidence in the `GS-CAPABILITY` log line (requiredCapability / model / measured score). Full before/after numbers land in tests/baseline-v5.json (phases 3/4).
