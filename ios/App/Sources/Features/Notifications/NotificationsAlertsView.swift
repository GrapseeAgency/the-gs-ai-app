import SwiftUI

/// Notifications & Alerts — docs/reference/pending/notifications-alerts.png.
///
/// Every group, row, well tone, caption and the closing card are the mock's, in
/// the mock's order and at the sheet's own scale (13/9 group headers, 10/8
/// rows, the 34×20 switch). What the mock cannot tell you is which of its
/// switches this build obeys, so the rule here is the same as Android's:
/// a switch is drawn live only where the preference is one the store keeps and
/// the notice path will read, and a channel this build does not have is drawn
/// disabled and off rather than as a live-looking dead control.
///
/// Two honest exceptions:
///  - Email Notifications, disabled and off: this build sends no mail at all;
///  - Push Notifications, which persists the preference but cannot deliver a
///    banner until a notification dispatcher exists on iOS. Its subtitle says
///    what it controls, not that a banner will arrive today. The in-app kinds
///    are no longer dead: `GsNotifier.record` reads this master switch and the
///    per-kind switches before it writes an Inbox row, and a sign-in is the
///    first real producer.
struct NotificationsAlertsView: View {

    @ObservedObject private var settings = SettingsStore.shared
    @State private var editQuietHours = false

    var body: some View {
        GsSheetPage(
            title: "Notifications & Alerts",
            subtitle: "Stay updated with what matters",
            onBack: { dismiss() }
        ) {
            // ---- Master ------------------------------------------------------
            GsGroupCard {
                GsSheetToggle(
                    title: "Notifications",
                    detail: "Control what you want to be notified about.",
                    isOn: $settings.pushNotifications
                ) {
                    GsWellTile(glyph: "bell", tone: .peach, size: 40)
                }
            }

            // ---- Notification Types -----------------------------------------
            GsSheetGroupHeader("Notification Types", caption: "Choose what to receive")
            GsGroupCard {
                typeRow(
                    "New Messages",
                    "Get notified when you receive a new message or response from Nexly.",
                    "message-circle", .rose, $settings.noticeMessages)
                divider
                typeRow(
                    "AI Updates",
                    "Be the first to know about new features, model updates and improvements.",
                    "sparkles", .lilac, $settings.noticeAiUpdates)
                divider
                typeRow(
                    "Tips & Suggestions",
                    "Receive helpful tips, shortcuts and personalized suggestions to get more from Nexly.",
                    "bulb", .peach, $settings.noticeTips)
                divider
                typeRow(
                    "Account Activity",
                    "Get alerts about sign-ins, security changes and important account events.",
                    "shield-check", .mint, $settings.noticeAccountActivity)
                divider
                typeRow(
                    "Product Updates",
                    "Stay informed about new tools, features and exclusive releases.",
                    "star", .lilac, $settings.noticeProductUpdates)
            }

            // ---- Notification Channels --------------------------------------
            GsSheetGroupHeader("Notification Channels", caption: "How you want to be notified")
            GsGroupCard {
                GsSheetToggle(
                    title: "Push Notifications",
                    detail: "Receive notifications on your device.",
                    isOn: $settings.pushNotifications
                ) {
                    GsWellTile(glyph: "bell", tone: .peach, size: 40)
                }
                divider
                // No delivery path exists: this build sends no email. Drawn
                // disabled and off is the truth; a live-looking switch is the
                // dead-toggle failure the honesty contract forbids.
                GsSheetToggle(
                    title: "Email Notifications",
                    detail: "Get updates via email.",
                    isOn: .constant(false),
                    enabled: false
                ) {
                    GsWellTile(glyph: "mail", tone: .lilac, size: 40)
                }
                divider
                GsSheetToggle(
                    title: "In-App Notifications",
                    detail: "See notifications within the app.",
                    isOn: $settings.noticeInApp
                ) {
                    GsWellTile(glyph: "message", tone: .mint, size: 40)
                }
            }

            // ---- Quiet Hours -------------------------------------------------
            GsSheetGroupHeader("Quiet Hours", caption: "Pause notifications when you need rest")
            GsGroupCard {
                // iOS cannot set the system Focus from inside an app, so this
                // is Nexly's own silence, not a claim to own the device filter.
                GsSheetToggle(
                    title: "Do Not Disturb",
                    detail: "Silence Nexly's notices during selected hours.",
                    isOn: $settings.noticeDnd
                ) {
                    GsWellTile(glyph: "moon", tone: .lilac, size: 40)
                }
                divider
                GsSheetRow(
                    title: "Quiet Hours",
                    detail: Self.windowLabel(
                        settings.quietStartMinute, settings.quietEndMinute),
                    action: { editQuietHours = true }
                ) {
                    GsWellTile(glyph: "clock", tone: .rose, size: 40)
                } trailing: {
                    GsSheetChevron()
                }
            }

            // ---- Closing card -------------------------------------------------
            Spacer().frame(height: 14)
            HStack(spacing: 10) {
                Image("GSHomeLotusNew")
                    .resizable()
                    .scaledToFill()
                    .frame(width: 38, height: 38)
                    .clipShape(Circle())
                Text("You're in control. Notifications help you stay connected, without getting in the way.")
                    .font(.custom(Aero.productName(.regular), size: 11.5, relativeTo: .caption))
                    .foregroundStyle(Aero.textTertiary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 13)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 20)
                    .fill(Aero.glassSurface.opacity(0.55))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .strokeBorder(.white.opacity(0.55), lineWidth: 1)
            )
        }
        .sheet(isPresented: $editQuietHours) {
            QuietHoursSheet()
                .presentationDetents([.medium])
        }
    }

    @Environment(\.dismiss) private var dismiss

    private var divider: some View {
        Rectangle()
            .fill(Aero.divider.opacity(0.85))
            .frame(height: 1.0 / 3.0)
            .padding(.leading, 50)
            .padding(.trailing, 12)
    }

    private func typeRow(
        _ title: String,
        _ detail: String,
        _ glyph: String,
        _ tone: GsWellTone,
        _ binding: Binding<Bool>
    ) -> some View {
        GsSheetToggle(title: title, detail: detail, isOn: binding) {
            GsWellTile(glyph: glyph, tone: tone, size: 40)
        }
    }

    /// "10:00 PM – 7:00 AM", built from the stored window.
    static func windowLabel(_ startMinute: Int, _ endMinute: Int) -> String {
        "\(clockLabel(startMinute)) – \(clockLabel(endMinute))"
    }

    static func clockLabel(_ minuteOfDay: Int) -> String {
        let hour = (minuteOfDay / 60) % 24
        let minute = minuteOfDay % 60
        let suffix = hour < 12 ? "AM" : "PM"
        let h12 = hour == 0 ? 12 : (hour > 12 ? hour - 12 : hour)
        return String(format: "%d:%02d %@", h12, minute, suffix)
    }
}

