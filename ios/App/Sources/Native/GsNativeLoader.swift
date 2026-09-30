import Foundation

#if canImport(GsFfi)
import GsFfi
#endif

/// Whether the native engine is usable, and why not when it is not.
///
/// The failure is described rather than reduced to a bool because "no engine"
/// has three very different causes and only one of them is a bug:
///
///  * ``GsNativeLoader/Reason/libraryMissing`` — this build shipped no
///    `GsFfi.xcframework`. Supported: the app is fully usable without it.
///  * ``GsNativeLoader/Reason/noModel`` — the engine is present but the user
///    declined the download, or it has not finished. Expected on first launch.
///  * ``GsNativeLoader/Reason/noBackend`` — the portable library links and
///    carries no generation backend. Expected for a build made without one.
///
/// Collapsing these into `false` is how a broken bridge gets reported as "the
/// app cannot do local AI", which is a different and much larger bug report.
public enum GsNativeLoader {

    public enum Reason: Equatable {
        case libraryMissing(String)
        case noModel
        case noBackend

        public var isUsable: Bool {
            if case .noModel = self { return true }   // recoverable
            if case .noBackend = self { return true }  // expected on portable builds
            return false
        }

        public var detail: String {
            switch self {
            case .libraryMissing(let d): return "no GsFfi framework: \(d)"
            case .noModel: return "no model loaded"
            case .noBackend: return "this build has no generation backend"
            }
        }
    }

    private static let stateLock = NSLock()
    nonisolated(unsafe) private static var cached: Result = .untried

    /// Cached probe outcome: `.untried` until the first resolve, then one of
    /// the three answers, cached so a missing framework is not re-probed on
    /// every keystroke.
    ///
    /// `: Equatable` because `isAvailable` compares a `Result` to `.ready`
    /// (run 36662206901, first build of this app ever):
    ///     GsNativeLoader.swift:56:25: error: binary operator '==' cannot be
    ///       applied to two 'GsNativeLoader.Result'
    ///
    /// `Reason` was already `Equatable`, which is all a derived conformance
    /// needs -- the associated value is the only thing that has to compare, and
    /// a `.ready`/`.untried` case has none. Synthesising is the whole fix; hand
    /// writing `==` here would be a second definition to keep in step.
    private enum Result: Equatable {
        case untried
        case ready
        case failed(Reason)
    }

    /// True when the engine is loaded, a model is resident and a generation
    /// backend exists — the whole condition, because a caller asking this is
    /// about to decide where a reply comes from.
    public static var isAvailable: Bool {
        guard resolve() == .ready else { return false }
        return GsNative.backendAvailable && GsNative.loadedModel != nil
    }

    /// Why the engine is unavailable. `nil` when it is available.
    public static var unavailableReason: Reason? {
        switch resolve() {
        case .untried: resolve(); return unavailableReason
        case .ready:
            if !GsNative.backendAvailable { return .noBackend }
            if GsNative.loadedModel == nil { return .noModel }
            return nil
        case .failed(let r): return r
        }
    }

    /// The model this process has loaded, or nil.
    public static var loadedModelPath: String? { GsNative.loadedModel }

    /// True when OCR works, which it does even with no engine loaded.
    ///
    /// Deliberately independent of ``isAvailable``. Vision is an OS framework,
    /// so a build with no native engine can still read text out of an image, and
    /// reporting "on-device AI unavailable" on a device whose OCR is working
    /// would be a false negative that hides a working feature.
    public static var isOcrAvailable: Bool { true }

    /// The OCR engine that will run. Always Apple Vision on iOS.
    public static var ocrEngine: String { GsNative.ocrEngine }

    /// Load the model if a path is known. Safe to call repeatedly.
    @discardableResult
    public static func initialize(modelPath: String) -> Bool {
        _ = resolve()
        guard GsNative.initialize(modelPath: modelPath) else { return false }
        GsNative.loadedModel = modelPath
        return true
    }

    /// Release the context. The framework stays loaded; re-initialising is cheap.
    public static func release() {
        GsNative.shutdown()
        GsNative.loadedModel = nil
    }

    /// For a diagnostics screen.
    public static var describe: String {
        if isAvailable {
            return "native engine ready (\(GsNative.loadedModel ?? "?"))"
        }
        return "native engine unavailable: \(unavailableReason?.detail ?? "unknown")"
    }

    /// Resolve once per process and cache. Retrying a missing framework on every
    /// keystroke would log the same failure forever and bury the real reason.
    private static func resolve() -> Result {
        stateLock.lock()
        defer { stateLock.unlock() }
        if case .untried = cached { cached = probe() }
        return cached
    }

    private static func probe() -> Result {
        // No framework means no engine. That is a supported configuration and
        // not an error: every backend-dependent call in the portable build
        // reports GS_ERR_UNAVAILABLE with a reason.
        #if canImport(GsFfi)
        if GsNative.buildInfo.isEmpty {
            return .failed(.libraryMissing("framework imported but no build info"))
        }
        return .ready
        #else
        return .failed(.libraryMissing("GsFfi not linked into this target"))
        #endif
    }
}

/// The loaded model path, held here rather than on `GsNative` so the engine type
/// stays a pure C-interop surface with no app state on it.
extension GsNative {
    private static let modelLock = NSLock()
    nonisolated(unsafe) static var loadedModel: String?
}
