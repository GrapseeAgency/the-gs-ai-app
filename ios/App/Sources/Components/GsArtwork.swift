import SwiftUI

/// GS ARTWORK — the hand-built illustration set, drawn from primitives so it
/// shares one light source (upper left), one material (peach glass with a
/// white edge) and one motif (the five-petal bloom) with the Android set.
/// Nothing here is a bitmap or a stock glyph: every mark is geometry, which is
/// what lets the set hold up at 34pt in a chip and at 148pt on the welcome.
enum GsArt {
    case bloom, conversation, search, offline, error, library, voice
    case success, privacy, archive, notifications
    case video, clarity, reflection, lotus
}

struct GsArtwork: View {
    var art: GsArt
    var size: CGFloat = 120
    var label: String = "GS illustration"

    var body: some View {
        Canvas { context, canvasSize in
            GsArtDrawing.draw(art, &context, canvasSize)
        }
        .frame(width: size, height: size)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        .accessibilityAddTraits(.isImage)
    }
}

// MARK: - Drawing

enum GsArtDrawing {

    static func draw(_ art: GsArt, _ ctx: inout GraphicsContext, _ size: CGSize) {
        let accent = Aero.accent
        let strong = Aero.accentStrong
        let pale = Aero.aurora.first ?? Aero.accent
        let ink = Aero.text
        switch art {
        case .bloom: bloom(&ctx, size, accent, strong, pale)
        case .conversation: conversation(&ctx, size, accent, strong, pale)
        case .search: search(&ctx, size, accent, strong, pale)
        case .offline: offline(&ctx, size, accent, strong, pale)
        case .error: error(&ctx, size, Aero.danger, accent, pale)
        case .library: library(&ctx, size, accent, strong, pale)
        case .voice: voice(&ctx, size, accent, strong, pale)
        case .success: success(&ctx, size, accent, strong, pale)
        case .privacy: privacy(&ctx, size, accent, strong, pale)
        case .archive: archive(&ctx, size, accent, strong, pale, ink)
        case .notifications: notifications(&ctx, size, accent, strong, pale)
        case .video: video(&ctx, size, accent, strong, pale)
        case .clarity: clarity(&ctx, size, accent, strong, pale, ink)
        case .reflection: reflection(&ctx, size, accent, strong, pale)
        case .lotus: lotus(&ctx, size, accent, strong, pale)
        }
    }

    // MARK: Primitives

    private static func rad(_ degrees: Double) -> Double {
        return degrees * Double.pi / 180
    }

    private static func petalPath(center: CGPoint, angle: Double, length: CGFloat, width: CGFloat) -> Path {
        var path = Path()
        path.move(to: .zero)
        path.addCurve(
            to: CGPoint(x: 0, y: -length),
            control1: CGPoint(x: -width, y: -length * 0.30),
            control2: CGPoint(x: -width * 0.55, y: -length))
        path.addCurve(
            to: .zero,
            control1: CGPoint(x: width * 0.55, y: -length),
            control2: CGPoint(x: width, y: -length * 0.30))
        path.closeSubpath()
        let transform = CGAffineTransform(translationX: center.x, y: center.y)
            .rotated(by: CGFloat(rad(angle)))
        return path.applying(transform)
    }

    private static func petalTip(center: CGPoint, angle: Double, length: CGFloat) -> CGPoint {
        return CGPoint(
            x: center.x + CGFloat(sin(rad(angle))) * length,
            y: center.y - CGFloat(cos(rad(angle))) * length)
    }

    private static func petal(
        _ ctx: inout GraphicsContext,
        center: CGPoint,
        angle: Double,
        length: CGFloat,
        width: CGFloat,
        top: Color,
        bottom: Color
    ) {
        guard length > 0.5 else { return }
        let path = petalPath(center: center, angle: angle, length: length, width: width)
        let tip = petalTip(center: center, angle: angle, length: length)
        ctx.fill(
            path,
            with: .linearGradient(
                Gradient(colors: [top, bottom]),
                startPoint: center,
                endPoint: tip))
        ctx.stroke(path, with: .color(Color.white.opacity(0.22)), lineWidth: 1)
    }

    private static func burst(
        _ ctx: inout GraphicsContext,
        center: CGPoint,
        radius: CGFloat,
        light: Color,
        deep: Color,
        petals: Int,
        tilt: Double
    ) {
        for index in 0..<petals {
            petal(
                &ctx,
                center: center,
                angle: tilt + Double(index) * (360.0 / Double(petals)),
                length: radius,
                width: radius * 0.36,
                top: light,
                bottom: deep)
        }
    }

