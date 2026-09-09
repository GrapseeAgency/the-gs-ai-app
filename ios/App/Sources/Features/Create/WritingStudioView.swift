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

// MARK: - Writing studio — brief, outline, editable draft

/// Full-screen writing workspace (presented via .fullScreenCover). Owns its
/// chrome: close control, serif title. Outlines and drafts are canned per
/// writing type; copy/share/save are local actions.
struct WritingStudioView: View {

    @Environment(\.dismiss) private var dismiss

    // MARK: Sample data

    private let types = ["Blog post", "Essay", "Email", "Script"]
    private let tones = ["Warm", "Sharp", "Neutral", "Playful"]

    private let outlines: [String: [String]] = [
        "Blog post": [
            "Hook — the reader's Monday-morning problem",
            "Why the old playbook stopped working",
            "Three moves that work in 2025",
            "A worked example from our own stack",
            "Close — one next action"
        ],
        "Essay": [
            "Thesis — state the claim plainly",
            "Evidence — two studies, one counterexample",
            "Counterargument — steel-man the opposition",
            "Synthesis — where the truth likely sits",
            "Conclusion — what changes if we agree"
        ],
        "Email": [
            "Subject — specific and short",
            "One line of context the reader already knows",
            "The ask, stated in the first third",
            "Two bullets of supporting detail",
            "Sign-off with a clear deadline"
        ],
        "Script": [
            "Cold open — one line of tension",
            "Set-up — who wants what, and why now",
            "Beat — the plan meets reality",
            "Turn — the choice that costs something",
            "Button — final line, echo the open"
        ]
    ]

    private let drafts: [String: String] = [
        "Blog post": "Every team I know is quietly rebuilding the same three AI workflows. The interesting part is not the models — it is the scaffolding around them. Here is what actually moved the needle for us, and what to skip.",
        "Essay": "The claim is simple: taste is becoming the scarce input, not compute. Two studies and one awkward counterexample later, the honest answer sits somewhere more useful than either camp admits.",
        "Email": "Subject: Pricing page test — decision by Friday. You know the Q3 numbers; the ask is a one-week test on the annual toggle. Two bullets of context below, and I need a yes or no by Friday noon.",
        "Script": "OPEN on a phone screen, 3am. MAYA wants the launch to land tomorrow; the build is not ready. When the demo finally runs, it says the one thing she never scripted — and the room goes quiet."
    ]

    // MARK: State

    @State private var type = "Blog post"
    @State private var tone = "Warm"
    @State private var brief = ""
    @State private var isThinking = false
    @State private var outlineShown = false
    @State private var draftText = ""
    @State private var toast: String?

    private var outlineRows: [String] {
        outlines[type] ?? []
    }

