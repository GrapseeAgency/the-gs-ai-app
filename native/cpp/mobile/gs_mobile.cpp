// gs_mobile.cpp — portable mobile surface.
//
// Compiles with a C++17 compiler and libm. No ONNX Runtime, no llama.cpp, no
// Tesseract unless GS_MOBILE_HAVE_LLAMA is defined, which only the desktop
// build does.
//
// The design point: every backend-dependent call reports GS_ERR_UNAVAILABLE with
// a reason. It does not return empty text, does not return a zero vector, and
// does not succeed quietly. A mobile app that asks for OCR on a build without an
// OCR engine gets told so, and can fall through to the provider pool. A build
// that returned "" would be indistinguishable from a photo of a blank page.

#include "gs_mobile.h"

#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#ifdef GS_MOBILE_HAVE_LLAMA
#include "llama_wrapper.h"
#endif

// UNCONDITIONAL, and it was not, which cost run 36852700499:
//
//   gs_mobile.cpp:270:17: error: 'gs_sd_ctx_t' was not declared in this scope
//   gs_mobile.cpp:279:12: error: 'gs_sd_render_svg' was not declared
//
// The include had been placed inside the #ifdef GS_MOBILE_HAVE_LLAMA block,
// because that is where the line above it lives and a text edit followed the
// neighbour rather than the dependency.
//
// Nothing here is conditional on llama. The image path is its own backend with
// its own gate -- GS_SD_HAVE_SDCPP, which is decided in build.rs -- and the
// procedural renderer needs no model, no GPU and no diffusion at all.
//
// WHICH IS EXACTLY WHY THE CONDITIONAL WAS WRONG IN THE OTHER DIRECTION TOO: a
// PORTABLE build is the one that most needs these symbols, because it is the
// build where the sd.cpp library is absent and gs_sd_generate has to report that
// honestly rather than not existing.
#include "gs_sd_wrapper.h"

namespace {
// Writes through the shared channel so gs_last_error() actually reports it.
void set_err(const std::string& m) { gs_set_error(m.c_str()); }

const char* kNoBackend =
    "no generation backend is compiled into this build; "
    "on mobile the default gs-ffi build is portable-only and chat requires a "
    "llama.cpp-enabled build";
}  // namespace

struct gs_mobile_ctx {
    int32_t n_ctx = 2048;
    int32_t n_threads = 4;
    // 0 = "whatever the library already did", which for all three is the value
    // that was hardcoded before they were reachable. See llama_config_t.
    int32_t n_batch = 0;
    int32_t n_ubatch = 0;
    int32_t flash_attn = 0;
    // -1, NOT 0: negative leaves llama_context_default_params() alone, and 0 here
    // would ask llama.cpp for zero threads. The other three use 0-means-default
    // because a zero size is not a value anyone types by accident; a zero thread
    // count is.
    int32_t n_threads_batch = -1;
    std::string model_path;
    bool has_model = false;
#ifdef GS_MOBILE_HAVE_LLAMA
    gs_llama_context_t* llama = nullptr;
#endif
};

