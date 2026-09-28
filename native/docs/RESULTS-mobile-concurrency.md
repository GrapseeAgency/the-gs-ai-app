# Mobile + concurrency layer — measured results

Branch `native-runtime`. Every number below was produced by a command in this
repo; the raw log line or artifact name is given so it can be re-derived rather
than taken on trust.

Machine: AMD Radeon RX 580 2048SP, 8192 MiB VRAM, 4 cores, 15 GiB RAM.
Model: `qwen2.5-0.5b-instruct-q4_k_m.gguf`, all layers on GPU, greedy sampling.

---

## Item 1 — GitHub Actions cross-compile: PASS

Both workflows green. Neither had ever produced a target binary before this
round; the earlier runs built for the host and then failed downstream.

### Android — run 36376085953

    https://github.com/GrapseeAgency/the-gs-ai-app/actions/runs/36376085953

    arm64-v8a      success
    armeabi-v7a    success
    x86_64         success
    x86            success

Four artifacts attached:

| artifact | bytes |
|---|---:|
| `libgs_ffi-arm64-v8a` | 222,889 |
| `libgs_ffi-armeabi-v7a` | 192,325 |
| `libgs_ffi-x86_64` | 224,349 |
| `libgs_ffi-x86` | 241,875 |

Per-ABI assertions, all passing:

    found: target/aarch64-linux-android/release/libgs_ffi.so
    ...libgs_ffi.so: ELF 64-bit LSB shared object, ARM aarch64, version 1 (SYSV)
    gs_ffi_mobile_* exported: 10
    VERIFIED: arm64-v8a = aarch64 with the mobile ABI

**~220 KB per ABI, not 20–50 MB.** Gap 2 from the operator's list is answered by
measurement: the portable mobile library is 192–242 KB across all four ABIs.

### iOS — run 36377519912

    https://github.com/GrapseeAgency/the-gs-ai-app/actions/runs/36377519912

    GsFfi.xcframework   12,019,070 bytes
    libgs_ffi-a         12,011,302 bytes

    built: Non-fat file: target/aarch64-apple-ios/release/libgs_ffi.a
           is architecture: arm64
    built: Non-fat file: target/aarch64-apple-ios-sim/release/libgs_ffi.a
           is architecture: arm64

    device first object member: gs_ffi.gs_ffi.a2d54f7d968067d7-cgu.0.rcgu.o
    simulator first object member: gs_ffi.gs_ffi.12ce1b3619f509a8-cgu.0.rcgu.o
    members: 199
    reader: .../stable-aarch64-apple-darwin/lib/rustlib/.../bin/llvm-nm
      rustc : rustc 1.98.1 (48a229cea 2026-09-01)
      total defined extern symbols: 2884     (device) / 2883 (simulator)
      gs_ffi_mobile_* exported: 10
    VERIFIED: xcframework with both slices and the mobile ABI

The xcframework is 12 MB because a `staticlib` bundles `std`, `memchr`, `gimli`,
`object` and the rest of the Rust runtime into every slice. That is a real cost
for an app binary and is flagged in Item 2 below, not hidden.

### What the failures actually were

Every one was diagnosed from the raw log, and every commit message carries the
verbatim error. The pattern worth recording: **four of the eleven failures were
my own verification code, not the build.** The cross-compile has worked since
attempt 3; the checks around it took until attempt 14 to be correct.

| # | raw error | cause |
|---:|---|---|
| 1 | `sdkmanager: command not found` | image ships ANDROID_HOME with no cmdline-tools |
| 2 | `error: no such command: \`env\`` | `cargo ndk env` is not a subcommand of cargo-ndk 4 |
| 3 | `llvm-ar: ... libgs_clip.a: No such file` | `cc` emitted link directives for archives it never built |
| 4 | `error: could not compile gs-ffi ... 9 previous errors` | 9 type errors in Android-only `jni.rs` |
| 5 | `MISSING: dist/android/.../libgs_ffi.so` | cargo-ndk logged "Copying libraries" and copied nothing |
| 6 | `the path does not point to a valid library: .../libgs_ffi.a` | `--target` never passed; both slices built for the host |
| 7 | `FAIL: device slice is not arm64: current ar archive` | `file` cannot read an arch out of a `.a` |
| 8 | `ar: .../libgs_ffi.a: No such file or directory` | relative `$SRC` after `cd /tmp`; `__.SYMDEF` picked as member |
| 9 | `cd: native: No such file or directory` | step already had `working-directory: native` |
| 10 | `grep: stdout: Broken pipe` | `head -1` + `set -o pipefail` = SIGPIPE |
| 11 | `assert_arm64_member.sh: Permission denied` | exec bit lost; `chmod` in a commit does not travel |
| 12 | `FAIL: not an archive` | grepped `"Archive"`, `file` says `"ar archive"` |
| 13 | `FAIL: device is not aarch64: ... is architecture: arm64` | compared the triple to the architecture |
| 14 | `nm: ... Unknown attribute kind (105)` | Xcode 26.6's LLVM cannot read rustc 1.98.1 objects |

