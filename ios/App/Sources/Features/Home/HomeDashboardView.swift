import SwiftUI

/// Warm consumer home for the real iOS app — the SwiftUI twin of the rebuilt
/// Android `HomeDashboardScreen`, laid out to the Nexly reference sheet: brand
/// lockup, greeting, quick-action pills, the Video / Mind Guide / reflection
/// bento, "Today's Used", a suggestions card, and a floating glass dock.
/// Presentation is native SwiftUI; every action hands off to the Router and
/// nothing here fabricates backend data.
struct HomeDashboardView: View {
    let onNewChat: () -> Void
    let onOpenDrawer: () -> Void
    let onOpenChats: () -> Void
    let onOpenAgent: () -> Void

    var body: some View {
        ZStack(alignment: .bottom) {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 18) {
                    header
                    greeting
                    pills
                    bento
                    todayUsedHeader
                    suggestionsCard
                    // Clear the floating dock so the last card is reachable.
                    Spacer(minLength: 120)
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)
            }
            dock
                .padding(.horizontal, 20)
                .padding(.bottom, 10)
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

    // MARK: Header — brand lockup + assistant + menu

    private var header: some View {
        HStack(spacing: 10) {
            ZStack {
                Circle()
                    .fill(LinearGradient(
                        colors: [Color(red: 1, green: 0.89, blue: 0.81),
                                 Color(red: 1, green: 0.79, blue: 0.66),
                                 Color(red: 1, green: 0.70, blue: 0.56)],
                        startPoint: .top, endPoint: .bottom))
                    .frame(width: 42, height: 42)
                AnimatedGsIllustration(imageName: "GSCompanionOrb", size: 34,
                                       breathDuration: 3.4, rotationDegrees: 1.2,
                                       lift: -2, startDelay: 0.08)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text("Nexly").font(Aero.title()).foregroundStyle(Aero.text)
                Text("AI Companion").font(Aero.caption()).foregroundStyle(Aero.textMuted)
            }
            Spacer()
            GlassCircleButton(system: "sparkles", label: "Assistant", action: onNewChat)
            GlassCircleButton(system: "line.3.horizontal", label: "Open menu", action: onOpenDrawer)
        }
    }

    // MARK: Greeting

    private var greeting: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Good morning,")
                .font(Aero.display())
                .foregroundStyle(Aero.text)
            HStack(spacing: 8) {
                Text("there")
                    .font(Aero.display())
                    .foregroundStyle(Aero.text)
                Image(systemName: "sparkles")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(Aero.accent)
            }
            Text("Your thoughts, questions, ideas —\nI'm here for it all.")
                .font(Aero.body())
                .foregroundStyle(Aero.textMuted)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.top, 6)
    }

    // MARK: Quick-action pills

    private var pills: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                HomePill(system: "plus", title: "New Chat", action: onNewChat)
                HomePill(system: "safari", title: "Explore", action: onOpenAgent)
                HomePill(system: "sparkles", title: "Create", action: onNewChat)
                HomePill(system: "ellipsis", title: "More", action: onOpenDrawer)
            }
        }
    }

    // MARK: Bento — Video / Mind Guide / Today's reflection

    private var bento: some View {
        HStack(alignment: .top, spacing: 12) {
            VideoCard(action: onNewChat)
                .frame(maxWidth: .infinity)
            VStack(spacing: 12) {
                MindGuideCard(action: onNewChat)
                ReflectionCard(action: onNewChat)
            }
            .frame(maxWidth: .infinity)
        }
        .frame(height: 208)
    }

    // MARK: Today's Used + suggestions

    private var todayUsedHeader: some View {
        HStack {
            Text("Today's Used")
                .font(Aero.headline())
                .foregroundStyle(Aero.text)
            Spacer()
            Button(action: onOpenChats) {
                HStack(spacing: 4) {
                    Text("See All").font(Aero.label())
                    Image(systemName: "arrow.right").font(.system(size: 11, weight: .semibold))
                }
                .foregroundStyle(Aero.textMuted)
            }
            .buttonStyle(KineticPressStyle())
        }
        .padding(.top, 4)
    }

    private var suggestionsCard: some View {
        Button(action: onNewChat) {
            HStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 13, style: .continuous)
                        .fill(Color.white.opacity(0.55))
                        .frame(width: 40, height: 40)
                    Image(systemName: "lightbulb")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundStyle(Aero.text)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text("Suggestions").font(Aero.body()).foregroundStyle(Aero.text)
                    Text("Try suggested questions first.")
                        .font(Aero.caption()).foregroundStyle(Aero.textMuted)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Aero.textMuted)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .gsGlass(.card, corner: Aero.Radius.card)
        }
        .buttonStyle(KineticPressStyle())
    }

    // MARK: Floating dock

    private var dock: some View {
        HStack(spacing: 6) {
            DockIcon(system: "house.fill", label: "Home", selected: true, action: {})
            DockIcon(system: "bubble.left", label: "Chats", action: onOpenChats)
            Button(action: onNewChat) {
                HStack(spacing: 6) {
                    Image(systemName: "sparkles").font(.system(size: 15, weight: .semibold))
                    Text("Generate").font(Aero.button())
                }
                .foregroundStyle(Color(red: 0.35, green: 0.18, blue: 0.10))
                .frame(maxWidth: .infinity)
                .frame(height: 46)
                .background(Capsule().fill(LinearGradient(
                    colors: [Color(red: 1, green: 0.79, blue: 0.66),
                             Color(red: 1, green: 0.70, blue: 0.56)],
                    startPoint: .leading, endPoint: .trailing)))
            }
            .buttonStyle(KineticPressStyle())
            DockIcon(system: "gearshape", label: "Settings", action: onOpenDrawer)
        }
        .padding(8)
        .gsCapsuleGlass(.floatingControl)
    }
}

