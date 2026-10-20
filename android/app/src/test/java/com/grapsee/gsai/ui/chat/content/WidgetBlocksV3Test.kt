package com.grapsee.gsai.ui.chat.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Catalogue v3 — chart parity (12 new sub-types) and the six new kinds
 * (sim/stage3d/canvas/editor/terminal/collab). The web suite pins the same
 * contract on the server; these pin the client decoder, because the client
 * also renders transcripts from older builds and mid-stream fences.
 */
class WidgetBlocksV3Test {

    // --- structural charts --------------------------------------------------

    @Test
    fun `sankey with string nodes decodes`() {
        val w = parseWidget(
            "chart",
            """{"type":"sankey","nodes":["Budget","Ads","Digital"],
               "links":[{"source":"Budget","target":"Ads","value":30},{"source":"Ads","target":"Digital","value":24}]}"""
        )
        assertNotNull(w)
        val chart = (w as Widget.Chart).w
        assertEquals(3, chart.nodes.size)
        assertEquals("Budget", chart.nodes[0].id)
        assertEquals(2, chart.links.size)
    }

    @Test
    fun `force without nodes is rejected`() {
        assertNull(parseWidget("chart", """{"type":"force","links":[{"source":"a","target":"b","value":1}]}"""))
    }

    @Test
    fun `violin accepts sample columns`() {
        val w = parseWidget("chart", """{"type":"violin","series":[{"name":"a","data":[[0,1],[1,2],[2,1.2],[3,0.8]]}]}""")
        assertNotNull(w)
        assertEquals("violin", (w as Widget.Chart).w.type)
    }

    @Test
    fun `contour needs at least nine points`() {
        assertNull(parseWidget("chart", """{"type":"contour","points":[[0,0,1],[1,1,2]]}"""))
        assertNotNull(
            parseWidget(
                "chart",
                """{"type":"contour","points":[[0,0,1],[1,0,2],[2,0,1],[0,1,2],[1,1,4],[2,1,2],[0,2,1],[1,2,2],[2,2,1]],"levels":6}"""
            )
        )
    }

    @Test
    fun `parallel needs axes and aligned values`() {
        assertNull(parseWidget("chart", """{"type":"parallel","axes":["a","b"],"series":[{"name":"S","values":[1,2]}]}"""))
        val w = parseWidget("chart", """{"type":"parallel","axes":["a","b","c"],"series":[{"name":"S","values":[1,2,3]}]}""")
        assertNotNull(w)
    }

    @Test
    fun `dendrogram and sunburst ride the root tree`() {
        val dendro = parseWidget(
            "chart",
            """{"type":"dendrogram","root":{"label":"all","distance":0,"children":[
               {"label":"x","distance":2},{"label":"y","distance":1,"children":[{"label":"y1","distance":0.5},{"label":"y2","distance":0.5}]}]}}"""
        )
        assertNotNull(dendro)
        val burst = parseWidget(
            "chart",
            """{"type":"sunburst","root":{"label":"all","children":[{"label":"a","value":2},{"label":"b","value":3}]}}"""
        )
        assertNotNull(burst)
    }

    @Test
    fun `depth needs one side of the book`() {
        assertNull(parseWidget("chart", """{"type":"depth"}"""))
        assertNotNull(parseWidget("chart", """{"type":"depth","bids":[[100,5],[99,8]],"asks":[[101,4]]}"""))
    }

    @Test
    fun `wordcloud and bullet ride items`() {
        assertNotNull(parseWidget("chart", """{"type":"wordcloud","items":[{"text":"gs","weight":9},{"text":"ui","weight":4}]}"""))
        assertNotNull(parseWidget("chart", """{"type":"bullet","items":[{"label":"q","value":3,"target":5,"max":8,"ranges":[[0,4],[4,8]]}]}"""))
    }

    // --- fence routing --------------------------------------------------------

    @Test
    fun `v3 chart fence aliases inject the type`() {
        val block = parseBlocks("```violin\n{\"series\":[{\"name\":\"a\",\"data\":[[0,1],[1,2],[2,3]]}]}\n```").filterIsInstance<Block.WidgetBlock>().single()
        assertEquals("chart", block.kind)
        assertTrue(block.json.contains("violin"))
    }

    @Test
    fun `sim and collab fences route to their kind`() {
        val cloth = parseBlocks("```cloth\n{\"title\":\"silk\"}\n```").filterIsInstance<Block.WidgetBlock>().single()
        assertEquals("sim", cloth.kind)
        assertTrue(cloth.json.contains("cloth"))
        val vote = parseBlocks("```vote\n{\"items\":[\"a\",\"b\"]}\n```").filterIsInstance<Block.WidgetBlock>().single()
        assertEquals("collab", vote.kind)
        // editor aliases sit before media: ```video is an editor, not a media widget
        val video = parseBlocks("```video\n{\"tracks\":[]}\n```").filterIsInstance<Block.WidgetBlock>().single()
        assertEquals("editor", video.kind)
        // ```mindmap stays a mermaid diagram unless the body is JSON
        val mermaidMindmap = parseBlocks("```mindmap\nmindmap\n  root\n```").none { it is Block.WidgetBlock }
        assertTrue(mermaidMindmap)
    }

    // --- the six kinds --------------------------------------------------------

    @Test
    fun `sim knobs clamp and junk world repairs`() {
        val w = parseWidget("sim", """{"sim":"blackhole","gravity":999,"count":100000,"speed":50}""")
        assertNotNull(w)
        val sim = (w as Widget.Sim).w
        assertEquals("particles", sim.sim)
        assertEquals(30f, sim.gravity)
        assertEquals(120, sim.count)
        assertEquals(3f, sim.speed)
    }

    @Test
    fun `stage3d z must evaluate as math`() {
        assertNull(parseWidget("stage3d", """{"view":"surface3d","z":"fetch(window)"}"""))
        val w = parseWidget("stage3d", """{"view":"molecule","nodes":[{"label":"O","x":0,"y":0,"z":0},{"label":"H","x":1,"y":0,"z":0}],"bonds":[[0,1],[0,9]]}""")
        assertNotNull(w)
        val stage = (w as Widget.Stage3D).w
        // the out-of-range bond is dropped, the good one stays
        assertEquals(1, stage.bonds.size)
    }

    @Test
    fun `collab room slugifies`() {
        val w = parseWidget("collab", """{"surface":"vote","room":"Launch Day!!","items":["a","b"]}""")
        assertNotNull(w)
        val collab = (w as Widget.Collab).w
        assertEquals("launch-day", collab.room)
        assertEquals("vote", collab.surface)
    }

    @Test
    fun `editor junk surface repairs to sheet`() {
        val w = parseWidget("editor", """{"editor":"ide"}""")
        assertNotNull(w)
        assertEquals("sheet", (w as Widget.Editor).w.editor)
    }

    @Test
    fun `canvas nodes clamp to the unit square`() {
        val w = parseWidget("canvas", """{"surface":"mindmap","nodes":[{"label":"A","x":2,"y":-1},{"label":"B","x":0.5,"y":0.5}],"edges":[[0,1],[0,7]]}""")
        assertNotNull(w)
        val canvas = (w as Widget.Canvas).w
        assertEquals(1f, canvas.nodes[0].x)
        assertEquals(0f, canvas.nodes[0].y)
        assertEquals(1, canvas.edges.size)
    }
}
