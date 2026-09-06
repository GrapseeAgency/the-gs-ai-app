import SwiftUI

/**
 * AERUO KINETIC drawer — the primary navigation, benchmark pattern
 * (ChatGPT / Claude / Kimi): obsidian panel sliding over the canvas,
 * account header, one-tap new chat, recents, and the section map.
 */
struct AeroDrawer: View {

    var onRoute: (AeroRoute) -> Void
    var onClose: () -> Void

    // Forced-obsidian palette (fixed benchmark-dark in both appearances)
    private let panel = Aero.dynamic(
        light: UIColor(red: 0.039, green: 0.051, blue: 0.071, alpha: 1),
        dark: UIColor(red: 0.039, green: 0.051, blue: 0.071, alpha: 1))
    private let raised = Aero.dynamic(
        light: UIColor(red: 0.094, green: 0.118, blue: 0.157, alpha: 1),
        dark: UIColor(red: 0.094, green: 0.118, blue: 0.157, alpha: 1))
    private let ink = Aero.dynamic(
        light: UIColor(red: 0.929, green: 0.937, blue: 0.949, alpha: 1),
        dark: UIColor(red: 0.929, green: 0.937, blue: 0.949, alpha: 1))
    private let muted = Aero.dynamic(
        light: UIColor(red: 0.545, green: 0.576, blue: 0.631, alpha: 1),
        dark: UIColor(red: 0.545, green: 0.576, blue: 0.631, alpha: 1))

    var body: some View {
        ZStack(alignment: .leading) {
            Color.black.opacity(0.5)
                .ignoresSafeArea()
                .onTapGesture { onClose() }
                .transition(.opacity)

            VStack(alignment: .leading, spacing: 0) {
                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                        header
                        newChat
                        Group {
                            sectionLabel("Recent")
                            row(title: "Q3 pricing strategy", icon: nil) { onRoute(.chat("demo-1")) }
                            row(title: "Kyoto trip plan", icon: nil) { onRoute(.chat("demo-2")) }
                            row(title: "Kotlin coroutines notes", icon: nil) { onRoute(.chat("demo-3")) }
                            row(title: "Brand voice guidelines", icon: nil) { onRoute(.chat("demo-4")) }
                            row(title: "Research: AI market", icon: nil) { onRoute(.chat("demo-5")) }
                        }
                        divider
                        Group {
                            sectionLabel("Explore")
                            row(title: "Chats", icon: "bubble.left") { onRoute(.chats) }
                            row(title: "Explore", icon: "safari") { onRoute(.explore) }
                            row(title: "Create", icon: "sparkles") { onRoute(.createTab) }
                            row(title: "Library", icon: "books.vertical") { onRoute(.library) }
                            row(title: "Projects", icon: "folder") { onRoute(.projects) }
                            row(title: "Assistants", icon: "smarttoy") { onRoute(.assistants) }
                            row(title: "Models", icon: "speed") { onRoute(.models) }
                            row(title: "Search", icon: "magnifyingglass") { onRoute(.search) }
                        }
                        divider
                        Group {
                            sectionLabel("Account")
                            row(title: "Upgrade plan", icon: "sparkles") { onRoute(.billing) }
                            row(title: "Notifications", icon: "bell") { onRoute(.notifications) }
                            row(title: "Profile", icon: "person") { onRoute(.profile) }
                            row(title: "Settings", icon: "gearshape") { onRoute(.settings) }
                        }
                    }
                    .padding(.horizontal, Aero.Spacing.m)
                    .padding(.bottom, Aero.Spacing.xl)
                }
            }
            .padding(.top, Aero.Spacing.xl)
            .frame(maxHeight: .infinity, alignment: .top)
            .background(panel)
            .transition(.move(edge: .leading).combined(with: .opacity))
        }
    }

    // MARK: Header

    private var header: some View {
        HStack(spacing: Aero.Spacing.s) {
            ZStack {
                Circle().fill(Aero.accent.opacity(0.16)).frame(width: 44, height: 44)
                Text("GA")
                    .font(Aero.label())
                    .foregroundColor(Aero.accent)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text("Grapsee Admin")
                    .font(Aero.body())
                    .foregroundColor(ink)
                Text("graphesee@gmail.com")
                    .font(Aero.caption())
                    .foregroundColor(muted)
            }
            Spacer()
            AeroChip(text: "Pro", selected: true) { onRoute(.billing) }
        }
        .padding(.bottom, Aero.Spacing.m)
    }

    private var newChat: some View {
        Button {
            onRoute(.chat(nil))
        } label: {
            HStack(spacing: Aero.Spacing.s) {
                Image(systemName: "square.and.pencil")
                    .font(.system(size: 14))
                    .foregroundColor(Aero.accent)
                Text("New chat")
                    .font(Aero.body())
                    .foregroundColor(ink)
                Spacer()
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.vertical, 13)
            .background(RoundedRectangle(cornerRadius: 14).fill(raised))
        }
        .buttonStyle(KineticPressStyle())
        .padding(.bottom, Aero.Spacing.m)
    }

    private var divider: some View {
        Rectangle()
            .fill(Aero.outline)
            .frame(height: 1)
            .padding(.vertical, Aero.Spacing.m)
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text.uppercased())
            .font(Aero.label())
            .foregroundColor(muted)
            .padding(.horizontal, 4)
            .padding(.bottom, Aero.Spacing.s)
    }

    private func row(title: String, icon: String?, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: Aero.Spacing.s) {
                if let icon {
                    Image(systemName: icon)
                        .font(.system(size: 13))
                        .foregroundColor(muted)
                        .frame(width: 18)
                }
                Text(title)
                    .font(Aero.body())
                    .foregroundColor(ink)
                    .lineLimit(1)
                Spacer()
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.vertical, 11)
            .contentShape(Rectangle())
        }
        .buttonStyle(KineticPressStyle())
    }
}
