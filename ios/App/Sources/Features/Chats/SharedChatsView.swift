import SwiftUI
import UIKit

/// Publicly shared conversations — copy link, revoke link (local-only this
/// pass; sharing endpoints land with the backend sharing task).
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

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                Text("Shared")
                    .font(Aero.displayTitle())
                    .foregroundStyle(Aero.text)
                Text("Anyone with the link can view these conversations.")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)

                if items.isEmpty {
                    EmptyStateView(
                        icon: "link",
                        title: "No shared links",
                        message: "Share a conversation to create a public, view-only link."
                    )
                } else {
                    VStack(spacing: Aero.Spacing.s) {
                        ForEach(items) { item in
                            row(item)
                        }
                    }
                }
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
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

                    Button {
                        pendingRevoke = item
                    } label: {
                        Image(systemName: "trash")
                            .font(.system(size: 15))
                            .foregroundStyle(Color(red: 0.9, green: 0.28, blue: 0.28))
                    }
                    .buttonStyle(KineticPressStyle())
                }
            }
        )
    }

    private func copyLink(for item: SharedItem) {
        UIPasteboard.general.string = "https://gs.ai/s/\(item.id.uuidString.prefix(8).lowercased())"
        copiedID = item.id
        Task {
            try? await Task.sleep(nanoseconds: 1_200_000_000)
            if copiedID == item.id {
                copiedID = nil
            }
        }
    }
}
