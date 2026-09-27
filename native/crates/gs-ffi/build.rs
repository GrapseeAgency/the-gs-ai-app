//! Compiles the C ABI surfaces and links the vendored native libraries.
//!
//! The C++ side is built with `cc` rather than CMake so `cargo build` is the
//! single entry point. CMake remains available for a standalone build.
//! GS_LLAMA_ROOT points at a llama.cpp build tree; without it the ABI surface
//! still compiles and every entry point honestly reports UNAVAILABLE.

/// True when the build target is a mobile platform.
///
/// Mobile builds are PORTABLE-ONLY: no ONNX Runtime, no llama.cpp, no
/// Tesseract. None of those have drop-in prebuilts for arm64 Android or iOS,
/// and shipping a 300 MB llama.cpp inside a phone binary is not the product.
/// Every backend-dependent entry point then reports GS_ERR_UNAVAILABLE with a
/// reason, which is the honest state and the one the host app routes around.
///
/// The distinction matters for the fail-closed check below: a desktop build with
/// no ONNX is a misconfiguration and must panic, while a mobile build with no
/// ONNX is the intended configuration.
fn is_mobile_target() -> bool {
    let os = std::env::var("CARGO_CFG_TARGET_OS").unwrap_or_default();
    let env = std::env::var("CARGO_CFG_TARGET_ENV").unwrap_or_default();
    matches!(os.as_str(), "android" | "ios")
        || (os == "ios" && env == "sim")
        || std::env::var("CARGO_CFG_TARGET_VENDOR").unwrap_or_default() == "apple"
}

