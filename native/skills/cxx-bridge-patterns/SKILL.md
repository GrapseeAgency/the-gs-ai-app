---
name: cxx-bridge-patterns
description: |-
  Bridge Rust and C++ safely with the `cxx` crate (dtolnay/cxx) — `#[cxx::bridge]` shared structs,
  opaque types, `extern "Rust"` / `extern "C++"` blocks, UniquePtr, CxxString, CxxVector, and Cargo/
  CMake integration. TRIGGER — read BEFORE writing any FFI glue — whenever the task involves: cxx,
  cxx::bridge, cxx_build::bridge, cxxbridge-cmd, mixing Rust with a C++ library, wrapping a C++ SDK in
  Rust, exposing Rust to C++, calling C++ from Rust safely, or replacing bindgen/cbindgen. Use when
  a Rust crate must call a C++ engine (llama.cpp, stable-diffusion.cpp, a vendor SDK) and you are
  choosing between bindgen and cxx. Covers the zero-copy builtin type table and the build.rs setup.
---

# CXX bridge patterns

## Purpose

`cxx` gives you a **safe** Rust↔C++ boundary. The premise: you declare *both* sides of
the boundary in one Rust module, and cxx statically verifies the two agree, then
generates matching shims for each. The Rust side can be 100% safe; the C++ side is
audited on its own.

This is **not** a bindgen replacement. bindgen reads a C header and emits unsafe Rust;
cbindgen does the reverse. cxx is a lower-level primitive — a replacement for the
concept of hand-written `extern "C"` signatures — that additionally gives you
idiomatic standard-library interop.

**Version requirement: rustc 1.88+ and C++11 or newer** (`cxx = "1.0"`).

## Quick Reference

### The three kinds of item in a bridge

| Kind | Where | Visibility | Passable by value? |
|---|---|---|---|
| **Shared struct** | top level of the bridge mod | fields visible to *both* | yes |
| **Opaque type** | inside `extern "Rust"` or `extern "C++"` | fields secret from the other side | **no** — only behind `&`/`Box`/`UniquePtr` |
| **Function** | inside either block | callable from the other side | n/a |

### Block semantics

- `extern "Rust"` — Rust is the source of truth. Items implicitly resolve against the
  **parent (`super`) module**. Think of every entry as an implicit `use super::X`.
- `extern "C++"` — C++ is the source of truth. You must `include!("...")` the header(s)
  declaring those APIs. Signatures are typed by hand but **static assertions verify
  they match the real header**.

### Builtin type table

| Rust | C++ | Restriction |
|---|---|---|
| `String` | `rust::String` | — |
| `&str` | `rust::Str` | — |
| `&[T]` | `rust::Slice<const T>` | cannot hold opaque C++ type |
| `&mut [T]` | `rust::Slice<T>` | cannot hold opaque C++ type |
| `CxxString` | `std::string` | **cannot be passed by value** |
| `Box<T>` | `rust::Box<T>` | cannot hold opaque C++ type |
| `UniquePtr<T>` | `std::unique_ptr<T>` | cannot hold opaque **Rust** type |
| `SharedPtr<T>` | `std::shared_ptr<T>` | cannot hold opaque **Rust** type |
| `[T; N]` | `std::array<T, N>` | cannot hold opaque C++ type |
| `Vec<T>` | `rust::Vec<T>` | cannot hold opaque C++ type |
| `CxxVector<T>` | `std::vector<T>` | not by value; no opaque Rust type |
| `*mut T` / `*const T` | `T*` / `const T*` | **fn using raw pointer args must be `unsafe`** |
| `fn(T,U) -> V` | `rust::Fn<V(T,U)>` | only Rust→C++ direction implemented |
| `Result<T>` | throw / catch | **return type only** |

Not yet implemented (do not plan around these): `BTreeMap`, `HashMap`, `Arc`,
`Option<T>`, `std::map`, `std::unordered_map`. For a map across the boundary, pass a
`Vec` of key/value pairs and convert on the far side.

The C++ side of the `rust` namespace is defined by `include/cxx.h` — **include it**
in any C++ file that touches `rust::String`, `rust::Vec`, `rust::Slice`, etc.

## Working Example

The canonical shape: a Rust app calling a C++ blobstore client, with one callback
into Rust. This is the README's `demo`, condensed.

