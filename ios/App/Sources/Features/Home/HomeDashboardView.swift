import SwiftUI

/// Warm consumer home for the real iOS app. Presentation is native SwiftUI;
/// actions hand off to the existing Router and never fabricate backend data.
struct HomeDashboardView: View {
    let onNewChat: () -> Void
    let onOpenDrawer: () -> Void
    let onOpenChats: () -> Void
    let onOpenAgent: () -> Void

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                topBar
                heroVisual
                hero
                quickActions
                featureGrid
                reflectionCard
                focusCard
                Button(action: onNewChat) {
                    Label("Generate", systemImage: "wand.and.stars")
                        .font(Aero.button())
                        .foregroundStyle(Aero.onAccent)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(Capsule().fill(Aero.accent))
                        .shadow(color: Aero.accent.opacity(0.22), radius: 14, y: 7)
                }
                .buttonStyle(KineticPressStyle())
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.l)
        }
        .background(
            LinearGradient(
                colors: [Color(red: 1, green: 0.70, blue: 0.43).opacity(0.30), Aero.background],
                startPoint: .topLeading,
                endPoint: .center
            )
            .ignoresSafeArea()
        )
        .toolbar(.hidden, for: .navigationBar)
    }

    private var topBar: some View {
        HStack(spacing: Aero.Spacing.s) {
            Button(action: onOpenDrawer) {
                Image(systemName: "line.3.horizontal")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Aero.text)
                    .frame(width: 42, height: 42)
                    .background(Circle().fill(Aero.surface))
            }
            .buttonStyle(KineticPressStyle())
            VStack(alignment: .leading, spacing: 2) {
                Text("GS AI").font(Aero.title()).foregroundStyle(Aero.text)
                Text("Your smarter companion").font(Aero.caption()).foregroundStyle(Aero.textMuted)
            }
            Spacer()
            Button(action: onOpenChats) {
                Image(systemName: "clock.arrow.circlepath")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Aero.accent)
                    .frame(width: 42, height: 42)
                    .background(Circle().fill(Aero.accentSoft))
            }
            .buttonStyle(KineticPressStyle())
        }
    }

    private var hero: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            Text("Intelligence reimagined.")
                .font(Aero.display())
                .foregroundStyle(Aero.text)
            Text("Discover a softer way to think.")
                .font(Aero.headline())
                .foregroundStyle(Aero.accentDeep)
            Text("Ask, create, explore, and get things done with your personal AI companion.")
                .font(Aero.body())
                .foregroundStyle(Aero.textMuted)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.top, Aero.Spacing.xl)
    }

    private var heroVisual: some View {
        Button(action: onNewChat) {
            ZStack {
                RadialGradient(colors: [Color(red: 1, green: 0.82, blue: 0.65), Color(red: 1, green: 0.48, blue: 0.25)], center: .center, startRadius: 8, endRadius: 240)
                HStack {
                    Label("GS-5 nano", systemImage: "brain")
                        .font(Aero.label())
                        .foregroundStyle(Aero.text)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Capsule().fill(Color.white.opacity(0.40)))
                    Spacer()
                }
                .frame(maxHeight: .infinity, alignment: .top)
                .padding(16)
                VStack(spacing: 10) {
                    ZStack {
                        Circle().fill(Color.white.opacity(0.88)).frame(width: 112, height: 112)
                        Circle().fill(AngularGradient(colors: [Color(red: 1, green: 0.55, blue: 0.30), Color(red: 1, green: 0.84, blue: 0.68), Color(red: 1, green: 0.55, blue: 0.30)], center: .center)).frame(width: 74, height: 74)
                        Image(systemName: "wand.and.stars").font(.system(size: 26, weight: .semibold)).foregroundStyle(.white)
                    }
                    Text("A calmer way to create").font(Aero.label()).foregroundStyle(Aero.text)
                }
            }
            .frame(height: 204)
            .clipShape(RoundedRectangle(cornerRadius: 30))
            .overlay(RoundedRectangle(cornerRadius: 30).stroke(Color.white.opacity(0.35), lineWidth: 1))
            .aeroCardShadow()
        }
        .buttonStyle(KineticPressStyle())
    }

    private var quickActions: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Aero.Spacing.s) {
                HomeChip(title: "Start new chat", icon: "square.and.pencil", selected: true, action: onNewChat)
                HomeChip(title: "Mind guide", icon: "brain", action: onNewChat)
                HomeChip(title: "Research", icon: "telescope", action: onNewChat)
            }
        }
    }

    private var featureGrid: some View {
        HStack(spacing: Aero.Spacing.s) {
            HomeFeatureCard(title: "Create\nwith AI", subtitle: "Turn ideas into something real", icon: "wand.and.stars", colors: [Color(red: 1, green: 0.83, blue: 0.69), Aero.surface], action: onNewChat)
            HomeFeatureCard(title: "Travel\nagent", subtitle: "Make every journey easier", icon: "globe", colors: [Color(red: 1, green: 0.78, blue: 0.69), Aero.background], action: onOpenAgent)
        }
    }

    private var reflectionCard: some View {
        Button(action: onNewChat) {
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                Label("Today's reflection", systemImage: "lightbulb")
                    .font(Aero.title())
                    .foregroundStyle(Aero.text)
                Text("What are you holding onto that you could gently release?")
                    .font(Aero.body())
                    .foregroundStyle(Aero.text)
                    .fixedSize(horizontal: false, vertical: true)
                Text("Open mind guide  →")
                    .font(Aero.label())
                    .foregroundStyle(Aero.accentDeep)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(Aero.Spacing.m)
            .background(LinearGradient(colors: [Color(red: 1, green: 0.89, blue: 0.82), Color(red: 1, green: 0.78, blue: 0.72)], startPoint: .leading, endPoint: .trailing))
            .clipShape(RoundedRectangle(cornerRadius: Aero.Radius.card))
            .overlay(RoundedRectangle(cornerRadius: Aero.Radius.card).stroke(Aero.outline, lineWidth: 1))
            .aeroCardShadow()
        }
        .buttonStyle(KineticPressStyle())
    }

    private var focusCard: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            HStack {
                Label("Suggestions", systemImage: "lightbulb")
                    .font(Aero.title())
                    .foregroundStyle(Aero.text)
                Spacer()
                Button("See all", action: onOpenChats)
                    .font(Aero.label())
                    .foregroundStyle(Aero.textMuted)
            }
            Text("Try a thoughtful question first")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
            ProgressView(value: 0.64)
                .tint(Aero.accent)
                .scaleEffect(y: 1.4)
        }
        .padding(Aero.Spacing.m)
        .background(RoundedRectangle(cornerRadius: Aero.Radius.card).fill(Aero.surface))
        .overlay(RoundedRectangle(cornerRadius: Aero.Radius.card).stroke(Aero.outline, lineWidth: 1))
        .aeroCardShadow()
    }
}

