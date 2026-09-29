# ISSUE LOG

Running record, newest last. Read this first.

Branch: `native-runtime`. All commits pushed. Working copy `/tmp/opencode/gsai`
(`/mnt/new_volume/.git/index` is wedged and is not being repaired).

Time is UTC.

| Time | Issue | Status | Commit | Evidence |
|------|-------|--------|--------|----------|
| — | iOS build + simulator tests | NOT STARTED | — | — |
| — | dynamic feature module (`:ocr-fallback`) | BLOCKED (2/4 attempts) | — | see RESULTS-mobile-concurrency.md |
| 04:0x | arm64-v8a `libgs_ffi.so` publishes | **PASS** | `7723183` | run `36543916433`, job `109327378295` success; `libgs_ffi.so` ELF64 `machine=AArch64`, 4,529,528 B |
| 04:0x | x86_64 `libgs_ffi.so` publishes | **PASS** | `7723183` | run `36543916433`, job `109327377954` success |
| 04:0x | armeabi-v7a / x86 `.so` publish | FAILING | `7723183` | run `36543916433`: `109327377609`, `109327377972` failure. 32-bit ABIs, not yet diagnosed |
| — | arm64 device run (emulator) | NOT ATTEMPTED | — | needs KVM/TCG; see note below |
| — | `a1` chat returns coherent English | NOT VERIFIED | `d35f6d4` | new assertions committed, never executed |
| — | `a2` OCR returns `INV-4471` | NOT VERIFIED | `c9bafb9` | fixture generation committed, never executed |
| — | `a3` consent + `a4` resume + SHA-256 | PARTIAL | `d35f6d4` | a4 PASSED on x86_64 run `36515129722`; a3 SKIPPED |
| — | `a5` prefer-local default OFF | **PASS** | `d35f6d4` | run `36515129722`, `a5_prefer_local_is_off_by_default` PASS |
| — | `a0` `isAvailable == true` | **PASS** | `d35f6d4` | run `36515129722`: `selfCheck after init = context=present backend=available(llama.cpp)` |
| — | `a1` chat non-empty (weak bar) | PASS (superseded) | `816d27f` | run `36515129722`: `chat -> , i have a question about the following code:` — a PROMPT FRAGMENT, not a reply |

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
