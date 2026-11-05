import SwiftUI

/// THE TWO PROFILE ROWS THAT HAD NO EDITOR — iOS twin of
/// `ProfilePersonalizationScreen.kt`.
///
/// "Interests" and "Content Preferences" were `staticRow`s: they printed what
/// the wizard had collected, carried no chevron, and offered nothing. The only
/// way to change either was to re-run the whole onboarding flow from step one,
/// which re-asks name, tone and notifications and overwrites all four answers
/// on the way out.
///
/// This page edits the two collections in place. Each toggle writes its own
/// key, so changing an interest cannot reset the tone picked three screens
/// earlier in the wizard.
///
/// The copy is careful about what these actually do: they choose the first
/// suggestions and the quick actions on Home. They are not a hidden system
/// prompt, and saying so is cheaper than a reader wondering why "Travel" did
/// not change an answer about tax law.
struct ProfilePersonalizationView: View {

    @ObservedObject private var onboarding = OnboardingProfileStore.shared
    @Environment(\.dismiss) private var dismiss

    private let chipColumns = [GridItem(.adaptive(minimum: 104), spacing: Aero.Spacing.xs)]

    var body: some View {
        GsSheetPage(
            title: "Personalization",
            subtitle: "What Nexly leads with, and what it does first",
            onBack: { dismiss() }
        ) {
            // ---- Interests ------------------------------------------------
            GsSheetGroupHeader(
                "Interests",
                caption: onboarding.interests.isEmpty
                    ? "Nothing picked"
                    : "\(onboarding.interests.count) picked")

            VStack(alignment: .leading, spacing: 10) {
                Text("I'm mostly here for")
                    .font(.custom(Aero.productName(.regular), size: 12, relativeTo: .footnote))
                    .foregroundStyle(Aero.textSecondary)
                LazyVGrid(columns: chipColumns, alignment: .leading, spacing: Aero.Spacing.xs) {
                    ForEach(OnboardingView.interestOptions, id: \.self) { topic in
                        AeroChip(text: topic, selected: onboarding.interests.contains(topic)) {
                            GSHaptics.select()
                            var next = onboarding.interests
                            if next.contains(topic) { next.remove(topic) } else { next.insert(topic) }
                            onboarding.setInterests(next)
                        }
                    }
                }
                if onboarding.interests.isEmpty {
                    Text("With nothing picked, Home leads with the six starting tools rather than a guess.")
                        .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2))
                        .lineSpacing(3)
                        .foregroundStyle(Aero.textTertiary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .gsSheetCard(corner: 20)

            // ---- Pinned capabilities --------------------------------------
            GsSheetGroupHeader(
                "What Nexly does first",
                caption: onboarding.capabilities.isEmpty
                    ? "Nothing pinned"
                    : "\(onboarding.capabilities.count) pinned")

            VStack(spacing: 0) {
                ForEach(Array(OnboardingView.capabilityOptions.enumerated()), id: \.element.title) { index, option in
                    if index > 0 {
                        Rectangle().fill(Aero.divider).frame(height: 0.7).padding(.leading, 52)
                    }
                    Button {
                        GSHaptics.select()
                        var next = onboarding.capabilities
                        if next.contains(option.title) {
                            next.remove(option.title)
                        } else {
                            next.insert(option.title)
                        }
                        onboarding.setCapabilities(next)
                    } label: {
                        HStack(spacing: 11) {
                            GsWellTile(glyph: option.variant.glyph, tone: option.variant.tone, size: 28)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(option.title)
                                    .font(.custom(Aero.productName(.medium), size: 12, relativeTo: .footnote))
                                    .foregroundStyle(Aero.text)
                                Text(option.caption)
                                    .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2))
                                    .foregroundStyle(Aero.textTertiary)
                                    .lineLimit(2)
                                    .multilineTextAlignment(.leading)
                            }
                            Spacer(minLength: 6)
                            // A lit ring when pinned, an empty one when not —
                            // the same language the wizard's rows use.
                            ZStack {
                                Circle()
                                    .strokeBorder(
                                        onboarding.capabilities.contains(option.title)
                                            ? Aero.accentStrong : Aero.outline,
                                        lineWidth: onboarding.capabilities.contains(option.title) ? 0 : 1)
                                if onboarding.capabilities.contains(option.title) {
                                    Circle().fill(Aero.accentStrong)
                                    NexlyGlyph(name: "check", size: 10, color: .white)
                                }
                            }
                            .frame(width: 18, height: 18)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 11)
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(option.title): \(option.caption)")
                    .accessibilityAddTraits(
                        onboarding.capabilities.contains(option.title) ? [.isSelected] : [])
                }
            }
            .gsSheetCard(corner: 20)

            Spacer().frame(height: 14)
            Text("These shape the first suggestions and the quick actions on Home. They are not a hidden instruction to the model, and they never override an answer.")
                .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2))
                .lineSpacing(3)
                .foregroundStyle(Aero.textTertiary)
                .padding(.horizontal, 6)
            Spacer().frame(height: 10)
        }
    }
}

/// The wizard paints these capabilities as full illustrations; a settings row
/// has 28 points, so it takes the same six ideas down to a glyph and a tone.
private extension NativeIllustrationVariant {
    var glyph: String {
        switch self {
        case .companion: return "message-circle"
        case .research:  return "search"
        case .vision:    return "palette"
        case .code:      return "code"
        case .documents: return "file-text"
        case .ideas:     return "sparkles"
        }
    }

    var tone: GsWellTone {
        switch self {
        case .companion: return .peach
        case .research:  return .blue
        case .vision:    return .rose
        case .code:      return .mint
        case .documents: return .lilac
        case .ideas:     return .peach
        }
    }
}
