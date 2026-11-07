import Foundation
import SwiftUI
import WebKit

/// iOS twin of `android/.../WidgetBlocks.kt` + `WidgetsV2.kt` + `ContentBlocks.kt`
/// widget catalogue. Accepts all 36 `WIDGET_KINDS` and every sub-type alias
/// declared in `src/lib/widgets/catalog.ts`.

// MARK: - Catalogue constants (36 top-level kinds + sub-types + synonyms)

let WIDGET_LANGUAGES: Set<String> = [
    "chart", "stat", "table", "compare", "progress", "timeline", "gauge",
    "quiz", "form", "carousel", "cards", "followups", "media", "actions",
    "panel", "voice", "calc", "app", "plan",
    "tabs", "accordion", "steps", "menu", "chips", "rating",
    "poll", "diff", "tree", "flashcards", "checklist",
    "sim", "stage3d", "canvas", "editor", "terminal", "collab"
]

let CHART_TYPES: Set<String> = [
    "bar", "hbar", "grouped", "stacked", "line", "area", "stackedArea",
    "pie", "donut", "scatter", "bubble", "radar", "funnel", "waterfall",
    "heatmap", "treemap", "boxplot", "candlestick", "gantt", "histogram",
    "correlation", "chord", "sankey", "network", "wordcloud", "sunburst",
    "bullet", "quadrant", "violin", "contour", "parallel", "dendrogram",
    "force", "depth"
]

let MEDIA_TYPES: Set<String> = [
    "image", "gallery", "video", "audio", "pdf", "file", "embed", "code",
    "citation", "search", "link", "weather", "stock", "flight", "product", "recipe",
    "entity", "listing", "doc", "document"
]

let CALC_TYPES: Set<String> = [
    "loan", "mortgage", "roi", "compound", "tip", "split", "unit", "currency",
    "bmi", "calorie", "pace", "percentage", "tax", "custom",
    "finance", "pricing", "budget", "investment", "probability", "statistics",
    "scientific", "datetime", "health"
]

let APP_TYPES: Set<String> = [
    "accordion", "tabs", "stepper", "kanban", "filter", "decision", "poll",
    "checklist", "flashcard", "flashcards", "timer", "schedule", "calendar",
    "booking", "seats", "cart", "pricing", "matrix", "proscons", "swot",
    "wizard", "onboarding", "sheet", "tasks", "dashboard", "config",
    "configurator", "search", "analytics", "report", "files", "connected", "scenario"
]

let SIM_TYPES: Set<String> = [
    "particles", "gravity", "pendulum", "springs", "wave", "fluid", "boids", "life",
    "cloth", "softbody", "smoke", "rigid", "terrain", "game"
]

let STAGE3D_TYPES: Set<String> = [
    "scene", "globe", "molecule", "surface", "surface3d", "scatter3d", "orbit", "cad", "anatomy", "vr", "ar"
]

let CANVAS_TYPES: Set<String> = [
    "whiteboard", "nodes", "mindmap", "erd", "diagram", "polygons"
]

let EDITOR_TYPES: Set<String> = [
    "sheet", "code", "richtext", "sql", "regex", "api", "timeline",
    "nodes", "pivot", "site", "css", "video", "audio"
]

let COLLAB_TYPES: Set<String> = [
    "counter", "vote", "board", "presence", "todo", "pointer"
]

let HTML_LANGUAGES: Set<String> = [
    "html", "htm", "web", "webapp", "app", "ui", "page", "site",
    "interactive", "miniapp", "svg"
]

let CHART_SYNONYMS: [String: String] = [
    "barchart": "bar", "bar-chart": "bar", "bar_chart": "bar", "column": "bar", "columns": "bar",
    "horizontal-bar": "hbar", "horizontal_bar": "hbar", "hbar-chart": "hbar",
    "grouped-bar": "grouped", "grouped_bar": "grouped", "multi-bar": "grouped",
    "stacked-bar": "stacked", "stacked_bar": "stacked",
    "linechart": "line", "line-chart": "line", "line_chart": "line", "timeseries": "line", "sparkline": "line", "trend": "line",
    "areachart": "area", "area-chart": "area", "area_chart": "area",
    "stacked-area": "stackedArea", "stacked_area": "stackedArea", "stackedarea": "stackedArea",
    "piechart": "pie", "pie-chart": "pie", "pie_chart": "pie",
    "doughnut": "donut", "donut-chart": "donut", "donutchart": "donut",
    "scatterplot": "scatter", "scatter-plot": "scatter", "xy": "scatter",
    "bubblechart": "bubble", "bubble-chart": "bubble",
    "spider": "radar", "radarchart": "radar", "radar-chart": "radar", "star": "radar",
    "funnelchart": "funnel", "funnel-chart": "funnel", "pipeline": "funnel",
    "bridge": "waterfall", "cascade": "waterfall", "waterfall-chart": "waterfall",
    "matrix": "heatmap", "heat-map": "heatmap", "heat_map": "heatmap",
    "corr": "correlation", "correlation-matrix": "correlation",
    "tree-map": "treemap", "tree_map": "treemap", "hierarchy-map": "treemap",
    "box": "boxplot", "box-plot": "boxplot", "box_plot": "boxplot", "whisker": "boxplot",
    "candles": "candlestick", "ohlc": "candlestick", "kline": "candlestick",
    "dist": "histogram", "distribution": "histogram", "frequency": "histogram",
    "alluvial": "sankey", "flow": "sankey", "sankey-diagram": "sankey",
    "graph": "network", "nodes": "network", "force-graph": "force",
    "cloud": "wordcloud", "word-cloud": "wordcloud", "word_cloud": "wordcloud", "tags": "wordcloud",
    "partition": "sunburst", "radial": "sunburst",
    "target": "bullet", "kpi-bar": "bullet",
    "2x2": "quadrant", "four-box": "quadrant", "eisenhower": "quadrant",
    "kde": "violin", "density": "violin",
    "isolines": "contour", "topography": "contour",
    "parcoords": "parallel", "parallel-coordinates": "parallel",
    "phylo": "dendrogram", "cluster-tree": "dendrogram",
    "orderbook": "depth", "order-book": "depth", "bid-ask": "depth"
]

let KIND_SYNONYMS: [String: String] = [
    "kpi": "stat", "kpis": "stat", "metric": "stat", "metrics": "stat", "stats": "stat",
    "numbers": "stat", "scorecard": "stat", "summary": "stat",
    "versus": "compare", "vs": "compare", "comparison": "compare",
    "bars": "progress", "completion": "progress",
    "meter": "gauge", "dial": "gauge", "speedometer": "gauge",
    "trivia": "quiz", "mcq": "quiz", "test": "quiz",
    "survey": "form", "inputs": "form", "questionnaire": "form",
    "slides": "carousel", "slider": "carousel", "deck-slides": "carousel",
    "callout": "panel", "alert": "panel", "banner": "panel", "hero": "panel",
    "notice": "panel", "warning": "panel", "info": "panel", "tip": "panel", "note": "panel",
    "phrasebook": "voice", "pronounce": "voice", "tts": "voice", "speech": "voice",
    "calculator": "calc",
    "suggestions": "followups", "next": "followups", "prompts": "followups",
    "buttons": "actions", "links": "actions", "cta": "actions",
    "shell": "terminal", "bash": "terminal", "console": "terminal", "cli": "terminal",
    "3d": "stage3d", "webgl": "stage3d",
    "physics": "sim", "simulation": "sim",
    "tab": "tabs", "tabbed": "tabs",
    "faq": "accordion", "faqs": "accordion", "collapsible": "accordion", "disclosure": "accordion",
    "stepper": "steps", "walkthrough": "steps", "howto": "steps", "how-to": "steps",
    "guide": "steps", "process": "steps", "workflow": "steps",
    "todos": "checklist", "todo": "checklist", "todo-list": "checklist", "tasklist": "checklist", "tasks": "checklist",
    "flashcard": "flashcards", "study": "flashcards", "deck": "flashcards", "vocab": "flashcards",
    "ballot": "poll", "voting": "poll",
    "patch": "diff", "codediff": "diff", "code-diff": "diff",
    "hierarchy": "tree", "filetree": "tree", "file-tree": "tree", "orgchart": "tree", "org-chart": "tree", "taxonomy": "tree",
    "choices": "menu", "dropdown": "menu", "select": "menu", "picker": "menu",
    "nps": "rating", "stars": "rating", "review": "rating", "feedback": "rating",
    "roadmap": "timeline", "milestones": "timeline", "chronology": "timeline", "history": "timeline", "events": "timeline", "agenda": "timeline",
    "csv": "table", "tsv": "table", "grid": "table", "leaderboard": "table", "datatable": "table", "data_table": "table"
]

private let SUBTYPED_KINDS: Set<String> = [
    "chart", "media", "calc", "app", "sim", "stage3d", "canvas", "editor", "collab"
]

func canonicalChartType(_ raw: String) -> String {
    let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    if CHART_TYPES.contains(trimmed) { return trimmed }
    let lower = trimmed.lowercased()
    if let hit = CHART_TYPES.first(where: { $0.lowercased() == lower }) { return hit }
    if let syn = CHART_SYNONYMS[lower] { return syn }
    return "bar"
}

func widgetKindOf(_ language: String?) -> String? {
    guard let raw = language?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
          !raw.isEmpty else { return nil }
    let lang = raw.replacingOccurrences(of: "^(widget|genui|gsui|ui|chart|app|calc|media)[:/._-]+", with: "", options: .regularExpression)
    if WIDGET_LANGUAGES.contains(lang) { return lang }
    if CHART_TYPES.contains(where: { $0.lowercased() == lang }) || CHART_SYNONYMS[lang] != nil { return "chart" }
    if EDITOR_TYPES.contains(lang) || ["spreadsheet", "codeeditor", "richtext", "sqlplayground", "regex", "apitester"].contains(lang) { return "editor" }
    if SIM_TYPES.contains(lang) { return "sim" }
    if STAGE3D_TYPES.contains(lang) { return "stage3d" }
    if CANVAS_TYPES.contains(lang) { return "canvas" }
    if COLLAB_TYPES.contains(lang) { return "collab" }
    if MEDIA_TYPES.contains(lang) { return "media" }
    if APP_TYPES.contains(lang) { return "app" }
    if CALC_TYPES.contains(lang) { return "calc" }
    if let syn = KIND_SYNONYMS[lang] { return syn }
    let compact = lang.replacingOccurrences(of: "[-_\\s]+", with: "", options: .regularExpression)
    if WIDGET_LANGUAGES.contains(compact) { return compact }
    if let syn = KIND_SYNONYMS[compact] { return syn }
    if CHART_SYNONYMS[compact] != nil { return "chart" }
    return nil
}

func injectWidgetType(json: String, alias: String, kind: String) -> String {
    guard SUBTYPED_KINDS.contains(kind) else { return json }
    if json.contains("\"type\"") { return json }
    let type = kind == "chart" ? canonicalChartType(alias) : alias.lowercased()
    guard let brace = json.firstIndex(of: "{") else { return json }
    let after = json.index(after: brace)
    return String(json[..<after]) + "\"type\":\"\(type)\"," + String(json[after...])
}

// MARK: - Flexible JSON parsing & normalization

func parseRelaxedJSONObject(kind: String, raw: String) -> [String: Any]? {
    let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty, trimmed.count <= 65_536 else { return nil }
    if let data = trimmed.data(using: .utf8),
       let any = try? JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed]) {
        return normalizeJSONRoot(kind: kind, value: any)
    }
    // Strip trailing commas before } or ] and retry
    let cleaned = trimmed.replacingOccurrences(of: ",\\s*([}\\]])", with: "$1", options: .regularExpression)
    if let data = cleaned.data(using: .utf8),
       let any = try? JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed]) {
        return normalizeJSONRoot(kind: kind, value: any)
    }
    return nil
}

private func normalizeJSONRoot(kind: String, value: Any) -> [String: Any]? {
    if let arr = value as? [Any] {
        guard !arr.isEmpty else { return nil }
        let key: String
        switch kind {
        case "stat", "flashcards", "cards": key = "cards"
        case "tabs": key = "tabs"
        case "accordion": key = "sections"
        case "steps": key = "steps"
        case "menu", "chips", "poll": key = "options"
        case "tree": key = "roots"
        case "timeline": key = "entries"
        case "quiz": key = "questions"
        case "form": key = "fields"
        case "followups": key = "prompts"
        case "actions": key = "buttons"
        case "voice": key = "phrases"
        case "calc": key = "sliders"
        case "terminal": key = "commands"
        default: key = "items"
        }
        return [key: arr]
    }
    guard var dict = value as? [String: Any] else { return nil }
    // Unwrap single-key envelope {"widget": {...}}, {"data": {...}}, {"<kind>": {...}}
    for envKey in [kind, "widget", "payload", "data", "content", "result", "spec", "config"] {
        if let inner = dict[envKey] as? [String: Any], dict.count <= 3 {
            var merged = inner
            if merged["title"] == nil, let t = dict["title"] { merged["title"] = t }
            dict = merged
            break
        }
    }
    return dict
}

func inferWidgetKind(_ raw: String) -> String? {
    guard let obj = parseRelaxedJSONObject(kind: "json", raw: raw) else { return nil }
    let keys = Set(obj.keys)
    var candidates: [String] = []
    for k in ["kind", "widget", "type", "sim", "view", "surface", "editor"] {
        if let s = obj[k] as? String, let mapped = widgetKindOf(s), !candidates.contains(mapped) {
            candidates.append(mapped)
        }
    }
    if parseNexlyPlan(raw) != nil { candidates.append("plan") }
    if keys.contains("series") || (keys.contains("nodes") && keys.contains("links")) || keys.contains("words") || keys.contains("axes") {
        candidates.append("chart")
    }
    if (keys.contains("columns") || keys.contains("headers")) && (keys.contains("rows") || keys.contains("data")) {
        candidates.append("table")
    }
    if keys.contains("tabs") { candidates.append("tabs") }
    if keys.contains("sections") { candidates.append("accordion"); candidates.append("tabs") }
    if keys.contains("roots") || keys.contains("tree") { candidates.append("tree") }
    if keys.contains("unified") || (keys.contains("before") && keys.contains("after")) || (keys.contains("old") && keys.contains("new")) {
        candidates.append("diff")
    }
    if keys.contains("cards") || keys.contains("metrics") || keys.contains("kpis") {
        candidates.append("flashcards"); candidates.append("stat"); candidates.append("cards")
    }
    if (keys.contains("left") && keys.contains("right")) || (keys.contains("sideA") && keys.contains("sideB")) {
        candidates.append("compare")
    }
    if keys.contains("entries") || keys.contains("events") || keys.contains("milestones") {
        candidates.append("timeline")
    }
    if keys.contains("questions") { candidates.append("quiz") }
    if keys.contains("fields") { candidates.append("form") }
    if keys.contains("options") || keys.contains("choices") {
        candidates.append("poll"); candidates.append("menu"); candidates.append("chips")
    }
    if keys.contains("style") && (keys.contains("max") || keys.contains("prompt")) {
        candidates.append("rating")
    }
    if keys.contains("prompts") || keys.contains("suggestions") { candidates.append("followups") }
    if keys.contains("buttons") || keys.contains("actions") { candidates.append("actions") }
    if keys.contains("phrases") { candidates.append("voice") }
    if keys.contains("sliders") || keys.contains("outputs") { candidates.append("calc") }
    if keys.contains("sim") { candidates.append("sim") }
    if keys.contains("view") || keys.contains("bonds") { candidates.append("stage3d") }
    if keys.contains("surface") { candidates.append("canvas"); candidates.append("collab") }
    if keys.contains("editor") || keys.contains("tracks") || keys.contains("cells") { candidates.append("editor") }
    if keys.contains("commands") || keys.contains("cwd") { candidates.append("terminal") }
    if keys.contains("room") { candidates.append("collab") }
    if keys.contains("steps") { candidates.append("steps"); candidates.append("app") }
    if keys.contains("items") || keys.contains("tasks") || keys.contains("todos") {
        candidates.append(contentsOf: ["checklist", "progress", "gauge", "carousel", "cards", "steps", "accordion", "menu", "chips", "app", "media"])
    }
    if keys.contains("pages") { candidates.append("media") }
    if keys.contains("body") && (keys.contains("title") || keys.contains("tone") || keys.contains("cta")) {
        candidates.append("panel")
    }
    return candidates.first { candidate in
        if candidate == "plan" { return parseNexlyPlan(raw) != nil }
        return isValidWidget(kind: candidate, json: raw)
    }
}

