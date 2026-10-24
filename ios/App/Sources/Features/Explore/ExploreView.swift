import SwiftUI

// MARK: - Explore + Discover — TWO pages, matching the two authored mocks
//
// EXPLORE (the mock with Popular Tools / Creative Collections / Featured):
// the featured hero, the Popular Tools grid, Creative Collections and the
// Featured journey card. No chips, no composer, no bottom navigation.
//
// DISCOVER (the mock with the EXPLORE hero / category chips / composer):
// the "Discover what you can do with Nexly" hero, category chips, Featured
// Tools, Popular prompt rows and the Ask-me-anything composer.
//
// Every card opens a REAL conversation through `.chatPrefill` /
// `.chatAutoSend` — the same dispatch path Home's quick pills use. The
// artwork is authored vector art; every panel carries the mock's rich
// glass: gradient fill, luminous rim, warm shadow.

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
    /// The reference paints some tiles as saturated discs with white glyphs.
    var filledTile: Bool = false
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
            filledTile: true,
            prompt: "Help me plan a short video. Ask what it is for and who watches it, then give me a shot-by-shot outline with narration lines and on-screen text for each scene."
        ),
        ExploreTool(
            id: "voice-assistant", title: "Voice Assistant",
            subtitle: "Natural, human-like voices",
            glyph: "chart-bar", bg: ExplorePalette.greenBg, fg: ExplorePalette.greenFg,
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
            glyph: "file-description", bg: ExplorePalette.blueBg, fg: ExplorePalette.blueFg,
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

private struct ExploreGlass: ViewModifier {
    var corner: CGFloat
    var elevation: CGFloat = 12

    func body(content: Content) -> some View {
        content
            .shadow(
                color: Color(red: 0.886, green: 0.612, blue: 0.486).opacity(0.33),
                radius: elevation, x: 0, y: elevation * 0.5
            )
            .background(
                RoundedRectangle(cornerRadius: corner)
                    .fill(
                        LinearGradient(
                            colors: [
                                Color(red: 1.0, green: 0.980, blue: 0.965).opacity(0.97),
                                Color(red: 0.988, green: 0.929, blue: 0.898).opacity(0.88),
                            ],
                            startPoint: .topLeading, endPoint: .bottomTrailing
                        )
                    )
            )
            .overlay(
                RoundedRectangle(cornerRadius: corner)
                    .strokeBorder(
                        LinearGradient(
                            colors: [.white.opacity(0.95), .white.opacity(0.30)],
                            startPoint: .topLeading, endPoint: .bottomTrailing
                        ),
                        lineWidth: 1
                    )
            )
            .clipShape(RoundedRectangle(cornerRadius: corner))
    }
}

private struct IconCircleStyle: ViewModifier {
    var bg: Color
    var diameter: CGFloat
    var solidTile: Bool = false

    func body(content: Content) -> some View {
        content
            .frame(width: diameter, height: diameter)
            .background(
                Circle().fill(
                    solidTile
                        ? AnyShapeStyle(bg)
                        : AnyShapeStyle(LinearGradient(
                            colors: [ExplorePalette.iconLight(bg), bg],
                            startPoint: .top, endPoint: .bottom
                        ))
                )
            )
            .overlay(
                Circle().strokeBorder(
                    LinearGradient(
                        colors: [.white.opacity(0.95), .white.opacity(0.40)],
                        startPoint: .top, endPoint: .bottom
                    ),
                    lineWidth: 1.2
                )
            )
            .shadow(color: Color(red: 0.886, green: 0.612, blue: 0.486).opacity(0.30), radius: 7, x: 0, y: 4)
    }
}

private extension ExplorePalette {
    static func iconLight(_ c: Color) -> Color {
        let ui = UIColor(c)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        ui.getRed(&r, green: &g, blue: &b, alpha: &a)
        return Color(
            red: min(1, r * 0.55 + 0.45),
            green: min(1, g * 0.55 + 0.45),
            blue: min(1, b * 0.55 + 0.45)
        )
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
                ZStack {
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [Color(red: 1.0, green: 0.867, blue: 0.722), ExplorePalette.peach],
                                startPoint: .topLeading, endPoint: .bottomTrailing
                            )
                        )
                    NexlyGlyph(name: "send", size: 15, color: canSend ? .white : Aero.textMuted)
                }
                .frame(width: 38, height: 38)
                .overlay(
                    Circle().strokeBorder(
                        LinearGradient(colors: [.white.opacity(0.90), .white.opacity(0.40)], startPoint: .topLeading, endPoint: .bottomTrailing),
                        lineWidth: 1.4
                    )
                )
                .shadow(color: Color(red: 0.922, green: 0.616, blue: 0.447).opacity(0.40), radius: 10, x: 0, y: 5)
            }
            .buttonStyle(.plain)
            .disabled(!canSend)
            .accessibilityLabel("Send message")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .modifier(ExploreGlass(corner: 26, elevation: 14))
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



