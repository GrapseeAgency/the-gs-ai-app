import SwiftUI

private struct FluidResponseStreamingKey: EnvironmentKey {
    static let defaultValue = false
}

private struct FluidFreshMountKey: EnvironmentKey {
    static let defaultValue = false
}

extension EnvironmentValues {
    var fluidFreshMount: Bool {
        get { self[FluidFreshMountKey.self] }
        set { self[FluidFreshMountKey.self] = newValue }
    }
    var fluidResponseStreaming: Bool {
        get { self[FluidResponseStreamingKey.self] }
        set { self[FluidResponseStreamingKey.self] = newValue }
    }
}

/// The original attributed text is always present. Only its freshly appended
/// ranges receive paint-level motion; no artificial pacing or layout animation.
struct FluidResponseText: View {
    @Environment(\.fluidResponseStreaming) private var streaming
    @Environment(\.fluidFreshMount) private var freshMount
    let value: AttributedString
    let ink: Color

    init(_ value: AttributedString, ink: Color) {
        self.value = value
        self.ink = ink
    }

    var body: some View {
        if streaming {
            LiveFluidResponseText(value: value, ink: ink, freshMount: freshMount)
        } else {
            Text(value)
        }
    }
}

private struct LiveFluidResponseText: View {
    let value: AttributedString
    let ink: Color
    @State private var motion: FluidTextMotion

    init(value: AttributedString, ink: Color, freshMount: Bool) {
        self.value = value
        self.ink = ink
        _motion = State(initialValue: FluidTextMotion(initial: freshMount ? "" : String(value.characters)))
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
            } else if #available(iOS 18.0, *) {
                // Build the marked runs once per incoming chunk. The display
                // clock changes only the native renderer, not the text runs.
                let marked = markedText(bands: preview.bands)
                TimelineView(.animation) { _ in
                    marked.textRenderer(FluidGlyphRenderer(now: ProcessInfo.processInfo.systemUptime))
                }
            } else {
                TimelineView(.animation) { _ in
                    Text(fadedText(bands: preview.bands, now: ProcessInfo.processInfo.systemUptime))
                }
            }
        }
        .onAppear { motion.update(raw, now: ProcessInfo.processInfo.systemUptime) }
        .onChange(of: raw) { newValue in
            motion.update(newValue, now: ProcessInfo.processInfo.systemUptime)
        }
        // This task only stops the paint clock. It does not gate text,
        // network delivery, copying, scrolling or completion.
        .task(id: raw) {
            guard !preview.bands.isEmpty else { return }
            try? await Task.sleep(nanoseconds: 390_000_000)
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
        var result = AttributedString()
        var cursor = 0
        let count = value.characters.count
        for band in bands where band.start >= cursor && band.end <= count {
            if cursor < band.start {
                result.append(AttributedString(value[index(cursor)..<index(band.start)]))
            }
            let range = index(band.start)..<index(band.end)
            let alpha = band.opacity(at: now)
            if alpha >= 1 {
                result.append(AttributedString(value[range]))
            } else {
                // Copy each immutable source run; do not reuse indices into
                // storage whose attribute runs have already been mutated.
                for run in value[range].runs {
                    var part = AttributedString(value[run.range])
                    part.foregroundColor = (run.foregroundColor ?? ink).opacity(alpha)
                    result.append(part)
                }
            }
            cursor = band.end
        }
        if cursor < count {
            result.append(AttributedString(value[index(cursor)..<value.endIndex]))
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
                    let remaining = 1 - progress
                    copy.translateBy(x: 0, y: 0.9 * remaining * remaining)
                    copy.addFilter(.blur(radius: 1.8 * remaining))
                    copy.draw(run)
                }
            }
        }
    }
}
