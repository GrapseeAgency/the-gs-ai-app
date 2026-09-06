import SwiftUI

/// Folder grid — client-side grouping of chats. Tapping a folder deep-dives
/// into chat search (placeholder until folder filtering ships); "New folder"
/// shows a friendly alert (noted in worklog, Task 6-e).
struct FoldersView: View {

    private struct FolderItem: Identifiable {
        let id = UUID()
        let name: String
        let count: Int
        let icon: String
    }

    @State private var folders: [FolderItem] = [
        FolderItem(name: "Work", count: 12, icon: "doc.text"),
        FolderItem(name: "Learning", count: 7, icon: "book"),
        FolderItem(name: "Client drafts", count: 3, icon: "folder")
    ]
    @State private var showingNewFolderNote = false

    private let columns = [
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible(), spacing: 12)
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                Text("Folders")
                    .font(Aero.displayTitle())
                    .foregroundStyle(Aero.text)
                Text("Group related chats so the right context is one tap away.")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)

                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(folders) { folder in
                        NavigationLink(value: AeroRoute.chatSearch) {
                            folderCard(folder)
                        }
                        .buttonStyle(KineticPressStyle())
                    }
                    newFolderCard
                }
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
        .alert("Folders", isPresented: $showingNewFolderNote) {
            Button("Got it", role: .cancel) {}
        } message: {
            Text("Folders are coming alive next build — create, pin and sort chats your way.")
        }
    }

    // MARK: Cards

    private func folderCard(_ folder: FolderItem) -> some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            Image(systemName: folder.icon)
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(Aero.accent)
                .frame(width: 40, height: 40)
                .background(RoundedRectangle(cornerRadius: 12).fill(Aero.container))
            Text(folder.name)
                .font(Aero.title())
                .foregroundStyle(Aero.text)
                .lineLimit(1)
            Text("\(folder.count) chats")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Aero.Spacing.m)
        .background(RoundedRectangle(cornerRadius: Aero.Radius.card).fill(Aero.surface))
        .overlay(RoundedRectangle(cornerRadius: Aero.Radius.card).stroke(Aero.outline, lineWidth: 1))
        .aeroCardShadow()
    }

    private var newFolderCard: some View {
        Button {
            showingNewFolderNote = true
        } label: {
            VStack(spacing: Aero.Spacing.s) {
                Image(systemName: "plus")
                    .font(.system(size: 20))
                    .foregroundStyle(Aero.textMuted)
                Text("New folder")
                    .font(Aero.label())
                    .foregroundStyle(Aero.textMuted)
            }
            .frame(maxWidth: .infinity, minHeight: 118)
            .background(
                RoundedRectangle(cornerRadius: Aero.Radius.card)
                    .fill(Aero.container.opacity(0.4))
            )
            .overlay(
                RoundedRectangle(cornerRadius: Aero.Radius.card)
                    .stroke(Aero.outline, style: StrokeStyle(lineWidth: 1, dash: [6, 4]))
            )
        }
        .buttonStyle(KineticPressStyle())
    }
}
