import SwiftUI

// THE TWO CONTROLS THAT WERE STILL STOCK.
//
// Everything around them is glass, so the two pieces of chrome a reader touches
// most often were also the two that gave the illusion away: a system `Toggle`
// with a flat track and a white dot, and a system `Slider` with a hairline rail,
// a circular thumb and tick marks. Both are correct iOS and both are the wrong
// material for this app — they read as a form from 2016 sitting inside a card
// made of light.
//
// The rules the rest of the app already follows, applied here:
//
//  - a control is a piece of GLASS, not a rectangle of colour: it has a lit top
//    edge, an interior fall, and a rim that is brighter than its fill;
//  - STATE IS LIGHT, not position. Off is glass seen from the shadowed side;
//    on is the same glass lit from inside by the accent, and the light spills
//    past the shape as a halo. The knob moves, but the glow is what tells you
//    the answer at a glance;
//  - the accent is the reader's own, so nothing here hard-codes a hue.
//
// Android draws the same two objects from `GsGlassControls.kt`; the geometry
// (48×28 track, 22pt knob, ten rungs at 13→42pt) is deliberately identical so
// the two builds cannot drift.

// MARK: - The switch

/// The app's on/off control, in glass.
///
/// Off: a sunken capsule — the page's own ink pressed down, a warm hairline, and
/// a frosted knob resting at the left. On: the capsule fills with the accent
/// (lit at the top, deeper at the bottom), gains a white rim, throws a coloured
/// halo onto the card behind it, and the knob travels right with a spring and
/// carries a lit core.
struct GsGlassSwitch: View {
    @Binding var isOn: Bool
    var enabled: Bool = true

    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private let trackWidth: CGFloat = 48
    private let trackHeight: CGFloat = 28
    private let knobSize: CGFloat = 22
    private let inset: CGFloat = 3

    private var travel: CGFloat {
        isOn ? trackWidth - knobSize - inset : inset
    }

    /// Off is glass in shadow: the page's ink at low alpha, deeper in light
    /// where the card is bright. A near-white fill (what this replaced) made the
    /// track a white pill that vanished into the cream card.
    private var offInk: Color {
        Aero.text.opacity(scheme == .dark ? 0.15 : 0.085)
    }

    private var offFill: LinearGradient {
        LinearGradient(
            colors: [offInk.opacity(0.72), offInk],
            startPoint: .top, endPoint: .bottom)
    }

    private var onFill: LinearGradient {
        LinearGradient(
            colors: [Aero.accentStrong, Aero.accent],
            startPoint: .top, endPoint: .bottom)
    }

    private var rimColor: Color {
        isOn
            ? Color.white.opacity(0.44)
            // The rim does the work when the fill cannot: a hairline of the
            // page's own warm border, doubled in light so the well still has an
            // edge against cream.
            : Aero.outline.opacity(scheme == .dark ? 0.60 : 0.95)
    }

    var body: some View {
        ZStack(alignment: .leading) {
            Capsule(style: .continuous)
                .fill(isOn ? AnyShapeStyle(onFill) : AnyShapeStyle(offFill))
                .overlay(
                    Capsule(style: .continuous).strokeBorder(rimColor, lineWidth: 1))
                // The specular band along the top edge — the same light the cards get.
                .overlay(
                    Capsule(style: .continuous)
                        .fill(
                            LinearGradient(
                                stops: [
                                    .init(color: .white.opacity(isOn ? 0.30 : 0.26), location: 0),
                                    .init(color: .clear, location: 0.58)
                                ],
                                startPoint: .top, endPoint: .bottom)))

            // The knob: a frosted sphere with its own rim and a lit core.
            ZStack {
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [.white, .white.opacity(0.86)],
                            startPoint: .topLeading, endPoint: .bottomTrailing))
                    .overlay(
                        Circle().strokeBorder(
                            isOn
                                ? Color.white.opacity(0.90)
                                // White on white reads as nothing, so a sunken
                                // track gives the knob the page's warm rim.
                                : Aero.outline.opacity(scheme == .dark ? 0.45 : 0.70),
                            lineWidth: 0.8))
                    .shadow(color: .black.opacity(0.22), radius: 2, y: 1)
                Circle()
                    .fill(Aero.accent.opacity(isOn ? 0.9 : 0))
                    .frame(width: 7, height: 7)
            }
            .frame(width: knobSize, height: knobSize)
            .offset(x: travel)
        }
        .frame(width: trackWidth, height: trackHeight)
        // The halo. A coloured shadow is the cheapest honest glow: it is light
        // leaving the control, not a second rectangle.
        .shadow(color: isOn ? Aero.accent.opacity(0.45) : .clear, radius: 9, y: 3)
        .contentShape(Capsule())
        .onTapGesture {
            guard enabled else { return }
            withAnimation(reduceMotion ? nil : .spring(response: 0.28, dampingFraction: 0.62)) {
                isOn.toggle()
            }
            GSHaptics.select()
        }
        .opacity(enabled ? 1 : 0.45)
        .animation(reduceMotion ? nil : .spring(response: 0.28, dampingFraction: 0.62), value: isOn)
        .accessibilityRepresentation {
            // `.switch` explicitly: this view is the representation OF a toggle,
            // so it must never inherit a custom toggle style and recurse.
            Toggle("", isOn: $isOn).labelsHidden().toggleStyle(.switch).disabled(!enabled)
        }
    }
}

