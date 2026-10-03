#pragma once

// gs_sd_dyn.h -- RUNTIME resolution of stable-diffusion.cpp's five entry points.
//
// WHY THIS EXISTS. sd.cpp bundles its own ggml (0.25.3, leejet fork) and
// llama.cpp bundles another (0.17.0, upstream). Linking both into one .so does
// not work: 574 identical `ggml_*` names, 0 only-in-llama.cpp, and four left
// undefined. That is a property of the SYMBOLS, not of the link order, so
// reordering the link line would convert a clean link failure into memory
// corruption at runtime.
//
// So they are not linked together. libgs_ffi.so links llama.cpp statically and
// this file loads sd.cpp out of a SEPARATE shared library at runtime.
//
// THE KEY MOVE, and the reason this is small rather than a rewrite: the sd.cpp
// TYPES (`sd_ctx_params_t`, `sd_img_gen_params_t`, `sd_image_t`, `sd_ctx_t`) come
// from `stable-diffusion.h` at COMPILE time and cost nothing at link time -- a
// header declares, it does not link. Only the five FUNCTIONS have to be
// resolved. So the wrapper keeps its exact existing code shape and every call
// site changes from `foo(args)` to `api().foo(args)`.

#if defined(GS_SD_HAVE_SDCPP)

#include <dlfcn.h>

#include <string>

namespace gs_sd_dyn {

struct Api {
    void (*ctx_params_init)(sd_ctx_params_t*) = nullptr;
    sd_ctx_t* (*new_ctx)(const sd_ctx_params_t*) = nullptr;
    void (*img_params_init)(sd_img_gen_params_t*) = nullptr;
    bool (*generate_image)(sd_ctx_t*, const sd_img_gen_params_t*, sd_image_t**, int*) =
        nullptr;
    void (*free_ctx)(sd_ctx_t*) = nullptr;

    bool loaded = false;
    // How the library was found, printed once so a log says WHICH route worked.
    std::string how;
    // Why not, when !loaded. Never empty on the failure path: a diagnostic that
    // says "diffusion unavailable" without saying what was tried is the class of
    // message this repository keeps having to unpick.
    std::string error;
};

namespace detail {

inline void* try_soname() {
    return dlopen("libgs_sd.so", RTLD_NOW | RTLD_LOCAL);
}

// Absolute path, derived from THIS library's own location.
//
// On Android the soname route works when the linker namespace already has the
// app's native lib dir, and it does not when it does not. Asking dladdr where
// libgs_ffi.so itself was loaded from is unconditional: libgs_sd.so is a SIBLING
// of it in the same jniLibs/<abi>/, so this finds it either way. That is the
// difference between "works on the runner" and "works on a phone".
inline void* try_sibling_path() {
    Dl_info info;
    if (dladdr(reinterpret_cast<void*>(&try_soname), &info) == 0 || !info.dli_fname) {
        return nullptr;
    }
    const std::string self(info.dli_fname);
    const size_t slash = self.find_last_of('/');
    if (slash == std::string::npos) return nullptr;
    const std::string path = self.substr(0, slash + 1) + "libgs_sd.so";
    return dlopen(path.c_str(), RTLD_NOW | RTLD_LOCAL);
}

// dlsym that fails LOUDLY and specifically, because a null function pointer
// called later is a segfault with no connection to its cause.
template <typename Fn>
bool bind(void* h, const char* name, Fn& slot, std::string& missing) {
    void* sym = dlsym(h, name);
    if (!sym) {
        if (!missing.empty()) missing += ", ";
        missing += name;
        return false;
    }
    slot = reinterpret_cast<Fn>(sym);
    return true;
}

}  // namespace detail

// Resolved once, thread-safe, never throws.
//
// RTLD_LOCAL is deliberate: sd.cpp's ggml symbols must NOT be able to satisfy a
// llama.cpp reference by accident. Global scope here would reintroduce exactly
// the collision this file exists to avoid.
inline const Api& get() {
    static Api api;
    static bool tried = false;
    if (tried) return api;
    tried = true;

    void* h = detail::try_soname();
    api.how = h ? "dlopen(\"libgs_sd.so\")" : "";
    if (!h) {
        h = detail::try_sibling_path();
        if (h) api.how = "dlopen(<dir of libgs_ffi.so>/libgs_sd.so)";
    }
    if (!h) {
        const char* e1 = dlerror();
        api.error =
            "libgs_sd.so could not be loaded by soname or by sibling path."
            " It must be packaged in the SAME jniLibs/<abi>/ directory as"
            " libgs_ffi.so. Last dlerror: " +
            std::string(e1 ? e1 : "(none reported)");
        return api;
    }

    std::string missing;
    bool ok = true;
    ok &= detail::bind(h, "sd_ctx_params_init", api.ctx_params_init, missing);
    ok &= detail::bind(h, "new_sd_ctx", api.new_ctx, missing);
    ok &= detail::bind(h, "sd_img_gen_params_init", api.img_params_init, missing);
    ok &= detail::bind(h, "generate_image", api.generate_image, missing);
    ok &= detail::bind(h, "free_sd_ctx", api.free_ctx, missing);

    if (!ok) {
        // The library OPENED but is not the sd.cpp we expect. Say exactly which
        // entry points were absent, because "generation unavailable" would send a
        // reader looking at the wrong file.
        api.error = "libgs_sd.so opened (" + api.how +
                    ") but is missing: " + missing;
        return api;
    }

    api.loaded = true;
    return api;
}

}  // namespace gs_sd_dyn

#endif  // GS_SD_HAVE_SDCPP