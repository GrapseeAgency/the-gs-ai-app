package com.grapsee.gsai.ui.chat.content

/**
 * STEP 5 test-content matrix — pure data, shared byte-for-byte with the iOS
 * twin (Features/Chats/Content/ContentFixtures.swift). These fixtures exercise
 * every renderer family plus the stream-safety cases (partial fences, partial
 * tables, dangling emphasis). [sanityReport] runs the parser over all of them
 * and returns a compact summary — a debug-checkable invariant report, not a
 * test suite.
 */
object ContentFixtures {

    data class Fixture(
        val id: String,
        val content: String,
        val metadata: RichMetadata? = null
    )

    private const val KOTLIN_SNIPPET = "```kotlin\n" +
        "fun greet(name: String): String {\n" +
        "    // Build a greeting\n" +
        "    val message = \"Hello, user!\"\n" +
        "    if (name.isEmpty()) return \"Hi\"\n" +
        "    return message\n" +
        "}\n" +
        "```"

    private const val PYTHON_SNIPPET = "```python\n" +
        "def fib(n):\n" +
        "    # classic recursion\n" +
        "    if n < 2:\n" +
        "        return n\n" +
        "    return fib(n - 1) + fib(n - 2)\n" +
        "\n" +
        "print(fib(10))\n" +
        "```"

    private const val JSON_SNIPPET = "```json\n" +
        "{\n" +
        "  \"name\": \"gs-ai\",\n" +
        "  \"version\": \"0.60.0\",\n" +
        "  \"features\": [\"stream\", \"translate\"],\n" +
        "  \"enabled\": true,\n" +
        "  \"retries\": 3\n" +
        "}\n" +
        "```"

    private const val SQL_SNIPPET = "```sql\n" +
        "-- Active users by plan\n" +
        "SELECT plan, COUNT(*) AS users\n" +
        "FROM accounts\n" +
        "WHERE active = 1\n" +
        "GROUP BY plan\n" +
        "ORDER BY users DESC;\n" +
        "```"

    private const val MERMAID_FLOWCHART = "```mermaid\n" +
        "flowchart TD\n" +
        "    Start([App start]) --> Check{Signed in?}\n" +
        "    Check -->|yes| Home[Show home]\n" +
        "    Check -->|no| Auth[Show auth]\n" +
        "    Auth --> Home\n" +
        "```"

    private const val MERMAID_SEQUENCE = "```mermaid\n" +
        "sequenceDiagram\n" +
        "    participant U as User\n" +
        "    participant S as Server\n" +
        "    U->>S: Request data\n" +
        "    S-->>U: Stream chunks\n" +
        "    U->>U: Render block\n" +
        "```"

    private const val MERMAID_INVALID = "```mermaid\n" +
        "flowchart TD\n" +
        "    A[broken --> ]\n" +
        "    subgraph X\n" +
        "    A --> B\n" +
        "```"

    private const val BLOCK_MATH = "$$\n" +
        "\\int_0^\\infty e^{-x^2} dx = \\frac{\\sqrt{\\pi}}{2}\n" +
        "$$"

    private fun hugeResponse(): String {
        val sb = StringBuilder()
        sb.append("## Full briefing\n\n")
        for (i in 1..24) sb.append("Paragraph ").append(i).append(" carries one idea and moves on without ceremony.\n\n")
        sb.append("### The long list\n\n")
        for (i in 1..120) sb.append(i).append(". Item ").append(i).append(" with a short note.\n")
        sb.append("\n### The wide table\n\n")
        sb.append("| Col A | Col B | Col C | Col D | Col E | Col F | Col G | Col H |\n")
        sb.append("|-------|-------|-------|-------|-------|-------|-------|-------|\n")
        for (i in 1..30) {
            sb.append("| v").append(i).append(" | v").append(i).append(" | v").append(i)
                .append(" | v").append(i).append(" | v").append(i).append(" | v").append(i)
                .append(" | v").append(i).append(" | v").append(i).append(" |\n")
        }
        sb.append("\n").append(KOTLIN_SNIPPET).append("\n\n")
        for (i in 25..30) sb.append("Paragraph ").append(i).append(" after the first code block.\n\n")
        sb.append(PYTHON_SNIPPET).append("\n\n")
        sb.append(SQL_SNIPPET).append("\n\n")
        sb.append("Closing paragraph of the very long response.")
        return sb.toString()
    }

