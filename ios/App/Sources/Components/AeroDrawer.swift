import SwiftUI

/**
 * AERUO KINETIC drawer — a CHAT HISTORY PANEL, not a route directory.
 * The sidebar is primarily about starting and navigating conversations:
 *
 *   1. ACCOUNT — one compact identity row; opening it reveals the account
 *      controls (Profile · Settings · Notifications · Billing) in a sheet
 *      instead of spending four sidebar rows on them.
 *   2. NEW CHAT + SEARCH — the two conversation actions, always first.
 *   3. RECENT — the visual heart: live conversations, tap to open,
 *      long-press for pin / rename / archive / delete. Genuine empty state —
 *      nothing is invented. "All chats" is the quiet overflow into the full
 *      history hub.
 *   4. VOICE — the one wired non-chat capability, a real row. The More
 *      sheet it used to live behind (Explore / Create / Projects / Library /
 *      Assistants demo catalogues) is deleted outright — the app opens as
 *      chat + voice + account.
 *
 * Machinery (tracked dismissal, VoiceOver containment, rename alert,
 * edge-swipe entry) is unchanged.
 */
struct AeroDrawer: View {

    /// Opening-drag offset owned by the host (edge-swipe rides the finger);
    /// the drawer adds its own close-drag tracking on top of it.
    @Binding var entryOffset: CGFloat
    var onRoute: (AeroRoute) -> Void
    var onClose: () -> Void

    /// Live selected-state input: the route currently on screen.
    var activeRoute: AeroRoute? = nil

    /// A capture needs four rows and a name without a database. Production
    /// passes nil and the drawer reads the store ONCE per presentation (below) —
    /// the old code called `store.activeConversations` from `body`, which is an
    /// O(n log n) sort on every drag sample.
    var recentFixture: DrawerFixture? = nil

    @State private var recents: [DrawerRecent] = []
    @Environment(\.colorScheme) private var scheme

    @ObservedObject private var store = ConversationStore.shared
    /// The drawer header shows the REAL stored identity — it re-renders the
    /// moment the account changes (sign-in, name edit).
    @ObservedObject private var account = AccountStore.shared

    @State private var renameTarget: StoredConversation?
    @State private var renameDraft = ""
    @State private var accountSheet = false

    /// Close-drag tracking (Task 85-e I9): while the finger is down the panel
    /// follows it 1:1 leftward; the state flip only happens on release, and
    /// only when the drag (or its projected fling) earns the dismissal.
    @State private var closeOffset: CGFloat = 0

    /// Panel geometry: bounded width, leading-anchored — the panel must never
    /// span the full screen; the scrim beyond it stays tappable and
    /// VoiceOver-reachable.
    private var panelWidth: CGFloat {
        DrawerMetrics.panelWidth(in: UIScreen.main.bounds.width)
    }

    /// The panel's surface. Measured on the sheet: body 237 L, a lit rim on the
    /// leading edge, and NO structure from the page behind it (HF p90 1.79 L) —
    /// so this is a frosted bloom, not a blur of Home. It is drawn from the
    /// backdrop's own blooms plus the warm wash, which is what a live blur would
    /// have to approximate anyway, without an offscreen pass per frame.
    private var panelSurface: some View {
        ZStack {
            Aero.navSurface
            // In dark the wash is a 4% white lift over the dark nav surface. The
            // first version painted the LIGHT cream at 85% in both appearances, so
            // the dark capture measured 206 L — a light panel behind a dark page.
            LinearGradient(
                colors: scheme == .dark
                    ? [Color.white.opacity(0.06), .clear]
                    : [Color(red: 1.0, green: 0.94, blue: 0.90),
                       Color(red: 0.98, green: 0.92, blue: 0.89)],
                startPoint: .topLeading, endPoint: .bottomTrailing
            ).opacity(scheme == .dark ? 0.9 : 0.85)
            LinearGradient(
                colors: [Color(red: 1, green: 0.70, blue: 0.43).opacity(0.16), .clear],
                startPoint: .topLeading,
                endPoint: .center
            )
        }
    }

