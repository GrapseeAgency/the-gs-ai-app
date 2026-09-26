---
name: android-ndk-cross
description: |-
  Cross-compile Rust and C/C++ to Android ABIs with cargo-ndk and the NDK toolchain.
  TRIGGER — read BEFORE writing any Android build config — whenever the task involves: cargo ndk,
  `aarch64-linux-android`, armeabi-v7a, jniLibs, ANDROID_NDK_HOME, NDK, `rustup target add`, JNI,
  cross-compiling to Android, `.so` for Android, arm64-v8a, building a native lib for an Android app,
  or a CI job that produces Android binaries. Covers target triples, linker configuration, jniLibs
  output layout, running tests via adb, and the missing-compiler-builtins / libc++_shared failures.
  Pair with `cargo-workspace-sys` when the crate also compiles a C/C++ dependency.
---

# Android NDK cross-compilation (cargo-ndk)

## Purpose

`cargo-ndk` is a cargo subcommand that configures the full cross-compilation
environment — linkers, sysroot, ar/ranlib wrappers, API level — and lays the
resulting `.so` files into the directory layout Android expects. It exists so you do
not hand-maintain a matrix of `CARGO_TARGET_*_LINKER` variables.

**Current version: cargo-ndk 4.1.2** (docs dated 2026-09-10). MSRV 1.86.
Supported hosts: Linux, macOS (x86_64 + arm64), Windows.

## Quick Reference

### Three subcommands

| Command | Purpose |
|---|---|
| `cargo ndk` | passthrough for `cargo` with the NDK env applied |
| `cargo ndk-test` | run tests on a connected device via `adb` |
| `cargo ndk-env` | print env exports — for rust-analyzer / VS Code / shell |

### Install toolchains first — mandatory

```bash
rustup target add \
    aarch64-linux-android \
    armv7-linux-androideabi \
    x86_64-linux-android \
    i686-linux-android
```

Then the tool:

```bash
cargo binstall cargo-ndk     # fast, recommended for CI
cargo install cargo-ndk      # from source
```

### ⚠️ Check WHICH cargo you are on before anything else

A system-wide Rust (`/usr/bin/cargo`, common on Arch/Debian) is **not managed by
rustup**, and installing rustup does not take it over. Check before anything else:

| `which cargo` | Meaning |
|---|---|
| `~/.cargo/bin/cargo` | rustup-managed — `rustup target add` applies |
| `/usr/bin/cargo` | **system Rust** — `rustup target add` has no effect on it |

With the system Rust, `rustup target add aarch64-linux-android` can report success
while builds keep using the system toolchain and fail with `can't find crate for
'core'`, or silently produce a host binary. Both installs coexist; only `PATH` order
decides the winner, so make the export **persistent in your shell profile** — a
per-session export silently reverts in the next shell:

```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --default-toolchain stable --profile minimal
export PATH="$HOME/.cargo/bin:$PATH"     # add to ~/.bashrc / ~/.zshrc
which cargo                              # must print /home/<you>/.cargo/bin/cargo
```

Then confirm rustup is actually in charge:

```bash
rustup target list | grep aarch64-linux-android   # android target is *available*
```

Availability proves rustup governs the toolchain. It does **not** prove the NDK
exists — the sysroot, linker and `adb` are separate prerequisites, and without them
this skill stays BLOCKED no matter what rustup says.

### The build command

```bash
cargo ndk -t armeabi-v7a -t arm64-v8a -o ./jniLibs build --release
```

- `-t` — target. Accepts **ABI names** (`arm64-v8a`, `armeabi-v7a`, `x86_64`,
  `x86`) *and* ordinary triples.
- `-o` — output dir, populated in the layout Android expects.
- everything after — passthrough to `cargo` (`build`, `--release`, `-v`).
- `-v` / `-vv` — verbosity, as usual, after the cargo subcommand.

### ABI name ↔ Rust triple

| ABI (`-t`) | Rust triple | Bits |
|---|---|---|
| `arm64-v8a` | `aarch64-linux-android` | 64 |
| `armeabi-v7a` | `armv7-linux-androideabi` | 32 |
| `x86_64` | `x86_64-linux-android` | 64 |
| `x86` | `i686-linux-android` | 32 |

This mapping is not derivable by string substitution — `x86` → `i686-linux-android`
and `arm64-v8a` → `aarch64-linux-android` both surprise people. Note also the
`eabi` suffix on the 32-bit ARM target; omitting it is not a valid target name.

