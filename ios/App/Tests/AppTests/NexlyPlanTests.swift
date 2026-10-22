import XCTest
@testable import GSApp

/**
 * The Nexly timetable contract on the iOS side. A ```plan fence must become a
 * first-class plan block, the model's wrapper/synonym slips must still parse,
 * and emoji must never survive into the card.
 */
final class NexlyPlanTests: XCTestCase {

    private let wire = """
    Got it! Here is your day.

    ```plan
    {
      "title": "Here's your personalized plan for tomorrow:",
      "rows": [
        { "time": "8:00 – 8:30 AM", "icon": "sunrise", "text": "Morning routine + light exercise" },
        { "time": "9:00 – 11:00 AM", "icon": "book-2", "text": "Classes" },
        { "time": "1:00 – 2:00 PM", "icon": "tools-kitchen-2", "text": "Lunch + short break" }
      ],
      "closing": "You got this, Arafat!",
      "closingIcon": "flame",
      "timestamp": "9:45 PM"
    }
    ```
    """

    func testClosedPlanFenceBecomesPlanBlock() {
        let blocks = parseBlocks(wire)
        XCTAssertEqual(blocks.count, 2)
        guard case .paragraph(_, _) = blocks[0] else {
            return XCTFail("expected the lead-in to stay prose")
        }
        guard case .plan(let plan, let open, _) = blocks[1] else {
            return XCTFail("expected a plan block, got \(blocks[1])")
        }
        XCTAssertFalse(open)
        XCTAssertEqual(plan.rows.count, 3)
        XCTAssertEqual(plan.rows[0].time, "8:00 – 8:30 AM")
        XCTAssertEqual(plan.rows[1].icon, "book-2")
        XCTAssertEqual(plan.closingIcon, "flame")
        XCTAssertEqual(plan.timestamp, "9:45 PM")
    }

    func testJSONWrappedPlanIsPromotedButLabelRowsAreNot() {
        let planJSON = #"{"rows":[{"time":"9:00 AM","icon":"book-2","text":"Classes"}]}"#
        guard let planBlock = parseBlocks("```json\n\(planJSON)\n```").single,
              case .plan(_, _, _) = planBlock else {
            return XCTFail("a json-wrapped timetable must still render")
        }

        let labelJSON = #"{"rows":[{"label":"Revenue","value":10}]}"#
        guard let codeBlock = parseBlocks("```json\n\(labelJSON)\n```").single,
              case .codeBlock(_, _, _, _) = codeBlock else {
            return XCTFail("ordinary JSON must stay code")
        }
    }

    func testWrappersAndSynonymsParse() {
        let plan = parseNexlyPlan(
            """
            {
              "schedule": {
                "heading": "Sunday",
                "items": [
                  {"start":"8:00 AM","activity":"Run","icon":"run"},
                  {"from":"9:00 AM","task":"Study","icon":"study"}
                ],
                "footer":"Go get it",
                "timestamp":"8:00 PM"
              }
            }
            """
        )
        XCTAssertNotNil(plan)
        guard let plan else { return XCTFail("expected the wrapped schedule to parse") }
        XCTAssertEqual(plan.title, "Sunday")
        XCTAssertEqual(plan.rows.count, 2)
        XCTAssertEqual(plan.rows.first?.text, "Run")
        XCTAssertEqual(plan.rows.last?.icon, "book-2")
        XCTAssertEqual(plan.closing, "Go get it")
    }

    func testIncompleteRowsDoNotProduceACard() {
        XCTAssertNil(parseNexlyPlan(#"{"rows":[{"time":"9:00"}]}"#))
        XCTAssertNil(parseNexlyPlan(#"{"rows":[{"text":"Class"}]}"#))
    }

    func testNaturalTimetableFenceNamesBecomePlans() {
        let json = #"{"rows":[{"time":"8:00","icon":"sunrise","text":"Run"}]}"#
        for language in ["plan", "schedule", "timetable", "routine", "day"] {
            guard let block = parseBlocks("```\(language)\n\(json)\n```").single,
                  case .plan(_, _, _) = block else {
                return XCTFail("\(language) fence must be a plan")
            }
        }
    }

    func testReferencePaletteSurvivesOmittedTone() {
        XCTAssertEqual(NexlyIcons.tone(for: "sunrise"), NexlyGlassTone.amber)
        XCTAssertEqual(NexlyIcons.tone(for: "book-2"), NexlyGlassTone.lavender)
        XCTAssertEqual(NexlyIcons.tone(for: "device-laptop"), NexlyGlassTone.blue)
        XCTAssertEqual(NexlyIcons.tone(for: "tools-kitchen-2"), NexlyGlassTone.green)
        XCTAssertEqual(NexlyIcons.tone(for: "run"), NexlyGlassTone.pink)
        XCTAssertEqual(NexlyIcons.tone(for: "moon"), NexlyGlassTone.lilac)
    }

    func testEmojiBecomesIconTokens() {
        XCTAssertEqual(cleanNexlyText("You got this 💪", max: 40), "You got this :flame:")
        XCTAssertFalse(cleanNexlyText("Classes 📚", max: 40).unicodeScalars.contains { scalar in
            (0x1F000...0x1FAFF).contains(Int(scalar.value))
        })
    }
}

private extension Array {
    var single: Element? { count == 1 ? self[0] : nil }
}
