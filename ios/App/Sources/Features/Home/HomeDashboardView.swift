import SwiftUI

/// Warm consumer home for the real iOS app — the SwiftUI twin of the rebuilt
/// Android `HomeDashboardScreen`, laid out to the Nexly reference sheet: brand
/// lockup, greeting, quick-action pills, the Video / Mind Guide / reflection
/// bento, "Today's Used", a suggestions card, and a floating glass dock.
/// Presentation is native SwiftUI; every action hands off to the Router and
/// nothing here fabricates backend data.
/// The page's own numbers, in one place.
///
/// These were literals repeated across the view: 20 spelled twice (the content
/// inset and the dock's), and a 140pt clearance standing in for "taller than the
/// dock". A change to the dock's height then silently un-changed the clearance,
/// and the last card slid under it. Anything that describes the same physical
/// thing is now derived from the same source.
enum HomeMetrics {
    /// Horizontal inset for the scroll content AND the dock. Two literals that
    /// had to agree and had nothing making them.
    static let margin: CGFloat = 20
    /// The dock's own height, from the numbers that draw it: one scaled icon
    /// button, the capsule's 14pt padding top and bottom, and the 10pt the view
    /// adds below it.
    static var dockHeight: CGFloat {
        Aero.scaledLength(46, relativeTo: .body) + 14 * 2 + 10
    }
    /// Air between the last card and the top of the dock.
    static let dockClearance: CGFloat = 24
}

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
    /// The header sparkle opens voice mode, matching Android's `onOpenVoice`.
    /// It used to open a blank chat, so a control labelled "Assistant" did the
    /// same thing as the one labelled "New Chat".
    let onOpenVoice: () -> Void
    /// The dock gear opens Settings, matching Android's `GsRoutes.SETTINGS`.
    /// It used to open the drawer, which the hamburger already does.
    let onOpenSettings: () -> Void
    /// Frozen clock hour for the greeting. nil means "use the real clock".
    ///
    /// Only the render harness sets this. A golden image whose words change at
    /// noon cannot be diffed against anything — the first iOS capture proved it
    /// by rendering "Good afternoon" while the sheet says "Good morning", which
    /// looks like a design defect in a screenshot and is actually a clock.
    var greetingHour: Int? = nil

    /// The signed-in name. The reference greets a person, not "there".
    @ObservedObject private var account = AccountStore.shared
    /// The skills the setup wizard actually pinned — they choose the suggestion.
    @ObservedObject private var profile = OnboardingProfileStore.shared
    @Environment(\.colorScheme) private var colorScheme

    /// Anchor for the dock's Home button. See `dock(scrollToTop:)`.
    private static let topAnchorID = "home-top"

    var body: some View {
        ScrollViewReader { proxy in
            ZStack(alignment: .bottom) {
                ScrollView(showsIndicators: false) {
                    // The sheet's rhythm, measured: 52 above the greeting, 46 to
                    // the pills, 40 to the bento, 10 to Today's Used, 18 to its row.
                    VStack(alignment: .leading, spacing: 0) {
                        header
                        HomeResumeCard(onOpenConversation: onOpenConversation)
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
                        // Clear the floating dock so the last card is reachable —
                        // derived from the dock's own height, not a 140 that
                        // happened to be enough the day it was written.
                        Spacer(minLength: HomeMetrics.dockHeight + HomeMetrics.dockClearance)
                    }
                    .padding(.horizontal, HomeMetrics.margin)
                    .padding(.top, 8)
                    .id(Self.topAnchorID)
                }
                dock(scrollToTop: {
                    withAnimation(.easeOut(duration: 0.25)) {
                        proxy.scrollTo(Self.topAnchorID, anchor: .top)
                    }
                })
                .padding(.horizontal, HomeMetrics.margin)
                .padding(.bottom, 10)
            }
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
                // 15: the sheet's band is 14.6 pt against our 16.0 at 16.5, measured
                // over the whole string so the descender is inside the window.
                Text("Nexly")
                    .font(Aero.responsive(15, .semibold, relativeTo: .headline))
                    .foregroundStyle(Aero.text)
                Text("AI Companion")
                    // .medium, not .regular: Android renders this role as
                    // labelSmall, and labelSmall is Medium 500 in Type.kt. The
                    // pair was one of the type-role mismatches the last review
                    // listed; check_home_type_parity.py now fails if either side
                    // moves off the shared contract again.
                    // 10: the sheet's band is 9.71 pt against our 11.00 at 11.
                    .font(Aero.responsive(10, .medium, relativeTo: .subheadline))
                    .foregroundStyle(Aero.textMuted)
            }
            Spacer()
            GlassCircleButton(icon: GsIcons.sparkles, label: "Assistant", action: onOpenVoice)
            GlassCircleButton(icon: GsIcons.menu, label: "Open menu", action: onOpenDrawer)
        }
    }

    // MARK: Greeting

    // MARK: Resume card

    /// The reference opens on the thread you were just in, as readable prose —
    /// not a list row and not a truncated preview.
    private struct HomeResumeCard: View {
        let onOpenConversation: (String) -> Void
        /// The store is observed HERE, not on HomeDashboardView.
        ///
        /// ConversationStore is the app's hottest object — every message write,
        /// pin and archive publishes through it — and Home is the NavigationStack
        /// root, so it stays mounted for the whole session. While the page's own
        /// body observed the store, every keystroke in a chat invalidated a view
        /// containing three nested GeometryReaders, a full-bleed raster and a
        /// blurred glass card per surface. Only this card reads the store, so only
        /// this card re-renders.
        @ObservedObject private var store = ConversationStore.shared

        var body: some View {
            // Inbox ordering, not the raw array. `conversations` has no ordering
            // contract — it is append-only from the bootstrap — so `.first` pinned
            // the OLDEST chat ever created, never updated as newer work happened,
            // and could deep-link into an archived thread. `activeConversations`
            // is the documented ordering (archived hidden, pins float, newest
            // first) and is what Android's resume card reads.
            if let conversation = store.activeConversations.first {
                Button {
                    onOpenConversation(conversation.id)
                } label: {
                    AeroCard {
                        VStack(alignment: .leading, spacing: 10) {
                            // O(1) index read. `messages(for:).last` filters and
                            // sorts the entire message table on every Home render
                            // — the exact per-row pattern ConversationStore
                            // documents as its fixed performance defect.
                            Text(HomeDashboardView.cleanPreview(store.lastMessage(for: conversation.id)?.content
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
                                GsIconView(GsIcons.arrowRight,
                                           size: Aero.scaledLength(13, relativeTo: .caption),
                                           color: Aero.accent)
                            }
                        }
                        .padding(16)
                    }
                }
                .buttonStyle(KineticPressStyle())
                .accessibilityLabel("Continue \(conversation.title)")
            }
            }
    }


    /// Markdown stripped to prose — the same rule the Android card uses, so a
    /// rich reply never leaks fences, pipes or separator rows into a preview.
    static func cleanPreview(_ raw: String, limit: Int = 150) -> String {
        // Delete fenced code blocks outright before anything else. Android's
        // twin strips the whole block with a dotall regex; replacing only the
        // fence markers left every line of the code intact, so a code-heavy
        // last message rendered as a raw code dump in the card — the exact
        // "reads as garbage" case this function exists to prevent.
        var text = raw.replacingOccurrences(of: "```[\\s\\S]*?```", with: " ",
                                           options: .regularExpression)
        let punctuation = CharacterSet(charactersIn: "|-: ")
        let kept = text.components(separatedBy: .newlines).filter { line in
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            guard !trimmed.isEmpty else { return false }
            return !trimmed.unicodeScalars.allSatisfy { punctuation.contains($0) }
        }
        // Plain assignment, not a second `var`: the fenced-block strip above
        // already introduced `text` in this scope. Declaring it twice is an
        // invalid redeclaration that failed the whole App target's compile.
        text = kept.joined(separator: " ")
        for token in ["```", "#", "*", "_", ">", "`", "|"] {
            text = text.replacingOccurrences(of: token, with: " ")
        }
        // Markdown horizontal rules (`---`) are a separate case from fenced
        // blocks and must survive as its own strip.
        text = text.replacingOccurrences(of: "-{3,}", with: " ", options: .regularExpression)
        text = text.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
        text = text.trimmingCharacters(in: .whitespaces)
        return text.count <= limit ? text : String(text.prefix(limit)).trimmingCharacters(in: .whitespaces) + "…"
    }

    /// The same time-of-day rule the Android dashboard uses — a hardcoded
    /// "Good morning" was wrong for half the day.
    private var greetingWord: String {
        Self.greetingWord(forHour: greetingHour ?? Calendar.current.component(.hour, from: Date()))
    }

    /// Pure so both the view and a deterministic render can use it.
    static func greetingWord(forHour hour: Int) -> String {
        switch hour {
        case 5...11: return "Good morning"
        case 12...16: return "Good afternoon"
        case 17...21: return "Good evening"
        default: return "Still awake"
        }
    }

    private var greeting: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Every Home role goes through Aero.responsive, not .system(size:).
            // .system is an absolute font: it ignores the system content-size
            // category and the Settings font-scale multiplier that every other
            // screen honours, so the page silently opted out of the app's
            // accessibility contract. responsive() keeps the exact spec size at
            // the default category and scales on the role's real curve beyond it.
            Text("\(greetingWord),")
                .font(Aero.responsive(26, .semibold, relativeTo: .title))
                .foregroundStyle(Aero.text)
            HStack(spacing: 8) {
                Text(account.firstName.isEmpty ? "there" : account.firstName)
                    .font(Aero.responsive(26, .semibold, relativeTo: .title))
                    .foregroundStyle(Aero.text)
                // One four-point star, not the three-star cluster.
                GsIconView(GsIcons.sparkle,
                           size: Aero.scaledLength(22, relativeTo: .title2),
                           color: Aero.accent)
            }
            // 12.5 with a 3 pt leading: the sheet's subtitle lines are 18 pt apart.
            // 15/22 was the contract's value and the contract was derived from the
            // code, so both platforms agreed on a number the picture does not use.
            Text("Your thoughts, questions, ideas —\nI'm here for it all.")
                .font(Aero.responsive(12.5, .regular, relativeTo: .caption))
                .lineSpacing(3)
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
                HomePill(icon: GsIcons.plus, title: "New Chat", compact: compact, action: onNewChat)
                HomePill(icon: GsIcons.compass, title: "Explore", compact: compact, action: onOpenAgent)
                HomePill(icon: GsIcons.sparkles, title: "Create", compact: compact,
                         action: { onStartPrompt(HomeDispatch.createPrompt) })
                HomePill(icon: GsIcons.ellipsis, title: "More", compact: compact, action: onOpenDrawer)
            }
        }
        // Height derived from the pill's own text curve, not pinned: a literal
        // 46 keeps the row fixed while scaled type grows past it.
        .frame(height: Aero.scaledLength(46, relativeTo: .caption))
    }

    // MARK: Bento — Video / Mind Guide / Today's reflection

    private var bento: some View {
        // Measured from the sheet's card rims: 54.5 / 45.5 across, 10dp gutter,
        // 214dp row, right column 37.7 / 62.3. The values below are the ones
        // currently shipped on BOTH platforms; they are within a point of the
        // measurement except the column split, which is corrected in Wave 3.
        GeometryReader { geo in
            let leftWidth = (geo.size.width - 10) * 0.545
            HStack(alignment: .top, spacing: 10) {
                VideoCard(action: { onStartPrompt(HomeDispatch.createPrompt) })
                    .frame(width: leftWidth)
                VStack(spacing: 10) {
                    MindGuideCard(action: { onStartPrompt(HomeDispatch.mindGuidePrompt) })
                        .frame(height: (geo.size.height - 10) * 0.375)
                    ReflectionCard(action: { onStartPrompt(HomeDispatch.reflectionPrompt) })
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            }
        }
        .frame(height: Aero.scaledLength(214, relativeTo: .body))
    }

    // MARK: Today's Used + suggestions

    private var todayUsedHeader: some View {
        HStack {
            // 15: the sheet's band is 15.78 pt against our 19.00 at 18.
            Text("Today's Used")
                .font(Aero.responsive(15, .semibold, relativeTo: .headline))
                .foregroundStyle(Aero.text)
            Spacer()
            Button(action: onOpenChats) {
                HStack(spacing: 4) {
                    // 11: the sheet's band is 7.89 pt against our 10.33 at 14.
                    Text("See All").font(Aero.responsive(11, .semibold, relativeTo: .callout))
                    GsIconView(GsIcons.arrowRight,
                               size: Aero.scaledLength(11, relativeTo: .caption),
                               color: Aero.textMuted)
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
                    GsIconView(GsIcons.lightbulb,
                               size: Aero.scaledLength(15, relativeTo: .body),
                               color: Aero.text)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text("Suggestions")
                        .font(Aero.responsive(14, .medium, relativeTo: .body))
                        .foregroundStyle(Aero.text)
                    Text("Try suggested questions first.")
                        .font(Aero.responsive(12, .medium, relativeTo: .footnote))
                        .foregroundStyle(Aero.textMuted)
                }
                Spacer()
            }
            .padding(10)
            .frame(maxWidth: .infinity, alignment: .leading)
            // cardDim, not cardLight: this row sits on flat cream, where the
            // sheet's own hairline measures +2.6 L instead of the bento's +19.9.
            .gsGlass(.cardDim, corner: Aero.Radius.homeCard)
        }
        .buttonStyle(KineticPressStyle())
    }

    // MARK: Floating dock

    private func dock(scrollToTop: @escaping () -> Void) -> some View {
        HStack(spacing: 14) {
            // Tapping the tab you are already on means "go back to the top".
            // It used to be `action: {}`, which leaves a labelled, focusable,
            // VoiceOver-announced control that does nothing when used.
            // The dock's Home and Settings marks are the family's FILLED
            // silhouettes — the twins of Android's `FilledHouse` / `FilledGear`
            // in HomeDashboardScreen.kt, drawn in the same 24-unit space as
            // OpenBookGlyph below — because the reference's dock is solid and
            // the generated set is stroke-only by contract.
            DockIcon(house: true, label: "Home", selected: true, action: scrollToTop)
            DockIcon(book: true, label: "Chats", action: onOpenChats)
            // The reference seats the peach pill inside a white well, so the
            // dock reads as three glass buttons and one lit action.
            Button(action: onNewChat) {
                HStack(spacing: 6) {
                    GsIconView(GsIcons.sparkles,
                               size: Aero.scaledLength(18, relativeTo: .body),
                               color: Color(red: 0.29, green: 0.14, blue: 0.08))
                    // 12: the sheet's band is 8.50 pt against our 10.33 at 15.
                    Text("Generate")
                        .font(Aero.responsive(12, .semibold, relativeTo: .callout))
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
            DockIcon(gear: true, label: "Settings", action: onOpenSettings)
        }
        .padding(14)
        .gsCapsuleGlass(.floatingControl)
    }
}

// MARK: - Building blocks

private struct GlassCircleButton: View {
    let icon: GsIconPaths
    let label: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            GsIconView(icon,
                       size: Aero.scaledLength(17, relativeTo: .body),
                       color: Aero.text)
                .frame(width: Aero.scaledLength(46, relativeTo: .body),
                       height: Aero.scaledLength(46, relativeTo: .body))
                // Half the button, whatever size it scaled to. The literal 23 it
                // used to carry is 46/2 at the default font scale only: at 1.4x
                // the frame grows to 64pt and the "circle" became a rounded
                // square, which is a defect the corner can cause alone.
                .gsGlass(.floatingControl, corner: Aero.scaledLength(46, relativeTo: .body) / 2)
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityLabel(label)
    }
}

private struct HomePill: View {
    let icon: GsIconPaths
    let title: String
    var compact = false
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: compact ? 3 : 4) {
                GsIconView(icon,
                           size: Aero.scaledLength(compact ? 11 : 13, relativeTo: .caption),
                           color: Aero.text)
                Text(title)
                    .font(Aero.responsive(compact ? 11.5 : 13, .medium, relativeTo: .caption))
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
                // The authored pearl render from the reference, on a clear layer
                // rather than as a plain sibling. `frame(width: 200, height: 200)`
                // sets a MINIMUM of 200 as well as an ideal, so as a ZStack child it
                // made the card 200 pt wide inside the 186.9 pt slot the bento gave
                // it; the outer `.frame(width:)` then centred the 13 pt excess, and
                // 6.5 pt of glass and rim hung OUTSIDE the page's 20 pt margin on
                // each side. Measured on the capture: the sheet's card edge is at
                // 17.5 pt, Android's at 20.0, iOS's at 13.5. `Color.clear` is what
                // Android's Box already does — the card's size comes from the
                // constraint, the art overflows it and the clipShape trims it.
                Color.clear.overlay(alignment: .bottomTrailing) {
                    Image("GSHomeVideoArt")
                        .resizable()
                        // Fill the 200 pt box and trim the sides: the twin of
                        // Android's `ContentScale.Crop`, and the reason is the
                        // card's own colour. `scaledToFit()` letterboxes this
                        // 864x560 asset to 200x130, so 84 pt of the card's 214
                        // show only their base gradient — measured across four
                        // renders, that leaves the card 3.5 L lighter and a
                        // quarter short of the reference's saturation (chroma
                        // mean 17.2 against the reference's 20.5; filled, 22.1).
                        // `.clipped()` is what makes this a crop rather than an
                        // overflow: a SwiftUI frame does not clip by itself.
                        .scaledToFill()
                        .frame(width: 200, height: 200)
                        .clipped()
                        .offset(x: 16, y: 12)
                }
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
                        GsIconView(GsIcons.play,
                                   size: Aero.scaledLength(8, relativeTo: .caption2),
                                   color: Aero.text)
                        // 10 pt: the sheet's chip is 7.2 pt tall with its icon. 11 was
                        // 3.8 pt over; 13 (Aero.caption()) was the iOS half of the
                        // "Video 12 vs 13" pair a review listed.
                        Text("Video").font(Aero.responsive(10, .medium, relativeTo: .caption))
                    }
                    .foregroundStyle(Aero.text)
                    .padding(.horizontal, 9).padding(.vertical, 5)
                    .background(Capsule().fill(Color.white.opacity(0.45)))
                    Spacer().frame(height: 5)
                    Text("Create Stunning\nVideos with AI")
                        .font(Aero.responsive(16, .semibold, relativeTo: .title3))
                        .lineSpacing(-1)
                        .foregroundStyle(Aero.text)
                        .multilineTextAlignment(.leading)
                        // Both lines, always. The card is a fixed 214pt and the
                        // body copy below it already refuses to compress
                        // (`.fixedSize`), so when the row ran out of room SwiftUI
                        // took it out of whichever text would yield — and the
                        // headline yielded: the capture rendered "Create
                        // Stunning…" where the sheet and Android both read
                        // "Create Stunning / Videos with AI". A headline that
                        // truncates its own second line is the defect, not the
                        // space; the body can wrap to three lines, this cannot.
                        .fixedSize(horizontal: false, vertical: true)
                        .layoutPriority(1)
                    Text("Turn your ideas into beautiful videos in minutes.")
                        .font(Aero.responsive(12, .medium, relativeTo: .footnote))
                        .foregroundStyle(Aero.textMuted)
                        .fixedSize(horizontal: false, vertical: true)
                    Spacer()
                    ZStack {
                        // 37 pt, the sheet's disc (44 was 7 pt over). Android drew an
                    // outline glyph here while iOS drew SF's solid `play`; Android now
                    // draws a filled triangle of the sheet's 9.2 x 11.5 pt.
                    Circle().fill(Color.white.opacity(0.55)).frame(width: 37, height: 37)
                        // The family's filled triangle — Android draws this same
                        // 9 × 11 solid mark on its 37dp disc; the generated set
                        // carries only the outline `play`, so the silhouette is
                        // hand-drawn here exactly like OpenBookGlyph below.
                        FilledPlayGlyph()
                            .fill(Aero.text)
                            .frame(width: Aero.scaledLength(9, relativeTo: .body),
                                   height: Aero.scaledLength(11, relativeTo: .body))
                            .offset(x: 1)
                    }
                }
                .padding(14)
            }
            .clipShape(RoundedRectangle(cornerRadius: Aero.Radius.homeCard, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Aero.Radius.homeCard, style: .continuous)
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
                        .frame(width: Aero.scaledLength(38, relativeTo: .caption),
                               height: Aero.scaledLength(38, relativeTo: .caption))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Mind Guide")
                            .font(Aero.responsive(13, .semibold, relativeTo: .subheadline))
                            .foregroundStyle(Aero.text)
                            .lineLimit(1)
                        Text("Your personal\nclarity companion.")
                            .font(Aero.responsive(9.5, .medium, relativeTo: .caption))
                            .lineSpacing(1.5)
                            .foregroundStyle(Aero.textMuted)
                            // Three lines so a 360pt phone wraps "companion."
                            // rather than clipping it to "personal…".
                            .lineLimit(3)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 5)
                .padding(.vertical, 8)
                HStack {
                    Spacer()
                    GsIconView(GsIcons.chevronRight,
                               size: Aero.scaledLength(11, relativeTo: .caption2),
                               color: Aero.textMuted)
                        .padding(.trailing, 10)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .gsGlass(.cardLight, corner: Aero.Radius.homeCard)
        }
        .buttonStyle(KineticPressStyle())
    }
}

