import SwiftUI

/// Billing — the plan, told truthfully.
///
/// This page used to say "You don't need a subscription", which contradicted
/// the owner's two facts: there is no free tier, and the plan costs
/// $12.99/month. The price now comes from `PlanStore`, so the number cannot
/// drift from the Subscription & Usage sheet.
///
/// There is still no payment processor in this build, so no card, invoice or
/// renewal can exist. The rows keep their slots and say exactly that.
struct BillingView: View {

    @ObservedObject private var plan = PlanStore.shared
    @EnvironmentObject private var router: Router
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            Aero.background.ignoresSafeArea()

            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                header
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        planCard
                        factsCard
                        Button {
                            router.path.append(.subscriptionUsage)
                        } label: {
                            Text("Subscription & Usage")
                                .font(Aero.button())
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 13)
                                .background(Capsule().fill(ExplorePalette.peachDeep))
                        }
                        .buttonStyle(KineticPressStyle())
                        Text("Subscription & Usage shows the plan, this month's real usage and the same $12.99 price. Nothing is charged silently.")
                            .font(Aero.caption())
                            .foregroundStyle(Aero.textMuted)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.bottom, Aero.Spacing.xl)
                    }
                }
            }
            .padding(.horizontal, Aero.Spacing.m)
        }
    }

    // MARK: Header

    private var header: some View {
        HStack {
            Text("Billing")
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
            .accessibilityLabel("Close")
        }
        .padding(.top, Aero.Spacing.m)
    }

    // MARK: The plan

    private var planCard: some View {
        GsHeroCard {
            HStack(spacing: 12) {
                GsWellTile(glyph: "crown", tone: .peach, size: 44)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 7) {
                        Text(plan.planName)
                            .font(.custom(Aero.productName(.semibold), size: 14, relativeTo: .headline))
                            .foregroundStyle(Aero.text)
                        if plan.isActive {
                            GsTagPill(text: "Active", tone: GsWellTone.mint.ink)
                        } else {
                            GsTagPill(text: "Not activated", tone: Aero.textSecondary)
                        }
                    }
                    Text("One plan, \(plan.priceLabel). No payment is taken in this build: there is no card, no invoice and no purchase path.")
                        .font(.custom(Aero.productName(.regular), size: 10, relativeTo: .caption2))
                        .foregroundStyle(Aero.textTertiary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
        }
    }

    private var factsCard: some View {
        VStack(spacing: 0) {
            factRow("Current plan", plan.planName)
            divider
            factRow("Billing cycle", plan.cycleLabel)
            divider
            factRow("Price", plan.priceLabel)
            divider
            factRow("Next billing date", nextBilling)
            divider
            factRow("Payment method", "Not set — no processor in this build")
            divider
            factRow("Invoices", "None can be issued")
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 6)
        .frame(maxWidth: .infinity, alignment: .leading)
        .gsSheetCard(corner: 22)
    }

    private var divider: some View {
        Rectangle()
            .fill(Aero.divider.opacity(0.85))
            .frame(height: 1.0 / 3.0)
    }

    private func factRow(_ label: String, _ value: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Text(label)
                .font(.custom(Aero.productName(.regular), size: 12, relativeTo: .caption))
                .foregroundStyle(Aero.textSecondary)
                .frame(width: 120, alignment: .leading)
            Text(value)
                .font(.custom(Aero.productName(.medium), size: 12, relativeTo: .caption))
                .foregroundStyle(Aero.text)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(.vertical, 9)
    }

    private var nextBilling: String {
        guard let date = plan.nextBillingDate() else { return "—" }
        return GSFormatters.dayTitle.string(from: date)
    }
}
