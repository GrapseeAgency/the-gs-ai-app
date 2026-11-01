import SwiftUI
import UIKit

/// Shared Conversations (docs/reference/pending/shared-conversations.png) —
/// the two tabs, the share explainer card with its Learn More pill, the
/// section header with its count, the shared rows, and the reference's own
/// orb art over the empty state with the Share a Conversation action.
/// Sharing is local and real: the system share sheet hands the transcript to
/// whatever the reader picks, and the store records what is true.
struct SharedConversationsView: View {
    @ObservedObject private var shares = SharedConversationsStore.shared
    @ObservedObject private var conversations = ConversationStore.shared
    @EnvironmentObject private var router: Router
    @Environment(\.dismiss) private var dismiss
    @State private var tab = "others"
    @State private var learnMoreOpen = false
    @State private var pickOpen = false
    @State private var removeTarget: SharedConversationsStore.Share?

    var body: some View {
        GsSheetPage(
            title: "Shared Conversations",
            subtitle: "View and manage conversations you've shared.",
            onBack: { dismiss() },
            headerTrailing: {
                AnyView(
                    Button {
                        learnMoreOpen = true
                    } label: {
                        NexlyGlyph(name: "dots-vertical", size: 16, color: Aero.text)
                            .frame(width: 36, height: 36)
                            .gsGlass(.floatingControl, corner: 18)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("More options")
                )
            }
        ) {
            tabs.padding(.bottom, 10)
            shareCard.padding(.bottom, 6)
            HStack {
                Text(tab == "others" ? "Shared with Others" : "Shared by You")
                    .font(.custom(Aero.productName(.semibold), size: 13, relativeTo: .headline))
                    .foregroundStyle(Aero.text)
                Spacer()
                Text("\(shares.entries.count) conversation\(shares.entries.count == 1 ? "" : "s")")
                    .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption))
                    .foregroundStyle(Aero.textTertiary)
            }
            .padding(.vertical, 10)

            if shares.entries.isEmpty {
                VStack(spacing: 0) {
                    Canvas { context, _ in
                        GsRefScenes.sharedOrb(&context, CGSize(width: 237, height: 156))
                    }
                    .frame(width: 237, height: 156)
                    .accessibilityHidden(true)
                    Text("No Shared Conversations Yet")
                        .font(.custom(Aero.productName(.semibold), size: 17, relativeTo: .title3))
                        .foregroundStyle(Aero.text)
                        .padding(.top, 2)
                    Text("Share your conversations to collaborate,\nlearn and discover together.")
                        .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption))
                        .foregroundStyle(Aero.textSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.top, 6)
                    Button {
                        pickOpen = true
                    } label: {
                        HStack(spacing: 8) {
                            NexlyGlyph(name: "share-2", size: 16, color: .white)
                            Text("Share a Conversation")
                                .font(.custom(Aero.productName(.semibold), size: 13, relativeTo: .body))
                                .foregroundStyle(.white)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 13)
                        .background(
                            Capsule().fill(
                                LinearGradient(colors: [Aero.refAccent.opacity(0.72), Aero.refAccent],
                                               startPoint: .leading, endPoint: .trailing)
                            )
                        )
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 18)
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 10)
            } else {
                VStack(spacing: 0) {
                    ForEach(shares.entries.indices, id: \.self) { index in
                        let share = shares.entries[index]
                        if index > 0 {
                            Divider()
                                .background(Aero.outline.opacity(0.5))
                                .padding(.leading, 66)
                        }
                        sharedRow(share)
                    }
                }
                .gsSheetCard()
            }
            Spacer(minLength: 10)
        }
        .alert("Share Conversations", isPresented: $learnMoreOpen) {
            Button("Got it", role: .cancel) {}
        } message: {
            Text("Sharing hands the conversation's transcript to the app you pick — mail, messages, notes. This build keeps the record on this device only: what you shared and when. Nothing is uploaded, and removing a record here does not recall a copy someone already saved.")
        }
        .confirmationDialog("Share a Conversation", isPresented: $pickOpen, titleVisibility: .visible) {
            ForEach(activeConversations.prefix(6)) { conversation in
                Button(gsConversationTitle(conversation.title)) {
                    share(conversation)
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text(activeConversations.isEmpty
                 ? "No conversations to share yet. Start a chat first, then share it from here or from its details."
                 : "Pick the conversation to share.")
        }
        .alert("Remove shared record?", isPresented: Binding(
            get: { removeTarget != nil },
            set: { if !$0 { removeTarget = nil } }
        )) {
            Button("Remove", role: .destructive) {
                if let target = removeTarget { shares.remove(id: target.id) }
            }
            Button("Cancel", role: .cancel) { removeTarget = nil }
        } message: {
            Text("The record leaves this list. A copy someone already saved cannot be recalled.")
        }
    }

    // MARK: Pieces

    private var activeConversations: [StoredConversation] {
        conversations.conversations.filter { !$0.archived }
    }

    private var tabs: some View {
        HStack(spacing: 6) {
            sharedTab("Shared with Others", glyph: "share-2", active: tab == "others") { tab = "others" }
            sharedTab("Shared by You", glyph: "send", active: tab == "you") { tab = "you" }
        }
        .padding(6)
        .gsSheetCard()
    }

    private func sharedTab(_ label: String, glyph: String, active: Bool, onTap: @escaping () -> Void) -> some View {
        Button(action: onTap) {
            HStack(spacing: 7) {
                NexlyGlyph(name: glyph, size: 15, color: active ? Aero.refAccentStrong : Aero.textSecondary)
                Text(label)
                    .font(.custom(Aero.productName(active ? .semibold : .medium), size: 10.5, relativeTo: .caption))
                    .foregroundStyle(active ? Aero.refAccentStrong : Aero.textSecondary)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 9)
            .background(Capsule().fill(active ? AnyShapeStyle(Aero.refAccentSoft) : AnyShapeStyle(Color.clear)))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    private var shareCard: some View {
        HStack(spacing: 12) {
            NexlyGlyph(name: "users", size: 22, color: Aero.refAccent)
                .frame(width: 44, height: 44)
                .background(
                    RoundedRectangle(cornerRadius: 13, style: .continuous)
                        .fill(GsWellTone.lilac.wash)
                )
            VStack(alignment: .leading, spacing: 2) {
                Text("Share Conversations")
                    .font(.custom(Aero.productName(.semibold), size: 12, relativeTo: .body))
                    .foregroundStyle(Aero.text)
                Text("Let others view your helpful conversations with Nexly.")
                    .font(.custom(Aero.productName(.regular), size: 9.5, relativeTo: .caption))
                    .foregroundStyle(Aero.textSecondary)
            }
            Spacer(minLength: 6)
            Button {
                learnMoreOpen = true
            } label: {
                HStack(spacing: 4) {
                    Text("Learn More")
                        .font(.custom(Aero.productName(.medium), size: 10, relativeTo: .caption))
                        .foregroundStyle(Aero.refAccentStrong)
                    NexlyGlyph(name: "chevron-right", size: 12, color: Aero.refAccentStrong)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(Capsule().fill(GsWellTone.lilac.wash))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Learn more about sharing")
        }
        .padding(12)
        .gsSheetCard()
    }

    private func sharedRow(_ share: SharedConversationsStore.Share) -> some View {
        let glyph = topicGlyph(share.title)
        let tone = topicTone(share.title)
        return HStack(spacing: 12) {
            // (glyph tile below)
            NexlyGlyph(name: glyph, size: 20, color: tone.ink)
                .frame(width: 44, height: 44)
                .background(
                    RoundedRectangle(cornerRadius: 13, style: .continuous)
                        .fill(tone.wash)
                )
            VStack(alignment: .leading, spacing: 2) {
                Text(share.title)
                    .font(.custom(Aero.productName(.semibold), size: 12, relativeTo: .body))
                    .foregroundStyle(Aero.text)
                    .lineLimit(1)
                Text(share.people > 0
                     ? "Shared \(Self.relative(from: share.sharedAt))  •  With \(share.people) \(share.people == 1 ? "person" : "people")"
                     : "Shared \(Self.relative(from: share.sharedAt))")
                    .font(.custom(Aero.productName(.regular), size: 9.5, relativeTo: .caption))
                    .foregroundStyle(Aero.textSecondary)
                if share.people > 0 {
                    HStack(spacing: 3) {
                        let chipTones: [GsWellTone] = [.lilac, .peach, .mint]
                        ForEach(0..<min(share.people, 3), id: \.self) { index in
                            avatarChip(chipTones[index % chipTones.count], diameter: 18)
                        }
                        if share.people > 3 {
                            Text("+\(share.people - 3)")
                                .font(.custom(Aero.productName(.regular), size: 9, relativeTo: .caption2))
                                .foregroundStyle(Aero.textTertiary)
                        }
                    }
                    .padding(.top, 2)
                }
            }
            Spacer(minLength: 6)
            Button {
                router.path.append(.chat(share.conversationId))
            } label: {
                Text("View")
                    .font(.custom(Aero.productName(.medium), size: 10.5, relativeTo: .caption))
                    .foregroundStyle(Aero.text)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 7)
                    .background(Capsule().fill(Color.white.opacity(0.72)))
                    .overlay(Capsule().strokeBorder(Color.white.opacity(0.85), lineWidth: 1))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("View \(share.title)")
            Button {
                removeTarget = share
            } label: {
                NexlyGlyph(name: "dots-vertical", size: 16, color: Aero.textTertiary)
                    .frame(width: 30, height: 30)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("More options for \(share.title)")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .contentShape(Rectangle())
        .onTapGesture { router.path.append(.chat(share.conversationId)) }
    }

    private func share(_ conversation: StoredConversation) {
        let title = gsConversationTitle(conversation.title)
        SharedConversationsStore.shared.record(conversationId: conversation.id, title: title)
        let body = "Shared from Nexly: \"\(title)\""
        let activity = UIActivityViewController(activityItems: [body], applicationActivities: nil)
        UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow?.rootViewController }
            .first?.present(activity, animated: true)
    }

    // MARK: Topic mapping — the mock's own pairs.

    private func topicGlyph(_ title: String) -> String {
        switch DrawerTopic.topic(for: title) {
        case .planner, .learning: return "book"
        case .travel, .itinerary: return "plane"
        case .code, .review: return "code"
        default: return "sparkles"
        }
    }

    private func topicTone(_ title: String) -> GsWellTone {
        switch DrawerTopic.topic(for: title) {
        case .code, .review, .planner: return .peach
        case .learning, .data, .money: return .mint
        default: return .lilac
        }
    }

    /// The mock's overlapping people chips: pastel glass circles with the
    /// person glyph — drawn, like everything else on the page.
    private func avatarChip(_ tone: GsWellTone, diameter: CGFloat) -> some View {
        NexlyGlyph(name: "user", size: 9, color: Aero.textSecondary)
            .frame(width: diameter, height: diameter)
            .background(Circle().fill(tone.wash))
            .overlay(Circle().strokeBorder(.white, lineWidth: 1))
    }

    static func relative(from date: Date) -> String {
        let elapsed = Date().timeIntervalSince(date)
        let minutes = Int(elapsed / 60)
        if minutes < 1 { return "just now" }
        if minutes < 60 { return "\(minutes)m ago" }
        let hours = minutes / 60
        if hours < 24 { return "\(hours)h ago" }
        let days = hours / 24
        if days < 7 { return "\(days)d ago" }
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d, yyyy"
        return formatter.string(from: date)
    }
}