    private static func crescent(_ ctx: inout GraphicsContext, center: CGPoint, radius: CGFloat, color: Color) {
        var path = Path()
        path.addArc(
            center: center,
            radius: radius * 0.78,
            startAngle: .degrees(42),
            endAngle: .degrees(318),
            clockwise: false)
        ctx.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: radius * 0.46, lineCap: .round))
    }

    private static func sparkle(_ ctx: inout GraphicsContext, center: CGPoint, radius: CGFloat, color: Color) {
        guard radius >= 0.5 else { return }
        var path = Path()
        path.move(to: CGPoint(x: center.x, y: center.y - radius))
        path.addCurve(
            to: CGPoint(x: center.x + radius, y: center.y),
            control1: CGPoint(x: center.x + radius * 0.16, y: center.y - radius * 0.20),
            control2: CGPoint(x: center.x + radius * 0.20, y: center.y - radius * 0.16))
        path.addCurve(
            to: CGPoint(x: center.x, y: center.y + radius),
            control1: CGPoint(x: center.x + radius * 0.20, y: center.y + radius * 0.16),
            control2: CGPoint(x: center.x + radius * 0.16, y: center.y + radius * 0.20))
        path.addCurve(
            to: CGPoint(x: center.x - radius, y: center.y),
            control1: CGPoint(x: center.x - radius * 0.16, y: center.y + radius * 0.20),
            control2: CGPoint(x: center.x - radius * 0.20, y: center.y + radius * 0.16))
        path.addCurve(
            to: CGPoint(x: center.x, y: center.y - radius),
            control1: CGPoint(x: center.x - radius * 0.20, y: center.y - radius * 0.16),
            control2: CGPoint(x: center.x - radius * 0.16, y: center.y - radius * 0.20))
        path.closeSubpath()
        ctx.fill(path, with: .color(color))
    }

    private static func rotated(_ path: Path, degrees: Double, pivot: CGPoint) -> Path {
        let transform = CGAffineTransform(translationX: pivot.x, y: pivot.y)
            .rotated(by: CGFloat(rad(degrees)))
            .translatedBy(x: -pivot.x, y: -pivot.y)
        return path.applying(transform)
    }

    private static func roundRect(_ rect: CGRect, radius: CGFloat) -> Path {
        return Path(roundedRect: rect, cornerRadius: radius)
    }

    private static func oval(_ ctx: inout GraphicsContext, rect: CGRect, colors: [Color]) {
        let path = Path(ellipseIn: rect)
        ctx.fill(
            path,
            with: .radialGradient(
                Gradient(colors: colors),
                center: CGPoint(x: rect.midX, y: rect.midY),
                startRadius: 0,
                endRadius: max(rect.width, rect.height) * 0.5))
    }

    /// A glass bubble: body gradient, upper-left specular, fading rim.
    private static func bubble(_ ctx: inout GraphicsContext, center: CGPoint, radius: CGFloat, tint: Color) {
        ctx.fill(
            Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)),
            with: .radialGradient(
                Gradient(colors: [
                    Color.white.opacity(0.35),
                    tint.opacity(0.12),
                    Color.clear
                ]),
                center: CGPoint(x: center.x - radius * 0.30, y: center.y - radius * 0.34),
                startRadius: 0,
                endRadius: radius * 1.25))
        var rim = Path()
        rim.addArc(
            center: center,
            radius: radius,
            startAngle: .degrees(150),
            endAngle: .degrees(350),
            clockwise: false)
        ctx.stroke(
            rim,
            with: .color(Color.white.opacity(0.65)),
            style: StrokeStyle(lineWidth: max(1, radius * 0.10), lineCap: .round))
        ctx.fill(
            Path(ellipseIn: CGRect(
                x: center.x - radius * 0.50,
                y: center.y - radius * 0.56,
                width: radius * 0.32,
                height: radius * 0.32)),
            with: .color(Color.white.opacity(0.80)))
    }

    // MARK: Scenes

    private static func bloom(
        _ ctx: inout GraphicsContext,
        _ size: CGSize,
        _ accent: Color,
        _ strong: Color,
        _ pale: Color
    ) {
        let w = size.width
        let h = size.height
        let c = CGPoint(x: w / 2, y: h * 0.52)
        let r = min(w, h) * 0.24

        oval(&ctx, rect: CGRect(x: c.x - r * 1.5, y: c.y + r * 1.9, width: r * 3, height: r * 0.9), colors: [
            Color.black.opacity(0.13), Color.clear
        ])
        for index in 0..<9 {
            let arc = Double(index) * 40 - 90
            let length = index % 2 == 0 ? r * 1.85 : r * 1.48
            petal(
                &ctx,
                center: c,
                angle: arc,
                length: length,
                width: r * 0.55,
                top: pale.opacity(0.85),
                bottom: accent.opacity(0.55))
        }
        ctx.fill(
            Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)),
            with: .radialGradient(
                Gradient(colors: [Color.white.opacity(0.9), pale, accent, strong]),
                center: CGPoint(x: c.x - r * 0.35, y: c.y - r * 0.45),
                startRadius: 0,
                endRadius: r * 1.55))
        ctx.stroke(
            Path(ellipseIn: CGRect(x: c.x - r * 0.92, y: c.y - r * 0.92, width: r * 1.84, height: r * 1.84)),
            with: .color(Color.white.opacity(0.5)),
            lineWidth: 1.2)
        ctx.fill(
            Path(ellipseIn: CGRect(x: c.x - r * 0.58, y: c.y - r * 0.66, width: r * 0.62, height: r * 0.34)),
            with: .color(Color.white.opacity(0.65)))
        ctx.fill(
            Path(ellipseIn: CGRect(x: c.x + r * 0.20, y: c.y + r * 0.24, width: r * 0.20, height: r * 0.20)),
            with: .color(strong.opacity(0.35)))
        var orbit = Path()
        orbit.addArc(
            center: c,
            radius: r * 1.55,
            startAngle: .degrees(200),
            endAngle: .degrees(450),
            clockwise: false)
        ctx.stroke(orbit, with: .color(accent.opacity(0.45)), style: StrokeStyle(lineWidth: 1.4, lineCap: .round))
        ctx.fill(
            Path(ellipseIn: CGRect(x: c.x + r * 1.33, y: c.y - r * 0.51, width: r * 0.18, height: r * 0.18)),
            with: .color(strong))
        for index in 0..<3 {
            let radians = rad(Double(index) * 120 + 25)
            let px = c.x + CGFloat(cos(radians)) * r * 1.85
            let py = c.y + CGFloat(sin(radians)) * r * 1.55
            ctx.fill(
                Path(ellipseIn: CGRect(x: px - r * 0.045, y: py - r * 0.045, width: r * 0.09, height: r * 0.09)),
                with: .color(Color.white.opacity(0.75)))
        }
    }

    private static func conversation(
        _ ctx: inout GraphicsContext,
        _ size: CGSize,
        _ accent: Color,
        _ strong: Color,
        _ pale: Color
    ) {
        let w = size.width
        let h = size.height
        var back = roundRect(
            CGRect(x: w * 0.10, y: h * 0.24, width: w * 0.56, height: h * 0.38),
            radius: w * 0.12)
        back.move(to: CGPoint(x: w * 0.22, y: h * 0.60))
        back.addLine(to: CGPoint(x: w * 0.15, y: h * 0.71))
        back.addLine(to: CGPoint(x: w * 0.34, y: h * 0.62))
        back.closeSubpath()
        ctx.fill(back, with: .color(Aero.surface.opacity(0.75)))
        ctx.stroke(back, with: .color(Aero.outline), lineWidth: 1)
        for index in 0..<3 {
            let bar = roundRect(
                CGRect(
                    x: w * 0.18,
                    y: h * (0.33 + Double(index) * 0.08),
                    width: w * (0.34 - Double(index) * 0.07),
                    height: h * 0.035),
                radius: h * 0.02)
            ctx.fill(bar, with: .color(accent.opacity(0.28)))
        }
        var front = roundRect(
            CGRect(x: w * 0.36, y: h * 0.42, width: w * 0.56, height: h * 0.36),
            radius: w * 0.12)
        front.move(to: CGPoint(x: w * 0.78, y: h * 0.77))
        front.addLine(to: CGPoint(x: w * 0.86, y: h * 0.86))
        front.addLine(to: CGPoint(x: w * 0.68, y: h * 0.78))
        front.closeSubpath()
        ctx.fill(
            front,
            with: .linearGradient(
                Gradient(colors: [accent, strong]),
                startPoint: CGPoint(x: w * 0.36, y: h * 0.42),
                endPoint: CGPoint(x: w * 0.92, y: h * 0.78)))
        for index in 0..<2 {
            let bar = roundRect(
                CGRect(
                    x: w * 0.45,
                    y: h * (0.52 + Double(index) * 0.09),
                    width: w * (0.34 - Double(index) * 0.10),
                    height: h * 0.035),
                radius: h * 0.02)
            ctx.fill(bar, with: .color(Color.white.opacity(0.72)))
        }
        burst(
            &ctx,
            center: CGPoint(x: w * 0.70, y: h * 0.30),
            radius: w * 0.085,
            light: Color.white.opacity(0.92),
            deep: pale,
            petals: 5,
            tilt: 12)
        ctx.fill(
            Path(ellipseIn: CGRect(x: w * 0.70 - w * 0.012, y: h * 0.30 - w * 0.012, width: w * 0.024, height: w * 0.024)),
            with: .color(Color.white.opacity(0.7)))
    }

    private static func search(
        _ ctx: inout GraphicsContext,
        _ size: CGSize,
        _ accent: Color,
        _ strong: Color,
        _ pale: Color
    ) {
        let w = size.width
        let h = size.height
        let lens = CGPoint(x: w * 0.46, y: h * 0.44)
        let r = w * 0.26
        ctx.fill(
            Path(ellipseIn: CGRect(x: lens.x - r, y: lens.y - r, width: r * 2, height: r * 2)),
            with: .radialGradient(
                Gradient(colors: [Color.white.opacity(0.85), pale.opacity(0.55), accent.opacity(0.12)]),
                center: CGPoint(x: lens.x - r * 0.3, y: lens.y - r * 0.35),
                startRadius: 0,
                endRadius: r * 1.4))
        ctx.stroke(
            Path(ellipseIn: CGRect(x: lens.x - r, y: lens.y - r, width: r * 2, height: r * 2)),
            with: .color(Color.white.opacity(0.6)),
            lineWidth: 2)
        ctx.stroke(
            Path(ellipseIn: CGRect(x: lens.x - r * 0.94, y: lens.y - r * 0.94, width: r * 1.88, height: r * 1.88)),
            with: .color(accent.opacity(0.5)),
            lineWidth: 1)
        var handle = Path()
        handle.move(to: CGPoint(x: lens.x + r * 0.72, y: lens.y + r * 0.72))
        handle.addLine(to: CGPoint(x: lens.x + r * 1.42, y: lens.y + r * 1.40))
        ctx.stroke(handle, with: .color(strong), style: StrokeStyle(lineWidth: w * 0.055, lineCap: .round))
        var glint = Path()
        glint.move(to: CGPoint(x: lens.x + r * 0.78, y: lens.y + r * 0.62))
        glint.addLine(to: CGPoint(x: lens.x + r * 1.25, y: lens.y + r * 1.18))
        ctx.stroke(
            glint,
            with: .color(Color.white.opacity(0.4)),
            style: StrokeStyle(lineWidth: w * 0.016, lineCap: .round))
        burst(
            &ctx,
            center: CGPoint(x: lens.x - r * 0.12, y: lens.y - r * 0.05),
            radius: r * 0.52,
            light: Color.white.opacity(0.98),
            deep: accent,
            petals: 5,
            tilt: 22)
        ctx.fill(
            Path(ellipseIn: CGRect(
                x: lens.x - r * 0.12 - r * 0.10,
                y: lens.y - r * 0.05 - r * 0.10,
                width: r * 0.20,
                height: r * 0.20)),
            with: .color(strong))
        sparkle(&ctx, center: CGPoint(x: w * 0.78, y: h * 0.20), radius: w * 0.035, color: Color.white.opacity(0.9))
        sparkle(&ctx, center: CGPoint(x: w * 0.20, y: h * 0.78), radius: w * 0.022, color: accent.opacity(0.55))
    }

    private static func offline(
        _ ctx: inout GraphicsContext,
        _ size: CGSize,
        _ accent: Color,
        _ strong: Color,
        _ pale: Color
    ) {
        let w = size.width
        let h = size.height
        ctx.fill(
            Path(ellipseIn: CGRect(x: w * 0.70 - w * 0.22, y: h * 0.30 - w * 0.22, width: w * 0.44, height: w * 0.44)),
            with: .radialGradient(
                Gradient(colors: [pale.opacity(0.9), pale.opacity(0)]),
                center: CGPoint(x: w * 0.70, y: h * 0.30),
                startRadius: 0,
                endRadius: w * 0.22))
        crescent(&ctx, center: CGPoint(x: w * 0.72, y: h * 0.29), radius: w * 0.115, color: Color.white.opacity(0.85))
        let body = roundRect(
            CGRect(x: w * 0.16, y: h * 0.48, width: w * 0.68, height: h * 0.16),
            radius: w * 0.10)
        ctx.fill(body, with: .color(pale.opacity(0.9)))
        let lobes: [(CGFloat, CGFloat, CGFloat)] = [
            (w * 0.34, h * 0.47, w * 0.14),
            (w * 0.52, h * 0.40, w * 0.18),
            (w * 0.68, h * 0.49, w * 0.12)
        ]
        for lobe in lobes {
            ctx.fill(
                Path(ellipseIn: CGRect(
                    x: lobe.0 - lobe.2,
                    y: lobe.1 - lobe.2,
                    width: lobe.2 * 2,
                    height: lobe.2 * 2)),
                with: .color(pale.opacity(0.92)))
        }
        ctx.fill(
            Path(ellipseIn: CGRect(x: w * 0.48 - w * 0.10, y: h * 0.37 - w * 0.10, width: w * 0.20, height: w * 0.20)),
            with: .color(Color.white.opacity(0.35)))
        // A z that drifts up, drawn as a stroke so it reads at any size.
        var z = Path()
        z.move(to: CGPoint(x: w * 0.80, y: h * 0.16))
        z.addLine(to: CGPoint(x: w * 0.845, y: h * 0.16))
        z.addLine(to: CGPoint(x: w * 0.80, y: h * 0.205))
        z.addLine(to: CGPoint(x: w * 0.85, y: h * 0.205))
        ctx.stroke(z, with: .color(strong.opacity(0.65)), style: StrokeStyle(lineWidth: w * 0.010, lineCap: .round))
        petal(
            &ctx,
            center: CGPoint(x: w * 0.30, y: h * 0.80),
            angle: 38,
            length: w * 0.10,
            width: w * 0.04,
            top: Color.white.opacity(0.9),
            bottom: accent.opacity(0.6))
    }

    private static func error(
        _ ctx: inout GraphicsContext,
        _ size: CGSize,
        _ errorColor: Color,
        _ accent: Color,
        _ pale: Color
    ) {
        let w = size.width
        let h = size.height
        let c = CGPoint(x: w * 0.50, y: h * 0.50)
        let r = w * 0.30
        ctx.fill(
            Path(ellipseIn: CGRect(x: c.x - r * 1.8, y: c.y - r * 1.8, width: r * 3.6, height: r * 3.6)),
            with: .radialGradient(
                Gradient(colors: [errorColor.opacity(0.22), Color.clear]),
                center: c,
                startRadius: 0,
                endRadius: r * 1.8))
        var shield = Path()
        shield.move(to: CGPoint(x: c.x, y: c.y - r))
        shield.addCurve(
            to: CGPoint(x: c.x, y: c.y + r),
            control1: CGPoint(x: c.x + r * 1.05, y: c.y - r * 0.55),
            control2: CGPoint(x: c.x + r * 0.65, y: c.y + r * 0.65))
        shield.addCurve(
            to: CGPoint(x: c.x, y: c.y - r),
            control1: CGPoint(x: c.x - r * 0.65, y: c.y + r * 0.65),
            control2: CGPoint(x: c.x - r * 1.05, y: c.y - r * 0.55))
        shield.closeSubpath()
        ctx.fill(
            shield,
            with: .linearGradient(
                Gradient(colors: [Color.white.opacity(0.9), pale.opacity(0.65)]),
                startPoint: CGPoint(x: c.x, y: c.y - r),
                endPoint: CGPoint(x: c.x, y: c.y + r)))
        ctx.stroke(shield, with: .color(errorColor.opacity(0.55)), lineWidth: 1.6)
        var crack = Path()
        crack.move(to: CGPoint(x: c.x - r * 0.30, y: c.y - r * 0.55))
        crack.addLine(to: CGPoint(x: c.x - r * 0.10, y: c.y - r * 0.18))
        crack.addLine(to: CGPoint(x: c.x + r * 0.16, y: c.y - r * 0.30))
        crack.addLine(to: CGPoint(x: c.x + r * 0.02, y: c.y + r * 0.06))
        crack.addLine(to: CGPoint(x: c.x + r * 0.28, y: c.y + r * 0.18))
        ctx.stroke(crack, with: .color(accent.opacity(0.75)), style: StrokeStyle(lineWidth: w * 0.018, lineCap: .round))
        ctx.fill(
            Path(ellipseIn: CGRect(x: c.x - r * 0.18, y: c.y - r * 0.18, width: r * 0.36, height: r * 0.36)),
            with: .color(accent))
        ctx.fill(
            Path(ellipseIn: CGRect(x: c.x - r * 0.12, y: c.y - r * 0.12, width: r * 0.12, height: r * 0.12)),
            with: .color(Color.white.opacity(0.8)))
        sparkle(&ctx, center: CGPoint(x: w * 0.78, y: h * 0.26), radius: w * 0.03, color: accent.opacity(0.55))
    }

    private static func library(
        _ ctx: inout GraphicsContext,
        _ size: CGSize,
        _ accent: Color,
        _ strong: Color,
        _ pale: Color
    ) {
        let w = size.width
        let h = size.height
        let cardW = w * 0.56
        let cardH = h * 0.36
        for index in 0..<3 {
            let dx = CGFloat(index - 1) * w * 0.045
            let dy = CGFloat(1 - index) * h * 0.055
            let rect = CGRect(x: w * 0.22 + dx, y: h * 0.38 + dy, width: cardW, height: cardH)
            let path = rotated(roundRect(rect, radius: w * 0.06), degrees: Double(index - 1) * 6, pivot: CGPoint(x: w * 0.5, y: h * 0.55))
            ctx.fill(path, with: .color(Color.white.opacity(0.52 + Double(index) * 0.10)))
            ctx.stroke(path, with: .color(pale.opacity(0.5)), lineWidth: 1)
        }
        ctx.fill(
            roundRect(CGRect(x: w * 0.30, y: h * 0.40, width: w * 0.16, height: h * 0.035), radius: h * 0.02),
            with: .color(accent.opacity(0.55)))
        for index in 0..<2 {
            ctx.fill(
                roundRect(
                    CGRect(
                        x: w * 0.30,
                        y: h * (0.47 + Double(index) * 0.06),
                        width: w * (0.32 - Double(index) * 0.08),
                        height: h * 0.022),
                    radius: h * 0.012),
                with: .color(strong.opacity(0.28)))
        }
        burst(
            &ctx,
            center: CGPoint(x: w * 0.66, y: h * 0.66),
            radius: w * 0.09,
            light: Color.white.opacity(0.95),
            deep: accent,
            petals: 5,
            tilt: 8)
        ctx.fill(
            Path(ellipseIn: CGRect(x: w * 0.66 - w * 0.018, y: h * 0.66 - w * 0.018, width: w * 0.036, height: w * 0.036)),
            with: .color(strong))
    }

    private static func voice(
        _ ctx: inout GraphicsContext,
        _ size: CGSize,
        _ accent: Color,
        _ strong: Color,
        _ pale: Color
    ) {
        let w = size.width
        let h = size.height
        let c = CGPoint(x: w * 0.50, y: h * 0.52)
        let r = w * 0.30
        ctx.fill(
            Path(ellipseIn: CGRect(x: c.x - r * 1.6, y: c.y - r * 1.6, width: r * 3.2, height: r * 3.2)),
            with: .radialGradient(
                Gradient(colors: [pale.opacity(0.75), Color.clear]),
                center: c,
                startRadius: 0,
                endRadius: r * 1.6))
        ctx.stroke(
            Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)),
            with: .color(Color.white.opacity(0.55)),
            lineWidth: 1.4)
        var arc = Path()
        arc.addArc(
            center: c,
            radius: r * 1.22,
            startAngle: .degrees(-40),
            endAngle: .degrees(210),
            clockwise: false)
        ctx.stroke(arc, with: .color(accent.opacity(0.55)), style: StrokeStyle(lineWidth: 2, lineCap: .round))
        let heights: [CGFloat] = [0.32, 0.62, 0.92, 0.54, 0.38]
        for (index, factor) in heights.enumerated() {
            let x = c.x + CGFloat(index - 2) * r * 0.26
            let barHeight = r * factor
            let rect = CGRect(x: x - r * 0.055, y: c.y - barHeight / 2, width: r * 0.11, height: barHeight)
            ctx.fill(
                roundRect(rect, radius: r * 0.055),
                with: .linearGradient(
                    Gradient(colors: [strong, accent]),
                    startPoint: CGPoint(x: x, y: c.y - barHeight / 2),
                    endPoint: CGPoint(x: x, y: c.y + barHeight / 2)))
        }
        sparkle(&ctx, center: CGPoint(x: w * 0.82, y: h * 0.24), radius: w * 0.030, color: Color.white.opacity(0.85))
        sparkle(&ctx, center: CGPoint(x: w * 0.18, y: h * 0.76), radius: w * 0.022, color: accent.opacity(0.50))
    }

    private static func success(
        _ ctx: inout GraphicsContext,
        _ size: CGSize,
        _ accent: Color,
        _ strong: Color,
        _ pale: Color
    ) {
        let w = size.width
        let h = size.height
        let c = CGPoint(x: w * 0.50, y: h * 0.50)
        let r = w * 0.30
        ctx.stroke(
            Path(ellipseIn: CGRect(x: c.x - r * 1.24, y: c.y - r * 1.24, width: r * 2.48, height: r * 2.48)),
            with: .color(accent.opacity(0.30)),
            lineWidth: 1.4)
        burst(
            &ctx,
            center: c,
            radius: r * 0.86,
            light: Color.white.opacity(0.90),
            deep: accent.opacity(0.50),
            petals: 8,
            tilt: -90)
        ctx.fill(
            Path(ellipseIn: CGRect(x: c.x - r * 0.46, y: c.y - r * 0.46, width: r * 0.92, height: r * 0.92)),
            with: .radialGradient(
                Gradient(colors: [Color.white, pale, accent.opacity(0.55)]),
                center: CGPoint(x: c.x - r * 0.14, y: c.y - r * 0.18),
                startRadius: 0,
                endRadius: r * 0.70))
        var check = Path()
        check.move(to: CGPoint(x: c.x - r * 0.24, y: c.y + r * 0.02))
        check.addLine(to: CGPoint(x: c.x - r * 0.05, y: c.y + r * 0.22))
        check.addLine(to: CGPoint(x: c.x + r * 0.28, y: c.y - r * 0.20))
        ctx.stroke(
            check,
            with: .color(strong),
            style: StrokeStyle(lineWidth: r * 0.14, lineCap: .round, lineJoin: .round))
        sparkle(&ctx, center: CGPoint(x: w * 0.80, y: h * 0.26), radius: w * 0.032, color: Color.white.opacity(0.85))
    }

    private static func privacy(
        _ ctx: inout GraphicsContext,
        _ size: CGSize,
        _ accent: Color,
        _ strong: Color,
        _ pale: Color
    ) {
        let w = size.width
        let h = size.height
        let c = CGPoint(x: w * 0.50, y: h * 0.48)
        let r = w * 0.27
        oval(&ctx, rect: CGRect(x: c.x - r * 1.05, y: c.y + r * 1.42, width: r * 2.10, height: r * 0.50), colors: [
            Color.black.opacity(0.10), Color.clear
        ])
        var shield = Path()
        shield.move(to: CGPoint(x: c.x, y: c.y - r * 1.30))
        shield.addCurve(
            to: CGPoint(x: c.x, y: c.y + r * 1.30),
            control1: CGPoint(x: c.x + r * 1.24, y: c.y - r * 0.78),
            control2: CGPoint(x: c.x + r * 0.80, y: c.y + r * 0.86))
        shield.addCurve(
            to: CGPoint(x: c.x, y: c.y - r * 1.30),
            control1: CGPoint(x: c.x - r * 0.80, y: c.y + r * 0.86),
            control2: CGPoint(x: c.x - r * 1.24, y: c.y - r * 0.78))
        shield.closeSubpath()
        ctx.fill(
            shield,
            with: .linearGradient(
                Gradient(colors: [Color.white.opacity(0.94), pale.opacity(0.72), accent.opacity(0.42)]),
                startPoint: CGPoint(x: c.x, y: c.y - r * 1.30),
                endPoint: CGPoint(x: c.x, y: c.y + r * 1.30)))
        ctx.stroke(shield, with: .color(Color.white.opacity(0.65)), lineWidth: 1.4)
        // Keyhole: privacy is a lock the flower grew around.
        ctx.fill(
            Path(ellipseIn: CGRect(x: c.x - r * 0.20, y: c.y - r * 0.42, width: r * 0.40, height: r * 0.40)),
            with: .color(strong.opacity(0.80)))
        ctx.fill(
            roundRect(CGRect(x: c.x - r * 0.07, y: c.y - r * 0.10, width: r * 0.14, height: r * 0.52), radius: r * 0.07),
            with: .color(strong.opacity(0.80)))
        ctx.fill(
            Path(ellipseIn: CGRect(x: c.x - r * 0.62, y: c.y - r * 0.92, width: r * 0.34, height: r * 0.50)),
            with: .color(Color.white.opacity(0.45)))
        petal(
            &ctx,
            center: CGPoint(x: w * 0.24, y: h * 0.80),
            angle: -42,
            length: w * 0.10,
            width: w * 0.038,
            top: Color.white.opacity(0.88),
            bottom: accent.opacity(0.50))
        sparkle(&ctx, center: CGPoint(x: w * 0.78, y: h * 0.22), radius: w * 0.030, color: accent.opacity(0.55))
    }

    private static func archive(
        _ ctx: inout GraphicsContext,
        _ size: CGSize,
        _ accent: Color,
        _ strong: Color,
        _ pale: Color,
        _ ink: Color
    ) {
        let w = size.width
        let h = size.height
        let cx = w * 0.50
        let top = h * 0.42
        // Lid, wider than the body so the box reads in silhouette.
        ctx.fill(
            roundRect(CGRect(x: w * 0.20, y: top, width: w * 0.60, height: h * 0.13), radius: w * 0.045),
            with: .linearGradient(
                Gradient(colors: [Color.white.opacity(0.95), pale.opacity(0.75)]),
                startPoint: CGPoint(x: w * 0.20, y: top),
                endPoint: CGPoint(x: w * 0.80, y: top + h * 0.13)))
        ctx.fill(
            roundRect(CGRect(x: w * 0.24, y: top + h * 0.11, width: w * 0.52, height: h * 0.30), radius: w * 0.035),
            with: .linearGradient(
                Gradient(colors: [pale.opacity(0.85), accent.opacity(0.42)]),
                startPoint: CGPoint(x: w * 0.24, y: top),
                endPoint: CGPoint(x: w * 0.76, y: top + h * 0.41)))
        ctx.stroke(
            roundRect(CGRect(x: w * 0.24, y: top + h * 0.11, width: w * 0.52, height: h * 0.30), radius: w * 0.035),
            with: .color(Color.white.opacity(0.50)),
            lineWidth: 1.2)
        // Slot
        ctx.fill(
            roundRect(CGRect(x: w * 0.40, y: top + h * 0.20, width: w * 0.20, height: h * 0.030), radius: h * 0.015),
            with: .color(ink.opacity(0.18)))
        // A petal folded over the lid, half in the box.
        petal(
            &ctx,
            center: CGPoint(x: cx, y: top + h * 0.02),
            angle: 16,
            length: h * 0.20,
            width: w * 0.055,
            top: Color.white.opacity(0.95),
            bottom: accent.opacity(0.55))
        ctx.fill(
            Path(ellipseIn: CGRect(x: cx - w * 0.022, y: top + h * 0.02 - w * 0.022, width: w * 0.044, height: w * 0.044)),
            with: .color(strong))
        sparkle(&ctx, center: CGPoint(x: w * 0.24, y: h * 0.24), radius: w * 0.026, color: Color.white.opacity(0.80))
        sparkle(&ctx, center: CGPoint(x: w * 0.80, y: h * 0.68), radius: w * 0.020, color: accent.opacity(0.45))
    }

    private static func notifications(
        _ ctx: inout GraphicsContext,
        _ size: CGSize,
        _ accent: Color,
        _ strong: Color,
        _ pale: Color
    ) {
        let w = size.width
        let h = size.height
        let c = CGPoint(x: w * 0.50, y: h * 0.52)
        let r = w * 0.26
        // Two quiet rings leaving the bell.
        for index in 0..<2 {
            var ring = Path()
            let inset = CGFloat(index) * r * 0.22
            ring.addArc(
                center: CGPoint(x: c.x + r * 0.80, y: c.y - r * 0.30),
                radius: r * (0.55 + inset / r * 0.4),
                startAngle: .degrees(-58),
                endAngle: .degrees(28),
                clockwise: false)
            ctx.stroke(
                ring,
                with: .color(accent.opacity(index == 0 ? 0.50 : 0.26)),
                style: StrokeStyle(lineWidth: 1.4, lineCap: .round))
        }
        var bell = Path()
        bell.move(to: CGPoint(x: c.x - r * 0.80, y: c.y + r * 0.46))
        bell.addCurve(
            to: CGPoint(x: c.x, y: c.y - r * 0.86),
            control1: CGPoint(x: c.x - r * 0.88, y: c.y - r * 0.30),
            control2: CGPoint(x: c.x - r * 0.52, y: c.y - r * 0.82))
        bell.addCurve(
            to: CGPoint(x: c.x + r * 0.80, y: c.y + r * 0.46),
            control1: CGPoint(x: c.x + r * 0.52, y: c.y - r * 0.82),
            control2: CGPoint(x: c.x + r * 0.88, y: c.y - r * 0.30))
        bell.closeSubpath()
        ctx.fill(
            bell,
            with: .linearGradient(
                Gradient(colors: [Color.white.opacity(0.92), pale.opacity(0.72), accent.opacity(0.55)]),
                startPoint: CGPoint(x: c.x, y: c.y - r),
                endPoint: CGPoint(x: c.x, y: c.y + r * 0.5)))
        ctx.stroke(bell, with: .color(accent.opacity(0.55)), lineWidth: 1.5)
        ctx.fill(
            roundRect(CGRect(x: c.x - r * 0.98, y: c.y + r * 0.40, width: r * 1.96, height: r * 0.22), radius: r * 0.11),
            with: .linearGradient(
                Gradient(colors: [accent, strong, accent]),
                startPoint: CGPoint(x: c.x - r, y: c.y),
                endPoint: CGPoint(x: c.x + r, y: c.y)))
        ctx.fill(
            Path(ellipseIn: CGRect(x: c.x - r * 0.20, y: c.y + r * 0.60, width: r * 0.40, height: r * 0.40)),
            with: .color(strong))
        ctx.fill(
            Path(ellipseIn: CGRect(x: c.x - r * 0.125, y: c.y + r * 0.68, width: r * 0.15, height: r * 0.15)),
            with: .color(Color.white.opacity(0.85)))
        ctx.fill(
            Path(ellipseIn: CGRect(x: c.x - r * 0.46, y: c.y - r * 0.58, width: r * 0.30, height: r * 0.52)),
            with: .color(Color.white.opacity(0.55)))
        petal(
            &ctx,
            center: CGPoint(x: w * 0.22, y: h * 0.78),
            angle: -48,
            length: w * 0.10,
            width: w * 0.038,
            top: Color.white.opacity(0.88),
            bottom: accent.opacity(0.50))
    }

    private static func video(
        _ ctx: inout GraphicsContext,
        _ size: CGSize,
        _ accent: Color,
        _ strong: Color,
        _ pale: Color
    ) {
        let w = size.width
        let h = size.height
        let cx = w * 0.52
        let cy = h * 0.46
        ctx.fill(
            Path(ellipseIn: CGRect(x: w * 0.26 - w * 0.55, y: h * 0.18 - w * 0.55, width: w * 1.10, height: w * 1.10)),
            with: .radialGradient(
                Gradient(colors: [Color.white.opacity(0.60), Color.clear]),
                center: CGPoint(x: w * 0.26, y: h * 0.18),
                startRadius: 0,
                endRadius: w * 0.55))
        var backRibbon = Path()
        backRibbon.move(to: CGPoint(x: -w * 0.04, y: h * 0.78))
        backRibbon.addCurve(
            to: CGPoint(x: w * 1.04, y: h * 0.52),
            control1: CGPoint(x: w * 0.24, y: h * 0.44),
            control2: CGPoint(x: w * 0.46, y: h * 0.98))
        ctx.stroke(
            backRibbon,
            with: .linearGradient(
                Gradient(colors: [pale.opacity(0.55), accent.opacity(0.22)]),
                startPoint: CGPoint(x: 0, y: h * 0.74),
                endPoint: CGPoint(x: w, y: h * 0.54)),
            style: StrokeStyle(lineWidth: w * 0.085, lineCap: .round))
        // Back film card.
        let backRect = CGRect(x: cx - w * 0.27, y: cy - h * 0.24, width: w * 0.54, height: h * 0.47)
        let backPath = rotated(roundRect(backRect, radius: w * 0.055), degrees: -9, pivot: CGPoint(x: cx, y: cy))
        ctx.fill(
            backPath,
            with: .linearGradient(
                Gradient(colors: [Color.white.opacity(0.78), pale.opacity(0.50), accent.opacity(0.32)]),
                startPoint: CGPoint(x: backRect.minX, y: backRect.minY),
                endPoint: CGPoint(x: backRect.maxX, y: backRect.maxY)))
        ctx.stroke(backPath, with: .color(Color.white.opacity(0.50)), lineWidth: 1.2)
        // Front film card.
        let pivot = CGPoint(x: cx, y: cy + h * 0.05)
        let frontRect = CGRect(x: cx - w * 0.29, y: cy - h * 0.17, width: w * 0.58, height: h * 0.43)
        let frontPath = rotated(roundRect(frontRect, radius: w * 0.06), degrees: 4, pivot: pivot)
        ctx.fill(
            frontPath,
            with: .linearGradient(
                Gradient(colors: [Color.white.opacity(0.94), pale.opacity(0.60), accent.opacity(0.38)]),
                startPoint: CGPoint(x: frontRect.minX, y: frontRect.minY),
                endPoint: CGPoint(x: frontRect.maxX, y: frontRect.maxY)))
        ctx.stroke(frontPath, with: .color(Color.white.opacity(0.72)), lineWidth: 1.4)
        // The aperture sits on the rotated card, so rotate its centre too.
        let cardTransform = CGAffineTransform(translationX: pivot.x, y: pivot.y)
            .rotated(by: CGFloat(rad(4)))
            .translatedBy(x: -pivot.x, y: -pivot.y)
        let lens = CGPoint(x: frontRect.midX, y: frontRect.minY + frontRect.height * 0.47).applying(cardTransform)
        let lr = w * 0.115
        ctx.fill(
            Path(ellipseIn: CGRect(x: lens.x - lr, y: lens.y - lr, width: lr * 2, height: lr * 2)),
            with: .radialGradient(
                Gradient(colors: [Color.white.opacity(0.95), pale.opacity(0.70), accent.opacity(0.42)]),
                center: CGPoint(x: lens.x - lr * 0.32, y: lens.y - lr * 0.42),
                startRadius: 0,
                endRadius: lr * 1.5))
        ctx.stroke(
            Path(ellipseIn: CGRect(x: lens.x - lr, y: lens.y - lr, width: lr * 2, height: lr * 2)),
            with: .color(Color.white.opacity(0.75)),
            lineWidth: 1.2)
        var tri = Path()
        tri.move(to: CGPoint(x: lens.x - lr * 0.24, y: lens.y - lr * 0.38))
        tri.addLine(to: CGPoint(x: lens.x + lr * 0.46, y: lens.y))
        tri.addLine(to: CGPoint(x: lens.x - lr * 0.24, y: lens.y + lr * 0.38))
        tri.closeSubpath()
        ctx.fill(tri, with: .color(strong.opacity(0.88)))
        for index in 0..<4 {
            let perf = roundRect(
                CGRect(
                    x: frontRect.minX + w * 0.020,
                    y: frontRect.minY + frontRect.height * 0.12 + CGFloat(index) * frontRect.height * 0.20,
                    width: w * 0.018,
                    height: frontRect.height * 0.10),
                radius: w * 0.006)
            ctx.fill(rotated(perf, degrees: 4, pivot: pivot), with: .color(Color.white.opacity(0.48)))
        }
        bubble(&ctx, center: CGPoint(x: w * 0.84, y: h * 0.26), radius: w * 0.075, tint: pale)
        bubble(&ctx, center: CGPoint(x: w * 0.90, y: h * 0.50), radius: w * 0.045, tint: accent)
        bubble(&ctx, center: CGPoint(x: w * 0.14, y: h * 0.24), radius: w * 0.034, tint: pale)
        petal(
            &ctx,
            center: CGPoint(x: w * 0.84, y: h * 0.26),
            angle: -22,
            length: w * 0.052,
            width: w * 0.019,
            top: Color.white.opacity(0.92),
            bottom: strong.opacity(0.42))
        sparkle(&ctx, center: CGPoint(x: w * 0.18, y: h * 0.80), radius: w * 0.028, color: Color.white.opacity(0.70))
    }

    private static func clarity(
        _ ctx: inout GraphicsContext,
        _ size: CGSize,
        _ accent: Color,
        _ strong: Color,
        _ pale: Color,
        _ ink: Color
    ) {
        let w = size.width
        let h = size.height
        let c = CGPoint(x: w * 0.50, y: h * 0.48)
        let r = min(w, h) * 0.30
        oval(&ctx, rect: CGRect(x: c.x - r * 1.05, y: c.y + r * 1.34, width: r * 2.10, height: r * 0.56), colors: [
            ink.opacity(0.10), Color.clear
        ])
        ctx.stroke(
            Path(ellipseIn: CGRect(x: c.x - r * 1.36, y: c.y - r * 1.36, width: r * 2.72, height: r * 2.72)),
            with: .color(pale.opacity(0.34)),
            lineWidth: 1.3)
        ctx.stroke(
            Path(ellipseIn: CGRect(x: c.x - r * 1.14, y: c.y - r * 1.14, width: r * 2.28, height: r * 2.28)),
            with: .color(accent.opacity(0.20)),
            lineWidth: 1.3)
        ctx.fill(
            Path(ellipseIn: CGRect(x: c.x - r * 0.94, y: c.y - r * 0.94, width: r * 1.88, height: r * 1.88)),
            with: .radialGradient(
                Gradient(colors: [Color.white.opacity(0.92), pale.opacity(0.55), accent.opacity(0.28)]),
                center: CGPoint(x: c.x - r * 0.34, y: c.y - r * 0.42),
                startRadius: 0,
                endRadius: r * 1.45))
        // Caustic band.
        let caustic = rotated(
            Path(ellipseIn: CGRect(x: c.x - r * 0.86, y: c.y - r * 0.16, width: r * 1.72, height: r * 0.32)),
            degrees: -32,
            pivot: c)
        ctx.fill(
            caustic,
            with: .radialGradient(
                Gradient(colors: [Color.white.opacity(0.55), Color.clear]),
                center: c,
                startRadius: 0,
                endRadius: r * 0.9))
        burst(
            &ctx,
            center: c,
            radius: r * 0.50,
            light: Color.white.opacity(0.86),
            deep: accent.opacity(0.42),
            petals: 5,
            tilt: -90)
        ctx.fill(
            Path(ellipseIn: CGRect(x: c.x - r * 0.16, y: c.y - r * 0.16, width: r * 0.32, height: r * 0.32)),
            with: .radialGradient(
                Gradient(colors: [Color.white, pale, strong]),
                center: CGPoint(x: c.x - r * 0.06, y: c.y - r * 0.08),
                startRadius: 0,
                endRadius: r * 0.24))
        ctx.stroke(
            Path(ellipseIn: CGRect(x: c.x - r * 0.86, y: c.y - r * 0.86, width: r * 1.72, height: r * 1.72)),
            with: .color(Color.white.opacity(0.55)),
            lineWidth: 1.2)
        ctx.fill(
            Path(ellipseIn: CGRect(x: c.x - r * 0.56, y: c.y - r * 0.66, width: r * 0.52, height: r * 0.24)),
            with: .color(Color.white.opacity(0.60)))
        var orbit = Path()
        orbit.move(to: CGPoint(x: c.x + r * 0.72, y: c.y - r * 0.92))
        orbit.addCurve(
            to: CGPoint(x: c.x + r * 1.02, y: c.y + r * 0.86),
            control1: CGPoint(x: c.x + r * 1.30, y: c.y - r * 0.70),
            control2: CGPoint(x: c.x + r * 1.34, y: c.y + r * 0.30))
        ctx.stroke(orbit, with: .color(accent.opacity(0.40)), style: StrokeStyle(lineWidth: 1.3, lineCap: .round))
        ctx.fill(
            Path(ellipseIn: CGRect(x: c.x + r * 1.30 - r * 0.085, y: c.y - r * 0.44 - r * 0.085, width: r * 0.17, height: r * 0.17)),
            with: .color(strong))
        sparkle(&ctx, center: CGPoint(x: w * 0.18, y: h * 0.22), radius: r * 0.16, color: Color.white.opacity(0.80))
    }

    /// The flower alone: three rings of petals, no water — the hero sheet
    /// fills the reflection card with petals.
    private static func lotus(
        _ ctx: inout GraphicsContext,
        _ size: CGSize,
        _ accent: Color,
        _ strong: Color,
        _ pale: Color
    ) {
        let w = size.width
        let h = size.height
        let c = CGPoint(x: w * 0.5, y: h * 0.78)

        for i in 0..<7 {
            let spread = Double(i - 3) * 24
            petal(&ctx, center: c, angle: spread,
                  length: h * 0.50 - CGFloat(abs(i - 3)) * h * 0.035,
                  width: w * 0.085,
                  top: Color.white.opacity(0.92),
                  bottom: pale.opacity(0.55))
        }
        for i in 0..<5 {
            let spread = Double(i - 2) * 21
            petal(&ctx, center: c, angle: spread,
                  length: h * 0.40 - CGFloat(abs(i - 2)) * h * 0.030,
                  width: w * 0.070,
                  top: Color.white.opacity(0.96),
                  bottom: accent.opacity(0.60))
        }
        for i in 0..<3 {
            petal(&ctx, center: c, angle: Double(i - 1) * 15,
                  length: h * 0.28,
                  width: w * 0.055,
                  top: .white,
                  bottom: strong.opacity(0.72))
        }
        let core = CGRect(x: c.x - w * 0.085, y: c.y - h * 0.02 - w * 0.085,
                          width: w * 0.17, height: w * 0.17)
        ctx.fill(Path(ellipseIn: core), with: .radialGradient(
            Gradient(colors: [.white, pale, strong.opacity(0.55)]),
            center: CGPoint(x: w * 0.488, y: c.y - h * 0.05),
            startRadius: 0, endRadius: w * 0.085))
    }

    private static func reflection(
        _ ctx: inout GraphicsContext,
        _ size: CGSize,
        _ accent: Color,
        _ strong: Color,
        _ pale: Color
    ) {
        let w = size.width
        let h = size.height
        let waterY = h * 0.56
        let c = CGPoint(x: w * 0.50, y: waterY)
        let water = Path(ellipseIn: CGRect(x: w * 0.06, y: waterY - h * 0.10, width: w * 0.88, height: h * 0.34))
        ctx.fill(
            water,
            with: .linearGradient(
                Gradient(colors: [pale.opacity(0.50), accent.opacity(0.26), Color.clear]),
                startPoint: CGPoint(x: w * 0.50, y: waterY - h * 0.10),
                endPoint: CGPoint(x: w * 0.50, y: waterY + h * 0.24)))
        for index in 0..<5 {
            let spread = Double(index - 2) * 22
            petal(
                &ctx,
                center: c,
                angle: 180 + spread,
                length: h * 0.15,
                width: w * 0.040,
                top: accent.opacity(0.22),
                bottom: accent.opacity(0.06))
        }
        ctx.stroke(
            Path(ellipseIn: CGRect(x: c.x - w * 0.30, y: c.y - h * 0.045, width: w * 0.60, height: h * 0.09)),
            with: .color(Color.white.opacity(0.45)),
            lineWidth: 1.2)
        ctx.stroke(
            Path(ellipseIn: CGRect(x: c.x - w * 0.42, y: c.y - h * 0.035, width: w * 0.84, height: h * 0.07)),
            with: .color(accent.opacity(0.22)),
            lineWidth: 1)
        var waterline = Path()
        waterline.move(to: CGPoint(x: c.x - w * 0.44, y: waterY))
        waterline.addLine(to: CGPoint(x: c.x + w * 0.44, y: waterY))
        ctx.stroke(
            waterline,
            with: .linearGradient(
                Gradient(colors: [Color.clear, Color.white.opacity(0.65), Color.clear]),
                startPoint: CGPoint(x: c.x - w * 0.44, y: waterY),
                endPoint: CGPoint(x: c.x + w * 0.44, y: waterY)),
            lineWidth: 1.4)
        for index in 0..<5 {
            let spread = Double(index - 2) * 24
            let length = h * 0.20 - CGFloat(abs(index - 2)) * h * 0.022
            petal(
                &ctx,
                center: c,
                angle: spread,
                length: length,
                width: w * 0.048,
                top: Color.white.opacity(0.88),
                bottom: accent.opacity(0.50))
        }
        let core = CGPoint(x: c.x, y: c.y - h * 0.012)
        ctx.fill(
            Path(ellipseIn: CGRect(x: core.x - w * 0.048, y: core.y - w * 0.048, width: w * 0.096, height: w * 0.096)),
            with: .radialGradient(
                Gradient(colors: [Color.white, pale, strong]),
                center: CGPoint(x: core.x - w * 0.015, y: core.y - h * 0.020),
                startRadius: 0,
                endRadius: w * 0.070))
        ctx.stroke(
            Path(ellipseIn: CGRect(x: core.x - w * 0.040, y: core.y - w * 0.040, width: w * 0.080, height: w * 0.080)),
            with: .color(Color.white.opacity(0.70)),
            lineWidth: 1)
        petal(
            &ctx,
            center: CGPoint(x: w * 0.80, y: h * 0.30),
            angle: 128,
            length: h * 0.075,
            width: w * 0.022,
            top: Color.white.opacity(0.90),
            bottom: accent.opacity(0.40))
        sparkle(&ctx, center: CGPoint(x: w * 0.20, y: h * 0.28), radius: w * 0.032, color: Color.white.opacity(0.80))
    }
}