### Configuration precedence

`ANDROID_NDK_HOME` overrides auto-detection. If the NDK is at the Android Studio
default location it is found automatically and the most recent version is used.

`CARGO_NDK_`-prefixed env vars set defaults (all overridable by flags):

| Variable | Meaning |
|---|---|
| `CARGO_NDK_TARGET` | default target(s), comma-separated |
| `CARGO_NDK_PLATFORM` | default API platform level |
| `CARGO_NDK_OUTPUT_PATH` | default output directory |

### Exported variables (read these in your own `build.rs`)

| Variable | Example | Notes |
|---|---|---|
| `CARGO_NDK_ANDROID_PLATFORM` | `21` | API number as int |
| `CARGO_NDK_NDK_VERSION` | `28.0.12433566` | detected NDK revision |
| `CARGO_NDK_OUTPUT_PATH` | `./jniLibs` | from `-o` |
| `CARGO_NDK_SYSROOT_PATH` | `/path/to/ndk/toolchains/llvm/prebuilt/...` | |
| `CARGO_NDK_SYSROOT_TARGET` | `aarch64-linux-android` | name *inside* the sysroot |
| `CARGO_NDK_SYSROOT_LIBS_PATH` | `.../usr/lib/aarch64-linux-android` | sysroot lib dir |
| `LIBCLANG_PATH` | `.../lib` | present when libclang detected |
| `ANDROID_PLATFORM` | `21` | platform version |
| `ANDROID_ABI` | `armeabi-v7a` | ABI name |

bindgen's environment is configured and exported automatically when it is present.

**Note the two distinct "target" notions**: `CARGO_NDK_SYSROOT_TARGET` is the
LLVM/sysroot directory name, and it *differs from the Rust triple* in some cases.
When your `build.rs` constructs a sysroot path by hand, use `CARGO_NDK_SYSROOT_LIBS_PATH`
directly rather than interpolating a triple — that is exactly the class of bug that
produces a path that exists for `x86_64` and not for `i686`.

### C dependencies

`cargo-ndk` derives env vars **the same way the `cc` crate does** — a `cc`-based C
dependency in your `build.rs` needs no special handling.

## Working Example

### `.cargo/config.toml` — run on device via adb

```toml
[target.aarch64-linux-android]
runner = "cargo ndk-runner"

[target.armv7-linux-androideabi]
runner = "cargo ndk-runner"
```

Repeat per target. With this, plain `cargo run --target aarch64-linux-android`
pushes the binary to a connected device and executes it.

### Tests on a real device

```bash
cargo ndk-test -t armeabi-v7a
```

This drives `cargo ndk-runner` under the hood to push and run in the Android shell.

### Inspect the environment

```bash
source <(cargo ndk-env)                              # bash / zsh
cargo ndk-env --powershell | Invoke-Expression       # PowerShell
cargo ndk-env --json                                 # rust-analyzer / JSON consumers
```

By default `cargo ndk-env` **omits cargo-ndk's internal linker-wrapper variables**.
If you want to source the output and then invoke cargo directly yourself, you need
`--include-internal` so the wrapper gets its required metadata. Do it in a
subshell — the `_CARGO_NDK_*` variables confuse a later `cargo ndk` in the same shell:

```bash
(
  source <(cargo ndk-env --target arm64-v8a --include-internal)
  cargo build --target aarch64-linux-android
)
```

### Two flags that fix common failures

```bash
# "some compiler builtins are missing"
cargo ndk --link-builtins build --release

# need libc++_shared.so (typical for C++ deps)
cargo ndk --link-libcxx-shared build --release
```

## Common Pitfalls

1. **Running the system Rust instead of the rustup one.** `which cargo` printing
   `/usr/bin/cargo` means `rustup target add` does nothing for you, and it can report
   success anyway. Always confirm `~/.cargo/bin/cargo` first. See the check above.
2. **Forgetting `rustup target add`.** `cargo-ndk` cannot install toolchains for
   you; without the target you get `can't find crate for 'core'`. It also cannot
   install the NDK, a linker, or a sysroot.
3. **Confusing the ABI name with the triple.** `-t arm64-v8a` and
   `--target aarch64-linux-android` are the *same target expressed two ways*; do not
   pass an ABI where a triple is expected or vice-versa in the wrong flag slot.
