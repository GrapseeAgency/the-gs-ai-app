---
name: cargo-workspace-sys
description: |-
  Structure a Rust `-sys` crate that compiles and links a native C/C++ library from `build.rs`.
  TRIGGER — read BEFORE writing build glue — whenever the task involves: a `-sys` crate, the `cmake`
  crate, `cc` crate, bindgen in build.rs, `rustc-link-lib` / `rustc-link-search`, `cargo:rustc-link-lib=static=`,
  `OUT_DIR`, wrapping a native library that ships a CMakeLists.txt, or a build failure of the form
  "undefined reference to" / "cannot find -lfoo" / "linker `cc` not found". Use when Rust must consume a
  C/C++ project (llama.cpp, stable-diffusion.cpp, a vendor SDK) that has its own CMake build.
  Covers the `-sys` vs safe-wrapper split, the cmake-rs Config builder, cross-compilation hooks, and
  how to keep the native build from running on every cargo invocation.
---

# Cargo `-sys` crate + CMake workspace pattern

## Purpose

A `-sys` crate is the thin, unsafe, *unopinionated* layer that compiles a native
library and links it. It exposes raw `extern "C"` declarations and nothing else. The
idiomatic layout is a **three-crate workspace**:

```
myproject/
├── myproject-sys/      # build.rs compiles the C/C++; raw extern "C" decls. No logic.
├── myproject/          # safe idiomatic wrapper. Depends on -sys. All the unsafety lives here.
└── myproject-cli/      # optional binary
```

Why the split: `-sys` must be rebuildable and auditable on its own, with zero
dependency churn. The safe wrapper carries the ergonomics and the `unsafe` blocks.
Merging them means every native rebuild drags your whole API surface with it.

## Quick Reference

### The three `cargo:` directives that matter

```rust
println!("cargo:rustc-link-search=native={}", dst.display());  // WHERE to look
println!("cargo:rustc-link-lib=static=foo");                   // WHAT to link
println!("cargo:rerun-if-changed=src/foo.cc");                 // WHEN to rebuild
```

`rustc-link-lib` forms:

| Form | Meaning |
|---|---|
| `static=foo` | link `libfoo.a` (bundled, picked from link-search) |
| `dylib=foo` | link `libfoo.so` / `.dylib` / `.dll` |
| `foo` | let the linker decide |

`rustc-link-search` forms:

| Form | Meaning |
|---|---|
| `native=<path>` | pass `-L<path>` — the normal case for `OUT_DIR` |
| `framework=<path>` | macOS `.framework` search path |
| `native=<kind>=<path>` | kind-gated (`+bundle=`, etc.) |

### `cmake` crate (cmake-rs 0.1.58)

```toml
[build-dependencies]
cmake = "0.1"
```

```rust
use cmake::Config;

// Minimal: build the project in ./libfoo, install into $OUT_DIR
let dst = cmake::build("libfoo");
println!("cargo:rustc-link-search=native={}", dst.display());
println!("cargo:rustc-link-lib=static=foo");
```

Builder form — this is what you actually want:

```rust
let dst = Config::new("libfoo")
    .define("FOO", "BAR")          // -DFOO=BAR
    .define("SD_VULKAN", "ON")     // vendor C++ options go through .define()
    .cflag("-fno-exceptions")      // C flags
    .cxxflag("-std=c++17")         // C++ flags
    .flag("/W3")                   // generic (MSVC-style) flags
    .profile("Release")
    .generator("Ninja")            // omit on Windows/MSVC if unsure
    .build_target("foo")           // build only this target, not `all`
    .build();

println!("cargo:rustc-link-search=native={}", dst.display());
println!("cargo:rustc-link-lib=static=foo");
```

Other useful `Config` methods: `.c_define()`, `.cxx_define()`, `.env(k, v)`,
`.host(host_triple)` (for cross), `.target(triple)`, `.shared(bool)`, `.archiver()`,
`.parallel(n)`, `.very_verbose(bool)`, `.verbose(bool)`, `.build_config(name)`.

