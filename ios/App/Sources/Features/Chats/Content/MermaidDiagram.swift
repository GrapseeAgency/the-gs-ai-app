import SwiftUI

/// STEP 5 — native Mermaid rendering (iOS twin of MermaidDiagram.kt).
/// NO WebView, NO WKWebView, NO remote page: diagrams parse and lay out
/// locally, then draw on a SwiftUI Canvas. Supported families: flowchart
/// (TD/LR/BT/RL) and sequenceDiagram — everything else degrades HONESTLY to
/// "Diagram unavailable" with a source viewer. Diagrams never render
/// mid-stream (a fenced mermaid block shows its plain source while streaming).

// MARK: - Model + parser

enum MermaidShape {
    case rect, round, diamond, stadium, circle
}

enum EdgeStyle {
    case solid, dashed, thick, open
}

struct FlowNode: Equatable {
    let id: String
    let label: String
    let shape: MermaidShape
}

struct FlowEdge: Equatable {
    let from: String
    let to: String
    let label: String?
    let style: EdgeStyle
}

struct SeqParticipant: Equatable {
    let id: String
    let label: String
}

enum SeqStyle {
    case solid, dashed, cross, async
}

struct SeqMessage: Equatable {
    let from: String
    let to: String
    let text: String
    let style: SeqStyle
}

struct SeqNote: Equatable {
    let ids: [String]
    let text: String
}

enum MermaidChart: Equatable {
    case flowchart(vertical: Bool, reversed: Bool, nodes: [FlowNode], edges: [FlowEdge])
    case sequence(participants: [SeqParticipant], messages: [SeqMessage], notes: [SeqNote])
    case unsupported(reason: String)
}

func parseMermaid(_ source: String) -> MermaidChart {
    let lines = source
        .components(separatedBy: "\n")
        .map { $0.trimmingCharacters(in: CharacterSet(charactersIn: " \t")) }
        .filter { !$0.isEmpty }
    guard let head = lines.first else {
        return .unsupported(reason: "The diagram source is empty.")
    }
    let rest = Array(lines.dropFirst())
    if head.hasPrefix("flowchart") || head.hasPrefix("graph") {
        let remainder = head
            .replacingOccurrences(of: "flowchart", with: "")
            .replacingOccurrences(of: "graph", with: "")
            .trimmingCharacters(in: .whitespaces)
        let dir = remainder.components(separatedBy: .whitespaces).first?.uppercased() ?? "TD"
        guard ["TD", "TB", "LR", "BT", "RL"].contains(dir) else {
            return .unsupported(reason: "Unknown flowchart direction \"\(dir)\".")
        }
        return parseFlowchart(
            vertical: dir == "TD" || dir == "TB" || dir == "BT",
            reversed: dir == "BT" || dir == "RL",
            lines: rest
        )
    }
    if head == "sequenceDiagram" || head == "sequence" {
        return parseSequence(rest)
    }
    if head.hasPrefix("classDiagram") { return .unsupported(reason: "Class diagrams aren't supported yet.") }
    if head.hasPrefix("stateDiagram") { return .unsupported(reason: "State diagrams aren't supported yet.") }
    if head.hasPrefix("erDiagram") { return .unsupported(reason: "Entity-relationship diagrams aren't supported yet.") }
    if head.hasPrefix("journey") { return .unsupported(reason: "User journeys aren't supported yet.") }
    if head.hasPrefix("gantt") { return .unsupported(reason: "Gantt charts aren't supported yet.") }
    if head.hasPrefix("mindmap") { return .unsupported(reason: "Mindmaps aren't supported yet.") }
    return .unsupported(reason: "This diagram type isn't supported yet.")
}

private let edgeOpRegex = try! NSRegularExpression(pattern: "(-\\.->|-->|==>|---|--)(\\|([^|]*)\\|)?")

