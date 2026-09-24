/*
 * GS AI — C++ NATIVE ENGINE — Cactus backend adapter.
 *
 * Cactus (cactus-compute/cactus): hand-written ARM NEON kernels, TTFT as low
 * as 50ms, 16-70+ tok/s on-device, OpenAI-compatible C/C++ API, tool calling
 * built in. PRIMARY backend for on-device inference.
 *
 * LINKING STRATEGY: the Cactus SDK ships per-platform (Android NDK .so /
 * XCFramework). This adapter dlopen()s `libcactus.so` / `libcactus.dylib`
 * at runtime and resolves its minimal symbol set; when the library is absent
 * the adapter reports GS_ERR_BACKEND_UNAVAILABLE — an honest BLOCKED, never a
 * simulated pass (hard rule 5). Static linking is a CI-platform decision and
 * plugs in at gs_registry_register() time without touching this file's logic.
 */
#include "gs_engine/c_api.h"

#include <dlfcn.h>
#include <cstring>
#include <string>

namespace {

/*
 * Cactus public C surface (subset this engine consumes; see
 * cactus-compute/cactus docs — OpenAI-compatible APIs for C/C++):
 *   int  cactus_init(const char* model_path, ...);
 *   int  cactus_chat(const char* messages_json, const char* options_json, char** out_json);
 *   void cactus_stop(void);
 *   void cactus_deinit(void);
 * Signatures are resolved lazily; a mismatch yields GS_ERR_BACKEND_UNAVAILABLE
 * with the dlerror() text recorded in `last_error`.
 */
struct CactusApi {
    int (*init)(const char*, const char*) = nullptr;
    int (*chat)(const char*, const char*, char**) = nullptr;
    void (*stop)(void) = nullptr;
    void (*deinit)(void) = nullptr;
    bool ok = false;
    std::string last_error;
};

CactusApi& cactus_api() {
    static CactusApi api;
    return api;
}

struct CactusService {
    gs_llm_service_vtable_t vtbl;
    bool model_loaded;
};

CactusApi& try_load_cactus() {
    CactusApi& api = cactus_api();
    if (api.ok) return api;
    void* h = dlopen("libcactus.so", RTLD_NOW | RTLD_LOCAL);
    if (!h) h = dlopen("libcactus.dylib", RTLD_NOW | RTLD_LOCAL);
    if (!h) {
        const char* dlerr = dlerror();  // single call: dlerror() clears its buffer
        api.last_error = std::string("dlopen libcactus: ") + (dlerr ? dlerr : "not found");
        return api;
    }
    api.init = reinterpret_cast<int (*)(const char*, const char*)>(dlsym(h, "cactus_init"));
    api.chat = reinterpret_cast<int (*)(const char*, const char*, char**)>(dlsym(h, "cactus_chat"));
    api.stop = reinterpret_cast<void (*)(void)>(dlsym(h, "cactus_stop"));
    api.deinit = reinterpret_cast<void (*)(void)>(dlsym(h, "cactus_deinit"));
    api.ok = api.init && api.chat && api.deinit;
    if (!api.ok) api.last_error = "libcactus found but required symbols missing";
    return api;
}

gs_status cactus_load(gs_llm_service_t* self, const char* model_path) {
    CactusApi& api = try_load_cactus();
    if (!api.ok) return GS_ERR_BACKEND_UNAVAILABLE;
    auto* s = reinterpret_cast<CactusService*>(self);
    int rc = api.init(model_path, nullptr);
    s->model_loaded = (rc == 0);
    return rc == 0 ? GS_OK : GS_ERR_GENERATION_FAILED;
}

gs_status cactus_generate(gs_llm_service_t* self, const char* prompt,
                          const gs_generation_options* opts, char** out_text,
                          gs_generation_stats* out_stats) {
    (void)self;
    CactusApi& api = try_load_cactus();
    if (!api.ok) return GS_ERR_BACKEND_UNAVAILABLE;
    if (!out_text || !out_stats) return GS_ERR_INVALID_ARG;
    // Cactus accepts OpenAI-style messages JSON; tool calling is native.
    std::string messages = "[{\"role\": \"user\", \"content\": " +
                           std::string("\"") + (prompt ? prompt : "") + "\"}]";
    std::string options = "{}";
    if (opts && opts->system_prompt) {
        options = "{\"system_prompt\": \"" + std::string(opts->system_prompt) + "\"}";
    }
    char* raw = nullptr;
    int rc = api.chat(messages.c_str(), options.c_str(), &raw);
    if (rc != 0 || !raw) return GS_ERR_GENERATION_FAILED;
    // NOTE: Cactus returns OpenAI-compatible JSON; the engine layer owns
    // parsing of choices[0].message.content. Kept minimal here: the full JSON
    // is handed to the caller in v1 (documented in native/README.md) and the
    // Kotlin/Swift bridges strip the envelope — one parser, platform side.
    *out_text = raw;
    out_stats->ttft_ms = -1;   // backend fills when its timing API is linked
    out_stats->total_ms = -1;
    out_stats->tokens_per_sec = -1.0f;
    out_stats->prompt_tokens = -1;
    out_stats->output_tokens = -1;
    return GS_OK;
}

void cactus_destroy(gs_llm_service_t* self) { std::free(self); }

gs_llm_service_t* cactus_create(void) {
    auto* s = static_cast<CactusService*>(std::malloc(sizeof(CactusService)));
    if (!s) return nullptr;
    std::memset(s, 0, sizeof(*s));
    s->vtbl.abi_version = GS_ENGINE_ABI_VERSION;
    s->vtbl.backend_id = "cactus";
    s->vtbl.load_model = cactus_load;
    s->vtbl.generate = cactus_generate;
    s->vtbl.generate_stream = nullptr; // streaming lands with the linked SDK
    s->vtbl.cancel = nullptr;
    s->vtbl.destroy = cactus_destroy;
    s->model_loaded = false;
    return reinterpret_cast<gs_llm_service_t*>(s);
}

} // namespace

extern "C" gs_plugin_entry_t* gs_cactus_backend_entry(void) {
    static gs_plugin_entry_t e = [] {
        gs_plugin_entry_t t{};
        t.backend_id = "cactus";
        t.priority = 100; // primary on-device backend
        t.serves = "generate";
        t.create = cactus_create;
        return t;
    }();
    return &e;
}
