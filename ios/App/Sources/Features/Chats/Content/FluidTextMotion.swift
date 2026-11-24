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
        // Bounded suffix only; avoid an O(document) character array.
        let suffix = Array(text.suffix(72))
        let floor = max(oldCount, count - 72)
        bands.removeAll { $0.end <= count - 72 }
        var start = floor
        var incoming = 0
        while start < count && incoming < 6 {
            let ideal = min(count, start + 12)
            var word = ideal
            let origin = count - suffix.count
            while word > start + 6 && word < count &&
                !suffix[word - 1 - origin].isWhitespace && !suffix[word - origin].isWhitespace {
                word -= 1
            }
            let preferred = word > start + 6 ? word : ideal
            let end = incoming == 5 ? count : preferred
            bands.append(FluidTextBand(start: start, end: end, born: now + Double(incoming) * stagger, duration: duration))
            start = end
            incoming += 1
        }
        bands = Array(bands.suffix(12))
    }

    mutating func settle() { bands = [] }
}
