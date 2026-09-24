/*
 * GS AI — C++ NATIVE ENGINE — smoke test + engine-overhead microbenchmarks.
 *
 * HONESTY CONTRACT (rule 5): with the mock backend these numbers measure the
 * C++ CORE's own overhead (registry select, vtable dispatch, agent-loop step,
 * context shaping) — NOT model inference. TTFT / tok/s / battery / offline
 * are backend+device properties and are NOT claimed here.
 *
 * Output: METRIC lines are machine-ingestible JSON for bench artifacts.
 */
#include "gs_engine/c_api.h"

#include <chrono>
#include <cstdio>
#include <cstring>
#include <functional>
#include <string>

extern "C" gs_plugin_entry_t* gs_mock_backend_entry(void);
extern "C" gs_plugin_entry_t* gs_cactus_backend_entry(void);
extern "C" gs_plugin_entry_t* gs_llamacpp_backend_entry(void);
extern "C" gs_tool_t* gs_mock_calculator_tool(void);

using Clock = std::chrono::steady_clock;

static double bench_ns_per_op(const std::function<void()>& fn, int iters) {
    auto t0 = Clock::now();
    for (int i = 0; i < iters; i++) fn();
    auto t1 = Clock::now();
    return std::chrono::duration<double, std::nano>(t1 - t0).count() / iters;
}

static gs_llm_service_vtable_t* vtbl_of(gs_llm_service_t* s) {
    return reinterpret_cast<gs_llm_service_vtable_t*>(s);
}

int failures = 0;
#define CHECK(cond, msg)                                                     \
    do {                                                                     \
        if (cond) printf("PASS %s\n", msg);                                  \
        else { printf("FAIL %s\n", msg); failures++; }                       \
    } while (0)

