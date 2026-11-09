import Foundation

/// The fixed Dutch phrases of `docs/design.md` that depend on data.
public enum Wording {
    /// The balance over all groups: "Je krijgt € 12,50", "Je bent € 8,00 verschuldigd" or "Je staat quitte".
    public static func ownBalance(_ cents: Int) -> String {
        if cents > 0 { return "Je krijgt \(Money.euro(cents))" }
        if cents < 0 { return "Je bent \(Money.euro(cents)) verschuldigd" }
        return "Je staat quitte"
    }

    /// A member's balance in words: "krijgt € 12,50", "betaalt € 8,00" or "quitte".
    public static func memberBalance(_ cents: Int) -> String {
        if cents > 0 { return "krijgt \(Money.euro(cents))" }
        if cents < 0 { return "betaalt \(Money.euro(cents))" }
        return "quitte"
    }

    /// What an expense means for you: "je krijgt € 6,66" / "je betaalt € 3,33"; nil when it changes nothing.
    public static func expenseImpact(_ cents: Int?) -> String? {
        guard let cents, cents != 0 else { return nil }
        return "je \(memberBalance(cents))"
    }

    /// "1 lid" / "3 leden".
    public static func memberCount(_ count: Int) -> String {
        count == 1 ? "1 lid" : "\(count) leden"
    }

    /// "NL91 ABNA 0417 1643 00": an IBAN in groups of four for reading.
    public static func groupedIBAN(_ iban: String) -> String {
        var groups: [String] = []
        var rest = Substring(iban)
        while !rest.isEmpty {
            groups.append(String(rest.prefix(4)))
            rest = rest.dropFirst(4)
        }
        return groups.joined(separator: " ")
    }
}
