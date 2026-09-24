/*
 * GS AI — C++ NATIVE ENGINE — llama.cpp backend adapter (fallback engine).
 *
 * Fallback per the engine decision: mature model support; iOS Metal GPU
 * (3-10x faster), Android Vulkan (4-8x). Bridged in production via llama.rn;
 * this adapter targets libllama directly so the C++ core can run it
 * standalone (desktop bench, CI) without React Native.
 *
 * Same policy as the Cactus adapter: dlopen libllama; absent library =>
 * GS_ERR_BACKEND_UNAVAILABLE (honest BLOCKED, never simulated — rule 5).
 */
#include "gs_engine/c_api.h"

#include <dlfcn.h>
#include <cstring>
#include <string>

namespace {

/*
 * llama.cpp C surface subset (stable for years):
 *   struct llama_model;  struct llama_context;
 *   llama_model* llama_model_load_from_file(const char*, struct llama_model_params);
 *   llama_context* llama_init_from_model(llama_model*, struct llama_context_params);
 *   ... tokenization/decode loop owned by the adapter's batching code.
 * The full decode loop lands with the linked backend build (native/README.md);
 * v1 wires the load path + availability probing so the registry can rank it.
 */
struct LlamaApi {
    void* (*model_load)(const char*, void*) = nullptr;
    bool ok = false;
    std::string last_error;
};

LlamaApi& llama_api() {
    static LlamaApi api;
    return api;
}

LlamaApi& try_load_llama() {
    LlamaApi& api = llama_api();
    if (api.ok) return api;
    void* h = dlopen("libllama.so", RTLD_NOW | RTLD_LOCAL);
    if (!h) h = dlopen("libllama.dylib", RTLD_NOW | RTLD_LOCAL);
    if (!h) {
        const char* dlerr = dlerror();  // single call: dlerror() clears its buffer
        api.last_error = std::string("dlopen libllama: ") + (dlerr ? dlerr : "not found");
        return api;
    }
    api.model_load = reinterpret_cast<void* (*)(const char*, void*)>(dlsym(h, "llama_model_load_from_file"));
    api.ok = api.model_load != nullptr;
    if (!api.ok) api.last_error = "libllama found but llama_model_load_from_file missing";
    return api;
}

struct LlamaService {
    gs_llm_service_vtable_t vtbl;
    bool model_loaded;
};

gs_status llama_load(gs_llm_service_t* self, const char* model_path) {
    LlamaApi& api = try_load_llama();
    if (!api.ok) return GS_ERR_BACKEND_UNAVAILABLE;
    auto* s = reinterpret_cast<LlamaService*>(self);
    void* model = api.model_load(model_path, nullptr);
    s->model_loaded = model != nullptr;
    return model ? GS_OK : GS_ERR_GENERATION_FAILED;
}

gs_status llama_generate(gs_llm_service_t*, const char*, const gs_generation_options*,
                         char** out_text, gs_generation_stats*) {
    LlamaApi& api = try_load_llama();
    if (!api.ok) return GS_ERR_BACKEND_UNAVAILABLE;
    // Decode loop (tokenize → batch → decode → detokenize) is wired with the
    // full linked backend; v1 honestly reports unavailability instead of
    // emitting placeholder text.
    (void)out_text;
    return GS_ERR_GENERATION_FAILED;
}

void llama_destroy(gs_llm_service_t* self) { std::free(self); }

gs_llm_service_t* llama_create(void) {
    auto* s = static_cast<LlamaService*>(std::malloc(sizeof(LlamaService)));
    if (!s) return nullptr;
    std::memset(s, 0, sizeof(*s));
    s->vtbl.abi_version = GS_ENGINE_ABI_VERSION;
    s->vtbl.backend_id = "llamacpp";
    s->vtbl.load_model = llama_load;
    s->vtbl.generate = llama_generate;
    s->vtbl.generate_stream = nullptr;
    s->vtbl.cancel = nullptr;
    s->vtbl.destroy = llama_destroy;
    s->model_loaded = false;
    return reinterpret_cast<gs_llm_service_t*>(s);
}

} // namespace

extern "C" gs_plugin_entry_t* gs_llamacpp_backend_entry(void) {
    static gs_plugin_entry_t e = [] {
        gs_plugin_entry_t t{};
        t.backend_id = "llamacpp";
        t.priority = 50; // fallback below cactus
        t.serves = "generate";
        t.create = llama_create;
        return t;
    }();
    return &e;
}
