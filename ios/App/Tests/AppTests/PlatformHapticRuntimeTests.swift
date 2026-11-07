import XCTest
import UIKit
import ObjectiveC.runtime
@testable import GSApp

/// Observes calls at the UIKit boundary and FORWARDS them to the real
/// generators. Simulator evidence is dispatch, never physical Taptic sensation.
final class PlatformHapticRuntimeTests: XCTestCase {
    @MainActor
    func testUIKitDispatchAndDisabledGate() throws {
        let impactSelector = #selector(UIImpactFeedbackGenerator.impactOccurred)
        let noticeSelector = #selector(UINotificationFeedbackGenerator.notificationOccurred(_:))
        let selectionSelector = #selector(UISelectionFeedbackGenerator.selectionChanged)
        let impact = try XCTUnwrap(class_getInstanceMethod(UIImpactFeedbackGenerator.self, impactSelector))
        let notice = try XCTUnwrap(class_getInstanceMethod(UINotificationFeedbackGenerator.self, noticeSelector))
        let selection = try XCTUnwrap(class_getInstanceMethod(UISelectionFeedbackGenerator.self, selectionSelector))
        let originalImpact = method_getImplementation(impact)
        let originalNotice = method_getImplementation(notice)
        let originalSelection = method_getImplementation(selection)
        typealias NoArgument = @convention(c) (AnyObject, Selector) -> Void
        typealias NoticeArgument = @convention(c) (AnyObject, Selector, Int) -> Void
        let forwardImpact = unsafeBitCast(originalImpact, to: NoArgument.self)
        let forwardNotice = unsafeBitCast(originalNotice, to: NoticeArgument.self)
        let forwardSelection = unsafeBitCast(originalSelection, to: NoArgument.self)
        var impacts = 0
        var notices: [Int] = []
        var selections = 0
        let impactBlock: @convention(block) (AnyObject) -> Void = { object in
            impacts += 1
            forwardImpact(object, impactSelector)
        }
        let noticeBlock: @convention(block) (AnyObject, Int) -> Void = { object, value in
            notices.append(value)
            forwardNotice(object, noticeSelector, value)
        }
        let selectionBlock: @convention(block) (AnyObject) -> Void = { object in
            selections += 1
            forwardSelection(object, selectionSelector)
        }
        let impactIMP = imp_implementationWithBlock(impactBlock)
        let noticeIMP = imp_implementationWithBlock(noticeBlock)
        let selectionIMP = imp_implementationWithBlock(selectionBlock)
        method_setImplementation(impact, impactIMP)
        method_setImplementation(notice, noticeIMP)
        method_setImplementation(selection, selectionIMP)
        let old = SettingsStore.shared.haptics
        defer {
            SettingsStore.shared.haptics = old
            method_setImplementation(impact, originalImpact)
            method_setImplementation(notice, originalNotice)
            method_setImplementation(selection, originalSelection)
            imp_removeBlock(impactIMP)
            imp_removeBlock(noticeIMP)
            imp_removeBlock(selectionIMP)
        }
        func fireVocabulary() {
            GSHaptics.tap(); GSHaptics.press(); GSHaptics.success()
            GSHaptics.warning(); GSHaptics.error(); GSHaptics.select()
        }
        SettingsStore.shared.haptics = true
        fireVocabulary()
        XCTAssertEqual(impacts, 2)
        XCTAssertEqual(notices, [UINotificationFeedbackGenerator.FeedbackType.success.rawValue,
            UINotificationFeedbackGenerator.FeedbackType.warning.rawValue,
            UINotificationFeedbackGenerator.FeedbackType.error.rawValue])
        XCTAssertEqual(selections, 1)
        SettingsStore.shared.haptics = false
        fireVocabulary()
        XCTAssertEqual(impacts, 2, "Disabled impacts reached UIKit")
        XCTAssertEqual(notices.count, 3, "Disabled notifications reached UIKit")
        XCTAssertEqual(selections, 1, "Disabled selection reached UIKit")
    }
}
