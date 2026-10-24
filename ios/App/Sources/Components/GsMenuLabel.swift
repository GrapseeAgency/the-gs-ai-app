import SwiftUI
import UIKit

/// A glyph from the family, rasterised into a **template image**.
///
/// Menus are not ordinary layout. A `Label`'s icon inside a `Menu`,
/// `.contextMenu` or `.swipeActions` is handed to UIKit's menu chrome, which
/// paints a template image with the label's own tint — that is how an SF
/// Symbol turns red in a `Button(role: .destructive)` row and dims when the
/// item is disabled. A `Canvas` drawing its own colour would keep that colour
/// in every row: a red Delete would still read black, and a disabled row would
/// not dim. `GsIconView` is therefore the wrong tool here, and this is the
/// right one: draw the same geometry into a template `UIImage`, so the chrome
/// tints the family's glyph exactly as it tinted the SF Symbol it replaces.
///
/// The stroke matches `GsIconView` to the point — 24-unit viewport, width 2,
/// round caps and joins — so a glyph in a menu and the same glyph on a page
/// are the same drawing, not two approximations of one.
enum GsMenuGlyph {

    /// Rasterised once per (icon, side) pair. Menu bodies are re-evaluated on
    /// every presentation and `Image(uiImage:)` does not memoise; the same
    /// twenty icons open dozens of times a session.
    private static var cache: [String: UIImage] = [:]

    static func image(_ icon: GsIconPaths, side: CGFloat) -> UIImage {
        let key = "\(side)|\(geometryKey(icon))"
        if let hit = cache[key] { return hit }
        let made = render(icon, side: side)
        cache[key] = made
        return made
    }

    /// `GsIconPaths` is not `Hashable`; its command stream is what actually
    /// distinguishes one glyph from another, so that is the identity.
    private static func geometryKey(_ icon: GsIconPaths) -> String {
        var key = ""
        for commands in icon.paths {
            key.append("[")
            for command in commands {
                switch command {
                case let .move(x, y): key.append("M\(x),\(y);")
                case let .line(x, y): key.append("L\(x),\(y);")
                case let .cubic(a, b, c, d, e, f): key.append("C\(a),\(b),\(c),\(d),\(e),\(f);")
                case .close: key.append("Z;")
                }
            }
        }
        return key
    }

    private static func render(_ icon: GsIconPaths, side: CGFloat) -> UIImage {
        let format = UIGraphicsImageRendererFormat.default()
        format.opaque = false
        format.scale = UIScreen.main.scale
        let box = CGSize(width: side, height: side)
        let image = UIGraphicsImageRenderer(size: box, format: format).image { context in
            let cg = context.cgContext
            let scale = min(box.width, box.height) / 24
            cg.scaleBy(x: scale, y: scale)
            let path = CGMutablePath()
            for commands in icon.paths {
                for command in commands {
                    switch command {
                    case let .move(x, y):
                        path.move(to: CGPoint(x: x, y: y))
                    case let .line(x, y):
                        path.addLine(to: CGPoint(x: x, y: y))
                    case let .cubic(c1x, c1y, c2x, c2y, x, y):
                        path.addCurve(
                            to: CGPoint(x: x, y: y),
                            control1: CGPoint(x: c1x, y: c1y),
                            control2: CGPoint(x: c2x, y: c2y)
                        )
                    case .close:
                        path.closeSubpath()
                    }
                }
            }
            cg.addPath(path)
            cg.setLineWidth(2)
            cg.setLineCap(.round)
            cg.setLineJoin(.round)
            // Black is the template's alpha; the chrome supplies the colour.
            cg.setStrokeColor(UIColor.black.cgColor)
            cg.strokePath()
        }
        return image.withRenderingMode(.alwaysTemplate)
    }
}

/// One menu row: the title, and the family's glyph in the icon slot SwiftUI
/// gives a `Label`.
///
/// 18 pt is the optical size of a menu's leading icon: Android's menu rows
/// draw theirs at 18.dp, and SF's own box beside a 17 pt label is the same.
/// The harness (`ios/App/Tests/UITests/MenuChromeTests.swift`) measures the
/// rasterised ink against that number instead of trusting it.
struct GsMenuLabel: View {
    static let glyphSide: CGFloat = 18

    private let title: String
    private let icon: GsIconPaths
    private let side: CGFloat

    init(_ title: String, icon: GsIconPaths, side: CGFloat = GsMenuLabel.glyphSide) {
        self.title = title
        self.icon = icon
        self.side = side
    }

    var body: some View {
        Label {
            Text(title)
        } icon: {
            Image(uiImage: GsMenuGlyph.image(icon, side: side))
                .resizable()
                .scaledToFit()
                .frame(width: side, height: side)
        }
    }
}
