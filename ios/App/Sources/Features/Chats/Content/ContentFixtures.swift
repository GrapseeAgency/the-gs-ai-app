import Foundation

/// STEP 5 test-content matrix (iOS twin of ContentFixtures.kt) — pure data,
/// byte-for-byte identical with the Android fixtures. Exercises every renderer
/// family plus the stream-safety cases. [ContentSanity.sanityReport] parses all
/// of them and returns a compact summary — a debug-checkable invariant report,
/// not a test suite.

struct ContentFixture {
    let id: String
    let content: String
    let metadata: RichMetadata?
}

enum ContentFixtures {
    static let kotlinSnippet = #"```kotlin"# + "\n" +
        #"fun greet(name: String): String {"# + "\n" +
        #"    // Build a greeting"# + "\n" +
        #"    val message = "Hello, user!""# + "\n" +
        #"    if (name.isEmpty()) return "Hi""# + "\n" +
        #"    return message"# + "\n" +
        #"}"# + "\n" +
        #"```"#

    static let pythonSnippet = #"```python"# + "\n" +
        #"def fib(n):"# + "\n" +
        #"    # classic recursion"# + "\n" +
        #"    if n < 2:"# + "\n" +
        #"        return n"# + "\n" +
        #"    return fib(n - 1) + fib(n - 2)"# + "\n" +
        #""# + "\n" +
        #"print(fib(10))"# + "\n" +
        #"```"#

    static let jsonSnippet = #"```json"# + "\n" +
        #"{"# + "\n" +
        #"  "name": "gs-ai","# + "\n" +
        #"  "version": "0.60.0","# + "\n" +
        #"  "features": ["stream", "translate"],"# + "\n" +
        #"  "enabled": true,"# + "\n" +
        #"  "retries": 3"# + "\n" +
        #"}"# + "\n" +
        #"```"#

    static let sqlSnippet = #"```sql"# + "\n" +
        #"-- Active users by plan"# + "\n" +
        #"SELECT plan, COUNT(*) AS users"# + "\n" +
        #"FROM accounts"# + "\n" +
        #"WHERE active = 1"# + "\n" +
        #"GROUP BY plan"# + "\n" +
        #"ORDER BY users DESC;"# + "\n" +
        #"```"#

    static let mermaidFlowchart = #"```mermaid"# + "\n" +
        #"flowchart TD"# + "\n" +
        #"    Start([App start]) --> Check{Signed in?}"# + "\n" +
        #"    Check -->|yes| Home[Show home]"# + "\n" +
        #"    Check -->|no| Auth[Show auth]"# + "\n" +
        #"    Auth --> Home"# + "\n" +
        #"```"#

    static let mermaidSequence = #"```mermaid"# + "\n" +
        #"sequenceDiagram"# + "\n" +
        #"    participant U as User"# + "\n" +
        #"    participant S as Server"# + "\n" +
        #"    U->>S: Request data"# + "\n" +
        #"    S-->>U: Stream chunks"# + "\n" +
        #"    U->>U: Render block"# + "\n" +
        #"```"#

    static let mermaidInvalid = #"```mermaid"# + "\n" +
        #"flowchart TD"# + "\n" +
        #"    A[broken --> ]"# + "\n" +
        #"    subgraph X"# + "\n" +
        #"    A --> B"# + "\n" +
        #"```"#

    static let blockMath = #"$$"# + "\n" +
        #"\int_0^\infty e^{-x^2} dx = \frac{\sqrt{\pi}}{2}"# + "\n" +
        #"$$"#

    static func hugeResponse() -> String {
        var sb = ""
        sb += "## Full briefing\n\n"
        for i in 1...24 { sb += "Paragraph \(i) carries one idea and moves on without ceremony.\n\n" }
        sb += "### The long list\n\n"
        for i in 1...120 { sb += "\(i). Item \(i) with a short note.\n" }
        sb += "\n### The wide table\n\n"
        sb += "| Col A | Col B | Col C | Col D | Col E | Col F | Col G | Col H |\n"
        sb += "|-------|-------|-------|-------|-------|-------|-------|-------|\n"
        for i in 1...30 {
            sb += "| v\(i) | v\(i) | v\(i) | v\(i) | v\(i) | v\(i) | v\(i) | v\(i) |\n"
        }
        sb += "\n" + kotlinSnippet + "\n\n"
        for i in 25...30 { sb += "Paragraph \(i) after the first code block.\n\n" }
        sb += pythonSnippet + "\n\n"
        sb += sqlSnippet + "\n\n"
        sb += "Closing paragraph of the very long response."
        return sb
    }

