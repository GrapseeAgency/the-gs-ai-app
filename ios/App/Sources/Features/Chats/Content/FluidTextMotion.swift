import Foundation

/// Character ranges are extended grapheme clusters, never UTF-16 slices.
struct FluidTextBand: Hashable {
    let start: Int
    let end: Int
    let born: TimeInterval
    let duration: TimeInterval

    func progress(at now: TimeInterval) -> Double {
        min(1, max(0, (now - born) / duration))
    }

    func localProgress(at now: TimeInterval, position: Double) -> Double {
        fluidLocalProgress(progress(at: now), position: position)
    }

    func opacity(at now: TimeInterval, position: Double = 0) -> Double {
        fluidInkOpacity(localProgress(at: now, position: position))
    }
}

func fluidLocalProgress(_ progress: Double, position: Double) -> Double {
    min(1, max(0, (progress - 0.24 * min(1, max(0, position))) / 0.76))
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
    private(set) var bands: [FluidTextBand] = []

    init(initial: String) { previous = initial }

    mutating func update(_ text: String, now: TimeInterval, enabled: Bool = true) {
        // Compare Characters, not canonical-equivalent UTF-8 byte prefixes.
        // A partial grapheme or a parser rewrite settles rather than replaying.
        guard enabled, text.hasPrefix(previous) else {
            previous = text
            lastArrival = nil
            cadence = 0.042
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
            cadence = 0.042
            bands = []
            return
        }
        previous = text
        if let lastArrival {
            let sample = min(0.160, max(0.016, now - lastArrival))
            cadence = cadence * 0.78 + sample * 0.22
        }
        lastArrival = now
        let duration = min(0.265, max(0.185, 0.175 + cadence * 0.85))
        let stagger = min(0.020, max(0.008, cadence * 0.25))
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
            bands.append(FluidTextBand(start: start, end: end, born: now + Double(incoming) * stagger, duration: duration))
            start = end
            incoming += 1
        }
    }

    mutating func settle() { bands = [] }
}
