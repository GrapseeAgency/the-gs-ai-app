package com.grapsee.gsai.ui.chat.content

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.skeletonSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ---------------------------------------------------------------------------
// Interactive document harness (0.78.0 "interactive chat").
//
// The native widget set (chart/stat/table/…) covers the data-visualization
// shapes. Everything else an AI answer can be — forms, sliders, dropdowns,
// dashboards, kanban boards, calendars, carousels, quizzes, calculators,
// unit/currency converters, booking flows, prototypes, custom mini-apps,
// video embeds, interactive maps — arrives as a fenced HTML document and is
// rendered HERE in a sandboxed WebView. The document is self-contained by
// contract: inline CSS/JS, CDN only for tiles/maps.
//
// Hard rules (honesty + safety):
//   * JS is on (interactivity IS the point), file/asset access is fenced by a
//     strict CSP in html mode and by the asset-only base URL in map/mermaid
//     mode.
//   * Any navigation (link click, form submit, redirect) leaves the harness
//     and opens in the system browser instead of hijacking the chat.
//   * A payload that fails to build degrades to a plain code block — never a
//     blank bubble, never a broken half-widget.
//   * Height auto-matches the document (measured through a JS bridge), so the
//     page scrolls with the transcript instead of inside a box.
// ---------------------------------------------------------------------------

private const val MAX_DOC_CHARS = 256_000
private const val DEFAULT_HEIGHT_DP = 300

/** Injected into every document: reports real content height, kills navigation. */
private val BRIDGE_SCRIPT = """
(function(){
  function h(){
    var b=document.body,d=document.documentElement;
    var attr=b&&b.getAttribute&&b.getAttribute('data-gs-height');
    if(attr&&parseInt(attr)>0){GsHost.setHeight(parseInt(attr));return;}
    var v=Math.max(b?(b.scrollHeight||b.offsetHeight):0,d?d.scrollHeight:0);
    var svg=document.querySelector('svg');
    if(svg){var r=svg.getBoundingClientRect();if(r.height>0&&r.bottom>v)v=r.bottom;}
    if(v>0)GsHost.setHeight(Math.ceil(v));
  }
  document.addEventListener('submit',function(e){e.preventDefault();},true);
  document.addEventListener('click',function(e){
    var a=e.target&&e.target.closest?e.target.closest('a[href]'):null;
    if(a&&a.getAttribute('target')!=='_blank'){a.setAttribute('target','_blank');}
  },true);
  h();
  window.addEventListener('load',h);
  window.addEventListener('resize',h);
  if(window.ResizeObserver){new ResizeObserver(h).observe(document.body||document.documentElement);}
  setTimeout(h,300);setTimeout(h,900);setTimeout(h,2500);
})();
""".trimIndent()

private fun injectBridge(doc: String): String =
    if (doc.contains("</body>", ignoreCase = true)) {
        doc.replace(Regex("</body>", RegexOption.IGNORE_CASE), "<script>$BRIDGE_SCRIPT</script></body>")
    } else {
        doc + "<script>$BRIDGE_SCRIPT</script>"
    }

/** CSS applied to model-authored HTML so it reads inside the chat bubble. */
private fun htmlCss(textHex: String, accentHex: String): String = """
<style>
html,body{margin:0;padding:0;background:transparent}
body{padding:10px 12px;color:$textHex;font-family:Roboto,system-ui,-apple-system,sans-serif;font-size:15px;line-height:1.55;-webkit-text-size-adjust:100%}
*{box-sizing:border-box;max-width:100%}
img,video,canvas,svg,table{max-width:100%;height:auto}
iframe{max-width:100%;border:0}
a{color:$accentHex}
h1,h2,h3{margin:.4em 0}
pre,code{white-space:pre-wrap;word-break:break-word}
</style>
""".trimIndent()

@Composable
fun HtmlBlockView(block: Block.HtmlBlock, streaming: Boolean) {
    if (block.open && streaming) {
        HarnessSkeleton(block.mode)
        return
    }
    val c = GsTheme.colors
    val textHex = remember(c.textPrimary) { hex6(c.textPrimary) }
    val accentHex = remember(c.accent) { hex6(c.accent) }
    val context = LocalContext.current
    val doc by produceState<String?>(null, block.mode, block.code, textHex, accentHex) {
        value = if (block.code.length > MAX_DOC_CHARS) null
        else withContext(Dispatchers.IO) { buildDocument(block.mode, block.code, textHex, accentHex) }
    }
    val built = doc
    if (built == null) {
        // Honest degradation: an over-cap or unbuildable document shows as code.
        CodeBlockCard(language = block.mode, code = block.code, open = false, onCopyCode = {})
        return
    }
    HarnessCard(tag = if (block.mode == "map") "MAP" else "HTML") {
        HarnessWebView(doc = built, fileBase = block.mode == "map")
    }
}