```rust
// src/main.rs
#[cxx::bridge]
mod ffi {
    // Shared struct: both languages see these fields.
    struct BlobMetadata {
        size: usize,
        tags: Vec<String>,
    }

    extern "Rust" {
        // Opaque to C++. Only Rust sees MultiBuf's fields.
        type MultiBuf;

        // Implemented in Rust, callable from C++.
        fn next_chunk(buf: &mut MultiBuf) -> &[u8];
    }

    unsafe extern "C++" {
        // Headers declaring the C++ API. Not parsed by cxx, but #include'd and
        // used in static assertions that verify our picture is accurate.
        include!("demo/include/blobstore.h");

        // Opaque to Rust. Only C++ sees BlobstoreClient's fields.
        type BlobstoreClient;

        // Implemented in C++, callable from Rust.
        fn new_blobstore_client() -> UniquePtr<BlobstoreClient>;
        fn put(&self, parts: &mut MultiBuf) -> u64;
        fn tag(&self, blobid: u64, tag: &str);
        fn metadata(&self, blobid: u64) -> BlobMetadata;
    }
}
```

**Your implementations do not need `extern "C"`, `#[no_mangle]`, or `unsafe`.**
cxx inserts the shims. Write plain Rust and plain C++.

```rust
// C++ side, unchanged idiomatic C++
std::unique_ptr<BlobstoreClient> new_blobstore_client() {
    return std::make_unique<BlobstoreClient>();
}
```

### Cargo build script

```toml
# Cargo.toml
[dependencies]
cxx = "1.0"

[build-dependencies]
cxx-build = "1.0"
```

```rust
// build.rs — cxx_build::bridge() returns a cc::Build, so you can chain onto it
fn main() {
    cxx_build::bridge("src/main.rs")
        .file("src/blobstore.cc")
        .std("c++11")
        .compile("cxxbridge-demo");

    // Without these, edits to C++ sources will not trigger a rebuild.
    println!("cargo:rerun-if-changed=src/blobstore.cc");
    println!("cargo:rerun-if-changed=include/blobstore.h");
}
```

### Non-Cargo builds (Bazel / Buck / CMake-driven)

cxx is not Cargo-only. Run the generator standalone:

```bash
$ cargo install cxxbridge-cmd
$ cxxbridge src/main.rs --header > path/to/mybridge.h
$ cxxbridge src/main.rs           > path/to/mybridge.cc
```

This is the seam to use when a C++ build system (e.g. CMake for llama.cpp or
stable-diffusion.cpp) drives compilation and Cargo is only along for the ride.

### The C++ side must speak cxx's types, not std's

This is verified by build, and it is the thing that most often blocks a first cxx
project. The bridge declaration *is* the C++ signature. Whatever you write on the
Rust side determines the C++ parameter and return types:

| You write in the bridge | C++ implementation MUST use |
|---|---|
| `fn run(&self, prompt: &str, n: i32)` | `RunResult run(rust::Str, std::int32_t) const` |
| `fn backends(&self) -> Vec<String>` | `rust::Vec<rust::String> backends() const` |
| `fn name(&self) -> &str` | `rust::Str name() const` |
| `fn take(&mut self, items: &[u8])` | `void take(rust::Slice<const std::uint8_t>)` |

Writing `std::string` / `std::vector<std::string>` in the C++ implementation
produces a wall of `cannot convert` errors from the *generated* `.cc`, naming
`rust::cxxbridge1::Str` and `rust::cxxbridge1::Vec<rust::String>`. Read those
type names literally — they are the contract.

**Three more hard rules, all build-verified:**

- **Never define a shared struct in your own C++ header.** cxx *generates* it from
  the bridge. Defining it too is a redefinition error. Reference the name; do not
  define it.
- **Hand-written `.cc` files must include the cxx-generated header** to see a
  shared struct's definition. `cxx_build` puts the generated include dir on the
  include path for every `.file()` you add, so
  `#include "mycrate/src/main.rs.h"` resolves (path is `<crate>/<bridge-file>.h`).
  Without it you get `aggregate has incomplete type and cannot be defined`.
- **C++ constructors are NOT auto-mapped.** `Engine::Engine()` gives you no
  `Engine::new()`. Declare a free factory in the bridge —
  `fn new_engine() -> UniquePtr<Engine>;` — implement it with
  `std::make_unique<Engine>()`, and **declare it after the class** in your header
  (a `std::unique_ptr<Engine>` return before `class Engine` is a hard error).
- **`&self` in the bridge means `const` in C++.** Forgetting `const` gives a
  "cannot convert member function pointer" error at the shim.

## Common Pitfalls

1. **Using `std::string` / `std::vector` in the C++ implementation** of a bridge
   function. See the type table above. This is the #1 first-project blocker.
