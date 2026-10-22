package com.grapsee.gsai.ui.chat.content

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.R
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.gsGlass
import com.grapsee.gsai.ui.theme.GsTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Locale
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// The wire shape
// ---------------------------------------------------------------------------

data class NexlyPlanRow(
    val time: String,
    val text: String,
    val icon: String,
    val tone: String,
)

data class NexlyPlanData(
    val title: String,
    val rows: List<NexlyPlanRow>,
    val closing: String? = null,
    val closingIcon: String? = null,
    val timestamp: String? = null,
)

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
private val planJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    // The model can leave a comma before the closing brace while it streams.
    // A plan card must not flash back to a code block for that.
    allowTrailingComma = true
    allowComments = true
}

private val TONES = setOf("amber", "lavender", "blue", "green", "pink", "peach", "mint", "lilac")
private val TONE_ORDER = listOf("amber", "lavender", "blue", "green", "pink", "peach", "mint", "lilac")

/**
 * Parse a plan payload emitted by the model. Mirrors `parseNexlyPlan` in
 * src/lib/nexly/plan.ts: rows without a time or text are dropped, icons are
 * resolved against the shared 5,247-glyph registry, and the reply is capped at
 * fourteen rows. Returns null when there is no renderable plan, so a malformed
 * fence degrades to a code block rather than an empty glass card.
 */
