import SwiftUI

/// Full-screen voice session — kinetic aurora waveform, live transcript,
/// call controls and voice picker. Closes itself via the environment dismiss.
struct VoiceView: View {

    @Environment(\.dismiss) private var dismiss

    private static let endCallRed = Color(red: 0.9, green: 0.28, blue: 0.28)

    @State private var animate = false
    @State private var muted = false
    @State private var speakerOn = true
    @State private var voice = "GS Aurora"

    private let barCount = 24

    /// Deterministic waveform targets (24…56pt) — stable between runs.
    private var heights: [CGFloat] {
        (0..<barCount).map { index in
            CGFloat(24 + abs(sin(Double(index) * 1.37)) * 32)
        }
    }

    var body: some View {
        ZStack {
            Aero.background.ignoresSafeArea()
            VStack(spacing: Aero.Spacing.xl) {
                topBar
                Spacer()
                status
                waveform
                Spacer()
                transcript
                controls
                voicePicker
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.l)
        }
        .toolbar(.hidden, for: .navigationBar)
    }

    // MARK: Top bar

    private var topBar: some View {
        HStack(spacing: Aero.Spacing.s) {
            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Aero.text)
                    .frame(width: 36, height: 36)
                    .background(Circle().fill(Aero.raised))
                    .overlay(Circle().stroke(Aero.outline, lineWidth: 1))
            }
            .buttonStyle(KineticPressStyle())
            Spacer()
            Text("Voice mode")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
        }
    }

    // MARK: Status

    private var status: some View {
        VStack(spacing: Aero.Spacing.xs) {
            Text("Listening…")
                .font(Aero.display())
                .foregroundStyle(Aero.text)
            Text("\(voice) · English (UK)")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
        }
    }

    // MARK: Waveform (aurora — the one AI-active moment)

    private var waveform: some View {
        HStack(spacing: 4) {
            ForEach(0..<barCount, id: \.self) { index in
                Capsule()
                    .fill(LinearGradient(colors: Aero.aurora, startPoint: .top, endPoint: .bottom))
                    .frame(width: 4, height: animate ? heights[index] : 8)
                    .animation(
                        .easeInOut(duration: 0.5)
                            .repeatForever(autoreverses: true)
                            .delay(Double(index) * 0.04),
                        value: animate
                    )
            }
        }
        .frame(height: 56)
        .onAppear { animate = true }
    }

    // MARK: Transcript

    private var transcript: some View {
        AeroCard {
            VStack(spacing: Aero.Spacing.s) {
                Text("What's on my plate this morning?")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                Text("Three focus blocks: the design review at ten, the release-note draft, and a pairing session at noon.")
                    .font(Aero.body())
                    .foregroundStyle(Aero.text)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    // MARK: Controls

    private var controls: some View {
        HStack(spacing: 24) {
            controlButton(
                muted ? "mic.slash" : "mic",
                dimmed: muted
            ) {
                muted.toggle()
            }
            controlButton(
                "phone.down.fill",
                tint: Color.white,
                background: Self.endCallRed
            ) {
                dismiss()
            }
            controlButton(
                "speaker.wave.2.fill",
                dimmed: !speakerOn
            ) {
                speakerOn.toggle()
            }
        }
    }

    private func controlButton(
        _ icon: String,
        tint: Color = Aero.text,
        background: Color = Aero.raised,
        dimmed: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 20, weight: .medium))
                .foregroundStyle(tint)
                .opacity(dimmed ? 0.55 : 1)
                .frame(width: 64, height: 64)
                .background(Circle().fill(background))
                .overlay(Circle().stroke(Aero.outline, lineWidth: 1))
        }
        .buttonStyle(KineticPressStyle())
    }

    // MARK: Voice picker

    private var voicePicker: some View {
        HStack(spacing: Aero.Spacing.s) {
            ForEach(["GS Aurora", "Ember", "Slate"], id: \.self) { option in
                AeroChip(text: option, selected: voice == option) {
                    voice = option
                }
            }
        }
        .frame(maxWidth: .infinity)
    }
}
