import Foundation

/// A label is a time coordinate only when its complete source is unambiguous.
func chronologicalChartLabelsIOS(_ labels: [String]) -> Bool {
    guard labels.count >= 2 else { return false }
    let months = ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"]
    var family: String?
    var previous: Double?
    for source in labels {
        let label = source.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        func matches(_ pattern: String) -> Bool { label.range(of: pattern, options: .regularExpression) != nil }
        let parts = label.components(separatedBy: .whitespacesAndNewlines).filter { !$0.isEmpty }
        let kind: String
        let coordinate: Double
        if matches("^(?:19|20|21)\\d{2}$"), let year = Double(label) {
            kind = "year"; coordinate = year
        } else if matches("^\\d{4}-\\d{2}(?:-\\d{2})?$" ) {
            let formatter = ISO8601DateFormatter()
            formatter.formatOptions = [.withFullDate]
            let day = label.count == 7 ? label + "-01" : label
            guard let date = formatter.date(from: day), formatter.string(from: date) == day else { return false }
            kind = "date"; coordinate = date.timeIntervalSince1970
        } else if matches("^q[1-4](?:\\s+(?:19|20|21)\\d{2})?$"), let quarter = Double(parts[0].dropFirst()) {
            kind = parts.count == 1 ? "quarter" : "dated-quarter"
            coordinate = (parts.count == 2 ? Double(parts[1]) ?? 0 : 0) * 4 + quarter
        } else if matches("^(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)(?:\\s+(?:19|20|21)\\d{2})?$"),
                  let month = months.firstIndex(of: String(parts[0].prefix(3))) {
            kind = parts.count == 1 ? "month" : "dated-month"
            coordinate = (parts.count == 2 ? Double(parts[1]) ?? 0 : 0) * 12 + Double(month)
        } else { return false }
        if let family, family != kind { return false }
        if let previous, coordinate <= previous { return false }
        family = kind; previous = coordinate
    }
    return true
}

func categoricalChartTypeIOS(_ labels: [String]) -> String {
    if chronologicalChartLabelsIOS(labels) { return "line" }
    return labels.count > 7 || labels.contains(where: { $0.count > 18 }) ? "hbar" : "bar"
}
