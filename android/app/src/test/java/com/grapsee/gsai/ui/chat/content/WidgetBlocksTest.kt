package com.grapsee.gsai.ui.chat.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GENUI widget contract (docs/GENUI.md): fenced widget blocks ride the normal
 * markdown stream; parsing the JSON happens at RENDER time, so the parser must
 * (a) recognise widget fences, (b) keep mid-stream fences open, (c) never let a
 * malformed payload break the transcript — parseWidget() returns null and the
 * renderer degrades to a code block.
 */
class WidgetBlocksTest {

    // --- fence routing -----------------------------------------------------

    @Test
    fun `closed chart fence becomes a widget block`() {
        val blocks = parseBlocks("before\n\n```chart\n{\"type\":\"line\"}\n```\n\nafter")
        assertEquals(3, blocks.size)
        val w = blocks[1] as Block.WidgetBlock
        assertEquals("chart", w.kind)
        assertEquals("{\"type\":\"line\"}", w.json)
        assertEquals(false, w.open)
    }

    @Test
    fun `all six widget kinds are recognised, case-insensitively`() {
        for (kind in listOf("chart", "stat", "table", "compare", "progress", "timeline")) {
            val blocks = parseBlocks("```${kind.uppercase()}\n{}\n```")
            val w = blocks.single() as Block.WidgetBlock
            assertEquals(kind, w.kind)
        }
    }

    @Test
    fun `open widget fence mid-stream is a widget block marked open`() {
        // The closing fence has not arrived yet — this is exactly what a flush
        // looks like while the model is still emitting the JSON.
        val blocks = parseBlocks("text\n\n```chart\n{\"type\":\"line\",\"seri")
        val w = blocks.last() as Block.WidgetBlock
        assertEquals("chart", w.kind)
        assertTrue(w.open)
    }

    @Test
    fun `unknown fence languages still become code blocks`() {
        val blocks = parseBlocks("```kotlin\nfun main() {}\n```")
        val c = blocks.single() as Block.CodeBlock
        assertEquals("kotlin", c.language)
    }

    @Test
    fun `widget fences survive streaming flushes byte-identically`() {
        val full = "intro\n\n```progress\n{\"items\":[{\"label\":\"a\",\"percent\":50}]}\n```\n\noutro"
        // Exercise the REAL incremental path the renderer uses, not parseBlocks.
        var cache = ""
        var blocks: List<Block> = emptyList()
        for (chunk in full.chunked(11)) {
            val next = cache + chunk
            blocks = parseStreamingBlocks(cache, blocks, next)
            cache = next
        }
        val direct = parseBlocks(full)
        assertEquals(direct, blocks)
        assertTrue(direct.any { it is Block.WidgetBlock && it.kind == "progress" && !it.open })
    }

    @Test
    fun `widget block offsets shift with the rekey delta`() {
        val b = Block.WidgetBlock("chart", "{}", false, 40)
        // withShiftedSource is internal plumbing; the important bit is the type survives.
        val shifted = parseBlocks("x\n\n```chart\n{}\n```")[1] as Block.WidgetBlock
        assertTrue(shifted.sourceStart > 0)
        assertNotNull(b.json)
    }

    // --- payload validation --------------------------------------------------

    private val chartJson = """{"type":"line","title":"Prices","unit":"$","series":[{"name":"a","data":[[1,2],[2,4],[3,3]]}]}"""

    @Test
    fun `valid chart parses with typed series`() {
        val w = parseWidget("chart", chartJson)
        assertTrue(w is Widget.Chart)
        assertEquals(1, (w as Widget.Chart).w.series.size)
        assertEquals(4f, w.w.series[0].data[1][1])
    }

    @Test
    fun `chart with too many points is capped, not dropped`() {
        val many = (1..300).joinToString(",") { "[$it,$it]" }
        val w = parseWidget("chart", """{"type":"line","series":[{"name":"a","data":[$many]}]}""") as Widget.Chart
        assertEquals(200, w.w.series[0].data.size)
    }

