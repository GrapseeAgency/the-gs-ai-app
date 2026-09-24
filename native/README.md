# GS AI — C++ Native Engine (Phase F)

RunAnywhere-pattern architecture: the C++ core owns the agent loop, tool
dispatch, context management, verification hooks and model routing; platform
SDKs are thin bridges; model inference lives behind swappable backend plugins.

```
native/
  include/gs_engine/c_api.h     C ABI — the ONLY cross-language surface
  src/plugin_registry.cpp       plugin registry + poka-yoke tool validation + 8k context shaping
  src/agent_loop.cpp            agent loop (parse → validate → dispatch → shape → repeat)
  src/backends/mock_backend.cpp deterministic mock (engine-overhead measurement only)
  src/backends/cactus_backend.cpp   PRIMARY device backend (dlopen libcactus; ARM NEON kernels live there)
  src/backends/llamacpp_backend.cpp FALLBACK device backend (dlopen libllama; Metal/Vulkan live there)
  bridges/kotlin/GsNativeEngine.kt  JNI bridge (source; wiring step below)
  bridges/swift/GsNativeEngine.swift Swift bridge over the same C header
  tests/smoke.cpp               14-check smoke + engine-overhead microbenchmarks
```

## FFI rules (enforced)
- C ABI only. No C++ types, no RTTI (`-fno-rtti`), and no exceptions cross the
  boundary — every `extern "C"` entry catches and returns a `gs_status` code.
- Service contract: the handle's **first member is the vtable by value**;
  recover it with `reinterpret_cast<gs_llm_service_vtable_t*>(handle)`.
  (A pointer-vs-value mismatch here produced a real SEGV at offset 0x11 —
  see worklog Phase F; the smoke test pins this contract now.)
- Memory: strings returned to callers are `malloc`'d and freed with
  `gs_free_string` / `free`.

## Plugin registry
Backends register a `gs_plugin_entry_t { backend_id, priority, serves, create }`.
`gs_registry_select(primitive)` returns the highest-priority plugin serving the
primitive. Priority today: cactus 100 > llamacpp 50 > mock 10. Swapping the
on-device backend is a registry change, not an app change — pinned by the
smoke test ("backend swap without touching consumer code").

## Honesty contract for measurements (hard rule 5)
The mock backend measures the C++ CORE's own overhead ONLY. TTFT, tok/s,
battery and offline capability are backend+device properties. They are
reported as NOT MEASURED (–1 / explicit BLOCKED) until a device run with a
linked backend exists. `native/tests/smoke.cpp` prints machine-readable
`METRIC {json}` lines for bench artifacts.

Sandbox measurement (x86-64, g++ 14.2 -O2, mock backend — engine overhead):
see `native/ENGINE-OVERHEAD.json`.

## Device wiring (next step, deliberately not done here)
1. Android: `android/app/build.gradle` gains
   `externalNativeBuild.cmake.path "../../native/CMakeLists.txt"`; CMake
   builds `libgs_engine.so` per ABI and links the Cactus SDK
   (`libcactus.so`, ARM NEON kernels). Kotlin bridge then loads it.
2. iOS: XCFramework containing `libgs_engine.a` + `c_api.h` modulemap;
   Cactus ships an XCFramework; Swift bridge imports the C header directly.
3. Both apps keep the server path as default; the engine path rolls out
   behind a flag after the on-device audit (TTFT/tok-s/battery/offline)
   produces its artifact.

Deliberate omissions (honest scope notes):
- No CMakeLists.txt is committed yet because no backend .so can be linked in
  this sandbox; committing one that references an absent SDK would break
  `assembleDebug`/CI for zero benefit.
- The llamacpp adapter wires the load/availability path only; its decode loop
  lands with the linked backend build, and `generate` honestly returns
  `GS_ERR_GENERATION_FAILED` until then (never placeholder text).

## Build & test (host)
```
cd native && make smoke   # 14 checks + METRIC lines
```
