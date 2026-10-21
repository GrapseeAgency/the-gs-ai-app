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
    /// Reopen a specific thread from the resume card.
    let onOpenConversation: (String) -> Void
    /// Open a new chat with the prompt already written. The reference's cards
    /// are four different doors, not four copies of "blank chat".
    let onStartPrompt: (String) -> Void

    @ObservedObject private var store = ConversationStore.shared
    /// The signed-in name. The reference greets a person, not "there".
    @ObservedObject private var account = AccountStore.shared

    var body: some View {
        ZStack(alignment: .bottom) {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 30) {
                    header
                    resumeCard
                    greeting
                    pills
                    bento
                    // The sheet puts "Today's Used" 30pt below the bento and
                    // its card only 8pt under the heading.
                    VStack(alignment: .leading, spacing: 8) {
                        todayUsedHeader
                        suggestionsCard
                    }
                    // Clear the floating dock so the last card is reachable.
                    Spacer(minLength: 140)
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)
            }
            dock
                .padding(.horizontal, 20)
                .padding(.bottom, 10)
        }
        .background(
            ZStack {
                LinearGradient(
                    colors: [Color(red: 1, green: 0.70, blue: 0.43).opacity(0.30), Aero.background],
                    startPoint: .topLeading,
                    endPoint: .center
                )
                // The sheet's backdrop is made of light through glass, not a
                // flat wash: three huge, out-of-focus pearls sit behind it.
                PearlBackdrop()
            }
            .ignoresSafeArea()
        )
        .toolbar(.hidden, for: .navigationBar)
    }

    // MARK: Header — brand lockup + assistant + menu

    private var header: some View {
        HStack(spacing: 12) {
            // The authored 3D bloom — the sheet's avatar already exists as a
            // real render, so it does not need a vector stand-in.
            AnimatedGsIllustration(imageName: "GSCompanionOrb", size: 38,
                                   breathDuration: 3.4, rotationDegrees: 1.2,
                                   lift: -2, startDelay: 0.08)
            VStack(alignment: .leading, spacing: 2) {
                Text("Nexly")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Aero.text)
                Text("AI Companion").font(.system(size: 13)).foregroundStyle(Aero.textMuted)
            }
            Spacer()
            GlassCircleButton(system: "sparkles", label: "Assistant", action: onNewChat)
            GlassCircleButton(system: "line.3.horizontal", label: "Open menu", action: onOpenDrawer)
        }
    }

    // MARK: Greeting

    // MARK: Resume card

    /// The reference opens on the thread you were just in, as readable prose —
    /// not a list row and not a truncated preview.
    @ViewBuilder private var resumeCard: some View {
        if let conversation = store.conversations.first {
            Button {
                onOpenConversation(conversation.id)
            } label: {
                AeroCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(Self.cleanPreview(store.messages(for: conversation.id).last?.content
                                               ?? conversation.title))
                            .font(Aero.body())
                            .foregroundStyle(Aero.text)
                            .lineLimit(3)
                            .multilineTextAlignment(.leading)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        HStack(spacing: 6) {
                            Text(conversation.title)
                                .font(Aero.caption())
                                .foregroundStyle(Aero.textMuted)
                                .lineLimit(1)
                            Spacer(minLength: 0)
                            Image(systemName: "arrow.right")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(Aero.accent)
                        }
                    }
                    .padding(16)
                }
            }
            .buttonStyle(KineticPressStyle())
            .accessibilityLabel("Continue \(conversation.title)")
        }
    }

    /// Markdown stripped to prose — the same rule the Android card uses, so a
    /// rich reply never leaks fences, pipes or separator rows into a preview.
    static func cleanPreview(_ raw: String, limit: Int = 150) -> String {
        let punctuation = CharacterSet(charactersIn: "|-: ")
        let kept = raw.components(separatedBy: .newlines).filter { line in
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            guard !trimmed.isEmpty else { return false }
            return !trimmed.unicodeScalars.allSatisfy { punctuation.contains($0) }
        }
        var text = kept.joined(separator: " ")
        for token in ["```", "#", "*", "_", ">", "`", "|"] {
            text = text.replacingOccurrences(of: token, with: " ")
        }
        text = text.replacingOccurrences(of: "-{3,}", with: " ", options: .regularExpression)
        text = text.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
        text = text.trimmingCharacters(in: .whitespaces)
        return text.count <= limit ? text : String(text.prefix(limit)).trimmingCharacters(in: .whitespaces) + "…"
    }

    /// The same time-of-day rule the Android dashboard uses — a hardcoded
    /// "Good morning" was wrong for half the day.
    private var greetingWord: String {
        let hour = Calendar.current.component(.hour, from: Date())
        switch hour {
        case 5...11: return "Good morning"
        case 12...16: return "Good afternoon"
        case 17...21: return "Good evening"
        default: return "Still awake"
        }
    }

    private var greeting: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("\(greetingWord),")
                .font(.system(size: 32, weight: .semibold))
                .foregroundStyle(Aero.text)
            HStack(spacing: 8) {
                Text(account.firstName.isEmpty ? "there" : account.firstName)
                    .font(.system(size: 32, weight: .semibold))
                    .foregroundStyle(Aero.text)
                // One four-point star, not the three-star cluster.
                Image(systemName: "sparkle")
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundStyle(Aero.accent)
            }
            Text("Your thoughts, questions, ideas —\nI'm here for it all.")
                .font(.system(size: 15))
                .lineSpacing(4)
                .foregroundStyle(Aero.textMuted)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.top, 6)
    }

    // MARK: Quick-action pills

    private var pills: some View {
        // All four pills, one row. At 12pt with 9pt of inset the set measures
        // ~300pt, so it fits a 360pt phone as well as the sheet's 393pt and
        // nothing has to be swiped to be discovered.
        HStack(spacing: 5) {
            HomePill(system: "plus", title: "New Chat", action: onNewChat)
            HomePill(system: "safari", title: "Explore", action: onOpenAgent)
            HomePill(system: "sparkles", title: "Create",
                     action: { onStartPrompt("Help me create a short video concept: ") })
            HomePill(system: "ellipsis", title: "More", action: onOpenDrawer)
        }
    }

    // MARK: Bento — Video / Mind Guide / Today's reflection

    private var bento: some View {
        // Measured from the sheet: 55 / 45 across, and the stacked pair splits
        // 39 / 61. An even split made the reflection card half the height it
        // should be and squeezed the video card's artwork out of frame.
        GeometryReader { geo in
            let leftWidth = (geo.size.width - 12) * 0.55
            HStack(alignment: .top, spacing: 12) {
                VideoCard(action: { onStartPrompt("Help me create a short video concept: ") })
                    .frame(width: leftWidth)
                VStack(spacing: 12) {
                    MindGuideCard(action: { onStartPrompt("Help me think this through with clarity: ") })
                        .frame(height: (geo.size.height - 12) * 0.36)
                    ReflectionCard(action: { onStartPrompt("Let's do today's reflection. ") })
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            }
        }
        .frame(height: 200)
    }

    // MARK: Today's Used + suggestions

    private var todayUsedHeader: some View {
        HStack {
            Text("Today's Used")
                .font(.system(size: 18, weight: .semibold))
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
        Button(action: { onStartPrompt("Suggest a few questions I could ask about ") }) {
            HStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(Color.white.opacity(0.55))
                        .frame(width: 32, height: 32)
                    Image(systemName: "lightbulb")
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(Aero.text)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text("Suggestions")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(Aero.text)
                    Text("Try suggested questions first.")
                        .font(.system(size: 12))
                        .foregroundStyle(Aero.textMuted)
                }
                Spacer()
            }
            .padding(10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .gsGlass(.card, corner: Aero.Radius.card)
        }
        .buttonStyle(KineticPressStyle())
    }

    // MARK: Floating dock

    private var dock: some View {
        HStack(spacing: 14) {
            DockIcon(system: "house.fill", label: "Home", selected: true, action: {})
            DockIcon(system: "book.fill", label: "Chats", action: onOpenChats)
            // The reference seats the peach pill inside a white well, so the
            // dock reads as three glass buttons and one lit action.
            Button(action: onNewChat) {
                HStack(spacing: 6) {
                    Image(systemName: "sparkles").font(.system(size: 18, weight: .semibold))
                    Text("Generate").font(.system(size: 15, weight: .semibold))
                }
                .foregroundStyle(Color(red: 0.29, green: 0.14, blue: 0.08))
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background(Capsule().fill(LinearGradient(
                    colors: [Color(red: 1, green: 0.83, blue: 0.71),
                             Color(red: 1, green: 0.75, blue: 0.61)],
                    startPoint: .leading, endPoint: .trailing)))
                .padding(6)
                .background(Capsule().fill(Color.white.opacity(0.55)))
            }
            .buttonStyle(KineticPressStyle())
            DockIcon(system: "gearshape.fill", label: "Settings", action: onOpenDrawer)
        }
        .padding(14)
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
                .frame(width: 40, height: 40)
                .gsGlass(.floatingControl, corner: 20)
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
            HStack(spacing: 4) {
                Image(systemName: system).font(.system(size: 12.5, weight: .medium))
                Text(title).font(.system(size: 12, weight: .medium))
            }
            .foregroundStyle(Aero.text)
            .padding(.horizontal, 9)
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
                // Sampled from the sheet: near-white card, colour from the art.
                LinearGradient(
                    colors: [Color(red: 0.99, green: 0.98, blue: 0.97),
                             Color(red: 0.97, green: 0.92, blue: 0.945),
                             Color(red: 0.98, green: 0.93, blue: 0.90)],
                    startPoint: .topLeading, endPoint: .bottomTrailing)
                // The reference's artwork is the pearl, bleeding off the
                // lower-right corner — not a film-strip glyph.
                PearlCluster()
                    .frame(maxWidth: .infinity, maxHeight: .infinity,
                           alignment: .bottomTrailing)
                    .offset(x: 44, y: 38)
                    .allowsHitTesting(false)
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
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Aero.text)
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
            HStack(spacing: 8) {
                PearlOrb(size: 36)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Mind Guide")
                        .font(.system(size: 13.5, weight: .semibold))
                        .foregroundStyle(Aero.text)
                        .lineLimit(1)
                    Text("Your personal clarity companion.")
                        .font(.system(size: 11.5))
                        .foregroundStyle(Aero.textMuted)
                        .lineLimit(2)
                        .minimumScaleFactor(0.9)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.system(size: 11, weight: .semibold)).foregroundStyle(Aero.textMuted)
            }
            .padding(12)
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
                        Text("Today's reflection")
                            .font(.system(size: 13.5, weight: .semibold))
                            .foregroundStyle(Aero.text)
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.system(size: 12, weight: .semibold)).foregroundStyle(Aero.textMuted)
                    }
                    Text("Take a moment to check in with yourself.")
                        .font(.system(size: 11.5))
                        .foregroundStyle(Aero.textMuted)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.trailing, 26)
                }
                GsArtwork(
                    art: .lotus,
                    size: 96,
                    label: "A lotus in bloom")
                    .offset(x: 12, y: 16)
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
                .foregroundStyle(selected ? Aero.text : Aero.text.opacity(0.78))
                .frame(width: 46, height: 46)
                .background(Circle().fill(Color.white.opacity(selected ? 0.62 : 0.44)))
                .overlay(Circle().strokeBorder(Color.white.opacity(0.55), lineWidth: 1))
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityLabel(label)
    }
}


