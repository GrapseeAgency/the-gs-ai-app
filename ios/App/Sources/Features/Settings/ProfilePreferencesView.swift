import SwiftUI

/// PROFILE & PREFERENCES — `docs/reference/settings/profile-preferences.png`.
///
/// Every value is real or honestly "Not set": the identity card reads
/// `AccountStore`, Response Style and Personalization read
/// `OnboardingProfileStore` (this screen is its first iOS reader — see the
/// store's contract comment). All four Profile rows open the identity editor,
/// which stores all four fields; none of them is a value with no way to change
/// it, and `staticRow`'s `trailingChevron` stays false everywhere a real
/// destination does not exist — a chevron with no destination is a dead end,
/// which the mocks' visual language can afford to paint and the app cannot.
struct ProfilePreferencesView: View {
    @ObservedObject private var account = AccountStore.shared
    @ObservedObject private var onboarding = OnboardingProfileStore.shared
    @EnvironmentObject private var router: Router
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
                // Name and Email were the only two rows here with no action,
                // while Date of Birth and Location opened the very editor that
                // stores all four. Android has always let you tap them; on iOS
                // the two most important fields were the two you could not.
                navRow("Name", value("Name", account.displayName), glyph: "user", tone: .peach) {
                    showIdentityEditor = true
                }
                divider
                navRow("Email", value("Email", account.email), glyph: "mail", tone: .lilac) {
                    showIdentityEditor = true
                }
                divider
                // These two printed "Not set" forever with no chevron: a field
                // the app names and can never learn. They open the same editor
                // as the name and address, which now stores both.
                navRow("Date of Birth", value("Date of Birth", account.dateOfBirth), glyph: "calendar", tone: .mint) {
                    showIdentityEditor = true
                }
                divider
                navRow("Location", value("Location", account.location), glyph: "map-pin", tone: .blue, isLast: true) {
                    showIdentityEditor = true
                }
            }

            // Tone, Detail and Language used to be three rows that showed a
            // value and offered no way to change it. One page owns all three,
            // and the two choices are read by the chat turn.
            GsGroupHeader(title: "Response Style", caption: "How Nexly communicates with you")
            GsGroupCard {
                navRow("Tone & Detail", "\(onboarding.personality) · \(onboarding.responseStyle)", glyph: "message-circle", tone: .peach) {
                    router.path.append(.responseStyle)
                }
                divider
                navRow("Language", "English (UK) — the only language this build ships", glyph: "language", tone: .mint, isLast: true) {
                    // The hub's Language row opens the Language page; this one
                    // opened Response Style, so two rows with one name led to
                    // two different places. The page that owns the answer wins.
                    router.path.append(.settingsSection(.language))
                }
            }

            GsGroupHeader(title: "Personalization Preferences", caption: "Tailor your experience")
            GsGroupCard {
                // These were static rows: the value, no chevron, no way in.
                // The only editor was the whole onboarding wizard from step
                // one, which re-asks name, tone and notifications and
                // overwrites all four answers on the way out.
                navRow("Interests", joined(onboarding.interests), glyph: "sparkles", tone: .peach) {
                    router.path.append(.profilePersonalization)
                }
                divider
                navRow("Content Preferences", joined(onboarding.capabilities), glyph: "book-2", tone: .lilac, isLast: true) {
                    router.path.append(.profilePersonalization)
                }
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

    /// A row that goes somewhere. A chevron only ever appears on these.
    private func navRow(
        _ title: String,
        _ shown: String,
        glyph: String,
        tone: GsWellTone,
        isLast: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            staticRow(title, shown, glyph: glyph, tone: tone, trailingChevron: true, isLast: isLast)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(title): \(shown)")
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
    @State private var dob = ""
    @State private var place = ""

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
            TextField("YYYY-MM-DD", text: $dob)
                .textFieldStyle(.roundedBorder)
                .autocorrectionDisabled()
                .accessibilityLabel("Date of birth")
            TextField("City, country", text: $place)
                .textFieldStyle(.roundedBorder)
                .autocorrectionDisabled()
                .accessibilityLabel("Location")
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
                    account.setDateOfBirth(dob.trimmingCharacters(in: .whitespacesAndNewlines))
                    account.setLocation(place.trimmingCharacters(in: .whitespacesAndNewlines))
                    dismiss()
                }
                .font(Aero.button())
                .disabled(emailInvalid)
            }
            Spacer()
        }
        .padding(Aero.Spacing.l)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .onAppear {
            name = account.displayName
            email = account.email
            dob = account.dateOfBirth
            place = account.location
        }
    }
}
