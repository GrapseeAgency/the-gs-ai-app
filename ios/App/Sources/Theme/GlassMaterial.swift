import SwiftUI

/**
 * THE GLASS — the iOS twin of `android/.../ui/components/GlassMaterial.kt`.
 *
 * One material, seven semantic roles, four optical cues painted in painterly
 * order. A screen asks for a role; it never picks an alpha. This mirrors the
 * Android contract exactly so a surface reads identically on both platforms
 * (mandate b10), and the platform's real translucency is used only where it does
 * not fight a measured number:
 *
 *  1. a translucent fill — the same analytic cream Android lays down, over
 *     Apple's `Material` (a genuine backdrop blur on the compositor) for the
 *     surfaces the reference does not pin down. Where it does, the material is
 *     off: its own veil is a fixed lift that no tint can cancel, and it put the
 *     sheet's two cards 2.6 L apart on a backdrop 10.0 L apart — see
 *     [GsGlassRole.cardLight];
 *  2. in-scattered airlight — a top-down veil wash that lifts the black floor
 *     where the light enters and fades downward ([GsGlassRole.veilingAlpha]);
 *  3. a lit rim, strongest at the top — measured off the sheet: its Mind Guide
 *     card's hairline reads +17.2 L above its own glass along the WHOLE top
 *     edge and +4.6 L along the bottom. Both ends are brighter than the fill;
 *     the axis is vertical with a lean proportional to the card's height, which
 *     is what Android draws, so a wide flat card keeps a lit top edge across all
 *     of it instead of only at its left corner;
 *  4. interior luminance fall — the panel is lighter at the top than the bottom.
 *
 * Warm, non-grey shadow carries the depth. The whole thing is stock SwiftUI
 * `Shape`/`LinearGradient`/`Material`/`shadow` — available since the app's iOS 16
 * floor, nothing experimental, so it compiles wherever the app does.
 */
enum GsGlassRole {
    case card
    /**
     * The sheet's own glass, over the bloom. Android calls this `CardLight`;
     * the two are the same measured material and must stay that way (b10).
     *
     * Two cards on the sheet pin the fill down: over the flat cream page the
     * Suggestions card drops 242.05 to 239.06, over the bloom the Mind Guide
     * card lifts 229.25 to 232.05. Two equations, two unknowns — the reference's
     * glass is a #F2EAE3 fill at 45% coverage, letting 55% of the ground through.
     *
     * That is also why this role carries NO `Material`. Apple's material is a
     * blur plus its own veil, and the veil is what broke the numbers here: the
     * last build measured the two cards 2.6 L apart on a backdrop 10.0 L apart,
     * i.e. the surface responded to the ground *inversely*. No tint can fix a
     * negative slope, so the material came out and the analytic fill — the same
     * arithmetic Android does — went in. `.card` keeps its material; only the
     * surfaces authored against the sheet opt out of it.
     */
    case cardLight
    /**
     * The same 45% glass with a DIM edge — the sheet's rows on flat cream. Not a
     * fudge to make a second probe pass: the sheet's own Suggestions hairline
     * stands +2.6 L off its glass where its Mind Guide stands +19.9 L. An edge
     * glow is light escaping through a frosted rim, so a card with bright bloom
     * behind it carries more of it than one on flat cream, and the renderer
     * cannot be told that — the role says it. Android's `CardDim` is this.
     */
    case cardDim
    case elevated
    case floatingControl
    case modal
    case sunken

    /// The colour the fill is painted with.
    ///
    /// `Aero.surface` (#F5EDE6) is what the sheet's cards LOOK like; the sheet's
    /// two cards say the fill it sees is #F2EAE3, and the two are not the same
    /// number — see `cardLight`. Android keeps this in its `glassSurface` token,
    /// which is why it is spelled out again here rather than folded into
    /// `Aero.surface`: that colour is every plain surface in the app, and this
    /// one is only ever a translucent layer over something else.
    var tintColor: Color {
        switch self {
        case .cardLight, .cardDim: return Color(red: 0.949, green: 0.918, blue: 0.890)
        default:                   return Aero.surface
        }
    }

    /// Top / bottom of the interior luminance fall, per role.
    var tintAlphaTop: Double {
        switch self {
        // 0.45 is the measured coverage; Android reaches the same number as
        // 0.80 (its token's alpha) × 0.565 (its fillAlpha).
        case .cardLight, .cardDim: return 0.452
        default:                   return fillAlpha * 0.55
        }
    }
    var tintAlphaBottom: Double {
        switch self {
        // Android's fill gradient falls to 82% of the top alpha: 0.452 × 0.82.
        case .cardLight, .cardDim: return 0.371
        default:                   return fillAlpha * 0.28
        }
    }

    /// How opaque the tinted fill sits over the material. Thicker glass scatters more.
    var fillAlpha: Double {
        switch self {
        case .card: return 1
        case .cardLight, .cardDim: return 1
        case .elevated: return 1
        case .floatingControl: return 0.86
        case .modal: return 1
        case .sunken: return 0.62
        }
    }

    /// Airlight added back — the in-scatter term, concentrated at the top edge.
    var veilingAlpha: Double {
        switch self {
        case .card: return 0.05
        case .cardLight, .cardDim: return 0.05
        case .elevated: return 0.08
        case .floatingControl: return 0.06
        case .modal: return 0.12
        case .sunken: return 0
        }
    }

    /// Rim brightness at the lit end.
    var rimStrength: Double {
        switch self {
        case .card: return 0.55
        case .cardLight: return 1.0
        case .cardDim: return 0.20
        case .elevated: return 0.75
        case .floatingControl: return 1.0
        case .modal: return 0.8
        case .sunken: return 0.3
        }
    }

