package com.grapsee.gsai.ui.chat.content

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import com.grapsee.gsai.ui.theme.LocalGsIsDark
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import com.grapsee.gsai.ui.icons.GsIcons
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.chat.NexlyPlanProgressStore
import com.grapsee.gsai.data.remote.PlanProgressDto
import com.grapsee.gsai.data.remote.PlanProgressItemDto
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.gsGlass
import com.grapsee.gsai.ui.theme.GsTheme
import kotlinx.coroutines.launch
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
    val family: FontFamily = FontFamily(Font(com.grapsee.gsai.R.font.tabler_icons))

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
        "mobile" to "device-mobile", "eyeglasses" to "eyeglass", "glasses" to "eyeglass",
        "circle-filled" to "circle-dot", "dot" to "circle-dot", "bullet" to "circle-dot",
        "lightning" to "bolt", "zap" to "bolt", "flash" to "bolt", "energy" to "bolt",
        "money" to "coin", "dollar" to "currency-dollar", "currency" to "coin", "price" to "coin",
        "graph" to "chart-bar", "chart" to "chart-bar", "analytics" to "chart-line", "stats" to "chart-bar",
        "todo" to "checkbox", "task" to "checkbox", "tasks" to "list-check", "checklist" to "list-check",
        "tip" to "bulb", "insight" to "bulb", "magic" to "sparkles", "ai" to "sparkles",
        "edu" to "school", "academic" to "school", "university" to "building-bank",
        "security" to "shield-check", "safe" to "shield-check", "verified" to "shield-check",
        "error" to "alert-circle", "danger" to "alert-triangle", "info" to "info-circle",
        "help" to "help-circle", "question" to "help-circle", "checkmark" to "checks"
    )

    /** Known emoji → the closest semantic glass glyph. Prompt bans emoji; this
     * is the renderer safety net, never an instruction to the model. */
    private val emoji = mapOf(
        "🌅" to "sunrise", "🌄" to "sunrise", "☀️" to "sunrise", "☀" to "sunrise",
        "📚" to "book-2", "📖" to "book-2", "💻" to "device-laptop", "🖥️" to "device-laptop",
        "🍽️" to "tools-kitchen-2", "🍴" to "tools-kitchen-2", "🍔" to "tools-kitchen-2",
        "📝" to "file-text", "📄" to "file-text", "🏃" to "run", "🏃‍♂️" to "run", "🏃‍♀️" to "run",
        "🏋️" to "barbell", "🎮" to "device-gamepad-2", "🕹️" to "device-gamepad-2",
        "🌙" to "moon", "😴" to "moon", "✨" to "sparkles", "✦" to "sparkles", "🧠" to "brain",
        "⚙️" to "settings", "⚙" to "settings", "💪" to "flame", "🔥" to "flame",
        "❤️" to "heart", "❤" to "heart", "✅" to "circle-check", "✔️" to "circle-check",
        "☑️" to "checkbox", "☑" to "checkbox",
        "⭐" to "star", "🎯" to "target", "☕" to "coffee", "🎨" to "palette",
        "🎵" to "music", "🚶" to "walk", "📅" to "calendar", "⏰" to "clock",
        "⏱️" to "clock", "⏳" to "hourglass",
        "⚡" to "bolt", "⚠️" to "alert-triangle", "⚠" to "alert-triangle", "💡" to "bulb",
        "📊" to "chart-bar", "📈" to "trending-up", "📉" to "trending-down",
        "👥" to "users", "👤" to "user", "🧮" to "calculator",
        "🔗" to "link", "🌐" to "world", "🌍" to "world", "🌎" to "world", "🌏" to "world",
        "🏛️" to "building-bank", "🏛" to "building-bank",
        "🚀" to "rocket", "🔒" to "lock", "🔓" to "lock-open",
        "🛡️" to "shield", "🛡" to "shield", "🐍" to "brand-python",
        "🔍" to "search", "🔎" to "search", "📌" to "pin", "📎" to "paperclip",
        "📋" to "clipboard", "📁" to "folder", "📂" to "folder-open",
        "💬" to "message-circle", "💭" to "message-circle", "🔔" to "bell",
        "💰" to "coin", "💵" to "cash", "💳" to "credit-card",
        "🏆" to "trophy", "🥇" to "trophy", "🎉" to "sparkles",
        "🧪" to "flask", "🧬" to "dna", "🔬" to "microscope", "🎓" to "school",
        "✈️" to "plane", "🚗" to "car", "🏠" to "home", "🌱" to "plant", "🌿" to "leaf",
        "🔋" to "battery", "🔌" to "plug", "🧭" to "compass", "🗺️" to "map",
        "🔑" to "key", "🛠️" to "tools", "🛠" to "tools", "🔧" to "tool",
        "📱" to "device-mobile", "🛒" to "shopping-cart", "🧾" to "receipt",
        "📧" to "mail", "✉️" to "mail", "📞" to "phone",
        "🎥" to "video", "📷" to "camera", "🖼️" to "photo", "🖼" to "photo", "🎬" to "movie",
        "👍" to "thumb-up", "👎" to "thumb-down", "😊" to "mood-smile", "😀" to "mood-smile",
        "😃" to "mood-smile", "😄" to "mood-smile", "😁" to "mood-smile", "🙂" to "mood-smile",
        "😉" to "mood-wink", "😍" to "heart", "🥰" to "heart", "😎" to "sunglasses",
        "🤓" to "eyeglass", "🧐" to "search", "🥳" to "sparkles",
        "😞" to "mood-sad", "😔" to "mood-sad", "😟" to "mood-sad", "😕" to "mood-sad",
        "🙁" to "mood-sad", "☹️" to "mood-sad", "☹" to "mood-sad", "😢" to "mood-sad", "😭" to "mood-sad",
        "😤" to "flame", "😠" to "alert-circle", "😡" to "alert-circle", "🤯" to "brain",
        "😱" to "alert-triangle", "🤗" to "heart", "🙌" to "sparkles", "👏" to "sparkles",
        "🙏" to "heart", "🤝" to "users", "👀" to "eye", "👁️" to "eye", "👁" to "eye",
        "💯" to "circle-check", "💎" to "diamond", "👑" to "crown", "🎁" to "gift",
        "🎈" to "sparkles", "🎊" to "sparkles", "🥈" to "medal", "🥉" to "medal", "🏅" to "medal",
        "⚽" to "ball-football", "🏀" to "ball-basketball", "🎾" to "ball-tennis",
        "💧" to "droplet", "🌊" to "wave-sine", "❄️" to "snowflake", "❄" to "snowflake",
        "🌈" to "sparkles", "🌳" to "tree", "🌲" to "tree", "🌴" to "tree", "🌵" to "plant",
        "🌸" to "flower", "🌺" to "flower", "🌻" to "flower", "🌹" to "flower", "🌷" to "flower",
        "🍀" to "leaf", "🍁" to "leaf", "🍂" to "leaf", "🍃" to "leaf",
        "🍎" to "apple", "🍏" to "apple", "🍋" to "lemon", "🥗" to "salad", "🍕" to "pizza",
        "🍵" to "mug", "🍺" to "beer", "🍷" to "glass-full",
        "🚌" to "bus", "🚆" to "train", "🚇" to "train", "🚲" to "bike", "🚢" to "ship",
        "⚓" to "anchor", "⛽" to "gas-station", "🚨" to "urgent", "🛑" to "octagon-off",
        "🚧" to "barrier-block", "🏥" to "building-hospital", "🏫" to "school",
        "🏢" to "building", "🏭" to "building-factory-2", "⛰️" to "mountain", "⛰" to "mountain",
        "🏔️" to "mountain", "🏖️" to "beach", "🎧" to "headphones", "🎤" to "microphone",
        "🎹" to "music", "🎸" to "music", "🥁" to "music", "📺" to "device-tv",
        "🖨️" to "printer", "⌨️" to "keyboard", "🖱️" to "mouse", "💾" to "device-floppy",
        "💿" to "disc", "🔭" to "telescope", "📡" to "satellite", "💊" to "pill",
        "🩺" to "stethoscope", "🛏️" to "bed", "🚿" to "bath", "🛁" to "bath",
        "🧹" to "brush", "🧯" to "fire-extinguisher",
        "🔴" to "circle-dot", "🟠" to "circle-dot", "🟡" to "circle-dot",
        "🟢" to "circle-dot", "🔵" to "circle-dot", "🟣" to "circle-dot",
        "⚫" to "circle-dot", "⚪" to "circle-dot", "🟤" to "circle-dot",
        "⬛" to "square", "⬜" to "square", "▪️" to "square", "▫️" to "square",
        "◾" to "square", "◽" to "square", "◼️" to "square", "◻️" to "square",
        "🔶" to "diamond", "🔷" to "diamond", "🔸" to "diamond", "🔹" to "diamond",
        "🔺" to "triangle", "🔻" to "triangle-inverted",
        "▶️" to "player-play", "▶" to "player-play", "◀️" to "player-skip-back", "◀" to "player-skip-back",
        "⏩" to "player-track-next", "⏪" to "player-track-prev", "⏸️" to "player-pause", "⏹️" to "player-stop",
        "🔄" to "refresh", "🔁" to "repeat", "🔀" to "arrows-shuffle",
        "➡️" to "arrow-right", "⬅️" to "arrow-left", "⬆️" to "arrow-up", "⬇️" to "arrow-down",
        "↗️" to "arrow-up-right", "↘️" to "arrow-down-right", "↙️" to "arrow-down-left", "↖️" to "arrow-up-left",
        "↔️" to "arrows-left-right", "↕️" to "arrows-up-down", "↩️" to "arrow-back-up", "↪️" to "arrow-forward-up",
        "⤴️" to "arrow-ramp-right", "⤵️" to "arrow-ramp-left",
        "➕" to "plus", "➖" to "minus", "➗" to "divide", "✖️" to "x", "✖" to "x",
        "♾️" to "infinity", "♾" to "infinity", "💲" to "currency-dollar", "💱" to "arrows-exchange",
        "™️" to "trademark", "™" to "trademark", "©️" to "copyright", "©" to "copyright",
        "®️" to "registered", "®" to "registered", "♻️" to "recycle", "♻" to "recycle",
        "‼️" to "alert-circle", "⁉️" to "help-circle", "〰️" to "wave-sine", "〽️" to "chart-line",
        "❇️" to "sparkles", "✳️" to "asterisk", "✴️" to "star",
        "⚖️" to "scale", "⚖" to "scale", "⚗️" to "flask", "⚛️" to "atom", "⚛" to "atom",
        "0️⃣" to "number-0", "1️⃣" to "number-1", "2️⃣" to "number-2", "3️⃣" to "number-3",
        "4️⃣" to "number-4", "5️⃣" to "number-5", "6️⃣" to "number-6", "7️⃣" to "number-7",
        "8️⃣" to "number-8", "9️⃣" to "number-9", "🔟" to "numbers",
        "🤔" to "brain", "❌" to "circle-x", "❓" to "help-circle", "❗" to "alert-circle",
        "ℹ️" to "info-circle", "ℹ" to "info-circle"
    )

    private val tokenRegex = Regex(":([a-z0-9][a-z0-9-]{1,42}):")

    private fun fallbackIconForCodePoint(cp: Int): String? = when (cp) {
        in 0x1F3FB..0x1F3FF -> null // skin tone modifier
        in 0x1F600..0x1F64F, in 0x1F910..0x1F92F, in 0x1F970..0x1F97A, in 0x1FAE0..0x1FAEF -> "mood-smile"
        in 0x1F440..0x1F487, in 0x1F930..0x1F93F, in 0x1FAF0..0x1FAFF -> "user"
        in 0x1F330..0x1F343, in 0x1F400..0x1F43F, in 0x1F980..0x1F9AE, in 0x1FAB0..0x1FABF -> "leaf"
        in 0x1F32D..0x1F32F, in 0x1F344..0x1F37F, in 0x1F950..0x1F96F, in 0x1FAD0..0x1FADF -> "cup"
        in 0x1F380..0x1F3CF, in 0x1F93C..0x1F94F -> "trophy"
        in 0x1F680..0x1F6FF, in 0x1F3D0..0x1F3FF -> "compass"
        in 0x1F300..0x1F32C -> "sun"
        in 0x1F488..0x1F5FF, in 0x1FA70..0x1FA9F -> "sparkles"
        else -> null
    }

    /** Replace known emoji with `:icon:` tokens, map any remaining emoji block
     * to a semantic `:icon:` token, and drop variation selectors and ZWJs. */
    fun normalize(text: String): String {
        // Numeric direction is source data; canonical non-emoji arrows keep
        // its sign intact when decorative emoji/variation selectors are removed.
        var out = text.replace(Regex("[↘↗]\\uFE0F?(?=\\s*[+-]?\\s*[\$€£¥₹৳]?\\s*\\d)")) {
            if (it.value.startsWith("↘")) "↓" else "↑"
        }
        for ((symbol, icon) in emoji.entries.sortedByDescending { it.key.length }) {
            out = out.replace(symbol, ":$icon:")
        }
        val filtered = StringBuilder(out.length)
        var i = 0
        var lastWasFallbackIcon = false
        while (i < out.length) {
            val cp = out.codePointAt(i)
            val fallbackIcon = fallbackIconForCodePoint(cp)
            val pictograph = (cp > 0x7F && android.icu.lang.UCharacter.hasBinaryProperty(cp, android.icu.lang.UProperty.EMOJI)) ||
                cp in 0xE0020..0xE007F || cp in 0x1F000..0x1FAFF ||
                cp in 0x2600..0x27BF ||
                cp in 0x2300..0x23FF ||
                cp in 0x2194..0x21AA ||
                cp in 0x25AA..0x25FE ||
                cp in 0x2934..0x2935 ||
                cp in 0x2B05..0x2B07 ||
                cp in 0x2B1B..0x2B1C ||
                cp == 0x203C ||
                cp == 0x2049 ||
                cp == 0x2122 ||
                cp == 0x2139 ||
                cp == 0x2B50 ||
                cp == 0x2B55 ||
                cp == 0x3030 ||
                cp == 0x303D ||
                cp == 0x3297 ||
                cp == 0x3299 ||
                cp in 0xFE00..0xFE0F ||
                cp == 0x200D ||
                cp == 0x20E3 ||
                cp in 0x1F1E6..0x1F1FF
            if (!pictograph) {
                filtered.appendCodePoint(cp)
                if (!Character.isWhitespace(cp)) lastWasFallbackIcon = false
            } else if (fallbackIcon != null && !lastWasFallbackIcon) {
                filtered.append(":$fallbackIcon:")
                lastWasFallbackIcon = true
            }
            i += Character.charCount(cp)
        }
        return filtered.toString()
    }

    /** Extract a leading `:icon:` or emoji token (even inside leading `**bold**`) as (iconName, remainingCleanText). */
    fun extractLeadingIcon(text: String): Pair<String?, String> {
        val norm = normalize(text).trim()
        val m = Regex("^(\\*{1,3}|_{1,3})?\\s*:([a-z0-9][a-z0-9-]{1,42}):\\s*([\\s\\S]*)$").find(norm)
        if (m != null) {
            val prefix = m.groupValues[1]
            val rawIcon = m.groupValues[2]
            val tail = m.groupValues[3].replace(tokenRegex, "").trim()
            return resolve(rawIcon) to "$prefix$tail"
        }
        return null to norm.replace(tokenRegex, "").trim()
    }

    /** Plain string with all emojis and `:icon:` tokens stripped cleanly. */
    fun cleanPlainText(text: String): String =
        normalize(text).replace(tokenRegex, "").replace(Regex("\\s+"), " ").trim()

    private val semanticKeywords: List<Triple<Regex, String, String>> = listOf(
        Triple(Regex("\\b(python|django|flask|pandas|numpy|pytorch)\\b", RegexOption.IGNORE_CASE), "brand-python", "blue"),
        Triple(Regex("\\b(java|spring|jvm|maven|coffee|cafe|espresso)\\b", RegexOption.IGNORE_CASE), "coffee", "peach"),
        Triple(Regex("\\b(javascript|node\\.?js|express|npm|v8)\\b", RegexOption.IGNORE_CASE), "brand-javascript", "amber"),
        Triple(Regex("\\b(typescript|tsx|deno)\\b", RegexOption.IGNORE_CASE), "brand-typescript", "blue"),
        Triple(Regex("\\b(react|next\\.?js|jsx|redux)\\b", RegexOption.IGNORE_CASE), "brand-react", "blue"),
        Triple(Regex("\\b(swift|swiftui|ios|xcode|apple)\\b", RegexOption.IGNORE_CASE), "brand-apple", "lavender"),
        Triple(Regex("\\b(android|kotlin|jetpack|gradle)\\b", RegexOption.IGNORE_CASE), "brand-android", "green"),
        Triple(Regex("\\b(rust|cargo)\\b", RegexOption.IGNORE_CASE), "brand-rust", "peach"),
        Triple(Regex("\\b(golang|go\\b)", RegexOption.IGNORE_CASE), "brand-golang", "mint"),
        Triple(Regex("(?:^|\\b)(c\\+\\+|cpp|clang)(?:\\b|$)", RegexOption.IGNORE_CASE), "brand-cpp", "blue"),
        Triple(Regex("^(c|c#|csharp)$", RegexOption.IGNORE_CASE), "code", "blue"),
        Triple(Regex("\\b(docker|container|kubernetes|k8s)\\b", RegexOption.IGNORE_CASE), "brand-docker", "blue"),
        Triple(Regex("\\b(git|github|commit|branch|pull request)\\b", RegexOption.IGNORE_CASE), "brand-github", "lavender"),
        Triple(Regex("\\b(database|sql|postgres|mysql|sqlite|redis|mongodb|schema|query)\\b", RegexOption.IGNORE_CASE), "database", "blue"),
        Triple(Regex("\\b(cloud|aws|azure|gcp|serverless|cdn|deploy)\\b", RegexOption.IGNORE_CASE), "cloud-computing", "blue"),
        Triple(Regex("\\b(security|auth|jwt|oauth|encrypt|password|privacy|shield|safe)\\b", RegexOption.IGNORE_CASE), "shield-check", "green"),
        Triple(Regex("\\b(performance|speed|fast|latency|throughput|optimize|cache|bolt)\\b", RegexOption.IGNORE_CASE), "bolt", "amber"),
        Triple(Regex("\\b(api|webhook|endpoint|rest|graphql|http|network|socket)\\b", RegexOption.IGNORE_CASE), "plug-connected", "mint"),
        Triple(Regex("\\b(bug|error|issue|warning|risk|pitfall|caution|alert|fail)\\b", RegexOption.IGNORE_CASE), "alert-triangle", "amber"),
        Triple(Regex("\\b(test|qa|verify|validation|check|assert|pass)\\b", RegexOption.IGNORE_CASE), "circle-check", "green"),
        Triple(Regex("\\b(loan|mortgage|apr|emi|amortiz|bank|debt|interest rate)\\b", RegexOption.IGNORE_CASE), "building-bank", "blue"),
        Triple(Regex("\\b(invest|compound|portfolio|dividend|stock|etf|roi|wealth|return|growth|profit)\\b", RegexOption.IGNORE_CASE), "trending-up", "green"),
        Triple(Regex("\\b(budget|salary|income|saving|expense|cost|price|revenue|money|payment|tax|fee)\\b", RegexOption.IGNORE_CASE), "coin", "amber"),
        Triple(Regex("\\b(formula|equation|math|calculate|calculator|sum|ratio|average)\\b", RegexOption.IGNORE_CASE), "calculator", "amber"),
        Triple(Regex("\\b(chart|metric|analytics|stat|benchmark|trend|data|distribution)\\b", RegexOption.IGNORE_CASE), "chart-bar", "lavender"),
        Triple(Regex("\\b(calorie|protein|macro|nutrition|diet|meal|food|vitamin|carbs|fat)\\b", RegexOption.IGNORE_CASE), "salad", "green"),
        Triple(Regex("\\b(workout|exercise|gym|fitness|strength|cardio|muscle|training|run)\\b", RegexOption.IGNORE_CASE), "barbell", "peach"),
        Triple(Regex("\\b(health|medical|heart|sleep|recovery|wellness|symptom|doctor)\\b", RegexOption.IGNORE_CASE), "heartbeat", "pink"),
        Triple(Regex("\\b(study|learn|course|book|read|exam|lesson|education|concept)\\b", RegexOption.IGNORE_CASE), "book-2", "lavender"),
        Triple(Regex("\\b(time|schedule|timeline|deadline|duration|hour|minute|morning|daily)\\b", RegexOption.IGNORE_CASE), "clock", "amber"),
        Triple(Regex("\\b(date|calendar|week|month|year|quarter|roadmap|milestone)\\b", RegexOption.IGNORE_CASE), "calendar", "blue"),
        Triple(Regex("\\b(travel|flight|trip|hotel|airport|vacation|tour)\\b", RegexOption.IGNORE_CASE), "plane", "blue"),
        Triple(Regex("\\b(location|map|city|country|region|route|address|global|world)\\b", RegexOption.IGNORE_CASE), "world", "mint"),
        Triple(Regex("\\b(ai|llm|neural|model|machine learning|gpt|agent|brain|smart)\\b", RegexOption.IGNORE_CASE), "brain", "lavender"),
        Triple(Regex("\\b(code|function|algorithm|class|module|compile|syntax|script)\\b", RegexOption.IGNORE_CASE), "code", "lavender"),
        Triple(Regex("\\b(user|team|customer|client|people|community|audience|member)\\b", RegexOption.IGNORE_CASE), "users", "blue"),
        Triple(Regex("\\b(goal|target|objective|focus|mission|priority|kpi)\\b", RegexOption.IGNORE_CASE), "target", "pink"),
        Triple(Regex("\\b(winner|best|top|rank|award|champion|leader|advantage)\\b", RegexOption.IGNORE_CASE), "trophy", "amber"),
        Triple(Regex("\\b(launch|start|ship|release|scale|boost|upgrade|future)\\b", RegexOption.IGNORE_CASE), "rocket", "peach"),
        Triple(Regex("\\b(setting|config|setup|install|tool|environment|option)\\b", RegexOption.IGNORE_CASE), "settings", "mint"),
        Triple(Regex("\\b(why|tip|insight|idea|note|summary|takeaway|key|pro tip)\\b", RegexOption.IGNORE_CASE), "bulb", "amber")
    )

    /**
     * Resolve any heading, bullet label, step title, or table entity to a semantic
     * custom glassy icon + tone from the 5,247 Tabler registry.
     */
    fun semanticIconFor(raw: String, fallbackIcon: String = "sparkles"): Pair<String, String> {
        val (explicit, clean) = extractLeadingIcon(raw)
        if (explicit != null) {
            val resolved = resolve(explicit)
            return resolved to toneFor(resolved)
        }
        val plain = cleanPlainText(clean)
        for ((re, icon, tone) in semanticKeywords) {
            if (re.containsMatchIn(plain)) {
                val resolved = resolve(icon)
                return resolved to tone
            }
        }
        val map = codepoints
        val words = plain.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9\\s-]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 3 }
        for (w in words) {
            val brand = "brand-$w"
            if (map != null && map.containsKey(brand)) return brand to toneFor(brand)
            aliases[w]?.let { a -> return resolve(a) to toneFor(a) }
            if (map != null && map.containsKey(w)) return w to toneFor(w)
            if (w.endsWith("s") && w.length > 3) {
                val sing = w.dropLast(1)
                aliases[sing]?.let { a -> return resolve(a) to toneFor(a) }
                if (map != null && map.containsKey(sing)) return sing to toneFor(sing)
            }
        }
        if (fallbackIcon.isEmpty()) return "" to "lavender"
        val fb = resolve(fallbackIcon)
        return fb to toneFor(fb)
    }

    /** Split a run on the inline `:icon:` grammar. Letter-initial tokens are
     * always resolved to a valid icon glyph so raw `:icon:` syntax never leaks. */
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
            if (name[0] in 'a'..'z' || map == null || map.containsKey(name) || aliases.containsKey(name)) {
                out += NexlyInlinePart(resolve(name), true)
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
        aliases[name]?.let { name = it }
        val map = codepoints ?: return name
        if (map.containsKey(name)) return name
        val stripped = name.removeSuffix("-filled").removeSuffix("-outline")
        aliases[stripped]?.let { if (map.containsKey(it)) return it }
        if (map.containsKey(stripped)) return stripped
        if (stripped.endsWith("s") && stripped.length > 3) {
            val singular = stripped.dropLast(1)
            aliases[singular]?.let { if (map.containsKey(it)) return it }
            if (map.containsKey(singular)) return singular
        }
        if (stripped.endsWith("es") && stripped.length > 4) {
            val singularEs = stripped.dropLast(2)
            aliases[singularEs]?.let { if (map.containsKey(it)) return it }
            if (map.containsKey(singularEs)) return singularEs
        }
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
    // The reference identity mark: a lilac sparkle in a lit glass circle, the
    // same language as the header brand — not the peach orb.
    Box(
        modifier = Modifier
            .size(size)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.94f), CircleShape)
            .border(1.dp, Color.White, CircleShape)
            .semantics { contentDescription = "Nexly" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            GsIcons.Sparkles,
            contentDescription = null,
            tint = Color(0xFF8E7BFF),
            modifier = Modifier.size(size * 0.5f),
        )
    }
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

