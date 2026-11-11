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

/// Whether a member is a person or the team pot.
public enum MemberKind: String, Sendable {
    case player, pot
}

extension MemberKind: Codable {
    /// Anything that is not the pot is treated as a person, also a kind a newer server might add.
    public init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = MemberKind(rawValue: raw) ?? .player
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(rawValue)
    }
}

public struct Member: Codable, Equatable, Identifiable, Sendable {
    public let id: String
    public var name: String
    /// `pot` is the team pot (teamkas): one per team, never a person, always last in the team.
    public var kind: MemberKind
    /// Nil for a member that was added by name and has no account (yet).
    public var userId: String?
    /// IBAN of the linked account, so others know where to pay.
    public var iban: String?

    public init(id: String, name: String, kind: MemberKind = .player, userId: String?, iban: String?) {
        self.id = id
        self.name = name
        self.kind = kind
        self.userId = userId
        self.iban = iban
    }

    public var hasAccount: Bool { userId != nil }
    public var isPot: Bool { kind == .pot }
}

public struct Balance: Codable, Equatable, Sendable {
    public let memberId: String
    /// The member's total. Positive: gets money back. The sum of `potCents` and `peerCents`.
    public let balanceCents: Int
    /// What they have with the team pot. Negative: they still owe the pot.
    public let potCents: Int
    /// What they have with other members, from ordinary shared expenses.
    public let peerCents: Int

    public init(memberId: String, balanceCents: Int, potCents: Int, peerCents: Int) {
        self.memberId = memberId
        self.balanceCents = balanceCents
        self.potCents = potCents
        self.peerCents = peerCents
    }
}

/// A suggested payment that helps settle the team.
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
    public var sport: Sport
    public var club: String?
    public var season: String?
    public var currency: String
    /// Team members, not counting the pot.
    public var memberCount: Int
    public var myMemberId: String
    public var myBalanceCents: Int
    public var lastActivityAt: String

    public init(
        id: String, name: String, color: GroupColor, sport: Sport, club: String?, season: String?, currency: String,
        memberCount: Int, myMemberId: String, myBalanceCents: Int, lastActivityAt: String
    ) {
        self.id = id
        self.name = name
        self.color = color
        self.sport = sport
        self.club = club
        self.season = season
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
    public var sport: Sport
    public var club: String?
    public var season: String?
    public var currency: String
    public var inviteCode: String
    public var myMemberId: String
    /// The team pot, also listed last in `members`.
    public var potMemberId: String
    /// The member who keeps the pot's money; their IBAN is where contributions go.
    public var treasurerMemberId: String?
    /// Team members in the order they joined, followed by the pot.
    public var members: [Member]
    public var balances: [Balance]
    /// Suggested payments: first everyone with the pot, then members among themselves.
    public var transfers: [Transfer]
    public var pot: PotStatus
    /// The fine list (boetelijst).
    public var fines: [Fine]
    /// Fines per member, most first; only members with at least one fine.
    public var fineStats: [FineStat]
    /// Expenses only, no charges or payments.
    public var totalSpentCents: Int

    public init(
        id: String, name: String, color: GroupColor, sport: Sport, club: String?, season: String?, currency: String,
        inviteCode: String, myMemberId: String, potMemberId: String, treasurerMemberId: String?,
        members: [Member], balances: [Balance], transfers: [Transfer], pot: PotStatus,
        fines: [Fine], fineStats: [FineStat], totalSpentCents: Int
    ) {
        self.id = id
        self.name = name
        self.color = color
        self.sport = sport
        self.club = club
        self.season = season
        self.currency = currency
        self.inviteCode = inviteCode
        self.myMemberId = myMemberId
        self.potMemberId = potMemberId
        self.treasurerMemberId = treasurerMemberId
        self.members = members
        self.balances = balances
        self.transfers = transfers
        self.pot = pot
        self.fines = fines
        self.fineStats = fineStats
        self.totalSpentCents = totalSpentCents
    }

    /// The people of the team: everyone but the pot.
    public var players: [Member] {
        members.filter { !$0.isPot }
    }

    public func member(_ id: String) -> Member? {
        members.first { $0.id == id }
    }

    /// The member's name, or a dash for a member that is no longer in the team.
    public func memberName(_ id: String) -> String {
        member(id)?.name ?? "–"
    }

    public func balance(of memberId: String) -> Balance? {
        balances.first { $0.memberId == memberId }
    }

    /// A member as the screens draw them: highlighted by their position in the team, or the pot's label.
    public func party(_ memberId: String) -> Party {
        guard let index = members.firstIndex(where: { $0.id == memberId }) else {
            return Party(id: memberId, name: "–", highlighter: GroupColor.palette(at: 0))
        }
        return Party(member: members[index], index: index)
    }

    /// Every member as a party, in team order (the pot last).
    public var parties: [Party] {
        members.indices.map { Party(member: members[$0], index: $0) }
    }
}

public extension Party {
    /// - Parameter index: the member's position in the team, which decides their highlighter.
    init(member: Member, index: Int) {
        self.init(id: member.id, name: member.name, highlighter: member.isPot ? nil : GroupColor.palette(at: index))
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
    public let sport: Sport
    public let club: String?
    /// Team members, not counting the pot.
    public let memberCount: Int
    public let alreadyMember: Bool
    /// Members without an account that the invitee may claim as themselves.
    public let unclaimedMembers: [UnclaimedMember]

    public init(
        groupId: String, name: String, color: GroupColor, sport: Sport, club: String?, memberCount: Int,
        alreadyMember: Bool, unclaimedMembers: [UnclaimedMember]
    ) {
        self.groupId = groupId
        self.name = name
        self.color = color
        self.sport = sport
        self.club = club
        self.memberCount = memberCount
        self.alreadyMember = alreadyMember
        self.unclaimedMembers = unclaimedMembers
    }
}
