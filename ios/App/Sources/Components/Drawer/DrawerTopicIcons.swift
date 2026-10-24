import Foundation
import SwiftUI

/// Which icon a conversation wears, decided by its headline.
///
/// Mirrors Android's TopicIcons.kt exactly — same topics, same order, same icon
/// names, same keywords — and `scripts/check_icon_catalogue.py` fails on any
/// difference. The keyword lists are English plus Spanish/French/German/Portuguese
/// and Russian/Chinese/Japanese/Arabic/Hindi terms, because a headline is written
/// in whatever language its author thinks in.
///
/// `\b` is an ASCII idea and does not exist in Chinese, Japanese or Thai, so a CJK
/// keyword is matched by containment while an ASCII one is matched as a whole word
/// ("plan" must not match "planet").
enum DrawerTopic: String, CaseIterable {
    case science, code, data, money, travel, food, health, learning, work, ideas, design, writing, plan, music, sport, home, shopping, weather, security, legal, media, chat

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

    /// Whole words for ASCII, containment for everything else — decided per keyword.
    var keywords: [String] {
        switch self {
        case .science:
            return [                    "quantum", "physics", "chemistry", "biology", "molecule",
                    "experiment", "research", "gravity", "relativity", "neuron",
                    "protein", "量子", "物理", "физика", "física",
                    "فيزياء", "भौतिकी"]
        case .code:
            return [                    "code", "bug", "error", "compile", "refactor",
                    "regex", "api", "kotlin", "swift", "python",
                    "javascript", "typescript", "function", "stack trace", "コード",
                    "код", "código", "برمجة", "कोड"]
        case .data:
            return [                    "chart", "graph", "data", "dashboard", "metric",
                    "analytics", "csv", "spreadsheet", "statistics", "database",
                    "sql", "query", "データ", "данные", "datos",
                    "بيانات", "डेटा"]
        case .money:
            return [                    "budget", "money", "cost", "price", "invoice",
                    "salary", "tax", "finance", "revenue", "invest",
                    "loan", "refund", "予算", "бюджет", "presupuesto",
                    "ميزانية", "बजट"]
        case .travel:
            return [                    "travel", "trip", "flight", "hotel", "itinerary",
                    "kyoto", "paris", "tokyo", "london", "visa",
                    "vacation", "holiday", "weekend in", "旅行", "путешествие",
                    "viaje", "voyage", "reise", "सफ़र", "سفر"]
        case .food:
            return [                    "recipe", "cook", "dinner", "lunch", "breakfast",
                    "food", "restaurant", "bake", "coffee", "menu",
                    "料理", "рецепт", "comida", "recette", "طعام",
                    "खाना"]
        case .health:
            return [                    "workout", "gym", "sleep", "diet", "calorie",
                    "doctor", "symptom", "health", "medical", "therapy",
                    "injury", "健康", "здоровье", "salud", "صحة",
                    "स्वास्थ्य"]
        case .learning:
            return [                    "learn", "study", "exam", "course", "explain",
                    "teach", "revision", "homework", "lesson", "tutorial",
                    "勉強", "学习", "учёба", "estudiar", "دراسة",
                    "पढ़ाई"]
        case .work:
            return [                    "meeting", "deadline", "project", "sprint", "roadmap",
                    "standup", "report", "interview", "hire", "onboarding",
                    "performance review", "会議", "работа", "trabajo", "travail",
                    "عمل", "काम"]
        case .ideas:
            return [                    "idea", "ideas", "brainstorm", "brand", "launch",
                    "naming", "concept", "creative", "pitch", "tagline",
                    "アイデア", "идея", "lluvia de ideas", "فكرة", "विचार"]
        case .design:
            return [                    "design", "ui", "ux", "figma", "layout",
                    "typography", "logo", "colour", "color", "font",
                    "wireframe", "デザイン", "дизайн", "diseño", "تصميم",
                    "डिज़ाइन"]
        case .writing:
            return [                    "write", "writing", "essay", "draft", "blog",
                    "article", "copy", "story", "poem", "email",
                    "letter", "文章", "текст", "escribir", "كتابة",
                    "लेखन"]
        case .plan:
            return [                    "plan", "schedule", "tomorrow", "calendar", "agenda",
                    "todo", "reminder", "this week", "next week", "予定",
                    "计划", "план", "planifier", "خطة", "योजना"]
        case .music:
            return [                    "song", "music", "playlist", "album", "guitar",
                    "chord", "spotify", "melody", "音楽", "музыка",
                    "música", "موسيقى", "संगीत"]
        case .sport:
            return [                    "match", "game", "score", "football", "cricket",
                    "tennis", "race", "league", "tournament", "training",
                    "試合", "матч", "partido", "مباراة", "मैच"]
        case .home:
            return [                    "home", "kitchen", "furniture", "rent", "apartment",
                    "decor", "room", "garden", "部屋", "квартира",
                    "casa", "منزل", "घर"]
        case .shopping:
            return [                    "buy", "shopping", "order", "cart", "store",
                    "purchase", "gift", "delivery", "買い物", "покупка",
                    "compras", "تسوق", "खरीदारी"]
        case .weather:
            return [                    "weather", "rain", "forecast", "temperature", "climate",
                    "storm", "snow", "天気", "погода", "clima",
                    "طقس", "मौसम"]
        case .security:
            return [                    "password", "security", "privacy", "breach", "encrypt",
                    "2fa", "phishing", "vpn", "パスワード", "пароль",
                    "contraseña", "كلمة المرور", "पासवर्ड"]
        case .legal:
            return [                    "contract", "legal", "law", "lawyer", "terms",
                    "gdpr", "copyright", "compliance", "契約", "договор",
                    "contrato", "عقد", "अनुबंध"]
        case .media:
            return [                    "image", "photo", "picture", "render", "illustration",
                    "video", "camera", "screenshot", "写真", "фото",
                    "foto", "صورة", "तस्वीर"]
        case .chat:
            return []
        }
    }