private func parseFlowchart(vertical: Bool, reversed: Bool, lines: [String]) -> MermaidChart {
    var nodeMap = LinkedHashMap<FlowNode>()
    var edges: [FlowEdge] = []

    func registerNode(_ raw: String) -> String? {
        guard let (id, shape, label) = parseNodeToken(raw.trimmingCharacters(in: .whitespaces)) else { return nil }
        if let existing = nodeMap[id] {
            if let label = label {
                nodeMap[id] = FlowNode(id: id, label: label, shape: shape)
            } else {
                _ = existing
            }
        } else {
            nodeMap[id] = FlowNode(id: id, label: label ?? id, shape: shape)
        }
        return id
    }

    for line in lines {
        let first = line.trimmingCharacters(in: .whitespaces).lowercased()
        if first.hasPrefix("subgraph") {
            return .unsupported(reason: "Subgraphs aren't supported yet.")
        }
        if first == "end" { continue }

        // Segment-based scan: node labels may contain spaces, so the line is
        // split by EDGE OPERATORS (never by whitespace).
        let ns = line as NSString
        let matches = edgeOpRegex.matches(
            in: line, range: NSRange(location: 0, length: ns.length))
        if matches.isEmpty { continue } // a bare node-declaration line
        var segments: [String] = []
        var cursor = 0
        for m in matches {
            segments.append(ns.substring(with: NSRange(location: cursor, length: m.range.location - cursor))
                .trimmingCharacters(in: .whitespaces))
            cursor = m.range.location + m.range.length
        }
        segments.append(ns.substring(from: cursor).trimmingCharacters(in: .whitespaces))

        var fromId: String? = nil
        var idx = 0
        while idx < matches.count {
            let op = matches[idx]
            let opText = ns.substring(with: op.range(at: 1))
            let pipeLabel = op.range(at: 3).location == NSNotFound
                ? nil
                : ns.substring(with: op.range(at: 3))
            let label = (pipeLabel?.isEmpty == false) ? pipeLabel : nil

            if fromId == nil {
                let firstSeg = idx < segments.count ? segments[idx] : ""
                if firstSeg.isEmpty { return .unsupported(reason: "An edge has no source node.") }
                guard let id = registerNode(firstSeg) else {
                    return .unsupported(reason: "Couldn't read a node definition.")
                }
                fromId = id
            }

            if opText == "--" || opText == "---" {
                let nextOp = idx + 1 < matches.count ? matches[idx + 1] : nil
                let nextOpText = nextOp.map { ns.substring(with: $0.range(at: 1)) }
                let segAfter = idx + 1 < segments.count ? segments[idx + 1] : ""
                if nextOpText == "-->" && !segAfter.isEmpty {
                    // "A -- text --> B" label form
                    let target = idx + 2 < segments.count ? segments[idx + 2] : ""
                    if target.isEmpty { return .unsupported(reason: "An edge has no target node.") }
                    guard let toId = registerNode(target) else {
                        return .unsupported(reason: "Couldn't read a node definition.")
                    }
                    edges.append(FlowEdge(from: fromId!, to: toId, label: segAfter.isEmpty ? nil : segAfter, style: .solid))
                    fromId = toId
                    idx += 2
                } else {
                    if opText == "--" { return .unsupported(reason: "Malformed edge.") }
                    if segAfter.isEmpty { return .unsupported(reason: "An edge has no target node.") }
                    guard let toId = registerNode(segAfter) else {
                        return .unsupported(reason: "Couldn't read a node definition.")
                    }
                    edges.append(FlowEdge(from: fromId!, to: toId, label: nil, style: .open))
                    fromId = toId
                    idx += 1
                }
                continue
            }

            let style: EdgeStyle
            switch opText {
            case "-.->": style = .dashed
            case "==>": style = .thick
            case "---": style = .open
            default: style = .solid
            }
            let target = idx + 1 < segments.count ? segments[idx + 1] : ""
            if target.isEmpty { return .unsupported(reason: "An edge has no target node.") }
            guard let toId = registerNode(target) else {
                return .unsupported(reason: "Couldn't read a node definition.")
            }
            edges.append(FlowEdge(from: fromId!, to: toId, label: label, style: style))
            fromId = toId
            idx += 1
        }
    }
    if nodeMap.values.isEmpty {
        return .unsupported(reason: "No nodes found in the diagram source.")
    }
    return .flowchart(vertical: vertical, reversed: reversed, nodes: nodeMap.values, edges: edges)
}

/// `id[label]`, `id(label)`, `id{label}`, `id([label])`, `id((label))`, bare `id`.
private func parseNodeToken(_ token: String) -> (String, MermaidShape, String?)? {
    guard let openerIndex = token.firstIndex(where: { $0 == "[" || $0 == "(" || $0 == "{" }) else {
        return token.allSatisfy { $0.isLetter || $0.isNumber || $0 == "_" }
            ? (token, .rect, nil)
            : nil
    }
    let id = String(token[token.startIndex..<openerIndex])
    guard id.allSatisfy({ $0.isLetter || $0.isNumber || $0 == "_" }) else { return nil }
    let rest = String(token[openerIndex...])
    if rest.hasPrefix("(["), rest.hasSuffix("])"), rest.count >= 4 {
        return (id, .stadium, String(rest.dropFirst(2).dropLast(2)))
    }
    if rest.hasPrefix("(("), rest.hasSuffix("))"), rest.count >= 4 {
        return (id, .circle, String(rest.dropFirst(2).dropLast(2)))
    }
    if rest.hasPrefix("["), rest.hasSuffix("]"), rest.count >= 2 {
        return (id, .rect, String(rest.dropFirst().dropLast()))
    }
    if rest.hasPrefix("("), rest.hasSuffix(")"), rest.count >= 2 {
        return (id, .round, String(rest.dropFirst().dropLast()))
    }
    if rest.hasPrefix("{"), rest.hasSuffix("}"), rest.count >= 2 {
        return (id, .diamond, String(rest.dropFirst().dropLast()))
    }
    return nil
}