/// Kept for call sites that want the standard `Toggle` API but this app's
/// material. Label on the left, glass switch on the right.
struct GsGlassToggleStyle: ToggleStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 12) {
            configuration.label
            Spacer(minLength: 8)
            // The switch drives the style's own state through an explicit
            // binding: `ToggleStyleConfiguration.isOn` is get/set but not a
            // projected value, and toggling it directly would fire twice — once
            // from the switch's tap, once from a row-level gesture.
            GsGlassSwitch(isOn: Binding(
                get: { configuration.isOn },
                set: { newValue in
                    guard newValue != configuration.isOn else { return }
                    withAnimation(.spring(response: 0.28, dampingFraction: 0.62)) {
                        configuration.isOn = newValue
                    }
                }))
        }
    }
}

// MARK: - The text-size ladder

/// Text size as a ladder of light rather than a rail with a dot on it.
///
/// Ten glass rungs grow from left to right — the shape IS the sentence "small to
/// big", so no tick marks and no labels are needed to explain the axis. The
/// rungs the reader has already climbed are washed with the accent's soft step;
/// the rung they are standing on is lit with the accent and throws a halo; the
/// ones ahead stay clear glass. The current value is a glass chip in the header,
/// so the number moves with the light instead of sitting in a corner.
struct GsTextSizeLadder: View {
    @Binding var value: Double
    var range: ClosedRange<Double> = 0.85...1.30
    var steps: Int = 8
    var title: String = "Text size"
    var onCommit: (() -> Void)? = nil

    @Environment(\.colorScheme) private var scheme

    private var rungs: Int { steps + 2 }

    private func valueAt(_ index: Int) -> Double {
        let t = Double(index) / Double(rungs - 1)
        return range.lowerBound + (range.upperBound - range.lowerBound) * t
    }

    private var activeIndex: Int {
        let span = range.upperBound - range.lowerBound
        let progress = min(max((value - range.lowerBound) / span, 0), 1)
        return min(rungs - 1, max(0, Int((progress * Double(rungs - 1)).rounded())))
    }

    private func height(_ index: Int) -> CGFloat {
        13 + (42 - 13) * (CGFloat(index) / CGFloat(rungs - 1))
    }

    private func pick(at x: CGFloat, width: CGFloat) {
        let inner = max(width - 24, 1)
        let clamped = min(max(x - 12, 0), inner)
        let index = min(rungs - 1, max(0, Int((clamped / inner) * CGFloat(rungs))))
        value = valueAt(index)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(title)
                    .font(.custom(Aero.productName(.medium), size: 14, relativeTo: .subheadline))
                    .foregroundStyle(Aero.text)
                Spacer()
                // The readout is a glass chip, not a coloured label: it belongs
                // to the same material as the control it reports on.
                Text("\(Int((value * 100).rounded()))%")
                    .font(.custom(Aero.productName(.semibold), size: 12, relativeTo: .caption))
                    .foregroundStyle(Aero.accentStrong)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 3)
                    .background(Capsule().fill(Aero.accent.opacity(0.12)))
                    .overlay(Capsule().strokeBorder(Aero.accent.opacity(0.34), lineWidth: 1))
            }
            .padding(.horizontal, 14)
            .padding(.top, 14)

            GeometryReader { geo in
                HStack(alignment: .bottom, spacing: 6) {
                    ForEach(Array(0..<rungs), id: \.self) { i in
                        rung(i)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .frame(width: geo.size.width, height: 66, alignment: .bottomLeading)
                .gsGlass(.sunken, corner: 18)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { pick(at: $0.location.x, width: geo.size.width) }
                        .onEnded { _ in onCommit?() }
                )
            }
            .frame(height: 66)
            .padding(.horizontal, 14)
            .padding(.top, 12)

            // The axis, spelled with type rather than with ticks.
            HStack {
                Text("Aa")
                    .font(.custom(Aero.productName(.medium), size: 10, relativeTo: .caption2))
                    .foregroundStyle(Aero.textTertiary)
                Spacer()
                Text("Aa")
                    .font(.custom(Aero.productName(.semibold), size: 17, relativeTo: .body))
                    .foregroundStyle(Aero.textTertiary)
            }
            .padding(.horizontal, 18)
            .padding(.top, 8)
            .padding(.bottom, 14)
        }
    }

    @ViewBuilder
    private func rung(_ index: Int) -> some View {
        let isActive = index == activeIndex
        let isBehind = index < activeIndex
        // Warm, not grey: the page is cream, and a neutral grey tile beside it
        // looks like a hole in the glass.
        let ahead = Aero.outline.opacity(scheme == .dark ? 0.30 : 0.45)

        RoundedRectangle(cornerRadius: 9, style: .continuous)
            .fill(
                isActive
                    ? AnyShapeStyle(
                        LinearGradient(
                            colors: [Aero.accentStrong, Aero.accent],
                            startPoint: .top, endPoint: .bottom))
                    : AnyShapeStyle(
                        LinearGradient(
                            colors: [
                                (isBehind ? Aero.accentSoft : ahead),
                                (isBehind ? Aero.accentSoft : ahead).opacity(0.72)
                            ],
                            startPoint: .top, endPoint: .bottom)))
            .overlay(
                RoundedRectangle(cornerRadius: 9, style: .continuous)
                    .strokeBorder(
                        isActive ? Color.white.opacity(0.36) : Aero.outline.opacity(scheme == .dark ? 0.45 : 0.75),
                        lineWidth: isActive ? 1 : 0.5))
            .frame(height: height(index))
            .shadow(
                color: isActive ? Aero.accent.opacity(0.45) : .clear,
                radius: isActive ? 8 : 0, y: isActive ? 3 : 0)
            .animation(.spring(response: 0.26, dampingFraction: 0.8), value: activeIndex)
    }
}
