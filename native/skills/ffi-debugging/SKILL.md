---
name: ffi-debugging
description: |-
  Debug native linkage and symbol resolution with ELF/binutils — `readelf`, `nm`, `objdump`, `ldd`,
  `LD_DEBUG`, `dlopen`/`dlsym` failures, undefined references, ABI mismatches and segfaults at a
  boundary. TRIGGER — read BEFORE guessing at a native link or load failure — whenever the task
  involves: undefined reference, unresolved symbol, `cannot find -l`, `symbol lookup error`,
  `dlopen` returns NULL, `dlsym` NULL, `undefined symbol:`, wrong-ABI crash, struct layout
  mismatch across FFI, vtable mismatch, `LD_DEBUG`, `.so` dependency inspection, checking whether a
  function is actually exported from a shared library, or a segfault whose faulting address is small
  (e.g. 0x11) which indicates a pointer/value or vtable-offset error rather than a null deref.
---

# FFI debugging: ELF, symbols, linkage

## Purpose

Native failures are not mysterious — they are *observable*. The ELF format records
exactly which symbols a library exports, which it needs, and how it was linked. This
skill is the command set for reading that record instead of guessing.

**The core discipline: establish whether a symbol is actually present before you
debug the code that calls it.** Most "my FFI is broken" reports are a symbol that was
never exported, a wrong search path, or a struct that was passed by value across an ABI
that does not agree.

## Quick Reference

### The five commands, and what each answers

| Command | Question it answers |
|---|---|
| `readelf -d <f>` | What does the dynamic section say — needed libs, SONAME, RPATH/RUNPATH? |
| `nm -D <f>` | Which dynamic symbols are **defined** here vs required (**U**)? |
| `objdump -T <f>` | Dynamic symbol table with visibility + weak/undefined marking |
| `ldd <f>` | Which libraries does the loader actually resolve, and what is missing? |
| `readelf -h` / `-s` | Header (arch, class) and the *full* symbol table incl. local symbols |

`-D` means **dynamic** (the exported surface). Without it, `nm` shows the full
symbol table including local/static symbols and `.o` internals — useful for `.o`
files, misleading for `.so`.

### The four standard questions

```bash
# 1. Is this symbol EXPORTED from the library?     (is it there at all?)
nm -D --defined-only libfoo.so | grep my_function

# 2. What does this library REQUIRE?               (what is missing?)
nm -D --undefined-only libfoo.so

# 3. Which arch / class is this artefact?          (wrong-arch load failure)
readelf -h libfoo.so | grep -E 'Class|Machine|Type'

# 4. Does the loader find its dependencies?        (search-path failure)
ldd libfoo.so
readelf -d libfoo.so | grep -E 'NEEDED|RPATH|RUNPATH'
```

### Reading `nm` output

```
0000000000001234 T my_function     # T = global text (exported code)
0000000000001240 t static_helper   # t = local text (NOT exported)
0000000000005678 U printf          # U = undefined (required from elsewhere)
00000000000099a0 B my_global       # B = uninitialised data (.bss)
00000000000099b0 D my_global_init  # D = initialised data
                 w weak_symbol     # w = weak
                 V vtable_entry    # V/V = weak object (vtables, typeinfo)
```

**If your function is `t` (lowercase) instead of `T`, it is not exported.** That is
the single most common cause of a `dlsym` returning NULL. A C function needs
`__attribute__((visibility("default")))` and must not be `static`; a C++ function
needs `extern "C"` (see pitfall 4).

## Working Example

### The `dlopen`/`dlsym` path — a plugin backend

This is the shape used by a runtime that loads backends (llama.cpp, a vendor SDK)
at runtime rather than link time. Debug it in this order.

```c
#include <dlfcn.h>
#include <stdio.h>

typedef int (*init_fn)(void);

void *h = dlopen("libllama.so", RTLD_NOW | RTLD_LOCAL);
if (!h) {
    // dlerror() is the ONLY source of the real reason. It is overwritten by
    // subsequent dl* calls, so capture it immediately.
    fprintf(stderr, "BLOCKED dlopen: %s\n", dlerror());
    return 1;
}

dlerror();                       // clear any stale error
init_fn f = (init_fn)dlsym(h, "llama_backend_init");
const char *err = dlerror();
if (err) {
    fprintf(stderr, "BLOCKED dlsym: %s\n", err);
    dlclose(h);
    return 1;
}
f();
```

**Two rules that prevent most `dlopen` debugging pain:**

