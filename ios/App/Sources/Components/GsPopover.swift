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
        GeometryReader { geometry in
            let clearance = min(verticalInset, max(8, (geometry.size.height - 120) / 2))
            let panelWidth = min(width, max(1, geometry.size.width - 2 * horizontalInset))
            let cap = max(44, geometry.size.height - 2 * clearance)
            ZStack(alignment: alignment) {
                Color.black.opacity(0.12)
                    .ignoresSafeArea()
                    .contentShape(Rectangle())
                    .onTapGesture { onDismiss() }
                    .accessibilityHidden(true)

                ViewThatFits(in: .vertical) {
                    content().fixedSize(horizontal: false, vertical: true)
                    ScrollView(showsIndicators: false) { content() }
                }
                .frame(width: panelWidth)
                .frame(maxHeight: cap)
                .padding(.vertical, 6)
                .gsOverlayGlass(corner: 22)
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("gs.reflective-popover")
                .accessibilityAddTraits(.isModal)
                .accessibilityAction(.escape) { onDismiss() }
                .padding(.horizontal, horizontalInset)
                .padding(.vertical, clearance)
                .transition(.scale(scale: 0.94, anchor: unitAnchor).combined(with: .opacity))
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
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
                    .gsGlass(.floatingControl, corner: 19)
                    .background(Circle().fill(destructive ? Aero.danger.opacity(0.14) : .clear))
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

// MARK: - App-owned long-press menus

/// An anchor snapshot is not observable: scrolling a transcript updates
/// the coordinate without invalidating message bodies or streaming text.
private final class GsMenuAnchor {
    var rect: CGRect = .zero
}

private struct GsMenuPresentation {
    let anchor: CGRect
    let content: AnyView
}

private struct GsPresentMenuKey: EnvironmentKey {
    static let defaultValue: ((CGRect, AnyView) -> Void)? = nil
}

private extension EnvironmentValues {
    var gsPresentMenu: ((CGRect, AnyView) -> Void)? {
        get { self[GsPresentMenuKey.self] }
        set { self[GsPresentMenuKey.self] = newValue }
    }
}

/// One screen-level menu, never clipped by a scrolling row or composer.
/// Native share sheets, text selection and swipe actions remain system-owned.
private struct GsReflectiveMenuHost: ViewModifier {
    @State private var active: GsMenuPresentation?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private func close() { active = nil }

    func body(content: Content) -> some View {
        content
            .environment(\.gsPresentMenu, { anchor, menu in
                active = GsMenuPresentation(anchor: anchor, content: menu)
            })
            .accessibilityHidden(active != nil)
            .overlay {
                if let active {
                    GeometryReader { geometry in
                        panel(active, geometry: geometry)
                    }
                    .transition(.opacity)
                }
            }
            .animation(reduceMotion ? nil : .easeOut(duration: 0.16), value: active != nil)
    }

    private func panel(_ menu: GsMenuPresentation, geometry: GeometryProxy) -> some View {
        let root = geometry.frame(in: .global)
        let width = min(CGFloat(248), max(1, geometry.size.width - 28))
        let x = min(max(menu.anchor.midX - root.minX - width / 2, 14),
                    max(14, geometry.size.width - width - 14))
        let top = min(max(menu.anchor.minY - root.minY, 14), max(14, geometry.size.height - 14))
        let bottom = min(max(menu.anchor.maxY - root.minY, top), max(top, geometry.size.height - 14))
        let above = max(0, top - 20)
        let below = max(0, geometry.size.height - bottom - 20)
        let opensBelow = below >= min(above, 350)
        // Tall message anchors may fill the viewport. In that case float
        // the menu in the available screen instead of crushing the rows.
        let centered = max(above, below) < 138
        let capacity = max(44, min(420, centered ? geometry.size.height - 28 : (opensBelow ? below : above)))
        let alignment: Alignment = centered ? .center : (opensBelow ? .topLeading : .bottomLeading)
        return ZStack(alignment: alignment) {
            Color.black.opacity(0.12)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture { close() }
                .accessibilityHidden(true)
            ViewThatFits(in: .vertical) {
                menuRows(menu.content).fixedSize(horizontal: false, vertical: true)
                ScrollView(showsIndicators: false) { menuRows(menu.content) }
            }
            .frame(width: width)
            .frame(maxHeight: capacity)
            .gsOverlayGlass(corner: 22)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("gs.reflective-action-menu")
            .accessibilityAddTraits(.isModal)
            .accessibilityAction(.escape) { close() }
            .padding(.leading, centered ? 0 : x)
            .padding(.top, centered || !opensBelow ? 0 : bottom + 6)
            .padding(.bottom, centered || opensBelow ? 0 : geometry.size.height - top + 6)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func menuRows(_ content: AnyView) -> some View {
        VStack(alignment: .leading, spacing: 0) { content }
            .labelStyle(.titleAndIcon)
            .buttonStyle(GsReflectiveMenuButtonStyle(close: close))
            .padding(6)
    }
}

/// A real Button remains the touch/keyboard/accessibility target. Only
/// menu-row styling and dismissal are added, not a second tap handler.
private struct GsReflectiveMenuButtonStyle: PrimitiveButtonStyle {
    let close: () -> Void

    func makeBody(configuration: Configuration) -> some View {
        Button(role: configuration.role) {
            close()
            configuration.trigger()
        } label: {
            configuration.label
                .font(.system(size: 14.5, weight: .medium))
                .foregroundStyle(configuration.role == .destructive ? Aero.danger : Aero.text)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 12)
                .frame(minHeight: 46)
                .contentShape(Rectangle())
        }
        .buttonStyle(KineticPressStyle())
    }
}

/// A Button with two exclusive gestures: a long press cannot also perform
/// its short-tap navigation/send action when the finger lifts.
private struct GsMenuTriggerButtonStyle: PrimitiveButtonStyle {
    let open: () -> Void
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .contentShape(Rectangle())
            .gesture(
                LongPressGesture(minimumDuration: 0.5, maximumDistance: 12)
                    .exclusively(before: TapGesture())
                    .onEnded { value in
                        switch value {
                        case .first: open()
                        case .second: configuration.trigger()
                        }
                    }
            )
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { configuration.trigger() }
    }
}

private struct GsReflectiveMenuTrigger<MenuContent: View>: ViewModifier {
    @Environment(\.gsPresentMenu) private var present
    @State private var anchor = GsMenuAnchor()
    let menu: () -> MenuContent
    var tapToOpen: Bool = false
    var button: Bool = false
    @State private var fallbackVisible = false

    private func open() {
        GSHaptics.select()
        if let present {
            present(anchor.rect, AnyView(menu()))
        } else {
            fallbackVisible = true
        }
    }

    @ViewBuilder
    private func trigger(_ content: Content) -> some View {
        if tapToOpen {
            Button(action: open) { content }
                .buttonStyle(KineticPressStyle())
        } else if button {
            content
                .buttonStyle(GsMenuTriggerButtonStyle(open: open))
                .accessibilityAction(named: Text("Actions")) { open() }
        } else {
            content
                .simultaneousGesture(LongPressGesture(minimumDuration: 0.5, maximumDistance: 12)
                    .onEnded { _ in open() })
                .accessibilityAction(named: Text("Actions")) { open() }
        }
    }

    func body(content: Content) -> some View {
        trigger(content)
            .background {
                GeometryReader { geo in
                    Color.clear
                        .onAppear { anchor.rect = geo.frame(in: .global) }
                        .onChange(of: geo.frame(in: .global)) { rect in anchor.rect = rect }
                }
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            }
            .popover(isPresented: $fallbackVisible) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) { menu() }
                        .labelStyle(.titleAndIcon)
                        .buttonStyle(GsReflectiveMenuButtonStyle(close: { fallbackVisible = false }))
                        .padding(6)
                }
                .frame(width: 248, height: 280)
                .gsOverlayGlass(corner: 22)
                .presentationBackground(.clear)
                .presentationCompactAdaptation(.popover)
            }
    }
}

extension View {
    func gsReflectiveMenuHost() -> some View {
        modifier(GsReflectiveMenuHost())
    }

    func gsReflectiveContextMenu<MenuContent: View>(
        button: Bool = false,
        @ViewBuilder _ menu: @escaping () -> MenuContent
    ) -> some View {
        modifier(GsReflectiveMenuTrigger(menu: menu, button: button))
    }

    func gsReflectiveTapMenu<MenuContent: View>(
        @ViewBuilder _ menu: @escaping () -> MenuContent
    ) -> some View {
        modifier(GsReflectiveMenuTrigger(menu: menu, tapToOpen: true))
    }
}
