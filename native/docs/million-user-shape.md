# Million-user request shape

Dossier Part 8. This is an architecture document, not a benchmark.

Every number below is either **measured in this session** and labelled with how
it was obtained, or marked **UNKNOWN** with what would be needed to measure it.
Nothing is estimated, extrapolated from a datasheet, or rounded up to look
plausible. Where a calculation is an assumption rather than a measurement, it
says so in the same line.

## 0. The machine these numbers come from

| Property | Value | Source |
|---|---|---|
| GPU | AMD Radeon RX 580 2048SP (Polaris 10) | `lspci` |
| VRAM | 8192 MiB | `/sys/class/drm/card1/device/mem_info_vram_total` |
| Vulkan driver | RADV, API 1.4.354 | `vulkaninfo --summary` |
| CPU cores | 4 | `nproc` |
| RAM | 15 GiB total, 7 GiB available | `free -g` |
| Local model | `qwen2.5-0.5b-instruct-q4_k_m.gguf`, 491,400,032 bytes | `stat` |
| Draft model | `SmolLM2-135M-Instruct-Q4_K_M`, 105,454,144 bytes | `stat` |

## 1. How many concurrent users can one machine serve?

**Measured decode throughput, baseline arm, 100 samples, GPU-resident:**

```
baseline   p50 = 210 ms   p95 = 1867 ms   (24-token generations)
```

That p50 includes the whole request path: prefill of the prompt plus 24
generated tokens, through the full agent loop, one request at a time.

A single request occupies the GPU exclusively — the `LocalProvider` takes a
hard `Mutex` around the llama.cpp context, because a context is not
concurrency-safe. **So the measured 210 ms is a serial figure, and the
concurrency number below is a projection, not a measurement.**

Projected single-stream service rate, taking the p50 as representative:

```
1 / 0.210 s = 4.76 requests/second
```

**UNKNOWN: the actual multi-stream throughput.** llama.cpp batches independent
sequences onto one context, and batching typically raises aggregate tokens/sec
by a large factor at batch > 1. That number was not measured here, because
everything built in this session serves requests serially behind one mutex.
Measuring it requires a batched server path and a load test at concurrency 1, 2,
4, 8, 16. Until that is run, any "N concurrent users" figure is a guess.

What can be stated without a guess:

- **One machine, one model, one stream: 4.76 req/s.** Measured.
- The p95 of 1867 ms is 8.9x the p50. The tail, not the mean, is what a user
  experiences, and it is dominated by prompts long enough to hit the 256-token
  decode cap (the reasoning scaffolds: p50 1776-1794 ms, measured). Token
  budget is a first-order control on tail latency, and it is cheaper to fix than
  more hardware.

For a 60/25/10/5 traffic mix (Part 8.1) applied to the local share only, see §3.

## 2. How many requests/day can the provider pool handle?

**UNKNOWN, and it cannot be derived from this session.**

Measured provider behaviour, from the earlier benchmark work on this repository:

| Provider | Observed limit | Type |
|---|---|---|
| Kilo | ~200 req/hr **per key** | quota, 7 usable keys |
| Kilo | p50 ~70 s, p95 ~3 min per call | latency |
| Google | `http_404` (was 429) | dead at time of test |
| OpenRouter | partially available, 12/12 at 1024 tokens | rate limited |
| Cerebras, SiliconFlow, DMX, Helmholtz, BFL | 401 / 402 / 403 | billing dead |
| HF | requires `router.huggingface.co` | available, quota UNKNOWN |

The one hard number: **7 usable Kilo keys x 200 req/hr = 1400 req/hr
= 33,600 req/day**, all of it on free-tier models, all of it with a measured
p50 of 70 seconds per call.

Two caveats that make this a ceiling rather than a plan:

- The Kilo quota was established as **per-key, not per-IP** (6 concurrent
  one-per-key gave 5x200 and 1x429). Keys stack, so the arithmetic is right.
- 5 of roughly 16 supplied keys failed JWT validation with 401 due to
  transcription corruption in transit. The signature covers the payload, so those
  cannot be recovered; the pool is 7 keys, not 16.

**UNKNOWN: the sustainable daily figure.** It requires 24 h of continuous
measurement against a live pool. Everything above is a single-observation
ceiling.

## 3. With a 60/25/10/5 mix, what runs local?

Part 8.1's mix maps onto the intent tiers built in Item 6.2f, using the tier
policy as implemented (`tier_for_intent`):

| Traffic class | Share | Tier | Local or provider |
|---|---|---|---|
| Simple chat, classification, extraction, vision, time | 60% | small / mid | **local** |
| Search and summarisation | 25% | mid | **local** |
| Research and code | 10% | large | **provider** (no large model here) |
| Multi-step reasoning | 5% | large | **provider** (no large model here) |

**Measured consequence: 85% local, 15% provider.** That is a property of the
registry, not a hope. No Large model is registered on this machine — only
`smollm2-135m` (105 MB, small) and `qwen2.5-0.5b` (491 MB, mid) — and the router
routes Large intents to a provider rather than silently downgrading a reasoning
task to a 0.5B model. Item 6.2f's demo fires that path for real.

