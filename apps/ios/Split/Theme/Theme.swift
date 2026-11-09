import SplitKit
import SwiftUI
import UIKit

/// The "Kassabon" house style (`docs/design.md`): the colour roles as SwiftUI
/// colours that follow light and dark mode, and the measures of the paper.
/// The hex values themselves live in `SplitKit.Palette`.
enum Theme {
    static let desk = Color(Palette.desk)
    static let paper = Color(Palette.paper)
    static let ink = Color(Palette.ink)
    static let onInk = Color(Palette.onInk)
    static let mutedInk = Color(Palette.mutedInk)
    static let perforation = Color(Palette.perforation)
    static let stamp = Color(Palette.stamp)
    /// Text that lies on a highlighter: the same in both themes.
    static let highlighterInk = Color(Palette.highlighterInk)

    /// Corner radius of buttons and segmented controls.
    static let radius: CGFloat = 4
    /// Width of ink outlines.
    static let outline: CGFloat = 1.5
    /// One tooth of the zigzag tear at the bottom of a receipt.
    static let toothWidth: CGFloat = 12
    static let toothHeight: CGFloat = 6
    /// Space between the edge of a receipt and what is printed on it.
    static let receiptPadding: CGFloat = 18
    /// Space between the edge of the screen and a receipt.
    static let deskPadding: CGFloat = 16
    static let buttonHeight: CGFloat = 48
    /// The dashed line of a perforation and of a field underline.
    static let perforationStroke = StrokeStyle(lineWidth: 1.5, dash: [6, 4])
}

extension Color {
    /// A colour that switches between its light and dark value with the interface style.
    init(_ themed: ThemedColor) {
        self.init(uiColor: UIColor(themed))
    }

    /// A colour that is the same in both themes.
    init(_ color: HexColor) {
        self.init(red: color.red, green: color.green, blue: color.blue)
    }
}

extension UIColor {
    convenience init(_ themed: ThemedColor) {
        self.init(dynamicProvider: { traits in
            UIColor(hex: traits.userInterfaceStyle == .dark ? themed.dark : themed.light)
        })
    }

    convenience init(hex color: HexColor) {
        self.init(red: CGFloat(color.red), green: CGFloat(color.green), blue: CGFloat(color.blue), alpha: 1)
    }
}

extension GroupColor {
    /// The highlighter as a SwiftUI colour.
    var highlighterColor: Color {
        Color(highlighter)
    }
}
