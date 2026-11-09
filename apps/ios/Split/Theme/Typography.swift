import SplitKit
import SwiftUI
import UIKit

/// The two typefaces of the house style, bundled in `Resources/Fonts` and listed
/// under `UIAppFonts`. The names are the fonts' PostScript names.
private enum Typeface {
    static let display = "BarlowCondensed-Bold"
    static let monoRegular = "JetBrainsMono-Regular"
    static let monoMedium = "JetBrainsMono-Medium"
    static let monoBold = "JetBrainsMono-Bold"
}

/// The text styles of the app. Every size is relative to a system text style, so
/// Dynamic Type scales it.
extension Font {
    // Barlow Condensed Bold: everything that is "printed big".

    /// "Split", torn in two, on the login screen.
    static let wordmark = Font.custom(Typeface.display, size: 84, relativeTo: .largeTitle)
    /// The title of a receipt ("Saldo", "Vereffenen") and a group's name.
    static let receiptTitle = Font.custom(Typeface.display, size: 22, relativeTo: .title2)
    static let buttonLabel = Font.custom(Typeface.display, size: 18, relativeTo: .headline)
    static let tabLabel = Font.custom(Typeface.display, size: 17, relativeTo: .headline)
    /// Person chips, small buttons and the stamp.
    static let chipLabel = Font.custom(Typeface.display, size: 16, relativeTo: .subheadline)

    // JetBrains Mono: everything a till would print.

    /// Receipt lines, field values and running text.
    static let till = Font.custom(Typeface.monoRegular, size: 15, relativeTo: .body)
    static let tillBold = Font.custom(Typeface.monoBold, size: 15, relativeTo: .body)
    /// Labels and secondary lines.
    static let tillSmall = Font.custom(Typeface.monoRegular, size: 13, relativeTo: .footnote)
    /// The headline figure ("+ € 109,55") and the amount of an expense.
    static let tillFigure = Font.custom(Typeface.monoMedium, size: 30, relativeTo: .largeTitle)
    /// The total at the bottom of a receipt.
    static let tillTotal = Font.custom(Typeface.monoMedium, size: 20, relativeTo: .title3)
}

extension Theme {
    /// Navigation bars lie on the desk and print their titles in Barlow Condensed
    /// (30 pt as a large title, 22 pt inline). Called once at launch;
    /// `UINavigationBar.appearance()` covers every bar after that.
    @MainActor
    static func applyNavigationBarAppearance() {
        let ink = UIColor(Palette.ink)
        let appearance = UINavigationBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor(Palette.desk)
        appearance.shadowColor = .clear
        appearance.titleTextAttributes = [.font: displayFont(size: 22), .foregroundColor: ink]
        appearance.largeTitleTextAttributes = [.font: displayFont(size: 30), .foregroundColor: ink]

        let navigationBar = UINavigationBar.appearance()
        navigationBar.standardAppearance = appearance
        navigationBar.compactAppearance = appearance
        navigationBar.scrollEdgeAppearance = appearance
    }

    private static func displayFont(size: CGFloat) -> UIFont {
        UIFont(name: Typeface.display, size: size) ?? UIFont.systemFont(ofSize: size, weight: .bold)
    }
}