private struct ReflectionCard: View {
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            // Copy owns the layout and sits top-leading, exactly as the sheet
            // draws it; the lotus is a BACKGROUND of that copy rather than a
            // ZStack sibling. It used to be `ZStack(alignment: .bottomTrailing)`,
            // and that alignment applies to every child — so the title and body
            // were shoved into the bottom-trailing corner on top of the flower.
            VStack(alignment: .leading, spacing: 4) {
                // Title and chevron share a row: overlaying the glyph made it
                // graze the final "n" at 360pt. A 10pt inset leaves 112.5pt at
                // 393pt, and the sheet's 12.5pt title measures 112.2pt, so it
                // keeps the sheet's size there; 360pt takes the 10pt step.
                HStack(spacing: 6) {
                    Text("Today's reflection")
                        .font(Aero.responsive(12.5, .semibold, relativeTo: .subheadline))
                        .foregroundStyle(Aero.text)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                    Spacer(minLength: 0)
                    GsIconView(GsIcons.chevronRight,
                               size: Aero.scaledLength(12, relativeTo: .caption),
                               color: Aero.textMuted)
                }
                Text("Take a moment to check in with yourself.")
                    .font(Aero.responsive(10.5, .medium, relativeTo: .caption))
                    .lineSpacing(1.5)
                    .foregroundStyle(Aero.textMuted)
                    .lineLimit(3)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            // 10pt horizontal, matching Android, so the title keeps the sheet's
            // 12.5pt at 393pt instead of being scaled down by a 14pt inset.
            .padding(.horizontal, 10)
            .padding(.vertical, 14)
            .background(alignment: .bottomTrailing) {
                // The authored lotus from the reference — feathered edges.
                Image("GSHomeLotusNew")
                    .resizable()
                    .scaledToFit()
                    .frame(width: Aero.scaledLength(150, relativeTo: .body),
                           height: Aero.scaledLength(100, relativeTo: .body))
                    .offset(x: 4, y: 8)
                    .allowsHitTesting(false)
            }
            // gsGlass only backgrounds and overlays — it never clips. Without
            // this the lotus' +4/+8 offset drew square corners outside the
            // rounded card, over the "Today's Used" heading below.
            .clipShape(RoundedRectangle(cornerRadius: Aero.Radius.homeCard, style: .continuous))
            .gsGlass(.cardLight, corner: Aero.Radius.homeCard)
        }
        .buttonStyle(KineticPressStyle())
    }
}

