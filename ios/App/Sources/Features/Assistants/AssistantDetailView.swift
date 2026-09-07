import SwiftUI

/// Assistant profile — hero, starters, capabilities, instructions, start chat.
/// User-created assistants get Edit + Delete; samples stay curated.
struct AssistantDetailView: View {

    let assistantID: String

    @EnvironmentObject private var router: Router
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var store = AssistantsStore.shared
    @State private var showDelete = false

    private let starters = [
        "Help me tighten my opening paragraph",
        "Turn these notes into a clear brief",
        "Give me three headline options for the launch"
    ]

    private var assistant: AssistantSample {
        AssistantsStore.shared.find(assistantID)
            ?? AssistantSample.catalog.first { $0.id == assistantID }
            ?? AssistantSample.catalog[0]
    }

    private var isUserAssistant: Bool {
        AssistantsStore.shared.find(assistantID) != nil
    }

    /// Store-backed favourite — survives relaunches, syncs with list badges.
    private var isFav: Bool {
        store.favourites.contains(assistantID)
    }

    private var instructionText: String {
        "Act as a seasoned \(assistant.category.lowercased()) partner. Keep replies concise, ask one clarifying question before long tasks, and always propose the next step."
    }

    init(assistantID: String) {
        self.assistantID = assistantID
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                hero
                startersSection
                capabilitiesSection
                instructionsSection
                startChatLink
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
        .toolbar {
            ToolbarItemGroup(placement: .navigationBarTrailing) {
                if isUserAssistant {
                    Button {
                        router.path.append(.assistantEdit(assistantID))
                    } label: {
                        Image(systemName: "pencil")
                            .font(.system(size: 15))
                            .foregroundStyle(Aero.text)
                    }
                    Button {
                        showDelete = true
                    } label: {
                        Image(systemName: "trash")
                            .font(.system(size: 15))
                            .foregroundStyle(Aero.text)
                    }
                } else {
                    Button {
                    } label: {
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 15))
                            .foregroundStyle(Aero.text)
                    }
                }
                Button {
                    store.toggleFavourite(assistantID)
                } label: {
                    Image(systemName: isFav ? "heart.fill" : "heart")
                        .font(.system(size: 15))
                        .foregroundStyle(isFav ? Aero.accent : Aero.text)
                }
            }
        }
        .confirmationDialog(
            "Delete “\(assistant.name)” from My assistants? Chats you started with it stay in your history.",
            isPresented: $showDelete,
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                AssistantsStore.shared.remove(assistantID)
                dismiss()
            }
            Button("Keep", role: .cancel) { }
        }
    }

    // MARK: Hero

    private var hero: some View {
        VStack(spacing: Aero.Spacing.s) {
            Image(systemName: "smarttoy")
                .font(.system(size: 26, weight: .medium))
                .foregroundStyle(Aero.accent)
                .frame(width: 64, height: 64)
                .background(Circle().fill(Aero.container))
            Text(assistant.name)
                .font(Aero.headline())
                .foregroundStyle(Aero.text)
            AeroChip(text: assistant.category)
            Text(assistant.desc)
                .font(Aero.body())
                .foregroundStyle(Aero.textMuted)
                .multilineTextAlignment(.center)
            Text("\(assistant.usesText) uses · ★ \(assistant.ratingText) · \(assistant.category)")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: Conversation starters

    private var startersSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            SectionHeader(title: "Conversation starters")
            ForEach(starters, id: \.self) { starter in
                starterRow(starter)
            }
        }
    }

    private func starterRow(_ text: String) -> some View {
        NavigationLink(value: AeroRoute.chatPrefill(text)) {
            AeroListRow(
                title: text,
                leading: {
                    Image(systemName: "bubble.left")
                        .font(.system(size: 14))
                        .foregroundStyle(Aero.accent)
                        .frame(width: 34, height: 34)
                        .background(Circle().fill(Aero.container))
                },
                trailing: {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12))
                        .foregroundStyle(Aero.textMuted)
                }
            )
        }
        .buttonStyle(KineticPressStyle())
    }

    // MARK: Capabilities

    private var capabilitiesSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            SectionHeader(title: "Capabilities")
            HStack(spacing: Aero.Spacing.s) {
                ForEach(["Web search", "Files", "Memory"], id: \.self) { capability in
                    AeroChip(text: capability)
                }
            }
        }
    }

    // MARK: Instructions

    private var instructionsSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            SectionHeader(title: "Instructions")
            AeroCard {
                Text(instructionText)
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
            }
        }
    }

    // MARK: Primary action

    private var startChatLink: some View {
        NavigationLink(value: AeroRoute.chatPrefill(starters.first ?? "")) {
            Text("Start chat")
                .font(Aero.title())
                .frame(maxWidth: .infinity)
                .padding(14)
                .background(RoundedRectangle(cornerRadius: Aero.Radius.card).fill(Aero.accent))
                .foregroundStyle(Color.white)
        }
        .buttonStyle(KineticPressStyle())
    }
}
