import Foundation
import SwiftUI

/// Which icon a conversation wears, decided by its headline.
///
/// The headline is the subject the user typed ("Weekend in Kyoto", "Explain quantum
/// physics"), so a keyword in it is evidence about the chat in a way that a hash of
/// the same string never was. Every icon below is a member of the 5,466-name family
/// this target already ships — and until now, never drew: `GsIconView` existed and
/// had no caller, so the drawer's rows were SF Symbols while Android drew Lucide.
///
/// Mirrored exactly in Android's TopicIcons.kt. `scripts/check_icon_catalogue.py`
/// fails if the two disagree on a topic, an icon, a keyword, or their order.
enum DrawerTopic: String, CaseIterable {
    case science, code, data, money, travel, food, health, learning, work, ideas
    case design, writing, plan, music, sport, home, shopping, weather, security
    case legal, media, chat

    /// The Lucide name, spelled as the generated catalogue spells it.
    var icon: String {
        switch self {
        case .science: return "Atom"
        case .code: return "Code"
        case .data: return "ChartLine"
        case .money: return "CircleDollarSign"
        case .travel: return "MapPin"
        case .food: return "Utensils"
        case .health: return "Heart"
        case .learning: return "GraduationCap"
        case .work: return "Briefcase"
        case .ideas: return "Lightbulb"
        case .design: return "Palette"
        case .writing: return "PenLine"
        case .plan: return "CalendarCheck"
        case .music: return "Music"
        case .sport: return "Trophy"
        case .home: return "House"
        case .shopping: return "ShoppingBag"
        case .weather: return "CloudSun"
        case .security: return "Shield"
        case .legal: return "Scale"
        case .media: return "Image"
        case .chat: return "MessageCircle"
        }
    }

    /// Whole words only: "planet" is not a plan and "cartoon" is not a cart.
    var keywords: [String] {
        switch self {
        case .science:
            return ["quantum", "physics", "chemistry", "biology", "molecule", "experiment",
                    "research", "gravity", "relativity", "neuron", "protein"]
        case .code:
            return ["code", "bug", "error", "compile", "refactor", "regex", "api", "kotlin",
                    "swift", "python", "javascript", "typescript", "function", "stack trace"]
        case .data:
            return ["chart", "graph", "data", "dashboard", "metric", "analytics", "csv",
                    "spreadsheet", "statistics", "database", "sql", "query"]
        case .money:
            return ["budget", "money", "cost", "price", "invoice", "salary", "tax", "finance",
                    "revenue", "invest", "loan", "refund"]
        case .travel:
            return ["travel", "trip", "flight", "hotel", "itinerary", "kyoto", "paris",
                    "tokyo", "london", "visa", "vacation", "holiday", "weekend in"]
        case .food:
            return ["recipe", "cook", "dinner", "lunch", "breakfast", "food", "restaurant",
                    "bake", "coffee", "menu"]
        case .health:
            return ["workout", "gym", "sleep", "diet", "calorie", "doctor", "symptom",
                    "health", "medical", "therapy", "injury"]
        case .learning:
            return ["learn", "study", "exam", "course", "explain", "teach", "revision",
                    "homework", "lesson", "tutorial"]
        case .work:
            return ["meeting", "deadline", "project", "sprint", "roadmap", "standup",
                    "report", "interview", "hire", "onboarding", "performance review"]
        case .ideas:
            return ["idea", "ideas", "brainstorm", "brand", "launch", "naming", "concept",
                    "creative", "pitch", "tagline"]
        case .design:
            return ["design", "ui", "ux", "figma", "layout", "typography", "logo", "colour",
                    "color", "font", "wireframe"]
        case .writing:
            return ["write", "writing", "essay", "draft", "blog", "article", "copy", "story",
                    "poem", "email", "letter"]
        case .plan:
            return ["plan", "schedule", "tomorrow", "calendar", "agenda", "todo", "reminder",
                    "this week", "next week"]
        case .music:
            return ["song", "music", "playlist", "album", "guitar", "chord", "spotify",
                    "melody"]
        case .sport:
            return ["match", "game", "score", "football", "cricket", "tennis", "race",
                    "league", "tournament", "training"]
        case .home:
            return ["home", "kitchen", "furniture", "rent", "apartment", "decor", "room",
                    "garden"]
        case .shopping:
            return ["buy", "shopping", "order", "cart", "store", "purchase", "gift",
                    "delivery"]
        case .weather:
            return ["weather", "rain", "forecast", "temperature", "climate", "storm", "snow"]
        case .security:
            return ["password", "security", "privacy", "breach", "encrypt", "2fa", "phishing",
                    "vpn"]
        case .legal:
            return ["contract", "legal", "law", "lawyer", "terms", "gdpr", "copyright",
                    "compliance"]
        case .media:
            return ["image", "photo", "picture", "render", "illustration", "video", "camera",
                    "screenshot"]
        case .chat:
            return []
        }
    }

