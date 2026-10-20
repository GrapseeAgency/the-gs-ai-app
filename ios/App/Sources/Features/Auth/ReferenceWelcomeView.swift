import SwiftUI

struct ReferenceWelcomeView: View {
    let onExplore: () -> Void
    let onSkip: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Spacer()
                Button("Skip", action: onSkip)
                    .font(Aero.label())
                    .foregroundStyle(Aero.text)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 10)
                    .background(Capsule().fill(Color.white.opacity(0.42)))
            }
            .padding(.top, Aero.Spacing.s)
            Spacer()
            VStack(spacing: Aero.Spacing.l) {
                Label("Smarter digital assistant", systemImage: "wand.and.stars")
                    .font(Aero.label())
                    .foregroundStyle(Aero.text)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 9)
                    .background(Capsule().fill(Color.white.opacity(0.46)))
                Image("GSCompanionOrb")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 168, height: 168)
                    .accessibilityLabel("GS AI companion")
                VStack(spacing: Aero.Spacing.s) {
                    Text("Your smarter digital assistant")
                        .font(Aero.display())
                        .foregroundStyle(Aero.text)
                        .multilineTextAlignment(.center)
                    Text("A calmer way to think, create, explore, and get things done.")
                        .font(Aero.body())
                        .foregroundStyle(Aero.textMuted)
                        .multilineTextAlignment(.center)
                }
            }
            Spacer()
            VStack(spacing: Aero.Spacing.m) {
                Text("Speak, ask, solve.").font(Aero.label()).foregroundStyle(Aero.text)
                Button("Explore now", action: onExplore)
                    .font(Aero.button())
                    .foregroundStyle(Aero.onAccent)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 17)
                    .background(Capsule().fill(Aero.accent))
                    .shadow(color: Aero.accent.opacity(0.24), radius: 14, y: 7)
                    .buttonStyle(KineticPressStyle())
            }
            .padding(.bottom, Aero.Spacing.l)
        }
        .padding(.horizontal, Aero.Spacing.m)
        .background(LinearGradient(colors: [Color(red: 1, green: 0.70, blue: 0.43).opacity(0.30), Aero.background], startPoint: .topLeading, endPoint: .center).ignoresSafeArea())
    }
}
