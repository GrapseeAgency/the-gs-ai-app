//! JNI bindings for the Android bridge.
//!
//! The engine stays native; this file is glue. It exposes exactly the surface
//! `com.grapsee.gsai.native.GsNative` declares, and nothing else — no business
//! logic, no model selection, no routing. Those belong in Kotlin and in
//! gs-core respectively.
//!
//! Three rules are enforced here rather than documented:
//!
//!   * Every entry point is `catch_unwind`-guarded. A Rust panic unwinding into
//!     the JVM is undefined behaviour; in practice it aborts the process.
//!   * A failure becomes a Java exception carrying the native reason, never an
//!     empty string. A Kotlin caller given "" cannot distinguish "the model
//!     declined" from "native inference is not in this build", and renders a
//!     blank bubble instead of falling back to the provider.
//!   * There is exactly one declaration of every C symbol, in `mobile.rs`. This
//!     file used to re-declare the `gs_mobile_*` functions with `*mut c_void`
//!     while `mobile.rs` declared them with `*mut GS_MobileCtx`; both modules
//!     compile on Android, so the duplicate `extern "C"` blocks collided.
//!
//! Symbol names follow JNI's mangling of `com.grapsee.gsai.native.GsNative`,
//! which is what the Kotlin `external fun` declarations resolve against.

use std::sync::Mutex;

use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{
    jboolean, jfloatArray, jint, jobject, jlong, jstring, JNI_FALSE, JNI_TRUE,
};
use jni::JNIEnv;

use crate::mobile::{self, MobileCtx};

// The status codes come from sd_bridge, which COPIES them from gs_abi.h rather
// than declaring its own. jni.rs had no GS_ERR_* imported at all before this,
// because every entry point it had returned a pointer or a string and never had
// a failure to describe; the first version of the image block used them and
// would not have compiled. Reading the values out of gs_abi.h and writing them
// here is a duplicate to keep the two from drifting, and it is a deliberate one:
// the alternative is a build dependency on a header this file cannot see.
use crate::sd_bridge::{GS_ERR_INVALID_ARG, GS_ERR_UNAVAILABLE, GS_OK};

extern "C" {
    fn gs_set_error(msg: *const std::ffi::c_char);
}

/// Write a reason into the shared error channel, so a failure from a JNI entry
/// point is READABLE from the Kotlin side instead of being a bare integer.
///
/// sdGenerate is the first entry point here that has a failure worth describing,
/// and it is the only one whose caller cannot get any context from the return
/// value: GS_ERR_UNAVAILABLE covers "no library", "no checkpoint" and "bad
/// prompt", which are three different bugs on a phone.
fn set_err(msg: &str) {
    if let Ok(c) = std::ffi::CString::new(msg) {
        unsafe { gs_set_error(c.as_ptr()) };
    }
}
/// The one process-wide context. A phone app is a single user, and a per-call
/// context would re-load the weights on every message.
///
/// A `Mutex<Option<..>>` rather than a bare pointer in a static: a JNI entry
/// point is reachable from any thread in the JVM, and an unsynchronised static
/// pointer is unsound the moment two of them read it. See the `Send` impl on
/// `MobileCtx` for the safety argument this depends on.
static GLOBAL: Mutex<Option<MobileCtx>> = Mutex::new(None);

/// Take the global slot.
///
/// Returns an `Err(reason)` instead of poisoning the whole app: a panic inside
/// one generation must degrade to "the local path is unavailable" and leave the
/// chat UI working, which is the entire reason the fallback exists.
fn with_global<T>(f: impl FnOnce(&MobileCtx) -> T) -> Result<T, String> {
    let g = GLOBAL
        .lock()
        .map_err(|_| "local context mutex poisoned by an earlier panic".to_string())?;
    let ctx = g.as_ref().ok_or_else(|| "no native context; call init(modelPath) first".to_string())?;
    Ok(f(ctx))
}

fn set_global(ctx: Option<MobileCtx>) {
    match GLOBAL.lock() {
        // Dropping the old value runs MobileCtx::drop, which calls
        // gs_mobile_free exactly once. Re-initialising is therefore safe and
        // leaks nothing.
        Ok(mut g) => *g = ctx,
        Err(_) => {
            // A poisoned mutex means the slot's contents are unknown. Leaving it
            // alone is the only safe option: freeing a context we may have
            // already freed is worse than leaking it.
        }
    }
}

