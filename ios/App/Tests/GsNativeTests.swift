import XCTest
@testable import GSApp

#if canImport(GsFfi)
import GsFfi
#endif

/// The iOS side of the native bridge.
///
/// The split matches the Android instrumented test: the properties that must
/// hold on a clean checkout are asserted, and the properties that need a 400 MB
/// model are skipped visibly rather than passed silently.
///
/// A 0.5B GGUF is not committed — no weights in git — so `chat` is exercised
/// only when one is placed in the app's Application Support directory. Everything
/// else, including the failure contract, is always asserted.
final class GsNativeTests: XCTestCase {

    /// The model path, if one has been provisioned. `nil` means the model-backed
    /// tests skip.
    private func deviceModel() -> String? {
        if let env = ProcessInfo.processInfo.environment["GS_TEST_MODEL"],
           FileManager.default.fileExists(atPath: env) {
            return env
        }
        let support = try? FileManager.default.url(
            for: .applicationSupportDirectory, in: .userDomainMask,
            appropriateFor: nil, create: false)
        guard let dir = support else { return nil }
        let candidate = dir.appendingPathComponent("qwen2.5-0.5b-instruct-q4_k_m.gguf")
        return FileManager.default.fileExists(atPath: candidate.path) ? candidate.path : nil
    }

    // MARK: - Always asserted

    /// The framework must be importable, or every other test is vacuous. This is
    /// the test that catches a modulemap that parses but exposes nothing.
    func testFrameworkIsReachableOrAbsentCleanly() {
        // Both branches are valid; what is NOT valid is a crash or an
        // unsatisfiable symbol. Reaching this line at all is the assertion.
        let reason = GsNativeLoader.unavailableReason
        if !GsNativeLoader.isAvailable {
            XCTAssertNotNil(reason, "unavailable but no reason reported")
            XCTAssertFalse(
                (reason?.detail ?? "").isEmpty,
                "a reason must carry text; an empty one hides the diagnosis")
        }
        print("GsNativeLoader: \(GsNativeLoader.describe)")
    }

    /// A failure must be a thrown error, never an empty string.
    ///
    /// This is the property the fallback chain rests on. A caller given `""`
    /// cannot distinguish "the model declined" from "there is no engine in this
    /// build" and renders a blank bubble instead of falling back.
    func testFailureThrowsRatherThanReturningEmptyString() throws {
        guard GsNativeLoader.isAvailable else {
            throw XCTSkip("no engine in this build: \(GsNativeLoader.unavailableReason?.detail ?? "")")
        }
        do {
            let out = try GsNative.chat("hello")
            XCTAssertFalse(out.isEmpty, "chat returned an empty string")
        } catch {
            // The expected path when the context exists but the backend does not.
            let engineError = error as? GsNative.EngineError
            XCTAssertNotNil(engineError, "expected GsNative.EngineError, got \(error)")
            XCTAssertFalse(
                (engineError?.reason ?? "").isEmpty,
                "an EngineError must carry the C-side reason")
        }
    }

    /// The iOS build must have llama.cpp COMPILED IN, and this is the only signal
    /// that says so.
    ///
    /// Why a new test rather than tightening an existing one. Everything else here
    /// that could tell portable from llama needs a loaded context:
    ///
    ///   * ``GsNative.backendAvailable`` is guarded by ``guard let context`` and
    ///     returns false on a correct build that has no model provisioned.
    ///   * ``GsNativeLoader.isAvailable`` reports the FRAMEWORK, and the portable
    ///     framework imports and probes fine.
    ///   * ``initialize(modelPath:)`` needs a GGUF, and no weights are in git.
    ///
    /// So on a clean checkout every one of those is either false or skipping, and
    /// a portable iOS build was indistinguishable from a llama one. Runs
    /// 36671474326 and 36673059524 are the shape of that: green, with every
    /// engine-backed test skipped.
    ///
    /// ``gs_mobile_build_info`` is the exception. It is a compile-time constant:
    ///
    ///     static std::string info = std::string("gs-ffi ") + GS_MOBILE_VERSION;
    ///     #ifdef GS_MOBILE_HAVE_LLAMA
    ///         info += " +llama";
    ///     #else
    ///         info += " portable";
    ///     #endif
    ///
    /// (gs_mobile.cpp:190) -- so it answers with no context, no model and no
    /// network, which is exactly what a CI runner has.
    ///
    /// NOT a skip. `XCTSkip("portable builds are supported")` would pass on both
    /// a portable build and a llama build and so distinguish nothing. The claim
    /// under test is that the iOS build HAS llama, so a portable build must fail
    /// here rather than be excused.
    func testBuildInfoNamesTheLlamaBackend() {
        let info = GsNative.buildInfo
        print("GsNative.buildInfo -> \(info)")

        // Empty is its own failure and has a known cause: the reader used to be
        // gs_last_error(), the FAILURE channel, which returns "" whenever no
        // error is pending -- i.e. on every healthy engine. Fixed in 8fa8792.
        XCTAssertFalse(
            info.isEmpty,
            "gs_mobile_build_info() returned empty. The Swift reader must be the " +
                "build-info accessor, not gs_last_error(), which is the failure " +
                "channel and is empty on a working engine.")
        XCTAssertTrue(
            info.contains("+llama"),
            """
            the iOS build reports no llama backend: "\(info)".
            GS_LLAMA_PREBUILT was not set for this cargo build, so build.rs did not \
            define GS_LLAMA_HAVE_LLAMA and gs-ffi compiled portable. The \
            cross-compile job being green is not sufficient -- it proves the \
            archives were produced, not that the app links them.
            """)
        XCTAssertFalse(
            info.contains("portable"),
            "the iOS build is the portable one, which is the state this branch exists "
                + "to end: \(info)")
        // The version is in the same string, so a stub that returns "+llama" and
        // nothing else cannot pass.
        XCTAssertTrue(
            info.hasPrefix("gs-ffi "),
            "buildInfo should identify the library, got: \(info)")
    }

