/*
 * GS AI — C++ NATIVE ENGINE — plugin registry + engine entry points +
 * poka-yoke tool dispatch + context shaping.
 *
 * FFI RULE: every extern "C" body is wrapped so no exception escapes
 * (returns GS_ERR_INTERNAL instead). No RTTI is used anywhere.
 */
#include "gs_engine/c_api.h"

#include <algorithm>
#include <cstring>
#include <string>
#include <vector>

namespace {

struct RegistryState {
    std::vector<gs_plugin_entry_t> entries;
};
RegistryState& registry() {
    static RegistryState s;
    return s;
}

char* dup_string(const std::string& s) {
    // malloc (not new) so C and Swift/JNI consumers free with gs_free_string /
    // free() consistently.
    char* p = static_cast<char*>(std::malloc(s.size() + 1));
    if (!p) return nullptr;
    std::memcpy(p, s.c_str(), s.size() + 1);
    return p;
}

bool serves(const gs_plugin_entry_t& e, const char* primitive) {
    if (!e.serves) return false;
    std::string list(e.serves);
    size_t pos = 0;
    while (pos < list.size()) {
        size_t comma = list.find(',', pos);
        if (comma == std::string::npos) comma = list.size();
        std::string item = list.substr(pos, comma - pos);
        // trim
        while (!item.empty() && item.front() == ' ') item.erase(item.begin());
        while (!item.empty() && item.back() == ' ') item.pop_back();
        if (item == primitive) return true;
        pos = comma + 1;
    }
    return false;
}

// ---------------------------------------------------------------------------
// poka-yoke argument validation (ACI — LEVER 1). Deliberately the same shapes
// as the server scaffold (src/lib/bench/scaffold.ts) so the C++ engine and
// the TS shim measure ONE ACI design. gs_engine_dispatch_tool runs these
// checks BEFORE the tool's executor — malformed shapes never execute.
// ---------------------------------------------------------------------------

struct Validation {
    bool ok;
    std::string error; // structured: code= field= expected= got= hint=
};

Validation require_string_field(const std::string& json, const char* field, size_t min_len) {
    // Minimal, dependency-free extraction of "<field>": "<value>" (the ACI
    // shapes use flat string/number args only — nested schemas are rejected
    // by design so this stays correct).
    const std::string needle = "\"" + std::string(field) + "\"";
    size_t k = json.find(needle);
    if (k == std::string::npos) {
        return {false, "TOOL_ERROR code=ARG_MISSING field=" + std::string(field) +
                           " expected=string hint=the field is required; the tool did NOT run"};
    }
    size_t colon = json.find(':', k + needle.size());
    size_t q1 = (colon == std::string::npos) ? std::string::npos : json.find('"', colon);
    if (q1 == std::string::npos) {
        return {false, "TOOL_ERROR code=ARG_TYPE field=" + std::string(field) +
                           " expected=\"string\" got=non-string hint=quote the value"};
    }
    size_t q2 = json.find('"', q1 + 1);
    if (q2 == std::string::npos) {
        return {false, "TOOL_ERROR code=ARG_TYPE field=" + std::string(field) +
                           " expected=closed string got=unterminated hint=close the quote"};
    }
    std::string value = json.substr(q1 + 1, q2 - q1 - 1);
    if (value.size() < min_len) {
        return {false, "TOOL_ERROR code=ARG_LENGTH field=" + std::string(field) + " expected=length>=" +
                           std::to_string(min_len) + " got=" + std::to_string(value.size()) +
                           " hint=the tool did NOT run"};
    }
    return {true, value};
}

Validation require_int_range(const std::string& json, const char* field, int lo, int hi, int def) {
    const std::string needle = "\"" + std::string(field) + "\"";
    size_t k = json.find(needle);
    if (k == std::string::npos) return {true, std::to_string(def)}; // optional with default
    size_t colon = json.find(':', k + needle.size());
    if (colon == std::string::npos) {
        return {false, "TOOL_ERROR code=ARG_TYPE field=" + std::string(field) +
                           " expected=integer hint=missing value"};
    }
    size_t d1 = json.find_first_of("-0123456789", colon);
    if (d1 == std::string::npos) {
        return {false, "TOOL_ERROR code=ARG_TYPE field=" + std::string(field) +
                           " expected=integer got=non-integer hint=must be an integer between " +
                           std::to_string(lo) + " and " + std::to_string(hi)};
    }
    size_t d2 = json.find_first_not_of("-0123456789", d1);
    std::string num = json.substr(d1, (d2 == std::string::npos ? json.size() : d2) - d1);
    try {
        int v = std::stoi(num);
        if (v < lo || v > hi) {
            return {false, "TOOL_ERROR code=ARG_RANGE field=" + std::string(field) + " expected=integer " +
                               std::to_string(lo) + "-" + std::to_string(hi) + " got=" + num +
                               " hint=the tool did NOT run"};
        }
        return {true, num};
    } catch (...) {
        return {false, "TOOL_ERROR code=ARG_TYPE field=" + std::string(field) +
                           " expected=integer got=" + num + " hint=unparseable"};
    }
}

Validation require_enum(const std::string& json, const char* field,
                        const std::vector<std::string>& values, const std::string& def) {
    const std::string needle = "\"" + std::string(field) + "\"";
    size_t k = json.find(needle);
    if (k == std::string::npos) return {true, def};
    size_t colon = json.find(':', k + needle.size());
    size_t q1 = (colon == std::string::npos) ? std::string::npos : json.find('"', colon);
    if (q1 == std::string::npos) {
        std::string expected;
        for (size_t i = 0; i < values.size(); i++) expected += (i ? "|" : "") + values[i];
        return {false, "TOOL_ERROR code=ARG_ENUM field=" + std::string(field) + " expected=" + expected +
                           " got=non-string hint=use one of the listed enum values"};
    }
    size_t q2 = json.find('"', q1 + 1);
    std::string value = (q2 == std::string::npos) ? "" : json.substr(q1 + 1, q2 - q1 - 1);
    for (const auto& v : values) {
        if (v == value) return {true, value};
    }
    std::string expected;
    for (size_t i = 0; i < values.size(); i++) expected += (i ? "|" : "") + values[i];
    return {false, "TOOL_ERROR code=ARG_ENUM field=" + std::string(field) + " expected=" + expected +
                       " got=\"" + value + "\" hint=the tool did NOT run"};
}

bool charset_ok(const std::string& expr, const char* allowed) {
    for (char c : expr) {
        if (!std::strchr(allowed, c)) return false;
    }
    return true;
}

/* Pre-execution ACI validation — one branch per registered tool shape. */
Validation validate_tool_args(const char* tool_name, const char* args_json) {
    std::string args = args_json ? args_json : "";
    if (std::strcmp(tool_name, "calculator") == 0) {
        Validation expr = require_string_field(args, "expression", 1);
        if (!expr.ok) return expr;
        static const char* CALC_CHARS = "0123456789+-*/(). %^\t";
        if (!charset_ok(expr.error, CALC_CHARS)) {
            return {false, "TOOL_ERROR code=ARG_CHARS field=expression expected=digits and + - * / ( ) % ^ only" +
                               std::string(" got=forbidden characters hint=the tool did NOT run")};
        }
        return require_enum(args, "precision", {"integer", "float"}, "float");
    }
    if (std::strcmp(tool_name, "web_search") == 0) {
        Validation q = require_string_field(args, "query", 3);
        if (!q.ok) return q;
        Validation mr = require_int_range(args, "max_results", 1, 8, 4);
        if (!mr.ok) return mr;
        return require_enum(args, "recency", {"any", "day", "week", "month", "year"}, "any");
    }
    return {true, ""};
}

std::string context_shape_impl(const std::string& tool_output, bool has_subagents, bool* offloaded) {
    if (tool_output.size() <= GS_OFFLOAD_THRESHOLD) {
        *offloaded = false;
        return tool_output;
    }
    *offloaded = true;
    // Agent-aware truncation hint (arXiv 2603.05344): the recovery strategy
    // suggested must exist in THIS agent's tool set.
    const char* hint = has_subagents
        ? "(recovery hint: delegate the full output to a subagent and continue with its summary)"
        : "(recovery hint: this agent has no subagent delegation — use incremental search with narrower queries instead of requesting everything at once)";
    std::string out = "[tool] output was " + std::to_string(tool_output.size()) +
                      " chars — too large for context. Offloaded to scratch. First " +
                      std::to_string(GS_OFFLOAD_PREVIEW) + " chars:\n" +
                      tool_output.substr(0, GS_OFFLOAD_PREVIEW) + "\n" + hint;
    return out;
}

} // namespace

