import SwiftUI
import Charts

/// Specialized data is never routed through the categorical line renderer.
let specializedNativeChartTypesIOS: Set<String> = ["hbar", "scatter", "bubble", "grouped", "stacked", "stackedArea", "waterfall", "candlestick", "gantt", "heatmap", "correlation"]
let basicNativeChartTypesIOS: Set<String> = ["line", "bar", "area", "pie", "donut"]

struct NativeChartPointIOS: Identifiable {
    let id: Int
    let series: String
    let label: String
    let x: Double
    let y: Double
    let extra: [Double]
    let start: Double
}

func specializedChartPointsIOS(_ obj: [String: Any]) -> [NativeChartPointIOS]? {
    let type = canonicalChartType((obj["type"] as? String) ?? "")
    guard specializedNativeChartTypesIOS.contains(type) else { return nil }
    let labels = (obj["labels"] as? [String]) ?? []
    let source: [[String: Any]]
    if let series = obj["series"] as? [[String: Any]] { source = series }
    else if let data = obj["data"] as? [Any] { source = [["data": data]] }
    else { return nil }
    var result: [NativeChartPointIOS] = []
    for (seriesIndex, item) in source.enumerated() {
        guard let data = item["data"] as? [Any] else { return nil }
        let name = item["name"] as? String ?? "Series \(seriesIndex + 1)"
        var cumulative = 0.0
        for (index, raw) in data.enumerated() {
            var values: [Double]
            let label: String
            if let pair = raw as? [Any] {
                let numbers = pair.map { chartValueIOS($0) }
                guard numbers.allSatisfy({ $0 != nil }) else { return nil }
                values = numbers.compactMap { $0 }
                if type == "heatmap" || type == "correlation" {
                    guard let x = values.first else { return nil }
                    label = x >= 0 && x < Double(labels.count) && x.rounded() == x ? labels[Int(x)] : String(x)
                } else {
                    label = type == "gantt" && data.count == 1 ? (seriesIndex < labels.count ? labels[seriesIndex] : name) : (index < labels.count ? labels[index] : "Point \(index + 1)")
                }
            } else if let point = raw as? [String: Any], let value = chartObservationValueIOS(raw) {
                // Category-only objects cannot invent paired numeric axes or OHLC.
                guard !["scatter", "bubble", "candlestick", "gantt"].contains(type) else { return nil }
                values = [Double(index), value]
                label = point["label"] as? String ?? (index < labels.count ? labels[index] : "Point \(index + 1)")
            } else { return nil }
            let required = type == "candlestick" ? 4 : type == "bubble" ? 3 : 2
            guard values.count == required, values.allSatisfy({ $0.isFinite }) else { return nil }
            if type == "bubble" && values[2] < 0 { return nil }
            if type == "gantt" && values[1] < values[0] { return nil }
            if type == "candlestick" && (values[1] < max(values[0], values[3]) || values[2] > min(values[0], values[3]) || values[2] > values[1]) { return nil }
            if type == "correlation" && abs(values[1]) > 1 { return nil }
            let start = cumulative
            if type == "waterfall" {
                cumulative += values[1]
                guard cumulative.isFinite else { return nil }
            }
            result.append(NativeChartPointIOS(id: result.count, series: name, label: label,
                                             x: values[0], y: values[1], extra: Array(values.dropFirst(2)), start: start))
        }
    }
    return result.isEmpty ? nil : result
}

struct SpecializedChartViewIOS: View {
    let obj: [String: Any]
    @State private var selected: Int?
    private var type: String { canonicalChartType((obj["type"] as? String) ?? "") }

    var body: some View {
        if let points = specializedChartPointsIOS(obj) {
            let rowCount = Set(points.map { ["heatmap", "correlation"].contains(type) ? $0.series : $0.label }).count
            VStack(alignment: .leading, spacing: 10) {
                if let unit = obj["unit"] as? String, !unit.isEmpty {
                    Text("Unit: \(unit)").font(.caption).foregroundStyle(.secondary)
                }
                nativeChart(points)
                    .frame(height: ["hbar", "gantt", "heatmap", "correlation"].contains(type) ? max(220, CGFloat(rowCount) * 28) : 260)
                    .chartOverlay { proxy in
                        GeometryReader { geometry in
                            Rectangle().fill(.clear).contentShape(Rectangle())
                                .gesture(DragGesture(minimumDistance: 0).onChanged { gesture in
                                    let area = geometry[proxy.plotAreaFrame]
                                    if ["hbar", "gantt"].contains(type) {
                                        if let category: String = proxy.value(atY: gesture.location.y - area.origin.y) {
                                            let candidates = points.filter { $0.label == category }
                                            selected = candidates.count == 1 ? candidates.first?.id : nil
                                        }
                                    } else if ["grouped", "stacked", "waterfall", "heatmap", "correlation"].contains(type) {
                                        if let category: String = proxy.value(atX: gesture.location.x - area.origin.x) {
                                            let row: String? = proxy.value(atY: gesture.location.y - area.origin.y)
                                            let candidates = points.filter { $0.label == category && (!["heatmap", "correlation"].contains(type) || $0.series == row) }
                                            selected = candidates.count == 1 ? candidates.first?.id : nil
                                        }
                                    } else if type == "scatter" || type == "bubble" {
                                        let finger = CGPoint(x: gesture.location.x - area.origin.x, y: gesture.location.y - area.origin.y)
                                        func distance(_ point: NativeChartPointIOS) -> CGFloat {
                                            guard let x = proxy.position(forX: point.x), let y = proxy.position(forY: point.y) else { return .infinity }
                                            return hypot(x - finger.x, y - finger.y)
                                        }
                                        selected = points.min(by: { distance($0) < distance($1) })?.id
                                    } else if let x: Double = proxy.value(atX: gesture.location.x - area.origin.x) {
                                        selected = points.min(by: { abs((type == "candlestick" ? Double($0.id) : $0.x) - x) < abs((type == "candlestick" ? Double($1.id) : $1.x) - x) })?.id
                                    }
                                })
                        }
                    }
                Picker("Inspect observation", selection: $selected) {
                    Text("Choose an observation").tag(Optional<Int>.none)
                    ForEach(points) { point in
                        Text("\(point.series) · \(point.label)").tag(Optional(point.id))
                    }
                }.pickerStyle(.menu).accessibilityIdentifier("chart.observation.picker")
                if let point = points.first(where: { $0.id == selected }) {
                    Text(observationText(point)).font(.caption.monospacedDigit()).textSelection(.enabled)
                }
            }
        } else {
            unsupportedChartSourceIOS(obj, message: "The source is incomplete for this chart’s required axes or ranges. No observations were substituted.")
        }
    }

