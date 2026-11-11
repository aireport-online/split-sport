import Foundation

/// "Rit berekenen": the cost of a drive as kilometres × a rate per kilometre.
public struct TripCalculation: Equatable, Sendable {
    /// The rate the helper starts with, as typed: € 0,23 per kilometre.
    public static let defaultRateText = "0,23"

    /// The distance in hundredths of a kilometre, so "12,5" stays exact.
    private let kilometerHundredths: Int
    /// The rate per kilometre in cents.
    public let rateCents: Int

    /// Nil while the kilometres or the rate are not positive numbers
    /// ("84", "12,5"; the rate as an amount, "0,23").
    public init?(kilometersText: String, rateText: String) {
        guard let kilometers = Money.parse(kilometersText), kilometers > 0,
              let rate = Money.parse(rateText), rate > 0
        else { return nil }
        // Both stay small enough for their product to fit: a drive is not a million kilometres.
        guard kilometers <= 100_000_000, rate <= SplitMath.maxAmountCents else { return nil }
        kilometerHundredths = kilometers
        rateCents = rate
    }

    /// The cost in cents, rounded half up.
    public var amountCents: Int {
        (kilometerHundredths * rateCents + 50) / 100
    }

    /// "84" or "12,5": the distance without needless decimals.
    public var kilometers: String {
        let whole = kilometerHundredths / 100
        let fraction = kilometerHundredths % 100
        if fraction == 0 { return "\(whole)" }
        if fraction % 10 == 0 { return "\(whole),\(fraction / 10)" }
        return "\(whole),\(fraction < 10 ? "0" : "")\(fraction)"
    }

    /// The description of an expense that has none yet: "Rit (84 km)".
    public var description: String {
        "Rit (\(kilometers) km)"
    }

    /// "84 km × € 0,23 = € 19,32".
    public var summary: String {
        "\(kilometers) km × \(Money.euro(rateCents)) = \(Money.euro(amountCents))"
    }
}
