import Foundation

public enum ExpenseKind: String, Codable, Sendable {
    case expense, settlement
}

public enum SplitMode: String, Codable, CaseIterable, Sendable {
    case equal, exact, shares, items

    /// The name of the mode in the "Verdeling" picker.
    public var title: String {
        switch self {
        case .equal: return "Gelijk"
        case .exact: return "Bedragen"
        case .shares: return "Aandelen"
        case .items: return "Per regel"
        }
    }
}

/// What one participant owes for an expense, as stored by the server.
public struct ExpenseShare: Codable, Equatable, Sendable {
    public let memberId: String
    public let amountCents: Int
    /// Only set for split mode `shares`.
    public let weight: Int?

    public init(memberId: String, amountCents: Int, weight: Int?) {
        self.memberId = memberId
        self.amountCents = amountCents
        self.weight = weight
    }
}

/// One line of a receipt and who had it.
public struct SplitItem: Codable, Equatable, Sendable {
    public var name: String
    public var amountCents: Int
    public var memberIds: [String]

    public init(name: String, amountCents: Int, memberIds: [String]) {
        self.name = name
        self.amountCents = amountCents
        self.memberIds = memberIds
    }
}

public struct Expense: Codable, Equatable, Identifiable, Sendable {
    public let id: String
    public let groupId: String
    public let kind: ExpenseKind
    public let description: String
    public let amountCents: Int
    /// Member that paid. For a settlement: the member that paid back.
    public let paidById: String
    public let date: CalendarDay
    public let splitMode: SplitMode
    /// What every participant owes. For a settlement: one share for the receiver.
    public let shares: [ExpenseShare]
    /// Receipt lines, only for split mode `items`.
    public let items: [SplitItem]
    public let createdAt: String
    public let updatedAt: String

    public init(
        id: String, groupId: String, kind: ExpenseKind, description: String, amountCents: Int, paidById: String,
        date: CalendarDay, splitMode: SplitMode, shares: [ExpenseShare], items: [SplitItem],
        createdAt: String, updatedAt: String
    ) {
        self.id = id
        self.groupId = groupId
        self.kind = kind
        self.description = description
        self.amountCents = amountCents
        self.paidById = paidById
        self.date = date
        self.splitMode = splitMode
        self.shares = shares
        self.items = items
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }

    /// What this expense does to a member's balance: what they paid minus their share.
    /// Nil when the member neither paid nor takes part.
    public func impactCents(on memberId: String) -> Int? {
        let share = shares.first { $0.memberId == memberId }
        guard paidById == memberId || share != nil else { return nil }
        return (paidById == memberId ? amountCents : 0) - (share?.amountCents ?? 0)
    }
}

/// Body of `POST`/`PUT /groups/:id/expenses`.
public struct ExpenseInput: Codable, Equatable, Sendable {
    public var description: String
    public var amountCents: Int
    public var date: CalendarDay
    public var paidById: String
    public var split: Split

    public init(description: String, amountCents: Int, date: CalendarDay, paidById: String, split: Split) {
        self.description = description
        self.amountCents = amountCents
        self.date = date
        self.paidById = paidById
        self.split = split
    }
}

/// Body of `POST /groups/:id/settlements`.
public struct SettlementInput: Codable, Equatable, Sendable {
    public var fromMemberId: String
    public var toMemberId: String
    public var amountCents: Int
    public var date: CalendarDay

    public init(fromMemberId: String, toMemberId: String, amountCents: Int, date: CalendarDay) {
        self.fromMemberId = fromMemberId
        self.toMemberId = toMemberId
        self.amountCents = amountCents
        self.date = date
    }
}

/// `POST /receipts/scan`: what the server read from a photo of a receipt.
public struct ReceiptScan: Codable, Equatable, Sendable {
    public struct Line: Codable, Equatable, Sendable {
        public let name: String
        public let amountCents: Int

        public init(name: String, amountCents: Int) {
            self.name = name
            self.amountCents = amountCents
        }
    }

    public let merchant: String?
    /// Kept as text: a date the server could not validate must not fail the whole scan.
    public let date: String?
    public let totalCents: Int?
    public let items: [Line]

    public init(merchant: String?, date: String?, totalCents: Int?, items: [Line]) {
        self.merchant = merchant
        self.date = date
        self.totalCents = totalCents
        self.items = items
    }
}