/// Minimal insertion-ordered dictionary — node declaration order matters.
private struct LinkedHashMap<Value> {
    private(set) var keys: [String] = []
    private var map: [String: Value] = [:]

    subscript(key: String) -> Value? {
        get { map[key] }
        set {
            if let newValue = newValue {
                if map[key] == nil { keys.append(key) }
                map[key] = newValue
            } else {
                map[key] = nil
                keys.removeAll { $0 == key }
            }
        }
    }

    var values: [Value] { keys.compactMap { map[$0] } }
    var isEmpty: Bool { map.isEmpty }
}

private func parseSequence(_ lines: [String]) -> MermaidChart {
    var participants = LinkedHashMap<SeqParticipant>()
    var messages: [SeqMessage] = []
    var notes: [SeqNote] = []

    func participant(_ id: String) -> String {
        if participants[id] == nil {
            participants[id] = SeqParticipant(id: id, label: id)
        }
        return id
    }

    let arrowRegex = try! NSRegularExpression(pattern: "(-->>|->>|-x|-\\)|->)")

    for line in lines {
        let trimmed = line.trimmingCharacters(in: .whitespaces)
        if trimmed.hasPrefix("participant ") {
            let body = trimmed.dropFirst("participant ".count).trimmingCharacters(in: .whitespaces)
            if let asRange = body.range(of: " as ") {
                let id = String(body[..<asRange.lowerBound]).trimmingCharacters(in: .whitespaces)
                let label = String(body[asRange.upperBound...]).trimmingCharacters(in: .whitespaces)
                participants[id] = SeqParticipant(id: id, label: label)
            } else {
                participant(body)
            }
        } else if trimmed.hasPrefix("Note ") {
            let body = trimmed.dropFirst("Note ".count).trimmingCharacters(in: .whitespaces)
            guard let colon = body.firstIndex(of: ":") else {
                return .unsupported(reason: "Couldn't read a note in the diagram source.")
            }
            let text = String(body[body.index(after: colon)...]).trimmingCharacters(in: .whitespaces)
            let target = String(body[..<colon]).trimmingCharacters(in: .whitespaces)
            let ids = target
                .replacingOccurrences(of: "over", with: "")
                .replacingOccurrences(of: "right of", with: "")
                .replacingOccurrences(of: "left of", with: "")
                .components(separatedBy: ",")
                .map { $0.trimmingCharacters(in: .whitespaces) }
                .filter { !$0.isEmpty }
            if ids.isEmpty {
                return .unsupported(reason: "Couldn't read a note in the diagram source.")
            }
            notes.append(SeqNote(ids: ids, text: text))
        } else if trimmed == "autonumber" || trimmed.hasPrefix("activate ") || trimmed.hasPrefix("deactivate ") {
            continue
        } else if trimmed.hasPrefix("loop") || trimmed.hasPrefix("alt") || trimmed.hasPrefix("opt") ||
            trimmed.hasPrefix("par") || trimmed.hasPrefix("critical") {
            return .unsupported(reason: "Group frames aren't supported yet.")
        } else {
            let ns = trimmed as NSString
            guard let match = arrowRegex.firstMatch(
                in: trimmed, range: NSRange(location: 0, length: ns.length)) else {
                return .unsupported(reason: "Couldn't read a line of the diagram source.")
            }
            let afterArrow = match.range.location + match.range.length
            guard afterArrow < ns.length else {
                return .unsupported(reason: "Couldn't read a message in the diagram source.")
            }
            let colonRange = ns.range(
                of: ":",
                options: [],
                range: NSRange(location: afterArrow, length: ns.length - afterArrow))
            guard colonRange.location != NSNotFound else {
                return .unsupported(reason: "Couldn't read a message in the diagram source.")
            }
            let text = ns.substring(from: colonRange.location + 1).trimmingCharacters(in: .whitespaces)
            let head = ns.substring(to: colonRange.location)
            // Split the head by the arrow WITHOUT String.components (which
            // cannot take an NSRegularExpression) — manual match walk.
            var parts: [String] = []
            var cursor = 0
            for m in arrowRegex.matches(in: head, range: NSRange(location: 0, length: (head as NSString).length)) {
                parts.append((head as NSString).substring(with: NSRange(location: cursor, length: m.range.location - cursor)))
                cursor = m.range.location + m.range.length
            }
            parts.append((head as NSString).substring(from: cursor))
            let sides = parts.filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
            guard sides.count == 2 else {
                return .unsupported(reason: "Couldn't read a message in the diagram source.")
            }
            let arrow = ns.substring(with: match.range)
            let style: SeqStyle
            switch arrow {
            case "-->>": style = .dashed
            case "-x": style = .cross
            case "-)": style = .async
            default: style = .solid
            }
            let from = participant(sides[0].trimmingCharacters(in: .whitespaces))
            let to = participant(sides[1].trimmingCharacters(in: .whitespaces))
            messages.append(SeqMessage(from: from, to: to, text: text, style: style))
        }
    }
    if participants.values.isEmpty {
        return .unsupported(reason: "No participants found in the diagram source.")
    }
    return .sequence(participants: participants.values, messages: messages, notes: notes)
}