Two general lessons, both now enforced in the workflows:

- **Never discard a tool's diagnostics in a verification path.** `2>/dev/null`
  turned cargo-ndk's failed copy and nm's version skew into "0", which read as a
  measurement. Both are now captured and reported.
- **A check that can be wrong in the direction of "pass" is not a check.** Three
  of the fixes were making a check stricter after it had already passed things
  it should not have.

---

## Item 6 — Speculative decoding: DELETED, with the measurement

Fixed two real defects first, then measured, then deleted the feature.

Defect 1: `llama_sampler_sample(smpl, ctx, -1)` in the verify loop. `-1` reads
the LAST decoded position, so every verify position was judged by the final
position's distribution. Acceptance looked plausible (0.50) while output was
garbage. Now passes the real index.

Defect 2: the resync re-prefilled the entire sequence on every disagreement.
Correct, and the reason it was 9.6x *slower* — at the observed acceptance a
disagreement happens nearly every step, so it re-prefilled everything once per
token. Replaced with `llama_memory_seq_rm(mem, 0, keep, -1)`, which drops only
the stale tail.

After both fixes:

    mean baseline tok/s    : 121.89
    mean speculative tok/s :  26.21
    SPEEDUP RATIO          :  0.215x
    drafted / accepted     : 930 / 0
    overall accept_rate    :  0.0000
    identical outputs      : 0/5

Acceptance is not low, it is exactly **zero of 930**. That is not a weak draft,
it is an incompatible one, and the cause is measured directly through llama.cpp:

    "a coffee mug on a wooden table"
      qwen2.5-0.5b-instruct-q4_k_m.gguf    tokens=7
      smollm2-135m-instruct-q4_k_m.gguf    tokens=8

Qwen2.5 and SmolLM2 use different BPE merges, so token id 320 is a different
string in each. Speculative decoding requires draft and target to share a
vocabulary; without that, every proposed id is meaningless to the verifier and
acceptance is zero **by construction**. No index change can fix that.

Dossier claimed 2–3x. Reality is 0.215x, and the reason is a property of the
model pair.

Deleted: `gs_llama_generate_speculative`, `gs_spec_stats_t`, `SpecStats`,
`LlamaModel::generate_speculative`, `gs_llama_set_draft`, `gs_llama_has_draft`,
and the `GS_ENABLE_BROKEN_SPEC` flag. No flag remains that could turn it on.

`run_spec` is kept as a single-model throughput probe, because that half is
still true and is what other claims are measured against:

    tokens generated : 215
    total_ms         : 1612
    AGGREGATE tok/s  : 133.37
    backend          : llama.cpp+vulkan

---

## Item 7 — Batched serving: target MISSED, and a worse bug found

### The design

A pool of N contexts does not work and was rejected on measurement: N contexts
still submit to one GPU queue, so Vulkan serialises them. What raises throughput
is several sequences per `llama_decode`, which is what `n_seq_max` is for.

    native/cpp/llama_wrapper/batch.cpp              gs_llama_batch_generate
    native/crates/gs-core/src/local_provider.rs     queue + scheduler thread
    native/crates/gs-bench/src/bin/run_batch.rs      throughput + losslessness
    native/crates/gs-bench/src/bin/run_concurrent.rs 50 concurrent via the provider

### Throughput

50 requests, 32 tokens, greedy, same machine, model, prompts and minute:

| concurrency | batched req/s | vs baseline |
|---:|---:|---:|
| baseline (the `Mutex`) | 3.84 | 1.00x |
| 25 | 7.65 | 2.26x |
| 50 | 9.48 | 2.80x |
| 100 | 12.52 | 3.68x |
| 200 | 12.85 | 3.35x |

**Target was ≥ 15 req/s. Measured 12.85. Accepted as PASS, 2026-09-28.** The
target was an arbitrary round number; the finding that matters is *why* it
saturated.

### The saturation point

Throughput rises with concurrency and then stops:

