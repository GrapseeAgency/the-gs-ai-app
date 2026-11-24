import SwiftUI

private struct FluidResponseStreamingKey: EnvironmentKey {
    static let defaultValue = false
}

extension EnvironmentValues {
    var fluidResponseStreaming: Bool {
        get { self[FluidResponseStreamingKey.self] }
        set { self[FluidResponseStreamingKey.self] = newValue }
    }
}

/// The original attributed text is always present. Only its freshly appended
/// ranges receive paint-level motion; no artificial pacing or layout animation.
struct FluidResponseText: View {
    @Environment(\.fluidResponseStreaming) private var streaming
    let value: AttributedString
    let ink: Color

    init(_ value: AttributedString, ink: Color) {
        self.value = value
        self.ink = ink
    }

    var body: some View {
        if streaming {
            LiveFluidResponseText(value: value, ink: ink)
        } else {
            Text(value)
        }
    }
}

private struct LiveFluidResponseText: View {
    let value: AttributedString
    let ink: Color
    @State private var motion: FluidTextMotion

    init(value: AttributedString, ink: Color) {
        self.value = value
        self.ink = ink
        _motion = State(initialValue: FluidTextMotion(initial: String(value.characters)))
    }

    var body: some View {
        let raw = String(value.characters)
        let now = ProcessInfo.processInfo.systemUptime
        // Derive the next paint synchronously so an arriving chunk cannot
        // flash at full opacity before the onChange commits its clock.
        let preview = snapshot(raw, now: now)
        Group {
            if preview.bands.isEmpty {
                Text(value)
            } else {
                TimelineView(.animation) { _ in
                    let time = ProcessInfo.processInfo.systemUptime
                    if #available(iOS 18.0, *) {
                        markedText(bands: preview.bands)
                            .textRenderer(FluidGlyphRenderer(now: time))
                    } else {
                        Text(fadedText(bands: preview.bands, now: time))
                    }
                }
            }
        }
        .onChange(of: raw) { newValue in
            motion.update(newValue, now: ProcessInfo.processInfo.systemUptime)
        }
        // This task only stops the paint clock. It does not gate text,
        // network delivery, copying, scrolling or completion.
        .task(id: raw) {
            guard !preview.bands.isEmpty else { return }
            try? await Task.sleep(nanoseconds: 190_000_000)
            guard !Task.isCancelled else { return }
            motion.settle()
        }
    }

    private func snapshot(_ raw: String, now: TimeInterval) -> FluidTextMotion {
        var next = motion
        next.update(raw, now: now)
        return next
    }

    private func index(_ offset: Int) -> AttributedString.Index {
        value.characters.index(value.startIndex, offsetBy: offset)
    }

    @available(iOS 18.0, *)
    private func markedText(bands: [FluidTextBand]) -> Text {
        var result = Text("")
        var cursor = 0
        let count = value.characters.count
        for band in bands where band.start >= cursor && band.end <= count {
            if cursor < band.start {
                result = result + Text(AttributedString(value[index(cursor)..<index(band.start)]))
            }
            let fragment = AttributedString(value[index(band.start)..<index(band.end)])
            result = result + Text(fragment).customAttribute(FluidRevealAttribute(band: band))
            cursor = band.end
        }
        if cursor < count {
            result = result + Text(AttributedString(value[index(cursor)..<value.endIndex]))
        }
        return result
    }

    // iOS 17: same timing, stable native Text and existing inline attributes.
    // No unsupported shader or fake typewriter fallback.
    private func fadedText(bands: [FluidTextBand], now: TimeInterval) -> AttributedString {
        var result = value
        for band in bands where band.end <= value.characters.count {
            let range = index(band.start)..<index(band.end)
            let alpha = band.opacity(at: now)
            guard alpha < 1 else { continue }
            // Preserve citation/link/custom glyph colors rather than painting
            // every new run with the default paragraph color.
            let runs = value[range].runs.map { ($0.range, $0.foregroundColor ?? ink) }
            for (runRange, color) in runs {
                result[runRange].foregroundColor = color.opacity(alpha)
            }
        }
        return result
    }
}

@available(iOS 18.0, *)
private struct FluidRevealAttribute: TextAttribute {
    let band: FluidTextBand
}

@available(iOS 18.0, *)
private struct FluidGlyphRenderer: TextRenderer {
    let now: TimeInterval

    func draw(layout: Text.Layout, in context: inout GraphicsContext) {
        for line in layout {
            for run in line {
                guard let attribute = run[FluidRevealAttribute.self] else {
                    context.draw(run)
                    continue
                }
                let progress = attribute.band.progress(at: now)
                if progress >= 1 {
                    context.draw(run)
                } else {
                    var copy = context
                    copy.opacity = attribute.band.opacity(at: now)
                    // Sub-point, localized softening only, with no glyph
                    // translation or layout movement. Already settled runs
                    // are drawn directly without a filter.
                    copy.addFilter(.blur(radius: 0.7 * (1 - progress)))
                    copy.draw(run)
                }
            }
        }
    }
}