// MARK: - Layout (pure data — theme applied at draw time)

struct LaidNode {
    let id: String
    let label: String
    let shape: MermaidShape
    let x: CGFloat
    let y: CGFloat
    let w: CGFloat
    let h: CGFloat
}

struct LaidEdge {
    let from: LaidNode
    let to: LaidNode
    let label: String?
    let style: EdgeStyle
}

struct DiagramLayout {
    let width: CGFloat
    let height: CGFloat
    let nodes: [LaidNode]
    let edges: [LaidEdge]
    let description: String
}

private let NODE_FONT: CGFloat = 12
private let CHAR_W: CGFloat = 6.6
private let LINE_H: CGFloat = 16
private let NODE_GAP: CGFloat = 36
private let LAYER_GAP: CGFloat = 56

private func nodeSize(_ label: String, _ shape: MermaidShape) -> (CGFloat, CGFloat) {
    let lines = wrapLabel(label, maxChars: 16)
    let textW = CGFloat((lines.map { $0.count }.max() ?? 1)) * CHAR_W
    var w = min(max(textW + 26, 56), 190)
    var h = CGFloat(lines.count) * LINE_H + 12
    if shape == .diamond { w += 18; h += 12 }
    if shape == .circle { w = max(w, h); h = w }
    return (w, h)
}

private func wrapLabel(_ label: String, maxChars: Int) -> [String] {
    let words = label.components(separatedBy: .whitespacesAndNewlines).filter { !$0.isEmpty }
    var lines: [String] = []
    var current = ""
    for word in words {
        if !current.isEmpty && current.count + word.count + 1 > maxChars {
            lines.append(current)
            current = ""
        }
        if !current.isEmpty { current.append(" ") }
        current.append(word)
    }
    if !current.isEmpty { lines.append(current) }
    return lines.isEmpty ? [""] : lines
}

func layoutFlowchart(_ chart: MermaidChart) -> DiagramLayout? {
    guard case .flowchart(let vertical, let reversed, let nodes, let edges) = chart else { return nil }
    _ = vertical // layout normalizes to vertical stacking; direction affects order only
    let nodeById = Dictionary(uniqueKeysWithValues: nodes.map { ($0.id, $0) })
    let incoming = Dictionary(grouping: edges, by: { $0.to })
    var memo: [String: Int] = [:]
    var stack: Set<String> = []

    func rank(_ id: String) -> Int {
        if let r = memo[id] { return r }
        if stack.contains(id) { return 0 } // cycle break
        stack.insert(id)
        let r = incoming[id]?.map { rank($0.from) + 1 }.max() ?? 0
        stack.remove(id)
        memo[id] = r
        return r
    }
    nodes.forEach { _ = rank($0.id) }

    let ordered = nodes.sorted {
        (memo[$0.id] ?? 0) * (reversed ? -1 : 1) < (memo[$1.id] ?? 0) * (reversed ? -1 : 1)
    }
    let sizes = Dictionary(uniqueKeysWithValues: ordered.map { ($0.id, nodeSize($0.label, $0.shape)) })
    var layerKeys: [Int: [FlowNode]] = [:]
    for node in ordered {
        layerKeys[memo[node.id] ?? 0, default: []].append(node)
    }
    let layers = layerKeys.sorted { $0.key < $1.key }

    let layerWidths: [Int: CGFloat] = Dictionary(uniqueKeysWithValues: layers.map { key, value in
        let total = value.reduce(0.0) { $0 + Double(sizes[$1.id]!.0) } + Double(NODE_GAP) * Double(max(value.count - 1, 0))
        return (key, CGFloat(total))
    })
    let width = max((layerWidths.values.max() ?? 300), 300)
    var y: CGFloat = 20
    var laid: [LaidNode] = []
    for (_, layerNodes) in layers {
        let layerH = layerNodes.map { sizes[$0.id]!.1 }.max() ?? 0
        let totalW = layerNodes.reduce(0.0) { $0 + Double(sizes[$1.id]!.0) } + Double(NODE_GAP) * Double(max(layerNodes.count - 1, 0))
        var x = (width - CGFloat(totalW)) / 2
        for node in layerNodes {
            let (w, h) = sizes[node.id]!
            laid.append(LaidNode(id: node.id, label: node.label, shape: node.shape,
                                 x: x, y: y + (layerH - h) / 2, w: w, h: h))
            x += w + NODE_GAP
        }
        y += layerH + LAYER_GAP
    }
    let height = y - LAYER_GAP + 20
    let laidById = Dictionary(uniqueKeysWithValues: laid.map { ($0.id, $0) })
    let laidEdges: [LaidEdge] = edges.compactMap { e in
        guard let from = laidById[e.from], let to = laidById[e.to] else { return nil }
        return LaidEdge(from: from, to: to, label: e.label, style: e.style)
    }
    let desc = "Flowchart diagram, \(laid.count) nodes, \(laidEdges.count) connections. Nodes: " +
        laid.map { $0.label.isEmpty ? $0.id : $0.label }.joined(separator: ", ")
    return DiagramLayout(width: width, height: height, nodes: laid, edges: laidEdges, description: String(desc.prefix(400)))
}