2. **Defining a shared struct in your own C++ header** when it is already declared
   in the bridge — redefinition. Reference it, don't define it.
3. **Forgetting to include the cxx-generated header** in a hand-written `.cc` that
   touches a shared struct.
4. **Expecting `Type::new()`** to construct a C++ type. Constructors are not
   mapped; write a factory `fn` returning `UniquePtr<T>`.
5. **Declaring the factory before the class** it returns — `std::unique_ptr<Engine>`
   needs `Engine` at least declared.
6. **Omitting `const` on a C++ method** that the bridge declares with `&self`.
7. **Trying to pass an opaque type by value.** Opaque types exist *because* their
   layout is secret. They must travel behind `&`, `Box`, or `UniquePtr`. cxx will
   reject it at compile time — good, that is the point.
8. **`UniquePtr<T>` where `T` is an opaque *Rust* type.** The restriction is
   asymmetric: `UniquePtr`/`SharedPtr` back onto real C++ smart pointers, so they
   cannot hold an opaque Rust type.
9. **Passing `std::string` by value.** `CxxString` cannot be passed by value — pass
   `&CxxString` or a `&str` and let cxx marshal.
10. **Raw pointer arguments without `unsafe`.** Any function taking `*mut T`/`*const T`
    must be declared `unsafe fn` in the bridge, or it will not compile.
11. **Using `Result<T>` as a parameter.** It is a **return type only** (it maps to
    C++ throw/catch). Errors as arguments will not compile.
12. **Forgetting `#include "rust/cxx.h"` in C++.** The `rust::` namespace types
    (`rust::String`, `rust::Vec`, `rust::Slice`) do not exist without it.
13. **Hand-writing `extern "C"` / `#[no_mangle]` on the implementations.** cxx emits
    the correct shims itself; manual annotations fight the generator.
14. **Omitting `cargo:rerun-if-changed`.** Cargo then does not know your `.cc`/`.h`
    files changed and you get stale objects — looks like the generator ignored your
    edit.
15. **Expecting bindgen's ergonomics.** Signatures are typed twice — once at the impl
    site, once in the bridge. This is deliberate: the repetition is what buys the
    static verification. It is still *less* repetition than bindgen, which forces you
    to re-express every idiomatic C++ API as C-style raw pointers first.
16. **Reaching for `HashMap` across the boundary.** Unimplemented. Pass a `Vec` of
    pairs and build the map on the far side.
17. **C++ exceptions escaping into Rust.** `Result<T>` return maps to throw/catch and
    cxx handles it; an exception thrown from a function *not* declared as returning
    `Result` will terminate rather than propagate.

## Debugging

**Look at the generated code** — cxx ships two generators and both are runnable:

```bash
# Rust-side shims (needs cargo-expand)
$ cargo expand --manifest-path demo/Cargo.toml

# C++-side header + source
$ cargo run --manifest-path bridge/cmd/Cargo.toml -- demo/src/main.rs
```

**Static assertions are your friend.** When cxx reports a signature mismatch, the
mismatch is between what you wrote in the bridge and the real header. Read the
`static_assert` text — it names the field. The usual cause is a `const` or
by-value/by-reference difference.

**"cannot be passed by value" for a struct you believe is shared.** Either it is
declared inside an `extern` block (so it is opaque, not shared) or it transitively
contains a pointer. Move the declaration to the top level of the bridge module.

**Linker error for `cxxbridge1$...`.** The C++ shim was not compiled or not linked.
Check that `build.rs` `.compile("cxxbridge-demo")` ran and that the name matches the
crate. Confirm with `nm -D` / `readelf -d` — see the `ffi-debugging` skill.

**Nothing happens when you change the bridge.** Stale build. `cargo clean -p <crate>`
and confirm your `rerun-if-changed` lines cover every C++ input.

## Source Links

- cxx README (primary — full `#[cxx::bridge]` example, the three-item taxonomy, the
  complete builtin-type restriction table, the build.rs canonical form):
  https://raw.githubusercontent.com/dtolnay/cxx/master/README.md
- Official tutorial, reference and example code: https://cxx.rs
- Working CMake + CXX integration example (both the `build.rs` side and the CMake
  side together): https://github.com/paandahl/cpp-with-rust
- Upstream repo: https://github.com/dtolnay/cxx
- The bindgen by-value-ABI segfault issue cxx works around (worth reading to
  understand *why* the repetition exists): https://github.com/rust-lang/rust-bindgen/issues/778
- `include/cxx.h` in the repo defines the whole C++ `rust::` namespace surface.