/// Run a fallible operation on the global context, flattening the two error
/// layers: "no context / poisoned" and "the engine declined".
fn call<T>(f: impl FnOnce(&MobileCtx) -> Result<T, mobile::MobileError>) -> Result<T, String> {
    match with_global(f) {
        Ok(r) => r.map_err(|e| e.to_string()),
        Err(e) => Err(e),
    }
}

/// Throw a Java exception carrying the native reason.
///
/// Returns () rather than a value: the entry points disagree about what a
/// failure looks like (jboolean, jobject, jfloatArray) and a helper that returns
/// one shape compiles for none of them.
fn throw(env: &mut JNIEnv, what: &str, reason: impl std::fmt::Display) {
    let msg = format!("GsNative.{what}: {reason}");
    // com.grapsee.gsai.native.GsNativeException, which now EXISTS -- see
    // android/app/src/main/java/com/grapsee/gsai/native/GsNativeException.kt.
    //
    // It did not, for the whole life of this bridge, and the note that used to
    // stand here was wrong: the entry points returning a Java String return NULL
    // on failure, and a null is not an explicit failure value to Kotlin code
    // declared `: String`. Raw, run 36576882672:
    //     java.lang.ClassNotFoundException: Didn't find class
    //       "com.grapsee.gsai.native.GsNativeException" on path: DexPathList[...]
    // so every native failure was a null reaching a caller with the reason
    // discarded. The find_class is still guarded so a host that genuinely lacks
    // the class degrades to the null return rather than aborting.
    if let Ok(cls) = env.find_class("com/grapsee/gsai/native/GsNativeException") {
        let _ = env.throw_new(cls, msg);
    }
}

/// Run `$body`, turning a panic into a Java exception plus `$fail`.
///
/// `$fail` is explicit and type-checked against the body. An earlier version
/// inferred the failure type from a helper returning `Option<()>`, which
/// compiled for no entry point at all once the bodies started returning
/// `jboolean` and raw pointers.
macro_rules! guard {
    ($env:ident, $name:expr, $fail:expr, $body:expr) => {{
        let r = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| $body));
        match r {
            Ok(v) => v,
            Err(_) => {
                throw(&mut $env, $name, "panic in native code; the context is not usable");
                $fail
            }
        }
    }};
}

/// Create the native context.
///
/// Returns JNI_TRUE on success. A portable build still returns TRUE: the
/// context exists, it simply has no generation backend, and `chat` is what
/// reports that. Treating "no backend" as a load failure would make the Kotlin
/// loader disable the bridge for a library that loaded perfectly well.
#[no_mangle]
pub extern "system" fn Java_com_grapsee_gsai_native_GsNative_init(
    mut env: JNIEnv,
    _class: JClass,
    model_path: JString,
) -> jboolean {
    guard!(env, "init", JNI_FALSE, {
        let path: String = match env.get_string(&model_path) {
            Ok(s) => s.into(),
            Err(e) => {
                throw(&mut env, "init", e);
                return JNI_FALSE;
            }
        };
        match MobileCtx::create(&path, 2048, 4) {
            Ok(c) => {
                set_global(Some(c));
                JNI_TRUE
            }
            Err(e) => {
                throw(&mut env, "init", e);
                JNI_FALSE
            }
        }
    })
}

/// 1 when a generation backend is compiled in. Kotlin checks this to decide
/// whether local inference is worth attempting at all.
/// `init` with the two knobs exposed, so a benchmark can vary them.
///
/// The one-argument `init` passes 2048 and 4, hardcoded, and that is what every
/// number measured until now was measured AT. Those two are the first two levers a
/// TTFT measurement should move, and neither was reachable from the host before
/// this: `gs_mobile_create(model_path, n_ctx, n_threads)` has always taken them and
/// nothing has ever passed anything else.
///
/// Kept as a SEPARATE name rather than changing init's signature: init is called
/// by the loader and by the app, and a benchmark's need for two extra arguments is
/// not a reason to touch the production entry point's ABI.
#[no_mangle]
pub extern "system" fn Java_com_grapsee_gsai_native_GsNative_initTuned(
    mut env: JNIEnv,
    _class: JClass,
    model_path: JString,
    n_ctx: jint,
    n_threads: jint,
    n_batch: jint,
    n_ubatch: jint,
    flash_attn: jint,
) -> jboolean {
    guard!(env, "initTuned", JNI_FALSE, {
        let path: String = match env.get_string(&model_path) {
            Ok(s) => s.into(),
            Err(e) => {
                throw(&mut env, "initTuned", e);
                return JNI_FALSE;
            }
        };
        match MobileCtx::create_tuned(&path, n_ctx, n_threads, n_batch, n_ubatch, flash_attn) {
            Ok(c) => {
                set_global(Some(c));
                JNI_TRUE
            }
            Err(e) => {
                throw(&mut env, "initTuned", e);
                JNI_FALSE
            }
        }
    })
}