struct SeqLayout {
    let width: CGFloat
    let height: CGFloat
    let columns: [(SeqParticipant, CGFloat)] // participant → center x
    let headerTop: CGFloat
    let headerHeight: CGFloat
    let messages: [(CGFloat, CGFloat, String, SeqStyle, CGFloat)] // from, to, text, style, y
    let notes: [(CGFloat, CGFloat, CGFloat, String)] // left, right, y, text
    let description: String
}

func layoutSequence(_ chart: MermaidChart) -> SeqLayout? {
    guard case .sequence(let participants, let messages, let notes) = chart else { return nil }
    let columns: [(SeqParticipant, CGFloat)] = participants.map { p in
        (p, max(CGFloat(p.label.count) * CHAR_W + 20, 90))
    }
    let totalW = columns.reduce(0.0) { $0 + Double($1.1) } + 24
    var centers: [String: CGFloat] = [:]
    var x: CGFloat = 12
    for (p, w) in columns {
        centers[p.id] = x + w / 2
        x += w
    }
    let headerTop: CGFloat = 8
    let headerHeight: CGFloat = 32
    var rows: [(CGFloat, CGFloat, String, SeqStyle, CGFloat)] = []
    var laidNotes: [(CGFloat, CGFloat, CGFloat, String)] = []
    var y = headerTop + headerHeight + 30
    for message in messages {
        let from = centers[message.from] ?? 0
        let to = centers[message.to] ?? 0
        rows.append((from, to, message.text, message.style, y))
        y += message.from == message.to ? 56 : 46
    }
    for note in notes {
        let xs = note.ids.compactMap { centers[$0] }
        if xs.isEmpty { continue }
        let left = max((xs.min() ?? 0) - 45, 4)
        let right = min((xs.max() ?? 0) + 45, CGFloat(totalW) - 4)
        laidNotes.append((left, right, y, note.text))
        y += 40
    }
    let height = y + 16
    let desc = "Sequence diagram, \(participants.count) participants, \(messages.count) messages. Participants: " +
        participants.map { $0.label }.joined(separator: ", ")
    return SeqLayout(
        width: CGFloat(totalW),
        height: height,
        columns: columns.map { ($0.0, centers[$0.0.id] ?? 0) },
        headerTop: headerTop,
        headerHeight: headerHeight,
        messages: rows,
        notes: laidNotes,
        description: String(desc.prefix(400))
    )
}

// MARK: - Renderer

struct MermaidBlockView: View {
    let source: String
    var streaming: Bool = false
    @State private var viewerOpen = false
    @State private var copied = false

    var body: some View {
        mermaidBody
    }

    @ViewBuilder
    private var mermaidBody: some View {
        // Streaming contract: the fenced source renders as a plain open code
        // block — diagram layout never runs at stream cadence.
        if streaming {
            CodeBlockCardView(language: "mermaid", code: source, open: true)
        } else {
            let chart = parseMermaid(source)
            switch chart {
            case .unsupported(let reason):
                MermaidFallbackView(reason: reason, source: source)
            case .flowchart:
                if let layout = layoutFlowchart(chart) {
                    mermaidCard(title: "Mermaid · Flowchart", description: layout.description) {
                        FlowchartCanvasView(layout: layout)
                    }
                } else {
                    MermaidFallbackView(reason: "Couldn't render this diagram.", source: source)
                }
            case .sequence:
                if let layout = layoutSequence(chart) {
                    mermaidCard(title: "Mermaid · Sequence", description: layout.description) {
                        SequenceCanvasView(layout: layout)
                    }
                } else {
                    MermaidFallbackView(reason: "Couldn't render this diagram.", source: source)
                }
            }
        }
    }

