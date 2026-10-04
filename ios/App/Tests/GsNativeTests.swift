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
    ///
    /// THE FILENAME IS NOT HARDCODED. It used to be:
    ///
    ///     dir.appendingPathComponent("qwen2.5-0.5b-instruct-q4_k_m.gguf")
    ///
    /// while `.github/workflows/ios-native.yml` provisions, into this very
    /// directory:
    ///
    ///     GGUF="$WORK/qwen2.5-0.5b-instruct-q4_0.gguf"
    ///     cp "$GGUF" "$SUPPORT/qwen2.5-0.5b-instruct-q4_0.gguf"
    ///
    /// So the model WAS provisioned and `deviceModel()` still returned `nil`, and
    /// every test gated on it skipped while reporting the cause as **"no model
    /// provisioned"** -- a statement that reads as a precondition which was not
    /// met, and is indistinguishable from one.
    ///
    /// A skip is the one result a reader cannot tell from success, so a stale
    /// literal here is worse than a wrong assertion: it removes the evidence rather
    /// than contradicting it.
    ///
    /// Same defect already fixed in three Android test files, where the filename
    /// comes from `ModelCatalog.MODEL_0_5B.id`. iOS has no catalogue, so the
    /// equivalent is to ask for the FAMILY and take what was provisioned --
    /// deterministically, sorted, so provisioning two quants cannot make it flaky.
    /// The quantisation is a provisioning decision and the pipeline makes it in
    /// one place; a test has no business restating it.
    private func deviceModel() -> String? {
        if let env = ProcessInfo.processInfo.environment["GS_TEST_MODEL"],
           FileManager.default.fileExists(atPath: env) {
            return env
        }
        let support = try? FileManager.default.url(
            for: .applicationSupportDirectory, in: .userDomainMask,
            appropriateFor: nil, create: false)
        guard let dir = support else { return nil }
        let family = "qwen2.5-0.5b-instruct-"
        let present = (try? FileManager.default.contentsOfDirectory(atPath: dir.path)) ?? []
        let matches = present
            .filter { $0.hasPrefix(family) && $0.hasSuffix(".gguf") }
            .sorted()
        guard let name = matches.first else { return nil }
        return dir.appendingPathComponent(name).path
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
    // MARK: - The chat SCREEN path, not GsNative directly
    //
    // Everything above calls GsNative. These two drive ChatViewModel.send, which
    // is what the user actually touches, and they are the iOS counterpart of a6
    // and a7 on Android.

    /// Loads the model and asserts the engine is really there, so a later
    /// assertion cannot be satisfied vacuously.
    private func loadEngineOrSkip() throws {
        guard let model = deviceModel() else {
            throw XCTSkip("no model provisioned; set GS_TEST_MODEL or place a GGUF in "
                          + "Application Support")
        }
        XCTAssertTrue(
            GsNativeLoader.initialize(modelPath: model),
            "GsNativeLoader.initialize returned false for \(model)")
        XCTAssertTrue(GsNativeLoader.isAvailable,
                      "isAvailable is false immediately after a successful initialize, so "
                      + "later assertions would be measuring nothing")
        try XCTSkipUnless(GsNative.backendAvailable, "no generation backend in this build")
    }

    /// The prompt both tests use. The 0.5B answers it with one word.
    private let screenPrompt =
        "What is the capital of France? Answer with one word."

    /// Every message the view model has, joined, so a single assertion can see a
    /// row that is not `last` -- the local path appends the user row and then the
    /// assistant row, and an assertion on `last` alone would miss a reply written
    /// somewhere else.
    ///
    /// @MainActor, and not decoratively. Run 36825039431:
    ///
    ///     Main actor-isolated property 'messages' can not be referenced from a
    ///       nonisolated context
    ///
    /// ChatViewModel's `messages` and `draft` are MainActor-isolated, so a helper
    /// that reads them has to be MainActor too. Calling it from the @MainActor
    /// test methods would have been fine either way; the helper's OWN isolation is
    /// what the compiler checks.
    @MainActor
    private func transcript(_ vm: ChatViewModel) -> String {
        vm.messages.map(\.content).joined(separator: "\n")
    }

    /// iOS's a6: with the switch on, `send()` must answer from the ENGINE.
    @MainActor
    func testTheChatScreenAnswersFromTheEngine() throws {
        try loadEngineOrSkip()

        let store = SettingsStore.shared
        let before = store.preferLocal
        defer { store.preferLocal = before }
        store.preferLocal = true
        XCTAssertTrue(store.preferLocal, "the switch did not turn on")

        let vm = ChatViewModel(conversationID: nil)
        vm.draft = screenPrompt
        vm.send()

        let text = transcript(vm)
        print("iOS a6 transcript -> \(text.prefix(160))")

        // The turn produced both rows, so the path ran to its end rather than
        // returning early.
        XCTAssertTrue(
            vm.messages.contains { $0.role == "user" && $0.content.contains("France") },
            "no user row was appended, so send() never reached the local path: \(text)")
        let assistant = vm.messages.last { $0.role == "assistant" }
        XCTAssertNotNil(assistant, "no assistant row at all: \(text)")

        // THE ASSERTION THAT MATTERS. "Paris" is the model's own answer and
        // nothing else on this path can produce it, so this is positive evidence
        // that GsNative.chat ran and its text reached the screen.
        XCTAssertTrue(
            text.contains("Paris"),
            "ChatViewModel.send did not put the engine's answer on screen. "
            + "The call site exists at ChatViewModel.swift:494 and the suite had "
            + "never executed it. Got: \(text)")

        // And the row is finished, not left mid-stream: finalizeLocal ran.
        XCTAssertFalse(
            assistant?.isStreaming ?? true,
            "the assistant row is still streaming, so finalizeLocal did not run")
    }

    /// iOS's a7, and the same claim the Android bug fix turned on: with the switch
    /// OFF -- the default -- the engine must NOT be consulted.
    ///
    /// This is not an absence-only assertion. The network path cannot succeed in
    /// this test environment, so what is asserted is that no message anywhere
    /// carries the model's marker: the engine was not reached at all.
    @MainActor
    func testTheChatScreenLeavesTheEngineAloneWhenTheSwitchIsOff() throws {
        try loadEngineOrSkip()

        let store = SettingsStore.shared
        let before = store.preferLocal
        defer { store.preferLocal = before }
        store.preferLocal = false
        XCTAssertFalse(store.preferLocal, "the switch did not turn off")

        let vm = ChatViewModel(conversationID: nil)
        vm.draft = screenPrompt
        vm.send()

        // The local path is synchronous, so if it had run, its answer would be
        // written by now. The network path starts a Task, so nothing is waited on
        // here -- which is fine, because the claim is about the ENGINE.
        let text = transcript(vm)
        print("iOS a7 transcript -> \(text.prefix(160))")
        XCTAssertFalse(
            text.contains("Paris"),
            "with preferLocal OFF the engine answered anyway. The switch is not "
            + "gating the engine on ChatViewModel.swift:493. Got: \(text)")

        // The user row is appended by both paths, so its presence proves send()
        // ran rather than the test measuring an untouched view model.
        XCTAssertTrue(
            vm.messages.contains { $0.role == "user" && $0.content.contains("France") },
            "send() did not append a user row at all: \(text)")
    }

    /// WHICH SLICE IS EXECUTING, reported by the process that is executing it.
    ///
    /// Everything else this repository knows about the executing architecture is a
    /// capability or a prediction, and all three have been measured disagreeing
    /// with the answer on the very run that produced them:
    ///
    ///   - `SIMULATOR_ARCHS` says what the simulator CAN run. It reported
    ///     `arm64 x86_64` on a runner that executed x86_64.
    ///   - `lipo -info` says what is LINKED. It reported `x86_64 arm64` for that
    ///     same run.
    ///   - `xcodebuild -showBuildSettings`'s `ARCHS` says what will be BUILT.
    ///
    /// None of those is what ran. This is. `#if arch` is evaluated per slice at
    /// compile time and Swift compiles each slice of a fat binary separately, so
    /// the branch taken here is the branch the executing slice was compiled
    /// with.
    ///
    /// It deliberately does NOT use `uname()`, which inside a simulator returns
    /// the HOST's architecture: it would have cheerfully reported `arm64` for a
    /// process running emulated x86_64 code. A wrong answer stated confidently is
    /// worse than no answer, and this test exists to remove exactly that.
    ///
    /// Measured on ios-native 37023563596: the tests executed **x86_64** on an
    /// arm64 host, so before this test nothing in the build said so.
    func test_the_process_reports_which_slice_is_executing() {
        #if arch(arm64)
        let arch = "arm64"
        #elseif arch(x86_64)
        let arch = "x86_64"
        #elseif arch(arm)
        let arch = "arm"
        #else
        let arch = "unknown"
        #endif

        // Field-exact and distinctly prefixed, so the workflow's log parse cannot
        // be satisfied by a build setting echoed into the same log.
        print("iOS-EXEC-ARCH: \(arch)")

        // Inert unless the workflow asks for a specific arch, so this test cannot
        // make a normal run fail. It is the cross-check for `sim_arch`, not a
        // permanent constraint on whatever the default destination resolves to.
        if let expected = ProcessInfo.processInfo.environment["GS_EXPECT_ARCH"],
           !expected.isEmpty {
            XCTAssertEqual(
                arch, expected,
                "the destination asked for arch=\(expected) and this process was "
                + "compiled as \(arch). xcodebuild built for a slice other than the "
                + "one that ran, so a green run on \(expected) proved nothing about "
                + "that architecture.")
        }
    }

}
