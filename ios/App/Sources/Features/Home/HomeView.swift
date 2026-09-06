import SwiftUI

// MARK: - Stagger entrance (private per-file helper)

/// Fades + lifts a section into place, delayed by its index —
/// the Aeruo Kinetic section entrance.
private struct StaggerIn<Content: View>: View {
    let index: Int
    @ViewBuilder var content: () -> Content

    @State private var appeared = false

    var body: some View {
        content()
            .opacity(appeared ? 1 : 0)
            .offset(y: appeared ? 0 : 16)
            .onAppear {
                withAnimation(Aero.spring.delay(Aero.stagger(index))) {
                    appeared = true
                }
            }
    }
}

// MARK: - Home — the AI command centre

/// HOME tab root (installed by the frozen router). Static sample content;
/// every tappable pushes an `AeroRoute` through NavigationLink(value:).
///
/// Body is split into two ViewBuilder groups to stay inside the classic
/// 10-children ViewBuilder limit (keeps Xcode 14 / iOS 16 SDK compatible).
struct HomeView: View {

    // MARK: Sample data

    private struct QuickAction: Identifiable {
        let id = UUID()
        let label: String
        let icon: String
        let route: AeroRoute
    }

    private struct ConversationRow: Identifiable {
        let id: String
        let title: String
        let subtitle: String
        let icon: String
    }

    private struct AssistantPin: Identifiable {
        let id: String
        let name: String
        let category: String
    }

    private struct ProjectRow: Identifiable {
        let id: String
        let name: String
        let meta: String
    }

    private struct ModelPick: Identifiable {
        let id: String
        let name: String
        let tier: String
        let icon: String
    }

    private let quickActions: [QuickAction] = [
        .init(label: "New chat", icon: "ellipsis.bubble", route: .chat(nil)),
        .init(label: "Voice", icon: "mic", route: .voice),
        .init(label: "Analyse image", icon: "photo", route: .chat(nil)),
        .init(label: "Analyse document", icon: "doc.text", route: .chat(nil)),
        .init(label: "Write", icon: "pencil", route: .chat(nil)),
        .init(label: "Research", icon: "magnifyingglass", route: .chat(nil)),
        .init(label: "Code", icon: "chevron.left.forwardslash.chevron.right", route: .chat(nil)),
        .init(label: "Translate", icon: "globe", route: .chat(nil)),
        .init(label: "Summarise", icon: "text.alignleft", route: .chat(nil)),
        .init(label: "Brainstorm", icon: "lightbulb", route: .chat(nil)),
        .init(label: "Generate image", icon: "wand.and.stars", route: .chat(nil))
    ]

    private let prompts = [
        "Plan a product launch",
        "Explain quantum computing simply",
        "Draft a cold email",
        "Debug my Swift code"
    ]

    private let continuing: [ConversationRow] = [
        .init(id: "demo-1", title: "Q3 pricing strategy", subtitle: "12 messages · 2h ago", icon: "clock.arrow.circlepath"),
        .init(id: "demo-2", title: "Kyoto trip plan", subtitle: "8 messages · yesterday", icon: "clock.arrow.circlepath")
    ]

    private let recents: [ConversationRow] = [
        .init(id: "demo-1", title: "Q3 pricing strategy", subtitle: "12 messages · 2h ago", icon: "bubble.left"),
        .init(id: "demo-2", title: "Kyoto trip plan", subtitle: "8 messages · yesterday", icon: "bubble.left"),
        .init(id: "demo-3", title: "API error debugging", subtitle: "21 messages · 3 days ago", icon: "bubble.left")
    ]

    private let assistants: [AssistantPin] = [
        .init(id: "asst-1", name: "Research Scout", category: "Research"),
        .init(id: "asst-2", name: "Copysmith", category: "Writing")
    ]

    private let projects: [ProjectRow] = [
        .init(id: "project-brand", name: "Brand Refresh 2025", meta: "8 chats · 14 files"),
        .init(id: "project-launch", name: "Q3 Launch Plan", meta: "5 chats · 9 files")
    ]

    private let models: [ModelPick] = [
        .init(id: "swift", name: "GS Swift", tier: "Fast", icon: "bolt.fill"),
        .init(id: "balanced", name: "GS Balanced", tier: "Everyday", icon: "slider.horizontal.3"),
        .init(id: "deep", name: "GS Deep", tier: "Deep reasoning", icon: "hourglass")
    ]

    // MARK: Greeting

