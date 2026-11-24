import SwiftUI

private struct FluidResponseStreamingKey: EnvironmentKey {
    static let defaultValue = false
}

private struct FluidFreshMountKey: EnvironmentKey {
    static let defaultValue = false
}
private struct FluidMotionAllowedKey: EnvironmentKey {
    static let defaultValue = true
}
private struct FluidNewBlockFloorKey: EnvironmentKey {
    static let defaultValue = Int.max
}

extension EnvironmentValues {
    var fluidMotionAllowed: Bool {
        get { self[FluidMotionAllowedKey.self] }
        set { self[FluidMotionAllowedKey.self] = newValue }
    }
    var fluidNewBlockFloor: Int {
        get { self[FluidNewBlockFloorKey.self] }
        set { self[FluidNewBlockFloorKey.self] = newValue }
    }
    var fluidFreshMount: Bool {
        get { self[FluidFreshMountKey.self] }
        set { self[FluidFreshMountKey.self] = newValue }
    }
    var fluidResponseStreaming: Bool {
        get { self[FluidResponseStreamingKey.self] }
        set { self[FluidResponseStreamingKey.self] = newValue }
    }
}

/// Stable native text identity from arrival through completion. The view's
/// visual tail owns its deadline; it is not removed when streaming ends.
struct FluidResponseText: View {
    @Environment(\.fluidResponseStreaming) private var streaming
    @Environment(\.fluidFreshMount) private var freshMount
    @Environment(\.fluidMotionAllowed) private var motionAllowed
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.colorSchemeContrast) private var contrast
    @State private var motion: FluidTextMotion?
    let value: AttributedString
    let ink: Color

    init(_ value: AttributedString, ink: Color) {
        self.value = value
        self.ink = ink
    }

    private struct Input: Equatable {
        let raw: String
        let streaming: Bool
        let allowed: Bool
    }

    var body: some View {
        let raw = String(value.characters)
        let now = ProcessInfo.processInfo.systemUptime
        let allowed = motionAllowed && !reduceMotion && contrast != .increased
        let active = allowed && (streaming || motion?.bands.isEmpty == false)
        let input = Input(raw: raw, streaming: streaming, allowed: allowed)
        let preview = snapshot(raw, now: now, active: active)
        let deadline = preview.bands.map { $0.born + $0.duration }.max()
        Group {
            if !active || preview.bands.isEmpty {
                Text(value)
            } else if #available(iOS 18.0, *) {
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
        .onChange(of: input, initial: true) { _, _ in
            motion = active ? preview : nil
        }
        .task(id: deadline) {
            guard let deadline else { return }
            let remaining = max(0, deadline - ProcessInfo.processInfo.systemUptime)
            try? await Task.sleep(nanoseconds: UInt64(remaining * 1_000_000_000) + 1_000_000)
            guard !Task.isCancelled else { return }
            // Never discard a later append or restart a completed range.
            if let latest = motion?.bands.map({ $0.born + $0.duration }).max(), latest <= deadline {
                motion?.settle()
            }
        }
    }

    private func snapshot(_ raw: String, now: TimeInterval, active: Bool) -> FluidTextMotion {
        var next = motion ?? FluidTextMotion(initial: active && streaming && freshMount ? "" : raw)
        next.update(raw, now: now, enabled: active, sourceAppend: freshMount)
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
            // The older native text path also travels across the arriving
            // range instead of fading the entire packet at a single opacity.
            let length = max(1, band.end - band.start)
            let step = max(1, (length + 7) / 8)
            var offset = band.start
            while offset < band.end {
                let end = min(band.end, offset + step)
                let range = index(offset)..<index(end)
                let position = Double(offset + end - 2 * band.start) / (2 * Double(length))
                let alpha = band.opacity(at: now, position: position)
                if alpha >= 1 {
                    result.append(AttributedString(value[range]))
                } else {
                    for run in value[range].runs {
                        var part = AttributedString(value[run.range])
                        part.foregroundColor = (run.foregroundColor ?? ink).opacity(alpha)
                        result.append(part)
                    }
                }
                offset = end
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
        // The phase is continuous across native runs and wrapped lines,
        // including inline bold/link changes. It does not restart at each run.
        var totals: [FluidTextBand: Int] = [:]
        for line in layout {
            for run in line {
                if let attribute = run[FluidRevealAttribute.self] {
                    totals[attribute.band, default: 0] += run.count
                }
            }
        }
        var consumed: [FluidTextBand: Int] = [:]
        for line in layout {
            for run in line {
                guard let attribute = run[FluidRevealAttribute.self] else {
                    context.draw(run)
                    continue
                }
                let band = attribute.band
                let offset = consumed[band, default: 0]
                let count = max(1, totals[band, default: run.count])
                consumed[band] = offset + run.count
                if band.progress(at: now) >= 1 {
                    context.draw(run)
                    continue
                }
                let bounds = run.typographicBounds.rect
                let startPhase = Double(offset) / Double(count)
                let endPhase = Double(offset + run.count) / Double(count)
                let last = band.localProgress(at: now, position: endPhase)
                if last >= 1 {
                    context.draw(run)
                    continue
                }
                // One optical layer per active native run, not one filtered
                // render pass per letter. The fully settled glyphs are drawn
                // outside the filter and never move again.
                var optical = context
                let softness = (1 - last) * (1 - last)
                optical.addFilter(.blur(radius: 0.85 * softness))
                optical.drawLayer { layer in
                    for slice in run {
                        let rect = slice.typographicBounds.rect
                        let x = min(1, max(0, (rect.midX - bounds.minX) / max(1, bounds.width)))
                        let directed = run.layoutDirection == .rightToLeft ? 1 - x : x
                        let position = startPhase + (endPhase - startPhase) * Double(directed)
                        let local = band.localProgress(at: now, position: position)
                        if local >= 1 { continue }
                        var copy = layer
                        copy.opacity = fluidInkOpacity(local)
                        copy.draw(slice)
                    }
                }
                for slice in run {
                    let rect = slice.typographicBounds.rect
                    let x = min(1, max(0, (rect.midX - bounds.minX) / max(1, bounds.width)))
                    let directed = run.layoutDirection == .rightToLeft ? 1 - x : x
                    let position = startPhase + (endPhase - startPhase) * Double(directed)
                    if band.localProgress(at: now, position: position) >= 1 {
                        context.draw(slice)
                    }
                }
            }
        }
    }
}
