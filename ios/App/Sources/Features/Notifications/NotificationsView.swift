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
/// "Mark all as read" all read `GsNoticeInbox`, whose writers are
/// `GsNotifier.post` (a banner the system accepted) and `GsNotifier.record`
/// (an in-app event: a sign-in, a finished onboarding, a version change).
/// Both honour the alert switches; `post` additionally honours Quiet Hours,
/// DND and the foreground gate. No banner dispatcher existed when this page
/// was first built — it does now, and the row is still written only after the
/// device actually showed something.
///
/// The strip carries one chip per kind this build can actually send. It used
/// to carry a Billing chip that could never fill — there is no payment
/// processor here — while Tips, a real kind with its own switch in Notification
/// settings, had no chip at all: a tips notice counted towards All and could
/// never be filtered to.
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
    @EnvironmentObject private var router: Router

    private let tabs: [GsInboxTab] = [
        GsInboxTab(id: "all", label: "All", glyph: "bell", tone: .peach),
        GsInboxTab(id: "account", label: "Account", glyph: "user", tone: .blue),
        GsInboxTab(id: "tips", label: "Tips", glyph: "bulb", tone: .peach),
        GsInboxTab(id: "messages", label: "Messages", glyph: "message-circle", tone: .rose),
        GsInboxTab(id: "updates", label: "Updates", glyph: "sparkles", tone: .lilac),
    ]

    /// The kinds a tab covers; nil = every kind (the All tab).
    private func kindsFor(_ id: String) -> Set<String>? {
        switch id {
        case "messages": return ["messages"]
        case "updates": return ["product-updates", "ai-updates"]
        case "account": return ["account-activity"]
        case "tips": return ["tips"]
        default: return nil
        }
    }

    private var filtered: [GsNoticeEntry] {
        // Newest first is the reader's contract, not the producer's: the store
        // prepends today, but a restored backup or a future producer may not,
        // and the Today/Earlier split below assumes the order.
        let kinds = kindsFor(tab)
        return inbox.entries
            .filter { kinds == nil || kinds!.contains($0.kindId) }
            .sorted { $0.createdAtMillis > $1.createdAtMillis }
    }

    private var unreadAnywhere: Int {
        inbox.entries.filter { !$0.read }.count
    }

    private var tabLabel: String {
        tabs.first { $0.id == tab }?.label ?? "All"
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
                // An empty TAB is not an empty INBOX. The old copy said "Nexly
                // has not sent you a notice yet" while nine message notices sat
                // one chip to the right — the page contradicted its own counts.
                if inbox.entries.isEmpty {
                    caughtUp(
                        title: "You're all caught up!",
                        body: "Nothing has been sent yet. A notice only lands here after this device actually showed one — a finished reply, a sign-in, a new on-device model.")
                } else {
                    caughtUp(
                        title: "Nothing in \(tabLabel) yet",
                        body: inbox.entries.count == 1
                            ? "You have 1 notice in another kind."
                            : "You have \(inbox.entries.count) notices in other kinds.",
                        actionLabel: "Show all notifications",
                        action: { tab = "all" })
                }
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
                // "You're all caught up!" used to sit under a list of unread
                // rows. It now only appears when it is true for what is on
                // screen, and names the unread mail it is not counting.
                if unreadCount == 0 {
                    if unreadAnywhere > 0 {
                        caughtUp(
                            title: "You're all caught up here",
                            body: unreadAnywhere == 1
                                ? "1 unread notice is waiting in another kind."
                                : "\(unreadAnywhere) unread notices are waiting in other kinds.",
                            actionLabel: "Show all notifications",
                            action: { tab = "all" })
                    } else {
                        caughtUp(title: "You're all caught up!", body: "No more notifications for now.")
                    }
                }
            }

            // The inbox could not reach its own settings: the only route was
            // Settings → Notifications, three taps from the page whose contents
            // they govern.
            Spacer().frame(height: 14)
            GsSheetGroupHeader("Settings", caption: "Where these come from")
            GsSheetRow(
                title: "Notification settings",
                detail: "Choose which kinds Nexly may send, and when",
                action: {
                    NotificationCenter.default.post(name: .gsOpenNotificationSettings, object: nil)
                }
            ) {
                GsWellTile(glyph: "bell", tone: .blue, size: 28)
            } trailing: {
                NexlyGlyph(name: "chevron-right", size: 12, color: Aero.textTertiary)
            }
            .gsSheetCard(corner: 20)
        }
        // The filter was a half-height sheet — a menu wearing a page's clothes.
        // It is a glass chooser now, and a notice is a pushed page.
        .gsDialog(isPresented: showFilter) {
            GsChoiceDialog(
                title: "Filter notifications",
                subtitle: "Every kind Nexly can send, counted from what it actually sent",
                options: tabs.map { item in
                    GsChoice(
                        id: item.id,
                        label: item.label,
                        detail: "\(inbox.counts[item.id] ?? 0) notices",
                        glyph: item.glyph,
                        tone: item.tone,
                        selected: tab == item.id)
                },
                onSelect: { choice in
                    tab = choice.id
                    showFilter = false
                },
                onDismiss: { showFilter = false })
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
            .gsArgentComponentBorder(RoundedRectangle(cornerRadius: 14), fallback: selected ? item.tone.ink.opacity(0.45) : Color.white.opacity(0.55), preserveBorder: selected)
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
                            text: GsNoticeStyle.forKind(entry.kindId).label,
                            tone: GsNoticeStyle.forKind(entry.kindId).tone.ink)),
                    action: {
                        inbox.markRead(id: entry.id)
                        router.path.append(.notificationDetail(entry.id))
                    }
                ) {
                    GsWellTile(
                        glyph: GsNoticeStyle.forKind(entry.kindId).glyph,
                        tone: GsNoticeStyle.forKind(entry.kindId).tone,
                        size: 34)
                } trailing: {
                    VStack(alignment: .trailing, spacing: 6) {
                        Text(GsNoticeStyle.relative(entry.createdAt, now: now))
                            .font(.custom(Aero.productName(.regular), size: 8.5, relativeTo: .caption2))
                            .foregroundStyle(Aero.textTertiary)
                        if entry.read {
                            NexlyGlyph(name: "chevron-right", size: 11, color: Aero.textTertiary)
                        } else {
                            Circle()
                                .fill(GsNoticeStyle.forKind(entry.kindId).tone.ink)
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
            // Scoped to this tab: the control sits in a filtered list, so a
            // global sweep cleared tabs the reader never opened.
            inbox.markAllRead(kinds: kindsFor(tab))
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

    private func caughtUp(
        title: String,
        body: String,
        actionLabel: String? = nil,
        action: (() -> Void)? = nil
    ) -> some View {
        // Short rules flank the title itself, as the mock draws them; flexible
        // full-width rules disappeared into the cream glass.
        VStack(spacing: 2) {
            HStack(spacing: 10) {
                Rectangle()
                    .fill(Aero.textTertiary.opacity(0.35))
                    .frame(width: 28, height: 1)
                Text(title)
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
            if let actionLabel, let action {
                Spacer().frame(height: 8)
                Button(action: action) {
                    Text(actionLabel)
                        .font(.custom(Aero.productName(.medium), size: 10, relativeTo: .caption2))
                        .foregroundStyle(GsWellTone.peach.ink)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Capsule().fill(GsWellTone.peach.wash.opacity(0.55)))
                }
                .buttonStyle(KineticPressStyle())
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity)
        .gsSheetCard(corner: 22)
    }

}

extension Notification.Name {
    /// Posted by the inbox's "Notification settings" action. The root shell
    /// listens and pushes `.notificationsAlerts`; a sheet cannot reach the
    /// router directly.
    static let gsOpenNotificationSettings = Notification.Name("gs.openNotificationSettings")
}