private struct DockIcon: View {
    /// The sheet's Chats glyph is a filled OPEN book; SF only offers an open
    /// outline or a closed fill, so the Android silhouette is drawn here too.
    var book: Bool = false
    /// Home and Settings are solid in the sheet as well — same arrangement,
    /// same Android twins (`FilledHouse`, `FilledGear`).
    var house: Bool = false
    var gear: Bool = false
    let label: String
    var selected = false
    let action: () -> Void

    /// The ink and the well's size, named once. They used to be re-inferred
    /// inside every branch of the Group below; three shape fills x two ternary
    /// inks x two scaledLength frames in one expression is what the compiler's
    /// "unable to type-check this expression in reasonable time" named at
    /// run 40116679302 — each mark is its own `some View` since.
    private var ink: Color { selected ? Aero.text : Aero.text.opacity(0.78) }
    private var markSide: CGFloat { Aero.scaledLength(20, relativeTo: .body) }

    private var bookMark: some View {
        OpenBookGlyph()
            .fill(ink)
            .frame(width: markSide, height: markSide)
    }

    private var houseMark: some View {
        FilledHouseGlyph()
            .fill(ink)
            .frame(width: markSide, height: markSide)
    }

    private var gearMark: some View {
        FilledGearMark(color: ink, side: markSide)
    }

