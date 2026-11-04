import SwiftUI

// A MENU THAT GROWS OUT OF ITS BUTTON.
//
// The panel in GsOverlayPanel is right for a sheet of content. It is wrong for a
// menu, and the reference the owner kept sending says so: the attach menu is a
// narrow list in the bottom-left corner, level with the "+" it came from, and
// the options menu is a narrow list under the three dots in the header. Neither
// is centred, neither spans the width, neither has a title or a drag handle — a
// menu is an appendage of one control, and it should look like one.
//
// Android draws the same object from `GsOverlay.kt` (`GsAnchoredPopover`), with
// the same numbers: 248pt for the attach list, 208 for the options menu, 22pt
// corners, 54/46pt rows.

struct GsAnchoredPopover<Content: View>: View {
    /// The corner it hangs off: `.bottomLeading` for attach, `.topTrailing` for
    /// the conversation menu.
    var alignment: Alignment = .bottomLeading
    var width: CGFloat = 248
    /// Clearance from the side it is anchored to.
    var horizontalInset: CGFloat = 14
    /// Clearance from the top or bottom — the header, or the composer.
    var verticalInset: CGFloat = 84
    var onDismiss: () -> Void
    @ViewBuilder var content: () -> Content

    @Environment(\.colorScheme) private var scheme

    /// The corner the growth starts at, so the panel emerges from the button
    /// instead of fading in on top of the page.
    private var unitAnchor: UnitPoint {
        switch alignment {
        case .topLeading: return .topLeading
        case .topTrailing: return .topTrailing
        case .bottomLeading: return .bottomLeading
        case .bottomTrailing: return .bottomTrailing
        default: return .center
        }
    }

    var body: some View {
        ZStack(alignment: alignment) {
            // A menu does not black out the page it points at. 12% is enough to
            // say "tap elsewhere to close".
            Color.black.opacity(0.12)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture { onDismiss() }

            content()
                .frame(width: width)
                .padding(.vertical, 6)
                // A menu has to be legible over ANY content, so it is the one
                // surface that is nearly opaque: the dialog fill under the glass,
                // plus a real shadow. Pure glass was pretty and unreadable.
                .background(
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .fill(Aero.dialogSurface.opacity(scheme == .dark ? 0.92 : 0.88)))
                .gsGlass(.modal, corner: 22)
                // One hairline so the panel has an EDGE on a pearl page — the
                // glass rim is white-on-white there and the shadow alone leaves
                // the menu looking like a smudge.
                .overlay(
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .strokeBorder(Aero.outline.opacity(scheme == .dark ? 0.55 : 0.95), lineWidth: 0.8))
                .shadow(color: .black.opacity(0.26), radius: 22, y: 10)
                .padding(.horizontal, horizontalInset)
                .padding(.vertical, verticalInset)
                .transition(
                    .scale(scale: 0.94, anchor: unitAnchor).combined(with: .opacity))
        }
        .ignoresSafeArea(.keyboard, edges: .bottom)
    }
}

/// One line of a menu: a glyph and a label, nothing else.
///
/// The reference rows are 54pt tall with a 38pt circular well on the left — the
/// well is what makes a list read as "things I can pick" rather than "settings",
/// so `well` is on by default and the conversation menu turns it off.
struct GsPopoverRow: View {
    let icon: GsIconPaths
    let label: String
    var destructive: Bool = false
    var enabled: Bool = true
    var well: Bool = true
    /// A second line, only for the two-step confirmations.
    var subtitle: String? = nil

    @Environment(\.colorScheme) private var scheme

    private var ink: Color {
        destructive ? Aero.danger : (enabled ? Aero.text : Aero.textTertiary)
    }

    var body: some View {
        HStack(spacing: 12) {
            if well {
                GsIconView(icon, size: 19, color: ink)
                    .frame(width: 38, height: 38)
                    .background(
                        Circle().fill(
                            destructive
                                ? Aero.danger.opacity(0.14)
                                : Aero.text.opacity(scheme == .dark ? 0.10 : 0.055)))
                    .overlay(
                        Circle().strokeBorder(
                            destructive
                                ? Aero.danger.opacity(0.28)
                                : Aero.outline.opacity(scheme == .dark ? 0.45 : 0.75),
                            lineWidth: 0.7))
            } else {
                GsIconView(icon, size: 18, color: ink)
                    .frame(width: 22)
            }
            VStack(alignment: .leading, spacing: 1) {
                Text(label)
                    .font(.custom(Aero.productName(.medium), size: 14.5, relativeTo: .body))
                    .foregroundStyle(ink)
                    .lineLimit(1)
                if let subtitle {
                    Text(subtitle)
                        .font(.custom(Aero.productName(.regular), size: 11.5, relativeTo: .caption2))
                        .foregroundStyle(Aero.textTertiary)
                        .lineLimit(2)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, subtitle == nil ? 0 : 6)
        .frame(minHeight: well ? 54 : 46)
        .contentShape(Rectangle())
    }
}

/// The hairline that separates a menu's groups without drawing a box.
struct GsPopoverRule: View {
    var body: some View {
        Rectangle()
            .fill(Aero.outline.opacity(0.55))
            .frame(height: 1)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
    }
}