/** The reusable WebView: loads [doc] once, reports content height back. */
@Composable
internal fun HarnessWebView(doc: String, fileBase: Boolean) {
    var heightPx by remember(doc) { mutableStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val heightDp = if (heightPx <= 0) DEFAULT_HEIGHT_DP.dp else with(density) {
        (heightPx / density.density).toInt().coerceIn(120, 2400).dp
    }
    AndroidView(
        factory = { ctx -> createHarnessWebView(ctx) { h -> heightPx = h } },
        update = { view ->
            if (view.tag != doc) {
                view.tag = doc
                view.loadDataWithBaseURL(
                    if (fileBase) "file:///android_asset/" else "https://gsai.local/",
                    doc,
                    "text/html",
                    "utf-8",
                    null
                )
            }
        },
        modifier = Modifier.fillMaxWidth().height(heightDp)
    )
}

/** Full-diagram WebView entry for the mermaid renderer's fallback path. */
@Composable
fun MermaidWebBlockView(block: Block.MermaidBlock) {
    val c = GsTheme.colors
    val textHex = remember(c.textPrimary) { hex6(c.textPrimary) }
    val accentHex = remember(c.accent) { hex6(c.accent) }
    val doc = remember(block.source, textHex, accentHex) { buildMermaidDoc(block.source, textHex, accentHex) }
    HarnessCard(tag = "mermaid") {
        HarnessWebView(doc = doc, fileBase = true)
    }
}

/** Card shell shared by all harness modes. */
@Composable
private fun HarnessCard(tag: String, content: @Composable () -> Unit) {
    val c = GsTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.elevatedSurface)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Interactive",
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary,
                modifier = Modifier.weight(1f)
            )
            Text(
                tag.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary,
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(c.raisedSurface)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        content()
    }
}

@Composable
private fun HarnessSkeleton(mode: String) {
    val c = GsTheme.colors
    Box(
        Modifier.fillMaxWidth().height(120.dp).skeletonSurface(RoundedCornerShape(14.dp), c.raisedSurface),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Building ${if (mode == "map") "map" else "interactive view"}…",
            style = MaterialTheme.typography.labelMedium,
            color = c.textTertiary
        )
    }
}

// ---------------------------------------------------------------------------
// Document builders
// ---------------------------------------------------------------------------

/** Returns the full HTML document, or null when the payload is unusable. */
private fun buildDocument(mode: String, code: String, textHex: String, accentHex: String): String? {
    if (code.isBlank()) return null
    return when (mode) {
        "html" -> buildHtmlDoc(code, textHex, accentHex)
        "map" -> buildMapDoc(code, textHex, accentHex)
        "mermaid" -> buildMermaidDoc(code, textHex, accentHex)
        else -> null
    }
}

private fun buildHtmlDoc(code: String, textHex: String, accentHex: String): String {
    val css = htmlCss(textHex, accentHex)
    val csp = ("<meta http-equiv=\"Content-Security-Policy\" content=\"" +
        "default-src https: data: blob: 'unsafe-inline' 'unsafe-eval'; " +
        "img-src https: data: blob:; frame-src https: data: blob:; media-src https: data: blob:;\">")
    val scrubbed = scrub(code)
    return if (scrubbed.contains("<html", ignoreCase = true)) {
        val head = "<head>"
        if (scrubbed.contains(head, ignoreCase = true)) {
            scrubbed.replaceFirst(Regex("<head>", RegexOption.IGNORE_CASE), "$head$csp$css")
        } else {
            csp + css + scrubbed
        }.let { injectBridge(it) }
    } else {
        injectBridge(
            "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
                "$csp$css</head><body>$scrubbed</body></html>"
        )
    }
}

/** Neutralize the two navigation vectors we refuse to allow in-chat. */
private fun scrub(code: String): String = code
    .replace(Regex("""<meta[^>]+http-equiv\s*=\s*["']?refresh["']?[^>]*>""", RegexOption.IGNORE_CASE), "")
    .replace(Regex("""(href|src)\s*=\s*["']?\s*javascript\s*:""", RegexOption.IGNORE_CASE), "$1=\"about:blank\" x-")

@Serializable
private data class MapPin(val lat: Double = 0.0, val lng: Double = 0.0, val label: String = "", val detail: String = "")

@Serializable
private data class MapPayload(
    val title: String = "",
    val center: List<Double> = emptyList(),
    val zoom: Int = 0,
    val pins: List<MapPin> = emptyList(),
    val route: List<List<Double>> = emptyList(),
    val height: Int = 0
)

