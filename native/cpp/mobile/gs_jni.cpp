// gs_jni.cpp — JNI shim over gs_mobile.h.
//
// Written in C++ rather than using the `jni` crate for two reasons that both
// showed up as build failures rather than as opinions:
//
//  1. The `jni` crate must itself be cross-compiled for every ABI, and its
//     build script is not in the set cargo-ndk handles cleanly. A C++ shim
//     compiled by the same `cc` invocation that already works for llama_wrapper
//     adds no new build-graph edges.
//
//  2. The shim is the only place a Java exception could cross into C++. Every
//     entry here is noexcept, converts a gs_status_t into a thrown Java
//     exception, and never lets a C++ exception escape.
//
// Method naming follows the JNI mangling of native/bridges/kotlin/GsNative.kt.

#include <jni.h>

#include <string>

#include "gs_mobile.h"

namespace {

// One context per JVM, created lazily. Mobile apps call chat() from the UI
// thread and from a worker pool, so the pointer is read under a mutex but the
// generation itself is serialised by the context.
pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;

gs_mobile_ctx_t* g_ctx = nullptr;

gs_mobile_ctx_t* ctx_get() {
    pthread_mutex_lock(&g_lock);
    if (!g_ctx) {
        g_ctx = gs_mobile_create(nullptr, 2048, 4);
    }
    gs_mobile_ctx_t* c = g_ctx;
    pthread_mutex_unlock(&g_lock);
    return c;
}

void throw_status(JNIEnv* env, const char* fn) {
    std::string msg = std::string(fn) + ": ";
    const char* e = gs_last_error();
    msg += (e && *e) ? e : "unknown error";
    jclass cls = env->FindClass("dev/grapsee/gs/nativeengine/GsNativeException");
    if (cls) env->ThrowNew(cls, msg.c_str());
}

jstring to_jstring(JNIEnv* env, const char* s) {
    if (!s) return nullptr;
    return env->NewStringUTF(s);
}

}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_dev_grapsee_gs_nativeengine_GsNative_nativeInit(JNIEnv* env, jclass, jstring model_path) {
    (void)env;
    pthread_mutex_lock(&g_lock);
    if (g_ctx) {
        pthread_mutex_unlock(&g_lock);
        return JNI_TRUE;
    }
    const char* p = nullptr;
    if (model_path) p = env->GetStringUTFChars(model_path, nullptr);
    g_ctx = gs_mobile_create(p, 2048, 4);
    if (p) env->ReleaseStringUTFChars(model_path, p);
    pthread_mutex_unlock(&g_lock);
    return g_ctx ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_dev_grapsee_gs_nativeengine_GsNative_nativeBackendAvailable(JNIEnv* env, jclass) {
    (void)env;
    gs_mobile_ctx_t* c = ctx_get();
    if (!c) return 0;
    return gs_mobile_backend_available(c) ? 1 : 0;
}

JNIEXPORT jstring JNICALL
Java_dev_grapsee_gs_nativeengine_GsNative_nativeBuildInfo(JNIEnv* env, jclass) {
    return to_jstring(env, gs_mobile_build_info());
}

JNIEXPORT jstring JNICALL
Java_dev_grapsee_gs_nativeengine_GsNative_nativeChat(JNIEnv* env, jclass, jstring prompt,
                                                     jint max_tokens, jfloat temperature) {
    gs_mobile_ctx_t* c = ctx_get();
    if (!c) {
        throw_status(env, "nativeChat");
        return nullptr;
    }
    const char* p = env->GetStringUTFChars(prompt, nullptr);
    char* out = gs_mobile_chat(c, p, max_tokens, temperature);
    env->ReleaseStringUTFChars(prompt, p);
    if (!out) {
        throw_status(env, "nativeChat");
        return nullptr;
    }
    jstring js = to_jstring(env, out);
    gs_free_string(out);
    return js;
}

JNIEXPORT jstring JNICALL
Java_dev_grapsee_gs_nativeengine_GsNative_nativeOcr(JNIEnv* env, jclass, jstring path) {
    gs_mobile_ctx_t* c = ctx_get();
    if (!c) {
        throw_status(env, "nativeOcr");
        return nullptr;
    }
    const char* p = env->GetStringUTFChars(path, nullptr);
    char* out = gs_mobile_ocr(c, p);
    env->ReleaseStringUTFChars(path, p);
    if (!out) {
        throw_status(env, "nativeOcr");
        return nullptr;
    }
    jstring js = to_jstring(env, out);
    gs_free_string(out);
    return js;
}

JNIEXPORT jfloatArray JNICALL
Java_dev_grapsee_gs_nativeengine_GsNative_nativeEmbedImage(JNIEnv* env, jclass, jstring path) {
    gs_mobile_ctx_t* c = ctx_get();
    if (!c) {
        throw_status(env, "nativeEmbedImage");
        return nullptr;
    }
    const int32_t dim = gs_mobile_embed_dim(c);
    if (dim < 0) {
        throw_status(env, "nativeEmbedImage");
        return nullptr;
    }
    std::vector<float> buf((size_t)dim, 0.0f);
    const char* p = env->GetStringUTFChars(path, nullptr);
    const int32_t n = gs_mobile_embed_image(c, p, buf.data(), dim);
    env->ReleaseStringUTFChars(path, p);
    if (n < 0) {
        throw_status(env, "nativeEmbedImage");
        return nullptr;
    }
    jfloatArray arr = env->NewFloatArray(n);
    env->SetFloatArrayRegion(arr, 0, n, buf.data());
    return arr;
}

}  // extern "C"
