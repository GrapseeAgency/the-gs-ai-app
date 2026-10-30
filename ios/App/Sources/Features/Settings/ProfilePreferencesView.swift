import SwiftUI

/// PROFILE & PREFERENCES — `docs/reference/settings/profile-preferences.png`.
///
/// Every value is real or honestly "Not set": the identity card reads
/// `AccountStore`, Response Style and Personalization read
/// `OnboardingProfileStore` (this screen is its first iOS reader — see the
/// store's contract comment), and the two rows the app has no editor for
/// (Date of Birth, Location) render "Not set" WITHOUT a chevron: a chevron
/// with no destination is a dead end, which the mocks' visual language can
/// afford to paint and the app cannot.
struct ProfilePreferencesView: View {
    @ObservedObject private var account = AccountStore.shared
    @ObservedObject private var onboarding = OnboardingProfileStore.shared
    @Environment(\.dismiss) private var dismiss
    @State private var showIdentityEditor = false

    var body: some View {
        GsSettingsPage(
            title: "Profile & Preferences",
            subtitle: "Make Nexly feel more like you",
            onBack: { dismiss() }
        ) {
            profileCard

            GsGroupHeader(title: "Profile", caption: "Your basic information")
            GsGroupCard {
                staticRow("Name", value("Name", account.displayName), glyph: "user", tone: .peach)
                divider
                staticRow("Email", value("Email", account.email), glyph: "mail", tone: .lilac)
                divider
                staticRow("Date of Birth", "Not set", glyph: "calendar", tone: .mint, trailingChevron: false)
                divider
                staticRow("Location", "Not set", glyph: "map-pin", tone: .blue, trailingChevron: false, isLast: true)
            }

            GsGroupHeader(title: "Response Style", caption: "How Nexly communicates with you")
            GsGroupCard {
                staticRow("Tone", onboarding.personality, glyph: "message-circle", tone: .peach, isLast: false)
                divider
                staticRow("Detail Level", onboarding.responseStyle, glyph: "adjustments", tone: .peach, isLast: true)
            }

            GsGroupHeader(title: "Personalization Preferences", caption: "Tailor your experience")
            GsGroupCard {
                staticRow("Interests", joined(onboarding.interests), glyph: "sparkles", tone: .peach)
                divider
                staticRow("Content Preferences", joined(onboarding.capabilities), glyph: "book-2", tone: .lilac)
                divider
                staticRow("Language", "English (UK)", glyph: "language", tone: .mint, trailingChevron: false, isLast: true)
            }

            Spacer().frame(height: 14)
            GsInfoCard(
                text: "Your preferences help Nexly give you better answers, more relevant suggestions, and a personalized experience.",
                glyph: "sparkles"
            )
            Spacer().frame(height: 10)
        }
        .sheet(isPresented: $showIdentityEditor) { IdentityEditSheet() }
    }

    // MARK: Pieces