func isValidWidget(kind: String, json: String) -> Bool {
    guard let resolved = widgetKindOf(kind) else { return false }
    if resolved == "plan" { return parseNexlyPlan(json) != nil }
    guard let obj = parseRelaxedJSONObject(kind: resolved, raw: json) else { return false }
    switch resolved {
    case "chart":
        return (obj["series"] as? [Any])?.isEmpty == false ||
               (obj["data"] as? [Any])?.isEmpty == false ||
               (obj["values"] as? [Any])?.isEmpty == false ||
               (obj["nodes"] as? [Any])?.isEmpty == false ||
               (obj["words"] as? [Any])?.isEmpty == false ||
               obj["root"] is [String: Any]
    case "stat":
        let list = (obj["cards"] ?? obj["items"] ?? obj["metrics"] ?? obj["kpis"] ?? obj["stats"]) as? [[String: Any]]
        return list?.contains(where: { $0["label"] != nil || $0["title"] != nil || $0["name"] != nil }) == true
    case "table":
        let hasCols = (obj["columns"] ?? obj["headers"]) is [Any]
        let hasRows = (obj["rows"] ?? obj["data"]) is [Any]
        return hasCols && hasRows
    case "compare":
        return (obj["left"] is [String: Any] && obj["right"] is [String: Any]) ||
               (obj["sideA"] is [String: Any] && obj["sideB"] is [String: Any]) ||
               (obj["a"] is [String: Any] && obj["b"] is [String: Any])
    case "progress", "gauge":
        if obj["value"] is NSNumber && (obj["label"] is String || obj["title"] is String) { return true }
        let items = (obj["items"] ?? obj["bars"] ?? obj["metrics"]) as? [[String: Any]]
        return items?.contains(where: { $0["value"] is NSNumber || $0["percent"] is NSNumber || $0["score"] is NSNumber }) == true
    case "timeline":
        let entries = (obj["entries"] ?? obj["events"] ?? obj["milestones"] ?? obj["items"]) as? [[String: Any]]
        return entries?.contains(where: { $0["date"] != nil || $0["time"] != nil || $0["year"] != nil || $0["title"] != nil }) == true
    case "quiz":
        if let q = obj["question"] as? String, !q.isEmpty, (obj["options"] as? [Any])?.count ?? 0 >= 2 { return true }
        let qs = (obj["questions"] ?? obj["items"]) as? [[String: Any]]
        return qs?.contains(where: { ($0["q"] ?? $0["question"]) != nil }) == true
    case "form":
        let fields = (obj["fields"] ?? obj["inputs"] ?? obj["questions"]) as? [[String: Any]]
        return fields?.isEmpty == false
    case "carousel", "cards":
        let items = (obj["items"] ?? obj["cards"] ?? obj["slides"]) as? [[String: Any]]
        return items?.contains(where: { ($0["title"] ?? $0["name"] ?? $0["heading"]) != nil && ($0["front"] == nil) }) == true
    case "followups":
        let prompts = (obj["prompts"] ?? obj["suggestions"] ?? obj["questions"] ?? obj["items"]) as? [Any]
        return prompts?.isEmpty == false
    case "actions":
        let buttons = (obj["buttons"] ?? obj["actions"] ?? obj["items"]) as? [Any]
        return buttons?.isEmpty == false
    case "panel":
        return (obj["body"] ?? obj["text"] ?? obj["content"] ?? obj["message"] ?? obj["summary"] ?? obj["title"]) != nil
    case "voice":
        let phrases = (obj["phrases"] ?? obj["items"]) as? [[String: Any]]
        return phrases?.isEmpty == false
    case "calc":
        return ((obj["sliders"] ?? obj["inputs"]) as? [Any])?.isEmpty == false ||
               ((obj["outputs"] ?? obj["results"]) as? [Any])?.isEmpty == false
    case "tabs":
        let tabs = (obj["tabs"] ?? obj["items"] ?? obj["sections"]) as? [[String: Any]]
        return tabs?.contains(where: { ($0["label"] ?? $0["title"] ?? $0["name"]) != nil && ($0["body"] ?? $0["content"] ?? $0["text"] ?? $0["items"]) != nil }) == true
    case "accordion":
        let secs = (obj["sections"] ?? obj["items"] ?? obj["faqs"]) as? [[String: Any]]
        return secs?.contains(where: { ($0["label"] ?? $0["title"] ?? $0["question"] ?? $0["q"]) != nil && ($0["body"] ?? $0["content"] ?? $0["answer"] ?? $0["a"]) != nil }) == true
    case "steps":
        let steps = (obj["steps"] ?? obj["items"] ?? obj["stages"]) as? [[String: Any]]
        return steps?.contains(where: { ($0["title"] ?? $0["label"] ?? $0["step"]) != nil }) == true
    case "menu", "chips":
        let opts = (obj["options"] ?? obj["items"] ?? obj["choices"] ?? obj["tags"]) as? [Any]
        return opts?.isEmpty == false
    case "rating":
        return obj["title"] != nil || obj["prompt"] != nil || obj["question"] != nil || obj["style"] != nil || obj["max"] != nil
    case "poll":
        let opts = (obj["options"] ?? obj["choices"] ?? obj["items"]) as? [Any]
        return (opts?.count ?? 0) >= 2
    case "diff":
        return obj["unified"] != nil || obj["patch"] != nil || obj["diff"] != nil ||
               obj["before"] != nil || obj["after"] != nil || obj["old"] != nil || obj["new"] != nil
    case "tree":
        let roots = (obj["roots"] ?? obj["tree"] ?? obj["nodes"] ?? obj["items"]) as? [[String: Any]]
        return roots?.isEmpty == false
    case "flashcards":
        let cards = (obj["cards"] ?? obj["items"] ?? obj["deck"]) as? [[String: Any]]
        return cards?.contains(where: { ($0["front"] ?? $0["question"] ?? $0["term"]) != nil && ($0["back"] ?? $0["answer"] ?? $0["definition"]) != nil }) == true
    case "checklist":
        let items = (obj["items"] ?? obj["tasks"] ?? obj["todos"]) as? [Any]
        return items?.isEmpty == false
    case "media", "app", "sim", "stage3d", "canvas", "editor", "terminal", "collab":
        return !obj.isEmpty
    default:
        return false
    }
}

// MARK: - Palette & Shell

private enum WidgetPalette {
    static let hues: [Color] = [
        Color(red: 0.30, green: 0.44, blue: 1.00), // 0 azure
        Color(red: 0.05, green: 0.65, blue: 0.47), // 1 jade
        Color(red: 0.94, green: 0.55, blue: 0.00), // 2 amber
        Color(red: 0.91, green: 0.35, blue: 0.05), // 3 tangerine
        Color(red: 0.61, green: 0.21, blue: 0.71), // 4 orchid
        Color(red: 0.06, green: 0.60, blue: 0.68), // 5 teal
        Color(red: 0.84, green: 0.20, blue: 0.42), // 6 rose
        Color(red: 0.40, green: 0.25, blue: 0.85), // 7 violet
        Color(red: 0.40, green: 0.66, blue: 0.06), // 8 olive
        Color(red: 0.88, green: 0.19, blue: 0.19), // 9 crimson
        Color(red: 0.04, green: 0.45, blue: 0.52), // 10 deep teal
        Color(red: 0.37, green: 0.24, blue: 0.77)  // 11 indigo
    ]

    static func at(_ index: Int) -> Color {
        let n = hues.count
        return hues[((index % n) + n) % n]
    }

    static func parseHex(_ raw: String?) -> Color? {
        guard let raw = raw?.trimmingCharacters(in: .whitespacesAndNewlines),
              raw.hasPrefix("#") else { return nil }
        let hex = String(raw.dropFirst())
        guard hex.count == 6, let val = UInt64(hex, radix: 16) else { return nil }
        let r = Double((val >> 16) & 0xFF) / 255.0
        let g = Double((val >> 8) & 0xFF) / 255.0
        let b = Double(val & 0xFF) / 255.0
        return Color(red: r, green: g, blue: b)
    }
}

private let RAW_CODE_OR_URL_KEYS: Set<String> = [
    "unified", "patch", "diff", "before", "after", "old", "new",
    "original", "modified", "expr", "formula", "url", "href", "link",
    "code", "html", "svg"
]

private func rawStr(_ dict: [String: Any], _ keys: String...) -> String {
    for k in keys {
        if let s = dict[k] as? String, !s.isEmpty { return NexlyIcons.normalize(s) }
        if let n = dict[k] as? NSNumber { return n.stringValue }
    }
    return ""
}

private func str(_ dict: [String: Any], _ keys: String...) -> String {
    for k in keys {
        if let s = dict[k] as? String, !s.isEmpty {
            return RAW_CODE_OR_URL_KEYS.contains(k) ? s : NexlyIcons.cleanPlainText(s)
        }
        if let n = dict[k] as? NSNumber { return n.stringValue }
    }
    return ""
}

private func dbl(_ dict: [String: Any], _ keys: String..., fallback: Double = 0) -> Double {
    for k in keys {
        if let n = dict[k] as? NSNumber { return n.doubleValue }
        if let s = dict[k] as? String, let v = Double(s.replacingOccurrences(of: "[^0-9.+-]", with: "", options: .regularExpression)) {
            return v
        }
    }
    return fallback
}

private func widgetTagGlassIconIOS(_ tag: String) -> (icon: String, tone: NexlyGlassTone) {
    switch tag.lowercased() {
    case "chart": return ("chart-bar", .lavender)
    case "table": return ("list-check", .cyan)
    case "calc", "calculator": return ("calculator", .amber)
    case "stat", "kpi", "metric": return ("trending-up", .green)
    case "compare", "versus": return ("arrows-sort", .lavender)
    case "timeline", "schedule", "calendar": return ("calendar", .blue)
    case "quiz", "poll", "choices": return ("help-circle", .lavender)
    case "form", "input": return ("edit", .cyan)
    case "checklist", "productivity", "kanban", "todo": return ("checkbox", .green)
    case "code", "terminal", "diff": return ("code", .slate)
    case "tree", "hierarchy", "flow", "diagram": return ("target", .teal)
    case "citation", "sources", "links": return ("link", .blue)
    case "alert", "callout", "warning": return ("alert-triangle", .amber)
    default: return ("sparkles", .lavender)
    }
}

private struct HtmlSandboxWebView: UIViewRepresentable {
    let html: String
    @Binding var dynamicHeight: CGFloat

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.userContentController.add(context.coordinator, name: "gsResize")
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.backgroundColor = .clear
        webView.scrollView.isScrollEnabled = false
        webView.navigationDelegate = context.coordinator
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        let wrapped = """
        <!doctype html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
        <style>
        :root {
          --gs-text: #1E1638;
          --gs-text-2: #4A416E;
          --gs-text-3: #7A719E;
          --gs-surface: rgba(255,255,255,0.82);
          --gs-raised: rgba(243,238,255,0.92);
          --gs-border: rgba(108,76,246,0.22);
          --gs-accent: #6C4CF6;
          --gs-hue-1: #6C4CF6; --gs-hue-2: #06B6D4; --gs-hue-3: #10B981;
          --gs-hue-4: #F59E0B; --gs-hue-5: #EC4899; --gs-hue-6: #3B82F6;
          --gs-radius: 14px; --gs-space: 8px;
          --gs-font: -apple-system, BlinkMacSystemFont, sans-serif;
        }
        * { box-sizing: border-box; }
        html, body {
          margin: 0; padding: 4px;
          font-family: var(--gs-font);
          color: var(--gs-text);
          background: transparent;
        }
        button, input, select {
          font-family: inherit;
        }
        svg { max-width: 100%; height: auto; display: block; margin: 0 auto; }
        </style>
        </head>
        <body>
        \(html)
        <script>
        function reportHeight() {
          var h = Math.max(document.body.scrollHeight || 0, document.documentElement.scrollHeight || 0, 80);
          if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.gsResize) {
            window.webkit.messageHandlers.gsResize.postMessage(h);
          }
        }
        window.addEventListener('load', reportHeight);
        window.addEventListener('click', function() { setTimeout(reportHeight, 50); });
        window.addEventListener('input', function() { setTimeout(reportHeight, 50); });
        setTimeout(reportHeight, 120);
        </script>
        </body>
        </html>
        """
        if context.coordinator.lastLoaded != wrapped {
            context.coordinator.lastLoaded = wrapped
            webView.loadHTMLString(wrapped, baseURL: nil)
        }
    }

    final class Coordinator: NSObject, WKNavigationDelegate, WKScriptMessageHandler {
        var parent: HtmlSandboxWebView
        var lastLoaded: String = ""

        init(_ parent: HtmlSandboxWebView) {
            self.parent = parent
        }

        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            if let h = message.body as? CGFloat {
                DispatchQueue.main.async {
                    self.parent.dynamicHeight = min(max(h + 12, 90), 560)
                }
            } else if let n = message.body as? NSNumber {
                DispatchQueue.main.async {
                    self.parent.dynamicHeight = min(max(CGFloat(truncating: n) + 12, 90), 560)
                }
            }
        }
    }
}

// MARK: - Top-level Widget & HTML Views

struct WidgetBlockView: View {
    let kind: String
    let json: String
    let open: Bool

    var body: some View {
        let resolvedKind = widgetKindOf(kind) ?? kind.lowercased()
        if open {
            HStack(spacing: 8) {
                ProgressView().scaleEffect(0.8)
                Text("Building \(resolvedKind)…")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textSecondary)
            }
            .frame(maxWidth: .infinity, minHeight: 76)
            .padding(12)
            .nexlyGlassCard(corner: 16)
        } else if let obj = parseRelaxedJSONObject(kind: resolvedKind, raw: json) {
            let effectiveKind = isValidWidget(kind: resolvedKind, json: json)
                ? resolvedKind
                : (inferWidgetKind(json) ?? resolvedKind)
            let accent = WidgetPalette.parseHex(obj["accent"] as? String) ?? WidgetPalette.at( abs(effectiveKind.hashValue) )
            let rawTitle = rawStr(obj, "title", "heading", "name", "question")
            let (explicitIcon, strippedTitle) = NexlyIcons.extractLeadingIcon(rawTitle)
            let cleanTitle = NexlyIcons.cleanPlainText(strippedTitle)
            let tagIcon = widgetTagGlassIconIOS(effectiveKind)
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .center, spacing: 8) {
                    NexlyGlassGlyphTile(
                        name: explicitIcon ?? tagIcon.icon,
                        tone: tagIcon.tone,
                        size: 24,
                        glyphSize: 14
                    )
                    if !cleanTitle.isEmpty {
                        Text(cleanTitle)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Aero.text)
                            .lineLimit(2)
                    }
                    Spacer(minLength: 4)
                    Text(effectiveKind.uppercased())
                        .font(.system(size: 10, weight: .bold, design: .rounded))
                        .foregroundStyle(accent)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(accent.opacity(0.14), in: Capsule())
                }
                WidgetBodyView(kind: effectiveKind, obj: obj, accent: accent)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .nexlyGlassCard(corner: 18)
        } else {
            CodeBlockCardView(language: kind, code: json, open: false)
        }
    }
}