/// Section title + optional See All — shared by both pages.
private struct ExploreSectionHeader: View {
    let title: String
    var action: (() -> Void)? = nil

    var body: some View {
        HStack {
            Text(title)
                .font(Aero.title())
                .foregroundStyle(Aero.text)
            Spacer()
            if let action {
                Button(action: action) {
                    HStack(spacing: 4) {
                        Text("See All").font(Aero.caption()).foregroundStyle(Aero.textMuted)
                        NexlyGlyph(name: "arrow-right", size: 11, color: Aero.textMuted)
                    }
                }
                .buttonStyle(.plain)
            }
        }
    }
}

// MARK: - EXPLORE — the mock with Popular Tools / Collections / Featured

struct ExploreView: View {
    let onOpenChat: (AeroRoute) -> Void
    let onOpenDiscover: () -> Void

    @State private var query = ""
    @State private var searchOpen = false

    init(
        onOpenChat: @escaping (AeroRoute) -> Void,
        onOpenDiscover: @escaping () -> Void = {},
        initialSearchOpen: Bool = false
    ) {
        self.onOpenChat = onOpenChat
        self.onOpenDiscover = onOpenDiscover
        _searchOpen = State(initialValue: initialSearchOpen)
    }

    var body: some View {
        ZStack {
            Image("GSExploreBackdrop")
                .resizable()
                .scaledToFill()
                .ignoresSafeArea()
                .allowsHitTesting(false)
            VStack(spacing: 0) {
                header
                if searchOpen { searchField.transition(.opacity.combined(with: .move(edge: .top))) }
                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 12) {
                        if !query.trimmingCharacters(in: .whitespaces).isEmpty {
                            resultsSection
                        } else {
                            heroCard
                            ExploreSectionHeader(title: "Popular Tools", action: onOpenDiscover())
                            toolsGrid
                            ExploreSectionHeader(title: "Creative Collections")
                            collectionsSection
                            ExploreSectionHeader(title: "Featured")
                            featuredSection
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 6)
                    .padding(.bottom, 16)
                }
            }
        }
    }

    private var header: some View {
        HStack(spacing: 10) {
            Image("GSCompanionOrb")
                .resizable()
                .scaledToFill()
                .frame(width: 34, height: 34)
                .clipShape(Circle())
                .overlay(Circle().strokeBorder(.white.opacity(0.6), lineWidth: 1))
            VStack(alignment: .leading, spacing: 0) {
                Text("Nexly")
                    .font(.custom(Aero.productName(.bold), size: 15, relativeTo: .title3))
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
                NexlyGlyph(name: "search", size: 16, color: Aero.text)
                    .modifier(IconCircleStyle(bg: Color(red: 0.996, green: 0.953, blue: 0.933), diameter: 36))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(searchOpen ? "Close search" : "Open search")
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 6)
    }

    private var searchField: some View {
        HStack(spacing: 8) {
            NexlyGlyph(name: "search", size: 14, color: Aero.textMuted)
            TextField("Search tools and abilities", text: $query)
                .font(Aero.bodyMedium())
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
                .accessibilityIdentifier("explore-search-input")
            if !query.isEmpty {
                Button { query = "" } label: {
                    NexlyGlyph(name: "x", size: 13, color: Aero.textMuted)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Clear search")
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 9)
        .modifier(ExploreGlass(corner: 22, elevation: 8))
        .padding(.horizontal, 16)
        .padding(.bottom, 4)
    }

    private var heroCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 4) {
                NexlyGlyph(name: "sparkles", size: 10, color: ExplorePalette.discoverInk)
                Text("Discover")
                    .font(Aero.caption())
                    .foregroundStyle(ExplorePalette.discoverInk)
            }
            .padding(.horizontal, 9)
            .padding(.vertical, 4)
            .background(Capsule().fill(ExplorePalette.discoverBg))

            Text("Explore AI\nPossibilities")
                .font(.custom(Aero.productName(.bold), size: 17.5, relativeTo: .title2))
                .foregroundStyle(Aero.text)
                .lineSpacing(2)

            Text("Powerful tools, creative features, and more ways to bring your ideas to life — all in one place.")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
                .frame(maxWidth: 220, alignment: .leading)

            Button {
                onOpenDiscover()
            } label: {
                HStack(spacing: 6) {
                    Text("Explore All")
                        .font(Aero.caption().weight(.semibold))
                        .foregroundStyle(Aero.text)
                    NexlyGlyph(name: "arrow-right", size: 11, color: Aero.text)
                }
                .padding(.horizontal, 13)
                .padding(.vertical, 7)
                .background(Capsule().fill(LinearGradient(colors: [ExplorePalette.ctaTop, ExplorePalette.ctaBottom], startPoint: .leading, endPoint: .trailing)))
                    .overlay(Capsule().strokeBorder(LinearGradient(colors: [.white.opacity(0.90), .white.opacity(0.35)], startPoint: .top, endPoint: .bottom), lineWidth: 1))
                        .shadow(color: Color(red: 0.922, green: 0.616, blue: 0.447).opacity(0.35), radius: 9, x: 0, y: 5)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Explore all tools")
        }
        .padding(13)
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(maxWidth: .infinity)
        .modifier(ExploreGlass(corner: 24, elevation: 14))
        .overlay(alignment: .trailing) {
            Image("GSExploreHero")
                .resizable()
                .scaledToFill()
                .frame(maxWidth: 216)
                .frame(maxHeight: .infinity)
                .clipped()
                .overlay(
                    LinearGradient(
                        stops: [
                            .init(color: Color(red: 0.992, green: 0.945, blue: 0.925).opacity(0.94), location: 0),
                            .init(color: Color(red: 0.992, green: 0.945, blue: 0.925).opacity(0.22), location: 0.45),
                            .init(color: .clear, location: 1),
                        ],
                        startPoint: .leading, endPoint: .trailing
                    )
                )
                .allowsHitTesting(false)
        }
    }

    private var toolsGrid: some View {
        VStack(spacing: 10) {
            let tools = Array(ExploreCatalog.tools.prefix(4))
            ForEach(0..<tools.count, id: \.self) { i in
                if i % 2 == 0, i < tools.count {
                    HStack(alignment: .top, spacing: 10) {
                        toolCardHorizontal(tools[i])
                        if i + 1 < tools.count {
                            toolCardHorizontal(tools[i + 1])
                        } else {
                            Spacer().frame(maxWidth: .infinity)
                        }
                    }
                }
            }
        }
    }

    /// The reference's Popular Tools card: icon LEFT, copy right, arrow low-right.
    private func toolCardHorizontal(_ tool: ExploreTool) -> some View {
        Button {
            onOpenChat(.chatPrefill(tool.prompt))
        } label: {
            VStack(alignment: .leading, spacing: 7) {
                HStack(spacing: 9) {
                    NexlyGlyph(name: tool.glyph, size: 15, color: tool.filledTile ? .white : tool.fg)
                        .modifier(IconCircleStyle(
                            bg: tool.filledTile ? tool.fg : tool.bg,
                            diameter: 36,
                            solidTile: tool.filledTile
                        ))
                    Text(tool.title)
                        .font(Aero.bodyMedium().weight(.semibold))
                        .foregroundStyle(Aero.text)
                        .lineLimit(1)
                }
                Text(tool.subtitle)
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
                    .lineLimit(2)
                    .frame(minHeight: 26, alignment: .top)
                HStack {
                    Spacer()
                    NexlyGlyph(name: "arrow-right", size: 11, color: Aero.textMuted)
                }
            }
            .padding(11)
            .frame(maxWidth: .infinity, alignment: .leading)
            .modifier(ExploreGlass(corner: 18, elevation: 10))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(tool.title): \(tool.subtitle)")
    }

    private var collectionsSection: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(ExploreCatalog.collections) { collection in
                    Button {
                        onOpenDiscover()
                    } label: {
                        VStack(alignment: .leading, spacing: 0) {
                            collectionArt(collection)
                                .frame(height: 72)
                                .frame(maxWidth: .infinity)
                                .clipped()
                                .overlay(LinearGradient(colors: [.white.opacity(0.06), .white.opacity(0.34)], startPoint: .top, endPoint: .bottom))
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
                            .padding(.horizontal, 10)
                            .padding(.vertical, 8)
                        }
                        .frame(width: 150)
                        .clipShape(RoundedRectangle(cornerRadius: 18))
                        .modifier(ExploreGlass(corner: 18, elevation: 10))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(collection.title): \(collection.subtitle)")
                }
            }
        }
    }

    private var featuredSection: some View {
        Button {
            onOpenChat(.chatPrefill(ExploreCatalog.featured.prompt))
        } label: {
            ZStack(alignment: .trailing) {
                Image("GSExploreKyoto")
                    .resizable()
                    .scaledToFill()
                    .frame(width: 150)
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
                HStack(spacing: 10) {
                    NexlyGlyph(name: ExploreCatalog.featured.glyph, size: 15, color: ExploreCatalog.featured.fg)
                        .modifier(IconCircleStyle(bg: ExploreCatalog.featured.bg, diameter: 34))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(ExploreCatalog.featured.title)
                            .font(Aero.bodyMedium().weight(.semibold))
                            .foregroundStyle(Aero.text)
                        Text(ExploreCatalog.featured.subtitle)
                            .font(Aero.caption())
                            .foregroundStyle(Aero.textMuted)
                            .lineLimit(2)
                    }
                    Spacer()
                    Circle()
                        .fill(.white.opacity(0.55))
                        .frame(width: 26, height: 26)
                        .overlay(NexlyGlyph(name: "chevron-right", size: 13, color: Aero.text))
                }
                .padding(10)
            }
            .modifier(ExploreGlass(corner: 18, elevation: 10))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(ExploreCatalog.featured.title): \(ExploreCatalog.featured.subtitle)")
    }

    private var resultsSection: some View {
        let results = ExploreCatalog.search(query)
        return VStack(alignment: .leading, spacing: 12) {
            ExploreSectionHeader(title: "Results")
            if results.isEmpty {
                emptySearch
            } else {
                VStack(spacing: 7) {
                    ForEach(results) { tool in promptRow(tool) }
                }
            }
        }
    }

    private var emptySearch: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text("No tool matches \(query.trimmingCharacters(in: .whitespaces)) yet")
                .font(Aero.bodyMedium().weight(.semibold))
                .foregroundStyle(Aero.text)
            Text("I can still help — ask me directly and we'll figure it out together.")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
            Button {
                onOpenChat(.chatAutoSend(query.trimmingCharacters(in: .whitespaces)))
            } label: {
                HStack(spacing: 6) {
                    Text("Ask about \(query.trimmingCharacters(in: .whitespaces))")
                        .font(Aero.caption().weight(.semibold))
                        .foregroundStyle(Aero.text)
                        .lineLimit(1)
                    NexlyGlyph(name: "arrow-right", size: 12, color: Aero.text)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Capsule().fill(LinearGradient(colors: [ExplorePalette.ctaTop, ExplorePalette.ctaBottom], startPoint: .leading, endPoint: .trailing)))
                    .overlay(Capsule().strokeBorder(LinearGradient(colors: [.white.opacity(0.90), .white.opacity(0.35)], startPoint: .top, endPoint: .bottom), lineWidth: 1))
                        .shadow(color: Color(red: 0.922, green: 0.616, blue: 0.447).opacity(0.35), radius: 9, x: 0, y: 5)
            }
            .buttonStyle(.plain)
            .padding(.top, 6)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .modifier(ExploreGlass(corner: 18, elevation: 10))
    }

    private func sectionHeader(_ title: String, action: (() -> Void)? = nil, actionLabel: String = "See All") -> some View {
        HStack {
            Text(title)
                .font(Aero.title())
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

    private func promptRow(_ tool: ExploreTool) -> some View {
        Button {
            onOpenChat(.chatPrefill(tool.prompt))
        } label: {
            HStack(spacing: 10) {
                NexlyGlyph(name: tool.glyph, size: 15, color: tool.filledTile ? .white : tool.fg)
                    .modifier(IconCircleStyle(
                        bg: tool.filledTile ? tool.fg : tool.bg,
                        diameter: 34,
                        solidTile: tool.filledTile
                    ))
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
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .modifier(ExploreGlass(corner: 16, elevation: 9))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(tool.title): \(tool.subtitle)")
    }

    @ViewBuilder
    private func collectionArt(_ collection: ExploreCatalog.Collection) -> some View {
        switch collection.subtitle {
        case "Work smarter, not harder":
            Image("GSExploreColWork").resizable().scaledToFill()
        case "Build new skills":
            Image("GSExploreColLearning").resizable().scaledToFill()
        default:
            Image("GSExploreColDesign").resizable().scaledToFill()
        }
    }
}

// MARK: - DISCOVER — the mock with the EXPLORE hero / chips / composer

struct DiscoverView: View {
    let onOpenChat: (AeroRoute) -> Void

    var initialQuery: String = ""
    var initialSearchOpen: Bool = false

    @State private var query: String
    @State private var searchOpen: Bool
    @State private var category: ExploreCategory = .all

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
            Image("GSExploreBackdrop")
                .resizable()
                .scaledToFill()
                .ignoresSafeArea()
                .allowsHitTesting(false)
            VStack(spacing: 0) {
                header
                if searchOpen { searchField.transition(.opacity.combined(with: .move(edge: .top))) }
                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 12) {
                        if !query.trimmingCharacters(in: .whitespaces).isEmpty {
                            resultsSection
                        } else {
                            heroCard
                            chips
                            ExploreSectionHeader(title: "Featured Tools")
                            toolsSection
                            ExploreSectionHeader(title: "Popular")
                            popularSection
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 6)
                    .padding(.bottom, 14)
                }
                composer
            }
        }
    }

    private var header: some View {
        HStack(spacing: 10) {
            Image("GSCompanionOrb")
                .resizable()
                .scaledToFill()
                .frame(width: 34, height: 34)
                .clipShape(Circle())
                .overlay(Circle().strokeBorder(.white.opacity(0.6), lineWidth: 1))
            VStack(alignment: .leading, spacing: 0) {
                Text("Nexly")
                    .font(.custom(Aero.productName(.bold), size: 15, relativeTo: .title3))
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
                NexlyGlyph(name: "search", size: 16, color: Aero.text)
                    .modifier(IconCircleStyle(bg: Color(red: 0.996, green: 0.953, blue: 0.933), diameter: 36))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(searchOpen ? "Close search" : "Open search")
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 6)
    }

    private var searchField: some View {
        HStack(spacing: 8) {
            NexlyGlyph(name: "search", size: 14, color: Aero.textMuted)
            TextField("Search tools and abilities", text: $query)
                .font(Aero.bodyMedium())
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
                .accessibilityIdentifier("explore-search-input")
            if !query.isEmpty {
                Button { query = "" } label: {
                    NexlyGlyph(name: "x", size: 13, color: Aero.textMuted)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Clear search")
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 9)
        .modifier(ExploreGlass(corner: 22, elevation: 8))
        .padding(.horizontal, 16)
        .padding(.bottom, 4)
    }

    /// The reference's eyebrow + two-line title + Start Exploring.
    private var heroCard: some View {
        VStack(alignment: .leading, spacing: 7) {
            Text("EXPLORE")
                .font(Aero.caption().weight(.semibold))
                .foregroundStyle(Aero.textMuted)
                .kerning(2)
            Text("Discover what\nyou can do with Nexly")
                .font(.custom(Aero.productName(.bold), size: 17, relativeTo: .title2))
                .foregroundStyle(Aero.text)
                .lineSpacing(2)
            Text("AI tools, creative features and smart experiences — all in one place.")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
                .frame(maxWidth: 240, alignment: .leading)
            Button {
                withAnimation { category = .all }
            } label: {
                HStack(spacing: 6) {
                    Text("Start Exploring")
                        .font(Aero.caption().weight(.semibold))
                        .foregroundStyle(Aero.text)
                    NexlyGlyph(name: "arrow-right", size: 11, color: Aero.text)
                }
                .padding(.horizontal, 13)
                .padding(.vertical, 7)
                .background(Capsule().fill(LinearGradient(colors: [ExplorePalette.ctaTop, ExplorePalette.ctaBottom], startPoint: .leading, endPoint: .trailing)))
                    .overlay(Capsule().strokeBorder(LinearGradient(colors: [.white.opacity(0.90), .white.opacity(0.35)], startPoint: .top, endPoint: .bottom), lineWidth: 1))
                        .shadow(color: Color(red: 0.922, green: 0.616, blue: 0.447).opacity(0.35), radius: 9, x: 0, y: 5)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Start exploring")
        }
        .padding(13)
        .frame(maxWidth: .infinity, alignment: .leading)
        .modifier(ExploreGlass(corner: 24, elevation: 14))
        .overlay(alignment: .trailing) {
            Image("GSExploreHero")
                .resizable()
                .scaledToFill()
                .frame(maxWidth: 200)
                .frame(maxHeight: .infinity)
                .clipped()
                .overlay(
                    LinearGradient(
                        stops: [
                            .init(color: Color(red: 0.992, green: 0.945, blue: 0.925).opacity(0.94), location: 0),
                            .init(color: Color(red: 0.992, green: 0.945, blue: 0.925).opacity(0.22), location: 0.45),
                            .init(color: .clear, location: 1),
                        ],
                        startPoint: .leading, endPoint: .trailing
                    )
                )
                .allowsHitTesting(false)
        }
    }

    private var chips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 7) {
                ForEach(ExploreCategory.allCases) { cat in
                    let active = cat == category
                    Button {
                        category = cat
                    } label: {
                        Text(cat.rawValue)
                            .font(Aero.bodyMedium())
                            .foregroundStyle(active ? Color.white : Aero.textMuted)
                            .padding(.horizontal, 13)
                            .padding(.vertical, 6)
                            .background(
                                Group {
                                    if active {
                                        Capsule().fill(LinearGradient(colors: [ExplorePalette.peach, ExplorePalette.peachDeep], startPoint: .leading, endPoint: .trailing))
                                            .overlay(Capsule().strokeBorder(LinearGradient(colors: [.white.opacity(0.85), .white.opacity(0.30)], startPoint: .top, endPoint: .bottom), lineWidth: 1))
                                            .shadow(color: Color(red: 0.922, green: 0.616, blue: 0.447).opacity(0.35), radius: 8, x: 0, y: 4)
                                    } else {
                                        Capsule().fill(Color.white.opacity(0.88))
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

    private var toolsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            ExploreSectionHeader(title: "Featured Tools")
            let tools = ExploreCatalog.byCategory(category)
            ForEach(0..<tools.count, id: \.self) { i in
                if i % 2 == 0, i < tools.count {
                    HStack(alignment: .top, spacing: 10) {
                        toolCardVertical(tools[i])
                        if i + 1 < tools.count {
                            toolCardVertical(tools[i + 1])
                        } else {
                            Spacer().frame(maxWidth: .infinity)
                        }
                    }
                }
            }
        }
    }

    /// The reference's Featured Tools card: icon top, copy below, arrow low-right.
    private func toolCardVertical(_ tool: ExploreTool) -> some View {
        Button {
            onOpenChat(.chatPrefill(tool.prompt))
        } label: {
            VStack(alignment: .leading, spacing: 8) {
                NexlyGlyph(name: tool.glyph, size: 15, color: tool.filledTile ? .white : tool.fg)
                    .modifier(IconCircleStyle(
                        bg: tool.filledTile ? tool.fg : tool.bg,
                        diameter: 36,
                        solidTile: tool.filledTile
                    ))
                Text(tool.title)
                    .font(Aero.bodyMedium().weight(.semibold))
                    .foregroundStyle(Aero.text)
                    .lineLimit(1)
                Text(tool.subtitle)
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
                    .lineLimit(2)
                    .frame(minHeight: 26, alignment: .top)
                HStack {
                    Spacer()
                    NexlyGlyph(name: "arrow-right", size: 11, color: Aero.textMuted)
                }
            }
            .padding(11)
            .frame(maxWidth: .infinity, alignment: .leading)
            .modifier(ExploreGlass(corner: 18, elevation: 10))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(tool.title): \(tool.subtitle)")
    }

    private var popularSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            ExploreSectionHeader(title: "Popular")
            let rows = ExploreCatalog.tools.filter { ["study-plan", "logo-maker", "trip-planner", "doc-summary"].contains($0.id) }
                .filter { category == .all || $0.category == category }
            VStack(spacing: 7) {
                ForEach(rows) { tool in
                    Button {
                        onOpenChat(.chatPrefill(tool.prompt))
                    } label: {
                        HStack(spacing: 10) {
                            NexlyGlyph(name: tool.glyph, size: 15, color: tool.filledTile ? .white : tool.fg)
                                .modifier(IconCircleStyle(
                                    bg: tool.filledTile ? tool.fg : tool.bg,
                                    diameter: 34,
                                    solidTile: tool.filledTile
                                ))
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
                        .padding(.horizontal, 10)
                        .padding(.vertical, 8)
                        .modifier(ExploreGlass(corner: 16, elevation: 9))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(tool.title): \(tool.subtitle)")
                }
            }
        }
    }

    private var resultsSection: some View {
        let results = ExploreCatalog.search(query)
        return VStack(alignment: .leading, spacing: 12) {
            ExploreSectionHeader(title: "Results")
            if results.isEmpty {
                emptySearch
            } else {
                VStack(spacing: 7) {
                    ForEach(results) { tool in
                        Button {
                            onOpenChat(.chatPrefill(tool.prompt))
                        } label: {
                            HStack(spacing: 10) {
                                NexlyGlyph(name: tool.glyph, size: 15, color: tool.filledTile ? .white : tool.fg)
                                    .modifier(IconCircleStyle(
                                        bg: tool.filledTile ? tool.fg : tool.bg,
                                        diameter: 34,
                                        solidTile: tool.filledTile
                                    ))
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
                            .padding(.horizontal, 10)
                            .padding(.vertical, 8)
                            .modifier(ExploreGlass(corner: 16, elevation: 9))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("\(tool.title): \(tool.subtitle)")
                    }
                }
            }
        }
    }

    private var emptySearch: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text("No tool matches \(query.trimmingCharacters(in: .whitespaces)) yet")
                .font(Aero.bodyMedium().weight(.semibold))
                .foregroundStyle(Aero.text)
            Text("I can still help — ask me directly and we'll figure it out together.")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
            Button {
                onOpenChat(.chatAutoSend(query.trimmingCharacters(in: .whitespaces)))
            } label: {
                HStack(spacing: 6) {
                    Text("Ask about \(query.trimmingCharacters(in: .whitespaces))")
                        .font(Aero.caption().weight(.semibold))
                        .foregroundStyle(Aero.text)
                        .lineLimit(1)
                    NexlyGlyph(name: "arrow-right", size: 12, color: Aero.text)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Capsule().fill(LinearGradient(colors: [ExplorePalette.ctaTop, ExplorePalette.ctaBottom], startPoint: .leading, endPoint: .trailing)))
                    .overlay(Capsule().strokeBorder(LinearGradient(colors: [.white.opacity(0.90), .white.opacity(0.35)], startPoint: .top, endPoint: .bottom), lineWidth: 1))
                        .shadow(color: Color(red: 0.922, green: 0.616, blue: 0.447).opacity(0.35), radius: 9, x: 0, y: 5)
            }
            .buttonStyle(.plain)
            .padding(.top, 6)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .modifier(ExploreGlass(corner: 18, elevation: 10))
    }

    /// The reference's composer: the typed words auto-send into a fresh chat.
    private var composer: some View {
        ExploreComposerBar { text in
            onOpenChat(.chatAutoSend(text))
        }
    }
}