    private var matcher: NSRegularExpression? { DrawerTopic.cache[self] }

    private static let cache: [DrawerTopic: NSRegularExpression] = {
        var built: [DrawerTopic: NSRegularExpression] = [:]
        for topic in DrawerTopic.allCases {
            let words = topic.keywords.filter { $0.unicodeScalars.allSatisfy { $0.isASCII } }
            guard !words.isEmpty else { continue }
            let alternatives = words.map { NSRegularExpression.escapedPattern(for: $0) }
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
        let folded = title.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: nil)
        for topic in DrawerTopic.allCases {
            if let matcher = topic.matcher,
               matcher.firstMatch(in: title, options: [], range: full) != nil {
                return topic
            }
            let loose = topic.keywords.filter { !$0.unicodeScalars.allSatisfy { $0.isASCII } }
            if loose.contains(where: { keyword in
                let needle = keyword.folding(options: [.caseInsensitive, .diacriticInsensitive],
                                             locale: nil)
                return folded.contains(needle)
            }) {
                return topic
            }
        }
        return .chat
    }

    /// The icon name for a row — pure, so `DrawerLogicTests` can assert it.
    static func iconName(title: String, pinned: Bool) -> String {
        pinned ? "Pin" : topic(for: title).icon
    }

    static func paths(title: String, pinned: Bool) -> GsIconPaths {
        paths(named: iconName(title: title, pinned: pinned))
    }

    /// A catalogue name resolved to geometry — used by the topic rows and by the
    /// destination rows, which name their icons directly.
    static func paths(named name: String) -> GsIconPaths {
        switch name {
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
        case "Compass": return GsIcons.compass
        case "Activity": return GsIcons.activity
        case "Bookmark": return GsIcons.bookmark
        default: return GsIcons.messageCircle
        }
    }
}
