// gs_sd_wrapper.cpp — the GS AI C ABI over stable-diffusion.cpp, plus the
// model-free procedural path.
//
// IN-PROCESS, NOT A SUBPROCESS. The previous route to an image was to spawn
// `sd-cli` from a path on a developer machine, which a phone has neither of. The
// library is called directly here, through the pinned upstream header.
//
// Every symbol is gs_sd_* because upstream's own public API is sd_* -- see
// sd_wrapper.h for why a collision is worse than a compile error.
//
// TWO PATHS, AND THEY ARE NOT SUBSTITUTES:
//
//   gs_sd_generate   real diffusion. Needs the sd.cpp library AND a 2.3 GB
//                    checkpoint. Absent either, it reports why and returns; it
//                    never writes a placeholder image.
//   gs_sd_render_svg procedural, no model, no GPU, no diffusion. Always
//                    available, and the right tool for diagrams and charts.
#include "gs_sd_wrapper.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <new>
#include <string>

// The pinned upstream API. SD_API is a plain visibility attribute on this
// toolchain, and the header needs only <stdbool.h> <stddef.h> <stdint.h>
// <string.h> — all of which the NDK already provides, so nothing else has to
// travel in the package.
#if defined(GS_SD_HAVE_SDCPP)
#include "stable-diffusion.h"
#endif

namespace {
// THE ERROR GOES THROUGH THE ONE SHARED CHANNEL, and this used not to.
//
// gs_abi.h records the reason for that channel existing:
//
//   "Added because the three wrappers each kept a PRIVATE thread_local:
//    gs_abi's g_last_error, llama_wrapper's t_err, and the mobile one.
//    gs_last_error() read only the first, so 43 writes in the llama wrapper and
//    every mobile error were unreachable -- a caller got "" for a failure that
//    had a perfectly good explanation sitting in a variable nothing read."
//
// THIS FILE WAS STILL THE FOURTH such variable. Every sd_wrapper failure set a
// private s_err that nothing read, so a caller of gs_sd_create got "" and a
// caller of the old sd_create could not have told "no such model" from
// "the library is not linked" -- which are opposite diagnoses with opposite
// fixes. One channel, one accessor, and the message is reachable.
void set_err(const std::string& m) { gs_set_error(m.c_str()); }
} // namespace

struct gs_sd_ctx {
#if defined(GS_SD_HAVE_SDCPP)
    sd_ctx_t* inner = nullptr;
#endif
    bool available = false;
    std::string backend_name = "sd.cpp:not-compiled";
    std::string model_path;
    int32_t threads = 0;
    float cfg_scale = -1.0f;
};

