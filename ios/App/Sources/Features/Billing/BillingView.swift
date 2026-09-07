import SwiftUI

/// Billing — standalone sheet: current plan + usage, credits, plans,
/// payment methods, invoices and account management.
/// Presented by the host (sheet); owns its own header chrome.
struct BillingView: View {

    @Environment(\.dismiss) private var dismiss

    private static let dangerRed = Color(red: 0.9, green: 0.28, blue: 0.28)
    private static let checkoutToast = "Checkout arrives with the App Store build"

    /// Fresh sample dates: the renewal anchor is the 12th of next month and
    /// the invoices are the three most recent completed billing months —
    /// computed so the samples never go stale (they used to say Aug 2025).
    // Cached once — these were fresh DateFormatters on every body pass.
    private static let monthLabelFormatter: DateFormatter = {
        let df = DateFormatter()
        df.dateFormat = "d MMM yyyy"
        df.locale = Locale(identifier: "en_GB")
        return df
    }()
    private static let invoiceMonthFormatter: DateFormatter = {
        let df = DateFormatter()
        df.dateFormat = "MMM yyyy"
        df.locale = Locale(identifier: "en_GB")
        return df
    }()

    /// the invoices are the three most recent completed billing months —
    /// computed so the samples never go stale (they used to say Aug 2025).
    private static var renewalDateText: String {
        let cal = Calendar.current
        let next = cal.date(byAdding: .month, value: 1, to: Date()) ?? Date()
        var comps = cal.dateComponents([.year, .month], from: next)
        comps.day = 12
        return monthLabelFormatter.string(from: cal.date(from: comps) ?? next)
    }

    private static var recentInvoiceMonths: [String] {
        (1...3).map { offset in
            invoiceMonthFormatter.string(from: Calendar.current.date(byAdding: .month, value: -offset, to: Date()) ?? Date())
        }
    }

    // Plan state (local until the billing backend lands)
    @State private var currentPlan = "Pro"
    @State private var selectedPlan = "Pro"

    // Confirmations
    @State private var showTeamConfirm = false
    @State private var showFreeConfirm = false
    @State private var showCancelConfirm = false

    // Brief overlay toast
    @State private var toast: String?

