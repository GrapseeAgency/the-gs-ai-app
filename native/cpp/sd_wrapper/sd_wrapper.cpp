// sd_wrapper.cpp — GS AI C ABI over stable-diffusion.cpp, plus the
// model-free procedural path.
//
// The procedural path is ALWAYS available: no weights, no GPU, no diffusion.
// That matters because the dossier asks for diagrams/charts/UI mockups, and a
// 2.3 GB diffusion model is the wrong tool for those.
#include "sd_wrapper.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <new>
#include <string>

namespace {
thread_local std::string s_err;
void set_err(const std::string& m) { s_err = m; }
inline std::string borrow(const char* s) { return s ? std::string(s) : std::string(); }
} // namespace

struct sd_context {
    sd_config_t cfg{};
    bool available = false;
    std::string backend_name = "none";
};

extern "C" {

int32_t sd_validate_config(const sd_config_t* config) {
    if (!config) { set_err("config is null"); return GS_ERR_INVALID_ARG; }
    if (!config->model_path || !*config->model_path) {
        set_err("model_path is required");
        return GS_ERR_INVALID_ARG;
    }
    if (config->width <= 0 || config->height <= 0) {
        set_err("width/height must be > 0");
        return GS_ERR_INVALID_ARG;
    }
    if (config->steps <= 0) { set_err("steps must be > 0"); return GS_ERR_INVALID_ARG; }
    return GS_OK;
}

sd_context_t* sd_create(const sd_config_t* config) {
    if (sd_validate_config(config) != GS_OK) return nullptr;
    sd_context_t* c = new (std::nothrow) sd_context();
    if (!c) { set_err("out of memory"); return nullptr; }

#if defined(GS_SD_HAVE_SDCPP)
    // TODO(v2): wire sd.cpp. Until a backend .so is linked the honest state is
    // "unavailable" — returning a placeholder image here would corrupt any
    // downstream quality measurement.
    c->available    = false;
    c->backend_name = "sd.cpp:not-compiled";
    set_err("stable-diffusion.cpp not linked: rebuild with GS_SD_HAVE_SDCPP");
#else
    c->available    = false;
    c->backend_name = "sd.cpp:not-compiled";
    set_err("stable-diffusion.cpp not linked: procedural path remains available");
#endif
    return c;
}

int32_t sd_generate(sd_context_t* ctx,
                    const char* prompt,
                    const char* negative_prompt,
                    const char* output_path) {
    (void)prompt; (void)negative_prompt;
    if (!ctx)      { set_err("context is null"); return GS_ERR_INVALID_ARG; }
    if (!output_path || !*output_path) {
        set_err("output_path is required");
        return GS_ERR_INVALID_ARG;
    }
    if (!ctx->available) {
        set_err(std::string("sd backend unavailable: ") + ctx->backend_name);
        return GS_ERR_UNAVAILABLE;
    }
    return GS_ERR_UNSUPPORTED;
}

// ---------------------------------------------------------------------------
// Procedural path: a tiny SVG emitter. spec_json is a flat JSON object of
// {"key": "value"} pairs; recognised keys drive the output. Unknown keys are
// ignored rather than rejected, so an LLM emitting extra fields still renders.
// ---------------------------------------------------------------------------
extern "C" int32_t sd_render_svg(const char* spec_json, const char* output_path) {
    if (!spec_json || !output_path || !*output_path) {
        set_err("spec_json and output_path are required");
        return GS_ERR_INVALID_ARG;
    }
    const std::string spec(spec_json);
    FILE* f = std::fopen(output_path, "wb");
    if (!f) { set_err(std::string("cannot open ") + output_path); return GS_ERR_IO; }

    auto field = [&spec](const char* key, const char* dflt) -> std::string {
        std::string pat = std::string("\"") + key + "\"";
        size_t p = spec.find(pat);
        if (p == std::string::npos) return dflt;
        p = spec.find(':', p);
        if (p == std::string::npos) return dflt;
        ++p;
        while (p < spec.size() && (spec[p] == ' ' || spec[p] == '\t')) ++p;
        if (p >= spec.size() || (spec[p] != '"' && spec[p] != '\'')) return dflt;
        const char q = spec[p];
        size_t e = spec.find(q, p + 1);
        if (e == std::string::npos) return dflt;
        return spec.substr(p + 1, e - p - 1);
    };

    const std::string title = field("title", "diagram");
    const std::string w     = field("width",  "800");
    const std::string h     = field("height", "450");
    const std::string bg    = field("background", "#0b0f14");
    const std::string fg    = field("foreground", "#e6edf3");

    std::fprintf(f,
        "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%s\" height=\"%s\" "
        "viewBox=\"0 0 %s %s\" role=\"img\" aria-label=\"%s\">\n",
        w.c_str(), h.c_str(), w.c_str(), h.c_str(), title.c_str());
    std::fprintf(f, "  <rect width=\"100%%\" height=\"100%%\" fill=\"%s\"/>\n", bg.c_str());
    std::fprintf(f, "  <style>text{font-family:ui-sans-serif,system-ui,sans-serif;"
                    "fill:%s}</style>\n", fg.c_str());
    std::fprintf(f, "  <text x=\"40\" y=\"60\" font-size=\"28\" font-weight=\"600\">%s</text>\n",
                 title.c_str());

    // Render each remaining "label": "value" pair as a row.
    size_t p = 0;
    int row = 0;
    while ((p = spec.find(':', p)) != std::string::npos) {
        ++p;
        while (p < spec.size() && spec[p] == ' ') ++p;
        if (p >= spec.size() || spec[p] != '"') { ++p; continue; }
        size_t e = spec.find('"', p + 1);
        if (e == std::string::npos) break;
        const std::string k = spec.substr(p + 1, e - p - 1);
        p = e + 1;
        while (p < spec.size() && spec[p] != ':') ++p;
        if (p >= spec.size()) break;
        ++p;
        while (p < spec.size() && spec[p] == ' ') ++p;
        if (p >= spec.size() || (spec[p] != '"' && spec[p] != '\'')) continue;
        const char q = spec[p];
        size_t e2 = spec.find(q, p + 1);
        if (e2 == std::string::npos) break;
        const std::string v = spec.substr(p + 1, e2 - p - 1);
        if (k != "title" && k != "width" && k != "height" &&
            k != "background" && k != "foreground") {
            std::fprintf(f,
                "  <g><rect x=\"40\" y=\"%d\" width=\"%d\" height=\"34\" rx=\"6\" "
                "fill=\"#161b22\" stroke=\"#30363d\"/>"
                "<text x=\"56\" y=\"%d\" font-size=\"16\">%s: %s</text></g>\n",
                100 + row * 44, 40, 122 + row * 44, k.c_str(), v.c_str());
            ++row;
        }
        p = e2;
    }
    std::fprintf(f, "</svg>\n");
    std::fclose(f);
    return GS_OK;
}

int32_t sd_available(sd_context_t* ctx) { return ctx && ctx->available ? 1 : 0; }

const char* sd_backend_name(sd_context_t* ctx) {
    return ctx ? ctx->backend_name.c_str() : "none";
}

void sd_free(sd_context_t* ctx) { delete ctx; }

} // extern "C"