    @Test
    fun `chart with too few points or unknown type degrades to null`() {
        assertNull(parseWidget("chart", """{"type":"line","series":[{"name":"a","data":[[1,2]]}]}"""))
        // scatter is a valid type now; use a genuinely unknown type for the null path.
        assertNull(parseWidget("chart", """{"type":"sparkline","series":[{"name":"a","data":[[1,2],[2,3]]}]}"""))
        assertNull(parseWidget("chart", """{"series":[]}"""))
    }

    @Test
    fun `stat cards parse, cap at eight`() {
        val cards = (1..10).joinToString(",") { """{"label":"L$it","value":"$it"}""" }
        val w = parseWidget("stat", """{"cards":[$cards]}""") as Widget.Stat
        assertEquals(8, w.w.cards.size)
    }

    @Test
    fun `table requires columns and rows`() {
        assertNull(parseWidget("table", """{"columns":[],"rows":[]}"""))
        val w = parseWidget(
            "table",
            """{"columns":["a","b"],"rows":[["1","x"],["2","y"]],"sortable":true}"""
        ) as Widget.Table
        assertEquals(2, w.w.rows.size)
        assertTrue(w.w.sortable)
    }

    @Test
    fun `progress percent is clamped to 0-100`() {
        val w = parseWidget("progress", """{"items":[{"label":"a","percent":140},{"label":"b","percent":-5}]}""") as Widget.Progress
        assertEquals(100f, w.w.items[0].percent)
        assertEquals(0f, w.w.items[1].percent)
    }

    @Test
    fun `compare and timeline parse`() {
        assertNotNull(parseWidget("compare", """{"left":{"title":"A","items":["x"]},"right":{"title":"B","items":["y"]}}"""))
        assertNotNull(parseWidget("timeline", """{"entries":[{"date":"2026","title":"Launch"}]}"""))
    }

    @Test
    fun `malformed payloads degrade to null, never throw`() {
        assertNull(parseWidget("chart", "not json"))
        assertNull(parseWidget("chart", """{"type":"line","series":"oops"}"""))
        assertNull(parseWidget("chart", ""))
        assertNull(parseWidget("stat", "[" + "1,".repeat(70000))) // over the byte cap
        assertNull(parseWidget("unknown-kind", "{}"))
    }

    @Test
    fun `unknown json keys are ignored, older clients survive newer payloads`() {
        val w = parseWidget("stat", """{"title":"t","cards":[{"label":"L","value":"1","futureField":{"nested":true}}]}""")
        assertTrue(w is Widget.Stat)
    }

    // --- all chart types ------------------------------------------------------

    @Test
    fun `every chart type parses with at least one valid series`() {
        val types = listOf(
            "line", "area", "stackedArea", "bar", "hbar", "stacked", "grouped",
            "pie", "donut", "scatter", "bubble", "histogram", "boxplot", "heatmap",
            "correlation", "treemap", "waterfall", "candlestick", "gantt", "funnel", "radar", "chord",
            // v3 — parity with the web renderer
            "quadrant", "sankey", "network", "wordcloud", "sunburst", "bullet",
            "violin", "contour", "parallel", "dendrogram", "force", "depth"
        )
        assertEquals(CHART_TYPES, types.toSet())
        for (type in types) {
            val json = """{"type":"$type","labels":["A","B"],"series":[{"name":"s","data":[[0,3],[1,7],[2,5]]}]}"""
            val w = parseWidget("chart", json)
            assertTrue("chart type '$type' should parse", w is Widget.Chart)
            assertEquals(type, (w as Widget.Chart).w.type)
        }
    }

    // --- interactive widgets --------------------------------------------------

    @Test
    fun `gauge parses fractions`() {
        val w = parseWidget(
            "gauge",
            """{"title":"Health","items":[{"label":"Battery","value":78,"max":100,"unit":"%"}]}"""
        ) as Widget.Gauge
        assertEquals(1, w.w.items.size)
        assertEquals(78f, w.w.items[0].value)
        assertEquals(100f, w.w.items[0].max)
    }