// MARK: - Building blocks

private struct GlassCircleButton: View {
    let system: String
    let label: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Image(systemName: system)
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(Aero.text)
                .frame(width: 42, height: 42)
                .gsGlass(.floatingControl, corner: 21)
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityLabel(label)
    }
}

private struct HomePill: View {
    let system: String
    let title: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 7) {
                Image(systemName: system).font(.system(size: 14, weight: .medium))
                Text(title).font(Aero.label())
            }
            .foregroundStyle(Aero.text)
            .padding(.horizontal, 14)
            .padding(.vertical, 11)
            .gsCapsuleGlass(.floatingControl)
        }
        .buttonStyle(KineticPressStyle())
    }
}

private struct VideoCard: View {
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            ZStack(alignment: .topLeading) {
                LinearGradient(
                    colors: [Color(red: 0.91, green: 0.86, blue: 1.0),
                             Color(red: 1.0, green: 0.85, blue: 0.91),
                             Color(red: 1.0, green: 0.91, blue: 0.83)],
                    startPoint: .topLeading, endPoint: .bottomTrailing)
                VStack {
                    HStack {
                        Spacer()
                        GsArtwork(
                            art: .video,
                            size: 70,
                            label: "Translucent film cards with a glass lens")
                            .offset(x: 10, y: -8)
                    }
                    Spacer()
                }
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 5) {
                        Image(systemName: "play").font(.system(size: 10, weight: .bold))
                        Text("Video").font(Aero.caption())
                    }
                    .foregroundStyle(Aero.text)
                    .padding(.horizontal, 9).padding(.vertical, 5)
                    .background(Capsule().fill(Color.white.opacity(0.45)))
                    Spacer()
                    Text("Create Stunning\nVideos with AI")
                        .font(Aero.title()).foregroundStyle(Aero.text)
                        .multilineTextAlignment(.leading)
                    Text("Turn your ideas into beautiful videos in minutes.")
                        .font(Aero.caption()).foregroundStyle(Aero.textMuted)
                        .fixedSize(horizontal: false, vertical: true)
                    ZStack {
                        Circle().fill(Color.white.opacity(0.55)).frame(width: 44, height: 44)
                        Image(systemName: "play.fill").font(.system(size: 15)).foregroundStyle(Aero.text)
                    }
                    .padding(.top, 4)
                }
                .padding(14)
            }
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous)
                .stroke(Color.white.opacity(0.35), lineWidth: 1))
            .aeroCardShadow()
        }
        .buttonStyle(KineticPressStyle())
        .frame(maxHeight: .infinity)
    }
}

private struct MindGuideCard: View {
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                GsArtwork(art: .clarity, size: 34, label: "A clarity lens holding a bloom")
                VStack(alignment: .leading, spacing: 2) {
                    Text("Mind Guide").font(Aero.label()).foregroundStyle(Aero.text)
                    Text("Your personal clarity")
                        .font(Aero.caption()).foregroundStyle(Aero.textMuted)
                        .lineLimit(2)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .semibold)).foregroundStyle(Aero.textMuted)
            }
            .padding(14)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .gsGlass(.card, corner: Aero.Radius.card)
        }
        .buttonStyle(KineticPressStyle())
    }
}

private struct ReflectionCard: View {
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            ZStack(alignment: .bottomTrailing) {
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text("Today's reflection").font(Aero.label()).foregroundStyle(Aero.text)
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.system(size: 12, weight: .semibold)).foregroundStyle(Aero.textMuted)
                    }
                    Text("Take a moment to check in with yourself.")
                        .font(Aero.caption()).foregroundStyle(Aero.textMuted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                GsArtwork(
                    art: .reflection,
                    size: 58,
                    label: "A lotus resting on still water")
                    .allowsHitTesting(false)
            }
            .padding(14)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .gsGlass(.card, corner: Aero.Radius.card)
        }
        .buttonStyle(KineticPressStyle())
    }
}

private struct DockIcon: View {
    let system: String
    let label: String
    var selected = false
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Image(systemName: system)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(selected ? Aero.text : Aero.textMuted)
                .frame(width: 46, height: 46)
                .background(Circle().fill(Aero.text.opacity(selected ? 0.08 : 0)))
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityLabel(label)
    }
}
