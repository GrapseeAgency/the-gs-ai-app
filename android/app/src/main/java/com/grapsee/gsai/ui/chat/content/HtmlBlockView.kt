package com.grapsee.gsai.ui.chat.content

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import androidx.compose.ui.graphics.luminance
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

/**
 * The palette a model-authored document is allowed to use.
 *
 * A ```html block used to receive exactly two colours (text and accent) and a
 * transparent background, so every bespoke view invented its own greys — which
 * is why they looked pasted on. Claude's visualizer solves this by handing the
 * model a token set; this is the same idea. Every value is emitted as a CSS
 * custom property, so a document writes `var(--gs-text-2)` instead of guessing
 * a hex, and it follows light/dark automatically.
 */
internal data class DocTokens(
    val text: String,
    val textSecondary: String,
    val textTertiary: String,
    val surface: String,
    val raised: String,
    val border: String,
    val accent: String,
    val dark: Boolean,
) {
    companion object {
        /** Used by tests and by callers that only know the two base colours. */
        fun of(text: String, accent: String) = DocTokens(
            text = text,
            textSecondary = "#5B6472",
            textTertiary = "#8A93A0",
            surface = "#F7F7F5",
            raised = "#EFEFEC",
            border = "#E5E5E1",
            accent = accent,
            dark = false,
        )
    }
}

/** The live theme, as document tokens. */
@Composable
internal fun rememberDocTokens(): DocTokens {
    val c = GsTheme.colors
    val dark = c.appBackground.luminance() < 0.4f
    return remember(c, dark) {
        DocTokens(
            text = hex6(c.textPrimary),
            textSecondary = hex6(c.textSecondary),
            textTertiary = hex6(c.textTertiary),
            surface = hex6(c.surface),
            raised = hex6(c.raisedSurface),
            border = hex6(c.divider),
            accent = hex6(c.accent),
            dark = dark,
        )
    }
}

/** CSS applied to model-authored HTML so it reads inside the chat bubble. */
private fun htmlCss(t: DocTokens): String {
    val hues = (0 until 12).joinToString("") { i -> "--gs-hue-${i + 1}:${hex6(WidgetPalette.at(i))};" }
    return """
<style>
:root{color-scheme:${if (t.dark) "dark" else "light"};
--gs-text:${t.text};--gs-text-2:${t.textSecondary};--gs-text-3:${t.textTertiary};
--gs-surface:${t.surface};--gs-raised:${t.raised};--gs-border:${t.border};
--gs-accent:${t.accent};$hues
--gs-radius:14px;--gs-space:8px;--gs-font:Roboto,system-ui,-apple-system,sans-serif}
html,body{margin:0;padding:0;background:transparent}
body{padding:10px 12px;color:var(--gs-text);font-family:var(--gs-font);font-size:15px;line-height:1.55;-webkit-text-size-adjust:100%}
*{box-sizing:border-box;max-width:100%}
img,video,canvas,svg,table{max-width:100%;height:auto}
iframe{max-width:100%;border:0}
a{color:var(--gs-accent)}
h1,h2,h3{margin:.4em 0}
pre,code{white-space:pre-wrap;word-break:break-word}
input,select,textarea,button{font-family:inherit;font-size:inherit}
</style>
""".trimIndent()
}

