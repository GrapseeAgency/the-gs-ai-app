import SwiftUI

struct TravelAgentView: View {
    let onBack: () -> Void
    let onSendToChat: (String) -> Void

    /// The same four starting points Android offers, with the same prompts —
    /// the screen is reachable from Explore/Discover now, so its content has
    /// to be a real brief rather than leftover generic chips.
    private struct TravelIdea: Identifiable {
        let id: String
        let title: String
        let icon: String
        let prompt: String
    }

    private static let ideas: [TravelIdea] = [
        TravelIdea(
            id: "slow", title: "A slow week", icon: "leaf",
            prompt: "Plan a slow seven-day trip somewhere warm. Ask me my budget and preferred pace, then build a day-by-day plan with one good neighbourhood and two day trips."),
        TravelIdea(
            id: "family", title: "A calm family trip", icon: "person.2",
            prompt: "Help me plan a calm family trip. Ask ages and how far we'll travel, then suggest three places with short transfers, easy food, and a rest day between activities."),
        TravelIdea(
            id: "work", title: "A work trip", icon: "briefcase",
            prompt: "I have a work trip coming up. Ask me the city and the meeting hours, then build an itinerary that protects focus time and adds one good walk each evening."),
        TravelIdea(
            id: "compare", title: "Compare two cities", icon: "mappin.and.ellipse",
            prompt: "Compare two cities for a four-night break. Ask me the month and budget, then use a table for cost, weather, walkability and the one thing each city does better."),
    ]

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
                        capability("compass", "Itineraries that breathe",
                                   "One anchor a day, not eight stops and a sprint.")
                        capability("globe", "Routes and neighbourhoods",
                                   "Walk times, transfers and where to stay for the trip you want.")
                        capability("gauge", "Budgets you can read",
                                   "A table with the real trade-offs, not a single invented total.")
                        capability("doc.text", "A plan you can keep",
                                   "Ask for a day-by-day table, a packing list, or a map with pins.")
                    }
                }
                Text("Travel details change. Verify opening hours, visas and prices before you book.")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
                    .padding(.horizontal, 6)
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
            }
            .buttonStyle(KineticPressStyle())
            .accessibilityLabel("Back")
            Text("Travel Agent").font(Aero.title()).foregroundStyle(Aero.text)
            Spacer()
            Image(systemName: "globe").foregroundStyle(Aero.accent).frame(width: 42, height: 42).background(Circle().fill(Aero.accentSoft))
        }
    }

    private var orb: some View {
        AnimatedGsIllustration(imageName: "GSTravelAgentOrb", size: 164, breathDuration: 4.1, rotationDegrees: 1.6, lift: -4, startDelay: 0.26)
            .accessibilityLabel("Travel Agent companion orb")
    }

    private var chips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Aero.Spacing.s) {
                ForEach(Self.ideas) { idea in
                    AgentChip(title: idea.title, icon: idea.icon) {
                        onSendToChat(idea.prompt)
                    }
                }
            }
        }
    }

    private func capability(_ glyph: String, _ title: String, _ detail: String) -> some View {
        HStack(alignment: .top, spacing: 9) {
            Image(systemName: glyph)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Aero.accent)
                .frame(width: 20)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(Aero.label()).foregroundStyle(Aero.text)
                Text(detail).font(Aero.caption()).foregroundStyle(Aero.textMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
    }
}

private struct AgentChip: View {
    let title: String
    let icon: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Label(title, systemImage: icon).font(Aero.label()).foregroundStyle(Aero.text).padding(.horizontal, 14).padding(.vertical, 10).background(Capsule().fill(Aero.surface))
        }.buttonStyle(KineticPressStyle())
    }
}
