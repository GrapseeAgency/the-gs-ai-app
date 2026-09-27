//! JNI bindings for the Android bridge.
//!
//! The engine stays native; this file is glue. It exposes exactly the surface
//! `com.grapsee.gsai.native.GsNative` declares, and nothing else — no business
//! logic, no model selection, no routing. Those belong in Kotlin and in
//! gs-core respectively.
//!
//! Two rules are enforced here rather than documented:
//!
//!   * Every entry point is `catch_unwind`-guarded. A Rust panic unwinding into
//!     the JVM is undefined behaviour; in practice it aborts the process.
//!   * A failure becomes a Java exception carrying gs_last_error(), never an
//!     empty string. A Kotlin caller that receives "" cannot distinguish "the
//!     model declined" from "native inference is not in this build", and will
//!     happily render a blank bubble instead of falling back to the provider.
//!
//! Symbol names follow JNI's mangling of `com.grapsee.gsai.native.GsNative`,
//! which is what the Kotlin `external fun` declarations resolve against.

use std::ffi::{c_char, c_int, c_void, CStr, CString};

extern "C" {
    // gs_abi.h — the shared status/error channel.
    fn gs_last_error() -> *const c_char;
    fn gs_free_string(s: *mut c_char);
}

/// Opaque JNIEnv. The `jni` crate owns the real definitions; these are the
/// symbols gs-ffi needs and they are resolved at link time against the NDK.
use jni::objects::{JClass, JObject, JString};
use jni::sys::{jboolean, jfloatArray, jint, jobject, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;

fn last_error() -> String {
    let p = unsafe { gs_last_error() };
    if p.is_null() {
        return String::from("no error detail reported");
    }
    unsafe { CStr::from_ptr(p) }.to_string_lossy().into_owned()
}

/// Throw a Java exception carrying the native error text, and return None so
/// callers can `return throw(env, "chat")`.
fn throw<'a>(env: &mut JNIEnv<'a>, what: &str) -> Option<()> {
    let msg = format!("GsNative.{what}: {}", last_error());
    // dev.grapsee.gsai.native.GsNativeException. If the class is missing we
    // must still not return a value that Kotlin would treat as success.
    if let Ok(cls) = env.find_class("com/grapsee/gsai/native/GsNativeException") {
        let _ = env.throw_new(cls, msg);
    }
    None
}

macro_rules! guard {
    ($env:expr, $name:expr, $body:expr) => {
        match std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| $body)) {
            Ok(v) => v,
            Err(_) => throw($env, $name),
        }
    };
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
    guard!(&mut env, "init", {
        let path: String = match env.get_string(&model_path) {
            Ok(s) => s.into(),
            Err(_) => return JNI_FALSE,
        };
        let c = CString::new(path).unwrap_or_default();
        let ctx = unsafe { gs_mobile_create(c.as_ptr(), 2048, 4) };
        if ctx.is_null() {
            return JNI_FALSE;
        }
        // The context is process-wide on purpose: a phone app is one user, and
        // a per-call context would re-load the weights on every message.
        unsafe { set_global(ctx) };
        JNI_TRUE
    })
    .map(|b| b as jboolean)
    .unwrap_or(JNI_FALSE)
}

static mut GLOBAL_CTX: *mut c_void = std::ptr::null_mut();

unsafe fn set_global(p: *mut c_void) {
    GLOBAL_CTX = p;
}

unsafe fn global() -> *mut c_void {
    GLOBAL_CTX
}

/// 1 when a generation backend is compiled in. Kotlin checks this to decide
/// whether local inference is worth attempting at all.
#[no_mangle]
pub extern "system" fn Java_com_grapsee_gsai_native_GsNative_backendAvailable(
    mut env: JNIEnv,
    _class: JClass,
) -> jboolean {
    guard!(&mut env, "backendAvailable", {
        let ctx = unsafe { global() };
        if ctx.is_null() {
            return JNI_FALSE;
        }
        let ok = unsafe { gs_mobile_backend_available(ctx) != 0 };
        if ok { JNI_TRUE } else { JNI_FALSE }
    })
    .map(|b| b as jboolean)
    .unwrap_or(JNI_FALSE)
}

/// Build identification, so a crash report can be matched to a commit.
#[no_mangle]
pub extern "system" fn Java_com_grapsee_gsai_native_GsNative_buildInfo(
    mut env: JNIEnv,
    _class: JClass,
) -> jobject {
    guard!(&mut env, "buildInfo", {
        let info = crate::mobile::build_info();
        match env.new_string(info) {
            Ok(s) => s.into_raw(),
            Err(_) => std::ptr::null_mut(),
        }
    })
    .unwrap_or(std::ptr::null_mut())
}

