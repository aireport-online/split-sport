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

/// The colour tokens of `docs/design.md`. The app target turns these into
/// dynamic SwiftUI colours; the table lives here so it is tested.
public enum Palette {
    public static let ground = ThemedColor(light: 0xF3F5F7, dark: 0x101214)
    public static let surface = ThemedColor(light: 0xFFFFFF, dark: 0x1A1D20)
    public static let border = ThemedColor(light: 0xE6E6E6, dark: 0x2C3034)
    public static let text = ThemedColor(light: 0x1B1D1F, dark: 0xECEEF0)
    public static let mutedText = ThemedColor(light: 0x5C6064, dark: 0xA0A5AB)
    public static let accent = ThemedColor(light: 0x1D5FD6, dark: 0x7FB0FF)
    public static let onAccent = ThemedColor(light: 0xFFFFFF, dark: 0x06224F)
    public static let positive = ThemedColor(light: 0x2F7A45, dark: 0x6FCF8B)
    public static let negative = ThemedColor(light: 0xC41C29, dark: 0xFF8A8F)

    /// Corner radius of controls, in points.
    public static let controlRadius = 10.0
}

/// The pale fill and dark ink a group tile or member circle is drawn in.
public struct Tint: Hashable, Sendable {
    public let background: ThemedColor
    public let ink: ThemedColor
}

public extension GroupColor {
    var tint: Tint {
        switch self {
        case .blue:
            return Tint(background: ThemedColor(light: 0xDCF1FF, dark: 0x12305C), ink: ThemedColor(light: 0x0A3A80, dark: 0xCFE6FF))
        case .amber:
            return Tint(background: ThemedColor(light: 0xFFF1CF, dark: 0x4A3206), ink: ThemedColor(light: 0x7A3E00, dark: 0xFFE2A6))
        case .teal:
            return Tint(background: ThemedColor(light: 0xD5F3F1, dark: 0x0E3D3A), ink: ThemedColor(light: 0x0B4F4A, dark: 0xBDEEEA))
        case .rose:
            return Tint(background: ThemedColor(light: 0xFFE1EC, dark: 0x52142E), ink: ThemedColor(light: 0x8A1846, dark: 0xFFC9DC))
        case .green:
            return Tint(background: ThemedColor(light: 0xDCFAE5, dark: 0x113D20), ink: ThemedColor(light: 0x0B4A22, dark: 0xC2F2D0))
        case .coral:
            return Tint(background: ThemedColor(light: 0xFFE4DC, dark: 0x52200F), ink: ThemedColor(light: 0x8A2B12, dark: 0xFFCDBF))
        case .lime:
            return Tint(background: ThemedColor(light: 0xECF6CC, dark: 0x2F3B08), ink: ThemedColor(light: 0x3F5200, dark: 0xE0F0A8))
        case .slate:
            return Tint(background: ThemedColor(light: 0xE9EDF2, dark: 0x2A3138), ink: ThemedColor(light: 0x2F3A46, dark: 0xD7DEE6))
        }
    }

    /// The Dutch name of the colour, for VoiceOver.
    var title: String {
        switch self {
        case .blue: return "Blauw"
        case .amber: return "Amber"
        case .teal: return "Groenblauw"
        case .rose: return "Roze"
        case .green: return "Groen"
        case .coral: return "Koraal"
        case .lime: return "Limoen"
        case .slate: return "Grijs"
        }
    }

    /// The colour at `index` of the palette, wrapping around: how members (by position) and new groups get a tint.
    static func palette(at index: Int) -> GroupColor {
        allCases[((index % allCases.count) + allCases.count) % allCases.count]
    }
}

public enum Initials {
    /// The first letter of the first two words: "Weekendje Texel" → "WT", "Anna" → "A".
    public static func of(_ name: String) -> String {
        name.split(whereSeparator: \.isWhitespace)
            .prefix(2)
            .compactMap(\.first)
            .map { String($0).uppercased() }
            .joined()
    }
}
