import SwiftUI

/// Subscription & Usage — docs/reference/pending/subscription-usage.png.
///
/// The sheet's groups, wells, gradient hero, artwork, progress bars and closing
/// card are reproduced in its order and at its own scale. The numbers are not:
/// every counter comes from `GSUsageMeter`, which counts real rows in this
/// device's store, and the plan facts come from `PlanStore` — including the
/// $12.99/month the owner confirmed. The mock's $19.99 is placeholder art and
/// is not drawn.
///
/// WHAT IS TRUTHFUL-ABSENT
/// There is no payment processor in this build: no card, no invoice, no
/// purchase path. The "Visa •••• 4242" the sheet draws exists nowhere in this
/// codebase, so Payment Method and Billing History keep their slots and state
/// the reality: no method is stored, no invoice can exist, and no chevron
/// promises a page that would be empty.
///
/// The "Go Pro" card keeps its gradient and its button, but there is only one
/// plan: the copy says so rather than advertising tiers that do not exist.
struct SubscriptionUsageView: View {

    @ObservedObject private var plan = PlanStore.shared
    @EnvironmentObject private var router: Router
    @Environment(\.dismiss) private var dismiss

    @State private var usage = GSUsageMeter.unknown
    @State private var usageDetail: UsageRowKind?

    private enum UsageRowKind: String, Identifiable {
        case messages, images, uploads, reasoning
        var id: String { rawValue }
    }

