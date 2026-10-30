import Combine
import Foundation

/// The one place the plan, its price and its limits come from on iOS — the
/// twin of Android's `data/PlanStore.kt`, with the same keys and the same
/// honesty rules.
///
/// WHAT IS REAL HERE
///  - the price: 12.99 USD/month, the figure the owner confirmed (the mock's
///    19.99 is placeholder art);
///  - the window: the next renewal is derived from a real activation stamp,
///    never from the day the app was opened;
///  - activation: `activated` is FALSE until `recordActivation` runs. There is
///    no purchase path in this build, so no install can honestly show an
///    "Active" pill or a billing date.
///
/// WHAT IS NOT REAL, AND THEREFORE NOT CLAIMED
///  - no payment processor, no card, no invoice, no Play/Store billing. The
///    Subscription screen must not draw a card or a receipt, and does not.
@MainActor
final class PlanStore: ObservableObject {

    static let shared = PlanStore()

    static let proID = "pro"
    static let proName = "Nexly Pro"
    static let proPriceUSD = 12.99

    /// Monthly allowances for the four meters the sheet draws.
    ///
    /// HONEST STATUS: DISPLAYED, not enforced. No code path consults them, so
    /// reaching one changes nothing. The screen must therefore never imply a
    /// cap that bites.
    static let limitMessages = 1_000
    static let limitImages = 500
    static let limitUploads = 50
    static let limitReasoning = 200

    private enum K {
        static let plan = "gs.plan.id"
        static let since = "gs.plan.since"
        static let activated = "gs.plan.activated"
    }

    @Published private(set) var planId: String
    @Published private(set) var sinceMillis: Double
    @Published private(set) var activated: Bool

    private let defaults = UserDefaults.standard

    private init() {
        planId = defaults.string(forKey: K.plan) ?? Self.proID
        sinceMillis = defaults.double(forKey: K.since)
        activated = defaults.bool(forKey: K.activated)
    }

    var planName: String {
        planId == Self.proID ? Self.proName : planId.capitalized
    }

    var cycleLabel: String { "Monthly" }

    /// "$12.99 / month" — formatted from the constant, never re-typed in a view.
    var priceLabel: String {
        let formatter = Self.priceFormatter
        let amount = formatter.string(from: NSNumber(value: Self.proPriceUSD)) ?? "12.99"
        return "$\(amount) / month"
    }

    /// True only when an activation was really recorded.
    var isActive: Bool { planId == Self.proID && activated }

    /// The next renewal from the real stamp, or nil when there is none.
    func nextBillingDate(now: Date = Date()) -> Date? {
        guard activated, sinceMillis > 0 else { return nil }
        let since = Date(timeIntervalSince1970: sinceMillis / 1000)
        var calendar = Calendar.current
        calendar.timeZone = TimeZone.current
        var next = since
        var guardCount = 0
        while next <= now && guardCount < 240 {
            guard let advanced = calendar.date(byAdding: .month, value: 1, to: next) else { return nil }
            next = advanced
            guardCount += 1
        }
        return next
    }

    /// The only way a plan becomes active. Called from a real activation flow.
    func recordActivation(now: Date = Date()) {
        planId = Self.proID
        sinceMillis = now.timeIntervalSince1970 * 1000
        activated = true
        defaults.set(Self.proID, forKey: K.plan)
        defaults.set(sinceMillis, forKey: K.since)
        defaults.set(true, forKey: K.activated)
    }

    func clearActivation() {
        activated = false
        sinceMillis = 0
        defaults.removeObject(forKey: K.activated)
        defaults.removeObject(forKey: K.since)
    }

    private static let priceFormatter: NumberFormatter = {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter
    }()
}

/// Real monthly usage for Subscription & Usage, counted from the same store
/// the chat list reads. A count that cannot be read is nil, and the screen
/// prints "—" rather than a plausible zero.
struct GSUsageMeter {
    var messages: Int
    var uploads: Int

    static let unknown = GSUsageMeter(messages: -1, uploads: -1)

    /// Counts user turns (and those carrying attachments) since the first
    /// instant of the current calendar month, in the device's timezone.
    @MainActor
    static func read(now: Date = Date()) -> GSUsageMeter {
        let calendar = Calendar.current
        guard let start = calendar.date(from: calendar.dateComponents([.year, .month], from: now)) else {
            return .unknown
        }
        var messages = 0
        var uploads = 0
        for message in ConversationStore.shared.messages where message.role == "user" {
            guard let created = GSFormatters.date(from: message.createdAt), created >= start else { continue }
            messages += 1
            if let attachments = message.attachments, !attachments.isEmpty {
                uploads += 1
            }
        }
        return GSUsageMeter(messages: messages, uploads: uploads)
    }
}
