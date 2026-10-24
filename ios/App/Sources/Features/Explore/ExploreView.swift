import SwiftUI

// MARK: - Explore AI — the discover page
//
// Matches the Explore reference pair: pearl backdrop, luminous glass panels,
// the featured hero with its Discover chip and Explore All door, category
// chips, a live search field, Featured Tools, Popular prompt rows, Creative
// Collections and the featured journey card. No bottom navigation bar — back
// is the stack's edge swipe, and everything on the page opens a REAL
// conversation through `.chatPrefill` / `.chatAutoSend`, the same dispatch
// path Home's quick pills use. Palette pixel-sampled from the references.

/// One discoverable capability — the mirror of the Android catalog.
struct ExploreTool: Identifiable, Hashable {
    let id: String
    let title: String
    let subtitle: String
    let glyph: String
    let bg: Color
    let fg: Color
    let category: ExploreCategory
    /// Seeds the conversation this card opens — never a dead end.
    let prompt: String
}

enum ExploreCategory: String, CaseIterable, Identifiable {
    case all = "All"
    case productivity = "Productivity"
    case creativity = "Creativity"
    case learning = "Learning"
    case lifestyle = "Lifestyle"

    var id: String { rawValue }
}

enum ExploreCatalog {
    static let tools: [ExploreTool] = [
        ExploreTool(
            id: "image-studio", title: "Image Studio",
            subtitle: "Create stunning images with AI",
            glyph: "photo", bg: ExplorePalette.pinkBg, fg: ExplorePalette.pinkFg,
            category: .creativity,
            prompt: "Act as my image studio director. Ask me the subject, mood and style one question at a time, then write three detailed image prompts I can paste into any image generator."
        ),
        ExploreTool(
            id: "video-generator", title: "Video Generator",
            subtitle: "Turn ideas into videos",
            glyph: "player-play", bg: ExplorePalette.lavenderBg, fg: ExplorePalette.lavenderFg,
            category: .creativity,
            prompt: "Help me plan a short video. Ask what it is for and who watches it, then give me a shot-by-shot outline with narration lines and on-screen text for each scene."
        ),
        ExploreTool(
            id: "voice-assistant", title: "Voice Assistant",
            subtitle: "Natural, human-like voices",
            glyph: "microphone", bg: ExplorePalette.greenBg, fg: ExplorePalette.greenFg,
            category: .productivity,
            prompt: "Be my speaking coach. We'll practise a short introduction out loud: I'll type what I want to say, you tighten it, then give me feedback on clarity and tone."
        ),
        ExploreTool(
            id: "code-assistant", title: "Code Assistant",
            subtitle: "Write, debug, and build faster",
            glyph: "code", bg: ExplorePalette.apricotBg, fg: ExplorePalette.apricotFg,
            category: .productivity,
            prompt: "Be my pair programmer. Ask what I'm building and which language, then help me write and debug it step by step, explaining every fix."
        ),
        ExploreTool(
            id: "writing-assistant", title: "Writing Assistant",
            subtitle: "Craft, improve and perfect your text",
            glyph: "file-text", bg: ExplorePalette.blueBg, fg: ExplorePalette.blueFg,
            category: .productivity,
            prompt: "Be my editor. I'll paste text; you keep my voice, fix grammar and flow, and show me a short list of what you changed and why."
        ),
        ExploreTool(
            id: "study-plan", title: "Create a study plan",
            subtitle: "Personalized learning plan for your goals",
            glyph: "school", bg: ExplorePalette.lavenderBg, fg: ExplorePalette.lavenderFg,
            category: .learning,
            prompt: "Build me a personalized study plan. Ask my subject, deadline and weekly hours first, then give a day-by-day plan with one review session per week."
        ),
        ExploreTool(
            id: "logo-maker", title: "Generate a logo",
            subtitle: "Turn your idea into a unique logo",
            glyph: "brush", bg: ExplorePalette.apricotBg, fg: ExplorePalette.apricotFg,
            category: .creativity,
            prompt: "Help me design a logo. Ask about the name, feeling and where it will live, then propose three concepts described precisely enough to brief a designer or generator."
        ),
        ExploreTool(
            id: "trip-planner", title: "Plan a trip",
            subtitle: "Get a custom travel itinerary",
            glyph: "plane", bg: ExplorePalette.greenBg, fg: ExplorePalette.greenFg,
            category: .lifestyle,
            prompt: "Plan a trip with me. Ask the destination, dates and pace, then build a day-by-day itinerary with one highlight, one meal and one rest stop per day."
        ),
        ExploreTool(
            id: "doc-summary", title: "Summarize a document",
            subtitle: "Save time with AI summaries",
            glyph: "clipboard-text", bg: ExplorePalette.blueBg, fg: ExplorePalette.blueFg,
            category: .learning,
            prompt: "Summarize documents for me. I'll paste or attach one; give a five-bullet summary, the key numbers, and three questions I should ask about it."
        ),
        ExploreTool(
            id: "meal-planner", title: "Weekly meal planner",
            subtitle: "Eat well without the guesswork",
            glyph: "salad", bg: ExplorePalette.greenBg, fg: ExplorePalette.greenFg,
            category: .lifestyle,
            prompt: "Be my meal planner. Ask dietary needs and cooking time per day, then write a seven-day plan with one shared grocery list."
        ),
    ]