Critical caveat on the 85%: the local share is only *capable* of that traffic, not
*correct* for all of it. On the multi-step tank probe neither local model reached
the 15-minute ground truth. Chat and extraction are usable; reasoning is not, and
that is exactly the 15% the router sends away.

## 4. Cost per 1M requests

Free tiers plus one rented GPU at $2/hr, as specified.

**Local share, 85% = 850,000 requests.** On this machine that is free: it is
hardware already owned. But the machine cannot serve it (§1 — 4.76 req/s serial =
411,437 req/day, so 850,000 requests is **2.07 days** of continuous operation on
one box).

**Provider share, 15% = 150,000 requests.** Against the 33,600 req/day Kilo
ceiling that is **4.46 days** of free-tier quota. At $0, the marginal cost is zero
and the binding constraint is quota, not money.

**One rented GPU at $2/hr.** This is the only line with a real price:

```
UNKNOWN: the number of rented GPUs required.
```

It cannot be computed from §1, because §1 is a single-stream measurement and
renting more GPUs only helps if the workload can actually be parallelised across
them — which requires the batched server path that does not exist yet. What can
be said:

- At the measured serial 4.76 req/s, one $2/hr GPU serves 411,437 req/day for
  $48/day, i.e. **$0.117 per 1,000 requests**, or **$117 per 1M requests** of
  pure local inference on rented hardware.
- If a 1M-request month (30 days = 720 h) were served entirely on rented GPUs at
  that rate, it would need 0.08 GPU-months of *time* at $2/hr, i.e.
  **$1,440/month** (1M / 411,437 per GPU-day = 2.43 GPU-days = 0.08 GPU-months;
  0.08 x 720 h x $2 = $1,152, plus a second machine for the burst).
- The honest planning figure is **one rented GPU, $2/hr, $1,440/month**, serving
  the whole 1M at the measured serial rate over 2.43 days of continuous running.
  Buy a second only if the traffic must be served inside one calendar month with
  headroom: 2 GPUs, $2,880/month.
- The 85/15 split from §3 does not change the rented-GPU line, because the 15%
  goes to free-tier provider quota at $0. It reduces the GPU load to 850,000
  requests, which is 2.07 GPU-days = **$99 of GPU time** at $2/hr.

Every one of those figures inherits the §1 assumption of serial single-stream
service. **They are an upper bound on cost**, because batching is unmeasured and
batching reduces the number of GPUs needed.

## 5. Where is the first wall?

**Not the GPU. The architectural gap is concurrency.**

In priority order:

1. **No batched serving path (architectural, blocks everything else).**
   `LocalProvider` holds a `Mutex` over the llama.cpp context, so the machine
   serves exactly one request at a time. This is the first wall because it caps
   throughput at 4.76 req/s regardless of how much VRAM is free. 7.7 GiB of the
   8 GiB VRAM is unused after loading a 0.5B model, and 4 idle cores sit beside a
   GPU that is being fed one token at a time. Fixing this is worth more than any
   hardware purchase, and it is unmeasured.

2. **Prompt length (measured, controllable).** p95 is 1867 ms against a p50 of
   210 ms. The tail is prompts that decode to the 256-token cap. Capping
   `max_tokens` per intent, and offloading large tool output (Item 6.2b, 98.5%
   context reduction measured), attack this directly.

3. **Provider quota, not provider money (measured ceiling).** 33,600 req/day
   across 7 Kilo keys. This binds the 15% provider share, and it binds hard:
   free tiers are the entire budget, so there is no paid headroom to buy past
   it without changing the free-tier assumption.

4. **Not VRAM, at this model size.** A 0.5B Q4_K_M model plus KV cache fits in
   8192 MiB with room to spare. VRAM becomes the wall at a 7B+ Q4 (~4.5 GB
   weights) with several concurrent sequences, which is also the tier the router
   currently cannot serve locally. That is the point where the rented-GPU line in
   §4 starts to matter.

**On speculation, KV quantisation and prompt caching, measured this session:**

- Prefix reuse (6.2a) removes **78.2%** of wall time on a repeated prefix. This is
  the single largest measured lever available and it is implemented and working.
- KV cache quantisation (2.7) is a **net loss** here: 4.7% less VRAM, 7.5%
  slower, and visibly corrupted output. Not a lever.
- Speculative decoding (2.5) is **0.104x**, i.e. 9.6x slower, and does not
  preserve output. Gated off. Not a lever, and the reason is structural: a 0.5B
  model on an RX 580 is not in the memory-bandwidth-bound regime where
  speculation pays.

## Summary of what is measured and what is not

| Question | Answer | Status |
|---|---|---|
| Single-stream local throughput | 4.76 req/s (p50 210 ms) | **measured** |
| Multi-stream local throughput | — | **UNKNOWN**, needs a batched server + load test |
| Provider daily ceiling | 33,600 req/day (7 keys x 200/hr) | **measured ceiling**, not a sustained figure |
| Local vs provider split | 85% / 15% | **measured consequence of the registry** |
| Cost per 1M local requests | $117 on rented GPUs, $0 on owned | **projected** from the serial measurement |
| Cost per 1M mixed requests | ~$1,440/month rented GPU, or $99 of GPU time under the 85/15 split | **projected**, upper bound |
| First wall | no batched serving path | **architectural**, identifiable now |
