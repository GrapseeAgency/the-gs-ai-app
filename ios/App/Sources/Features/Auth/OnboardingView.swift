import SwiftUI

/// Onboarding — 6-step first-run wizard: interests, AI preferences,
/// capabilities, notifications, personalisation, ready.
/// Standalone chrome (custom back circle); the host wires presentation.
struct OnboardingView: View {
    let onComplete: () -> Void

    private enum OnboardingStep: Int, CaseIterable {
        case interests, aiPreferences, capabilities, notifications, personalisation, ready
    }

    // MARK: State

    @State private var step: OnboardingStep = .interests

    // Interests (multi-select, >= 1 to continue)
    @State private var selectedInterests: Set<String> = []
    private let interests = [
        "Writing", "Coding", "Research", "Business", "Design", "Education",
        "Science", "Language", "Productivity", "Entertainment", "Marketing", "Data"
    ]

    // AI preferences (sensible defaults pre-selected)
    @State private var responseStyle = "Balanced"
    @State private var reasoningEffort = "Medium"
    @State private var personality = "Professional"

    // Capabilities (multi-select, >= 1 to continue)
    @State private var selectedCapabilities: Set<String> = []

    // Notifications (defaults per spec)
    @State private var notifyTasks = true
    @State private var notifyFiles = true
    @State private var notifyAssistants = false
    @State private var notifyNews = false

    // Personalisation
    @State private var displayName = ""
    @State private var accentChoice = 0

    // Ready progress — the one aurora moment
    @State private var readyProgress: CGFloat = 0
    @State private var readyAnimated = false

    private struct CapabilityOption {
        let title: String
        let caption: String
        let icon: String
    }

    private let capabilityOptions: [CapabilityOption] = [
        CapabilityOption(title: "Draft and write", caption: "Emails, posts, docs and scripts", icon: "pencil.line"),
        CapabilityOption(title: "Research the web", caption: "Live sources with citations", icon: "magnifyingglass"),
        CapabilityOption(title: "Analyse images", caption: "Charts, screenshots and scans", icon: "eye"),
        CapabilityOption(title: "Write and fix code", caption: "Debug, explain and refactor", icon: "curlybraces"),
        CapabilityOption(title: "Summarise documents", caption: "Long files into key points", icon: "doc.text"),
        CapabilityOption(title: "Brainstorm ideas", caption: "Names, angles and wild cards", icon: "lightbulb")
    ]

    private let chipColumns = [GridItem(.adaptive(minimum: 104), spacing: Aero.Spacing.xs)]

    private let totalSteps = 6

