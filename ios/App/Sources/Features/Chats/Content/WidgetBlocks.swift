import Foundation
import CoreFoundation
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

private let widgetRawPresentationKeys: Set<String> = [
    "id", "key", "type", "kind", "expr", "expression", "formula",
    "code", "source", "script", "html", "svg", "css", "sql", "query",
    "url", "href", "link", "src", "route", "action", "method", "prompt",
    "language", "format", "accent", "palette", "variant", "color",
    "before", "after", "old", "new", "original", "modified", "patch", "diff", "unified", "z", "pattern", "regex"
]

/// Decoded JSON boundary, including escaped Unicode and primitive string arrays.
/// Only presentation changes; source, expressions, identities and URLs stay exact.
func sanitizeWidgetPresentationIOS(_ value: Any, key: String = "") -> Any {
    if let text = value as? String {
        if widgetRawPresentationKeys.contains(key) { return text }
        let normalized = NexlyIcons.normalize(text)
        if key == "title" { return normalized }
        if key == "icon" || key == "closingIcon" { return NexlyIcons.extractLeadingIcon(text).0 ?? NexlyIcons.resolve(text) }
        return normalized.replacingOccurrences(of: ":[a-z0-9][a-z0-9-]{1,42}:", with: "", options: .regularExpression)
    }
    if let array = value as? [Any] { return array.map { sanitizeWidgetPresentationIOS($0, key: key) } }
    if let object = value as? [String: Any] {
        return object.reduce(into: [String: Any]()) { result, entry in
            result[entry.key] = sanitizeWidgetPresentationIOS(entry.value, key: entry.key)
        }
    }
    return value
}

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
    return recoverPlainTextWidgetIOS(kind: kind, raw: trimmed).map {
        sanitizeWidgetPresentationIOS($0) as? [String: Any] ?? $0
    }
}

private func recoverPlainTextWidgetIOS(kind: String, raw: String) -> [String: Any]? {
    let lines = raw.components(separatedBy: .newlines)
        .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
        .filter { !$0.isEmpty && !$0.hasPrefix("```") }
    guard !lines.isEmpty else { return nil }
    var kvPairs: [(String, String)] = []
    var bullets: [String] = []
    for line in lines {
        let stripped = line.replacingOccurrences(of: "^[-*•]\\s+", with: "", options: .regularExpression)
        if let colon = stripped.firstIndex(of: ":"), colon != stripped.startIndex {
            let k = String(stripped[..<colon]).trimmingCharacters(in: CharacterSet(charactersIn: "\"' "))
            let v = String(stripped[stripped.index(after: colon)...]).trimmingCharacters(in: CharacterSet(charactersIn: "\"', "))
            if !k.isEmpty && !v.isEmpty {
                kvPairs.append((k, v))
                continue
            }
        }
        bullets.append(stripped)
    }
    switch kind {
    case "chart", "progress", "gauge":
        let numPairs = kvPairs.compactMap { k, v -> [String: Any]? in
            let numStr = v.replacingOccurrences(of: "[^0-9.-]", with: "", options: .regularExpression)
            guard let d = Double(numStr) else { return nil }
            return ["label": k, "value": d]
        }
        guard !numPairs.isEmpty else { return nil }
        return kind == "chart"
            ? ["type": "bar", "series": [["name": "Value", "data": numPairs]]]
            : ["items": numPairs]
    case "stat":
        guard !kvPairs.isEmpty else { return nil }
        return ["cards": kvPairs.map { ["label": $0.0, "value": $0.1] }]
    case "checklist", "steps", "poll", "menu", "chips", "followups", "actions":
        let items = (!bullets.isEmpty ? bullets : kvPairs.map { "\($0.0): \($0.1)" })
        guard !items.isEmpty else { return nil }
        if kind == "poll" || kind == "menu" || kind == "chips" {
            return ["options": items.map { ["label": $0, "votes": 0] }]
        }
        if kind == "followups" {
            return ["prompts": items]
        }
        if kind == "actions" {
            return ["buttons": items.map { ["label": $0, "prompt": $0] }]
        }
        return ["items": items.map { ["title": $0, "label": $0, "text": $0] }]
    default:
        if !kvPairs.isEmpty {
            var dict: [String: Any] = [:]
            for (k, v) in kvPairs { dict[k] = v }
            dict["items"] = kvPairs.map { ["title": $0.0, "subtitle": $0.1] }
            return dict
        }
        if !bullets.isEmpty {
            return ["items": bullets.map { ["title": $0] }]
        }
        return nil
    }
}

private let RAW_KEYS_PRESERVED_IOS: Set<String> = [
    "code", "html", "svg", "url", "src", "href", "unified", "before", "after",
    "formula", "expression", "expr", "z", "pattern", "regex", "query", "sql"
]
private let ICON_NAME_KEYS_IOS: Set<String> = ["icon", "closingIcon"]

private func sanitizeJSONValueIOS(kind: String, value: Any, keyHint: String? = nil) -> Any {
    sanitizeWidgetPresentationIOS(value, key: keyHint ?? "")
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
        return sanitizeJSONValueIOS(kind: kind, value: [key: arr]) as? [String: Any]
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
    return sanitizeJSONValueIOS(kind: kind, value: dict) as? [String: Any]
}

func inferWidgetKind(_ raw: String) -> String? {
    guard let obj = parseRelaxedJSONObject(kind: "json", raw: raw) else { return nil }
    let keys = Set(obj.keys)
    if keys == Set(["items"]), let items = obj["items"] as? [Any] {
        let labels: Set<String> = ["label", "text", "title", "name", "task", "todo", "percent", "value", "front", "back", "question", "options", "url", "src", "time", "date", "body"]
        let meaningful = items.contains { item in
            if let text = item as? String { return !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
            if let object = item as? [String: Any] { return !labels.isDisjoint(with: object.keys) }
            return false
        }
        if !meaningful { return nil }
    }
    let explicitApp = ["kind", "widget", "type"].contains { key in
        guard let value = obj[key] as? String else { return false }
        return widgetKindOf(value) == "app"
    }
    if explicitApp { return isValidWidget(kind: "app", json: raw) ? "app" : nil }
    if (keys.contains("columns") || keys.contains("headers")) && (keys.contains("rows") || keys.contains("data")) {
        return isValidWidget(kind: "table", json: raw) ? "table" : nil
    }
    if keys.contains("series") { return isValidWidget(kind: "chart", json: raw) ? "chart" : nil }
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
        return ((obj["fields"] ?? obj["sliders"] ?? obj["inputs"] ?? obj["variables"] ?? obj["params"]) as? [Any])?.isEmpty == false ||
               ((obj["outputs"] ?? obj["results"]) as? [Any])?.isEmpty == false ||
               (obj["formula"] ?? obj["expression"] ?? obj["expr"]) != nil
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
        guard let items = (obj["items"] ?? obj["tasks"] ?? obj["todos"]) as? [Any], !items.isEmpty, items.count <= 30 else { return false }
        return items.allSatisfy { item in
            let fields = item as? [String: Any] ?? [:]
            guard let label = (item as? String) ?? ((fields["label"] ?? fields["title"] ?? fields["text"] ?? fields["task"] ?? fields["name"]) as? String) else { return false }
            return !NexlyIcons.cleanPlainText(label).isEmpty
        }
    case "app":
        let type = (obj["type"] as? String ?? "wizard").lowercased()
        if type == "tasks" { return isValidWidget(kind: "checklist", json: json) }
        if type == "timer", let raw = obj["durationSeconds"] {
            guard let number = raw as? NSNumber, CFGetTypeID(number) != CFBooleanGetTypeID() else { return false }
            let seconds = number.doubleValue
            return seconds.isFinite && seconds.rounded() == seconds && seconds >= 1 && seconds <= 604800
        }
        if type == "wizard" || type == "onboarding" {
            return ((obj["steps"] ?? obj["stages"]) as? [Any])?.isEmpty == false
        }
        return !obj.isEmpty
    case "collab":
        let surface = (obj["surface"] ?? obj["type"]) as? String ?? "counter"
        guard COLLAB_TYPES.contains(surface) else { return false }
        let rawItems = (obj["items"] ?? obj["options"]) as? [Any] ?? []
        guard rawItems.count <= 12, rawItems.allSatisfy({ ($0 as? String)?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false }) else { return false }
        if surface == "vote" && rawItems.count < 2 { return false }
        if let raw = obj["start"] {
            guard let number = raw as? NSNumber, CFGetTypeID(number) != CFBooleanGetTypeID(), number.doubleValue.isFinite else { return false }
        }
        return true
    case "media", "sim", "stage3d", "canvas", "editor", "terminal":
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

enum DOMDisplayPolicyIOS {
    static let script: String? = {
        guard let url = Bundle.main.url(forResource: "emoji-dom-policy", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { return nil }
        return object["script"] as? String
    }()
}

private struct HtmlSandboxWebView: UIViewRepresentable {
    let html: String
    @Binding var dynamicHeight: CGFloat

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        if let policy = DOMDisplayPolicyIOS.script {
            config.userContentController.addUserScript(WKUserScript(
                source: policy, injectionTime: .atDocumentStart, forMainFrameOnly: false))
        }
        config.userContentController.addUserScript(WKUserScript(source: Self.resizeScript, injectionTime: .atDocumentEnd, forMainFrameOnly: true))
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
        let displayHTML = DOMDisplayPolicyIOS.script == nil
            ? "<p>Interactive preview unavailable: display policy could not be loaded.</p>" : html
        let head = """
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
        """
        let wrapped = HTMLDocumentIOS.withHead(displayHTML, head: head)
        if context.coordinator.lastLoaded != wrapped {
            context.coordinator.lastLoaded = wrapped
            webView.loadHTMLString(wrapped, baseURL: nil)
        }
    }

    static func dismantleUIView(_ webView: WKWebView, coordinator: Coordinator) {
        // A reset or route change must discard the old document and its
        // Workers/timers, and release the script-handler -> binding cycle.
        webView.stopLoading()
        webView.navigationDelegate = nil
        webView.configuration.userContentController.removeScriptMessageHandler(forName: "gsResize")
        webView.configuration.userContentController.removeAllUserScripts()
        webView.loadHTMLString("<!doctype html><html><body></body></html>", baseURL: nil)
    }

    private static let resizeScript = """
    function reportHeight() {
      var h = Math.max(document.body ? document.body.scrollHeight : 0, document.documentElement.scrollHeight || 0, 80);
      if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.gsResize) {
        window.webkit.messageHandlers.gsResize.postMessage(h);
      }
    }
    window.addEventListener('load', reportHeight);
    window.addEventListener('click', function() { setTimeout(reportHeight, 50); });
    window.addEventListener('input', function() { setTimeout(reportHeight, 50); });
    setTimeout(reportHeight, 120);
    """

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

func validatedWidgetKindIOS(kind: String, json: String) -> String? {
    if isValidWidget(kind: kind, json: json) { return kind }
    if widgetKindOf(kind) == "checklist" { return nil }
    guard let inferred = inferWidgetKind(json), isValidWidget(kind: inferred, json: json) else { return nil }
    return inferred
}

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
        } else if let obj = parseRelaxedJSONObject(kind: resolvedKind, raw: json),
                  let effectiveKind = validatedWidgetKindIOS(kind: resolvedKind, json: json) {
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
                    .id(effectiveKind + ":" + json)
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
                .id((try? JSONSerialization.data(withJSONObject: obj, options: [.sortedKeys])) ?? Data())
        case "media", "carousel", "cards":
            MediaBodyView(obj: obj, accent: accent)
        case "app":
            AppBodyView(obj: obj, accent: accent)
        case "editor":
            EditorBodyView(obj: obj, accent: accent)
        case "sim":
            SimBodyView(obj: obj, accent: accent)
        case "stage3d":
            Stage3DBodyView(obj: obj, accent: accent)
        case "canvas":
            CanvasBodyView(obj: obj, accent: accent)
        case "terminal":
            TerminalBodyView(obj: obj, accent: accent)
        case "collab":
            CollabBodyView(obj: obj, accent: accent)
        default:
            GenericStructuredBodyView(kind: kind, obj: obj, accent: accent)
        }
    }
}

func chartGrowthPercentIOS(first: Double, last: Double) -> Double? {
    guard first.isFinite, last.isFinite, first != 0 else { return nil }
    let result = ((last - first) / abs(first)) * 100
    return result.isFinite ? result : nil
}

func chartStepPercentIOS(values: [Double], index: Int) -> Double? {
    guard index > 0, index < values.count else { return nil }
    return chartGrowthPercentIOS(first: values[index - 1], last: values[index])
}

func chartDomainIOS(_ values: [Double]) -> (lower: Double, upper: Double) {
    let lower = min(0, values.min() ?? 0)
    let upper = max(0, values.max() ?? 0)
    return (lower, upper == lower ? lower + 1 : upper)
}

func chartHeightFractionIOS(_ value: Double, domain: (lower: Double, upper: Double)) -> Double {
    (domain.upper - value) / (domain.upper - domain.lower)
}

func chartSignedTotalIOS(_ values: [Double]) -> Double { values.reduce(0, +) }

// Numeric failures are not measurements of zero. Keep this parser shared
// by validation and drawing so a missing/invalid point cannot fabricate KPIs.
func chartValueIOS(_ raw: Any?) -> Double? {
    let number: Double?
    if let value = raw as? NSNumber {
        guard CFGetTypeID(value) != CFBooleanGetTypeID() else { return nil }
        number = value.doubleValue
    } else if let value = raw as? String {
        number = Double(value.trimmingCharacters(in: .whitespacesAndNewlines))
    } else { return nil }
    guard let number, number.isFinite else { return nil }
    return number
}

func chartObservationValueIOS(_ raw: Any) -> Double? {
    if let pair = raw as? [Any] {
        return pair.count >= 2 ? chartValueIOS(pair[1]) : nil
    }
    if let point = raw as? [String: Any] {
        return chartValueIOS(point["value"] ?? point["y"] ?? point["v"] ?? point["count"] ?? point["score"])
    }
    return chartValueIOS(raw)
}

func chartSliceLabelIOS(name: String, labels: [String], seriesIndex: Int) -> String {
    if !name.isEmpty { return name }
    return seriesIndex < labels.count ? labels[seriesIndex] : "Series \(seriesIndex + 1)"
}

private struct ChartBodyView: View {
    let obj: [String: Any]
    let accent: Color

    @State private var activeType: String = ""
    @State private var rangeIdx: Int = 0
    @State private var selectedIdx: Int? = nil
    @State private var selectedSeriesIndex = 0

    private var isSliceSeries: Bool {
        guard currentType == "pie" || currentType == "donut",
              let series = obj["series"] as? [[String: Any]] else { return false }
        return series.allSatisfy { (($0["data"] as? [Any])?.count ?? 0) <= 1 }
    }

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