    static let all: [ContentFixture] = [
        ContentFixture(
            id: "F1_PLAIN_PROSE",
            content: "Photosynthesis converts light into chemical energy.\n\n" +
                "The process has two stages. Light-dependent reactions capture energy, and the Calvin cycle fixes carbon.\n\n" +
                "Chlorophyll absorbs most strongly in the blue and red spectrum.",
            metadata: nil
        ),
        ContentFixture(
            id: "F2_HEADINGS",
            content: "# Level one\n## Level two\n### Level three\n#### Level four\n##### Level five\n###### Level six\n\nBody after headings.",
            metadata: nil
        ),
        ContentFixture(
            id: "F3_NESTED_LISTS",
            content: "- Fruits\n  - Citrus\n    - Blood orange\n    - Yuzu\n  - Stone fruit\n- Vegetables\n  1. Root\n  2. Leafy",
            metadata: nil
        ),
        ContentFixture(
            id: "F4_BLOCKQUOTES",
            content: "> Simple quote line.\n\n> First line of a quote\n> continues here.\n>\n> Second paragraph in the same quote.",
            metadata: nil
        ),
        ContentFixture(
            id: "F5_LINKS",
            content: "Read the [Compose docs](https://developer.android.com/jetpack/compose) first.\n\n" +
                "Or visit https://kotlinlang.org, then [SwiftUI](https://developer.apple.com/xcode/swiftui/).\n\n" +
                "A link ends a sentence at https://example.com.",
            metadata: nil
        ),
        ContentFixture(
            id: "F6_INLINE_STYLES",
            content: "Mix **bold**, *italic*, ~~gone~~, and `inline_code()` together.\n\n" +
                "**Bold with *nested italic* inside** stays readable.",
            metadata: nil
        ),
        ContentFixture(id: "F7_KOTLIN_CODE", content: kotlinSnippet, metadata: nil),
        ContentFixture(id: "F8_PYTHON_CODE", content: pythonSnippet, metadata: nil),
        ContentFixture(id: "F9_JSON", content: jsonSnippet, metadata: nil),
        ContentFixture(id: "F10_SQL", content: sqlSnippet, metadata: nil),
        ContentFixture(
            id: "F11_TABLE",
            content: "| Feature | Android | iOS |\n" +
                "|:--------|:-------:|----:|\n" +
                "| Streaming | yes | yes |\n" +
                "| Tables | **now** | now |\n" +
                "| Math | partial | partial |\n" +
                "| A very long feature name that should force scrolling | always | always |",
            metadata: nil
        ),
        ContentFixture(
            id: "F12_MIXED_MARKDOWN",
            content: "## Release notes\n\nThis release **ships** the renderer.\n\n- Faster streaming\n- Richer blocks\n\n" +
                "```json\n{\"ok\": true}\n```\n\n> Content is now block-based.\n\n" +
                "| Block | Status |\n|-------|--------|\n| Code | done |",
            metadata: nil
        ),
        ContentFixture(
            id: "F13_INLINE_MATH",
            content: "Einstein wrote \\(E = mc^2\\), and Euler gave us $e^{i\\pi} + 1 = 0$.\n\nWater is H\\(_2\\)O.",
            metadata: nil
        ),
        ContentFixture(id: "F14_BLOCK_MATH", content: blockMath, metadata: nil),
        ContentFixture(id: "F15_MERMAID_FLOWCHART", content: mermaidFlowchart, metadata: nil),
        ContentFixture(id: "F16_MERMAID_SEQUENCE", content: mermaidSequence, metadata: nil),
        ContentFixture(id: "F17_MERMAID_INVALID", content: mermaidInvalid, metadata: nil),
        ContentFixture(
            id: "F18_MULTIPLE_CODE",
            content: "First block:\n\n```kotlin\nval x = 1\n```\n\nMiddle prose.\n\n```python\ny = 2\n```\n\nDone.",
            metadata: nil
        ),
        ContentFixture(id: "F19_HUGE_RESPONSE", content: hugeResponse(), metadata: nil),
        ContentFixture(id: "F20_STREAM_PARTIAL_FENCE", content: "Here is a start:\n\n```kotlin\nfun hello(", metadata: nil),
        ContentFixture(id: "F21_STREAM_PARTIAL_TABLE", content: "| Name | Value |\n|------\n| Alpha | 1 |", metadata: nil),
        ContentFixture(id: "F22_STREAM_PARTIAL_MARKDOWN", content: "**important\n- item one\n## Head", metadata: nil),
        ContentFixture(
            id: "F23_CITATION_METADATA",
            content: "The answer cites sources[1][2].",
            metadata: RichMetadata(
                citations: [
                    CitationEntry(number: 1, title: "Compose performance", domain: "developer.android.com", snippet: "Best practices for stable lambdas"),
                    CitationEntry(number: 2, title: "SwiftUI rendering", domain: "developer.apple.com", snippet: "View identity and rendering")
                ],
                toolResults: []
            )
        ),
        ContentFixture(
            id: "F24_TOOL_RESULT",
            content: "Searching now.",
            metadata: RichMetadata(
                citations: [],
                toolResults: [
                    ToolResultEntry(kind: "search", status: "completed", title: "Searched 12 sources", detail: "Query: compose streaming performance")
                ]
            )
        ),
        ContentFixture(
            id: "F25_COMBINED",
            content: "## Analysis\n\nGrowth follows `y = a e^{kt}` curves \\(k > 0\\).\n\n- step one\n- step two\n\n" +
                "| Metric | Q1 | Q2 |\n|--------|----|----|\n| Revenue | 10 | 14 |\n\n" +
                "```kotlin\nval ok = true\n```\n\n> Trends compound.\n\n" +
                "Details: [Spec](https://example.com/spec)\n\n---\n\n" +
                blockMath + "\n\n" +
                "```mermaid\nflowchart LR\n    A --> B --> C\n```",
            metadata: nil
        )
    ]
}

