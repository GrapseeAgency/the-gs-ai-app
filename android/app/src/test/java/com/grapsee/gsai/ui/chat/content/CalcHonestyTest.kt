package com.grapsee.gsai.ui.chat.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalcHonestyTest {
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
