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
    /// The skills the setup wizard actually pinned — they choose the suggestion.
    @ObservedObject private var profile = OnboardingProfileStore.shared
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        ZStack(alignment: .bottom) {
            ScrollView(showsIndicators: false) {
                // The sheet's rhythm, measured: 52 above the greeting, 46 to
                // the pills, 40 to the bento, 10 to Today's Used, 18 to its row.
                VStack(alignment: .leading, spacing: 0) {
                    header
                    resumeCard
                    Spacer().frame(height: 52)
                    greeting
                    Spacer().frame(height: 46)
                    pills
                    Spacer().frame(height: 40)
                    bento
                    Spacer().frame(height: 10)
                    VStack(alignment: .leading, spacing: 18) {
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
                // The authored pearl backdrop from the reference render.
                // In dark mode, fall back to the warm gradient + pearls.
                if colorScheme == .dark {
                    LinearGradient(
                        colors: [Color(red: 1, green: 0.70, blue: 0.43).opacity(0.30), Aero.background],
                        startPoint: .topLeading,
                        endPoint: .center
                    )
                    PearlBackdrop()
                } else {
                    Image("GSHomeBackdrop")
                        .resizable()
                        .scaledToFill()
                        .ignoresSafeArea()
                }
            }
            .ignoresSafeArea()
        )
        .toolbar(.hidden, for: .navigationBar)
    }

    // MARK: Header — brand lockup + assistant + menu

    private var header: some View {
        HStack(spacing: 12) {
            // The authored pearl avatar from the reference render.
            Image("GSHomeAvatar")
                .resizable()
                .scaledToFit()
                .frame(width: 42, height: 42)
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
                .font(.system(size: 26, weight: .semibold))
                .foregroundStyle(Aero.text)
            HStack(spacing: 8) {
                Text(account.firstName.isEmpty ? "there" : account.firstName)
                    .font(.system(size: 26, weight: .semibold))
                    .foregroundStyle(Aero.text)
                // One four-point star, not the three-star cluster.
                Image(systemName: "sparkle")
                    .font(.system(size: 22, weight: .semibold))
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
        // All four pills, one row, never scrolled. Measured against a font
        // wider than SF Pro (a conservative bound): the wide tier is 322pt so
        // it clears a 375pt iPhone's 335pt row, and the compact tier is 271pt
        // for the 320pt classes. The old single tier measured 345pt and clipped
        // "More" on any standard iPhone — the defect the review flagged.
        GeometryReader { geo in
            let compact = geo.size.width < 330
            HStack(spacing: compact ? 3 : 4) {
                HomePill(system: "plus", title: "New Chat", compact: compact, action: onNewChat)
                HomePill(system: "safari", title: "Explore", compact: compact, action: onOpenAgent)
                HomePill(system: "sparkles", title: "Create", compact: compact,
                         action: { onStartPrompt(HomeDispatch.createPrompt) })
                HomePill(system: "ellipsis", title: "More", compact: compact, action: onOpenDrawer)
            }
        }
        .frame(height: 46)
    }

    // MARK: Bento — Video / Mind Guide / Today's reflection

    private var bento: some View {
        // Reference: left card 55.6% of content width, right column 37/63 split.
        GeometryReader { geo in
            let leftWidth = (geo.size.width - 12) * 0.556
            HStack(alignment: .top, spacing: 12) {
                VideoCard(action: { onStartPrompt(HomeDispatch.createPrompt) })
                    .frame(width: leftWidth)
                VStack(spacing: 12) {
                    MindGuideCard(action: { onStartPrompt(HomeDispatch.mindGuidePrompt) })
                        .frame(height: (geo.size.height - 12) * 0.375)
                    ReflectionCard(action: { onStartPrompt(HomeDispatch.reflectionPrompt) })
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            }
        }
        .frame(height: 224)
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
        // Same rule as Android: dispatch the first suggestion the wizard's
        // pinned capabilities actually selected, not a generic stub.
        Button(action: {
            if let first = HomeDispatch.selectedSuggestions(for: profile.capabilities).first {
                onStartPrompt(first.prompt)
            }
        }) {
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
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(Aero.text)
                    Text("Try suggested questions first.")
                        .font(.system(size: 12.5))
                        .foregroundStyle(Aero.textMuted)
                }
                Spacer()
            }
            .padding(10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .gsGlass(.card, corner: 16)
        }
        .buttonStyle(KineticPressStyle())
    }

    // MARK: Floating dock

    private var dock: some View {
        HStack(spacing: 14) {
            DockIcon(system: "house.fill", label: "Home", selected: true, action: {})
            DockIcon(book: true, label: "Chats", action: onOpenChats)
            // The reference seats the peach pill inside a white well, so the
            // dock reads as three glass buttons and one lit action.
            Button(action: onNewChat) {
                HStack(spacing: 6) {
                    Image(systemName: "sparkles").font(.system(size: 18, weight: .semibold))
                    Text("Generate")
                        .font(.system(size: 15, weight: .semibold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.9)
                }
                .foregroundStyle(Color(red: 0.29, green: 0.14, blue: 0.08))
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background(Capsule().fill(LinearGradient(
                    colors: [Color(red: 0.98, green: 0.85, blue: 0.78),
                             Color(red: 0.96, green: 0.78, blue: 0.67)],
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
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(Aero.text)
                .frame(width: 46, height: 46)
                .gsGlass(.floatingControl, corner: 23)
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityLabel(label)
    }
}

private struct HomePill: View {
    let system: String
    let title: String
    var compact = false
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: compact ? 3 : 4) {
                Image(systemName: system).font(.system(size: compact ? 11 : 13, weight: .medium))
                Text(title)
                    .font(.system(size: compact ? 11.5 : 13, weight: .medium))
                    .lineLimit(1)
            }
            .foregroundStyle(Aero.text)
            .padding(.horizontal, compact ? 6 : 8)
            .padding(.vertical, 10)
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
                    colors: [Color(red: 0.99, green: 0.98, blue: 0.97),
                             Color(red: 0.95, green: 0.92, blue: 0.96),
                             Color(red: 0.97, green: 0.92, blue: 0.89)],
                    startPoint: .topLeading, endPoint: .bottomTrailing)
                // The authored pearl render from the reference.
                Image("GSHomeVideoArt")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 200, height: 200)
                    .frame(maxWidth: .infinity, maxHeight: .infinity,
                           alignment: .bottomTrailing)
                    .offset(x: 16, y: 12)
                    .allowsHitTesting(false)
                LinearGradient(
                    stops: [
                        .init(color: Color(red: 0.984, green: 0.957, blue: 0.941), location: 0),
                        .init(color: Color(red: 0.984, green: 0.965, blue: 0.953).opacity(0.6), location: 0.55),
                        .init(color: .clear, location: 1)
                    ],
                    startPoint: .leading, endPoint: .trailing
                )
                VStack(alignment: .leading, spacing: 6) {
                    // Copy stacks from the top exactly as the sheet does; only
                    // the play control is pinned to the bottom of the card.
                    HStack(spacing: 5) {
                        Image(systemName: "play").font(.system(size: 10, weight: .bold))
                        Text("Video").font(Aero.caption())
                    }
                    .foregroundStyle(Aero.text)
                    .padding(.horizontal, 9).padding(.vertical, 5)
                    .background(Capsule().fill(Color.white.opacity(0.45)))
                    Spacer().frame(height: 5)
                    Text("Create Stunning\nVideos with AI")
                        .font(.system(size: 16, weight: .semibold))
                        .lineSpacing(-1)
                        .foregroundStyle(Aero.text)
                        .multilineTextAlignment(.leading)
                    Text("Turn your ideas into beautiful videos in minutes.")
                        .font(Aero.caption()).foregroundStyle(Aero.textMuted)
                        .fixedSize(horizontal: false, vertical: true)
                    Spacer()
                    ZStack {
                        Circle().fill(Color.white.opacity(0.55)).frame(width: 44, height: 44)
                        Image(systemName: "play.fill").font(.system(size: 15)).foregroundStyle(Aero.text)
                    }
                }
                .padding(14)
            }
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous)
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
            ZStack {
                // "clarity companion." measures 93pt at 10pt, so the text
                // column keeps every point: a 30pt orb, tight insets, and the
                // chevron overlaid in the corner rather than reserving a gutter.
                HStack(spacing: 6) {
                    Image("GSHomeMindOrb")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 30, height: 30)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Mind Guide")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Aero.text)
                            .lineLimit(1)
                        Text("Your personal\nclarity companion.")
                            .font(.system(size: 10))
                            .lineSpacing(1.5)
                            .foregroundStyle(Aero.textMuted)
                            // Three lines so a 360pt phone wraps "companion."
                            // rather than clipping it to "personal…".
                            .lineLimit(3)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 6)
                .padding(.vertical, 8)
                HStack {
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(Aero.textMuted)
                        .padding(.trailing, 10)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .gsGlass(.card, corner: 16)
        }
        .buttonStyle(KineticPressStyle())
    }
}

private struct ReflectionCard: View {
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            ZStack(alignment: .bottomTrailing) {
                // The authored lotus from the reference — feathered edges.
                Image("GSHomeLotusNew")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 150, height: 100)
                    .offset(x: 4, y: 8)
                    .allowsHitTesting(false)
                VStack(alignment: .leading, spacing: 4) {
                    // Title and chevron share a row: overlaying the glyph made
                    // it graze the final "n" at 360pt. A 10pt inset leaves
                    // 112.5pt at 393pt, and the sheet's 12.5pt title measures
                    // 112.2pt, so it keeps the sheet's size there; 360pt takes
                    // the 10pt step.
                    HStack(spacing: 6) {
                        Text("Today's reflection")
                            .font(.system(size: 12.5, weight: .semibold))
                            .foregroundStyle(Aero.text)
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                        Spacer(minLength: 0)
                        Image(systemName: "chevron.right")
                            .font(.system(size: 12, weight: .semibold)).foregroundStyle(Aero.textMuted)
                    }
                    Text("Take a moment to check in with yourself.")
                        .font(.system(size: 10.5))
                        .lineSpacing(1.5)
                        .foregroundStyle(Aero.textMuted)
                        .lineLimit(3)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            // 10pt horizontal, matching Android, so the title keeps the sheet's
            // 12.5pt at 393pt instead of being scaled down by a 14pt inset.
            .padding(.horizontal, 10)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .gsGlass(.card, corner: 16)
        }
        .buttonStyle(KineticPressStyle())
    }
}

private struct DockIcon: View {
    var system: String = ""
    /// The sheet's Chats glyph is a filled OPEN book; SF only offers an open
    /// outline or a closed fill, so the Android silhouette is drawn here too.
    var book: Bool = false
    let label: String
    var selected = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Group {
                if book {
                    OpenBookGlyph()
                        .fill(selected ? Aero.text : Aero.text.opacity(0.78))
                        .frame(width: 20, height: 20)
                } else {
                    Image(systemName: system)
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(selected ? Aero.text : Aero.text.opacity(0.78))
                }
            }
            .frame(width: 46, height: 46)
            .background(Circle().fill(Color.white.opacity(selected ? 0.62 : 0.44)))
            .overlay(Circle().strokeBorder(Color.white.opacity(0.55), lineWidth: 1))
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityLabel(label)
    }
}