4. **Dropping the `eabi` on 32-bit ARM.** `armv7-linux-android` is not a target.
5. **Omitting `--link-libcxx-shared`** when any C++ dependency uses the NDK's
   `libc++_shared.so`. Static-linking `libc++` alongside the shared one gives
   duplicate-symbol or `__cxa_*` crashes at runtime, not at link time.
6. **Not passing `--link-builtins`** when the native code needs compiler-rt
   builtins. Symptom is undefined references to `__aeabi_*` / `__udivdi3` at link.
7. **Sourcing `cargo ndk-env` into your interactive shell** and then running
   `cargo ndk` later. Leftover `_CARGO_NDK_*` variables change the linker
   behaviour underneath you. Use a subshell.
8. **Forgetting `--include-internal`** and then wondering why the exported linker
   config does not work when you run cargo yourself.
9. **Hand-interpolating a triple into a sysroot path** instead of using
   `CARGO_NDK_SYSROOT_LIBS_PATH`. See the note above.
10. **Forgetting `-o`.** Without it the `.so` files land in
    `target/<triple>/release/` and never reach `jniLibs` — the build "succeeds" and
    the app loads nothing.
11. **Assuming a `.so` built for the host ABI runs on the device.** An `x86_64` host
    is not the `x86_64` device ABI in any way that matters here — always cross-build.
12. **Building 32-bit ABIs the app cannot use.** Check `abiFilters` in Gradle and build
    only what you ship.
13. **Expecting `cargo ndk` to work on Android/Termux.** Requires
    `CARGO_NDK_ON_ANDROID` at build time and is explicitly **not supported**.

## Debugging

**Confirm the toolchain is present and the target is installed:**

```bash
rustup target list --installed
echo "$ANDROID_NDK_HOME"
ls "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/"
ls "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/" | grep -E 'clang|ld.lld'
```

**Dump the exact environment cargo-ndk will use** — fastest way to spot a wrong path:

```bash
cargo ndk-env --target arm64-v8a
cargo ndk -t arm64-v8a -v build --release 2>&1 | head -50
```

**Verify the output layout.** This is the step people skip:

```bash
find ./jniLibs -name '*.so' -exec sh -c \
  'echo "$1"; file "$1"' _ {} \;
```

Each `.so` must report the architecture you asked for. A host-arch binary in
`jniLibs` is a silent, on-device-only crash.

**If the Rust builds but the C++ does not**, the failure is in your `build.rs`, not
in cargo-ndk. The `-sys` crate needs the exported variables above — see
`cargo-workspace-sys`. Confirm what the build script actually saw by printing
`CARGO_NDK_SYSROOT_LIBS_PATH` and `TARGET` from inside it.

**Device-side failure with a good build:** check the linker on the device.

```bash
adb shell getprop ro.product.cpu.abilist   # which ABIs the device accepts
adb logcat -s linker:*                      # undefined-symbol rejections at load
```

`logcat -s linker:*` is the single most useful command here — a missing
`libc++_shared.so` or an unresolved symbol is reported there and nowhere else.

**Static analysis of the produced `.so`** (symbol visibility, needed deps, arch):
see the `ffi-debugging` skill.

## Source Links

- cargo-ndk repository and README (primary — install, examples, env var tables,
  troubleshooting, supported hosts):
  https://github.com/bbqsrc/cargo-ndk
- cargo-ndk crate docs, v4.1.2 (authoritative module list: `cargo`, `cli`, `meta`,
  `shell`): https://docs.rs/cargo-ndk/latest/cargo_ndk/
- Official Rust cross-compilation guide (Android section — manual
  `CARGO_TARGET_*_LINKER` setup, useful for understanding what cargo-ndk automates):
  https://rust-lang.github.io/rustup/cross-compilation.html
- Oxidized NDK (ONDK) — NDK repackaged with a Rust toolchain, shows the
  `CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER` variable and the `rustup toolchain link`
  approach: https://github.com/riolee/ndk_rust
- Pitfall reference — a real `Cross.toml` with `CARGO_TARGET_*_LINKER` and
  `rustflags` patterns for Android targets:
  https://github.com/hyperledger-indy/indy-vdr/blob/main/Cross.toml
- Android NDK guide (Gradle `externalNativeBuild` wiring):
  https://developer.android.com/ndk/guides/cmake
