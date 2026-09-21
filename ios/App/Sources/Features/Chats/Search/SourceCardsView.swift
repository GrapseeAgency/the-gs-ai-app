import SwiftUI

/**
 * PHASE 8.1 — compact source cards (docs/search-event-protocol.md). One row
 * per real source: favicon tile with the monogram fallback (PHASE 8.2 — the
 * real favicon is fetched client-side from the DDG icon service, the
 * monogram covers loading/failure), title (max 2 lines), domain, optional
 * date, ordinal badge. The ordinal IS the citation number the answer's
 * `[N]` chips refer to.
 *
 * Honest-state rules: cards render ONLY real sources — live ones as `source`
 * events arrive, the done payload's persisted rows after finalize. A tap
 * opens the REAL URL through the same `UIApplication.open` path the image
 * block uses (never a fabricated link; URLs come from server data only).
 * `snippet_only` sources say so — headline-only is a real state, not a
 * failure to hide; `failed`/`skipped` rows stay visible with their status.
 */
struct SourceCardsView: View {

    let sources: [MessageSource]

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Sources")
                .font(Aero.label())
                .foregroundStyle(Aero.textMuted)
            ForEach(ordered, id: \.id) { source in
                SourceCardRow(source: source)
            }
        }
    }

    /// Citation order — the wire guarantees ordinals; sorting keeps live
    /// arrival order from scrambling the numbered badges.
    private var ordered: [MessageSource] {
        sources.sorted { $0.ordinal < $1.ordinal }
    }
}

private struct SourceCardRow: View {

    let source: MessageSource

    private var url: URL? {
        let lower = source.url.lowercased()
        guard lower.hasPrefix("https://") || lower.hasPrefix("http://") else { return nil }
        return URL(string: source.url)
    }

    private var monogram: String {
        let base = source.domain.isEmpty ? source.title : source.domain
        return base.first.map { String($0).uppercased() } ?? "#"
    }

    private var headline: String {
        if !source.title.isEmpty { return source.title }
        return source.domain.isEmpty ? "Source \(source.ordinal)" : source.domain
    }

    private var dateLabel: String? {
        guard let raw = source.publishedDate, !raw.isEmpty else { return nil }
        if let date = GSFormatters.date(from: raw) {
            return GSFormatters.dayTitle.string(from: date)
        }
        return raw
    }

    var body: some View {
        Button {
            if let url {
                UIApplication.shared.open(url)
            }
        } label: {
            HStack(spacing: 10) {
                SourceFaviconView(domain: source.domain, monogram: monogram)
                VStack(alignment: .leading, spacing: 2) {
                    Text(headline)
                        .font(Aero.bodyMedium())
                        .foregroundStyle(Aero.text)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    metaRow
                }
                Spacer(minLength: 0)
                if url != nil {
                    Image(systemName: "arrow.up.right")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(Aero.textTertiary)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(RoundedRectangle(cornerRadius: Aero.Radius.sm).fill(Aero.raisedSurface))
            .contentShape(Rectangle())
        }
        .buttonStyle(KineticPressStyle())
        .disabled(url == nil)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilityText)
    }

    @ViewBuilder
    private var metaRow: some View {
        HStack(spacing: 6) {
            Text("\(source.ordinal)")
                .font(Aero.metadata())
                .foregroundStyle(Aero.text)
                .padding(.horizontal, 5)
                .padding(.vertical, 1)
                .background(Capsule().fill(Aero.container))
                .overlay(Capsule().stroke(Aero.outline, lineWidth: 1))
            if !source.domain.isEmpty {
                Text(source.domain)
                    .font(Aero.metadata())
                    .foregroundStyle(Aero.textTertiary)
                    .lineLimit(1)
            }
            if let dateLabel {
                Text(dateLabel)
                    .font(Aero.metadata())
                    .foregroundStyle(Aero.textTertiary)
            }
            statusView
            if source.used == true {
                Text("cited")
                    .font(Aero.metadata())
                    .foregroundStyle(Aero.textMuted)
            }
        }
    }

    /// Live fetch status — a spinner only while the page is genuinely being
    /// read (reduced-motion users see the state via the text labels; the
    /// system control is the same one the image block uses).
    @ViewBuilder
    private var statusView: some View {
        switch source.status {
        case "reading", "opening":
            HStack(spacing: 3) {
                ProgressView()
                    .scaleEffect(0.55)
                Text("reading")
                    .font(Aero.metadata())
                    .foregroundStyle(Aero.textTertiary)
            }
        case "failed":
            HStack(spacing: 3) {
                Image(systemName: "xmark.circle")
                    .font(.system(size: 10))
                Text("failed")
                    .font(Aero.metadata())
            }
            .foregroundStyle(Aero.textMuted)
        case "skipped":
            HStack(spacing: 3) {
                Image(systemName: "minus.circle")
                    .font(.system(size: 10))
                Text("skipped")
                    .font(Aero.metadata())
            }
            .foregroundStyle(Aero.textTertiary)
        case "snippet_only":
            Text("headline only")
                .font(Aero.metadata())
                .foregroundStyle(Aero.textTertiary)
        default:
            EmptyView()
        }
    }

    private var accessibilityText: String {
        var parts = ["Source \(source.ordinal). \(headline)"]
        if !source.domain.isEmpty { parts.append(source.domain) }
        if let dateLabel { parts.append(dateLabel) }
        return parts.joined(separator: ", ")
    }
}

/// PHASE 8.2 — the source tile: the REAL favicon (the protocol's DDG icon
/// URL derived client-side from the server-sent domain) over the 8.1
/// monogram fallback. AsyncImage fetches off the main thread; while loading
/// and on failure the exact monogram tile shows, so layout never jumps and
/// a dead icon degrades silently. All chrome stays on semantic tokens — the
/// icon itself is content, the same identity the web client shows.
private struct SourceFaviconView: View {

    let domain: String
    let monogram: String

    /// `https://icons.duckduckgo.com/ip3/<domain>.ico` — built from the
    /// domain field only (server truth; never model text, never a URL host
    /// re-parse). Anything that doesn't look like a bare hostname falls
    /// back to the monogram.
    private var iconURL: URL? {
        let host = domain.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !host.isEmpty,
              !host.contains("/"), !host.contains("?"), !host.contains("#"),
              !host.contains(" ") else { return nil }
        return URL(string: "https://icons.duckduckgo.com/ip3/\(host).ico")
    }

    var body: some View {
        ZStack {
            if let iconURL {
                AsyncImage(url: iconURL) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFit()
                            .padding(5)
                    case .empty, .failure:
                        monogramTile // placeholder + fallback: the same tile
                    @unknown default:
                        monogramTile
                    }
                }
            } else {
                monogramTile
            }
        }
        .frame(width: 30, height: 30)
        .background(RoundedRectangle(cornerRadius: 8).fill(Aero.container))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Aero.outline, lineWidth: 1))
    }

    private var monogramTile: some View {
        Text(monogram)
            .font(Aero.label())
            .foregroundStyle(Aero.text)
    }
}
