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
    // Committing the exact preview is bookkeeping, not a second rendering
    // event. A reference ledger avoids invalidating the native Text for every
    // provider publication. Only its terminal cleanup changes view state.
    private final class MotionLedger {
        var motion: FluidTextMotion?
    }
    @State private var ledger = MotionLedger()
    @State private var settledRevision: UInt64 = 0
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
        let _ = settledRevision
        let motion = ledger.motion
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
                let segments = prepareLegacySegments(bands: active ? preview.bands : [])
                TimelineView(.animation(paused: !active || preview.bands.isEmpty)) { _ in
                    Text(preview.bands.isEmpty ? value : fadedText(segments: segments, now: ProcessInfo.processInfo.systemUptime))
                }
            }
        }
        .onChange(of: input, initial: true) { _, _ in
            ledger.motion = active ? preview : nil
        }
        .task(id: deadline) {
            guard let deadline else { return }
            let remaining = max(0, deadline - ProcessInfo.processInfo.systemUptime)
            try? await Task.sleep(nanoseconds: UInt64(remaining * 1_000_000_000) + 1_000_000)
            guard !Task.isCancelled else { return }
            // Never discard a later append or restart a completed range.
            if let latest = ledger.motion?.bands.map({ $0.born + $0.duration }).max(), latest <= deadline {
                ledger.motion?.settle()
                settledRevision &+= 1
            }
        }
    }

    private func snapshot(_ raw: String, now: TimeInterval, active: Bool) -> FluidTextMotion {
        var next = ledger.motion ?? FluidTextMotion(initial: active && streaming && freshMount ? "" : raw)
        next.update(raw, now: now, enabled: active, sourceAppend: freshMount)
        return next
    }

    // Move the attributed-string index forward from its last position.
    // Repeated offsetBy from startIndex is especially costly for long
    // paragraphs containing extended grapheme clusters.
    @available(iOS 18.0, *)
    private func markedText(bands: [FluidTextBand]) -> Text {
        var result = Text("")
        var cursor = 0
        var index = value.startIndex
        let count = value.characters.count
        for band in bands where band.start >= cursor && band.end <= count {
            if cursor < band.start {
                let start = value.characters.index(index, offsetBy: band.start - cursor)
                result = result + Text(AttributedString(value[index..<start]))
                index = start
            }
            let end = value.characters.index(index, offsetBy: band.end - band.start)
            let fragment = AttributedString(value[index..<end])
            result = result + Text(fragment).customAttribute(FluidRevealAttribute(band: band))
            index = end
            cursor = band.end
        }
        if index < value.endIndex {
            result = result + Text(AttributedString(value[index..<value.endIndex]))
        }
        return result
    }

    private struct LegacySegment {
        let value: AttributedString
        let band: FluidTextBand?
        let position: Double
        let color: Color?
    }

    // Prepare immutable styled runs once per published text snapshot. The
    // legacy path changes only ink opacity at frame cadence; it never walks
    // the complete source's character indices or splits the old text again.
    private func prepareLegacySegments(bands: [FluidTextBand]) -> [LegacySegment] {
        guard !bands.isEmpty else {
            return [LegacySegment(value: value, band: nil, position: 0, color: nil)]
        }
        var segments: [LegacySegment] = []
        var cursor = 0
        var index = value.startIndex
        let count = value.characters.count
        for band in bands where band.start >= cursor && band.end <= count {
            if cursor < band.start {
                let start = value.characters.index(index, offsetBy: band.start - cursor)
                segments.append(LegacySegment(value: AttributedString(value[index..<start]), band: nil, position: 0, color: nil))
                index = start
            }
            let length = max(1, band.end - band.start)
            let step = max(1, (length + 7) / 8)
            var offset = band.start
            while offset < band.end {
                let end = min(band.end, offset + step)
                let next = value.characters.index(index, offsetBy: end - offset)
                let position = Double(offset + end - 2 * band.start) / (2 * Double(length))
                for run in value[index..<next].runs {
                    segments.append(LegacySegment(value: AttributedString(value[run.range]),
                        band: band, position: position, color: run.foregroundColor ?? ink))
                }
                index = next
                offset = end
            }
            cursor = band.end
        }
        if index < value.endIndex {
            segments.append(LegacySegment(value: AttributedString(value[index..<value.endIndex]), band: nil, position: 0, color: nil))
        }
        return segments
    }

    private func fadedText(segments: [LegacySegment], now: TimeInterval) -> AttributedString {
        var result = AttributedString()
        for segment in segments {
            guard let band = segment.band, let color = segment.color else {
                result.append(segment.value)
                continue
            }
            let alpha = band.opacity(at: now, position: segment.position)
            if alpha >= 1 {
                result.append(segment.value)
            } else {
                var part = segment.value
                part.foregroundColor = color.opacity(alpha)
                result.append(part)
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
    // Construct the contrast curve once. Each run only moves/scales its
    // native coordinate space. Extended ends supply the clamped ink values.
    private static let inkGradient = Gradient(stops: (0...32).map { index in
        let p = Double(index) / 32
        return Gradient.Stop(color: .white.opacity(fluidInkOpacity(p)), location: CGFloat(p))
    })

    func draw(layout: Text.Layout, in context: inout GraphicsContext) {
        // The phase is continuous across native runs and wrapped lines,
        // including inline bold/link changes. It does not restart at each run.
        var totals: [FluidTextBand: Int] = [:]
        for line in layout {
            for run in line {
                if let attribute = run[FluidRevealAttribute.self], attribute.band.progress(at: now) < 1 {
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
                if band.progress(at: now) >= 1 {
                    context.draw(run)
                    continue
                }
                let offset = consumed[band, default: 0]
                let count = max(1, totals[band, default: run.count])
                consumed[band] = offset + run.count
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
                let projection = fluidInkProjection(progress: band.progress(at: now),
                    phaseFrom: phaseFrom, phaseTo: phaseTo, travel: band.travel)
                if bounds.width < 0.5 || projection.solid != nil {
                    var ink = context
                    ink.opacity = projection.solid ?? band.opacity(at: now, position: (startPhase + endPhase) * 0.5)
                    ink.draw(run)
                    continue
                }
                // The native gradient uses the cached stop array. GraphicsContext
                // has no extendsStart/extendsEnd options; paint only any
                // overhang outside the axis with its exact endpoint opacity.
                let padding = max(8, bounds.height)
                let maskBounds = bounds.insetBy(dx: -padding, dy: -padding)
                let x0 = bounds.minX + bounds.width * CGFloat(projection.origin)
                let x1 = x0 + bounds.width * CGFloat(projection.scale)
                var ink = context
                ink.clipToLayer { mask in
                    let left = min(x0, x1)
                    let right = max(x0, x1)
                    let firstOpacity = fluidInkOpacity(x0 < x1 ? 0 : 1)
                    let lastOpacity = fluidInkOpacity(x0 < x1 ? 1 : 0)
                    if maskBounds.minX < left {
                        let edge = min(left, maskBounds.maxX)
                        mask.fill(Path(CGRect(x: maskBounds.minX, y: maskBounds.minY,
                            width: edge - maskBounds.minX, height: maskBounds.height)),
                            with: .color(.white.opacity(firstOpacity)))
                    }
                    let innerLeft = max(maskBounds.minX, left)
                    let innerRight = min(maskBounds.maxX, right)
                    if innerRight > innerLeft {
                        mask.fill(Path(CGRect(x: innerLeft, y: maskBounds.minY,
                            width: innerRight - innerLeft, height: maskBounds.height)),
                            with: .linearGradient(Self.inkGradient,
                                startPoint: CGPoint(x: x0, y: bounds.midY),
                                endPoint: CGPoint(x: x1, y: bounds.midY)))
                    }
                    if maskBounds.maxX > right {
                        let edge = max(right, maskBounds.minX)
                        mask.fill(Path(CGRect(x: edge, y: maskBounds.minY,
                            width: maskBounds.maxX - edge, height: maskBounds.height)),
                            with: .color(.white.opacity(lastOpacity)))
                    }
                }
                ink.draw(run)
            }
        }
    }
}