fun parseNexlyPlanData(raw: String): NexlyPlanData? {
    val obj = runCatching { planJson.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
    val root = obj.planRoot()
    val rowsRaw = (root["rows"] as? JsonArray) ?: (root["items"] as? JsonArray) ?: return null
    val rows = rowsRaw.take(14).mapNotNull { element ->
        val row = element as? JsonObject ?: return@mapNotNull null
        val time = cleanPlanText(row.firstString("time", "start", "from", "date", "slot"), 40)
        val text = cleanPlanText(
            row.firstString("text", "label", "title", "activity", "detail", "task"),
            180
        )
        if (time.isBlank() || text.isBlank()) return@mapNotNull null
        val icon = NexlyIcons.resolve(row.string("icon") ?: "sparkles")
        val tone = row.string("tone")?.lowercase(Locale.US)?.takeIf { it in TONES }
            ?: NexlyIcons.toneFor(icon)
        NexlyPlanRow(time = time, text = text, icon = icon, tone = tone)
    }
    if (rows.isEmpty()) return null
    val closing = cleanPlanText(root.firstString("closing", "footer"), 220).takeIf { it.isNotBlank() }
    val closingIcon = root.string("closingIcon")?.takeIf { it.isNotBlank() }?.let { NexlyIcons.resolve(it) }
    val timestamp = cleanPlanText(root.string("timestamp"), 24).takeIf { it.isNotBlank() }
    return NexlyPlanData(
        title = cleanPlanText(root.firstString("title", "heading"), 160).ifBlank { "Here's your plan:" },
        rows = rows,
        closing = closing,
        closingIcon = closingIcon,
        timestamp = timestamp
    )
}

/** Accept the three wrappers models reach for, not only a bare root object. */
private fun JsonObject.planRoot(): JsonObject {
    for (key in listOf("plan", "schedule", "timetable")) {
        (this[key] as? JsonObject)?.let { return it }
    }
    return this
}

private fun JsonObject.firstString(vararg keys: String): String? {
    for (key in keys) string(key)?.let { return it }
    return null
}

fun placeholderPlanData(): NexlyPlanData = NexlyPlanData(
    title = "Building your plan\u2026",
    rows = emptyList()
)

private fun JsonObject.string(key: String): String? =
    runCatching { (get(key) as? JsonPrimitive)?.contentOrNull }.getOrNull()

/** Remove pictographs the prompt bans; known emoji become `:icon:` tokens. */
internal fun cleanPlanText(value: String?, max: Int): String {
    if (value.isNullOrBlank()) return ""
    return NexlyIcons.normalize(value)
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        .replace(Regex("__(.+?)__"), "$1")
        .replace(Regex("~~(.+?)~~"), "$1")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(max)
}

// ---------------------------------------------------------------------------
// Font + registry
// ---------------------------------------------------------------------------

/** One piece of a prose run: either literal text or a Tabler icon name. */
data class NexlyInlinePart(val value: String, val isIcon: Boolean)

/**
 * One 1.8 MB outline font carries every glyph. The class-to-codepoint map is
 * shared byte-for-byte with iOS; loading it once at first use keeps a plan
 * row from doing any per-icon work beyond a map lookup.
 */
object NexlyIcons {
    val family: FontFamily = FontFamily(Font(R.font.tabler_icons))

    private val aliases = mapOf(
        "study" to "book-2", "studying" to "book-2", "class" to "book-2", "classes" to "book-2",
        "lecture" to "book-2", "reading" to "book-2", "book" to "book-2",
        "homework" to "file-text", "assignment" to "file-text", "assignments" to "file-text",
        "work" to "device-laptop", "laptop" to "device-laptop", "computer" to "device-laptop",
        "coding" to "code", "email" to "mail", "meeting" to "users",
        "food" to "tools-kitchen-2", "lunch" to "tools-kitchen-2", "dinner" to "tools-kitchen-2",
        "breakfast" to "sunrise", "utensils" to "tools-kitchen-2", "fork-knife" to "tools-kitchen-2",
        "cafe" to "coffee", "coffee" to "coffee",
        "workout" to "run", "exercise" to "run", "gym" to "barbell", "run" to "run",
        "running" to "run", "walk" to "walk", "yoga" to "stretching", "health" to "heart",
        "sleep" to "moon", "rest" to "moon", "winddown" to "moon", "wind-down" to "moon",
        "night" to "moon", "game" to "device-gamepad-2", "games" to "device-gamepad-2",
        "gaming" to "device-gamepad-2", "gamepad" to "device-gamepad-2",
        "hobby" to "palette", "hobbies" to "palette", "music" to "music",
        "think" to "sparkles", "thinking" to "sparkles", "reasoning" to "brain",
        "effort" to "settings", "gear" to "settings", "cog" to "settings",
        "muscle" to "flame", "flex" to "flame", "biceps" to "flame", "strong" to "flame",
        "motivate" to "flame", "motivation" to "flame", "sparkle" to "sparkles",
        "sun" to "sunrise", "morning" to "sunrise", "star" to "star",
        "check" to "checks", "done" to "checks", "warning" to "alert-triangle",
        "calendar" to "calendar", "clock" to "clock", "time" to "clock",
        "target" to "target", "idea" to "bulb", "lightbulb" to "bulb",
        "person" to "user", "user" to "user", "home" to "home", "school" to "school",
        "travel" to "plane", "plane" to "plane", "phone" to "device-mobile",
        "mobile" to "device-mobile"
    )

    /** Known emoji → the closest semantic glass glyph. Prompt bans emoji; this
     * is the renderer safety net, never an instruction to the model. */
    private val emoji = mapOf(
        "🌅" to "sunrise", "🌄" to "sunrise", "☀️" to "sunrise", "☀" to "sunrise",
        "📚" to "book-2", "📖" to "book-2", "💻" to "device-laptop", "🖥️" to "device-laptop",
        "🍽️" to "tools-kitchen-2", "🍴" to "tools-kitchen-2", "🍔" to "tools-kitchen-2",
        "📝" to "file-text", "📄" to "file-text", "🏃" to "run", "🏃‍♂️" to "run", "🏃‍♀️" to "run",
        "🏋️" to "barbell", "🎮" to "device-gamepad-2", "🕹️" to "device-gamepad-2",
        "🌙" to "moon", "😴" to "moon", "✨" to "sparkles", "🧠" to "brain",
        "⚙️" to "settings", "⚙" to "settings", "💪" to "flame", "🔥" to "flame",
        "❤️" to "heart", "❤" to "heart", "✅" to "circle-check", "✔️" to "circle-check",
        "⭐" to "star", "🎯" to "target", "☕" to "coffee", "🎨" to "palette",
        "🎵" to "music", "🚶" to "walk", "📅" to "calendar", "⏰" to "clock"
    )

    private val tokenRegex = Regex(":([a-z0-9][a-z0-9-]{1,42}):")

    /** Replace known emoji with `:icon:` tokens and drop any remaining
     * pictograph, including variation selectors and ZWJ sequences. */
    fun normalize(text: String): String {
        var out = text
        for ((symbol, icon) in emoji.entries.sortedByDescending { it.key.length }) {
            out = out.replace(symbol, ":$icon:")
        }
        val filtered = StringBuilder(out.length)
        var i = 0
        while (i < out.length) {
            val cp = out.codePointAt(i)
            val pictograph = cp in 0x1F000..0x1FAFF ||
                cp in 0x2600..0x27BF ||
                cp in 0xFE00..0xFE0F ||
                cp == 0x200D ||
                cp in 0x1F1E6..0x1F1FF
            if (!pictograph) filtered.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return filtered.toString()
    }

    /** Split a run on the inline `:icon:` grammar. Unknown tokens stay
     * literal text, so a stray colon pair never silently disappears. */
    fun inlineParts(text: String): List<NexlyInlinePart> {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return emptyList()
        val out = mutableListOf<NexlyInlinePart>()
        var last = 0
        tokenRegex.findAll(normalized).forEach { match ->
            if (match.range.first > last) {
                out += NexlyInlinePart(normalized.substring(last, match.range.first), false)
            }
            val name = match.groupValues[1]
            val map = codepoints
            if (map == null || map.containsKey(name) || aliases.containsKey(name)) {
                out += NexlyInlinePart(name, true)
            } else {
                out += NexlyInlinePart(match.value, false)
            }
            last = match.range.last + 1
        }
        if (last < normalized.length) {
            out += NexlyInlinePart(normalized.substring(last), false)
        }
        return out
    }

    @Volatile private var codepoints: Map<String, Int>? = null
    @Volatile private var attempted = false

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (codepoints != null || attempted) return
        attempted = true
        runCatching {
            val text = context.applicationContext.assets.open("nexly-icons.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val obj = planJson.parseToJsonElement(text) as JsonObject
            codepoints = obj.mapNotNull { (name, value) ->
                val hex = (value as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val cp = hex.toIntOrNull(16) ?: return@mapNotNull null
                name to cp
            }.toMap()
        }
    }

    fun resolve(raw: String?): String {
        var name = raw.orEmpty().trim().lowercase(Locale.US)
            .replace(Regex("^[:\\[]+|[:\\]]+$"), "")
            .replace(Regex("^(icon|ti|tabler|gs)[-_:]"), "")
            .replace(Regex("[_\\s]+"), "-")
            .replace(Regex("-+"), "-")
        if (name.isBlank()) return "sparkles"
        if (aliases.containsKey(name)) name = aliases.getValue(name)
        val map = codepoints
        if (map != null && map.containsKey(name)) return name
        if (map == null) return name // before the asset loads, let glyph() fall through
        return "sparkles"
    }

    /** The reference response's semantic palette; used when the model omits tone. */
    private val referenceTones = mapOf(
        "sunrise" to "amber",
        "book-2" to "lavender",
        "device-laptop" to "blue",
        "tools-kitchen-2" to "green",
        "file-text" to "lavender",
        "run" to "pink",
        "device-gamepad-2" to "blue",
        "moon" to "lilac",
        "flame" to "peach"
    )

    fun toneFor(icon: String): String {
        referenceTones[icon]?.let { return it }
        var hash = 0
        for (ch in icon) hash = hash * 31 + ch.code
        return TONE_ORDER[((hash and Int.MAX_VALUE) % TONE_ORDER.size)]
    }

    fun glyph(name: String?): String {
        val resolved = resolve(name)
        val cp = codepoints?.get(resolved)
            ?: codepoints?.get("sparkles")
            ?: 0xF6D7
        return String(Character.toChars(cp))
    }
}

@Composable
fun NexlyGlyph(
    name: String?,
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    remember(context) { NexlyIcons.ensureLoaded(context.applicationContext) }
    val glyph = remember(name) { NexlyIcons.glyph(name) }
    Text(
        text = glyph,
        modifier = modifier
            .size(size)
            .wrapContentSize(Alignment.Center),
        style = TextStyle(
            fontFamily = NexlyIcons.family,
            fontSize = size.value.sp,
            lineHeight = (size.value * 1.02f).sp,
            color = color,
            fontWeight = FontWeight.Normal
        ),
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

/** The companion identity mark outside each glass assistant card. */
@Composable
fun NexlyAssistantOrb(size: Dp = 30.dp) {
    Image(
        painter = painterResource(R.drawable.gs_companion_orb),
        contentDescription = "Nexly",
        modifier = Modifier
            .size(size)
            .shadow(5.dp, CircleShape)
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape)
    )
}

// ---------------------------------------------------------------------------
// Glass surfaces
// ---------------------------------------------------------------------------

data class NexlyTone(
    val top: Color,
    val bottom: Color,
    val ink: Color,
    val halo: Color
)

private fun tone(name: String): NexlyTone = when (name) {
    "amber" -> NexlyTone(Color(0xFFFFE7BE), Color(0xFFF6B96F), Color.White, Color(0x30C9823A))
    "lavender" -> NexlyTone(Color(0xFFE7DCFF), Color(0xFFC3A9F7), Color.White, Color(0x306F53C9))
    "blue" -> NexlyTone(Color(0xFFD9EDFF), Color(0xFFA5CEF8), Color.White, Color(0x303E83BE))
    "green" -> NexlyTone(Color(0xFFD9F6E1), Color(0xFF9DE1B1), Color.White, Color(0x30388F54))
    "pink" -> NexlyTone(Color(0xFFFFE0E9), Color(0xFFF7B2C6), Color.White, Color(0x30BB5875))
    "peach" -> NexlyTone(Color(0xFFFFE7D5), Color(0xFFF7B98E), Color.White, Color(0x30C4713E))
    "mint" -> NexlyTone(Color(0xFFDDF7EE), Color(0xFF9FE2CF), Color.White, Color(0x30328C74))
    else -> NexlyTone(Color(0xFFEDE2FF), Color(0xFFCDB1F8), Color.White, Color(0x307A56CC))
}

/**
 * A circular pastel glass tile with a real Tabler outline glyph. The optical
 * cues match the app's glass language: a white top rim, a soft tone-coloured
 * halo, a radial airlight wash entering from the top-left, and a WHITE glyph —
 * the reference puts the colour in the glass, never in the ink.
 */
@Composable
fun NexlyGlassGlyphTile(
    name: String,
    toneName: String? = null,
    size: Dp = 30.dp,
    glyphSize: Dp = 15.dp,
    modifier: Modifier = Modifier
) {
    val c = tone(toneName ?: NexlyIcons.toneFor(name))
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 5.dp,
                shape = CircleShape,
                ambientColor = c.halo.copy(alpha = 0.38f),
                spotColor = c.halo.copy(alpha = 0.46f)
            )
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(c.top, c.bottom)))
            .border(1.dp, Color.White.copy(alpha = 0.82f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = size * 0.20f, top = size * 0.14f)
                .size(size * 0.26f)
                .background(Color.White.copy(alpha = 0.38f), CircleShape)
        )
        NexlyGlyph(name = name, size = glyphSize, color = c.ink)
    }
}

