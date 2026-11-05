import SwiftUI

/// One help article — iOS twin of `HelpCatalog.kt`.
///
/// The topics used to be an array built inside `HelpSupportView`, which is why
/// tapping one could only open a `.sheet`: there was no addressable id, so there
/// was nowhere for a page to live. Each article now has a slug, its steps, and
/// the ids of the articles to read next.
struct GsHelpTopic: Hashable, Identifiable {
    /// Stable address for the route (`helpArticle(slug)`).
    let slug: String
    let question: String
    /// The full answer.
    let answer: String
    /// The one-line row subtitle.
    let summary: String
    let category: GsHelpCategory
    let glyph: String
    let tone: GsWellTone
    /// The same answer, split into the steps a reader follows.
    var steps: [String] = []
    /// Slugs of the articles to read next.
    var related: [String] = []

    var id: String { slug }
}

enum GsHelpCatalog {

    static let topics: [GsHelpTopic] = [
        GsHelpTopic(
            slug: "change-model",
            question: "How do I change my AI model?",
            answer: "Settings → AI & Intelligence opens the model screen. The effort tile is remembered for the next new chat, and on-device models are managed under AI & Intelligence → On-device.",
            summary: "Switch models and adjust reasoning effort in AI & Intelligence.",
            category: .features, glyph: "sparkles", tone: .lilac,
            steps: [
                "Open Settings, then AI & Intelligence.",
                "Pick a model; the choice applies to the next new chat.",
                "Effort is a separate control — it changes how long the model may think.",
            ],
            related: ["update-available", "notifications"]),
        GsHelpTopic(
            slug: "mail-address",
            question: "Where is my Grapsee Mail address?",
            answer: "Account & Security shows the Grapsee Mail address this install signs in with. Edit it in Profile & Preferences; Nexly never asks for a password.",
            summary: "Account & Security shows the address this install signs in with.",
            category: .account, glyph: "lock", tone: .rose,
            steps: [
                "Settings → Account & Security.",
                "The address is the identity line at the top of the page.",
                "To change it, edit the identity card in Profile & Preferences.",
            ],
            related: ["biometric-login", "subscription"]),
        GsHelpTopic(
            slug: "subscription",
            question: "Manage my subscription",
            answer: "Subscription & Usage shows Nexly Pro at $12.99 per month and this month's real usage. No payment is taken in this build, so there is no card or invoice to manage.",
            summary: "Nexly Pro is $12.99/month — see this month's real usage.",
            category: .account, glyph: "credit-card", tone: .blue,
            steps: [
                "Settings → Plan, or Profile → Subscription & Usage.",
                "Usage counts messages and attachments actually sent this month.",
                "Nothing can be charged: there is no payment processor in this build.",
            ],
            related: ["mail-address"]),
        GsHelpTopic(
            slug: "image-generation",
            question: "Fix image generation issues",
            answer: "Image generation is not part of this build. Chat replies, attachments and on-device models are; if a reply fails, the composer keeps your text so you can retry.",
            summary: "Image generation is not part of this build.",
            category: .troubleshooting, glyph: "photo", tone: .mint,
            steps: [
                "If a reply fails, the draft stays in the composer — retry from there.",
                "Check that the device is online; the transcript is stored locally either way.",
                "Image generation is not implemented, so no prompt will produce one.",
            ],
            related: ["notifications", "change-model"]),
        GsHelpTopic(
            slug: "notifications",
            question: "Notifications aren't arriving",
            answer: "Check the master switch, the per-type switch and iOS's own permission. Quiet Hours also holds notices back until the window ends.",
            summary: "Check the switches, the permission and Quiet Hours.",
            category: .troubleshooting, glyph: "bell", tone: .peach,
            steps: [
                "Settings → Notifications: the master switch must be on.",
                "Each kind (messages, updates, account) has its own switch.",
                "If Quiet Hours is on, notices wait until the window closes.",
                "Finally, check the system permission for Nexly in iOS Settings.",
            ],
            related: ["change-model"]),
        GsHelpTopic(
            slug: "biometric-login",
            question: "How do I turn on Biometric Login?",
            answer: "Account & Security → Biometric Login asks for your fingerprint or face, and only switches on once it succeeds. The lock is then required at launch.",
            summary: "Ask for your fingerprint or face at launch.",
            category: .privacy, glyph: "fingerprint", tone: .rose,
            steps: [
                "Settings → Account & Security → Biometric Login.",
                "The switch only turns on after a successful scan.",
                "The lock is then required every time the app is opened.",
            ],
            related: ["mail-address"]),
        GsHelpTopic(
            slug: "update-available",
            question: "Why does an update say it is available?",
            answer: "The app checks its release feed and says so once per version, with a route straight to Your chats. Nothing is installed without your tap.",
            summary: "The app checks its release feed once per version.",
            category: .features, glyph: "sparkles", tone: .lilac,
            steps: [
                "The check runs once per app version, not on every launch.",
                "The notice appears in Your chats, where the install flow starts.",
                "Nothing downloads or installs until you tap it.",
            ],
            related: ["change-model"]),
    ]

    static func bySlug(_ slug: String) -> GsHelpTopic? {
        topics.first { $0.slug == slug }
    }

    static func related(to topic: GsHelpTopic) -> [GsHelpTopic] {
        topic.related.compactMap(bySlug)
    }

    static func count(in category: GsHelpCategory) -> Int {
        topics.filter { $0.category == category }.count
    }
}