private struct HomeChip: View {
    let title: String
    let icon: String
    var selected = false
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Label(title, systemImage: icon)
                .font(Aero.label())
                .foregroundStyle(selected ? Aero.onAccent : Aero.text)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(Capsule().fill(selected ? Aero.accent : Aero.surface))
        }
        .buttonStyle(KineticPressStyle())
    }
}

private struct HomeFeatureCard: View {
    let title: String
    let subtitle: String
    let icon: String
    let colors: [Color]
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Aero.accentDeep)
                    .frame(width: 40, height: 40)
                    .background(Circle().fill(Color.white.opacity(0.70)))
                Spacer(minLength: 12)
                Text(title).font(Aero.title()).foregroundStyle(Aero.text).multilineTextAlignment(.leading)
                Text(subtitle).font(Aero.caption()).foregroundStyle(Aero.textMuted).multilineTextAlignment(.leading)
            }
            .frame(maxWidth: .infinity, minHeight: 164, alignment: .leading)
            .padding(Aero.Spacing.m)
            .background(LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing))
            .clipShape(RoundedRectangle(cornerRadius: Aero.Radius.card))
            .overlay(RoundedRectangle(cornerRadius: Aero.Radius.card).stroke(Aero.outline, lineWidth: 1))
            .aeroCardShadow()
        }
        .buttonStyle(KineticPressStyle())
    }
}
