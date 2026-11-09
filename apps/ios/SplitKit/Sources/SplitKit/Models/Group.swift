import Foundation

/// The highlighter of a group or a person. The order is the palette order of
/// `docs/design.md`, which is also how members get their colour (position modulo 8).
public enum GroupColor: String, CaseIterable, Identifiable, Sendable {
    case amber, rose, green, coral, blue, lime, teal, slate

    public var id: String { rawValue }
}

extension GroupColor: Codable {
    /// A colour this version does not know yet becomes the grey highlighter instead of failing the whole response.
    public init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = GroupColor(rawValue: raw) ?? .slate
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(rawValue)
    }
}

public struct Member: Codable, Equatable, Identifiable, Sendable {
    public let id: String
    public var name: String
    /// Nil for a member that was added by name and has no account (yet).
    public var userId: String?
    /// IBAN of the linked account, so others know where to pay.
    public var iban: String?

    public init(id: String, name: String, userId: String?, iban: String?) {
        self.id = id
        self.name = name
        self.userId = userId
        self.iban = iban
    }

    public var hasAccount: Bool { userId != nil }
}

public struct Balance: Codable, Equatable, Sendable {
    public let memberId: String
    /// Positive: the group owes this member. Negative: this member owes the group.
    public let balanceCents: Int

    public init(memberId: String, balanceCents: Int) {
        self.memberId = memberId
        self.balanceCents = balanceCents
    }
}

/// A suggested payment that helps settle the group.
public struct Transfer: Codable, Hashable, Sendable {
    public let fromMemberId: String
    public let toMemberId: String
    public let amountCents: Int

    public init(fromMemberId: String, toMemberId: String, amountCents: Int) {
        self.fromMemberId = fromMemberId
        self.toMemberId = toMemberId
        self.amountCents = amountCents
    }
}

public struct GroupSummary: Codable, Equatable, Identifiable, Sendable {
    public let id: String
    public var name: String
    public var color: GroupColor
    public var currency: String
    public var memberCount: Int
    public var myMemberId: String
    public var myBalanceCents: Int
    public var lastActivityAt: String

    public init(
        id: String, name: String, color: GroupColor, currency: String, memberCount: Int,
        myMemberId: String, myBalanceCents: Int, lastActivityAt: String
    ) {
        self.id = id
        self.name = name
        self.color = color
        self.currency = currency
        self.memberCount = memberCount
        self.myMemberId = myMemberId
        self.myBalanceCents = myBalanceCents
        self.lastActivityAt = lastActivityAt
    }
}

public struct GroupDetail: Codable, Equatable, Identifiable, Sendable {
    public let id: String
    public var name: String
    public var color: GroupColor
    public var currency: String
    public var inviteCode: String
    public var myMemberId: String
    public var members: [Member]
    public var balances: [Balance]
    public var transfers: [Transfer]
    public var totalSpentCents: Int

    public init(
        id: String, name: String, color: GroupColor, currency: String, inviteCode: String, myMemberId: String,
        members: [Member], balances: [Balance], transfers: [Transfer], totalSpentCents: Int
    ) {
        self.id = id
        self.name = name
        self.color = color
        self.currency = currency
        self.inviteCode = inviteCode
        self.myMemberId = myMemberId
        self.members = members
        self.balances = balances
        self.transfers = transfers
        self.totalSpentCents = totalSpentCents
    }

    public func member(_ id: String) -> Member? {
        members.first { $0.id == id }
    }

    /// The member's name, or a dash for a member that is no longer in the group.
    public func memberName(_ id: String) -> String {
        member(id)?.name ?? "–"
    }

    public func balanceCents(of memberId: String) -> Int {
        balances.first { $0.memberId == memberId }?.balanceCents ?? 0
    }

    /// A member's highlighter is their position in the group, modulo the palette size.
    public func color(ofMember memberId: String) -> GroupColor {
        GroupColor.palette(at: members.firstIndex { $0.id == memberId } ?? 0)
    }
}

/// `GET /invites/:code`: what someone sees before joining.
public struct InvitePreview: Codable, Equatable, Sendable {
    public struct UnclaimedMember: Codable, Equatable, Identifiable, Sendable {
        public let id: String
        public let name: String

        public init(id: String, name: String) {
            self.id = id
            self.name = name
        }
    }

    public let groupId: String
    public let name: String
    public let color: GroupColor
    public let memberCount: Int
    public let alreadyMember: Bool
    /// Members without an account that the invitee may claim as themselves.
    public let unclaimedMembers: [UnclaimedMember]

    public init(
        groupId: String, name: String, color: GroupColor, memberCount: Int,
        alreadyMember: Bool, unclaimedMembers: [UnclaimedMember]
    ) {
        self.groupId = groupId
        self.name = name
        self.color = color
        self.memberCount = memberCount
        self.alreadyMember = alreadyMember
        self.unclaimedMembers = unclaimedMembers
    }
}