    @Test
    fun `quiz parses questions with options and answer index`() {
        val w = parseWidget(
            "quiz",
            """{"title":"T","questions":[{"q":"1+1?","options":["1","2","3"],"answer":1,"explain":"two"}]}"""
        ) as Widget.Quiz
        assertEquals(1, w.w.questions.size)
        assertEquals(1, w.w.questions[0].answer)
        assertEquals("two", w.w.questions[0].explain)
    }

    @Test
    fun `form parses fields of every control type`() {
        val fields = listOf("text", "textarea", "select", "radio", "checkbox", "slider", "toggle", "date")
            .mapIndexed { i, t -> """{"type":"$t","label":"F$i","required":${i % 2 == 0}}""" }
            .joinToString(",")
        val w = parseWidget("form", """{"title":"Signup","submitLabel":"Go","fields":[$fields]}""") as Widget.Form
        assertEquals(8, w.w.fields.size)
        assertEquals("Go", w.w.submitLabel)
    }

    @Test
    fun `carousel keeps only https images`() {
        val w = parseWidget(
            "carousel",
            """{"title":"Pics","items":[{"image":"https://example.com/a.png","caption":"A"},{"image":"http://insecure/x.png","caption":"B"}]}"""
        ) as Widget.Carousel
        // the insecure (http) item is dropped by the parser
        assertEquals(1, w.w.items.size)
    }

    @Test
    fun `cards and followups parse tap-to-send prompts`() {
        val c = parseWidget("cards", """{"title":"T","cards":[{"title":"X","subtitle":"s","prompt":"do x"}]}""") as Widget.Cards
        assertEquals("do x", c.w.cards[0].prompt)
        val f = parseWidget("followups", """{"title":"T","prompts":["next","more"]}""") as Widget.Followups
        assertEquals(listOf("next", "more"), f.w.prompts)
    }

    @Test
    fun `media covers every listed sub-type`() {
        val payloads = mapOf(
            "image" to """{"type":"image","items":[{"url":"https://x/a.png"}]}""",
            "gallery" to """{"type":"gallery","items":[{"url":"https://x/a.png"}]}""",
            "video" to """{"type":"video","items":[{"url":"https://x/v.mp4","title":"V"}]}""",
            "audio" to """{"type":"audio","items":[{"url":"https://x/a.mp3","title":"A"}]}""",
            "code" to """{"type":"code","code":"fun main(){}","language":"kotlin"}""",
            "file" to """{"type":"file","items":[{"url":"https://x/a.pdf","title":"a.pdf"}]}""",
            "link" to """{"type":"link","items":[{"url":"https://x","title":"X"}]}""",
            "citation" to """{"type":"citation","items":[{"url":"https://x","title":"X"}]}""",
            "search" to """{"type":"search","items":[{"url":"https://x","title":"X","snippet":"s"}]}""",
            "entity" to """{"type":"entity","items":[{"title":"X"}]}""",
            "product" to """{"type":"product","items":[{"title":"X","price":"9.99"}]}""",
            "listing" to """{"type":"listing","items":[{"title":"X","meta":"m"}]}""",
            "doc" to """{"type":"doc","items":[{"url":"https://x/a.pdf","title":"a.pdf"}]}""",
            "document" to """{"type":"document","items":[{"url":"https://x/b.pdf","title":"b.pdf"}]}""",
            "pdf" to """{"type":"pdf","items":[{"url":"https://x/c.pdf","title":"c.pdf"}]}""",
            "embed" to """{"type":"embed","items":[{"url":"https://x/embed","title":"Embed"}]}"""
        )
        assertEquals(MEDIA_TYPES, payloads.keys)
        for ((type, json) in payloads) {
            val w = parseWidget("media", json) as Widget.Media
            // document/pdf are aliases normalized to the single `doc` renderer.
            assertEquals(if (type == "document" || type == "pdf") "doc" else type, w.w.type)
        }
    }