    struct Collection: Identifiable, Hashable {
        let id = UUID()
        let title: String
        let subtitle: String
        let category: ExploreCategory
    }

    static let collections: [Collection] = [
        Collection(title: "Design & Visuals", subtitle: "For creators & dreamers", category: .creativity),
        Collection(title: "Work & Productivity", subtitle: "Work smarter, not harder", category: .productivity),
        Collection(title: "Learning & Growth", subtitle: "Build new skills", category: .learning),
    ]

    static let featured = ExploreTool(
        id: "kyoto-weekend", title: "Weekend in Kyoto",
        subtitle: "A creative journey through Japan's timeless beauty",
        glyph: "sparkles", bg: ExplorePalette.apricotBg, fg: ExplorePalette.apricotFg,
        category: .lifestyle,
        prompt: "Plan a creative weekend in Kyoto. Ask my pace and interests, then give me one day per page: morning temple light, one craft workshop, one quiet meal, and one place locals actually go."
    )

    static func byCategory(_ category: ExploreCategory) -> [ExploreTool] {
        category == .all ? tools : tools.filter { $0.category == category }
    }

    static func search(_ query: String) -> [ExploreTool] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !q.isEmpty else { return tools }
        return tools.filter {
            $0.title.lowercased().contains(q)
                || $0.subtitle.lowercased().contains(q)
                || $0.category.rawValue.lowercased().contains(q)
        }
    }
}

/// Palette pixel-sampled from the Explore references (2026-10-24).
enum ExplorePalette {
    static let pinkBg = Color(red: 0.984, green: 0.843, blue: 0.906)
    static let lavenderBg = Color(red: 0.871, green: 0.847, blue: 0.992)
    static let greenBg = Color(red: 0.792, green: 0.922, blue: 0.831)
    static let apricotBg = Color(red: 0.996, green: 0.875, blue: 0.773)
    static let blueBg = Color(red: 0.839, green: 0.902, blue: 0.988)

    static let pinkFg = Color(red: 0.769, green: 0.200, blue: 0.400)
    static let lavenderFg = Color(red: 0.353, green: 0.314, blue: 0.839)
    static let greenFg = Color(red: 0.090, green: 0.478, blue: 0.314)
    static let apricotFg = Color(red: 0.722, green: 0.361, blue: 0.071)
    static let blueFg = Color(red: 0.086, green: 0.388, blue: 0.769)

    static let peach = Color(red: 0.996, green: 0.706, blue: 0.490)
    static let peachDeep = Color(red: 0.996, green: 0.769, blue: 0.576)
    static let peachPale = Color(red: 0.996, green: 0.875, blue: 0.773)
    static let ctaTop = Color(red: 1.0, green: 0.914, blue: 0.863)
    static let ctaBottom = Color(red: 0.996, green: 0.827, blue: 0.753)

    static let discoverBg = Color(red: 0.984, green: 0.902, blue: 0.961)
    static let discoverInk = Color(red: 0.753, green: 0.267, blue: 0.494)

    static let inkStrong = Color(red: 0.094, green: 0.110, blue: 0.188)
    static let inkHeader = Color(red: 0.169, green: 0.173, blue: 0.216)
    static let inkBody = Color(red: 0.290, green: 0.294, blue: 0.333)
    static let inkMuted = Color(red: 0.553, green: 0.553, blue: 0.584)
}

