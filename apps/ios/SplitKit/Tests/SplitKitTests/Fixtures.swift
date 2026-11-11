import Foundation
@testable import SplitKit

/// A team of three: Anna (the signed-in person and penningmeester), Bas and Chris,
/// who has no account, and the team pot.
enum Fixtures {
    static let anna = Member(id: "m-anna", name: "Anna", userId: "u-anna", iban: "NL91ABNA0417164300")
    static let bas = Member(id: "m-bas", name: "Bas", userId: "u-bas", iban: nil)
    static let chris = Member(id: "m-chris", name: "Chris", userId: nil, iban: nil)
    static let pot = Member(id: "m-pot", name: "Teamkas", kind: .pot, userId: nil, iban: nil)

    static let user = User(id: "u-anna", name: "Anna", email: "anna@example.nl", iban: "NL91ABNA0417164300")
    static let server = URL(string: "https://split.example.nl")!
    static let today = CalendarDay(year: 2026, month: 11, day: 9)

    static let fines = [
        Fine(id: "f1", name: "Te laat", amountCents: 200),
        Fine(id: "f2", name: "Gele kaart", amountCents: 500),
    ]

    /// Among each other Bas and Chris each owe Anna 10,00. With the pot: Bas still
    /// owes 12,00 and the pot owes Chris 40,00 for the balls he bought.
    static let group = GroupDetail(
        id: "g1", name: "Heren 3", color: .teal, sport: .hockey, club: "MHC Oostpoort", season: "2026/27",
        currency: "EUR", inviteCode: "ABCD2345", myMemberId: anna.id, potMemberId: pot.id, treasurerMemberId: anna.id,
        members: [anna, bas, chris, pot],
        balances: [
            Balance(memberId: anna.id, balanceCents: 2000, potCents: 0, peerCents: 2000),
            Balance(memberId: bas.id, balanceCents: -2200, potCents: -1200, peerCents: -1000),
            Balance(memberId: chris.id, balanceCents: 3000, potCents: 4000, peerCents: -1000),
            Balance(memberId: pot.id, balanceCents: -2800, potCents: -2800, peerCents: 0),
        ],
        transfers: [
            Transfer(fromMemberId: bas.id, toMemberId: pot.id, amountCents: 1200),
            Transfer(fromMemberId: pot.id, toMemberId: chris.id, amountCents: 4000),
            Transfer(fromMemberId: bas.id, toMemberId: anna.id, amountCents: 1000),
            Transfer(fromMemberId: chris.id, toMemberId: anna.id, amountCents: 1000),
        ],
        pot: PotStatus(cashCents: 4750, toReceiveCents: 1200, toPayCents: 4000),
        fines: fines,
        fineStats: [
            FineStat(memberId: bas.id, count: 3, totalCents: 900),
            FineStat(memberId: anna.id, count: 1, totalCents: 200),
        ],
        totalSpentCents: 3000
    )

    static func summary(id: String = "g1", name: String = "Heren 3", balance: Int = 0) -> GroupSummary {
        GroupSummary(
            id: id, name: name, color: .teal, sport: .hockey, club: "MHC Oostpoort", season: "2026/27",
            currency: "EUR", memberCount: 3, myMemberId: anna.id, myBalanceCents: balance,
            lastActivityAt: "2026-11-09T10:00:00.000Z"
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