1. **Capture `dlerror()` immediately.** Any intervening `dl*` call clobbers it.
2. **`RTLD_NOW`, not `RTLD_LAZY`.** `RTLD_NOW` resolves every relocation at load and
   fails loudly with a symbol name. `RTLD_LAZY` defers and gives you a crash at an
   arbitrary later call site with no message. For diagnostics, `RTLD_NOW` is
   strictly better. Use `RTLD_LOCAL` unless you intend to share symbols.

### The Rust side — `libloading`

```rust
use std::os::raw::{c_char, c_int, c_void};

type LlamaBackendInit = unsafe extern "C" fn() -> c_int;

unsafe {
    let lib = libloading::Library::<libllama::Llama>::new("libllama.so")
        .map_err(|e| format!("BLOCKED load: {e}"))?;

    // Must be unsafe extern "C" — a plain extern "C" fn is a different ABI
    // contract and miscompiles on some ABIs.
    let init: Symbol<LlamaBackendInit> = lib.get(b"llama_backend_init\0")
        .map_err(|e| format!("BLOCKED sym: {e}"))?;
    init();
}
```

Note the **`\0` terminator** in the byte-string symbol name. Omitting it works on
some platforms and fails on others — an excellent source of intermittent bugs.

### Undefined reference at link time — the debug sequence

```bash
# 1. Does the archive actually contain the symbol?
nm libfoo.a 2>/dev/null | grep my_function

# 2. Is the search path reaching it?
echo "$LIBRARY_PATH"; echo "$RUSTFLAGS"

# 3. What is rustc actually asking the linker for?
cargo build -v 2>&1 | grep -E '\-L|\-l'

# 4. Which library is missing the symbol at runtime?
ldd target/debug/myapp | grep 'not found'
```

### Wrong architecture

```bash
file libfoo.so                 # quick
readelf -h libfoo.so | grep Machine
```

`ELF 64-bit LSB shared object, x86-64` vs `aarch64` — a mismatch here produces
`cannot open shared object file` on device with no useful detail, or a crash in the
first instruction.

## Common Pitfalls

1. **Confusing "not exported" with "not linked".** `nm -D` on the *providing* library
   settles it. If the symbol is `t` or absent, the fix is in that library's build
   flags, not in your link line.
2. **`static` function, or a C++ function without `extern "C"`.** C++ mangles names
   (`foo() -> _Z3foov`). `dlsym(h, "foo")` returns NULL while
   `dlsym(h, "_Z3foov")` works — a terrible thing to ship. Always `extern "C"` at an
   FFI boundary, and check the mangled name in `nm -D` if you are stuck.
3. **Forgetting the NUL terminator** in a `dlsym` name. Use `b"sym\0"` in Rust.
4. **Defaulting to `RTLD_LAZY`.** You convert a clear load-time error into an
   anonymous crash. Use `RTLD_NOW` until you have a reason not to.
5. **Losing the `dlerror()` string** by calling something else first. Capture into a
   local immediately.
6. **Assuming RPATH is consulted for transitive deps.** `DT_RPATH` is; `DT_RUNPATH`
   (the modern default) is **not** inherited by dependencies. A library that loads
   fine standalone can fail when pulled in by a host whose own RUNPATH lacks the
   directory. Check both with `readelf -d`.
7. **Passing a C++ object by value across the ABI.** A struct with identical fields
   and alignment on both sides is still a different ABI when passed by value, on some
   ABIs — a long-standing bindgen bug class that produces segfaults in
   correct-looking code. Return structs indirectly. This is exactly why the CXX
   bridge inserts a zero-cost workaround automatically — see `cxx-bridge-patterns`.
8. **Vtable passed as a pointer when the contract says by value** (or vice-versa).
   A faulting address in the low bytes — `0x11`, `0x8`, a small offset — means the
   CPU jumped through a small integer used as a function pointer. That is a vtable
   or struct-layout contract error, **not** a null deref. Pin the contract with a test.
9. **Mixing `libstdc++` and `libc++`** in one process, or linking `libc++_shared`
   when something else statically linked `libc++`. Duplicate `__cxa_*` / typeinfo
   symbols, and `typeinfo for X` comparison failures that look like logic bugs.
10. **Assuming `ldd` output is authoritative.** It is a *simulation* using the
    loader's rules; a library that resolves under `ldd` can still fail in a container
    or on device. `logcat -s linker:*` is the real oracle on Android.