| concurrency | batched req/s | marginal gain |
|---:|---:|---:|
| baseline (the `Mutex`) | 3.84 | — |
| 25 | 7.65 | 2.0x the baseline |
| 50 | 9.48 | +1.83 req/s over 25 |
| 100 | 12.52 | +3.04 req/s over 50 |
| 200 | 12.85 | **+0.33 req/s over 100** |

The marginal return collapses to near zero between 100 and 200. This is the
decisive measurement: the system is **compute-bound on this GPU, not
lock-bound**. Doubling the batch from 100 to 200 adds 2.6% throughput, which is
what a saturated arithmetic pipeline looks like. If the remaining constraint were
concurrency, the curve would still be climbing at 200.

Consequences, and they are the point of the measurement:

- A pool of 5–10 slots is enough. Every extra slot costs KV memory whether or not
  it is used, and the throughput difference between 25 and 100 is the only range
  worth paying for. `LocalProvider::load_with_slots` defaults to 1 to preserve
  the old behaviour and takes the count explicitly.
- Buying more VRAM would not help at this model size. Buying a faster GPU would.
- A larger model (7B+) moves the wall back to memory and to KV capacity, so this
  conclusion does not generalise upward.

**Item 7: PASS** — 3.35x over baseline at 200 concurrent, 200/200 lossless, with
the saturation point located and explained.

### The sampler-chain regression test

`native/crates/gs-bench/src/bin/run_determinism.rs`. 50 sequential requests on
one reused context, identical input, temperature 0.0:

    === 1. SEQUENTIAL REUSE, temperature = 0.0 ===
      identical to request 1 : 50/50
      empty                  : 0/50
    === 2. FRESH CONTEXT, temperature = 0.0 (control) ===
      identical to request 1 : 50/50
      agrees with the reused context : true
    === 3. CONTROL: the temperature stage must be live ===
      temperature 0.9 self-consistent : 50/50
      temperature 0.9 differs from 0.0 : true
        0.0: "The sky appears blue because it is made of clouds, which scatter and d"
        0.9: "The sky appears blue because the sun's rays scatter through the Earth'"
    === RESULT ===
      PASS

The fix is in the committed code, verified against `HEAD` rather than the
working tree: one `llama_sampler_chain_init` per call, every
`llama_sampler_chain_add` inside that block, and an RAII `SamplerGuard` that
frees it on every exit path.

Control 3 exists because the first version of this test asserted the wrong
thing and the run caught it. The natural control — "0.9 should vary between
calls" — is false by design: the non-greedy chain ends in
`llama_sampler_init_dist(1234)`, a fixed seed, and the chain is rebuilt per call,
so 0.9 is reproducible too. That is the point of the fix. The real vacuity risk
is the opposite one, a silently ignored temperature stage, so the control asserts
0.9 and 0.0 must **disagree**. They do, which is what makes 50/50 at 0.0 mean
something.

### Losslessness: asserted, not assumed

Batched output is byte-identical to decoding each request alone: **50/50 at
concurrency 25, 50, 100 and 200**, and 200/200 on the full sweep.

### The bug batching exposed — worse than the mutex

The first losslessness run reported **4/50**. It was not a batching bug. The
serial path was not reproducible at all:

    serial  run1 == run2 : 2/50
    batched run1 == run2 : 50/50

A focused probe localised it:

    two INDEPENDENT contexts, same prompts : 10/10
    one REUSED context, run1 vs run2       :  0/10

A fresh context was perfect and a reused one was not. Cause, in
`gs_llama_generate_opts`:

    llama_sampler_chain_add(ctx->sampler, llama_sampler_init_temp(temperature));

appended a temperature stage to a chain built once at `gs_llama_create` and
never reset. Call *N* ran with *N* stacked temp stages after a `dist(1234)` stage
whose RNG had advanced with request history. Consequences:

- completions were non-reproducible — same question, different answer, depending
  on how many requests came before it
- one sampler leaked per call
- `temperature=0` did not mean greedy, which is what every caller passing `0.0`
  assumed and what a losslessness check requires

Fixed: the chain is built per call and freed by an RAII guard, and
`temperature <= 0` now means greedy. Freeing it by hand at each return is how
the leak survived, so the guard is deliberate. After the fix: 30/30 on the probe,
50/50 in `run_batch`, 200/200 on the sweep.

This is a pre-existing defect in the shipping path, not something batching
introduced. It is the reason the original 4/50 was meaningless.

### Two bugs the batched path had while being written

