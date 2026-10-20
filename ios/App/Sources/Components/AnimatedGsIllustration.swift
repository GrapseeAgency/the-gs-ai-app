import SwiftUI

/// A restrained idle breath and drift for the generated companion artwork.
/// The motion is decorative, slow, interruptible, and respects Reduce Motion.
struct AnimatedGsIllustration: View {
    let imageName: String
    let size: CGFloat
    var cornerRadius: CGFloat = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var isAlive = false

    var body: some View {
        Image(imageName)
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
            .scaleEffect(reduceMotion ? 1 : (isAlive ? 1.035 : 1))
            .rotationEffect(.degrees(reduceMotion ? 0 : (isAlive ? 2.2 : 0)))
            .animation(
                reduceMotion ? nil : .easeInOut(duration: 2.4).repeatForever(autoreverses: true),
                value: isAlive
            )
            .onAppear {
                guard !reduceMotion else { return }
                isAlive = true
            }
            .accessibilityHidden(true)
    }
}
