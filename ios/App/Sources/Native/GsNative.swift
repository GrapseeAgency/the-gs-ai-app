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
    public static var buildInfo: String {
        guard let p = gs_last_error() else { return "" }
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
        defer { gs_llama_free_text(out) }
        return String(cString: out)
    }

    /// OCR an image file. Throws rather than returning `""` on failure.
    public static func runOcr(_ imagePath: String) throws -> String {
        let ctx = try requireContext("runOcr")
        let out = imagePath.withCString { gs_mobile_ocr(ctx, $0) }
        guard let out else { throw makeError("runOcr") }
        defer { gs_llama_free_text(out) }
        return String(cString: out)
    }

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