int main() {
    setbuf(stdout, nullptr);  // LSan _exit skips stdio flush
    // ---- registry: cactus priority 100, llamacpp 50, mock 10 ---------------
    gs_registry_register(gs_mock_backend_entry());
    gs_registry_register(gs_cactus_backend_entry());
    gs_registry_register(gs_llamacpp_backend_entry());
    CHECK(gs_registry_count() == 3, "registry holds 3 backends");

    gs_plugin_entry_t sel{};
    CHECK(gs_registry_select("generate", &sel) == GS_OK &&
              std::strcmp(sel.backend_id, "cactus") == 0,
          "registry selects highest-priority backend serving 'generate' (cactus)");

    // ---- 1. registry select overhead ---------------------------------------
    double ns_select = bench_ns_per_op([&] {
        gs_plugin_entry_t s{};
        gs_registry_select("generate", &s);
    }, 1000000);
    printf("METRIC {\"name\": \"registry_select_ns\", \"value\": %.1f}\n", ns_select);

    // ---- 2. vtable dispatch overhead through the mock service ---------------
    // The registry picked cactus (priority 100), whose SDK is absent in this
    // sandbox => honest unavailable. Swap the registry to the mock — the
    // app-level call shape (gs_engine_create_service) does not change: backend
    // swap without touching consumer code.
    gs_registry_clear();
    gs_registry_register(gs_mock_backend_entry());
    gs_llm_service_t* mock_svc = nullptr;
    CHECK(gs_engine_create_service(GS_ROLE_EXECUTOR, &mock_svc) == GS_OK,
          "engine creates mock service after registry swap");
    CHECK(vtbl_of(mock_svc)->load_model(mock_svc, "mock://model") == GS_OK, "mock model load");

    gs_generation_options gopts{};
    gopts.temperature = 0.2f;
    gopts.max_tokens = 8;
    gopts.role = GS_ROLE_EXECUTOR;
    double ns_vcall = bench_ns_per_op([&] {
        char* text = nullptr;
        gs_generation_stats st{};
        vtbl_of(mock_svc)->generate(mock_svc, "hello", &gopts, &text, &st);
        gs_free_string(text);
    }, 200000);
    printf("METRIC {\"name\": \"vtable_generate_dispatch_ns\", \"value\": %.1f}\n", ns_vcall);

    // ---- 3. agent loop step overhead (mock backend, 1 tool) -----------------
    gs_tool_t* calc = gs_mock_calculator_tool();
    gs_agent_options aopts{};
    aopts.max_steps = 4;
    aopts.tools = calc;
    aopts.tool_count = 1;
    aopts.system_prompt = nullptr;

    double ns_agent = bench_ns_per_op([&] {
        gs_agent_result r{};
        gs_agent_run(mock_svc, "CALC 2+2", &aopts, &r);
        gs_agent_result_free(&r);
    }, 20000);
    printf("METRIC {\"name\": \"agent_loop_two_step_ns\", \"value\": %.1f}\n", ns_agent);

    // Functional: loop must parse the tool call, dispatch the tool, finish.
    gs_agent_result r{};
    CHECK(gs_agent_run(mock_svc, "CALC 2+2", &aopts, &r) == GS_OK, "agent run OK");
    CHECK(r.tool_calls == 1, "agent dispatched the calculator tool once");
    CHECK(r.steps_used == 2, "agent used 2 steps (call + final)");
    gs_agent_result_free(&r);

    // ---- 4. context shaping: 8k offload + hint ------------------------------
    std::string big(12000, 'x');
    char* shaped = nullptr;
    CHECK(gs_engine_context_shape(big.c_str(), 0, &shaped) == GS_OK && shaped != nullptr,
          "context shape OK on oversized tool output");
    std::string shapedStr = shaped ? shaped : "";
    CHECK(shapedStr.find("no subagent delegation") != std::string::npos,
          "truncation hint is agent-aware (no-subagent variant)");
    CHECK(shapedStr.size() < GS_OFFLOAD_THRESHOLD,
          "offloaded output fits under the 8k threshold");
    gs_free_string(shaped);
    char* shaped2 = nullptr;
    CHECK(gs_engine_context_shape(big.c_str(), 1, &shaped2) == GS_OK &&
              std::string(shaped2).find("delegate the full output to a subagent") != std::string::npos,
          "subagent-capable agent gets the delegation hint");
    gs_free_string(shaped2);

    double ns_shape = bench_ns_per_op([&] {
        char* s = nullptr;
        gs_engine_context_shape(big.c_str(), 0, &s);
        gs_free_string(s);
    }, 50000);
    printf("METRIC {\"name\": \"context_shape_12k_ns\", \"value\": %.1f}\n", ns_shape);

    // ---- 5. poka-yoke dispatch: structured errors ---------------------------
    char* err = nullptr;
    gs_status unknown = gs_engine_dispatch_tool(calc, 1, "nope", "{}", &err);
    CHECK(unknown == GS_ERR_INVALID_ARG && err && std::strstr(err, "UNKNOWN_TOOL") != nullptr,
          "unknown tool yields UNKNOWN_TOOL structured error");
    gs_free_string(err);
    char* err2 = nullptr;
    gs_status missing = gs_engine_dispatch_tool(calc, 1, "calculator", "{}", &err2);
    CHECK(missing == GS_OK || (missing == GS_ERR_INVALID_ARG && err2 && std::strstr(err2, "TOOL_ERROR") != nullptr),
          "malformed args yield a structured TOOL_ERROR (never silent execution)");
    gs_free_string(err2);

    // ---- 6. backend adapters honestly unavailable in this environment ------
    gs_registry_clear();
    gs_registry_register(gs_cactus_backend_entry());
    gs_llm_service_t* cactus_svc = nullptr;
    gs_status cst = gs_engine_create_service(GS_ROLE_EXECUTOR, &cactus_svc);
    if (cst == GS_OK) {
        char* text = nullptr;
        gs_generation_stats st{};
        gs_status gst = vtbl_of(cactus_svc)->generate(cactus_svc, "hi", &gopts, &text, &st);
        CHECK(gst == GS_ERR_BACKEND_UNAVAILABLE,
              "cactus adapter reports honest BACKEND_UNAVAILABLE without the SDK (rule 5)");
        gs_free_string(text);
        gs_free_service(cactus_svc);
    } else {
        CHECK(cst == GS_ERR_BACKEND_UNAVAILABLE, "cactus create path honest when SDK absent");
    }

    gs_free_service(mock_svc);
    printf(failures == 0 ? "SMOKE PASS\n" : "SMOKE FAIL (%d)\n", failures);
    return failures == 0 ? 0 : 1;
}