/** A translucent circular avatar with the same rim as the icon tiles. */
@Composable
fun NexlyUserAvatar(size: Dp = 34.dp) {
    val colors = GsTheme.colors
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.58f),
                        colors.accentSoft.copy(alpha = 0.48f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.78f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        NexlyGlyph("user", size = size * 0.48f, color = Color.White.copy(alpha = 0.94f))
    }
}

/**
 * The timetable itself: one glass card, icon-led rows with the time on its own
 * line above the activity, a closing line, and the timestamp at the trailing
 * edge. The card owns its glass because it is a sibling of the prose card, not
 * content nested inside it.
 */
@Composable
fun NexlyPlanCard(
    plan: NexlyPlanData,
    open: Boolean = false,
    fallbackTimestamp: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = GsTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .gsGlass(GsGlassRole.Card, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        NexlyInlineRichText(
            text = cleanPlanText(plan.title, 160),
            color = colors.textPrimary,
            fontSize = 15.sp,
            glyphSize = 18.dp,
            weight = FontWeight.SemiBold
        )
        if (plan.rows.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { index ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .background(colors.surface.copy(alpha = 0.55f), CircleShape)
                        )
                        Spacer(Modifier.width(11.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(
                                Modifier
                                    .fillMaxWidth(0.34f)
                                    .height(7.dp)
                                    .background(colors.textTertiary.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth(if (index == 1) 0.72f else 0.58f)
                                    .height(8.dp)
                                    .background(colors.textTertiary.copy(alpha = 0.10f), RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                plan.rows.forEach { row ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NexlyGlassGlyphTile(
                            name = row.icon,
                            toneName = row.tone,
                            size = 30.dp,
                            glyphSize = 15.dp
                        )
                        Spacer(Modifier.width(11.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = row.time,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = colors.textSecondary,
                                maxLines = 1
                            )
                            NexlyInlineRichText(
                                text = row.text,
                                color = colors.textPrimary,
                                fontSize = 13.5.sp,
                                glyphSize = 16.dp,
                                weight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
        val stamp = plan.timestamp ?: fallbackTimestamp?.let { timeLabelForPlan(it) }
        if (plan.closing != null || stamp != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                plan.closing?.let { closing ->
                    NexlyInlineRichText(
                        text = closing,
                        color = colors.textPrimary,
                        fontSize = 14.5.sp,
                        glyphSize = 17.dp
                    )
                    if (plan.closingIcon != null &&
                        !Regex(":([a-z0-9][a-z0-9-]{1,42}):").containsMatchIn(closing)
                    ) {
                        Spacer(Modifier.width(4.dp))
                        NexlyGlassGlyphTile(
                            name = plan.closingIcon,
                            toneName = "peach",
                            size = 23.dp,
                            glyphSize = 12.dp
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (stamp != null) {
                    Text(
                        text = stamp,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = colors.textTertiary
                    )
                }
            }
        }
    }
}

@Composable
fun NexlyInlineRichText(
    text: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    glyphSize: Dp,
    weight: FontWeight = FontWeight.Medium,
) {
    val context = LocalContext.current
    remember(context) { NexlyIcons.ensureLoaded(context.applicationContext) }
    val annotated = remember(text, color, fontSize, glyphSize, weight) {
        buildAnnotatedString {
            for (part in NexlyIcons.inlineParts(text)) {
                if (part.isIcon) {
                    withStyle(
                        SpanStyle(
                            fontFamily = NexlyIcons.family,
                            fontSize = glyphSize.value.sp,
                            color = color
                        )
                    ) { append(NexlyIcons.glyph(part.value)) }
                } else {
                    append(part.value)
                }
            }
        }
    }
    Text(
        text = annotated,
        style = TextStyle(
            fontSize = fontSize,
            lineHeight = (fontSize.value + 3).sp,
            fontWeight = weight
        ),
        color = color
    )
}

/** Clock label used only when the model omitted a plan timestamp. */
private fun timeLabelForPlan(iso: String): String? {
    if (iso.isBlank()) return null
    return runCatching {
        val parsed = java.time.OffsetDateTime.parse(iso)
        parsed.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", Locale.US))
    }.getOrNull()
}

/**
 * The reference stage behind the conversation: a warm pearl wash, four
 * translucent bubbles and the app's soft lotus in the lower-left. The wash and
 * bubbles are drawn; the lotus is the existing transparent asset, reused so
 * the chat does not fork a second illustration.
 */
@Composable
fun Modifier.nexlyPearlBackdrop(): Modifier {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val lotus = remember(context) {
        runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.gs_home_lotus_new)
                .asImageBitmap()
        }.getOrNull()
    }
    return drawBehind {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.verticalGradient(
                if (dark) {
                    listOf(
                        Color(0xFF171210),
                        Color(0xFF241A17),
                        Color(0xFF1A1311)
                    )
                } else {
                    listOf(
                        Color(0xFFFAF0EA),
                        Color(0xFFF7E7E0),
                        Color(0xFFF8EEF0)
                    )
                }
            )
        )
        fun bubble(cx: Float, cy: Float, radius: Float, alpha: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = alpha),
                        Color(0xFFFFD8C4).copy(alpha = alpha * 0.36f),
                        Color(0xFFDCD4FF).copy(alpha = alpha * 0.18f),
                        Color.Transparent
                    ),
                    center = Offset(cx - radius * 0.22f, cy - radius * 0.28f),
                    radius = radius
                ),
                radius = radius,
                center = Offset(cx, cy)
            )
        }
        bubble(w * 0.94f, h * 0.08f, w * 0.44f, if (dark) 0.10f else 0.52f)
        bubble(w * -0.06f, h * 0.30f, w * 0.38f, if (dark) 0.07f else 0.34f)
        bubble(w * 0.16f, h * 0.86f, w * 0.46f, if (dark) 0.07f else 0.30f)
        bubble(w * 0.96f, h * 0.78f, w * 0.30f, if (dark) 0.06f else 0.24f)
        lotus?.let { image ->
            if (image.width > 0 && image.height > 0) {
                val targetW = w * 1.15f
                val targetH = targetW * (image.height.toFloat() / image.width.toFloat())
                drawImage(
                    image = image,
                    dstOffset = IntOffset(
                        (w * 0.22f - targetW / 2f).roundToInt(),
                        (h * 0.88f - targetH / 2f).roundToInt()
                    ),
                    dstSize = IntSize(targetW.roundToInt(), targetH.roundToInt()),
                    alpha = if (dark) 0.08f else 0.20f,
                    filterQuality = FilterQuality.Medium
                )
            }
        }
    }
}