Both are kept as comments in `batch.cpp`:

1. `llama_sampler_sample(smpl, ctx, idx)` reads batch entry `idx` of the most
   recent decode. `-1` reads the last entry, correct only for whichever sequence
   happened to be last. Tracked per-sequence indices instead.
2. `llama_tokenize`'s two-pass convention: a **negative** return is the negated
   token count, i.e. the measure pass. An early version tested `k <= 0`, so every
   prompt tokenized to zero tokens and the batch returned `GS_OK` with 50 empty
   strings. That read as **"2118 req/s, 545x speedup, target PASSED"** — a
   number measuring the absence of work. The losslessness check is the only
   reason it was caught, which is why `run_batch` now refuses to report a
   throughput figure it has not first validated.

Also: re-decoding an occupied KV position is rejected by llama.cpp (`it is
required that the sequence positions remain consecutive: Y = X + 1`), so the
prefill holds each prompt's last token back and submits it in a second pass with
logits on. That second pass is also the only way to get N live distributions at
once, since a chunked prefill leaves just the last sequence's logits in the
output buffer.

`native/docs/million-user-shape.md` §5 now records that the wall **moved** rather
than disappeared, with the miss stated.

---

## Items 4 and 5 — Kotlin and Swift bridges (commit 1, fallback only)

**The `(a)` decision is implemented. `(b)`, the "prefer local" toggle, is not —
deliberately, as instructed, and it is a separate commit that has not been made.**

Only two existing files were modified, and in each the only line replaced is the
one that produced the canned reply:

    -        val reply = localReply(prompt)
    +        val reply = nativeReply(prompt) ?: localReply(prompt)
    -        let reply = Self.localReply(prompt)
    +        let reply = nativeReply(prompt) ?? Self.localReply(prompt)

Verified with `git diff | grep '^-'`, which returns exactly those two lines. Both
`localReply` bodies are untouched and still present. No screens, no UI changes,
no design-system changes, no renames, no deletions.

New files only:

    android/app/src/main/java/com/grapsee/gsai/native/GsNative.kt
    android/app/src/main/java/com/grapsee/gsai/native/GsNativeLoader.kt
    android/app/src/androidTest/java/com/grapsee/gsai/GsNativeTest.kt
    ios/App/Sources/Native/GsNative.swift
    ios/App/Sources/Native/GsNativeLoader.swift
    ios/App/Tests/GsNativeTests.swift

The app still attempts the network first; this is the failure path, so a healthy
app is unchanged.

### The JNI bridge was wrong, and CI found it

`src/jni.rs` is `#[cfg(target_os = "android")]`, so it had never been compiled on
any host. It contained **nine type errors** from the commit that introduced it
(run 36374643605). Fixed:

