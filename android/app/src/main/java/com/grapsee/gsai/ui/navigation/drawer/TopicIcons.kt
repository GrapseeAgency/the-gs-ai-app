package com.grapsee.gsai.ui.navigation.drawer

import androidx.compose.ui.graphics.vector.ImageVector
import com.grapsee.gsai.ui.icons.GsIcons

/**
 * Which icon a conversation wears, decided by its headline.
 *
 * The headline is the subject the user typed ("Weekend in Kyoto", "Explain quantum
 * physics"), so a keyword in it is evidence about the chat in a way that a hash of
 * the same string never was. Every icon below is a member of the 5,466-name family
 * the app already ships; nothing here invents artwork.
 *
 * Three rules shaped this table:
 *
 *   1. SPECIFIC BEFORE GENERAL. "Explain quantum physics" contains "explain", which
 *      is a learning word, but the subject is physics — so Science is matched before
 *      Learning. The order of the enum IS the precedence.
 *   2. WORD BOUNDARIES FOR ASCII, CONTAINMENT FOR EVERYTHING ELSE. `\b` does not
 *      exist in Chinese, Japanese or Thai, so a CJK keyword matched with a word
 *      boundary would never fire; "plan" would still wrongly match "planet" if it
 *      were treated the same way. Each keyword's own script decides.
 *   3. ONE TABLE, TWO LANGUAGES. This file and DrawerTopicIcons.swift carry the
 *      same topics, icons and keywords in the same order, and
 *      scripts/check_icon_catalogue.py fails on a single difference. Keeping the
 *      keyword lists in step by hand is how they drifted the first time.
 */
private val FOLD_MARKS = Regex("\\p{M}+")

enum class DrawerTopic(val icon: String, vararg val keywords: String) {
    Science(
        "Atom",
        "quantum", "physics", "chemistry", "biology", "molecule",
        "experiment", "research", "gravity", "relativity", "neuron",
        "protein", "量子", "物理", "физика", "física",
        "فيزياء", "भौतिकी",
    ),
    Code(
        "Code",
        "code", "bug", "error", "compile", "refactor",
        "regex", "api", "kotlin", "swift", "python",
        "javascript", "typescript", "function", "stack trace", "コード",
        "код", "código", "برمجة", "कोड",
    ),
    Data(
        "ChartLine",
        "chart", "graph", "data", "dashboard", "metric",
        "analytics", "csv", "spreadsheet", "statistics", "database",
        "sql", "query", "データ", "данные", "datos",
        "بيانات", "डेटा",
    ),
    Money(
        "CircleDollarSign",
        "budget", "money", "cost", "price", "invoice",
        "salary", "tax", "finance", "revenue", "invest",
        "loan", "refund", "予算", "бюджет", "presupuesto",
        "ميزانية", "बजट",
    ),
    Travel(
        "Sprout",
        "travel", "trip", "flight", "hotel", "itinerary",
        "kyoto", "paris", "tokyo", "london", "visa",
        "vacation", "holiday", "weekend in", "京都", "東京", "东京", "旅行", "путешествие",
        "viaje", "voyage", "reise", "सफ़र", "سفر",
    ),
    Food(
        "Utensils",
        "recipe", "cook", "dinner", "lunch", "breakfast",
        "food", "restaurant", "bake", "coffee", "menu",
        "料理", "рецепт", "comida", "recette", "طعام",
        "खाना",
    ),
    Health(
        "Heart",
        "workout", "gym", "sleep", "diet", "calorie",
        "doctor", "symptom", "health", "medical", "therapy",
        "injury", "健康", "здоровье", "salud", "صحة",
        "स्वास्थ्य",
    ),
    Learning(
        "GraduationCap",
        "learn", "study", "exam", "course", "explain",
        "teach", "revision", "homework", "lesson", "tutorial",
        "勉強", "学习", "учёба", "estudiar", "دراسة",
        "पढ़ाई",
    ),
    Work(
        "Briefcase",
        "meeting", "deadline", "project", "sprint", "roadmap",
        "standup", "report", "interview", "hire", "onboarding",
        "performance review", "会議", "работа", "trabajo", "travail",
        "عمل", "काम",
    ),
    Ideas(
        "Lightbulb",
        "idea", "ideas", "brainstorm", "brand", "launch",
        "naming", "concept", "creative", "pitch", "tagline",
        "アイデア", "идея", "lluvia de ideas", "فكرة", "विचार",
    ),
    Design(
        "Palette",
        "design", "ui", "ux", "figma", "layout",
        "typography", "logo", "colour", "color", "font",
        "wireframe", "デザイン", "дизайн", "diseño", "تصميم",
        "डिज़ाइन",
    ),
    Writing(
        "PenLine",
        "write", "writing", "essay", "draft", "blog",
        "article", "copy", "story", "poem", "email",
        "letter", "文章", "текст", "escribir", "كتابة",
        "लेखन",
    ),
    Plan(
        "Sparkles",
        "plan", "schedule", "tomorrow", "calendar", "agenda",
        "todo", "reminder", "this week", "next week", "予定",
        "计划", "план", "planifier", "خطة", "योजना",
    ),
    Music(
        "Music",
        "song", "music", "playlist", "album", "guitar",
        "chord", "spotify", "melody", "音楽", "музыка",
        "música", "موسيقى", "संगीत",
    ),
    Sport(
        "Trophy",
        "match", "game", "score", "football", "cricket",
        "tennis", "race", "league", "tournament", "training",
        "試合", "матч", "partido", "مباراة", "मैच",
    ),
    Home(
        "House",
        "home", "kitchen", "furniture", "rent", "apartment",
        "decor", "room", "garden", "部屋", "квартира",
        "casa", "منزل", "घर",
    ),
    Shopping(
        "ShoppingBag",
        "buy", "shopping", "order", "cart", "store",
        "purchase", "gift", "delivery", "買い物", "покупка",
        "compras", "تسوق", "खरीदारी",
    ),
    Weather(
        "CloudSun",
        "weather", "rain", "forecast", "temperature", "climate",
        "storm", "snow", "天気", "погода", "clima",
        "طقس", "मौसम",
    ),
    Security(
        "Shield",
        "password", "security", "privacy", "breach", "encrypt",
        "2fa", "phishing", "vpn", "パスワード", "пароль",
        "contraseña", "كلمة المرور", "पासवर्ड",
    ),
    Legal(
        "Scale",
        "contract", "legal", "law", "lawyer", "terms",
        "gdpr", "copyright", "compliance", "契約", "договор",
        "contrato", "عقد", "अनुबंध",
    ),
    Media(
        "Image",
        "image", "photo", "picture", "render", "illustration",
        "video", "camera", "screenshot", "写真", "фото",
        "foto", "صورة", "तस्वीर",
    ),
    Chat("MessageCircle");

