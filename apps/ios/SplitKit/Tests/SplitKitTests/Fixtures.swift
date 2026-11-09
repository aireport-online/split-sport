import Foundation
@testable import SplitKit

/// A household of three: Anna (the signed-in person), Bas and Chris, who has no account.
enum Fixtures {
    static let anna = Member(id: "m-anna", name: "Anna", userId: "u-anna", iban: "NL91ABNA0417164300")
    static let bas = Member(id: "m-bas", name: "Bas", userId: "u-bas", iban: nil)
    static let chris = Member(id: "m-chris", name: "Chris", userId: nil, iban: nil)

    static let user = User(id: "u-anna", name: "Anna", email: "anna@example.nl", iban: "NL91ABNA0417164300")
    static let server = URL(string: "https://split.example.nl")!
    static let today = CalendarDay(year: 2026, month: 11, day: 9)

    static let group = GroupDetail(
        id: "g1", name: "Weekendje Texel", color: .teal, currency: "EUR", inviteCode: "ABCD2345", myMemberId: anna.id,
        members: [anna, bas, chris],
        balances: [
            Balance(memberId: anna.id, balanceCents: 2000),
            Balance(memberId: bas.id, balanceCents: -1000),
            Balance(memberId: chris.id, balanceCents: -1000),
        ],
        transfers: [
            Transfer(fromMemberId: bas.id, toMemberId: anna.id, amountCents: 1000),
            Transfer(fromMemberId: chris.id, toMemberId: anna.id, amountCents: 1000),
        ],
        totalSpentCents: 3000
    )

    static func summary(id: String = "g1", name: String = "Weekendje Texel", balance: Int = 0) -> GroupSummary {
        GroupSummary(
            id: id, name: name, color: .teal, currency: "EUR", memberCount: 3, myMemberId: anna.id,
            myBalanceCents: balance, lastActivityAt: "2026-11-09T10:00:00.000Z"
        )
    }

    static func expense(
        id: String = "e1",
        kind: ExpenseKind = .expense,
        description: String = "Boodschappen",
        amountCents: Int = 3000,
        paidById: String = anna.id,
        date: CalendarDay = CalendarDay(year: 2026, month: 11, day: 2),
        splitMode: SplitMode = .equal,
        shares: [ExpenseShare] = [
            ExpenseShare(memberId: anna.id, amountCents: 1000, weight: nil),
            ExpenseShare(memberId: bas.id, amountCents: 1000, weight: nil),
            ExpenseShare(memberId: chris.id, amountCents: 1000, weight: nil),
        ],
        items: [SplitItem] = []
    ) -> Expense {
        Expense(
            id: id, groupId: group.id, kind: kind, description: description, amountCents: amountCents,
            paidById: paidById, date: date, splitMode: splitMode, shares: shares, items: items,
            createdAt: "2026-11-02T10:00:00.000Z", updatedAt: "2026-11-02T10:00:00.000Z"
        )
    }

    static let serverError = APIError.server(status: 400, code: "validation_error", message: "Controleer wat je hebt ingevuld.")
}
