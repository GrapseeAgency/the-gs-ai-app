import SwiftUI

/// Chats tab root — quick access cards, filter chips, conversation list.
/// List content is a static seed this pass (backend wiring of
/// `APIClient.shared.conversations()` noted in worklog, Task 6-e).
struct ChatsListView: View {

    private enum Filter: String, CaseIterable, Identifiable {
        case all = "All"
        case pinned = "Pinned"
        case unread = "Unread"
        var id: String { rawValue }
    }

    private struct SampleConversation: Identifiable {
        let id: String
        let title: String
        let preview: String
        let time: String
        var pinned: Bool = false
        var unread: Bool = false
    }

    @State private var filter: Filter = .all
    @State private var conversations: [SampleConversation] = [
        SampleConversation(id: "demo-1", title: "Q3 pricing strategy", preview: "You: send the revised deck?", time: "2h", pinned: true),
        SampleConversation(id: "demo-2", title: "Kyoto trip planning", preview: "GS: temples worth the early train…", time: "5h", unread: true),
        SampleConversation(id: "demo-3", title: "Kotlin coroutines debug", preview: "You: why does launch block here?", time: "1d"),
        SampleConversation(id: "demo-4", title: "Brand voice workshop", preview: "GS: three tone pillars emerged…", time: "3d")
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                header
                quickAccess
                filterChips
                conversationList
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
    }

    // MARK: Header

    private var header: some View {
        HStack(alignment: .center) {
            Text("Chats")
                .font(Aero.displayTitle())
                .foregroundStyle(Aero.text)
            Spacer()
            NavigationLink(value: AeroRoute.chatSearch) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundStyle(Aero.text)
            }
            .buttonStyle(KineticPressStyle())
            .padding(.trailing, 6)
            NavigationLink(value: AeroRoute.chat(nil)) {
                Image(systemName: "plus.circle.fill")
                    .font(.system(size: 24))
                    .foregroundStyle(Aero.accent)
            }
            .buttonStyle(KineticPressStyle())
        }
    }

    // MARK: Quick access

    private var quickAccess: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            SectionHeader(title: "Quick access")
            HStack(spacing: Aero.Spacing.s) {
                quickCard("Folders", icon: "folder", route: .chatFolders)
                quickCard("Archived", icon: "archivebox", route: .chatArchive)
                quickCard("Shared", icon: "person.2", route: .chatShared)
            }
        }
    }

    private func quickCard(_ title: String, icon: String, route: AeroRoute) -> some View {
        AeroCard {
            NavigationLink(value: route) {
                VStack(alignment: .leading, spacing: 6) {
                    Image(systemName: icon)
                        .font(.system(size: 16, weight: .medium))
                        .foregroundStyle(Aero.accent)
                    Text(title)
                        .font(Aero.label())
                        .foregroundStyle(Aero.text)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(KineticPressStyle())
        }
    }

    // MARK: Filter chips

    private var filterChips: some View {
        HStack(spacing: Aero.Spacing.s) {
            ForEach(Filter.allCases) { candidate in
                AeroChip(text: candidate.rawValue, selected: filter == candidate) {
                    withAnimation(Aero.snappy) { filter = candidate }
                }
            }
            Spacer()
        }
    }

    // MARK: Conversation list

    private var filtered: [SampleConversation] {
        conversations.filter { sample in
            switch filter {
            case .all: return true
            case .pinned: return sample.pinned
            case .unread: return sample.unread
            }
        }
    }

    private var conversationList: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            SectionHeader(title: "Recent")
            if filtered.isEmpty {
                EmptyStateView(
                    icon: "tray",
                    title: "Nothing here",
                    message: "No conversations match this filter."
                )
            } else {
                ForEach(filtered) { sample in
                    NavigationLink(value: AeroRoute.chat(sample.id)) {
                        AeroListRow(
                            title: sample.title,
                            subtitle: "\(sample.preview) · \(sample.time)",
                            leading: {
                                Image(systemName: "bubble.left")
                                    .font(.system(size: 14))
                                    .foregroundStyle(Aero.text)
                                    .frame(width: 36, height: 36)
                                    .background(Circle().fill(Aero.containerHigh))
                            },
                            trailing: {
                                if sample.pinned {
                                    Image(systemName: "star.fill")
                                        .font(.system(size: 13))
                                        .foregroundStyle(Aero.accent)
                                }
                            }
                        )
                    }
                    .buttonStyle(KineticPressStyle())
                }
            }
        }
    }
}
