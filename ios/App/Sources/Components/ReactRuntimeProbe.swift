#if DEBUG
import SwiftUI

/// Test-only source fixture. Parsing, code controls, compiler, React events and
/// storage all use production components. Nothing is compiled into Release.
struct ReactRuntimeProbe: View {
    @State private var otherResponse = false
    private static let source: String? = {
        guard let url = Bundle.main.url(forResource: "react-preview-acceptance", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let contract = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        return contract["source"] as? String
    }()
    var body: some View {
        VStack {
            Button(otherResponse ? "Use original response" : "Use other response") { otherResponse.toggle() }
            Text(otherResponse ? "Other response" : "Original response")
            ScrollView {
                if let source = Self.source {
                    // Exercise actual ChatDetailView message composition and
                    // its production storage scope, including a freshly created
                    // UI UUID after relaunch with the same durable row identity.
                    MessageBubble(
                        message: ChatViewModel.ChatMessage(
                            role: "assistant", content: "```tsx\n" + source + "\n```",
                            createdAt: "2026-10-20T10:30:00Z",
                            durableID: otherResponse ? "other" : "original"
                        ),
                        conversationID: ProcessInfo.processInfo.environment["GS_REACT_ACCEPTANCE_SCOPE"] ?? "react-ui-acceptance"
                    )
                        .id(otherResponse)
                        .padding(12)
                } else { Text("Missing React acceptance source") }
            }
        }
        .background(Color.white)
    }
}
#endif