private struct FixtureSummary {
    let kinds: [String: Int]
    let errors: [String]
}

private func summarize(_ blocks: [Block]) -> FixtureSummary {
    var kinds: [String: Int] = [:]
    var errors: [String] = []

    func name(_ block: Block) -> String {
        switch block {
        case .paragraph: return "Paragraph"
        case .heading: return "Heading"
        case .bulletList: return "BulletList"
        case .orderedList: return "OrderedList"
        case .blockQuote: return "BlockQuote"
        case .codeBlock: return "CodeBlock"
        case .table: return "TableBlock"
        case .divider: return "Divider"
        case .mathBlock: return "MathBlock"
        case .mermaid: return "MermaidBlock"
        case .image: return "ImageBlock"
        case .collapsible: return "Collapsible"
        case .citations: return "Citations"
        case .toolResult: return "ToolResult"
        }
    }

    func walk(_ block: Block) {
        kinds[name(block), default: 0] += 1
        switch block {
        case .bulletList(let items, _), .orderedList(_, let items, _):
            items.forEach { item in item.children.forEach(walk) }
        case .blockQuote(let inner, _):
            inner.forEach(walk)
        case .collapsible(_, let inner, _):
            inner.forEach(walk)
        default:
            break
        }
    }
    blocks.forEach(walk)
    return FixtureSummary(kinds: kinds, errors: errors)
}

enum ContentSanity {
    /// Parses every fixture and returns a human-readable report — block-kind
    /// counts per fixture. Used during development to validate the parser on
    /// both platforms; carries no runtime behaviour.
    static func sanityReport() -> String {
        var out = ""
        for fixture in ContentFixtures.all {
            let summary: FixtureSummary
            do {
                summary = summarize(try parseValidated(fixture.content, metadata: fixture.metadata))
            } catch {
                summary = FixtureSummary(kinds: [:], errors: ["PARSE THREW: \(error.localizedDescription)"])
            }
            let counts = summary.kinds.map { "\($0.key):\($0.value)" }.joined(separator: ", ")
            let suffix = summary.errors.isEmpty ? "" : "  ERRORS: \(summary.errors.joined(separator: "; "))"
            out += "\(fixture.id) → \(counts)\(suffix)\n"
        }
        return out
    }

    /// parseBlocks never throws today; the throwing wrapper keeps the report
    /// honest if the parser grows throwing paths.
    private static func parseValidated(_ content: String, metadata: RichMetadata?) throws -> [Block] {
        parseBlocks(content, metadata: metadata)
    }
}
