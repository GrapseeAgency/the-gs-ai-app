import SwiftUI

/// One chip in the mock's five-up strip.
private struct GsInboxTab: Identifiable {
    let id: String
    let label: String
    let glyph: String
    let tone: GsWellTone
}

/// Notifications Inbox — docs/reference/pending/notifications-inbox.png.
///
/// The sheet's chips, per-tab counts, Today/Earlier split, unread dots and
/// "Mark all as read" all read `GsNoticeInbox`, whose writer is
/// `GsNotifier.record`: it honours the alert switches and records in-app
/// events, with a sign-in and onboarding completion as its real producers. No
/// banner dispatcher exists in this build, and nothing is seeded, so an install
/// with no events is genuinely empty and says "You're all caught up" — the
/// truth, not a designed state.
///
/// The Billing chip is always empty because nothing in this build produces a
/// billing notice: there is no payment processor to take a payment from.
struct NotificationsView: View {

    @ObservedObject private var inbox = GsNoticeInbox.shared

    /// The reader's clock. Production uses the wall clock; the render harness
    /// passes a fixed 18:00 so the Today/Earlier split and the "2h ago" labels
    /// cannot change with the hour the capture happens to run.
    var now: Date = Date()

    /// Explicit so the render harness can pass its fixed clock across the test
    /// module even though the state properties are private.
    init(now: Date = Date()) {
        self.now = now
    }

    @State private var tab = "all"
    @State private var earlierExpanded = false
    @State private var showFilter = false
    @State private var openNotice: GsNoticeEntry?

    private let tabs: [GsInboxTab] = [
        GsInboxTab(id: "all", label: "All", glyph: "bell", tone: .peach),
        GsInboxTab(id: "account", label: "Account", glyph: "user", tone: .blue),
        GsInboxTab(id: "billing", label: "Billing", glyph: "credit-card", tone: .mint),
        GsInboxTab(id: "messages", label: "Messages", glyph: "message-circle", tone: .rose),
        GsInboxTab(id: "updates", label: "Updates", glyph: "sparkles", tone: .lilac),
    ]

    private var filtered: [GsNoticeEntry] {
        // Newest first is the reader's contract, not the producer's: the store
        // prepends today, but a restored backup or a future producer may not,
        // and the Today/Earlier split below assumes the order.
        inbox.entries
            .filter { entry in
                switch tab {
                case "messages": return entry.kindId == "messages"
                case "updates": return entry.kindId == "product-updates" || entry.kindId == "ai-updates"
                case "account": return entry.kindId == "account-activity"
                case "billing": return false
                default: return true
                }
            }
            .sorted { $0.createdAtMillis > $1.createdAtMillis }
    }

    private var dayStart: Date {
        Calendar.current.startOfDay(for: now)
    }

    private var unreadCount: Int {
        filtered.filter { !$0.read }.count
    }

    var body: some View {
        GsSheetPage(
            title: "Notifications",
            subtitle: "Your updates, alerts and messages",
            onBack: { dismiss() },
            headerTrailing: {
                AnyView(
                    GsHeaderAction(
                        glyph: "adjustments",
                        label: "Filter notifications",
                        action: { showFilter = true })
                )
            }
        ) {
            chips
                .padding(.top, 4)

            if filtered.isEmpty {
                Spacer().frame(height: 18)
                caughtUp(
                    body: tab == "billing"
                        ? "Payments aren't processed in this build, so no billing notices exist yet."
                        : "Nexly has not sent you a notice yet.")
            } else {
                let today = filtered.filter { $0.createdAt >= dayStart }
                let earlier = filtered.filter { $0.createdAt < dayStart }
                if !today.isEmpty {
                    GsSheetGroupHeader("Today", trailing: AnyView(markAllButton))
                    rows(today)
                }
                if !earlier.isEmpty {
                    let shown = earlierExpanded ? earlier : Array(earlier.prefix(3))
                    GsSheetGroupHeader(
                        "Earlier",
                        caption: earlierExpanded ? "Show less" : "See all",
                        onCaption: { earlierExpanded.toggle() })
                    rows(shown)
                }
                Spacer().frame(height: 14)
                caughtUp(body: "No more notifications for now.")
            }
        }
        .sheet(item: $openNotice) { notice in
            noticeSheet(notice)
                .presentationDetents([.medium])
        }
        .sheet(isPresented: $showFilter) {
            filterSheet
                .presentationDetents([.medium])
        }
    }

