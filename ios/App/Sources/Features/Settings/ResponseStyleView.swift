import SwiftUI

/// HOW NEXLY ANSWERS — iOS twin of `ResponseStyleScreen.kt`.
///
/// Profile showed "Tone: Friendly" and "Detail Level: Standard" with no chevron
/// and no editor: two settings, printed, unchangeable — and unread by anything.
/// This page makes all three rows real. Tone and detail are choices written
/// through `ResponseStyle`, which the chat turn now reads as non-visible
/// context; language is stated, not offered, because only English ships here
/// and a picker with one option is a lie about a choice.
struct ResponseStyleView: View {

    @ObservedObject private var store = OnboardingProfileStore.shared
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        GsSheetPage(
            title: "Response Style",
            subtitle: "How Nexly talks to you — and how much it says",
            onBack: { dismiss() }
        ) {
            GsSheetGroupHeader("Tone", caption: "The register, not the facts")
            GsGroupCard {
                ForEach(Array(ResponseStyle.tones.enumerated()), id: \.element) { index, option in
                    choiceRow(
                        title: option,
                        subtitle: ResponseStyle.toneBlurb(option),
                        selected: store.personality == option,
                        action: { ResponseStyle.setTone(option) })
                    if index != ResponseStyle.tones.count - 1 {
                        Divider().overlay(Aero.outline.opacity(0.4)).padding(.leading, 12)
                    }
                }
            }

            GsSheetGroupHeader("Detail Level", caption: "How much of the reasoning you see")
            GsGroupCard {
                ForEach(Array(ResponseStyle.details.enumerated()), id: \.element) { index, option in
                    choiceRow(
                        title: option,
                        subtitle: ResponseStyle.detailBlurb(option),
                        selected: store.responseStyle == option,
                        action: { ResponseStyle.setDetail(option) })
                    if index != ResponseStyle.details.count - 1 {
                        Divider().overlay(Aero.outline.opacity(0.4)).padding(.leading, 12)
                    }
                }
            }

            GsSheetGroupHeader("Language", caption: "One language ships in this build")
            GsGroupCard {
                HStack(spacing: 11) {
                    GsWellTile(glyph: "language", tone: .mint, size: 28)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("English (UK)")
                            .font(Aero.bodyMedium().weight(.medium))
                            .foregroundStyle(Aero.text)
                        Text("The only language Nexly is built and tested in")
                            .font(Aero.caption())
                            .foregroundStyle(Aero.textTertiary)
                    }
                    Spacer(minLength: 8)
                    NexlyGlyph(name: "circle-check", size: 15, color: Aero.accentStrong)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .frame(minHeight: 52)
            }

            Spacer().frame(height: 12)
            HStack(alignment: .top, spacing: 8) {
                NexlyGlyph(name: "sparkles", size: 13, color: Aero.accentStrong)
                Text("This is sent with every new message as hidden context: tone \(ResponseStyle.summary). It changes how an answer is written, never what it says — facts, sources and structure are untouched.")
                    .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2))
                    .lineSpacing(3)
                    .foregroundStyle(Aero.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 18)
                    .fill(Aero.accent.opacity(0.06))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 18)
                    .strokeBorder(Aero.accent.opacity(0.22), lineWidth: 0.8)
            )

            Spacer().frame(height: 10)
            Text("Interests and content preferences still live in Profile & Preferences, and re-running setup overwrites this page's two choices.")
                .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2))
                .foregroundStyle(Aero.textTertiary)
                .padding(.horizontal, 6)
        }
    }

    /// One option: a lit row when chosen, clear glass when not.
    private func choiceRow(
        title: String,
        subtitle: String,
        selected: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 11) {
                ZStack {
                    Circle()
                        .fill(selected ? Aero.accent : Color.clear)
                    Circle()
                        .strokeBorder(selected ? Aero.accentStrong : Aero.outline, lineWidth: 1.4)
                    if selected {
                        NexlyGlyph(name: "check", size: 9, color: .white)
                    }
                }
                .frame(width: 17, height: 17)
                VStack(alignment: .leading, spacing: 1) {
                    Text(title)
                        .font(.custom(Aero.productName(selected ? .semibold : .medium), size: 12, relativeTo: .footnote))
                        .foregroundStyle(Aero.text)
                    Text(subtitle)
                        .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2))
                        .foregroundStyle(Aero.textTertiary)
                        .fixedSize(horizontal: false, vertical: true)
                        .multilineTextAlignment(.leading)
                }
                Spacer(minLength: 6)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 9)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 14)
                    .fill(selected ? Aero.accent.opacity(0.10) : .clear)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}