struct HtmlBlockView: View {
    let kind: String
    let source: String
    let open: Bool
    @State private var webHeight: CGFloat = 180

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                NexlyGlassGlyphTile(
                    name: kind == "map" ? "world" : "code",
                    tone: .cyan,
                    size: 22,
                    glyphSize: 13
                )
                Text(kind.uppercased())
                    .font(.system(size: 10, weight: .bold, design: .rounded))
                    .foregroundStyle(WidgetPalette.at(5))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(WidgetPalette.at(5).opacity(0.14), in: Capsule())
                Spacer()
            }
            if kind == "map", let obj = parseRelaxedJSONObject(kind: "map", raw: source) {
                let title = NexlyIcons.cleanPlainText(str(obj, "title", "label", "name"))
                let mode = str(obj, "mode")
                let rawPins = ((obj["pins"] ?? obj["markers"] ?? obj["locations"] ?? obj["places"]) as? [[String: Any]]) ?? []
                let rawStops = ((obj["stops"] ?? obj["waypoints"] ?? obj["itinerary"]) as? [[String: Any]]) ?? []
                let rawSteps = ((obj["steps"] ?? obj["directions"]) as? [Any]) ?? []
                if !title.isEmpty {
                    HStack {
                        Text(title).font(.system(size: 14, weight: .semibold)).foregroundStyle(Aero.text)
                        if !mode.isEmpty {
                            Spacer()
                            Text(mode.uppercased()).font(Aero.mono(10)).foregroundStyle(Aero.textSecondary)
                        }
                    }
                }
                let places = rawPins.isEmpty ? rawStops : rawPins
                if !places.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(Array(places.prefix(12).enumerated()), id: \.offset) { idx, p in
                                let lbl = NexlyIcons.cleanPlainText(str(p, "label", "title", "name", "place"))
                                let det = NexlyIcons.cleanPlainText(str(p, "detail", "description", "address"))
                                let pLat = dbl(p, "lat", "latitude")
                                let pLng = dbl(p, "lng", "lon", "longitude")
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(lbl.isEmpty ? "Stop \(idx + 1)" : lbl)
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundStyle(Aero.text)
                                    if !det.isEmpty {
                                        Text(det).font(.system(size: 11)).foregroundStyle(Aero.textSecondary).lineLimit(2)
                                    }
                                    Text(String(format: "%.4f, %.4f", pLat, pLng))
                                        .font(Aero.mono(10))
                                        .foregroundStyle(WidgetPalette.at(5))
                                }
                                .padding(9)
                                .frame(minWidth: 140, alignment: .leading)
                                .background(Color.white.opacity(0.55), in: RoundedRectangle(cornerRadius: 10))
                            }
                        }
                    }
                } else {
                    let lat = dbl(obj, "lat", "latitude")
                    let lng = dbl(obj, "lng", "lon", "longitude")
                    Text(String(format: "Coordinates: %.4f, %.4f", lat, lng))
                        .font(Aero.mono(12))
                        .foregroundStyle(Aero.textSecondary)
                }
                if !rawSteps.isEmpty {
                    VStack(alignment: .leading, spacing: 4) {
                        ForEach(Array(rawSteps.prefix(15).enumerated()), id: \.offset) { idx, st in
                            let instruction = (st as? String) ?? str((st as? [String: Any]) ?? [:], "instruction", "text", "step", "title")
                            let dist = str((st as? [String: Any]) ?? [:], "distance", "dist", "duration")
                            HStack(alignment: .firstTextBaseline, spacing: 6) {
                                Text("\(idx + 1).").font(.system(size: 11, weight: .bold)).foregroundStyle(WidgetPalette.at(5))
                                Text(NexlyIcons.cleanPlainText(instruction)).font(.system(size: 12)).foregroundStyle(Aero.text)
                                Spacer()
                                if !dist.isEmpty {
                                    Text(dist).font(Aero.mono(10)).foregroundStyle(Aero.textSecondary)
                                }
                            }
                        }
                    }
                }
            } else if open {
                HStack(spacing: 8) {
                    ProgressView().scaleEffect(0.8)
                    Text("Rendering \(kind)…")
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textSecondary)
                }
                .frame(maxWidth: .infinity, minHeight: 72)
            } else {
                HtmlSandboxWebView(html: source, dynamicHeight: $webHeight)
                    .frame(maxWidth: .infinity)
                    .frame(height: webHeight)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .nexlyGlassCard(corner: 18)
    }
}

// MARK: - Per-kind SwiftUI Body Views

private struct WidgetBodyView: View {
    let kind: String
    let obj: [String: Any]
    let accent: Color

    var body: some View {
        switch kind {
        case "chart":
            ChartBodyView(obj: obj, accent: accent)
        case "stat":
            StatBodyView(obj: obj, accent: accent)
        case "table":
            TableBodyView(obj: obj, accent: accent)
        case "compare":
            CompareBodyView(obj: obj)
        case "progress", "gauge":
            ProgressBodyView(obj: obj, accent: accent)
        case "timeline":
            TimelineBodyView(obj: obj, accent: accent)
        case "quiz":
            QuizBodyView(obj: obj, accent: accent)
        case "tabs":
            TabsBodyView(obj: obj, accent: accent)
        case "accordion":
            AccordionBodyView(obj: obj, accent: accent)
        case "steps":
            StepsBodyView(obj: obj, accent: accent)
        case "poll":
            PollBodyView(obj: obj, accent: accent)
        case "rating":
            RatingBodyView(obj: obj, accent: accent)
        case "diff":
            DiffBodyView(obj: obj)
        case "tree":
            TreeBodyView(obj: obj, accent: accent)
        case "flashcards":
            FlashcardsBodyView(obj: obj, accent: accent)
        case "checklist":
            ChecklistBodyView(obj: obj, accent: accent)
        case "menu", "chips", "followups", "actions":
            ChoicesBodyView(obj: obj, accent: accent)
        case "panel":
            PanelBodyView(obj: obj, accent: accent)
        case "form":
            FormBodyView(obj: obj, accent: accent)
        case "calc":
            CalcBodyView(obj: obj, accent: accent)
        case "media", "carousel", "cards":
            MediaBodyView(obj: obj, accent: accent)
        case "app", "editor":
            ProductivityBodyView(kind: kind, obj: obj, accent: accent)
        default:
            GenericStructuredBodyView(kind: kind, obj: obj, accent: accent)
        }
    }
}

private struct ChartBodyView: View {
    let obj: [String: Any]
    let accent: Color

    @State private var activeType: String = ""
    @State private var rangeIdx: Int = 0
    @State private var selectedIdx: Int? = nil

    private struct BarPoint: Identifiable {
        let id = UUID()
        let index: Int
        let seriesName: String
        let label: String
        let value: Double
        let color: Color
    }

    private var initialChartType: String {
        let raw = str(obj, "type", "chartType").lowercased()
        if ["line", "bar", "area", "pie", "donut"].contains(raw) { return raw }
        return "line"
    }

    private var currentType: String {
        activeType.isEmpty ? initialChartType : activeType
    }

    private var allPoints: [BarPoint] {
        var out: [BarPoint] = []
        let labels = (obj["labels"] as? [Any])?.map { "\($0)" } ?? []
        if let series = obj["series"] as? [[String: Any]] {
            for (sIdx, s) in series.prefix(8).enumerated() {
                let sName = str(s, "name", "label")
                let color = WidgetPalette.at(sIdx)
                if let data = s["data"] as? [Any] {
                    for (pIdx, pt) in data.prefix(12).enumerated() {
                        let fallbackLbl = pIdx < labels.count ? labels[pIdx] : "#\(pIdx + 1)"
                        if let pair = pt as? [Any], pair.count >= 2 {
                            let lbl = pIdx < labels.count ? labels[pIdx] : "\(pair[0])"
                            let val = (pair[1] as? NSNumber)?.doubleValue ?? Double("\(pair[1])") ?? 0
                            out.append(BarPoint(index: out.count, seriesName: sName, label: lbl, value: val, color: color))
                        } else if let num = pt as? NSNumber {
                            out.append(BarPoint(index: out.count, seriesName: sName, label: fallbackLbl, value: num.doubleValue, color: color))
                        } else if let d = pt as? [String: Any] {
                            let lbl = str(d, "label", "x", "name")
                            let val = dbl(d, "value", "y", "v")
                            out.append(BarPoint(index: out.count, seriesName: sName, label: lbl.isEmpty ? fallbackLbl : lbl, value: val, color: color))
                        }
                    }
                }
            }
        } else if let data = (obj["data"] ?? obj["items"] ?? obj["values"] ?? obj["slices"]) as? [[String: Any]] {
            for (idx, d) in data.prefix(12).enumerated() {
                let lbl = str(d, "label", "name", "x", "category")
                let val = dbl(d, "value", "y", "count", "score")
                out.append(BarPoint(index: idx, seriesName: "", label: lbl.isEmpty ? "#\(idx + 1)" : lbl, value: val, color: WidgetPalette.at(idx)))
            }
        }
        return out
    }

    private var slicedRange: (points: [BarPoint], label: String) {
        let base = allPoints
        let n = base.count
        guard n > 2 else { return (base, "All (\(n) pts)") }
        let q3 = max(2, (n * 3 + 3) / 4)
        let half = max(2, (n + 1) / 2)
        let specs: [(String, Int, Int)] = [
            ("All (\(n) pts)", 0, n),
            ("Last \(q3) pts", n - q3, n),
            ("Last \(half) pts", n - half, n),
            ("First \(half) pts", 0, half)
        ]
        let spec = specs[rangeIdx % specs.count]
        let slice = Array(base[spec.1..<spec.2]).enumerated().map { idx, pt in
            BarPoint(index: idx, seriesName: pt.seriesName, label: pt.label, value: pt.value, color: pt.color)
        }
        return (slice, spec.0)
    }

    private func compactNumber(_ n: Double, unit: String) -> String {
        let prefix = ["$", "€", "£"].contains(unit) ? unit : ""
        let suffix = (!unit.isEmpty && prefix.isEmpty) ? unit : ""
        let absVal = abs(n)
        if absVal >= 1_000_000 { return String(format: "%@%.1fM%@", prefix, n / 1_000_000, suffix) }
        if absVal >= 1_000 { return String(format: "%@%.1fK%@", prefix, n / 1_000, suffix) }
        if n.rounded() == n { return String(format: "%@%.0f%@", prefix, n, suffix) }
        return String(format: "%@%.1f%@", prefix, n, suffix)
    }

    private static let chartTypeTabs: [(id: String, icon: String, label: String)] = [
        ("line", "chart-line", "Line"),
        ("bar", "chart-bar", "Bar"),
        ("area", "chart-area", "Area"),
        ("pie", "chart-pie", "Pie")
    ]