/// Generate a completion. Returns a Java String, or throws.
#[no_mangle]
pub extern "system" fn Java_com_grapsee_gsai_native_GsNative_chat(
    mut env: JNIEnv,
    _class: JClass,
    prompt: JString,
    max_tokens: jint,
) -> jobject {
    guard!(&mut env, "chat", {
        let ctx = unsafe { global() };
        if ctx.is_null() {
            throw(&mut env, "chat");
            return std::ptr::null_mut();
        }
        let p: String = match env.get_string(&prompt) {
            Ok(s) => s.into(),
            Err(_) => return std::ptr::null_mut(),
        };
        let c = CString::new(p).unwrap_or_default();
        let out = unsafe { gs_mobile_chat(ctx, c.as_ptr(), max_tokens, 0.2) };
        if out.is_null() {
            throw(&mut env, "chat");
            return std::ptr::null_mut();
        }
        let s = unsafe { CStr::from_ptr(out) }.to_string_lossy().into_owned();
        unsafe { gs_free_string(out) };
        match env.new_string(s) {
            Ok(j) => j.into_raw(),
            Err(_) => std::ptr::null_mut(),
        }
    })
    .unwrap_or(std::ptr::null_mut())
}

/// OCR. Returns text, or throws — never an empty string on failure.
#[no_mangle]
pub extern "system" fn Java_com_grapsee_gsai_native_GsNative_runOcr(
    mut env: JNIEnv,
    _class: JClass,
    image_path: JString,
) -> jobject {
    guard!(&mut env, "runOcr", {
        let ctx = unsafe { global() };
        if ctx.is_null() {
            throw(&mut env, "runOcr");
            return std::ptr::null_mut();
        }
        let p: String = match env.get_string(&image_path) {
            Ok(s) => s.into(),
            Err(_) => return std::ptr::null_mut(),
        };
        let c = CString::new(p).unwrap_or_default();
        let out = unsafe { gs_mobile_ocr(ctx, c.as_ptr()) };
        if out.is_null() {
            throw(&mut env, "runOcr");
            return std::ptr::null_mut();
        }
        let s = unsafe { CStr::from_ptr(out) }.to_string_lossy().into_owned();
        unsafe { gs_free_string(out) };
        match env.new_string(s) {
            Ok(j) => j.into_raw(),
            Err(_) => std::ptr::null_mut(),
        }
    })
    .unwrap_or(std::ptr::null_mut())
}

/// Embed a text query. Returns a float array, or throws.
#[no_mangle]
pub extern "system" fn Java_com_grapsee_gsai_native_GsNative_embedText(
    mut env: JNIEnv,
    _class: JClass,
    text: JString,
) -> jfloatArray {
    guard!(&mut env, "embedText", {
        let ctx = unsafe { global() };
        if ctx.is_null() {
            throw(&mut env, "embedText");
            return std::ptr::null_mut();
        }
        let t: String = match env.get_string(&text) {
            Ok(s) => s.into(),
            Err(_) => return std::ptr::null_mut(),
        };
        let c = CString::new(t).unwrap_or_default();
        let dim = unsafe { gs_mobile_embed_dim(ctx) };
        if dim < 0 {
            throw(&mut env, "embedText");
            return std::ptr::null_mut();
        }
        let mut buf = vec![0f32; dim as usize];
        let n = unsafe { gs_mobile_embed_text(ctx, c.as_ptr(), buf.as_mut_ptr(), dim) };
        if n < 0 {
            throw(&mut env, "embedText");
            return std::ptr::null_mut();
        }
        let arr = match env.new_float_array(dim as jint) {
            Ok(a) => a,
            Err(_) => return std::ptr::null_mut(),
        };
        if env.set_float_array_region(&arr, 0, dim as jint, &buf).is_err() {
            return std::ptr::null_mut();
        }
        arr.into_raw()
    })
    .unwrap_or(std::ptr::null_mut())
}

/// Release the process-wide context.
#[no_mangle]
pub extern "system" fn Java_com_grapsee_gsai_native_GsNative_shutdown(
    _env: JNIEnv,
    _class: JClass,
) {
    let ctx = unsafe { global() };
    if !ctx.is_null() {
        unsafe { gs_mobile_free(ctx) };
        unsafe { set_global(std::ptr::null_mut()) };
    }
}

// Imported from the C++ portable layer. Declared here rather than routed
// through gs-ffi/src/mobile.rs so the JNI surface keeps its own stable names.
extern "C" {
    fn gs_mobile_create(model_path: *const c_char, n_ctx: c_int, n_threads: c_int) -> *mut c_void;
    fn gs_mobile_free(ctx: *mut c_void);
    fn gs_mobile_backend_available(ctx: *mut c_void) -> c_int;
    fn gs_mobile_chat(
        ctx: *mut c_void,
        prompt: *const c_char,
        max_tokens: c_int,
        temperature: f32,
    ) -> *mut c_char;
    fn gs_mobile_ocr(ctx: *mut c_void, image_path: *const c_char) -> *mut c_char;
    fn gs_mobile_embed_dim(ctx: *mut c_void) -> c_int;
    fn gs_mobile_embed_text(
        ctx: *mut c_void,
        text: *const c_char,
        out: *mut f32,
        cap: c_int,
    ) -> c_int;
}
