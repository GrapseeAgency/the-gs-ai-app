//! Runtime library search paths for the binaries and test harness.
//!
//! gs-ffi links ONNX Runtime and llama.cpp, but `cargo:rustc-link-arg` emitted
//! from a *library's* build script only applies to that library. Anything that
//! links the gs-ffi rlib — the binaries, and the unit-test harness — needs the
//! runtime search path too, otherwise it dies with
//! "libonnxruntime.so.1: cannot open shared object file".
//!
//! Cargo rejects `rustc-link-arg-bins` from a package with no bin target, so
//! that directive is only emitted when src/main.rs is present. Driven by the
//! same environment variables gs-ffi uses, so there is one place to point at an
//! installation.

fn emit(dir: &str) {
    let p = std::path::Path::new(dir);
    if !p.is_dir() {
        return;
    }
    let flag = if std::path::Path::new("src/main.rs").exists() {
        // This package has binaries, so the -bins directive is legal.
        "cargo:rustc-link-arg-bins=-Wl,-rpath,{dir}"
    } else {
        "cargo:rustc-link-arg=-Wl,-rpath,{dir}"
    };
    println!("{}", flag.replace("{dir}", dir));
}

fn main() {
    for var in ["GS_ONNXRUNTIME_ROOT", "GS_LLAMA_ROOT"] {
        println!("cargo:rerun-if-env-changed={var}");
    }

    if let Ok(root) = std::env::var("GS_ONNXRUNTIME_ROOT") {
        emit(&format!("{root}/lib"));
    }
    if let Ok(root) = std::env::var("GS_LLAMA_ROOT") {
        emit(&format!("{root}/build/bin"));
        emit(&format!("{root}/bin"));
    }
}