// MARK: - Pearlescent material

/// One glass sphere: white crown, violet shoulder, blue rim, blush underside,
/// and a specular highlight — the reference's hero material.
private struct PearlOrb: View {
    var size: CGFloat = 44

    var body: some View {
        Circle()
            .fill(
                RadialGradient(
                    colors: [
                        Color.white,
                        Color(red: 0.85, green: 0.78, blue: 1.00),
                        Color(red: 0.56, green: 0.61, blue: 1.00),
                        Color(red: 0.39, green: 0.71, blue: 1.00),
                        Color(red: 1.00, green: 0.65, blue: 0.78)
                    ],
                    center: UnitPoint(x: 0.34, y: 0.28),
                    startRadius: 0,
                    endRadius: size * 0.78
                )
            )
            .frame(width: size, height: size)
            .overlay(Circle().strokeBorder(Color.white.opacity(0.65), lineWidth: 0.8))
            .overlay(alignment: .topLeading) {
                Circle()
                    .fill(Color.white.opacity(0.7))
                    .frame(width: size * 0.24, height: size * 0.24)
                    .padding(.leading, size * 0.22)
                    .padding(.top, size * 0.18)
            }
            .shadow(color: Color(red: 0.88, green: 0.55, blue: 0.62).opacity(0.20),
                    radius: size * 0.18, y: size * 0.10)
    }
}

