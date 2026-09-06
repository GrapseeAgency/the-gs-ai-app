import SwiftUI

/// Archived conversations — unarchive is local-only this pass (no DELETE of
/// the archived flag server-side yet; noted in worklog, Task 6-e).
struct ArchivedChatsView: View {

    private struct ArchivedItem: Identifiable {
        let id = UUID()
        let title: String
        let subtitle: String
    }

    @State private var items: [ArchivedItem] = [
        ArchivedItem(title: "Q3 pricing strategy", subtitle: "Archived 2 weeks ago · 24 messages"),
        ArchivedItem(title: "Kyoto trip planning", subtitle: "Archived last month · 41 messages"),
        ArchivedItem(title: "Old design critique", subtitle: "Archived last month · 9 messages"),
        ArchivedItem(title: "Weekly sync notes", subtitle: "Archived 2 months ago · 12 messages")
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                Text("Archived")
                    .font(Aero.displayTitle())
                    .foregroundStyle(Aero.text)
                Text("Archived chats stay searchable but leave your main list.")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)

                if items.isEmpty {
                    EmptyStateView(
                        icon: "archivebox",
                        title: "Nothing archived",
                        message: "Conversations you archive will rest here until you bring them back."
                    )
                } else {
                    VStack(spacing: Aero.Spacing.s) {
                        ForEach(items) { item in
                            AeroListRow(
                                title: item.title,
                                subtitle: item.subtitle,
                                leading: {
                                    Image(systemName: "archivebox")
                                        .font(.system(size: 14))
                                        .foregroundStyle(Aero.text)
                                        .frame(width: 36, height: 36)
                                        .background(Circle().fill(Aero.containerHigh))
                                },
                                trailing: {
                                    Button {
                                        unarchive(item)
                                    } label: {
                                        Image(systemName: "arrow.up.circle")
                                            .font(.system(size: 18))
                                            .foregroundStyle(Aero.accent)
                                    }
                                    .buttonStyle(KineticPressStyle())
                                }
                            )
                        }
                    }
                }
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
    }

    private func unarchive(_ item: ArchivedItem) {
        withAnimation(Aero.snappy) {
            items.removeAll { $0.id == item.id }
        }
    }
}