// ---------------------------------------------------------------------------
// A PNG ENCODER, because the library does not export one.
//
// stable-diffusion.cpp returns raw pixels in an sd_image_t and its public header
// has no image writer — `save_imatrix` is a calibration dump, not a PNG. So the
// options are link a PNG library or write one. This writes one: a stored-deflate
// PNG, which is a valid zlib stream that no encoder needs to compress, plus
// correct CRCs and ADLER-32. About a hundred lines, no dependency, and it is what
// lets the device test decode the result and prove it is not blank — which a
// linked encoder would have let us do too, at the cost of one more library in
// every phone APK.
// ---------------------------------------------------------------------------
namespace {

uint32_t crc32_of(const uint8_t* data, size_t n, uint32_t crc = 0) {
    static uint32_t table[256];
    static bool built = false;
    if (!built) {
        for (uint32_t i = 0; i < 256; ++i) {
            uint32_t c = i;
            for (int k = 0; k < 8; ++k) c = (c & 1) ? (0xEDB88320u ^ (c >> 1)) : (c >> 1);
            table[i] = c;
        }
        built = true;
    }
    crc = crc ^ 0xFFFFFFFFu;
    for (size_t i = 0; i < n; ++i) crc = table[(crc ^ data[i]) & 0xFF] ^ (crc >> 8);
    return crc ^ 0xFFFFFFFFu;
}

void put_u32(std::string& out, uint32_t v) {
    out.push_back(static_cast<char>((v >> 24) & 0xFF));
    out.push_back(static_cast<char>((v >> 16) & 0xFF));
    out.push_back(static_cast<char>((v >> 8) & 0xFF));
    out.push_back(static_cast<char>(v & 0xFF));
}

void png_chunk(std::string& out, const char tag[4], const std::string& body) {
    put_u32(out, static_cast<uint32_t>(body.size()));
    const size_t crc_start = out.size();
    out.append(tag, 4);
    out.append(body);
    const uint32_t crc = crc32_of(
        reinterpret_cast<const uint8_t*>(out.data()) + crc_start, 4 + body.size());
    put_u32(out, crc);
}

// zlib stream using STORED (uncompressed) deflate blocks. Block header is
// BFINAL in bit 0 and BTYPE=00 in bits 1-2, so one byte per 65535-byte block,
// then LEN and its one's complement, then the raw bytes.
std::string zlib_store(const std::string& raw) {
    std::string out;
    out.push_back(static_cast<char>(0x78));  // CMF: deflate, 32K window
    out.push_back(static_cast<char>(0x01));  // FLG: no dict, check bits match
    size_t off = 0;
    if (raw.empty()) {
        out.push_back(static_cast<char>(0x01));
        out.append(2, '\0');
        out.append(2, '\xFF');
    }
    while (off < raw.size()) {
        const size_t chunk = (raw.size() - off > 65535u) ? 65535u : (raw.size() - off);
        const bool last = (off + chunk) >= raw.size();
        out.push_back(static_cast<char>(last ? 0x01 : 0x00));
        out.push_back(static_cast<char>(chunk & 0xFF));
        out.push_back(static_cast<char>((chunk >> 8) & 0xFF));
        const uint16_t n = static_cast<uint16_t>(~chunk);
        out.push_back(static_cast<char>(n & 0xFF));
        out.push_back(static_cast<char>((n >> 8) & 0xFF));
        out.append(raw, off, chunk);
        off += chunk;
    }
    // ADLER-32 over the uncompressed data.
    uint32_t a = 1, b = 0;
    for (size_t i = 0; i < raw.size(); ++i) {
        a = (a + static_cast<uint8_t>(raw[i])) % 65521u;
        b = (b + a) % 65521u;
    }
    put_u32(out, (b << 16) | a);
    return out;
}

// Write `pixels` (RGB or RGBA, `channels` per pixel) as a PNG at `path`.
// Returns false with s_err set. Exposed here so the device test can be pointed
// at a known image and know the encoder itself is not the thing under suspicion.
bool write_png(const uint8_t* pixels, uint32_t w, uint32_t h, uint32_t channels,
               const char* path) {
    if (!pixels || !w || !h || (channels != 3 && channels != 4)) {
        set_err("write_png: need non-null pixels, w and h > 0, and 3 or 4 channels");
        return false;
    }
    std::string raw;
    raw.reserve(static_cast<size_t>(h) * (1 + static_cast<size_t>(w) * 3));
    for (uint32_t y = 0; y < h; ++y) {
        raw.push_back(0);  // filter type 0 (None) for every scanline
        const uint8_t* row = pixels + static_cast<size_t>(y) * w * channels;
        for (uint32_t x = 0; x < w; ++x) {
            const uint8_t* px = row + static_cast<size_t>(x) * channels;
            raw.push_back(static_cast<char>(px[0]));
            raw.push_back(static_cast<char>(px[1]));
            raw.push_back(static_cast<char>(px[2]));
        }
    }

    std::string png;
    const char sig[8] = { '\x89', 'P', 'N', 'G', '\r', '\n', '\x1a', '\n' };
    png.append(sig, 8);

    std::string ihdr;
    put_u32(ihdr, w);
    put_u32(ihdr, h);
    ihdr.push_back(8);  // bit depth
    ihdr.push_back(2);  // colour type 2 = truecolour RGB
    ihdr.push_back(0);  // deflate
    ihdr.push_back(0);  // adaptive filtering
    ihdr.push_back(0);  // no interlace
    png_chunk(png, "IHDR", ihdr);
    png_chunk(png, "IDAT", zlib_store(raw));
    png_chunk(png, "IEND", std::string());

    FILE* f = std::fopen(path, "wb");
    if (!f) {
        set_err(std::string("write_png: cannot open ") + path);
        return false;
    }
    const size_t wrote = std::fwrite(png.data(), 1, png.size(), f);
    std::fclose(f);
    if (wrote != png.size()) {
        set_err(std::string("write_png: short write to ") + path);
        return false;
    }
    return true;
}

} // namespace

// Exposed for the device test, so a failure can be attributed to the diffusion
// step or to the encoder rather than to "generation did not work".
int32_t gs_sd_write_png_for_test(const uint8_t* pixels, uint32_t w, uint32_t h,
                                 uint32_t channels, const char* path) {
    return write_png(pixels, w, h, channels, path) ? GS_OK : GS_ERR_IO;
}

