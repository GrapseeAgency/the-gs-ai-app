import Foundation
import SwiftUI

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

private func str(_ dict: [String: Any], _ keys: String...) -> String {
    for k in keys {
        if let s = dict[k] as? String, !s.isEmpty { return s }
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
            let title = str(obj, "title", "heading", "name", "question")
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .center, spacing: 8) {
                    if !title.isEmpty {
                        Text(title)
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

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(kind.uppercased())
                    .font(.system(size: 10, weight: .bold, design: .rounded))
                    .foregroundStyle(WidgetPalette.at(5))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(WidgetPalette.at(5).opacity(0.14), in: Capsule())
                Spacer()
            }
            if kind == "map", let obj = parseRelaxedJSONObject(kind: "map", raw: source) {
                let title = str(obj, "title", "label", "name")
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
                                let lbl = str(p, "label", "title", "name", "place")
                                let det = str(p, "detail", "description", "address")
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
                                Text(instruction).font(.system(size: 12)).foregroundStyle(Aero.text)
                                Spacer()
                                if !dist.isEmpty {
                                    Text(dist).font(Aero.mono(10)).foregroundStyle(Aero.textSecondary)
                                }
                            }
                        }
                    }
                }
            } else {
                let stripped = source
                    .replacingOccurrences(of: "<style[^>]*>[\\s\\S]*?</style>", with: "", options: .regularExpression)
                    .replacingOccurrences(of: "<script[^>]*>[\\s\\S]*?</script>", with: "", options: .regularExpression)
                    .replacingOccurrences(of: "<[^>]+>", with: " ", options: .regularExpression)
                    .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                if !stripped.isEmpty {
                    Text(stripped)
                        .font(Aero.body())
                        .foregroundStyle(Aero.text)
                } else {
                    Text("Interactive \(kind.uppercased()) surface (\(source.count) chars)")
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textSecondary)
                }
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

    private struct BarPoint: Identifiable {
        let id = UUID()
        let seriesName: String
        let label: String
        let value: Double
        let color: Color
    }

    private var points: [BarPoint] {
        var out: [BarPoint] = []
        if let series = obj["series"] as? [[String: Any]] {
            for (sIdx, s) in series.prefix(8).enumerated() {
                let sName = str(s, "name", "label")
                let color = WidgetPalette.at(sIdx)
                if let data = s["data"] as? [Any] {
                    for (pIdx, pt) in data.prefix(12).enumerated() {
                        if let pair = pt as? [Any], pair.count >= 2 {
                            let lbl = "\(pair[0])"
                            let val = (pair[1] as? NSNumber)?.doubleValue ?? Double("\(pair[1])") ?? 0
                            out.append(BarPoint(seriesName: sName, label: lbl, value: val, color: color))
                        } else if let d = pt as? [String: Any] {
                            let lbl = str(d, "label", "x", "name")
                            let val = dbl(d, "value", "y", "v")
                            out.append(BarPoint(seriesName: sName, label: lbl.isEmpty ? "#\(pIdx + 1)" : lbl, value: val, color: color))
                        }
                    }
                }
            }
        } else if let data = (obj["data"] ?? obj["items"] ?? obj["values"]) as? [[String: Any]] {
            for (idx, d) in data.prefix(12).enumerated() {
                let lbl = str(d, "label", "name", "x", "category")
                let val = dbl(d, "value", "y", "count", "score")
                out.append(BarPoint(seriesName: "", label: lbl.isEmpty ? "#\(idx + 1)" : lbl, value: val, color: WidgetPalette.at(idx)))
            }
        }
        return out
    }

    var body: some View {
        let pts = points
        let maxVal = max(pts.map { abs($0.value) }.max() ?? 1.0, 0.0001)
        let unit = str(obj, "unit")
        let subtitle = str(obj, "subtitle")
        VStack(alignment: .leading, spacing: 6) {
            if !subtitle.isEmpty {
                Text(subtitle).font(Aero.caption()).foregroundStyle(Aero.textSecondary)
            }
            ForEach(pts) { pt in
                VStack(alignment: .leading, spacing: 3) {
                    HStack {
                        Text(pt.seriesName.isEmpty ? pt.label : "\(pt.label) · \(pt.seriesName)")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(Aero.text)
                            .lineLimit(1)
                        Spacer()
                        Text(String(format: "%.1f%@", pt.value, unit))
                            .font(Aero.mono(11))
                            .foregroundStyle(Aero.textSecondary)
                    }
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule().fill(Color.black.opacity(0.06))
                            Capsule()
                                .fill(pt.color)
                                .frame(width: max(6, geo.size.width * CGFloat(min(abs(pt.value) / maxVal, 1.0))))
                        }
                    }
                    .frame(height: 7)
                }
            }
        }
    }
}

