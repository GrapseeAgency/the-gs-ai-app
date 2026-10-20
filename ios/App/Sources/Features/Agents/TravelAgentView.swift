import SwiftUI

struct TravelAgentView: View {
    let onBack: () -> Void
    let onSendToChat: (String) -> Void

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: Aero.Spacing.l) {
                header
                orb
                VStack(spacing: Aero.Spacing.s) {
                    Text("Welcome to Travel Agent")
                        .font(Aero.headline())
                        .foregroundStyle(Aero.text)
                        .multilineTextAlignment(.center)
                    Text("Your own smart companion is here to make every journey feel easier.")
                        .font(Aero.body())
                        .foregroundStyle(Aero.textMuted)
                        .multilineTextAlignment(.center)
                }
                chips
                AeroCard(action: { onSendToChat("Help me plan a thoughtful trip") }) {
                    HStack(spacing: Aero.Spacing.s) {
                        Image(systemName: "globe")
                            .foregroundStyle(Aero.accent)
                            .frame(width: 40, height: 40)
                            .background(Circle().fill(Aero.accentSoft))
                        VStack(alignment: .leading, spacing: 3) {
                            Text("Plan your next escape").font(Aero.title()).foregroundStyle(Aero.text)
                            Text("Destinations, stays, routes, and beautiful details.").font(Aero.caption()).foregroundStyle(Aero.textMuted)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").foregroundStyle(Aero.textMuted)
                    }
                }
                AeroCard {
                    VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                        Label("Travel Agent", systemImage: "globe")
                            .font(Aero.title())
                            .foregroundStyle(Aero.text)
                        Text("Tell me where you want to go, and I’ll shape the whole journey around you.")
                            .font(Aero.body())
                            .foregroundStyle(Aero.text)
                        HStack(spacing: Aero.Spacing.s) {
                            MediaAction(title: "Gif", icon: "photo", action: onSendToChat)
                            MediaAction(title: "Image", icon: "photo.on.rectangle", action: onSendToChat)
                            MediaAction(title: "Video", icon: "play.rectangle", action: onSendToChat)
                        }
                    }
                }
                Spacer(minLength: 90)
                Button { onSendToChat("Help me plan a thoughtful trip") } label: {
                    HStack {
                        Text("Ask me anything...").font(Aero.body()).foregroundStyle(Aero.textMuted)
                        Spacer()
                        Image(systemName: "arrow.up").foregroundStyle(Aero.onAccent).frame(width: 38, height: 38).background(Circle().fill(Aero.accent))
                    }
                    .padding(.horizontal, Aero.Spacing.m)
                    .padding(.vertical, 9)
                    .background(Capsule().fill(Aero.surface))
                    .overlay(Capsule().stroke(Aero.outline, lineWidth: 1))
                    .aeroCardShadow()
                }
                .buttonStyle(KineticPressStyle())
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.l)
        }
        .background(LinearGradient(colors: [Color(red: 1, green: 0.70, blue: 0.43).opacity(0.24), Aero.background], startPoint: .topLeading, endPoint: .center).ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
    }

    private var header: some View {
        HStack(spacing: Aero.Spacing.s) {
            Button(action: onBack) {
                Image(systemName: "chevron.left").foregroundStyle(Aero.text).frame(width: 42, height: 42).background(Circle().fill(Aero.surface))
            }.buttonStyle(KineticPressStyle())
            Text("Travel Agent").font(Aero.title()).foregroundStyle(Aero.text)
            Spacer()
            Image(systemName: "globe").foregroundStyle(Aero.accent).frame(width: 42, height: 42).background(Circle().fill(Aero.accentSoft))
        }
    }

    private var orb: some View {
        Image("GSTravelAgentOrb")
            .resizable()
            .scaledToFit()
            .frame(width: 164, height: 164)
            .accessibilityLabel("Travel Agent companion orb")
    }

    private var chips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Aero.Spacing.s) {
                AgentChip(title: "Yesterday", icon: "clock.arrow.circlepath", action: onSendToChat)
                AgentChip(title: "Creative", icon: "wand.and.stars", action: onSendToChat)
                AgentChip(title: "Planning", icon: "location.north", action: onSendToChat)
            }
        }
    }
}

private struct MediaAction: View {
    let title: String
    let icon: String
    let action: (String) -> Void
    var body: some View {
        Button { action("Generate a \(title) for my trip") } label: {
            Label(title, systemImage: icon)
                .font(Aero.label())
                .foregroundStyle(Aero.text)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Capsule().fill(Aero.accentSoft))
        }
        .buttonStyle(KineticPressStyle())
    }
}

private struct AgentChip: View {
    let title: String
    let icon: String
    let action: (String) -> Void
    var body: some View {
        Button { action("Open my \(title) travel ideas") } label: {
            Label(title, systemImage: icon).font(Aero.label()).foregroundStyle(Aero.text).padding(.horizontal, 14).padding(.vertical, 10).background(Capsule().fill(Aero.surface))
        }.buttonStyle(KineticPressStyle())
    }
}