    @Environment(\.dismiss) private var dismiss

    // MARK: Chips

    private var chips: some View {
        HStack(spacing: 7) {
            ForEach(tabs) { item in
                chip(item)
            }
        }
    }

    private func chip(_ item: GsInboxTab) -> some View {
        let selected = tab == item.id
        let count = inbox.counts[item.id] ?? 0
        return Button {
            tab = item.id
        } label: {
            VStack(spacing: 0) {
                NexlyGlyph(name: item.glyph, size: 15, color: item.tone.ink)
                Spacer().frame(height: 5)
                Text(item.label)
                    .font(.custom(Aero.productName(selected ? .semibold : .medium), size: 9, relativeTo: .caption2))
                    .foregroundStyle(Aero.text)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                Spacer().frame(height: 2)
                Text("\(count)")
                    .font(.custom(Aero.productName(.regular), size: 8, relativeTo: .caption2))
                    .foregroundStyle(Aero.textSecondary)
                    .padding(.horizontal, 5)
                    .padding(.vertical, 1)
                    .background(
                        Capsule().fill(
                            selected
                                ? item.tone.ink.opacity(0.14)
                                : Aero.outline.opacity(0.28))
                    )
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 9)
            .padding(.horizontal, 4)
            .background(
                RoundedRectangle(cornerRadius: 14)
                    .fill(
                        selected
                            ? item.tone.wash.opacity(0.55)
                            : Color.white.opacity(0.38))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .strokeBorder(
                        selected ? item.tone.ink.opacity(0.45) : Color.white.opacity(0.55),
                        lineWidth: 1)
            )
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityLabel("\(item.label), \(count)")
        .accessibilityAddTraits(selected ? AccessibilityTraits.isSelected : [])
    }

    // MARK: Rows

    private func rows(_ entries: [GsNoticeEntry]) -> some View {
        // One card per notice, as the mock draws it — a shared group card made
        // every unread row read as a table line.
        VStack(spacing: 5) {
            ForEach(entries) { entry in
                GsSheetRow(
                    title: entry.title,
                    detail: entry.body,
                    titleTrailing: AnyView(
                        GsTagPill(
                            text: styleFor(entry.kindId).label,
                            tone: styleFor(entry.kindId).tone.ink)),
                    action: {
                        inbox.markRead(id: entry.id)
                        openNotice = entry
                    }
                ) {
                    GsWellTile(
                        glyph: styleFor(entry.kindId).glyph,
                        tone: styleFor(entry.kindId).tone,
                        size: 34)
                } trailing: {
                    VStack(alignment: .trailing, spacing: 6) {
                        Text(relativeLabel(entry.createdAt, now: now))
                            .font(.custom(Aero.productName(.regular), size: 8.5, relativeTo: .caption2))
                            .foregroundStyle(Aero.textTertiary)
                        if entry.read {
                            NexlyGlyph(name: "chevron-right", size: 11, color: Aero.textTertiary)
                        } else {
                            Circle()
                                .fill(styleFor(entry.kindId).tone.ink)
                                .frame(width: 5, height: 5)
                        }
                    }
                }
                .gsSheetCard(corner: 20)
            }
        }
    }

    private var markAllButton: some View {
        Button {
            inbox.markAllRead()
        } label: {
            HStack(spacing: 4) {
                NexlyGlyph(
                    name: "circle-check",
                    size: 11,
                    color: unreadCount > 0 ? Aero.textSecondary : Aero.textTertiary.opacity(0.5))
                Text("Mark all as read")
                    .font(.custom(Aero.productName(.regular), size: 9, relativeTo: .caption2))
                    .foregroundStyle(unreadCount > 0 ? Aero.textSecondary : Aero.textTertiary.opacity(0.5))
            }
        }
        .buttonStyle(.plain)
        .disabled(unreadCount == 0)
        .accessibilityLabel("Mark all as read")
    }

    // MARK: Closing card

    private func caughtUp(body: String) -> some View {
        // Short rules flank the title itself, as the mock draws them; flexible
        // full-width rules disappeared into the cream glass.
        VStack(spacing: 2) {
            HStack(spacing: 10) {
                Rectangle()
                    .fill(Aero.textTertiary.opacity(0.35))
                    .frame(width: 28, height: 1)
                Text("You're all caught up!")
                    .font(.custom(Aero.productName(.medium), size: 10.5, relativeTo: .caption2))
                    .foregroundStyle(Aero.textSecondary)
                Rectangle()
                    .fill(Aero.textTertiary.opacity(0.35))
                    .frame(width: 28, height: 1)
            }
            Text(body)
                .font(.custom(Aero.productName(.regular), size: 8.5, relativeTo: .caption2))
                .foregroundStyle(Aero.textTertiary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity)
        .gsSheetCard(corner: 22)
    }

    // MARK: Sheets

    private func noticeSheet(_ notice: GsNoticeEntry) -> some View {
        let style = styleFor(notice.kindId)
        return ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 10) {
                    GsWellTile(glyph: style.glyph, tone: style.tone, size: 38)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(notice.title)
                            .font(.custom(Aero.productName(.semibold), size: 14, relativeTo: .headline))
                            .foregroundStyle(Aero.text)
                        Text("\(style.label) • \(relativeLabel(notice.createdAt, now: now))")
                            .font(.custom(Aero.productName(.regular), size: 9.5, relativeTo: .caption2))
                            .foregroundStyle(Aero.textTertiary)
                    }
                    Spacer()
                }
                Text(notice.body)
                    .font(.custom(Aero.productName(.regular), size: 12, relativeTo: .caption))
                    .foregroundStyle(Aero.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                Button {
                    openNotice = nil
                    NotificationCenter.default.post(name: .gsOpenNotificationSettings, object: nil)
                } label: {
                    Text("Notification settings")
                        .font(Aero.button())
                        .foregroundStyle(Aero.accent)
                }
                .buttonStyle(.plain)
                Spacer()
            }
            .padding(20)
        }
    }

    private var filterSheet: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 4) {
                Text("Filter notifications")
                    .font(.custom(Aero.productName(.semibold), size: 14, relativeTo: .headline))
                    .foregroundStyle(Aero.text)
                    .padding(.bottom, 6)
                ForEach(tabs) { item in
                    Button {
                        tab = item.id
                        showFilter = false
                    } label: {
                        HStack(spacing: 10) {
                            GsWellTile(glyph: item.glyph, tone: item.tone, size: 30)
                            Text(item.label)
                                .font(.custom(Aero.productName(.medium), size: 11, relativeTo: .caption))
                                .foregroundStyle(Aero.text)
                            Spacer()
                            Text("\(inbox.counts[item.id] ?? 0)")
                                .font(.custom(Aero.productName(.regular), size: 9, relativeTo: .caption2))
                                .foregroundStyle(Aero.textTertiary)
                        }
                        .padding(.vertical, 7)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(20)
        }
    }

    // MARK: Helpers

    private func styleFor(_ kindId: String) -> (glyph: String, tone: GsWellTone, label: String) {
        switch kindId {
        case "messages": return ("message-circle", .rose, "Messages")
        case "ai-updates": return ("sparkles", .lilac, "Update")
        case "product-updates": return ("settings", .blue, "Updates")
        case "account-activity": return ("user", .peach, "Account")
        case "tips": return ("bulb", .peach, "Tips")
        default: return ("bell", .blue, "Update")
        }
    }

    private func relativeLabel(_ date: Date, now: Date) -> String {
        let seconds = max(0, now.timeIntervalSince(date))
        let minutes = Int(seconds / 60)
        if minutes < 1 { return "now" }
        if minutes < 60 { return "\(minutes)m ago" }
        let hours = minutes / 60
        if hours < 24 { return "\(hours)h ago" }
        return "\(hours / 24)d ago"
    }
}

extension Notification.Name {
    /// Posted by the inbox's "Notification settings" action. The root shell
    /// listens and pushes `.notificationsAlerts`; a sheet cannot reach the
    /// router directly.
    static let gsOpenNotificationSettings = Notification.Name("gs.openNotificationSettings")
}
