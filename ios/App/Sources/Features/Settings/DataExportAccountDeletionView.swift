import SwiftUI

/// Data Export & Account Deletion —
/// docs/reference/pending/data-export-account-deletion.png.
///
/// The mock's three groups, in its order and at the sheet kit's measured type:
/// Export Your Data (two exports, the format tiles, the complete archive),
/// Data Retention (period, auto-delete, clear history) and Delete Your Account
/// (the warning card with its consent gate).
///
/// HONESTY — accepted deviations from the reference mock, D1–D7.
///
/// The mock is the bar for inventory, order, geometry, tones and glyphs. The
/// seven entries below are the ONLY places this page intentionally differs
/// from it, each explicitly accepted as deliberate rather than drifted into.
/// Any mismatch with the mock not listed here is a defect.
///
///  - D1 ACCEPTED — Auto-Delete Inactive Data ships OFF; the mock draws it
///    on. The switch's setter runs the purge the moment it turns on, and the
///    same purge runs at launch, so a default-on switch would start deleting
///    conversations on first launch. The honest default is off, with a real
///    reader behind the stored value. Changing the default is a product
///    decision in SettingsStore, not a render fix.
///  - D2 ACCEPTED — Archive row: the mock says "Download or Request Archive"
///    / "Export will be prepared and sent to your email." / "Request
///    Archive"; this build says "Download Complete Archive" / "Saved to a
///    file you choose on this device. Large exports may take some time." /
///    "Save Archive". It sends no mail, so it will not promise an email
///    delivery it cannot perform.
///  - D3 ACCEPTED — Conversations row: the mock says "and attachments.";
///    this build says "and attachment details." Attachment RECORDS travel
///    with a chat export; attachment bytes do not.
///  - D4 ACCEPTED — Retention row: the mock says "Choose how long to keep
///    your data after account inactivity or deletion."; this build says
///    "Choose how long to keep conversations you have not opened." The purge
///    only ever touches the local conversation corpus, so the mock's wider
///    noun would promise a sweep the code cannot perform.
///  - D5 ACCEPTED — Toggle detail: the mock says "Automatically delete data
///    after the retention period."; this build says "Automatically delete
///    conversations after the retention period." Same scope reason as D4.
///  - D6 ACCEPTED — Clear Chat History: the mock says "Permanently remove
///    all conversations from your account."; this build says "on this
///    device." There is no server account; conversations live only here.
///  - D7 ACCEPTED — Delete card: the mock says "This will remove your
///    account, all data, and access to Nexly. This action cannot be
///    undone."; this build names the local effect — conversations, saved
///    items, files and account details, then sign-out — because no server
///    account exists to delete. The honest scope is the local corpus plus
///    the local identity.
///
///  Mechanics the register rests on: an export writes a real JSON/PDF/CSV
///  file and hands it to the share sheet; Clear Chat History removes
///  conversations, messages and staged attachment copies while Library saves
///  stay; Delete Account removes all of that plus saved items and signs the
///  session out.
struct DataExportAccountDeletionView: View {

    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme
    @ObservedObject private var settings = SettingsStore.shared
    @AppStorage("gs.session.active") private var sessionActive = true

    @State private var format: DataExportFormat = .json
    @State private var exportURL: URL?
    @State private var showExport = false
    @State private var showRetention = false
    @State private var showClear = false
    @State private var showDelete = false
    @State private var understood = false
    @State private var notice: Notice?