    var body: some View {
        let (pts, rangeLabel) = slicedRange
        let maxVal = max(pts.map { abs($0.value) }.max() ?? 1.0, 0.0001)
        let totalVal = max(pts.map { max(0, $0.value) }.reduce(0, +), 0.0001)
        let unit = str(obj, "unit")
        let subtitle = str(obj, "subtitle")
        let peakIdx = pts.enumerated().max(by: { $0.element.value < $1.element.value })?.offset ?? 0
        let activeIdx = min(max(0, selectedIdx ?? peakIdx), max(0, pts.count - 1))

        VStack(alignment: .leading, spacing: 10) {
            // 07. Interactive Charts: Type Switcher Pills + Range Selector (Screenshot 6)
            HStack(spacing: 6) {
                HStack(spacing: 3) {
                    ForEach(Array(Self.chartTypeTabs.enumerated()), id: \.offset) { _, tab in
                        let tabId = tab.id
                        let isSel = currentType == tabId || (tabId == "pie" && currentType == "donut")
                        Button {
                            GSHaptics.select()
                            withAnimation(.easeOut(duration: 0.18)) { activeType = tabId }
                        } label: {
                            HStack(spacing: 4) {
                                NexlyGlyph(
                                    name: tab.icon,
                                    size: 12,
                                    color: isSel ? .white : Color(red: 0.243, green: 0.212, blue: 0.400)
                                )
                                Text(tab.label)
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundStyle(isSel ? Color.white : Color(red: 0.243, green: 0.212, blue: 0.400))
                            }
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(
                                RoundedRectangle(cornerRadius: 7)
                                    .fill(isSel ? Color(red: 0.424, green: 0.298, blue: 0.965) : Color.clear)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(3)
                .background(RoundedRectangle(cornerRadius: 10).fill(Color(red: 0.929, green: 0.902, blue: 1.0)))

                Spacer(minLength: 4)

                Button {
                    GSHaptics.select()
                    rangeIdx = (rangeIdx + 1) % 4
                    selectedIdx = nil
                } label: {
                    HStack(spacing: 4) {
                        NexlyGlyph(
                            name: "calendar",
                            size: 12,
                            color: Color(red: 0.263, green: 0.231, blue: 0.420)
                        )
                        Text("\(rangeLabel) ▾")
                            .font(.system(size: 10.5, weight: .semibold))
                            .foregroundStyle(Color(red: 0.263, green: 0.231, blue: 0.420))
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(RoundedRectangle(cornerRadius: 8).fill(Color.white.opacity(0.82)))
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color(red: 0.875, green: 0.843, blue: 0.980), lineWidth: 1))
                }
                .buttonStyle(.plain)
            }

            if !subtitle.isEmpty {
                Text(subtitle).font(Aero.caption()).foregroundStyle(Aero.textSecondary)
            }

            // Floating Glass Tooltip for Selected / Peak Datum (Screenshot 6)
            if !pts.isEmpty && activeIdx < pts.count {
                let selPt = pts[activeIdx]
                let avgVal = totalVal / Double(max(1, pts.count))
                let prevVal = activeIdx > 0 ? pts[activeIdx - 1].value : avgVal
                let stepPct = prevVal != 0 ? ((selPt.value - prevVal) / abs(prevVal)) * 100.0 : 0.0
                HStack(spacing: 8) {
                    Circle()
                        .fill(Color(red: 0.424, green: 0.298, blue: 0.965))
                        .frame(width: 8, height: 8)
                    Text(selPt.label)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Aero.textSecondary)
                    Text(compactNumber(selPt.value, unit: unit))
                        .font(.system(size: 13.5, weight: .bold))
                        .foregroundStyle(Aero.text)
                    Spacer()
                    Text(String(format: "%@ %.1f%%", stepPct >= 0 ? "↑" : "↓", abs(stepPct)))
                        .font(.system(size: 11.5, weight: .bold))
                        .foregroundStyle(stepPct >= 0 ? Color(red: 0.071, green: 0.604, blue: 0.353) : Color(red: 0.878, green: 0.310, blue: 0.373))
                }
                .padding(.horizontal, 11)
                .padding(.vertical, 7)
                .background(RoundedRectangle(cornerRadius: 10).fill(Color.white.opacity(0.86)))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color(red: 0.863, green: 0.827, blue: 0.980), lineWidth: 1))
            }

            // Chart Visualization Surface
            if (currentType == "line" || currentType == "area") && pts.count >= 2 {
                // True 2D Smooth Cubic Bezier Line/Area Chart with Y-axis, X-axis, Gradient Fill, and Vertical Guide Line (Screenshot 6)
                VStack(spacing: 4) {
                    HStack(spacing: 6) {
                        VStack(alignment: .trailing, spacing: 0) {
                            ForEach([1.0, 0.66, 0.33, 0.0], id: \.self) { frac in
                                Text(compactNumber(maxVal * frac, unit: unit))
                                    .font(.system(size: 9.5, weight: .medium))
                                    .foregroundStyle(Aero.textSecondary)
                                if frac > 0 { Spacer() }
                            }
                        }
                        .frame(width: 36, height: 132)

                        GeometryReader { geo in
                            let w = max(1, geo.size.width)
                            let h = max(1, geo.size.height)
                            let count = pts.count
                            let coords: [CGPoint] = pts.enumerated().map { idx, pt in
                                let x = count > 1 ? w * CGFloat(idx) / CGFloat(count - 1) : w * 0.5
                                let y = h * (1.0 - CGFloat(min(max(pt.value / maxVal, 0.0), 1.0)))
                                return CGPoint(x: x, y: y)
                            }
                            let linePath = Path { path in
                                for (i, p) in coords.enumerated() {
                                    if i == 0 {
                                        path.move(to: p)
                                    } else {
                                        let prev = coords[i - 1]
                                        let midX = (prev.x + p.x) * 0.5
                                        path.addCurve(
                                            to: p,
                                            control1: CGPoint(x: midX, y: prev.y),
                                            control2: CGPoint(x: midX, y: p.y)
                                        )
                                    }
                                }
                            }
                            let areaPath = Path { path in
                                path.addPath(linePath)
                                if let last = coords.last, let first = coords.first {
                                    path.addLine(to: CGPoint(x: last.x, y: h))
                                    path.addLine(to: CGPoint(x: first.x, y: h))
                                    path.closeSubpath()
                                }
                            }

                            ZStack {
                                // Horizontal gridlines
                                ForEach([0.0, 0.33, 0.66, 1.0], id: \.self) { g in
                                    Path { p in
                                        let y = h * CGFloat(g)
                                        p.move(to: CGPoint(x: 0, y: y))
                                        p.addLine(to: CGPoint(x: w, y: y))
                                    }
                                    .stroke(Color.black.opacity(0.07), lineWidth: 1)
                                }

                                // Soft purple gradient fill underneath the curve
                                areaPath.fill(
                                    LinearGradient(
                                        colors: [
                                            Color(red: 0.424, green: 0.298, blue: 0.965).opacity(currentType == "area" ? 0.34 : 0.18),
                                            Color(red: 0.424, green: 0.298, blue: 0.965).opacity(0.01)
                                        ],
                                        startPoint: .top,
                                        endPoint: .bottom
                                    )
                                )

                                // Smooth cubic Bezier stroke
                                linePath.stroke(
                                    Color(red: 0.424, green: 0.298, blue: 0.965),
                                    style: StrokeStyle(lineWidth: 2.8, lineCap: .round, lineJoin: .round)
                                )

                                // Selected datum vertical dashed guide line
                                if activeIdx < coords.count {
                                    let selCoord = coords[activeIdx]
                                    Path { p in
                                        p.move(to: CGPoint(x: selCoord.x, y: 0))
                                        p.addLine(to: CGPoint(x: selCoord.x, y: h))
                                    }
                                    .stroke(
                                        Color(red: 0.424, green: 0.298, blue: 0.965).opacity(0.45),
                                        style: StrokeStyle(lineWidth: 1.2, dash: [4, 3])
                                    )
                                }

                                // Interactive data points
                                ForEach(Array(coords.enumerated()), id: \.offset) { idx, c in
                                    let isSel = idx == activeIdx
                                    Circle()
                                        .fill(isSel ? Color.white : Color(red: 0.424, green: 0.298, blue: 0.965))
                                        .frame(width: isSel ? 11 : 7, height: isSel ? 11 : 7)
                                        .overlay(
                                            Circle()
                                                .stroke(Color(red: 0.424, green: 0.298, blue: 0.965), lineWidth: isSel ? 3 : 0)
                                        )
                                        .position(c)
                                        .onTapGesture {
                                            GSHaptics.select()
                                            selectedIdx = idx
                                        }
                                }
                            }
                        }
                        .frame(height: 132)
                    }

                    // X-axis labels row
                    HStack(spacing: 0) {
                        Spacer().frame(width: 42)
                        ForEach(Array(pts.prefix(8).enumerated()), id: \.offset) { idx, pt in
                            Text(pt.label)
                                .font(.system(size: 10, weight: idx == activeIdx ? .bold : .regular))
                                .foregroundStyle(idx == activeIdx ? Color(red: 0.357, green: 0.239, blue: 0.961) : Aero.textSecondary)
                                .frame(maxWidth: .infinity)
                                .lineLimit(1)
                        }
                    }
                }
                .padding(.top, 4)
            } else if currentType == "bar" && pts.count >= 2 {
                HStack(alignment: .bottom, spacing: 8) {
                    ForEach(Array(pts.prefix(8).enumerated()), id: \.offset) { idx, pt in
                        let isSel = idx == activeIdx
                        let ratio = CGFloat(min(max(abs(pt.value) / maxVal, 0.08), 1.0))
                        Button {
                            GSHaptics.select()
                            selectedIdx = idx
                        } label: {
                            VStack(spacing: 4) {
                                RoundedRectangle(cornerRadius: 6)
                                    .fill(
                                        isSel
                                            ? Color(red: 0.424, green: 0.298, blue: 0.965)
                                            : Color(red: 0.424, green: 0.298, blue: 0.965).opacity(0.45)
                                    )
                                    .frame(height: max(12, 110 * ratio))
                                Text(pt.label)
                                    .font(.system(size: 10, weight: isSel ? .bold : .medium))
                                    .foregroundStyle(isSel ? Color(red: 0.357, green: 0.239, blue: 0.961) : Aero.textSecondary)
                                    .lineLimit(1)
                            }
                            .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .frame(height: 132)
                .padding(.top, 4)
            } else if (currentType == "pie" || currentType == "donut") && !pts.isEmpty {
                // True 2D Donut Ring Chart with Center Total + Legend (Screenshots 1 & 6)
                HStack(spacing: 16) {
                    ZStack {
                        Circle()
                            .stroke(Color.black.opacity(0.06), lineWidth: 16)
                            .frame(width: 96, height: 96)
                        ForEach(Array(pts.prefix(6).enumerated()), id: \.offset) { idx, pt in
                            let startSum = pts.prefix(idx).map { max(0, $0.value) }.reduce(0, +)
                            let startFrac = CGFloat(startSum / totalVal)
                            let endFrac = CGFloat((startSum + max(0, pt.value)) / totalVal)
                            Circle()
                                .trim(from: startFrac, to: max(startFrac + 0.005, endFrac - 0.008))
                                .stroke(pt.color, style: StrokeStyle(lineWidth: idx == activeIdx ? 18 : 15, lineCap: .butt))
                                .rotationEffect(.degrees(-90))
                                .frame(width: 96, height: 96)
                                .onTapGesture {
                                    GSHaptics.select()
                                    selectedIdx = idx
                                }
                        }
                        VStack(spacing: 1) {
                            Text(compactNumber(totalVal, unit: unit))
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(Aero.text)
                            Text("Total")
                                .font(.system(size: 9.5, weight: .medium))
                                .foregroundStyle(Aero.textSecondary)
                        }
                    }
                    .padding(.leading, 4)

                    VStack(alignment: .leading, spacing: 5) {
                        ForEach(Array(pts.prefix(5).enumerated()), id: \.offset) { idx, pt in
                            let sharePct = (max(0, pt.value) / totalVal) * 100.0
                            Button {
                                GSHaptics.select()
                                selectedIdx = idx
                            } label: {
                                HStack(spacing: 6) {
                                    Circle()
                                        .fill(pt.color)
                                        .frame(width: 8, height: 8)
                                    Text(pt.label)
                                        .font(.system(size: 11.5, weight: idx == activeIdx ? .bold : .medium))
                                        .foregroundStyle(Aero.text)
                                        .lineLimit(1)
                                    Spacer()
                                    Text(String(format: "%.0f%%", sharePct))
                                        .font(.system(size: 11, weight: .bold))
                                        .foregroundStyle(Aero.textSecondary)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
                .padding(.vertical, 6)
            } else {
                ForEach(Array(pts.enumerated()), id: \.offset) { idx, pt in
                    let isSel = idx == activeIdx
                    let sharePct = (max(0, pt.value) / totalVal) * 100.0
                    Button {
                        GSHaptics.select()
                        selectedIdx = idx
                    } label: {
                        VStack(alignment: .leading, spacing: 3) {
                            HStack {
                                Text(pt.seriesName.isEmpty ? pt.label : "\(pt.label) · \(pt.seriesName)")
                                    .font(.system(size: 12, weight: isSel ? .bold : .medium))
                                    .foregroundStyle(Aero.text)
                                    .lineLimit(1)
                                Spacer()
                                Text(
                                    currentType == "pie" || currentType == "donut"
                                        ? String(format: "%@ (%.0f%%)", compactNumber(pt.value, unit: unit), sharePct)
                                        : compactNumber(pt.value, unit: unit)
                                )
                                .font(Aero.mono(11))
                                .foregroundStyle(isSel ? Color(red: 0.357, green: 0.239, blue: 0.961) : Aero.textSecondary)
                            }
                            GeometryReader { geo in
                                ZStack(alignment: .leading) {
                                    Capsule().fill(Color.black.opacity(0.06))
                                    Capsule()
                                        .fill(isSel ? Color(red: 0.424, green: 0.298, blue: 0.965) : pt.color)
                                        .frame(width: max(6, geo.size.width * CGFloat(min(abs(pt.value) / maxVal, 1.0))))
                                }
                            }
                            .frame(height: isSel ? 9 : 7)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }

            // 3-Card KPI Summary Strip with Top-Left Lavender Icon Badge & Bottom Green Pill (Screenshot 6)
            if pts.count >= 2 {
                let firstVal = pts.first?.value ?? 1.0
                let lastVal = pts.last?.value ?? 0.0
                let growthPct = firstVal != 0 ? ((lastVal - firstVal) / abs(firstVal)) * 100.0 : 0.0
                let pos = growthPct >= 0
                let pillBg = pos ? Color(red: 0.902, green: 0.976, blue: 0.941) : Color(red: 0.992, green: 0.925, blue: 0.925)
                let pillFg = pos ? Color(red: 0.071, green: 0.604, blue: 0.353) : Color(red: 0.878, green: 0.310, blue: 0.373)
                let peakPt = pts[peakIdx]
                HStack(spacing: 7) {
                    VStack(alignment: .leading, spacing: 3) {
                        NexlyGlassGlyphTile(
                            name: "chart-bar",
                            tone: .lavender,
                            size: 24,
                            glyphSize: 14
                        )
                        Text(compactNumber(totalVal, unit: unit))
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(Aero.text)
                        Text("Total")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundStyle(Aero.textSecondary)
                        Text(String(format: "%@ %@%.1f%%", pos ? "↑" : "↓", pos ? "+" : "", growthPct))
                            .font(.system(size: 9.5, weight: .bold))
                            .foregroundStyle(pillFg)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Capsule().fill(pillBg))
                    }
                    .padding(.horizontal, 9)
                    .padding(.vertical, 8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color.white.opacity(0.82)))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(red: 0.882, green: 0.855, blue: 0.980), lineWidth: 1))

                    VStack(alignment: .leading, spacing: 3) {
                        NexlyGlassGlyphTile(
                            name: "trophy",
                            tone: .amber,
                            size: 24,
                            glyphSize: 14
                        )
                        Text(compactNumber(peakPt.value, unit: unit))
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(Aero.text)
                        Text("Peak (\(peakPt.label))")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundStyle(Aero.textSecondary)
                            .lineLimit(1)
                        HStack(spacing: 3) {
                            NexlyGlyph(
                                name: "star",
                                size: 10,
                                color: Color(red: 0.357, green: 0.239, blue: 0.961)
                            )
                            Text("Highest")
                                .font(.system(size: 9.5, weight: .bold))
                                .foregroundStyle(Color(red: 0.357, green: 0.239, blue: 0.961))
                        }
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Capsule().fill(Color(red: 0.937, green: 0.918, blue: 1.0)))
                    }
                    .padding(.horizontal, 9)
                    .padding(.vertical, 8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color.white.opacity(0.82)))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(red: 0.882, green: 0.855, blue: 0.980), lineWidth: 1))

                    VStack(alignment: .leading, spacing: 3) {
                        NexlyGlassGlyphTile(
                            name: "trending-up",
                            tone: .green,
                            size: 24,
                            glyphSize: 14
                        )
                        Text(String(format: "%@%.1f%%", pos ? "+" : "", growthPct))
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(Aero.text)
                        Text("Growth Rate")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundStyle(Aero.textSecondary)
                            .lineLimit(1)
                        Text(String(format: "%@ %@%.1f%%", pos ? "↑" : "↓", pos ? "+" : "", growthPct))
                            .font(.system(size: 9.5, weight: .bold))
                            .foregroundStyle(pillFg)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Capsule().fill(pillBg))
                    }
                    .padding(.horizontal, 9)
                    .padding(.vertical, 8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color.white.opacity(0.82)))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(red: 0.882, green: 0.855, blue: 0.980), lineWidth: 1))
                }

                // Key Insights Box (Screenshot 6)
                HStack(alignment: .top, spacing: 8) {
                    NexlyGlassGlyphTile(
                        name: "bulb",
                        tone: .amber,
                        size: 24,
                        glyphSize: 14
                    )
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Key Insights:")
                            .font(.system(size: 11.5, weight: .bold))
                            .foregroundStyle(Color(red: 0.129, green: 0.094, blue: 0.271))
                        Text(String(format: "Peak in %@ (%@) · %@%.1f%% overall change across %@", peakPt.label, compactNumber(peakPt.value, unit: unit), pos ? "+" : "", growthPct, rangeLabel.lowercased()))
                            .font(.system(size: 11))
                            .foregroundStyle(Color(red: 0.224, green: 0.196, blue: 0.361))
                    }
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 11)
                .padding(.vertical, 9)
                .background(RoundedRectangle(cornerRadius: 12).fill(Color(red: 0.949, green: 0.925, blue: 1.0)))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(red: 0.851, green: 0.800, blue: 1.0), lineWidth: 1))
            }
        }
    }
}

private struct StatBodyView: View {
    let obj: [String: Any]
    let accent: Color
    @State private var selectedIdx: Int = 0