    private var hasInvalidObservations: Bool {
        if let series = obj["series"] as? [[String: Any]] {
            let selected = min(selectedSeriesIndex, max(0, series.count - 1))
            for (index, item) in series.enumerated() where isSliceSeries || index == selected {
                guard let data = item["data"] as? [Any] else { return true }
                if data.contains(where: { chartObservationValueIOS($0) == nil }) { return true }
            }
            return false
        }
        guard let data = (obj["data"] ?? obj["items"] ?? obj["values"] ?? obj["slices"]) as? [Any] else { return false }
        return data.contains { chartObservationValueIOS($0) == nil }
    }

    private var allPoints: [BarPoint] {
        var out: [BarPoint] = []
        let labels = (obj["labels"] as? [Any])?.map { "\($0)" } ?? []
        if let series = obj["series"] as? [[String: Any]] {
            for (sIdx, s) in series.enumerated() {
                if !isSliceSeries && sIdx != min(selectedSeriesIndex, max(0, series.count - 1)) { continue }
                let sName = str(s, "name", "label")
                let color = WidgetPalette.at(sIdx)
                if let data = s["data"] as? [Any] {
                    for (pIdx, pt) in data.enumerated() {
                        let sliceLabel = chartSliceLabelIOS(name: sName, labels: labels, seriesIndex: sIdx)
                        let fallbackLbl = isSliceSeries ? sliceLabel : (pIdx < labels.count ? labels[pIdx] : "#\(pIdx + 1)")
                        if let pair = pt as? [Any], pair.count >= 2 {
                            let lbl = isSliceSeries ? sliceLabel : (pIdx < labels.count ? labels[pIdx] : "\(pair[0])")
                            guard let val = chartObservationValueIOS(pt) else { continue }
                            out.append(BarPoint(index: out.count, seriesName: sName, label: lbl, value: val, color: color))
                        } else if let val = chartValueIOS(pt) {
                            out.append(BarPoint(index: out.count, seriesName: sName, label: fallbackLbl, value: val, color: color))
                        } else if let d = pt as? [String: Any] {
                            let lbl = isSliceSeries ? sliceLabel : str(d, "label", "x", "name")
                            guard let val = chartObservationValueIOS(d) else { continue }
                            out.append(BarPoint(index: out.count, seriesName: sName, label: lbl.isEmpty ? fallbackLbl : lbl, value: val, color: color))
                        }
                    }
                }
            }
        } else if let data = (obj["data"] ?? obj["items"] ?? obj["values"] ?? obj["slices"]) as? [[String: Any]] {
            for (idx, d) in data.enumerated() {
                let lbl = str(d, "label", "name", "x", "category")
                guard let val = chartObservationValueIOS(d) else { continue }
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
        let totalVal = chartSignedTotalIOS(pts.map { $0.value })
        let unit = str(obj, "unit")
        let subtitle = str(obj, "subtitle")
        let peakIdx = pts.enumerated().max(by: { $0.element.value < $1.element.value })?.offset ?? 0
        let activeIdx = min(max(0, selectedIdx ?? peakIdx), max(0, pts.count - 1))

        VStack(alignment: .leading, spacing: 10) {
            chartHeaderBar(rangeLabel: rangeLabel)
            if let series = obj["series"] as? [[String: Any]], series.count > 1, !isSliceSeries {
                Menu {
                    ForEach(series.indices, id: \.self) { index in
                        Button(str(series[index], "name", "label").ifEmptyIOS("Series \(index + 1)")) {
                            selectedSeriesIndex = index
                            selectedIdx = nil
                            rangeIdx = 0
                        }
                    }
                } label: {
                    Text("Series: \(str(series[min(selectedSeriesIndex, series.count - 1)], "name", "label").ifEmptyIOS("Series \(selectedSeriesIndex + 1)"))")
                }
            }
            if !subtitle.isEmpty {
                Text(subtitle).font(Aero.caption()).foregroundStyle(Aero.textSecondary)
            }
            if hasInvalidObservations {
                Text("Chart unavailable: an observation is missing or is not a finite number. Correct the source data; no zero values or summary have been invented.")
                    .font(Aero.caption()).foregroundStyle(Aero.textSecondary)
            } else if pts.isEmpty {
                Text("No observations in this range.").font(Aero.caption()).foregroundStyle(Aero.textSecondary)
            } else {
                chartTooltipRow(pts: pts, activeIdx: activeIdx, totalVal: totalVal, unit: unit)
                chartVisualizationSurface(pts: pts, activeIdx: activeIdx, maxVal: maxVal, totalVal: totalVal, unit: unit)
                chartSummaryFooter(pts: pts, peakIdx: peakIdx, totalVal: totalVal, unit: unit, rangeLabel: rangeLabel)
            }
        }
    }

    @ViewBuilder
    private func chartHeaderBar(rangeLabel: String) -> some View {
        HStack(spacing: 6) {
            ScrollView(.horizontal, showsIndicators: false) {
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
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
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
            }
            .frame(minWidth: 0, maxWidth: .infinity)
            .frame(height: 34)
            .accessibilityIdentifier("chart.type.controls")

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
            .fixedSize(horizontal: true, vertical: false)
        }
    }

    @ViewBuilder
    private func chartTooltipRow(pts: [BarPoint], activeIdx: Int, totalVal: Double, unit: String) -> some View {
        if !pts.isEmpty && activeIdx < pts.count {
            let selPt = pts[activeIdx]
            let slices = currentType == "pie" || currentType == "donut"
            let stepPct = slices ? nil : chartStepPercentIOS(values: pts.map { $0.value }, index: activeIdx)
            let stepText = stepPct.map { String(format: "%@ %.1f%%", $0 >= 0 ? "↑" : "↓", abs($0)) } ?? (slices ? "N/A (slice values)" : "N/A (no prior baseline)")
            let stepColor = stepPct == nil ? Aero.textSecondary : ((stepPct ?? 0) >= 0 ? Color(red: 0.071, green: 0.604, blue: 0.353) : Color(red: 0.878, green: 0.310, blue: 0.373))
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
                Text(stepText)
                    .font(.system(size: 11.5, weight: .bold))
                    .foregroundStyle(stepColor)
            }
            .padding(.horizontal, 11)
            .padding(.vertical, 7)
            .background(RoundedRectangle(cornerRadius: 10).fill(Color.white.opacity(0.86)))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color(red: 0.863, green: 0.827, blue: 0.980), lineWidth: 1))
        }
    }

    @ViewBuilder
    private func chartVisualizationSurface(pts: [BarPoint], activeIdx: Int, maxVal: Double, totalVal: Double, unit: String) -> some View {
        let domain = chartDomainIOS(pts.map { $0.value })
        if (currentType == "pie" || currentType == "donut") &&
            (pts.contains { $0.value < 0 } || totalVal <= 0) {
            Text("Pie shares require nonnegative values and a positive total. Choose Line or Bar for this dataset.")
                .font(Aero.caption()).foregroundStyle(Aero.textSecondary)
        } else if (currentType == "line" || currentType == "area") && pts.count >= 2 {
            VStack(spacing: 4) {
                HStack(spacing: 6) {
                    VStack(alignment: .trailing, spacing: 0) {
                        ForEach([1.0, 0.66, 0.33, 0.0], id: \.self) { (frac: Double) in
                            Text(compactNumber(domain.lower + (domain.upper - domain.lower) * frac, unit: unit))
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
                            let y = h * CGFloat(chartHeightFractionIOS(pt.value, domain: domain))
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
                                let zeroY = h * CGFloat(chartHeightFractionIOS(0, domain: domain))
                                path.addLine(to: CGPoint(x: last.x, y: zeroY))
                                path.addLine(to: CGPoint(x: first.x, y: zeroY))
                                path.closeSubpath()
                            }
                        }

                        ZStack {
                            ForEach([0.0, 0.33, 0.66, 1.0], id: \.self) { (g: Double) in
                                Path { p in
                                    let y = h * CGFloat(g)
                                    p.move(to: CGPoint(x: 0, y: y))
                                    p.addLine(to: CGPoint(x: w, y: y))
                                }
                                .stroke(Color.black.opacity(0.07), lineWidth: 1)
                            }

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

                            linePath.stroke(
                                Color(red: 0.424, green: 0.298, blue: 0.965),
                                style: StrokeStyle(lineWidth: 2.8, lineCap: .round, lineJoin: .round)
                            )

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

                HStack(spacing: 0) {
                    Spacer().frame(width: 42)
                    ForEach(Array(pts.enumerated()), id: \.offset) { idx, pt in
                        Text(pt.label)
                            .font(.system(size: 10, weight: idx == activeIdx ? .bold : .regular))
                            .foregroundStyle(idx == activeIdx ? Color(red: 0.357, green: 0.239, blue: 0.961) : Aero.textSecondary)
                            .frame(maxWidth: .infinity)
                            .lineLimit(1)
                    }
                }
            }
            .padding(.top, 4)
        } else if currentType == "bar" && !pts.isEmpty {
            HStack(alignment: .bottom, spacing: 8) {
                ForEach(Array(pts.enumerated()), id: \.offset) { idx, pt in
                    let isSel = idx == activeIdx

                    Button {
                        GSHaptics.select()
                        selectedIdx = idx
                    } label: {
                        VStack(spacing: 4) {
                            GeometryReader { geo in
                                let zeroY = geo.size.height * CGFloat(chartHeightFractionIOS(0, domain: domain))
                                let valueY = geo.size.height * CGFloat(chartHeightFractionIOS(pt.value, domain: domain))
                                ZStack {
                                    Path { path in
                                        path.move(to: CGPoint(x: 0, y: zeroY))
                                        path.addLine(to: CGPoint(x: geo.size.width, y: zeroY))
                                    }.stroke(Color.gray.opacity(0.4), lineWidth: 1)
                                    RoundedRectangle(cornerRadius: 3)
                                        .fill(Color(red: 0.424, green: 0.298, blue: 0.965).opacity(isSel ? 1 : 0.45))
                                        .frame(width: geo.size.width, height: abs(valueY - zeroY))
                                        .position(x: geo.size.width / 2, y: (valueY + zeroY) / 2)
                                }
                            }.frame(height: 110)
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
            HStack(spacing: 16) {
                ZStack {
                    Circle()
                        .stroke(Color.black.opacity(0.06), lineWidth: 16)
                        .frame(width: 96, height: 96)
                    ForEach(Array(pts.enumerated()), id: \.offset) { idx, pt in
                        let startSum = pts.prefix(idx).map { max(0, $0.value) }.reduce(0, +)
                        let startFrac = CGFloat(startSum / totalVal)
                        let endFrac = CGFloat((startSum + max(0, pt.value)) / totalVal)
                        Circle()
                            .trim(from: startFrac, to: endFrac)
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
                    ForEach(Array(pts.enumerated()), id: \.offset) { idx, pt in
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

                    }
                }
                .buttonStyle(.plain)
            }
        }
    }

    @ViewBuilder
    private func chartSummaryFooter(pts: [BarPoint], peakIdx: Int, totalVal: Double, unit: String, rangeLabel: String) -> some View {
        if pts.count >= 2 {
            let slices = currentType == "pie" || currentType == "donut"
            let growthPct = slices ? nil : chartGrowthPercentIOS(first: pts[0].value, last: pts[pts.count - 1].value)
            let pos = (growthPct ?? -1) >= 0
            let growthText = growthPct.map { String(format: "%@%.1f%%", pos ? "+" : "", $0) } ?? (slices ? "N/A (slice values)" : "N/A (zero baseline)")
            let deltaText = growthPct == nil ? "No defined baseline" : "\(pos ? "↑" : "↓") \(growthText)"
            let pillBg = growthPct == nil ? Color.gray.opacity(0.1) : (pos ? Color(red: 0.902, green: 0.976, blue: 0.941) : Color(red: 0.992, green: 0.925, blue: 0.925))
            let pillFg = growthPct == nil ? Aero.textSecondary : (pos ? Color(red: 0.071, green: 0.604, blue: 0.353) : Color(red: 0.878, green: 0.310, blue: 0.373))
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
                    Text(deltaText)
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
                    Text(growthText)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Aero.text)
                    Text(slices ? "Slice values" : "First–last change")
                        .font(.system(size: 10, weight: .medium))
                        .foregroundStyle(Aero.textSecondary)
                        .lineLimit(1)
                    Text(deltaText)
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
                    Text("Peak in \(peakPt.label) (\(compactNumber(peakPt.value, unit: unit))) · \(growthText) overall change across \(rangeLabel.lowercased())")
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

private struct StatBodyView: View {
    let obj: [String: Any]
    let accent: Color

    var body: some View {
        let cards = ((obj["cards"] ?? obj["items"] ?? obj["metrics"] ?? obj["kpis"] ?? obj["stats"]) as? [[String: Any]]) ?? []
        // A metric is not a drill-down button unless there is a real drill-down.
        // Preserve all supplied cards and units, with no invented trend arrows.
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
            ForEach(Array(cards.enumerated()), id: \.offset) { _, card in
                metricCard(card)
            }
        }
    }

    private func metricCard(_ card: [String: Any]) -> some View {
        let label = NexlyIcons.cleanPlainText(str(card, "label", "title", "name", "metric"))
        let value = NexlyIcons.cleanPlainText(str(card, "value", "val", "number", "amount"))
        let unit = NexlyIcons.cleanPlainText(str(card, "unit"))
        let delta = NexlyIcons.cleanPlainText(str(card, "delta", "change", "trend", "sub", "note"))
        let numericDelta = Double(delta.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "%$", with: "", options: .regularExpression).replacingOccurrences(of: "−", with: "-"))
        let explicitDirection = str(card, "direction").lowercased()
        let direction = !explicitDirection.isEmpty ? explicitDirection : numericDelta.map { $0.isFinite ? ($0 > 0 ? "up" : $0 < 0 ? "down" : "flat") : "" } ?? ""
        let tone = direction == "up" ? Color(red: 0.071, green: 0.604, blue: 0.353) : direction == "down" ? Color(red: 0.878, green: 0.310, blue: 0.373) : Aero.textSecondary
        return VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(Aero.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
            HStack(alignment: .firstTextBaseline, spacing: 3) {
                Text(value)
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundStyle(Color(red: 0.122, green: 0.094, blue: 0.259))
                    .fixedSize(horizontal: false, vertical: true)
                if !unit.isEmpty {
                    Text(unit).font(.system(size: 10, weight: .medium)).foregroundStyle(Aero.textSecondary)
                }
            }
            if !delta.isEmpty {
                HStack(spacing: 4) {
                    if direction == "up" || direction == "down" {
                        NexlyGlyph(name: direction == "up" ? "trending-up" : "trending-down", size: 12, color: tone)
                    }
                    Text(delta).font(.system(size: 10, weight: .bold)).foregroundStyle(tone)
                }
                .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white.opacity(0.82), in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(red: 0.886, green: 0.859, blue: 0.980), lineWidth: 1))
    }
}

private func iosTableEntityIconBadge(_ raw: String) -> (icon: String, tone: NexlyGlassTone)? {
    let clean = NexlyIcons.cleanPlainText(raw)
    guard !clean.isEmpty, clean.count <= 32, clean.contains(where: { $0.isLetter }) else { return nil }
    let lower = clean.lowercased()
    if lower.hasPrefix("python") { return ("brand-python", .blue) }
    if lower == "java" { return ("coffee", .amber) }
    if lower.hasPrefix("c++") || lower == "cpp" { return ("brand-cpp", .blue) }
    if lower == "c" || lower == "c#" { return ("code", .lavender) }
    if lower.hasPrefix("javascript") || lower == "js" { return ("brand-javascript", .amber) }
    if lower.hasPrefix("typescript") || lower == "ts" { return ("brand-typescript", .blue) }
    if lower.hasPrefix("rust") { return ("brand-rust", .peach) }
    if lower == "go" || lower == "golang" { return ("brand-golang", .mint) }
    if lower.hasPrefix("swift") { return ("brand-apple", .lavender) }
    if lower.hasPrefix("kotlin") || lower.hasPrefix("android") { return ("brand-android", .green) }
    return NexlyIcons.semanticIcon(for: raw, fallback: "sparkles")
}

private func parseWidgetTableNumIOS(_ raw: String) -> Double? {
    let s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        .replacingOccurrences(of: "^[↑↓↗↘+-]?\\s*[$€£¥₹৳]?\\s*", with: "", options: .regularExpression)
        .replacingOccurrences(of: "[%$,\\s]+$", with: "", options: .regularExpression)
        .replacingOccurrences(of: ",", with: "")
    guard !s.isEmpty else { return nil }
    var mult = 1.0
    var numPart = s
    if let last = s.last?.lowercased() {
        switch last {
        case "k": mult = 1_000.0; numPart = String(s.dropLast())
        case "m": mult = 1_000_000.0; numPart = String(s.dropLast())
        case "b": mult = 1_000_000_000.0; numPart = String(s.dropLast())
        case "t": mult = 1_000_000_000_000.0; numPart = String(s.dropLast())
        default: break
        }
    }
    guard let v = Double(numPart.trimmingCharacters(in: .whitespaces)) else { return nil }
    return (raw.contains("-") || raw.hasPrefix("↓") || raw.hasPrefix("↘")) && v > 0 ? -v * mult : v * mult
}

// A strict comparator must return false for equal cells in BOTH directions.
func widgetTablePrecedesIOS(_ left: String, _ right: String, ascending: Bool) -> Bool {
    if let a = parseWidgetTableNumIOS(left), let b = parseWidgetTableNumIOS(right) {
        return ascending ? a < b : a > b
    }
    return ascending ? left < right : left > right
}

func widgetTablePageSizeIOS(selection: Int, rowCount: Int) -> Int {
    if selection == -1 { return max(1, rowCount) }
    return selection > 0 ? selection : ((4...5).contains(rowCount) ? 3 : 5)
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
            if let s = $0 as? String { return NexlyIcons.cleanPlainText(s) }
            if let d = $0 as? [String: Any] { return str(d, "label", "title", "name", "key") }
            return NexlyIcons.cleanPlainText("\($0)")
        } ?? []
        let rawRows = ((obj["rows"] ?? obj["data"]) as? [Any]) ?? []
        let normRows: [[String]] = rawRows.map { row in
            if let arr = row as? [Any] { return arr.map { NexlyIcons.cleanPlainText("\($0)") } }
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
                return widgetTablePrecedesIOS(av, bv, ascending: sortAsc)
            }
        }()

        let effPageSize = widgetTablePageSizeIOS(selection: pageSize, rowCount: pageSize == 0 ? normRows.count : filteredRows.count)
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
                        ForEach(Array(cols.enumerated()), id: \.offset) { ci, col in
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
                        ForEach(Array(row.enumerated()), id: \.offset) { ci, cell in
                            let trimmed = cell.trimmingCharacters(in: .whitespacesAndNewlines)
                            let isTrend = trimmed.range(of: "^[↑↓↗↘+-]?\\s*[+-]?\\d+(?:\\.\\d+)?%$", options: .regularExpression) != nil &&
                                (trimmed.hasPrefix("+") || trimmed.hasPrefix("-") || trimmed.hasPrefix("↑") || trimmed.hasPrefix("↓") || (ci < cols.count && cols[ci].lowercased().contains("trend")))
                            let isNeg = isTrend && (trimmed.contains("-") || trimmed.hasPrefix("↓") || trimmed.hasPrefix("↘"))

                            if ci == primaryNameCol, let badge = iosTableEntityIconBadge(trimmed) {
                                HStack(spacing: 6) {
                                    NexlyGlassGlyphTile(
                                        name: badge.icon,
                                        tone: badge.tone,
                                        size: 21,
                                        glyphSize: 12
                                    )
                                    Text(NexlyIcons.cleanPlainText(trimmed))
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

                        ForEach(0..<totalPages, id: \.self) { (p: Int) in
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
                        switch pageSize == 0 ? effPageSize : pageSize {
                        case 3: pageSize = 5
                        case 5: pageSize = 10
                        case 10: pageSize = -1
                        default: pageSize = 3
                        }
                        page = 0
                    } label: {
                        Text("Show: \(pageSize == -1 ? "All" : String(effPageSize)) ▾")
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

    private func resolveCorrectIndex(q: [String: Any], opts: [String], rawOpts: [Any]) -> Int {
        for (i, ro) in rawOpts.enumerated() {
            if let d = ro as? [String: Any], (d["correct"] as? Bool) == true || (d["isCorrect"] as? Bool) == true {
                return i
            }
        }
        if let n = (q["answer"] ?? q["correct"] ?? q["correctIndex"]) as? NSNumber {
            let idx = n.intValue
            if idx >= 0 && idx < opts.count { return idx }
            if idx >= 1 && idx <= opts.count { return idx - 1 }
        }
        let ansStr = str(q, "answer", "correct", "correctAnswer").trimmingCharacters(in: .whitespacesAndNewlines)
        if !ansStr.isEmpty {
            if let idx = Int(ansStr) {
                if idx >= 0 && idx < opts.count { return idx }
                if idx >= 1 && idx <= opts.count { return idx - 1 }
            }
            if let exact = opts.firstIndex(where: { $0.caseInsensitiveCompare(ansStr) == .orderedSame }) {
                return exact
            }
            if ansStr.count == 1, let firstScalar = ansStr.uppercased().unicodeScalars.first {
                let letterIdx = Int(firstScalar.value) - 65
                if letterIdx >= 0 && letterIdx < opts.count { return letterIdx }
            }
            if let partial = opts.firstIndex(where: { $0.localizedCaseInsensitiveContains(ansStr) }) {
                return partial
            }
        }
        return 0
    }

    var body: some View {
        let questions: [[String: Any]] = {
            if let qs = (obj["questions"] ?? obj["items"]) as? [[String: Any]] { return qs }
            if obj["question"] != nil || obj["q"] != nil { return [obj] }
            return []
        }()
        VStack(alignment: .leading, spacing: 12) {
            ForEach(Array(questions.prefix(6).enumerated()), id: \.offset) { qIdx, q in
                let prompt = NexlyIcons.cleanPlainText(str(q, "q", "question", "prompt", "title"))
                let rawOpts = ((q["options"] ?? q["choices"] ?? q["answers"]) as? [Any]) ?? []
                let opts: [String] = rawOpts.map { ro in
                    if let s = ro as? String { return NexlyIcons.cleanPlainText(s) }
                    if let d = ro as? [String: Any] { return NexlyIcons.cleanPlainText(str(d, "label", "text", "title", "value")) }
                    return NexlyIcons.cleanPlainText("\(ro)")
                }
                let ans = resolveCorrectIndex(q: q, opts: opts, rawOpts: rawOpts)
                let explanation = NexlyIcons.cleanPlainText(str(q, "explanation", "rationale", "reason", "hint", "feedback"))
                VStack(alignment: .leading, spacing: 6) {
                    Text(prompt).font(.system(size: 13, weight: .semibold)).foregroundStyle(Aero.text)
                    ForEach(Array(opts.enumerated()), id: \.offset) { oIdx, opt in
                        let sel = picked[qIdx] == oIdx
                        let answered = picked[qIdx] != nil
                        let isRight = oIdx == ans
                        Button {
                            GSHaptics.select()
                            picked[qIdx] = oIdx
                        } label: {
                            HStack(spacing: 8) {
                                Text(opt).font(.system(size: 12, weight: sel ? .semibold : .regular)).foregroundStyle(Aero.text)
                                Spacer()
                                if sel || (answered && isRight) {
                                    NexlyGlyph(
                                        name: isRight ? "circle-check" : "circle-x",
                                        size: 14,
                                        color: isRight ? Color.green : Color.red
                                    )
                                }
                            }
                            .padding(8)
                            .background(
                                (sel ? (isRight ? Color.green.opacity(0.16) : Color.red.opacity(0.16)) : (answered && isRight ? Color.green.opacity(0.10) : Color.white.opacity(0.55))),
                                in: RoundedRectangle(cornerRadius: 10)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                    if picked[qIdx] != nil && !explanation.isEmpty {
                        HStack(alignment: .top, spacing: 6) {
                            NexlyGlyph(name: "bulb", size: 13, color: accent)
                            Text(explanation)
                                .font(.system(size: 11.5))
                                .foregroundStyle(Aero.textSecondary)
                        }
                        .padding(8)
                        .background(accent.opacity(0.08), in: RoundedRectangle(cornerRadius: 8))
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

func pollSourceVotesIOS(_ option: Any) -> Int? {
    guard let dict = option as? [String: Any] else { return option is String ? 0 : nil }
    guard let raw = dict["votes"] ?? dict["count"] ?? dict["value"] else { return 0 }
    guard let value = chartValueIOS(raw), value >= 0 else { return nil }
    return Int(exactly: value)
}

private struct PollBodyView: View {
    @Environment(\.gsWidgetAction) private var widgetAction
    let obj: [String: Any]
    let accent: Color
    @State private var voted: Int? = nil

    private var parsedOptions: [(label: String, votes: Int?)] {
        let raw = ((obj["options"] ?? obj["choices"] ?? obj["items"]) as? [Any]) ?? []
        return raw.map { opt in
            if let plain = opt as? String { return (NexlyIcons.cleanPlainText(plain), 0) }
            let dict = opt as? [String: Any] ?? [:]
            return (
                NexlyIcons.cleanPlainText(str(dict, "label", "title", "name", "text")),
                pollSourceVotesIOS(dict)
            )
        }
    }

    var body: some View {
        let opts = parsedOptions
        let totalBase = opts.reduce(0.0) { $0 + Double($1.votes ?? 0) } + (voted != nil ? 1 : 0)
        VStack(alignment: .leading, spacing: 6) {
            Text("Source counts, if supplied, plus your local choice. Not a live audience poll.")
                .font(Aero.caption()).foregroundStyle(Aero.textSecondary)
            if opts.contains(where: { $0.votes == nil }) {
                Text("Poll unavailable: supplied vote counts must be nonnegative whole numbers.")
                    .font(Aero.caption()).foregroundStyle(Aero.textSecondary)
            } else {
                ForEach(Array(opts.enumerated()), id: \.offset) { idx, opt in
                    let votes = Double(opt.votes ?? 0) + (voted == idx ? 1 : 0)
                    let pct = totalBase > 0 ? Int(round(votes / totalBase * 100.0)) : 0
                    Button {
                        GSHaptics.select()
                        voted = idx
                        widgetAction?(opt.label)
                    } label: {
                        HStack {
                            Text(opt.label).font(.system(size: 12, weight: voted == idx ? .bold : .regular)).foregroundStyle(Aero.text)
                            Spacer()
                            Text("\(String(format: "%.0f", votes)) (\(pct)%)").font(Aero.mono(11)).foregroundStyle(accent)
                        }
                        .padding(9)
                        .background(voted == idx ? accent.opacity(0.16) : Color.white.opacity(0.55), in: RoundedRectangle(cornerRadius: 10))
                    }
                    .buttonStyle(.plain)
                }
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
                ForEach(1...maxR, id: \.self) { (n: Int) in
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
                        NexlyGlyph(
                            name: kids.isEmpty ? "circle-dot" : (isCollapsed ? "chevron-right" : "chevron-down"),
                            size: 12,
                            color: accent
                        )
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

private struct ChecklistTaskIOS: Identifiable {
    let id: String
    var label: String
    var detail: String
    var done: Bool
}

private struct ChecklistBodyView: View {
    let obj: [String: Any]
    let accent: Color
    @State private var tasks: [ChecklistTaskIOS]
    @State private var editingID: String? = nil
    @State private var adding = false
    @State private var draft = ""

    init(obj: [String: Any], accent: Color) {
        self.obj = obj; self.accent = accent
        _tasks = State(initialValue: Self.sourceTasks(obj))
    }
    private static func sourceTasks(_ obj: [String: Any]) -> [ChecklistTaskIOS] {
        let items = ((obj["items"] ?? obj["tasks"] ?? obj["todos"]) as? [Any]) ?? []
        return items.enumerated().map { index, raw in
            let dict = raw as? [String: Any] ?? [:]
            let label = NexlyIcons.cleanPlainText((raw as? String) ?? str(dict, "label", "title", "text", "task", "name"))
            let rawDone = (dict["done"] ?? dict["checked"] ?? dict["completed"]) as? NSNumber
            let done = rawDone.map { CFGetTypeID($0) == CFBooleanGetTypeID() && $0.boolValue } ?? false
            let detail = [str(dict, "detail", "description", "subtitle"), str(dict, "meta"), str(dict, "badge", "priority"), str(dict, "date")]
                .filter { !$0.isEmpty }.map { NexlyIcons.cleanPlainText($0) }.joined(separator: " · ")
            return ChecklistTaskIOS(id: "source-\(index)", label: label, detail: detail, done: done)
        }
    }
    private var sourceIdentity: Data {
        (try? JSONSerialization.data(withJSONObject: obj, options: [.sortedKeys])) ?? Data()
    }
    private func cancelEdit() { editingID = nil; adding = false; draft = "" }
    private func saveTask() {
        let text = NexlyIcons.cleanPlainText(draft).trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        if let id = editingID, let index = tasks.firstIndex(where: { $0.id == id }) { tasks[index].label = text }
        else { tasks.append(ChecklistTaskIOS(id: UUID().uuidString, label: text, detail: "", done: false)) }
        cancelEdit()
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("\(tasks.filter(\.done).count) of \(tasks.count) completed")
                .font(.caption).foregroundStyle(Aero.textSecondary).accessibilityIdentifier("checklist.progress")
            ForEach(tasks) { task in
                HStack(spacing: 8) {
                    Button {
                        if let index = tasks.firstIndex(where: { $0.id == task.id }) { tasks[index].done.toggle() }
                        GSHaptics.select()
                    } label: {
                        HStack(spacing: 9) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 5).fill(task.done ? accent : Color.white.opacity(0.85)).frame(width: 18, height: 18)
                                RoundedRectangle(cornerRadius: 5).stroke(accent, lineWidth: 1).frame(width: 18, height: 18)
                                if task.done { NexlyGlyph(name: "check", size: 11, color: .white) }
                            }
                            VStack(alignment: .leading, spacing: 2) {
                                Text(task.label).strikethrough(task.done).foregroundStyle(task.done ? Aero.textSecondary : Aero.text)
                                if !task.detail.isEmpty { Text(task.detail).font(.caption).foregroundStyle(Aero.textSecondary) }
                            }
                            Spacer(minLength: 0)
                        }
                        .font(.system(size: 13)).padding(.vertical, 4)
                    }
                    .buttonStyle(.plain).accessibilityLabel(task.label + (task.detail.isEmpty ? "" : ", " + task.detail))
                    .accessibilityValue(task.done ? "Completed" : "Not completed")
                    Button { editingID = task.id; adding = false; draft = task.label } label: {
                        NexlyGlyph(name: "pencil", size: 13, color: accent)
                    }
                    .buttonStyle(.plain).accessibilityLabel("Edit task: \(task.label)")
                    Button {
                        tasks.removeAll { $0.id == task.id }
                        if editingID == task.id { cancelEdit() }
                    } label: { NexlyGlyph(name: "trash", size: 13, color: Aero.textSecondary) }
                    .buttonStyle(.plain).accessibilityLabel("Delete task: \(task.label)")
                }
            }
            if adding || editingID != nil {
                TextField("Task text", text: $draft).textFieldStyle(.roundedBorder)
                    .accessibilityIdentifier("checklist.task.input")
                    .onChange(of: draft) { value in
                        let clean = NexlyIcons.emojiToIconTokens(value).replacingOccurrences(of: ":([a-z0-9][a-z0-9-]{1,42}):", with: "", options: .regularExpression)
                        if clean != value { draft = clean }
                    }
                HStack {
                    Button(editingID == nil ? "Add" : "Save task") { saveTask() }
                        .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    Button("Cancel task edit") { cancelEdit() }
                }
                .font(.caption)
            } else {
                Button { adding = true; draft = "" } label: {
                    HStack(spacing: 5) {
                        NexlyGlyph(name: "plus", size: 11, color: accent)
                        Text("Add Task").font(.system(size: 11, weight: .bold))
                    }
                    .padding(.horizontal, 10).padding(.vertical, 5)
                    .background(Capsule().fill(Color.white.opacity(0.82)))
                    .overlay(Capsule().stroke(accent.opacity(0.2), lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
        }
        .onChange(of: sourceIdentity) { _ in tasks = Self.sourceTasks(obj); cancelEdit() }
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
    @Environment(\.gsWidgetAction) private var widgetAction
    let obj: [String: Any]
    let accent: Color
    @State private var expanded: Bool = true
    @State private var dismissed: Bool = false

    var body: some View {
        let pType = str(obj, "type", "variant").lowercased()
        let isCollapsible = ["collapsible", "drawer", "sheet", "modal", "popover"].contains(pType)
        let isDismissible = ["toast", "banner", "alert", "notice"].contains(pType)
        let badge = NexlyIcons.cleanPlainText(str(obj, "badge", "status", "tag", "severity"))
        let bodyText = NexlyIcons.cleanPlainText(str(obj, "body", "text", "content", "description", "message", "summary"))
        let actions = ((obj["actions"] ?? obj["buttons"] ?? obj["ctas"]) as? [Any]) ?? []

        if !dismissed {
            VStack(alignment: .leading, spacing: 8) {
                if !badge.isEmpty || isCollapsible || isDismissible {
                    HStack {
                        if !badge.isEmpty {
                            Text(badge.uppercased())
                                .font(.system(size: 10, weight: .bold))
                                .foregroundStyle(accent)
                                .padding(.horizontal, 7)
                                .padding(.vertical, 2)
                                .background(accent.opacity(0.14), in: Capsule())
                        }
                        Spacer()
                        if isCollapsible {
                            Button {
                                GSHaptics.select()
                                expanded.toggle()
                            } label: {
                                HStack(spacing: 4) {
                                    Text(expanded ? "Hide" : "Expand")
                                        .font(.system(size: 11, weight: .semibold))
                                        .foregroundStyle(accent)
                                    NexlyGlyph(name: expanded ? "chevron-up" : "chevron-down", size: 12, color: accent)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                        if isDismissible {
                            Button {
                                GSHaptics.select()
                                dismissed = true
                            } label: {
                                NexlyGlyph(name: "x", size: 13, color: Aero.textSecondary)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                if expanded {
                    if !bodyText.isEmpty {
                        Text(bodyText).font(.system(size: 13)).foregroundStyle(Aero.text)
                    }
                    if let items = (obj["items"] ?? obj["points"] ?? obj["bullets"]) as? [Any] {
                        ForEach(Array(items.enumerated()), id: \.offset) { _, it in
                            let line: String = {
                                if let s = it as? String { return NexlyIcons.cleanPlainText(s) }
                                if let d = it as? [String: Any] {
                                    let t = NexlyIcons.cleanPlainText(str(d, "title", "label", "name", "text"))
                                    let sub = NexlyIcons.cleanPlainText(str(d, "subtitle", "detail", "value", "description"))
                                    return sub.isEmpty ? t : "\(t) — \(sub)"
                                }
                                return NexlyIcons.cleanPlainText("\(it)")
                            }()
                            HStack(alignment: .top, spacing: 6) {
                                NexlyGlyph(name: "circle-check", size: 13, color: accent)
                                    .padding(.top, 1)
                                Text(line).font(.system(size: 12)).foregroundStyle(Aero.text)
                            }
                        }
                    }
                    if !actions.isEmpty {
                        HStack(spacing: 6) {
                            ForEach(Array(actions.prefix(4).enumerated()), id: \.offset) { _, act in
                                let lbl: String = {
                                    if let s = act as? String { return NexlyIcons.cleanPlainText(s) }
                                    if let d = act as? [String: Any] { return NexlyIcons.cleanPlainText(str(d, "label", "title", "text", "name")) }
                                    return "Action"
                                }()
                                let prompt: String = {
                                    if let d = act as? [String: Any] {
                                        let p = str(d, "prompt", "action", "value")
                                        if !p.isEmpty { return p }
                                    }
                                    return lbl
                                }()
                                Button {
                                    GSHaptics.select()
                                    widgetAction?(prompt)
                                } label: {
                                    Text(lbl)
                                        .font(.system(size: 11.5, weight: .bold))
                                        .foregroundStyle(.white)
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 5)
                                        .background(accent, in: Capsule())
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.top, 2)
                    }
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
    @State private var multiValues: [Int: Set<String>] = [:]
    @State private var errorMsg: String = ""
    @State private var submitted = false

    private func extractFieldOptions(_ f: [String: Any]) -> [String] {
        guard let raw = (f["options"] ?? f["choices"] ?? f["items"] ?? f["values"]) as? [Any] else { return [] }
        return raw.compactMap { item -> String? in
            if let s = item as? String { return NexlyIcons.cleanPlainText(s) }
            if let d = item as? [String: Any] {
                let s = NexlyIcons.cleanPlainText(str(d, "label", "title", "name", "value"))
                return s.isEmpty ? nil : s
            }
            return nil
        }
    }

    var body: some View {
        let fields = ((obj["fields"] ?? obj["inputs"] ?? obj["questions"]) as? [[String: Any]]) ?? []
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(fields.prefix(10).enumerated()), id: \.offset) { idx, f in
                formFieldRow(idx: idx, f: f)
            }
            if !errorMsg.isEmpty {
                Text(errorMsg)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(Color.red)
            }
            formSubmitButton(fields: fields)
        }
    }

    @ViewBuilder
    private func formFieldRow(idx: Int, f: [String: Any]) -> some View {
        let label = NexlyIcons.cleanPlainText(str(f, "label", "title", "name", "key"))
        let fType = str(f, "type").lowercased()
        let isRequired = (f["required"] as? Bool) == true
        let opts = extractFieldOptions(f)
        VStack(alignment: .leading, spacing: 4) {
            if fType == "checkbox" && !opts.isEmpty {
                formMultiCheckRow(idx: idx, label: label, isRequired: isRequired, opts: opts)
            } else if ["select", "dropdown", "radio", "segmented"].contains(fType) || !opts.isEmpty {
                formChoiceRow(idx: idx, f: f, label: label, isRequired: isRequired, opts: opts)
            } else if fType == "rating" {
                formRatingRow(idx: idx, f: f, label: label, isRequired: isRequired)
            } else if ["switch", "toggle", "checkbox", "boolean"].contains(fType) {
                Toggle(isRequired ? "\(label) *" : label, isOn: Binding(
                    get: { boolValues[idx] ?? ((f["value"] as? Bool) ?? false) },
                    set: { boolValues[idx] = $0 }
                ))
                .font(.system(size: 12, weight: .medium))
                .tint(accent)
            } else if ["slider", "range", "number"].contains(fType) {
                formSliderRow(idx: idx, f: f, label: label, isRequired: isRequired)
            } else if fType == "file" {
                formFileRow(idx: idx, label: label, isRequired: isRequired)
            } else {
                Text(isRequired ? "\(label) *" : label).font(.system(size: 12, weight: .medium)).foregroundStyle(Aero.text)
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

    @ViewBuilder
    private func formMultiCheckRow(idx: Int, label: String, isRequired: Bool, opts: [String]) -> some View {
        Text(isRequired ? "\(label) *" : label).font(.system(size: 12, weight: .medium)).foregroundStyle(Aero.text)
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(opts, id: \.self) { (opt: String) in
                    let active = multiValues[idx, default: []].contains(opt)
                    Button {
                        GSHaptics.select()
                        var set = multiValues[idx, default: []]
                        if active { set.remove(opt) } else { set.insert(opt) }
                        multiValues[idx] = set
                    } label: {
                        HStack(spacing: 4) {
                            NexlyGlyph(name: active ? "checkbox" : "square", size: 12, color: active ? .white : accent)
                            Text(opt).font(.system(size: 11.5, weight: .semibold)).foregroundStyle(active ? .white : Aero.text)
                        }
                        .padding(.horizontal, 9)
                        .padding(.vertical, 5)
                        .background(active ? accent : Color.white.opacity(0.65), in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    @ViewBuilder
    private func formChoiceRow(idx: Int, f: [String: Any], label: String, isRequired: Bool, opts: [String]) -> some View {
        let curChoice = textValues[idx] ?? str(f, "value", "default").ifEmptyIOS(opts.first ?? "")
        Text(isRequired ? "\(label) *" : label).font(.system(size: 12, weight: .medium)).foregroundStyle(Aero.text)
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(opts, id: \.self) { (opt: String) in
                    let active = curChoice == opt
                    Button {
                        GSHaptics.select()
                        textValues[idx] = opt
                    } label: {
                        Text(opt)
                            .font(.system(size: 11.5, weight: .semibold))
                            .foregroundStyle(active ? .white : Aero.text)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(active ? accent : Color.white.opacity(0.65), in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    @ViewBuilder
    private func formRatingRow(idx: Int, f: [String: Any], label: String, isRequired: Bool) -> some View {
        let maxStars = min(max(Int(dbl(f, "max", fallback: 5)), 1), 10)
        let curStars = Int(numValues[idx] ?? dbl(f, "value", "default", fallback: 0))
        HStack {
            Text(isRequired ? "\(label) *" : label).font(.system(size: 12, weight: .medium)).foregroundStyle(Aero.text)
            Spacer()
            HStack(spacing: 3) {
                ForEach(0..<maxStars, id: \.self) { (sIdx: Int) in
                    let s = sIdx + 1
                    Button {
                        GSHaptics.select()
                        numValues[idx] = Double(s)
                    } label: {
                        NexlyGlyph(
                            name: s <= curStars ? "star-filled" : "star",
                            size: 16,
                            color: s <= curStars ? accent : Aero.textTertiary.opacity(0.45)
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    @ViewBuilder
    private func formSliderRow(idx: Int, f: [String: Any], label: String, isRequired: Bool) -> some View {
        let minV = dbl(f, "min", fallback: 0)
        let maxV = max(dbl(f, "max", fallback: 100), minV + 1)
        let stepV = max(dbl(f, "step", fallback: 1), 0.01)
        let cur = numValues[idx] ?? dbl(f, "value", "default", fallback: minV)
        let unit = str(f, "unit")
        HStack {
            Text(isRequired ? "\(label) *" : label).font(.system(size: 12, weight: .medium)).foregroundStyle(Aero.text)
            Spacer()
            Text(stepV >= 1 ? String(format: "%.0f%@", cur, unit) : String(format: "%.1f%@", cur, unit))
                .font(Aero.mono(11))
                .foregroundStyle(accent)
        }
        Slider(value: Binding(
            get: { min(max(cur, minV), maxV) },
            set: { (newVal: Double) in
                let snapped = round((newVal - minV) / stepV) * stepV + minV
                numValues[idx] = min(max(snapped, minV), maxV)
            }
        ), in: minV...maxV)
        .tint(accent)
    }

    @ViewBuilder
    private func formFileRow(idx: Int, label: String, isRequired: Bool) -> some View {
        let presets = ["Document.pdf", "Dataset.csv", "Image.png", "Report.xlsx"]
        let current = textValues[idx] ?? ""
        Text(isRequired ? "\(label) *" : label).font(.system(size: 12, weight: .medium)).foregroundStyle(Aero.text)
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(presets, id: \.self) { (fileOpt: String) in
                    let active = current == fileOpt
                    Button {
                        GSHaptics.select()
                        textValues[idx] = active ? "" : fileOpt
                    } label: {
                        HStack(spacing: 4) {
                            NexlyGlyph(name: active ? "circle-check" : "file", size: 12, color: active ? .white : accent)
                            Text(fileOpt).font(.system(size: 11.5, weight: .semibold)).foregroundStyle(active ? .white : Aero.text)
                        }
                        .padding(.horizontal, 9)
                        .padding(.vertical, 5)
                        .background(active ? accent : Color.white.opacity(0.65), in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    @ViewBuilder
    private func formSubmitButton(fields: [[String: Any]]) -> some View {
        Button {
            GSHaptics.select()
            var missingLabels: [String] = []
            for (idx, f) in fields.prefix(10).enumerated() {
                guard (f["required"] as? Bool) == true else { continue }
                let lbl = NexlyIcons.cleanPlainText(str(f, "label", "title", "name", "key"))
                let fType = str(f, "type").lowercased()
                let opts = extractFieldOptions(f)
                if fType == "checkbox" && !opts.isEmpty {
                    if multiValues[idx, default: []].isEmpty { missingLabels.append(lbl) }
                } else if !["switch", "toggle", "checkbox", "boolean", "slider", "range", "number", "select", "dropdown", "radio", "segmented"].contains(fType) && opts.isEmpty {
                    let v = (textValues[idx] ?? str(f, "value", "default")).trimmingCharacters(in: .whitespacesAndNewlines)
                    if v.isEmpty { missingLabels.append(lbl) }
                }
            }
            if !missingLabels.isEmpty {
                errorMsg = "Required: \(missingLabels.joined(separator: ", "))"
                submitted = false
                return
            }
            errorMsg = ""
            submitted = true
            let summary = fields.prefix(10).enumerated().map { idx, f in
                let lbl = NexlyIcons.cleanPlainText(str(f, "label", "title", "name", "key"))
                let fType = str(f, "type").lowercased()
                let opts = extractFieldOptions(f)
                if fType == "checkbox" && !opts.isEmpty {
                    return "\(lbl): \(multiValues[idx, default: []].sorted().joined(separator: ", "))"
                } else if ["switch", "toggle", "checkbox", "boolean"].contains(fType) {
                    return "\(lbl): \((boolValues[idx] ?? ((f["value"] as? Bool) ?? false)) ? "Yes" : "No")"
                } else if ["slider", "range", "number", "rating"].contains(fType) {
                    let cur = numValues[idx] ?? dbl(f, "value", "default", fallback: dbl(f, "min", fallback: 0))
                    return String(format: "%@: %.1f", lbl, cur)
                } else {
                    let defChoice = str(f, "value", "default").ifEmptyIOS(opts.first ?? "")
                    return "\(lbl): \(textValues[idx] ?? defChoice)"
                }
            }.joined(separator: ", ")
            if !summary.isEmpty {
                widgetAction?(summary)
            }
        } label: {
            HStack(spacing: 5) {
                if submitted {
                    NexlyGlyph(name: "circle-check", size: 12, color: .white)
                }
                Text(submitted ? "Submitted" : (str(obj, "submitLabel").isEmpty ? "Submit" : str(obj, "submitLabel")))
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(.white)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 7)
            .background(accent, in: Capsule())
        }
        .buttonStyle(.plain)
    }
}

private extension String {
    func ifEmptyIOS(_ fallback: String) -> String {
        isEmpty ? fallback : self
    }
}

/// Safe recursive-descent math expression evaluator for `calc` widgets on iOS (parity with Android `CalcExpr.eval`).
enum CalcExprIOS {
    static func evalOptional(_ expr: String, vars: [String: Double]) -> Double? {
        let trimmed = expr.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty,
              trimmed.range(of: "^[0-9a-zA-Z_+\\-*/%^().,\\s]+$", options: .regularExpression) != nil else {
            return nil
        }
        if let rx = try? NSRegularExpression(pattern: "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b") {
            let ns = trimmed as NSString
            let knownFns: Set<String> = ["sqrt", "abs", "round", "floor", "ceil", "min", "max", "pow", "log", "log10", "ln", "exp", "sin", "cos", "tan", "pi", "e"]
            for m in rx.matches(in: trimmed, range: NSRange(location: 0, length: ns.length)) {
                let ident = ns.substring(with: m.range(at: 1))
                if !knownFns.contains(ident.lowercased()) && vars[ident] == nil && vars[ident.lowercased()] == nil {
                    return nil
                }
            }
        }
        let v = eval(trimmed, vars: vars)
        return v.isFinite ? v : nil
    }

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
            var left = parseUnary()
            while true {
                skipWs()
                guard pos < chars.count else { break }
                if pos + 1 < chars.count && chars[pos] == "*" && chars[pos + 1] == "*" { break }
                let op = chars[pos]
                if op == "*" { pos += 1; left *= parseUnary() }
                else if op == "/" {
                    pos += 1
                    let r = parseUnary()
                    left = r == 0.0 ? Double.nan : left / r
                } else if op == "%" {
                    pos += 1
                    let r = parseUnary()
                    left = r == 0.0 ? Double.nan : left.truncatingRemainder(dividingBy: r)
                } else { break }
            }
            return left
        }

        func parsePower() -> Double {
            let base = parsePrimary()
            skipWs()
            if pos < chars.count && chars[pos] == "^" {
                pos += 1
                return pow(base, parseUnary())
            }
            if pos + 1 < chars.count && chars[pos] == "*" && chars[pos + 1] == "*" {
                pos += 2
                return pow(base, parseUnary())
            }
            return base
        }

        func parseUnary() -> Double {
            skipWs()
            if pos < chars.count && chars[pos] == "-" { pos += 1; return -parseUnary() }
            if pos < chars.count && chars[pos] == "+" { pos += 1; return parseUnary() }
            return parsePower()
        }

        func parsePrimary() -> Double {
            skipWs()
            guard pos < chars.count else { return Double.nan }
            if chars[pos] == "(" {
                pos += 1
                let v = parseAddSub()
                skipWs()
                guard pos < chars.count && chars[pos] == ")" else { return Double.nan }; pos += 1
                return v
            }
            if chars[pos].isNumber || chars[pos] == "." {
                let start = pos
                while pos < chars.count && (chars[pos].isNumber || chars[pos] == ".") { pos += 1 }
                return Double(String(chars[start..<pos])) ?? Double.nan
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
                    guard pos < chars.count && chars[pos] == ")" else { return Double.nan }; pos += 1
                    let a = args.first ?? Double.nan
                    let b = args.count > 1 ? args[1] : 0.0
                    switch ident.lowercased() {
                    case "sqrt": return a >= 0 ? sqrt(a) : Double.nan
                    case "abs": return abs(a)
                    case "round": return round(a)
                    case "floor": return floor(a)
                    case "ceil": return ceil(a)
                    case "min": return args.min() ?? Double.nan
                    case "max": return args.max() ?? Double.nan
                    case "pow": return pow(a, b)
                    case "ln": return a > 0 ? log(a) : Double.nan
                    case "log", "log10": return a > 0 ? log10(a) : Double.nan
                    case "exp": return exp(a)
                    case "sin": return sin(a)
                    case "cos": return cos(a)
                    case "tan": return tan(a)
                    default: return Double.nan
                    }
                }
                if ident.lowercased() == "pi" { return Double.pi }
                if ident.lowercased() == "e" { return M_E }
                return vars[ident] ?? vars[ident.lowercased()] ?? Double.nan
            }
            pos += 1
            return Double.nan
        }

        let res = parseAddSub()
        skipWs()
        guard pos == chars.count else { return Double.nan }
        return res.isFinite ? res : Double.nan
    }
}

/// Percent format takes ratios; a '%' unit without that format takes points.
func calcPercentDisplayIOS(_ value: Double, format: String) -> Double {
    ["percent", "pct"].contains(format.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)) ? value * 100 : value
}

private struct CalcBodyView: View {
    let obj: [String: Any]
    let accent: Color
    @State private var vals: [String: Double] = [:]
    @State private var drafts: [String: String] = [:]

    private func formatCalcResult(_ n: Double, unit: String, format: String = "") -> String {
        guard n.isFinite else { return "Undefined for these inputs" }
        let uLow = unit.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        let fLow = format.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        let isCurrency = ["money", "currency", "usd"].contains(fLow) || ["currency", "money", "usd", "$", "€", "£", "¥"].contains(uLow)
        let isPercent = ["percent", "pct", "%"].contains(fLow) || ["percent", "pct", "%"].contains(uLow)
        let prefix: String = {
            if ["$", "€", "£", "¥"].contains(unit.trimmingCharacters(in: .whitespacesAndNewlines)) {
                return unit.trimmingCharacters(in: .whitespacesAndNewlines)
            }
            return isCurrency ? "$" : ""
        }()
        let suffix: String = {
            if isPercent { return "%" }
            if isCurrency || unit.isEmpty { return "" }
            return " \(unit)"
        }()
        let displayNum = calcPercentDisplayIOS(n, format: fLow)
        let formatted: String = {
            if displayNum.truncatingRemainder(dividingBy: 1.0) == 0 && !isCurrency {
                return String(format: "%.0f", displayNum)
            }
            return String(format: "%.2f", displayNum)
        }()
        return "\(prefix)\(formatted)\(suffix)"
    }

    private var calcFields: [[String: Any]] {
        ((obj["fields"] ?? obj["sliders"] ?? obj["inputs"] ?? obj["variables"] ?? obj["params"]) as? [[String: Any]]) ?? []
    }

    private var calcOutputs: [[String: Any]] {
        if let outs = (obj["outputs"] ?? obj["results"]) as? [[String: Any]], !outs.isEmpty {
            return outs
        }
        let topFormula = str(obj, "formula", "expression", "expr")
        if !topFormula.isEmpty {
            let topLbl = str(obj, "outputLabel", "resultLabel").ifEmptyIOS("Result")
            return [["label": topLbl, "expr": topFormula, "unit": str(obj, "unit", "currency")]]
        }
        return []
    }

    private func computeActiveVars(fields: [[String: Any]]) -> [String: Double] {
        var map: [String: Double] = [:]
        for (idx, f) in fields.prefix(10).enumerated() {
            let rawKey = str(f, "id", "key", "name")
            let key = rawKey.isEmpty ? "x\(idx + 1)" : rawKey
            let choices = f["choices"] as? [[String: Any]] ?? []
            let initial = dbl(f, "value", "default", fallback: choices.first.map { dbl($0, "value", fallback: .nan) } ?? .nan)
            let cur = vals[key] ?? initial
            map[key] = cur
            map[key.lowercased()] = cur
            map["x\(idx + 1)"] = cur
        }
        return map
    }

    var body: some View {
        let fields = calcFields
        let outputs = calcOutputs
        let activeVars = computeActiveVars(fields: fields)

        VStack(alignment: .leading, spacing: 10) {
            if !vals.isEmpty {
                HStack {
                    Spacer()
                    Button("Reset") {
                        GSHaptics.select()
                        vals.removeAll()
                        drafts.removeAll()
                    }
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(accent)
                    .buttonStyle(.plain)
                }
            }
            ForEach(Array(fields.prefix(8).enumerated()), id: \.offset) { idx, f in
                calcFieldRow(idx: idx, f: f, activeVars: activeVars)
            }
            ForEach(Array(outputs.prefix(6).enumerated()), id: \.offset) { _, out in
                calcOutputRow(out: out, activeVars: activeVars)
            }
        }
    }

    @ViewBuilder
    private func calcFieldRow(idx: Int, f: [String: Any], activeVars: [String: Double]) -> some View {
        let label = NexlyIcons.cleanPlainText(str(f, "label", "title", "name", "key"))
        let rawKey = str(f, "id", "key", "name")
        let key = rawKey.isEmpty ? "x\(idx + 1)" : rawKey
        let minV = dbl(f, "min", fallback: -.greatestFiniteMagnitude)
        let maxV = dbl(f, "max", fallback: .greatestFiniteMagnitude)
        let hasRange = f["min"] != nil && f["max"] != nil && minV.isFinite && maxV.isFinite && maxV > minV
        let rawStep = max(dbl(f, "step", fallback: 0), 0)
        let effStep = rawStep > 0 ? rawStep : (hasRange ? (maxV - minV) / 100 : 1)
        let cur = activeVars[key] ?? minV
        let rawUnit = str(f, "unit")
        let unit = ["currency", "money", "usd"].contains(rawUnit.lowercased()) ? "$" : rawUnit
        let choices = (f["choices"] as? [[String: Any]]) ?? []
        VStack(alignment: .leading, spacing: 3) {
            if !choices.isEmpty {
                Text(label).font(.system(size: 12, weight: .medium)).foregroundStyle(Aero.text)
                HStack(spacing: 4) {
                    ForEach(Array(choices.prefix(5).enumerated()), id: \.offset) { _, ch in
                        let cLbl = NexlyIcons.cleanPlainText(str(ch, "label", "title", "name"))
                        let cVal = dbl(ch, "value", fallback: 0)
                        let selected = abs(cur - cVal) < 1e-6
                        Button {
                            GSHaptics.select()
                            vals[key] = cVal
                        } label: {
                            Text(cLbl)
                                .font(.system(size: 11, weight: .bold))
                                .foregroundStyle(selected ? .white : Aero.text)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 5)
                                .frame(maxWidth: .infinity)
                                .background(selected ? accent : Color.white.opacity(0.65), in: RoundedRectangle(cornerRadius: 8))
                        }
                        .buttonStyle(.plain)
                    }
                }
            } else {
                HStack {
                    Text(label).font(.system(size: 12, weight: .medium)).foregroundStyle(Aero.text)
                    Spacer()
                    TextField(
                        "0",
                        text: Binding(
                            get: {
                                drafts[key] ?? (cur.isFinite ? String(cur) : "")
                            },
                            set: { (s: String) in
                                drafts[key] = s
                                vals[key] = Double(s) ?? .nan
                            }
                        )
                    )
                    .accessibilityIdentifier("calc-input-\(key)")
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
                if hasRange {
                Slider(
                    value: Binding(
                        get: { cur.isFinite ? min(max(cur, minV), maxV) : minV },
                        set: { (newVal: Double) in
                            let snapped = round((newVal - minV) / effStep) * effStep + minV
                            vals[key] = min(max(snapped, minV), maxV)
                            drafts.removeValue(forKey: key)
                        }
                    ),
                    in: minV...maxV
                )
                .tint(accent)
                }
            }
        }
    }

    @ViewBuilder
    private func calcOutputRow(out: [String: Any], activeVars: [String: Double]) -> some View {
        let label = NexlyIcons.cleanPlainText(str(out, "label", "title", "name"))
        let expr = str(out, "expr", "formula", "value")
        let unit = str(out, "unit")
        let fmt = str(out, "format")
        let evaluated: String = {
            if expr.isEmpty { return "Missing formula" }
            for (index, field) in calcFields.prefix(10).enumerated() {
                let rawKey = str(field, "id", "key", "name")
                let key = rawKey.isEmpty ? "x\(index + 1)" : rawKey
                let value = activeVars[key] ?? .nan
                if !value.isFinite || value < dbl(field, "min", fallback: -.greatestFiniteMagnitude)
                    || value > dbl(field, "max", fallback: .greatestFiniteMagnitude) {
                    return "Enter valid inputs"
                }
            }
            if let literal = Double(expr) { return formatCalcResult(literal, unit: unit, format: fmt) }
            let val = CalcExprIOS.eval(expr, vars: activeVars)
            return formatCalcResult(val, unit: unit, format: fmt)
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

private struct BookingBodyView: View {
    let obj: [String: Any]
    let accent: Color
    @State private var day: String? = nil
    @State private var slot: String? = nil
    @State private var saved = false
    private var sourceIdentity: Data { (try? JSONSerialization.data(withJSONObject: obj, options: [.sortedKeys])) ?? Data() }
    var body: some View {
        let slots = (obj["slots"] as? [String] ?? []).filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        let days = (obj["items"] as? [[String: Any]] ?? []).map { str($0, "title", "label", "date") }.filter { !$0.isEmpty }
        let ready = (days.isEmpty || day != nil) && (slots.isEmpty || slot != nil) && (day != nil || slot != nil)
        VStack(alignment: .leading, spacing: 10) {
            Text("Local selection only. No booking service is connected and no reservation will be made.").font(.caption).foregroundStyle(Aero.textSecondary)
            ForEach(Array(days.enumerated()), id: \.offset) { _, value in
                Button { day = value; saved = false } label: {
                    HStack {
                        Text(NexlyIcons.cleanPlainText(value))
                        if day == value { NexlyGlyph(name: "check", size: 12, color: accent) }
                    }
                }
                .accessibilityValue(day == value ? "Selected" : "Not selected")
            }
            ForEach(Array(slots.enumerated()), id: \.offset) { _, value in
                Button { slot = value; saved = false } label: {
                    HStack {
                        Text(NexlyIcons.cleanPlainText(value))
                        if slot == value { NexlyGlyph(name: "check", size: 12, color: accent) }
                    }
                }
                .accessibilityValue(slot == value ? "Selected" : "Not selected")
            }
            Button(str(obj, "submitLabel").isEmpty ? "Save slot locally" : str(obj, "submitLabel")) { saved = true }
                .disabled(!ready)
            if saved {
                Text("Saved locally: \([day, slot].compactMap { $0 }.joined(separator: " at ")). Preview only; no reservation has been made.")
                    .font(.caption).accessibilityIdentifier("booking.local.receipt")
            }
        }
        .onChange(of: sourceIdentity) { _ in day = nil; slot = nil; saved = false }
    }
}

/// User-configured or source-timed countdown, not a price or a simulated timer.
private struct CountdownBodyView: View {
    let obj: [String: Any]
    let accent: Color
    @State private var durationText: String
    @State private var left: Double
    @State private var running = false
    @State private var started = false
    @State private var deadline: ContinuousClock.Instant?

    private static func sourceSeconds(_ obj: [String: Any]) -> Int? {
        guard let number = obj["durationSeconds"] as? NSNumber,
              CFGetTypeID(number) != CFBooleanGetTypeID() else { return nil }
        let value = number.doubleValue
        guard value.isFinite, value.rounded() == value, value >= 1, value <= 604800 else { return nil }
        return Int(value)
    }
    init(obj: [String: Any], accent: Color) {
        self.obj = obj; self.accent = accent
        let seconds = Self.sourceSeconds(obj)
        _durationText = State(initialValue: seconds.map { String($0) } ?? "")
        _left = State(initialValue: Double(seconds ?? 0))
    }
    private var duration: Int? {
        guard let value = Int(durationText), (1...604800).contains(value) else { return nil }
        return value
    }
    private var sourceIdentity: Data { (try? JSONSerialization.data(withJSONObject: obj, options: [.sortedKeys])) ?? Data() }
    private static func remaining(until deadline: ContinuousClock.Instant) -> Double {
        let parts = ContinuousClock().now.duration(to: deadline).components
        return max(0, Double(parts.seconds) + Double(parts.attoseconds) / 1e18)
    }
    var body: some View {
        VStack(spacing: 12) {
            if obj["durationSeconds"] != nil && Self.sourceSeconds(obj) == nil {
                Text("Timer durationSeconds must be whole seconds from 1 through 604800. Source durations are not clamped or taken from prices.")
            } else {
                let seconds = Int(ceil(left))
                let fraction = min(1, max(0, left / Double(duration ?? 1)))
                ZStack {
                    Circle().stroke(accent.opacity(0.12), lineWidth: 11)
                    Circle().trim(from: 0, to: fraction).stroke(accent, style: StrokeStyle(lineWidth: 11, lineCap: .round)).rotationEffect(.degrees(-90))
                    Text(String(format: "%02d:%02d", seconds / 60, seconds % 60))
                        .font(.system(size: 26, weight: .bold, design: .monospaced))
                        .accessibilityIdentifier("timer.remaining")
                }.frame(width: 150, height: 150)
                TextField("Duration (seconds)", text: $durationText)
                    .keyboardType(.numberPad).textFieldStyle(.roundedBorder)
                    .accessibilityIdentifier("timer.duration")
                    .disabled(running || started)
                if duration == nil {
                    Text("Enter whole seconds from 1 through 604800 to configure this local countdown.").font(.caption)
                }
                HStack(spacing: 20) {
                    Button(running ? "Pause" : "Start") {
                        if running {
                            if let deadline { left = Self.remaining(until: deadline) }
                            deadline = nil; running = false
                        } else {
                            deadline = ContinuousClock().now.advanced(by: .seconds(left))
                            started = true; running = true
                        }
                    }.disabled(duration == nil || left <= 0).accessibilityIdentifier("timer.toggle")
                    Button("Reset") {
                        running = false; started = false; deadline = nil; left = Double(duration ?? 0)
                    }.accessibilityIdentifier("timer.reset")
                }
                Text("Local countdown only. No background alarm or notification is scheduled.").font(.caption)
                if started && left <= 0 { Text("Done — countdown completed.").foregroundStyle(accent).accessibilityIdentifier("timer.completed") }
            }
        }
        .onChange(of: durationText) { _ in if !started && !running { left = Double(duration ?? 0) } }
        .onChange(of: sourceIdentity) { _ in
            running = false; started = false; deadline = nil
            let seconds = Self.sourceSeconds(obj)
            durationText = seconds.map { String($0) } ?? ""; left = Double(seconds ?? 0)
        }
        .task(id: running) {
            guard running else { return }
            while !Task.isCancelled, let target = deadline {
                left = Self.remaining(until: target)
                if left <= 0 { running = false; deadline = nil; break }
                do { try await Task.sleep(nanoseconds: 200_000_000) } catch { break }
            }
        }
    }
}

private struct AppBodyView: View {
    @Environment(\.gsWidgetAction) private var widgetAction
    let obj: [String: Any]
    let accent: Color
    @State private var columnOverrides: [String: Int] = [:]
    @State private var stepIdx: Int = 0
    @State private var selectedSlot: Int = 0
    @State private var selectedConfig: [Int: Int] = [:]

    private struct KanbanCardItem: Identifiable {
        let id: String
        let origCol: Int
        let label: String
        let subtitle: String
        let badge: String
    }

    var body: some View {
        let aType = str(obj, "type", "layout").lowercased()
        let cols = (obj["columns"] ?? obj["lanes"] ?? obj["boards"]) as? [[String: Any]] ?? []
        let steps = (obj["steps"] ?? obj["stages"] ?? obj["pages"]) as? [[String: Any]] ?? []
        let groups = (obj["groups"] ?? obj["categories"] ?? obj["tiers"]) as? [[String: Any]] ?? []
        let items = (obj["items"] ?? obj["cards"] ?? obj["metrics"] ?? obj["kpis"]) as? [[String: Any]] ?? []

        if aType == "timer" {
            CountdownBodyView(obj: obj, accent: accent)
        } else if aType == "tasks" {
            ChecklistBodyView(obj: obj, accent: accent)
        } else if ["booking", "schedule"].contains(aType) {
            BookingBodyView(obj: obj, accent: accent)
        } else if !cols.isEmpty || aType == "kanban" {
            kanbanView(cols: cols)
        } else if ["wizard", "onboarding", "stepper", "decision"].contains(aType) && !steps.isEmpty {
            wizardView(steps: steps)
        } else if ["config", "configurator", "pricing"].contains(aType) && !groups.isEmpty {
            configuratorView(groups: groups)
        } else if !items.isEmpty {
            kpiGridView(items: items)
        } else if (obj["rows"] ?? obj["data"]) is [Any] {
            TableBodyView(obj: obj, accent: accent)
        } else {
            GenericStructuredBodyView(kind: "app", obj: obj, accent: accent)
        }
    }

    @ViewBuilder
    private func kanbanView(cols: [[String: Any]]) -> some View {
        let numCols = max(min(cols.count, 5), 1)
        let allCards: [KanbanCardItem] = cols.prefix(5).enumerated().flatMap { cIdx, col -> [KanbanCardItem] in
            let colItems = (col["items"] ?? col["cards"] ?? col["tasks"]) as? [Any] ?? []
            return colItems.prefix(12).enumerated().map { iIdx, it in
                let dict = (it as? [String: Any]) ?? [:]
                let lbl = NexlyIcons.cleanPlainText((it as? String) ?? str(dict, "title", "label", "text", "task", "name"))
                let sub = NexlyIcons.cleanPlainText(str(dict, "subtitle", "description", "owner"))
                let badge = NexlyIcons.cleanPlainText(str(dict, "badge", "priority", "tag", "status"))
                return KanbanCardItem(id: "c\(cIdx)_\(iIdx)", origCol: cIdx, label: lbl, subtitle: sub, badge: badge)
            }
        }
        VStack(alignment: .leading, spacing: 6) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 8) {
                    ForEach(0..<numCols, id: \.self) { (cIdx: Int) in
                        let col = cols[cIdx]
                        let colTitle = NexlyIcons.cleanPlainText(str(col, "title", "name", "label", "status"))
                        let colCards = allCards.filter { (columnOverrides[$0.id] ?? $0.origCol) == cIdx }
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text(colTitle.uppercased())
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundStyle(WidgetPalette.at(cIdx))
                                Spacer()
                                Text("\(colCards.count)")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundStyle(Aero.textSecondary)
                            }
                            ForEach(colCards) { (card: KanbanCardItem) in
                                Button {
                                    guard numCols > 1 else { return }
                                    GSHaptics.select()
                                    let cur = columnOverrides[card.id] ?? card.origCol
                                    columnOverrides[card.id] = (cur + 1) % numCols
                                } label: {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(card.label).font(.system(size: 12, weight: .medium)).foregroundStyle(Aero.text)
                                        if !card.subtitle.isEmpty {
                                            Text(card.subtitle).font(.system(size: 10.5)).foregroundStyle(Aero.textSecondary)
                                        }
                                        if !card.badge.isEmpty {
                                            Text(card.badge).font(.system(size: 10, weight: .bold)).foregroundStyle(accent)
                                        }
                                    }
                                    .padding(8)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .background(Color.white.opacity(0.68), in: RoundedRectangle(cornerRadius: 8))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(8)
                        .frame(width: 148, alignment: .leading)
                        .background(Color.white.opacity(0.42), in: RoundedRectangle(cornerRadius: 10))
                    }
                }
            }
            Text("Tap a card to advance it to the next column")
                .font(.system(size: 10.5))
                .foregroundStyle(Aero.textSecondary)
        }
    }

    @ViewBuilder
    private func wizardView(steps: [[String: Any]]) -> some View {
        let safeStep = min(max(0, stepIdx), steps.count - 1)
        let cur = steps[safeStep]
        let title = NexlyIcons.cleanPlainText(str(cur, "title", "label", "name"))
        let bodyText = NexlyIcons.cleanPlainText(str(cur, "body", "description", "text", "detail"))
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 4) {
                ForEach(0..<steps.count, id: \.self) { (idx: Int) in
                    Capsule()
                        .fill(idx <= safeStep ? accent : Aero.outline.opacity(0.3))
                        .frame(height: 4)
                }
            }
            Text("Step \(safeStep + 1) of \(steps.count) · \(title)")
                .font(.system(size: 12.5, weight: .bold))
                .foregroundStyle(Aero.text)
            if !bodyText.isEmpty {
                Text(bodyText)
                    .font(.system(size: 12))
                    .foregroundStyle(Aero.textSecondary)
            }
            HStack {
                Button {
                    GSHaptics.select()
                    stepIdx = max(0, safeStep - 1)
                } label: {
                    Text("← Back")
                        .font(.system(size: 11.5, weight: .semibold))
                        .foregroundStyle(safeStep > 0 ? Aero.text : Aero.textTertiary)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(Color.white.opacity(0.65), in: Capsule())
                }
                .disabled(safeStep == 0)
                .buttonStyle(.plain)

                Spacer()

                Button {
                    GSHaptics.select()
                    if safeStep < steps.count - 1 {
                        stepIdx = safeStep + 1
                    } else {
                        widgetAction?("Completed \(str(obj, "title").ifEmptyIOS("wizard"))")
                    }
                } label: {
                    Text(safeStep < steps.count - 1 ? "Next" : "Finish")
                        NexlyGlyph(name: safeStep < steps.count - 1 ? "arrow-right" : "check", size: 12, color: accent)
                        .font(.system(size: 11.5, weight: .bold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 5)
                        .background(accent, in: Capsule())
                }
                .buttonStyle(.plain)
            }
        }
    }

    @ViewBuilder
    private func configuratorView(groups: [[String: Any]]) -> some View {
        let basePrice = dbl(obj, "basePrice", "price", fallback: 0)
        let total: Double = groups.enumerated().reduce(basePrice) { acc, pair in
            let (gIdx, g) = pair
            let opts = (g["options"] ?? g["items"] ?? g["choices"]) as? [[String: Any]] ?? []
            let selIdx = min(max(0, selectedConfig[gIdx] ?? 0), max(0, opts.count - 1))
            guard selIdx < opts.count else { return acc }
            return acc + dbl(opts[selIdx], "price", "cost", "delta", fallback: 0)
        }
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(groups.prefix(5).enumerated()), id: \.offset) { gIdx, g in
                let gTitle = NexlyIcons.cleanPlainText(str(g, "title", "label", "name"))
                let opts = (g["options"] ?? g["items"] ?? g["choices"]) as? [[String: Any]] ?? []
                let selIdx = selectedConfig[gIdx] ?? 0
                VStack(alignment: .leading, spacing: 4) {
                    Text(gTitle).font(.system(size: 11.5, weight: .semibold)).foregroundStyle(Aero.textSecondary)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(Array(opts.enumerated()), id: \.offset) { oIdx, opt in
                                let oLbl = NexlyIcons.cleanPlainText(str(opt, "label", "title", "name"))
                                let oPrice = dbl(opt, "price", "cost", "delta", fallback: 0)
                                let active = selIdx == oIdx
                                Button {
                                    GSHaptics.select()
                                    selectedConfig[gIdx] = oIdx
                                } label: {
                                    HStack(spacing: 4) {
                                        Text(oLbl).font(.system(size: 11.5, weight: .semibold))
                                        if oPrice != 0 {
                                            Text(String(format: "+$%.0f", oPrice)).font(Aero.mono(10.5))
                                        }
                                    }
                                    .foregroundStyle(active ? .white : Aero.text)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 5)
                                    .background(active ? accent : Color.white.opacity(0.68), in: Capsule())
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
            }
            HStack {
                Text("Configured Total")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Aero.textSecondary)
                Spacer()
                Text(String(format: "$%.2f", total))
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                    .foregroundStyle(accent)
            }
            .padding(8)
            .background(Color.white.opacity(0.75), in: RoundedRectangle(cornerRadius: 8))
        }
    }

    @ViewBuilder
    private func kpiGridView(items: [[String: Any]]) -> some View {
        let pairs = stride(from: 0, to: min(items.count, 8), by: 2).map { idx in
            Array(items[idx..<min(idx + 2, min(items.count, 8))])
        }
        VStack(alignment: .leading, spacing: 6) {
            ForEach(Array(pairs.enumerated()), id: \.offset) { _, pair in
                HStack(spacing: 8) {
                    ForEach(Array(pair.enumerated()), id: \.offset) { _, item in
                        let title = NexlyIcons.cleanPlainText(str(item, "title", "label", "name"))
                        let sub = NexlyIcons.cleanPlainText(str(item, "value", "subtitle"))
                        let meta = NexlyIcons.cleanPlainText(str(item, "meta", "caption", "date"))
                        let badge = NexlyIcons.cleanPlainText(str(item, "badge", "delta", "status"))
                        let primaryVal = sub.isEmpty ? (meta.isEmpty ? "—" : meta) : sub
                        let secondaryCap = (!sub.isEmpty && !meta.isEmpty) ? meta : ""
                        VStack(alignment: .leading, spacing: 3) {
                            Text(title)
                                .font(.system(size: 11, weight: .medium))
                                .foregroundStyle(Aero.textSecondary)
                                .lineLimit(1)
                            Text(primaryVal)
                                .font(.system(size: 15, weight: .bold))
                                .foregroundStyle(Aero.text)
                                .lineLimit(1)
                            if !secondaryCap.isEmpty {
                                Text(secondaryCap)
                                    .font(.system(size: 10.5))
                                    .foregroundStyle(Aero.textSecondary)
                                    .lineLimit(1)
                            }
                            if !badge.isEmpty {
                                Text(badge)
                                    .font(.system(size: 10.5, weight: .bold))
                                    .foregroundStyle(badge.hasPrefix("-") ? Color.red : accent)
                            }
                        }
                        .padding(10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.white.opacity(0.68), in: RoundedRectangle(cornerRadius: 10))
                    }
                    if pair.count == 1 {
                        Spacer().frame(maxWidth: .infinity)
                    }
                }
            }
        }
    }
}

private struct EditorBodyView: View {
    let obj: [String: Any]
    let accent: Color
    @State private var cellOverrides: [String: String] = [:]
    @State private var selectedCell: String = "B1"
    @State private var playhead: Double = 0.25
    @State private var isPlaying: Bool = false

    private func colName(_ idx: Int) -> String {
        let letters = Array("ABCDEFGH")
        return String(letters[min(max(0, idx), letters.count - 1)])
    }

    private func initialCells() -> [String: String] {
        var map: [String: String] = [:]
        let columns = ((obj["columns"] ?? obj["headers"]) as? [Any])?.map { NexlyIcons.cleanPlainText("\($0)") } ?? []
        let rows = ((obj["rows"] ?? obj["data"]) as? [[Any]]) ?? []
        var rowOffset = 1
        if !columns.isEmpty {
            for (ci, col) in columns.prefix(6).enumerated() {
                map["\(colName(ci))1"] = col
            }
            rowOffset = 2
        }
        for (ri, row) in rows.prefix(8).enumerated() {
            for (ci, v) in row.prefix(6).enumerated() {
                map["\(colName(ci))\(ri + rowOffset)"] = NexlyIcons.cleanPlainText("\(v)")
            }
        }
        if let rawCells = obj["cells"] as? [String: Any] {
            for (k, v) in rawCells {
                let parts = k.split(separator: ":")
                if parts.count == 2, let r = Int(parts[0]), let c = Int(parts[1]) {
                    map["\(colName(c))\(r + rowOffset)"] = "\(v)"
                } else {
                    map[k.uppercased()] = "\(v)"
                }
            }
        }
        if map.isEmpty {
            map["A1"] = "Item"; map["B1"] = "Cost"
            map["A2"] = "Hosting"; map["B2"] = "120"
            map["A3"] = "API"; map["B3"] = "180"
            map["A4"] = "Total"; map["B4"] = "=SUM(B2:B3)"
        }
        return map
    }

    private func evalCell(_ ref: String, all: [String: String], depth: Int = 0) -> String {
        guard depth < 6 else { return "#REF" }
        let raw = (all[ref] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard raw.hasPrefix("=") else { return raw }
        let expr = String(raw.dropFirst()).trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if let rx = try? NSRegularExpression(pattern: "^(SUM|AVG|MIN|MAX)\\(([A-F])(\\d+):([A-F])(\\d+)\\)$"),
           let m = rx.firstMatch(in: expr, range: NSRange(expr.startIndex..., in: expr)),
           let fnR = Range(m.range(at: 1), in: expr),
           let c1R = Range(m.range(at: 2), in: expr),
           let r1R = Range(m.range(at: 3), in: expr),
           let c2R = Range(m.range(at: 4), in: expr),
           let r2R = Range(m.range(at: 5), in: expr) {
            let fn = String(expr[fnR])
            let c1 = Int(expr[c1R].unicodeScalars.first!.value) - 65
            let c2 = Int(expr[c2R].unicodeScalars.first!.value) - 65
            let r1 = Int(expr[r1R]) ?? 1
            let r2 = Int(expr[r2R]) ?? 1
            var vals: [Double] = []
            for c in min(c1, c2)...max(c1, c2) {
                for r in min(r1, r2)...max(r1, r2) {
                    let subRef = "\(colName(c))\(r)"
                    if let d = Double(evalCell(subRef, all: all, depth: depth + 1)) {
                        vals.append(d)
                    }
                }
            }
            guard !vals.isEmpty else { return "0" }
            let result: Double = {
                switch fn {
                case "AVG": return vals.reduce(0, +) / Double(vals.count)
                case "MIN": return vals.min() ?? 0
                case "MAX": return vals.max() ?? 0
                default: return vals.reduce(0, +)
                }
            }()
            return result.truncatingRemainder(dividingBy: 1.0) == 0 ? String(format: "%.0f", result) : String(format: "%.2f", result)
        }
        var vars: [String: Double] = [:]
        for (k, _) in all {
            if k != ref, let d = Double(evalCell(k, all: all, depth: depth + 1)) {
                vars[k] = d
                vars[k.lowercased()] = d
            }
        }
        let val = CalcExprIOS.eval(expr, vars: vars)
        return val.truncatingRemainder(dividingBy: 1.0) == 0 ? String(format: "%.0f", val) : String(format: "%.2f", val)
    }

    var body: some View {
        let edType = str(obj, "editor", "type").lowercased()
        if ["video", "audio", "timeline"].contains(edType) {
            timelineEditorView
        } else {
            sheetEditorView
        }
    }

    @ViewBuilder
    private var timelineEditorView: some View {
        let tracks = ((obj["tracks"] ?? obj["layers"] ?? obj["items"]) as? [[String: Any]]) ?? [
            ["label": "Main Track", "duration": "00:45"],
            ["label": "Voiceover", "duration": "00:32"]
        ]
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Button {
                    GSHaptics.select()
                    isPlaying.toggle()
                    if isPlaying {
                        playhead = playhead >= 0.95 ? 0.0 : min(1.0, playhead + 0.15)
                    }
                } label: {
                    HStack(spacing: 4) {
                        NexlyGlyph(name: isPlaying ? "player-pause" : "player-play", size: 12, color: .white)
                        Text(isPlaying ? "Pause" : "Play")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(.white)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(accent, in: Capsule())
                }
                .buttonStyle(.plain)

                Slider(value: $playhead, in: 0...1)
                    .tint(accent)

                Text(String(format: "00:%02d", Int(playhead * 60)))
                    .font(Aero.mono(11))
                    .foregroundStyle(Aero.textSecondary)
            }
            ForEach(Array(tracks.prefix(5).enumerated()), id: \.offset) { tIdx, tr in
                let tLbl = NexlyIcons.cleanPlainText(str(tr, "label", "title", "name").ifEmptyIOS("Track \(tIdx + 1)"))
                HStack {
                    Text(tLbl)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(Aero.text)
                        .frame(width: 84, alignment: .leading)
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            RoundedRectangle(cornerRadius: 6)
                                .fill(WidgetPalette.at(tIdx).opacity(0.22))
                            RoundedRectangle(cornerRadius: 6)
                                .fill(WidgetPalette.at(tIdx).opacity(0.65))
                                .frame(width: max(12, geo.size.width * CGFloat(playhead)))
                        }
                    }
                    .frame(height: 18)
                }
            }
        }
    }

    @ViewBuilder
    private var sheetEditorView: some View {
        let merged = initialCells().merging(cellOverrides) { _, new in new }
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Text(selectedCell)
                    .font(Aero.mono(11.5))
                    .foregroundStyle(accent)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 4)
                    .background(accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 6))
                TextField(
                    "Value or =SUM(B2:B3)",
                    text: Binding(
                        get: { merged[selectedCell] ?? "" },
                        set: { (newVal: String) in cellOverrides[selectedCell] = newVal }
                    )
                )
                .font(Aero.mono(11.5))
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color.white.opacity(0.82), in: RoundedRectangle(cornerRadius: 6))
            }
            ScrollView(.horizontal, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 1) {
                    HStack(spacing: 1) {
                        Text("")
                            .frame(width: 24, height: 22)
                        ForEach(0..<4, id: \.self) { (c: Int) in
                            Text(colName(c))
                                .font(Aero.mono(10.5))
                                .foregroundStyle(Aero.textSecondary)
                                .frame(width: 72, height: 22)
                                .background(Color(red: 0.933, green: 0.910, blue: 1.0))
                        }
                    }
                    ForEach(0..<5, id: \.self) { (rIdx: Int) in
                        let r = rIdx + 1
                        HStack(spacing: 1) {
                            Text("\(r)")
                                .font(Aero.mono(10))
                                .foregroundStyle(Aero.textSecondary)
                                .frame(width: 24, height: 26)
                                .background(Color(red: 0.953, green: 0.937, blue: 1.0))
                            ForEach(0..<4, id: \.self) { (c: Int) in
                                let ref = "\(colName(c))\(r)"
                                let isSel = selectedCell == ref
                                let shown = evalCell(ref, all: merged)
                                Button {
                                    GSHaptics.select()
                                    selectedCell = ref
                                } label: {
                                    Text(shown)
                                        .font(Aero.mono(11))
                                        .foregroundStyle(Aero.text)
                                        .lineLimit(1)
                                        .frame(width: 72, height: 26)
                                        .background(isSel ? accent.opacity(0.16) : Color.white.opacity(0.78))
                                        .overlay(
                                            Rectangle().stroke(isSel ? accent : Aero.outline.opacity(0.25), lineWidth: isSel ? 1.5 : 0.5)
                                        )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
            }
        }
    }
}

private struct SimBodyView: View {
    let obj: [String: Any]
    let accent: Color
    @State private var isRunning: Bool = true
    @State private var tick: Int = 0
    @State private var speed: Double = 1.0
    @State private var damping: Double = 0.5
    @State private var lifeGrid: Set<String> = ["2:1", "2:2", "2:3", "1:3", "0:2"]

    private func stepLife(_ current: Set<String>) -> Set<String> {
        var next: Set<String> = []
        for r in 0..<8 {
            for c in 0..<12 {
                var neighbors = 0
                for dr in -1...1 {
                    for dc in -1...1 where !(dr == 0 && dc == 0) {
                        let nr = (r + dr + 8) % 8
                        let nc = (c + dc + 12) % 12
                        if current.contains("\(nr):\(nc)") { neighbors += 1 }
                    }
                }
                let key = "\(r):\(c)"
                if neighbors == 3 || (neighbors == 2 && current.contains(key)) {
                    next.insert(key)
                }
            }
        }
        return next
    }

    var body: some View {
        let sType = str(obj, "sim", "type").lowercased()
        let phase = Double(tick) * 0.14 * speed
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Button {
                    GSHaptics.select()
                    isRunning.toggle()
                } label: {
                    HStack(spacing: 4) {
                        NexlyGlyph(name: isRunning ? "player-pause" : "player-play", size: 11, color: .white)
                        Text(isRunning ? "Pause" : "Run")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(.white)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(accent, in: Capsule())
                }
                .buttonStyle(.plain)

                Button {
                    GSHaptics.select()
                    tick += 1
                    if sType == "life" {
                        lifeGrid = stepLife(lifeGrid)
                    }
                } label: {
                    HStack(spacing: 4) {
                        NexlyGlyph(name: "player-skip-forward", size: 11, color: accent)
                        Text("Step")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(accent)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(accent.opacity(0.14), in: Capsule())
                }
                .buttonStyle(.plain)

                Button {
                    GSHaptics.select()
                    tick = 0
                    lifeGrid = ["2:1", "2:2", "2:3", "1:3", "0:2"]
                } label: {
                    Text("Reset")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(Aero.textSecondary)
                }
                .buttonStyle(.plain)

                Spacer()
                Text("t = \(tick)")
                    .font(Aero.mono(10.5))
                    .foregroundStyle(Aero.textSecondary)
            }

            if sType == "life" {
                VStack(spacing: 2) {
                    ForEach(0..<8, id: \.self) { (r: Int) in
                        HStack(spacing: 2) {
                            ForEach(0..<12, id: \.self) { (c: Int) in
                                let key = "\(r):\(c)"
                                let alive = lifeGrid.contains(key)
                                Button {
                                    if alive { lifeGrid.remove(key) } else { lifeGrid.insert(key) }
                                } label: {
                                    RoundedRectangle(cornerRadius: 3)
                                        .fill(alive ? accent : Color.white.opacity(0.65))
                                        .frame(height: 18)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
                .padding(6)
                .background(Color.white.opacity(0.45), in: RoundedRectangle(cornerRadius: 10))
            } else {
                Canvas { ctx, size in
                    let w = size.width
                    let h = size.height
                    if sType.contains("pendulum") || sType.contains("spring") {
                        let origin = CGPoint(x: w / 2, y: 16)
                        let angle = 0.65 * cos(phase) * max(0.2, 1.0 - damping * 0.3)
                        let len = min(w, h) * 0.68
                        let bob = CGPoint(x: origin.x + CGFloat(sin(angle)) * len, y: origin.y + CGFloat(cos(angle)) * len)
                        var rod = Path()
                        rod.move(to: origin)
                        rod.addLine(to: bob)
                        ctx.stroke(rod, with: .color(Aero.textSecondary), lineWidth: 2)
                        ctx.fill(Path(ellipseIn: CGRect(x: bob.x - 12, y: bob.y - 12, width: 24, height: 24)), with: .color(accent))
                    } else if sType.contains("wave") || sType.contains("fluid") {
                        var path = Path()
                        for x in stride(from: 0.0, through: Double(w), by: 4.0) {
                            let y = Double(h / 2) + sin(x * 0.04 + phase) * Double(h * 0.28) * (0.5 + damping * 0.5)
                            if x == 0 { path.move(to: CGPoint(x: x, y: y)) }
                            else { path.addLine(to: CGPoint(x: x, y: y)) }
                        }
                        ctx.stroke(path, with: .color(accent), lineWidth: 2.5)
                    } else {
                        for i in 0..<18 {
                            let fi = Double(i)
                            let px = (sin(phase * (0.6 + fi * 0.07) + fi * 1.3) * 0.42 + 0.5) * Double(w)
                            let py = (cos(phase * (0.5 + fi * 0.05) + fi * 0.9) * 0.38 + 0.5) * Double(h)
                            let r: CGFloat = CGFloat(4 + (i % 4) * 2)
                            ctx.fill(
                                Path(ellipseIn: CGRect(x: CGFloat(px) - r, y: CGFloat(py) - r, width: r * 2, height: r * 2)),
                                with: .color(WidgetPalette.at(i))
                            )
                        }
                    }
                }
                .frame(height: 136)
                .background(Color.white.opacity(0.65), in: RoundedRectangle(cornerRadius: 10))
                .onTapGesture {
                    tick += 3
                }
            }

            HStack(spacing: 8) {
                Text("Speed")
                    .font(.system(size: 10.5, weight: .medium))
                    .foregroundStyle(Aero.textSecondary)
                Slider(value: $speed, in: 0.2...3.0)
                    .tint(accent)
                Text(String(format: "%.1fx", speed))
                    .font(Aero.mono(10.5))
                    .foregroundStyle(accent)
            }
        }
        .onAppear {
            if isRunning { tick = 1 }
        }
    }
}

private struct Stage3DBodyView: View {
    let obj: [String: Any]
    let accent: Color
    @State private var yaw: Double = 0.6
    @State private var pitch: Double = 0.35
    @State private var zoom: Double = 1.0

    var body: some View {
        let sType = str(obj, "stage", "type").lowercased()
        let atoms = (obj["atoms"] ?? obj["nodes"] ?? obj["points"] ?? obj["markers"] ?? obj["objects"]) as? [[String: Any]] ?? []
        VStack(alignment: .leading, spacing: 8) {
            Canvas { ctx, size in
                let cx = size.width / 2
                let cy = size.height / 2
                let scale = min(size.width, size.height) * 0.34 * CGFloat(zoom)

                func project(_ x: Double, _ y: Double, _ z: Double) -> CGPoint {
                    let x1 = x * cos(yaw) - z * sin(yaw)
                    let z1 = x * sin(yaw) + z * cos(yaw)
                    let y2 = y * cos(pitch) - z1 * sin(pitch)
                    return CGPoint(x: cx + CGFloat(x1) * scale, y: cy - CGFloat(y2) * scale)
                }

                let cubeVerts: [(Double, Double, Double)] = [
                    (-1, -1, -1), (1, -1, -1), (1, 1, -1), (-1, 1, -1),
                    (-1, -1, 1), (1, -1, 1), (1, 1, 1), (-1, 1, 1)
                ]
                let edges = [
                    (0, 1), (1, 2), (2, 3), (3, 0),
                    (4, 5), (5, 6), (6, 7), (7, 4),
                    (0, 4), (1, 5), (2, 6), (3, 7)
                ]
                for (a, b) in edges {
                    var p = Path()
                    p.move(to: project(cubeVerts[a].0, cubeVerts[a].1, cubeVerts[a].2))
                    p.addLine(to: project(cubeVerts[b].0, cubeVerts[b].1, cubeVerts[b].2))
                    ctx.stroke(p, with: .color(accent.opacity(0.45)), lineWidth: 1.5)
                }

                if !atoms.isEmpty {
                    for (idx, atom) in atoms.prefix(24).enumerated() {
                        let ax = dbl(atom, "x", "lon", fallback: cos(Double(idx) * 1.1) * 0.8)
                        let ay = dbl(atom, "y", "lat", fallback: sin(Double(idx) * 1.3) * 0.8)
                        let az = dbl(atom, "z", fallback: sin(Double(idx) * 0.7) * 0.6)
                        let pt = project(ax, ay, az)
                        let r: CGFloat = sType == "molecule" ? 7 : 5
                        ctx.fill(
                            Path(ellipseIn: CGRect(x: pt.x - r, y: pt.y - r, width: r * 2, height: r * 2)),
                            with: .color(WidgetPalette.at(idx))
                        )
                    }
                } else {
                    for i in -3...3 {
                        for j in -3...3 {
                            let x = Double(i) / 3.0
                            let z = Double(j) / 3.0
                            let y = sin(x * 2.5 + yaw) * cos(z * 2.5 + pitch) * 0.5
                            let pt = project(x, y, z)
                            ctx.fill(Path(ellipseIn: CGRect(x: pt.x - 3, y: pt.y - 3, width: 6, height: 6)), with: .color(accent))
                        }
                    }
                }
            }
            .frame(height: 150)
            .background(Color.white.opacity(0.65), in: RoundedRectangle(cornerRadius: 10))
            .gesture(
                DragGesture(minimumDistance: 2)
                    .onChanged { g in
                        yaw += Double(g.translation.width) * 0.008
                        pitch = min(max(-1.2, pitch + Double(g.translation.height) * 0.006), 1.2)
                    }
            )

            HStack(spacing: 8) {
                Button {
                    yaw -= 0.35
                } label: {
                    Text("⟲ Rotate")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(accent)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(accent.opacity(0.12), in: Capsule())
                }
                .buttonStyle(.plain)

                Slider(value: $zoom, in: 0.5...1.8)
                    .tint(accent)

                Text(String(format: "%.0f%%", zoom * 100))
                    .font(Aero.mono(10.5))
                    .foregroundStyle(Aero.textSecondary)
            }
        }
    }
}

private struct CanvasBodyView: View {
    let obj: [String: Any]
    let accent: Color
    @State private var nodeOffsets: [Int: CGSize] = [:]
    @State private var selectedNode: Int? = nil
    @State private var strokes: [[CGPoint]] = []
    @State private var currentStroke: [CGPoint] = []

    var body: some View {
        let nodes = ((obj["nodes"] ?? obj["items"] ?? obj["entities"] ?? obj["shapes"]) as? [[String: Any]]) ?? []
        VStack(alignment: .leading, spacing: 8) {
            ZStack {
                Canvas { ctx, size in
                    for stroke in strokes + [currentStroke] where stroke.count > 1 {
                        var p = Path()
                        p.move(to: stroke[0])
                        for pt in stroke.dropFirst() { p.addLine(to: pt) }
                        ctx.stroke(p, with: .color(accent), lineWidth: 2.2)
                    }
                }
                .gesture(
                    DragGesture(minimumDistance: 1)
                        .onChanged { g in
                            currentStroke.append(g.location)
                        }
                        .onEnded { _ in
                            strokes.append(currentStroke)
                            currentStroke = []
                        }
                )

                ForEach(Array(nodes.prefix(8).enumerated()), id: \.offset) { idx, node in
                    let lbl = NexlyIcons.cleanPlainText(str(node, "label", "title", "name", "id").ifEmptyIOS("Node \(idx + 1)"))
                    let baseX = CGFloat(60 + (idx % 3) * 95)
                    let baseY = CGFloat(34 + (idx / 3) * 52)
                    let off = nodeOffsets[idx] ?? .zero
                    Button {
                        GSHaptics.select()
                        selectedNode = idx
                    } label: {
                        Text(lbl)
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(selectedNode == idx ? .white : Aero.text)
                            .padding(.horizontal, 9)
                            .padding(.vertical, 5)
                            .background(selectedNode == idx ? accent : Color.white.opacity(0.9), in: RoundedRectangle(cornerRadius: 8))
                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(accent.opacity(0.45), lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    .position(x: baseX + off.width, y: baseY + off.height)
                    .gesture(
                        DragGesture()
                            .onChanged { g in
                                nodeOffsets[idx] = g.translation
                            }
                    )
                }
            }
            .frame(height: 146)
            .frame(maxWidth: .infinity)
            .background(Color.white.opacity(0.65), in: RoundedRectangle(cornerRadius: 10))
            .clipped()

            HStack {
                Text("Drag nodes or sketch directly on the canvas")
                    .font(.system(size: 10.5))
                    .foregroundStyle(Aero.textSecondary)
                Spacer()
                if !strokes.isEmpty || !nodeOffsets.isEmpty {
                    Button("Clear") {
                        GSHaptics.select()
                        strokes.removeAll()
                        nodeOffsets.removeAll()
                    }
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(accent)
                    .buttonStyle(.plain)
                }
            }
        }
    }
}

private struct TerminalBodyView: View {
    let obj: [String: Any]
    let accent: Color

    var body: some View {
        // Same local files, real JS Worker and refusal semantics on all clients.
        // Model-supplied output is never presented as an execution result.
        HtmlBlockView(kind: "html", source: TerminalSandboxIOS.document(payload: obj), open: false)
    }
}

private struct CollabBodyView: View {
    let obj: [String: Any]
    let accent: Color
    @State private var counterVal: Double? = nil
    @State private var votes: [Int: Int] = [:]
    @State private var voted: Set<Int> = []
    @State private var checked: Set<Int> = []
    @State private var addedItems: [String] = []
    @State private var removedIndices: Set<Int> = []
    @State private var draftItem: String = ""

    var body: some View {
        let surface = str(obj, "surface", "type").lowercased()
        let startObj = (obj["start"] as? [String: Any]) ?? [:]
        let initialCount = dbl(obj, "start", "count", "value", fallback: dbl(startObj, "count", "value", fallback: 0))
        let currentCount = counterVal ?? initialCount
        let rawItems = ((obj["items"] ?? obj["options"] ?? obj["tasks"] ?? obj["notes"]) as? [Any])?.map { item -> String in
            if let s = item as? String { return NexlyIcons.cleanPlainText(s) }
            if let d = item as? [String: Any] { return NexlyIcons.cleanPlainText(str(d, "title", "label", "text", "name")) }
            return NexlyIcons.cleanPlainText("\(item)")
        } ?? []

        VStack(alignment: .leading, spacing: 8) {
            Text("Local preview only. Changes are not shared with other viewers or saved on a server.")
                .font(.caption).foregroundStyle(Aero.textSecondary)
            if surface == "pointer" {
                Text("Live pointer synchronization is not connected. No remote cursors have been observed.").font(.caption)
            } else if surface == "counter" {
                HStack(spacing: 12) {
                    Button {
                        GSHaptics.select()
                        counterVal = currentCount - 1
                    } label: {
                        Text("−1")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(accent)
                            .frame(width: 38, height: 32)
                            .background(accent.opacity(0.14), in: RoundedRectangle(cornerRadius: 8))
                    }
                    .buttonStyle(.plain)

                    Text("\(currentCount)")
                        .font(.system(size: 22, weight: .bold, design: .monospaced))
                        .foregroundStyle(Aero.text)
                        .frame(minWidth: 54)

                    Button {
                        GSHaptics.select()
                        counterVal = currentCount + 1
                    } label: {
                        Text("+1")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 38, height: 32)
                            .background(accent, in: RoundedRectangle(cornerRadius: 8))
                    }
                    .buttonStyle(.plain)

                    Button("Reset") {
                        GSHaptics.select()
                        counterVal = initialCount
                    }
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Aero.textSecondary)
                    .buttonStyle(.plain)
                }
            } else if surface == "vote" {
                let opts = rawItems
                if opts.count < 2 { Text("Voting requires at least two supplied options.").font(.caption) }
                let total = opts.indices.reduce(0) { $0 + (votes[$1] ?? 0) }
                ForEach(Array(opts.enumerated()), id: \.offset) { idx, label in
                    let v = votes[idx] ?? 0
                    Button {
                        GSHaptics.select()
                        guard !voted.contains(idx) else { return }
                        voted.insert(idx); votes[idx] = v + 1
                    } label: {
                        HStack {
                            Text(label).font(.system(size: 12, weight: .medium)).foregroundStyle(Aero.text)
                            Spacer()
                            Text("\(v) (\(total > 0 ? Int(round(Double(v) / Double(total) * 100)) : 0)%)")
                                .font(Aero.mono(11))
                                .foregroundStyle(accent)
                        }
                        .padding(8)
                        .background(Color.white.opacity(0.68), in: RoundedRectangle(cornerRadius: 8))
                    }
                    .buttonStyle(.plain)
                    .disabled(voted.contains(idx))
                }
            } else {
                let allItems = rawItems + addedItems
                ForEach(Array(allItems.enumerated()), id: \.offset) { idx, item in
                    if !removedIndices.contains(idx) {
                        HStack {
                            Button {
                                if checked.contains(idx) { checked.remove(idx) } else { checked.insert(idx) }
                            } label: { NexlyGlyph(name: checked.contains(idx) ? "square-check" : "square", size: 13, color: accent) }
                                .buttonStyle(.plain)
                                .accessibilityLabel("Toggle task: " + item)
                                .accessibilityValue(checked.contains(idx) ? "Completed" : "Not completed")
                            Text(item)
                                .font(.system(size: 12))
                                .foregroundStyle(Aero.text)
                            Spacer()
                            Button {
                                GSHaptics.select()
                                removedIndices.insert(idx)
                            } label: {
                                NexlyGlyph(name: "x", size: 12, color: Aero.textSecondary)
                            }
                            .buttonStyle(.plain)
                        }
                        .padding(7)
                        .background(Color.white.opacity(0.65), in: RoundedRectangle(cornerRadius: 8))
                    }
                }
                HStack(spacing: 6) {
                    TextField("Add shared item…", text: $draftItem)
                        .font(.system(size: 12))
                    Button("Add") {
                        let clean = draftItem.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard !clean.isEmpty else { return }
                        GSHaptics.select()
                        addedItems.append(clean)
                        draftItem = ""
                    }
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(accent, in: Capsule())
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color.white.opacity(0.75), in: RoundedRectangle(cornerRadius: 8))
            }
        }
    }
}