    var body: some View {
        Button(action: action) {
            Group {
                if book {
                    bookMark
                } else if house {
                    houseMark
                } else if gear {
                    gearMark
                }
            }
            .frame(width: Aero.scaledLength(46, relativeTo: .body),
                   height: Aero.scaledLength(46, relativeTo: .body))
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

/// Filled house, the twin of Android's `FilledHouse` in HomeDashboardScreen.kt
/// — the same 24-unit outline, cubic for cubic. The reference's dock is solid;
/// the generated Lucide set is stroke-only by contract, so this silhouette (and
/// its gear and play twins) lives with the page that draws it, as the book's
/// already does.
private struct FilledHouseGlyph: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 24
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x * s, y: y * s) }
        var path = Path()
        path.move(to: p(12.0, 3.1))
        path.addLine(to: p(2.7, 10.8))
        path.addCurve(to: p(3.1, 11.9), control1: p(2.3, 11.2), control2: p(2.5, 11.9))
        path.addLine(to: p(4.6, 11.9))
        path.addLine(to: p(4.6, 19.3))
        path.addCurve(to: p(5.8, 20.5), control1: p(4.6, 20.0), control2: p(5.1, 20.5))
        path.addLine(to: p(9.5, 20.5))
        path.addLine(to: p(9.5, 15.5))
        path.addCurve(to: p(10.4, 14.6), control1: p(9.5, 15.0), control2: p(9.9, 14.6))
        path.addLine(to: p(13.6, 14.6))
        path.addCurve(to: p(14.5, 15.5), control1: p(14.1, 14.6), control2: p(14.5, 15.0))
        path.addLine(to: p(14.5, 20.5))
        path.addLine(to: p(18.2, 20.5))
        path.addCurve(to: p(19.4, 19.3), control1: p(18.9, 20.5), control2: p(19.4, 20.0))
        path.addLine(to: p(19.4, 11.9))
        path.addLine(to: p(20.9, 11.9))
        path.addCurve(to: p(21.3, 10.8), control1: p(21.5, 11.9), control2: p(21.7, 11.2))
        path.closeSubpath()
        return path
    }
}