`.define()` is the one that matters for C++ libraries: their options are CMake cache
variables, not compiler flags. Passing `-DLLAMA_CUBLAS=ON` as a `cflag` does nothing.

### `cc` crate — for plain C/C++ with no build system

```rust
cc::Build::new()
    .cpp(true)                       // compile as C++
    .file("src/foo.cc")
    .include("include")
    .std("c++17")
    .flag_if_supported("-march=native")
    .define("FOO", Some("BAR"))
    .warnings(true)
    .compile("foo");
```

Use `cc` for a handful of files, `cmake` when the vendor ships a `CMakeLists.txt`
(llama.cpp, stable-diffusion.cpp, and most SDKs do). Trying to reimplement their
build with `cc` is a reliable way to get subtly wrong `-D` flags.

### Where the artefacts land

`cmake::Config::build()` returns the **install** prefix, not the build dir. Link
against the returned path. Never hardcode `target/debug/build/...` — that hash
directory name is unstable and will break.

## Working Example

`build.rs` for a `-sys` crate wrapping a C++ library with a CMake build:

```rust
use std::env;
use std::path::PathBuf;

fn main() {
    // Host toolchain awareness — needed for cross builds and for bindgen.
    let target = env::var("TARGET").unwrap();
    let host   = env::var("HOST").unwrap();

    // Tell cmake-rs where to put things.
    let dst = cmake::Config::new("native")
        .define("LIBFOO_BUILD_TESTS", "OFF")
        .define("LIBFOO_BUILD_SHARED", "OFF")
        .cxxflag("-std=c++17")
        .profile("Release")
        .build();

    // Static, bundled, found via the search path above.
    println!("cargo:rustc-link-search=native={}", dst.join("lib").display());
    println!("cargo:rustc-link-lib=static=foo");

    // System libs the C++ side needs but does not vendor.
    if target.contains("apple") {
        println!("cargo:rustc-link-lib=framework=CoreFoundation");
        println!("cargo:rustc-link-lib=framework=Metal");
    } else if target.contains("linux") {
        println!("cargo:rustc-link-lib=dylib=dl");
        println!("cargo:rustc-link-lib=dylib=pthread");
    }

    // --- bindgen (optional: only if you are NOT hand-writing extern "C") ---
    // let builder = bindgen::Builder::default()
    //     .header("wrapper.h")
    //     .allowlist_function("foo_.*")
    //     .generate_comments(false);
    // let bindings = builder.generate().expect("bindgen failed");
    // bindings
    //     .write_to_file(PathBuf::from(env::var_os("OUT_DIR").unwrap()).join("bindings.rs"))
    //     .expect("could not write bindings");

    println!("cargo:rerun-if-changed=native/CMakeLists.txt");
    println!("cargo:rerun-if-changed=build.rs");
    println!("cargo:rerun-if-env-changed=FOO_EXTRA_CFLAGS");
}
```

Then the crate root — raw declarations only, no logic:

```rust
// lib.rs
#![allow(non_camel_case_types)]

use std::os::raw::{c_char, c_int};

extern "C" {
    pub fn foo_init() -> c_int;
    pub fn foo_run(ctx: *mut FooCtx, input: *const c_char) -> *mut c_char;
    pub fn foo_free_string(s: *mut c_char);
}
```

Hand-writing the `extern "C"` block is a legitimate choice and often better than
bindgen for a small, stable surface: no clang dependency at build time, no generated
code to review, no libclang version drift. Use bindgen when the surface is large or
changes often. See `cxx-bridge-patterns` if you want type safety rather than raw
pointers.

## Common Pitfalls

1. **Using `cflag()` for a CMake option.** `SD_VULKAN=ON` is a *define*, not a flag.
   Wrong method and the option is silently dropped — the build succeeds with the
   wrong backend and you benchmark the CPU path believing it is Vulkan.
2. **Hardcoding the `target/.../build/<hash>` path.** The hash is unstable. Always use
   the `dst` returned by `build()`.
3. **Linking `static=` but only building a shared lib** (or vice versa). Match the
   `rustc-link-lib` form to what the CMake build actually produced.
