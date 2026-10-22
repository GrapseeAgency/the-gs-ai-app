import SwiftUI

/**
 * THE GLASS — the iOS twin of `android/.../ui/components/GlassMaterial.kt`.
 *
 * One material, five semantic roles, four optical cues painted in painterly
 * order. A screen asks for a role; it never picks an alpha. This mirrors the
 * Android contract exactly so a surface reads identically on both platforms
 * (mandate b10), with the platform's real translucency doing the part only the
 * OS can do:
 *
 *  1. a translucent fill — Apple's `Material` (a genuine backdrop blur on the
 *     compositor), standing in for the analytic fill Android draws;
 *  2. in-scattered airlight — a top-down veil wash that lifts the black floor
 *     where the light enters and fades downward ([GsGlassRole.veilingAlpha]);
 *  3. a directional rim — the key light sits above and to the left in every
 *     reference frame, so the top-leading edge is bright and the trailing edge
 *     falls. A uniform hairline erases the strongest depth cue; this is the fix;
 *  4. interior luminance fall — the panel is lighter at the top than the bottom.
 *
 * Warm, non-grey shadow carries the depth. The whole thing is stock SwiftUI
 * `Shape`/`LinearGradient`/`Material`/`shadow` — available since the app's iOS 16
 * floor, nothing experimental, so it compiles wherever the app does.
 */
enum GsGlassRole {
    case card
    /// The reference sheet's own card glass, measured rather than eyeballed.
    ///
    /// `.card`'s fill is `Aero.surface` (luminance ~238) laid over
    /// `.regularMaterial`, and the material floor drags the composite down to
    /// ~235 — 7.1 L below the page. The sheet's card sits only 2.79 L below its
    /// page, so on the Home reference `.card` reads visibly too dark. This role
    /// is additive: every existing `.card` call site keeps its current look and
    /// only the surfaces that were authored against the sheet opt in.
    case cardLight
    case elevated
    case floatingControl
    case modal
    case sunken

    /// The colour the fill is painted with. `.cardLight` uses a lighter warm
    /// white because `Aero.surface` cannot reach the measured target however
    /// its alpha is set — the material underneath is already darker than the
    /// surface itself, so raising alpha moves the composite the wrong way.
    var tintColor: Color {
        switch self {
        case .cardLight: return Color(red: 0.984, green: 0.961, blue: 0.941)
        default:         return Aero.surface
        }
    }

    /// Top / bottom of the interior luminance fall, per role.
    var tintAlphaTop: Double {
        switch self {
        case .cardLight: return 0.62
        default:         return fillAlpha * 0.55
        }
    }
    var tintAlphaBottom: Double {
        switch self {
        case .cardLight: return 0.40
        default:         return fillAlpha * 0.28
        }
    }

    /// How opaque the tinted fill sits over the material. Thicker glass scatters more.
    var fillAlpha: Double {
        switch self {
        case .card: return 1
        case .cardLight: return 1
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
        case .cardLight: return 0.05
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
        case .cardLight: return 0.55
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
        case .cardLight: return 10
        case .elevated: return 16
        case .floatingControl: return 20
        case .modal: return 28
        case .sunken: return 0
        }
    }

    /// The translucency the material provides. Glass seen from the shadowed
    /// side (sunken) uses the thinnest frost; the heaviest glass (modal) the
    /// thickest, so a sheet reads as a slab over the transcript.
    var material: Material {
        switch self {
        case .card, .cardLight, .elevated: return .regularMaterial
        case .floatingControl: return .thinMaterial
        case .modal: return .thickMaterial
        case .sunken: return .ultraThinMaterial
        }
    }
}

/// The reference glass as a background + rim + shadow on any rounded surface.
private struct GsGlassModifier: ViewModifier {
    let role: GsGlassRole
    let corner: CGFloat

    func body(content: Content) -> some View {
        content
            .background(glassShape)
            .overlay(rim)
            // Cue: warm, non-grey shadow — this is depth, not darkness.
            .shadow(
                color: Color(red: 0.35, green: 0.16, blue: 0.10).opacity(role.elevation > 0 ? 0.14 : 0),
                radius: role.elevation * 0.6,
                x: 0,
                y: role.elevation * 0.28
            )
    }

    /// Material + interior luminance fall + top airlight, all clipped to shape.
    private var glassShape: some View {
        RoundedRectangle(cornerRadius: corner, style: .continuous)
            .fill(role.material)
            .overlay(
                // 1 + 4. tinted fill, lighter at the top, deeper at the bottom.
                RoundedRectangle(cornerRadius: corner, style: .continuous)
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
            )
            .overlay(
                // 2. in-scatter — a broad soft airlight wash over the top third.
                RoundedRectangle(cornerRadius: corner, style: .continuous)
                    .fill(
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

    /// 3. Directional rim — top-leading catches the light, trailing falls away.
    private var rim: some View {
        RoundedRectangle(cornerRadius: corner, style: .continuous)
            .strokeBorder(
                LinearGradient(
                    colors: [
                        Aero.elevatedSurface.opacity(0.9 * role.rimStrength),
                        Aero.outline.opacity(0.55),
                        Aero.divider.opacity(0.25)
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
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
            .background(Capsule().fill(role.material))
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
            .overlay(
                Capsule().strokeBorder(
                    LinearGradient(
                        colors: [
                            Aero.elevatedSurface.opacity(0.9 * role.rimStrength),
                            Aero.outline.opacity(0.55),
                            Aero.divider.opacity(0.25)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
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
