import Foundation

/// The fixed Dutch phrases of `docs/design.md` that depend on data.
public enum Wording {
    /// Whether a balance is in someone's favour, against them, or square.
    public enum BalanceSign: Sendable {
        case positive, negative, zero

        public init(cents: Int) {
            self = cents > 0 ? .positive : cents < 0 ? .negative : .zero
        }
    }

    /// The balance over all groups: "Je krijgt € 12,50", "Je bent € 8,00 verschuldigd" or "Je staat quitte".
    public static func ownBalance(_ cents: Int) -> String {
        switch BalanceSign(cents: cents) {
        case .positive: return "Je krijgt \(Money.format(cents))"
        case .negative: return "Je bent \(Money.format(-cents)) verschuldigd"
        case .zero: return "Je staat quitte"
        }
    }

    /// A member's balance in a group: "krijgt € 12,50", "betaalt € 8,00" or "quitte".
    public static func memberBalance(_ cents: Int) -> String {
        switch BalanceSign(cents: cents) {
        case .positive: return "krijgt \(Money.format(cents))"
        case .negative: return "betaalt \(Money.format(-cents))"
        case .zero: return "quitte"
        }
    }

    /// What an expense means for you: "je krijgt € 6,66" / "je betaalt € 3,33"; nil when it changes nothing.
    public static func expenseImpact(_ cents: Int?) -> String? {
        guard let cents, cents != 0 else { return nil }
        return cents > 0 ? "je krijgt \(Money.format(cents))" : "je betaalt \(Money.format(-cents))"
    }

    /// "Anna (jij)" for the signed-in person's own member.
    public static func memberName(_ name: String, isMe: Bool) -> String {
        isMe ? "\(name) (jij)" : name
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