    private var profileCard: some View {
        GsGroupCard {
            // The card carried a chevron and a pencil badge but opened
            // nothing; it is the way into the identity editor now.
            Button { showIdentityEditor = true } label: {
            HStack(alignment: .center, spacing: 11) {
                ZStack(alignment: .bottomTrailing) {
                    Circle()
                        .fill(Aero.raisedSurface)
                        .frame(width: 52, height: 52)
                        .overlay(Circle().strokeBorder(.white.opacity(0.55), lineWidth: 2))
                    Text(initials)
                        .font(.custom(Aero.productName(.semibold), size: 17, relativeTo: .headline))
                        .foregroundStyle(Aero.text)
                    Circle()
                        .fill(Aero.raisedSurface)
                        .frame(width: 18, height: 18)
                        .overlay(NexlyGlyph(name: "pencil", size: 10, color: Aero.textSecondary))
                        .overlay(Circle().strokeBorder(.white.opacity(0.85), lineWidth: 1))
                        .offset(x: 3, y: 3)
                }
                VStack(alignment: .leading, spacing: 1) {
                    Text(account.displayName.isEmpty ? "Your account" : account.displayName)
                        .font(Aero.bodyMedium().weight(.semibold))
                        .foregroundStyle(Aero.text)
                        .lineLimit(1)
                    Text(account.email.isEmpty ? "Add your Grapsee Mail address" : account.email)
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textTertiary)
                        .lineLimit(1)
                    Text(PlanStore.shared.planName)
                        .font(.custom(Aero.productName(.medium), size: 10.5, relativeTo: .caption2))
                        .foregroundStyle(Color(red: 0.541, green: 0.290, blue: 0.122))
                        .padding(.horizontal, 9)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(Color(red: 0.961, green: 0.812, blue: 0.710).opacity(0.55)))
                        .padding(.top, 3)
                }
                Spacer(minLength: 6)
                NexlyGlyph(name: "chevron-right", size: 13, color: Aero.textTertiary)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 11)
            .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Edit your details")
        }
    }

    private var initials: String {
        let parts = account.displayName.split(separator: " ").map(String.init)
        if parts.isEmpty { return "A" }
        if parts.count == 1 { return String(parts[0].prefix(2)).uppercased() }
        return (String(parts[0].prefix(1)) + String(parts[parts.count - 1].prefix(1))).uppercased()
    }

    private func value(_ what: String, _ raw: String) -> String {
        raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Not set" : raw
    }

    private func joined(_ set: Set<String>) -> String {
        set.isEmpty ? "Not set" : set.sorted().joined(separator: ", ")
    }

    private var divider: some View {
        Rectangle()
            .fill(Aero.divider.opacity(0.7))
            .frame(height: 1.0 / 3.0)
            .padding(.leading, 50)
            .padding(.trailing, 12)
    }

    /// A read-only value row. `trailingChevron` is only true where a real
    /// destination exists; everything the app cannot edit renders plainly.
    private func staticRow(
        _ title: String,
        _ shown: String,
        glyph: String,
        tone: GsWellTone,
        trailingChevron: Bool = false,
        isLast: Bool = false
    ) -> some View {
        HStack(spacing: 11) {
            GsWellTile(glyph: glyph, tone: tone, size: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(Aero.bodyMedium().weight(.medium))
                    .foregroundStyle(Aero.text)
                Text(shown)
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textTertiary)
                    .lineLimit(2)
            }
            Spacer(minLength: 8)
            if trailingChevron {
                NexlyGlyph(name: "chevron-right", size: 13, color: Aero.textTertiary)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 7)
        .frame(minHeight: 52)
        .overlay(alignment: .bottom) {
            if !isLast {
                Rectangle()
                    .fill(Aero.divider.opacity(0.7))
                    .frame(height: 1.0 / 3.0)
                    .padding(.leading, 50)
                    .padding(.trailing, 12)
            }
        }
    }
}

/// The editor behind Profile's Name/Email rows and Account & Security's
/// identity card. Before this sheet the two screens pointed at each other for
/// editing and neither had a field.
private struct IdentityEditSheet: View {

    @ObservedObject private var account = AccountStore.shared
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var email = ""

    private var emailInvalid: Bool {
        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines)
        return !trimmed.isEmpty && !trimmed.contains("@")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            Text("Your details")
                .font(Aero.headline())
                .foregroundStyle(Aero.text)
            TextField("Your name", text: $name)
                .textFieldStyle(.roundedBorder)
                .autocorrectionDisabled()
            TextField("you@grapsee.com", text: $email)
                .textFieldStyle(.roundedBorder)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            if emailInvalid {
                Text("Enter an address like you@grapsee.com")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.danger)
            }
            Text("Saved on this device. This changes what the app shows; it does not create or move a Grapsee Mail account.")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
                .fixedSize(horizontal: false, vertical: true)
            HStack {
                Button("Cancel") { dismiss() }
                    .foregroundStyle(Aero.textMuted)
                Spacer()
                Button("Save") {
                    account.setDisplayName(name.trimmingCharacters(in: .whitespacesAndNewlines))
                    account.setEmail(email.trimmingCharacters(in: .whitespacesAndNewlines))
                    dismiss()
                }
                .font(Aero.button())
                .disabled(emailInvalid)
            }
            Spacer()
        }
        .padding(Aero.Spacing.l)
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
        .onAppear {
            name = account.displayName
            email = account.email
        }
    }
}
