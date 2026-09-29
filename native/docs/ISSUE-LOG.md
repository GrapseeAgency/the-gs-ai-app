# ISSUE LOG

Running record, newest last. Read this first.

Branch: `native-runtime`. All commits pushed. Working copy `/tmp/opencode/gsai`
(`/mnt/new_volume/.git/index` is wedged and is not being repaired).

Time is UTC.

| Time | Issue | Status | Commit | Evidence |
|------|-------|--------|--------|----------|
| — | iOS build + simulator tests | NOT STARTED | — | — |
| 14:0x | fixture glyph `V` unreadable by ML Kit | OPEN, 3 attempts | `ae4a909` | `INYOICE INY-4471` — every digit/dash/word exact. Wrong 3 ways: U-shape, Y-shape, and a bolding pass that was a no-op (`ae4a909`). A 5x7 V is ambiguous; the next lever is a bigger font, not a different V |
| 14:0x | a3 download consent gate | SKIP | — | never fails, never passes; the reason is understood and unfixed |
| — | dynamic feature module (`:ocr-fallback`) | BLOCKED (2/4 attempts) | — | see RESULTS-mobile-concurrency.md |
| 04:0x | arm64-v8a `libgs_ffi.so` publishes | **PASS** | `7723183` | run `36543916433`, job `109327378295` success; `libgs_ffi.so` ELF64 `machine=AArch64`, 4,529,528 B |
| 04:0x | x86_64 `libgs_ffi.so` publishes | **PASS** | `7723183` | run `36543916433`, job `109327377954` success |
| 04:0x | armeabi-v7a / x86 `.so` publish | FAILING | `7723183` | run `36543916433`: `109327377609`, `109327377972` failure. 32-bit ABIs, not yet diagnosed |
| — | arm64 device run (emulator) | NOT ATTEMPTED | — | needs KVM/TCG; see note below |
| 12:5x | `a1b` chat answers the question asked | **PASS** | `732bac9` | run `36566952196`: asked "What is the capital of France? Answer with one word." -> `Paris.` |
| 12:5x | `a1` chat returns text (strong bar) | **PASS** | `732bac9` | run `36566952196`: `chat("hello") -> Hello! How can I assist you today?` |
| 12:5x | `<|im_end|>` leaking into the reply | FIXED, UNVERIFIED | `732bac9` | `special=false` + strip; needs a run |
| 12:5x | OCR fixture delivered to the app | FIXED, UNVERIFIED | `732bac9` | `run-as` into filesDir; needs a run. a2 has never seen the image |
| 14:0x | `a2` OCR: marker not matched (V only) | FAIL | `3defde1` | run `36601495339`: `INYOICE INY-4471 DUE 2026-03-01` — every digit, dash and word is exact; only `V` misreads |
| 14:0x | `a2`/`ocrReturns` now call ML Kit, not the native stub | FIXED | `3defde1` | native `runOcr` has no engine; ML Kit 16.0.1 is bundled and now has a call site |
| 14:0x | `failureIsAnException` passed only while broken | **PASS** | `006ff4d` | run `36601495339` PASS: `chat("")` raises, `chat("hello")` returns text |
| 12:5x | arm64-v8a `.so` with chat template | **PASS** | `732bac9` | run `36561597743` |
| 12:5x | armeabi-v7a / x86 `.so` | FAILING | — | run `36561597743`. 32-bit ABIs, undiagnosed |

| — | `a3` consent + `a4` resume + SHA-256 | PARTIAL | `d35f6d4` | a4 PASSED on x86_64 run `36515129722`; a3 SKIPPED |
| — | `a5` prefer-local default OFF | **PASS** | `d35f6d4` | run `36515129722`, `a5_prefer_local_is_off_by_default` PASS |
| — | `a0` `isAvailable == true` | **PASS** | `d35f6d4` | run `36515129722`: `selfCheck after init = context=present backend=available(llama.cpp)` |
| — | `a1` chat non-empty (weak bar) | PASS (superseded) | `816d27f` | run `36515129722` prompt fragment; superseded by `a1b` above |

## BLOCKED

**armeabi-v7a and x86 do not publish.** No diagnosis yet. Distinct from arm64
(x86_64), which publishes. Needs its own raw log.

**Dynamic feature module.** Left commented out. `com.android.dynamic-feature`
fixed the variant error; the remaining failure is inside the module's manifest
task (`Failed to calculate ... 'applicationId' > Collection is empty`) and a split
must not declare `applicationId`, so AGP is resolving it on the base variant.
2 of the 4 permitted attempts used.

**arm64 device run.** An `arm64-v8a` Android system image on an `x86_64` runner
has no hardware virtualisation available (KVM only virtualises the host
architecture). The boot outcome is unknown and has not been attempted. Until it
is, every device result in this file is from the **x86_64 emulator**, which is
not the shipping ABI. No arm64 code path has ever executed.

## The two mistakes that cost the most time

Recorded so they are not repeated.

1. **`build.rs` printed `portable mobile build` unconditionally** for every mobile
   target, seven lines before the llama.cpp linkage was attempted. It proved
   nothing, and it was believed. A CI gate built on it could never pass.
2. **`android-native.yml` had `"llamacpp-x86_64"` hardcoded** in the artifact
   selector while the *paths* beside it were parameterised. Every ABI downloaded
   the x86_64 archives. Four separate gates — `android-deps`, `assert_arch_member.sh`,
   the CMake architecture check, and an ELF check — all passed, because every one
   of them ran somewhere other than the job that did the unpacking.