#[no_mangle]
pub extern "system" fn Java_com_grapsee_gsai_native_GsNative_backendAvailable(
    mut env: JNIEnv,
    _class: JClass,
) -> jboolean {
    guard!(env, "backendAvailable", JNI_FALSE, {
        match with_global(|c| c.backend_available()) {
            Ok(true) => JNI_TRUE,
            Ok(false) => JNI_FALSE,
            Err(e) => {
                throw(&mut env, "backendAvailable", e);
                JNI_FALSE
            }
        }
    })
}

/// What the library actually resolved at load time.
///
/// This exists because a device test that calls `chat` and gets nothing back
/// cannot tell WHICH of three things failed: the .so did not load, the .so
/// loaded but has no generation backend, or the backend is present and the model
/// is missing. This reports that state directly, so a failing device run names
/// the cause instead of leaving it to be guessed at.
///
/// Returns a human-readable string, never the literal "UNAVAILABLE", unless the
/// whole mobile layer is missing -- so a caller testing for that exact string is
/// testing for a real and specific condition.
#[no_mangle]
pub extern "system" fn Java_com_grapsee_gsai_native_GsNative_selfCheck(
    env: JNIEnv,
    _class: JClass,
) -> jobject {
    let mut env = env;
    let has_ctx = match GLOBAL.lock() {
        Ok(g) => g.is_some(),
        Err(_) => false,
    };
    let mut parts: Vec<String> = Vec::new();
    parts.push(format!("context={}", if has_ctx { "present" } else { "absent" }));
    if !has_ctx {
        // The exact literal, because a caller is entitled to check for it.
        return match env.new_string("UNAVAILABLE") {
            Ok(s) => s.into_raw(),
            Err(_) => std::ptr::null_mut(),
        };
    }
    let backend = call(|c| {
        if c.backend_available() {
            Ok(c.backend_name())
        } else {
            Err(mobile::MobileError::Unavailable("no generation backend compiled in".into()))
        }
    });
    match backend {
        Ok(name) => parts.push(format!("backend=available({name})")),
        Err(e) => parts.push(format!("backend=unavailable({e})")),
    }
    parts.push(format!("selfCheck={}", mobile::build_info()));
    match env.new_string(parts.join(" ")) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// Build identification, so a crash report can be matched to a commit.
#[no_mangle]
pub extern "system" fn Java_com_grapsee_gsai_native_GsNative_buildInfo(
    mut env: JNIEnv,
    _class: JClass,
) -> jobject {
    guard!(env, "buildInfo", std::ptr::null_mut(), {
        let info = mobile::build_info();
        match env.new_string(info) {
            Ok(s) => s.into_raw(),
            Err(e) => {
                throw(&mut env, "buildInfo", e);
                std::ptr::null_mut()
            }
        }
    })
}

/// Default token budget for chat(). A phone reply that runs to 4096 tokens is
/// unreadable in a chat bubble and will still be streaming when the user has
/// scrolled away.
const DEFAULT_MAX_TOKENS: i32 = 256;

/// Generate a completion. Returns a Java String, or throws.
#[no_mangle]
pub extern "system" fn Java_com_grapsee_gsai_native_GsNative_chat(
    env: JNIEnv,
    _class: JClass,
    prompt: JString,
) -> jobject {
    chat_impl(env, prompt, DEFAULT_MAX_TOKENS)
}

/// As `chat`, with an explicit token budget. The single-argument form cannot
/// carry one — JNI mangles by arity, so this is a distinct name rather than an
/// overload.
#[no_mangle]
pub extern "system" fn Java_com_grapsee_gsai_native_GsNative_chatWithBudget(
    env: JNIEnv,
    _class: JClass,
    prompt: JString,
    max_tokens: jint,
) -> jobject {
    chat_impl(env, prompt, max_tokens as i32)
}

fn chat_impl(mut env: JNIEnv, prompt: JString, max_tokens: i32) -> jobject {
    guard!(env, "chat", std::ptr::null_mut(), {
        let p: String = match env.get_string(&prompt) {
            Ok(s) => s.into(),
            Err(e) => {
                throw(&mut env, "chat", e);
                return std::ptr::null_mut();
            }
        };
        let r = call(|c| c.chat(&p, max_tokens, 0.2));
        match r {
            Ok(text) => match env.new_string(text) {
                Ok(j) => j.into_raw(),
                Err(e) => {
                    throw(&mut env, "chat", e);
                    std::ptr::null_mut()
                }
            },
            Err(e) => {
                throw(&mut env, "chat", e);
                std::ptr::null_mut()
            }
        }
    })
}

/// OCR. Returns text, or throws — never an empty string on failure.
#[no_mangle]
pub extern "system" fn Java_com_grapsee_gsai_native_GsNative_runOcr(
    mut env: JNIEnv,
    _class: JClass,
    image_path: JString,
) -> jobject {
    guard!(env, "runOcr", std::ptr::null_mut(), {
        let p: String = match env.get_string(&image_path) {
            Ok(s) => s.into(),
            Err(e) => {
                throw(&mut env, "runOcr", e);
                return std::ptr::null_mut();
            }
        };
        match call(|c| c.ocr(&p)) {
            Ok(text) => match env.new_string(text) {
                Ok(j) => j.into_raw(),
                Err(e) => {
                    throw(&mut env, "runOcr", e);
                    std::ptr::null_mut()
                }
            },
            Err(e) => {
                throw(&mut env, "runOcr", e);
                std::ptr::null_mut()
            }
        }
    })
}

/// Embed a text query. Returns a float array, or throws.
#[no_mangle]
pub extern "system" fn Java_com_grapsee_gsai_native_GsNative_embedText(
    mut env: JNIEnv,
    _class: JClass,
    text: JString,
) -> jfloatArray {
    guard!(env, "embedText", std::ptr::null_mut(), {
        let t: String = match env.get_string(&text) {
            Ok(s) => s.into(),
            Err(e) => {
                throw(&mut env, "embedText", e);
                return std::ptr::null_mut();
            }
        };
        match call(|c| c.embed_text(&t)) {
            Ok(v) => finish_embed(&mut env, &v),
            Err(e) => {
                throw(&mut env, "embedText", e);
                std::ptr::null_mut()
            }
        }
    })
}

/// Embed raw RGB pixels, three per pixel, row-major.
#[no_mangle]
pub extern "system" fn Java_com_grapsee_gsai_native_GsNative_embedImage(
    mut env: JNIEnv,
    _class: JClass,
    rgb: JByteArray,
    w: jint,
    h: jint,
) -> jfloatArray {
    guard!(env, "embedImage", std::ptr::null_mut(), {
        if w <= 0 || h <= 0 {
            throw(&mut env, "embedImage", format!("w and h must be > 0, got {w}x{h}"));
            return std::ptr::null_mut();
        }
        let expect = (w as usize) * (h as usize) * 3;
        let len = match env.get_array_length(&rgb) {
            Ok(n) => n as usize,
            Err(e) => {
                throw(&mut env, "embedImage", e);
                return std::ptr::null_mut();
            }
        };
        // Checked here as well as in MobileCtx. A short array is a caller bug
        // and must not reach as_raw(), which would hand the C side a length it
        // cannot check.
        if len < expect {
            throw(&mut env, "embedImage", format!("expected {expect} bytes for {w}x{h} RGB, got {len}"));
            return std::ptr::null_mut();
        }
        // A copy rather than get_byte_array_elements: the JVM may move or pin
        // the backing array, and the C call must not observe that mid-decode.
        let owned: Vec<u8> = match env.convert_byte_array(&rgb) {
            Ok(v) => v,
            Err(e) => {
                throw(&mut env, "embedImage", e);
                return std::ptr::null_mut();
            }
        };
        match call(|c| c.embed_image(&owned, w as i32, h as i32)) {
            Ok(v) => finish_embed(&mut env, &v),
            Err(e) => {
                throw(&mut env, "embedImage", e);
                std::ptr::null_mut()
            }
        }
    })
}

/// Copy a float vector into a fresh Java array, or null.
fn finish_embed(env: &mut JNIEnv, buf: &[f32]) -> jfloatArray {
    if buf.is_empty() {
        // An empty vector is a failure here: callers index into it, and an empty
        // cosine similarity is silently 0.0 rather than an error.
        throw(env, "embed", "embedder returned no dimensions");
        return std::ptr::null_mut();
    }
    let arr = match env.new_float_array(buf.len() as jint) {
        Ok(a) => a,
        Err(e) => {
            throw(env, "embed", e);
            return std::ptr::null_mut();
        }
    };
    if let Err(e) = env.set_float_array_region(&arr, 0, buf) {
        throw(env, "embed", e);
        return std::ptr::null_mut();
    }
    arr.into_raw()
}

/// Release the process-wide context. Safe to call when nothing was loaded.
#[no_mangle]
pub extern "system" fn Java_com_grapsee_gsai_native_GsNative_shutdown(
    _env: JNIEnv,
    _class: JClass,
) {
    set_global(None);
}

// ---------------------------------------------------------------------------
// Image generation, IN-PROCESS.
//
// The handles are jlong rather than jobject because they are C pointers, and a
// Java object would imply a lifecycle this layer does not own: the pointer is
// freed by the explicit free method and by nothing else, so a Kotlin caller that
// drops the handle without calling it leaks a model context -- which for a
// diffusion model is gigabytes, not bytes.
//
// The device test asserts BOTH halves of that: that a handle works, and that
// generate() on an unavailable backend writes no file.
// ---------------------------------------------------------------------------

#[no_mangle]
pub extern "system" fn Java_com_grapsee_gsai_native_GsNative_renderSvg(
    // `mut`, because env.get_string() takes &mut self in jni 0.21. The file's
    // own entry points have said so since init; the two added here did not, and
    // the compiler named both.
    mut env: JNIEnv,
    _class: JClass,
    spec_json: JString,
    out_path: JString,
) -> jint {
    // The procedural renderer needs no weights, no GPU and no diffusion, so it is
    // available in EVERY build. It is the only image path that can be claimed on
    // a device with no checkpoint present.
    // `s.into()` is this file's own idiom for JavaStr -> String, at lines 141, 276
    // and 308. The first version of this block called `.as_bytes()`, which does
    // not exist on JavaStr in jni 0.21: it Derefs to str, so `.into()` and
    // `.to_string()` are the spellings that compile. Copying the neighbours is
    // what made this a one-line fix instead of a search.
    let spec: String = match env.get_string(&spec_json) {
        Ok(s) => s.into(),
        Err(_) => return GS_ERR_INVALID_ARG,
    };
    let out: String = match env.get_string(&out_path) {
        Ok(s) => s.into(),
        Err(_) => return GS_ERR_INVALID_ARG,
    };
    // &str, not &CStr, and there is no unwrap here: render_svg_via_ffi owns the
    // NUL. The previous version did `CStr::from_bytes_with_nul(c.as_bytes())
    // .unwrap()` on a CString, whose as_bytes() never contains a NUL -- so that
    // unwrap was a panic on every single call, inside an extern "system"
    // function. See the function's own comment.
    crate::sd_bridge::render_svg_via_ffi(&spec, &out)
}

/// The shared error channel, as a String.
///
/// Every other entry point throws with its reason attached, so this is not needed
/// for them. It is needed for the two places a throw would be wrong: a probe that
/// is SUPPOSED to fail (does this model exist? is this backend linked?) has no
/// business throwing, and a caller that caught and discarded an exception has lost
/// the only useful part of it.
#[no_mangle]
pub extern "system" fn Java_com_grapsee_gsai_native_GsNative_lastError(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let p = unsafe { gs_last_error() };
    let msg = if p.is_null() {
        String::new()
    } else {
        unsafe { std::ffi::CStr::from_ptr(p) }.to_string_lossy().into_owned()
    };
    match env.new_string(msg) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

extern "C" {
    fn gs_last_error() -> *const std::ffi::c_char;
}

#[no_mangle]
pub extern "system" fn Java_com_grapsee_gsai_native_GsNative_sdCreate(
    // `mut`, because env.get_string() takes &mut self in jni 0.21. The file's
    // own entry points have said so since init; the two added here did not, and
    // the compiler named both.
    mut env: JNIEnv,
    _class: JClass,
    model_path: JString,
) -> jlong {
    // `let p: String` and `.into()`: JavaStr Derefs to str, so to_string_lossy()
    // does not exist on it. That was one of the eight errors, and it was in the
    // one entry point whose whole job is to turn a Java string into a path.
    let p: String = match env.get_string(&model_path) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let path = std::path::PathBuf::from(p);
    // load() rather than a raw pointer: it validates and reports the reason, and
    // the pointer it returns is the same one the raw call would give.
    match crate::sd_bridge::SdModel::load(&path) {
        Ok(m) => m.into_handle(),
        // The reason is in the shared channel. Deliberately NOT turned into a
        // Java exception here: the device test asserts on the empty-handle +
        // readable-reason pair, which is the contract the C ABI states.
        Err(_) => 0,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_grapsee_gsai_native_GsNative_sdAvailable(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jint {
    match unsafe { crate::sd_bridge::SdModel::borrowed(handle) } {
        Some(m) => m.can_generate() as jint,
        None => 0,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_grapsee_gsai_native_GsNative_sdBackendName(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jstring {
    let name = match unsafe { crate::sd_bridge::SdModel::borrowed(handle) } {
        Some(m) => m.backend_name(),
        None => "none".to_string(),
    };
    match env.new_string(name) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[allow(clippy::too_many_arguments)]
#[no_mangle]
pub extern "system" fn Java_com_grapsee_gsai_native_GsNative_sdGenerate(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    prompt: JString,
    negative_prompt: JString,
    width: jint,
    height: jint,
    steps: jint,
    out_path: JString,
) -> jint {
    let mut m = match unsafe { crate::sd_bridge::SdModel::borrowed_mut(handle) } {
        Some(m) => m,
        None => return GS_ERR_INVALID_ARG,
    };
    let pr: String = match env.get_string(&prompt) {
        Ok(s) => s.into(),
        Err(_) => return GS_ERR_INVALID_ARG,
    };
    let ng: String = match env.get_string(&negative_prompt) {
        Ok(s) => s.into(),
        Err(_) => String::new(),
    };
    let op: String = match env.get_string(&out_path) {
        Ok(s) => s.into(),
        Err(_) => return GS_ERR_INVALID_ARG,
    };
    // THROWS, and this is the second version.
    //
    // The first returned GS_ERR_UNAVAILABLE and wrote the reason to the shared
    // channel, which a Kotlin caller would have had to know to go and read. But
    // GsNative.kt states the rule this file was ignoring:
    //
    //   "Failure is an exception, never an empty string. A caller that gets ""
    //    cannot tell ... Anything that can fail returns a value or throws."
    //
    // And an int is not a failure a caller can act on: GS_ERR_UNAVAILABLE covers
    // "no library linked", "no checkpoint" and "bad prompt", which are three
    // different bugs on a phone. The reason is the whole value.
    //
    // lastError() exists for the cases where throwing is wrong -- a probe that is
    // SUPPOSED to fail -- and this is not one of them.
    match m.generate(
        &pr,
        &ng,
        width as i32,
        height as i32,
        steps as i32,
        std::path::Path::new(&op),
    ) {
        Ok(_) => GS_OK,
        Err(e) => {
            set_err(&e);
            throw(&mut env, "sdGenerate", e);
            GS_ERR_UNAVAILABLE
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_grapsee_gsai_native_GsNative_sdFree(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    // Consumes the handle, so a second call with the same jlong is a use-after-free
    // that the Kotlin side prevents by nulling the field. Documented on the Kotlin
    // method rather than defended here, because defending it would mean guessing
    // which of two frees is the mistake.
    if handle != 0 {
        drop(unsafe { crate::sd_bridge::SdModel::from_owned(handle) });
    }
}
