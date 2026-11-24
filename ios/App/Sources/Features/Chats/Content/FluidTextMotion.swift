import Foundation

/// Character ranges are extended grapheme clusters, never UTF-16 slices.
struct FluidTextBand: Hashable {
    let start: Int
    let end: Int
    let born: TimeInterval
    let duration: TimeInterval
    var phaseFrom: Double = 0
    var phaseTo: Double = 1
    var travel: Double = 0.24

    func progress(at now: TimeInterval) -> Double {
        min(1, max(0, (now - born) / duration))
    }

    func localProgress(at now: TimeInterval, position: Double) -> Double {
        fluidLocalProgress(progress(at: now), position: phaseFrom + (phaseTo - phaseFrom) * min(1, max(0, position)), travel: travel)
    }

    func opacity(at now: TimeInterval, position: Double = 0) -> Double {
        fluidInkOpacity(localProgress(at: now, position: position))
    }
}

func fluidLocalProgress(_ progress: Double, position: Double, travel: Double = 0.24) -> Double {
    min(1, max(0, (progress - travel * min(1, max(0, position))) / (1 - travel)))
}

func fluidInkOpacity(_ progress: Double) -> Double {
    let p = min(1, max(0, progress))
    let eased = p * p * p * (p * (p * 6 - 15) + 10)
    return 0.18 + 0.82 * eased
}

/// A bounded, monotonic visual clock independent of the response transport.
struct FluidTextMotion {
    private var previous: String
    private var lastArrival: TimeInterval?
    private var cadence: TimeInterval = 0.042
    private var frontUntil: TimeInterval = 0
    private(set) var bands: [FluidTextBand] = []

    init(initial: String) { previous = initial }

    mutating func update(_ text: String, now: TimeInterval, enabled: Bool = true, sourceAppend: Bool = false) {
        // An actual source append may reformat existing rendered Markdown.
        // Arbitrary replacements/reset events still clear the motion ledger.
        guard enabled, sourceAppend || text.hasPrefix(previous) else {
            previous = text
            lastArrival = nil
            cadence = 0.042
            frontUntil = now
            bands = []
            return
        }
        bands.removeAll { $0.progress(at: now) >= 1 }
        guard text != previous else { return }
        let oldCount: Int
        let count = text.count
        if text.hasPrefix(previous) {
            oldCount = previous.count
        } else {
            let match = reconcileFluidAppend(previous, text)
            let oldBands = bands
            bands = match.spans.flatMap { span in
                oldBands.compactMap { band -> FluidTextBand? in
                    let from = max(span.oldStart, band.start)
                    let to = min(span.oldEnd, band.end)
                    guard from < to else { return nil }
                    let length = Double(max(1, band.end - band.start))
                    let phaseFrom = band.phaseFrom + (band.phaseTo - band.phaseFrom) * Double(from - band.start) / length
                    let phaseTo = band.phaseFrom + (band.phaseTo - band.phaseFrom) * Double(to - band.start) / length
                    return FluidTextBand(start: span.newStart + from - span.oldStart,
                        end: span.newStart + to - span.oldStart, born: band.born, duration: band.duration,
                        phaseFrom: phaseFrom, phaseTo: phaseTo, travel: band.travel)
                }
            }.sorted { $0.start < $1.start }
            oldCount = match.newTailStart
        }
        previous = text
        guard count > oldCount else { return }
        if let lastArrival {
            let sample = min(0.160, max(0.016, now - lastArrival))
            cadence = cadence * 0.78 + sample * 0.22
        }
        lastArrival = now
        let duration = min(0.265, max(0.185, 0.175 + cadence * 0.85))
        // An optical arrival cursor joins consecutive real packets. It
        // never gates source text and is bounded even under burst delivery.
        let horizon = now + 0.075
        var front = max(now, min(frontUntil, horizon))
        // Cover the entire append, including large provider bursts. Retain
        // every unfinished band until its own deadline; no tail-size eviction.
        let suffix = Array(text.suffix(count - oldCount))
        let groupSize = max(32, (suffix.count + 3) / 4)
        var start = oldCount
        var incoming = 0
        while start < count && incoming < 4 {
            let ideal = min(count, start + groupSize)
            var word = ideal
            let lower = max(start + 1, ideal - 6)
            while word > lower && word < count &&
                !suffix[word - 1 - oldCount].isWhitespace && !suffix[word - oldCount].isWhitespace {
                word -= 1
            }
            let preferred = word > lower ? word : ideal
            let end = incoming == 3 ? count : preferred
            let length = end - start
            let travel = min(0.48, max(0.18, 0.18 + Double(min(120, length)) * 0.0025))
            bands.append(FluidTextBand(start: start, end: end, born: front,
                                       duration: duration, travel: travel))
            front = min(horizon, front + min(0.045, 0.009 + Double(min(60, length)) * 0.001))
            start = end
            incoming += 1
        }
        if incoming > 0 { frontUntil = front }
    }

    mutating func settle() { bands = []; frontUntil = 0 }
}

struct FluidMatchSpan {
    let oldStart: Int
    let oldEnd: Int
    let newStart: Int
}
struct FluidAppendMatch {
    let spans: [FluidMatchSpan]
    let newTailStart: Int
}

/// Conservative unique anchor in the native Character stream. No guessed
/// words or synthetic tokens; existing ages survive delimiter removal.
func reconcileFluidAppend(_ old: String, _ next: String) -> FluidAppendMatch {
    let a = Array(old)
    let b = Array(next)
    var prefix = 0
    while prefix < min(a.count, b.count), a[prefix] == b[prefix] { prefix += 1 }
    var spans: [FluidMatchSpan] = []
    if prefix > 0 { spans.append(FluidMatchSpan(oldStart: 0, oldEnd: prefix, newStart: 0)) }
    let syntax: Set<Character> = Set("*_~`[]()")
    var oldEnd = a.count
    while oldEnd > prefix, a.count - oldEnd < 4, syntax.contains(a[oldEnd - 1]) { oldEnd -= 1 }
    if oldEnd == prefix { return FluidAppendMatch(spans: spans, newTailStart: prefix) }
    let anchor = max(prefix, oldEnd - 12)
    let length = oldEnd - anchor
    guard length >= 3, b.count - prefix >= length else {
        return FluidAppendMatch(spans: spans, newTailStart: b.count)
    }
    var location: Int?
    for at in prefix...(b.count - length) {
        if b[at..<(at + length)].elementsEqual(a[anchor..<oldEnd]) {
            if location != nil { return FluidAppendMatch(spans: spans, newTailStart: b.count) }
            location = at
        }
    }
    guard let at = location else { return FluidAppendMatch(spans: spans, newTailStart: b.count) }
    var oldStart = anchor
    var newStart = at
    while oldStart > prefix, newStart > prefix, a[oldStart - 1] == b[newStart - 1] {
        oldStart -= 1; newStart -= 1
    }
    spans.append(FluidMatchSpan(oldStart: oldStart, oldEnd: oldEnd, newStart: newStart))
    return FluidAppendMatch(spans: spans, newTailStart: at + length)
}