/// Filled open book, same silhouette as Android's `FilledBook`.
private struct OpenBookGlyph: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 24
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x * s, y: y * s) }
        var path = Path()
        path.move(to: p(11.2, 6.4))
        path.addCurve(to: p(2.8, 6.6), control1: p(8.6, 4.9), control2: p(5.4, 4.9))
        path.addCurve(to: p(2.4, 7.4), control1: p(2.5, 6.8), control2: p(2.4, 7.1))
        path.addLine(to: p(2.4, 18.2))
        path.addCurve(to: p(3.7, 19.0), control1: p(2.4, 18.9), control2: p(3.1, 19.3))
        path.addCurve(to: p(11.0, 19.2), control1: p(6.0, 17.7), control2: p(8.8, 17.8))
        path.addCurve(to: p(12.2, 19.2), control1: p(11.4, 19.4), control2: p(11.8, 19.4))
        path.addCurve(to: p(19.5, 19.0), control1: p(14.4, 17.8), control2: p(17.2, 17.7))
        path.addCurve(to: p(20.8, 18.2), control1: p(20.1, 19.3), control2: p(20.8, 18.9))
        path.addLine(to: p(20.8, 7.4))
        path.addCurve(to: p(20.4, 6.6), control1: p(20.8, 7.1), control2: p(20.7, 6.8))
        path.addCurve(to: p(12.0, 6.4), control1: p(17.8, 4.9), control2: p(14.6, 4.9))
        path.addLine(to: p(12.0, 17.4))
        path.addCurve(to: p(11.2, 17.2), control1: p(11.8, 17.4), control2: p(11.4, 17.3))
        path.closeSubpath()
        return path
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
