import SwiftUI

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

// MARK: - Image studio — prompt, style, four variations

/// Full-screen image-generation workspace (presented via .fullScreenCover).
/// Owns its chrome: close control, serif title. Generation is a local 1.6s
/// moment with the sanctioned aurora progress bar.
struct ImageStudioView: View {

    @Environment(\.dismiss) private var dismiss

    // MARK: Sample data

    private let styles = ["Editorial", "Cinematic", "Minimal", "Bold", "Watercolour"]
    private let aspects = ["1:1", "3:2", "16:9", "9:16"]
    private let tileOpacities: [Double] = [0.06, 0.10, 0.14, 0.18]

    private let styleColumns = [
        GridItem(.adaptive(minimum: 110), spacing: Aero.Spacing.xs)
    ]

    private let tileColumns = [
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible(), spacing: 12)
    ]

    // MARK: State

    @State private var prompt = ""
    @State private var style = "Editorial"
    @State private var aspect = "1:1"
    @State private var isGenerating = false
    @State private var progress: CGFloat = 0
    @State private var hasResults = false
    @State private var toast: String?

    private var trimmedPrompt: String {
        prompt.trimmingCharacters(in: .whitespacesAndNewlines)
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
                        StaggerIn(index: 0) { promptSection }
                        StaggerIn(index: 1) { styleSection }
                        StaggerIn(index: 2) { aspectSection }
                        StaggerIn(index: 3) { generateSection }
                        if isGenerating {
                            StaggerIn(index: 4) { progressSection }
                        }
                        if hasResults {
                            StaggerIn(index: 4) { resultsSection }
                            StaggerIn(index: 5) { regenerateSection }
                        }
                    }
                    .padding(.horizontal, Aero.Spacing.m)
                    .padding(.bottom, Aero.Spacing.xl)
                }
            }
        }
        .toolbar(.hidden, for: .navigationBar)
        .overlay(alignment: .bottom) { toastView }
    }

    // MARK: Header (own chrome — no router)

    private var header: some View {
        HStack(spacing: Aero.Spacing.s) {
            closeButton
            Text("Image studio")
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
    }

    // MARK: Prompt

    private var promptSection: some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                SectionHeader(title: "Prompt")
                editorContainer(
                    "Describe the image — subject, mood, light…",
                    text: $prompt,
                    height: 80
                )
            }
        }
    }

    // MARK: Style + aspect

    private var styleSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Style")
            LazyVGrid(columns: styleColumns, alignment: .leading, spacing: Aero.Spacing.xs) {
                ForEach(styles, id: \.self) { option in
                    AeroChip(text: option, selected: style == option) {
                        style = option
                    }
                }
            }
        }
    }

    private var aspectSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Aspect")
            HStack(spacing: Aero.Spacing.s) {
                ForEach(aspects, id: \.self) { option in
                    AeroChip(text: option, selected: aspect == option) {
                        aspect = option
                    }
                }
                Spacer()
            }
        }
    }

    // MARK: Generate + progress

    private var generateSection: some View {
        Button {
            startGeneration()
        } label: {
            Text("Generate")
                .font(Aero.title())
                .frame(maxWidth: .infinity)
                .padding(14)
                .background(RoundedRectangle(cornerRadius: Aero.Radius.card).fill(Aero.accent))
                .foregroundStyle(Color.white)
        }
        .buttonStyle(KineticPressStyle())
        .disabled(trimmedPrompt.isEmpty || isGenerating)
        .opacity(trimmedPrompt.isEmpty || isGenerating ? 0.4 : 1)
    }

    private var progressSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule().fill(Aero.container)
                    Capsule()
                        .fill(LinearGradient(colors: Aero.aurora, startPoint: .leading, endPoint: .trailing))
                        .frame(width: max(8, proxy.size.width * progress))
                }
            }
            .frame(height: 8)
            Text("Dreaming up 4 variations…")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
        }
    }

    private func startGeneration() {
        guard !trimmedPrompt.isEmpty, !isGenerating else { return }
        isGenerating = true
        hasResults = false
        progress = 0
        withAnimation(.linear(duration: 1.6)) { progress = 1 }
        Task {
            try? await Task.sleep(nanoseconds: 1_600_000_000)
            withAnimation(Aero.spring) {
                isGenerating = false
                hasResults = true
            }
        }
    }

    // MARK: Results

    private var resultsSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Variations")
            LazyVGrid(columns: tileColumns, spacing: 12) {
                ForEach(tileOpacities.indices, id: \.self) { index in
                    variationTile(opacity: tileOpacities[index])
                }
            }
        }
    }

    private func variationTile(opacity: Double) -> some View {
        RoundedRectangle(cornerRadius: 16)
            .fill(Aero.container.opacity(opacity))
            .overlay(
                RoundedRectangle(cornerRadius: 16).stroke(Aero.outline, lineWidth: 1)
            )
            .overlay(
                Image(systemName: "photo")
                    .font(.system(size: 22))
                    .foregroundStyle(Aero.textMuted)
            )
            .overlay(alignment: .topTrailing) {
                Button {
                    // Real save: the variation's prompt lands in the Library
                    // under the Images kind — filters catch it.
                    ConversationStore.shared.saveToLibrary(content: trimmedPrompt, kind: "image")
                    showToast("Saved to Library")
                } label: {
                    Image(systemName: "arrow.down.circle")
                        .font(.system(size: 16))
                        .foregroundStyle(Aero.text)
                        .padding(6)
                        .background(Circle().fill(Aero.surface))
                        .overlay(Circle().stroke(Aero.outline, lineWidth: 1))
                }
                .buttonStyle(KineticPressStyle())
                .padding(8)
            }
            .aspectRatio(1, contentMode: .fit)
    }

    private var regenerateSection: some View {
        HStack(spacing: Aero.Spacing.s) {
            AeroChip(text: "Regenerate") {
                startGeneration()
            }
            Spacer()
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
        withAnimation(Aero.snappy) { toast = message }
        Task {
            try? await Task.sleep(nanoseconds: 1_800_000_000)
            if toast == message {
                withAnimation(Aero.snappy) { toast = nil }
            }
        }
    }
}
