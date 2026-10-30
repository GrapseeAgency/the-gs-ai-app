import SwiftUI

/// One provider row. `status` is always the truth for this build.
///
/// `tint` is only the brand's own ink where a single colour identifies it
/// (Spotify green, Discord blurple, Trello blue, Zoom blue). Marks whose
/// identity is a multi-colour lockup (Google, Slack, Microsoft, Figma) stay in
/// the app's ink rather than being recoloured into something that is neither.
struct GsIntegrationProvider: Identifiable, Hashable {
    var id: String { name }
    let name: String
    let scope: String
    let glyph: String
    var tint: Color?
    var available: Bool = false
}

/// Connected Apps & Integrations — docs/reference/pending/connected-apps.png.
///
/// The sheet's groups, provider rows, status pills, "Available Integrations"
/// tiles and both closing cards are reproduced in its order and at its own
/// scale. The statuses are NOT the sheet's.
///
/// This build ships no OAuth client, no token store, no permissions registry and
/// no provider adapters — verify it: there is no integration code anywhere in
/// this repository. Google, GitHub, Notion, Spotify, Slack, Microsoft, Dropbox,
/// Figma, Trello, Discord and Zoom are all "Not Connected" because that is what
/// they are, and nothing here can make them otherwise.
///
/// The sheet's green "Connected" and blue "Limited Access" pills would have been
/// the most fabricated thing in the app — a claim of a live link to eleven
/// third-party accounts, none of which has ever been linked. The owner's rule is
/// "no mock data no fake data", and that rule costs this screen its pills.
///
/// Every control still resolves: "..." opens that provider's real status sheet,
/// "+ Connect" opens the same sheet, "Learn More" explains what a connection
/// would require, "Manage Permissions" states what is granted (nothing), and
/// "Your data, your control" is a real route to Privacy Settings.
struct ConnectedAppsView: View {

    @EnvironmentObject private var router: Router
    @Environment(\.dismiss) private var dismiss

    @State private var sheet: GsIntegrationSheet?

    private let providers: [GsIntegrationProvider] = [
        GsIntegrationProvider(name: "Google", scope: "Email, Drive, Calendar", glyph: "brand-google"),
        GsIntegrationProvider(name: "GitHub", scope: "Code repositories & projects", glyph: "brand-github"),
        GsIntegrationProvider(name: "Notion", scope: "Notes, docs & workspace", glyph: "brand-notion"),
        GsIntegrationProvider(name: "Spotify", scope: "Music & media", glyph: "brand-spotify", tint: Color(red: 0.114, green: 0.725, blue: 0.329)),
        GsIntegrationProvider(name: "Slack", scope: "Work communication", glyph: "brand-slack"),
        GsIntegrationProvider(name: "Microsoft", scope: "OneDrive, Outlook & more", glyph: "brand-windows", tint: Color(red: 0.0, green: 0.643, blue: 0.937)),
    ]

    private let available: [GsIntegrationProvider] = [
        GsIntegrationProvider(name: "Dropbox", scope: "File storage", glyph: "brand-dropbox", tint: Color(red: 0.0, green: 0.380, blue: 1.0), available: true),
        GsIntegrationProvider(name: "Figma", scope: "Design & files", glyph: "brand-figma", available: true),
        GsIntegrationProvider(name: "Trello", scope: "Project management", glyph: "brand-trello", tint: Color(red: 0.0, green: 0.475, blue: 0.749), available: true),
        GsIntegrationProvider(name: "Discord", scope: "Community", glyph: "brand-discord", tint: Color(red: 0.345, green: 0.396, blue: 0.949), available: true),
        GsIntegrationProvider(name: "Zoom", scope: "Meetings", glyph: "brand-zoom", tint: Color(red: 0.176, green: 0.549, blue: 1.0), available: true),
    ]