    private func observationText(_ point: NativeChartPointIOS) -> String {
        if type == "candlestick" { return "\(point.label): open \(point.x), high \(point.y), low \(point.extra[0]), close \(point.extra[1])" }
        if type == "gantt" { return "\(point.label): \(point.x)–\(point.y)" }
        if type == "scatter" || type == "bubble" { return "\(point.series): x \(point.x), y \(point.y)" + (point.extra.first.map { ", size \($0)" } ?? "") }
        return "\(point.series) · \(point.label): \(point.y) \((obj["unit"] as? String) ?? "")"
    }

    private func nativeChart(_ points: [NativeChartPointIOS]) -> some View {
        Chart {
            ForEach(points) { point in
                marks(point, points: points)
            }
        }
        .chartLegend(position: .bottom)
        .accessibilityLabel("\(type) chart, \(points.count) supplied observations")
        .accessibilityIdentifier("chart.native.\(type)")
    }

    @ChartContentBuilder
    private func marks(_ point: NativeChartPointIOS, points: [NativeChartPointIOS]) -> some ChartContent {
        switch type {
        case "hbar":
            BarMark(x: .value("Value", point.y), y: .value("Category", point.label))
                .foregroundStyle(by: .value("Series", point.series)).position(by: .value("Series", point.series))
        case "scatter":
            PointMark(x: .value("X", point.x), y: .value("Y", point.y)).foregroundStyle(by: .value("Series", point.series))
        case "bubble":
            PointMark(x: .value("X", point.x), y: .value("Y", point.y)).symbolSize(by: .value("Size", point.extra[0]))
                .foregroundStyle(by: .value("Series", point.series))
        case "grouped":
            BarMark(x: .value("Category", point.label), y: .value("Value", point.y))
                .foregroundStyle(by: .value("Series", point.series)).position(by: .value("Series", point.series))
        case "stacked":
            BarMark(x: .value("Category", point.label), y: .value("Value", point.y)).foregroundStyle(by: .value("Series", point.series))
        case "stackedArea":
            AreaMark(x: .value("X", point.x), y: .value("Value", point.y)).foregroundStyle(by: .value("Series", point.series))
        case "waterfall":
            BarMark(x: .value("Category", point.label), yStart: .value("Start", point.start), yEnd: .value("End", point.start + point.y))
                .foregroundStyle(point.y >= 0 ? Color.green : Color.red)
        case "candlestick":
            RuleMark(x: .value("Observation", point.id), yStart: .value("Low", point.extra[0]), yEnd: .value("High", point.y))
            BarMark(x: .value("Observation", point.id), yStart: .value("Open", point.x), yEnd: .value("Close", point.extra[1]), width: .ratio(0.6))
                .foregroundStyle(point.extra[1] >= point.x ? Color.green : Color.red)
        case "gantt":
            BarMark(xStart: .value("Start", point.x), xEnd: .value("End", point.y), y: .value("Task", point.label))
                .foregroundStyle(by: .value("Series", point.series))
        case "heatmap", "correlation":
            RectangleMark(x: .value("Column", point.label), y: .value("Row", point.series)).foregroundStyle(by: .value("Value", point.y))
        default:
            // Exhaustive routing above makes this unreachable; never draw a line as a substitute.
            if false { RuleMark(y: .value("Unavailable", 0)) }
        }
    }
}

func unsupportedChartSourceIOS(_ obj: [String: Any], message: String) -> some View {
    VStack(alignment: .leading, spacing: 8) {
        Text(message).font(.caption).foregroundStyle(.secondary)
        DisclosureGroup("Original chart data") {
            if let data = try? JSONSerialization.data(withJSONObject: obj, options: [.sortedKeys, .prettyPrinted]), let source = String(data: data, encoding: .utf8) {
                Text(source).font(.caption.monospaced()).textSelection(.enabled)
            }
        }
    }
}