    var body: some View {
        GsSheetPage(
            title: "Subscription & Usage",
            subtitle: "Your plan, usage, and billing details",
            onBack: { dismiss() }
        ) {
            hero

            GsSheetGroupHeader("Plan Details", caption: "Current plan information")
            GsGroupCard {
                HStack(spacing: 8) {
                    VStack(alignment: .leading, spacing: 0) {
                        GsFactRow(label: "Plan Name", value: plan.planName)
                        GsFactRow(label: "Billing Cycle", value: plan.cycleLabel)
                        GsFactRow(label: "Next Billing Date", value: nextBillingLabel)
                        GsFactRow(label: "Price", value: plan.priceLabel)
                    }
                    GsArtwork(art: .clarity, size: 76, label: "Plan artwork")
                        .opacity(0.55)
                        .offset(x: 12, y: 2)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 9)
            }

            GsSheetGroupHeader("Usage Overview", caption: "Your current month's usage")
            GsGroupCard {
                usageRow(
                    .messages, "Messages", "AI conversations",
                    "message-circle", .lilac, usage.messages, PlanStore.limitMessages)
                divider
                usageRow(
                    .images, "Images", "Not metered in this build",
                    "photo", .rose, -1, PlanStore.limitImages)
                divider
                usageRow(
                    .uploads, "File Uploads", "Documents, files, and more",
                    "upload", .mint, usage.uploads, PlanStore.limitUploads)
                divider
                usageRow(
                    .reasoning, "Advanced Reasoning", "Not metered per message in this build",
                    "brain", .blue, -1, PlanStore.limitReasoning)
            }

            GsSheetGroupHeader("Billing & Payment", caption: "Manage your payment method")
            GsGroupCard {
                GsSheetRow(
                    title: "Payment Method",
                    detail: "No card is stored — this build has no payment processor",
                    action: nil
                ) {
                    GsWellTile(glyph: "credit-card", tone: .blue, size: 34)
                } trailing: {
                    GsTagPill(text: "Not set", tone: Aero.textSecondary)
                }
                divider
                GsSheetRow(
                    title: "Billing History",
                    detail: "No invoices — none can be issued in this build",
                    action: nil
                ) {
                    GsWellTile(glyph: "receipt", tone: .peach, size: 34)
                } trailing: {
                    EmptyView()
                }
            }

            GsSheetGroupHeader("Upgrade Options", caption: "Your plan")
            GsHeroCard(quiet: true) {
                HStack(spacing: 11) {
                    GsWellTile(glyph: "sparkles", tone: .lilac, size: 44)
                    VStack(alignment: .leading, spacing: 1) {
                        Text("Nexly Pro — everything included")
                            .font(.custom(Aero.productName(.semibold), size: 11, relativeTo: .caption))
                            .foregroundStyle(Aero.text)
                        Text("One plan, $12.99/month: more usage, advanced models, exclusive features and priority support.")
                            .font(.custom(Aero.productName(.regular), size: 8.5, relativeTo: .caption2))
                            .foregroundStyle(Aero.textTertiary)
                            .lineLimit(2)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 6)
                    GsSheetPillButton(label: "View Plan") {
                        router.path.append(.billing)
                    }
                }
            }

            Spacer().frame(height: 14)
            Button {
                router.path.append(.helpSupport)
            } label: {
                HStack(spacing: 10) {
                    GsWellTile(glyph: "crown", tone: .peach, size: 34)
                    VStack(alignment: .leading, spacing: 1) {
                        Text("Need help?")
                            .font(GsSheetType.rowTitle())
                            .foregroundStyle(Aero.text)
                        Text("Our support team is here to assist you with any billing or plan questions.")
                            .font(GsSheetType.rowDetail())
                            .foregroundStyle(Aero.textTertiary)
                            .lineLimit(2)
                    }
                    Spacer(minLength: 8)
                    GsSheetChevron()
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 11)
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
            .buttonStyle(KineticPressStyle())
        }
        .task {
            // The meter reads ConversationStore (main-actor); hop explicitly
            // so this compiles under actor isolation rather than relying on
            // the task's inherited context.
            let measured = await MainActor.run { GSUsageMeter.read() }
            await MainActor.run { usage = measured }
        }
        .sheet(item: $usageDetail) { kind in
            usageSheet(kind)
                .presentationDetents([.medium])
        }
    }

    // MARK: Hero

    private var hero: some View {
        GsHeroCard {
            HStack(spacing: 12) {
                GsWellTile(glyph: "crown", tone: .peach, size: 44)
                VStack(alignment: .leading, spacing: 1) {
                    HStack(spacing: 7) {
                        Text(plan.planName)
                            .font(.custom(Aero.productName(.semibold), size: 12, relativeTo: .caption))
                            .foregroundStyle(Aero.text)
                        if plan.isActive {
                            GsTagPill(text: "Active", tone: GsWellTone.mint.ink)
                        } else {
                            GsTagPill(text: "Not activated", tone: Aero.textSecondary)
                        }
                    }
                    Text("Unlock more limits, advanced models, and premium features.")
                        .font(.custom(Aero.productName(.regular), size: 8.5, relativeTo: .caption2))
                        .foregroundStyle(Aero.textTertiary)
                        .lineLimit(2)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 6)
                GsSheetPillButton(label: "Manage Plan") {
                    router.path.append(.billing)
                }
            }
        }
    }

    // MARK: Usage rows

    private func usageRow(
        _ kind: UsageRowKind,
        _ title: String,
        _ detail: String,
        _ glyph: String,
        _ tone: GsWellTone,
        _ used: Int,
        _ limit: Int
    ) -> some View {
        VStack(spacing: 7) {
            HStack(spacing: 11) {
                GsWellTile(glyph: glyph, tone: tone, size: 34)
                VStack(alignment: .leading, spacing: 0) {
                    Text(title)
                        .font(GsSheetType.rowTitle())
                        .foregroundStyle(Aero.text)
                        .lineLimit(1)
                    Text(detail)
                        .font(GsSheetType.rowDetail())
                        .foregroundStyle(Aero.textTertiary)
                        .lineLimit(1)
                }
                Spacer(minLength: 8)
                Text(used < 0 ? "—" : "\(used) / \(grouped(limit))")
                    .font(GsSheetType.rowValue())
                    .foregroundStyle(Aero.textSecondary)
                Button {
                    usageDetail = kind
                } label: {
                    GsSheetChevron()
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(title) details")
            }
            GsProgressBar(
                fraction: used < 0 || limit <= 0 ? nil : Double(used) / Double(limit),
                tone: tone.ink)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 9)
    }

    private func usageSheet(_ kind: UsageRowKind) -> some View {
        let facts: [(String, String)]
        switch kind {
        case .messages:
            facts = [
                ("This month", "\(max(0, usage.messages)) messages"),
                ("Allowance", grouped(PlanStore.limitMessages)),
                ("Source", "User turns in this device's conversation store"),
                ("Enforced", "No — the allowance is displayed, not enforced"),
            ]
        case .uploads:
            facts = [
                ("This month", "\(max(0, usage.uploads)) messages with attachments"),
                ("Allowance", grouped(PlanStore.limitUploads)),
                ("Source", "User turns carrying at least one attachment"),
                ("Enforced", "No — the allowance is displayed, not enforced"),
            ]
        case .images:
            facts = [
                ("This month", "—"),
                ("Allowance", grouped(PlanStore.limitImages)),
                ("Why", "Image generation is not part of this build"),
            ]
        case .reasoning:
            facts = [
                ("This month", "—"),
                ("Allowance", grouped(PlanStore.limitReasoning)),
                ("Why", "Effort is stored per conversation, not counted per message"),
            ]
        }
        return GsFactSheet(title: kind.rawValue.capitalized, facts: facts)
    }

    // MARK: Helpers

    private var divider: some View {
        Rectangle()
            .fill(Aero.divider.opacity(0.85))
            .frame(height: 1.0 / 3.0)
            .padding(.leading, 50)
            .padding(.trailing, 12)
    }

    private var nextBillingLabel: String {
        guard let date = plan.nextBillingDate() else { return "—" }
        return GSFormatters.dayTitle.string(from: date)
    }

    private func grouped(_ value: Int) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter.string(from: NSNumber(value: value)) ?? "\(value)"
    }
}
