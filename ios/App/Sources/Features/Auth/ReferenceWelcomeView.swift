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
                ZStack {
                    Circle().fill(RadialGradient(colors: [.white.opacity(0.98), Color(red: 1, green: 0.75, blue: 0.55).opacity(0.68), Color(red: 1, green: 0.48, blue: 0.25).opacity(0.30)], center: .center, startRadius: 12, endRadius: 132)).frame(width: 168, height: 168)
                    Circle().fill(AngularGradient(colors: [Color(red: 1, green: 0.52, blue: 0.30), Color(red: 1, green: 0.82, blue: 0.64), Color(red: 1, green: 0.52, blue: 0.30)], center: .center)).frame(width: 112, height: 112)
                    Image(systemName: "wand.and.stars").font(.system(size: 36, weight: .semibold)).foregroundStyle(.white)
                }
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