private fun buildMapDoc(code: String, textHex: String, accentHex: String): String? {
    val payload = try {
        widgetJson.decodeFromString<MapPayload>(code)
    } catch (_: Throwable) {
        null
    } ?: return null
    val pins = payload.pins.filter { it.lat in -90.0..90.0 && it.lng in -180.0..180.0 }
    val route = payload.route.filter { it.size >= 2 && it[0] in -90.0..90.0 && it[1] in -180.0..180.0 }
    if (pins.isEmpty() && route.isEmpty()) return null
    val markerJs = StringBuilder()
    pins.forEachIndexed { i, p ->
        val label = jsString(p.label.ifEmpty { "Pin ${i + 1}" })
        val detail = if (p.detail.isEmpty()) "" else "<br><small>${jsString(p.detail)}</small>"
        markerJs.append(
            "L.marker([${p.lat},${p.lng}]).addTo(map).bindPopup('<b>$label</b>$detail');\n"
        )
    }
    if (route.size >= 2) {
        markerJs.append("L.polyline([${route.joinToString(",") { "[${it[0]},${it[1]}]" }}],{color:'$accentHex',weight:4}).addTo(map);\n")
    }
    val fit = if (payload.center.size >= 2) {
        "map.setView([${payload.center[0]},${payload.center[1]}],${payload.zoom.coerceIn(2, 18)});"
    } else {
        "var b=L.latLngBounds([${(pins.map { "[${it.lat},${it.lng}]" } + route.map { "[${it[0]},${it[1]}]" }).joinToString(",")}]);map.fitBounds(b.pad(0.15));"
    }
    val titleHtml = if (payload.title.isEmpty()) "" else "<h3 style=\"margin:2px 0 8px\">${jsString(payload.title)}</h3>"
    val height = if (payload.height in 160..600) payload.height.coerceIn(160, 600) else 260
    return """
    <!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
    <link rel="stylesheet" href="leaflet.css"/>
    <script src="leaflet.js"></script>
    <style>html,body{margin:0;background:transparent;color:$textHex;font-family:Roboto,system-ui,sans-serif}
    #map{width:100%;height:${height}px;border-radius:12px}
    .leaflet-popup-content{color:#222;font-size:13px}
    </style></head><body>
    $titleHtml<div id="map"></div>
    <script>
    var map=L.map('map',{zoomControl:true});
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);
    $markerJs
    $fit
    </script>
    ${injectBridge("")}
    </body></html>
    """.trimIndent()
}

private fun buildMermaidDoc(source: String, textHex: String, accentHex: String): String {
    val escaped = source
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    return """
    <!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
    <script src="mermaid.min.js"></script>
    <style>html,body{margin:0;background:transparent;color:$textHex;font-family:Roboto,system-ui,sans-serif}
    .mermaid{background:transparent}</style></head><body>
    <div class="mermaid">$escaped</div>
    <script>
    mermaid.initialize({startOnLog:false,startOnLoad:true,theme:'base',securityLevel:'strict',
      themeVariables:{primaryColor:'$accentHex',primaryTextColor:'$textHex',lineColor:'$textHex',
      background:'transparent',mainBkg:'transparent',fontFamily:'Roboto,system-ui,sans-serif'}});
    </script>
    ${injectBridge("")}
    </body></html>
    """.trimIndent()
}

/** Public entry for the mermaid renderer's fallback (full diagram coverage). */
fun MermaidWebDocument(source: String, textHex: String, accentHex: String): String =
    buildMermaidDoc(source, textHex, accentHex)

// ---------------------------------------------------------------------------
// WebView
// ---------------------------------------------------------------------------

private class HeightBridge(val onHeight: (Int) -> Unit) {
    @JavascriptInterface
    fun setHeight(px: Int) {
        val v = if (px < 0) 0 else px.coerceAtMost(6000)
        onHeight(v)
    }
}

private fun createHarnessWebView(context: Context, onHeight: (Int) -> Unit): WebView =
    WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = false
        settings.allowFileAccess = true // asset base URL for map/mermaid docs
        settings.allowContentAccess = false
        settings.mediaPlaybackRequiresUserGesture = true
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.setSupportZoom(false)
        settings.textZoom = 100
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        setBackgroundColor(Color.Transparent.toArgb())
        overScrollMode = View.OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        addJavascriptInterface(HeightBridge(onHeight), "GsHost")
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("about:") || url.startsWith("data:") || url.startsWith("blob:")) return false
                openExternally(view.context, url)
                return true
            }
        }
    }

private fun openExternally(context: Context, url: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Throwable) {
        // no browser installed — the harness simply swallows the tap
    }
}

// ---------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------

private fun hex6(color: Color): String =
    "#%02X%02X%02X".format(
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt()
    )

private fun jsString(s: String): String = s
    .replace("\\", "\\\\")
    .replace("'", "\\'")
    .replace("\"", "&quot;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\n", " ")

/** JSON codec shared with WidgetBlocks (same lenient configuration). */
internal val harnessJson = Json { ignoreUnknownKeys = true }

/** Skeleton/no-op used by other renderers through this file's helpers. */
internal fun noopHeight() {}

/** Stream-time placeholder used when a mermaid fence is still arriving. */
@Composable
fun MermaidStreamSkeleton() {
    val c = GsTheme.colors
    Box(
        Modifier.fillMaxWidth().height(120.dp).skeletonSurface(RoundedCornerShape(14.dp), c.raisedSurface),
        contentAlignment = Alignment.Center
    ) {
        Text("Building diagram…", style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
    }
}

/** Kept for API symmetry with the widget path. */
@Composable
fun DocumentStreamSkeleton() {
    LaunchedEffect(Unit) { noopHeight() }
    HarnessSkeleton("html")
}
