import Foundation

#if canImport(GsFfi)
import GsFfi
#endif

/// The native engine, as a Swift surface. Declarations and thin C-interop only:
/// the engine itself is Rust and C++, reached through the `GsFfi` xcframework.
///
/// Three rules, enforced on the Rust side and relied on here:
///
///  1. **Failure is a thrown error, never an empty string.** A caller given `""`
///     cannot tell "the model declined" from "there is no engine in this build",
///     and will render a blank bubble instead of falling back.
///  2. **The context is process-wide.** `initialize` is cheap after the first
///     call and the weights stay resident; a phone app is one user.
///  3. **Nothing here may be called on the main thread.** A generation takes
///     seconds. The callers in this app are `async`.
public final class GsNative {

    private static var context: OpaquePointer?
    private static let lock = NSLock()

    /// A failure from the native engine, carrying the C-side reason.
    ///
    /// `gs_last_error()` is the single shared error channel, so the message here
    /// is the same text the C ABI would have returned. Losing it would turn every
    /// diagnosis into "it didn't work".
    public struct EngineError: Error, CustomStringConvertible {
        public let operation: String
        public let reason: String

        public var description: String { "GsNative.\(operation): \(reason)" }
    }

    // MARK: - Lifecycle

    /// Load the model at `modelPath` into the process-wide context.
    ///
    /// - Returns: true when a context exists. It can be true while
    ///   ``backendAvailable`` is false: the portable build links cleanly and
    ///   simply has no generation backend, and reporting that as a load failure
    ///   would disable the bridge for a library that loaded perfectly well.
    @discardableResult
    public static func initialize(modelPath: String) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        guard context == nil else { return true }
        guard let cPath = strdup(modelPath) else { return false }
        defer { free(cPath) }
        let ctx = gs_mobile_create(cPath, 2048, 4)
        guard let ctx else {
            throwPending(operation: "initialize")
            return false
        }
        context = ctx
        return true
    }

    /// 1 when a generation backend is compiled into this build.
    public static var backendAvailable: Bool {
        lock.lock()
        defer { lock.unlock() }
        guard let context else { return false }
        return gs_mobile_backend_available(context) != 0
    }

    /// Build identification, for matching a crash report to a commit.
    ///
    /// `gs_mobile_build_info`, NOT `gs_last_error`. This read the ERROR channel
    /// as though it were a build identifier:
    ///
    ///     guard let p = gs_last_error() else { return "" }
    ///
    /// which returns "" whenever no error is pending, which is the normal case.
    /// So buildInfo was empty on a healthy engine, and GsNativeLoader.probe()
    /// then reported
    ///
    ///     "framework imported but no build info"
    ///
    /// and took the `libraryMissing` branch -- on run 36671474326, with the
    /// framework linked correctly and the app running. Every engine-backed test
    /// skipped as a result, and the two that could run passed anyway, so a green
    /// job was reporting an engine that reported itself missing.
    ///
    /// gs_last_error() carries the last FAILURE's message and is the right thing
    /// for makeError, which is what it was presumably copied from. The ABI has a
    /// purpose-built accessor for this and it is in the header the xcframework
    /// publishes: `const char* gs_mobile_build_info(void)`, returning e.g.
    /// "gs-ffi 0.1.0 +llama" -- the same string the Android side prints in
    /// selfCheck.
    public static var buildInfo: String {
        guard let p = gs_mobile_build_info() else { return "" }
        return String(cString: p)
    }

    /// Release the process-wide context. Safe to call when nothing was loaded.
    public static func shutdown() {
        lock.lock()
        defer { lock.unlock() }
        guard let ctx = context else { return }
        gs_mobile_free(ctx)
        context = nil
    }

    // MARK: - Inference

    /// Generate a completion with the default token budget.
    /// - Throws: ``EngineError`` with the native reason.
    public static func chat(_ prompt: String) throws -> String {
        try chat(prompt, maxTokens: 256)
    }

    /// Generate a completion with an explicit token budget.
    ///
    /// A 4096-token reply is unreadable in a chat bubble and will still be
    /// streaming when the user has scrolled away.
    public static func chat(_ prompt: String, maxTokens: Int32) throws -> String {
        let ctx = try requireContext("chat")
        let out = prompt.withCString { gs_mobile_chat(ctx, $0, maxTokens, 0.2) }
        guard let out else { throw makeError("chat") }
        // gs_free_string, NOT gs_llama_free_text.
        //
        // This never compiled until the xcframework was actually linked
        // (run 36662206901, first build of this app ever):
        //     GsNative.swift:99:17: error: cannot find 'gs_llama_free_text' in scope
        //
        // gs_llama_free_text exists -- but it is an INTERNAL C++ symbol declared
        // in native/cpp/llama_wrapper/llama_wrapper.h:125, defined in batch.cpp,
        // and it is not part of the mobile C ABI. The xcframework ships exactly
        // two headers, gs_abi.h and gs_mobile.h, and neither mentions it, so it
        // was never reachable from Swift. Reaching into the wrapper's internals
        // from the iOS bridge would couple the app to a file the module does not
        // publish.
        //
        // gs_free_string is the ABI's own convention, documented at gs_abi.h:53:
        //   "Generic owned-string release. Every gs_free_* in other headers
        //    forwards here so callers only need one free convention."
        // So this is the intended call, not a workaround.
        defer { gs_free_string(out) }
        return String(cString: out)
    }

    /// OCR an image file. Throws rather than returning `""` on failure.
    ///
    /// Routed to Apple Vision, not to `gs_mobile_ocr`. The C ABI has no OCR
    /// backend compiled into an iOS build -- Tesseract was never cross-compiled
    /// for it, and Decision 2 replaced that plan with Vision -- so calling the C
    /// entry point here would return a GS_ERR_UNAVAILABLE string on every
    /// image, which is exactly the "empty string instead of an error" failure
    /// this whole surface is built to avoid.
    ///
    /// Note that this does NOT require a native context. Vision is an OS
    /// framework, so OCR works in a build with no engine at all; the two
    /// capabilities are genuinely independent and conflating them would mean
    /// reporting "no local AI" on a device whose OCR is fine.
    public static func runOcr(_ imagePath: String) throws -> String {
        try AppleVisionOcr.recogniseText(atPath: imagePath)
    }

    /// OCR raw image bytes, for callers that hold the data rather than a path.
    public static func runOcr(data: Data) throws -> String {
        try AppleVisionOcr.recogniseText(data: data)
    }

    /// Which OCR engine is in use. Reported rather than assumed, because
    /// "which engine ran" is a question a support answer needs.
    public static var ocrEngine: String { "Apple Vision (VNRecognizeTextRequest)" }

    /// Embed a text query. Throws rather than returning an empty vector.
    public static func embedText(_ text: String) throws -> [Float] {
        let ctx = try requireContext("embedText")
        let dim = gs_mobile_embed_dim(ctx)
        guard dim > 0 else { throw makeError("embedText") }
        var out = [Float](repeating: 0, count: Int(dim))
        let n = text.withCString { p in
            gs_mobile_embed_text(ctx, p, &out, dim)
        }
        guard n > 0 else { throw makeError("embedText") }
        return out
    }

    /// Embed raw RGB pixels, three bytes per pixel, row-major.
    ///
    /// The native side checks `rgb.count` against `w * h * 3` and throws on a
    /// mismatch rather than reading past the end of the buffer.
    public static func embedImage(rgb: [UInt8], w: Int32, h: Int32) throws -> [Float] {
        let ctx = try requireContext("embedImage")
        guard w > 0, h > 0, rgb.count >= Int(w) * Int(h) * 3 else {
            throw EngineError(operation: "embedImage",
                              reason: "expected \(Int(w) * Int(h) * 3) bytes, got \(rgb.count)")
        }
        let dim = gs_mobile_embed_dim(ctx)
        guard dim > 0 else { throw makeError("embedImage") }
        var out = [Float](repeating: 0, count: Int(dim))
        let n = rgb.withUnsafeBufferPointer { p in
            gs_mobile_embed_image(ctx, p.baseAddress, w, h, &out, dim)
        }
        guard n > 0 else { throw makeError("embedImage") }
        return out
    }

    // MARK: - Internals

    private static func requireContext(_ op: String) throws -> OpaquePointer {
        lock.lock()
        defer { lock.unlock() }
        guard let ctx = context else {
            throw EngineError(
                operation: op,
                reason: "no native context; call initialize(modelPath:) first")
        }
        return ctx
    }

    private static func makeError(_ op: String) -> EngineError {
        var reason = "no detail reported"
        if let p = gs_last_error() {
            reason = String(cString: p)
        }
        return EngineError(operation: op, reason: reason)
    }

    private static func throwPending(operation: String) {
        if let p = gs_last_error(), String(cString: p).isEmpty == false {
            NSLog("GsNative: %@ failed: %s", operation, String(cString: p))
        }
    }
}