    var body: some View {
        VStack(spacing: 0) {
            header

            ScrollView {
                VStack(alignment: .leading, spacing: Aero.Spacing.m) {
                    stepContent
                }
                .padding(.horizontal, Aero.Spacing.m)
                .padding(.top, Aero.Spacing.m)
                .padding(.bottom, Aero.Spacing.l)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .scrollDismissesKeyboard(.interactively)

            if step != .ready {
                continueBar
            }
        }
        .background(Aero.background.ignoresSafeArea())
    }

    // MARK: Header (progress + back)

    private var header: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            HStack {
                if step.rawValue > 0 {
                    Button {
                        guard let previous = OnboardingStep(rawValue: step.rawValue - 1) else { return }
                        withAnimation(Aero.spring) { step = previous }
                    } label: {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(Aero.text)
                            .frame(width: 36, height: 36)
                            .background(Circle().fill(Aero.container))
                    }
                    .buttonStyle(KineticPressStyle())
                }
                Spacer()
            }
            ProgressView(value: Double(step.rawValue + 1) / Double(totalSteps))
                .tint(Aero.accent)
            Text("Step \(step.rawValue + 1) of \(totalSteps)")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
        }
        .padding(.horizontal, Aero.Spacing.m)
        .padding(.top, Aero.Spacing.s)
    }

    // MARK: Steps

    @ViewBuilder
    private var stepContent: some View {
        switch step {
        case .interests: interestsStep
        case .aiPreferences: aiPreferencesStep
        case .capabilities: capabilitiesStep
        case .notifications: notificationsStep
        case .personalisation: personalisationStep
        case .ready: readyStep
        }
    }

    // Step 1 — interests

    private var interestsStep: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            StaggerIn(index: 0) {
                VStack(alignment: .leading, spacing: Aero.Spacing.xs) {
                    Text("What are you into?")
                        .font(Aero.headline())
                        .foregroundStyle(Aero.text)
                    Text("Pick a few — GS tunes itself to your world.")
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                }
            }
            StaggerIn(index: 1) {
                LazyVGrid(columns: chipColumns, alignment: .leading, spacing: Aero.Spacing.xs) {
                    ForEach(interests, id: \.self) { interest in
                        AeroChip(text: interest, selected: selectedInterests.contains(interest)) {
                            if selectedInterests.contains(interest) {
                                selectedInterests.remove(interest)
                            } else {
                                selectedInterests.insert(interest)
                            }
                        }
                    }
                }
            }
        }
    }

    // Step 2 — AI preferences

    private var aiPreferencesStep: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            StaggerIn(index: 0) {
                VStack(alignment: .leading, spacing: Aero.Spacing.xs) {
                    Text("Tune your AI")
                        .font(Aero.headline())
                        .foregroundStyle(Aero.text)
                    Text("Change any of this later in Settings.")
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                }
            }
            StaggerIn(index: 1) {
                AeroCard {
                    VStack(alignment: .leading, spacing: Aero.Spacing.s + 4) {
                        SectionHeader(title: "Response style")
                        chipRow(["Concise", "Balanced", "Detailed"], selection: $responseStyle)
                    }
                }
            }
            StaggerIn(index: 2) {
                AeroCard {
                    VStack(alignment: .leading, spacing: Aero.Spacing.s + 4) {
                        SectionHeader(title: "Reasoning effort")
                        chipRow(["Low", "Medium", "High"], selection: $reasoningEffort)
                    }
                }
            }
            StaggerIn(index: 3) {
                AeroCard {
                    VStack(alignment: .leading, spacing: Aero.Spacing.s + 4) {
                        SectionHeader(title: "Personality")
                        chipRow(["Professional", "Friendly", "Playful"], selection: $personality)
                    }
                }
            }
        }
    }

    private func chipRow(_ options: [String], selection: Binding<String>) -> some View {
        HStack(spacing: Aero.Spacing.s) {
            ForEach(options, id: \.self) { option in
                AeroChip(text: option, selected: selection.wrappedValue == option) {
                    selection.wrappedValue = option
                }
            }
            Spacer()
        }
    }

    // Step 3 — capabilities

    private var capabilitiesStep: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            StaggerIn(index: 0) {
                VStack(alignment: .leading, spacing: Aero.Spacing.xs) {
                    Text("What should GS do first?")
                        .font(Aero.headline())
                        .foregroundStyle(Aero.text)
                    Text("Choose at least one starting skill.")
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                }
            }
            StaggerIn(index: 1) {
                VStack(spacing: Aero.Spacing.s) {
                    ForEach(capabilityOptions, id: \.title) { option in
                        capabilityRow(option)
                    }
                }
            }
        }
    }

    private func capabilityRow(_ option: CapabilityOption) -> some View {
        let selected = selectedCapabilities.contains(option.title)
        return Button {
            withAnimation(Aero.snappy) {
                if selected {
                    selectedCapabilities.remove(option.title)
                } else {
                    selectedCapabilities.insert(option.title)
                }
            }
        } label: {
            HStack(spacing: Aero.Spacing.s + 4) {
                Image(systemName: option.icon)
                    .font(.system(size: 14))
                    .foregroundStyle(selected ? Aero.accent : Aero.textMuted)
                    .frame(width: 34, height: 34)
                    .background(Circle().fill(Aero.container))
                VStack(alignment: .leading, spacing: 2) {
                    Text(option.title)
                        .font(Aero.title())
                        .foregroundStyle(Aero.text)
                        .lineLimit(1)
                    Text(option.caption)
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                        .lineLimit(1)
                }
                Spacer()
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 18))
                    .foregroundStyle(selected ? Aero.accent : Aero.outline)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(RoundedRectangle(cornerRadius: 14).fill(Aero.container))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(Aero.accent, lineWidth: 1)
                    .opacity(selected ? 1 : 0)
            )
        }
        .buttonStyle(KineticPressStyle())
    }

    // Step 4 — notifications

    private var notificationsStep: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            StaggerIn(index: 0) {
                VStack(alignment: .leading, spacing: Aero.Spacing.xs) {
                    Text("Stay in the loop")
                        .font(Aero.headline())
                        .foregroundStyle(Aero.text)
                    Text("Quiet by default — only what you ask for.")
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                }
            }
            StaggerIn(index: 1) {
                AeroCard {
                    VStack(alignment: .leading, spacing: Aero.Spacing.s + 4) {
                        SectionHeader(title: "Notifications")
                        toggleRow("Task completions", isOn: $notifyTasks)
                        toggleRow("File processed", isOn: $notifyFiles)
                        toggleRow("Assistant updates", isOn: $notifyAssistants)
                        toggleRow("Product news", isOn: $notifyNews)
                    }
                }
            }
        }
    }

    private func toggleRow(_ title: String, isOn: Binding<Bool>) -> some View {
        Toggle(title, isOn: isOn)
            .font(Aero.body())
            .foregroundStyle(Aero.text)
            .tint(Aero.accent)
    }

    // Step 5 — personalisation

    private var personalisationStep: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            StaggerIn(index: 0) {
                VStack(alignment: .leading, spacing: Aero.Spacing.xs) {
                    Text("Make it yours")
                        .font(Aero.headline())
                        .foregroundStyle(Aero.text)
                    Text("A name and a colour — that's all it takes.")
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                }
            }
            StaggerIn(index: 1) {
                AeroCard {
                    VStack(alignment: .leading, spacing: Aero.Spacing.s + 4) {
                        SectionHeader(title: "Your name")
                        TextField("What should GS call you?", text: $displayName)
                            .autocorrectionDisabled()
                            .font(Aero.body())
                            .foregroundStyle(Aero.text)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 12)
                            .background(Capsule().fill(Aero.container))
                            .overlay(Capsule().stroke(Aero.outline, lineWidth: 1))
                    }
                }
            }
            StaggerIn(index: 2) {
                AeroCard {
                    VStack(alignment: .leading, spacing: Aero.Spacing.s + 4) {
                        SectionHeader(title: "Accent")
                        HStack(spacing: Aero.Spacing.m) {
                            accentCircle(index: 0, color: Aero.accent, checkColor: .white)
                            accentCircle(index: 1, color: Aero.text, checkColor: Aero.background)
                            accentCircle(index: 2, color: Aero.accentDeep, checkColor: .white)
                            Spacer()
                        }
                    }
                }
            }
        }
    }

    private func accentCircle(index: Int, color: Color, checkColor: Color) -> some View {
        Button {
            withAnimation(Aero.snappy) { accentChoice = index }
        } label: {
            ZStack {
                Circle().fill(color)
                if accentChoice == index {
                    Image(systemName: "checkmark")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(checkColor)
                }
            }
            .frame(width: 44, height: 44)
            .overlay(
                Circle().stroke(Aero.outline, lineWidth: accentChoice == index ? 2 : 1)
            )
        }
        .buttonStyle(KineticPressStyle())
    }

    // Step 6 — ready (the one aurora moment)

    private var readyStep: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            StaggerIn(index: 0) {
                VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                    GeometryReader { proxy in
                        ZStack(alignment: .leading) {
                            Capsule().fill(Aero.container)
                            Capsule()
                                .fill(LinearGradient(colors: Aero.aurora, startPoint: .leading, endPoint: .trailing))
                                .frame(width: proxy.size.width * readyProgress)
                        }
                    }
                    .frame(height: 8)
                    Text("Your command centre is ready.")
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                }
                .onAppear { animateReady() }
            }
            StaggerIn(index: 1) {
                Text("You're all set, \(readyName).")
                    .font(Aero.headline())
                    .foregroundStyle(Aero.text)
            }
            StaggerIn(index: 2) {
                Button {
                    onComplete()
                } label: {
                    Text("Enter GS AI")
                        .font(Aero.title())
                        .frame(maxWidth: .infinity)
                        .padding(14)
                        .background(RoundedRectangle(cornerRadius: Aero.Radius.card).fill(Aero.accent))
                        .foregroundStyle(Color.white)
                }
                .buttonStyle(KineticPressStyle())
            }
        }
    }

    private var readyName: String {
        let trimmed = displayName.trimmingCharacters(in: .whitespaces)
        return trimmed.isEmpty ? "friend" : trimmed
    }

    private func animateReady() {
        guard !readyAnimated else { return }
        readyAnimated = true
        withAnimation(.linear(duration: 1.2)) {
            readyProgress = 1
        }
    }

    // MARK: Bottom bar

    private var continueDisabled: Bool {
        switch step {
        case .interests: return selectedInterests.isEmpty
        case .capabilities: return selectedCapabilities.isEmpty
        default: return false
        }
    }

    private var continueBar: some View {
        Button {
            guard let next = OnboardingStep(rawValue: step.rawValue + 1) else {
                onComplete()
                return
            }
            withAnimation(Aero.spring) { step = next }
        } label: {
            Text("Continue")
                .font(Aero.title())
                .frame(maxWidth: .infinity)
                .padding(14)
                .background(RoundedRectangle(cornerRadius: Aero.Radius.card).fill(Aero.accent))
                .foregroundStyle(Color.white)
        }
        .buttonStyle(KineticPressStyle())
        .disabled(continueDisabled)
        .opacity(continueDisabled ? 0.4 : 1)
        .padding(.horizontal, Aero.Spacing.m)
        .padding(.vertical, Aero.Spacing.s)
    }
}

// MARK: - Staggered entrance (private per-file helper)

private struct StaggerIn<Content: View>: View {
    let index: Int
    @ViewBuilder var content: () -> Content

    @State private var appeared = false

    var body: some View {
        content()
            .opacity(appeared ? 1 : 0)
            .offset(y: appeared ? 0 : 16)
            .onAppear {
                withAnimation(Aero.spring.delay(Aero.stagger(index))) {
                    appeared = true
                }
            }
    }
}
