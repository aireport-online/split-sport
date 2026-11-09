import SplitKit
import SwiftUI
import UIKit

/// The colour roles of `docs/design.md` as SwiftUI colours that follow light and
/// dark mode. The hex values themselves live in `SplitKit.Palette`.
enum Theme {
    static let ground = Color(Palette.ground)
    static let surface = Color(Palette.surface)
    static let border = Color(Palette.border)
    static let text = Color(Palette.text)
    static let mutedText = Color(Palette.mutedText)
    static let accent = Color(Palette.accent)
    static let onAccent = Color(Palette.onAccent)
    static let positive = Color(Palette.positive)
    static let negative = Color(Palette.negative)

    static let controlRadius = CGFloat(Palette.controlRadius)
}

extension Color {
    /// A colour that switches between its light and dark value with the interface style.
    init(_ themed: ThemedColor) {
        self.init(uiColor: UIColor(dynamicProvider: { traits in
            UIColor(hex: traits.userInterfaceStyle == .dark ? themed.dark : themed.light)
        }))
    }
}

extension UIColor {
    convenience init(hex color: HexColor) {
        self.init(red: CGFloat(color.red), green: CGFloat(color.green), blue: CGFloat(color.blue), alpha: 1)
    }
}

extension Wording.BalanceSign {
    /// A positive balance is green, a negative one the normal text colour
    /// (owing money is not an error), zero is grey.
    var color: Color {
        switch self {
        case .positive: return Theme.positive
        case .negative: return Theme.text
        case .zero: return Theme.mutedText
        }
    }
}

extension View {
    /// A list or form on the app's ground colour.
    func themedList() -> some View {
        scrollContentBackground(.hidden)
            .background(Theme.ground)
    }
}
