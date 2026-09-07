import SwiftUI

/// Inline sample notification.
/// `type` ∈ task / file / assistant / share / project / system / security.
struct NotificationSample: Identifiable {
    let id: String
    let type: String
    let title: String
    let body: String
    let time: String
    var unread: Bool

    static let samples: [NotificationSample] = [
        NotificationSample(id: "n1", type: "task", title: "Export finished", body: "Your chat archive is ready to download.", time: "2m ago", unread: true),
        NotificationSample(id: "n2", type: "file", title: "Report summarised", body: "Q3 revenue report — 12 pages, 6 key points extracted.", time: "26m ago", unread: true),
        NotificationSample(id: "n3", type: "assistant", title: "Writing Coach updated", body: "New conversation starters and a calmer tone profile.", time: "1h ago", unread: true),
        NotificationSample(id: "n4", type: "security", title: "New sign-in", body: "MacBook Pro · London, UK — was this you?", time: "2h ago", unread: false),
        NotificationSample(id: "n5", type: "share", title: "Shared conversation", body: "Maya shared “Aurora launch plan” with you.", time: "5h ago", unread: false),
        NotificationSample(id: "n6", type: "project", title: "Aurora site activity", body: "Sam added 3 files to the Aurora site project.", time: "Yesterday", unread: false),
        NotificationSample(id: "n7", type: "system", title: "New in GS 2.1", body: "Voice mode wakes faster and interrupts cleanly.", time: "Yesterday", unread: false),
        NotificationSample(id: "n8", type: "system", title: "Password changed", body: "Your password was updated successfully.", time: "Tuesday", unread: false)
    ]
}

/// Notification centre — Today / Earlier groups, mark all read.
struct NotificationsView: View {

    @EnvironmentObject private var router: Router
    @State private var samples = NotificationSample.samples

    private var today: [NotificationSample] { Array(samples.prefix(4)) }
    private var earlier: [NotificationSample] { Array(samples.dropFirst(4)) }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: Aero.Spacing.s) {
                header
                SectionHeader(title: "Today")
                    .padding(.top, Aero.Spacing.s)
                ForEach(today) { sample in
                    NotificationRow(sample: sample, highlighted: sample.type == "security") {
                        open(sample)
                    }
                }
                SectionHeader(title: "Earlier")
                    .padding(.top, Aero.Spacing.m)
                ForEach(earlier) { sample in
                    NotificationRow(sample: sample, highlighted: sample.type == "security") {
                        open(sample)
                    }
                }
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button("Mark all read") {
                    for index in samples.indices {
                        samples[index].unread = false
                    }
                }
                .font(Aero.label())
                .foregroundStyle(Aero.accent)
            }
        }
    }

    // MARK: Open — mark read, then deep-link to the related surface

    /// Type-based deep link: finished work opens a chat, activity opens its hub.
    private func route(for type: String) -> AeroRoute {
        switch type {
        case "assistant": return .explore
        case "share", "project": return .projects
        case "system", "security": return .settings
        default: return .chat(nil) // task, file — continue the work in a chat
        }
    }

    private func open(_ sample: NotificationSample) {
        if let index = samples.firstIndex(where: { $0.id == sample.id }) {
            samples[index].unread = false
        }
        router.path.append(route(for: sample.type))
    }

    // MARK: Header

    private var header: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.xs) {
            Text("Notifications")
                .font(Aero.displayTitle())
                .foregroundStyle(Aero.text)
            Text("Task, file and account activity.")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
        }
    }
}

// MARK: - Row

private struct NotificationRow: View {
    let sample: NotificationSample
    var highlighted: Bool = false
    var onTap: () -> Void = {}

    var body: some View {
        Button(action: onTap) {
            AeroCard {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: Self.icon(for: sample.type))
                    .font(.system(size: 14))
                    .foregroundStyle(Aero.accent)
                    .frame(width: 36, height: 36)
                    .background(Circle().fill(Aero.container))
                VStack(alignment: .leading, spacing: 2) {
                    Text(sample.title)
                        .font(Aero.title())
                        .foregroundStyle(Aero.text)
                        .lineLimit(1)
                    Text(sample.body)
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                        .lineLimit(2)
                    Text(sample.time)
                        .font(Aero.label())
                        .foregroundStyle(Aero.textMuted)
                }
                Spacer(minLength: 0)
                if sample.unread {
                    Circle()
                        .fill(Aero.accent)
                        .frame(width: 8, height: 8)
                }
            }
        }
        .overlay {
            if highlighted {
                RoundedRectangle(cornerRadius: Aero.Radius.card)
                    .stroke(Aero.accent, lineWidth: 1.5)
            }
        }
        .buttonStyle(KineticPressStyle())
        }
    }

    static func icon(for type: String) -> String {
        switch type {
        case "task": return "bell.fill"
        case "file": return "doc.text"
        case "assistant": return "smarttoy"
        case "share": return "person.2"
        case "project": return "folder"
        case "system": return "info"
        case "security": return "shield.fill"
        default: return "bell.fill"
        }
    }
}