    var body: some View {
        GsSheetPage(
            title: "Data Export & Account Deletion",
            subtitle: "Take control of your data. Export what you need or delete your account when you're ready.",
            onBack: { dismiss() }
        ) {
            // ---- Export Your Data -----------------------------------------
            GsSheetGroupHeader("Export Your Data", caption: "Download your information")
            GsGroupCard {
                GsSheetRow(
                    title: "Export Conversations",
                    detail: "Download your chat history, messages, and attachment details.",
                    action: { export(.conversations) }
                ) {
                    GsWellTile(glyph: "cloud-download", tone: .lilac, size: 34)
                } trailing: {
                    actionPill("Export")
                }
                divider
                GsSheetRow(
                    title: "Personal Data Archive",
                    detail: "Get a copy of your account data, profile information, and settings.",
                    action: { export(.personalArchive) }
                ) {
                    GsWellTile(glyph: "user", tone: .rose, size: 34)
                } trailing: {
                    actionPill("Export")
                }
                formatDivider
                formatBlock
                formatDivider
                GsSheetRow(
                    title: "Download Complete Archive",
                    detail: "Saved to a file you choose on this device. Large exports may take some time.",
                    action: { export(.complete) }
                ) {
                    archiveTile
                } trailing: {
                    actionPill("Save Archive")
                }
            }

            // ---- Data Retention -------------------------------------------
            GsSheetGroupHeader("Data Retention", caption: "Control how long we keep your data")
            GsGroupCard {
                GsSheetRow(
                    title: "Data Retention Period",
                    detail: "Choose how long to keep conversations you have not opened.",
                    action: { showRetention = true }
                ) {
                    GsWellTile(glyph: "clock", tone: .blue, size: 34)
                } trailing: {
                    actionPill("\(settings.dataRetentionMonths) months")
                }
                divider
                GsSheetToggle(
                    title: "Auto-Delete Inactive Data",
                    detail: "Automatically delete conversations after the retention period.",
                    isOn: autoDeleteBinding,
                    // The reference's lilac ON track (Android draws the same
                    // accentSoft → accentStrong wash in its pinned palette).
                    onColors: [Aero.refAccentSoft, Aero.refAccentStrong]
                ) {
                    GsWellTile(glyph: "trash", tone: .lilac, size: 34)
                }
                divider
                GsSheetRow(
                    title: "Clear Chat History",
                    detail: "Permanently remove all conversations on this device.",
                    action: { showClear = true }
                ) {
                    GsWellTile(glyph: "message-circle", tone: .peach, size: 34)
                } trailing: {
                    actionPill("Clear History")
                }
            }

            // ---- Delete Your Account --------------------------------------
            GsSheetGroupHeader("Delete Your Account", caption: "This action is permanent")
            deleteCard
            Spacer(minLength: 10)
        }
        .sheet(isPresented: $showExport) {
            if let exportURL {
                ActivitySheet(items: [exportURL])
            }
        }
        .gsDialog(isPresented: showRetention) {
            GsChoiceDialog(
                title: "Data Retention Period",
                subtitle: "Conversations untouched for longer than this are removed when auto-delete is on.",
                options: [3, 6, 12, 24].map { months in
                    GsChoice(
                        id: "\(months)",
                        label: "\(months) months",
                        detail: months == 24 ? "Two years \u{2014} the longest window offered" : nil,
                        selected: settings.dataRetentionMonths == months
                    )
                },
                onSelect: { choice in
                    guard let months = Int(choice.id) else { return }
                    settings.dataRetentionMonths = months
                    if settings.autoDeleteInactive {
                        purgeNow()
                    }
                    showRetention = false
                },
                onDismiss: { showRetention = false }
            )
        }
        .gsDialog(isPresented: showClear) {
            GsDialog(
                title: "Clear chat history?",
                message: "Every conversation and message on this device will be removed, along with staged attachment copies. Library saves stay. This cannot be undone.",
                confirmLabel: "Clear history",
                onConfirm: {
                    DataRetention.clearChatHistory()
                    GSHaptics.warning()
                    notice = Notice(
                        title: "Chat history cleared",
                        message: "Every conversation and message left this device. Library saves stayed.")
                },
                onDismiss: { showClear = false },
                destructive: true,
                glyph: "trash",
                tone: .rose,
                footnote: "Library saves stay."
            )
        }
        .gsDialog(isPresented: showDelete) {
            GsDialog(
                title: "Delete everything on this device?",
                message: "Your conversations, saved items, attachment files and account details will be removed, and you'll be signed out. This cannot be undone.",
                confirmLabel: "Delete account",
                onConfirm: {
                    DataRetention.deleteAllContent()
                    // Account details and the local plan activation go with the
                    // corpus; leaving either behind would make "account details"
                    // a half-truth the next sign-in inherits.
                    AccountStore.shared.setDisplayName("")
                    AccountStore.shared.setEmail("")
                    PlanStore.shared.clearActivation()
                    GSHaptics.warning()
                    // There is no server account to delete; the honest action is
                    // to close the identity with the local corpus.
                    sessionActive = false
                },
                onDismiss: { showDelete = false },
                destructive: true,
                glyph: "user-x",
                tone: .rose,
                footnote: "No server copy exists \u{2014} this is the whole record."
            )
        }
        .gsDialog(isPresented: notice != nil) {
            GsDialog(
                title: notice?.title ?? "",
                message: notice?.message ?? "",
                confirmLabel: "OK",
                onConfirm: {},
                onDismiss: { notice = nil },
                dismissLabel: nil,
                glyph: "circle-check",
                tone: .mint
            )
        }
    }

    // MARK: - Pieces

    private var divider: some View {
        Rectangle()
            .fill(Aero.divider.opacity(0.85))
            .frame(height: 1.0 / 3.0)
            .padding(.leading, 50)
            .padding(.trailing, 12)
    }

    /// The format block is a sub-section, not a row: the mock insets its two
    /// hairlines to the card edge instead of the 50pt row caption indent.
    private var formatDivider: some View {
        Rectangle()
            .fill(Aero.divider.opacity(0.85))
            .frame(height: 1.0 / 3.0)
            .padding(.horizontal, 12)
    }

