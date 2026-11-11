import Foundation

/// Parsing and formatting of amounts. Money is integer cents everywhere.
///
/// Formatting is written out by hand instead of using `NumberFormatter`, so the
/// result is the same Dutch notation on every device locale and on Linux. The
/// spaces inside an amount are no-break spaces, so "€ 12,50" and "+ 24,95" never
/// wrap in the middle; in the monospaced type they are as wide as a normal space.
public enum Money {
    /// The largest integer JavaScript represents exactly; `parse` rejects more, like the reference.
    private static let maxSafeInteger = 9_007_199_254_740_991

    private static let space = "\u{00A0}"
    private static let minus = "\u{2212}"

    /// Parses what a person typed as an amount ("12,50", "12.5", "1.234,56", "€ 12")
    /// into integer cents. Nil when it is not a valid non-negative amount.
    /// A port of `parseAmount` in `packages/core/src/money.ts`.
    public static func parse(_ input: String) -> Int? {
        var text = input.filter { !$0.isWhitespace && !"€$£".contains($0) }
        guard !text.isEmpty else { return nil }

        if let decimalAt = text.lastIndex(where: { $0 == "," || $0 == "." }) {
            let separator = text[decimalAt]
            let decimals = text.distance(from: decimalAt, to: text.endIndex) - 1
            let occurrences = text.filter { $0 == separator }.count
            // "1.234" or "1,234,567" is a thousands separator, "12,5" / "12.50" a decimal one.
            if decimals <= 2 && occurrences == 1 {
                let whole = text[..<decimalAt].filter { $0 != "," && $0 != "." }
                text = whole + "." + text[text.index(after: decimalAt)...]
            } else if decimals == 3 {
                text = text.filter { $0 != "," && $0 != "." }
            } else {
                return nil
            }
        }

        let parts = text.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count <= 2, let whole = parts.first, !whole.isEmpty, whole.allSatisfy(isDigit) else { return nil }
        let fraction = parts.count == 2 ? parts[1] : ""
        guard fraction.count <= 2, fraction.allSatisfy(isDigit) else { return nil }

        guard let wholeValue = Int(whole), let fractionValue = Int(fraction + String(repeating: "0", count: 2 - fraction.count))
        else { return nil }
        let (scaled, overflow) = wholeValue.multipliedReportingOverflow(by: 100)
        guard !overflow, scaled <= maxSafeInteger - fractionValue else { return nil }
        return scaled + fractionValue
    }

    /// An amount as a till prints it on a receipt line, without sign or currency: "1.234,56".
    public static func plain(_ cents: Int) -> String {
        digits(cents.magnitude, grouped: true)
    }

    /// A balance on a receipt line: "+ 24,95", "− 21,60" or "0,00".
    public static func signedPlain(_ cents: Int) -> String {
        sign(cents) + plain(cents)
    }

    /// An amount in a total, a headline or a sentence: "€ 1.234,56" (without sign).
    public static func euro(_ cents: Int) -> String {
        "€" + space + plain(cents)
    }

    /// The cash in the pot: "€ 47,50", or "− € 5,00" when more went out than came in.
    public static func cash(_ cents: Int) -> String {
        cents < 0 ? signedEuro(cents) : euro(cents)
    }

    /// A balance as a headline figure: "+ € 109,55", "− € 8,00" or "€ 0,00".
    public static func signedEuro(_ cents: Int) -> String {
        sign(cents) + euro(cents)
    }

    /// "1234,56": the text to put in an amount field so it parses back to the same value.
    public static func editingText(_ cents: Int) -> String {
        digits(cents.magnitude, grouped: false)
    }

    private static func sign(_ cents: Int) -> String {
        cents == 0 ? "" : (cents > 0 ? "+" : minus) + space
    }

    private static func digits(_ cents: UInt, grouped: Bool) -> String {
        var whole = String(cents / 100)
        if grouped {
            var index = whole.count - 3
            while index > 0 {
                whole.insert(".", at: whole.index(whole.startIndex, offsetBy: index))
                index -= 3
            }
        }
        let fraction = cents % 100
        return whole + "," + (fraction < 10 ? "0" : "") + String(fraction)
    }

    private static func isDigit(_ character: Character) -> Bool {
        character.isASCII && character.isNumber
    }
}
