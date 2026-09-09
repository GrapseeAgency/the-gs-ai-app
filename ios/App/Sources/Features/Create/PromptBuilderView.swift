import SwiftUI
import UIKit

// MARK: - Stagger entrance (private per-file helper)

/// Fades + lifts a section into place, delayed by its index —
/// the Aeruo Kinetic section entrance.
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

// MARK: - Prompt builder — role, context, goal, format, refinements

/// Full-screen prompt workspace (presented via .fullScreenCover). Owns its
/// chrome: close control, serif title. Assembles a monospaced preview from
/// the four fields; `onOpenChat` is injected by the presenter when a chat
/// hand-off exists, and the button hides when it is nil.
struct PromptBuilderView: View {

    @Environment(\.dismiss) private var dismiss

    var onOpenChat: (() -> Void)? = nil

    private let refinements = ["Be concise", "Add examples", "Cite sources", "Ask clarifying questions"]

    private let refinementColumns = [
        GridItem(.adaptive(minimum: 150), spacing: Aero.Spacing.xs)
    ]

    // MARK: State

    @State private var role = ""
    @State private var context = ""
    @State private var goal = ""
    @State private var format = ""
    @State private var activeRefinements: Set<String> = []
    @State private var toast: String?

    private var assembledPrompt: String {
        var lines: [String] = []
        if !role.isEmpty { lines.append("Role: \(role)") }
        if !context.isEmpty { lines.append("Context: \(context)") }
        if !goal.isEmpty { lines.append("Goal: \(goal)") }
        if !format.isEmpty { lines.append("Format: \(format)") }
        for item in refinements where activeRefinements.contains(item) {
            lines.append("- \(item)")
        }
        return lines.joined(separator: "\n")
    }

    // MARK: Body

    var body: some View {
        ZStack {
            Aero.background.ignoresSafeArea()
            VStack(spacing: 0) {
                header
                    .padding(.horizontal, Aero.Spacing.m)
                    .padding(.top, Aero.Spacing.s)
                    .padding(.bottom, Aero.Spacing.m)
                ScrollView {
                    VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                        StaggerIn(index: 0) { fieldsSection }
                        StaggerIn(index: 1) { previewSection }
                        StaggerIn(index: 2) { refinementSection }
                        StaggerIn(index: 3) { actionSection }
                    }
                    .padding(.horizontal, Aero.Spacing.m)
                    .padding(.bottom, Aero.Spacing.xl)
                }
            }
        }
        .toolbar(.hidden, for: .navigationBar)
        .overlay(alignment: .bottom) { toastView }
        .scrollDismissesKeyboard(.interactively)
        .onAppear { GSHaptics.prepare() }
    }

    // MARK: Header (own chrome — no router)

    private var header: some View {
        HStack(spacing: Aero.Spacing.s) {
            closeButton
            Text("Prompt builder")
                .font(Aero.headline())
                .foregroundStyle(Aero.text)
            Spacer()
        }
    }

    private var closeButton: some View {
        Button {
            dismiss()
        } label: {
            Image(systemName: "xmark")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Aero.text)
                .frame(width: 36, height: 36)
                .background(Circle().fill(Aero.raised))
                .overlay(Circle().stroke(Aero.outline, lineWidth: 1))
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityLabel("Close")
    }

    // MARK: Fields

    private var fieldsSection: some View {
        VStack(spacing: Aero.Spacing.s) {
            fieldCard("Role", "e.g. A senior research analyst…", text: $role)
            fieldCard("Context", "Background the model should assume…", text: $context)
            fieldCard("Goal", "What a great answer achieves…", text: $goal)
            fieldCard("Format", "Shape of the output…", text: $format)
        }
    }

    private func fieldCard(
        _ title: String,
        _ placeholder: String,
        text: Binding<String>
    ) -> some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                Text(title)
                    .font(Aero.title())
                    .foregroundStyle(Aero.text)
                TextField(placeholder, text: text)
                    .font(Aero.body())
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(Capsule().fill(Aero.container))
                    .overlay(Capsule().stroke(Aero.outline, lineWidth: 1))
            }
        }
    }

    // MARK: Assembled preview

    private var previewSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Assembled prompt")
            Group {
                if assembledPrompt.isEmpty {
                    Text("Fill any field and the prompt assembles here.")
                        .font(.system(size: 13, design: .monospaced))
                        .foregroundColor(Aero.textPlaceholder)
                } else {
                    Text(assembledPrompt)
                        .font(.system(size: 13, design: .monospaced))
                        .foregroundColor(Aero.text)
                }
            }
            .padding(Aero.Spacing.m)
            .frame(maxWidth: .infinity, minHeight: 120, alignment: .topLeading)
            .background(RoundedRectangle(cornerRadius: 16).fill(Aero.inputSurface))
        }
    }

    // MARK: Refinements (multi-select, appended as suffix lines)

    private var refinementSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Refinements")
            LazyVGrid(columns: refinementColumns, alignment: .leading, spacing: Aero.Spacing.xs) {
                ForEach(refinements, id: \.self) { option in
                    AeroChip(text: option, selected: activeRefinements.contains(option)) {
                        GSHaptics.select()
                        withAnimation(Aero.snappy) {
                            if activeRefinements.contains(option) {
                                activeRefinements.remove(option)
                            } else {
                                activeRefinements.insert(option)
                            }
                        }
                    }
                }
            }
        }
    }

    // MARK: Actions

    private var actionSection: some View {
        HStack(spacing: Aero.Spacing.s) {
            Button {
                copyPrompt()
            } label: {
                Label("Copy", systemImage: "doc.on.doc")
                    .font(Aero.label())
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(Capsule().fill(Aero.container))
                    .foregroundStyle(Aero.text)
            }
            .buttonStyle(KineticPressStyle())

            if onOpenChat != nil {
                Button {
                    onOpenChat?()
                } label: {
                    Label("Open in chat", systemImage: "bubble.left")
                        .font(Aero.label())
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(Capsule().fill(Aero.accent))
                        .foregroundStyle(Aero.onAccent)
                }
                .buttonStyle(KineticPressStyle())
            }
            Spacer()
        }
    }

    private func copyPrompt() {
        guard !assembledPrompt.isEmpty else {
            showToast("Nothing to copy yet")
            return
        }
        UIPasteboard.general.string = assembledPrompt
        GSHaptics.success()
        showToast("Prompt copied")
    }

    // MARK: Toast

    private var toastView: some View {
        Group {
            if let message = toast {
                Text(message)
                    .font(Aero.label())
                    .foregroundStyle(Aero.text)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Capsule().fill(Aero.raised))
                    .overlay(Capsule().stroke(Aero.outline, lineWidth: 1))
                    .aeroCardShadow()
                    .padding(.bottom, Aero.Spacing.l)
            }
        }
    }

    private func showToast(_ message: String) {
        withAnimation(Aero.motion(Aero.snappy)) { toast = message }
        Task {
            try? await Task.sleep(nanoseconds: 1_800_000_000)
            if toast == message {
                withAnimation(Aero.motion(Aero.snappy)) { toast = nil }
            }
        }
    }
}