    private var wordCount: Int {
        draftText
            .split(whereSeparator: { $0.isWhitespace || $0.isNewline })
            .filter { !$0.isEmpty }
            .count
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
                        StaggerIn(index: 0) { typeSection }
                        StaggerIn(index: 1) { toneSection }
                        StaggerIn(index: 2) { briefSection }
                        StaggerIn(index: 3) { outlineButton }
                        if isThinking {
                            StaggerIn(index: 4) { thinkingRow }
                        }
                        if outlineShown {
                            StaggerIn(index: 4) { outlineSection }
                            StaggerIn(index: 5) { draftSection }
                        }
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
            Text("Writing")
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

    // MARK: Type + tone

    private var typeSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Type")
            HStack(spacing: Aero.Spacing.s) {
                ForEach(types, id: \.self) { option in
                    AeroChip(text: option, selected: type == option) {
                        GSHaptics.select()
                        withAnimation(Aero.snappy) { type = option }
                    }
                }
                Spacer()
            }
        }
    }

    private var toneSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Tone")
            HStack(spacing: Aero.Spacing.s) {
                ForEach(tones, id: \.self) { option in
                    AeroChip(text: option, selected: tone == option) {
                        GSHaptics.select()
                        withAnimation(Aero.snappy) { tone = option }
                    }
                }
                Spacer()
            }
        }
    }

    // MARK: Brief

    private var briefSection: some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                SectionHeader(title: "Brief")
                editorContainer(
                    "What should this piece say — and for whom?",
                    text: $brief,
                    height: 90
                )
            }
        }
    }

    // MARK: Outline

    private var outlineButton: some View {
        Button {
            startDraft()
        } label: {
            Text("Draft outline")
                .font(Aero.title())
                .frame(maxWidth: .infinity)
                .padding(14)
                .background(RoundedRectangle(cornerRadius: Aero.Radius.card).fill(Aero.container))
                .overlay(RoundedRectangle(cornerRadius: Aero.Radius.card).stroke(Aero.outline, lineWidth: 1))
                .foregroundStyle(Aero.text)
        }
        .buttonStyle(KineticPressStyle())
        .disabled(isThinking)
    }

    private var thinkingRow: some View {
        HStack(spacing: Aero.Spacing.s) {
            AuroraIndicator()
            Text("Outlining…")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
        }
    }

    private func startDraft() {
        guard !isThinking else { return }
        GSHaptics.tap()   // committed — the draft moment begins
        isThinking = true
        Task {
            try? await Task.sleep(nanoseconds: 700_000_000)
            withAnimation(Aero.spring) {
                isThinking = false
                outlineShown = true
                draftText = drafts[type] ?? ""
            }
        }
    }

    private var outlineSection: some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                Text("Outline")
                    .font(Aero.headline())
                    .foregroundStyle(Aero.text)
                ForEach(outlineRows.indices, id: \.self) { index in
                    HStack(alignment: .top, spacing: Aero.Spacing.s) {
                        Text("\(index + 1)")
                            .font(Aero.label())
                            .foregroundStyle(Aero.accent)
                            .frame(width: 16, alignment: .leading)
                        Text(outlineRows[index])
                            .font(Aero.body())
                            .foregroundStyle(Aero.text)
                    }
                }
            }
        }
    }

    // MARK: Draft

    private var draftSection: some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                Text("Draft")
                    .font(Aero.headline())
                    .foregroundStyle(Aero.text)
                editorContainer(
                    "Start writing, or re-draft the outline…",
                    text: $draftText,
                    height: 200
                )
                Text("\(wordCount) words · \(type) · \(tone) tone")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
                actionRow
            }
        }
    }

    private var actionRow: some View {
        HStack(spacing: Aero.Spacing.s) {
            Button {
                UIPasteboard.general.string = draftText
                GSHaptics.success()
                showToast("Copied")
            } label: {
                Label("Copy", systemImage: "doc.on.doc")
                    .font(Aero.label())
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(Capsule().fill(Aero.container))
                    .foregroundStyle(Aero.text)
            }
            .buttonStyle(KineticPressStyle())

            ShareLink(item: draftText) {
                Label("Share", systemImage: "square.and.arrow.up")
                    .font(Aero.label())
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(Capsule().fill(Aero.container))
                    .foregroundStyle(Aero.text)
            }
            .buttonStyle(KineticPressStyle())

            Button {
                // Real save: the finished draft lands in the Library under
                // the Documents kind — filters catch it.
                ConversationStore.shared.saveToLibrary(content: draftText, kind: "document")
                GSHaptics.success()
                showToast("Saved to Library")
            } label: {
                Label("Save", systemImage: "bookmark")
                    .font(Aero.label())
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(Capsule().fill(Aero.container))
                    .foregroundStyle(Aero.text)
            }
            .buttonStyle(KineticPressStyle())

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
        withAnimation(Aero.motion(Aero.snappy)) { toast = message }
        Task {
            try? await Task.sleep(nanoseconds: 1_800_000_000)
            if toast == message {
                withAnimation(Aero.motion(Aero.snappy)) { toast = nil }
            }
        }
    }
}
