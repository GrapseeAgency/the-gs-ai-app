package com.grapsee.gsai.ui.chat.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalcHonestyTest {
    @Test fun nativeFencesPreserveEverySourceSeparatorAndBlankLine() {
        val sources = listOf("", "\n", "\r\n", "\n\nconst value = 7;\n\n", "\r\nconst value = 7;\r\n\r\n", "বাংলা 日本語\r\n\n\r\n", "const key = \"😀\";\r")
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
        val source = "first\n```\n~~~\nlast\n\n"
        val block = parseBlocks("````javascript\n" + source + "\n`````").single() as Block.CodeBlock
        assertEquals(source, block.code)
        assertEquals(false, block.open)
        val html = "<!doctype html>\r\n<html>\r\n\r\n<body>Actual source</body></html>\r\n"
        assertEquals(html, (parseBlocks("~~~~html\r\n" + html + "\r\n~~~~").single() as Block.HtmlBlock).code)
        assertEquals("chart", (parseBlocks("~~~chart\n{\"labels\":[\"A\"],\"values\":[7]}\n~~~").single() as Block.WidgetBlock).kind)
    }

    @Test fun nativeStreamingMatchesFullParseAtEveryFencePrefix() {
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
