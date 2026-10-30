import XCTest
@testable import GSApp

/// The rule behind the Model Selection page's two controls — the iOS half of
/// Android's `ModelRouterTest`. It is a pure function on purpose: the reference
/// draws Auto Select ON and Medium lit at once, and this is what makes that
/// pair mean something instead of two stored values nothing reads.
final class ModelRouterTests: XCTestCase {

    func testAnImageAlwaysWantsTheVisionModel() {
        XCTAssertEqual(
            ModelRouter.pick(text: "make this brighter", hasImage: true, preferred: .pro),
            .vision)
        XCTAssertEqual(
            ModelRouter.pick(text: "compile this", hasImage: true, preferred: .code),
            .vision)
    }

    func testACodeSignalBeatsLengthAndPreference() {
        XCTAssertEqual(
            ModelRouter.pick(text: "```func main() {}```", hasImage: false, preferred: .lite),
            .code)
        XCTAssertEqual(
            ModelRouter.pick(text: "why does this stack trace appear", hasImage: false, preferred: .pro),
            .code)
    }

    func testALongMessageIsComplexAndShortOnesKeepThePreference() {
        let long = String(repeating: "word ", count: 60)
        XCTAssertEqual(ModelRouter.pick(text: long, hasImage: false, preferred: .lite), .pro)
        XCTAssertEqual(
            ModelRouter.pick(text: "what's the weather", hasImage: false, preferred: .lite),
            .lite)
    }

    func testEffortMapsToTheThreeWireModes() {
        XCTAssertEqual(ModelRouter.mode(for: "minimal"), "flash")
        XCTAssertEqual(ModelRouter.mode(for: "low"), "flash")
        XCTAssertEqual(ModelRouter.mode(for: "standard"), "auto")
        XCTAssertEqual(ModelRouter.mode(for: "high"), "thinking")
    }

    func testAutoSelectTakesTheModelsEffortAndOffTakesTheReaders() {
        XCTAssertEqual(
            ModelRouter.modeForMessage(
                text: "please debug this python", hasImage: false,
                preferred: .pro, effort: "minimal", autoSelect: true),
            "thinking")
        XCTAssertEqual(
            ModelRouter.modeForMessage(
                text: "please debug this python", hasImage: false,
                preferred: .pro, effort: "minimal", autoSelect: false),
            "flash")
    }

    func testAnUnknownStoredIdFallsBackToPro() {
        XCTAssertEqual(GsModel.byId(nil), .pro)
        XCTAssertEqual(GsModel.byId("gpt-9"), .pro)
        XCTAssertEqual(GsModel.byId("lite"), .lite)
    }

    func testTheTabsBucketsMatchTheModelsTheyShow() {
        XCTAssertEqual(GsModel.allCases.filter { $0.capabilities.contains("text") }, [.pro, .lite])
        XCTAssertEqual(GsModel.allCases.filter { $0.capabilities.contains("vision") }, [.pro, .vision])
        XCTAssertEqual(GsModel.allCases.filter { $0.capabilities.contains("code") }, [.pro, .code])
    }

    /// Picking a model moves the effort chip to that model's default, so the
    /// tap is visible instead of storing a label nothing acts on.
    func testPickingAModelMovesTheEffortToItsDefault() {
        let settings = SettingsStore.shared
        let previousModel = settings.preferredModel
        let previousEffort = settings.reasoningEffort
        defer {
            settings.preferredModel = previousModel
            settings.reasoningEffort = previousEffort
        }
        settings.applyPreferredModel("lite")
        XCTAssertEqual(settings.preferredModel, "lite")
        XCTAssertEqual(settings.reasoningEffort, "low")
        settings.applyPreferredModel("code")
        XCTAssertEqual(settings.reasoningEffort, "high")
    }
}