struct ExploreView: View {
    /// `.chatPrefill` when a card seeds a draft; `.chatAutoSend` when the
    /// composer sends typed words.
    let onOpenChat: (AeroRoute) -> Void

    /// Render-harness entry points: the real states, driven from outside.
    var initialQuery: String = ""
    var initialSearchOpen: Bool = false

    @State private var query: String
    @State private var searchOpen: Bool
    @State private var category: ExploreCategory = .all
    @State private var showAllTools: Bool = false

    init(
        onOpenChat: @escaping (AeroRoute) -> Void,
        initialQuery: String = "",
        initialSearchOpen: Bool = false
    ) {
        self.onOpenChat = onOpenChat
        self.initialQuery = initialQuery
        self.initialSearchOpen = initialSearchOpen
        _query = State(initialValue: initialQuery)
        _searchOpen = State(initialValue: initialSearchOpen)
    }

    var body: some View {
        ZStack {
            Aero.background
            VStack(spacing: 0) {
                header
                if searchOpen { searchField.transition(.opacity.combined(with: .move(edge: .top))) }
                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 14) {
                        if !query.trimmingCharacters(in: .whitespaces).isEmpty {
                            resultsSection
                        } else {
                            heroCard
                            chips
                            toolsSection
                            popularSection
                            collectionsSection
                            featuredSection
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                    .padding(.bottom, 18)
                }
                composer
            }
        }
    }

    // MARK: header — orb + product + the search circle

    private var header: some View {
        HStack(spacing: 10) {
            Image("GSCompanionOrb")
                .resizable()
                .scaledToFill()
                .frame(width: 38, height: 38)
                .clipShape(Circle())
                .overlay(Circle().strokeBorder(.white.opacity(0.6), lineWidth: 1))
            VStack(alignment: .leading, spacing: 0) {
                Text("Nexly")
                    .font(.custom(Aero.productName(.bold), size: 16.5, relativeTo: .title3))
                    .foregroundStyle(Aero.text)
                Text("AI Companion")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
            }
            Spacer()
            Button {
                withAnimation(.easeOut(duration: 0.2)) {
                    searchOpen.toggle()
                    if !searchOpen { query = "" }
                }
            } label: {
                NexlyGlyph(name: "search", size: 18, color: Aero.text)
                    .frame(width: 40, height: 40)
                    .background(Circle().fill(Aero.glassSurface))
                    .overlay(Circle().strokeBorder(.white.opacity(0.6), lineWidth: 1))
            }
            .accessibilityLabel(searchOpen ? "Close search" : "Open search")
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
        .padding(.bottom, 6)
    }

    private var searchField: some View {
        HStack(spacing: 8) {
            NexlyGlyph(name: "search", size: 15, color: Aero.textMuted)
            TextField("Search tools and abilities", text: $query)
                .font(Aero.bodyMedium())
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
                .accessibilityIdentifier("explore-search-input")
            if !query.isEmpty {
                Button {
                    query = ""
                } label: {
                    NexlyGlyph(name: "x", size: 14, color: Aero.textMuted)
                }
                .accessibilityLabel("Clear search")
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 11)
        .background(Capsule().fill(Aero.glassSurface))
        .overlay(Capsule().strokeBorder(.white.opacity(0.55), lineWidth: 1))
        .padding(.horizontal, 20)
        .padding(.bottom, 4)
    }

    // MARK: hero

    private var heroCard: some View {
        AeroCard {
            // The art is a pure overlay: a luminous glass bubble drawn
            // procedurally (the GsArtwork technique) over the card's right
            // side. Overlay never participates in layout, so the copy column
            // wraps exactly like the Android card and the card height is the
            // content's height.
            VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 5) {
                        NexlyGlyph(name: "sparkles", size: 11, color: ExplorePalette.discoverInk)
                        Text("Discover")
                            .font(Aero.caption())
                            .foregroundStyle(ExplorePalette.discoverInk)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Capsule().fill(ExplorePalette.discoverBg))

                    Text("Explore AI\nPossibilities")
                        .font(.custom(Aero.productName(.bold), size: 22, relativeTo: .title2))
                        .foregroundStyle(Aero.text)
                        .lineSpacing(2)

                    Text("Powerful tools, creative features, and more ways to bring your ideas to life — all in one place.")
                        .font(Aero.bodyMedium())
                        .foregroundStyle(Aero.textMuted)
                        .frame(maxWidth: 215, alignment: .leading)

                    Button {
                        category = .all
                        showAllTools = true
                    } label: {
                        HStack(spacing: 8) {
                            Text("Explore All")
                                .font(Aero.bodyMedium().weight(.semibold))
                                .foregroundStyle(Aero.text)
                            NexlyGlyph(name: "arrow-right", size: 13, color: Aero.text)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Capsule().fill(LinearGradient(colors: [ExplorePalette.ctaTop, ExplorePalette.ctaBottom], startPoint: .leading, endPoint: .trailing)))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Explore all tools")
                }
                .padding(18)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .frame(maxWidth: .infinity)
            .overlay(alignment: .trailing) {
                // The reference's OWN iridescent bubbles — cropped from the
                // authored Explore mock, shipped as GSExploreHero — carry the
                // card's right side; the scrim fades them into the pearl so
                // the copy sits on quiet ground.
                Image("GSExploreHero")
                    .resizable()
                    .scaledToFill()
                    .frame(maxWidth: 240)
                    .frame(maxHeight: .infinity)
                    .clipped()
                    .overlay(
                        LinearGradient(
                            stops: [
                                .init(color: Color(red: 0.992, green: 0.945, blue: 0.925).opacity(0.80), location: 0),
                                .init(color: Color(red: 0.992, green: 0.945, blue: 0.925).opacity(0.10), location: 0.42),
                                .init(color: .clear, location: 1),
                            ],
                            startPoint: .leading, endPoint: .trailing
                        )
                    )
                    .allowsHitTesting(false)
            }
    }

    // MARK: chips

    private var chips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(ExploreCategory.allCases) { cat in
                    let active = cat == category
                    Button {
                        category = cat
                    } label: {
                        Text(cat.rawValue)
                            .font(Aero.bodyMedium())
                            .foregroundStyle(active ? Color.white : Aero.textMuted)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(
                                Group {
                                    if active {
                                        Capsule().fill(LinearGradient(colors: [ExplorePalette.peach, ExplorePalette.peachDeep], startPoint: .leading, endPoint: .trailing))
                                    } else {
                                        Capsule().fill(Aero.glassSurface)
                                    }
                                }
                            )
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(cat.rawValue)
                }
            }
            .padding(.vertical, 2)
        }
    }