/**
 * The Nexly assistant card runs one step smaller than the app's default body
 * copy, matching the sheet's airy preamble. Null keeps every other chat surface
 * on its normal typography.
 */
internal val LocalNexlyProseStyle = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.ui.text.TextStyle?> { null }

/** Nexly cards soft-wrap code; the rest of the app keeps horizontal scroll. */
internal val LocalNexlyCodeWrap = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * The sheet's ink is a soft neutral grey, not the app's near-black brown.
 * This is the single biggest reason the first native pass read heavier than
 * the reference: every line was at maximum contrast.
 */
internal object NexlyInk {
    // THE INK FOLLOWS THE THEME. Every value here used to be a fixed mid-grey,
    // chosen against the light pearl field. In dark mode the same grey sat on a
    // 0.44-alpha dark card: roughly 1.6:1, which is why a dark chat answered in
    // what looked like grey smoke. The light values are unchanged; the dark ones
    // are the same hues lifted until the body text clears AA on the dark card.
    private val dark: Boolean @Composable get() = LocalGsIsDark.current

    val prose: Color @Composable get() = if (dark) Color(0xFFEDEAF6) else Color(0xFF4A4750)
    val title: Color @Composable get() = if (dark) Color(0xFFF4F1FA) else Color(0xFF555860)
    val row: Color @Composable get() = if (dark) Color(0xFFDDD8EA) else Color(0xFF5E6068)
    val label: Color @Composable get() = if (dark) Color(0xFFE6E1F2) else Color(0xFF4B4E58)
    val meta: Color @Composable get() = if (dark) Color(0xFFA9A3BD) else Color(0xFF8C8E96)
    val header: Color @Composable get() = if (dark) Color(0xFFF6F3FC) else Color(0xFF3B3A40)
    val headerMeta: Color @Composable get() = if (dark) Color(0xFFA9A3BD) else Color(0xFF7E7E86)
    val peach: Color @Composable get() = if (dark) Color(0xFFE8A87C) else Color(0xFFD57F4C)
}

