import SwiftUI

/// Notification centre — honest empty state (Phase 2). There is no
/// notification backend yet, so nothing is listed: the 8 hardcoded samples
/// (and their deep links) are gone. When real updates exist they will appear
/// here; until then the screen tells the truth.
struct NotificationsView: View {

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                header
                EmptyStateView(
                    icon: "bell",
                    title: "You're all caught up",
                    message: "Updates about your conversations will appear here."
                )
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
    }

    // MARK: Header

    private var header: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.xs) {
            Text("Notifications")
                .font(Aero.displayTitle())
                .foregroundStyle(Aero.text)
            Text("Updates about your conversations.")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
        }
    }
}
