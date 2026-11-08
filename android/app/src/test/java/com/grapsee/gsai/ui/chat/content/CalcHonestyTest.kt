package com.grapsee.gsai.ui.chat.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalcHonestyTest {
    @Test fun citationsCannotForgeTrustOrAcademicAuthorityThroughLabelsPathsOrCredentials() {
        assertEquals("www.nature.com", citationSourceUrl("https://www.nature.com/article")?.host)
        assertEquals(true, isAcademicSourceDomain("untrusted.example", "https://www.nature.com/article"))
        for (raw in listOf("https://untrusted.example/nature.com?publisher=arxiv.org", "https://nature.com.untrusted.example/paper", "https://untrusted.example/?from=university.edu")) {
            assertEquals(false, isAcademicSourceDomain("nature.com", raw))
        }
        for (raw in listOf("javascript:alert(1)", "file:///private/file", "https://nature.com@untrusted.example/paper", "https://example.org:99999/a")) {
            assertNull(citationSourceUrl(raw))
            assertEquals(false, isAcademicSourceDomain("nature.com", raw))
        }
    }

    @Test fun timersHaveExplicitSecondsNotPricesAndPassTheActualParserWithoutFakeItems() {
        val configured = parseWidget("app", """{"type":"timer","durationSeconds":5}""") as Widget.App
        assertEquals(5.0, configured.w.durationSeconds!!, 0.0)
        val empty = parseWidget("app", """{"type":"timer"}""") as Widget.App
        assertNull(empty.w.durationSeconds)
        val monetary = parseWidget("app", """{"type":"timer","items":[{"price":25}]}""") as Widget.App
        assertNull(monetary.w.durationSeconds)
        for (duration in listOf("0", "-1", "1.5", "604801", "true", "\"$25\"")) {
            assertNull(parseWidget("app", "{\"type\":\"timer\",\"durationSeconds\":" + duration + "}"))
        }
    }

    @Test fun checklistRefusesInvalidEntriesAndOverflowInsteadOfDeletingSourceTasks() {
        for (json in listOf("""{"items":[{"label":"Actual"},{"alien":42}]}""", """{"items":["Actual",42]}""",
            "{\"items\":[" + (1..31).joinToString(",") { "{\"label\":\"Task $it\"}" } + "]}")) {
            assertNull(parseWidget("checklist", json))
        }
    }

    @Test fun deferredPromotedSourceEditWaitsForTheExactSourceCard() {
        val actions = ResponseCodeActions()
        var targetEdits = 0
        var unrelatedEdits = 0
        actions.editOnRegistration(42)
        actions.register(7, ResponseCodeActions.Entry("text", { "unrelated" }, { unrelatedEdits++ }))
        assertEquals(0, unrelatedEdits)
        actions.register(42, ResponseCodeActions.Entry("text", { "original visual source" }, { targetEdits++ }))
        assertEquals(1, targetEdits)
        actions.register(42, ResponseCodeActions.Entry("text", { "original visual source" }, { targetEdits++ }))
        assertEquals(1, targetEdits)
    }

    @Test fun wholeJsonPreservesMachineStringsBeforeMarkdownAndStreaming() {
        val documents = listOf("{}", "[]", "[1,2,3]", "[{\"literal\":\"**bold** :rocket: 😀\"}]",
            " \r\n{\"literal\":\"**bold** :rocket: 😀\",\"nested\":{\"x\":true}}\r\n ")
        for (source in documents) {
            val blocks = parseBlocks(source)
            assertEquals(1, blocks.size)
            val block = blocks.single() as Block.CodeBlock
            assertEquals("json", block.language)
            assertEquals(source, block.code)
            var previous = ""
            var streamed: List<Block> = emptyList()
            for (end in 1..source.length) {
                val current = source.substring(0, end)
                streamed = parseStreamingBlocks(previous, streamed, current)
                assertEquals(parseBlocks(current), streamed)
                previous = current
            }
        }
    }

    @Test fun invalidDataDoesNotEscapeNativeCapacityAsAnUnrelatedEditor() {
        val table = "{\"columns\":[\"Actual\"],\"rows\":[" + (1..101).joinToString(",") { "[$it]" } + "]}"
        assertNull(parseWidget("table", table))
        assertNull(inferWidgetKind(table))
        val series = (1..10).joinToString(",") { "{\"name\":\"S$it\",\"data\":[[0,$it]]}" }
        val chart = parseWidget("chart", "{\"type\":\"line\",\"series\":[$series]}") as Widget.Chart
        assertEquals(10, chart.w.series.size)
    }

    @Test fun contextualCodeActionsAreResponseScopedAndPreserveRealDrafts() {
        val first = ResponseCodeActions()
        val other = ResponseCodeActions()
        var source = "original"
        var edits = 0
        first.editFirst()
        first.register(7, ResponseCodeActions.Entry("javascript", { source }, { edits++ }))
        assertEquals(1, edits)
        source = "actually edited"
        assertEquals("actually edited", first.source(7)?.second)
        first.remove(7)
        assertEquals("actually edited", first.source(7)?.second)
        assertNull(other.source(7))
        first.register(7, ResponseCodeActions.Entry("javascript", { source }, { edits++ }))
        first.editFirst()
        assertEquals(2, edits)
    }

    @Test fun sourceArchivePreservesBytesAndDoesNotInventAProject() {
        val source = "বাংলা 日本語\r\n" + String(Character.toChars(0x1f600)) + "\u0000"
        val bytes = responseSourceArchive(listOf("python" to source, "../../unsafe" to "second"))
        val files = linkedMapOf<String, String>()
        java.util.zip.ZipInputStream(bytes.inputStream(), Charsets.UTF_8).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                files[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        assertEquals(listOf("README.txt", "snippet-1.py", "snippet-2.txt"), files.keys.toList())
        assertEquals(source, files["snippet-1.py"])
        org.junit.Assert.assertTrue(files["README.txt"]!!.contains("not a complete runnable project"))
        assertEquals("js", responseSourceExtension("JAVASCRIPT"))
    }

    @Test fun sharedResponseReportIncludesActualTableAndWidgetData() {
        val blocks = parseBlocks("| Name | Amount |\n| --- | --- |\n| Actual | 42 |\n\n```chart\n{\"labels\":[\"A\"],\"values\":[73]}\n```")
        val report = extractCopyableBlockSummary(blocks, false)
        org.junit.Assert.assertTrue(report.contains("Actual\t42"))
        org.junit.Assert.assertTrue(report.contains("73"))
        assertEquals("", extractCopyableBlockSummary(emptyList(), true))
    }

    @Test fun codeValidationRejectsNonJsonAndNeverPretendsToRunPython() {
        for (source in listOf("print(6*7)", "x=7\nprint(x)", "import pandas as pd", "while True: pass")) {
            val result = executeCodePreview("python", source)
            assertEquals("", result.output)
            org.junit.Assert.assertTrue(result.error!!.contains("Not executed: no Python runtime"))
        }
        val invalid = listOf("{'x':1}", "{x:1}", "{\"x\":1,}", "[1,]", "{\"x\":1} false", "01", "NaN", "Infinity", "[1;2]", "//comment\n{}", "\"raw\nnewline\"", "\"bad\\x\"", "\"bad\\u123x\"", "\u00a0{}", "")
        for (source in invalid) {
            val result = executeCodePreview("json", source)
            org.junit.Assert.assertNotNull("Invalid JSON must not validate: $source", result.error)
            assertEquals("", result.output)
        }
        for (source in listOf("{\"x\":1,\"s\":\"বাংলা\"}", "[true,false,null,-1.25e+3,\"NaN\"]", "true", "null", "\"😀\"")) {
            val result = executeCodePreview("json", source)
            assertNull(result.error)
            org.junit.Assert.assertTrue(result.output.isNotBlank())
        }
    }

    @Test fun nativeFencesPreserveEverySourceSeparatorAndBlankLine() {
        val sources = listOf("", "\n", "\r\n", "\n\nconst value = 7;\n\n", "\r\nconst value = 7;\r\n\r\n", "বাংলা 日本語\r\n\n\r\n", "const key = \"😀\";\rkeep")
        for (marker in listOf("```", "````", "~~~~~")) {
            for (separator in listOf("\n", "\r\n")) {
                for (source in sources) {
                    val block = parseBlocks(marker + "javascript" + separator + source + separator + marker).single() as Block.CodeBlock
                    assertEquals("javascript", block.language)
                    assertEquals("Source must survive $marker with separator ${separator.toByteArray().toList()}", source, block.code)
                    assertEquals(false, block.open)
                }
            }
        }
        assertEquals("const value = 7;\r", (parseBlocks("```javascript\nconst value = 7;\r").single() as Block.CodeBlock).code)
        val source = "first\n```\n~~~\nlast\n\n"
        val block = parseBlocks("````javascript\n" + source + "\n`````").single() as Block.CodeBlock
        assertEquals(source, block.code)
        assertEquals(false, block.open)
        val html = "<!doctype html>\r\n<html>\r\n\r\n<body>Actual source</body></html>\r\n"
        assertEquals(html, (parseBlocks("~~~~html\r\n" + html + "\r\n~~~~").single() as Block.HtmlBlock).code)
        assertEquals("chart", (parseBlocks("~~~chart\n{\"labels\":[\"A\"],\"values\":[7]}\n~~~").single() as Block.WidgetBlock).kind)
    }

    @Test(timeout = 5000) fun nativeStreamingMatchesFullParseAtEveryFencePrefix() {
        val prefix = "Intro.\n\nStable paragraph.\n\n"
        val documents = listOf(
            prefix + "````javascript\r\n\r\nconst value = 7;\r\n\r\n```\r\nconsole.log(value);\n\n````\nAfter fence.\n\nFinal.",
            prefix + "~~~~html\r\n<!doctype html>\r\n\r\n<html><body><button>Run</button></body></html>\r\n~~~~\r\nAfter app.",
            prefix + "> ````javascript\r\n> \r\n> const value = 7;\r\n> \r\n> ````\r\n\r\nAfter quote."
        )
        for (document in documents) {
            var previous = ""
            var blocks: List<Block> = emptyList()
            for (end in 1..document.length) {
                val current = document.substring(0, end)
                blocks = parseStreamingBlocks(previous, blocks, current)
                assertEquals("Streaming must not freeze a spanning block at prefix $end", parseBlocks(current), blocks)
                previous = current
            }
        }
    }

    @Test fun pieSwitchPreservesEveryObservationAndRejectsInvalidShares() {
        val source = ChartWidget(labels = listOf("A", "B", "C"), series = listOf(
            ChartSeries("First", listOf(listOf(0f, 10f), listOf(1f, 20f), listOf(2f, 30f))),
            ChartSeries("Second", listOf(listOf(0f, 5f), listOf(1f, 15f)))
        ))
        assertEquals(listOf("First: A", "First: B", "First: C", "Second: A", "Second: B"), pieSlices(source).map { it.first })
        assertEquals(listOf(10f, 20f, 30f, 5f, 15f), pieSlices(source).map { it.second })
        assertNull(pieSourceProblemAndroid(source))
        val mixed = source.copy(labels = listOf("P4", "P5"), series = listOf(
            ChartSeries("Long", listOf(listOf(0f, 7f), listOf(1f, 8f))),
            ChartSeries("Short", listOf(listOf(0f, 104f)))
        ))
        assertEquals(listOf("Long: P4", "Long: P5", "Short: P4"), pieSlices(mixed).map { it.first })
        for (values in listOf(listOf(-10f, 20f), listOf(0f, 0f), listOf(Float.NaN, 10f))) {
            val invalid = source.copy(series = listOf(ChartSeries("Invalid", values.mapIndexed { i, value -> listOf(i.toFloat(), value) })))
            org.junit.Assert.assertNotNull(pieSourceProblemAndroid(invalid))
            assertEquals(values.size, pieSlices(invalid).size)
        }
    }

    @Test fun rangeDoesNotRestoreShortSeriesOutsideWindow() {
        val chart = ChartWidget(labels = (0..7).map { "P$it" }, series = listOf(
            ChartSeries("long", (0..7).map { listOf(it.toFloat(), (it * 10).toFloat()) }),
            ChartSeries("short", (0..4).map { listOf(it.toFloat(), (100 + it).toFloat()) })
        ))
        val (tail, _) = sliceChartByRange(chart, 2)
        assertEquals(listOf("P4", "P5", "P6", "P7"), tail.labels)
        assertEquals(listOf(40f, 50f, 60f, 70f), tail.series[0].data.map { it[1] })
        assertEquals(listOf(104f), tail.series[1].data.map { it[1] })
        val (withoutOldData, _) = sliceChartByRange(chart.copy(series = chart.series + ChartSeries("absent", listOf(listOf(0f, 9f)))), 2)
        assertEquals(listOf("long", "short"), withoutOldData.series.map { it.name })
    }

    @Test fun tableAllIsNotCappedAtTwentyFive() {
        assertEquals(73, widgetTablePageSizeAndroid(-1, 73))
        assertEquals(2, widgetTablePageSizeAndroid(-1, 2))
        assertEquals(1, widgetTablePageSizeAndroid(-1, 0))
        assertEquals(3, widgetTablePageSizeAndroid(3, 73))
        val rows = (1..73).toList()
        assertEquals(rows, rows.take(widgetTablePageSizeAndroid(-1, rows.size)))
    }

    @Test fun tooltipDeltaRequiresAnActualPriorPoint() {
        assertNull(chartStepPercentAndroid(listOf(20.0, 30.0), 0))
        assertNull(chartStepPercentAndroid(listOf(0.0, 30.0), 1))
        assertNull(chartStepPercentAndroid(listOf(20.0), 5))
        assertEquals(50.0, chartStepPercentAndroid(listOf(20.0, 30.0), 1)!!, 1e-9)
    }
    @Test fun zeroBaselineGrowthIsUndefinedRatherThanFabricated() {
        assertNull(chartGrowthPercentAndroid(0.0, 25.0))
        assertNull(chartGrowthPercentAndroid(0.0, 0.0))
        assertEquals(50.0, chartGrowthPercentAndroid(20.0, 30.0)!!, 1e-9)
        assertEquals(50.0, chartGrowthPercentAndroid(-20.0, -10.0)!!, 1e-9)
    }
    @Test fun percentagesHaveOneMeaningAcrossMagnitudeBoundary() {
        assertEquals("99.00%", formatCalc(0.99, "percent", ""))
        assertEquals("100.00%", formatCalc(1.0, "percent", ""))
        assertEquals("101.00%", formatCalc(1.01, "percent", ""))
        assertEquals("-120.00%", formatCalc(-1.2, "percent", ""))
        assertEquals("0.99%", formatCalc(0.99, "number", "%"))
    }
    @Test fun missingBoundsAndValuesAreNotInvented() {
        val field = CalcField(key = "p", label = "Principal")
        assertNull(field.min)
        assertNull(field.max)
        org.junit.Assert.assertTrue(field.value.isNaN())
    }
    @Test fun requiresWholeExpression() {
        for (expr in listOf("2 garbage", "2 3", "(2+3", "missing + 1", "1/0", "sqrt(-1)")) {
            assertNull(expr, CalcExpr.eval(expr, emptyMap()))
        }
    }
    @Test fun exponentPrecedenceAndActualInputs() {
        assertEquals(-4.0, CalcExpr.eval("-2**2", emptyMap())!!, 1e-9)
        assertEquals(512.0, CalcExpr.eval("2**3**2", emptyMap())!!, 1e-9)
        assertEquals(276.0, CalcExpr.eval("amount*(1+rate/100)", mapOf("amount" to 240.0, "rate" to 15.0))!!, 1e-9)
    }
}
