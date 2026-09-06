import SwiftUI
import UIKit

/// One conversation — streaming transcript, stop button, starter chips for
/// empty chats, per-message copy/regenerate/share. Backed by ChatViewModel,
/// which owns conversation creation and the SSE stream lifecycle.
struct ChatDetailView: View {

    @StateObject private var vm: ChatViewModel

    // Attach + local toasts (added 8-d; streaming/VM logic untouched)
    @State private var showingAttachments = false
    @State private var toast: String?

    init(conversationID: String?) {
        _vm = StateObject(wrappedValue: ChatViewModel(conversationID: conversationID))
    }

    var body: some View {
        VStack(spacing: 0) {
            if let error = vm.errorMessage {
                ErrorStateView(message: error, retry: {
                    vm.errorMessage = nil
                    vm.retry()
                })
                .padding(.horizontal, Aero.Spacing.m)
                .padding(.top, Aero.Spacing.s)
            }

            transcript

            if vm.isStreaming {
                streamingBar
            }

            Divider().overlay(Aero.outline)
            attachRow
            inputBar
        }
        .background(Aero.background.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showingAttachments) {
            AttachmentSheetView { option in
                showToast(attachmentMessage(for: option))
            }
        }
    }

    // MARK: Transcript

    private var transcript: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 12) {
                    if vm.isLoadingHistory {
                        LoadingView(label: "Catching up")
                            .padding(.top, Aero.Spacing.l)
                    }

                    if vm.messages.isEmpty && !vm.isStreaming && !vm.isLoadingHistory {
                        emptyState
                            .padding(.top, Aero.Spacing.l)
                    }

                    ForEach(vm.messages) { message in
                        MessageBubble(
                            message: message,
                            onRegenerate: { vm.regenerate() },
                            onTranslate: { showToast("Translation arrives with the language pack build") },
                            onSave: { showToast("Saved to Library") }
                        )
                        .id(message.id)
                    }
                }
                .padding(.horizontal, Aero.Spacing.m)
                .padding(.vertical, Aero.Spacing.m)
            }
            .onChange(of: vm.messages.last?.content) { _ in
                withAnimation(Aero.gentle) {
                    if let last = vm.messages.last {
                        proxy.scrollTo(last.id, anchor: .bottom)
                    }
                }
            }
        }
    }

    // MARK: Streaming controls

    private var streamingBar: some View {
        HStack(spacing: Aero.Spacing.s) {
            AuroraIndicator()
            Spacer()
            Button {
                vm.stop()
            } label: {
                Label("Stop generating", systemImage: "stop.fill")
                    .font(Aero.label())
                    .foregroundStyle(Aero.text)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .overlay(Capsule().stroke(Aero.accent, lineWidth: 1))
            }
            .buttonStyle(KineticPressStyle())
        }
        .padding(.horizontal, Aero.Spacing.m)
        .padding(.vertical, Aero.Spacing.s)
    }

    // MARK: Input

    private var inputBar: some View {
        AeroInputBar(text: $vm.draft, action: { vm.send() })
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.vertical, Aero.Spacing.s)
            .background(Aero.surface.ignoresSafeArea(edges: .bottom))
    }

    // MARK: Attach row (sits above the input bar — outside the frozen AeroInputBar)

    private var attachRow: some View {
        HStack(spacing: Aero.Spacing.s) {
            Button {
                showingAttachments = true
            } label: {
                Image(systemName: "plus")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Aero.text)
                    .frame(width: 30, height: 30)
                    .background(Circle().fill(Aero.container))
                    .overlay(Circle().stroke(Aero.outline, lineWidth: 1))
            }
            .buttonStyle(KineticPressStyle())
            if let message = toast {
                Image(systemName: "checkmark.circle")
                    .font(.system(size: 12))
                    .foregroundStyle(Aero.accent)
                Text(message)
                    .font(Aero.label())
                    .foregroundStyle(Aero.textMuted)
                    .lineLimit(1)
            }
            Spacer()
        }
        .padding(.horizontal, Aero.Spacing.m)
        .padding(.top, Aero.Spacing.s)
    }

    private func attachmentMessage(for option: String) -> String {
        switch option {
        case "Camera": return "Camera capture arrives with device builds"
        case "Gallery": return "Gallery import arrives with device builds"
        case "Files": return "File import arrives with the files build"
        case "Document": return "Document upload arrives with the files build"
        case "Code": return "Code attachments arrive with the repo build"
        case "Prompt template": return "Prompt templates arrive with the library build"
        default: return "Attachment support lands with the next build"
        }
    }

    private func showToast(_ message: String) {
        toast = message
        Task {
            try? await Task.sleep(nanoseconds: 1_800_000_000)
            if toast == message {
                toast = nil
            }
        }
    }

    // MARK: Empty state

    private let starters = ["Draft a launch plan", "Explain quantum computing", "Plan a Kyoto itinerary"]

    private var emptyState: some View {
        VStack(spacing: Aero.Spacing.l) {
            EmptyStateView(
                icon: "sparkles",
                title: "New conversation",
                message: "Ask anything — GS is listening."
            )
            VStack(spacing: Aero.Spacing.s) {
                ForEach(starters, id: \.self) { starter in
                    AeroChip(text: starter) {
                        vm.draft = starter
                        vm.send()
                    }
                }
            }
        }
    }
}

