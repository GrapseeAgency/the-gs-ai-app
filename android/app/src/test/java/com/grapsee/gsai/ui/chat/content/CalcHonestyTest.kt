package com.grapsee.gsai.ui.chat.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalcHonestyTest {
    @Test fun nativeChartSelectionKeepsDataMeaningAndEverySignedObservation() {
        val cases = listOf(
            listOf("Jan", "Feb", "Mar") to "line", listOf("2022", "2023", "2024") to "line",
            listOf("Q1 2024", "Q2 2024", "Q1 2025") to "line",
            listOf("2024-01", "2024-02", "2024-03") to "line",
            listOf("Mar", "Jan", "Feb") to "bar", listOf("2024-02-31", "2024-03-01") to "bar",
            listOf("Alpha", "Beta") to "bar", listOf("A supplied category with a long name", "Another supplied category") to "hbar"
        )
        for ((labels, type) in cases) {
            assertEquals(labels.toString(), type, categoricalChartType(labels))
            val headers = listOf("Period", "Actual measure").map { listOf(InlineSpan.Text(it)) }
            val rows = labels.mapIndexed { index, label -> listOf(listOf(InlineSpan.Text(label)), listOf(InlineSpan.Text("${index + 1}"))) }
            assertEquals(type, chartFromTableBlock(Block.TableBlock(headers, List(2) { TableAlign.LEFT }, rows, 0))!!.type)
        }
        val chart = ChartWidget(type = "hbar", labels = (1..13).map { "Actual $it" },
            series = listOf(ChartSeries(name = "Actual", data = (1..13).map { listOf(it.toFloat(), (it - 7).toFloat()) })))
        val observations = horizontalChartObservations(chart)!!
        assertEquals(13, observations.size)
        assertEquals(-6f, observations.first().value, 0f)
        assertEquals("Actual 13", observations.last().label)
        assertEquals(26, horizontalChartObservations(chart.copy(series = chart.series + chart.series))!!.size)
        assertNull(horizontalChartObservations(chart.copy(series = listOf(ChartSeries(data = listOf(listOf(0f)))))))
        assertEquals(false, pieAlternativeAllowed(chart.copy(type = "line")))
        assertEquals(false, pieAlternativeAllowed(ChartWidget(type = "line", unit = "%", series = listOf(ChartSeries(data = listOf(listOf(0f,2f),listOf(1f,3f)))))))
        assertEquals(true, pieAlternativeAllowed(ChartWidget(type = "bar", unit = "%", series = listOf(ChartSeries(data = listOf(listOf(0f,40f),listOf(1f,60f)))))))
    }

    @Test fun actualCalculatorParserKeepsThirteenthInputSixthChoiceAndNinthOutput() {
        val fields = (1..13).joinToString(",") { """{"key":"x$it","label":"Input $it","value":$it}""" }
        val choices = (1..6).joinToString(",") { """{"label":"Option $it","value":$it}""" }
        val outputs = (1..8).joinToString(",") { """{"label":"Result $it","expr":"x1"}""" }
        val source = """{"fields":[$fields,{"key":"option","label":"Choice","value":1,"choices":[$choices]}],"outputs":[$outputs,{"label":"Final result","expr":"x12+x13+option"}]}"""
        val calc = (parseWidget("calc", source) as Widget.Calc).w
        assertEquals(14, calc.fields.size)
        assertEquals("x13", calc.fields[12].key)
        assertEquals(6, calc.fields.last().choices.size)
        assertEquals(9, calc.outputs.size)
        assertEquals(256.0, CalcExpr.eval(calc.outputs.last().expr,
            calc.fields.associate { it.key to it.value } + mapOf("x12" to 120.0, "x13" to 130.0, "option" to 6.0))!!, 0.0)
    }

    @Test fun actualChartDecoderNeverCompactsMissingObservationsOrFabricatesZero() {
        for (invalid in listOf("""null""", """[0,null]""", """[0,true]""", """[0,1e100]""", """[0,10,null]""", """[null,10]""", """[0,10,"unknown"]""", """{"x":1,"y":null}""", """{"x":null,"y":10}""", """{"x":1,"y":10,"z":null}""", """{"open":1,"high":2,"low":null,"close":1}""")) {
            val source = """{"type":"line","labels":["Before","Missing","After"],"series":[{"name":"Actual","data":[[0,10],$invalid,[2,30]]}]}"""
            assertNull("Preserve source rather than chart missing observation: $invalid", parseWidget("chart", source))
            assertNull("Pie expansion must not hide a missing measurement: $invalid", parseWidget("chart", source.replace("\"line\"", "\"pie\"")))
        }
        val source = """{"type":"line","labels":["Loss","Zero","Gain"],"series":[{"name":"Actual","data":[[0,-10],[1,0],[2,30]]}]}"""
        val actual = (parseWidget("chart", source) as Widget.Chart).w.series.single().data
        assertEquals(listOf(-10f, 0f, 30f), actual.map { it[1] })
        assertEquals(listOf(0f, 1f, 2f), actual.map { it[0] })
        val categories = (parseWidget("chart", """{"type":"bar","series":[{"data":[["Jan",10],["Feb",20]]}]}""") as Widget.Chart).w.series.single().data
        assertEquals(listOf(listOf(0f, 10f), listOf(1f, 20f)), categories)
        val candle = (parseWidget("chart", """{"type":"candlestick","series":[{"data":[{"open":10,"high":20,"low":5,"close":15}]}]}""") as Widget.Chart).w.series.single().data.single()
        assertEquals(listOf(10f, 20f, 5f, 15f), candle)
        val pie = (parseWidget("chart", """{"type":"pie","labels":["A","B"],"series":[{"data":[[0,10,99],[1,20,88]]}]}""") as Widget.Chart).w
        assertEquals(listOf(10f, 20f), pie.series.map { it.data.single()[1] })
        assertEquals(listOf("A", "B"), pie.series.map { it.name })
        val namedPie = (parseWidget("chart", """{"type":"pie","series":[{"data":[["Food",10],["Rent",20]]}]}""") as Widget.Chart).w
        assertEquals(listOf("Food", "Rent"), namedPie.series.map { it.name })
        assertEquals(listOf(10f, 20f), namedPie.series.map { it.data.single()[1] })
    }

    @Test fun nativeDialectRepairNeverRewritesQuotedSourceOrSuppliedText() {
        val source = "const text = " + 34.toChar() + " True False None " + 34.toChar() + "; const slash = " + 92.toChar() + 34.toChar() + " True " + 92.toChar() + 34.toChar()
        val payload = kotlinx.serialization.json.JsonObject(mapOf(
            "code" to kotlinx.serialization.json.JsonPrimitive(source),
            "title" to kotlinx.serialization.json.JsonPrimitive(" True False None ")
        )).toString()
        assertEquals(payload, cleanWidgetDialectLiteralsAndroid(payload))
        val normalized = kotlinx.serialization.json.Json.parseToJsonElement(normalizeWidgetJson("app", payload)) as kotlinx.serialization.json.JsonObject
        assertEquals(source, (normalized["code"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals(" True False None ", (normalized["title"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals("""{"enabled":true,"selected":false,"value":null,"text":" True False None "}""",
            cleanWidgetDialectLiteralsAndroid("""{"enabled":True,"selected":False,"value":None,"text":" True False None "}"""))
        assertEquals("""{"True":" False ","NotTrue":" None "}""",
            cleanWidgetDialectLiteralsAndroid("""{"True":" False ","NotTrue":" None "}"""))
    }

    @Test fun actualCategoryLabelsAndDatasetCountsSurviveModelShapeNormalization() {
        val pairs = (parseWidget("chart", """{"type":"line","series":[{"data":[["January",10],["February",20]]}]}""") as Widget.Chart).w
        assertEquals(listOf("January", "February"), pairs.labels)
        val objects = (parseWidget("chart", """{"type":"bar","data":[{"label":"Food","value":10},{"name":"Rent","value":20}]}""") as Widget.Chart).w
        assertEquals(listOf("Food", "Rent"), objects.labels)
        val explicit = (parseWidget("chart", """{"type":"bar","labels":["Supplied A","Supplied B"],"series":[{"data":[["Other A",10],["Other B",20]]}]}""") as Widget.Chart).w
        assertEquals(listOf("Supplied A", "Supplied B"), explicit.labels)
        val dataset = (parseWidget("chart", """{"type":"line","datasets":[{"label":"Actual","values":[["Jan",10],["Feb",20]]}]}""") as Widget.Chart).w
        assertEquals(listOf("Jan", "Feb"), dataset.labels)
        assertEquals("Actual", dataset.series.single().name)
        for (bad in listOf("""null""", """{"label":"Missing"}""", """{"data":null}""", """{"values":"not observations"}""")) {
            assertNull("Never drop the malformed dataset beside a valid one: $bad",
                parseWidget("chart", """{"type":"line","datasets":[{"data":[10,20]},$bad]}"""))
        }
    }

    @Test fun actualChartDomainsRetainLossesZeroAndSourcePeakWithoutDoublingTheRange() {
        val signed = chartValueDomainAndroid(listOf(-20f, 0f, 10f))
        assertEquals(-20.0, signed.lower, 0.0)
        assertEquals(10.0, signed.upper, 0.0)
        assertEquals(1f, signed.heightFraction(-20f), 0f)
        assertEquals(1f / 3f, signed.heightFraction(0f), 0.000001f)
        assertEquals(0f, signed.heightFraction(10f), 0f)
        val sales = chartValueDomainAndroid(listOf(22800f, 34200f, 50400f, 75600f, 60500f, 78400f))
        assertEquals(78400.0, sales.upper, 0.0)
        assertEquals(0f, sales.heightFraction(78400f), 0f)
        assertEquals(0.0, chartValueDomainAndroid(listOf(-5f, -2f)).upper, 0.0)
        assertEquals(1.0, chartValueDomainAndroid(listOf(0f, 0f)).upper, 0.0)
        val extreme = chartValueDomainAndroid(listOf(-Float.MAX_VALUE, Float.MAX_VALUE))
        assertEquals(0.5f, extreme.heightFraction(0f), 0f)
        assertEquals(Float.MAX_VALUE, extreme.axisValue(0f), 0f)
        assertEquals(-Float.MAX_VALUE, extreme.axisValue(1f), 0f)
    }

    @Test fun axisLabelsCoverTheCompleteActualObservationDomain() {
        assertEquals(listOf(0, 4, 9, 13, 17, 21, 26, 30), chartAxisLabelIndicesAndroid(31))
        assertEquals((0 until 6).toList(), chartAxisLabelIndicesAndroid(6))
        assertEquals(emptyList<Int>(), chartAxisLabelIndicesAndroid(0))
        assertEquals(listOf(30), chartAxisLabelIndicesAndroid(31, 1))
    }

    @Test fun markdownNumbersKeepDirectionalSignsAndRejectMalformedCells() {
        for ((raw, expected) in listOf("↓ 1.2%" to -1.2, "↘ -1.2%" to -1.2, "↑ +0.5%" to 0.5, "−0.3%" to -0.3, "-\$1,200.50" to -1200.5, "\$-1.2k" to -1200.0, "৳2M" to 2e6, "2x" to 2.0, "0" to 0.0)) {
            assertEquals(raw, expected, parseSortableNumberAndroid(raw)!!, 0.0)
        }
        for (raw in listOf("1,00", "1+2", "1 2", "1e3", "NaN", "Infinity", "↓+1%", "↑-1%", "--1", "+-1", "growth 12%", "1.2.3")) {
            assertNull(raw, parseSortableNumberAndroid(raw))
        }
    }

    @Test fun markdownChartUsesRealMeasureLabelsUnitsAndEverySourceRow() {
        fun cell(text: String): List<InlineSpan> = listOf(InlineSpan.TextSpan(text))
        val headers = listOf("#", "Language", "Popularity").map(::cell)
        val rows = (1..31).map { listOf(cell("$it"), cell("Language $it"), cell("$it%")) }
        val table = Block.TableBlock(headers, List(3) { TableAlign.LEFT }, rows, 0)
        val chart = chartFromTableBlock(table)!!
        assertEquals("Popularity", chart.title)
        assertEquals("%", chart.unit)
        assertEquals(31, chart.series.single().data.size)
        assertEquals("Language 31", chart.labels.last())
        assertEquals(31f, chart.series.single().data.last().last(), 0f)
        for (bad in listOf("not recorded", "\$3")) {
            val changed = rows.mapIndexed { index, row -> if (index == 2) listOf(row[0], row[1], cell(bad)) else row }
            assertNull(chartFromTableBlock(table.copy(rows = changed)))
        }
    }

    @Test fun learningKeepsCompleteDefinitionsAndRejectsAmbiguousAnswerAlternatives() {
        val definition = List(6) { "A supplied definition with an important source qualification." }.joinToString(" ")
        val source = listOf("Alpha", "Beta", "Gamma").joinToString("\n") { "- **$it**: $definition $it." }
        val concepts = extractSpontaneousConceptsFromBlocks(parseBlocks(source))
        assertEquals(3, concepts.size)
        assertEquals("$definition Alpha.", concepts.first().definition)
        val duplicates = listOf("Alpha", "Beta", "Gamma").joinToString("\n") { "- **$it**: $definition" }
        assertEquals(1, extractSpontaneousConceptsFromBlocks(parseBlocks(duplicates)).size)
    }

    @Test fun spontaneousLearningUsesDefinitionsButDoesNotTurnSourcesOrPlansIntoQuizzes() {
        val definitions = "- Atom: A basic unit of a chemical element.\n- Molecule: Two or more atoms bonded together.\n- Element: A substance made of one type of atom."
        assertEquals(3, extractSpontaneousConceptsFromBlocks(parseBlocks("## Core concepts\n\n$definitions")).size)
        for (heading in listOf("Sources", "Key Sources", "References", "Step-by-step plan", "Next actions")) {
            assertEquals(heading, 0, extractSpontaneousConceptsFromBlocks(parseBlocks("## $heading\n\n$definitions")).size)
        }
        assertEquals(3, extractSpontaneousConceptsFromBlocks(parseBlocks("## Sources\n\n- A supplied source record.\n\n## Core concepts\n\n$definitions")).size)
    }

    @Test fun proseScenarioInferenceNeverTreatsMarkupAsAMillionOrChangesUnsupportedSourceTerms() {
        assertEquals(100.0, extractQuickCalcParams("$100 markup at 10%")!!.first, 0.0)
        assertEquals(1200.0, extractQuickCalcParams("€1.2k with 10% tax")!!.first, 0.0)
        assertEquals(2000000.0, extractQuickCalcParams("$2M loan at 6% for 5 years")!!.first, 0.0)
        for (wire in listOf("$1e6 loan at 10% for 5 years", "$1,00 discount 10%", "-$100 discount 10%", "$100 discount -10%", "$100 discount −10%", "$100 discount 1e2%", "$100 loan at 10%", "$100 loan at 10% for 0 years", "$100 loan at 10% for 99 years")) {
            assertNull(wire, extractQuickCalcParams(wire))
        }
    }

    @Test fun actualScenarioSeedsNeverRoundSmallAPRToZeroOrTruncateSourceAmounts() {
        assertEquals("0.0012", sourceScenarioNumberText(0.0012))
        assertEquals("0.0000000012", sourceScenarioNumberText(0.0000000012))
        assertEquals("100.999", sourceScenarioNumberText(100.999))
        assertEquals("120000000000000000000", sourceScenarioNumberText(1.2e20))
        assertEquals("0", sourceScenarioNumberText(0.0))
        assertEquals("", sourceScenarioNumberText(Double.NaN))
    }

    @Test fun sourceLoanPaymentsKeepTrueZeroAndSmallRateInterestWithoutInventedRounding() {
        // Independent 90-digit Decimal amortization references.
        assertEquals(1000.0 / 12, sourceLoanMonthlyPayment(1000.0, 0.0, 1)!!, 1e-9)
        assertEquals(88.84878867834170734, sourceLoanMonthlyPayment(1000.0, 12.0, 1)!!, 1e-9)
        assertEquals(2778279.1966664201205, sourceLoanMonthlyPayment(1e9, 0.0012, 30)!!, 1e-6)
        assertEquals(2777777.7782791666667, sourceLoanMonthlyPayment(1e9, 0.0000000012, 30)!!, 1e-6)
        assertNull(sourceLoanMonthlyPayment(-1.0, 5.0, 1))
        assertNull(sourceLoanMonthlyPayment(1000.0, -5.0, 1))
        assertNull(sourceLoanMonthlyPayment(1000.0, 5.0, 0))
        assertNull(sourceLoanMonthlyPayment(Double.NaN, 5.0, 1))
    }

    @Test fun ordinaryMarkdownSourcesUseOneHeadingAndOnlyActualCompleteSourceDestinations() {
        for (marker in listOf("1.", "-")) {
            val wire = "## Key Sources\n\n$marker [IEA](https://www.iea.org/) - Supplied report.\n${if (marker == "1.") "2." else "-"} [IRENA](https://www.irena.org/) - Supplied publication."
            val parsed = parseBlocks(wire)
            val card = requireNotNull(sourceCardForHeading(parsed[0], parsed[1]))
            assertEquals("Key Sources", card.second)
            assertEquals(listOf("https://www.iea.org/", "https://www.irena.org/"), card.first.citations.map { it.url })
        }
        // Recognising a publisher's name is not evidence for any destination.
        for (name in listOf("IEA", "IRENA", "Nature", "React", "Tailwind", "MDN", "Python", "Nexly", "arXiv", "IEEE")) {
            val parsed = parseBlocks("## Sources\n\n1. $name: supplied name only")
            assertNull(sourceCardForHeading(parsed[0], parsed[1]))
        }
        val mixed = parseBlocks("## Sources\n\n1. [Actual source](https://example.org/report)\n2. Missing destination must remain visible.")
        assertNull(sourceCardForHeading(mixed[0], mixed[1]))
        val unrelated = parseBlocks("## Priorities\n\n1. [Actual link](https://example.org/)")
        assertNull(sourceCardForHeading(unrelated[0], unrelated[1]))
    }

    @Test fun chartLabelsUseActualCurrencyPositionSignedMagnitudeAndUnits() {
        assertEquals("$321.9K", axisLabel(321900f, "$"))
        assertEquals("-$78.4K", axisLabel(-78400f, "$"))
        assertEquals("1.2K kg", axisLabel(1200f, "kg"))
        assertEquals("12.5%", axisLabel(12.5f, "%"))
        assertEquals("৳1.2K", axisLabel(1200f, "৳"))
        assertEquals("Undefined", axisLabel(Float.POSITIVE_INFINITY, "$"))
    }

    @Test fun sharedChoicesRefuseInventedLabelsAndKeepActualFractionalStarts() {
        val counter = parseWidget("collab", """{"surface":"counter","room":"actual-source","start":12.5}""") as Widget.Collab
        assertEquals(12.5, counter.w.start, 0.0)
        for (items in listOf("[]", "[\"Only choice\"]", "[\"Actual\",\"\"]", "[\"Actual\",{}]")) {
            assertNull(parseWidget("collab", """{"surface":"vote","room":"actual-source","items":$items}"""))
        }
        val valid = parseWidget("collab", """{"surface":"vote","room":"actual-source","items":["First","Second"]}""") as Widget.Collab
        assertEquals(listOf("First", "Second"), valid.w.items)
    }

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
    @Test fun actualModelCalculatorNormalizationNeverInventsAnInputOrCurrency() {
        for (extra in listOf("", ",\"min\":100")) {
            val source = """{"type":"finance","fields":[{"key":"x","label":"X"$extra}],"outputs":[{"expr":"x*2"}]}"""
            val calc = (parseWidget("calc", source) as Widget.Calc).w
            org.junit.Assert.assertTrue(calc.fields.single().value.isNaN())
            assertNull(CalcExpr.eval(calc.outputs.single().expr, mapOf("x" to calc.fields.single().value)))
        }
        for (supplied in listOf("\"value\":0", "\"default\":0", "\"initial\":0")) {
            val source = """{"type":"finance","fields":[{"key":"x",$supplied}],"outputs":[{"expr":"x*2"}]}"""
            val calc = (parseWidget("calc", source) as Widget.Calc).w
            assertEquals(0.0, calc.fields.single().value, 0.0)
        }
        assertEquals("1,234.50", formatCalc(1234.5, "money", ""))
        assertEquals("$1,234.50", formatCalc(1234.5, "money", "USD"))
        assertEquals("€1,234.50", formatCalc(1234.5, "money", "€"))
        assertEquals("1,234.50 EUR", formatCalc(1234.5, "money", "EUR"))
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