/// Three overlapping pearls for the video card's artwork.
private struct PearlCluster: View {
    var body: some View {
        ZStack {
            // A haze holds the cluster together, so it reads as one mass of
            // light rather than three separate balls.
            Circle()
                .fill(RadialGradient(
                    colors: [Color(red: 1, green: 0.85, blue: 0.93).opacity(0.35),
                             Color(red: 0.81, green: 0.76, blue: 1).opacity(0.18),
                             .clear],
                    center: .center, startRadius: 0, endRadius: 110))
                .frame(width: 220, height: 220)
            PearlOrb(size: 112).offset(x: -10, y: 10)
            PearlOrb(size: 64).offset(x: 56, y: -38)
            PearlOrb(size: 46).offset(x: 20, y: 52)
            PearlOrb(size: 40).offset(x: -48, y: 58)
                .opacity(0.85)
        }
        .frame(width: 210, height: 210)
    }
}

/// The ambient backdrop: very large, very soft pearls behind the content.
private struct PearlBackdrop: View {
    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            ZStack {
                PearlOrb(size: w * 0.72)
                    .opacity(0.62)
                    .blur(radius: 26)
                    .position(x: w * 0.98, y: geo.size.height * 0.16)
                PearlOrb(size: w * 0.62)
                    .opacity(0.50)
                    .blur(radius: 30)
                    .position(x: w * -0.02, y: geo.size.height * 0.60)
                PearlOrb(size: w * 0.76)
                    .opacity(0.52)
                    .blur(radius: 30)
                    .position(x: w * 0.72, y: geo.size.height * 0.98)
            }
        }
        .allowsHitTesting(false)
    }
}
