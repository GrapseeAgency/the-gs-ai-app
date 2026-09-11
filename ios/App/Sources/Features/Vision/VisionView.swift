import SwiftUI

// MARK: - Vision — coming soon (honest stub, Phase 2)

/// Full-screen image-analysis surface. The fake estate (the phantom
/// IMG_2041.jpg, canned detections with confidence bars, OCR lines, chart
/// readings and scripted follow-up answers) is gone. What remains is the
/// truth: image analysis isn't built yet.
struct VisionView: View {

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
                        icon: "photo",
                        title: "Vision is coming soon",
                        message: "Image analysis isn't available yet. When it arrives you'll be able to analyse photos, screenshots and charts here."
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
            Text("Vision")
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
