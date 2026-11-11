import Foundation

/// The sport a team plays. The order is the order of the picker.
public enum Sport: String, CaseIterable, Identifiable, Sendable {
    case voetbal, hockey, korfbal, volleybal, handbal, basketbal, tennis, padel, rugby, waterpolo, overig

    public var id: String { rawValue }

    /// The Dutch name of the sport.
    public var label: String {
        switch self {
        case .voetbal: return "Voetbal"
        case .hockey: return "Hockey"
        case .korfbal: return "Korfbal"
        case .volleybal: return "Volleybal"
        case .handbal: return "Handbal"
        case .basketbal: return "Basketbal"
        case .tennis: return "Tennis"
        case .padel: return "Padel"
        case .rugby: return "Rugby"
        case .waterpolo: return "Waterpolo"
        case .overig: return "Overig"
        }
    }
}

extension Sport: Codable {
    /// A sport this version does not know yet becomes "overig" instead of failing the whole response.
    public init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = Sport(rawValue: raw) ?? .overig
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(rawValue)
    }
}

public enum Season {
    /// The season a day falls in, running from July to June: "2026/27".
    public static func of(_ day: CalendarDay) -> String {
        let start = day.month >= 7 ? day.year : day.year - 1
        return String(format: "%d/%02d", start, (start + 1) % 100)
    }
}

/// A line of the team's fine list (boetelijst).
public struct Fine: Codable, Equatable, Identifiable, Sendable {
    public let id: String
    public var name: String
    public var amountCents: Int

    public init(id: String, name: String, amountCents: Int) {
        self.id = id
        self.name = name
        self.amountCents = amountCents
    }
}

/// How many fines a member has and what they add up to.
public struct FineStat: Codable, Equatable, Sendable {
    public let memberId: String
    public let count: Int
    public let totalCents: Int

    public init(memberId: String, count: Int, totalCents: Int) {
        self.memberId = memberId
        self.count = count
        self.totalCents = totalCents
    }
}

/// The state of the team pot (teamkas).
public struct PotStatus: Codable, Equatable, Sendable {
    /// Money actually in the pot (kasstand): what came in minus what went out.
    public let cashCents: Int
    /// What members still have to pay into the pot.
    public let toReceiveCents: Int
    /// What the pot still has to pay out to members who advanced something.
    public let toPayCents: Int

    public init(cashCents: Int, toReceiveCents: Int, toPayCents: Int) {
        self.cashCents = cashCents
        self.toReceiveCents = toReceiveCents
        self.toPayCents = toPayCents
    }
}

/// A member as the screens draw them: a person is their name on their
/// highlighter, the team pot a label without one.
public struct Party: Equatable, Identifiable, Sendable {
    public let id: String
    public let name: String
    /// The member's highlighter; nil for the team pot, which is not a person.
    public let highlighter: GroupColor?

    public init(id: String, name: String, highlighter: GroupColor?) {
        self.id = id
        self.name = name
        self.highlighter = highlighter
    }

    public var isPot: Bool { highlighter == nil }
}

/// A charge is a claim of the pot on members: a penalty or a contribution.
public enum ChargeType: String, Codable, Sendable {
    case fine, contribution
}

/// Body of `POST /groups/:id/charges`.
public struct ChargeInput: Codable, Equatable, Sendable {
    public var type: ChargeType
    public var description: String
    /// The amount per member.
    public var amountCents: Int
    public var memberIds: [String]
    public var date: CalendarDay

    public init(type: ChargeType, description: String, amountCents: Int, memberIds: [String], date: CalendarDay) {
        self.type = type
        self.description = description
        self.amountCents = amountCents
        self.memberIds = memberIds
        self.date = date
    }
}

/// Body of `PATCH /groups/:id`. Nil fields are left as they are; an empty
/// `club` or `season` clears it.
public struct GroupPatch: Codable, Equatable, Sendable {
    public var name: String?
    public var color: GroupColor?
    public var sport: Sport?
    public var club: String?
    public var season: String?
    public var treasurerMemberId: String?

    public init(
        name: String? = nil, color: GroupColor? = nil, sport: Sport? = nil,
        club: String? = nil, season: String? = nil, treasurerMemberId: String? = nil
    ) {
        self.name = name
        self.color = color
        self.sport = sport
        self.club = club
        self.season = season
        self.treasurerMemberId = treasurerMemberId
    }
}