/* ---- registry ------------------------------------------------------------ */

gs_status gs_registry_register(const gs_plugin_entry_t* entry) {
    if (!entry || !entry->backend_id || !entry->create) return GS_ERR_INVALID_ARG;
    try {
        auto& v = registry().entries;
        auto it = std::find_if(v.begin(), v.end(), [&](const gs_plugin_entry_t& e) {
            return std::strcmp(e.backend_id, entry->backend_id) == 0;
        });
        if (it != v.end()) *it = *entry;
        else v.push_back(*entry);
        return GS_OK;
    } catch (...) { return GS_ERR_INTERNAL; }
}

int32_t gs_registry_count(void) {
    try { return static_cast<int32_t>(registry().entries.size()); }
    catch (...) { return 0; }
}

gs_status gs_registry_select(const char* primitive, gs_plugin_entry_t* out) {
    if (!primitive || !out) return GS_ERR_INVALID_ARG;
    try {
        const auto& v = registry().entries;
        const gs_plugin_entry_t* best = nullptr;
        for (const auto& e : v) {
            if (!serves(e, primitive)) continue;
            if (!best || e.priority > best->priority) best = &e;
        }
        if (!best) return GS_ERR_BACKEND_UNAVAILABLE;
        *out = *best;
        return GS_OK;
    } catch (...) { return GS_ERR_INTERNAL; }
}