extern "C" {

gs_sd_ctx_t* gs_sd_create(const char* model_path) {
    if (!model_path || !*model_path) {
        set_err("gs_sd_create: model_path is required");
        return nullptr;
    }
    gs_sd_ctx_t* c = new (std::nothrow) gs_sd_ctx();
    if (!c) { set_err("gs_sd_create: out of memory"); return nullptr; }
    c->model_path = model_path;

#if defined(GS_SD_HAVE_SDCPP)
    sd_ctx_params_t params;
    sd_ctx_params_init(&params);
    params.model_path   = model_path;
    params.n_threads    = 0;              // 0 = sd_get_num_physical_cores()
    params.enable_mmap  = true;           // a 2.3 GB checkpoint must not be read twice
    params.backend      = nullptr;        // CPU. Vulkan is a separate build.
    c->inner = new_sd_ctx(&params);
    if (!c->inner) {
        set_err(std::string("gs_sd_create: new_sd_ctx returned null for ") + model_path);
        delete c;
        return nullptr;
    }
    c->available    = true;
    c->backend_name = "sd.cpp";
#else
    // HONEST, AND THE POINT OF IT. Without the library linked there is no
    // generation, and saying so is the whole value of this branch. The previous
    // version of this file is explicit that returning a placeholder image here
    // "would corrupt any downstream quality measurement", and that is still
    // true: a test that decodes a PNG cannot distinguish a real generation from a
    // grey rectangle, and a grey rectangle that always appears is worse than no
    // image at all.
    c->available    = false;
    c->backend_name = "sd.cpp:not-compiled";
    set_err("gs_sd_create: stable-diffusion.cpp is not linked into this build "
            "(GS_SD_HAVE_SDCPP undefined). gs_sd_render_svg remains available "
            "and needs no weights.");
#endif
    return c;
}

int32_t gs_sd_set_options(gs_sd_ctx_t* ctx, int32_t threads, float cfg_scale) {
    if (!ctx) { set_err("gs_sd_set_options: ctx is null"); return GS_ERR_INVALID_ARG; }
    if (threads > 0) ctx->threads = threads;
    if (cfg_scale >= 0.0f) ctx->cfg_scale = cfg_scale;
    return GS_OK;
}

int32_t gs_sd_available(const gs_sd_ctx_t* ctx) { return ctx && ctx->available ? 1 : 0; }

const char* gs_sd_backend_name(const gs_sd_ctx_t* ctx) {
    return ctx ? ctx->backend_name.c_str() : "none";
}

int32_t gs_sd_generate(gs_sd_ctx_t* ctx,
                       const char* prompt,
                       const char* negative_prompt,
                       int32_t width,
                       int32_t height,
                       int32_t steps,
                       const char* output_path) {
    if (!ctx)          { set_err("gs_sd_generate: ctx is null"); return GS_ERR_INVALID_ARG; }
    if (!prompt || !*prompt) {
        set_err("gs_sd_generate: prompt is required");
        return GS_ERR_INVALID_ARG;
    }
    if (!output_path || !*output_path) {
        set_err("gs_sd_generate: output_path is required");
        return GS_ERR_INVALID_ARG;
    }
    if (width <= 0 || height <= 0) {
        set_err("gs_sd_generate: width and height must be > 0");
        return GS_ERR_INVALID_ARG;
    }
    if (steps <= 0) {
        set_err("gs_sd_generate: steps must be > 0");
        return GS_ERR_INVALID_ARG;
    }
    if (!ctx->available) {
        set_err(std::string("gs_sd_generate: no diffusion backend: ")
                + ctx->backend_name);
        return GS_ERR_UNAVAILABLE;
    }

#if defined(GS_SD_HAVE_SDCPP)
    sd_img_gen_params_t gp;
    sd_img_gen_params_init(&gp);
    gp.prompt         = prompt;
    gp.negative_prompt = negative_prompt ? negative_prompt : "";
    gp.width          = width;
    gp.height         = height;
    gp.batch_count    = 1;
    gp.seed           = -1;   // -1 = pick one; reproducibility is the caller's job
    // NOTE: sd_guidance_params_t has NO `scale` field in the pinned header. It
    // has txt_cfg, img_cfg and distilled_guidance. An earlier reading of this
    // project's docs, or a header from a different revision, would have written
    // `guidance.scale` and produced a compile error here.
    gp.sample_params.sample_steps = steps;
    gp.sample_params.guidance.txt_cfg =
        (ctx->cfg_scale >= 0.0f) ? ctx->cfg_scale : 7.0f;

    sd_image_t* images = nullptr;
    int n_images = 0;
    const bool ok = generate_image(ctx->inner, &gp, &images, &n_images);
    if (!ok || !images || n_images < 1) {
        set_err("gs_sd_generate: generate_image returned no image");
        return GS_ERR_RUNTIME;
    }
    const sd_image_t& img = images[0];
    const int rc = write_png(img.data, img.width, img.height, img.channel, output_path);
    // The library owns `images`; there is no free_image in the pinned header, so
    // the array is left alone rather than guessed at. That is a real leak for a
    // long-lived process and is recorded rather than papered over — on a phone
    // each generation allocates a fresh model context in the common path, so it
    // does not accumulate; see ISSUE-LOG.md.
    if (rc != GS_OK) return rc;
    return GS_OK;
#else
    set_err("gs_sd_generate: stable-diffusion.cpp is not linked into this build");
    return GS_ERR_UNAVAILABLE;
#endif
}

void gs_sd_free(gs_sd_ctx_t* ctx) {
    if (!ctx) return;
#if defined(GS_SD_HAVE_SDCPP)
    if (ctx->inner) free_sd_ctx(ctx->inner);
#endif
    delete ctx;
}

int32_t gs_sd_render_svg(const char* spec_json, const char* output_path) {
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

} // extern "C"