import SwiftUI

/// Profile — identity, monthly AI usage, account rows.
struct ProfileView: View {

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                identity
                usageCard
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
                NavigationLink(value: AeroRoute.notifications) {
                    Image(systemName: "bell")
                        .font(.system(size: 16))
                        .foregroundStyle(Aero.text)
                }
            }
        }
    }

    // MARK: Identity

    private var identity: some View {
        VStack(spacing: Aero.Spacing.s) {
            ZStack {
                Circle()
                    .fill(Aero.accent.opacity(0.14))
                    .frame(width: 84, height: 84)
                Text("GA")
                    .font(Aero.headline())
                    .foregroundStyle(Aero.accent)
            }
            Text("Grapsee Admin")
                .font(Aero.headline())
                .foregroundStyle(Aero.text)
            Text("graphesee@gmail.com")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: Monthly AI usage (aurora progress is an allowed AI-active moment)

    private var usageCard: some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.m) {
                Text("Monthly AI usage")
                    .font(Aero.title())
                    .foregroundStyle(Aero.text)
                HStack(spacing: Aero.Spacing.xl) {
                    usageStat("1,284", "messages")
                    usageStat("45m", "voice")
                    usageStat("32", "images")
                }
                GeometryReader { proxy in
                    ZStack(alignment: .leading) {
                        Capsule()
                            .fill(Aero.container)
                        Capsule()
                            .fill(LinearGradient(colors: Aero.aurora, startPoint: .leading, endPoint: .trailing))
                            .frame(width: proxy.size.width * 0.68)
                    }
                }
                .frame(height: 8)
                Text("Pro plan · resets in 12 days")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
            }
        }
    }

    private func usageStat(_ value: String, _ label: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(Aero.title())
                .foregroundStyle(Aero.text)
            Text(label)
                .font(Aero.label())
                .foregroundStyle(Aero.textMuted)
        }
    }

    // MARK: Account rows

    private var accountRows: some View {
        VStack(spacing: Aero.Spacing.s) {
            accountRow("Personal info", icon: "person") {
                Image(systemName: "chevron.right")
                    .font(.system(size: 12))
                    .foregroundStyle(Aero.textMuted)
            }
            accountRow("Subscription", icon: "creditcard") {
                AeroChip(text: "Pro", selected: true)
            }
            accountRow("Connected services", icon: "link") {
                Image(systemName: "chevron.right")
                    .font(.system(size: 12))
                    .foregroundStyle(Aero.textMuted)
            }
            accountRow("Devices", icon: "desktopcomputer") {
                Text("3")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
            }
            accountRow("Security", icon: "lock.shield") {
                Text("Strong")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
            }
            accountRow("Sessions", icon: "clock") {
                Text("2 active")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
            }
        }
    }

    private func accountRow<Trailing: View>(
        _ title: String,
        icon: String,
        @ViewBuilder trailing: () -> Trailing
    ) -> some View {
        AeroListRow(
            title: title,
            leading: { leadingIcon(icon) },
            trailing: trailing
        )
    }

    private func leadingIcon(_ name: String) -> some View {
        Image(systemName: name)
            .font(.system(size: 14))
            .foregroundStyle(Aero.accent)
            .frame(width: 34, height: 34)
            .background(Circle().fill(Aero.container))
    }
}