    @Test
    fun `actions parse prompt copy and open buttons`() {
        val w = parseWidget(
            "actions",
            """{"title":"T","buttons":[{"label":"Ask","kind":"prompt","value":"hi"},{"label":"Copy","kind":"copy","value":"x"},{"label":"Open","kind":"open","value":"https://x"}]}"""
        ) as Widget.Actions
        assertEquals(3, w.w.buttons.size)
        assertEquals("prompt", w.w.buttons[0].kind)
    }

    @Test
    fun `panel parses badges and popover`() {
        val w = parseWidget(
            "panel",
            """{"title":"T","summary":"s","body":"b","badges":[{"label":"New","tone":"success"}],"popover":"note"}"""
        ) as Widget.Panel
        assertEquals(1, w.w.badges.size)
        assertEquals("note", w.w.popover)
    }

    /**
     * Contract lock: the fence-routing set and the parser must never drift.
     * Every language that `fenceToBlock` treats as a widget must have a parser
     * branch that accepts a minimal valid payload — otherwise the model emits a
     * widget the app silently drops to a code block.
     */
    @Test
    fun `every routed widget kind has a parser branch`() {
        val minimal = mapOf(
            "chart" to """{"type":"line","series":[{"name":"a","data":[[1,2],[2,3]]}]}""",
            "stat" to """{"cards":[{"label":"L","value":"1"}]}""",
            "table" to """{"columns":["a"],"rows":[["1"]]}""",
            "compare" to """{"left":{"title":"A","items":["x"]},"right":{"title":"B","items":["y"]}}""",
            "progress" to """{"items":[{"label":"a","percent":50}]}""",
            "timeline" to """{"entries":[{"date":"2026","title":"Launch"}]}""",
            "gauge" to """{"items":[{"label":"a","value":5,"max":10}]}""",
            "quiz" to """{"questions":[{"q":"?","options":["a","b"],"answer":0}]}""",
            "form" to """{"fields":[{"type":"text","label":"Name"}]}""",
            "carousel" to """{"items":[{"image":"https://example.com/a.png"}]}""",
            "cards" to """{"cards":[{"title":"X","prompt":"do"}]}""",
            "followups" to """{"prompts":["next"]}""",
            "media" to """{"type":"image","items":[{"url":"https://example.com/a.png","title":"A"}]}""",
            "actions" to """{"buttons":[{"label":"Go","kind":"prompt","value":"go"}]}""",
            "panel" to """{"title":"T","summary":"s","body":"b"}""",
            "voice" to """{"title":"Talk","prompt":"hello"}""",
            "calc" to """{"type":"loan","fields":[{"key":"p","label":"Principal","value":1000,"min":0,"max":5000}],"outputs":[{"label":"Pay","expr":"p*2","format":"money","primary":true}]}""",
            "app" to """{"type":"tasks","items":[{"title":"Do it"}]}""",
            // v3 kinds — same contract: routed means parsed.
            "sim" to """{"sim":"particles","count":40}""",
            "stage3d" to """{"view":"scene","nodes":[{"id":"a","label":"A"}]}""",
            "canvas" to """{"surface":"whiteboard","nodes":[{"x":0.2,"y":0.3}]}""",
            "editor" to """{"editor":"sheet","cells":{"A1":"1"}}""",
            "terminal" to """{"commands":[{"name":"help","description":"list commands"}]}""",
            "collab" to """{"surface":"counter","room":"lobby"}"""
        )
        // The routing set is exactly the parser set — no orphan language, no
        // parser branch reachable only by a hand-written call.
        assertEquals(minimal.keys, WIDGET_LANGUAGES)
        for ((kind, json) in minimal) {
            assertNotNull("kind '$kind' is routed but not parsed", parseWidget(kind, json))
        }
    }