    /// Warm shadow. Sunken wells are seen from the shadowed side, so no lift.
    var elevation: CGFloat {
        switch self {
        case .card: return 10
        case .cardLight, .cardDim: return 10
        case .elevated: return 16
        case .floatingControl: return 20
        case .modal: return 28
        case .sunken: return 0
        }
    }

    /// The translucency the material provides. Glass seen from the shadowed
    /// side (sunken) uses the thinnest frost; the heaviest glass (modal) the
    /// thickest, so a sheet reads as a slab over the transcript.
    ///
    /// `nil` means the role paints its fill over the raw backdrop, because its
    /// numbers were solved against that backdrop and a veil on top of them
    /// breaks them — see `cardLight`.
    var material: Material? {
        switch self {
        case .card, .elevated: return .regularMaterial
        case .cardLight, .cardDim: return nil
        case .floatingControl: return .thinMaterial
        case .modal: return .thickMaterial
        case .sunken: return .ultraThinMaterial
        }
    }
}

/// How far the unlit end of the rim falls below the lit end. The sheet's Mind
/// Guide hairline is +17.2 L over its glass at the top and +4.6 L at the bottom;
/// 4.6 / 17.2 is this number, and Android's `RIM_FALL` is the same constant.
private let gsRimFall = 0.27

/// The reference glass as a background + rim + shadow on any rounded surface.
private struct GsGlassModifier: ViewModifier {
    let role: GsGlassRole
    let corner: CGFloat

    func body(content: Content) -> some View {
        content
            .background(glassShape)
            .overlay { geometry in rim(in: geometry.size) }
            // Cue: warm, non-grey shadow — this is depth, not darkness.
            .shadow(
                color: Color(red: 0.35, green: 0.16, blue: 0.10).opacity(role.elevation > 0 ? 0.14 : 0),
                radius: role.elevation * 0.6,
                x: 0,
                y: role.elevation * 0.28
            )
    }

    /// Material + interior luminance fall + top airlight, all clipped to shape.
    @ViewBuilder private var glassShape: some View {
        let shape = RoundedRectangle(cornerRadius: corner, style: .continuous)
        let filled = shape
            // 1 + 4. tinted fill, lighter at the top, deeper at the bottom.
            .fill(
                LinearGradient(
                    colors: [
                        role.tintColor.opacity(role.tintAlphaTop),
                        role.tintColor.opacity(role.tintAlphaBottom)
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
            .overlay(
                // 2. in-scatter — a broad soft airlight wash over the top third.
                shape.fill(
                    LinearGradient(
                        colors: [
                            Aero.elevatedSurface.opacity(role.veilingAlpha),
                            Aero.elevatedSurface.opacity(role.veilingAlpha * 0.25),
                            .clear
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
            )
        // The backdrop blur sits under the fill only where the role has one.
        // `cardLight` has none, and the reason is in its documentation.
        if let material = role.material {
            filled.background(shape.fill(material))
        } else {
            filled
        }
    }

    /// 3. The lit rim — vertical with a lean, both ends brighter than the fill.
    ///
    /// The lean is a fraction of the card's HEIGHT, exactly as Android's brush
    /// computes it, and it needs the drawn size to do that: a lean expressed as
    /// a fraction of the width drags the top-right corner of a wide flat card
    /// all the way down to the unlit stop, which is how the previous brush ended
    /// up painting no rim at all along the one edge that carries it.
    private func rim(in size: CGSize) -> some View {
        let lit = 0.9 * role.rimStrength
        let lean = min(0.49, size.height * 0.075 / max(size.width, 1))
        return RoundedRectangle(cornerRadius: corner, style: .continuous)
            .strokeBorder(
                LinearGradient(
                    colors: [
                        Aero.elevatedSurface.opacity(lit),
                        Aero.elevatedSurface.opacity(lit * gsRimFall)
                    ],
                    startPoint: UnitPoint(x: 0.5 - lean, y: 0),
                    endPoint: UnitPoint(x: 0.5 + lean, y: 1)
                ),
                lineWidth: 1
            )
    }
}

/// The capsule variant for the composer and pill controls.
private struct GsCapsuleGlassModifier: ViewModifier {
    let role: GsGlassRole

    func body(content: Content) -> some View {
        content
            .background(
                Capsule().fill(
                    LinearGradient(
                        colors: [
                            Aero.surface.opacity(role.fillAlpha * 0.55),
                            Aero.surface.opacity(role.fillAlpha * 0.28)
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
            )
            .background {
                // Only roles that have a material get the backdrop blur; see
                // `cardLight` for why the sheet's own glass opts out of it.
                if let material = role.material {
                    Capsule().fill(material)
                }
            }
            .overlay(
                Capsule().strokeBorder(
                    LinearGradient(
                        colors: [
                            Aero.elevatedSurface.opacity(0.9 * role.rimStrength),
                            Aero.elevatedSurface.opacity(0.9 * role.rimStrength * gsRimFall)
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    ),
                    lineWidth: 1
                )
            )
            .shadow(
                color: Color(red: 0.35, green: 0.16, blue: 0.10).opacity(role.elevation > 0 ? 0.14 : 0),
                radius: role.elevation * 0.6,
                x: 0,
                y: role.elevation * 0.28
            )
    }
}

extension View {
    /// Apply the shared glass material as a rounded-rect background. The iOS
    /// twin of Android `Modifier.gsGlass(role, shape)`.
    func gsGlass(_ role: GsGlassRole = .card, corner: CGFloat = Aero.Radius.card) -> some View {
        modifier(GsGlassModifier(role: role, corner: corner))
    }

    /// The pill/composer variant of the same material.
    func gsCapsuleGlass(_ role: GsGlassRole = .floatingControl) -> some View {
        modifier(GsCapsuleGlassModifier(role: role))
    }
}