private struct StatBodyView: View {
    let obj: [String: Any]
    let accent: Color

    var body: some View {
        let cards = ((obj["cards"] ?? obj["items"] ?? obj["metrics"] ?? obj["kpis"] ?? obj["stats"]) as? [[String: Any]]) ?? []
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
            ForEach(Array(cards.prefix(8).enumerated()), id: \.offset) { idx, card in
                let label = str(card, "label", "title", "name", "metric")
                let value = str(card, "value", "val", "number", "amount")
                let delta = str(card, "delta", "change", "trend", "sub")
                VStack(alignment: .leading, spacing: 3) {
                    Text(label)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(Aero.textSecondary)
                        .lineLimit(1)
                    Text(value)
                        .font(.system(size: 17, weight: .bold, design: .rounded))
                        .foregroundStyle(WidgetPalette.at(idx))
                        .lineLimit(1)
                    if !delta.isEmpty {
                        Text(delta)
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundStyle(Aero.textSecondary)
                            .lineLimit(1)
                    }
                }
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.white.opacity(0.55), in: RoundedRectangle(cornerRadius: 12))
            }
        }
    }
}

private struct TableBodyView: View {
    let obj: [String: Any]
    let accent: Color

    @State private var query = ""
    @State private var sortCol: Int? = nil
    @State private var sortAsc = true

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
        VStack(alignment: .leading, spacing: 6) {
            if normRows.count >= 4 {
                TextField("Filter rows…", text: $query)
                    .font(.system(size: 12))
                    .padding(7)
                    .background(Color.white.opacity(0.6), in: RoundedRectangle(cornerRadius: 8))
            }
            if !cols.isEmpty {
                HStack(spacing: 8) {
                    ForEach(Array(cols.prefix(6).enumerated()), id: \.offset) { ci, col in
                        Button {
                            if sortCol == ci { sortAsc.toggle() } else { sortCol = ci; sortAsc = true }
                        } label: {
                            Text(sortCol == ci ? "\(col) \(sortAsc ? "↑" : "↓")" : col)
                                .font(.system(size: 11, weight: .bold))
                                .foregroundStyle(accent)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .buttonStyle(.plain)
                    }
                }
                Divider()
            }
            ForEach(Array(filteredRows.prefix(15).enumerated()), id: \.offset) { _, row in
                HStack(spacing: 8) {
                    ForEach(Array(row.prefix(6).enumerated()), id: \.offset) { _, cell in
                        Text(cell)
                            .font(.system(size: 12))
                            .foregroundStyle(Aero.text)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding(.vertical, 2)
            }
        }
    }
}

private struct CompareBodyView: View {
    let obj: [String: Any]

    var body: some View {
        let left = (obj["left"] ?? obj["sideA"] ?? obj["a"]) as? [String: Any] ?? [:]
        let right = (obj["right"] ?? obj["sideB"] ?? obj["b"]) as? [String: Any] ?? [:]
        HStack(alignment: .top, spacing: 10) {
            compareCol(left, color: WidgetPalette.at(0))
            compareCol(right, color: WidgetPalette.at(3))
        }
    }

    private func compareCol(_ side: [String: Any], color: Color) -> some View {
        let name = str(side, "name", "title", "label")
        let summary = str(side, "summary", "subtitle", "description")
        let rows = (side["rows"] ?? side["items"] ?? side["points"]) as? [[String: Any]] ?? []
        return VStack(alignment: .leading, spacing: 5) {
            Text(name).font(.system(size: 13, weight: .bold)).foregroundStyle(color)
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
        .background(Color.white.opacity(0.55), in: RoundedRectangle(cornerRadius: 12))
    }
}

private struct ProgressBodyView: View {
    let obj: [String: Any]
    let accent: Color

    var body: some View {
        let items: [[String: Any]] = {
            if let arr = (obj["items"] ?? obj["bars"] ?? obj["metrics"]) as? [[String: Any]] { return arr }
            if obj["value"] != nil { return [obj] }
            return []
        }()
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(items.prefix(10).enumerated()), id: \.offset) { idx, item in
                let label = str(item, "label", "name", "title")
                let val = dbl(item, "value", "percent", "score")
                let maxV = max(dbl(item, "max", "total", "target", fallback: 100), 1)
                let pct = min(max(val / maxV, 0), 1)
                VStack(alignment: .leading, spacing: 3) {
                    HStack {
                        Text(label).font(.system(size: 12, weight: .medium)).foregroundStyle(Aero.text)
                        Spacer()
                        Text("\(Int(pct * 100))%").font(Aero.mono(11)).foregroundStyle(Aero.textSecondary)
                    }
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule().fill(Color.black.opacity(0.06))
                            Capsule().fill(WidgetPalette.at(idx)).frame(width: max(6, geo.size.width * CGFloat(pct)))
                        }
                    }
                    .frame(height: 7)
                }
            }
        }
    }
}