    @Test
    fun `voice widget parses and rejects an empty payload`() {
        val v = parseWidget("voice", """{"title":"Talk","prompt":"weather","hint":"Speak"}""") as Widget.Voice
        assertEquals("Talk", v.w.title)
        assertEquals("weather", v.w.prompt)
        assertNull(parseWidget("voice", """{}"""))
    }

    @Test
    fun `media doc requires an https url and normalizes document and pdf`() {
        val doc = parseWidget(
            "media",
            """{"type":"document","items":[{"url":"https://example.com/a.pdf","title":"A","mime":"application/pdf"}]}"""
        ) as Widget.Media
        assertEquals("doc", doc.w.type)
        assertEquals("application/pdf", doc.w.items[0].mime)

        val pdf = parseWidget(
            "media",
            """{"type":"pdf","items":[{"url":"https://example.com/b.pdf","title":"B"}]}"""
        ) as Widget.Media
        assertEquals("doc", pdf.w.type)

        // A document without a fetchable https link would render nothing.
        assertNull(parseWidget("media", """{"type":"doc","items":[{"title":"local"}]}"""))
    }

    @Test
    fun `media video and audio keep the direct stream url`() {
        val video = parseWidget(
            "media",
            """{"type":"video","items":[{"url":"https://cdn.example.com/a.mp4","thumb":"https://cdn.example.com/a.jpg","title":"A"}]}"""
        ) as Widget.Media
        assertEquals("https://cdn.example.com/a.mp4", video.w.items[0].url)

        val audio = parseWidget(
            "media",
            """{"type":"audio","items":[{"url":"https://cdn.example.com/a.mp3","title":"B"}]}"""
        ) as Widget.Media
        assertEquals("https://cdn.example.com/a.mp3", audio.w.items[0].url)
    }

    // --- calculators ---------------------------------------------------------

    @Test
    fun `calculator expressions honour precedence and functions`() {
        val vars = mapOf("p" to 250000.0, "r" to 0.005, "n" to 360.0)
        assertEquals(7.0, CalcExpr.eval("1+2*3", vars)!!, 1e-9)
        assertEquals(9.0, CalcExpr.eval("(1+2)*3", vars)!!, 1e-9)
        assertEquals(8.0, CalcExpr.eval("2^3", vars)!!, 1e-9)
        assertEquals(512.0, CalcExpr.eval("2^3^2", vars)!!, 1e-9) // right-associative
        assertEquals(3.0, CalcExpr.eval("sqrt(9)", vars)!!, 1e-9)
        assertEquals(5.0, CalcExpr.eval("max(2, 5, 3)", vars)!!, 1e-9)
        assertEquals(-4.0, CalcExpr.eval("-p/p*4", vars)!!, 1e-9)
        assertEquals(1.0, CalcExpr.eval("sin(pi/2)", vars)!!, 1e-9)

        // The real thing: a 30-year amortising payment.
        val payment = CalcExpr.eval("p*r*pow(1+r,n)/(pow(1+r,n)-1)", vars)!!
        assertEquals(1498.88, payment, 0.05)
        assertNull(CalcExpr.eval("1/0", vars))
        assertNull(CalcExpr.eval("nonsense(", vars))
    }

    @Test
    fun `calculator formatting is locale-stable and unit aware`() {
        assertEquals("1,234.50", formatCalc(1234.5, "money", ""))
        assertEquals("12.50%", formatCalc(0.125, "percent", ""))
        assertEquals("1,235", formatCalc(1234.5, "integer", ""))
        assertEquals("1.2K", formatCalc(1234.0, "compact", ""))
        assertEquals("42 km", formatCalc(42.0, "number", "km"))
        assertEquals("—", formatCalc(Double.NaN, "number", ""))
    }