/**
 * The sheet's cards are thinner than the app's standard card glass: a clear
 * white veil over the pearl field, a hairline rim and almost no shadow. The
 * shared `.gsGlass(Card)` is intentionally milkier for menus and sheets.
 */
@Composable
internal fun Modifier.nexlyGlassCard(corner: Dp = 20.dp): Modifier {
    val dark = LocalGsIsDark.current
    val shape = RoundedCornerShape(corner)
    return this
        .shadow(
            elevation = 3.dp,
            shape = shape,
            ambientColor = Color(0x126B4A3A),
            spotColor = Color(0x186B4A3A)
        )
        .clip(shape)
        .background(
            // THE CARD IS FROSTED GLASS, NOT A BEIGE SLAB. The old fill was a
            // warm grey (F3E8E4) at 0.62 over a cool lilac backdrop, which reads
            // as mud — the "dark mud" complaint, one layer up. Frost is the
            // backdrop seen through a white veil: high white alpha in light,
            // a faint white lift in dark, so the bloom still shows through and
            // the card never turns into a grey rectangle.
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (dark) 0.10f else 0.78f),
                    Color.White.copy(alpha = if (dark) 0.055f else 0.60f)
                )
            ),
            shape
        )
        // The lit rim, brightest at the top edge — the same optical cue the app's
        // gsGlass material uses, so a chat card and a settings card are the same
        // substance.
        .border(
            width = 0.9.dp,
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (dark) 0.22f else 0.95f),
                    Color.White.copy(alpha = if (dark) 0.07f else 0.45f)
                )
            ),
            shape = shape
        )
}