    private func mermaidCard(title: String, description: String, @ViewBuilder content: @escaping () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(title)
                    .font(Aero.label())
                    .foregroundStyle(Aero.textMuted)
                    .lineLimit(1)
                Spacer()
                Button {
                    UIPasteboard.general.string = source
                    GSHaptics.success()
                    withAnimation(.easeOut(duration: 0.15)) { copied = true }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) {
                        withAnimation(.easeIn(duration: 0.2)) { copied = false }
                    }
                } label: {
                    Image(systemName: copied ? "checkmark" : "doc.on.doc")
                        .foregroundStyle(copied ? Aero.accent : Aero.textMuted)
                }
                .buttonStyle(KineticPressStyle())
                .accessibilityLabel(copied ? "Copied" : "Copy diagram source")
            }
            .padding(.horizontal, 12)
            .padding(.top, 6)
            // Tap opens the viewer — the conversation's vertical scroll stays
            // dominant; no continuous gesture lives on the inline card.
            content()
                .frame(maxHeight: 340)
                .frame(maxWidth: .infinity)
                .contentShape(Rectangle())
                .onTapGesture { viewerOpen = true }
                .padding(.bottom, 8)
        }
        .background(RoundedRectangle(cornerRadius: 12).fill(Aero.container))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Aero.outline, lineWidth: 1))
        .accessibilityElement(children: .contain)
        .accessibilityLabel(description)
        .fullScreenCover(isPresented: $viewerOpen) {
            MermaidViewerView(title: title, description: description, content: content)
        }
    }
}

private struct MermaidFallbackView: View {
    let reason: String
    let source: String
    @State private var showSource = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Image(systemName: "info.circle")
                    .foregroundStyle(Aero.textTertiary)
                Text("Diagram unavailable")
                    .font(Aero.label())
                    .foregroundStyle(Aero.text)
            }
            Text(reason)
                .font(Aero.caption())
                .foregroundStyle(Aero.textSecondary)
            HStack(spacing: 12) {
                Button(showSource ? "Hide source" : "View source") {
                    withAnimation(.easeOut(duration: 0.2)) { showSource.toggle() }
                }
                .font(Aero.responsive(13, relativeTo: .footnote))
                .foregroundStyle(Aero.textMuted)
                Button("Copy source") {
                    UIPasteboard.general.string = source
                    GSHaptics.success()
                }
                .font(Aero.responsive(13, relativeTo: .footnote))
                .foregroundStyle(Aero.textMuted)
            }
            .buttonStyle(KineticPressStyle())
            if showSource {
                Text(source)
                    .font(.system(size: 12, weight: .regular, design: .monospaced))
                    .foregroundStyle(Aero.text)
                    .textSelection(.enabled)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 12).fill(Aero.container))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Aero.outline, lineWidth: 1))
    }
}

// MARK: - Canvases

private struct DiagramColors {
    let nodeFill: Color
    let nodeStroke: Color
    let text: Color
    let edge: Color
    let noteFill: Color
}

@ViewBuilder
private func FlowchartCanvasView(layout: DiagramLayout) -> some View {
    let colors = DiagramColors(
        nodeFill: Aero.raisedSurface,
        nodeStroke: Aero.outline,
        text: Aero.text,
        edge: Aero.textTertiary,
        noteFill: Aero.accent.opacity(0.10)
    )
    Canvas { context, size in
        let scale = min(min(1, size.width / layout.width), size.height / layout.height)
        let left = (size.width - layout.width * scale) / 2
        let top = (size.height - layout.height * scale) / 2
        var transform = context.transform
        transform = transform.translatedBy(x: left, y: top)
        transform = transform.scaledBy(x: scale, y: scale)
        context.transform = transform
        for edge in layout.edges {
            drawEdgeElbow(&context, edge: edge, colors: colors, vertical: true)
        }
        for node in layout.nodes {
            drawNodeBox(&context, x: node.x, y: node.y, w: node.w, h: node.h, label: node.label, shape: node.shape, colors: colors)
        }
    }
    .accessibilityLabel(layout.description)
}

@ViewBuilder
private func SequenceCanvasView(layout: SeqLayout) -> some View {
    let colors = DiagramColors(
        nodeFill: Aero.raisedSurface,
        nodeStroke: Aero.outline,
        text: Aero.text,
        edge: Aero.textTertiary,
        noteFill: Aero.accent.opacity(0.10)
    )
    Canvas { context, size in
        let scale = min(min(1, size.width / layout.width), size.height / layout.height)
        let left = (size.width - layout.width * scale) / 2
        let top = (size.height - layout.height * scale) / 2
        var transform = context.transform
        transform = transform.translatedBy(x: left, y: top)
        transform = transform.scaledBy(x: scale, y: scale)
        context.transform = transform
        // Lifelines
        for (_, cx) in layout.columns {
            var lifeline = Path()
            lifeline.move(to: CGPoint(x: cx, y: layout.headerTop + layout.headerHeight + 4))
            lifeline.addLine(to: CGPoint(x: cx, y: layout.height - 8))
            context.stroke(
                lifeline,
                with: .color(colors.edge),
                style: StrokeStyle(lineWidth: 1.2, dash: [6, 6]))
        }
        // Header boxes
        for (p, cx) in layout.columns {
            let w = max(CGFloat(p.label.count) * CHAR_W + 20, 90)
            drawNodeBox(&context, x: cx - w / 2, y: layout.headerTop, w: w, h: layout.headerHeight,
                        label: p.label, shape: .rect, colors: colors)
        }
        // Messages
        for (from, to, text, style, y) in layout.messages {
            drawSeqMessage(&context, from: from, to: to, text: text, style: style, y: y, colors: colors)
        }
        // Notes
        for (left, right, y, text) in layout.notes {
            let rect = CGRect(x: left, y: y, width: right - left, height: 28)
            context.fill(Path(roundedRect: rect, cornerRadius: 8), with: .color(colors.noteFill))
            context.draw(
                Text(text).font(.system(size: NODE_FONT)).foregroundColor(colors.text),
                in: CGRect(x: left, y: y + 6, width: right - left, height: 20))
        }
    }
    .accessibilityLabel(layout.description)
}

