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
        let fresh: Bool
    }

    var body: some View {
        let raw = String(value.characters)
        let now = ProcessInfo.processInfo.systemUptime
        let allowed = motionAllowed && !reduceMotion && contrast != .increased
        // A provider can finish with a final append after a short quiet
        // period. A previously live node must still reveal that real suffix.
        let active = allowed && (streaming || motion?.bands.isEmpty == false || (freshMount && motion != nil))
        let input = Input(raw: raw, streaming: streaming, allowed: allowed, fresh: freshMount)
        let preview = snapshot(raw, now: now, active: active)
        let deadline = preview.bands.map { $0.born + $0.duration }.max()
        // Keep the same native rendering branch throughout the reply.
        // Pausing the display schedule does not replace the Text view.
        Group {
            if #available(iOS 18.0, *) {
                let marked = !active || preview.bands.isEmpty ? Text(value) : markedText(bands: preview.bands)
                TimelineView(.animation(paused: !active || preview.bands.isEmpty)) { _ in
                    marked.textRenderer(FluidGlyphRenderer(now: ProcessInfo.processInfo.systemUptime))
                }
            } else {
                TimelineView(.animation(paused: !active || preview.bands.isEmpty)) { _ in
                    Text(preview.bands.isEmpty ? value : fadedText(bands: preview.bands, now: ProcessInfo.processInfo.systemUptime))
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
                // Draw each shaped run once through a continuous alpha
                // mask. No per-glyph draw calls, no blur-pass split that
                // changes glyph edges as they switch into the settled path.
                let reversed = run.layoutDirection == .rightToLeft
                let first = reversed ? endPhase : startPhase
                let lastPhase = reversed ? startPhase : endPhase
                let phaseFrom = band.phaseFrom + (band.phaseTo - band.phaseFrom) * first
                let phaseTo = band.phaseFrom + (band.phaseTo - band.phaseFrom) * lastPhase
                let stops = fluidInkStops(progress: band.progress(at: now),
                                          phaseFrom: phaseFrom, phaseTo: phaseTo, travel: band.travel)
                if bounds.width < 0.5 || (stops.count == 2 && abs(stops[0].opacity - stops[1].opacity) < 0.0001) {
                    var ink = context
                    ink.opacity = stops[0].opacity
                    ink.draw(run)
                    continue
                }
                // Extend the endpoint alpha beyond typographic bounds so
                // italic overhangs and tall accents are not clipped.
                let padding = max(8, bounds.height)
                let maskBounds = bounds.insetBy(dx: -padding, dy: -padding)
                let gradient = Gradient(stops: stops.map {
                    Gradient.Stop(color: .white.opacity($0.opacity), location: CGFloat($0.position))
                })
                var ink = context
                ink.clipToLayer { mask in
                    let left = CGRect(x: maskBounds.minX, y: maskBounds.minY,
                                      width: padding, height: maskBounds.height)
                    let right = CGRect(x: bounds.maxX, y: maskBounds.minY,
                                       width: padding, height: maskBounds.height)
                    mask.fill(Path(left), with: .color(.white.opacity(stops[0].opacity)))
                    mask.fill(Path(right), with: .color(.white.opacity(stops[stops.count - 1].opacity)))
                    let middle = CGRect(x: bounds.minX, y: maskBounds.minY,
                                        width: bounds.width, height: maskBounds.height)
                    mask.fill(Path(middle), with: .linearGradient(gradient,
                        startPoint: CGPoint(x: bounds.minX, y: bounds.midY),
                        endPoint: CGPoint(x: bounds.maxX, y: bounds.midY)))
                }
                ink.draw(run)
            }
        }
    }
}