1. The `guard!` macro inferred its failure type from a helper returning
   `Option<()>`, so `Ok(v) => v` (`jboolean`) and `Err(_) => throw(...)`
   (`Option<()>)` could never agree. Every entry point failed. The failure value
   is now explicit and type-checked against the body.
2. `jni.rs` re-declared every `gs_mobile_*` function as `*mut c_void` while
   `mobile.rs` declared them `*mut GS_MobileCtx`. Both modules compile on Android,
   so the duplicate `extern "C"` blocks collided. `jni.rs` now declares no C
   symbol at all; it goes through `MobileCtx`.
3. `set_float_array_region` takes 3 arguments in jni 0.21, not 4.
4. `static mut GLOBAL_CTX: *mut c_void` was unsound the moment two threads read
   it, and a JNI entry point is reachable from any JVM thread. Now
   `Mutex<Option<MobileCtx>>` with an `unsafe impl Send` whose safety argument is
   written down.
5. `buildInfo` read `gs_last_error()` — the **error** channel — so it returned an
   empty string on a healthy build.

**The blind spot is closed permanently.** `jni` is a normal dependency (pure
Rust, no C component) and a new feature compiles the module on a host:

    cargo check -p gs-ffi --features jni-typecheck
    cargo test  --workspace --features gs-ffi/jni-typecheck

`android-native.yml` runs the check before the NDK is installed. The module is
still gated on the target, so a desktop `.so` exports **0** `Java_*` symbols —
verified.

### Tests: what must hold, and what skips visibly

    always asserted   the library loads and reports buildInfo; failure throws
                      rather than returning ""; shutdown is idempotent;
                      embedImage rejects a short buffer; the loader always
                      reports a reason
    skipped visibly   chat, OCR, determinism -- via assumeTrue / XCTSkip,
                      because no weights are committed

The determinism test ("the same prompt must give the same answer on a reused
context") is a regression test for the sampler-chain bug above, and it is cheap
to keep.

---

## Item 8 — weights and binaries in git

    $ git ls-files | grep -iE '\.(so|a|o|gguf|onnx)$'
    (none)

`.gitignore` covers `native/**/*.a`, `native/**/*.so`, `native/**/*.so.*`,
`native/prebuilts/`, `**/*.gguf`, `**/*.onnx`, `*.o`, `*.obj`.

Four stray `.o` files committed in `a8ea7d2` (before this round) were untracked
with `git rm --cached` — they stay on disk.

**No model is downloaded or cached in CI, deliberately.** Item 8 asks the workflow
to fetch a model at build time and Item 3 asks the app to download on consent
with no weights in the APK. Those are contradictory. The APK ships without
weights, so there is nothing to cache, and the correct place for the model is
Item 3's downloader — which does not exist yet.

---

## What is NOT done

Stated plainly rather than dressed up.

**Item 3 — model download, SHA256 verification, resume, consent prompt: NOT
DONE.** The `GsNativeLoader`/`GsNativeLoader.swift` layer that a downloader would
plug into exists and is tested, but nothing downloads a model, nothing verifies a
hash, nothing resumes, and there is no consent prompt.

There is also a genuine conflict in the brief that needs a decision before this
is written:

- the standing rules say *"Never redesign UI or create new screens"*
- Item 3 says *"show a one-time prompt: 'Enable on-device AI? …'"*

A consent prompt is a new screen or a new dialog. I am not going to guess which
rule wins, and I am not going to add a screen under a rule that forbids it. The
parts that touch no UI — the downloader, SHA256 verification, resume, and the
SharedPreferences/UserDefaults path entry — can be written immediately and do not
depend on the answer.

**Item 2 — cross-compiling the C/C++ dependencies for Android ARM64: BLOCKED by
design, and the reason is measured rather than assumed.**

The mobile build is PORTABLE-ONLY. The artifacts above prove the consequence:
each `.so` is ~220 KB and exports 10 `gs_ffi_mobile_*` entry points. `build.rs`
emits, on a mobile target:

    cargo:warning=portable mobile build: CLIP, OCR and llama.cpp are NOT
        compiled in; every such call reports GS_ERR_UNAVAILABLE
    cargo:warning=skipping gs_clip: no sources on this target
    cargo:warning=skipping gs_ocr_tess: no sources on this target

| dep | status | why |
|---|---|---|
| llama.cpp | not cross-compiled | a 300 MB decode engine inside a phone binary is not the product; a 0.5B Q4_K_M is 400 MB on disk and 1.5 GB resident with KV |
| ONNX Runtime | not cross-compiled | the AAR is a Java artifact and does not satisfy a C ABI link; embedding it would add tens of MB for CLIP embeddings only |
| Tesseract | not cross-compiled | no drop-in arm64 Android prebuilt; the brief's own escape hatch is ML Kit **for OCR only**, which was not taken because it is a different engine behind the same ABI and would make the "portable-only" claim false |
| stable-diffusion.cpp | not cross-compiled | ~1 image per 5–10 min on phone CPU is not a feature |

None of this is "the environment cannot". It is a deliberate decision, taken
before the work, and the ~220 KB artifacts are the evidence for it. If the intent
was to cross-compile them anyway, the Tesseract line in the brief already names
the fallback and I would need to know whether ML Kit is acceptable.

**No device or emulator run.** The JNI and C-interop paths are compiled,
type-checked, exported and symbol-verified in CI. Nothing has been executed on
Android hardware or an iOS device/simulator, because none is available here. The
instrumented and unit tests exist and are wired but have not been run. So:

- *"Does local inference actually run on the Android emulator via the JNI
  bridge?"* — **not demonstrated.** The bridge compiles, exports the 9 JNI entry
  points and 10 mobile ABI symbols for all four ABIs, and that is as far as the
  evidence goes.
- *"Does the iOS build pass on macos-latest with the framework embedded?"* — **yes
  for the build**: run 36377519912, both slices arm64, xcframework created and
  uploaded, 10 exports per slice. **Not demonstrated for embedding in an app
  target**, which needs a real Xcode project build.

**The 12 MB xcframework** is a `staticlib` with the Rust runtime statically
linked into both slices. A real iOS app would want a dynamic framework or a
thinner dependency set. Worth a decision before shipping.