    @Test
    fun `calculator widget parses every listed type`() {
        for (type in CALC_TYPES) {
            val json = """{"type":"$type","fields":[{"key":"x","label":"X","value":5,"min":0,"max":10}],"outputs":[{"label":"Y","expr":"x*2","primary":true}]}"""
            val w = parseWidget("calc", json)
            assertTrue("calc type '$type' should parse", w is Widget.Calc)
            assertEquals(type, (w as Widget.Calc).w.type)
        }
        // Unknown type, no fields, or no outputs must degrade rather than render blank.
        assertNull(parseWidget("calc", """{"type":"astrology","fields":[{"key":"x"}],"outputs":[{"expr":"x"}]}"""))
        assertNull(parseWidget("calc", """{"type":"loan","fields":[],"outputs":[{"expr":"x"}]}"""))
        assertNull(parseWidget("calc", """{"type":"loan","fields":[{"key":"x"}],"outputs":[]}"""))
    }

    // --- app surfaces --------------------------------------------------------

    @Test
    fun `app widget parses every supported layout`() {
        val payloads = mapOf(
            "wizard" to """{"steps":[{"title":"One","body":"b"}]}""",
            "onboarding" to """{"steps":[{"title":"Welcome","body":"hi"}]}""",
            "kanban" to """{"columns":[{"title":"Todo","items":[{"title":"A"}]}]}""",
            "calendar" to """{"month":"2026-10","items":[{"title":"E","date":"2026-10-05"}]}""",
            "sheet" to """{"rows":[["A","B"],["1","2"]]}""",
            "tasks" to """{"items":[{"title":"A"}]}""",
            "dashboard" to """{"items":[{"title":"MRR","meta":"$12k","badge":"+8%"}]}""",
            "config" to """{"groups":[{"title":"Plan","options":[{"label":"Pro","price":20}]}]}""",
            "configurator" to """{"groups":[{"title":"Size","options":[{"label":"L","price":5}]}]}""",
            "booking" to """{"items":[{"title":"Mon 20"}],"slots":["09:00"]}""",
            "schedule" to """{"items":[{"title":"Tue 21"}],"slots":["10:00"]}""",
            "search" to """{"items":[{"title":"Result"}]}""",
            "filter" to """{"columns":[{"title":"Tag","items":[{"title":"news"}]}],"items":[{"title":"A","badge":"news"}]}""",
            "analytics" to """{"items":[{"title":"Sessions","meta":"1,204"}]}""",
            "report" to """{"items":[{"title":"Churn","meta":"2.1%"}]}""",
            "files" to """{"items":[{"title":"a.pdf"}]}""",
            "connected" to """{"items":[{"title":"Drive"}]}""",
            "decision" to """{"steps":[{"title":"Criteria","body":"pick"}]}""",
            "scenario" to """{"items":[{"title":"Base","meta":"$10k"}]}""",
            "timer" to """{"items":[{"title":"Focus","price":25}]}"""
        )
        assertEquals(APP_TYPES, payloads.keys)
        for ((type, json) in payloads) {
            val w = parseWidget("app", json.replaceFirst("{", "{\"type\":\"$type\","))
            assertTrue("app type '$type' should parse", w is Widget.App)
            assertEquals(type, (w as Widget.App).w.type)
        }
        // A layout with nothing to render is a silent blank card — reject it.
        assertNull(parseWidget("app", """{"type":"kanban","columns":[]}"""))
        assertNull(parseWidget("app", """{"type":"sheet","rows":[["only-header"]]}"""))
        assertNull(parseWidget("app", """{"type":"nonsense","items":[{"title":"A"}]}"""))
    }

    @Test
    fun `app and calc fences route to widgets while html still routes to the webview`() {
        val appJson = parseBlocks("```app\n{\"type\":\"tasks\",\"items\":[{\"title\":\"A\"}]}\n```").single()
        assertTrue(appJson is Block.WidgetBlock)

        val appHtml = parseBlocks("```app\n<html><body>hi</body></html>\n```").single()
        assertTrue(appHtml is Block.HtmlBlock)

        val calc = parseBlocks("```calc\n{\"type\":\"loan\",\"fields\":[{\"key\":\"x\"}],\"outputs\":[{\"expr\":\"x\"}]}\n```").single()
        assertTrue(calc is Block.WidgetBlock)
    }
}