4. **Missing `cargo:rerun-if-changed`.** Cargo defaults to rerunning `build.rs` only
   when *no* `rerun-if` is present, and then it reruns on **every** file in the
   package. One missing line means either a stale native build or a full C++ rebuild
   on every single `cargo build`. Both are painful; pick deliberately.
5. **No `rerun-if-env-changed`** for flags the build reads from the environment. Change
   `CFLAGS` and nothing rebuilds.
6. **Forgetting the system libs the C++ side needs.** `libstdc++`, `dl`, `pthread`,
   `m` on Linux; `Metal`/`CoreFoundation` frameworks on macOS. The C++ link succeeds
   and *your Rust* link fails with undefined references — which sends you hunting in
   the wrong place.
7. **Omitting `.build_target()`** so cmake builds `all`, including tests, examples
   and benchmarks. Vastly slower, and often fails on an optional dependency.
8. **Ignoring `HOST` vs `TARGET`.** When cross-compiling you must configure cmake for
   `TARGET` while it *runs* on `HOST`. Getting this backwards produces a native
   binary named like the target.
9. **Relying on `.flag()` for gcc/clang.** `flag()` is MSVC-style (`/W3`). For
   gcc/clang use `.cflag()`/`.cxxflag()`, or flags land in the wrong position.
10. **Vendoring `libstdc++` expectations.** If the C++ uses exceptions or RTTI across
    the boundary, the Rust side must be built with matching `-fexceptions` /
    `-frtti` expectations, or you get typeinfo mismatch crashes at `catch`.
11. **Skipping the `-sys` crate and putting `unsafe` in the public API.** You lose
    the ability to audit the boundary in one place.

## Debugging

**Is the native library actually built?** Before blaming the link, look:

```bash
find target -name "libfoo.a" -o -name "libfoo.so" | head
ls target/*/build/*/out/lib/ 2>/dev/null
```

If it is missing, the problem is cmake, not rustc.

**Separate "not found" from "not linkable":**

```bash
# Is it in the declared search path at all?
nm -A $(find target -name 'libfoo.a') | head

# What does rustc actually pass to the linker?
cargo build -v 2>&1 | grep -- '-lfoo'

# What does the built artefact reference?
readelf -d target/debug/libmyproject.rlib 2>/dev/null | grep NEEDED
```

**Turn on native build output.** `Config::very_verbose(true)` streams full cmake
stdout/stderr through cargo. Usually the real error is in there and cargo is
collapsing it.

**Check the C++ side links standalone first.** If CMake built the lib, run
`cmake --build build --verbose` and read the exact compile/link lines. Reproduce a
failure by hand with those flags — you get a real error message instead of cargo's.

**`cc` vs `cmake` mismatch symptoms:** if the C++ library builds fine under its own
cmake but fails under `cc`, you are missing a generated header (configure step) or
a `-D` define. Check for headers emitted into the build dir and `include!` them via
`.include(build_dir.join("include"))`.

For undefined-symbol resolution *after* a successful build, and for `dlopen`-based
backends, use the `ffi-debugging` skill.

## Source Links

- `cmake` crate on crates.io (primary — builder API, `Config`, `build`):
  https://crates.io/crates/cmake
- `cmake` crate docs, v0.1.58 (authoritative method list and examples):
  https://docs.rs/cmake/latest/cmake/
- cmake-rs repository: https://github.com/rust-lang/cmake-rs
- piper-rs DeepWiki — build system page showing a real `-sys` crate that combines
  `build.rs`, bindgen and cross-platform native linking:
  https://deepwiki.com/thewh1teagle/piper-rs
- cef-rs DeepWiki — a `-sys` crate compiling C++ through CMake with platform-specific
  linking: https://deepwiki.com/tauri-apps/cef-rs
- Reference example, a real `build.rs` that runs CMake from an FFI shim (CrispASR):
  https://github.com/CrispStrobe/CrispASR/blob/main/crispasr-sys/build.rs
- `cc` crate: https://docs.rs/cc/latest/cc/
- `bindgen`: https://rust-lang.github.io/rust-bindgen/