    var body: some View {
        let cards = ((obj["cards"] ?? obj["items"] ?? obj["metrics"] ?? obj["kpis"] ?? obj["stats"]) as? [[String: Any]]) ?? []
        VStack(alignment: .leading, spacing: 8) {
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                ForEach(Array(cards.prefix(8).enumerated()), id: \.offset) { idx, card in
                    let isSelected = selectedIdx == idx
                    let label = str(card, "label", "title", "name", "metric")
                    let value = str(card, "value", "val", "number", "amount")
                    let delta = str(card, "delta", "change", "trend", "sub")
                    let isNeg = delta.contains("-") || delta.hasPrefix("↓") || delta.hasPrefix("↘")
                    Button {
                        GSHaptics.select()
                        selectedIdx = idx
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(label)
                                .font(.system(size: 11, weight: isSelected ? .bold : .medium))
                                .foregroundStyle(isSelected ? Color(red: 0.357, green: 0.239, blue: 0.961) : Aero.textSecondary)
                                .lineLimit(1)
                            Text(value)
                                .font(.system(size: 17, weight: .bold, design: .rounded))
                                .foregroundStyle(Color(red: 0.122, green: 0.094, blue: 0.259))
                                .lineLimit(1)
                            if !delta.isEmpty {
                                let cleanDelta = delta.replacingOccurrences(of: "^[↑↓↗↘\\s]+", with: "", options: .regularExpression)
                                Text("\(isNeg ? "↘" : "↗") \(cleanDelta)")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundStyle(isNeg ? Color(red: 0.878, green: 0.310, blue: 0.373) : Color(red: 0.071, green: 0.604, blue: 0.353))
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(
                                        Capsule().fill(
                                            isNeg
                                                ? Color(red: 0.992, green: 0.925, blue: 0.925)
                                                : Color(red: 0.902, green: 0.976, blue: 0.941)
                                        )
                                    )
                                    .lineLimit(1)
                            }
                        }
                        .padding(10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(
                            (isSelected ? Color(red: 0.953, green: 0.933, blue: 1.0) : Color.white.opacity(0.82)),
                            in: RoundedRectangle(cornerRadius: 12)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 12).stroke(
                                isSelected ? Color(red: 0.424, green: 0.298, blue: 0.965) : Color(red: 0.886, green: 0.859, blue: 0.980),
                                lineWidth: isSelected ? 1.5 : 1
                            )
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            if selectedIdx < cards.count {
                let selCard = cards[selectedIdx]
                let selLabel = str(selCard, "label", "title", "name", "metric")
                let selValue = str(selCard, "value", "val", "number", "amount")
                let selDelta = str(selCard, "delta", "change", "trend", "sub", "note")
                HStack(spacing: 8) {
                    NexlyGlassGlyphTile(name: "chart-bar", tone: .lavender, size: 22, glyphSize: 13)
                    Text("\(selLabel): \(selValue)")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Aero.text)
                        .lineLimit(1)
                    Spacer()
                    if !selDelta.isEmpty {
                        Text(selDelta)
                            .font(Aero.mono(11))
                            .foregroundStyle(Color(red: 0.357, green: 0.239, blue: 0.961))
                            .lineLimit(1)
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 7)
                .background(Color(red: 0.961, green: 0.945, blue: 1.0), in: RoundedRectangle(cornerRadius: 10))
            }
        }
    }
}

private func iosTableEntityMonogram(_ raw: String) -> (String, Color, Color)? {
    let clean = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !clean.isEmpty, clean.count <= 28, clean.contains(where: { $0.isLetter }) else { return nil }
    let lower = clean.lowercased()
    if lower.hasPrefix("python") { return ("Py", Color(red: 0.878, green: 0.949, blue: 0.996), Color(red: 0.008, green: 0.518, blue: 0.780)) }
    if lower == "java" { return ("Jv", Color(red: 1.0, green: 0.929, blue: 0.835), Color(red: 0.918, green: 0.345, blue: 0.047)) }
    if lower.hasPrefix("c++") { return ("C+", Color(red: 0.859, green: 0.918, blue: 0.996), Color(red: 0.145, green: 0.388, blue: 0.922)) }
    if lower == "c" || lower == "c#" { return (clean, Color(red: 0.878, green: 0.906, blue: 1.0), Color(red: 0.310, green: 0.275, blue: 0.898)) }
    if lower.hasPrefix("javascript") { return ("JS", Color(red: 0.996, green: 0.953, blue: 0.780), Color(red: 0.851, green: 0.467, blue: 0.024)) }
    if lower.hasPrefix("typescript") { return ("TS", Color(red: 0.859, green: 0.918, blue: 0.996), Color(red: 0.114, green: 0.306, blue: 0.847)) }
    if lower.hasPrefix("rust") { return ("Rs", Color(red: 0.996, green: 0.886, blue: 0.886), Color(red: 0.863, green: 0.149, blue: 0.149)) }
    let parts = clean.components(separatedBy: .whitespaces).filter { !$0.isEmpty }
    let code = parts.count >= 2 ? "\(parts[0].prefix(1))\(parts[1].prefix(1))".uppercased() : String(clean.prefix(2)).uppercased()
    return (code, Color(red: 0.929, green: 0.898, blue: 1.0), Color(red: 0.357, green: 0.239, blue: 0.961))
}

private struct TableBodyView: View {
    let obj: [String: Any]
    let accent: Color

    @State private var query = ""
    @State private var sortCol: Int? = nil
    @State private var sortAsc = true
    @State private var pageSize: Int = 0
    @State private var page: Int = 0

    var body: some View {
        let cols: [String] = ((obj["columns"] ?? obj["headers"]) as? [Any])?.map {
            if let s = $0 as? String { return s }
            if let d = $0 as? [String: Any] { return str(d, "label", "title", "name", "key") }
            return "\($0)"
        } ?? []
        let rawRows = ((obj["rows"] ?? obj["data"]) as? [Any]) ?? []
        let normRows: [[String]] = rawRows.map { row in
            if let arr = row as? [Any] { return arr.map { "\($0)" } }
            if let dict = row as? [String: Any] { return cols.map { str(dict, $0, $0.lowercased()) } }
            return []
        }
        let filteredRows: [[String]] = {
            let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            let base = q.isEmpty ? normRows : normRows.filter { r in r.contains { $0.lowercased().contains(q) } }
            guard let sc = sortCol else { return base }
            return base.sorted { a, b in
                let av = sc < a.count ? a[sc] : ""
                let bv = sc < b.count ? b[sc] : ""
                let an = Double(av.replacingOccurrences(of: "[^0-9.-]", with: "", options: .regularExpression))
                let bn = Double(bv.replacingOccurrences(of: "[^0-9.-]", with: "", options: .regularExpression))
                let cmp: Bool = (an != nil && bn != nil) ? (an! < bn!) : (av < bv)
                return sortAsc ? cmp : !cmp
            }
        }()

        let effPageSize = pageSize > 0 ? pageSize : ((4...5).contains(normRows.count) ? 3 : 5)
        let totalPages = max(1, (filteredRows.count + effPageSize - 1) / effPageSize)
        let safePage = min(max(0, page), totalPages - 1)
        let startIdx = safePage * effPageSize
        let visibleRows: [[String]] = startIdx < filteredRows.count
            ? Array(filteredRows[startIdx..<min(filteredRows.count, startIdx + effPageSize)])
            : []
        let startRow = filteredRows.isEmpty ? 0 : startIdx + 1
        let endRow = min(filteredRows.count, startIdx + visibleRows.count)
        let primaryNameCol: Int = {
            if let idx = cols.firstIndex(where: {
                let l = $0.lowercased()
                return l.contains("language") || l.contains("product") || l.contains("name") || l.contains("framework") || l.contains("model") || l.contains("company")
            }) {
                return idx
            }
            if cols.first?.trimmingCharacters(in: .whitespaces) == "#" || cols.first?.lowercased().contains("rank") == true {
                return 1
            }
            return 0
        }()

        VStack(alignment: .leading, spacing: 8) {
            if normRows.count >= 4 {
                TextField("Filter rows…", text: $query)
                    .font(.system(size: 12))
                    .padding(7)
                    .background(Color.white.opacity(0.6), in: RoundedRectangle(cornerRadius: 8))
            }

            VStack(alignment: .leading, spacing: 0) {
                if !cols.isEmpty {
                    HStack(spacing: 8) {
                        ForEach(Array(cols.prefix(6).enumerated()), id: \.offset) { ci, col in
                            Button {
                                if sortCol == ci { sortAsc.toggle() } else { sortCol = ci; sortAsc = true }
                            } label: {
                                Text(sortCol == ci ? "\(col) \(sortAsc ? "↑" : "↓")" : col)
                                    .font(.system(size: 11.5, weight: .bold))
                                    .foregroundStyle(Color(red: 0.133, green: 0.102, blue: 0.278))
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 8)
                    .background(Color(red: 0.929, green: 0.902, blue: 1.0))

                    Divider()
                }

                ForEach(Array(visibleRows.enumerated()), id: \.offset) { rIdx, row in
                    HStack(spacing: 8) {
                        ForEach(Array(row.prefix(6).enumerated()), id: \.offset) { ci, cell in
                            let trimmed = cell.trimmingCharacters(in: .whitespacesAndNewlines)
                            let isTrend = trimmed.range(of: "^[↑↓↗↘+-]?\\s*[+-]?\\d+(?:\\.\\d+)?%$", options: .regularExpression) != nil &&
                                (trimmed.hasPrefix("+") || trimmed.hasPrefix("-") || trimmed.hasPrefix("↑") || trimmed.hasPrefix("↓") || (ci < cols.count && cols[ci].lowercased().contains("trend")))
                            let isNeg = isTrend && (trimmed.contains("-") || trimmed.hasPrefix("↓") || trimmed.hasPrefix("↘"))

                            if ci == primaryNameCol, let badge = iosTableEntityMonogram(trimmed) {
                                HStack(spacing: 6) {
                                    Text(badge.0)
                                        .font(.system(size: 10, weight: .bold))
                                        .foregroundStyle(badge.2)
                                        .frame(width: 21, height: 21)
                                        .background(RoundedRectangle(cornerRadius: 6).fill(badge.1))
                                    Text(trimmed)
                                        .font(.system(size: 12, weight: .semibold))
                                        .foregroundStyle(Aero.text)
                                        .lineLimit(1)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                            } else if isTrend {
                                let cleanNum = trimmed.replacingOccurrences(of: "^[↑↓↗↘\\s]+", with: "", options: .regularExpression)
                                Text("\(isNeg ? "↓" : "↑") \(cleanNum)")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundStyle(isNeg ? Color(red: 0.878, green: 0.310, blue: 0.373) : Color(red: 0.071, green: 0.604, blue: 0.353))
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            } else {
                                Text(trimmed)
                                    .font(.system(size: 12, weight: ci == 0 ? .semibold : .regular))
                                    .foregroundStyle(Aero.text)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 7)

                    if rIdx != visibleRows.count - 1 {
                        Divider().opacity(0.45)
                    }
                }

                // Pagination & Display Controls Bar (Screenshot 5)
                HStack {
                    HStack(spacing: 4) {
                        Button("‹") {
                            page = max(0, safePage - 1)
                        }
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(safePage > 0 ? Aero.textSecondary : Aero.textTertiary.opacity(0.45))
                        .disabled(safePage == 0)
                        .buttonStyle(.plain)

                        ForEach(0..<totalPages, id: \.self) { p in
                            let active = p == safePage
                            Button {
                                page = p
                            } label: {
                                Text("\(p + 1)")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundStyle(active ? Color.white : Aero.textSecondary)
                                    .frame(width: 22, height: 22)
                                    .background(Circle().fill(active ? Color(red: 0.424, green: 0.298, blue: 0.965) : Color.clear))
                            }
                            .buttonStyle(.plain)
                        }

                        Button("›") {
                            page = min(totalPages - 1, safePage + 1)
                        }
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(safePage < totalPages - 1 ? Aero.textSecondary : Aero.textTertiary.opacity(0.45))
                        .disabled(safePage >= totalPages - 1)
                        .buttonStyle(.plain)

                        Text("\(startRow)–\(endRow) of \(filteredRows.count)")
                            .font(.system(size: 10.5))
                            .foregroundStyle(Aero.textSecondary)
                            .padding(.leading, 4)
                    }

                    Spacer()

                    Button {
                        switch effPageSize {
                        case 3: pageSize = 5
                        case 5: pageSize = 10
                        case 10: pageSize = 25
                        default: pageSize = 3
                        }
                        page = 0
                    } label: {
                        Text("Show: \(effPageSize) ▾")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(Color(red: 0.243, green: 0.212, blue: 0.400))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(RoundedRectangle(cornerRadius: 7).fill(Color.white.opacity(0.85)))
                            .overlay(RoundedRectangle(cornerRadius: 7).stroke(Color(red: 0.875, green: 0.843, blue: 0.980), lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Color(red: 0.965, green: 0.949, blue: 1.0))
            }
            .background(RoundedRectangle(cornerRadius: 12).fill(Color.white.opacity(0.75)))
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(red: 0.886, green: 0.859, blue: 0.980), lineWidth: 1))

            if filteredRows.count >= 2, let firstRow = filteredRows.first, primaryNameCol < firstRow.count {
                let topName = firstRow[primaryNameCol].trimmingCharacters(in: .whitespacesAndNewlines)
                let topMetric = (primaryNameCol + 1 < firstRow.count) ? firstRow[primaryNameCol + 1].trimmingCharacters(in: .whitespacesAndNewlines) : ""
                let secondName = (filteredRows.count > 1 && primaryNameCol < filteredRows[1].count) ? filteredRows[1][primaryNameCol].trimmingCharacters(in: .whitespacesAndNewlines) : ""
                let secondMetric = (filteredRows.count > 1 && primaryNameCol + 1 < filteredRows[1].count) ? filteredRows[1][primaryNameCol + 1].trimmingCharacters(in: .whitespacesAndNewlines) : ""
                if !topName.isEmpty {
                    HStack(alignment: .top, spacing: 8) {
                        NexlyGlassGlyphTile(
                            name: "bulb",
                            tone: .amber,
                            size: 24,
                            glyphSize: 14
                        )
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Key Insights")
                                .font(.system(size: 11.5, weight: .bold))
                                .foregroundStyle(Color(red: 0.129, green: 0.094, blue: 0.271))
                            Text("• \(topName) leads\(topMetric.isEmpty ? "" : " (\(topMetric))")\(secondName.isEmpty ? "" : " followed by \(secondName)\(secondMetric.isEmpty ? "" : " (\(secondMetric))")") across \(filteredRows.count) entries")
                                .font(.system(size: 11))
                                .foregroundStyle(Color(red: 0.224, green: 0.196, blue: 0.361))
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 11)
                    .padding(.vertical, 9)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color(red: 0.949, green: 0.925, blue: 1.0)))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(red: 0.851, green: 0.800, blue: 1.0), lineWidth: 1))
                }
            }
        }
    }
}

private struct CompareBodyView: View {
    @Environment(\.gsWidgetAction) private var widgetAction
    let obj: [String: Any]
    @State private var selectedSide: Int = 0

    var body: some View {
        let left = (obj["left"] ?? obj["sideA"] ?? obj["a"]) as? [String: Any] ?? [:]
        let right = (obj["right"] ?? obj["sideB"] ?? obj["b"]) as? [String: Any] ?? [:]
        let leftName = str(left, "name", "title", "label")
        let rightName = str(right, "name", "title", "label")
        let activeName = selectedSide == 0 ? (leftName.isEmpty ? "Option A" : leftName) : (rightName.isEmpty ? "Option B" : rightName)
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 10) {
                compareCol(left, sideIdx: 0, color: WidgetPalette.at(0))
                compareCol(right, sideIdx: 1, color: WidgetPalette.at(3))
            }
            HStack {
                Text("Selected: \(activeName)")
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(Aero.textSecondary)
                Spacer()
                Button("Explore \(activeName) →") {
                    GSHaptics.select()
                    widgetAction?("Deep dive into \(activeName)")
                }
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(Color(red: 0.357, green: 0.239, blue: 0.961))
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(Color(red: 0.933, green: 0.910, blue: 1.0), in: Capsule())
                .buttonStyle(.plain)
            }
        }
    }

    private func compareCol(_ side: [String: Any], sideIdx: Int, color: Color) -> some View {
        let isSelected = selectedSide == sideIdx
        let name = str(side, "name", "title", "label")
        let summary = str(side, "summary", "subtitle", "description")
        let rows = (side["rows"] ?? side["items"] ?? side["points"]) as? [[String: Any]] ?? []
        return Button {
            GSHaptics.select()
            selectedSide = sideIdx
        } label: {
            VStack(alignment: .leading, spacing: 5) {
                HStack {
                    Text(name).font(.system(size: 13, weight: .bold)).foregroundStyle(color)
                    Spacer()
                    if isSelected {
                        NexlyGlyph(name: "circle-check", size: 13, color: color)
                    }
                }
                if !summary.isEmpty {
                    Text(summary).font(.system(size: 11)).foregroundStyle(Aero.textSecondary)
                }
                ForEach(Array(rows.prefix(8).enumerated()), id: \.offset) { _, r in
                    HStack {
                        Text(str(r, "k", "label", "key", "name"))
                            .font(.system(size: 11))
                            .foregroundStyle(Aero.textSecondary)
                        Spacer()
                        Text(str(r, "v", "value", "val"))
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(Aero.text)
                    }
                }
            }
            .padding(10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                (isSelected ? Color(red: 0.953, green: 0.933, blue: 1.0) : Color.white.opacity(0.55)),
                in: RoundedRectangle(cornerRadius: 12)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12).stroke(isSelected ? color : Color.clear, lineWidth: 1.5)
            )
        }
        .buttonStyle(.plain)
    }
}

private struct ProgressBodyView: View {
    let obj: [String: Any]
    let accent: Color
    @State private var overrides: [Int: Double] = [:]

