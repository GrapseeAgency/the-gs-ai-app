import SwiftUI

/**
 * PHASE 8.1 — clarify quick-choices (docs/search-event-protocol.md). Shown on
 * a `clarify` event (and on reloaded messages carrying `clarifyOptions`):
 * the question plus the protocol's option chips verbatim (World, Technology,
 * Business, Science, Sports, Entertainment, Health, Bangladesh — whatever the
 * wire sent). Tapping an option sends the label as a NORMAL user message
 * through the existing send path (`ChatViewModel.sendClarifyChoice`) —
 * nothing is auto-filled into the composer, nothing is invented.
 */
struct ClarifyChipsView: View {

    let prompt: ClarifyPrompt
    let onPick: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(prompt.question)
                .font(Aero.bodyMedium())
                .foregroundStyle(Aero.text)
                .fixedSize(horizontal: false, vertical: true)
            if !prompt.options.isEmpty {
                LazyVGrid(
                    alignment: .leading,
                    columns: [GridItem(.adaptive(minimum: 108), spacing: Aero.Spacing.s)],
                    spacing: Aero.Spacing.s
                ) {
                    ForEach(prompt.options) { option in
                        Button {
                            onPick(option.label)
                        } label: {
                            Text(option.label)
                                .font(Aero.label())
                                .foregroundStyle(Aero.text)
                                .lineLimit(1)
                                .padding(.horizontal, Aero.Spacing.control)
                                .padding(.vertical, 8)
                                .frame(maxWidth: .infinity)
                                .background(Capsule().fill(Aero.raisedSurface))
                                .overlay(Capsule().stroke(Aero.outline, lineWidth: 1))
                                .contentShape(Capsule())
                        }
                        .buttonStyle(KineticPressStyle())
                        .accessibilityLabel("Ask about \(option.label)")
                    }
                }
            }
        }
    }
}