fn main() {
    let mobile = is_mobile_target();
    if mobile {
        println!(
            "cargo:warning=portable mobile build: CLIP, OCR and llama.cpp are NOT \
             compiled in; every such call reports GS_ERR_UNAVAILABLE"
        );
    }
    // Tell the compiler this cfg name is expected, so an unexpected one is a
    // warning rather than silently accepted.
    println!("cargo:rustc-check-cfg=cfg(gs_onnxruntime)");

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
    println!("cargo:rerun-if-env-changed=GS_LLAMA_ROOT");
    let kernels = cpp.join("..").join("c").join("kernels");
    let llama_root = if mobile { None } else { std::env::var("GS_LLAMA_ROOT").ok() };
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

    // Link libllama/ggml. These directives were lost during the mobile work and
    // the symptom was a link failure that named llama symbols rather than
    // anything about the change that caused it:
    //   undefined symbol: llama_backend_init / llama_model_default_params
    if have_llama {
        let root = llama_root.as_ref().expect("set when have_llama");
        for sub in ["build/bin", "bin"] {
            let p = format!("{root}/{sub}");
            if std::path::Path::new(&p).is_dir() {
                println!("cargo:rustc-link-search=native={p}");
            }
        }
        for l in ["llama", "ggml", "ggml-base"] {
            println!("cargo:rustc-link-lib=dylib={l}");
        }
        let bin = format!("{root}/build/bin");
        if std::path::Path::new(&bin).is_dir() {
            println!("cargo:rustc-link-arg=-Wl,-rpath,{bin}");
        }
    }

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

    // clip_wrapper: ONNX Runtime is optional. Without GS_ONNXRUNTIME_ROOT the
    // wrapper still compiles and every entry point reports UNAVAILABLE, so a
    // machine without onnxruntime can still build the workspace.
    let clip_dir = cpp.join("clip_wrapper");
    let mut clip_build = cc::Build::new();
    clip_build
        .cpp(true)
        .std("c++17")
        .include(&inc)
        .include(&clip_dir)
        .warnings(true);
    if !mobile {
        clip_build.file(clip_dir.join("clip_wrapper.cpp"));
    }

    println!("cargo:rerun-if-env-changed=GS_ONNXRUNTIME_ROOT");
    let ort_root = if mobile { None } else { std::env::var("GS_ONNXRUNTIME_ROOT").ok() };
    let mut have_ort = false;
    if let Some(root) = &ort_root {
        let p = std::path::Path::new(root);
        let inc_dir = p.join("include");
        if inc_dir.join("onnxruntime_cxx_api.h").exists() {
            clip_build
                .define("GS_CLIP_HAVE_ONNXRUNTIME", None)
                .include(&inc_dir);
            have_ort = true;
        }
    }
    clip_build.compile("gs_clip");

    if have_ort {
        // Link and rpath ONNX Runtime. This block was lost during the mobile
        // work and the failure named ONNX rather than the edit that caused it:
        //   undefined symbol: OrtGetApiBase
        let root = ort_root.as_ref().expect("set when have_ort");
        println!("cargo:rustc-link-search=native={root}/lib");
        println!("cargo:rustc-link-lib=dylib=onnxruntime");
        println!("cargo:rustc-link-arg=-Wl,-rpath,{root}/lib");
    }

    // ocr_tess: Tesseract is found through pkg-config when it is installed.
    // Absent it, the wrapper compiles and reports UNAVAILABLE.
    let ocr_dir = cpp.join("ocr_wrapper");
    let mut ocr_build = cc::Build::new();
    ocr_build
        .cpp(true)
        .std("c++17")
        .include(&ocr_dir)
        .warnings(true);
    if !mobile {
        ocr_build.file(ocr_dir.join("gs_ocr_tess.cpp"));
    }

    // Tesseract and Leptonica are probed independently: chaining them would
    // keep only the last library's link flags, so -ltesseract would be lost.
    let pkgs: Vec<pkg_config::Library> = ["tesseract", "lept"]
        .iter()
        .filter(|_| !mobile)
        .filter_map(|p| {
            pkg_config::Config::new()
                .cargo_metadata(false)
                .probe(p)
                .ok()
        })
        .collect();
    let have_tess = pkgs.len() == 2;
    if have_tess {
        ocr_build.define("GS_OCR_HAVE_TESSERACT", None);
        for c in &pkgs {
            for p in &c.include_paths {
                ocr_build.include(p);
            }
            for p in &c.link_paths {
                println!("cargo:rustc-link-search=native={}", p.display());
            }
            for l in &c.libs {
                println!("cargo:rustc-link-lib=dylib={l}");
            }
        }
    }
    ocr_build.compile("gs_ocr_tess");
    if !have_tess && !mobile {
        println!(
            "cargo:warning=tesseract/leptonica not found via pkg-config: OCR is \
             BLOCKED, provider routing only"
        );
    }

    // ---- portable mobile surface -----------------------------------------
    // Always compiled, desktop and mobile. On mobile it is the only thing linked.
    {
        let mdir = cpp.join("mobile");
        let mut mb = cc::Build::new();
        mb.cpp(true)
            .std("c++17")
            .include(&inc)
            .include(&mdir)
            // `cc::Build::define` prefixes the name itself, so passing
            // "GS_MOBILE_VERSION=\"0.1.0\"" produced -DGS_MOBILE_VERSION=GS_MOBILE_VERSION="0.1.0".
            // The value is a version string with dots, which the preprocessor
            // reads as a malformed float unless it is quoted, and cc offers no
            // way to quote it. So it goes in as a raw flag via .flag().
            .flag(format!(
                "-DGS_MOBILE_VERSION=\"{}\"",
                std::env::var("CARGO_PKG_VERSION").unwrap_or_else(|_| "0.0.0".into())
            ))
            .file(mdir.join("gs_mobile.cpp"))
            .warnings(true);
        if have_llama {
            mb.define("GS_MOBILE_HAVE_LLAMA", None)
                .include(cpp.join("llama_wrapper"));
        }
        if have_ort {
            mb.define("GS_CLIP_HAVE_ONNXRUNTIME", None);
        }
        if have_tess {
            mb.define("GS_OCR_HAVE_TESSERACT", None);
        }
        mb.compile("gs_mobile");
    }

    // JNI lives in Rust (src/jni.rs, android-only) rather than in a C++ shim.
    // Two implementations would export the same Java_* symbols and the dynamic
    // linker would pick one arbitrarily, which is the worst possible outcome
    // for a bridge: it works in the developer's build and not in CI.
    println!("cargo:rerun-if-env-changed=JNI_INCLUDE_DIRS");

    // Batched decode needs the gs_llama_ctx definition, so it compiles as part
    // of the same unit as llama_wrapper rather than as a separate archive.
    {
        let mut bb = cc::Build::new();
        bb.cpp(true)
            .std("c++17")
            .include(&inc)
            .include(cpp.join("llama_wrapper"))
            .file(cpp.join("llama_wrapper").join("batch.cpp"))
            .warnings(true);
        if let Some(root) = &llama_root {
            let p = std::path::Path::new(root);
            if p.join("include").join("llama.h").exists() {
                bb.define("GS_LLAMA_HAVE_LLAMA", None)
                    .include(p.join("include"))
                    .include(p.join("ggml").join("include"));
            }
        }
        bb.compile("gs_batch");
    }
    println!("cargo:rerun-if-changed={}/llama_wrapper/batch.cpp", cpp.display());
    println!("cargo:rerun-if-changed={}/clip_wrapper/clip_wrapper.cpp", cpp.display());
    println!("cargo:rerun-if-changed={}/mobile/gs_mobile.cpp", cpp.display());
    println!("cargo:rerun-if-changed={}/mobile/gs_jni.cpp", cpp.display());
    println!("cargo:rerun-if-changed={}/ocr_wrapper/gs_ocr_tess.cpp", cpp.display());
    println!("cargo:rerun-if-changed={}/simd.c", kernels.display());
}