    var body: some View {
        let items: [[String: Any]] = {
            if let arr = (obj["items"] ?? obj["bars"] ?? obj["metrics"]) as? [[String: Any]] { return arr }
            if obj["value"] != nil { return [obj] }
            return []
        }()
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(items.prefix(10).enumerated()), id: \.offset) { idx, item in
                let label = str(item, "label", "name", "title")
                let baseVal = dbl(item, "value", "percent", "score")
                let maxV = max(dbl(item, "max", "total", "target", fallback: 100), 1)
                let curVal = overrides[idx] ?? baseVal
                let pct = min(max(curVal / maxV, 0), 1)
                Button {
                    GSHaptics.select()
                    let step = maxV * 0.1
                    let next = curVal + step
                    overrides[idx] = next > maxV ? 0.0 : next
                } label: {
                    VStack(alignment: .leading, spacing: 3) {
                        HStack {
                            Text(label).font(.system(size: 12, weight: .medium)).foregroundStyle(Aero.text)
                            Spacer()
                            Text("\(Int(round(pct * 100)))%").font(Aero.mono(11)).foregroundStyle(Aero.textSecondary)
                        }
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Capsule().fill(Color.black.opacity(0.06))
                                Capsule().fill(WidgetPalette.at(idx)).frame(width: max(6, geo.size.width * CGFloat(pct)))
                            }
                        }
                        .frame(height: 8)
                    }
                }
                .buttonStyle(.plain)
            }
        }
    }
}

private struct TimelineBodyView: View {
    let obj: [String: Any]
    let accent: Color
    @State private var selectedIdx: Int = 0

    var body: some View {
        let entries = ((obj["entries"] ?? obj["events"] ?? obj["milestones"] ?? obj["items"]) as? [[String: Any]]) ?? []
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(entries.prefix(12).enumerated()), id: \.offset) { idx, entry in
                let isSelected = selectedIdx == idx
                Button {
                    GSHaptics.select()
                    selectedIdx = idx
                } label: {
                    HStack(alignment: .top, spacing: 10) {
                        Circle()
                            .fill(isSelected ? Color(red: 0.424, green: 0.298, blue: 0.965) : accent)
                            .frame(width: isSelected ? 10 : 8, height: isSelected ? 10 : 8)
                            .padding(.top, 5)
                        VStack(alignment: .leading, spacing: 2) {
                            let date = str(entry, "date", "time", "year", "when", "phase")
                            if !date.isEmpty {
                                Text(date).font(.system(size: 11, weight: .bold)).foregroundStyle(accent)
                            }
                            Text(str(entry, "title", "label", "event", "name"))
                                .font(.system(size: 13, weight: isSelected ? .bold : .semibold))
                                .foregroundStyle(Aero.text)
                            let detail = str(entry, "detail", "description", "body", "text")
                            if !detail.isEmpty {
                                Text(detail).font(.system(size: 12)).foregroundStyle(Aero.textSecondary)
                            }
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(isSelected ? 8 : 2)
                    .background(
                        (isSelected ? Color(red: 0.957, green: 0.937, blue: 1.0) : Color.clear),
                        in: RoundedRectangle(cornerRadius: 10)
                    )
                }
                .buttonStyle(.plain)
            }
        }
    }
}

private struct QuizBodyView: View {
    let obj: [String: Any]
    let accent: Color
    @State private var picked: [Int: Int] = [:]