    var body: some View {
        ZStack {
            Aero.background.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: Aero.Spacing.m) {
                    StaggerIn(index: 0) { header }
                    StaggerIn(index: 1) { currentPlanCard }
                    StaggerIn(index: 2) { creditsCard }
                    StaggerIn(index: 3) { plansSection }
                    StaggerIn(index: 4) { paymentMethodsCard }
                    StaggerIn(index: 5) { invoicesCard }
                    StaggerIn(index: 6) { manageSection }
                }
                .padding(.horizontal, Aero.Spacing.m)
                .padding(.bottom, Aero.Spacing.xl)
            }
        }
        .overlay(alignment: .bottom) { toastView }
        .confirmationDialog(
            "Switch to Team?",
            isPresented: $showTeamConfirm,
            titleVisibility: .visible
        ) {
            Button("Switch to Team — £39 per user / month") {
                changePlan(to: "Team")
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Shared workspaces and admin controls for everyone. Pro stays active until \(Self.renewalDateText).")
        }
        .confirmationDialog(
            "Downgrade to Free?",
            isPresented: $showFreeConfirm,
            titleVisibility: .visible
        ) {
            Button("Downgrade to Free", role: .destructive) {
                changePlan(to: "Free")
            }
            Button("Keep Pro", role: .cancel) {}
        } message: {
            Text("You'll drop to 40 messages a day, 1 model and basic tools at the end of the period.")
        }
        .confirmationDialog(
            "Cancel subscription?",
            isPresented: $showCancelConfirm,
            titleVisibility: .visible
        ) {
            Button("Cancel subscription", role: .destructive) {
                changePlan(to: "Free")
            }
            Button("Keep Pro", role: .cancel) {}
        } message: {
            Text("Pro stays active until \(Self.renewalDateText), then moves to Free. Your chats and files are safe.")
        }
        .task(id: toast) {
            guard toast != nil else { return }
            try? await Task.sleep(nanoseconds: 2_400_000_000)
            withAnimation(Aero.snappy) { self.toast = nil }
        }
    }

    // MARK: Header

    private var header: some View {
        HStack {
            Text("Subscription")
                .font(Aero.headline())
                .foregroundStyle(Aero.text)
            Spacer()
            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Aero.text)
                    .frame(width: 34, height: 34)
                    .background(Circle().fill(Aero.container))
            }
            .buttonStyle(KineticPressStyle())
        }
        .padding(.top, Aero.Spacing.m)
    }

    // MARK: Current plan

    private var currentPlanCard: some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s + 4) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(currentPlan)
                            .font(Aero.headline())
                            .foregroundStyle(Aero.text)
                        Text(planCaption)
                            .font(Aero.caption())
                            .foregroundStyle(Aero.textMuted)
                    }
                    Spacer()
                    AeroChip(text: "Active", selected: true)
                }
                usageRow("Messages", detail: "1,284 / 2,000", fraction: 0.64)
                usageRow("Voice", detail: "45m / 120m", fraction: 0.375)
                usageRow("Images", detail: "32 / 100", fraction: 0.32)
            }
        }
    }

    private var planCaption: String {
        switch currentPlan {
        case "Team": return "£39/user · renews \(Self.renewalDateText)"
        case "Free": return "£0 · no renewal date"
        default: return "£16/month · renews \(Self.renewalDateText)"
        }
    }

    private func usageRow(_ label: String, detail: String, fraction: Double) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(label)
                    .font(Aero.body())
                    .foregroundStyle(Aero.text)
                Spacer()
                Text(detail)
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
            }
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule().fill(Aero.container)
                    Capsule()
                        .fill(Aero.accent)
                        .frame(width: proxy.size.width * CGFloat(fraction))
                }
            }
            .frame(height: 8)
        }
    }

    // MARK: Credits

    private var creditsCard: some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s + 4) {
                SectionHeader(title: "Credits")
                Text("240 credits")
                    .font(Aero.title())
                    .foregroundStyle(Aero.text)
                HStack(spacing: Aero.Spacing.s) {
                    AeroChip(text: "+100 · £2") { showToast(Self.checkoutToast) }
                    AeroChip(text: "+500 · £8") { showToast(Self.checkoutToast) }
                    AeroChip(text: "+2,000 · £25") { showToast(Self.checkoutToast) }
                }
            }
        }
    }

    // MARK: Plans

    private var plansSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            SectionHeader(title: "Plans")
            planCard(
                id: "Free", name: "Free", price: "£0", per: "forever",
                features: ["40 messages a day", "1 model", "Basic tools"]
            )
            planCard(
                id: "Pro", name: "Pro", price: "£16", per: "per month",
                features: ["Unlimited chats", "All 8 models", "Vision + voice", "2,000 message quota"]
            )
            planCard(
                id: "Team", name: "Team", price: "£39", per: "per user / month",
                features: ["Everything in Pro", "Shared workspaces", "Admin controls"]
            )
            planActions
        }
    }

    private func planCard(
        id: String,
        name: String,
        price: String,
        per: String,
        features: [String]
    ) -> some View {
        AeroCard(action: {
            withAnimation(Aero.snappy) { selectedPlan = id }
        }) {
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                HStack {
                    Text(name)
                        .font(Aero.title())
                        .foregroundStyle(Aero.text)
                    Spacer()
                    if currentPlan == id {
                        // Visual twin of a selected AeroChip — avoids a nested
                        // Button inside this card's tappable surface.
                        Text("Current")
                            .font(Aero.label())
                            .foregroundStyle(Color.white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(Capsule().fill(Aero.accent))
                    }
                }
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text(price)
                        .font(Aero.headline())
                        .foregroundStyle(Aero.text)
                    Text(per)
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                }
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(features, id: \.self) { feature in
                        HStack(spacing: 6) {
                            Image(systemName: "checkmark")
                                .font(.system(size: 10, weight: .semibold))
                                .foregroundStyle(Aero.accent)
                            Text(feature)
                                .font(Aero.caption())
                                .foregroundStyle(Aero.textMuted)
                        }
                    }
                }
            }
        }
        .overlay(
            RoundedRectangle(cornerRadius: Aero.Radius.card)
                .stroke(Aero.accent, lineWidth: 2)
                .opacity(selectedPlan == id ? 1 : 0)
        )
    }

    @ViewBuilder
    private var planActions: some View {
        if selectedPlan == "Team" && currentPlan != "Team" {
            Button {
                showTeamConfirm = true
            } label: {
                primaryLabel("Switch to Team")
            }
            .buttonStyle(KineticPressStyle())
        } else if selectedPlan == "Free" && currentPlan != "Free" {
            Button {
                showFreeConfirm = true
            } label: {
                tonalLabel("Downgrade to Free")
            }
            .buttonStyle(KineticPressStyle())
        } else if selectedPlan == "Pro" && currentPlan != "Pro" {
            Button {
                changePlan(to: "Pro")
                showToast("You're on Pro — welcome back.")
            } label: {
                primaryLabel("Switch to Pro")
            }
            .buttonStyle(KineticPressStyle())
        } else {
            Text("You're on \(currentPlan).")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
                .frame(maxWidth: .infinity, alignment: .center)
        }
    }

    private func changePlan(to plan: String) {
        withAnimation(Aero.snappy) {
            currentPlan = plan
            selectedPlan = plan
        }
    }

    private func primaryLabel(_ title: String) -> some View {
        Text(title)
            .font(Aero.title())
            .frame(maxWidth: .infinity)
            .padding(14)
            .background(RoundedRectangle(cornerRadius: Aero.Radius.card).fill(Aero.accent))
            .foregroundStyle(Color.white)
    }

    private func tonalLabel(_ title: String) -> some View {
        Text(title)
            .font(Aero.title())
            .frame(maxWidth: .infinity)
            .padding(14)
            .background(RoundedRectangle(cornerRadius: Aero.Radius.card).fill(Aero.container))
            .foregroundStyle(Aero.text)
    }

    // MARK: Payment methods

    private var paymentMethodsCard: some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                SectionHeader(title: "Payment methods")
                AeroListRow(
                    title: "Visa •• 4242",
                    subtitle: "Expiry 09/27",
                    leading: { leadingIcon("creditcard") },
                    trailing: {
                        Text("Default")
                            .font(Aero.caption())
                            .foregroundStyle(Aero.textMuted)
                    }
                )
                AeroListRow(
                    title: "Apple Pay",
                    subtitle: "Face ID",
                    leading: { leadingIcon("apple.logo") },
                    trailing: { EmptyView() }
                )
                Button {
                    showToast(Self.checkoutToast)
                } label: {
                    HStack(spacing: Aero.Spacing.s) {
                        Image(systemName: "plus")
                            .font(.system(size: 14))
                            .foregroundStyle(Aero.accent)
                        Text("Add payment method")
                            .font(Aero.body())
                            .foregroundStyle(Aero.text)
                        Spacer()
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                    .background(RoundedRectangle(cornerRadius: 14).fill(Aero.container.opacity(0.5)))
                    .overlay(
                        RoundedRectangle(cornerRadius: 14)
                            .stroke(Aero.outline, style: StrokeStyle(lineWidth: 1, dash: [4]))
                    )
                }
                .buttonStyle(KineticPressStyle())
            }
        }
    }

    // MARK: Invoices

    private var invoicesCard: some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                SectionHeader(title: "Invoices")
                ForEach(Self.recentInvoiceMonths, id: \.self) { month in
                    invoiceRow(month)
                }
            }
        }
    }

    private func invoiceRow(_ month: String) -> some View {
        AeroListRow(
            title: month,
            subtitle: "£16.00 · Paid",
            leading: { leadingIcon("doc.text") },
            trailing: {
                Image(systemName: "arrow.down.circle")
                    .font(.system(size: 16))
                    .foregroundStyle(Aero.accent)
            },
            action: {
                // Real save: a readable invoice lands in the Library as a
                // document — no more save-in-name-only.
                ConversationStore.shared.saveToLibrary(
                    content: "GS AI — Invoice \(month)\n\nPlan: Pro · £16.00 · Paid\nBilled monthly. Chats, files and exports stay yours.",
                    kind: "document",
                    title: "Invoice \(month)"
                )
                showToast("Invoice saved to Library")
            }
        )
    }

    // MARK: Manage

    private var manageSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            SectionHeader(title: "Manage")
            AeroListRow(
                title: "Restore purchases",
                leading: { leadingIcon("arrow.counterclockwise") },
                trailing: {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12))
                        .foregroundStyle(Aero.textMuted)
                },
                action: { showToast("Purchases restored — you're back on Pro.") }
            )
            Button {
                showCancelConfirm = true
            } label: {
                Text("Cancel subscription")
                    .font(Aero.body())
                    .foregroundStyle(Self.dangerRed)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(KineticPressStyle())
            .padding(.top, 2)
        }
    }

    // MARK: Toast

    @ViewBuilder
    private var toastView: some View {
        if let toast {
            Text(toast)
                .font(Aero.label())
                .foregroundStyle(Aero.text)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(Capsule().fill(Aero.containerHigh))
                .overlay(Capsule().stroke(Aero.outline, lineWidth: 1))
                .padding(.horizontal, Aero.Spacing.l)
                .padding(.bottom, Aero.Spacing.m)
                .transition(.opacity.combined(with: .move(edge: .bottom)))
        }
    }

    private func showToast(_ message: String) {
        withAnimation(Aero.snappy) { toast = message }
    }

    // MARK: Helpers

    private func leadingIcon(_ name: String) -> some View {
        Image(systemName: name)
            .font(.system(size: 14))
            .foregroundStyle(Aero.accent)
            .frame(width: 34, height: 34)
            .background(Circle().fill(Aero.container))
    }
}

// MARK: - Staggered entrance (private per-file helper)

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