extern "C" {

gs_mobile_ctx_t* gs_mobile_create_tuned(const char* model_path,
                                        int32_t n_ctx, int32_t n_threads,
                                        int32_t n_threads_batch,
                                        int32_t n_batch, int32_t n_ubatch,
                                        int32_t flash_attn) {
    try {
        auto* ctx = new gs_mobile_ctx();
        if (n_ctx > 0) ctx->n_ctx = n_ctx;
        if (n_threads > 0) ctx->n_threads = n_threads;
        ctx->n_batch = n_batch > 0 ? n_batch : 0;
        ctx->n_ubatch = n_ubatch > 0 ? n_ubatch : 0;
        // flash_attn is an ENUM, so it is passed through rather than reduced to a
        // flag. -1 (AUTO) is the value this build used before, by never assigning
        // the field at all, and reducing it to 0/1 would throw that away and pin
        // the kernel to DISABLED for every caller that had not opted in.
        ctx->flash_attn = flash_attn < 0 ? -1 : (flash_attn > 0 ? 1 : 0);
        ctx->n_threads_batch = n_threads_batch < 0 ? -1 : n_threads_batch;
        if (model_path && *model_path) {
            ctx->model_path = model_path;
            // Presence is checked, not opened. A model that ships in app storage
            // is verified by the host, and failing here would make "not
            // downloaded yet" look like a library fault.
            ctx->has_model = true;
        }
#ifdef GS_MOBILE_HAVE_LLAMA
        if (ctx->has_model) {
            llama_config_t cfg{};
            cfg.model_path = ctx->model_path.c_str();
            cfg.n_ctx = ctx->n_ctx;
            cfg.n_threads = ctx->n_threads;
            cfg.n_gpu_layers = 0;  // mobile: no GPU offload path here
            cfg.use_mmap = 1;
            cfg.cache_type_k = 1;  // F16
            cfg.cache_type_v = 1;
            cfg.n_batch   = ctx->n_batch;
            cfg.n_ubatch  = ctx->n_ubatch;
            cfg.flash_attn = ctx->flash_attn;
            cfg.n_threads_batch = ctx->n_threads_batch;
            ctx->llama = gs_llama_create(&cfg);
            if (!ctx->llama) {
                set_err(std::string("llama load failed: ") + gs_last_error());
                delete ctx;
                return nullptr;
            }
        }
#endif
        return ctx;
    } catch (const std::exception& e) {
        set_err(std::string("gs_mobile_create: ") + e.what());
        return nullptr;
    }
}

/* The three-knob-free form, delegating so the defaults live in ONE place. */
gs_mobile_ctx_t* gs_mobile_create(const char* model_path, int32_t n_ctx, int32_t n_threads) {
    return gs_mobile_create_tuned(model_path, n_ctx, n_threads, -1, 0, 0, -1);
}

void gs_mobile_free(gs_mobile_ctx_t* ctx) {
    if (!ctx) return;
#ifdef GS_MOBILE_HAVE_LLAMA
    if (ctx->llama) gs_llama_free(ctx->llama);
#endif
    delete ctx;
}

int32_t gs_mobile_backend_available(gs_mobile_ctx_t* ctx) {
    (void)ctx;
#ifdef GS_MOBILE_HAVE_LLAMA
    return ctx && ctx->llama && gs_llama_available(ctx->llama) ? 1 : 0;
#else
    return 0;
#endif
}

const char* gs_mobile_backend_name(gs_mobile_ctx_t* ctx) {
    (void)ctx;
#ifdef GS_MOBILE_HAVE_LLAMA
    return ctx && ctx->llama ? "llama.cpp" : "none";
#else
    return "none";
#endif
}

char* gs_mobile_chat(gs_mobile_ctx_t* ctx, const char* prompt, int32_t max_tokens, float temperature) {
    if (!ctx || !prompt) {
        set_err("ctx or prompt is null");
        return nullptr;
    }
    if (max_tokens <= 0) max_tokens = 256;
#ifdef GS_MOBILE_HAVE_LLAMA
    if (ctx->llama) {
        // TEMPLATED, NOT RAW. The raw prompt produced a prompt FRAGMENT rather
        // than an answer, from a passing test on the emulator:
        //
        //   chat("hello") -> ", i have a question about the following code:"
        //
        // which is a base model completing a document, because nothing told it a
        // user was speaking. The template comes from the GGUF itself, so this is
        // not a Qwen-specific assumption.
        //
        // The system prompt is terse on purpose. This is a phone assistant and
        // the tokens spent on a persona are tokens not spent answering.
        static const char* kSystem = "You are a helpful assistant on a phone. "
                                     "Answer briefly and directly.";
        llama_result_t r = gs_llama_chat(ctx->llama, kSystem, prompt,
                                         max_tokens, temperature);
        if (r.status != GS_OK) {
            set_err(std::string("generate failed: ") + gs_last_error());
            return nullptr;
        }
        return r.text;  // owned by caller; gs_llama_free_result_text releases
    }
#endif
    set_err(kNoBackend);
    return nullptr;
}

char* gs_mobile_ocr(gs_mobile_ctx_t* ctx, const char* image_path) {
    (void)ctx;
    if (!image_path) {
        set_err("image_path is null");
        return nullptr;
    }
    // No OCR engine in the portable build. Returning "" here would be read as
    // "the image contains no text", which is a different and wrong claim.
    set_err("no OCR engine is compiled into this mobile build; "
            "run OCR through the provider path or use a gs-ocr-enabled build");
    return nullptr;
}

int32_t gs_mobile_embed_image(gs_mobile_ctx_t* ctx, const uint8_t* rgb, int32_t w, int32_t h,
                              float* out, int32_t cap) {
    (void)ctx;
    (void)rgb;
    (void)w;
    (void)h;
    (void)out;
    (void)cap;
    set_err("no image embedder is compiled into this mobile build");
    return GS_ERR_UNAVAILABLE;
}

int32_t gs_mobile_embed_text(gs_mobile_ctx_t* ctx, const char* text, float* out, int32_t cap) {
    (void)ctx; (void)text; (void)out; (void)cap;
    set_err("no text embedder is compiled into this mobile build");
    return GS_ERR_UNAVAILABLE;
}

int32_t gs_mobile_embed_dim(gs_mobile_ctx_t* ctx) {
    (void)ctx;
    return GS_ERR_UNAVAILABLE;
}

gs_device_class_t gs_mobile_device_class(void) { return gs_device_class(); }

int32_t gs_mobile_should_use_local(uint64_t max_params) { return gs_device_allows(max_params); }

const char* gs_mobile_build_info(void) {
    // Assembled in pieces because the #ifdef arms are separate string literals.
    //
    // STATIC, and it has to be: this returned info.c_str() from a function-local
    // std::string, which dangles the moment the function returns. The symptom was
    // build_info() == "" on every call, with no crash, because the freed
    // buffer happened to read as an empty string.
    static std::string info = std::string("gs-ffi ") + GS_MOBILE_VERSION;
#ifdef GS_MOBILE_HAVE_LLAMA
    info += " +llama";
#else
    info += " portable";
#endif
#ifdef GS_CLIP_HAVE_ONNXRUNTIME
    info += " +clip";
#endif
#ifdef GS_OCR_HAVE_TESSERACT
    info += " +ocr";
#endif
    return info.c_str();
}

/* ---------------------------------------------------------------------------
 * Image generation, IN-PROCESS.
 *
 * These exist because the C ABI was reachable only from C++ inside this library.
 * Everything else here goes through gs_ffi_* wrappers in Rust, and routing the
 * mobile surface through gs_mobile.cpp keeps every backend-dependent call behind
 * one portable file, which is what the other four backends already do.
 *
 * The ordering question this avoids: Rust calling gs_sd_* directly would need
 * libgs_sd.a positioned against libgs_ffi.a. It does not need to be -- a static
 * archive's consumer there is the Rust object that is the link entry point, not
 * another archive -- but keeping the mobile surface in C++ means nobody has to
 * re-derive that to change it.
 *
 * WHAT THIS DOES NOT DO: spawn anything. There is no subprocess route left on any
 * path, and no_subprocess_remains_in_this_file in sd_bridge.rs reads its own
 * source to keep it that way.
 * ------------------------------------------------------------------------- */

/* An image-generation handle. Opaque; the caller only passes it back. */
typedef struct gs_mobile_img {
    void* ctx;        /* gs_sd_ctx_t* */
} gs_mobile_img_t;

gs_mobile_img_t* gs_mobile_sd_create(const char* model_path) {
    gs_sd_ctx_t* c = gs_sd_create(model_path);
    if (!c) return nullptr;   /* the reason is in gs_last_error() */
    gs_mobile_img_t* h = (gs_mobile_img_t*)calloc(1, sizeof(gs_mobile_img_t));
    if (!h) { gs_sd_free(c); return nullptr; }
    h->ctx = c;
    return h;
}

int32_t gs_mobile_sd_set_options(gs_mobile_img_t* img, int32_t threads, float cfg_scale) {
    if (!img) return GS_ERR_INVALID_ARG;
    return gs_sd_set_options((gs_sd_ctx_t*)img->ctx, threads, cfg_scale);
}

/* 1 when diffusion can run, 0 when it cannot. The two reasons -- no library linked,
 * and no weights -- stay DISTINGUISHABLE, because gs_sd_backend_name() says which,
 * and they are opposite problems with opposite fixes. */
int32_t gs_mobile_sd_available(gs_mobile_img_t* img) {
    if (!img) return 0;
    return gs_sd_available((gs_sd_ctx_t*)img->ctx);
}

const char* gs_mobile_sd_backend_name(gs_mobile_img_t* img) {
    if (!img) return "none";
    return gs_sd_backend_name((gs_sd_ctx_t*)img->ctx);
}

/* Writes a PNG. Never writes a placeholder on failure -- see the C ABI header. */
int32_t gs_mobile_sd_generate(gs_mobile_img_t* img,
                              const char* prompt,
                              const char* negative_prompt,
                              int32_t width, int32_t height, int32_t steps,
                              const char* output_path) {
    if (!img) return GS_ERR_INVALID_ARG;
    return gs_sd_generate((gs_sd_ctx_t*)img->ctx, prompt, negative_prompt,
                          width, height, steps, output_path);
}

void gs_mobile_sd_free(gs_mobile_img_t* img) {
    if (!img) return;
    gs_sd_free((gs_sd_ctx_t*)img->ctx);
    free(img);
}

/* The procedural renderer. Needs no weights, no GPU and no diffusion, so it works
 * in EVERY build -- including the ones where sd.cpp is not linked at all. This is
 * what lets "the app can generate an image" be a claim that does not depend on a
 * 2.3 GB checkpoint having been downloaded. */
int32_t gs_mobile_render_svg(const char* spec_json, const char* output_path) {
    return gs_sd_render_svg(spec_json, output_path);
}

}  // extern "C"
