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

// MARK: - Vision — analyse an image, then talk about it

/// Full-screen image-analysis workspace (presented via .fullScreenCover).
/// Owns its chrome: close control, serif title. Source picking, detection,
/// OCR, chart reading and follow-ups are all local sample state.
struct VisionView: View {

    @Environment(\.dismiss) private var dismiss

    // MARK: Sample data

    private struct QA: Identifiable {
        let id = UUID()
        let question: String
        let answer: String
    }

    private struct Detection: Identifiable {
        let id = UUID()
        let label: String
        let confidence: CGFloat

        var percentText: String { "\(Int((confidence * 100).rounded()))%" }
    }

    private let sourceOptions = ["Camera", "Gallery", "Screenshot"]
    private let tabs = ["Summary", "Objects", "Text", "Chart"]

    private let detections: [Detection] = [
        .init(label: "Ceramic mug", confidence: 0.98),
        .init(label: "Laptop", confidence: 0.96),
        .init(label: "Notebook", confidence: 0.91),
        .init(label: "Plant", confidence: 0.87),
        .init(label: "Window", confidence: 0.74)
    ]

    private let ocrLines = [
        "Quarterly revenue report",
        "Q2 2025 · internal",
        "Revenue: $4.82M (+23% QoQ)",
        "Prepared by GS Studio"
    ]

    private let chartHeights: [CGFloat] = [56, 84, 68, 104, 92, 124]

    private let cannedAnswers = [
        "Based on the image, revenue grew 23% quarter over quarter with the steepest lift in the final bar.",
        "Based on the image, five objects are visible on the desk — the ceramic mug carries the highest confidence.",
        "Based on the image, the chart spans six months from January through June."
    ]

    // MARK: State

