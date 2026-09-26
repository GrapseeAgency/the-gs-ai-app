//! Compiles the C ABI surfaces and links the vendored native libraries.
//!
//! The C++ side is built with `cc` rather than CMake so `cargo build` is the
//! single entry point. CMake remains available for a standalone build.
//! GS_LLAMA_ROOT points at a llama.cpp build tree; without it the ABI surface
//! still compiles and every entry point honestly reports UNAVAILABLE.

fn main() {
    let cpp = std::path::Path::new("..").join("..").join("cpp");
    let inc = cpp.join("include");

    // llama_wrapper
    let mut llama_build = cc::Build::new();
    llama_build
        .cpp(true)
        .std("c++17")
        .include(&inc)
        .include(cpp.join("llama_wrapper"))
        .file(cpp.join("llama_wrapper").join("llama_wrapper.cpp"))
        .warnings(true);

    // Link the real llama.cpp when a build tree is available. Without this
    // define the wrapper compiles but every call reports UNAVAILABLE, and
    // LlamaModel::load will hand back a handle that cannot generate anything.
    let llama_root = std::env::var("GS_LLAMA_ROOT").ok();
    let mut have_llama = false;
    if let Some(root) = &llama_root {
        let p = std::path::Path::new(root);
        if p.join("include").join("llama.h").exists() {
            llama_build
                .define("GS_LLAMA_HAVE_LLAMA", None)
                .include(p.join("include"))
                .include(p.join("ggml").join("include"));
            have_llama = true;
        }
    }
    llama_build.compile("gs_llama");

    // shared ABI: status codes, last-error, device gate
    cc::Build::new()
        .cpp(true)
        .std("c++17")
        .include(&inc)
        .file(cpp.join("src").join("gs_abi.cpp"))
        .warnings(true)
        .compile("gs_abi");

    // sd_wrapper: procedural path always, diffusion when GS_SD_ROOT is set
    cc::Build::new()
        .cpp(true)
        .std("c++17")
        .include(&inc)
        .include(cpp.join("sd_wrapper"))
        .file(cpp.join("sd_wrapper").join("sd_wrapper.cpp"))
        .warnings(true)
        .compile("gs_sd");

    // ---- link libllama when a build tree is available --------------------
    if have_llama {
        let root = llama_root.as_ref().expect("set when have_llama");
        println!("cargo:rustc-link-search=native={root}/build/bin");
        println!("cargo:rustc-link-search=native={root}/bin");
        println!("cargo:rustc-link-lib=dylib=llama");
        println!("cargo:rustc-link-lib=dylib=ggml");
        println!("cargo:rustc-link-lib=dylib=ggml-base");
        // Load-time path so the binary runs without LD_LIBRARY_PATH.
        println!("cargo:rustc-link-arg=-Wl,-rpath,{root}/build/bin");
        println!("cargo:rerun-if-env-changed=GS_LLAMA_ROOT");
    } else {
        println!(
            "cargo:warning=GS_LLAMA_ROOT unset or invalid: local generation is \
             BLOCKED, provider routing only"
        );
    }

    // ---- C kernels --------------------------------------------------------
    let kernels = std::path::Path::new("..").join("..").join("c").join("kernels");
    cc::Build::new()
        .include(&kernels)
        .file(kernels.join("simd.c"))
        .flag_if_supported("-std=c11")
        .warnings(true)
        .compile("gs_kernels");

    println!("cargo:rerun-if-changed={}/llama_wrapper/llama_wrapper.cpp", cpp.display());
    println!("cargo:rerun-if-changed={}/src/gs_abi.cpp", cpp.display());
    println!("cargo:rerun-if-changed={}/sd_wrapper/sd_wrapper.cpp", cpp.display());
    println!("cargo:rerun-if-changed={}/simd.c", kernels.display());
}
