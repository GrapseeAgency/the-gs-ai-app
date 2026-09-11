import SwiftUI

/// Profile — minimal and honest (Phase 2): the REAL stored identity
/// (AccountStore — the name/email the reader actually gave; a neutral
/// fallback when they didn't) plus real destinations only. No usage card,
/// no device/session/security fiction — those numbers were never measured.
struct ProfileView: View {

    @ObservedObject private var account = AccountStore.shared

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                identity
                VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                    SectionHeader(title: "Account")
                    accountRows
                }
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
        .toolbar {
            ToolbarItemGroup(placement: .navigationBarTrailing) {
                NavigationLink(value: AeroRoute.settings) {
                    Image(systemName: "gearshape")
                        .font(.system(size: 16))
                        .foregroundStyle(Aero.text)
                }
                .accessibilityLabel("Settings")
                NavigationLink(value: AeroRoute.notifications) {
                    Image(systemName: "bell")
                        .font(.system(size: 16))
                        .foregroundStyle(Aero.text)
                }
                .accessibilityLabel("Notifications")
            }
        }
    }

    // MARK: Identity (real, never invented)

    private var identity: some View {
        VStack(spacing: Aero.Spacing.s) {
            ZStack {
                Circle()
                    .fill(Aero.accent.opacity(0.14))
                    .frame(width: 84, height: 84)
                if account.displayName.isEmpty {
                    Image(systemName: "person.fill")
                        .font(.system(size: 28))
                        .foregroundStyle(Aero.accent)
                } else {
                    Text(initials)
                        .font(Aero.headline())
                        .foregroundStyle(Aero.accent)
                }
            }
            Text(account.displayName.isEmpty ? "Account" : account.displayName)
                .font(Aero.headline())
                .foregroundStyle(Aero.text)
            if !account.email.isEmpty {
                Text(account.email)
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
            }
        }
        .frame(maxWidth: .infinity)
    }

    /// Up to two leading initials of the stored display name — derived only;
    /// the empty-name case never reaches here.
    private var initials: String {
        account.displayName
            .split(separator: " ")
            .prefix(2)
            .map { String($0.prefix(1)).uppercased() }
            .joined()
    }

    // MARK: Account rows (real destinations only)

    private var accountRows: some View {
        VStack(spacing: Aero.Spacing.s) {
            NavigationLink(value: AeroRoute.settings) {
                AeroListRow(
                    title: "Settings",
                    leading: {
                        Image(systemName: "gearshape")
                            .font(.system(size: 14))
                            .foregroundStyle(Aero.accent)
                            .frame(width: 34, height: 34)
                            .background(Circle().fill(Aero.container))
                    },
                    trailing: {
                        Image(systemName: "chevron.right")
                            .font(.system(size: 12))
                            .foregroundStyle(Aero.textMuted)
                    }
                )
            }
            .buttonStyle(KineticPressStyle())
            NavigationLink(value: AeroRoute.billing) {
                AeroListRow(
                    title: "Billing",
                    leading: {
                        Image(systemName: "creditcard")
                            .font(.system(size: 14))
                            .foregroundStyle(Aero.accent)
                            .frame(width: 34, height: 34)
                            .background(Circle().fill(Aero.container))
                    },
                    trailing: {
                        Image(systemName: "chevron.right")
                            .font(.system(size: 12))
                            .foregroundStyle(Aero.textMuted)
                    }
                )
            }
            .buttonStyle(KineticPressStyle())
        }
    }
}
