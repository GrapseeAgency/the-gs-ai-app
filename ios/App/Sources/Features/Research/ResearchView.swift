import SwiftUI

// MARK: - Research — coming soon (honest stub, Phase 2)

/// Full-screen research surface, presented via .fullScreenCover. The fake
/// pipeline (canned synthesis, fabricated sources with relevance scores,
/// "queued" export toasts that did nothing) is gone. What remains is the
/// truth: Research isn't built yet, and chat is the real way to ask today.
struct ResearchView: View {

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            Aero.background.ignoresSafeArea()
            VStack(spacing: 0) {
                header
                    .padding(.horizontal, Aero.Spacing.m)
                    .padding(.top, Aero.Spacing.s)
                    .padding(.bottom, Aero.Spacing.m)
                ScrollView {
                    EmptyStateView(
                        icon: "doc.text.magnifyingglass",
                        title: "Research is coming soon",
                        message: "Multi-source research with citations isn't available yet. You can ask questions in a chat today — Research will appear here when it's ready."
                    )
                    .padding(.horizontal, Aero.Spacing.m)
                    .padding(.bottom, Aero.Spacing.xl)
                }
            }
        }
        .toolbar(.hidden, for: .navigationBar)
    }

    // MARK: Header (own chrome — no router)

    private var header: some View {
        HStack(spacing: Aero.Spacing.s) {
            closeButton
            Text("Research")
                .font(Aero.headline())
                .foregroundStyle(Aero.text)
            Spacer()
        }
    }

    private var closeButton: some View {
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
    }
}