    /// `shutdown` is idempotent: calling it with nothing loaded must not trap.
    /// A double free in native teardown is a crash, not a Swift error.
    func testShutdownIsIdempotent() {
        GsNative.shutdown()
        GsNative.shutdown()
    }

    /// `embedImage` must reject a buffer that is too short rather than reading
    /// past its end. A native over-read is a memory-safety bug, not a wrong
    /// answer, so this is asserted at the API boundary.
    func testEmbedImageRejectsShortBuffer() throws {
        guard GsNativeLoader.isAvailable else {
            throw XCTSkip("no engine in this build")
        }
        XCTAssertThrowsError(
            try GsNative.embedImage(rgb: [UInt8](repeating: 0, count: 3), w: 64, h: 64),
            "a 3-byte buffer for a 64x64 image must be rejected"
        )
    }

    // MARK: - Skipped without a model

    func testChatReturnsNonEmptyText() throws {
        guard let model = deviceModel() else {
            throw XCTSkip("no model provisioned; set GS_TEST_MODEL or place a GGUF in Application Support")
        }
        XCTAssertTrue(GsNativeLoader.initialize(modelPath: model))
        try XCTSkipUnless(GsNative.backendAvailable, "no generation backend in this build")
        let out = try GsNative.chat("Say hello in one short sentence.")
        XCTAssertFalse(out.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        XCTAssertFalse(
            out.lowercased().contains("unavailable"),
            "returned an unavailability marker instead of text: \(out)")
        print("GsNative.chat -> \(out.prefix(80))")
    }

    func testSamePromptTwiceIsIdentical() throws {
        guard let model = deviceModel() else {
            throw XCTSkip("no model provisioned")
        }
        XCTAssertTrue(GsNativeLoader.initialize(modelPath: model))
        try XCTSkipUnless(GsNative.backendAvailable, "no generation backend in this build")
        // Greedy sampling must be reproducible on a reused context. This is not
        // hypothetical: the C layer once appended a temperature stage to a
        // sampler chain that was never reset, so a reused context disagreed
        // with itself while a fresh one was perfect.
        let a = try GsNative.chat("Name three primary colours.")
        let b = try GsNative.chat("Name three primary colours.")
        XCTAssertEqual(a, b, "the same prompt must give the same answer on a reused context")
    }

    func testOcrReturnsTextOnFixture() throws {
        guard GsNativeLoader.isAvailable else { throw XCTSkip("no engine in this build") }
        try XCTSkipUnless(GsNative.backendAvailable, "no OCR backend in this build")
        let support = try XCTUnwrap(
            FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask,
                                    appropriateFor: nil, create: false))
        let img = support.appendingPathComponent("invoice.png")
        try XCTSkipUnless(FileManager.default.fileExists(atPath: img.path),
                          "no fixture image at \(img.path)")
        let text = try GsNative.runOcr(img.path)
        XCTAssertFalse(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        XCTAssertFalse(text.lowercased().contains("unavailable"),
                       "returned an unavailability marker: \(text)")
    }
}