11. **Reading `nm` output on a stripped library** and concluding the symbols are
    gone. Stripped dynamic symbols remain in `.dynsym` — use `nm -D`. `nm` without
    `-D` on a stripped `.so` shows almost nothing, which reads as "not exported" when
    it is a tooling mistake.
12. **Trusting a resolved symbol as proof the signature is right.** Symbols carry no
    type information. A signature mismatch produces a working link and a runtime
    corruption. Guard the contract with a test, not with `nm`.

## Debugging

**Turn the loader into a narrator.** This is the highest-value command in the whole
skill:

```bash
LD_DEBUG=libs      ./myapp 2>&1 | head -50   # every library search + decision
LD_DEBUG=bindings  ./myapp 2>&1 | head -50   # every symbol relocation
LD_DEBUG=symbols   ./myapp 2>&1 | head -50   # version/symbol lookup
```

Every `search path=...` and `trying file=...` line is the loader telling you exactly
where it looked. Also available: `LD_BIND_NOW=1` (force eager binding on an already
`RTLD_LAZY` library), `LD_DEBUG_OUTPUT=<file>` (write to a file instead of stderr —
avoids interleaving with your own logging), `LD_TRACE_LOADED_OBJECTS=1`.

**Get a real backtrace with symbols, not addresses.** Unstripped first:

```bash
gdb -batch -ex run -ex bt -ex 'info sharedlibrary' ./myapp
```

Stripped binary — grab the unstripped one from the build tree, or use the
separate debug file:

```bash
eu-unstrip -n ./myapp -o ./myapp.debug
```

A backtrace of bare addresses like `0x5555...` tells you nothing. A backtrace with
function names tells you immediately which side of the boundary you are on. If the
top frame is a mangled C++ symbol you did not write, the crash is inside the
dependency.

**Sanitise the boundary.** ASan catches use-after-free and overflow at the exact
allocation, and it crosses into `dlopen`'d libraries in the same process:

```bash
CXXFLAGS="-fsanitize=address -fno-omit-frame-pointer" \
LDFLAGS="-fsanitize=address" cmake -B build-asan -DCMAKE_BUILD_TYPE=Debug
```

For C++ specifically, also consider `-fsanitize=undefined` for the alignment and
typeinfo problems behind symptoms 8 and 9 above.

**One-line triage of a shared object:**

```bash
readelf -hd libfoo.so | head -30 && \
  echo "--- undefined ---" && nm -D --undefined-only libfoo.so | head -20 && \
  echo "--- deps ---" && ldd libfoo.so
```

That gives class, arch, dynamic tags, outstanding requirements and resolved
dependencies in one shot. Ninety percent of linkage questions are answered by it.

**On Android, always check logcat** — the platform linker reports rejected
dependencies there and nowhere else:

```bash
adb logcat -s linker:*
```

## Source Links

**Note on provenance:** the canonical *ELF Analysis Static Recipe* command list
(covering `readelf -d`, `nm -D`, `objdump -T`, `ldd`) and the *binaries debugging*
recipe were supplied as references for this skill without resolvable deep URLs. The
commands documented above are the standard, stable binutils/glibc surface and are
verified against local toolchain behaviour; the links below are the authoritative
upstream documentation for the same tool set. Command semantics for these tools have
been stable for decades, so this is a low-drift reference — but verify against
`man` on the target machine if you are on an unusual libc.

- Binutils manual — authoritative reference for `nm`, `objdump`, `readelf`,
  `ld`, `strip`: https://sourceware.org/binutils/docs/binutils/
- `nm` manual: https://sourceware.org/binutils/docs/binutils/nm.html
- `readelf` manual: https://sourceware.org/binutils/docs/binutils/readelf.html
- `objdump` manual: https://sourceware.org/binutils/docs/binutils/objdump.html
- ELF format specification (gABI) — the actual normative source for `DT_NEEDED`,
  `DT_RPATH`/`DT_RUNPATH`, `.dynsym`, visibility:
  https://refspecs.linuxfoundation.org/elf/gabi4+/ch4.ehdr.html
- glibc dynamic linker and loader tunables (`LD_DEBUG`, `LD_LIBRARY_PATH`,
  `LD_PRELOAD`): https://man7.org/linux/man-pages/man8/ld.so.8.html
- `dlopen`/`dlsym`/`dlerror` semantics: https://man7.org/linux/man-pages/man3/dlopen.3.html
- Kdab — linker error and undefined-reference debugging guides (practical
  walkthroughs; search the site for the current article path):
  https://www.kdab.com
- Android dynamic linker behaviour and `logcat` tags:
  https://developer.android.com/ndk/guides/abis