    /// Compiled once per topic. `NSRegularExpression` rather than Swift's `Regex`
    /// so the minimum deployment target does not have to move for a row icon.
    private var matcher: NSRegularExpression? {
        DrawerTopic.cache[self]
    }

    private static let cache: [DrawerTopic: NSRegularExpression] = {
        var built: [DrawerTopic: NSRegularExpression] = [:]
        for topic in DrawerTopic.allCases where !topic.keywords.isEmpty {
            let alternatives = topic.keywords.map { NSRegularExpression.escapedPattern(for: $0) }
            let source = "\\b(" + alternatives.joined(separator: "|") + ")\\b"
            if let regex = try? NSRegularExpression(pattern: source, options: [.caseInsensitive]) {
                built[topic] = regex
            }
        }
        return built
    }()

    /// The topic a headline is about; `.chat` when nothing matches.
    static func topic(for title: String) -> DrawerTopic {
        let full = NSRange(title.startIndex..<title.endIndex, in: title)
        for topic in DrawerTopic.allCases {
            guard let matcher = topic.matcher else { continue }
            if matcher.firstMatch(in: title, options: [], range: full) != nil { return topic }
        }
        return .chat
    }

    /// The icon name for a row — pure, so `DrawerLogicTests` can assert it.
    static func iconName(title: String, pinned: Bool) -> String {
        pinned ? "Pin" : topic(for: title).icon
    }

    /// The drawing. Every name the table can produce has a branch; the catalogue
    /// check fails if the table gains one this misses.
    static func paths(title: String, pinned: Bool) -> GsIconPaths {
        switch iconName(title: title, pinned: pinned) {
        case "Pin": return GsIcons.pin
        case "Atom": return GsIcons.atom
        case "Code": return GsIcons.code
        case "ChartLine": return GsIcons.chartLine
        case "CircleDollarSign": return GsIcons.circleDollarSign
        case "MapPin": return GsIcons.mapPin
        case "Utensils": return GsIcons.utensils
        case "Heart": return GsIcons.heart
        case "GraduationCap": return GsIcons.graduationCap
        case "Briefcase": return GsIcons.briefcase
        case "Lightbulb": return GsIcons.lightbulb
        case "Palette": return GsIcons.palette
        case "PenLine": return GsIcons.penLine
        case "CalendarCheck": return GsIcons.calendarCheck
        case "Music": return GsIcons.music
        case "Trophy": return GsIcons.trophy
        case "House": return GsIcons.house
        case "ShoppingBag": return GsIcons.shoppingBag
        case "CloudSun": return GsIcons.cloudSun
        case "Shield": return GsIcons.shield
        case "Scale": return GsIcons.scale
        case "Image": return GsIcons.image
        case "MessageCircle": return GsIcons.messageCircle
        default: return GsIcons.messageCircle
        }
    }
}
