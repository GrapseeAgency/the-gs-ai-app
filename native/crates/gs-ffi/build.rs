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
    // THIS MESSAGE WAS A LIE AND COST SEVERAL RUNS TO DISCOVER.
    //
    // It sat here, inside a bare `if mobile`, printing "portable mobile build:
    // CLIP, OCR and llama.cpp are NOT compiled in" for EVERY mobile target --
    // unconditionally, before the llama.cpp linkage seven lines below had even
    // been attempted. So a build that linked a cross-compiled llama.cpp printed
    // it, and a build that did not print it too. It distinguished nothing.
    //
    // Two separate things were believed because of it:
    //   - that the shipped .so was portable-only (TRUE, and proven independently
    //     by selfCheck() on the device, but for a different reason: nothing set
    //     GS_LLAMA_PREBUILT at all)
    //   - that a build log containing it proved the build was portable (FALSE --
    //     it is printed unconditionally, so it proves only that the target is
    //     mobile)
    //
    // A CI gate built on it (`grep -q 'portable mobile build' && exit 1`) could
    // therefore never pass, and would have failed every future Android build
    // forever. Removed rather than made conditional: the accurate statement is
    // already made downstream, per-branch, by the messages that report which
    // backend was actually linked.
    let _ = mobile;
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
    println!("cargo:rerun-if-env-changed=GS_LLAMA_PREBUILT");
    let kernels = cpp.join("..").join("c").join("kernels");

    // Where do the llama.cpp headers and archives come from?
    //
    // Desktop: GS_LLAMA_ROOT, a full llama.cpp build tree.
    //
    // Mobile: GS_LLAMA_PREBUILT, the per-ABI package produced by the
    // android-deps workflow. This is new. The mobile build was PORTABLE-ONLY --
    // llama_root was forced to None, so gs_mobile_backend_available() returned 0
    // and every call reported GS_ERR_UNAVAILABLE. That made the .so ~220 KB and
    // shippable, and it also meant chat could not run on a phone at all.
    //
    // It also meant the device tests could not test the thing they exist to
    // test. Compiled and symbol-verified is not working, and the gap was only
    // visible once there was something to run it on.
    //
    // PREBUILT takes precedence on mobile because a cross-compiled archive is
    // the only thing that CAN work there: a host build tree is x86-64 Linux and
    // cannot link into an arm64 Android library.
    let prebuilt = std::env::var("GS_LLAMA_PREBUILT").ok().map(std::path::PathBuf::from);
    let llama_root = if mobile {
        // Only a prebuilt package is usable on a device target.
        prebuilt.as_ref().and_then(|p| {
            if p.join("include").join("llama.h").exists() { Some(p.clone()) } else { None }
        })
    } else {
        std::env::var("GS_LLAMA_ROOT").ok().map(std::path::PathBuf::from)
    };
    let using_prebuilt = prebuilt.is_some() && llama_root.is_some();

    let mut have_llama = false;
    if let Some(root) = &llama_root {
        if root.join("include").join("llama.h").exists() {
            llama_build
                .define("GS_LLAMA_HAVE_LLAMA", None)
                .include(root.join("include"));
            // TWO LAYOUTS, AND PICKING THE WRONG ONE IS A BUILD FAILURE.
            //
            // A llama.cpp BUILD TREE (GS_LLAMA_ROOT, desktop) puts the ggml
            // headers at ggml/include/. The android-deps PACKAGE (GS_LLAMA_PREBUILT,
            // mobile) ships them at ggml-include/. Adding only the build-tree path
            // meant the mobile build compiled llama_wrapper.cpp and then:
            //
            //     native/prebuilts/android-x86_64/include/llama.h:4:10: fatal
            //     error: 'ggml.h' file not found
            //     1 error generated.
            //     error occurred in cc-rs: command did not execute successfully
            //
            // Both are added, and only when present. cc-rs tolerates include
            // paths that do not exist, so this costs nothing and covers both
            // shapes rather than encoding a guess about which one we were handed.
            let ggml_tree = root.join("ggml").join("include");
            let ggml_pkg = root.join("ggml-include");
            let have_tree = ggml_tree.is_dir();
            let have_pkg = ggml_pkg.is_dir();
            // Fail with a NAMED error rather than a compiler diagnostic about a
            // header, because "ggml.h not found" does not say which of the two
            // layouts was expected. Checked BEFORE the include() calls, which
            // take ownership of the paths.
            if !have_tree && !have_pkg {
                let listing: Vec<String> = std::fs::read_dir(root)
                    .map(|d| {
                        d.filter_map(|e| e.ok().map(|e| e.file_name()))
                            .map(|n| n.to_string_lossy().into_owned())
                            .collect()
                    })
                    .unwrap_or_default();
                panic!(
                    "no ggml headers under {}: expected ggml/include/ (build tree) \
                     or ggml-include/ (prebuilt package), found neither. contents: {:?}",
                    root.display(),
                    listing
                );
            }
            if have_tree {
                llama_build.include(&ggml_tree);
            }
            if have_pkg {
                llama_build.include(&ggml_pkg);
            }
            have_llama = true;
        }
    }
    if mobile && have_llama {
        // This is the define gs_mobile_backend_available() keys off. Without it
        // the library links cleanly and still cannot generate, which is the
        // worst combination: it looks installed and does nothing.
        println!("cargo:rustc-cfg=gs_mobile_llama");
        println!("cargo:rustc-check-cfg=cfg(gs_mobile_llama)");
        println!("cargo:warning=mobile build WITH a llama.cpp backend from the prebuilt package");
    } else if mobile {
        println!(
            "cargo:warning=portable mobile build: no llama.cpp prebuilt at GS_LLAMA_PREBUILT. \
             Every generation call will report GS_ERR_UNAVAILABLE. This is a supported \
             configuration and the app falls back, but chat will not run."
        );
    }
    if using_prebuilt {
        println!("cargo:warning=using cross-compiled llama.cpp from {}", llama_root.as_ref().unwrap().display());
    }
    llama_build.compile("gs_llama");

    // Link libllama/ggml. These directives were lost during the mobile work and
    // the symptom was a link failure that named llama symbols rather than
    // anything about the change that caused it:
    //   undefined symbol: llama_backend_init / llama_model_default_params
    if have_llama {
        let root = llama_root.as_ref().expect("set when have_llama");
        // The prebuilt package puts archives in lib/; a host llama.cpp build
        // tree puts them in build/bin. Both are searched, and rpath is only
        // emitted for a host tree because a device library is not loaded from
        // a build directory.
        for sub in ["lib", "build/bin", "bin"] {
            let p = format!("{}/{sub}", root.display());
            if std::path::Path::new(&p).is_dir() {
                println!("cargo:rustc-link-search=native={p}");
            }
        }
        // Static or shared is decided by WHAT IS ON DISK, not by a hardcoded
        // assumption. The desktop llama.cpp here is a SHARED build, so there is
        // no libllama.a anywhere, and asking for `static=llama` produces:
        //     error: could not find native static library `llama`
        // The prebuilt device package is static-only, because a .so for arm64
        // Android is not something a NDK build can install into a library.
        let libdir = std::path::Path::new(root).join("lib");
        let mut statics: Vec<String> = Vec::new();
        if let Ok(rd) = std::fs::read_dir(&libdir) {
            for e in rd.flatten() {
                let p = e.path();
                if p.extension().and_then(|x| x.to_str()) == Some("a") {
                    if let Some(stem) = p.file_stem().and_then(|x| x.to_str()) {
                        statics.push(stem.to_string());
                    }
                }
            }
        }
        // TWO BUGS, BOTH INVISIBLE UNTIL A LINKER RAN.
        //
        // (1) THE `lib` PREFIX MUST BE STRIPPED. `file_stem()` on `libggml.a`
        //     returns `libggml`, so `rustc-link-lib=static=libggml` makes rustc
        //     search for `liblibggml.a`. That is the whole of run 36512916400:
        //
        //       error: could not find native static library `libggml`,
        //       perhaps an -L flag is missing?
        //
        //     The -L flag was fine. The archives are all present. The name was
        //     wrong, and the message blames the path, which is why nothing in the
        //     build log pointed at it.
        //
        // (2) ORDER MATTERS FOR STATIC ARCHIVES, AND SORTING IS NOT ORDERING.
        //     A consumer must precede what it consumes: llama needs ggml, ggml
        //     needs ggml-cpu and ggml-base. Alphabetical gives
        //         libggml, libggml-base, libggml-cpu, libllama
        //     which is exactly backwards. The correct order is
        //         llama, ggml, ggml-cpu, ggml-base
        //     Left alphabetical, this becomes a wall of undefined symbols the
        //     moment (1) is fixed -- so both are corrected together rather than
        //     discovering (2) as a second failure.
        //
        // Anything not named here keeps a stable alphabetical position AFTER the
        // known dependencies, so a future archive is still linked rather than
        // silently dropped.
        const DEP_ORDER: &[&str] = &["llama", "ggml", "ggml-cpu", "ggml-base"];
        statics.sort_by_key(|n| {
            let bare = n.strip_prefix("lib").unwrap_or(n.as_str());
            match DEP_ORDER.iter().position(|d| *d == bare) {
                Some(i) => (i, String::new()),
                None => (DEP_ORDER.len(), n.clone()),
            }
        });
        if !statics.is_empty() {
            // Whatever the package actually contains, listed, because a missing
            // archive surfaces as "undefined symbol: ggml_..." at link time with
            // no hint about which package should have provided it.
            println!("cargo:warning=linking static llama.cpp archives in link order: {}",
                     statics.iter().map(|n| n.strip_prefix("lib").unwrap_or(n.as_str()).to_string()).collect::<Vec<_>>().join(", "));
            for n in statics {
                let bare = n.strip_prefix("lib").unwrap_or(n.as_str()).to_string();
                println!("cargo:rustc-link-lib=static={bare}");
            }
        } else {
            println!("cargo:warning=linking shared llama.cpp from {}/build/bin", root.display());
            for l in ["llama", "ggml", "ggml-base"] {
                println!("cargo:rustc-link-lib=dylib={l}");
            }
        }
        let bin = format!("{}/build/bin", root.display());
        if !using_prebuilt && std::path::Path::new(&bin).is_dir() {
            println!("cargo:rustc-link-arg=-Wl,-rpath,{bin}");
        }

        // SYSTEM FRAMEWORKS ON APPLE TARGETS.
        //
        // GGML_METAL is ON by default when llama.cpp is built for an Apple
        // target, so the package contains libggml-metal.a, and every symbol THAT
        // archive references belongs to a system framework. Raw, run 36705647392:
        //
        //   Undefined symbols for architecture arm64:
        //     "_MTLCreateSystemDefaultDevice", referenced from
        //        _ggml_metal_device_init in libggml-metal.a[3](ggml-metal-device.m.o)
        //     "_OBJC_CLASS_$_MTLCompileOptions", ...
        //     "_OBJC_CLASS_$_NSLock", ...
        //     "_OBJC_CLASS_$_NSString", ...
        //     "___CFConstantStringClassReference", ...
        //
        // NOT ONE llama_ or ggml_ symbol in that list, which IS the diagnosis:
        // llama.cpp and ggml linked correctly and only the frameworks they call
        // into are missing. A llama_ symbol there would have meant a different
        // fix entirely.
        //
        // THIS BELONGS HERE AND NOT IN ios/project.yml. `cargo build` uses `cc`
        // as the LINKER DRIVER --
        //
        //   error: linking with `cc` failed: exit status: 1
        //
        // -- and project.yml's OTHER_LDFLAGS only ever reaches xcodebuild. The
        // first attempt put -framework Metal in project.yml, which made the
        // XCODE flags right and left the CARGO link failing with exactly the
        // same undefined symbols. Both links need it; they read it from
        // different files.
        if have_llama && cfg!(target_vendor = "apple") {
            // `framework=` is the cargo spelling for -framework=.
            println!("cargo:rustc-link-lib=framework=Metal");
            println!("cargo:rustc-link-lib=framework=Foundation");
            // Accelerate, for vDSP. ggml-cpu.a calls it UNCONDITIONALLY on Apple
            // targets -- run 36725021214 left _vDSP_maxv, _vDSP_vadd, _vDSP_vmul,
            // _vDSP_vsmul, _vDSP_vsub and others undefined, all referenced from
            // libggml-cpu.a(ops.cpp.o). Accelerate is Apple's own vector library
            // and is part of the OS, so this adds no size to the binary.
            println!("cargo:rustc-link-lib=framework=Accelerate");
            // -ObjC++ links the Objective-C runtime those _OBJC_CLASS_$
            // references resolve through.
            println!("cargo:rustc-link-arg=-ObjC++");
            println!("cargo:warning=linking Metal and Foundation for an Apple target with llama.cpp");
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
    // On mobile the clip wrapper is deliberately not compiled: ONNX Runtime has
    // no drop-in arm64 Android build and a portable phone library must not carry
    // a decode engine. Its entry points still exist in gs_mobile and report
    // GS_ERR_UNAVAILABLE with a reason.
    //
    // The compile() call below used to be UNCONDITIONAL, which emitted a
    // `rustc-link-lib=static=gs_clip` for an archive that was never created.
    // Raw error, run 36350855020, all four ABIs:
    //     llvm-ar: error: unable to load
    //       .../out/libgs_clip.a: No such file or directory
    //     error: failed to run custom build command for `gs-ffi`
    //     ##[error]Process completed with exit code 101.
    // So: compile only when there is something to compile.
    let mut have_clip_src = false;
    if !mobile {
        clip_build.file(clip_dir.join("clip_wrapper.cpp"));
        have_clip_src = true;
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
    if have_clip_src {
        clip_build.compile("gs_clip");
    } else {
        println!("cargo:warning=skipping gs_clip: no sources on this target");
    }

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
    // Same defect as clip_wrapper: compiling with zero sources emits a link
    // directive for an archive that does not exist. Both produced the same
    // llvm-ar error on the same run.
    let mut have_ocr_src = false;
    if !mobile {
        ocr_build.file(ocr_dir.join("gs_ocr_tess.cpp"));
        have_ocr_src = true;
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
    if have_ocr_src {
        ocr_build.compile("gs_ocr_tess");
    } else {
        println!("cargo:warning=skipping gs_ocr_tess: no sources on this target");
    }
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
