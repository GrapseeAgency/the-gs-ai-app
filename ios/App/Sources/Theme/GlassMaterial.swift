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
    /// One colour for every role, because Android has one: `glassSurface` is the
    /// translucent layer's token there, and this used to be #F2EAE3 for the two
    /// card roles and `Aero.surface` (#F5EDE6) for everything else. Over a 45%
    /// layer that 3.0 L is not a nuance — it is the difference between the pill
    /// measuring the reference and measuring 3 L brighter than the same glass on
    /// the other platform. `Aero.surface` stays what it is: the right number for
    /// an OPAQUE surface, the wrong one for a layer you see a bloom through.
    /// Dark matches Android's `0xD92A1C19`.
    var tintColor: Color {
        Aero.glassSurface
    }

    /// Top / bottom of the interior luminance fall.
    ///
    /// Derived rather than per-role, because Android derives them the same way:
    /// its `glassSurface` token carries 0xCC = 0.80 alpha, `drawRect` multiplies
    /// the role's `fillAlpha` into that, and the body gradient falls to 82% of
    /// the top. For the card roles that product is 0.80 × 0.565 = 0.452 — the
    /// coverage solved off the sheet, which is why the two numbers hardcoded here
    /// were those. The roles that were NOT hardcoded fell through a `× 0.55`
    /// / `× 0.28` rule Android does not have, and that is how the same role came
    /// to sit 0.22 of an alpha apart on the two platforms.
    var tintAlphaTop: Double { gsGlassTokenAlpha * fillAlpha }
    var tintAlphaBottom: Double { gsGlassTokenAlpha * fillAlpha * 0.82 }

    /// How opaque the tinted fill sits over the material. Thicker glass scatters
    /// more. These are Android's `GsGlassRole` table, unchanged: a role means the
    /// same glass on both platforms or mandate b10 is not met.
    var fillAlpha: Double {
        switch self {
        case .card: return 1
        case .cardLight, .cardDim: return 0.565
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
    ///
    /// `floatingControl` joined that list on measurement, not taste. The sheet's
    /// quick-action pill is a 2.85 L darkening of the page behind it and Android
    /// reads 1.93 L; iOS with `.thinMaterial` read 11.64 L — the pill greyer than
    /// its own fill colour, which no alpha can explain. A vibrancy material is a
    /// second, platform-specific opacity: it blurs the whole background behind
    /// the capsule, so where the neighbourhood contains ink it subtracts light
    /// the recipe never asked for, and the rim measurement rode that difference
    /// (+21.25 L against the sheet's +11.82). Same role, same numbers, both
    /// platforms: the fill is the fill.
    var material: Material? {
        switch self {
        case .card, .elevated: return .regularMaterial
        case .cardLight, .cardDim, .floatingControl: return nil
        case .modal: return .thickMaterial
        case .sunken: return .ultraThinMaterial
        }
    }
}

/// How far the unlit end of the rim falls below the lit end. The sheet's Mind
/// Guide hairline is +17.2 L over its glass at the top and +4.6 L at the bottom;
/// 4.6 / 17.2 is this number, and Android's `RIM_FALL` is the same constant.
private let gsRimFall = 0.27

/// The alpha baked into Android's `glassSurface` token (0xCC), which its
/// `drawRect` then multiplies the role's `fillAlpha` into. Named because both
/// ends of the luminance fall are derived from it, and a platform that is
/// matching Android has to match the multiplication, not just the colour.
private let gsGlassTokenAlpha = 0.80

/// The reference glass as a background + rim + shadow on any shape.
///
/// The shape is an `AnyInsettableShape` rather than a corner radius because the
/// pill and the composer are the SAME material as the cards, cut differently —
/// Android reaches them all through one `gsGlass(role, shape)` and iOS had a
/// second, hand-written capsule recipe that had drifted: no veiling layer, no
/// height-proportional rim lean, and a plain `.shadow` that painted the capsule's
/// own silhouette *through* its translucent fill. That last one cost 5.1 L, which
/// is why the quick-action pills read greyer than the page behind them on iOS
/// while Android's measured the reference's own 2.6 L.
///
/// Insettable, not just `Shape`: the rim is drawn with `strokeBorder`, which only
/// exists on `InsettableShape`, and `AnyShape` erases that conformance — the CI
/// compiler answered `AnyShape` with "value of type 'AnyShape' has no member
/// 'strokeBorder'".
private struct GsGlassModifier: ViewModifier {
    let role: GsGlassRole
    let shape: AnyInsettableShape

    func body(content: Content) -> some View {
        content
            .background(glassShape)
            // A GeometryReader in the overlay, not `overlay { proxy in … }`:
            // `overlay(content:)` has BOTH a zero-parameter and a
            // GeometryProxy-parameter overload, and the labelled closure picks
            // the zero-parameter one — the CI compiler answered the second with
            // "contextual closure type '() -> some View' expects 0 arguments".
            // The size is needed because the rim's lean is a fraction of the
            // card's height, and only the drawn card knows its height.
            .overlay {
                GeometryReader { geometry in
                    rim(in: geometry.size)
                }
            }
            // Cue: warm, non-grey shadow — this is depth, not darkness. Cast
            // from outside the glass rather than through it; see `castShadow`.
            .background {
                if role.elevation > 0 {
                    castShadow
                }
            }
    }

    /// The card's shadow, with the card's own footprint punched out of it.
    ///
    /// SwiftUI's `.shadow` paints the blur behind whatever casts it, and a glass
    /// at 45% coverage is see-through: the card's own shadow came back up through
    /// it and took 7.9 L out of the interior. That 7.9 L is not a coincidence —
    /// it is the size of the shadow the sheet puts on the PAGE below its card
    /// (-7.2 L measured), while the sheet's glass interior is a +2.8 L LIFT over
    /// its ground. A real card's shadow falls on the table, not on the inside of
    /// the glass, so the silhouette is erased from the layer that casts it.
    ///
    /// `.offset` is applied before the punch because it does not move the layout
    /// frame: the erased footprint therefore lands where the card is, not where
    /// its shadow is, which is the whole point.
    private var castShadow: some View {
        shape
            .fill(Color(red: 0.35, green: 0.16, blue: 0.10))
            .opacity(0.14)
            .blur(radius: role.elevation * 0.6)
            .offset(y: role.elevation * 0.28)
            .overlay(shape.fill(Color.black).blendMode(.destinationOut))
            .compositingGroup()
    }

    /// The backdrop blur, or nothing where the role has no material.
    ///
    /// `cardLight` and `cardDim` have none, and the reason is in their
    /// documentation. An AnyShapeStyle keeps this one expression: a conditional
    /// view here would fork the modifier chain the rim's gradient depends on.
    private var baseStyle: AnyShapeStyle {
        if let material = role.material {
            return AnyShapeStyle(material)
        }
        return AnyShapeStyle(Color.clear)
    }

    /// Material + interior luminance fall + top airlight, all clipped to shape.
    private var glassShape: some View {
        shape
            .fill(baseStyle)
            .overlay(
                // 1 + 4. tinted fill, lighter at the top, deeper at the bottom.
                shape.fill(
                    LinearGradient(
                        colors: [
                            role.tintColor.opacity(role.tintAlphaTop),
                            role.tintColor.opacity(role.tintAlphaBottom)
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )
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
        return shape
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

extension View {
    /// Apply the shared glass material as a rounded-rect background. The iOS
    /// twin of Android `Modifier.gsGlass(role, shape)`.
    func gsGlass(_ role: GsGlassRole = .card, corner: CGFloat = Aero.Radius.card) -> some View {
        modifier(GsGlassModifier(
            role: role,
            shape: AnyInsettableShape(RoundedRectangle(cornerRadius: corner, style: .continuous))))
    }

    /// The pill and composer controls: the same material, cut as a capsule.
    ///
    /// This used to be a second recipe of its own. It is one call now because a
    /// duplicate is how the drift happened: the capsule version had no veiling
    /// layer, no height-proportional rim lean, and it cast its shadow with
    /// `.shadow` straight onto the modified view, so the capsule's own silhouette
    /// came back through a translucent fill and took 5.1 L out of the pill. The
    /// gate measures that (A2 material law on the `pill` probe), and Android's
    /// pills — which always went through the single path — did not have it.
    func gsCapsuleGlass(_ role: GsGlassRole = .floatingControl) -> some View {
        modifier(GsGlassModifier(role: role, shape: AnyInsettableShape(Capsule())))
    }
}