    @State private var selectedSource: String?
    @State private var isAnalysing = false
    @State private var hasImage = false
    @State private var selectedTab = "Summary"
    @State private var compareOn = false
    @State private var followUp = ""
    @State private var qa: [QA] = []
    @State private var toast: String?

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
                        StaggerIn(index: 0) { sourceSection }
                        StaggerIn(index: 1) { previewSection }
                        StaggerIn(index: 2) { compareSection }
                        StaggerIn(index: 3) { tabSection }
                        if !qa.isEmpty {
                            StaggerIn(index: 4) { qaSection }
                        }
                    }
                    .padding(.horizontal, Aero.Spacing.m)
                    .padding(.bottom, Aero.Spacing.xl)
                }
                Divider().overlay(Aero.outline)
                followUpBar
            }
        }
        .toolbar(.hidden, for: .navigationBar)
        .overlay(alignment: .bottom) { toastView }
    }

    // MARK: Header (own chrome — no router)

    private var header: some View {
        HStack(spacing: Aero.Spacing.s) {
            closeButton
            Text("Vision")
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

    // MARK: Source chips

    private var sourceSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Source")
            HStack(spacing: Aero.Spacing.s) {
                ForEach(sourceOptions, id: \.self) { option in
                    AeroChip(text: option, selected: selectedSource == option) {
                        selectSource(option)
                    }
                }
                Spacer()
            }
        }
    }

    private func selectSource(_ source: String) {
        guard !isAnalysing else { return }
        selectedSource = source
        isAnalysing = true
        hasImage = false
        Task {
            try? await Task.sleep(nanoseconds: 600_000_000)
            isAnalysing = false
            withAnimation(Aero.spring) { hasImage = true }
        }
    }

    // MARK: Preview

    private var previewSection: some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                SectionHeader(title: "Preview")
                previewTile
            }
        }
    }

    private var previewTile: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 16)
                .fill(hasImage ? Aero.container : Aero.containerHigh)
            if isAnalysing {
                VStack(spacing: Aero.Spacing.s) {
                    AuroraIndicator()
                    Text("Analysing…")
                        .font(Aero.label())
                        .foregroundStyle(Aero.textMuted)
                }
            } else if hasImage {
                Image(systemName: "photo.fill")
                    .font(.system(size: 34))
                    .foregroundStyle(Aero.textMuted)
            } else {
                VStack(spacing: Aero.Spacing.s) {
                    Image(systemName: "photo")
                        .font(.system(size: 34))
                        .foregroundStyle(Aero.textMuted)
                    Text("Pick a source to analyse an image")
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                }
            }
        }
        .frame(height: 200)
        .frame(maxWidth: .infinity)
        .overlay(alignment: .bottomTrailing) {
            if hasImage && !isAnalysing {
                Text("IMG_2041.jpg")
                    .font(Aero.label())
                    .foregroundStyle(Aero.text)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Capsule().fill(Aero.surface))
                    .overlay(Capsule().stroke(Aero.outline, lineWidth: 1))
                    .padding(10)
            }
        }
    }

    // MARK: Compare toggle

    private var compareSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            AeroChip(text: "Compare", selected: compareOn) {
                withAnimation(Aero.snappy) { compareOn.toggle() }
            }
            if compareOn {
                HStack(spacing: Aero.Spacing.m) {
                    compareSlotFilled
                    compareSlotEmpty
                }
            }
        }
    }

    private var compareSlotFilled: some View {
        ZStack(alignment: .bottomLeading) {
            RoundedRectangle(cornerRadius: 16)
                .fill(Aero.container)
            Image(systemName: "photo.fill")
                .font(.system(size: 22))
                .foregroundStyle(Aero.textMuted)
            Text("IMG_2041.jpg")
                .font(Aero.label())
                .foregroundStyle(Aero.text)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Capsule().fill(Aero.surface))
                .padding(8)
        }
        .frame(height: 110)
        .frame(maxWidth: .infinity)
    }

    private var compareSlotEmpty: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 16)
                .stroke(Aero.outline, style: StrokeStyle(lineWidth: 1, dash: [6, 4]))
            VStack(spacing: Aero.Spacing.xs) {
                Image(systemName: "plus")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(Aero.textMuted)
                Text("Add another image")
                    .font(Aero.label())
                    .foregroundStyle(Aero.textMuted)
            }
        }
        .frame(height: 110)
        .frame(maxWidth: .infinity)
    }

    // MARK: Tabs

    private var tabSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            HStack(spacing: Aero.Spacing.s) {
                ForEach(tabs, id: \.self) { tab in
                    AeroChip(text: tab, selected: selectedTab == tab) {
                        selectedTab = tab
                    }
                }
                Spacer()
            }
            tabContent
        }
    }

    @ViewBuilder
    private var tabContent: some View {
        switch selectedTab {
        case "Objects": objectsTab
        case "Text": textTab
        case "Chart": chartTab
        default: summaryTab
        }
    }

    private var summaryTab: some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                Text("A tidy desk scene: a ceramic mug beside a laptop showing a quarterly revenue chart, with a notebook and plant in frame. Lighting is soft daylight from the window on the right.")
                    .font(Aero.body())
                    .foregroundStyle(Aero.text)
                Text("Chart interpreted: revenue up 23% QoQ")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
            }
        }
    }

    private var objectsTab: some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s + 4) {
                ForEach(detections) { detection in
                    detectionRow(detection)
                }
            }
        }
    }

    private func detectionRow(_ detection: Detection) -> some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.xs) {
            HStack {
                Text(detection.label)
                    .font(Aero.title())
                    .foregroundStyle(Aero.text)
                Spacer()
                Text(detection.percentText)
                    .font(Aero.label())
                    .foregroundStyle(Aero.textMuted)
            }
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule().fill(Aero.container)
                    Capsule()
                        .fill(Aero.accent)
                        .frame(width: proxy.size.width * detection.confidence)
                }
            }
            .frame(height: 6)
        }
    }

    private var textTab: some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                Text("Detected text")
                    .font(Aero.title())
                    .foregroundStyle(Aero.text)
                VStack(alignment: .leading, spacing: 3) {
                    ForEach(ocrLines, id: \.self) { line in
                        Text(line)
                            .font(.system(size: 13, design: .monospaced))
                            .foregroundStyle(Aero.text)
                    }
                }
                Button {
                    UIPasteboard.general.string = ocrLines.joined(separator: "\n")
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
            }
        }
    }

    private var chartTab: some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                Text("Revenue by month")
                    .font(Aero.title())
                    .foregroundStyle(Aero.text)
                HStack(alignment: .bottom, spacing: 10) {
                    ForEach(chartHeights.indices, id: \.self) { index in
                        RoundedRectangle(cornerRadius: 6)
                            .fill(Aero.accent)
                            .frame(width: 30, height: chartHeights[index])
                    }
                }
                .frame(height: 128, alignment: .bottom)
                Text("Interpretation: revenue climbed 23% quarter over quarter, led by the March cohort.")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
            }
        }
    }

    // MARK: Follow-up Q&A (appends above the input bar)

    private var qaSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            SectionHeader(title: "Follow-ups")
            ForEach(qa) { item in
                VStack(spacing: Aero.Spacing.s) {
                    HStack(alignment: .bottom, spacing: 0) {
                        Spacer(minLength: 56)
                        Text(item.question)
                            .font(Aero.body())
                            .foregroundStyle(Aero.text)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 12)
                            .background(RoundedRectangle(cornerRadius: 18).fill(Aero.accent.opacity(0.14)))
                            .frame(maxWidth: 280, alignment: .trailing)
                    }
                    HStack(alignment: .top, spacing: 0) {
                        Text(item.answer)
                            .font(Aero.body())
                            .foregroundStyle(Aero.text)
                            .padding(14)
                            .background(RoundedRectangle(cornerRadius: 18).fill(Aero.surface))
                            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Aero.outline, lineWidth: 1))
                            .frame(maxWidth: 300, alignment: .leading)
                        Spacer(minLength: 40)
                    }
                }
            }
        }
    }

    private var followUpBar: some View {
        AeroInputBar(
            text: $followUp,
            placeholder: "Ask about this image…",
            action: { sendFollowUp() }
        )
        .padding(.horizontal, Aero.Spacing.m)
        .padding(.vertical, Aero.Spacing.s)
        .background(Aero.surface.ignoresSafeArea(edges: .bottom))
    }

    private func sendFollowUp() {
        let trimmed = followUp.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let answer = cannedAnswers[qa.count % cannedAnswers.count]
        withAnimation(Aero.gentle) {
            qa.append(QA(question: trimmed, answer: answer))
        }
        followUp = ""
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
                    .padding(.bottom, 96)
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