    val all: List<Fixture> = listOf(
        Fixture(
            "F1_PLAIN_PROSE",
            "Photosynthesis converts light into chemical energy.\n\n" +
                "The process has two stages. Light-dependent reactions capture energy, and the Calvin cycle fixes carbon.\n\n" +
                "Chlorophyll absorbs most strongly in the blue and red spectrum."
        ),
        Fixture(
            "F2_HEADINGS",
            "# Level one\n## Level two\n### Level three\n#### Level four\n##### Level five\n###### Level six\n\nBody after headings."
        ),
        Fixture(
            "F3_NESTED_LISTS",
            "- Fruits\n  - Citrus\n    - Blood orange\n    - Yuzu\n  - Stone fruit\n- Vegetables\n  1. Root\n  2. Leafy"
        ),
        Fixture(
            "F4_BLOCKQUOTES",
            "> Simple quote line.\n\n> First line of a quote\n> continues here.\n>\n> Second paragraph in the same quote."
        ),
        Fixture(
            "F5_LINKS",
            "Read the [Compose docs](https://developer.android.com/jetpack/compose) first.\n\n" +
                "Or visit https://kotlinlang.org, then [SwiftUI](https://developer.apple.com/xcode/swiftui/).\n\n" +
                "A link ends a sentence at https://example.com."
        ),
        Fixture(
            "F6_INLINE_STYLES",
            "Mix **bold**, *italic*, ~~gone~~, and `inline_code()` together.\n\n" +
                "**Bold with *nested italic* inside** stays readable."
        ),
        Fixture("F7_KOTLIN_CODE", KOTLIN_SNIPPET),
        Fixture("F8_PYTHON_CODE", PYTHON_SNIPPET),
        Fixture("F9_JSON", JSON_SNIPPET),
        Fixture("F10_SQL", SQL_SNIPPET),
        Fixture(
            "F11_TABLE",
            "| Feature | Android | iOS |\n" +
                "|:--------|:-------:|----:|\n" +
                "| Streaming | yes | yes |\n" +
                "| Tables | **now** | now |\n" +
                "| Math | partial | partial |\n" +
                "| A very long feature name that should force scrolling | always | always |"
        ),
        Fixture(
            "F12_MIXED_MARKDOWN",
            "## Release notes\n\nThis release **ships** the renderer.\n\n- Faster streaming\n- Richer blocks\n\n" +
                "```json\n{\"ok\": true}\n```\n\n> Content is now block-based.\n\n" +
                "| Block | Status |\n|-------|--------|\n| Code | done |"
        ),
        Fixture(
            "F13_INLINE_MATH",
            "Einstein wrote \\(E = mc^2\\), and Euler gave us \$e^{i\\pi} + 1 = 0\$.\n\nWater is H\\(_2\\)O."
        ),
        Fixture("F14_BLOCK_MATH", BLOCK_MATH),
        Fixture("F15_MERMAID_FLOWCHART", MERMAID_FLOWCHART),
        Fixture("F16_MERMAID_SEQUENCE", MERMAID_SEQUENCE),
        Fixture("F17_MERMAID_INVALID", MERMAID_INVALID),
        Fixture(
            "F18_MULTIPLE_CODE",
            "First block:\n\n```kotlin\nval x = 1\n```\n\nMiddle prose.\n\n```python\ny = 2\n```\n\nDone."
        ),
        Fixture("F19_HUGE_RESPONSE", hugeResponse()),
        Fixture("F20_STREAM_PARTIAL_FENCE", "Here is a start:\n\n```kotlin\nfun hello("),
        Fixture("F21_STREAM_PARTIAL_TABLE", "| Name | Value |\n|------\n| Alpha | 1 |"),
        Fixture("F22_STREAM_PARTIAL_MARKDOWN", "**important\n- item one\n## Head"),
        Fixture(
            "F23_CITATION_METADATA",
            "The answer cites sources[1][2].",
            RichMetadata(
                citations = listOf(
                    CitationEntry(1, "Compose performance", "developer.android.com", "Best practices for stable lambdas"),
                    CitationEntry(2, "SwiftUI rendering", "developer.apple.com", "View identity and rendering")
                )
            )
        ),
        Fixture(
            "F24_TOOL_RESULT",
            "Searching now.",
            RichMetadata(
                toolResults = listOf(
                    ToolResultEntry("search", "completed", "Searched 12 sources", "Query: compose streaming performance")
                )
            )
        ),
        Fixture(
            "F25_COMBINED",
            "## Analysis\n\nGrowth follows `y = a e^{kt}` curves \\(k > 0\\).\n\n- step one\n- step two\n\n" +
                "| Metric | Q1 | Q2 |\n|--------|----|----|\n| Revenue | 10 | 14 |\n\n" +
                "```kotlin\nval ok = true\n```\n\n> Trends compound.\n\n" +
                "Details: [Spec](https://example.com/spec)\n\n---\n\n" +
                BLOCK_MATH + "\n\n" +
                "```mermaid\nflowchart LR\n    A --> B --> C\n```"
        )
    )
}

