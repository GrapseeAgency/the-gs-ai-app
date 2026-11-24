import Foundation

/// Character ranges are extended grapheme clusters, never UTF-16 slices.
struct FluidTextBand: Equatable {
    let start: Int
    let end: Int
    let born: TimeInterval
    let duration: TimeInterval

    func progress(at now: TimeInterval) -> Double {
        min(1, max(0, (now - born) / duration))
    }

    func opacity(at now: TimeInterval) -> Double {
        let remaining = 1 - progress(at: now)
        return 1 - 0.42 * remaining * remaining * remaining
    }
}

/// A bounded, monotonic visual clock independent of the response transport.
struct FluidTextMotion {
    private var previous: String
    private var lastArrival: TimeInterval?
    private(set) var bands: [FluidTextBand] = []

    init(initial: String) { previous = initial }

    mutating func update(_ text: String, now: TimeInterval, enabled: Bool = true) {
        // Compare Characters, not canonical-equivalent UTF-8 byte prefixes.
        // A partial grapheme or a parser rewrite settles rather than replaying.
        guard enabled, text.hasPrefix(previous) else {
            previous = text
            lastArrival = nil
            bands = []
            return
        }
        bands.removeAll { $0.progress(at: now) >= 1 }
        guard text != previous else { return }
        let oldCount = previous.count
        let count = text.count
        guard count > oldCount else {
            previous = text
            lastArrival = nil
            bands = []
            return
        }
        previous = text
        let duration = lastArrival.map { min(0.150, max(0.100, 0.100 + (now - $0) * 0.5)) } ?? 0.150
        lastArrival = now
        let floor = max(oldCount, count - 48)
        bands.removeAll { $0.end <= count - 48 }
        var start = floor
        var incoming = 0
        while start < count && incoming < 4 {
            let end = incoming == 3 ? count : min(count, start + 12)
            bands.append(FluidTextBand(start: start, end: end, born: now + Double(incoming) * 0.008, duration: duration))
            start = end
            incoming += 1
        }
        bands = Array(bands.suffix(8))
    }

    mutating func settle() { bands = [] }
}