    private var greeting: String {
        switch Calendar.current.component(.hour, from: Date()) {
        case 5..<12: return "Good morning"
        case 12..<18: return "Good afternoon"
        default: return "Good evening"
        }
    }

    private var dateCaption: String {
        Date().formatted(date: .abbreviated, time: .omitted)
    }

    // MARK: Body

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                    StaggerIn(index: 0) { header }
                    StaggerIn(index: 1) { universalInput }
                    StaggerIn(index: 2) { quickActionsSection }
                    StaggerIn(index: 3) { suggestedPrompts }
                    StaggerIn(index: 4) { continueSection }
                }
                VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                    StaggerIn(index: 5) { recentSection }
                    StaggerIn(index: 6) { pinnedAssistants }
                    StaggerIn(index: 7) { recentProjects }
                    StaggerIn(index: 8) { recommendedModels }
                    StaggerIn(index: 9) { todaySection }
                    StaggerIn(index: 10) { capabilitiesRow }
                }
                .padding(.top, Aero.Spacing.l)
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
    }

    // MARK: Greeting header + toolbar links

    private var header: some View {
        HStack(alignment: .center, spacing: Aero.Spacing.s) {
            VStack(alignment: .leading, spacing: Aero.Spacing.xs) {
                Text(greeting)
                    .font(Aero.displayTitle())
                    .foregroundStyle(Aero.text)
                Text(dateCaption)
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
            }
            Spacer()
            HStack(spacing: Aero.Spacing.s) {
                headerLink("bell", .notifications)
                headerLink("gearshape", .settings)
                headerLink("magnifyingglass", .search)
            }
        }
    }

    private func headerLink(_ icon: String, _ route: AeroRoute) -> some View {
        NavigationLink(value: route) {
            Image(systemName: icon)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(Aero.text)
                .frame(width: 34, height: 34)
                .background(Circle().fill(Aero.surface))
                .overlay(Circle().stroke(Aero.outline, lineWidth: 1))
        }
        .buttonStyle(KineticPressStyle())
    }

    // MARK: Universal input (static row → new chat)

    private var universalInput: some View {
        NavigationLink(value: AeroRoute.chat(nil)) {
            HStack(spacing: Aero.Spacing.s) {
                Image(systemName: "sparkles")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(Aero.accent)
                Text("Ask anything…")
                    .font(Aero.body())
                    .foregroundStyle(Aero.textMuted)
                Spacer()
                Image(systemName: "mic.fill")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Aero.textMuted)
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.vertical, 14)
            .background(Capsule().fill(Aero.container))
            .overlay(Capsule().stroke(Aero.outline, lineWidth: 1))
        }
        .buttonStyle(KineticPressStyle())
    }

    // MARK: Quick actions (11 tiles)

    private var quickActionsSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Quick actions")
            LazyVGrid(
                columns: [
                    GridItem(.flexible(), spacing: 12),
                    GridItem(.flexible(), spacing: 12),
                    GridItem(.flexible(), spacing: 12),
                    GridItem(.flexible(), spacing: 12)
                ],
                spacing: 12
            ) {
                ForEach(quickActions) { action in
                    NavigationLink(value: action.route) {
                        QuickActionTile(label: action.label, icon: action.icon)
                            .allowsHitTesting(false)   // frozen tile embeds a Button — let the link take the tap
                    }
                    .buttonStyle(KineticPressStyle())
                }
            }
        }
    }

    // MARK: Suggested prompts (horizontal chips → new chat)

    private var suggestedPrompts: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Suggested prompts")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Aero.Spacing.s) {
                    ForEach(prompts, id: \.self) { prompt in
                        NavigationLink(value: AeroRoute.chat(nil)) {
                            AeroChip(text: prompt)
                                .allowsHitTesting(false)   // frozen chip embeds a Button
                        }
                        .buttonStyle(KineticPressStyle())
                    }
                }
                .padding(.vertical, 2)
            }
        }
    }

    // MARK: Continue where you left off

    private var continueSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Continue where you left off")
            VStack(spacing: Aero.Spacing.s) {
                ForEach(continuing) { row in
                    chatRow(row)
                }
            }
        }
    }

    // MARK: Recent conversations

    private var recentSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Recent conversations")
            VStack(spacing: Aero.Spacing.s) {
                ForEach(recents) { row in
                    chatRow(row)
                }
            }
        }
    }

    // MARK: Pinned assistants

    private var pinnedAssistants: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Pinned assistants")
            HStack(spacing: Aero.Spacing.m) {
                ForEach(assistants) { assistant in
                    NavigationLink(value: AeroRoute.assistant(assistant.id)) {
                        AeroCard {
                            HStack(spacing: Aero.Spacing.s) {
                                Image(systemName: "smarttoy")
                                    .font(.system(size: 15, weight: .medium))
                                    .foregroundStyle(Aero.text)
                                    .frame(width: 36, height: 36)
                                    .background(Circle().fill(Aero.container))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(assistant.name)
                                        .font(Aero.title())
                                        .foregroundStyle(Aero.text)
                                        .lineLimit(1)
                                        .minimumScaleFactor(0.7)
                                    Text(assistant.category)
                                        .font(Aero.caption())
                                        .foregroundStyle(Aero.textMuted)
                                        .lineLimit(1)
                                }
                                Spacer(minLength: 0)
                            }
                        }
                    }
                    .buttonStyle(KineticPressStyle())
                }
            }
        }
    }

    // MARK: Recent projects

    private var recentProjects: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Recent projects")
            VStack(spacing: Aero.Spacing.s) {
                ForEach(projects) { project in
                    NavigationLink(value: AeroRoute.project(project.id)) {
                        AeroListRow(
                            title: project.name,
                            subtitle: project.meta,
                            leading: {
                                Image(systemName: "folder")
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundStyle(Aero.text)
                                    .frame(width: 36, height: 36)
                                    .background(Circle().fill(Aero.containerHigh))
                            },
                            trailing: {
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(Aero.textMuted)
                            }
                        )
                    }
                    .buttonStyle(KineticPressStyle())
                }
            }
        }
    }

    // MARK: Recommended models

    private var recommendedModels: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Recommended models")
            HStack(spacing: Aero.Spacing.m) {
                ForEach(models) { model in
                    NavigationLink(value: AeroRoute.models) {
                        AeroCard {
                            VStack(alignment: .leading, spacing: 3) {
                                Image(systemName: model.icon)
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundStyle(Aero.text)
                                Text(model.name)
                                    .font(Aero.label())
                                    .foregroundStyle(Aero.text)
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.7)
                                Text(model.tier)
                                    .font(Aero.caption())
                                    .foregroundStyle(Aero.textMuted)
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.7)
                            }
                        }
                    }
                    .buttonStyle(KineticPressStyle())
                }
            }
        }
    }

    // MARK: Today stat (aurora = AI activity, the one allowed gradient here)

    private var todaySection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Today")
            AeroCard {
                VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                    Text("12 chats · 3 docs · 45m voice")
                        .font(Aero.title())
                        .foregroundStyle(Aero.text)
                    ZStack(alignment: .leading) {
                        Capsule().fill(Aero.container).frame(height: 8)
                        Rectangle()
                            .fill(LinearGradient(colors: Aero.aurora, startPoint: .leading, endPoint: .trailing))
                            .frame(width: 120, height: 8)
                            .clipShape(Capsule())
                    }
                    Text("45m of 8h daily AI activity")
                        .font(Aero.label())
                        .foregroundStyle(Aero.textMuted)
                }
            }
        }
    }

    // MARK: Capabilities row

    private var capabilitiesRow: some View {
        HStack(spacing: Aero.Spacing.m) {
            AeroCard {   // static — task defines no route for Vision
                capability("eye", "Vision")
            }
            NavigationLink(value: AeroRoute.voice) {
                AeroCard { capability("waveform", "Voice") }
            }
            .buttonStyle(KineticPressStyle())
            NavigationLink(value: AeroRoute.chat(nil)) {
                AeroCard { capability("document.magnifyingglass", "Research") }
            }
            .buttonStyle(KineticPressStyle())
        }
    }

    private func capability(_ icon: String, _ label: String) -> some View {
        VStack(spacing: Aero.Spacing.xs) {
            Image(systemName: icon)
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(Aero.text)
            Text(label)
                .font(Aero.label())
                .foregroundStyle(Aero.textMuted)
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: Row builder

    private func chatRow(_ row: ConversationRow) -> some View {
        NavigationLink(value: AeroRoute.chat(row.id)) {
            AeroListRow(
                title: row.title,
                subtitle: row.subtitle,
                leading: {
                    Image(systemName: row.icon)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(Aero.text)
                        .frame(width: 36, height: 36)
                        .background(Circle().fill(Aero.containerHigh))
                },
                trailing: {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Aero.textMuted)
                }
            )
        }
        .buttonStyle(KineticPressStyle())
    }
}
