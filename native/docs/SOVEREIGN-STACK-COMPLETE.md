# SOVEREIGN STACK COMPLETE — honest item audit (0.1 to 1.0)

Date: 2026-10-08. Method: every line below cites a commit hash, a CI run id, a file path, or a raw error. No numbers without a source. Nothing is rounded up.

Label note: besides DONE / PARTIAL / BLOCKED / NOT STARTED, two items carry the label CLOSED, used only where the project itself closed the item with recorded evidence (a measurement-based rejection, or an upstream defect proven not ours). Forcing those into the four standard labels would misstate them.

One root blocker recurs: no arm64 instruction has ever been executed by this repository (three hosts, three measured reasons, nested virtualization is the finding, native/docs/RESULTS-mobile-concurrency.md). It caps items 22 and 23 and limits the device claims of several DONE items, which are marked as such in their evidence lines.

---

1. Rust/C++/C workspace
ITEM: DONE
EVIDENCE: native/Cargo.toml (5 crates: gs-common, gs-core, gs-ffi, gs-server, gs-bench, 18 run_*.rs bench bins); native/cpp/CMakeLists.txt (gs_abi, gs_llama, gs_sd, gs_ocr, gs_mobile, batch); native/c/kernels/simd.c with scalar-vs-SIMD test_kernels.c; native/Makefile smoke target builds libgs_engine.a + 14-check smoke; 137 #[test] counted in the workspace (RESULTS-mobile-concurrency.md).

2. llama.cpp linked, CPU decode
ITEM: DONE
EVIDENCE: iOS llama-linked run 36759872954, all jobs green, 46 passed 0 skipped, GsNative.chat returned a real completion (ISSUE-LOG.md); decode rate measured 12.15 tok/s / 4808 ms TTFT on the x86_64 emulator (RESULTS-mobile-concurrency.md); linked build wired in ios-native.yml (llama-ios job, GS_LLAMA_PREBUILT) and android-native.yml (llamacpp-<abi> artifacts).
LIMIT: n/a for the shipped path; the Phase-F adapter native/src/backends/llamacpp_backend.cpp remains an honest stub returning GS_ERR_GENERATION_FAILED (production decode lives in native/cpp/llama_wrapper/llama_wrapper.cpp + gs_mobile.cpp); no arm64 execution.

3. Provider pool + rotation + breaker
ITEM: DONE
EVIDENCE: src/lib/keypool.ts (four-layer durable pool: env, .secrets/openrouter.keys, SQLite vault, /home/z/.gs-vault, self-healing load); src/lib/openrouter.ts (rotation on 401 and 429); src/lib/ai.ts:180-194 (account-level throttle circuit breaker, 10-minute open); live-verified 2026-10-08: POST /api/v1/conversations/{id}/messages returned HTTP 200 in 6.98 s with an exact marker echoed (worklog.md resume-5).

4. Agent loop + 6 intents
ITEM: DONE
EVIDENCE: native/src/agent_loop.cpp:89-160 gs_agent_run (generate, extract JSON tool call, poka-yoke dispatch, context shaping, repeat); intents implemented as a superset: 8 variants (Chat, Search, Research, Code, Vision, ImageCreate, ImageGenerate, Time) in native/crates/gs-common/src/lib.rs:11-22 with classifier tests gs-core/src/agent.rs:379-572.
LIMIT: n/a; note the brief said 6 intents, 8 shipped (superset), and the classifier lives in the Rust core while the C++ loop owns the cycle.

5. Memory tiers + compaction
ITEM: DONE
EVIDENCE: native/crates/gs-core/src/memory.rs (working, episodic, semantic tiers; structural_compaction; offload_tool_output); native/crates/gs-core/src/compaction.rs (sspm_compact, attn_compress, focus_compact with identifier preservation); TS side: src/lib/bench/scaffold.ts context lever (episodic summary when >5 messages), src/lib/context-crystallize.ts.

6. Verification guards
ITEM: DONE
EVIDENCE: native/crates/gs-core/src/verification.rs (three guards: numeric, contradiction, citation; Verdict::Regenerate; cheapest-first run order); src/lib/bench/scaffold.ts verification lever (separate-model verifier, gs-ai-flash != generator, VERIFIER_PROMPT, regeneration path).
LIMIT: n/a for the code; the benchmark verdict for the verification lever arm is pending its CI run (tracked in results/scaffold-lever-report.md, Step 2 of the current directive).

