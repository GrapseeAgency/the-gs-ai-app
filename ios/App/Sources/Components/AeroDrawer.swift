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

    @State private var renameId: String?
    @State private var renameDraft = ""

    /// The account sheet is presented by the SHELL, not by this panel. When
    /// the drawer owned it, routing from a row happened while the sheet's own
    /// presenter was being removed in the same transaction, and the push was
    /// silently dropped (run 41014412869: Notifications closed the drawer and
    /// never mounted the inbox). One owner for the modal, one owner for the
    /// route.
    var onAccount: (() -> Void)? = nil

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

    /// The panel's surface. Measured on the sheet: body 239.5 L, a lit leading
    /// edge, and NO structure from the page behind it (HF p90 1.0 L) — so this
    /// is a frosted bloom, not a blur of Home.
    ///
    /// It is ANALYTIC, and it has to be. A `Material` is a backdrop blur the
    /// compositor resolves at display time; `drawHierarchy(in:afterScreenUpdates:)`
    /// — the only way a UI test can capture a window — does not run that pass, so
    /// a material panel would be scored from its flat fallback tint and the gate
    /// would be measuring a surface the device never draws. Every cue here is a
    /// gradient the renderer writes into the PNG: the cream body, the bloom
    /// inside it, and the lit edge. That is also the construction Android's
    /// `GlassFrostedPanel` uses (frost, veil, fill, rim), painted rather than
    /// composited.
    ///
    /// The body colour is the sheet's own: this renders (250.8, 238.7, 229.8) on
    /// the final capture, the sheet's margin measures (251.0, 237.2, 232.0), and
    /// it rendered (250.7, 238.6, 229.1) before this pass. The cream is NOT
    /// re-tinted to move the gate's body number — it already matches the picture.
    private var panelSurface: some View {
        ZStack {
            // Opaque ground: the panel must never show Home's bento cards
            // through it, which is the structure DR3's HF ceiling forbids.
            Aero.navSurface
            DrawerPanelSurface(dark: scheme == .dark)
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
            get: { renameId != nil },
            set: { if !$0 { renameId = nil } }
        )) {
            TextField("Chat name", text: $renameDraft)
            Button("Rename") {
                let trimmed = renameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
                if let id = renameId, !trimmed.isEmpty {
                    store.rename(id: id, to: trimmed)
                    sync(id, title: trimmed)
                    GSHaptics.success()
                }
                renameId = nil
            }
            Button("Cancel", role: .cancel) { renameId = nil }
        } message: {
            Text("Give this conversation a name you'll recognise.")
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
            leadingRim
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

    /// The sheet's lit leading edge: a lit bar with a bloom tail.
    ///
    /// Measured on the sheet at 1.7125 px/pt, by column median across the panel's
    /// rows: +4.5 L in the first 1.5 pt, a +9.5 L peak 2.3 pt in, and body again
    /// by 6 pt. Android paints a hard 1 pt bar at +7.6 L and then nothing. The
    /// previous iOS value was a 1.5 pt ramp from +7.2 straight down to 0, so half
    /// its nominal width was unlit and the 1.5 pt median came out +4.5 — a hairline
    /// where the sheet has a bar, which is what made the panel read as paper.
    ///
    /// This profile is a 1.25 pt bar at +7.0 L with the sheet's 5 pt tail. The
    /// effective white alpha over each panel's own body is 0.50 here, 0.39 on
    /// Android (7.6 L over 235.5) and 0.29 on the sheet at its frame edge (4.5 L
    /// over 239.5) — so the edge contrast lands within half a point of Android's
    /// while the tail is the fall the sheet carries and Android does not. Both
    /// numbers are below DR3's +14 L ceiling, and neither is a shadow or a blur:
    /// this is one `LinearGradient`, written in the same draw.
    ///
    /// The DARK profile is the same shape at a fifth of the alpha, and that is a
    /// measurement rather than a taste: 0.46 white over a 35 L body is +99 L, where
    /// Android's dark edge measures +20 L and no gate could see the difference —
    /// the light gate's [4, 14] band is about a 240 L body. `check_drawer_dark.py`
    /// now measures this edge as K6.
    private var leadingRim: some View {
        LinearGradient(
            gradient: Gradient(stops: DrawerRim.stops(dark: scheme == .dark)),
            startPoint: .leading,
            endPoint: .trailing
        )
        .frame(width: DrawerRim.width)
        .allowsHitTesting(false)
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
                    .font(Aero.specFont(DrawerMetrics.timestamp))
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
                GsIconView(GsIcons.sparkles, size: 22, color: .white.opacity(0.9))
            }
            VStack(alignment: .leading, spacing: 0) {
                Text("Nexly")
                    .font(Aero.specFont(DrawerMetrics.wordmark, .semibold))
                    .foregroundColor(Aero.text)
                Text("AI Companion")
                    .font(Aero.specFont(DrawerMetrics.subtitle))
                    .foregroundColor(DrawerMetrics.labelInk)
            }
            Spacer(minLength: 0)
            Button {
                GSHaptics.tap()
                onClose()
            } label: {
                GsIconView(GsIcons.x, size: DrawerMetrics.closeGlyph, color: Aero.text)
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
                GsIconView(GsIcons.search, size: DrawerMetrics.searchGlyph, color: DrawerMetrics.labelInk)
                Text("Search chats")
                    .font(Aero.specFont(DrawerMetrics.searchText))
                    .foregroundColor(DrawerMetrics.labelInk)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 16)
            .frame(minHeight: DrawerMetrics.searchHeight)
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
                    GsIconView(GsIcons.plus, size: 17, color: .white)
                }
                Text("New chat")
                    .font(Aero.specFont(DrawerMetrics.heroLabel, .semibold))
                    .foregroundColor(Aero.text)
                Spacer(minLength: 0)
                chevron
            }
            .padding(.leading, 8)
            .padding(.trailing, DrawerMetrics.chevronInset)
            .frame(minHeight: DrawerMetrics.heroHeight)
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
                disc(DrawerTopic.paths(named: destination.icon))
                Text(destination.label)
                    .font(Aero.specFont(DrawerMetrics.rowTitle))
                    .foregroundColor(activeRoute == destination.route ? Aero.accent : Aero.text)
                Spacer(minLength: 0)
                chevron
            }
            .padding(.leading, DrawerMetrics.contentInset)
            .padding(.trailing, DrawerMetrics.chevronInset)
            .frame(minHeight: DrawerMetrics.destPitch)
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
            onRoute(.chat(recent.id))
        } label: {
            HStack(spacing: DrawerMetrics.discToTitle) {
                disc(DrawerTopic.paths(title: recent.title, pinned: recent.pinned))
                VStack(alignment: .leading, spacing: 1) {
                    Text(recent.title)
                        .font(Aero.specFont(DrawerMetrics.chatTitle, .semibold))
                        .foregroundColor(Aero.text)
                        .lineLimit(1)
                    Text(recent.timestamp)
                        .font(Aero.specFont(DrawerMetrics.timestamp))
                        .foregroundColor(DrawerMetrics.timestampInk)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
                chevron
            }
            .padding(.leading, DrawerMetrics.contentInset)
            .padding(.trailing, DrawerMetrics.chevronInset)
            .frame(minHeight: DrawerMetrics.recentPitch)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(recent.title), \(recent.timestamp)")
        // The rebuild dropped the old drawer's per-row actions, which left Android
        // able to pin/rename/archive/delete from the drawer and iOS unable to —
        // a behavioural regression that the pixel gate could not see. Restored
        // against the same store and API calls the old code used.
        .contextMenu {
            Button {
                let next = !recent.pinned
                GSHaptics.success()
                store.setPinned(id: recent.id, next)
                sync(recent.id, pinned: next)
                // The row updates from the tap, not from the next presentation.
                if let index = recents.firstIndex(where: { $0.id == recent.id }) {
                    recents[index].pinned = next
                    recents[index].symbol = DrawerTopic.iconName(title: recents[index].title, pinned: next)
                }
            } label: {
                GsMenuLabel(recent.pinned ? "Unpin" : "Pin to top", icon: GsIcons.pin)
            }
            Button {
                renameId = recent.id
                renameDraft = recent.title
            } label: {
                GsMenuLabel("Rename…", icon: GsIcons.squarePen)
            }
            Button {
                GSHaptics.success()
                store.setArchived(id: recent.id, true)
                sync(recent.id, archived: true)
            } label: {
                GsMenuLabel("Archive", icon: GsIcons.archive)
            }
            Button(role: .destructive) {
                GSHaptics.warning()
                store.delete(id: recent.id)
                syncDelete(recent.id)
            } label: {
                GsMenuLabel("Delete", icon: GsIcons.trash2)
            }
        }
    }

    /// The disc holds the icon family's own glyph — the same geometry Android
    /// draws — rather than an SF Symbol. `GsIconView` had no caller before this:
    /// the iOS target shipped 12,000 lines of icon data and drew none of it, so
    /// the two apps had two icon languages.
    private func disc(_ icon: GsIconPaths) -> some View {
        ZStack {
            Circle()
                .fill(Color.white.opacity(scheme == .dark ? 0.10 : 0.62))
                .overlay(Circle().strokeBorder(
                    Color.white.opacity(scheme == .dark ? 0.16 : 0.75), lineWidth: 1))
                .frame(width: DrawerMetrics.disc, height: DrawerMetrics.disc)
            GsIconView(icon, size: DrawerMetrics.discGlyph,
                       color: Aero.text.opacity(0.75))
        }
    }

    private var chevron: some View {
        GsIconView(GsIcons.chevronRight, size: DrawerMetrics.chevron, color: DrawerMetrics.labelInk)
    }

    /// Reads the store once per presentation and keeps the result in `@State`.
    private func refreshRecents() {
        if let fixture = recentFixture {
            // prefix(4), not the whole fixture: the 500-chat capture drew twelve
            // bands against nine, and the panel's "four rows" has to be a property
            // of the panel rather than of where the rows came from.
            recents = Array(fixture.recents.prefix(4))
            return
        }
        recents = localRecents()
        // The drawer used to read ONLY the local store, so a chat the server had
        // auto-titled still read "New chat" here until something else refetched.
        // One pull per presentation, then the rows are rebuilt from the merged
        // store; a failed pull leaves the local rows exactly as they were.
        Task {
            guard await store.syncFromServer(limit: 30) else { return }
            recents = localRecents()
        }
    }

    /// The four newest local rows, as drawer rows.
    private func localRecents() -> [DrawerRecent] {
        ConversationStore.shared.activeConversations.prefix(4).map { conversation in
            let title = gsConversationTitle(conversation.title)
            return DrawerRecent(id: conversation.id, title: title,
                                timestamp: DrawerTime.relative(iso: conversation.updatedAt),
                                symbol: DrawerTopic.iconName(title: title, pinned: conversation.pinned),
                                pinned: conversation.pinned)
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
                onAccount?()
            } label: {
                HStack(spacing: 10) {
                    ZStack {
                        Circle().fill(Aero.accentSoft)
                            .frame(width: DrawerMetrics.avatar, height: DrawerMetrics.avatar)
                        Text(fixtureIdentity.initials)
                            .font(Aero.specFont(DrawerMetrics.footerName, .semibold))
                            .foregroundColor(Aero.text)
                    }
                    VStack(alignment: .leading, spacing: 1) {
                        Text(fixtureIdentity.name)
                            .font(Aero.specFont(DrawerMetrics.footerName, .semibold))
                            .foregroundColor(Aero.text)
                            .lineLimit(1)
                        Text(fixtureIdentity.workspace)
                            .font(Aero.specFont(DrawerMetrics.footerSub))
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
                    GsIconView(GsIcons.settings, size: DrawerMetrics.gearGlyph, color: Aero.text.opacity(0.8))
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Settings")
        }
        .padding(.horizontal, DrawerMetrics.contentInset)
        .padding(.top, 8)
        // 30 pt of clearance, ~the sheet's 34 pt above the home indicator: the
        // panel is full-bleed, so without it the footer sits under the indicator
        // and the panel's own bottom edge is pushed out of the frame.
        .padding(.bottom, 30)
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
}

/// The account reveal sheet — Profile / Settings / Notifications / Billing.
///
/// Presented as an IN-APP overlay by `RootView`, not as a native `.sheet`.
/// Two CI runs proved the native presentation wrong here: the row taps never
/// reached the buttons (the accessibility tree kept the sheet open and the
/// drawer behind it), so the inbox was unreachable through the account menu.
/// The app already paints its own drawer and glass sheets; one more overlay
/// keeps every tap on a plain SwiftUI button and removes the modal/presenter
/// class of failure entirely.
struct AccountSheetView: View {

    @ObservedObject private var account = AccountStore.shared

    /// The route currently on screen, so the matching row paints selected.
    var activeRoute: AeroRoute?
    let onRoute: (AeroRoute) -> Void

    init(activeRoute: AeroRoute?, onRoute: @escaping (AeroRoute) -> Void) {
        self.activeRoute = activeRoute
        self.onRoute = onRoute
    }

    var body: some View {
        AeroSheetShell(title: account.displayName.isEmpty ? "Account" : account.displayName) {
            VStack(spacing: Aero.Spacing.s) {
                row("Profile", icon: "User", route: .profile)
                row("Settings", icon: "Settings", route: .settings)
                row("Notifications", icon: "Bell", route: .notifications)
                row("Billing", icon: "CreditCard", route: .billing)
            }
        }
    }

    private func row(_ title: String, icon: String, route: AeroRoute) -> some View {
        let isSelected = activeRoute == route
        return Button {
            GSHaptics.tap()
            onRoute(route)
        } label: {
            HStack(spacing: 12) {
                GsIconView(DrawerTopic.paths(named: icon), size: 16,
                           color: isSelected ? Aero.accent : Aero.text.opacity(0.8))
                    .frame(width: 28)
                Text(title)
                    .font(Aero.specFont(DrawerMetrics.rowTitle))
                    .foregroundColor(isSelected ? Aero.accent : Aero.text)
                Spacer(minLength: 0)
                GsIconView(GsIcons.chevronRight, size: DrawerMetrics.chevron, color: DrawerMetrics.labelInk)
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

/// The leading edge's profile in POINTS, so the numbers this file and
/// `docs/DRAWER_STATUS.md` quote are the numbers it draws: a 1.25 pt bar, a 2 pt
/// shoulder, a 3 pt fall and a 4.5 pt tail, transparent from 6.5 pt.
///
/// The stop positions are divided by the gradient's own width here rather than
/// written as bare fractions at the call site — that is what let an earlier
/// version's "1.5 pt" claim drift from the 0.16-of-8 it actually drew.
///
/// The dark alphas are the light ones at about a fifth: over a 35 L body the
/// light bar is +99 L where Android's dark edge is +20 L. K6 in
/// `check_drawer_dark.py` is the measurement that holds this.
private enum DrawerRim {
    /// The widest the gradient ever draws; every stop is a fraction of it.
    static let width: CGFloat = 8

    static func stops(dark: Bool) -> [Gradient.Stop] {
        let profile: [(point: CGFloat, alpha: Double)] = dark
            ? [(0, 0.10), (1.25, 0.10), (2, 0.06), (3, 0.03), (4.5, 0.015), (6.5, 0)]
            : [(0, 0.50), (1.25, 0.50), (2, 0.34), (3, 0.20), (4.5, 0.08), (6.5, 0)]
        return profile.map {
            Gradient.Stop(color: Color.white.opacity($0.alpha),
                          location: $0.point / width)
        }
    }
}

/// The panel's surface, painted in ONE `Canvas`: the cream body, the bloom the
/// glass is seen through, and the sheet's warm wash.
///
/// The sheet's panel interior is a low-frequency field — p90 derivative 1.0 L,
/// no card-shaped steps, 236 L at the leading margin rising to 246 L where the
/// backdrop's light sits behind the header. It is the diffused image of the page
/// under the glass, and there is no way to obtain it honestly here: `Material`
/// does not composite into a `drawHierarchy` capture, and a live blur would put
/// Home's bento cards *into* the glass (HF p90 1.0 L is the sheet's proof that
/// the reference has no card edges in it). Android paints the same field from
/// radial gradients for the same reason.
///
/// ONE `Canvas`, not four stacked SwiftUI gradients, because STACKED GRADIENTS
/// ARE WHAT THE GATE MEASURES. SwiftUI dithers every gradient layer separately
/// to stop banding, and the dither does not cancel: three stacked radians took
/// the panel's blank margin from HF p90 2.72 L to 5.07 L (run 40201896377) —
/// over DR3's 3.0 L ceiling, i.e. the bloom scored as the ghosting the ceiling
/// exists to catch. The surface is one Core Graphics pass now, so the field
/// costs no layer and the fill's own dither has nothing to add to.
///
/// Amplitudes are deliberately small — each glow is a 2–3 L lift over the 240 L
/// body, which is what the sheet's own low-pass shows in this band. A stronger
/// wash would read as a spotlight rather than as light in glass. In dark the
/// three glows are scaled to 12 % because the sheet has no dark frame and a
/// daytime bloom over a 35 L panel reads as a smear; the warm wash below is NOT
/// scaled — it is the peach the header has always carried in both appearances,
/// and the dark gate measures the result (panel 28.70 → 34.56 L).
///
/// INPUTS MUST STAY `dark` AND THE PROPOSED SIZE. The panel lives inside the
/// subtree whose `.offset` follows the finger, so anything this struct read from
/// gesture state would make a full-panel Core Graphics raster (≈2.1 MPix, five
/// fills) run per touch sample. With one `Bool` as its only input, SwiftUI's
/// structural comparison short-circuits it and the drag stays a compositing
/// transform — the same profile the three gradient layers had.
private struct DrawerPanelSurface: View {
    let dark: Bool

    var body: some View {
        Canvas { ctx, size in
            let w = size.width
            let h = size.height
            let s = dark ? 0.12 : 1.0
            let field = Path(CGRect(origin: .zero, size: size))

            // 1. The frosted cream, 85% over the ground in light — the layer
            //    that renders the sheet's 240 L body: (250.8, 238.7, 229.8) on
            //    the final capture against the sheet's (251.0, 237.2, 232.0).
            //    Dark is the 4% white lift instead: the first version painted the
            //    LIGHT cream in both appearances, so the dark capture measured
            //    206 L — a light panel behind a dark page.
            let cream = dark
                ? [Color.white.opacity(0.054), Color.white.opacity(0)]
                : [Color(red: 1.0, green: 0.94, blue: 0.90).opacity(0.85),
                   Color(red: 0.98, green: 0.92, blue: 0.89).opacity(0.85)]
            ctx.fill(field, with: .linearGradient(
                Gradient(colors: cream),
                startPoint: .zero,
                endPoint: CGPoint(x: w, y: h)))

            // 2. The bloom. x and radius are fractions of the panel's width, y
            //    of its height; one radial per row, in painterly order.
            let glows: [(r: Double, g: Double, b: Double, a: Double,
                         x: CGFloat, y: CGFloat, radius: CGFloat)] = [
                // The warm light behind the header — the sheet's brightest part
                // of the field, and the reason its R−B reads +23 there.
                (1.0, 0.955, 0.90, 0.55, 0.02, 0.0, 1.05),
                // The blush down the leading edge.
                (1.0, 0.93, 0.93, 0.28, 0.00, 0.42, 0.85),
                // Cool light under the footer: the sheet's field is a degree
                // cooler at the bottom than at the top.
                (0.965, 0.955, 0.95, 0.30, 0.45, 1.0, 0.95),
            ]
            for glow in glows {
                let colour = Color(red: glow.r, green: glow.g, blue: glow.b)
                ctx.fill(field, with: .radialGradient(
                    Gradient(stops: [
                        .init(color: colour.opacity(glow.a * s), location: 0),
                        .init(color: colour.opacity(0), location: 1),
                    ]),
                    center: CGPoint(x: w * glow.x, y: h * glow.y),
                    startRadius: 0,
                    endRadius: w * glow.radius))
            }

            // 3. The sheet's warm wash, top-leading to the centre — the layer
            //    the panel carried before this pass, in the same place and the
            //    same alpha, so the peach at the header does not move.
            ctx.fill(field, with: .linearGradient(
                Gradient(colors: [Color(red: 1, green: 0.70, blue: 0.43).opacity(0.16),
                                  .clear]),
                startPoint: .zero,
                endPoint: CGPoint(x: w * 0.5, y: h * 0.5)))
        }
        .allowsHitTesting(false)
    }
}
