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

// MARK: - Project detail

/// Pushed via `.project(id)` — resolves the sample project, shows a hero card
/// with the shared instructions preview, then Chats / Files / Activity /
/// Members tabs. Chats push `.chat(id)`; gearshape in the toolbar pushes
/// `.settings`.
struct ProjectDetailView: View {

    // MARK: Sample data

    private enum DetailTab: String, CaseIterable, Identifiable {
        case chats = "Chats"
        case files = "Files"
        case activity = "Activity"
        case members = "Members"

        var id: String { rawValue }
    }

    private struct Sample {
        let name: String
        let detail: String
        let instructions: String
    }

    private struct FileRow: Identifiable {
        let id = UUID()
        let name: String
        let detail: String
        let icon: String
    }

    private struct ActivityRow: Identifiable {
        let id = UUID()
        let title: String
        let time: String
    }

    private struct MemberRow: Identifiable {
        let id = UUID()
        let name: String
        let initials: String
        let role: String
        let isOwner: Bool
    }

    let projectID: String

    @State private var tab: DetailTab = .chats

    private var sample: Sample {
        switch projectID {
        case "project-brand":
            return Sample(
                name: "Brand Refresh 2025",
                detail: "Repositioning, voice guidelines and the new visual identity.",
                instructions: "You are the brand copilot. Keep answers concise and on-voice; cite the brand book before offering opinions."
            )
        case "project-launch":
            return Sample(
                name: "Q3 Launch Plan",
                detail: "Go-to-market plan, comms calendar and the launch-day runbook.",
                instructions: "Bias toward action — end every answer with the next concrete step for the launch team."
            )
        case "project-research":
            return Sample(
                name: "Research: AI market",
                detail: "Market sizing, competitor scan and a living source library.",
                instructions: "Always show sources. Prefer primary data and flag anything older than 2024."
            )
        default:
            return Sample(
                name: "Project",
                detail: "A shared workspace for chats, files and instructions.",
                instructions: "Help the team move fast with short, sourced answers."
            )
        }
    }

    private let chats: [(title: String, subtitle: String, id: String)] = [
        ("Landing page copy", "12 messages · 2h ago", "demo-1"),
        ("Brand voice workshop", "9 messages · 5h ago", "demo-2"),
        ("Palette exploration", "4 messages · yesterday", "demo-3")
    ]

    private let files: [FileRow] = [
        .init(name: "brand-book.pdf", detail: "PDF · 4.2 MB", icon: "doc.text"),
        .init(name: "logo-pack.zip", detail: "ZIP · 12 files", icon: "doc.zipper"),
        .init(name: "hero-shot.png", detail: "PNG · 1.1 MB", icon: "photo"),
        .init(name: "reach-chart.png", detail: "PNG · 820 KB", icon: "chart.bar")
    ]

    private let activity: [ActivityRow] = [
        .init(title: "Maya added brief.pdf", time: "1h ago"),
        .init(title: "Jonas started “Landing page copy”", time: "3h ago"),
        .init(title: "Priya updated project instructions", time: "yesterday"),
        .init(title: "You shared this project", time: "2d ago")
    ]

    private let members: [MemberRow] = [
        .init(name: "Maya Kobayashi", initials: "MK", role: "Owner", isOwner: true),
        .init(name: "Jonas Petersen", initials: "JP", role: "Editor", isOwner: false),
        .init(name: "Priya Sharma", initials: "PS", role: "Viewer", isOwner: false)
    ]

    // MARK: Body

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                StaggerIn(index: 0) { hero }
                StaggerIn(index: 1) { tabBar }
                StaggerIn(index: 2) { tabContent }
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
        .toolbar(.visible, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                NavigationLink(value: AeroRoute.settings) {
                    Image(systemName: "gearshape")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundStyle(Aero.text)
                }
                .buttonStyle(KineticPressStyle())
                .accessibilityLabel("Project settings")
            }
        }
    }

    // MARK: Hero

    private var hero: some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                Text(sample.name)
                    .font(Aero.headline())   // serif — hero voice
                    .foregroundStyle(Aero.text)
                Text(sample.detail)
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
                    .fixedSize(horizontal: false, vertical: true)
                VStack(alignment: .leading, spacing: Aero.Spacing.xs) {
                    Text("INSTRUCTIONS")
                        .font(Aero.label())
                        .foregroundStyle(Aero.textMuted)
                    Text(sample.instructions)
                        .font(Aero.caption())
                        .foregroundStyle(Aero.text)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(Aero.Spacing.s)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 12).fill(Aero.container))
            }
        }
    }

    // MARK: Tabs

    private var tabBar: some View {
        HStack(spacing: Aero.Spacing.s) {
            ForEach(DetailTab.allCases) { t in
                AeroChip(
                    text: t.rawValue,
                    selected: tab == t,
                    action: {
                        withAnimation(Aero.snappy) { tab = t }
                    }
                )
            }
        }
    }

    @ViewBuilder
    private var tabContent: some View {
        switch tab {
        case .chats: chatsTab
        case .files: filesTab
        case .activity: activityTab
        case .members: membersTab
        }
    }

    // MARK: Chats tab

    private var chatsTab: some View {
        VStack(spacing: Aero.Spacing.s) {
            ForEach(chats, id: \.id) { chat in
                NavigationLink(value: AeroRoute.chat(chat.id)) {
                    AeroListRow(
                        title: chat.title,
                        subtitle: chat.subtitle,
                        leading: {
                            Image(systemName: "bubble.left")
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

    // MARK: Files tab

    private var filesTab: some View {
        VStack(spacing: Aero.Spacing.s) {
            ForEach(files) { file in
                AeroListRow(
                    title: file.name,
                    subtitle: file.detail,
                    leading: {
                        Image(systemName: file.icon)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(Aero.text)
                            .frame(width: 36, height: 36)
                            .background(Circle().fill(Aero.containerHigh))
                    },
                    trailing: {
                        Image(systemName: "ellipsis")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(Aero.textMuted)
                    }
                )
            }
        }
    }

    // MARK: Activity tab

    private var activityTab: some View {
        VStack(spacing: Aero.Spacing.s) {
            ForEach(activity) { event in
                AeroListRow(
                    title: event.title,
                    subtitle: event.time,
                    leading: {
                        Image(systemName: "person.crop.circle")
                            .font(.system(size: 20))
                            .foregroundStyle(Aero.textMuted)
                            .frame(width: 36, height: 36)
                            .background(Circle().fill(Aero.containerHigh))
                    },
                    trailing: {
                        EmptyView()
                    }
                )
            }
        }
    }

    // MARK: Members tab

    private var membersTab: some View {
        VStack(spacing: Aero.Spacing.s) {
            ForEach(members) { member in
                AeroListRow(
                    title: member.name,
                    leading: {
                        Text(member.initials)
                            .font(Aero.label())
                            .foregroundStyle(Aero.text)
                            .frame(width: 36, height: 36)
                            .background(Circle().fill(Aero.containerHigh))
                    },
                    trailing: {
                        RoleChip(role: member.role, isOwner: member.isOwner)
                    }
                )
            }
        }
    }
}

// MARK: - Role chip (non-interactive)

private struct RoleChip: View {
    let role: String
    var isOwner: Bool = false

    var body: some View {
        Text(role)
            .font(Aero.label())
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(Capsule().fill(Aero.container))
            .foregroundStyle(isOwner ? Aero.accentDeep : Aero.textMuted)
    }
}