/// Filled gear with an even-odd hole — Android's `FilledGear`, command for
/// command, including the open second subpath Android leaves unclosed. The
/// geometry is authored in the 24-unit space, as every family glyph is.
private func gearGeometry(_ path: inout Path) {
    func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x, y: y) }
    path.move(to: p(18.79, 9.6))
    path.addLine(to: p(19.02, 10.38))
    path.addLine(to: p(21.28, 10.53))
    path.addLine(to: p(21.28, 13.47))
    path.addLine(to: p(19.02, 13.62))
    path.addLine(to: p(18.79, 14.4))
    path.addLine(to: p(18.5, 15.1))
    path.addLine(to: p(18.11, 15.82))
    path.addLine(to: p(19.6, 17.53))
    path.addLine(to: p(17.53, 19.6))
    path.addLine(to: p(15.82, 18.11))
    path.addLine(to: p(15.1, 18.5))
    path.addLine(to: p(14.4, 18.79))
    path.addLine(to: p(13.62, 19.02))
    path.addLine(to: p(13.47, 21.28))
    path.addLine(to: p(10.53, 21.28))
    path.addLine(to: p(10.38, 19.02))
    path.addLine(to: p(9.6, 18.79))
    path.addLine(to: p(8.9, 18.5))
    path.addLine(to: p(8.18, 18.11))
    path.addLine(to: p(6.47, 19.6))
    path.addLine(to: p(4.4, 17.53))
    path.addLine(to: p(5.89, 15.82))
    path.addLine(to: p(5.5, 15.1))
    path.addLine(to: p(5.21, 14.4))
    path.addLine(to: p(4.98, 13.62))
    path.addLine(to: p(2.72, 13.47))
    path.addLine(to: p(2.72, 10.53))
    path.addLine(to: p(4.98, 10.38))
    path.addLine(to: p(5.21, 9.6))
    path.addLine(to: p(5.5, 8.9))
    path.addLine(to: p(5.89, 8.18))
    path.addLine(to: p(4.4, 6.47))
    path.addLine(to: p(6.47, 4.4))
    path.addLine(to: p(8.18, 5.89))
    path.addLine(to: p(8.9, 5.5))
    path.addLine(to: p(9.6, 5.21))
    path.addLine(to: p(10.38, 4.98))
    path.addLine(to: p(10.53, 2.72))
    path.addLine(to: p(13.47, 2.72))
    path.addLine(to: p(13.62, 4.98))
    path.addLine(to: p(14.4, 5.21))
    path.addLine(to: p(15.1, 5.5))
    path.addLine(to: p(15.82, 5.89))
    path.addLine(to: p(17.53, 4.4))
    path.addLine(to: p(19.6, 6.47))
    path.addLine(to: p(18.11, 8.18))
    path.addLine(to: p(18.5, 8.9))
    path.closeSubpath()
    path.move(to: p(15.1, 12))
    path.addLine(to: p(14.68, 10.45))
    path.addLine(to: p(13.55, 9.32))
    path.addLine(to: p(12, 8.9))
    path.addLine(to: p(10.45, 9.32))
    path.addLine(to: p(9.32, 10.45))
    path.addLine(to: p(8.9, 12))
    path.addLine(to: p(9.32, 13.55))
    path.addLine(to: p(10.45, 14.68))
    path.addLine(to: p(12, 15.1))
    path.addLine(to: p(13.55, 14.68))
    path.addLine(to: p(14.68, 13.55))
}

/// The gear drawn through a Canvas, not a Shape.fill. Even-odd is the twin of
/// Compose's PathFillType.EvenOdd — the ring and the hub wind the same way, so
/// the nonzero rule would fill the hole solid — and the context's fill is the
/// one API on this SDK that takes `FillStyle(eo:)`: run 40117076973 rejected
/// `eo:` on Shape.fill, run 40117461200 rejected `isEOFilled:` there too, and
/// `PathFillStyle` is iOS 17 while the page ships at iOS 16. GsIconView draws
/// through the same Canvas for the same reason.
private struct FilledGearMark: View {
    let color: Color
    let side: CGFloat
    var body: some View {
        Canvas { context, size in
            let scale = min(size.width, size.height) / 24
            context.scaleBy(x: scale, y: scale)
            var path = Path()
            gearGeometry(&path)
            context.fill(path, with: .color(color), style: FillStyle(eo: true))
        }
        .frame(width: side, height: side)
    }
}

/// The video card's play mark: a solid triangle, the twin of the 9 × 11 Canvas
/// path Android draws on its 37dp disc. `play` in the family is the outline;
/// the sheet's disc is filled, and an outline on 37pt of glass reads as a ring.
private struct FilledPlayGlyph: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.midY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
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