    // MARK: sections

    private func sectionHeader(_ title: String, action: (() -> Void)? = nil, actionLabel: String = "See All") -> some View {
        HStack {
            Text(title)
                .font(Aero.headline())
                .foregroundStyle(Aero.text)
            Spacer()
            if let action {
                Button(action: action) {
                    HStack(spacing: 4) {
                        Text(actionLabel).font(Aero.caption()).foregroundStyle(Aero.textMuted)
                        NexlyGlyph(name: "arrow-right", size: 11, color: Aero.textMuted)
                    }
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var toolsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader("Featured Tools", action: { showAllTools.toggle() }, actionLabel: showAllTools ? "Show less" : "See All")
            let tools = showAllTools ? ExploreCatalog.byCategory(category) : Array(ExploreCatalog.byCategory(category).prefix(4))
            ForEach(0..<tools.count, id: \.self) { i in
                if i % 2 == 0, i < tools.count {
                    HStack(alignment: .top, spacing: 12) {
                        toolCard(tools[i])
                        if i + 1 < tools.count {
                            toolCard(tools[i + 1])
                        } else {
                            Spacer().frame(maxWidth: .infinity)
                        }
                    }
                }
            }
        }
    }

    private func toolCard(_ tool: ExploreTool) -> some View {
        Button {
            onOpenChat(.chatPrefill(tool.prompt))
        } label: {
            VStack(alignment: .leading, spacing: 8) {
                Circle()
                    .fill(tool.bg)
                    .frame(width: 46, height: 46)
                    .overlay(NexlyGlyph(name: tool.glyph, size: 19, color: tool.fg))
                Text(tool.title)
                    .font(Aero.bodyMedium().weight(.semibold))
                    .foregroundStyle(Aero.text)
                    .lineLimit(1)
                Text(tool.subtitle)
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
                    .lineLimit(2)
                    .frame(minHeight: 30, alignment: .top)
                HStack {
                    Spacer()
                    NexlyGlyph(name: "arrow-right", size: 13, color: Aero.textMuted)
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 20).fill(Aero.glassSurface))
            .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(.white.opacity(0.5), lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(tool.title): \(tool.subtitle)")
    }

    private var popularSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader("Popular")
            let rows = ExploreCatalog.tools.filter { ["study-plan", "logo-maker", "trip-planner", "doc-summary"].contains($0.id) }
                .filter { category == .all || $0.category == category }
            VStack(spacing: 8) {
                ForEach(rows) { tool in
                    promptRow(tool)
                }
            }
        }
    }

    private func promptRow(_ tool: ExploreTool) -> some View {
        Button {
            onOpenChat(.chatPrefill(tool.prompt))
        } label: {
            HStack(spacing: 12) {
                Circle()
                    .fill(tool.bg)
                    .frame(width: 40, height: 40)
                    .overlay(NexlyGlyph(name: tool.glyph, size: 17, color: tool.fg))
                VStack(alignment: .leading, spacing: 2) {
                    Text(tool.title)
                        .font(Aero.bodyMedium().weight(.semibold))
                        .foregroundStyle(Aero.text)
                        .lineLimit(1)
                    Text(tool.subtitle)
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                        .lineLimit(1)
                }
                Spacer()
                NexlyGlyph(name: "chevron-right", size: 14, color: Aero.textMuted)
            }
            .padding(12)
            .background(RoundedRectangle(cornerRadius: 18).fill(Aero.glassSurface))
            .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(.white.opacity(0.5), lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(tool.title): \(tool.subtitle)")
    }

    private var collectionsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader("Creative Collections")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(ExploreCatalog.collections) { collection in
                        Button {
                            category = collection.category
                            showAllTools = false
                        } label: {
                            VStack(alignment: .leading, spacing: 0) {
                                // Each collection carries its own scene, like
                                // the reference set: the lotus for Design, the
                                // pearl-drape scene for Work, the bubble
                                // close-up for Learning.
                                collectionArt(collection)
                                    .frame(height: 86)
                                    .frame(maxWidth: .infinity)
                                    .clipped()
                                    .overlay(LinearGradient(colors: [.white.opacity(0.08), .white.opacity(0.38)], startPoint: .top, endPoint: .bottom))
                                    .allowsHitTesting(false)
                                VStack(alignment: .leading, spacing: 2) {
                                    HStack {
                                        Text(collection.title)
                                            .font(Aero.bodyMedium().weight(.semibold))
                                            .foregroundStyle(Aero.text)
                                            .lineLimit(1)
                                        Spacer()
                                        NexlyGlyph(name: "arrow-right", size: 12, color: Aero.textMuted)
                                    }
                                    Text(collection.subtitle)
                                        .font(Aero.caption())
                                        .foregroundStyle(Aero.textMuted)
                                        .lineLimit(1)
                                }
                                .padding(.horizontal, 12)
                                .padding(.vertical, 10)
                            }
                            .frame(width: 168)
                            .background(RoundedRectangle(cornerRadius: 20).fill(Aero.glassSurface))
                            .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(.white.opacity(0.5), lineWidth: 1))
                            .clipShape(RoundedRectangle(cornerRadius: 20))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("\(collection.title): \(collection.subtitle)")
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func collectionArt(_ collection: ExploreCatalog.Collection) -> some View {
        // Each scene is cropped from the authored Explore mock: the lotus
        // for Design, the misty pagoda landscape for Work, the orange lotus
        // close-up for Learning.
        switch collection.subtitle {
        case "Work smarter, not harder":
            Image("GSExploreColWork").resizable().scaledToFill()
        case "Build new skills":
            Image("GSExploreColLearning").resizable().scaledToFill()
        default:
            Image("GSExploreColDesign").resizable().scaledToFill()
        }
    }

    private var featuredSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader("Featured")
            Button {
                onOpenChat(.chatPrefill(ExploreCatalog.featured.prompt))
            } label: {
                ZStack(alignment: .trailing) {
                // The reference's featured card: the scene fills the card's
                // right side under a pearl veil, words on the left.
                GeometryReader { geo in
                    HStack(spacing: 0) {
                        Spacer(minLength: 0)
                        Image("GSExploreKyoto")
                            .resizable()
                            .scaledToFill()
                            .frame(width: geo.size.width * 0.46)
                            .clipped()
                            .overlay(
                                LinearGradient(
                                    stops: [
                                        .init(color: Color(red: 0.984, green: 0.929, blue: 0.906).opacity(0.88), location: 0),
                                        .init(color: Color(red: 0.984, green: 0.929, blue: 0.906).opacity(0.18), location: 0.55),
                                        .init(color: .clear, location: 1),
                                    ],
                                    startPoint: .leading, endPoint: .trailing
                                )
                            )
                            .allowsHitTesting(false)
                    }
                }
                HStack(spacing: 12) {
                    Circle()
                        .fill(ExploreCatalog.featured.bg)
                        .frame(width: 40, height: 40)
                        .overlay(NexlyGlyph(name: ExploreCatalog.featured.glyph, size: 17, color: ExploreCatalog.featured.fg))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(ExploreCatalog.featured.title)
                            .font(Aero.body().weight(.semibold))
                            .foregroundStyle(Aero.text)
                        Text(ExploreCatalog.featured.subtitle)
                            .font(Aero.caption())
                            .foregroundStyle(Aero.textMuted)
                            .lineLimit(2)
                    }
                    Spacer()
                    Circle()
                        .fill(.white.opacity(0.55))
                        .frame(width: 30, height: 30)
                        .overlay(NexlyGlyph(name: "chevron-right", size: 14, color: Aero.text))
                }
                .padding(12)
            }
                .background(RoundedRectangle(cornerRadius: 20).fill(Aero.glassSurface))
                .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(.white.opacity(0.5), lineWidth: 1))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(ExploreCatalog.featured.title): \(ExploreCatalog.featured.subtitle)")
        }
    }

    // MARK: results (live search)

    private var resultsSection: some View {
        let results = ExploreCatalog.search(query)
        return VStack(alignment: .leading, spacing: 12) {
            sectionHeader("Results")
            if results.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text("No tool matches \"\(query.trimmingCharacters(in: .whitespaces))\" yet")
                        .font(Aero.bodyMedium().weight(.semibold))
                        .foregroundStyle(Aero.text)
                    Text("I can still help — ask me directly and we'll figure it out together.")
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                    Button {
                        onOpenChat(.chatAutoSend(query.trimmingCharacters(in: .whitespaces)))
                    } label: {
                        HStack(spacing: 6) {
                            Text("Ask about \"\(query.trimmingCharacters(in: .whitespaces))\"")
                                .font(Aero.caption().weight(.semibold))
                                .foregroundStyle(Aero.text)
                                .lineLimit(1)
                            NexlyGlyph(name: "arrow-right", size: 12, color: Aero.text)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 9)
                        .background(Capsule().fill(LinearGradient(colors: [ExplorePalette.ctaTop, ExplorePalette.ctaBottom], startPoint: .leading, endPoint: .trailing)))
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 6)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(18)
                .background(RoundedRectangle(cornerRadius: 20).fill(Aero.glassSurface))
                .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(.white.opacity(0.5), lineWidth: 1))
            } else {
                VStack(spacing: 8) {
                    ForEach(results) { tool in
                        promptRow(tool)
                    }
                }
            }
        }
    }

