import XCTest
@testable import GSApp

/// The model preference catalogue and actual server-supported wire-mode contract.
/// Auto delegates provider choice to the server, never to client-side guesses.
///
/// `@MainActor` because the last test drives `SettingsStore.shared`, whose
/// published properties are main-actor isolated — the same reason
/// `GsNotifierTests` carries it.
@MainActor
final class ModelRouterTests: XCTestCase {

    func testAutoDelegatesModelSelectionToServerRegardlessOfStoredEffort() {
        for effort in ["minimal", "low", "standard", "high"] {
            XCTAssertEqual(ModelRouter.modeForMessage(effort: effort, autoSelect: true), "auto")
        }
    }

    func testEffortMapsToTheThreeWireModes() {
        XCTAssertEqual(ModelRouter.mode(for: "minimal"), "flash")
        XCTAssertEqual(ModelRouter.mode(for: "low"), "flash")
        XCTAssertEqual(ModelRouter.mode(for: "standard"), "auto")
        XCTAssertEqual(ModelRouter.mode(for: "high"), "thinking")
    }

    func testManualModeUsesReadersEffortWithoutGuessingProvider() {
        XCTAssertEqual(ModelRouter.modeForMessage(effort: "minimal", autoSelect: false), "flash")
        XCTAssertEqual(ModelRouter.modeForMessage(effort: "high", autoSelect: false), "thinking")
        XCTAssertEqual(ModelRouter.modeForMessage(effort: "standard", autoSelect: false), "auto")
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