private struct TimelineBodyView: View {
    let obj: [String: Any]
    let accent: Color

    var body: some View {
        let entries = ((obj["entries"] ?? obj["events"] ?? obj["milestones"] ?? obj["items"]) as? [[String: Any]]) ?? []
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(entries.prefix(12).enumerated()), id: \.offset) { _, entry in
                HStack(alignment: .top, spacing: 10) {
                    Circle().fill(accent).frame(width: 8, height: 8).padding(.top, 5)
                    VStack(alignment: .leading, spacing: 2) {
                        let date = str(entry, "date", "time", "year", "when", "phase")
                        if !date.isEmpty {
                            Text(date).font(.system(size: 11, weight: .bold)).foregroundStyle(accent)
                        }
                        Text(str(entry, "title", "label", "event", "name"))
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Aero.text)
                        let detail = str(entry, "detail", "description", "body", "text")
                        if !detail.isEmpty {
                            Text(detail).font(.system(size: 12)).foregroundStyle(Aero.textSecondary)
                        }
                    }
                }
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

    var body: some View {
        let steps = ((obj["steps"] ?? obj["items"] ?? obj["stages"]) as? [[String: Any]]) ?? []
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(steps.enumerated()), id: \.offset) { idx, step in
                HStack(alignment: .top, spacing: 10) {
                    Text("\(idx + 1)")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(accent)
                        .frame(width: 22, height: 22)
                        .background(accent.opacity(0.16), in: Circle())
                    VStack(alignment: .leading, spacing: 2) {
                        Text(str(step, "title", "label", "name", "step"))
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Aero.text)
                        let detail = str(step, "detail", "description", "body", "text")
                        if !detail.isEmpty {
                            Text(detail).font(.system(size: 12)).foregroundStyle(Aero.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

private struct PollBodyView: View {
    let obj: [String: Any]
    let accent: Color
    @State private var voted: Int? = nil

    var body: some View {
        let opts = ((obj["options"] ?? obj["choices"] ?? obj["items"]) as? [Any]) ?? []
        VStack(alignment: .leading, spacing: 6) {
            ForEach(Array(opts.enumerated()), id: \.offset) { idx, opt in
                let label = (opt as? String) ?? str((opt as? [String: Any]) ?? [:], "label", "title", "name", "text")
                let baseVotes = Int(dbl((opt as? [String: Any]) ?? [:], "votes", "count", "value", fallback: 0))
                let votes = baseVotes + (voted == idx ? 1 : 0)
                Button {
                    voted = idx
                } label: {
                    HStack {
                        Text(label).font(.system(size: 12, weight: voted == idx ? .bold : .regular)).foregroundStyle(Aero.text)
                        Spacer()
                        Text("\(votes)").font(Aero.mono(11)).foregroundStyle(accent)
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
    let obj: [String: Any]
    let accent: Color
    @State private var chosen: Int? = nil

    var body: some View {
        let maxR = min(max(Int(dbl(obj, "max", fallback: 5)), 2), 10)
        let prompt = str(obj, "prompt", "question")
        VStack(alignment: .leading, spacing: 8) {
            if !prompt.isEmpty {
                Text(prompt).font(.system(size: 12)).foregroundStyle(Aero.textSecondary)
            }
            HStack(spacing: 6) {
                ForEach(1...maxR, id: \.self) { n in
                    let active = (chosen ?? 0) >= n
                    Button {
                        chosen = n
                    } label: {
                        Text("★")
                            .font(.system(size: 18))
                            .foregroundStyle(active ? accent : Aero.textSecondary.opacity(0.35))
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

    var body: some View {
        let roots = ((obj["roots"] ?? obj["tree"] ?? obj["nodes"] ?? obj["items"]) as? [[String: Any]]) ?? []
        VStack(alignment: .leading, spacing: 4) {
            ForEach(Array(roots.enumerated()), id: \.offset) { _, node in
                treeLines(node, depth: 0)
            }
        }
    }

    private func treeLines(_ node: [String: Any], depth: Int) -> AnyView {
        let label = str(node, "label", "name", "title")
        let meta = str(node, "meta", "badge", "type")
        let kids = ((node["children"] ?? node["items"] ?? node["nodes"]) as? [[String: Any]]) ?? []
        return AnyView(
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(kids.isEmpty ? "•" : "▾").foregroundStyle(accent)
                    Text(label).font(.system(size: 12, weight: kids.isEmpty ? .regular : .semibold)).foregroundStyle(Aero.text)
                    if !meta.isEmpty {
                        Spacer()
                        Text(meta).font(Aero.mono(10)).foregroundStyle(Aero.textSecondary)
                    }
                }
                .padding(.leading, CGFloat(depth * 14))
                if depth < 4 {
                    ForEach(Array(kids.enumerated()), id: \.offset) { _, child in
                        treeLines(child, depth: depth + 1)
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
            let front = str(card, "front", "question", "q", "term", "prompt")
            let back = str(card, "back", "answer", "a", "definition")
            VStack(alignment: .leading, spacing: 8) {
                Button {
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

    var body: some View {
        let items = ((obj["items"] ?? obj["tasks"] ?? obj["todos"]) as? [Any]) ?? []
        VStack(alignment: .leading, spacing: 6) {
            ForEach(Array(items.enumerated()), id: \.offset) { idx, raw in
                let dict = raw as? [String: Any] ?? [:]
                let label = (raw as? String) ?? str(dict, "label", "title", "text", "task", "name")
                let initialDone = (dict["done"] as? Bool) ?? (dict["checked"] as? Bool) ?? false
                let isDone = toggled.contains(idx) ? !initialDone : initialDone
                Button {
                    if toggled.contains(idx) { toggled.remove(idx) } else { toggled.insert(idx) }
                } label: {
                    HStack(spacing: 8) {
                        Text(isDone ? "☑" : "☐").foregroundStyle(isDone ? accent : Aero.textSecondary)
                        Text(label)
                            .font(.system(size: 13))
                            .strikethrough(isDone)
                            .foregroundStyle(isDone ? Aero.textSecondary : Aero.text)
                    }
                }
                .buttonStyle(.plain)
            }
        }
    }
}

private struct ChoicesBodyView: View {
    let obj: [String: Any]
    let accent: Color
    @State private var selected: Set<Int> = []

    var body: some View {
        let items = ((obj["options"] ?? obj["prompts"] ?? obj["buttons"] ?? obj["items"] ?? obj["choices"] ?? obj["suggestions"]) as? [Any]) ?? []
        VStack(alignment: .leading, spacing: 6) {
            ForEach(Array(items.prefix(10).enumerated()), id: \.offset) { idx, item in
                let label = (item as? String) ?? str((item as? [String: Any]) ?? [:], "label", "title", "text", "name", "prompt")
                let active = selected.contains(idx)
                Button {
                    if active { selected.remove(idx) } else { selected.insert(idx) }
                } label: {
                    Text(label)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(active ? accent : Aero.text)
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
        let bodyText = str(obj, "body", "text", "content", "description", "message", "summary")
        VStack(alignment: .leading, spacing: 6) {
            if !bodyText.isEmpty {
                Text(bodyText).font(.system(size: 13)).foregroundStyle(Aero.text)
            }
            if let items = (obj["items"] ?? obj["points"] ?? obj["bullets"]) as? [Any] {
                ForEach(Array(items.enumerated()), id: \.offset) { _, it in
                    Text("• \(it)").font(.system(size: 12)).foregroundStyle(Aero.text)
                }
            }
        }
    }
}

private struct GenericStructuredBodyView: View {
    let kind: String
    let obj: [String: Any]
    let accent: Color

    var body: some View {
        let items = ((obj["items"] ?? obj["cards"] ?? obj["fields"] ?? obj["sliders"] ?? obj["phrases"] ?? obj["commands"] ?? obj["nodes"] ?? obj["steps"]) as? [[String: Any]]) ?? []
        let subtitle = str(obj, "subtitle", "description", "body", "text", "summary")
        VStack(alignment: .leading, spacing: 6) {
            if !subtitle.isEmpty {
                Text(subtitle).font(.system(size: 12)).foregroundStyle(Aero.textSecondary)
            }
            ForEach(Array(items.prefix(10).enumerated()), id: \.offset) { _, item in
                let primary = str(item, "title", "label", "name", "phrase", "command", "id")
                let secondary = str(item, "subtitle", "description", "detail", "value", "translation", "output", "type")
                if !primary.isEmpty || !secondary.isEmpty {
                    HStack(alignment: .top) {
                        Text(primary).font(.system(size: 12, weight: .semibold)).foregroundStyle(Aero.text)
                        Spacer()
                        if !secondary.isEmpty {
                            Text(secondary).font(.system(size: 11)).foregroundStyle(Aero.textSecondary)
                        }
                    }
                    .padding(8)
                    .background(Color.white.opacity(0.50), in: RoundedRectangle(cornerRadius: 8))
                }
            }
        }
    }
}

private struct FormBodyView: View {
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
                let label = str(f, "label", "title", "name", "key")
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
                submitted = true
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

private struct CalcBodyView: View {
    let obj: [String: Any]
    let accent: Color
    @State private var vals: [String: Double] = [:]

    var body: some View {
        let fields = ((obj["fields"] ?? obj["sliders"] ?? obj["inputs"]) as? [[String: Any]]) ?? []
        let outputs = ((obj["outputs"] ?? obj["results"]) as? [[String: Any]]) ?? []
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(fields.prefix(8).enumerated()), id: \.offset) { idx, f in
                let label = str(f, "label", "title", "name", "key")
                let key = str(f, "key", "id", "name").isEmpty ? "x\(idx + 1)" : str(f, "key", "id", "name")
                let minV = dbl(f, "min", fallback: 0)
                let maxV = max(dbl(f, "max", fallback: 1000), minV + 1)
                let cur = vals[key] ?? dbl(f, "value", "default", fallback: minV)
                let unit = str(f, "unit")
                VStack(alignment: .leading, spacing: 2) {
                    HStack {
                        Text(label).font(.system(size: 12, weight: .medium)).foregroundStyle(Aero.text)
                        Spacer()
                        Text(String(format: "%.1f%@", cur, unit)).font(Aero.mono(11)).foregroundStyle(accent)
                    }
                    Slider(value: Binding(
                        get: { cur },
                        set: { vals[key] = $0 }
                    ), in: minV...maxV)
                    .tint(accent)
                }
            }
            ForEach(Array(outputs.prefix(6).enumerated()), id: \.offset) { _, out in
                HStack {
                    Text(str(out, "label", "title", "name"))
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Aero.textSecondary)
                    Spacer()
                    Text(str(out, "value", "expr", "formula"))
                        .font(Aero.mono(12))
                        .foregroundStyle(accent)
                }
                .padding(8)
                .background(Color.white.opacity(0.55), in: RoundedRectangle(cornerRadius: 8))
            }
        }
    }
}

private struct MediaBodyView: View {
    let obj: [String: Any]
    let accent: Color

    var body: some View {
        let rawItems = ((obj["items"] ?? obj["cards"] ?? obj["slides"] ?? obj["results"] ?? obj["images"] ?? obj["files"] ?? obj["products"]) as? [[String: Any]]) ?? [obj]
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(alignment: .top, spacing: 10) {
                ForEach(Array(rawItems.prefix(12).enumerated()), id: \.offset) { _, item in
                    let title = str(item, "title", "name", "label", "filename")
                    let sub = str(item, "subtitle", "author", "domain", "mime")
                    let cap = str(item, "caption", "description", "snippet", "summary")
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

private struct ProductivityBodyView: View {
    let kind: String
    let obj: [String: Any]
    let accent: Color

    var body: some View {
        let cols = (obj["columns"] ?? obj["lanes"] ?? obj["boards"]) as? [[String: Any]] ?? []
        if !cols.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 8) {
                    ForEach(Array(cols.prefix(4).enumerated()), id: \.offset) { cIdx, col in
                        let colTitle = str(col, "title", "name", "label", "status")
                        let colItems = (col["items"] ?? col["cards"] ?? col["tasks"]) as? [Any] ?? []
                        VStack(alignment: .leading, spacing: 6) {
                            Text(colTitle.uppercased())
                                .font(.system(size: 10, weight: .bold))
                                .foregroundStyle(WidgetPalette.at(cIdx))
                            ForEach(Array(colItems.prefix(10).enumerated()), id: \.offset) { _, it in
                                let lbl = (it as? String) ?? str((it as? [String: Any]) ?? [:], "title", "label", "text", "task", "name")
                                let badge = str((it as? [String: Any]) ?? [:], "badge", "priority", "tag", "status")
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(lbl).font(.system(size: 12, weight: .medium)).foregroundStyle(Aero.text)
                                    if !badge.isEmpty {
                                        Text(badge).font(.system(size: 10, weight: .bold)).foregroundStyle(accent)
                                    }
                                }
                                .padding(8)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color.white.opacity(0.6), in: RoundedRectangle(cornerRadius: 8))
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
