package com.grapsee.gsai.ui.chat.content

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Document-harness contract tests (0.80.0).
 *
 * These cover the three WebView payloads that are generated rather than
 * parsed from the model's HTML: map (routing/clustering/comparison), inline
 * player, and the code sandbox. A regression here is invisible at compile
 * time — it just renders an empty box — so the generated document is asserted
 * directly.
 */
class HtmlHarnessTest {

    private val text = "#111111"
    private val accent = "#3366FF"

    @Test
    fun `map with stops routes with the right profile`() {
        val driving = buildDocument(
            "map",
            """{"mode":"driving","stops":[{"lat":23.8,"lng":90.4,"label":"A"},{"lat":23.9,"lng":90.5,"label":"B"}]}""",
            text,
            accent
        )
        assertNotNull(driving)
        assertTrue(driving!!.contains("router.project-osrm.org/route/v1/driving/"))
        assertTrue(driving.contains("MODE='driving'"))

        val walking = buildDocument(
            "map",
            """{"mode":"walking","stops":[{"lat":23.8,"lng":90.4},{"lat":23.9,"lng":90.5}]}""",
            text,
            accent
        )!!
        assertTrue(walking.contains("MODE='walking'"))
        assertTrue(walking.contains("routing.openstreetmap.de/routed-foot"))

        val cycling = buildDocument(
            "map",
            """{"mode":"cycling","stops":[{"lat":23.8,"lng":90.4},{"lat":23.9,"lng":90.5}]}""",
            text,
            accent
        )!!
        assertTrue(cycling.contains("routing.openstreetmap.de/routed-bike"))
    }

    @Test
    fun `map clustering and place cards are opt in`() {
        val doc = buildDocument(
            "map",
            """{"cluster":true,"cards":true,"pins":[{"lat":23.8,"lng":90.4,"label":"A","detail":"one"}]}""",
            text,
            accent
        )!!
        assertTrue(doc.contains("CLUSTER=true"))
        assertTrue(doc.contains("CARDS=true"))
        assertTrue(doc.contains("circleMarker"))
    }

    @Test
    fun `map comparison renders one panel per entry`() {
        val doc = buildDocument(
            "map",
            """{"title":"Compare","compare":[
              {"title":"Dhaka","center":[23.8,90.4],"zoom":11,"pins":[{"lat":23.8,"lng":90.4,"label":"A"}]},
              {"title":"Chattogram","center":[22.3,91.8],"zoom":11,"pins":[{"lat":22.3,"lng":91.8,"label":"B"}]}
            ]}""",
            text,
            accent
        )!!
        assertTrue(doc.contains("id=\"m0\""))
        assertTrue(doc.contains("id=\"m1\""))
        assertTrue(doc.contains("initPanel('m0'"))
        assertTrue(doc.contains("initPanel('m1'"))
        assertTrue(doc.contains("Dhaka"))
    }

    @Test
    fun `map without pins or route degrades to null`() {
        assertNull(buildDocument("map", """{"title":"empty"}""", text, accent))
        assertNull(buildDocument("map", "not json", text, accent))
    }

    @Test
    fun `video player embeds a real video element`() {
        val doc = buildDocument(
            "player",
            """{"kind":"video","url":"https://cdn.example.com/a.mp4","title":"Clip","poster":"https://cdn.example.com/a.jpg"}""",
            text,
            accent
        )!!
        assertTrue(doc.contains("<video"))
        assertTrue(doc.contains("https://cdn.example.com/a.mp4"))
        assertTrue(doc.contains("poster=\"https://cdn.example.com/a.jpg\""))
    }

    @Test
    fun `audio player embeds a real audio element`() {
        val doc = buildDocument(
            "player",
            """{"kind":"audio","url":"https://cdn.example.com/a.mp3","title":"Track"}""",
            text,
            accent
        )!!
        assertTrue(doc.contains("<audio"))
    }

    @Test
    fun `player rejects non https media`() {
        assertNull(buildDocument("player", """{"kind":"video","url":"javascript:alert(1)"}""", text, accent))
        assertNull(buildDocument("player", """{"kind":"video","url":"file:///etc/passwd"}""", text, accent))
    }

    @Test
    fun `sandbox is editable and escapes the source`() {
        val doc = buildDocument(
            "sandbox",
            """{"language":"javascript","code":"console.log('<hi> & bye')"}""",
            text,
            accent
        )!!
        assertTrue(doc.contains("<textarea"))
        assertTrue(doc.contains("&lt;hi&gt; &amp; bye"))
        assertTrue(doc.contains("IS_HTML=false"))
        // No network and no file access from the execution context.
        assertTrue(doc.contains("default-src 'none'"))
    }

    @Test
    fun `sandbox marks html as a preview and other languages as non runnable`() {
        val html = buildDocument(
            "sandbox",
            """{"language":"html","code":"<b>hi</b>"}""",
            text,
            accent
        )!!
        assertTrue(html.contains("IS_HTML=true"))
        assertTrue(html.contains("Preview"))

        val python = buildDocument(
            "sandbox",
            """{"language":"python","code":"print(1)"}""",
            text,
            accent
        )!!
        assertTrue(python.contains("Execution is JavaScript/HTML only"))
        assertFalse(python.contains("IS_HTML=true"))
    }
}
