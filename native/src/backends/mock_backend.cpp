/*
 * GS AI — C++ NATIVE ENGINE — deterministic mock backend.
 *
 * Used for ENGINE-OVERHEAD measurement (registry select, vtable dispatch,
 * agent-loop step cost, context shaping) WITHOUT a model: the numbers it
 * produces are the C++ core's own cost, never presented as model inference.
 *
 * Behavior: echoes a calculator-style tool call on step 1 when the prompt
 * contains "CALC", otherwise returns a final answer. Deterministic.
 */
#include "gs_engine/c_api.h"

#include <cstring>
#include <string>

namespace {

struct MockService {
    gs_llm_service_vtable_t vtbl;
    bool loaded;
};

gs_status mock_load(gs_llm_service_t* self, const char* model_path) {
    auto* s = reinterpret_cast<MockService*>(self);
    (void)s;
    if (!model_path || !*model_path) return GS_ERR_INVALID_ARG;
    return GS_OK;
}

gs_status mock_generate(gs_llm_service_t* self, const char* prompt,
                        const gs_generation_options* opts, char** out_text,
                        gs_generation_stats* out_stats) {
    (void)self;
    (void)opts;
    if (!out_text || !out_stats) return GS_ERR_INVALID_ARG;
    std::string p = prompt ? prompt : "";
    std::string reply;
    if (p.find("CALC") != std::string::npos && p.find("TOOL_RESULT") == std::string::npos) {
        reply = "{\"tool\": \"calculator\", \"args\": {\"expression\": \"2+2\", \"precision\": \"integer\"}}";
    } else {
        reply = "42";
    }
    char* buf = static_cast<char*>(std::malloc(reply.size() + 1));
    if (!buf) return GS_ERR_OUT_OF_MEMORY;
    std::memcpy(buf, reply.c_str(), reply.size() + 1);
    *out_text = buf;
    out_stats->ttft_ms = 0;
    out_stats->total_ms = 0;
    out_stats->tokens_per_sec = -1.0f;
    out_stats->prompt_tokens = -1;
    out_stats->output_tokens = -1;
    return GS_OK;
}

gs_status mock_generate_stream(gs_llm_service_t* self, const char* prompt,
                               const gs_generation_options* opts, gs_stream_callback cb,
                               void* user, gs_generation_stats* out_stats) {
    (void)self;
    (void)prompt;
    (void)opts;
    if (!out_stats || !cb) return GS_ERR_INVALID_ARG;
    const char* tokens[] = {"4", "2"};
    for (const char* t : tokens) {
        if (cb(t, user) != 0) return GS_ERR_CANCELLED;
    }
    out_stats->ttft_ms = 0;
    out_stats->total_ms = 0;
    out_stats->tokens_per_sec = -1.0f;
    out_stats->prompt_tokens = -1;
    out_stats->output_tokens = 2;
    return GS_OK;
}

void mock_cancel(gs_llm_service_t*) {}
void mock_destroy(gs_llm_service_t* self) { std::free(self); }

gs_llm_service_t* mock_create(void) {
    auto* s = static_cast<MockService*>(std::malloc(sizeof(MockService)));
    if (!s) return nullptr;
    std::memset(s, 0, sizeof(*s));
    s->vtbl.abi_version = GS_ENGINE_ABI_VERSION;
    s->vtbl.backend_id = "mock";
    s->vtbl.load_model = mock_load;
    s->vtbl.generate = mock_generate;
    s->vtbl.generate_stream = mock_generate_stream;
    s->vtbl.cancel = mock_cancel;
    s->vtbl.destroy = mock_destroy;
    s->loaded = false;
    return reinterpret_cast<gs_llm_service_t*>(s);
}

gs_status mock_tool_execute(gs_tool_t*, const char* args_json, char** out_summary) {
    std::string out = "[calculator] parsed args=" + std::string(args_json ? args_json : "{}") + " result=4";
    char* buf = static_cast<char*>(std::malloc(out.size() + 1));
    if (!buf) return GS_ERR_OUT_OF_MEMORY;
    std::memcpy(buf, out.c_str(), out.size() + 1);
    *out_summary = buf;
    return GS_OK;
}

} // namespace

extern "C" gs_llm_service_vtable_t* gs_mock_backend_vtable(void) {
    // Exposed so tests can register the mock explicitly.
    static gs_llm_service_vtable_t v = [] {
        gs_llm_service_vtable_t t{};
        t.abi_version = GS_ENGINE_ABI_VERSION;
        t.backend_id = "mock";
        t.load_model = mock_load;
        t.generate = mock_generate;
        t.generate_stream = mock_generate_stream;
        t.cancel = mock_cancel;
        t.destroy = mock_destroy;
        return t;
    }();
    return &v;
}

extern "C" gs_plugin_entry_t* gs_mock_backend_entry(void) {
    static gs_plugin_entry_t e = [] {
        gs_plugin_entry_t t{};
        t.backend_id = "mock";
        t.priority = 10;
        t.serves = "generate, generate_stream";
        t.create = mock_create;
        return t;
    }();
    return &e;
}

extern "C" gs_tool_t* gs_mock_calculator_tool(void) {
    static gs_tool_t t = [] {
        gs_tool_t x{};
        x.name = "calculator";
        x.args_schema = "{\"expression\": string (required), \"precision\": \"integer\"|\"float\"}";
        x.execute = mock_tool_execute;
        x.user = nullptr;
        return x;
    }();
    return &t;
}
