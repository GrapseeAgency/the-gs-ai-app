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
 * Two rules shaped the order:
 *
 *   1. SPECIFIC BEFORE GENERAL. "Explain quantum physics" contains "explain", which
 *      is a learning word, but the subject is physics — so Science is matched before
 *      Learning. The order of the enum IS the precedence, and the parity check
 *      compares that order across platforms.
 *   2. WORD BOUNDARIES, NOT SUBSTRINGS. "planet" must not match "plan" and
 *      "cartoon" must not match "cart". Keywords are matched as whole words.
 *
 * This table is mirrored exactly in DrawerTopicIcons.swift; scripts/check_icon_
 * catalogue.py fails if the two disagree on a single topic, icon or keyword.
 */
enum class DrawerTopic(val icon: String, vararg val keywords: String) {
    Science(
        "Atom",
        "quantum", "physics", "chemistry", "biology", "molecule", "experiment",
        "research", "gravity", "relativity", "neuron", "protein",
    ),
    Code(
        "Code",
        "code", "bug", "error", "compile", "refactor", "regex", "api", "kotlin",
        "swift", "python", "javascript", "typescript", "function", "stack trace",
    ),
    Data(
        "ChartLine",
        "chart", "graph", "data", "dashboard", "metric", "analytics", "csv",
        "spreadsheet", "statistics", "database", "sql", "query",
    ),
    Money(
        "CircleDollarSign",
        "budget", "money", "cost", "price", "invoice", "salary", "tax", "finance",
        "revenue", "invest", "loan", "refund",
    ),
    Travel(
        "MapPin",
        "travel", "trip", "flight", "hotel", "itinerary", "kyoto", "paris",
        "tokyo", "london", "visa", "vacation", "holiday", "weekend in",
    ),
    Food(
        "Utensils",
        "recipe", "cook", "dinner", "lunch", "breakfast", "food", "restaurant",
        "bake", "coffee", "menu",
    ),
    Health(
        "Heart",
        "workout", "gym", "sleep", "diet", "calorie", "doctor", "symptom",
        "health", "medical", "therapy", "injury",
    ),
    Learning(
        "GraduationCap",
        "learn", "study", "exam", "course", "explain", "teach", "revision",
        "homework", "lesson", "tutorial",
    ),
    Work(
        "Briefcase",
        "meeting", "deadline", "project", "sprint", "roadmap", "standup",
        "report", "interview", "hire", "onboarding", "performance review",
    ),
    Ideas(
        "Lightbulb",
        "idea", "ideas", "brainstorm", "brand", "launch", "naming", "concept",
        "creative", "pitch", "tagline",
    ),
    Design(
        "Palette",
        "design", "ui", "ux", "figma", "layout", "typography", "logo", "colour",
        "color", "font", "wireframe",
    ),
    Writing(
        "PenLine",
        "write", "writing", "essay", "draft", "blog", "article", "copy", "story",
        "poem", "email", "letter",
    ),
    Plan(
        "CalendarCheck",
        "plan", "schedule", "tomorrow", "calendar", "agenda", "todo", "reminder",
        "this week", "next week",
    ),
    Music(
        "Music",
        "song", "music", "playlist", "album", "guitar", "chord", "spotify",
        "melody",
    ),
    Sport(
        "Trophy",
        "match", "game", "score", "football", "cricket", "tennis", "race",
        "league", "tournament", "training",
    ),
    Home(
        "House",
        "home", "kitchen", "furniture", "rent", "apartment", "decor", "room",
        "garden",
    ),
    Shopping(
        "ShoppingBag",
        "buy", "shopping", "order", "cart", "store", "purchase", "gift",
        "delivery",
    ),
    Weather(
        "CloudSun",
        "weather", "rain", "forecast", "temperature", "climate", "storm", "snow",
    ),
    Security(
        "Shield",
        "password", "security", "privacy", "breach", "encrypt", "2fa", "phishing",
        "vpn",
    ),
    Legal(
        "Scale",
        "contract", "legal", "law", "lawyer", "terms", "gdpr", "copyright",
        "compliance",
    ),
    Media(
        "Image",
        "image", "photo", "picture", "render", "illustration", "video", "camera",
        "screenshot",
    ),
    Chat("MessageCircle");

    /** Whole-word alternation, compiled once per topic rather than per row. */
    val pattern: Regex by lazy {
        if (keywords.isEmpty()) Regex("$^")
        else Regex("\\b(" + keywords.joinToString("|") { Regex.escape(it) } + ")\\b",
            RegexOption.IGNORE_CASE)
    }
}

/** The topic a headline is about; `Chat` when nothing matches. */
fun drawerTopic(title: String): DrawerTopic =
    DrawerTopic.entries.firstOrNull { it.pattern.containsMatchIn(title) } ?: DrawerTopic.Chat

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