    private val asciiKeywords: List<String>
        get() = keywords.filter { word -> word.all { it.code < 128 } }

    private val looseKeywords: List<String>
        get() = keywords.filter { word -> word.any { it.code > 127 } }

    /** Whole-word alternation over the ASCII keywords, compiled once per topic. */
    val pattern: Regex by lazy {
        val words = asciiKeywords
        if (words.isEmpty()) Regex("$^")
        else Regex("\\b(" + words.joinToString("|") { Regex.escape(it) } + ")\\b",
            RegexOption.IGNORE_CASE)
    }

    /** Diacritics folded, lowercased: "FISICA" must match "física". */
    private fun fold(text: String): String =
        java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
            .replace(FOLD_MARKS, "")
            .lowercase()

    /** True when the headline is about this topic, in any script. */
    fun matches(title: String): Boolean {
        if (pattern.containsMatchIn(title)) return true
        if (looseKeywords.isEmpty()) return false
        val foldedTitle = fold(title)
        return looseKeywords.any { foldedTitle.contains(fold(it)) }
    }
}

/** The topic a headline is about; `Chat` when nothing matches. */
fun drawerTopic(title: String): DrawerTopic =
    DrawerTopic.entries.firstOrNull { it.matches(title) } ?: DrawerTopic.Chat

/**
 * The icon NAME for a row. A name rather than a vector, so the decision is pure
 * and testable on the JVM without initialising Compose's vector machinery.
 */
fun drawerIconName(title: String, pinned: Boolean): String =
    if (pinned) "Pin" else drawerTopic(title).icon

/** The drawing. Every name in the table has a branch here; the catalogue check
 *  fails if the table gains a name this `when` does not cover. */
fun drawerIcon(title: String, pinned: Boolean): ImageVector =
    when (drawerIconName(title, pinned)) {
        "Pin" -> GsIcons.Pin
        "Atom" -> GsIcons.Atom
        "Code" -> GsIcons.Code
        "ChartLine" -> GsIcons.ChartLine
        "CircleDollarSign" -> GsIcons.CircleDollarSign
        "MapPin" -> GsIcons.MapPin
        "Utensils" -> GsIcons.Utensils
        "Heart" -> GsIcons.Heart
        "GraduationCap" -> GsIcons.GraduationCap
        "Briefcase" -> GsIcons.Briefcase
        "Lightbulb" -> GsIcons.Lightbulb
        "Palette" -> GsIcons.Palette
        "PenLine" -> GsIcons.PenLine
        "CalendarCheck" -> GsIcons.CalendarCheck
        "Sparkles" -> GsIcons.Sparkles
        "Sprout" -> GsIcons.Sprout
        "Music" -> GsIcons.Music
        "Trophy" -> GsIcons.Trophy
        "House" -> GsIcons.House
        "ShoppingBag" -> GsIcons.ShoppingBag
        "CloudSun" -> GsIcons.CloudSun
        "Shield" -> GsIcons.Shield
        "Scale" -> GsIcons.Scale
        "Image" -> GsIcons.Image
        "MessageCircle" -> GsIcons.MessageCircle
        else -> GsIcons.MessageCircle
    }