    var body: some View {
        ZStack(alignment: .leading) {
            scrimLayer
            panelLayer
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Navigation menu")
        .accessibilityAction(.escape) { onClose() }
        .alert("Rename chat", isPresented: Binding(
            get: { renameTarget != nil },
            set: { if !$0 { renameTarget = nil } }
        )) {
            TextField("Chat name", text: $renameDraft)
            Button("Rename") {
                let trimmed = renameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
                if let target = renameTarget, !trimmed.isEmpty {
                    store.rename(id: target.id, to: trimmed)
                    sync(target.id, title: trimmed)
                    GSHaptics.success()
                }
                renameTarget = nil
            }
            Button("Cancel", role: .cancel) { renameTarget = nil }
        } message: {
            Text("Give this conversation a name you'll recognise.")
        }
        .sheet(isPresented: $accountSheet) {
            accountSheetView
                .presentationDetents([.medium])
        }
    }

    // MARK: Body building blocks (type-check split — the previous single
    // expression exceeded the Swift type-checker's budget; same view tree,
    // decomposed into named sub-expressions)

    private var scrimLayer: some View {
        DrawerMetrics.scrim
            .ignoresSafeArea()
    }

    private var panelLayer: some View {
        VStack(alignment: .leading, spacing: 0) {
            brandHeader
            ScrollView(showsIndicators: false) {
                panelList
            }
            .frame(maxHeight: .infinity)
            footer
        }
        .frame(width: panelWidth, alignment: .top)
        .frame(maxHeight: .infinity, alignment: .top)
        .background(panelSurface)
        .overlay(alignment: .leading) {
            // The sheet's lit leading edge: ~+6 L over the body, 1 pt wide.
            LinearGradient(colors: [Color.white.opacity(0.55), .clear],
                           startPoint: .leading, endPoint: .trailing)
                .frame(width: 1.5)
        }
        .clipShape(UnevenRoundedRectangle(bottomTrailingRadius: DrawerMetrics.cornerRadius))
        .offset(x: entryOffset + closeOffset)
        .gesture(closeDragGesture)
        .transition(.move(edge: .leading).combined(with: .opacity))
        .onAppear {
            // A stale tracked offset must never survive a fresh presentation.
            closeOffset = 0
            refreshRecents()
        }
    }

    private var panelList: some View {
        LazyVStack(alignment: .leading, spacing: 0) {
            searchPill
            heroRow
            drawerSectionLabel("YOUR SPACE")
            ForEach(DrawerDestination.allCases) { destinationRow($0) }
            dividerRow
            drawerSectionLabel("RECENT CHATS")
            if recents.isEmpty {
                Text("No conversations yet.")
                    .font(.system(size: DrawerMetrics.timestamp))
                    .foregroundColor(DrawerMetrics.timestampInk)
                    .padding(.leading, DrawerMetrics.contentInset)
                    .padding(.vertical, 8)
            } else {
                ForEach(recents) { recentRow($0) }
            }
            dividerRow
        }
        .padding(.bottom, 4)
    }

    /// The brand lockup: the product orb, the wordmark and its subtitle, and the
    /// close control. The sheet's orb starts 62.7 pt from the frame top, i.e.
    /// 3.7 pt below a 59 pt safe area, which is why the padding is 4.
    private var brandHeader: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(LinearGradient(colors: [Color(red: 1, green: 0.914, blue: 0.835),
                                                  Color(red: 1, green: 0.725, blue: 0.627)],
                                         startPoint: .topLeading, endPoint: .bottomTrailing))
                    .frame(width: DrawerMetrics.headerOrb, height: DrawerMetrics.headerOrb)
                Image(systemName: "sparkles")
                    .font(.system(size: 22, weight: .regular))
                    .foregroundColor(.white.opacity(0.9))
            }
            VStack(alignment: .leading, spacing: 0) {
                Text("Nexly")
                    .font(.system(size: DrawerMetrics.wordmark, weight: .semibold))
                    .foregroundColor(Aero.text)
                Text("AI Companion")
                    .font(.system(size: DrawerMetrics.subtitle))
                    .foregroundColor(DrawerMetrics.labelInk)
            }
            Spacer(minLength: 0)
            Button {
                GSHaptics.tap()
                onClose()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: DrawerMetrics.closeGlyph, weight: .medium))
                    .foregroundColor(Aero.text)
                    .frame(width: DrawerMetrics.closeHit + 10,
                           height: DrawerMetrics.closeHit + 10)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Close menu")
        }
        .padding(.leading, DrawerMetrics.contentInset + 4)
        .padding(.trailing, DrawerMetrics.contentInset)
        .padding(.top, 4)
        .padding(.bottom, 8)
    }

    /// The magnifier + placeholder pill. It opens the real search screen; the
    /// sheet draws it as a field, and a field that filters in place would
    /// duplicate a screen that already does.
    private var searchPill: some View {
        Button {
            GSHaptics.tap()
            onRoute(.chatSearch)
        } label: {
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: DrawerMetrics.searchGlyph))
                    .foregroundColor(DrawerMetrics.labelInk)
                Text("Search chats")
                    .font(.system(size: DrawerMetrics.searchText))
                    .foregroundColor(DrawerMetrics.labelInk)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 16)
            .frame(height: DrawerMetrics.searchHeight)
            .background(Capsule().fill(Color.white.opacity(scheme == .dark ? 0.10 : 0.55)))
            .overlay(Capsule().strokeBorder(Color.white.opacity(scheme == .dark ? 0.14 : 0.65),
                                            lineWidth: 1))
            .padding(.horizontal, DrawerMetrics.contentInset)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Search chats")
        .padding(.top, 18)
    }

    /// "New chat" — the sheet's one warm row.
    private var heroRow: some View {
        Button {
            GSHaptics.tap()
            onRoute(.chat(nil))
        } label: {
            HStack(spacing: DrawerMetrics.discToTitle) {
                ZStack {
                    Circle()
                        .fill(LinearGradient(colors: [DrawerMetrics.heroOrbTop,
                                                      DrawerMetrics.heroOrbBottom],
                                             startPoint: .top, endPoint: .bottom))
                        .frame(width: DrawerMetrics.heroOrb, height: DrawerMetrics.heroOrb)
                    Image(systemName: "plus")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(.white)
                }
                Text("New chat")
                    .font(.system(size: DrawerMetrics.heroLabel, weight: .semibold))
                    .foregroundColor(Aero.text)
                Spacer(minLength: 0)
                chevron
            }
            .padding(.leading, 8)
            .padding(.trailing, DrawerMetrics.chevronInset)
            .frame(height: DrawerMetrics.heroHeight)
            .background(
                RoundedRectangle(cornerRadius: DrawerMetrics.heroRadius)
                    .fill(LinearGradient(
                        colors: scheme == .dark
                            ? [DrawerMetrics.heroLeft.opacity(0.30),
                               DrawerMetrics.heroRight.opacity(0.12)]
                            : [DrawerMetrics.heroLeft, DrawerMetrics.heroRight],
                        startPoint: .leading, endPoint: .trailing))
            )
            .padding(.horizontal, DrawerMetrics.contentInset)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("New chat")
        .padding(.top, DrawerMetrics.pillToHero)
    }

    private func drawerSectionLabel(_ text: String) -> some View {
        Text(text)
            .font(DrawerMetrics.sectionLabelStyle())
            .tracking(DrawerMetrics.sectionLabel * DrawerMetrics.sectionTracking)
            .foregroundColor(DrawerMetrics.labelInk)
            .padding(.leading, DrawerMetrics.contentInset)
            .padding(.top, 16)
            .padding(.bottom, 8)
    }

    private var dividerRow: some View {
        Rectangle()
            .fill(Aero.outline)
            .frame(height: 1)
            .padding(.horizontal, DrawerMetrics.contentInset + 4)
            .padding(.vertical, 6)
    }

    private func destinationRow(_ destination: DrawerDestination) -> some View {
        Button {
            GSHaptics.tap()
            onRoute(destination.route)
        } label: {
            HStack(spacing: DrawerMetrics.discToTitle) {
                disc(destination.symbol)
                Text(destination.label)
                    .font(.system(size: DrawerMetrics.rowTitle))
                    .foregroundColor(activeRoute == destination.route ? Aero.accent : Aero.text)
                Spacer(minLength: 0)
                chevron
            }
            .padding(.leading, DrawerMetrics.contentInset)
            .padding(.trailing, DrawerMetrics.chevronInset)
            .frame(height: DrawerMetrics.destPitch)
            .background(
                RoundedRectangle(cornerRadius: 14)
                    .fill(activeRoute == destination.route
                          ? Aero.accent.opacity(0.12) : Color.clear)
            )
            .padding(.horizontal, 0)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(destination.label)
        .accessibilityAddTraits(activeRoute == destination.route ? .isSelected : [])
    }

    private func recentRow(_ recent: DrawerRecent) -> some View {
        Button {
            GSHaptics.tap()
            if let match = ConversationStore.shared.activeConversations.first(where: { $0.id == recent.id }) {
                onRoute(.chat(match.id))
            } else if recentFixture != nil {
                onRoute(.chat(nil))
            }
        } label: {
            HStack(spacing: DrawerMetrics.discToTitle) {
                disc(recent.symbol)
                VStack(alignment: .leading, spacing: 1) {
                    Text(recent.title)
                        .font(.system(size: DrawerMetrics.chatTitle, weight: .semibold))
                        .foregroundColor(Aero.text)
                        .lineLimit(1)
                    Text(recent.timestamp)
                        .font(.system(size: DrawerMetrics.timestamp))
                        .foregroundColor(DrawerMetrics.timestampInk)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
                chevron
            }
            .padding(.leading, DrawerMetrics.contentInset)
            .padding(.trailing, DrawerMetrics.chevronInset)
            .frame(height: DrawerMetrics.recentPitch)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(recent.title), \(recent.timestamp)")
    }

    private func disc(_ symbol: String) -> some View {
        ZStack {
            Circle()
                .fill(Color.white.opacity(scheme == .dark ? 0.10 : 0.62))
                .overlay(Circle().strokeBorder(
                    Color.white.opacity(scheme == .dark ? 0.16 : 0.75), lineWidth: 1))
                .frame(width: DrawerMetrics.disc, height: DrawerMetrics.disc)
            Image(systemName: symbol)
                .font(.system(size: 16))
                .foregroundColor(Aero.text.opacity(0.75))
        }
    }

    private var chevron: some View {
        Image(systemName: "chevron.right")
            .font(.system(size: DrawerMetrics.chevron, weight: .medium))
            .foregroundColor(DrawerMetrics.labelInk)
    }

    /// Reads the store once per presentation and keeps the result in `@State`.
    private func refreshRecents() {
        if let fixture = recentFixture {
            recents = fixture.recents
            return
        }
        recents = ConversationStore.shared.activeConversations.prefix(4).map { conversation in
            let title = gsConversationTitle(conversation.title)
            return DrawerRecent(id: conversation.id, title: title,
                                timestamp: DrawerTime.relative(iso: conversation.updatedAt),
                                symbol: DrawerTime.symbol(for: title))
        }
    }

    /// The reference's destinations, in its order (New chat + Search already
    /// live in `newChatRow`). Each routes to a real screen.
    /// Voice — the one wired non-chat capability, promoted to a real row.
    /// Account footer — pinned to the bottom of the panel, outside the
    /// scroll: identity + account sheet stay reachable from any scroll
    /// position (the More row that used to sit here is gone).
    private var footer: some View {
        HStack(spacing: 10) {
            Button {
                GSHaptics.tap()
                accountSheet = true
            } label: {
                HStack(spacing: 10) {
                    ZStack {
                        Circle().fill(Aero.accentSoft)
                            .frame(width: DrawerMetrics.avatar, height: DrawerMetrics.avatar)
                        Text(fixtureIdentity.initials)
                            .font(.system(size: DrawerMetrics.footerName, weight: .semibold))
                            .foregroundColor(Aero.text)
                    }
                    VStack(alignment: .leading, spacing: 1) {
                        Text(fixtureIdentity.name)
                            .font(.system(size: DrawerMetrics.footerName, weight: .semibold))
                            .foregroundColor(Aero.text)
                            .lineLimit(1)
                        Text(fixtureIdentity.workspace)
                            .font(.system(size: DrawerMetrics.footerSub))
                            .foregroundColor(DrawerMetrics.timestampInk)
                            .lineLimit(1)
                    }
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Account options")

            Button {
                GSHaptics.tap()
                onRoute(.settings)
            } label: {
                ZStack {
                    Circle().fill(Color.white.opacity(scheme == .dark ? 0.10 : 0.62))
                        .overlay(Circle().strokeBorder(
                            Color.white.opacity(scheme == .dark ? 0.16 : 0.75), lineWidth: 1))
                        .frame(width: DrawerMetrics.gear, height: DrawerMetrics.gear)
                    Image(systemName: "gearshape")
                        .font(.system(size: DrawerMetrics.gearGlyph))
                        .foregroundColor(Aero.text.opacity(0.8))
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Settings")
        }
        .padding(.horizontal, DrawerMetrics.contentInset)
        .padding(.vertical, 8)
    }

    /// The identity the footer shows: the fixture when a capture is running, the
    /// real account otherwise.
    private var fixtureIdentity: (name: String, workspace: String, initials: String) {
        if let fixture = recentFixture {
            return (fixture.name, fixture.workspace, fixture.initials)
        }
        let name = account.displayName.isEmpty ? "Your account" : account.displayName
        return (name, "Personal workspace", String(name.prefix(1)).uppercased())
    }

    /// New chat + Search — the two conversation actions, always first.
    /// The heart of the sidebar — live conversations only. When the store is
    /// empty the panel says so honestly; nothing is invented.
    // MARK: Account reveal sheet

    private var accountSheetView: some View {
        AeroSheetShell(title: account.displayName.isEmpty ? "Account" : account.displayName) {
            VStack(spacing: Aero.Spacing.s) {
                sheetRow(title: "Profile", icon: "person", isSelected: activeRoute == .profile) { onRoute(.profile) }
                sheetRow(title: "Settings", icon: "gearshape", isSelected: activeRoute == .settings) { onRoute(.settings) }
                sheetRow(title: "Notifications", icon: "bell", isSelected: activeRoute == .notifications) { onRoute(.notifications) }
                sheetRow(title: "Billing", icon: "creditcard", isSelected: activeRoute == .billing) { onRoute(.billing) }
            }
        }
    }

    // MARK: Finger-tracked dismissal (Task 85-e I9)

    /// Leftward tracking is clamped at one screen; a rightward pull against
    /// the resting edge rubber-bands (damped to 12%).
    private static func trackedOffset(_ raw: CGFloat) -> CGFloat {
        if raw < 0 {
            return max(raw, -UIScreen.main.bounds.width)
        }
        return raw * 0.12
    }

    /// Drag → panel offset → release decision, like a UIKit drawer:
    /// committed drags and leftward flings dismiss, everything else springs
    /// back. `predictedEndTranslation` is SwiftUI's built-in velocity signal
    /// (DragGesture.Value exposes no raw velocity).
    private var closeDragGesture: some Gesture {
        DragGesture(minimumDistance: 20)
            .onChanged { value in
                closeOffset = Self.trackedOffset(value.translation.width)
            }
            .onEnded { value in
                let raw = value.translation.width
                let projected = value.predictedEndTranslation.width
                if raw < -110 || projected < -240 {
                    onClose()
                } else {
                    withAnimation(Aero.motion(Aero.spring)) { closeOffset = 0 }
                }
            }
    }

    private func sync(_ id: String, pinned: Bool? = nil, archived: Bool? = nil, title: String? = nil) {
        guard !id.hasPrefix("demo-"), !id.hasPrefix("local-") else { return }
        Task { try? await APIClient.shared.updateConversation(id: id, pinned: pinned, archived: archived, title: title) }
    }

    private func syncDelete(_ id: String) {
        guard !id.hasPrefix("demo-"), !id.hasPrefix("local-") else { return }
        Task { try? await APIClient.shared.deleteConversation(id: id) }
    }

    // MARK: Header

    /// Account header — the REAL stored identity (AccountStore; neutral
    /// fallback when the reader never gave a name). The whole row reveals the
    /// account sheet (Profile / Settings / Notifications / Billing) instead
    /// of spending sidebar rows on account chrome.
    /// Up to two leading initials of the stored display name — derived only,
    /// never invented (empty name never reaches here).
    private var accountInitials: String {
        account.displayName
            .split(separator: " ")
            .prefix(2)
            .map { String($0.prefix(1)).uppercased() }
            .joined()
    }

    /// One row inside the account / More reveal sheets. The route handler in
    /// RootView closes the drawer itself (which unmounts the sheet with it) —
    /// so the row only routes.
    private func sheetRow(title: String, icon: String, isSelected: Bool = false,
                          action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.system(size: 16))
                    .foregroundColor(isSelected ? Aero.accent : Aero.text.opacity(0.8))
                    .frame(width: 28)
                Text(title)
                    .font(.system(size: DrawerMetrics.rowTitle))
                    .foregroundColor(isSelected ? Aero.accent : Aero.text)
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.system(size: DrawerMetrics.chevron, weight: .medium))
                    .foregroundColor(DrawerMetrics.labelInk)
            }
            .padding(.horizontal, 12)
            .frame(height: 52)
            .background(RoundedRectangle(cornerRadius: 16)
                .fill(isSelected ? Aero.accent.opacity(0.12) : Color.clear))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
    }
}
