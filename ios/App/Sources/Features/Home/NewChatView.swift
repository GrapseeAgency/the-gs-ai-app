import SwiftUI

/// "Start a New Chat" — docs/reference/pending/home-new-chat.png.
///
/// The screen a reader lands on before a conversation exists: the companion
/// header, the hero bloom, the composer, the six starting tools, the recent
/// chats and the way into Model Selection.
///
/// Every element is wired:
///  - the composer sends what is typed and writes the wire mode through
///    `ModelRouter`, so Auto Select and the effort control are read here;
///  - each tool chip seeds the composer with that tool's own prompt — the same
///    rule Explore uses, where only the trip planner has a screen of its own;
///  - the recent rows are the real active conversations, newest first, with
///    their real previews and times;
///  - "Explore AI Models" opens `ModelSelectionView`;
///  - the top-right control opens the drawer, because this page carries no dock
///    and an app with a drawer cannot ship without one way to reach it.
struct NewChatView: View {

    var onStartChat: (String, Bool) -> Void
    var onOpenConversation: (String) -> Void
    var onOpenAllChats: () -> Void
    var onOpenModels: () -> Void
    var onOpenMenu: () -> Void
    var onOpenExplore: () -> Void

    /// The reader's clock; the capture harness pins it so "2h ago" is stable.
    var now: Date = Date()

    @ObservedObject private var store = ConversationStore.shared
    @ObservedObject private var settings = SettingsStore.shared
    @Environment(\.colorScheme) private var scheme
    @State private var draft = ""
    @FocusState private var focused: Bool

    private struct Tool { let label, prompt, glyph: String; let tone: GsWellTone }
    private let tools: [Tool] = [
        Tool(label: "Web Search", prompt: "Search the web for ", glyph: "globe", tone: .lilac),
        Tool(label: "Code", prompt: "Write code that ", glyph: "code", tone: .blue),
        Tool(label: "Write", prompt: "Help me write ", glyph: "pencil", tone: .peach),
        Tool(label: "Analyze", prompt: "Analyze this for me: ", glyph: "chart-bar", tone: .mint),
        Tool(label: "Image", prompt: "Create an image of ", glyph: "photo", tone: .rose),
    ]

    private var recent: [StoredConversation] {
        Array(store.conversations.filter { !$0.archived }.prefix(4))
    }

    var body: some View {
        ZStack {
            GsSettingsBackdrop().allowsHitTesting(false)
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 0) {
                    header
                    HStack {
                        Spacer()
                        AnimatedGsIllustration(
                            imageName: "GSCompanionOrb", size: 112,
                            breathDuration: 3.0, rotationDegrees: 1.0,
                            lift: -2, startDelay: 0.1)
                        Spacer()
                    }
                    .padding(.top, 4)
                    VStack(spacing: 4) {
                        Text("Start a New Chat")
                            .font(.custom(Aero.productName(.semibold), size: 22, relativeTo: .title2))
                            .foregroundStyle(Aero.text)
                        Text("Ask anything, get instant answers,\nor explore something new.")
                            .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption))
                            .foregroundStyle(Aero.textTertiary)
                            .multilineTextAlignment(.center)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top, 2)