private fun tone(name: String): NexlyTone = when (name) {
    "amber" -> NexlyTone(Color(0xFFFFF7E8), Color(0xFFFBE5C2), Color(0xFFE09A35), Color(0x55C9823A))
    "lavender" -> NexlyTone(Color(0xFFF7F1FF), Color(0xFFE8DDFB), Color(0xFF7863D4), Color(0x55705AD0))
    "blue" -> NexlyTone(Color(0xFFF0F8FF), Color(0xFFDCEBFB), Color(0xFF4D8DCF), Color(0x553E83BE))
    "green" -> NexlyTone(Color(0xFFF0FBF3), Color(0xFFDDF3E4), Color(0xFF4DA568), Color(0x55388F54))
    "pink" -> NexlyTone(Color(0xFFFFF3F7), Color(0xFFFBE1E9), Color(0xFFD6678A), Color(0x55BB5875))
    "peach" -> NexlyTone(Color(0xFFFFF6ED), Color(0xFFFBE4D2), Color(0xFFD57F4C), Color(0x55C4713E))
    "mint" -> NexlyTone(Color(0xFFF0FBF7), Color(0xFFDDF3EC), Color(0xFF46A58A), Color(0x55328C74))
    else -> NexlyTone(Color(0xFFF7F1FF), Color(0xFFE9DFFA), Color(0xFF8065D8), Color(0x557A56CC))
}