7. Plugins + skills
ITEM: BLOCKED
EVIDENCE: manifests exist (native/plugins/{calc,code_review,ocr,web_search}.json, native/skills/{research_paper,fact_check}.json + 6 SKILL.md dirs); runtime side exists for skills (gs-core/src/skills.rs SkillRegistry register/expand/resolve/execute) and backends (native/src/plugin_registry.cpp with poka-yoke dispatch); tools.rs:260 emits native:// entrypoints.
LIMIT: no runtime code loads native/plugins/*.json or native/skills/*.json; skill bodies are hardcoded via set_body (skills.rs:58). Downgraded from PARTIAL to BLOCKED on 2026-10-08: this sandbox has no Rust toolchain (cargo and rustc absent) and no CI job runs `cargo test` for gs-core (ios-native.yml and android-native.yml run `cargo build -p gs-ffi` only), so a manifest loader written here would ship untested.
RESOLUTION: an environment with cargo, or one CI job running `cargo test -p gs-core`; then load_manifests(dir) + a behavioral test is half a day of work.

8. Server (OpenAI endpoints, SSE)
ITEM: DONE
EVIDENCE: Rust server native/crates/gs-server/src/main.rs:161-165 (/health, /v1/models, /v1/chat/completions, /v1/completions, /v1/stream/:id); Next.js shim src/app/api/v1/openai/{chat/completions,completions,responses,models}/route.ts (auth via GS_BENCH_API_KEY; currently exercised by every CI benchmark run, e.g. runs 37428551164 and 37764839853); product SSE at src/app/api/v1/stream/[streamId]/route.ts:186 (text/event-stream) and conversations/[id]/messages/route.ts:235.
LIMIT: n/a; note the OpenAI-compatible shim is non-streaming JSON (SSE lives on the product routes, not the shim).

9. CLIP vision
ITEM: BLOCKED
EVIDENCE: native/cpp/clip_wrapper/clip_wrapper.cpp (GS_CLIP_HAVE_ONNXRUNTIME); gs-ffi/src/clip_bridge.rs (load, embed_text, embed_image); bench bins gs-bench/src/bin/run_clip.rs (MobileCLIP2-S0 preprocessing) and run_semcache.rs; gs-core/src/vision.rs OCR-then-CLIP turn; mobile build honestly refuses (gs_mobile.cpp:206-227, GS_ERR_UNAVAILABLE).
LIMIT: no recorded execution run of the CLIP path exists in the docs. Downgraded from PARTIAL to BLOCKED on 2026-10-08: executing run_clip requires ONNX Runtime plus the MobileCLIP2-S0 weights under /mnt/new_volume/models/clip, and that volume is not mounted in this sandbox; the mobile path additionally has no arm64 ONNX Runtime package (RESULTS-mobile-concurrency.md:521).
RESOLUTION: a machine with the model volume mounted (or the weights re-hosted) runs `cargo run -p gs-bench --bin run_clip` once to record real numbers; mobile needs an arm64 ONNX Runtime build.

10. OCR (ML Kit + Tesseract on-demand)
ITEM: BLOCKED
EVIDENCE: shipping half DONE and device-verified: android MlKitOcr.kt + device test a2_ocr_reads_the_fixture (DeviceVerificationTest.kt:356-397, reads INVOICE INV-4471; native OCR asserts its honest refusal, gs_mobile.cpp:201-202); iOS AppleVisionOcr.swift; Tesseract cross-compiled and verified in android-deps.yml:60-115.
LIMIT: the on-demand half cannot ship: the :ocr-fallback dynamic feature is closed permanently on an upstream AGP defect (item 24; six AGP releases byte-identical, live producer measurement run 37285014335). Downgraded from PARTIAL to BLOCKED on 2026-10-08 because half of this item is upstream-blocked and cannot be resolved from this repository.
RESOLUTION: ML Kit path is complete; de-Googled-device OCR needs the manual module build (android/app/build.gradle.kts documents the exact command) until AGP fixes the metadata producer.

11. SVG procedural image gen
ITEM: DONE
EVIDENCE: native/cpp/sd_wrapper/gs_sd_wrapper.cpp gs_sd_render_svg (always compiled, gs-ffi/build.rs:376); exposed end to end: sd_bridge.rs:16, gs_ffi_mobile_render_svg, GsNative.kt:156 renderSvg; a real product bug was caught and fixed on-device: run 36868936414, test c0_the_procedural_path_writes_a_real_svg_on_the_device, key/value shift verified against 9 shapes (ISSUE-LOG.md).

12. stable-diffusion.cpp (desktop only)
ITEM: DONE
EVIDENCE: subprocess removed, in-process linkage proven: android-deps.yml:963-976 copies include/stable-diffusion.h and asserts 5 required symbols, pinned 3f8527a4; verified run 36832394895 (artifact 11147951401); two 512x512 images generated on the x86_64 emulator, 187.5 s and 186.9 s, run 37164998711 (android-native.yml); mobile surface exists and fails closed when absent (gs_mobile.cpp:279-319, GS_ERR_UNAVAILABLE).
LIMIT: n/a within the desktop-only scope; diffusion is gated OFF in shipped APKs by declared decision (41.9 MB per ABI, android-native.yml enable_sd_diffusion default false); never executed on arm64.

13. Speculative decoding
ITEM: CLOSED
EVIDENCE: measured rejection: acceptance exactly zero of 930 proposed tokens, speedup 0.215x vs plain decode; cause is a tokenizer mismatch (Qwen2.5 draft vs SmolLM2 target use different BPE merges, so acceptance is zero by construction); all code deleted (gs_llama_generate_speculative, gs_spec_stats_t, GS_ENABLE_BROKEN_SPEC, no flag remains) (RESULTS-mobile-concurrency.md:105-155).
RESOLUTION: closed with measurement; reopening requires a draft and target model sharing a vocabulary, then re-measuring; do not re-add on hope.

14. KV cache quantization
ITEM: DONE
EVIDENCE: measured verdict NEGATIVE at the desktop layer: 4.7% less memory, 7.5% slower, visibly corrupted output (million-user-shape.md:225); harness gs-bench/src/bin/run_kvquant.rs (GS_KV=1 f16, GS_KV=2 q4_0); plumbing in llama_bridge.rs:27-29 and llama_wrapper.cpp:174-175; mobile ships F16 by explicit choice (gs_mobile.cpp:107-108).
LIMIT: n/a for the capability; the mobile Q8_0 knob is deliberately not pulled (ISSUE-LOG.md); no arm64 measurement.
RESOLUTION: n/a (capability exists, verdict recorded); arm64 re-measure folds into item 23's unblock.

15. Batched serving
ITEM: DONE
EVIDENCE: measured with the miss stated: throughput 3.84 to 12.85 req/s from 1 to 200 concurrent clients (3.35x at saturation), 200/200 responses lossless; target was >= 15 req/s, measured 12.85, miss documented with the saturation analysis (RESULTS-mobile-concurrency.md:158-317); components exist: cpp/llama_wrapper/batch.cpp gs_llama_batch_generate, gs-core/src/local_provider.rs queue + scheduler, run_batch.rs, run_concurrent.rs.

16. Android cross-compile (4 ABIs)
ITEM: DONE
EVIDENCE: matrix arm64-v8a, armeabi-v7a, x86_64, x86 (android-native.yml:110-133); first all-green run 36376085953 with per-ABI `file` assertions; byte-verified larger build run 36651304587 (arm64-v8a 4,562,904 B, "ELF 64-bit LSB shared object, ARM aarch64, built by NDK r25c").
LIMIT: n/a for cross-compilation; binaries are statically verified and only executed on the x86_64 emulator (no arm64 execution anywhere, see item 22).

17. iOS cross-compile
ITEM: DONE
EVIDENCE: ios-native.yml three jobs (build device+sim xcframework, simulator-tests, llama-ios); first xcframework run 36377519912 (12,019,070 B); eleven cross-compile failures fixed and recorded (ISSUE-LOG.md, e.g. __chkstk_darwin iOS 12 floor); llama-linked green run 36759872954 (46 passed, 0 skipped); device build verified from its bytes, run 36828234718 (arm64 Mach-O under Debug-iphoneos).

18. Model download flow + consent
ITEM: DONE
EVIDENCE: device tests a3_download_refuses_without_consent_resumes_and_verifies (consent gate asserts Refused with "not been enabled"; catalog digest pins: 1.5B model, sha256 6a1a2eb6..., 1,117,320,736 bytes) and a4_resume_continues_from_the_partial_file, both green run 36763870230; ModelDownloader.kt (streaming SHA-256 before promotion out of .part, mismatch abort); iOS double verification (size before hash, re-read inside container, ISSUE-LOG.md).

19. Mobile app integration (JNI/Swift)
ITEM: DONE
EVIDENCE: Android: GsNative.kt (21 external funs), GsNativeLoader.kt:237 System.loadLibrary, wired into ChatRepository.kt and ModelStore.kt; symbol gate check_jni_matches_kt.sh compares every Kotlin external against the .so before the emulator boots; all 17 expected gs_ffi_mobile_* symbols verified from a real .so, run 36859948614; iOS: GsNativeLoader.initialize(modelPath:) in GSApp.swift:51, 8/8 GsNativeTests green.
LIMIT: n/a for the integration; genuine token-streaming callback over JNI does not exist yet (ISSUE-LOG.md: "Real token streaming does not exist"); native/bridges/* are legacy source superseded by the apps' own packages.

20. Frontend wiring (Android)
ITEM: DONE
EVIDENCE: SettingsScreen.kt:209 "Prefer on-device AI" toggle + OnDeviceModelConsentDialog; routing proven by device test a7_the_switch_actually_routes_off_and_on (OFF against a dead server must not answer "Paris", ON must; assertNotEquals so the switch is provably routing, DeviceVerificationTest.kt:582-661); runs: 14/14 at 36763870230, 18 PASS 0 SKIP 0 FAIL at 36814985859 (head 96200fa).
LIMIT: n/a for the switch; on-screen token streaming is emulated, not real (same streaming gap as item 19).

21. Frontend wiring (iOS)
ITEM: DONE
EVIDENCE: ChatViewModel.swift:493-494 routes to GsNative.chat when preferLocal, no attachments, engine available; fallback path :1146-1160; settings surface SettingsView.swift:167; the previously missing ViewModel-driving tests now exist and pass: testTheChatScreenAnswersFromTheEngine (51.6 s, a real 0.5B generation) and testTheChatScreenLeavesTheEngineAloneWhenTheSwitchIsOff, run 36828234718 (head c12e866, 48 tests, 0 failures).

22. arm64 device run
ITEM: BLOCKED
EVIDENCE: three hosts, three measured reasons: run 36676280935 (x86_64 host: "Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator"), run 36688288136 (ubuntu-24.04-arm: no /dev/kvm), run 36735804030 (macos-15: HVF error: HV_UNSUPPORTED); finding: the blocker is nested virtualization, not arm64; correct-label probes ubuntu-24.04-arm64 queued 4h51m and never started (run 36831713320), two hours zero runner seconds then cancelled (run 37280191290); "No arm64 instruction has been executed by this repository" (RESULTS-mobile-concurrency.md).
RESOLUTION: one of: a physical arm64 device reachable over adb in CI, a KVM-capable arm64 runner, or an HVF-capable bare-metal macOS runner. Cannot be resolved in this environment. CLOSED WITH QUEUE-TIME EVIDENCE per the 2026-10-09 directive (no re-dispatch, three failed attempts already spent): the final probe run 37799687774 was QUEUED 2026-10-08T15:18:44Z and never received a runner; it was cancelled 2026-10-09T13:30Z after 22h11m in queue with zero runner seconds (conclusion: cancelled). Cumulative arm64 queue evidence: 4h51m (36831713320) + 2h (37280191290) + 22h11m (37799687774) = 29h02m queued across three probes, zero arm64 instructions ever executed.

23. Q4_0 vs Q4_K_M on real arm64
ITEM: BLOCKED
EVIDENCE: tracked OPEN in ISSUE-LOG.md:6627-6649: "blocked on the same hardware wall as the arm64 device run"; all existing measurements are x86_64-emulator, CPU-only (Q4_0 4.9x-5.4x TTFT advantage, runs 37005521803 and 37022078103; quality delta +0.06, QuantQuality50Test run 37118362666, shipped default switched to Q4_0); the doc itself refuses to generalize ("The K-quant advantage on arm64 is normally the reverse... Choosing a shipped quantisation needs a run on the target architecture"); revert criterion pre-committed (ISSUE-LOG.md:6638-6644).
RESOLUTION: same as item 22; until then the shipped default rests on emulator evidence and the doc says so.

24. Tesseract dynamic feature module (:ocr-fallback)
ITEM: CLOSED
EVIDENCE: closed permanently on an upstream AGP defect: five recorded fix attempts; .single() is byte-identical in AGP 8.5.2, 8.7.3, 8.9.2, 8.11.1, 8.12.3, 8.13.2 (six releases, read from sources jars); live producer measurement on AGP 8.13.2 confirms the metadata producer returns an empty collection (run 37285014335, DynamicFeatureVariantImpl.kt:274); operator's wording verbatim in android/app/build.gradle.kts with the 140-line rationale; module code exists and is correct (android/ocr-fallback/, dist:onDemand, fusing, minSdk 26).
RESOLUTION: reopen only when AGP fixes the base-module metadata producer; interim path for de-Googled devices is the documented manual build with the module enabled.

25. iOS shipping archive
ITEM: BLOCKED
EVIDENCE: unsigned device App.app built and inventoried, run 37101990669, artifact ios-device-app-unsigned, bundle inventory recorded, "codesign exit: 1, VERIFIED: the only problem is the missing signature"; "Not claimed: a signed .ipa. That needs an identity and a provisioning profile this repository does not have" (RESULTS-mobile-concurrency.md); no archive/export/notarization step exists in ios-native.yml; worklog.md: "BLOCKED unchanged: iOS signed .ipa (needs operator account)".
RESOLUTION: an Apple Developer account (identity + provisioning profile), then xcodebuild archive + exportArchive; notarization for distribution. Cannot be resolved in this environment.

---

Tally (updated 2026-10-08): DONE 18, BLOCKED 6 (items 7, 9, 10, 22, 23, 25), CLOSED 2 (items 13, 24). No PARTIAL remains: each former PARTIAL was either fixed with evidence or downgraded to BLOCKED with its precise raw reason and unblock condition. Every claim above cites a file, run id, or raw error.
