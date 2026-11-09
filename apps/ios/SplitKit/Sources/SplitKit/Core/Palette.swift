import Foundation

/// A colour as `0xRRGGBB`.
public struct HexColor: Hashable, Sendable {
    public let hex: UInt32

    public init(_ hex: UInt32) {
        self.hex = hex
    }

    public var red: Double { Double((hex >> 16) & 0xFF) / 255 }
    public var green: Double { Double((hex >> 8) & 0xFF) / 255 }
    public var blue: Double { Double(hex & 0xFF) / 255 }
}

/// One colour role: its value on the light and on the dark theme.
public struct ThemedColor: Hashable, Sendable {
    public let light: HexColor
    public let dark: HexColor

    public init(light: UInt32, dark: UInt32) {
        self.light = HexColor(light)
        self.dark = HexColor(dark)
    }
}

/// The colours of the "Kassabon" house style (`docs/design.md`). The app target
/// turns these into dynamic SwiftUI colours; the table lives here so it is tested.
public enum Palette {
    /// The page ground the receipts lie on.
    public static let desk = ThemedColor(light: 0xE8E5DE, dark: 0x171614)
    /// Receipts, sheets and dialogs.
    public static let paper = ThemedColor(light: 0xFBF8F1, dark: 0x24221F)
    /// Text, primary buttons and outlines.
    public static let ink = ThemedColor(light: 0x141414, dark: 0xF4F0E7)
    /// Text on a primary button.
    public static let onInk = ThemedColor(light: 0xFBF8F1, dark: 0x171614)
    /// Labels and secondary lines.
    public static let mutedInk = ThemedColor(light: 0x615D55, dark: 0xA9A499)
    /// Dashed dividers and field underlines.
    public static let perforation = ThemedColor(light: 0xB5B0A5, dark: 0x4A463F)
    /// The "BETAALD" stamp, destructive actions and errors.
    public static let stamp = ThemedColor(light: 0xD0342C, dark: 0xFF6B5E)

    /// Text that lies on a highlighter: the same in both themes, like the highlighters themselves.
    public static let highlighterInk = HexColor(0x141414)
}

public extension GroupColor {
    /// The highlighter colour; the same on the light and the dark theme.
    var highlighter: HexColor {
        switch self {
        case .amber: return HexColor(0xFFF04A)
        case .rose: return HexColor(0xFF8CCF)
        case .green: return HexColor(0x8AF0AE)
        case .coral: return HexColor(0xFFAE5C)
        case .blue: return HexColor(0x8FD4FF)
        case .lime: return HexColor(0xD2F25A)
        case .teal: return HexColor(0x74E6DA)
        case .slate: return HexColor(0xD8D4C9)
        }
    }

    /// The Dutch name of the highlighter, for VoiceOver.
    var title: String {
        switch self {
        case .amber: return "Geel"
        case .rose: return "Roze"
        case .green: return "Mint"
        case .coral: return "Oranje"
        case .blue: return "Blauw"
        case .lime: return "Limoen"
        case .teal: return "Turquoise"
        case .slate: return "Grijs"
        }
    }

    /// The colour at `index` of the palette, wrapping around: how members (by position) and new groups get a colour.
    static func palette(at index: Int) -> GroupColor {
        allCases[((index % allCases.count) + allCases.count) % allCases.count]
    }
}