/** One fixture's parse summary — kind counts plus any unexpected failure. */
private data class FixtureSummary(val kinds: Map<String, Int>, val errors: List<String>)

private fun summarize(blocks: List<Block>): FixtureSummary {
    val kinds = LinkedHashMap<String, Int>()
    val errors = mutableListOf<String>()
    fun walk(block: Block) {
        val name = when (block) {
            is Block.Paragraph -> "Paragraph"
            is Block.Heading -> "Heading"
            is Block.BulletList -> "BulletList"
            is Block.OrderedList -> "OrderedList"
            is Block.BlockQuote -> "BlockQuote"
            is Block.CodeBlock -> "CodeBlock"
            is Block.TableBlock -> "TableBlock"
            is Block.Divider -> "Divider"
            is Block.MathBlock -> "MathBlock"
            is Block.MermaidBlock -> "MermaidBlock"
            is Block.ImageBlock -> "ImageBlock"
            is Block.CollapsibleBlock -> "Collapsible"
            is Block.CitationsBlock -> "Citations"
            is Block.ToolResultBlock -> "ToolResult"
        }
        kinds[name] = (kinds[name] ?: 0) + 1
        when (block) {
            is Block.BulletList -> block.items.forEach { item -> item.children.forEach(::walk) }
            is Block.OrderedList -> block.items.forEach { item -> item.children.forEach(::walk) }
            is Block.BlockQuote -> block.blocks.forEach(::walk)
            is Block.CollapsibleBlock -> block.blocks.forEach(::walk)
            else -> Unit
        }
    }
    blocks.forEach(::walk)
    return FixtureSummary(kinds, errors)
}

/**
 * Parses every fixture and returns a human-readable report — block-kind counts
 * per fixture. Used during development to validate the parser on both
 * platforms; carries no runtime behaviour.
 */
fun sanityReport(): String {
    val sb = StringBuilder()
    for (fixture in ContentFixtures.all) {
        val summary = try {
            summarize(parseBlocks(fixture.content, fixture.metadata))
        } catch (t: Throwable) {
            FixtureSummary(emptyMap(), listOf("PARSE THREW: ${t.message}"))
        }
        val counts = summary.kinds.entries.joinToString(", ") { "${it.key}:${it.value}" }
        val suffix = if (summary.errors.isEmpty()) "" else "  ERRORS: ${summary.errors.joinToString("; ")}"
        sb.append(fixture.id).append(" → ").append(counts).append(suffix).append('\n')
    }
    return sb.toString()
}