void gs_registry_clear(void) {
    try { registry().entries.clear(); } catch (...) {}
}

/* ---- engine entry points -------------------------------------------------- */

gs_status gs_engine_create_service(gs_model_role role, gs_llm_service_t** out) {
    if (!out) return GS_ERR_INVALID_ARG;
    try {
        // Routing primitive: planner/executor split (LEVER 4) — the registry
        // may serve different backends per role (e.g. cactus executor +
        // llamacpp planner). v1 selects "generate" for both roles; per-role
        // primitives ("generate_planner") plug into the same registry without
        // an ABI change.
        (void)role;
        gs_plugin_entry_t sel{};
        gs_status st = gs_registry_select("generate", &sel);
        if (st != GS_OK) return st;
        gs_llm_service_t* svc = sel.create();
        if (!svc) return GS_ERR_BACKEND_UNAVAILABLE;
        *out = svc;
        return GS_OK;
    } catch (...) { return GS_ERR_INTERNAL; }
}

const char* gs_engine_last_backend(gs_llm_service_t*) {
    // Backends report their own id via the vtable; the agent loop stores the
    // selected id on the service. This stub keeps ABI stability for consumers
    // compiled against v1; see gs_llm_service_vtable_t.backend_id.
    return nullptr;
}

void gs_free_string(char* p) { std::free(p); }

void gs_free_service(gs_llm_service_t* service) {
    if (!service) return;
    // Documented contract: the handle's first member IS the vtable (by value).
    auto* vtbl = reinterpret_cast<gs_llm_service_vtable_t*>(service);
    if (vtbl && vtbl->destroy) vtbl->destroy(service);
}

/* ---- tool dispatch --------------------------------------------------------- */

gs_status gs_engine_dispatch_tool(const gs_tool_t* tools, int32_t tool_count,
                                  const char* tool_name, const char* args_json,
                                  char** out_summary) {
    if (!tools || tool_count <= 0 || !tool_name || !args_json || !out_summary) {
        return GS_ERR_INVALID_ARG;
    }
    try {
        const gs_tool_t* tool = nullptr;
        for (int32_t i = 0; i < tool_count; i++) {
            if (tools[i].name && std::strcmp(tools[i].name, tool_name) == 0) { tool = &tools[i]; break; }
        }
        if (!tool) {
            std::string expected;
            for (int32_t i = 0; i < tool_count; i++) expected += (i ? "|" : "") + std::string(tools[i].name ? tools[i].name : "?");
            *out_summary = dup_string("TOOL_ERROR code=UNKNOWN_TOOL expected=" + expected + " got=\"" + tool_name +
                                      "\" hint=use exactly one of the listed tools");
            return GS_ERR_INVALID_ARG;
        }
        // Poka-yoke gate: validate the argument SHAPE before the executor runs
        // (structured errors say WHAT was wrong — expected vs got — and that
        // the tool did NOT run).
        Validation v = validate_tool_args(tool_name, args_json);
        if (!v.ok) {
            *out_summary = dup_string(v.error);
            return GS_ERR_INVALID_ARG;
        }
        if (!tool->execute) return GS_ERR_INTERNAL;
        char* raw = nullptr;
        gs_status st = tool->execute(tool->user ? const_cast<gs_tool_t*>(tool) : nullptr, args_json, &raw);
        if (st != GS_OK) return st;
        bool off = false;
        std::string shaped = context_shape_impl(raw ? raw : "", 0, &off);
        std::free(raw);
        *out_summary = dup_string(shaped);
        return *out_summary ? GS_OK : GS_ERR_OUT_OF_MEMORY;
    } catch (...) { return GS_ERR_INTERNAL; }
}

/* ---- context shaping -------------------------------------------------------- */

gs_status gs_engine_context_shape(const char* tool_output, int has_subagents, char** out_shaped) {
    if (!out_shaped) return GS_ERR_INVALID_ARG;
    try {
        bool off = false;
        std::string shaped = context_shape_impl(tool_output ? tool_output : "", has_subagents != 0, &off);
        *out_shaped = dup_string(shaped);
        return *out_shaped ? GS_OK : GS_ERR_OUT_OF_MEMORY;
    } catch (...) { return GS_ERR_INTERNAL; }
}
