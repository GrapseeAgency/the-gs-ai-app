import SwiftUI

/// The drawer's measured geometry, mirroring Android's `DrawerMetrics`.
///
/// Every value is derived from `assets/reference/drawer-reference.png` by
/// `scripts/check_drawer_render.py --derive`, and the gate reads these two files
/// back and fails if either drifts from the contract. The comments are the
/// sheet's own measurements.
enum DrawerMetrics {
    /// 529 px of a 673 px screen = 308.9 pt at 393.
    static let panelRatio: CGFloat = 0.786

    /// The sheet's bottom-right arc fits r = 47.1 px. Top-right is square.
    static let cornerRadius: CGFloat = 27

    /// The first section label's ink starts 25.1 pt from the panel's left.
    static let contentInset: CGFloat = 24

    static let searchHeight: CGFloat = 42       // sheet 42.0
    static let searchGlyph: CGFloat = 18
    static let heroHeight: CGFloat = 54         // sheet 53.7
    static let heroRadius: CGFloat = 20
    static let heroOrb: CGFloat = 42            // sheet 42.3
    static let pillToHero: CGFloat = 15         // sheet 15.2

    static let disc: CGFloat = 44
    static let destPitch: CGFloat = 46
    static let discToTitle: CGFloat = 13
    static let recentPitch: CGFloat = 56        // sheet 55.7

    static let chevron: CGFloat = 12
    static let chevronInset: CGFloat = 24

    static let wordmark: CGFloat = 22
    static let subtitle: CGFloat = 12
    static let searchText: CGFloat = 15
    static let heroLabel: CGFloat = 15
    static let sectionLabel: CGFloat = 10.5
    static let rowTitle: CGFloat = 13
    static let chatTitle: CGFloat = 13
    static let timestamp: CGFloat = 11.5
    static let footerName: CGFloat = 12.5
    static let footerSub: CGFloat = 11.5

    /// +0.10 em on the sheet's section labels.
    static let sectionTracking: CGFloat = 0.10

    static let avatar: CGFloat = 36
    static let gear: CGFloat = 34
    static let gearGlyph: CGFloat = 15
    static let closeHit: CGFloat = 30
    static let closeGlyph: CGFloat = 10
    static let headerOrb: CGFloat = 56

    /// Measured at 0.18–0.22 black against our own Home band. The old drawer used
    /// `Aero.scrim` (0.50 light / 0.60 dark), which is 2.5× the reference.
    static let scrim = Color.black.opacity(0.20)

    /// Neutral greys from the sheet — cooler than the app's warm tertiary.
    static let labelInk = Color(red: 0.471, green: 0.482, blue: 0.498)      // 120,123,127
    static let timestampInk = Color(red: 0.569, green: 0.549, blue: 0.569)  // 145,140,145

    static let heroLeft = Color(red: 0.996, green: 0.898, blue: 0.753)
    static let heroRight = Color(red: 0.973, green: 0.922, blue: 0.906)
    static let heroOrbTop = Color(red: 1.000, green: 0.847, blue: 0.659)
    static let heroOrbBottom = Color(red: 0.996, green: 0.671, blue: 0.424)

    static func panelWidth(in width: CGFloat) -> CGFloat { width * panelRatio }

    static func sectionLabelStyle() -> Font {
        .system(size: sectionLabel, weight: .medium)
    }
}

/// The four destinations, and the reason each one is real. Mirrors Android's
/// `DESTINATIONS`; the Explore line is the single point the concurrent Explore
/// work re-points.
enum DrawerDestination: String, CaseIterable, Identifiable {
    case explore, imageStudio, voice, savedCreations

    var id: String { rawValue }

    var label: String {
        switch self {
        case .explore: return "Explore"
        case .imageStudio: return "Image Studio"
        case .voice: return "Voice"
        case .savedCreations: return "Saved creations"
        }
    }

    var symbol: String {
        switch self {
        case .explore: return "safari"
        case .imageStudio: return "photo"
        case .voice: return "waveform"
        case .savedCreations: return "bookmark"
        }
    }

    /// Image Studio has no screen yet, so it lands in the composer with a real
    /// draft — the same `chatPrefill` contract Home's Create tile uses.
    var route: AeroRoute {
        switch self {
        case .explore: return .travelAgent
        case .imageStudio: return .chatPrefill(DrawerMetrics.imageStudioPrompt)
        case .voice: return .voice
        case .savedCreations: return .chatArchive
        }
    }
}

extension DrawerMetrics {
    static let imageStudioPrompt = "Create an image of "
}

/// One row of the recent list, resolved before it reaches the view so `body`
/// never sorts or filters the store (that used to run on every drag sample).
struct DrawerRecent: Identifiable {
    let id: String
    let title: String
    let timestamp: String
    let symbol: String
}

/// A capture needs rows without a database. Production passes nil.
struct DrawerFixture {
    let name: String
    let workspace: String
    let initials: String
    let recents: [DrawerRecent]
}

/// The sheet's time vocabulary, mirroring Android's `relativeTime`: "Just now",
/// "Yesterday", then a weekday short name. Parsing is ISO-8601 like the store's
/// `updatedAt`, and an unparseable stamp reads "Earlier" instead of pretending to
/// be fresh.
enum DrawerTime {
    private static let weekdays = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]

    static func relative(iso: String, now: Date = Date()) -> String {
        guard let date = ISO8601DateFormatter().date(from: iso)
                ?? isoDateTimeFormatter.date(from: iso) else { return "Earlier" }
        let seconds = now.timeIntervalSince(date)
        if seconds < 3600 { return "Just now" }
        if seconds < 12 * 3600 { return "\(Int(seconds / 3600)) h ago" }
        let days = Int(seconds / 86_400)
        if days <= 1 { return "Yesterday" }
        if days < 7 {
            let index = (Calendar.current.component(.weekday, from: date) + 5) % 7
            return weekdays[index]
        }
        return "\(days / 7)w ago"
    }

    /// A decorative glyph chosen by a stable hash of the title, mirroring
    /// Android's `iconForTitle` so the two platforms pick the same family.
    static func symbol(for title: String) -> String {
        let glyphs = ["sparkles", "chart.line.uptrend.xyaxis", "atom", "paintbrush"]
        var hash = 7
        for scalar in title.unicodeScalars { hash = (hash &* 31 &+ Int(scalar.value)) & 0x7fff_ffff }
        return glyphs[hash % glyphs.count]
    }

    private static let isoDateTimeFormatter: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()
}