/// The window editor: one enable switch and two half-hour steppers. The
/// reference draws this row as a plain window editor with a chevron, so the
/// switch lives in here rather than being invented on the row.
private struct QuietHoursSheet: View {

    @ObservedObject private var settings = SettingsStore.shared
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text("Quiet Hours")
                        .font(.custom(Aero.productName(.semibold), size: 15, relativeTo: .headline))
                        .foregroundStyle(Aero.text)
                    Spacer()
                    GsSheetSwitch(isOn: $settings.quietHoursEnabled)
                }
                stepperRow(
                    "From",
                    value: settings.quietStartMinute,
                    onMinus: { settings.quietStartMinute = step(settings.quietStartMinute, -30) },
                    onPlus: { settings.quietStartMinute = step(settings.quietStartMinute, 30) })
                stepperRow(
                    "Until",
                    value: settings.quietEndMinute,
                    onMinus: { settings.quietEndMinute = step(settings.quietEndMinute, -30) },
                    onPlus: { settings.quietEndMinute = step(settings.quietEndMinute, 30) })
                Text("Nexly holds every notice back inside this window. Messages you miss are still there when it ends.")
                    .font(.custom(Aero.productName(.regular), size: 11.5, relativeTo: .caption))
                    .foregroundStyle(Aero.textTertiary)
                    .fixedSize(horizontal: false, vertical: true)
                Button {
                    dismiss()
                } label: {
                    Text("Done")
                        .font(Aero.button())
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Capsule().fill(ExplorePalette.peachDeep))
                }
                .buttonStyle(KineticPressStyle())
            }
            .padding(20)
        }
    }

    private func stepperRow(
        _ label: String,
        value: Int,
        onMinus: @escaping () -> Void,
        onPlus: @escaping () -> Void
    ) -> some View {
        HStack {
            Text(label)
                .font(.custom(Aero.productName(.regular), size: 13, relativeTo: .body))
                .foregroundStyle(Aero.textSecondary)
            Spacer()
            Button(action: onMinus) {
                NexlyGlyph(name: "minus", size: 13, color: Aero.text)
                    .frame(width: 30, height: 30)
                    .background(Circle().fill(Aero.raisedSurface))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Earlier")
            Text(NotificationsAlertsView.clockLabel(value))
                .font(.custom(Aero.productName(.medium), size: 14, relativeTo: .body))
                .foregroundStyle(Aero.text)
                .frame(minWidth: 88)
            Button(action: onPlus) {
                NexlyGlyph(name: "plus", size: 13, color: Aero.text)
                    .frame(width: 30, height: 30)
                    .background(Circle().fill(Aero.raisedSurface))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Later")
        }
    }

    private func step(_ minute: Int, _ delta: Int) -> Int {
        let next = minute + delta
        return ((next % 1440) + 1440) % 1440
    }
}