/**
 * A circular pastel glass tile with a real Tabler outline glyph. The sheet
 * uses a very pale tone wash with the glyph itself carrying the saturated
 * colour — not a saturated chip with white ink, which reads as a heavy badge.
 */
@Composable
fun NexlyGlassGlyphTile(
    name: String,
    toneName: String? = null,
    size: Dp = 30.dp,
    glyphSize: Dp = 15.dp,
    active: Boolean = false,
    modifier: Modifier = Modifier
) {
    val c = tone(toneName ?: NexlyIcons.toneFor(name))
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                if (active) {
                    drawCircle(
                        color = c.halo.copy(alpha = 0.70f),
                        radius = this.size.minDimension / 2f + 2.dp.toPx(),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
            .shadow(
                elevation = 2.dp,
                shape = CircleShape,
                ambientColor = c.halo.copy(alpha = 0.22f),
                spotColor = c.halo.copy(alpha = 0.26f)
            )
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(c.top, c.bottom)))
            .border(1.dp, Color.White.copy(alpha = 0.92f), CircleShape),
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
    modifier: Modifier = Modifier,
    planConversationId: String? = null
) {
    val colors = GsTheme.colors
    val context = LocalContext.current
    val rowPairs = remember(plan) {
        plan.rows.map { row -> row.time to row.text }
    }
    val fingerprint = remember(plan.title, rowPairs) {
        NexlyPlanProgressStore.fingerprint(plan.title, rowPairs)
    }
    val doneRows = remember(planConversationId, fingerprint) {
        mutableStateMapOf<String, Boolean>().apply {
            NexlyPlanProgressStore
                .load(context, planConversationId, fingerprint)
                .forEach { put(it, true) }
        }
    }
    val preference = remember(planConversationId, fingerprint) {
        mutableStateOf(
            NexlyPlanProgressStore.loadPreference(context, planConversationId, fingerprint)
        )
    }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    fun pushProgress() {
        val id = planConversationId ?: return
        val items = rowPairs.map { (time, text) ->
            PlanProgressItemDto(
                key = time + "|" + text,
                label = if (time.isBlank()) text else "$time — $text"
            )
        }
        val dto = PlanProgressDto(
            fingerprint = fingerprint,
            title = plan.title,
            items = items,
            done = doneRows.filterValues { it }.keys.toList(),
            preference = preference.value
        )
        scope.launch {
            runCatching { ServiceLocator.api.savePlanProgress(id, dto) }
        }
    }

    // Hydrate from the server so a second device sees the same checklist.
    LaunchedEffect(planConversationId, fingerprint) {
        val id = planConversationId ?: return@LaunchedEffect
        val remote = runCatching { ServiceLocator.api.planProgress(id) }.getOrNull()
            ?: return@LaunchedEffect
        if (remote.fingerprint == fingerprint) {
            doneRows.clear()
            remote.done.forEach { doneRows[it] = true }
            preference.value = remote.preference
            NexlyPlanProgressStore.save(
                context = context,
                conversationId = id,
                fingerprint = fingerprint,
                title = plan.title,
                rows = rowPairs,
                done = remote.done.toSet(),
                preference = remote.preference
            )
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .nexlyGlassCard(20.dp)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        NexlyInlineRichText(
            text = cleanPlanText(plan.title, 160),
            color = NexlyInk.title,
            fontSize = 12.sp,
            glyphSize = 14.dp,
            weight = FontWeight.Medium
        )
        if (plan.rows.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                plan.rows.forEach { row ->
                    val rowKey = row.time + "|" + row.text
                    val done = doneRows[rowKey] == true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .toggleable(
                                value = done,
                                role = Role.Checkbox,
                                onValueChange = { checked ->
                                    doneRows[rowKey] = checked
                                    NexlyPlanProgressStore.save(
                                        context = context,
                                        conversationId = planConversationId,
                                        fingerprint = fingerprint,
                                        title = plan.title,
                                        rows = rowPairs,
                                        done = doneRows.filterValues { it }.keys,
                                        preference = preference.value
                                    )
                                    pushProgress()
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            )
                            .semantics { contentDescription = "${row.time}, ${row.text}" }
                            .padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NexlyGlassGlyphTile(
                            name = row.icon,
                            toneName = row.tone,
                            size = 24.dp,
                            glyphSize = 12.dp,
                            modifier = Modifier.alpha(if (done) 0.55f else 1f)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = row.time,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    lineHeight = 11.5.sp,
                                    fontWeight = FontWeight.Normal,
                                    textDecoration = if (done) TextDecoration.LineThrough else null
                                ),
                                color = NexlyInk.meta,
                                maxLines = 1
                            )
                            NexlyInlineRichText(
                                text = row.text,
                                color = if (done) NexlyInk.meta else NexlyInk.row,
                                fontSize = 12.5.sp,
                                glyphSize = 13.5.dp,
                                weight = FontWeight.Normal,
                                textDecoration = if (done) TextDecoration.LineThrough else null
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .then(
                                    if (done) {
                                        Modifier.background(NexlyInk.peach, CircleShape)
                                    } else {
                                        Modifier.border(
                                            1.dp,
                                            NexlyInk.meta.copy(alpha = 0.38f),
                                            CircleShape
                                        )
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (done) {
                                NexlyGlyph(name = "check", size = 9.dp, color = Color.White)
                            }
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
                        color = NexlyInk.row,
                        fontSize = 12.5.sp,
                        glyphSize = 12.5.dp,
                        glyphColor = NexlyInk.peach
                    )
                    if (plan.closingIcon != null &&
                        !Regex(":([a-z0-9][a-z0-9-]{1,42}):").containsMatchIn(closing)
                    ) {
                        Spacer(Modifier.width(4.dp))
                        NexlyGlyph(
                            name = plan.closingIcon,
                            size = 12.dp,
                            color = tone("peach").ink
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (stamp != null) {
                    Text(
                        text = stamp,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = NexlyInk.meta
                    )
                }
            }
        }
        // Completion is progress; this is the actual preference signal. Both
        // are persisted and sent as turn context on the next message.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Was this plan helpful?",
                style = MaterialTheme.typography.labelSmall.copy(
                    // 11.5, not 10.5: the question the whole card exists to
                    // ask was the smallest text on the screen.
                    fontSize = 11.5.sp,
                    lineHeight = 14.sp
                ),
                color = NexlyInk.meta
            )
            Spacer(Modifier.weight(1f))
            listOf("up" to "thumb-up", "down" to "thumb-down").forEach { (id, icon) ->
                val selected = preference.value == id
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = if (id == "up") "Mark plan helpful"
                            else "Mark plan not helpful"
                        ) {
                            preference.value = if (selected) null else id
                            NexlyPlanProgressStore.save(
                                context = context,
                                conversationId = planConversationId,
                                fingerprint = fingerprint,
                                title = plan.title,
                                rows = rowPairs,
                                done = doneRows.filterValues { it }.keys,
                                preference = preference.value
                            )
                            pushProgress()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        .semantics {
                            contentDescription = if (id == "up") "Plan helpful" else "Plan not helpful"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    NexlyGlyph(
                        name = icon,
                        size = 14.dp,
                        color = when {
                            !selected -> NexlyInk.meta
                            id == "up" -> NexlyInk.peach
                            else -> Color(0xFFC76D7D)
                        }
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
    glyphColor: Color? = null,
    textDecoration: TextDecoration? = null,
) {
    val context = LocalContext.current
    remember(context) { NexlyIcons.ensureLoaded(context.applicationContext) }
    val annotated = remember(text, color, fontSize, glyphSize, weight, glyphColor, textDecoration) {
        buildAnnotatedString {
            for (part in NexlyIcons.inlineParts(text)) {
                if (part.isIcon) {
                    withStyle(
                        SpanStyle(
                            fontFamily = NexlyIcons.family,
                            fontSize = glyphSize.value.sp,
                            color = glyphColor ?: color
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
            fontWeight = weight,
            textDecoration = textDecoration
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
    val dark = LocalGsIsDark.current
    val context = LocalContext.current
    val lotus = remember(context) {
        runCatching {
            BitmapFactory.decodeResource(context.resources, com.grapsee.gsai.R.drawable.gs_home_lotus_new)
                .asImageBitmap()
        }.getOrNull()
    }
    return drawBehind {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.verticalGradient(
                if (dark) {
                    // TWO stops, not three. A middle stop that does not lie on
                    // the line between its neighbours makes the gradient
                    // change slope at exactly 50% height — on screen that is a
                    // visible horizontal crease across the whole page, and it
                    // was sitting in the middle of every conversation.
                    listOf(
                        Color(0xFF171210),
                        Color(0xFF1B1412)
                    )
                } else {
                    listOf(
                        Color(0xFFFFFCF7),
                        Color(0xFFFDF1E9)
                    )
                }
            )
        )
        // Broad daylight from the top-left, as in the sheet's pearl field.
        drawRect(
            Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (dark) 0.05f else 0.42f),
                    Color.Transparent
                ),
                center = Offset(w * 0.12f, h * 0.02f),
                radius = w * 0.9f
            )
        )
        fun bubble(cx: Float, cy: Float, radius: Float, alpha: Float) {
            // Defined pearl: clear body, a warm/cool refraction band, a crisp
            // white rim and a specular crescent. The previous wash was so soft
            // it read as a flat pink field.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = alpha * 0.16f),
                        Color(0xFFFFD6C2).copy(alpha = alpha * 0.34f),
                        Color(0xFFE6DAFF).copy(alpha = alpha * 0.24f),
                        Color(0xFFC8E8FF).copy(alpha = alpha * 0.12f),
                        Color.Transparent
                    ),
                    center = Offset(cx - radius * 0.12f, cy - radius * 0.10f),
                    radius = radius * 0.95f
                ),
                radius = radius,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = Color.White.copy(alpha = alpha * 0.09f),
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = 7.dp.toPx())
            )
            drawCircle(
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = (alpha * 0.90f).coerceAtMost(0.90f)),
                        Color(0xFFFFC8D5).copy(alpha = alpha * 0.36f),
                        Color(0xFFC4DEFF).copy(alpha = alpha * 0.24f)
                    )
                ),
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = 1.2.dp.toPx())
            )
            drawCircle(
                color = Color.White.copy(alpha = alpha * 0.22f),
                radius = radius * 0.88f,
                center = Offset(cx + radius * 0.04f, cy + radius * 0.02f),
                style = Stroke(width = 0.8.dp.toPx())
            )
            // Specular crescent and its hot spot.
            drawArc(
                color = Color.White.copy(alpha = (alpha * 0.92f).coerceAtMost(0.92f)),
                startAngle = 205f,
                sweepAngle = 92f,
                useCenter = false,
                topLeft = Offset(cx - radius * 0.78f, cy - radius * 0.82f),
                size = Size(radius * 1.32f, radius * 1.32f),
                style = Stroke(width = radius * 0.06f, cap = StrokeCap.Round)
            )
            drawCircle(
                color = Color.White.copy(alpha = (alpha * 0.88f).coerceAtMost(0.88f)),
                radius = radius * 0.055f,
                center = Offset(cx - radius * 0.48f, cy - radius * 0.52f)
            )
        }
        bubble(w * 0.93f, h * 0.09f, w * 0.46f, if (dark) 0.26f else 0.95f)
        bubble(w * -0.03f, h * 0.33f, w * 0.40f, if (dark) 0.22f else 0.82f)
        bubble(w * 0.98f, h * 0.80f, w * 0.31f, if (dark) 0.18f else 0.72f)
        bubble(w * 0.78f, h * 0.22f, w * 0.10f, if (dark) 0.14f else 0.55f)
        lotus?.let { image ->
            if (image.width > 0 && image.height > 0) {
                val targetW = w * 1.18f
                val targetH = targetW * (image.height.toFloat() / image.width.toFloat())
                val left = w * 0.16f - targetW / 2f
                val top = h * 0.90f - targetH / 2f
                // The asset carries its own wash: alpha 0 at its top edge,
                // already 134/255 sixty pixels down. Scaled to this width that
                // ramp is a visible horizontal band across the page — the one
                // hard edge left on the chat surface, and the reason the
                // backdrop looked like two rectangles stacked.
                //
                // So the image is composited into its own layer and faded in
                // with a DstIn mask: the flower still sits low and warm, but
                // it arrives out of the ground instead of starting at a line.
                drawContext.canvas.saveLayer(
                    Rect(left, top, left + targetW, top + targetH),
                    Paint()
                )
                drawImage(
                    image = image,
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize(targetW.roundToInt(), targetH.roundToInt()),
                    alpha = if (dark) 0.16f else 0.72f,
                    filterQuality = FilterQuality.Medium
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.30f to Color.Black,
                        1f to Color.Black
                    ),
                    topLeft = Offset(left, top),
                    size = Size(targetW, targetH),
                    blendMode = BlendMode.DstIn
                )
                drawContext.canvas.restore()
            }
        }
    }
}