    var body: some View {
        GsSheetPage(
            title: "Connected Apps & Integrations",
            subtitle: "Link your favorite services and manage permissions for a more connected experience.",
            onBack: { dismiss() }
        ) {
            hero

            GsSheetGroupHeader(
                "Your Connected Apps",
                caption: "Manage Permissions",
                onCaption: { sheet = .permissions })
            // The mock draws each provider as its own 20-corner card with a
            // 5pt gap; one group card with hairlines read as a table.
            VStack(spacing: 5) {
                ForEach(providers) { provider in
                    GsSheetRow(
                        title: provider.name,
                        detail: provider.scope,
                        // The mock puts each provider's status on its own line
                        // under the scope; only Microsoft, whose row also
                        // carries "+ Connect", keeps it in the trailing slot.
                        detailTrailing: provider.name == "Microsoft"
                            ? nil
                            : AnyView(GsTagPill(text: "Not Connected", tone: Aero.textSecondary)),
                        action: nil
                    ) {
                        GsBrandWell(glyph: provider.glyph, size: 34, tint: provider.tint)
                    } trailing: {
                        HStack(spacing: 6) {
                            if provider.name == "Microsoft" {
                                GsTagPill(text: "Not Connected", tone: Aero.textSecondary)
                                connectPill(provider)
                            } else {
                                GsDotsButton(label: "\(provider.name) status") {
                                    sheet = .provider(provider)
                                }
                            }
                        }
                    }
                    .gsSheetCard(corner: 20)
                }
            }

            GsSheetGroupHeader(
                "Available Integrations",
                caption: "Browse All",
                onCaption: { sheet = .browse })
            // One height for all five tiles even when a caption wraps, with
            // each "+" on that height's floor — `fixedSize` is what stops the
            // flexible frames from claiming the scroll view's height.
            HStack(alignment: .top, spacing: 7) {
                ForEach(available) { provider in
                    GsSheetTile(
                        title: provider.name,
                        caption: provider.scope,
                        brandGlyph: provider.glyph,
                        brandTint: provider.tint,
                        corner: .plus,
                        centered: true,
                        minHeight: 108,
                        pinCorner: true,
                        action: { sheet = .provider(provider) })
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .fixedSize(horizontal: false, vertical: true)

            Spacer().frame(height: 14)
            Button {
                router.path.append(.settingsSection(.privacy))
            } label: {
                HStack(spacing: 10) {
                    GsWellTile(glyph: "shield-check", tone: .lilac, size: 34)
                    VStack(alignment: .leading, spacing: 1) {
                        Text("Your data, your control")
                            .font(GsSheetType.rowTitle())
                            .foregroundStyle(Aero.text)
                        Text("When an app is connected, you decide what it can read and you can revoke it here at any time.")
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

            Spacer().frame(height: 10)
            Button {
                sheet = .learn
            } label: {
                HStack(spacing: 10) {
                    GsWellTile(glyph: "link", tone: .lilac, size: 34)
                    VStack(alignment: .leading, spacing: 1) {
                        Text("Connect New App or Service")
                            .font(GsSheetType.rowTitle())
                            .foregroundStyle(Aero.text)
                        Text("Integration support is not available in this build.")
                            .font(GsSheetType.rowDetail())
                            .foregroundStyle(Aero.textTertiary)
                    }
                    Spacer(minLength: 8)
                    GsSheetChevron()
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 11)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 20)
                        .fill(
                            LinearGradient(
                                colors: [
                                    Color(red: 0.937, green: 0.902, blue: 0.988),
                                    Color(red: 0.984, green: 0.914, blue: 0.945),
                                ],
                                startPoint: .leading, endPoint: .trailing))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .strokeBorder(.white.opacity(0.50), lineWidth: 1)
                )
            }
            .buttonStyle(KineticPressStyle())
        }
        .sheet(item: $sheet) { which in
            GsIntegrationSheetView(sheet: which)
                .presentationDetents([.medium])
        }
    }

    private var hero: some View {
        GsHeroCard {
            HStack(spacing: 11) {
                GsWellTile(glyph: "link", tone: .lilac, size: 44)
                VStack(alignment: .leading, spacing: 1) {
                    Text("More possibilities together")
                        .font(.custom(Aero.productName(.semibold), size: 11, relativeTo: .caption))
                        .foregroundStyle(Aero.text)
                    Text("No integration is wired in this build yet — connecting an app starts with a provider adapter.")
                        .font(.custom(Aero.productName(.regular), size: 8.5, relativeTo: .caption2))
                        .foregroundStyle(Aero.textTertiary)
                        .lineLimit(2)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 6)
                GsSheetPillButton(label: "Learn More") {
                    sheet = .learn
                }
            }
        }
    }

    private func connectPill(_ provider: GsIntegrationProvider) -> some View {
        Button {
            sheet = .provider(provider)
        } label: {
            HStack(spacing: 3) {
                NexlyGlyph(name: "plus", size: 9, color: Aero.text)
                Text("Connect")
                    .font(.custom(Aero.productName(.medium), size: 8.5, relativeTo: .caption2))
                    .foregroundStyle(Aero.text)
            }
            .padding(.horizontal, 9)
            .padding(.vertical, 4)
            .background(Capsule().fill(.white.opacity(0.55)))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Connect \(provider.name)")
    }
}

enum GsIntegrationSheet: Identifiable {
    case provider(GsIntegrationProvider)
    case learn
    case permissions
    case browse

    var id: String {
        switch self {
        case .provider(let provider): return "provider-\(provider.name)"
        case .learn: return "learn"
        case .permissions: return "permissions"
        case .browse: return "browse"
        }
    }
}

struct GsIntegrationSheetView: View {
    let sheet: GsIntegrationSheet
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                switch sheet {
                case .provider(let provider):
                    header(provider.name)
                    Text("Not connected. Nexly has no authorization to \(provider.name): no token is stored and no third-party data is read. A connection can exist only once a \(provider.name) adapter ships in the app.")
                case .learn:
                    header("How integrations work")
                    Text("An integration needs four things this build does not ship: an OAuth client for the provider, a token store, a permissions registry and an adapter that reads the provider's data. Until all four exist, every app here stays Not Connected — and nothing on this screen claims otherwise.")
                case .permissions:
                    header("Manage Permissions")
                    Text("No permissions are granted. There is no connected app, so there is nothing to allow, limit or revoke. Privacy Settings keeps the on-device switches in one place.")
                case .browse:
                    header("Available Integrations")
                    Text("None can be connected in this build. The five apps on the sheet are listed so the slot is not empty — not because an adapter exists.")
                }
                Button {
                    dismiss()
                } label: {
                    Text("Close")
                        .font(Aero.button())
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Capsule().fill(ExplorePalette.peachDeep))
                }
                .buttonStyle(KineticPressStyle())
                .padding(.top, 4)
            }
            .padding(20)
        }
    }

    private func header(_ title: String) -> some View {
        Text(title)
            .font(.custom(Aero.productName(.semibold), size: 15, relativeTo: .headline))
            .foregroundStyle(Aero.text)
    }
}
