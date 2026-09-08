import SwiftUI
import UIKit

/// Publicly shared conversations — copy link, revoke link (native swipe
/// action per Task 86-e; local-only this pass; sharing endpoints land with
/// the backend sharing task).
struct SharedChatsView: View {

    private struct SharedItem: Identifiable {
        let id = UUID()
        let title: String
        let views: Int
    }

    @State private var items: [SharedItem] = [
        SharedItem(title: "Q3 pricing strategy", views: 14),
        SharedItem(title: "Brand voice workshop", views: 6),
        SharedItem(title: "Research: edge AI chips", views: 21)
    ]
    @State private var pendingRevoke: SharedItem?
    @State private var copiedID: UUID?

    /// Card-row insets (Task 86-e) — same shape as ChatsListView's.
    private var rowInsets: EdgeInsets {
        EdgeInsets(top: 4, leading: Aero.Spacing.m, bottom: 4, trailing: Aero.Spacing.m)
    }

    var body: some View {
        List {
            Text("Shared")
                .font(Aero.displayTitle())
                .foregroundStyle(Aero.text)
                .accessibilityAddTraits(.isHeader)
                .gsListRowChrome(EdgeInsets(
                    top: Aero.Spacing.s, leading: Aero.Spacing.m, bottom: 0, trailing: Aero.Spacing.m))
            Text("Anyone with the link can view these conversations.")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
                .gsListRowChrome(EdgeInsets(
                    top: Aero.Spacing.l, leading: Aero.Spacing.m, bottom: Aero.Spacing.l - 4, trailing: Aero.Spacing.m))

            if items.isEmpty {
                EmptyStateView(
                    icon: "link",
                    title: "No shared links",
                    message: "Share a conversation to create a public, view-only link."
                )
                .gsListRowChrome(rowInsets)
            } else {
                ForEach(items) { item in
                    row(item)
                }
            }

            // Tail spacer reproduces the old LazyVStack bottom padding.
            Color.clear
                .frame(height: Aero.Spacing.xl - 4)
                .gsListRowChrome(EdgeInsets())
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .environment(\.defaultMinListRowHeight, 1)
        .background(Aero.background.ignoresSafeArea())
        .confirmationDialog(
            "Revoke link?",
            isPresented: Binding(
                get: { pendingRevoke != nil },
                set: { if !$0 { pendingRevoke = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Revoke link", role: .destructive) {
                if let item = pendingRevoke {
                    withAnimation(Aero.snappy) {
                        items.removeAll { $0.id == item.id }
                    }
                }
                pendingRevoke = nil
            }
            Button("Cancel", role: .cancel) {
                pendingRevoke = nil
            }
        } message: {
            Text("People with the link will lose access to “\(pendingRevoke?.title ?? "this chat")”.")
        }
    }

    // MARK: Row

    private func row(_ item: SharedItem) -> some View {
        AeroListRow(
            title: item.title,
            subtitle: "Anyone with the link · views \(item.views)",
            leading: {
                Image(systemName: "person.2")
                    .font(.system(size: 14))
                    .foregroundStyle(Aero.text)
                    .frame(width: 36, height: 36)
                    .background(Circle().fill(Aero.containerHigh))
            },
            trailing: {
                HStack(spacing: 14) {
                    Button {
                        copyLink(for: item)
                    } label: {
                        Image(systemName: copiedID == item.id ? "checkmark" : "link")
                            .font(.system(size: 15))
                            .foregroundStyle(copiedID == item.id ? Aero.accent : Aero.textMuted)
                    }
                    .buttonStyle(KineticPressStyle())
                    .accessibilityLabel(copiedID == item.id ? "Link copied" : "Copy link")

                    Button {
                        requestRevoke(item)
                    } label: {
                        Image(systemName: "trash")
                            .font(.system(size: 15))
                            .foregroundStyle(Color(red: 0.9, green: 0.28, blue: 0.28))
                    }
                    .buttonStyle(KineticPressStyle())
                    .accessibilityLabel("Revoke link")
                }
            }
        )
        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
            Button(role: .destructive) {
                requestRevoke(item)
            } label: {
                Label("Revoke", systemImage: "trash")
            }
        }
        .gsListRowChrome(rowInsets)
    }

    /// Shared by the inline trash button and the trailing swipe — both raise
    /// the same confirming dialog; nothing is revoked without it.
    private func requestRevoke(_ item: SharedItem) {
        pendingRevoke = item
    }

    private func copyLink(for item: SharedItem) {
        UIPasteboard.general.string = "https://gs.ai/s/\(item.id.uuidString.prefix(8).lowercased())"
        // Copy completed — the success tick matches the chat-surface copy idiom.
        GSHaptics.success()
        copiedID = item.id
        Task {
            try? await Task.sleep(nanoseconds: 1_200_000_000)
            if copiedID == item.id {
                copiedID = nil
            }
        }
    }
}