private func drawNodeBox(_ context: inout GraphicsContext, x: CGFloat, y: CGFloat, w: CGFloat, h: CGFloat, label: String, shape: MermaidShape, colors: DiagramColors) {
    let rect = CGRect(x: x, y: y, width: w, height: h)
    switch shape {
    case .rect, .round, .stadium, .circle:
        let radius: CGFloat
        switch shape {
        case .rect: radius = 4
        case .stadium, .circle: radius = h / 2
        default: radius = h / 2.4
        }
        let path = Path(roundedRect: rect, cornerRadius: radius)
        context.fill(path, with: .color(colors.nodeFill))
        context.stroke(path, with: .color(colors.nodeStroke), lineWidth: 1.4)
    case .diamond:
        var path = Path()
        path.move(to: CGPoint(x: x + w / 2, y: y))
        path.addLine(to: CGPoint(x: x + w, y: y + h / 2))
        path.addLine(to: CGPoint(x: x + w / 2, y: y + h))
        path.addLine(to: CGPoint(x: x, y: y + h / 2))
        path.closeSubpath()
        context.fill(path, with: .color(colors.nodeFill))
        context.stroke(path, with: .color(colors.nodeStroke), lineWidth: 1.4)
    }
    let lines = wrapLabel(label, maxChars: 16)
    let startY = y + h / 2 - CGFloat(lines.count - 1) * LINE_H / 2
    for (index, line) in lines.enumerated() {
        let lineRect = CGRect(x: x, y: startY + CGFloat(index) * LINE_H - LINE_H / 2, width: w, height: LINE_H)
        context.draw(
            Text(line).font(.system(size: NODE_FONT)).foregroundColor(colors.text),
            in: lineRect)
    }
}

private func drawEdgeElbow(_ context: inout GraphicsContext, edge: LaidEdge, colors: DiagramColors, vertical: Bool) {
    let style = StrokeStyle(lineWidth: edge.style == .thick ? 2.2 : 1.4, dash: edge.style == .dashed ? [8, 6] : [])
    if vertical {
        let sx = edge.from.x + edge.from.w / 2
        let sy = edge.from.y + edge.from.h
        let tx = edge.to.x + edge.to.w / 2
        let ty = edge.to.y
        let midY = (sy + ty) / 2
        var path = Path()
        path.move(to: CGPoint(x: sx, y: sy))
        path.addLine(to: CGPoint(x: sx, y: midY))
        path.addLine(to: CGPoint(x: tx, y: midY))
        path.addLine(to: CGPoint(x: tx, y: ty - 7))
        context.stroke(path, with: .color(colors.edge), style: style)
        drawArrowHead(&context, x: tx, y: ty, down: true, color: colors.edge)
        if let label = edge.label {
            context.draw(
                Text(label).font(.system(size: NODE_FONT)).foregroundColor(colors.edge),
                in: CGRect(x: (sx + tx) / 2 - 60, y: midY - 16, width: 120, height: 14))
        }
    } else {
        let sx = edge.from.x + edge.from.w
        let sy = edge.from.y + edge.from.h / 2
        let tx = edge.to.x
        let ty = edge.to.y + edge.to.h / 2
        let midX = (sx + tx) / 2
        var path = Path()
        path.move(to: CGPoint(x: sx, y: sy))
        path.addLine(to: CGPoint(x: midX, y: sy))
        path.addLine(to: CGPoint(x: midX, y: ty))
        path.addLine(to: CGPoint(x: tx - 7, y: ty))
        context.stroke(path, with: .color(colors.edge), style: style)
        drawArrowHead(&context, x: tx, y: ty, down: false, color: colors.edge)
        if let label = edge.label {
            context.draw(
                Text(label).font(.system(size: NODE_FONT)).foregroundColor(colors.edge),
                in: CGRect(x: midX - 60, y: (sy + ty) / 2 - 16, width: 120, height: 14))
        }
    }
}