    /// The mock's format block lives inside the export card: a heading, its
    /// whisper and three selectable tiles. The card, not the screen, owns the
    /// state, so the choice is visible where the exports are.
    private var formatBlock: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Export Format")
                .font(.custom(Aero.productName(.semibold), size: 12.5, relativeTo: .subheadline))
                .foregroundStyle(Aero.text)
            Text("Choose your preferred file format for export.")
                .font(.custom(Aero.productName(.regular), size: 8.5, relativeTo: .caption2))
                .foregroundStyle(Aero.textTertiary)
                .padding(.top, 1)
            HStack(spacing: 6) {
                ForEach(DataExportFormat.allCases) { option in
                    formatTile(option)
                }
            }
            .padding(.top, 8)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
    }

    private func formatTile(_ option: DataExportFormat) -> some View {
        let selected = format == option
        return Button {
            format = option
            GSHaptics.select()
        } label: {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 4) {
                    Text(option.label)
                        .font(.custom(Aero.productName(.medium), size: 10.5, relativeTo: .caption2))
                        .foregroundStyle(Aero.text)
                    Spacer(minLength: 0)
                    if selected {
                        NexlyGlyph(name: "check", size: 8, color: Aero.onRefAccent)
                            .frame(width: 13, height: 13)
                            .background(Circle().fill(Aero.refAccent))
                    }
                }
                Text(option.caption)
                    .font(.custom(Aero.productName(.regular), size: 8, relativeTo: .caption2))
                    .foregroundStyle(Aero.textTertiary)
                    .lineLimit(2)
                    .padding(.top, 3)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, minHeight: 42, alignment: .leading)
            .padding(.horizontal, 9)
            .padding(.vertical, 9)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(
                        selected
                            ? selectedTileFill
                            : Color.white.opacity(colorScheme == .dark ? 0.10 : 0.42))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(
                        selected ? Aero.refAccent.opacity(0.45) : Color.white.opacity(0.62),
                        lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
        .accessibilityLabel("\(option.label), \(option.caption)")
    }

    /// The reference art's lilac, not the product chrome's peach: this page is
    /// one of the Dreamy Pastel reference pages (docs/reference/pending), so
    /// every accent-tinted element reads the Aero.refAccent family and the
    /// Delete card keeps its rose/danger signal.
    private var selectedTileFill: Color {
        Aero.refAccentSoft
    }

    /// The mock's paper pill: content-sized, accent ink, a chevron. Drawn as a
    /// label because the row owns the click; two nested touch targets in one
    /// row would split the same action.
    private func actionPill(_ label: String) -> some View {
        HStack(spacing: 3) {
            Text(label)
                .font(.custom(Aero.productName(.semibold), size: 9.5, relativeTo: .caption2))
                .foregroundStyle(Aero.refAccentStrong)
                .lineLimit(1)
            NexlyGlyph(name: "chevron-right", size: 10, color: Aero.refAccentStrong.opacity(0.70))
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(Capsule().fill(Aero.refAccentSoft))
        .overlay(Capsule().strokeBorder(.white.opacity(0.85), lineWidth: 1))
    }

    /// The mock draws the archive row's download as a solid spring-green
    /// circle with a plain white arrow — the one well on the page that is not
    /// pale (sampled off the reference art: #7AE9BA → #51E5A4). The
    /// "download" glyph is in both platforms' Nexly inventories; Kotlin draws
    /// the same tile.
    private var archiveTile: some View {
        ZStack {
            Circle()
                .fill(
                    LinearGradient(
                        colors: [
                            Color(red: 0.478, green: 0.914, blue: 0.729),
                            Color(red: 0.318, green: 0.898, blue: 0.643),
                        ],
                        startPoint: .top, endPoint: .bottom))
            Circle().strokeBorder(.white.opacity(0.85), lineWidth: 1)
            NexlyGlyph(name: "download", size: 16, color: .white)
        }
        .frame(width: 34, height: 34)
        .shadow(
            color: Color(red: 0.090, green: 0.478, blue: 0.314).opacity(0.16),
            radius: 3, x: 0, y: 2)
    }

    private var deleteCard: some View {        VStack(alignment: .leading, spacing: 9) {
            HStack(spacing: 11) {
                ZStack {
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [
                                    Color.white.opacity(0.65),
                                    colorScheme == .dark
                                        ? Aero.danger.opacity(0.20)
                                        : Color(red: 0.984, green: 0.878, blue: 0.886),
                                ],
                                startPoint: .top, endPoint: .bottom))
                    Circle().strokeBorder(.white.opacity(0.85), lineWidth: 1)
                    NexlyGlyph(name: "alert-triangle", size: 18, color: Aero.danger)
                }
                .frame(width: 36, height: 36)
                VStack(alignment: .leading, spacing: 1) {
                    Text("Permanently Delete Account")
                        .font(.custom(Aero.productName(.semibold), size: 11.5, relativeTo: .caption))
                        .foregroundStyle(Aero.danger)
                    Text("This will remove your conversations, saved items, files and account details from this device, and sign you out. This action cannot be undone.")
                        .font(.custom(Aero.productName(.regular), size: 8, relativeTo: .caption2))
                        .foregroundStyle(Aero.textTertiary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(.horizontal, 4)
            .padding(.vertical, 4)

            VStack(spacing: 0) {
                Button {
                    withAnimation(.easeOut(duration: 0.15)) { understood.toggle() }
                    GSHaptics.select()
                } label: {
                    HStack(spacing: 8) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 4)
                                .fill(understood ? Aero.danger : .clear)
                            RoundedRectangle(cornerRadius: 4)
                                .strokeBorder(
                                    understood ? Aero.danger : Aero.danger.opacity(0.45),
                                    lineWidth: 1.5)
                            if understood {
                                NexlyGlyph(name: "check", size: 10, color: .white)
                            }
                        }
                        .frame(width: 14, height: 14)
                        Text("I understand that this action is permanent and I want to delete my account.")
                            .font(.custom(Aero.productName(.regular), size: 8, relativeTo: .caption2))
                            .foregroundStyle(Aero.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                        Spacer(minLength: 0)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityRepresentation {
                    Toggle(
                        "I understand that this action is permanent and I want to delete my account.",
                        isOn: $understood)
                }
                .padding(.vertical, 3)

                Button {
                    showDelete = true
                } label: {
                    HStack(spacing: 6) {
                        NexlyGlyph(name: "trash", size: 14, color: .white.opacity(0.92))
                        Text("Delete Account")
                            .font(.custom(Aero.productName(.semibold), size: 10, relativeTo: .caption2))
                            .foregroundStyle(.white.opacity(0.92))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 9)
                    .background(
                        Capsule().fill(
                            understood ? Aero.danger : Aero.danger.opacity(0.55))
                    )
                }
                .buttonStyle(KineticPressStyle())
                .disabled(!understood)
                .accessibilityHint("Requires the consent checkbox above")
            }
            .padding(10)
            .background(
                RoundedRectangle(cornerRadius: 18)
                    .fill(Color.white.opacity(colorScheme == .dark ? 0.08 : 0.36))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 18)
                    .strokeBorder(
                        Color.white.opacity(colorScheme == .dark ? 0.16 : 0.55),
                        lineWidth: 1)
            )
        }
        .padding(10)
        .background(
            RoundedRectangle(cornerRadius: 22)
                .fill(
                    LinearGradient(
                        colors: colorScheme == .dark
                            ? [Aero.danger.opacity(0.18), Aero.danger.opacity(0.10)]
                            : [
                                Color(red: 0.992, green: 0.945, blue: 0.949),
                                Color(red: 0.984, green: 0.890, blue: 0.910),
                            ],
                        startPoint: .top, endPoint: .bottom))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 22)
                .strokeBorder(
                    Color.white.opacity(colorScheme == .dark ? 0.18 : 0.62),
                    lineWidth: 1)
        )
    }

    // MARK: - Actions

    private var autoDeleteBinding: Binding<Bool> {
        Binding(
            get: { settings.autoDeleteInactive },
            set: { on in
                settings.autoDeleteInactive = on
                if on { purgeNow() }
            })
    }

    private func export(_ scope: DataExportScope) {
        let snapshot = DataExport.snapshot(scope: scope)
        let chosen = format
        let store = ConversationStore.shared
        Task {
            let url = await Task.detached(priority: .userInitiated) {
                var local = snapshot
                // The corpus is the heavy read. `exportMessages()` is
                // nonisolated and the SQLite queue makes it safe off-main, so
                // the screen never blocks on a large history.
                if local.scope != .personalArchive {
                    local.messages = store.exportMessages()
                }
                return DataExport.write(snapshot: local, format: chosen)
            }.value
            guard let url else {
                notice = Notice(
                    title: "Could not prepare the export",
                    message: "Nothing was written. Try again, or choose a smaller scope.")
                return
            }
            exportURL = url
            GSHaptics.success()
            showExport = true
        }
    }

    private func purgeNow() {
        let removed = DataRetention.purgeExpired()
        if removed > 0 {
            notice = Notice(
                title: "Inactive conversations removed",
                message: "\(removed) conversation\(removed == 1 ? "" : "s") older than the retention period left this device.")
        }
    }
}

/// A one-shot result message for the page: an export that could not be
/// prepared, or a purge that removed something. One alert slot, so the two
/// outcomes cannot replace each other.
private struct Notice {
    let title: String
    let message: String
}