@Composable
fun HtmlBlockView(block: Block.HtmlBlock, streaming: Boolean) {
    if (block.open && streaming) {
        HarnessSkeleton(block.mode)
        return
    }
    val tokens = rememberDocTokens()
    val context = LocalContext.current
    val doc by produceState<String?>(null, block.mode, block.code, tokens) {
        value = if (block.code.length > MAX_DOC_CHARS) null
        else withContext(Dispatchers.IO) { buildDocument(block.mode, block.code, tokens) }
    }
    val built = doc
    if (built == null) {
        // Honest degradation: an over-cap or unbuildable document shows as code.
        CodeBlockCard(language = block.mode, code = block.code, open = false, onCopyCode = {})
        return
    }
    HarnessCard(tag = when (block.mode) { "map" -> "MAP"; "svg" -> "SVG"; else -> "HTML" }) {
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
        factory = { ctx -> createHarnessWebView(ctx, serveAssets = fileBase) { h -> heightPx = h } },
        update = { view ->
            if (view.tag != doc) {
                view.tag = doc
                // Always an https origin: map/mermaid assets are intercepted
                // from the APK, while remote calls (OSRM routing) stay a normal
                // CORS request instead of a blocked file:// one.
                view.loadDataWithBaseURL("https://gsai.local/", doc, "text/html", "utf-8", null)
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
internal fun buildDocument(mode: String, code: String, textHex: String, accentHex: String): String? =
    buildDocument(mode, code, DocTokens.of(textHex, accentHex))

internal fun buildDocument(mode: String, code: String, tokens: DocTokens): String? {
    if (code.isBlank()) return null
    return when (mode) {
        "html", "svg" -> buildHtmlDoc(code, tokens)
        "map" -> buildMapDoc(code, tokens.text, tokens.accent)
        "mermaid" -> buildMermaidDoc(code, tokens.text, tokens.accent)
        "player" -> buildPlayerDoc(code, tokens.text, tokens.accent)
        "sandbox" -> buildSandboxDoc(code, tokens.text, tokens.accent)
        else -> null
    }
}

private fun buildHtmlDoc(code: String, t: DocTokens): String {
    val css = htmlCss(t)
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

/** One turn-by-turn instruction. Used only when routing cannot be fetched. */
@Serializable
private data class MapStep(val instruction: String = "", val distance: String = "")

/** One map inside a side-by-side comparison. */
@Serializable
private data class MapPanel(
    val title: String = "",
    val center: List<Double> = emptyList(),
    val zoom: Int = 0,
    val pins: List<MapPin> = emptyList(),
    val route: List<List<Double>> = emptyList()
)

@Serializable
private data class MapPayload(
    val title: String = "",
    val center: List<Double> = emptyList(),
    val zoom: Int = 0,
    val pins: List<MapPin> = emptyList(),
    val route: List<List<Double>> = emptyList(),
    /** driving | walking | cycling — enables live OSRM routing between [stops]. */
    val mode: String = "",
    /** Ordered waypoints; 2+ turns the map into a routed itinerary. */
    val stops: List<MapPin> = emptyList(),
    /** Static turn list, shown until/unless OSRM answers. */
    val steps: List<MapStep> = emptyList(),
    /** Group dense pins into counted bubbles. */
    val cluster: Boolean = false,
    /** Show the place-detail carousel under the map. */
    val cards: Boolean = false,
    /** Two or more panels render as a map comparison. */
    val compare: List<MapPanel> = emptyList(),
    val height: Int = 0
)

private fun validPin(p: MapPin) = p.lat in -90.0..90.0 && p.lng in -180.0..180.0
private fun validRoute(r: List<List<Double>>) = r.filter { it.size >= 2 && it[0] in -90.0..90.0 && it[1] in -180.0..180.0 }

private fun buildMapDoc(code: String, textHex: String, accentHex: String): String? {
    val payload = try {
        widgetJson.decodeFromString<MapPayload>(code)
    } catch (_: Throwable) {
        null
    } ?: return null
    val pins = payload.pins.filter(::validPin)
    val route = validRoute(payload.route)
    val stops = payload.stops.filter(::validPin)
    val compare = payload.compare
        .filter { it.pins.any(::validPin) || validRoute(it.route).isNotEmpty() }
        .take(4)
    if (compare.isNotEmpty()) {
        return buildCompareMapDoc(compare, payload.title, textHex, accentHex, payload.height)
    }
    if (pins.isEmpty() && route.isEmpty() && stops.size < 2) return null
    val mode = when (payload.mode.lowercase()) {
        "walking", "walk", "foot", "pedestrian" -> "walking"
        "cycling", "cycle", "bike", "bicycle" -> "cycling"
        "driving", "drive", "car" -> "driving"
        else -> ""
    }
    val height = if (payload.height in 160..600) payload.height else 260
    val pinsJs = pins.joinToString(",") { p ->
        "{lat:${p.lat},lng:${p.lng},label:'${jsString(p.label)}',detail:'${jsString(p.detail)}'}"
    }
    val stopsJs = stops.joinToString(",") { p ->
        "{lat:${p.lat},lng:${p.lng},label:'${jsString(p.label)}',detail:'${jsString(p.detail)}'}"
    }
    val stepsJs = payload.steps.take(40).joinToString(",") { s ->
        "{text:'${jsString(s.instruction)}',dist:'${jsString(s.distance)}'}"
    }
    val routeJs = route.joinToString(",") { "[${it[0]},${it[1]}]" }
    val titleHtml = if (payload.title.isEmpty()) "" else "<h3>${jsString(payload.title)}</h3>"
    val centerJs = if (payload.center.size >= 2) "[${payload.center[0]},${payload.center[1]}]" else "null"
    return """
<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'self' https: data: blob:; script-src 'self' 'unsafe-inline' https:; style-src 'self' 'unsafe-inline' https:; img-src https: data: blob:; connect-src https:;">
<link rel="stylesheet" href="leaflet.css"/>
<script src="leaflet.js"></script>
<style>
html,body{margin:0;background:transparent;color:$textHex;font-family:Roboto,system-ui,sans-serif}
#map{width:100%;height:${height}px;border-radius:12px}
.leaflet-popup-content{color:#222;font-size:13px}
h3{margin:2px 0 8px;font-size:15px}
.cards{display:flex;gap:8px;overflow-x:auto;padding:10px 0 2px;-webkit-overflow-scrolling:touch}
.card{flex:0 0 auto;max-width:220px;border:1px solid rgba(128,128,128,.35);border-radius:10px;padding:8px 10px;cursor:pointer;background:rgba(128,128,128,.08)}
.card b{display:block;font-size:13px;margin-bottom:2px}
.card span{font-size:11px;opacity:.75;display:block;line-height:1.35}
.steps{margin-top:8px}
.sum{font-size:12px;opacity:.8;margin:6px 0 2px}
.step{display:flex;gap:8px;padding:5px 0;font-size:12.5px;border-bottom:1px solid rgba(128,128,128,.14);align-items:baseline}
.step i{font-style:normal;opacity:.55;min-width:16px}
.step em{font-style:normal;margin-left:auto;opacity:.7;white-space:nowrap}
</style></head><body>
$titleHtml
<div id="map"></div>
<div id="cards" class="cards"></div>
<div id="steps" class="steps"></div>
<script>
var ACCENT='$accentHex';
var PINS=[$pinsJs], STOPS=[$stopsJs], STATIC_STEPS=[$stepsJs], ROUTE=[$routeJs];
var MODE='$mode', CLUSTER=${payload.cluster}, CARDS=${payload.cards}, CENTER=$centerJs, ZOOM=${payload.zoom.coerceIn(2, 18)};
var map=L.map('map',{zoomControl:true});
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'(c) OpenStreetMap'}).addTo(map);
function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
function fmt(m){if(m==null||isNaN(m))return '';return m<1000?Math.round(m)+' m':(m/1000).toFixed(1)+' km';}
function fmtDur(s){if(s==null||isNaN(s))return '';var m=Math.round(s/60);return m<60?m+' min':Math.floor(m/60)+' h '+(m%60)+' min';}
function addPin(p,layer){
  var m=L.marker([p.lat,p.lng]);
  (layer||map).addLayer(m);
  m.bindPopup('<b>'+esc(p.label||'Place')+'</b>'+(p.detail?'<br><small>'+esc(p.detail)+'</small>':''));
  return m;
}
var pinLayer=L.layerGroup().addTo(map);
function drawPins(){
  pinLayer.clearLayers();
  if(!CLUSTER){PINS.forEach(function(p){addPin(p,pinLayer);});return;}
  var z=map.getZoom()||2, cell=360/Math.pow(2,z)/6, g={};
  PINS.forEach(function(p){
    var k=Math.floor((p.lat+90)/cell)+':'+Math.floor((p.lng+180)/cell);
    (g[k]=g[k]||[]).push(p);
  });
  Object.keys(g).forEach(function(k){
    var a=g[k];
    if(a.length===1){addPin(a[0],pinLayer);return;}
    var lat=0,lng=0;a.forEach(function(p){lat+=p.lat;lng+=p.lng;});
    lat/=a.length;lng/=a.length;
    var c=L.circleMarker([lat,lng],{radius:Math.min(13+a.length*2,26),weight:2,color:'#fff',fillColor:ACCENT,fillOpacity:.9});
    c.bindTooltip(a.length+' places');
    c.on('click',function(){map.setView([lat,lng],Math.min((map.getZoom()||2)+2,18));});
    pinLayer.addLayer(c);
  });
}
function renderSteps(list){
  var box=document.getElementById('steps');
  box.innerHTML='';
  (list||[]).forEach(function(s,i){
    var row=document.createElement('div');row.className='step';
    var n=document.createElement('i');n.textContent=String(i+1);
    var t=document.createElement('span');t.textContent=s.text||'';
    var d=document.createElement('em');d.textContent=s.dist||'';
    row.appendChild(n);row.appendChild(t);row.appendChild(d);box.appendChild(row);
  });
}
function buildCards(){
  if(!CARDS||!PINS.length)return;
  var box=document.getElementById('cards');
  PINS.forEach(function(p){
    var d=document.createElement('div');d.className='card';
    var b=document.createElement('b');b.textContent=p.label||'Place';d.appendChild(b);
    if(p.detail){var s=document.createElement('span');s.textContent=p.detail;d.appendChild(s);}
    d.addEventListener('click',function(){
      map.setView([p.lat,p.lng],Math.max(map.getZoom()||2,14));
      L.popup().setLatLng([p.lat,p.lng]).setContent('<b>'+esc(p.label||'Place')+'</b>'+(p.detail?'<br><small>'+esc(p.detail)+'</small>':'')).openOn(map);
    });
    box.appendChild(d);
  });
}
function osrmBase(m){
  if(m==='walking')return 'https://routing.openstreetmap.de/routed-foot/route/v1/driving/';
  if(m==='cycling')return 'https://routing.openstreetmap.de/routed-bike/route/v1/driving/';
  return 'https://router.project-osrm.org/route/v1/driving/';
}
function fitAll(){
  var pts=PINS.map(function(p){return [p.lat,p.lng];}).concat(STOPS.map(function(p){return [p.lat,p.lng];}));
  if(ROUTE.length)pts=pts.concat(ROUTE);
  if(CENTER){map.setView(CENTER,ZOOM);}
  else if(pts.length){try{map.fitBounds(L.latLngBounds(pts).pad(.15));}catch(e){}}
}
if(STOPS.length>=2){STOPS.forEach(function(s,i){addPin({lat:s.lat,lng:s.lng,label:s.label||('Stop '+(i+1)),detail:s.detail});});}
if(ROUTE.length>=2){L.polyline(ROUTE,{color:ACCENT,weight:4,dashArray:'6 6'}).addTo(map);}
fitAll();
drawPins();
map.on('zoomend',function(){if(CLUSTER)drawPins();});
buildCards();
if(STATIC_STEPS.length)renderSteps(STATIC_STEPS);
if(MODE&&STOPS.length>=2){
  var coords=STOPS.map(function(s){return s.lng+','+s.lat;}).join(';');
  fetch(osrmBase(MODE)+coords+'?overview=full&geometries=geojson&steps=true')
    .then(function(r){return r.json();})
    .then(function(d){
      if(!d||!d.routes||!d.routes[0])return;
      var rt=d.routes[0];
      var layer=L.geoJSON(rt.geometry,{style:{color:ACCENT,weight:5,opacity:.92}}).addTo(map);
      try{map.fitBounds(layer.getBounds().pad(.12));}catch(e){}
      var out=[];
      (rt.legs||[]).forEach(function(leg){
        (leg.steps||[]).forEach(function(s){
          var m=s.maneuver||{}, t=(m.type||'continue').replace(/_/g,' ');
          var mod=m.modifier?(' '+m.modifier):'';
          var name=s.name?(' onto '+s.name):'';
          out.push({text:(t.charAt(0).toUpperCase()+t.slice(1)+mod+name),dist:fmt(s.distance)});
        });
      });
      renderSteps(out);
      var sum=document.createElement('div');sum.className='sum';
      sum.textContent=fmt(rt.distance)+'  -  '+fmtDur(rt.duration);
      var box=document.getElementById('steps');
      box.insertBefore(sum,box.firstChild);
    })
    .catch(function(){});
}
</script>
${injectBridge("")}
</body></html>
    """.trimIndent()
}

/** Two or more maps side by side (stacked on narrow screens). */
private fun buildCompareMapDoc(
    panels: List<MapPanel>,
    title: String,
    textHex: String,
    accentHex: String,
    rawHeight: Int
): String {
    val h = if (rawHeight in 140..520) rawHeight else 190
    val initJs = panels.mapIndexed { i, p ->
        val pins = p.pins.filter(::validPin)
        val route = validRoute(p.route)
        val pinsJs = pins.joinToString(",") { q ->
            "{lat:${q.lat},lng:${q.lng},label:'${jsString(q.label)}',detail:'${jsString(q.detail)}'}"
        }
        val routeJs = route.joinToString(",") { "[${it[0]},${it[1]}]" }
        val center = if (p.center.size >= 2) "[${p.center[0]},${p.center[1]}]" else "null"
        "initPanel('m$i',$center,${p.zoom.coerceIn(2, 18)},[$pinsJs],[$routeJs]);"
    }.joinToString("\n")
    val divs = panels.indices.joinToString("") { """<div class="pane"><div class="mp" id="m$it"></div><div class="cap">${jsString(panels[it].title.ifEmpty { "Map ${it + 1}" })}</div></div>""" }
    val titleHtml = if (title.isEmpty()) "" else "<h3>${jsString(title)}</h3>"
    return """
<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'self' https: data: blob:; script-src 'self' 'unsafe-inline' https:; style-src 'self' 'unsafe-inline' https:; img-src https: data: blob:; connect-src https:;">
<link rel="stylesheet" href="leaflet.css"/>
<script src="leaflet.js"></script>
<style>
html,body{margin:0;background:transparent;color:$textHex;font-family:Roboto,system-ui,sans-serif}
h3{margin:2px 0 8px;font-size:15px}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:8px}
.mp{width:100%;height:${h}px;border-radius:12px}
.cap{font-size:11.5px;opacity:.75;padding:4px 2px 0;font-weight:500}
.leaflet-popup-content{color:#222;font-size:13px}
</style></head><body>
$titleHtml
<div class="grid">$divs</div>
<script>
var ACCENT='$accentHex';
function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
function initPanel(id,center,zoom,pins,route){
  var map=L.map(id,{zoomControl:false,attributionControl:false});
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19}).addTo(map);
  pins.forEach(function(p){
    L.marker([p.lat,p.lng]).addTo(map).bindPopup('<b>'+esc(p.label||'Place')+'</b>'+(p.detail?'<br><small>'+esc(p.detail)+'</small>':''));
  });
  if(route.length>=2)L.polyline(route,{color:ACCENT,weight:4}).addTo(map);
  var pts=pins.map(function(p){return [p.lat,p.lng];}).concat(route);
  if(center){map.setView(center,zoom);}
  else if(pts.length){try{map.fitBounds(L.latLngBounds(pts).pad(.15));}catch(e){}}
  setTimeout(function(){map.invalidateSize();},250);
}
$initJs
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
// Inline media player (video embeds / audio experiences)
// ---------------------------------------------------------------------------

@Serializable
private data class PlayerPayload(
    val kind: String = "video",
    val url: String = "",
    val title: String = "",
    val poster: String = "",
    val height: Int = 0
)

private fun safeMediaUrl(url: String): Boolean =
    url.startsWith("https://") || url.startsWith("data:") || url.startsWith("blob:")

private fun buildPlayerDoc(code: String, textHex: String, accentHex: String): String? {
    val p = try {
        widgetJson.decodeFromString<PlayerPayload>(code)
    } catch (_: Throwable) {
        null
    } ?: return null
    if (!safeMediaUrl(p.url)) return null
    return playerDoc(p.kind, p.url, p.title, p.poster, textHex, accentHex, p.height)
}

private fun playerDoc(
    kind: String,
    url: String,
    title: String,
    poster: String,
    textHex: String,
    accentHex: String,
    rawHeight: Int = 0
): String {
    val isAudio = kind.equals("audio", ignoreCase = true)
    val titleHtml = if (title.isEmpty()) "" else "<div class=\"ttl\">${jsString(title)}</div>"
    val body = if (isAudio) {
        """
        <div class="audio">
          <div class="disc"></div>
          <audio id="p" controls preload="metadata" src="$url"></audio>
        </div>
        """.trimIndent()
    } else {
        val posterAttr = if (safeMediaUrl(poster)) " poster=\"$poster\"" else ""
        val h = if (rawHeight in 140..520) rawHeight else 210
        """
        <video id="p" controls playsinline preload="metadata"$posterAttr src="$url" style="width:100%;height:${h}px;border-radius:10px;background:#000"></video>
        """.trimIndent()
    }
    return """
<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; media-src https: data: blob:; img-src https: data:; style-src 'unsafe-inline'; script-src 'unsafe-inline';">
<style>
html,body{margin:0;background:transparent;color:$textHex;font-family:Roboto,system-ui,sans-serif}
.ttl{font-size:13px;font-weight:600;margin:0 0 6px}
video{display:block;background:#000}
.audio{display:flex;align-items:center;gap:10px;padding:8px 10px;border-radius:10px;background:rgba(128,128,128,.12)}
.audio audio{flex:1;height:40px}
.disc{width:34px;height:34px;border-radius:50%;background:$accentHex;opacity:.85;flex:0 0 auto}
</style></head><body>
$titleHtml
$body
${injectBridge("")}
</body></html>
    """.trimIndent()
}

/** Inline player used by the `media` widget for video/audio items. */
@Composable
fun MediaPlayerView(kind: String, url: String, title: String, poster: String = "") {
    val c = GsTheme.colors
    val textHex = remember(c.textPrimary) { hex6(c.textPrimary) }
    val accentHex = remember(c.accent) { hex6(c.accent) }
    val doc = remember(kind, url, title, poster, textHex, accentHex) {
        if (safeMediaUrl(url)) playerDoc(kind, url, title, poster, textHex, accentHex) else null
    }
    if (doc == null) {
        Text(
            title.ifEmpty { "Media unavailable" },
            style = MaterialTheme.typography.bodySmall,
            color = c.textTertiary
        )
        return
    }
    HarnessCard(tag = if (kind.equals("audio", ignoreCase = true)) "audio" else "video") {
        HarnessWebView(doc = doc, fileBase = false)
    }
}

// ---------------------------------------------------------------------------
// Code sandbox (interactive + editable code)
//
// Execution is deliberately narrow: JavaScript runs inside this WebView with
// no network, no file access and no storage; HTML runs in a `sandbox="allow-
// scripts"` iframe. Every other language stays editable + copyable but is
// never executed — pretending to run Python would be a lie, and shelling out
// on-device would be a hole.
// ---------------------------------------------------------------------------

@Serializable
private data class SandboxPayload(
    val language: String = "javascript",
    val code: String = "",
    val title: String = "",
    val height: Int = 0
)

private fun buildSandboxDoc(code: String, textHex: String, accentHex: String): String? {
    val p = try {
        widgetJson.decodeFromString<SandboxPayload>(code)
    } catch (_: Throwable) {
        null
    } ?: return null
    if (p.code.isBlank()) return null
    return sandboxDoc(p.language, p.code, p.title, textHex, accentHex, p.height)
}

private fun htmlEscapeText(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun sandboxDoc(
    language: String,
    code: String,
    title: String,
    textHex: String,
    accentHex: String,
    rawHeight: Int = 0
): String {
    val lang = language.lowercase()
    val runnable = lang in setOf("javascript", "js", "html", "htm", "svg")
    val isHtml = lang in setOf("html", "htm", "svg")
    val h = if (rawHeight in 120..520) rawHeight else 150
    val titleHtml = if (title.isEmpty()) "" else "<div class=\"ttl\">${jsString(title)}</div>"
    val langLabel = if (lang.isBlank()) "text" else lang
    val runLabel = if (isHtml) "Preview" else "Run"
    val notice = if (runnable) "" else
        "<div class=\"note\">$langLabel can be edited and copied here. Execution is JavaScript/HTML only — running other languages on-device is not supported.</div>"
    return """
<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' 'unsafe-eval'; style-src 'unsafe-inline'; img-src data: blob:; media-src data: blob:; frame-src 'self' data: blob:;">
<style>
html,body{margin:0;background:transparent;color:$textHex;font-family:Roboto,system-ui,sans-serif;font-size:13px}
.ttl{font-size:13px;font-weight:600;margin:0 0 6px}
.bar{display:flex;align-items:center;gap:8px;margin:0 0 6px}
.bar .sp{flex:1}
.tag{font-size:10.5px;letter-spacing:.06em;text-transform:uppercase;opacity:.6}
button{font:inherit;font-size:12px;padding:5px 12px;border-radius:8px;border:0;background:$accentHex;color:#fff;cursor:pointer}
button.ghost{background:rgba(128,128,128,.18);color:$textHex}
textarea{width:100%;box-sizing:border-box;min-height:${h}px;resize:vertical;border-radius:9px;border:1px solid rgba(128,128,128,.35);background:rgba(128,128,128,.08);color:$textHex;font-family:ui-monospace,Menlo,Consolas,monospace;font-size:12.5px;line-height:1.5;padding:9px 10px;outline:none}
#out{white-space:pre-wrap;word-break:break-word;font-family:ui-monospace,Menlo,Consolas,monospace;font-size:12px;line-height:1.5;padding:8px 10px;margin-top:8px;border-radius:9px;background:rgba(128,128,128,.1);display:none}
#out.err{color:#c0392b}
iframe{width:100%;border:0;border-radius:9px;margin-top:8px;background:#fff}
.note{font-size:11.5px;opacity:.7;margin-top:8px;line-height:1.45}
</style></head><body>
$titleHtml
<div class="bar"><span class="tag">$langLabel</span><span class="sp"></span>
<button class="ghost" id="copy">Copy</button>
<button id="run" ${if (runnable) "" else "style=\"display:none\""}>$runLabel</button></div>
<textarea id="src" spellcheck="false" autocapitalize="off" autocomplete="off">${htmlEscapeText(code)}</textarea>
<pre id="out"></pre>
<div id="frame"></div>
$notice
<script>
var IS_HTML=$isHtml;
function out(t,err){
  var o=document.getElementById('out');
  o.style.display='block';o.className=err?'err':'';o.textContent=t;
}
function fmt(v){
  if(typeof v==='string')return v;
  try{return JSON.stringify(v);}catch(e){return String(v);}
}
function run(){
  var src=document.getElementById('src').value;
  document.getElementById('frame').innerHTML='';
  document.getElementById('out').style.display='none';
  if(IS_HTML){
    var f=document.createElement('iframe');
    f.setAttribute('sandbox','allow-scripts');
    f.style.height='220px';
    f.srcdoc=src;
    document.getElementById('frame').appendChild(f);
    return;
  }
  var lines=[];
  var real={log:console.log,error:console.error,warn:console.warn,info:console.info};
  function cap(prefix){return function(){var a=[];for(var i=0;i<arguments.length;i++)a.push(fmt(arguments[i]));lines.push(prefix+a.join(' '));};}
  console.log=cap('');console.info=cap('');console.warn=cap('[warn] ');console.error=cap('[error] ');
  var err=null;
  try{ (new Function(src))(); }catch(e){ err=(e&&e.message)?e.message:String(e); }
  console.log=real.log;console.error=real.error;console.warn=real.warn;console.info=real.info;
  var text=lines.join('\n');
  if(err){ text=(text?text+'\n':'')+'Error: '+err; out(text,true); }
  else if(text){ out(text,false); }
  else { out('(no output)',false); }
}
document.getElementById('run').addEventListener('click',run);
document.getElementById('copy').addEventListener('click',function(){
  var t=document.getElementById('src');
  t.select();t.setSelectionRange(0,999999);
  try{document.execCommand('copy');}catch(e){}
  var b=document.getElementById('copy');var old=b.textContent;b.textContent='Copied';setTimeout(function(){b.textContent=old;},1200);
});
</script>
${injectBridge("")}
</body></html>
    """.trimIndent()
}

/** Editable, runnable code card used by `media` type=code and the sandbox kind. */
@Composable
fun CodeSandboxView(code: String, language: String, title: String = "") {
    val c = GsTheme.colors
    val textHex = remember(c.textPrimary) { hex6(c.textPrimary) }
    val accentHex = remember(c.accent) { hex6(c.accent) }
    val doc = remember(code, language, title, textHex, accentHex) {
        sandboxDoc(language, code, title, textHex, accentHex)
    }
    HarnessCard(tag = "code") {
        HarnessWebView(doc = doc, fileBase = false)
    }
}

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

private fun createHarnessWebView(
    context: Context,
    serveAssets: Boolean,
    onHeight: (Int) -> Unit
): WebView =
    WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = false
        settings.allowFileAccess = false
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
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                if (!serveAssets) return null
                val url = request.url.toString()
                if (!url.startsWith("https://gsai.local/")) return null
                val name = url.removePrefix("https://gsai.local/")
                    .substringBefore('?')
                    .substringBefore('#')
                if (name.isBlank() || name.contains("..")) return null
                return try {
                    val stream = view.context.assets.open(name)
                    val mime = when {
                        name.endsWith(".js") -> "application/javascript"
                        name.endsWith(".css") -> "text/css"
                        name.endsWith(".json") -> "application/json"
                        else -> "application/octet-stream"
                    }
                    WebResourceResponse(mime, "utf-8", stream)
                } catch (_: Throwable) {
                    null
                }
            }

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