private func drawArrowHead(_ context: inout GraphicsContext, x: CGFloat, y: CGFloat, down: Bool, color: Color) {
    var path = Path()
    if down {
        path.move(to: CGPoint(x: x, y: y))
        path.addLine(to: CGPoint(x: x - 4.5, y: y - 8))
        path.addLine(to: CGPoint(x: x + 4.5, y: y - 8))
    } else {
        path.move(to: CGPoint(x: x, y: y))
        path.addLine(to: CGPoint(x: x - 8, y: y - 4.5))
        path.addLine(to: CGPoint(x: x - 8, y: y + 4.5))
    }
    path.closeSubpath()
    context.fill(path, with: .color(color))
}

private func drawSeqMessage(_ context: inout GraphicsContext, from: CGFloat, to: CGFloat, text: String, style: SeqStyle, y: CGFloat, colors: DiagramColors) {
    let dash: [CGFloat] = style == .dashed ? [8, 6] : []
    if from == to {
        var path = Path()
        path.move(to: CGPoint(x: from, y: y - 8))
        path.addLine(to: CGPoint(x: from + 26, y: y - 8))
        path.addLine(to: CGPoint(x: from + 26, y: y + 8))
        path.addLine(to: CGPoint(x: from, y: y + 8))
        context.stroke(path, with: .color(colors.edge), style: StrokeStyle(lineWidth: 1.4, dash: dash))
        drawArrowHead(&context, x: from + 1, y: y + 8, down: false, color: colors.edge)
        context.draw(
            Text(text).font(.system(size: NODE_FONT)).foregroundColor(colors.text),
            in: CGRect(x: from + 34, y: y - 9, width: 220, height: 18))
    } else {
        let left = min(from, to)
        let right = max(from, to)
        var line = Path()
        line.move(to: CGPoint(x: left, y: y))
        line.addLine(to: CGPoint(x: right - (style == .cross ? 0 : 2), y: y))
        context.stroke(line, with: .color(colors.edge), style: StrokeStyle(lineWidth: 1.4, dash: dash))
        switch style {
        case .cross:
            var x1 = Path()
            x1.move(to: CGPoint(x: right - 9, y: y - 9))
            x1.addLine(to: CGPoint(x: right + 1, y: y + 1))
            var x2 = Path()
            x2.move(to: CGPoint(x: right - 9, y: y + 1))
            x2.addLine(to: CGPoint(x: right + 1, y: y - 9))
            context.stroke(x1, with: .color(colors.edge), lineWidth: 1.6)
            context.stroke(x2, with: .color(colors.edge), lineWidth: 1.6)
        case .async:
            var a1 = Path()
            a1.move(to: CGPoint(x: right - 8, y: y - 4.5))
            a1.addLine(to: CGPoint(x: right, y: y))
            var a2 = Path()
            a2.move(to: CGPoint(x: right - 8, y: y + 4.5))
            a2.addLine(to: CGPoint(x: right, y: y))
            context.stroke(a1, with: .color(colors.edge), lineWidth: 1.4)
            context.stroke(a2, with: .color(colors.edge), lineWidth: 1.4)
        default:
            drawArrowHead(&context, x: to > from ? right : left, y: y, down: false, color: colors.edge)
        }
        context.draw(
            Text(text).font(.system(size: NODE_FONT)).foregroundColor(colors.text),
            in: CGRect(x: (left + right) / 2 - 120, y: y - 22, width: 240, height: 16))
    }
}

// MARK: - Full-screen viewer (zoom + pan, double-tap reset)

private struct MermaidViewerView<Content: View>: View {
    let title: String
    let description: String
    let content: () -> Content
    @Environment(\.dismiss) private var dismiss
    @State private var scale: CGFloat = 1
    @State private var offset: CGSize = .zero

    var body: some View {
        ZStack(alignment: .top) {
            Aero.background.ignoresSafeArea()
            content()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .scaleEffect(scale)
                .offset(offset)
                .gesture(
                    MagnificationGesture()
                        .onChanged { value in
                            scale = min(max(scale * value, 1), 6)
                        }
                )
                .simultaneousGesture(
                    DragGesture()
                        .onChanged { value in
                            offset = CGSize(width: offset.width + value.translation.width * 0.35,
                                            height: offset.height + value.translation.height * 0.35)
                        }
                )
                .onTapGesture(count: 2) {
                    withAnimation(.easeOut(duration: 0.2)) {
                        scale = 1
                        offset = .zero
                    }
                }
            HStack {
                Text(title)
                    .font(Aero.label())
                    .foregroundStyle(Aero.text)
                    .accessibilityLabel(description)
                Spacer()
                Button("Close") { dismiss() }
                    .font(Aero.responsive(15, relativeTo: .subheadline))
                    .foregroundStyle(Aero.textMuted)
            }
            .padding(16)
        }
    }
}