    var body: some View {
        let questions: [[String: Any]] = {
            if let qs = (obj["questions"] ?? obj["items"]) as? [[String: Any]] { return qs }
            if obj["question"] != nil || obj["q"] != nil { return [obj] }
            return []
        }()
        VStack(alignment: .leading, spacing: 12) {
            ForEach(Array(questions.prefix(6).enumerated()), id: \.offset) { qIdx, q in
                let prompt = str(q, "q", "question", "prompt", "title")
                let opts = ((q["options"] ?? q["choices"] ?? q["answers"]) as? [Any])?.map { "\($0)" } ?? []
                let ans = Int(dbl(q, "answer", "correct", "correctIndex", fallback: 0))
                VStack(alignment: .leading, spacing: 6) {
                    Text(prompt).font(.system(size: 13, weight: .semibold)).foregroundStyle(Aero.text)
                    ForEach(Array(opts.enumerated()), id: \.offset) { oIdx, opt in
                        let sel = picked[qIdx] == oIdx
                        let isRight = oIdx == ans
                        Button {
                            picked[qIdx] = oIdx
                        } label: {
                            HStack {
                                Text(opt).font(.system(size: 12)).foregroundStyle(Aero.text)
                                Spacer()
                                if sel {
                                    Text(isRight ? "✓" : "✕")
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundStyle(isRight ? Color.green : Color.red)
                                }
                            }
                            .padding(8)
                            .background(
                                (sel ? (isRight ? Color.green.opacity(0.16) : Color.red.opacity(0.16)) : Color.white.opacity(0.55)),
                                in: RoundedRectangle(cornerRadius: 10)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }
}

private struct TabsBodyView: View {
    let obj: [String: Any]
    let accent: Color
    @State private var active = 0

    var body: some View {
        let tabs = ((obj["tabs"] ?? obj["items"] ?? obj["sections"]) as? [[String: Any]]) ?? []
        let safeIdx = min(max(active, 0), max(tabs.count - 1, 0))
        VStack(alignment: .leading, spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(Array(tabs.enumerated()), id: \.offset) { idx, t in
                        let label = str(t, "label", "title", "name")
                        Button {
                            active = idx
                        } label: {
                            Text(label)
                                .font(.system(size: 12, weight: idx == safeIdx ? .bold : .medium))
                                .foregroundStyle(idx == safeIdx ? accent : Aero.textSecondary)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .background(idx == safeIdx ? accent.opacity(0.15) : Color.white.opacity(0.45), in: Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            if !tabs.isEmpty {
                let current = tabs[safeIdx]
                let bodyText = str(current, "body", "content", "text", "description")
                if !bodyText.isEmpty {
                    Text(bodyText).font(.system(size: 13)).foregroundStyle(Aero.text)
                }
                if let items = (current["items"] ?? current["bullets"]) as? [Any] {
                    ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                        Text("• \(item)").font(.system(size: 12)).foregroundStyle(Aero.text)
                    }
                }
            }
        }
    }
}

private struct AccordionBodyView: View {
    let obj: [String: Any]
    let accent: Color
    @State private var openIndex: Int? = 0

    var body: some View {
        let sections = ((obj["sections"] ?? obj["items"] ?? obj["faqs"]) as? [[String: Any]]) ?? []
        VStack(alignment: .leading, spacing: 6) {
            ForEach(Array(sections.enumerated()), id: \.offset) { idx, sec in
                let label = str(sec, "label", "title", "question", "q")
                let detail = str(sec, "body", "content", "answer", "a", "text")
                let isOpen = openIndex == idx
                VStack(alignment: .leading, spacing: 4) {
                    Button {
                        openIndex = isOpen ? nil : idx
                    } label: {
                        HStack {
                            Text(label).font(.system(size: 13, weight: .semibold)).foregroundStyle(Aero.text)
                            Spacer()
                            Text(isOpen ? "▾" : "▸").foregroundStyle(accent)
                        }
                    }
                    .buttonStyle(.plain)
                    if isOpen && !detail.isEmpty {
                        Text(detail).font(.system(size: 12)).foregroundStyle(Aero.textSecondary)
                    }
                }
                .padding(9)
                .background(Color.white.opacity(0.55), in: RoundedRectangle(cornerRadius: 10))
            }
        }
    }
}

private struct StepsBodyView: View {
    let obj: [String: Any]
    let accent: Color
    @State private var completed: Set<Int> = []

    var body: some View {
        let steps = ((obj["steps"] ?? obj["items"] ?? obj["stages"]) as? [[String: Any]]) ?? []
        let doneCount = completed.count
        VStack(alignment: .leading, spacing: 8) {
            if steps.count > 1 {
                HStack {
                    Text("\(doneCount) of \(steps.count) completed")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(Aero.textSecondary)
                    Spacer()
                    Text("\(Int(round(Double(doneCount) / Double(max(1, steps.count)) * 100.0)))%")
                        .font(Aero.mono(11))
                        .foregroundStyle(accent)
                }
            }
            ForEach(Array(steps.enumerated()), id: \.offset) { idx, step in
                let isDone = completed.contains(idx)
                Button {
                    GSHaptics.select()
                    if isDone { completed.remove(idx) } else { completed.insert(idx) }
                } label: {
                    HStack(alignment: .top, spacing: 10) {
                        ZStack {
                            Circle()
                                .fill(isDone ? Color(red: 0.863, green: 0.988, blue: 0.906) : accent.opacity(0.16))
                                .frame(width: 22, height: 22)
                            if isDone {
                                NexlyGlyph(name: "check", size: 11, color: Color(red: 0.082, green: 0.502, blue: 0.239))
                            } else {
                                Text("\(idx + 1)")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundStyle(accent)
                            }
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            Text(str(step, "title", "label", "name", "step"))
                                .font(.system(size: 13, weight: .semibold))
                                .strikethrough(isDone)
                                .foregroundStyle(isDone ? Aero.textSecondary : Aero.text)
                            let detail = str(step, "detail", "description", "body", "text")
                            if !detail.isEmpty {
                                Text(detail)
                                    .font(.system(size: 12))
                                    .foregroundStyle(Aero.textSecondary)
                                    .opacity(isDone ? 0.7 : 1.0)
                            }
                        }
                        Spacer(minLength: 0)
                    }
                }
                .buttonStyle(.plain)
            }
        }
    }
}

private struct PollBodyView: View {
    @Environment(\.gsWidgetAction) private var widgetAction
    let obj: [String: Any]
    let accent: Color
    @State private var voted: Int? = nil

    private var parsedOptions: [(label: String, votes: Int)] {
        let raw = ((obj["options"] ?? obj["choices"] ?? obj["items"]) as? [Any]) ?? []
        return raw.map { opt in
            if let plain = opt as? String { return (NexlyIcons.cleanPlainText(plain), 1) }
            let dict = opt as? [String: Any] ?? [:]
            return (
                NexlyIcons.cleanPlainText(str(dict, "label", "title", "name", "text")),
                Int(dbl(dict, "votes", "count", "value", fallback: 1))
            )
        }
    }

    var body: some View {
        let opts = parsedOptions
        let totalBase = opts.reduce(0) { $0 + $1.votes } + (voted != nil ? 1 : 0)
        VStack(alignment: .leading, spacing: 6) {
            ForEach(Array(opts.enumerated()), id: \.offset) { idx, opt in
                let votes = opt.votes + (voted == idx ? 1 : 0)
                let pct = totalBase > 0 ? Int(round(Double(votes) / Double(totalBase) * 100.0)) : 0
                Button {
                    GSHaptics.select()
                    voted = idx
                    widgetAction?(opt.label)
                } label: {
                    HStack {
                        Text(opt.label).font(.system(size: 12, weight: voted == idx ? .bold : .regular)).foregroundStyle(Aero.text)
                        Spacer()
                        Text("\(votes) (\(pct)%)").font(Aero.mono(11)).foregroundStyle(accent)
                    }
                    .padding(9)
                    .background(voted == idx ? accent.opacity(0.16) : Color.white.opacity(0.55), in: RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
            }
        }
    }
}

private struct RatingBodyView: View {
    @Environment(\.gsWidgetAction) private var widgetAction
    let obj: [String: Any]
    let accent: Color
    @State private var chosen: Int? = nil

    var body: some View {
        let maxR = min(max(Int(dbl(obj, "max", fallback: 5)), 2), 10)
        let prompt = NexlyIcons.cleanPlainText(str(obj, "prompt", "question"))
        VStack(alignment: .leading, spacing: 8) {
            if !prompt.isEmpty {
                Text(prompt).font(.system(size: 12)).foregroundStyle(Aero.textSecondary)
            }
            HStack(spacing: 6) {
                ForEach(1...maxR, id: \.self) { n in
                    let active = (chosen ?? 0) >= n
                    Button {
                        GSHaptics.select()
                        chosen = n
                        widgetAction?(prompt.isEmpty ? "Rated \(n)/\(maxR)" : "\(prompt): \(n)/\(maxR)")
                    } label: {
                        NexlyGlyph(
                            name: "star",
                            size: 18,
                            color: active ? accent : Aero.textSecondary.opacity(0.35)
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}

private struct DiffBodyView: View {
    let obj: [String: Any]

    var body: some View {
        let unified = str(obj, "unified", "patch", "diff")
        let before = str(obj, "before", "old", "original", "left")
        let after = str(obj, "after", "new", "modified", "right")
        VStack(alignment: .leading, spacing: 3) {
            if !unified.isEmpty {
                ForEach(Array(unified.components(separatedBy: "\n").prefix(30).enumerated()), id: \.offset) { _, line in
                    Text(line)
                        .font(Aero.mono(11))
                        .foregroundStyle(line.hasPrefix("+") ? Color.green : (line.hasPrefix("-") ? Color.red : Aero.text))
                }
            } else {
                ForEach(Array(before.components(separatedBy: "\n").prefix(15).enumerated()), id: \.offset) { _, line in
                    Text("- \(line)").font(Aero.mono(11)).foregroundStyle(Color.red)
                }
                ForEach(Array(after.components(separatedBy: "\n").prefix(15).enumerated()), id: \.offset) { _, line in
                    Text("+ \(line)").font(Aero.mono(11)).foregroundStyle(Color.green)
                }
            }
        }
        .padding(8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white.opacity(0.55), in: RoundedRectangle(cornerRadius: 10))
    }
}

private struct TreeBodyView: View {
    let obj: [String: Any]
    let accent: Color
    @State private var collapsed: Set<String> = []

    var body: some View {
        let roots = ((obj["roots"] ?? obj["tree"] ?? obj["nodes"] ?? obj["items"]) as? [[String: Any]]) ?? []
        VStack(alignment: .leading, spacing: 4) {
            ForEach(Array(roots.enumerated()), id: \.offset) { idx, node in
                treeLines(node, depth: 0, path: "r\(idx)")
            }
        }
    }

    private func treeLines(_ node: [String: Any], depth: Int, path: String) -> AnyView {
        let label = NexlyIcons.cleanPlainText(str(node, "label", "name", "title"))
        let meta = str(node, "meta", "badge", "type")
        let kids = ((node["children"] ?? node["items"] ?? node["nodes"]) as? [[String: Any]]) ?? []
        let isCollapsed = collapsed.contains(path)
        return AnyView(
            VStack(alignment: .leading, spacing: 3) {
                Button {
                    guard !kids.isEmpty else { return }
                    GSHaptics.select()
                    if isCollapsed { collapsed.remove(path) } else { collapsed.insert(path) }
                } label: {
                    HStack(spacing: 6) {
                        Text(kids.isEmpty ? "•" : (isCollapsed ? "▸" : "▾")).foregroundStyle(accent)
                        Text(label).font(.system(size: 12, weight: kids.isEmpty ? .regular : .semibold)).foregroundStyle(Aero.text)
                        if !meta.isEmpty {
                            Spacer()
                            Text(meta).font(Aero.mono(10)).foregroundStyle(Aero.textSecondary)
                        }
                    }
                    .padding(.leading, CGFloat(depth * 14))
                }
                .buttonStyle(.plain)
                if depth < 4 && !isCollapsed {
                    ForEach(Array(kids.enumerated()), id: \.offset) { cIdx, child in
                        treeLines(child, depth: depth + 1, path: "\(path).\(cIdx)")
                    }
                }
            }
        )
    }
}

private struct FlashcardsBodyView: View {
    let obj: [String: Any]
    let accent: Color
    @State private var index = 0
    @State private var flipped = false

    var body: some View {
        let cards = ((obj["cards"] ?? obj["items"] ?? obj["deck"]) as? [[String: Any]]) ?? []
        if !cards.isEmpty {
            let safeIdx = min(max(index, 0), cards.count - 1)
            let card = cards[safeIdx]
            let front = NexlyIcons.cleanPlainText(str(card, "front", "question", "q", "term", "prompt"))
            let back = NexlyIcons.cleanPlainText(str(card, "back", "answer", "a", "definition"))
            VStack(alignment: .leading, spacing: 8) {
                Button {
                    GSHaptics.select()
                    flipped.toggle()
                } label: {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(flipped ? "ANSWER" : "QUESTION")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(accent)
                        Text(flipped ? back : front)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(Aero.text)
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, minHeight: 80, alignment: .leading)
                    .background(accent.opacity(flipped ? 0.14 : 0.07), in: RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
                if cards.count > 1 {
                    HStack {
                        Button("← Prev") { index = (safeIdx - 1 + cards.count) % cards.count; flipped = false }
                            .font(.system(size: 11, weight: .semibold)).foregroundStyle(accent)
                        Spacer()
                        Text("\(safeIdx + 1) / \(cards.count)").font(Aero.mono(11)).foregroundStyle(Aero.textSecondary)
                        Spacer()
                        Button("Next →") { index = (safeIdx + 1) % cards.count; flipped = false }
                            .font(.system(size: 11, weight: .semibold)).foregroundStyle(accent)
                    }
                }
            }
        }
    }
}

private struct ChecklistBodyView: View {
    let obj: [String: Any]
    let accent: Color
    @State private var toggled: Set<Int> = []
    @State private var addedTasks: [(text: String, done: Bool)] = []
    @State private var addingTask = false
    @State private var newTaskText = ""

    var body: some View {
        let items = ((obj["items"] ?? obj["tasks"] ?? obj["todos"]) as? [Any]) ?? []
        VStack(alignment: .leading, spacing: 6) {
            ForEach(Array(items.enumerated()), id: \.offset) { idx, raw in
                let dict = raw as? [String: Any] ?? [:]
                let label = NexlyIcons.cleanPlainText((raw as? String) ?? str(dict, "label", "title", "text", "task", "name"))
                let initialDone = (dict["done"] as? Bool) ?? (dict["checked"] as? Bool) ?? false
                let isDone = toggled.contains(idx) ? !initialDone : initialDone
                Button {
                    GSHaptics.select()
                    if toggled.contains(idx) { toggled.remove(idx) } else { toggled.insert(idx) }
                } label: {
                    HStack(spacing: 9) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 5)
                                .fill(isDone ? Color(red: 0.424, green: 0.298, blue: 0.965) : Color.white.opacity(0.85))
                                .frame(width: 18, height: 18)
                            if !isDone {
                                RoundedRectangle(cornerRadius: 5)
                                    .stroke(Color(red: 0.612, green: 0.533, blue: 0.973), lineWidth: 1.5)
                                    .frame(width: 18, height: 18)
                            } else {
                                NexlyGlyph(name: "check", size: 11, color: .white)
                            }
                        }
                        Text(label)
                            .font(.system(size: 13))
                            .strikethrough(isDone)
                            .foregroundStyle(isDone ? Aero.textSecondary : Aero.text)
                    }
                    .padding(.vertical, 2)
                }
                .buttonStyle(.plain)
            }
            ForEach(Array(addedTasks.enumerated()), id: \.offset) { aIdx, task in
                HStack(spacing: 9) {
                    Button {
                        GSHaptics.select()
                        addedTasks[aIdx].done.toggle()
                    } label: {
                        HStack(spacing: 9) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 5)
                                    .fill(task.done ? Color(red: 0.424, green: 0.298, blue: 0.965) : Color.white.opacity(0.85))
                                    .frame(width: 18, height: 18)
                                if !task.done {
                                    RoundedRectangle(cornerRadius: 5)
                                        .stroke(Color(red: 0.612, green: 0.533, blue: 0.973), lineWidth: 1.5)
                                        .frame(width: 18, height: 18)
                                } else {
                                    NexlyGlyph(name: "check", size: 11, color: .white)
                                }
                            }
                            Text(task.text)
                                .font(.system(size: 13))
                                .strikethrough(task.done)
                                .foregroundStyle(task.done ? Aero.textSecondary : Aero.text)
                        }
                    }
                    .buttonStyle(.plain)
                    Spacer()
                    Button {
                        addedTasks.remove(at: aIdx)
                    } label: {
                        NexlyGlyph(name: "x", size: 12, color: Aero.textTertiary)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.vertical, 2)
            }
            if addingTask {
                HStack(spacing: 6) {
                    TextField("Type a new task…", text: $newTaskText)
                        .font(.system(size: 12.5))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(RoundedRectangle(cornerRadius: 8).fill(Color.white.opacity(0.92)))
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color(red: 0.847, green: 0.808, blue: 0.976), lineWidth: 1))
                    Button("Add") {
                        let trimmed = newTaskText.trimmingCharacters(in: .whitespacesAndNewlines)
                        if !trimmed.isEmpty {
                            addedTasks.append((trimmed, false))
                            newTaskText = ""
                            addingTask = false
                        }
                    }
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(RoundedRectangle(cornerRadius: 8).fill(Color(red: 0.424, green: 0.298, blue: 0.965)))
                    .buttonStyle(.plain)

                    Button {
                        newTaskText = ""
                        addingTask = false
                    } label: {
                        NexlyGlyph(name: "x", size: 13, color: Aero.textSecondary)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.top, 2)
            } else {
                Button {
                    GSHaptics.select()
                    addingTask = true
                } label: {
                    HStack(spacing: 5) {
                        NexlyGlyph(name: "plus", size: 11, color: Color(red: 0.357, green: 0.239, blue: 0.961))
                        Text("Add Task")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(Color(red: 0.357, green: 0.239, blue: 0.961))
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(Capsule().fill(Color.white.opacity(0.82)))
                    .overlay(Capsule().stroke(Color(red: 0.878, green: 0.847, blue: 0.980), lineWidth: 1))
                }
                .buttonStyle(.plain)
                .padding(.top, 2)
            }
        }
    }
}

private struct ChoicesBodyView: View {
    @Environment(\.gsWidgetAction) private var widgetAction
    let obj: [String: Any]
    let accent: Color
    @State private var selected: Set<Int> = []

    var body: some View {
        let items = ((obj["options"] ?? obj["prompts"] ?? obj["buttons"] ?? obj["items"] ?? obj["choices"] ?? obj["suggestions"]) as? [Any]) ?? []
        VStack(alignment: .leading, spacing: 6) {
            ForEach(Array(items.prefix(10).enumerated()), id: \.offset) { idx, item in
                let rawLabel = (item as? String).map { NexlyIcons.normalize($0) } ?? rawStr((item as? [String: Any]) ?? [:], "label", "title", "text", "name", "prompt")
                let promptText = str((item as? [String: Any]) ?? [:], "prompt", "value", "label", "title", "text")
                let (icon, stripped) = NexlyIcons.extractLeadingIcon(rawLabel)
                let label = NexlyIcons.cleanPlainText(stripped)
                let active = selected.contains(idx)
                Button {
                    GSHaptics.select()
                    if active { selected.remove(idx) } else { selected.insert(idx) }
                    let dispatch = promptText.isEmpty ? label : promptText
                    if !dispatch.isEmpty {
                        widgetAction?(dispatch)
                    }
                } label: {
                    HStack(spacing: 6) {
                        if let icon {
                            NexlyGlyph(name: icon, size: 13, color: active ? accent : Aero.textSecondary)
                        }
                        Text(label)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(active ? accent : Aero.text)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(active ? accent.opacity(0.16) : Color.white.opacity(0.55), in: Capsule())
                }
                .buttonStyle(.plain)
            }
        }
    }
}

private struct PanelBodyView: View {
    let obj: [String: Any]
    let accent: Color

    var body: some View {
        let bodyText = NexlyIcons.cleanPlainText(str(obj, "body", "text", "content", "description", "message", "summary"))
        VStack(alignment: .leading, spacing: 6) {
            if !bodyText.isEmpty {
                Text(bodyText).font(.system(size: 13)).foregroundStyle(Aero.text)
            }
            if let items = (obj["items"] ?? obj["points"] ?? obj["bullets"]) as? [Any] {
                ForEach(Array(items.enumerated()), id: \.offset) { _, it in
                    Text("• \(NexlyIcons.cleanPlainText("\(it)"))").font(.system(size: 12)).foregroundStyle(Aero.text)
                }
            }
        }
    }
}

private struct GenericStructuredBodyView: View {
    @Environment(\.gsWidgetAction) private var widgetAction
    let kind: String
    let obj: [String: Any]
    let accent: Color
    @State private var selectedIdx: Int = 0
    @State private var simStep: Int = 1
    @State private var isRunning: Bool = true

    var body: some View {
        let items = ((obj["items"] ?? obj["cards"] ?? obj["fields"] ?? obj["sliders"] ?? obj["phrases"] ?? obj["commands"] ?? obj["nodes"] ?? obj["steps"]) as? [[String: Any]]) ?? []
        let subtitle = NexlyIcons.cleanPlainText(str(obj, "subtitle", "description", "body", "text", "summary"))
        VStack(alignment: .leading, spacing: 6) {
            if ["sim", "stage3d", "canvas", "terminal", "collab", "voice", "plan"].contains(kind) {
                HStack(spacing: 8) {
                    Button {
                        GSHaptics.select()
                        isRunning.toggle()
                    } label: {
                        HStack(spacing: 4) {
                            NexlyGlyph(name: isRunning ? "player-pause" : "player-play", size: 11, color: .white)
                            Text(isRunning ? "Active" : "Paused")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundStyle(.white)
                        }
                        .padding(.horizontal, 9)
                        .padding(.vertical, 4)
                        .background(accent, in: Capsule())
                    }
                    .buttonStyle(.plain)

                    Button {
                        GSHaptics.select()
                        simStep += 1
                    } label: {
                        HStack(spacing: 4) {
                            NexlyGlyph(name: "refresh", size: 11, color: accent)
                            Text("Step #\(simStep)")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(accent)
                        }
                        .padding(.horizontal, 9)
                        .padding(.vertical, 4)
                        .background(accent.opacity(0.14), in: Capsule())
                    }
                    .buttonStyle(.plain)

                    Spacer()
                }
            }
            if !subtitle.isEmpty {
                Text(subtitle).font(.system(size: 12)).foregroundStyle(Aero.textSecondary)
            }
            ForEach(Array(items.prefix(10).enumerated()), id: \.offset) { idx, item in
                let isSelected = selectedIdx == idx
                let primary = NexlyIcons.cleanPlainText(str(item, "title", "label", "name", "phrase", "command", "id"))
                let secondary = NexlyIcons.cleanPlainText(str(item, "subtitle", "description", "detail", "value", "translation", "output", "type"))
                if !primary.isEmpty || !secondary.isEmpty {
                    Button {
                        GSHaptics.select()
                        selectedIdx = idx
                        if kind == "voice" || kind == "terminal" {
                            widgetAction?(primary)
                        }
                    } label: {
                        HStack(alignment: .top) {
                            Text(primary)
                                .font(.system(size: 12, weight: isSelected ? .bold : .semibold))
                                .foregroundStyle(Aero.text)
                            Spacer()
                            if !secondary.isEmpty {
                                Text(secondary)
                                    .font(.system(size: 11))
                                    .foregroundStyle(isSelected ? accent : Aero.textSecondary)
                            }
                        }
                        .padding(8)
                        .background(
                            (isSelected ? Color(red: 0.953, green: 0.933, blue: 1.0) : Color.white.opacity(0.50)),
                            in: RoundedRectangle(cornerRadius: 8)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 8).stroke(isSelected ? accent.opacity(0.45) : Color.clear, lineWidth: 1)
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}

private struct FormBodyView: View {
    @Environment(\.gsWidgetAction) private var widgetAction
    let obj: [String: Any]
    let accent: Color
    @State private var textValues: [Int: String] = [:]
    @State private var numValues: [Int: Double] = [:]
    @State private var boolValues: [Int: Bool] = [:]
    @State private var submitted = false

    var body: some View {
        let fields = ((obj["fields"] ?? obj["inputs"] ?? obj["questions"]) as? [[String: Any]]) ?? []
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(fields.prefix(10).enumerated()), id: \.offset) { idx, f in
                let label = NexlyIcons.cleanPlainText(str(f, "label", "title", "name", "key"))
                let fType = str(f, "type").lowercased()
                VStack(alignment: .leading, spacing: 4) {
                    if ["switch", "toggle", "checkbox", "boolean"].contains(fType) {
                        Toggle(label, isOn: Binding(
                            get: { boolValues[idx] ?? ((f["value"] as? Bool) ?? false) },
                            set: { boolValues[idx] = $0 }
                        ))
                        .font(.system(size: 12, weight: .medium))
                        .tint(accent)
                    } else if ["slider", "range", "number"].contains(fType) {
                        let minV = dbl(f, "min", fallback: 0)
                        let maxV = max(dbl(f, "max", fallback: 100), minV + 1)
                        let cur = numValues[idx] ?? dbl(f, "value", "default", fallback: minV)
                        HStack {
                            Text(label).font(.system(size: 12, weight: .medium)).foregroundStyle(Aero.text)
                            Spacer()
                            Text(String(format: "%.1f", cur)).font(Aero.mono(11)).foregroundStyle(accent)
                        }
                        Slider(value: Binding(
                            get: { cur },
                            set: { numValues[idx] = $0 }
                        ), in: minV...maxV)
                        .tint(accent)
                    } else {
                        Text(label).font(.system(size: 12, weight: .medium)).foregroundStyle(Aero.text)
                        TextField(str(f, "placeholder", "hint"), text: Binding(
                            get: { textValues[idx] ?? str(f, "value", "default") },
                            set: { textValues[idx] = $0 }
                        ))
                        .font(.system(size: 12))
                        .padding(7)
                        .background(Color.white.opacity(0.6), in: RoundedRectangle(cornerRadius: 8))
                    }
                }
            }
            Button {
                GSHaptics.select()
                submitted = true
                let summary = fields.prefix(10).enumerated().map { idx, f in
                    let lbl = str(f, "label", "title", "name", "key")
                    let fType = str(f, "type").lowercased()
                    if ["switch", "toggle", "checkbox", "boolean"].contains(fType) {
                        return "\(lbl): \((boolValues[idx] ?? ((f["value"] as? Bool) ?? false)) ? "Yes" : "No")"
                    } else if ["slider", "range", "number"].contains(fType) {
                        let cur = numValues[idx] ?? dbl(f, "value", "default", fallback: dbl(f, "min", fallback: 0))
                        return String(format: "%@: %.1f", lbl, cur)
                    } else {
                        return "\(lbl): \(textValues[idx] ?? str(f, "value", "default"))"
                    }
                }.joined(separator: ", ")
                if !summary.isEmpty {
                    widgetAction?(summary)
                }
            } label: {
                Text(submitted ? "Submitted ✓" : (str(obj, "submitLabel").isEmpty ? "Submit" : str(obj, "submitLabel")))
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 7)
                    .background(accent, in: Capsule())
            }
            .buttonStyle(.plain)
        }
    }
}

/// Safe recursive-descent math expression evaluator for `calc` widgets on iOS (parity with Android `CalcExpr.eval`).
enum CalcExprIOS {
    static func eval(_ expr: String, vars: [String: Double]) -> Double {
        let chars = Array(expr)
        var pos = 0

        func skipWs() {
            while pos < chars.count && chars[pos].isWhitespace { pos += 1 }
        }

        func parseAddSub() -> Double {
            var left = parseMulDiv()
            while true {
                skipWs()
                guard pos < chars.count else { break }
                let op = chars[pos]
                if op == "+" { pos += 1; left += parseMulDiv() }
                else if op == "-" { pos += 1; left -= parseMulDiv() }
                else { break }
            }
            return left
        }

        func parseMulDiv() -> Double {
            var left = parsePower()
            while true {
                skipWs()
                guard pos < chars.count else { break }
                if pos + 1 < chars.count && chars[pos] == "*" && chars[pos + 1] == "*" { break }
                let op = chars[pos]
                if op == "*" { pos += 1; left *= parsePower() }
                else if op == "/" {
                    pos += 1
                    let r = parsePower()
                    left = r == 0.0 ? 0.0 : left / r
                } else if op == "%" {
                    pos += 1
                    let r = parsePower()
                    left = r == 0.0 ? 0.0 : left.truncatingRemainder(dividingBy: r)
                } else { break }
            }
            return left
        }

        func parsePower() -> Double {
            let base = parseUnary()
            skipWs()
            if pos < chars.count && chars[pos] == "^" {
                pos += 1
                return pow(base, parsePower())
            }
            if pos + 1 < chars.count && chars[pos] == "*" && chars[pos + 1] == "*" {
                pos += 2
                return pow(base, parsePower())
            }
            return base
        }

        func parseUnary() -> Double {
            skipWs()
            if pos < chars.count && chars[pos] == "-" { pos += 1; return -parseUnary() }
            if pos < chars.count && chars[pos] == "+" { pos += 1; return parseUnary() }
            return parsePrimary()
        }

        func parsePrimary() -> Double {
            skipWs()
            guard pos < chars.count else { return 0.0 }
            if chars[pos] == "(" {
                pos += 1
                let v = parseAddSub()
                skipWs()
                if pos < chars.count && chars[pos] == ")" { pos += 1 }
                return v
            }
            if chars[pos].isNumber || chars[pos] == "." {
                let start = pos
                while pos < chars.count && (chars[pos].isNumber || chars[pos] == ".") { pos += 1 }
                return Double(String(chars[start..<pos])) ?? 0.0
            }
            if chars[pos].isLetter || chars[pos] == "_" {
                let start = pos
                while pos < chars.count && (chars[pos].isLetter || chars[pos].isNumber || chars[pos] == "_") { pos += 1 }
                let ident = String(chars[start..<pos])
                skipWs()
                if pos < chars.count && chars[pos] == "(" {
                    pos += 1
                    var args: [Double] = []
                    skipWs()
                    if pos < chars.count && chars[pos] != ")" {
                        args.append(parseAddSub())
                        skipWs()
                        while pos < chars.count && chars[pos] == "," {
                            pos += 1
                            args.append(parseAddSub())
                            skipWs()
                        }
                    }
                    if pos < chars.count && chars[pos] == ")" { pos += 1 }
                    let a = args.first ?? 0.0
                    let b = args.count > 1 ? args[1] : 0.0
                    switch ident.lowercased() {
                    case "sqrt": return a >= 0 ? sqrt(a) : 0.0
                    case "abs": return abs(a)
                    case "round": return round(a)
                    case "floor": return floor(a)
                    case "ceil": return ceil(a)
                    case "min": return args.min() ?? 0.0
                    case "max": return args.max() ?? 0.0
                    case "pow": return pow(a, b)
                    case "log": return a > 0 ? log(a) : 0.0
                    case "exp": return exp(a)
                    case "sin": return sin(a)
                    case "cos": return cos(a)
                    case "tan": return tan(a)
                    default: return a
                    }
                }
                if ident.lowercased() == "pi" { return Double.pi }
                if ident.lowercased() == "e" { return M_E }
                return vars[ident] ?? vars[ident.lowercased()] ?? 0.0
            }
            pos += 1
            return 0.0
        }

        let res = parseAddSub()
        return res.isFinite ? res : 0.0
    }
}

private struct CalcBodyView: View {
    let obj: [String: Any]
    let accent: Color
    @State private var vals: [String: Double] = [:]

    private func formatCalcResult(_ n: Double, unit: String) -> String {
        let prefix = ["$", "€", "£", "¥"].contains(unit) ? unit : ""
        let suffix = (!unit.isEmpty && prefix.isEmpty) ? (unit == "%" ? "%" : " \(unit)") : ""
        let formatted: String = {
            if abs(n) >= 100 && n.truncatingRemainder(dividingBy: 1.0) == 0 {
                return String(format: "%.0f", n)
            }
            if n.truncatingRemainder(dividingBy: 1.0) == 0 {
                return String(format: "%.0f", n)
            }
            return String(format: "%.2f", n)
        }()
        return "\(prefix)\(formatted)\(suffix)"
    }

    var body: some View {
        let fields = ((obj["fields"] ?? obj["sliders"] ?? obj["inputs"]) as? [[String: Any]]) ?? []
        let outputs = ((obj["outputs"] ?? obj["results"]) as? [[String: Any]]) ?? []
        let activeVars: [String: Double] = {
            var map: [String: Double] = [:]
            for (idx, f) in fields.prefix(10).enumerated() {
                let rawKey = str(f, "id", "key", "name")
                let key = rawKey.isEmpty ? "x\(idx + 1)" : rawKey
                let minV = dbl(f, "min", fallback: 0)
                let cur = vals[key] ?? dbl(f, "default", "value", fallback: minV)
                map[key] = cur
                map[key.lowercased()] = cur
                map["x\(idx + 1)"] = cur
            }
            return map
        }()

        VStack(alignment: .leading, spacing: 10) {
            ForEach(Array(fields.prefix(8).enumerated()), id: \.offset) { idx, f in
                let label = NexlyIcons.cleanPlainText(str(f, "label", "title", "name", "key"))
                let rawKey = str(f, "id", "key", "name")
                let key = rawKey.isEmpty ? "x\(idx + 1)" : rawKey
                let minV = dbl(f, "min", fallback: 0)
                let maxV = max(dbl(f, "max", fallback: 1000), minV + 1)
                let stepV = max(dbl(f, "step", fallback: 0), 0)
                let cur = activeVars[key] ?? minV
                let unit = str(f, "unit")
                VStack(alignment: .leading, spacing: 3) {
                    HStack {
                        Text(label).font(.system(size: 12, weight: .medium)).foregroundStyle(Aero.text)
                        Spacer()
                        TextField(
                            "0",
                            text: Binding(
                                get: {
                                    cur.truncatingRemainder(dividingBy: 1.0) == 0
                                        ? String(format: "%.0f", cur)
                                        : String(format: "%.2f", cur)
                                },
                                set: { s in
                                    if let d = Double(s.replacingOccurrences(of: ",", with: "")) {
                                        vals[key] = d
                                    }
                                }
                            )
                        )
                        .font(Aero.mono(11.5))
                        .foregroundStyle(accent)
                        .multilineTextAlignment(.trailing)
                        .frame(width: 72)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 3)
                        .background(Color.white.opacity(0.85), in: RoundedRectangle(cornerRadius: 6))
                        if !unit.isEmpty {
                            Text(unit).font(Aero.mono(11)).foregroundStyle(Aero.textSecondary)
                        }
                    }
                    if stepV > 0 && (maxV - minV) / stepV <= 5000 {
                        Slider(
                            value: Binding(
                                get: { min(max(cur, minV), maxV) },
                                set: { vals[key] = $0 }
                            ),
                            in: minV...maxV,
                            step: stepV
                        )
                        .tint(accent)
                    } else {
                        Slider(
                            value: Binding(
                                get: { min(max(cur, minV), maxV) },
                                set: { vals[key] = $0 }
                            ),
                            in: minV...maxV
                        )
                        .tint(accent)
                    }
                }
            }
            ForEach(Array(outputs.prefix(6).enumerated()), id: \.offset) { _, out in
                let label = NexlyIcons.cleanPlainText(str(out, "label", "title", "name"))
                let expr = str(out, "expr", "formula", "value")
                let unit = str(out, "unit")
                let evaluated: String = {
                    if expr.isEmpty { return "0" }
                    if let literal = Double(expr) { return formatCalcResult(literal, unit: unit) }
                    let val = CalcExprIOS.eval(expr, vars: activeVars)
                    return formatCalcResult(val, unit: unit)
                }()
                HStack {
                    Text(label)
                        .font(.system(size: 12.5, weight: .semibold))
                        .foregroundStyle(Aero.textSecondary)
                    Spacer()
                    Text(evaluated)
                        .font(.system(size: 14, weight: .bold, design: .monospaced))
                        .foregroundStyle(accent)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 8)
                .background(Color.white.opacity(0.75), in: RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(accent.opacity(0.22), lineWidth: 1))
            }
        }
    }
}

private struct MediaBodyView: View {
    let obj: [String: Any]
    let accent: Color

    var body: some View {
        let mType = str(obj, "type").lowercased()
        let rawItems = ((obj["items"] ?? obj["cards"] ?? obj["slides"] ?? obj["results"] ?? obj["sources"] ?? obj["citations"] ?? obj["images"] ?? obj["files"] ?? obj["products"]) as? [[String: Any]]) ?? [obj]
        let isCitationLike = ["citation", "search", "link", "sources", "references"].contains(mType) ||
            rawItems.contains { $0["domain"] != nil || $0["snippet"] != nil }

        if isCitationLike {
            VStack(alignment: .leading, spacing: 8) {
                ForEach(Array(rawItems.prefix(8).enumerated()), id: \.offset) { idx, item in
                    let title = NexlyIcons.cleanPlainText(str(item, "title", "name", "label"))
                    let url = str(item, "url", "href", "link")
                    let domain = str(item, "domain", "source", "site", "publisher")
                    let snippet = NexlyIcons.cleanPlainText(str(item, "snippet", "description", "caption", "summary"))
                    let hay = "\(domain) \(url)".lowercased()
                    let academic = hay.contains(".edu") || hay.contains("nature.com") || hay.contains("arxiv.org") || hay.contains("ieee.org") || hay.contains("acm.org")

                    HStack(alignment: .top, spacing: 10) {
                        Text("\(idx + 1)")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(Color(red: 0.357, green: 0.239, blue: 0.961))
                            .frame(width: 24, height: 24)
                            .background(Circle().fill(Color(red: 0.929, green: 0.898, blue: 1.0)))
                            .overlay(Circle().stroke(Color(red: 0.835, green: 0.776, blue: 1.0), lineWidth: 1))
                            .padding(.top, 1)

                        VStack(alignment: .leading, spacing: 3) {
                            if !title.isEmpty {
                                Text(title)
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundStyle(Aero.text)
                                    .lineLimit(2)
                            }
                            if !snippet.isEmpty {
                                Text(snippet)
                                    .font(.system(size: 12))
                                    .foregroundStyle(Aero.textSecondary)
                                    .lineLimit(2)
                            }
                            HStack {
                                HStack(spacing: 4) {
                                    NexlyGlyph(
                                        name: "external-link",
                                        size: 11,
                                        color: Color(red: 0.357, green: 0.239, blue: 0.961)
                                    )
                                    Text(domain.isEmpty ? url : domain)
                                        .font(.system(size: 11, weight: .semibold))
                                        .foregroundStyle(Color(red: 0.357, green: 0.239, blue: 0.961))
                                        .lineLimit(1)
                                }
                                Spacer()
                                HStack(spacing: 4) {
                                    NexlyGlyph(
                                        name: academic ? "building-bank" : "shield-check",
                                        size: 11,
                                        color: academic
                                            ? Color(red: 0.357, green: 0.239, blue: 0.961)
                                            : Color(red: 0.071, green: 0.604, blue: 0.353)
                                    )
                                    Text(academic ? "Academic" : "Trusted Source")
                                        .font(.system(size: 10, weight: .bold))
                                        .foregroundStyle(
                                            academic
                                                ? Color(red: 0.357, green: 0.239, blue: 0.961)
                                                : Color(red: 0.071, green: 0.604, blue: 0.353)
                                        )
                                }
                                .padding(.horizontal, 8)
                                .padding(.vertical, 2)
                                .background(
                                    Capsule().fill(
                                        academic
                                            ? Color(red: 0.937, green: 0.918, blue: 1.0)
                                            : Color(red: 0.902, green: 0.976, blue: 0.941)
                                    )
                                )
                            }
                        }
                    }
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color.white.opacity(0.75)))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(red: 0.886, green: 0.859, blue: 0.980), lineWidth: 1))
                }
            }
        } else {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 10) {
                    ForEach(Array(rawItems.prefix(12).enumerated()), id: \.offset) { _, item in
                        let title = NexlyIcons.cleanPlainText(str(item, "title", "name", "label", "filename"))
                        let sub = NexlyIcons.cleanPlainText(str(item, "subtitle", "author", "domain", "mime"))
                        let cap = NexlyIcons.cleanPlainText(str(item, "caption", "description", "snippet", "summary"))
                        let price = str(item, "price", "badge", "meta", "size")
                        VStack(alignment: .leading, spacing: 4) {
                            if !title.isEmpty {
                                Text(title).font(.system(size: 13, weight: .bold)).foregroundStyle(Aero.text).lineLimit(2)
                            }
                            if !sub.isEmpty {
                                Text(sub).font(.system(size: 11, weight: .medium)).foregroundStyle(accent).lineLimit(1)
                            }
                            if !cap.isEmpty {
                                Text(cap).font(.system(size: 11)).foregroundStyle(Aero.textSecondary).lineLimit(3)
                            }
                            if !price.isEmpty {
                                Text(price).font(Aero.mono(11)).foregroundStyle(Aero.text)
                            }
                        }
                        .padding(10)
                        .frame(width: rawItems.count > 1 ? 180 : 250, alignment: .leading)
                        .background(Color.white.opacity(0.55), in: RoundedRectangle(cornerRadius: 12))
                    }
                }
            }
        }
    }
}

private struct ProductivityBodyView: View {
    let kind: String
    let obj: [String: Any]
    let accent: Color
    @State private var columnOverrides: [String: Int] = [:]

    var body: some View {
        let cols = (obj["columns"] ?? obj["lanes"] ?? obj["boards"]) as? [[String: Any]] ?? []
        if !cols.isEmpty {
            let numCols = min(cols.count, 4)
            let allCards: [(id: String, origCol: Int, label: String, badge: String)] = cols.prefix(4).enumerated().flatMap { cIdx, col -> [(String, Int, String, String)] in
                let colItems = (col["items"] ?? col["cards"] ?? col["tasks"]) as? [Any] ?? []
                return colItems.prefix(10).enumerated().map { iIdx, it in
                    let lbl = NexlyIcons.cleanPlainText((it as? String) ?? str((it as? [String: Any]) ?? [:], "title", "label", "text", "task", "name"))
                    let badge = NexlyIcons.cleanPlainText(str((it as? [String: Any]) ?? [:], "badge", "priority", "tag", "status"))
                    return ("c\(cIdx)_\(iIdx)", cIdx, lbl, badge)
                }
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 8) {
                    ForEach(0..<numCols, id: \.self) { cIdx in
                        let col = cols[cIdx]
                        let colTitle = NexlyIcons.cleanPlainText(str(col, "title", "name", "label", "status"))
                        let colCards = allCards.filter { (columnOverrides[$0.id] ?? $0.origCol) == cIdx }
                        VStack(alignment: .leading, spacing: 6) {
                            Text("\(colTitle.uppercased()) (\(colCards.count))")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundStyle(WidgetPalette.at(cIdx))
                            ForEach(colCards, id: \.id) { card in
                                Button {
                                    guard numCols > 1 else { return }
                                    GSHaptics.select()
                                    let cur = columnOverrides[card.id] ?? card.origCol
                                    columnOverrides[card.id] = (cur + 1) % numCols
                                } label: {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(card.label).font(.system(size: 12, weight: .medium)).foregroundStyle(Aero.text)
                                        if !card.badge.isEmpty {
                                            Text(card.badge).font(.system(size: 10, weight: .bold)).foregroundStyle(accent)
                                        }
                                    }
                                    .padding(8)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .background(Color.white.opacity(0.6), in: RoundedRectangle(cornerRadius: 8))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .frame(width: 135, alignment: .leading)
                    }
                }
            }
        } else if (obj["rows"] ?? obj["data"]) is [Any] {
            TableBodyView(obj: obj, accent: accent)
        } else {
            GenericStructuredBodyView(kind: kind, obj: obj, accent: accent)
        }
    }
}