    // MARK: composer — sends typed words into a fresh conversation

    private var composer: some View {
        ExploreComposerBar { text in
            onOpenChat(.chatAutoSend(text))
        }
    }
}

/// The "Ask me anything…" bar from the reference: a real field with a real send.
struct ExploreComposerBar: View {
    let onSend: (String) -> Void
    @State private var draft: String = ""
    @FocusState private var focused: Bool

    private var canSend: Bool {
        !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        HStack(spacing: 8) {
            NexlyGlyph(name: "sparkles", size: 16, color: Aero.textMuted)
            TextField("Ask me anything…", text: $draft)
                .font(Aero.bodyMedium())
                .focused($focused)
                .submitLabel(.send)
                .onSubmit(send)
                .accessibilityIdentifier("explore-composer-input")
            Button(action: send) {
                NexlyGlyph(name: "send", size: 17, color: canSend ? .white : Aero.textMuted)
                    .frame(width: 42, height: 42)
                    .background(
                        Circle().fill(
                            LinearGradient(
                                colors: [ExplorePalette.peachPale, ExplorePalette.peach],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                    )
            }
            .buttonStyle(.plain)
            .disabled(!canSend)
            .accessibilityLabel("Send message")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(Capsule().fill(Aero.glassSurface))
        .overlay(Capsule().strokeBorder(.white.opacity(0.55), lineWidth: 1))
        .padding(.horizontal, 16)
        .padding(.top, 4)
        .padding(.bottom, 10)
    }

    private func send() {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        onSend(text)
        draft = ""
        focused = false
    }
}
