import SwiftUI

/// Billing — honest stub (Phase 2). There is no billing backend and none is
/// implied: one card states the early-access reality. All fabricated plans,
/// quotas, credits, payment methods and invoices are gone — including the
/// invoice rows that wrote fictional documents into the real Library.
/// Presented as a route (.billing) or a sheet; owns its own header chrome.
struct BillingView: View {

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            Aero.background.ignoresSafeArea()

            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                header
                ScrollView {
                    honestCard
                        .padding(.bottom, Aero.Spacing.xl)
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

    // MARK: The one honest card

    private var honestCard: some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                Image(systemName: "sparkles")
                    .font(.system(size: 18))
                    .foregroundStyle(Aero.accent)
                Text("GS AI is in early access")
                    .font(Aero.headline())
                    .foregroundStyle(Aero.text)
                Text("You don't need a subscription or payment to use GS AI. If paid plans ever launch, billing will appear here.")
                    .font(Aero.body())
                    .foregroundStyle(Aero.textMuted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.vertical, Aero.Spacing.s)
        }
    }
}