                    composer.padding(.top, 10)
                    toolGrid.padding(.top, 10)
                    recentHeader
                    recentList
                    modelEntry.padding(.top, 14)
                    Spacer(minLength: 24)
                }
                .padding(.horizontal, 20)
            }
        }
        .toolbar(.hidden, for: .navigationBar)
    }

    // MARK: Header

    private var header: some View {
        HStack(spacing: 10) {
            Image("GSHomeAvatar")
                .resizable()
                .scaledToFit()
                .frame(width: 34, height: 34)
            VStack(alignment: .leading, spacing: 0) {
                Text("Nexly")
                    .font(.custom(Aero.productName(.semibold), size: 17, relativeTo: .headline))
                    .foregroundStyle(Aero.text)
                Text("Your AI companion.\nAlways.")
                    .font(.custom(Aero.productName(.regular), size: 9.5, relativeTo: .caption2))
                    .foregroundStyle(Aero.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
            Button(action: onOpenMenu) {
                ZStack {
                    Circle().fill(.white.opacity(scheme == .dark ? 0.10 : 0.50))
                    Circle().strokeBorder(.white.opacity(scheme == .dark ? 0.14 : 0.75), lineWidth: 1)
                    NexlyGlyph(name: "user", size: 16, color: Aero.textSecondary)
                }
                .frame(width: 36, height: 36)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Open menu")
            .accessibilityIdentifier("new-chat-menu")
        }
        .padding(.top, 8)
    }

    // MARK: Composer

    private var composer: some View {
        VStack(alignment: .leading, spacing: 10) {
            ZStack(alignment: .leading) {
                if draft.isEmpty {
                    Text("Type your message...")
                        .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption))
                        .foregroundStyle(Aero.textTertiary)
                }
                TextField("", text: $draft, axis: .vertical)
                    .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption))
                    .foregroundStyle(Aero.text)
                    .lineLimit(1...4)
                    .focused($focused)
                    .accessibilityIdentifier("new-chat-input")
            }
            HStack(spacing: 8) {
                composerButton("paperclip", "Attach a file") { onStartChat("", false) }
                composerButton("photo", "Attach an image") { onStartChat("", false) }
                composerButton("sparkles", "Create", action: onOpenExplore)
                composerButton("dots", "More", action: onOpenExplore)
                Spacer(minLength: 0)
                Button(action: send) {
                    ZStack {
                        Circle().fill(Aero.accent.opacity(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0.35 : 1))
                        NexlyGlyph(name: "arrow-right", size: 16, color: .white)
                    }
                    .frame(width: 38, height: 38)
                }
                .buttonStyle(.plain)
                .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                .accessibilityLabel("Send")
                .accessibilityIdentifier("new-chat-send")
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .gsGlass(.cardLight, corner: 22)
    }

    private func composerButton(_ glyph: String, _ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            ZStack {
                Circle().fill(.white.opacity(scheme == .dark ? 0.10 : 0.50))
                Circle().strokeBorder(.white.opacity(scheme == .dark ? 0.14 : 0.75), lineWidth: 1)
                NexlyGlyph(name: glyph, size: 14, color: Aero.textSecondary)
            }
            .frame(width: 32, height: 32)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    /// The send path's one decision. Written before navigating so the next
    /// reply is the mode the reader saw.
    private func send() {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        let mode = ModelRouter.modeForMessage(
            text: text,
            hasImage: false,
            preferred: GsModel.byId(settings.preferredModel),
            effort: settings.reasoningEffort,
            autoSelect: settings.autoSelectModel)
        UserDefaults.standard.set(mode, forKey: "gs.mode.new")
        draft = ""
        onStartChat(text, true)
    }

    // MARK: Tools

    private var toolGrid: some View {
        VStack(spacing: 7) {
            ForEach(Array(tools.enumerated()), id: \.element.label) { index, tool in
                if index % 2 == 0 {
                    HStack(spacing: 7) {
                        chip(tool.label, tool.glyph, tool.tone) { onStartChat(tool.prompt, false) }
                        if index + 1 < tools.count {
                            let next = tools[index + 1]
                            chip(next.label, next.glyph, next.tone) { onStartChat(next.prompt, false) }
                        } else {
                            chip("More", "layout-grid", .lilac, action: onOpenExplore)
                        }
                    }
                }
            }
        }
    }

    private func chip(_ label: String, _ glyph: String, _ tone: GsWellTone,
                      action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 9) {
                GsWellTile(glyph: glyph, tone: tone, size: 24)
                Text(label)
                    .font(.custom(Aero.productName(.regular), size: 9))
                    .foregroundStyle(Aero.text)
                    .lineLimit(1)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 9)
            .padding(.vertical, 5)
            .frame(maxWidth: .infinity, minHeight: 40, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 16).fill(.white.opacity(scheme == .dark ? 0.08 : 0.42)))
            .overlay(RoundedRectangle(cornerRadius: 16)
                .strokeBorder(.white.opacity(scheme == .dark ? 0.12 : 0.62), lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    // MARK: Recent

    private var recentHeader: some View {
        HStack {
            Text("Recent Chats")
                .font(.custom(Aero.productName(.semibold), size: 13))
                .foregroundStyle(Aero.text)
                .accessibilityAddTraits(.isHeader)
            Spacer()
            Button(action: onOpenAllChats) {
                HStack(spacing: 4) {
                    Text("View all")
                        .font(.custom(Aero.productName(.regular), size: 9))
                        .foregroundStyle(Aero.textTertiary)
                    NexlyGlyph(name: "arrow-right", size: 12, color: Aero.textTertiary)
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel("View all")
        }
        .padding(.top, 12)
        .padding(.bottom, 6)
    }

    @ViewBuilder
    private var recentList: some View {
        if recent.isEmpty {
            Text("No conversations yet — your first one starts above.")
                .font(.custom(Aero.productName(.regular), size: 9))
                .foregroundStyle(Aero.textTertiary)
                .padding(.vertical, 10)
        } else {
            VStack(spacing: 6) {
                ForEach(recent) { conversation in
                    Button { onOpenConversation(conversation.id) } label: {
                        HStack(spacing: 10) {
                            GsWellTile(glyph: glyph(for: conversation),
                                       tone: tone(for: conversation), size: 28)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(conversation.title.isEmpty ? "Untitled chat" : conversation.title)
                                    .font(.custom(Aero.productName(.medium), size: 9.5))
                                    .foregroundStyle(Aero.text)
                                    .lineLimit(1)
                                if let preview = store.lastMessage(for: conversation.id)?.content,
                                   !preview.isEmpty {
                                    Text(Self.preview(preview))
                                        .font(.custom(Aero.productName(.regular), size: 7.5))
                                        .foregroundStyle(Aero.textTertiary)
                                        .lineLimit(1)
                                }
                            }
                            Spacer(minLength: 8)
                            Text(Self.timeLabel(conversation.updatedAt, now: now))
                                .font(.custom(Aero.productName(.regular), size: 7.5))
                                .foregroundStyle(Aero.textTertiary)
                            NexlyGlyph(name: "chevron-right", size: 13, color: Aero.textTertiary)
                        }
                        .padding(.horizontal, 11)
                        .padding(.vertical, 5)
                        .background(RoundedRectangle(cornerRadius: 18).fill(.white.opacity(scheme == .dark ? 0.08 : 0.46)))
                        .overlay(RoundedRectangle(cornerRadius: 18)
                            .strokeBorder(.white.opacity(scheme == .dark ? 0.12 : 0.62), lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("recent-row-\(conversation.id)")
                }
            }
        }
    }

    private var modelEntry: some View {
        Button(action: onOpenModels) {
            HStack(spacing: 10) {
                GsWellTile(glyph: "sparkles", tone: .lilac, size: 28)
                VStack(alignment: .leading, spacing: 1) {
                    Text("Explore AI Models")
                        .font(.custom(Aero.productName(.medium), size: 9.5))
                        .foregroundStyle(Aero.text)
                    Text("Choose the right model for your needs.")
                        .font(.custom(Aero.productName(.regular), size: 7.5))
                        .foregroundStyle(Aero.textTertiary)
                        .lineLimit(1)
                }
                Spacer(minLength: 8)
                NexlyGlyph(name: "chevron-right", size: 13, color: Aero.textTertiary)
            }
            .padding(.horizontal, 11)
            .padding(.vertical, 6)
            .background(RoundedRectangle(cornerRadius: 18)
                .fill((scheme == .dark ? Aero.accentSoft.opacity(0.55) : GsWellTone.lilac.wash.opacity(0.55))))
            .overlay(RoundedRectangle(cornerRadius: 18)
                .strokeBorder(.white.opacity(scheme == .dark ? 0.12 : 0.62), lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Explore AI Models")
        .accessibilityIdentifier("explore-models")
    }

    // MARK: Topic + formatting

    /// The same shipped classifier the drawer and Chat History use, so the rows
    /// carry the mock's variety from real titles. `DrawerTopic.icon` is the
    /// drawer's own vector name; the well draws Tabler names, so each topic is
    /// mapped to the equivalent glyph the catalogue ships.
    private func glyph(for conversation: StoredConversation) -> String {
        if conversation.pinned { return "bookmark" }
        switch DrawerTopic.topic(for: conversation.title) {
        case .science: return "atom"
        case .code: return "code"
        case .data: return "chart-bar"
        case .money: return "currency-dollar"
        case .travel: return "plane"
        case .food: return "map"
        case .health: return "heart"
        case .learning: return "book"
        case .work: return "briefcase"
        case .ideas: return "bulb"
        case .design: return "palette"
        case .writing: return "pencil"
        case .plan: return "sparkles"
        case .music: return "music"
        case .sport: return "trophy"
        case .home: return "home"
        case .shopping: return "shopping-bag"
        case .weather: return "cloud"
        case .security: return "lock"
        case .legal: return "scale"
        case .media: return "photo"
        case .chat: return "message-circle"
        }
    }

    private func tone(for conversation: StoredConversation) -> GsWellTone {
        switch DrawerTopic.topic(for: conversation.title) {
        case .code: return .blue
        case .data, .money: return .mint
        case .travel: return .rose
        case .learning, .science: return .peach
        default: return .lilac
        }
    }

    static func preview(_ raw: String, limit: Int = 70) -> String {
        let flattened = raw
            .replacingOccurrences(of: "[#*_`>\\[\\]]", with: "", options: .regularExpression)
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return flattened.count > limit ? String(flattened.prefix(limit)).trimmingCharacters(in: .whitespaces) + "..." : flattened
    }

    /// The stored stamp is UTC ISO; the label is elapsed time, never canned.
    static func timeLabel(_ iso: String, now: Date) -> String {
        guard let then = ISO8601DateFormatter.fractional.date(from: iso)
                ?? ISO8601DateFormatter().date(from: iso) else { return "" }
        let delta = max(0, now.timeIntervalSince(then))
        let minutes = Int(delta / 60)
        let hours = Int(delta / 3600)
        let days = Int(delta / 86_400)
        if minutes < 1 { return "now" }
        if minutes < 60 { return "\(minutes)m ago" }
        if hours < 24 { return "\(hours)h ago" }
        if days < 7 { return "\(days)d ago" }
        let formatter = DateFormatter()
        formatter.dateFormat = "d/M"
        return formatter.string(from: then)
    }
}

private extension ISO8601DateFormatter {
    static let fractional: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()
}