// MARK: - Bubble

private struct MessageBubble: View {

    let message: ChatViewModel.ChatMessage
    var onRegenerate: () -> Void = {}
    var onTranslate: () -> Void = {}
    var onSave: () -> Void = {}

    var body: some View {
        if message.role == "user" {
            userBubble
        } else {
            assistantBubble
        }
    }

    private var userBubble: some View {
        HStack(alignment: .bottom, spacing: 0) {
            Spacer(minLength: 56)
            Text(message.content)
                .font(Aero.body())
                .foregroundStyle(Aero.text)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(RoundedRectangle(cornerRadius: 18).fill(Aero.accent.opacity(0.14)))
                .frame(maxWidth: 280, alignment: .trailing)
        }
    }

    private var assistantBubble: some View {
        HStack(alignment: .top, spacing: 0) {
            VStack(alignment: .leading, spacing: 6) {
                Text(bubbleText)
                    .font(Aero.body())
                    .foregroundStyle(Aero.text)
                    .textSelection(.enabled)
                if message.isStreaming {
                    AuroraIndicator()
                } else if !message.content.isEmpty {
                    actionRow
                }
            }
            .padding(14)
            .background(RoundedRectangle(cornerRadius: 18).fill(Aero.surface))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Aero.outline, lineWidth: 1))
            .frame(maxWidth: 300, alignment: .leading)
            .contextMenu { bubbleMenu }
            Spacer(minLength: 40)
        }
    }

    private var bubbleText: String {
        (message.isStreaming && message.content.isEmpty) ? "…" : message.content
    }

    private var actionRow: some View {
        HStack(spacing: 18) {
            Button {
                UIPasteboard.general.string = message.content
            } label: {
                Image(systemName: "doc.on.doc")
            }
            Button(action: onRegenerate) {
                Image(systemName: "arrow.clockwise")
            }
            ShareLink(item: message.content) {
                Image(systemName: "square.and.arrow.up")
            }
            Button {
                // Read aloud — wired with TTS in a later pass.
            } label: {
                Image(systemName: "speaker.wave.2")
            }
            Button(action: onTranslate) {
                Image(systemName: "translate")
            }
            Button(action: onSave) {
                Image(systemName: "bookmark")
            }
        }
        .font(.system(size: 13))
        .foregroundStyle(Aero.textMuted)
        .buttonStyle(KineticPressStyle())
    }

    private var bubbleMenu: some View {
        Group {
            Button {
                UIPasteboard.general.string = message.content
            } label: {
                Label("Copy", systemImage: "doc.on.doc")
            }
            Button(action: onRegenerate) {
                Label("Regenerate", systemImage: "arrow.clockwise")
            }
            ShareLink(item: message.content) {
                Label("Share", systemImage: "square.and.arrow.up")
            }
        }
    }
}
