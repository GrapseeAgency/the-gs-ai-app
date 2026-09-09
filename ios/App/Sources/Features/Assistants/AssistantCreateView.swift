import SwiftUI

/// Assistant builder — identity, behaviour, starters, capabilities, visibility.
/// Create mode starts blank; edit mode (editID) prefills from the local store.
struct AssistantCreateView: View {

    @Environment(\.dismiss) private var dismiss

    private let editID: String?
    private let existing: AssistantSample?

    // Identity
    @State private var name = ""
    @State private var desc = ""

    // Behaviour
    @State private var instructions = ""
    @State private var category = "Writing"

    // Starters
    @State private var starters: [String] = ["", "", ""]

    // Capabilities + visibility
    @State private var capabilities: Set<String> = []
    @State private var visibility = "Private"

    // Created confirmation
    @State private var showCreated = false

    init(editID: String? = nil) {
        self.editID = editID
        let resolved = editID.flatMap { AssistantsStore.shared.find($0) }
        self.existing = resolved
        _name = State(initialValue: resolved?.name ?? "")
        _desc = State(initialValue: resolved?.desc ?? "")
        _instructions = State(initialValue: resolved?.instructions ?? "")
        _category = State(initialValue: resolved?.category ?? "Writing")
        _starters = State(initialValue: resolved?.starters.isEmpty == true
            ? ["", "", ""]
            : (resolved?.starters ?? ["", "", ""]))
        _capabilities = State(initialValue: Set(resolved?.capabilities ?? []))
        _visibility = State(initialValue: (resolved?.published ?? false) ? "Published" : "Private")
    }

    private let categories = [
        "Writing", "Coding", "Research", "Business",
        "Education", "Design", "Data", "Language"
    ]

    private let capabilityOptions = ["Web search", "Code", "Vision", "Files", "Memory"]

    private let chipColumns = [
        GridItem(.adaptive(minimum: 84), spacing: Aero.Spacing.xs)
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.m) {
                identitySection
                behaviourSection
                startersSection
                capabilitiesSection
                visibilitySection
                createButton
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
        .scrollDismissesKeyboard(.interactively)
        .onAppear { GSHaptics.prepare() }
    }

    // MARK: Identity

    private var identitySection: some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s + 2) {
                SectionHeader(title: "Identity")
                TextField("Name", text: $name)
                    .font(Aero.body())
                    .textInputAutocapitalization(.words)
                    .autocorrectionDisabled()
                    .textContentType(.name)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(Capsule().fill(Aero.container))
                    .overlay(Capsule().stroke(Aero.outline, lineWidth: 1))
                editorContainer(
                    "Describe what this assistant does…",
                    text: $desc,
                    height: 80
                )
            }
        }
    }

    // MARK: Behaviour

    private var behaviourSection: some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s + 2) {
                SectionHeader(title: "Behaviour")
                editorContainer(
                    "How should it think, talk and behave?",
                    text: $instructions,
                    height: 140
                )
                LazyVGrid(columns: chipColumns, alignment: .leading, spacing: Aero.Spacing.xs) {
                    ForEach(categories, id: \.self) { option in
                        AeroChip(text: option, selected: category == option) {
                            GSHaptics.select()
                            withAnimation(Aero.snappy) { category = option }
                        }
                    }
                }
            }
        }
    }

    // MARK: Starters

    private var startersSection: some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s + 2) {
                SectionHeader(title: "Starters")
                ForEach(0..<3, id: \.self) { index in
                    TextField("Starter \(index + 1)", text: $starters[index])
                        .font(Aero.body())
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                        .background(Capsule().fill(Aero.container))
                        .overlay(Capsule().stroke(Aero.outline, lineWidth: 1))
                }
            }
        }
    }

    // MARK: Capabilities

    private var capabilitiesSection: some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s + 2) {
                SectionHeader(title: "Capabilities")
                LazyVGrid(columns: chipColumns, alignment: .leading, spacing: Aero.Spacing.xs) {
                    ForEach(capabilityOptions, id: \.self) { option in
                        AeroChip(text: option, selected: capabilities.contains(option)) {
                            GSHaptics.select()
                            withAnimation(Aero.snappy) {
                                if capabilities.contains(option) {
                                    capabilities.remove(option)
                                } else {
                                    capabilities.insert(option)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // MARK: Visibility

    private var visibilitySection: some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s + 2) {
                SectionHeader(title: "Visibility")
                HStack(spacing: Aero.Spacing.s) {
                    ForEach(["Private", "Published"], id: \.self) { option in
                        AeroChip(text: option, selected: visibility == option) {
                            GSHaptics.select()
                            withAnimation(Aero.snappy) { visibility = option }
                        }
                    }
                    Spacer()
                }
            }
        }
    }

    // MARK: Create

    private var createButton: some View {
        Button {
            let clean = { (s: String) in s.trimmingCharacters(in: .whitespacesAndNewlines) }
            let savedStarters = starters.map(clean).filter { !$0.isEmpty }
            let saved = AssistantSample(
                id: editID ?? "asst-u-\(Int(Date().timeIntervalSince1970 * 1000))",
                name: clean(name),
                category: category,
                desc: clean(desc),
                uses: existing?.uses ?? 1,
                rating: existing?.rating ?? 5.0,
                isFav: existing?.isFav ?? false,
                published: visibility == "Published",
                instructions: clean(instructions),
                starters: savedStarters,
                capabilities: capabilities.sorted()
            )
            AssistantsStore.shared.upsert(saved)
            GSHaptics.success()
            showCreated = true
        } label: {
            Text(editID == nil ? "Create assistant" : "Save changes")
                .font(Aero.title())
                .frame(maxWidth: .infinity)
                .padding(14)
                .background(RoundedRectangle(cornerRadius: Aero.Radius.card).fill(Aero.accent))
                .foregroundStyle(Color.white)
        }
        .buttonStyle(KineticPressStyle())
        .disabled(name.isEmpty)
        .opacity(name.isEmpty ? 0.4 : 1)
        .confirmationDialog(
            editID == nil
                ? "Assistant created — it now lives in My assistants."
                : "Assistant saved — it lives in My assistants.",
            isPresented: $showCreated,
            titleVisibility: .visible
        ) {
            Button("OK") { dismiss() }
        }
    }

    // MARK: Text editor container (iOS 16 TextEditor has no placeholder)

    private func editorContainer(
        _ placeholder: String,
        text: Binding<String>,
        height: CGFloat
    ) -> some View {
        ZStack(alignment: .topLeading) {
            if text.wrappedValue.isEmpty {
                Text(placeholder)
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
                    .padding(.top, 10)
                    .padding(.leading, 6)
                    .allowsHitTesting(false)
            }
            TextEditor(text: text)
                .font(Aero.caption())
                .foregroundStyle(Aero.text)
                .frame(height: height)
                .scrollContentBackground(.hidden)
                .background(Color.clear)
                // Fixed-height editor has no scroll-to-dismiss — the shared
                // Done bar puts the keyboard away (Task 85-e I6).
                .gsKeyboardDoneBar()
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 4)
        .frame(minHeight: height + 8)
        .background(RoundedRectangle(cornerRadius: 14).fill(Aero.container))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Aero.outline, lineWidth: 1))
    }
}
